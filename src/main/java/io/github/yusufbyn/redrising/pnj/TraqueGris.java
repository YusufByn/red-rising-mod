package io.github.yusufbyn.redrising.pnj;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.factions.Acces;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Controle;
import io.github.yusufbyn.redrising.factions.EffetsActions;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.factions.Ronde;
import io.github.yusufbyn.redrising.factions.Traque;
import io.github.yusufbyn.redrising.monde.CelluleRising;
import io.github.yusufbyn.redrising.monde.PosteSociete;
import io.github.yusufbyn.redrising.monde.Vue;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
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
 * Traque (G5.6) : à 100 de Suspicion, des Gris chassent le joueur. Règles et valeurs dans {@link Traque} ;
 * ici, les chasseurs, l'arrestation et l'interrogatoire.
 *
 * <ul>
 *     <li><b>Chasseurs</b> : des PNJ Gris qui apparaissent loin du joueur, plus les patrouilles proches. Ils le poursuivent
 *     quand ils le voient, fouillent autour de sa dernière position (une {@link Ronde}) sinon, et tirent des flèches
 *     qui l'étourdissent. Ils ne paniquent pas quand on les frappe.</li>
 *     <li><b>Arrestation</b> : un chasseur au contact assez longtemps ({@link Traque#ARRESTATION}, un {@link Controle}).
 *     L'arrêté est emmené au poste de la Société le plus proche et interrogé : immobile, et il ne peut pas mourir.</li>
 *     <li>La traque et l'interrogatoire sont enregistrés sur le joueur : ils reprennent à sa reconnexion (de nouveaux
 *     chasseurs arrivent), et le temps passé hors ligne ne compte pas.</li>
 * </ul>
 */
public final class TraqueGris {
    /** Sur le joueur traqué. */
    public static final AttachmentType<Traque> TRAQUE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "traque"),
            builder -> builder.persistent(Traque.CODEC).copyOnDeath()
    );

    /** Interrogatoire après une arrestation : ticks restants, et où le joueur est tenu. */
    public record Interrogatoire(long restant, BlockPos lieu, ResourceKey<Level> dimension) {
        public static final Codec<Interrogatoire> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("restant").forGetter(Interrogatoire::restant),
                BlockPos.CODEC.fieldOf("lieu").forGetter(Interrogatoire::lieu),
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Interrogatoire::dimension)
        ).apply(instance, Interrogatoire::new));
    }

    public static final AttachmentType<Interrogatoire> INTERROGATOIRE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "interrogatoire"),
            builder -> builder.persistent(Interrogatoire.CODEC).copyOnDeath()
    );

    /** Sur un PNJ Gris : le joueur qu'il chasse. */
    public static final AttachmentType<UUID> CHASSEUR = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "chasseur"),
            builder -> builder.persistent(UUIDUtil.CODEC)
    );

    /** Sur une flèche tirée par un chasseur (pas sauvegardé). */
    private static final AttachmentType<Boolean> TIR = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "tir_etourdissant"),
            builder -> {
            }
    );

    private static final int PERIODE = 5;
    private static final float VITESSE_POURSUITE = 1.0f;
    private static final float VITESSE_FOUILLE = 0.7f;
    /** Rayon de la fouille autour de la dernière position connue. */
    private static final int RAYON_FOUILLE = 6;
    /** Les chasseurs ne tirent pas au contact (ils arrêtent). */
    private static final double DISTANCE_TIR_MIN = 4;
    private static final Identifier BONUS = Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "chasseur");

    private static final Set<Villager> CHASSEURS = new HashSet<>();
    /** Joueur → dernier tick de jeu où sa traque ou son interrogatoire a avancé (connecté). */
    private static final Map<UUID, Long> DERNIER_PASSAGE = new HashMap<>();
    private static final Map<UUID, ServerBossEvent> BARRES = new HashMap<>();
    private static final Map<UUID, BlockPos> DERNIERE_POSITION_VUE = new HashMap<>();
    private static final Map<UUID, Controle> ARRETS = new HashMap<>();
    private static final Map<UUID, Long> DERNIER_TIR = new HashMap<>();
    private static final Map<UUID, Ronde> FOUILLES = new HashMap<>();

    private TraqueGris() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()}. */
    public static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((entite, monde) -> {
            if (entite instanceof Villager pnj && pnj.hasAttached(CHASSEUR)) {
                // Chasseur d'une traque passée (rechargement) : la traque en enverra d'autres. Une patrouille reprend sa ronde.
                if (pnj.hasAttached(PatrouillePnj.RONDE)) {
                    pnj.removeAttached(CHASSEUR);
                } else {
                    pnj.discard();
                }
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entite, monde) -> {
            if (entite instanceof Villager pnj) {
                oublierChasseur(pnj);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((connexion, envoi, server) ->
                DERNIER_PASSAGE.put(connexion.getPlayer().getUUID(), server.overworld().getGameTime()));

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entite, source, degats) -> {
            if (!(entite instanceof ServerPlayer joueur)) {
                return true;
            }
            if (joueur.hasAttached(INTERROGATOIRE)) {
                return false;
            }
            if (source.getDirectEntity() instanceof AbstractArrow fleche && fleche.hasAttached(TIR)) {
                joueur.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Traque.ETOURDISSEMENT, 4));
            }
            return true;
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entite, source, degats) -> {
            if (entite instanceof ServerPlayer joueur && joueur.hasAttached(INTERROGATOIRE)) {
                joueur.setHealth(1);
                return false;
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entite, source) -> {
            if (entite instanceof Villager pnj && pnj.getAttached(CHASSEUR) instanceof UUID chasse
                    && pnj.level().getServer().getPlayerList().getPlayer(chasse) instanceof ServerPlayer joueur
                    && joueur.getAttached(TRAQUE) instanceof Traque traque) {
                joueur.setAttached(TRAQUE, traque.chasseurTue());
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Chaque tick : un chasseur frappé ne panique pas (le cerveau des villageois les ferait fuir)
            for (Villager chasseur : CHASSEURS) {
                chasseur.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
                chasseur.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            }
            if (server.getTickCount() % PERIODE == 0) {
                avancer(server);
            }
        });
    }

    // --- Déroulement ---

    private static void avancer(MinecraftServer server) {
        long maintenant = server.overworld().getGameTime();
        for (Villager chasseur : new ArrayList<>(CHASSEURS)) {
            UUID id = chasseur.getAttached(CHASSEUR);
            ServerPlayer chasse = id == null ? null : server.getPlayerList().getPlayer(id);
            if (chasseur.isRemoved() || !chasseur.isAlive() || chasseur.level().getServer() != server) {
                oublierChasseur(chasseur);
            } else if (chasse == null || !chasse.hasAttached(TRAQUE) || chasse.level() != chasseur.level()) {
                liberer(chasseur);
            }
        }
        for (ServerPlayer joueur : server.getPlayerList().getPlayers()) {
            long delta = maintenant - DERNIER_PASSAGE.getOrDefault(joueur.getUUID(), maintenant);
            DERNIER_PASSAGE.put(joueur.getUUID(), maintenant);
            if (joueur.getAttached(INTERROGATOIRE) instanceof Interrogatoire interrogatoire) {
                interroger(joueur, interrogatoire, delta);
            } else if (joueur.getAttached(TRAQUE) instanceof Traque traque) {
                if (joueur.isAlive() && !joueur.isSpectator()) {
                    suivre(joueur, traque, delta, maintenant);
                }
            } else if (joueur.isAlive() && !joueur.isSpectator() && JaugesJoueur.get(joueur).suspicion() >= Acces.SEUIL_TRAQUE) {
                commencer(joueur);
            }
        }
    }

    /** La Suspicion a atteint le maximum : la traque commence. Les patrouilles proches s'y joignent. */
    public static void commencer(ServerPlayer joueur) {
        joueur.setAttached(TRAQUE, Traque.DEBUT);
        DERNIERE_POSITION_VUE.put(joueur.getUUID(), joueur.blockPosition());
        titre(joueur, "traque.redrising.titre", "traque.redrising.sous_titre");
        joueur.connection.send(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.HOSTILE,
                joueur.getX(), joueur.getY(), joueur.getZ(), 64, 1, joueur.getRandom().nextLong()));
        double rayon = Traque.RALLIEMENT_PATROUILLES;
        for (Villager gris : joueur.level().getEntities(EntityTypes.VILLAGER, joueur.getBoundingBox().inflate(rayon),
                pnj -> pnj.hasAttached(PatrouillePnj.RONDE) && !pnj.hasAttached(CHASSEUR) && pnj.distanceTo(joueur) <= rayon)) {
            enroler(gris, joueur);
        }
        RedRising.LOGGER.info("Traque de {} : début", joueur.getPlainTextName());
    }

    private static void suivre(ServerPlayer joueur, Traque traque, long delta, long maintenant) {
        List<Villager> siens = chasseursDe(joueur);
        for (int i = siens.size(); i < traque.chasseursVoulus(); i++) {
            Villager nouveau = faireApparaitre(joueur);
            if (nouveau != null) {
                siens.add(nouveau);
            }
        }
        boolean vu = siens.stream().anyMatch(chasseur -> voit(chasseur, joueur));
        if (vu) {
            DERNIERE_POSITION_VUE.put(joueur.getUUID(), joueur.blockPosition());
        }
        traque = traque.avancer(delta, vu);
        joueur.setAttached(TRAQUE, traque);
        switch (traque.issue(vu)) {
            case GAGNEE -> finir(joueur, Traque.SUSPICION_GAGNEE, "traque.redrising.gagnee");
            case RETRAIT -> finir(joueur, Traque.SUSPICION_RETRAIT, "traque.redrising.retrait");
            case EN_COURS -> {
                montrerLaBarre(joueur, traque, vu);
                for (Villager chasseur : siens) {
                    if (chasser(chasseur, joueur, maintenant)) {
                        arreter(joueur);
                        return;
                    }
                }
            }
        }
    }

    /** Un chasseur poursuit (ou fouille), tire ; renvoie {@code true} s'il a arrêté le joueur. */
    private static boolean chasser(Villager chasseur, ServerPlayer joueur, long maintenant) {
        var cerveau = chasseur.getBrain();
        double distance = chasseur.distanceTo(joueur);
        if (voit(chasseur, joueur)) {
            FOUILLES.remove(chasseur.getUUID());
            cerveau.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(joueur, VITESSE_POURSUITE, 0));
            cerveau.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(joueur, true));
            if (distance >= DISTANCE_TIR_MIN && distance <= Traque.PORTEE_TIR
                    && maintenant - DERNIER_TIR.getOrDefault(chasseur.getUUID(), Long.MIN_VALUE / 2) >= Traque.CADENCE_TIR) {
                tirer(chasseur, joueur);
                DERNIER_TIR.put(chasseur.getUUID(), maintenant);
            }
        } else {
            BlockPos derniere = DERNIERE_POSITION_VUE.getOrDefault(joueur.getUUID(), joueur.blockPosition());
            Ronde fouille = FOUILLES.get(chasseur.getUUID());
            if (fouille == null || !fouille.points().contains(coin(chasseur.level(), derniere, -RAYON_FOUILLE, -RAYON_FOUILLE))) {
                fouille = fouilleAutour(chasseur.level(), derniere);
            } else if (fouille.atteint(chasseur.blockPosition())) {
                fouille = fouille.suivante();
            }
            FOUILLES.put(chasseur.getUUID(), fouille);
            cerveau.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(fouille.pointEnCours(), VITESSE_FOUILLE, 1));
        }
        // Pas d'arrestation à travers un mur (joueur caché dans une cellule, contre la paroi) ; au contact, la fumée ne cache rien
        Controle arret = ARRETS.computeIfAbsent(chasseur.getUUID(), id -> new Controle(Traque.ARRESTATION, maintenant));
        double distanceArret = Vue.voit(chasseur, joueur) ? distance : Double.MAX_VALUE;
        return arret.suivre(distanceArret, maintenant) == Controle.Issue.ABOUTI;
    }

    private static boolean voit(Villager chasseur, ServerPlayer joueur) {
        return chasseur.level() == joueur.level() && chasseur.distanceTo(joueur) <= Traque.VUE && Vue.voit(chasseur, joueur);
    }

    /** Fouille : les quatre coins d'un carré autour de la dernière position connue, au niveau du sol (aussi pour les patrouilles). */
    static Ronde fouilleAutour(Level monde, BlockPos centre) {
        return Ronde.de(List.of(coin(monde, centre, -RAYON_FOUILLE, -RAYON_FOUILLE), coin(monde, centre, RAYON_FOUILLE, -RAYON_FOUILLE),
                coin(monde, centre, RAYON_FOUILLE, RAYON_FOUILLE), coin(monde, centre, -RAYON_FOUILLE, RAYON_FOUILLE)));
    }

    private static BlockPos coin(Level monde, BlockPos centre, int dx, int dz) {
        int x = centre.getX() + dx;
        int z = centre.getZ() + dz;
        return new BlockPos(x, monde.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Flèche visée comme celle d'un squelette ; elle étourdit le joueur qu'elle touche (voir {@code ALLOW_DAMAGE}). */
    private static void tirer(Villager chasseur, ServerPlayer joueur) {
        ServerLevel monde = (ServerLevel) chasseur.level();
        Arrow fleche = new Arrow(monde, chasseur, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        fleche.pickup = AbstractArrow.Pickup.DISALLOWED;
        fleche.setAttached(TIR, true);
        double dx = joueur.getX() - chasseur.getX();
        double dy = joueur.getY(1 / 3.0) - fleche.getY();
        double dz = joueur.getZ() - chasseur.getZ();
        fleche.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, 1.6f, 2);
        monde.addFreshEntity(fleche);
        monde.playSound(null, chasseur.getX(), chasseur.getY(), chasseur.getZ(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1, 1);
    }

    /** Un chasseur s'est pris dans une chausse-trappe (G5.8) : un rebelle est passé là, la fouille repart de ce point. */
    public static void piegeDeclenche(Villager chasseur, BlockPos piege) {
        UUID chasse = chasseur.getAttached(CHASSEUR);
        if (chasse != null) {
            DERNIERE_POSITION_VUE.put(chasse, piege.immutable());
            FOUILLES.remove(chasseur.getUUID());
        }
    }

    // --- Chasseurs ---

    private static List<Villager> chasseursDe(ServerPlayer joueur) {
        List<Villager> siens = new ArrayList<>();
        for (Villager chasseur : CHASSEURS) {
            if (joueur.getUUID().equals(chasseur.getAttached(CHASSEUR))) {
                siens.add(chasseur);
            }
        }
        return siens;
    }

    /** Nombre de chasseurs sur ce joueur (debug, tests). */
    public static int nombreDeChasseurs(ServerPlayer joueur) {
        return chasseursDe(joueur).size();
    }

    /** Un Gris à 24-32 blocs, au sol, hors de vue du joueur si possible (dix essais). */
    private static @Nullable Villager faireApparaitre(ServerPlayer joueur) {
        ServerLevel monde = joueur.level();
        for (int essai = 0; essai < 10; essai++) {
            double angle = joueur.getRandom().nextDouble() * 2 * Math.PI;
            double rayon = Traque.APPARITION_MIN + joueur.getRandom().nextDouble() * (Traque.APPARITION_MAX - Traque.APPARITION_MIN);
            int x = (int) Math.floor(joueur.getX() + Math.cos(angle) * rayon);
            int z = (int) Math.floor(joueur.getZ() + Math.sin(angle) * rayon);
            BlockPos sol = new BlockPos(x, monde.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            Villager gris = PnjCouleur.creer(monde, Vec3.atBottomCenterOf(sol), 0, Couleur.GRIS);
            if (gris == null) {
                continue;
            }
            if (essai < 9 && Vue.voit(gris, joueur)) {
                gris.discard();
                continue;
            }
            // Un chasseur court, encaisse mieux qu'un villageois
            gris.getAttribute(Attributes.MOVEMENT_SPEED).addOrReplacePermanentModifier(new AttributeModifier(BONUS, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            gris.getAttribute(Attributes.MAX_HEALTH).addOrReplacePermanentModifier(new AttributeModifier(BONUS, 10, AttributeModifier.Operation.ADD_VALUE));
            gris.getAttribute(Attributes.ARMOR).addOrReplacePermanentModifier(new AttributeModifier(BONUS, 6, AttributeModifier.Operation.ADD_VALUE));
            gris.setHealth(gris.getMaxHealth());
            enroler(gris, joueur);
            return gris;
        }
        return null;
    }

    private static void enroler(Villager gris, ServerPlayer joueur) {
        gris.setAttached(CHASSEUR, joueur.getUUID());
        CHASSEURS.add(gris);
    }

    /** Fin de traque pour ce chasseur : une patrouille reprend sa ronde, les autres rentrent (ils disparaissent). */
    private static void liberer(Villager chasseur) {
        oublierChasseur(chasseur);
        chasseur.removeAttached(CHASSEUR);
        chasseur.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (!chasseur.hasAttached(PatrouillePnj.RONDE)) {
            chasseur.discard();
        }
    }

    private static void oublierChasseur(Villager chasseur) {
        CHASSEURS.remove(chasseur);
        ARRETS.remove(chasseur.getUUID());
        DERNIER_TIR.remove(chasseur.getUUID());
        FOUILLES.remove(chasseur.getUUID());
    }

    // --- Fin de traque ---

    /** Fin de la traque : chasseurs libérés, barre retirée, Suspicion fixée. */
    public static void finir(ServerPlayer joueur, int suspicion, String message) {
        terminerLaTraque(joueur);
        JaugesJoueur.set(joueur, Jauge.SUSPICION, suspicion);
        joueur.sendOverlayMessage(Component.translatable(message));
        RedRising.LOGGER.info("Traque de {} : fin ({}), Suspicion {}", joueur.getPlainTextName(), message, suspicion);
    }

    private static void terminerLaTraque(ServerPlayer joueur) {
        joueur.removeAttached(TRAQUE);
        ServerBossEvent barre = BARRES.remove(joueur.getUUID());
        if (barre != null) {
            barre.removeAllPlayers();
        }
        chasseursDe(joueur).forEach(TraqueGris::liberer);
        DERNIERE_POSITION_VUE.remove(joueur.getUUID());
    }

    /**
     * Le joueur traqué entre dans une cellule du Rising ({@code CelluleRising}). Sans être vu : la traque est gagnée.
     * Sous les yeux d'un chasseur : la cellule est repérée (descente un jour plus tard, et Soutien −10 à ce moment : G5.10), et la traque continue.
     */
    public static void entreDansCellule(ServerPlayer joueur, CelluleRising.Cellule cellule) {
        if (!joueur.hasAttached(TRAQUE)) {
            return;
        }
        if (chasseursDe(joueur).stream().anyMatch(chasseur -> voit(chasseur, joueur))) {
            // Descente de la Société un jour de jeu plus tard ; le joueur perdra son Soutien à ce moment (G5.10)
            CelluleRising.marquerReperee(joueur.level().getServer(), cellule, joueur);
            joueur.sendOverlayMessage(Component.translatable("traque.redrising.reperee"));
            RedRising.LOGGER.info("Traque de {} : vu en entrant dans une cellule, cellule repérée", joueur.getPlainTextName());
        } else {
            finir(joueur, Traque.SUSPICION_GAGNEE, "traque.redrising.cachee");
        }
    }

    /** Arrestation : Loyauté perdue, emmené au poste le plus proche (sinon sur place), interrogatoire. */
    public static void arreter(ServerPlayer joueur) {
        terminerLaTraque(joueur);
        ActionsFaction.agir(joueur, "arrêté au bout d'une traque", EffetsActions.arrestation());
        ServerLevel lieuMonde = joueur.level();
        BlockPos lieu = joueur.blockPosition();
        PosteSociete.Poste poste = posteLePlusProche(joueur);
        if (poste != null) {
            lieuMonde = joueur.level().getServer().overworld();
            lieu = poste.sortie();
        }
        joueur.setAttached(INTERROGATOIRE, new Interrogatoire(Traque.DUREE_INTERROGATOIRE, lieu, lieuMonde.dimension()));
        tenir(joueur, lieuMonde, lieu);
        titre(joueur, "traque.redrising.arrestation", "traque.redrising.interrogatoire");
        RedRising.LOGGER.info("Traque de {} : arrêté, interrogé en {}", joueur.getPlainTextName(), lieu.toShortString());
    }

    private static PosteSociete.@Nullable Poste posteLePlusProche(ServerPlayer joueur) {
        if (joueur.level().dimension() != Level.OVERWORLD) {
            return null;
        }
        PosteSociete.Poste meilleur = null;
        double meilleureDistance = Traque.RAYON_POSTE * Traque.RAYON_POSTE;
        for (PosteSociete.Poste poste : PosteSociete.postes(joueur.level().getServer())) {
            double distance = joueur.blockPosition().distSqr(poste.sortie());
            if (distance <= meilleureDistance) {
                meilleur = poste;
                meilleureDistance = distance;
            }
        }
        return meilleur;
    }

    /** Pendant l'interrogatoire : tenu sur place, ralenti et aveuglé ; à la fin, relâché avec la Suspicion fixée. */
    private static void interroger(ServerPlayer joueur, Interrogatoire interrogatoire, long delta) {
        long restant = interrogatoire.restant() - delta;
        if (restant <= 0) {
            joueur.removeAttached(INTERROGATOIRE);
            joueur.removeEffect(MobEffects.SLOWNESS);
            joueur.removeEffect(MobEffects.BLINDNESS);
            JaugesJoueur.set(joueur, Jauge.SUSPICION, Traque.SUSPICION_ARRETE);
            joueur.sendOverlayMessage(Component.translatable("traque.redrising.relache"));
            RedRising.LOGGER.info("{} relâché après l'interrogatoire, Suspicion {}", joueur.getPlainTextName(), Traque.SUSPICION_ARRETE);
            return;
        }
        joueur.setAttached(INTERROGATOIRE, new Interrogatoire(restant, interrogatoire.lieu(), interrogatoire.dimension()));
        ServerLevel monde = joueur.level().getServer().getLevel(interrogatoire.dimension());
        if (monde == null) {
            monde = joueur.level();
        }
        if (joueur.level() != monde || joueur.blockPosition().distSqr(interrogatoire.lieu()) > 1) {
            tenir(joueur, monde, interrogatoire.lieu());
        }
        joueur.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PERIODE * 3, 6));
        joueur.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
    }

    private static void tenir(ServerPlayer joueur, ServerLevel monde, BlockPos lieu) {
        joueur.teleport(new TeleportTransition(monde, Vec3.atBottomCenterOf(lieu), Vec3.ZERO, joueur.getYRot(), 0, TeleportTransition.DO_NOTHING));
    }

    /** Fin d'une traque ou d'un interrogatoire sans effet (debug). */
    public static void annuler(ServerPlayer joueur) {
        terminerLaTraque(joueur);
        joueur.removeAttached(INTERROGATOIRE);
    }

    // --- Affichage ---

    private static void titre(ServerPlayer joueur, String titre, String sousTitre) {
        joueur.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        joueur.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(sousTitre)));
        joueur.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(titre)));
    }

    /** Barre de boss : rouge quand un chasseur voit le joueur, jaune sinon ; elle se remplit vers la victoire. */
    private static void montrerLaBarre(ServerPlayer joueur, Traque traque, boolean vu) {
        ServerBossEvent barre = BARRES.computeIfAbsent(joueur.getUUID(), id -> new ServerBossEvent(UUID.randomUUID(),
                Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
        if (!barre.getPlayers().contains(joueur)) {
            // Reconnecté : c'est un nouvel objet joueur
            barre.removeAllPlayers();
            barre.addPlayer(joueur);
        }
        barre.setName(Component.translatable(vu ? "traque.redrising.barre.vu" : "traque.redrising.barre.cache"));
        barre.setColor(vu ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
        barre.setProgress(traque.progression());
    }
}
