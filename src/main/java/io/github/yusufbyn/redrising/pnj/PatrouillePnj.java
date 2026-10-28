package io.github.yusufbyn.redrising.pnj;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.factions.Acces;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Controle;
import io.github.yusufbyn.redrising.factions.EffetsActions;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.factions.Ronde;
import io.github.yusufbyn.redrising.jalons.EffetsMonde;
import io.github.yusufbyn.redrising.jalons.Levier;
import io.github.yusufbyn.redrising.monde.Vue;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Patrouilles de Gris (G5.5) : un PNJ Gris suit une {@link Ronde} et contrôle les joueurs suspects ({@link Controle}).
 *
 * <p>Le PNJ marche grâce au cerveau des villageois : on lui donne sa destination (mémoire {@code WALK_TARGET}) toutes les
 * demi-secondes, et il trouve son chemin. Le contrôle passe avant la ronde ; la ronde reprend là où elle s'était arrêtée.</p>
 *
 * <p>Seule la ronde est enregistrée avec le PNJ ; un contrôle en cours est perdu au rechargement. Se déconnecter pendant
 * un contrôle compte comme une fuite.</p>
 */
public final class PatrouillePnj {
    /** Ronde d'un PNJ Gris en patrouille. */
    public static final AttachmentType<Ronde> RONDE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "ronde"),
            builder -> builder.persistent(Ronde.CODEC)
    );

    /**
     * Sur le joueur : tick du dernier contrôle subi, et jour de jeu du dernier contrôle obéi qui a fait baisser la Suspicion
     * (une fois par jour au plus, sinon on se blanchit en allant voir les patrouilles).
     */
    public record SuiviControle(long dernierControle, long dernierJourBlanchi) {
        public static final SuiviControle AUCUN = new SuiviControle(-Controle.ATTENTE_ENTRE_CONTROLES, -1);
        public static final Codec<SuiviControle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("dernier_controle").forGetter(SuiviControle::dernierControle),
                Codec.LONG.fieldOf("dernier_jour_blanchi").forGetter(SuiviControle::dernierJourBlanchi)
        ).apply(instance, SuiviControle::new));
    }

    public static final AttachmentType<SuiviControle> SUIVI_CONTROLE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "suivi_controle"),
            builder -> builder.persistent(SuiviControle.CODEC).copyOnDeath()
    );

    /** Sur le joueur, du « Halte » à l'issue du contrôle. Sauvegardé : s'il se déconnecte entre les deux, c'est une fuite. */
    private static final AttachmentType<Boolean> CONTROLE_EN_COURS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "controle_en_cours"),
            builder -> builder.persistent(Codec.BOOL)
    );

    private static final int PERIODE = 10;
    /** Le Gris s'arrête 3 secondes à chaque point de sa ronde. */
    private static final int PAUSE_AU_POINT = 60;
    private static final float VITESSE_RONDE = 0.5f;
    private static final float VITESSE_APPROCHE = 0.6f;
    private static final float VITESSE_FOUILLE = 0.6f;
    /** 🟡 Un Gris pris dans une chausse-trappe fouille 20 secondes autour avant de reprendre sa ronde (G5.8). */
    public static final long DUREE_FOUILLE_PIEGE = 400;

    /** PNJ en patrouille chargés dans le monde (tenus à jour au chargement et au déchargement des entités). */
    private static final Set<Villager> PATROUILLEURS = new HashSet<>();
    /** PNJ arrêtés à un point de leur ronde → tick d'arrivée. */
    private static final Map<UUID, Long> EN_PAUSE = new HashMap<>();
    /** PNJ en train de contrôler → le contrôle. */
    private static final Map<UUID, EnCours> CONTROLES = new HashMap<>();

    private record EnCours(UUID suspect, Controle controle) {
    }

    /** PNJ qui fouillent autour d'une chausse-trappe → la fouille (pas sauvegardée). */
    private static final Map<UUID, Fouille> FOUILLES = new HashMap<>();

    private record Fouille(Ronde ronde, long fin) {
    }

    private PatrouillePnj() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()}. */
    public static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((entite, monde) -> {
            if (entite instanceof Villager pnj && pnj.hasAttached(RONDE)) {
                PATROUILLEURS.add(pnj);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entite, monde) -> {
            if (entite instanceof Villager pnj) {
                oublier(pnj);
            }
        });
        // Revenu avec un contrôle en cours : il s'était déconnecté (ou le serveur s'était arrêté) pendant le contrôle.
        // On ne le compte pas à la déconnexion : l'événement peut tourner hors du fil du serveur.
        ServerPlayConnectionEvents.JOIN.register((connexion, envoi, server) -> {
            ServerPlayer joueur = connexion.getPlayer();
            if (joueur.hasAttached(CONTROLE_EN_COURS)) {
                joueur.removeAttached(CONTROLE_EN_COURS);
                joueur.sendOverlayMessage(Component.translatable("faction.redrising.controle.fuite"));
                fuir(joueur, "s'est déconnecté pendant un contrôle");
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % PERIODE != 0) {
                return;
            }
            for (Villager pnj : new ArrayList<>(PATROUILLEURS)) {
                if (pnj.isRemoved() || !pnj.isAlive() || pnj.level().getServer() != server) {
                    oublier(pnj);
                } else if (pnj.level() instanceof ServerLevel monde) {
                    agir(monde, pnj, monde.getGameTime());
                }
            }
        });
    }

    /** Crée un PNJ Gris au premier point, qui fera la ronde. */
    public static @Nullable Villager creer(ServerLevel monde, List<BlockPos> points) {
        Ronde ronde = Ronde.de(points);
        Villager gris = PnjCouleur.creer(monde, Vec3.atBottomCenterOf(ronde.pointEnCours()), 0, Couleur.GRIS);
        if (gris != null) {
            donnerRonde(gris, ronde);
        }
        return gris;
    }

    public static void donnerRonde(Villager pnj, Ronde ronde) {
        pnj.setAttached(RONDE, ronde);
        PATROUILLEURS.add(pnj);
    }

    public static void retirerRonde(Villager pnj) {
        pnj.removeAttached(RONDE);
        pnj.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        oublier(pnj);
    }

    /** Le joueur que ce PNJ contrôle en ce moment, ou {@code null}. */
    public static @Nullable UUID suspectControle(Villager pnj) {
        EnCours enCours = CONTROLES.get(pnj.getUUID());
        return enCours == null ? null : enCours.suspect();
    }

    /** Le contrôle que mène ce Gris s'arrête sans effet (il est à terre, G6.4). */
    public static void interrompre(Villager pnj) {
        EnCours controle = CONTROLES.remove(pnj.getUUID());
        MinecraftServer server = pnj.level().getServer();
        ServerPlayer suspect = controle == null || server == null ? null : server.getPlayerList().getPlayer(controle.suspect());
        if (suspect != null) {
            suspect.removeAttached(CONTROLE_EN_COURS);
        }
    }

    private static void oublier(Villager pnj) {
        PATROUILLEURS.remove(pnj);
        EN_PAUSE.remove(pnj.getUUID());
        FOUILLES.remove(pnj.getUUID());
        // Le contrôleur disparaît (tué, retiré) en plein contrôle : le contrôle s'arrête sans effet, ce n'est pas une
        // fuite. Un suspect déjà déconnecté garde sa marque (à l'arrêt du serveur, les joueurs partent avant les PNJ).
        interrompre(pnj);
    }

    private static void agir(ServerLevel monde, Villager gris, long maintenant) {
        if (gris.hasAttached(TraqueGris.CHASSEUR) || PnjEssentiels.inconscient(gris)) {
            return; // en traque (G5.6), ou à terre (G6.4) : il reprendra sa ronde après
        }
        EnCours enCours = CONTROLES.get(gris.getUUID());
        if (enCours != null) {
            suivreControle(monde.getServer(), gris, enCours, maintenant);
            return;
        }
        ServerPlayer suspect = chercherSuspect(monde, gris, maintenant);
        if (suspect != null) {
            commencerControle(gris, suspect, maintenant);
            return;
        }
        if (!fouiller(gris, maintenant)) {
            faireLaRonde(gris, maintenant);
        }
    }

    /** Un Gris en patrouille s'est pris dans une chausse-trappe (G5.8) : il fouille autour avant de reprendre sa ronde. */
    public static void piegeDeclenche(Villager gris, BlockPos piege) {
        if (gris.hasAttached(RONDE)) {
            FOUILLES.put(gris.getUUID(), new Fouille(TraqueGris.fouilleAutour(gris.level(), piege), gris.level().getGameTime() + DUREE_FOUILLE_PIEGE));
            EN_PAUSE.remove(gris.getUUID());
        }
    }

    /** Fouille en cours autour d'une chausse-trappe ; renvoie {@code false} s'il n'y en a pas (ou plus). */
    private static boolean fouiller(Villager gris, long maintenant) {
        Fouille fouille = FOUILLES.get(gris.getUUID());
        if (fouille == null) {
            return false;
        }
        if (maintenant >= fouille.fin()) {
            FOUILLES.remove(gris.getUUID());
            return false;
        }
        Ronde ronde = fouille.ronde().atteint(gris.blockPosition()) ? fouille.ronde().suivante() : fouille.ronde();
        FOUILLES.put(gris.getUUID(), new Fouille(ronde, fouille.fin()));
        gris.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(ronde.pointEnCours(), VITESSE_FOUILLE, 1));
        return true;
    }

    /** Fin de la fouille autour d'une chausse-trappe, en secondes restantes (0 sinon) ; debug. */
    public static long fouilleRestante(Villager gris) {
        Fouille fouille = FOUILLES.get(gris.getUUID());
        return fouille == null ? 0 : Math.max(0, fouille.fin() - gris.level().getGameTime()) / 20;
    }

    private static void faireLaRonde(Villager gris, long maintenant) {
        Ronde ronde = gris.getAttached(RONDE);
        if (ronde == null) {
            return;
        }
        if (ronde.atteint(gris.blockPosition())) {
            long arrivee = EN_PAUSE.computeIfAbsent(gris.getUUID(), id -> maintenant);
            if (maintenant - arrivee >= PAUSE_AU_POINT) {
                EN_PAUSE.remove(gris.getUUID());
                ronde = ronde.suivante();
                gris.setAttached(RONDE, ronde);
            }
        }
        // Pendant la pause, la destination reste le point atteint : le PNJ ne part pas se promener
        gris.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(ronde.pointEnCours(), VITESSE_RONDE, 1));
    }

    /**
     * Un joueur au palier de contrôle, assez proche, visible, pas contrôlé récemment ni déjà par un autre Gris. Pendant
     * une alerte des patrouilles (G6.3), le Gris voit plus loin, contrôle plus tôt et plus souvent.
     */
    private static @Nullable ServerPlayer chercherSuspect(ServerLevel monde, Villager gris, long maintenant) {
        int alerte = EffetsMonde.niveau(monde.getServer(), Levier.ALERTE_PATROUILLES);
        for (ServerPlayer joueur : monde.players()) {
            if (joueur.isSpectator() || !joueur.isAlive() || !Acces.estControle(JaugesJoueur.get(joueur), alerte)
                    || gris.distanceTo(joueur) > Controle.detection(alerte)
                    || maintenant - suivi(joueur).dernierControle() < Controle.attenteEntreControles(alerte)
                    || dejaControle(joueur) || !Vue.voit(gris, joueur)) {
                continue;
            }
            return joueur;
        }
        return null;
    }

    private static boolean dejaControle(ServerPlayer joueur) {
        return CONTROLES.values().stream().anyMatch(enCours -> enCours.suspect().equals(joueur.getUUID()));
    }

    private static void commencerControle(Villager gris, ServerPlayer suspect, long maintenant) {
        CONTROLES.put(gris.getUUID(), new EnCours(suspect.getUUID(), new Controle(Controle.CONTROLE, maintenant)));
        suspect.setAttached(SUIVI_CONTROLE, new SuiviControle(maintenant, suivi(suspect).dernierJourBlanchi()));
        suspect.setAttached(CONTROLE_EN_COURS, true);
        suspect.sendOverlayMessage(Component.translatable("faction.redrising.controle.halte"));
        RedRising.LOGGER.info("Un Gris contrôle {} (Suspicion {})", suspect.getPlainTextName(), JaugesJoueur.get(suspect).suspicion());
    }

    private static void suivreControle(MinecraftServer server, Villager gris, EnCours enCours, long maintenant) {
        ServerPlayer suspect = server.getPlayerList().getPlayer(enCours.suspect());
        if (suspect == null) {
            // Déconnecté : la fuite sera comptée à son retour (CONTROLE_EN_COURS)
            CONTROLES.remove(gris.getUUID());
            return;
        }
        if (!suspect.isAlive()) {
            CONTROLES.remove(gris.getUUID());
            suspect.removeAttached(CONTROLE_EN_COURS);
            return;
        }
        // Passé dans une autre dimension (portail) : il a fui
        double distance = suspect.level() == gris.level() ? gris.distanceTo(suspect) : Double.MAX_VALUE;
        Controle.Issue issue = enCours.controle().suivre(distance, maintenant);
        if (issue != Controle.Issue.EN_COURS) {
            suspect.removeAttached(CONTROLE_EN_COURS);
        }
        switch (issue) {
            case EN_COURS -> {
                gris.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(suspect, VITESSE_APPROCHE, 1));
                gris.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(suspect, true));
            }
            case ABOUTI -> {
                CONTROLES.remove(gris.getUUID());
                obeir(suspect, maintenant);
            }
            case FUITE -> {
                CONTROLES.remove(gris.getUUID());
                suspect.sendOverlayMessage(Component.translatable("faction.redrising.controle.fuite"));
                fuir(suspect, "fuit un contrôle");
            }
            case ABANDON -> {
                CONTROLES.remove(gris.getUUID());
                RedRising.LOGGER.info("Contrôle de {} abandonné (Gris bloqué)", suspect.getPlainTextName());
            }
        }
    }

    private static void fuir(ServerPlayer suspect, String action) {
        ActionsFaction.agir(suspect, action, EffetsActions.fuiteControle());
    }

    /** Contrôle mené à bout : la Suspicion baisse, une fois par jour de jeu au plus. */
    private static void obeir(ServerPlayer suspect, long maintenant) {
        suspect.sendOverlayMessage(Component.translatable("faction.redrising.controle.fin"));
        long jour = maintenant / ActionsFaction.TICKS_PAR_JOUR;
        SuiviControle suivi = suivi(suspect);
        if (suivi.dernierJourBlanchi() == jour) {
            RedRising.LOGGER.info("{} obéit à un contrôle (déjà blanchi aujourd'hui : sans effet)", suspect.getPlainTextName());
            return;
        }
        suspect.setAttached(SUIVI_CONTROLE, new SuiviControle(suivi.dernierControle(), jour));
        ActionsFaction.agir(suspect, "obéit à un contrôle", EffetsActions.controleObei());
    }

    public static SuiviControle suivi(ServerPlayer joueur) {
        return joueur.getAttachedOrElse(SUIVI_CONTROLE, SuiviControle.AUCUN);
    }
}
