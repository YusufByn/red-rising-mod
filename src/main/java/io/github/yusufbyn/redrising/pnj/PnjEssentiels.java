package io.github.yusufbyn.redrising.pnj;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.jalons.Attitude;
import io.github.yusufbyn.redrising.jalons.HorlogeMonde;
import io.github.yusufbyn.redrising.jalons.MortPersonnage;
import io.github.yusufbyn.redrising.jalons.Personnage;
import io.github.yusufbyn.redrising.jalons.Personnages;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Les personnages canon incarnés par des PNJ (G6.4) : essentiels avant leur jalon, tuables après (GDD section 8).
 *
 * <ul>
 *     <li><b>Inconscient au lieu de mourir</b> (essentiel) : couché, immobile, invulnérable et témoin de rien pendant
 *     {@link Personnages#DUREE_INCONSCIENCE} ; il se relève avec la moitié de sa vie. Une mort sans attaquant (lave,
 *     vide, noyade, {@code /kill}...) le ramène d'abord à son foyer. Celui qui l'a mis à terre paie un K.-O.
 *     ({@code EffetsActions.ko} : jamais de Soutien).</li>
 *     <li><b>Conversions</b> : la foudre ne change pas un essentiel en sorcière ({@code VillagerMixin}) ; un zombie ne le
 *     convertit pas, puisque sa mort est annulée. Après son jalon, une conversion compte comme sa mort, et le converti
 *     ne l'incarne plus (sinon on guérirait un personnage mort).</li>
 *     <li><b>Un personnage, un seul PNJ vivant</b> ({@link #PORTEURS}). Un personnage mort ne se redonne pas.</li>
 *     <li>La mort d'un personnage est enregistrée dans l'état du monde, pour le {@code Bilan} des jalons suivants.</li>
 *     <li><b>Cellule qui tombe</b> : un essentiel n'est pas arrêté. Son foyer hors de la cellule : il y est renvoyé ;
 *     dans la cellule : il est retiré du monde sans être tué, et son personnage est « en fuite » (dans l'état du monde,
 *     jusqu'à ce qu'on le redonne à un PNJ). Il doit réapparaître plus tard (G7.5, G15.2).</li>
 * </ul>
 */
public final class PnjEssentiels {
    /** Sur le PNJ : le personnage canon qu'il incarne. Pas copié à une conversion : le converti n'est plus lui. */
    public static final AttachmentType<String> PERSONNAGE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "personnage"),
            builder -> builder.persistent(Codec.STRING)
    );

    /** Sur le PNJ : là où il a reçu son personnage. Un essentiel mort hors combat y est ramené. */
    public static final AttachmentType<GlobalPos> FOYER = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "foyer"),
            builder -> builder.persistent(GlobalPos.CODEC)
    );

    /**
     * Sur le PNJ inconscient (sauvegardé : un rechargement ne le réveille pas avant l'heure).
     *
     * @param reveil     tick de jeu (horloge de l'Overworld) du réveil
     * @param sansIaAvant s'il était déjà sans IA : on le lui rend au réveil
     */
    public record Inconscience(long reveil, boolean sansIaAvant) {
        public static final Codec<Inconscience> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("reveil").forGetter(Inconscience::reveil),
                Codec.BOOL.fieldOf("sans_ia_avant").forGetter(Inconscience::sansIaAvant)
        ).apply(instance, Inconscience::new));
    }

    public static final AttachmentType<Inconscience> INCONSCIENT = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "inconscient"),
            builder -> builder.persistent(Inconscience.CODEC)
    );

    /** Global : le PNJ qui incarne chaque personnage. Un autre PNJ qui le porterait encore le perd à son chargement. */
    private static final AttachmentType<Map<String, UUID>> PORTEURS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "porteurs_personnages"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, UUIDUtil.STRING_CODEC))
    );

    /**
     * Global : le foyer de chaque personnage incarné (G6.5). Une cellule qui tombe doit savoir qui y avait son foyer, même
     * si son PNJ est ailleurs ou pas chargé.
     */
    private static final AttachmentType<Map<String, GlobalPos>> FOYERS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "foyers_personnages"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, GlobalPos.CODEC))
    );

    private static final int PERIODE = 10;
    /** Inconscients chargés. */
    private static final Set<Villager> A_TERRE = new HashSet<>();
    /** PNJ que {@code /rr personnage tuer} laisse vraiment mourir. */
    private static final Set<UUID> FORCES = new HashSet<>();

    private PnjEssentiels() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()}. */
    public static void init() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entite, source, degats) -> {
            if (entite instanceof Villager pnj && !FORCES.contains(pnj.getUUID()) && essentiel(pnj)) {
                tomberInconscient(pnj, source);
                return false;
            }
            return true;
        });
        // Inconscient : invulnérable
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entite, source, degats) ->
                !entite.hasAttached(INCONSCIENT) || FORCES.contains(entite.getUUID()));
        ServerLivingEntityEvents.AFTER_DEATH.register((entite, source) -> {
            if (entite instanceof Villager pnj && pnj.getAttached(PERSONNAGE) instanceof String personnage) {
                ServerPlayer joueur = source.getEntity() instanceof ServerPlayer tueur ? tueur : null;
                enregistrerMort(pnj.level().getServer(), personnage, joueur, "mort (" + source.getMsgId() + ")");
            }
        });
        // Zombie, foudre : le converti n'incarne plus le personnage, et la conversion compte comme sa mort
        ServerLivingEntityEvents.MOB_CONVERSION.register((ancien, converti, parametres) -> {
            if (ancien.getAttached(PERSONNAGE) instanceof String personnage) {
                enregistrerMort(ancien.level().getServer(), personnage, null, "converti en " + converti.getType().getDescriptionId());
                converti.removeAttached(PERSONNAGE);
                converti.removeAttached(FOYER);
                converti.removeAttached(INCONSCIENT);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entite, monde) -> {
            if (!(entite instanceof Villager pnj)) {
                return;
            }
            String personnage = pnj.getAttached(PERSONNAGE);
            UUID porteur = personnage == null ? null : porteurs(monde.getServer()).get(personnage);
            if (personnage != null && porteur != null && !porteur.equals(pnj.getUUID())) {
                RedRising.LOGGER.info("PNJ {} : n'incarne plus {} (donné à un autre PNJ)", pnj.getUUID(), personnage);
                oublierPersonnage(pnj);
            }
            if (pnj.hasAttached(INCONSCIENT)) {
                // La pose n'est pas sauvegardée : on recouche le PNJ (ou il se réveille au prochain passage)
                pnj.setPose(Pose.SLEEPING);
                A_TERRE.add(pnj);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entite, monde) -> {
            if (!(entite instanceof Villager pnj)) {
                return;
            }
            A_TERRE.remove(pnj);
            // Mort, converti, retiré : le personnage n'a plus de PNJ
            Entity.RemovalReason raison = pnj.getRemovalReason();
            String personnage = pnj.getAttached(PERSONNAGE);
            if (raison != null && raison.shouldDestroy() && personnage != null && pnj.getUUID().equals(porteurs(monde.getServer()).get(personnage))) {
                modifierPorteurs(monde.getServer(), porteurs -> porteurs.remove(personnage));
                modifierFoyers(monde.getServer(), foyers -> foyers.remove(personnage));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % PERIODE != 0) {
                return;
            }
            long maintenant = server.overworld().getGameTime();
            for (Villager pnj : Set.copyOf(A_TERRE)) {
                Inconscience inconscience = pnj.getAttached(INCONSCIENT);
                if (pnj.isRemoved() || inconscience == null) {
                    A_TERRE.remove(pnj);
                } else if (maintenant >= inconscience.reveil()) {
                    reveiller(pnj);
                } else if (server.getTickCount() % (PERIODE * 2) == 0 && pnj.level() instanceof ServerLevel monde) {
                    monde.sendParticles(ParticleTypes.CRIT, pnj.getX(), pnj.getY() + 0.6, pnj.getZ(), 3, 0.3, 0.1, 0.3, 0);
                }
            }
        });
    }

    /** Ce PNJ incarne un personnage essentiel (déclaré, avec un jalon qui n'a pas encore d'issue). */
    public static boolean essentiel(Villager pnj) {
        String id = pnj.getAttached(PERSONNAGE);
        Personnage personnage = id == null ? null : Personnages.get(id);
        MinecraftServer server = pnj.level().getServer();
        return personnage != null && server != null && Personnages.essentiel(personnage, EtatMondeServeur.get(server));
    }

    public static boolean inconscient(Entity entite) {
        return entite.hasAttached(INCONSCIENT);
    }

    // --- Inconscience ---

    /** Sa mort est annulée : ramené à son foyer s'il n'y a pas d'attaquant, puis à terre ; l'attaquant paie un K.-O. */
    private static void tomberInconscient(Villager pnj, DamageSource source) {
        pnj.setHealth(1);
        pnj.clearFire();
        pnj.setAirSupply(pnj.getMaxAirSupply());
        pnj.resetFallDistance();
        if (source.getEntity() == null) {
            ramenerAuFoyer(pnj);
        }
        if (!inconscient(pnj)) {
            mettreATerre(pnj);
        }
        RedRising.LOGGER.info("{} ({}) à terre, inconscient ({})", pnj.getAttached(PERSONNAGE), pnj.getUUID(), source.getMsgId());
        if (source.getEntity() instanceof ServerPlayer joueur) {
            joueur.sendOverlayMessage(Component.translatable("personnage.redrising.a_terre", pnj.getDisplayName()));
            ActionsFaction.mettreKo(joueur, pnj);
            AttitudesPnj.changer(joueur, pnj, Attitude.KO, "l'a mis à terre");
        }
    }

    /** Couché, immobile, pour {@link Personnages#DUREE_INCONSCIENCE}. Un contrôle qu'il menait s'arrête. */
    public static void mettreATerre(Villager pnj) {
        long reveil = pnj.level().getServer().overworld().getGameTime() + Personnages.DUREE_INCONSCIENCE;
        pnj.setAttached(INCONSCIENT, new Inconscience(reveil, pnj.isNoAi()));
        pnj.setNoAi(true);
        pnj.getNavigation().stop();
        pnj.setDeltaMovement(Vec3.ZERO);
        pnj.setPose(Pose.SLEEPING);
        PatrouillePnj.interrompre(pnj);
        A_TERRE.add(pnj);
    }

    /** Il se relève avec la moitié de sa vie, et reprend ce qu'il faisait. */
    public static void reveiller(Villager pnj) {
        Inconscience inconscience = pnj.getAttached(INCONSCIENT);
        A_TERRE.remove(pnj);
        if (inconscience == null) {
            return;
        }
        pnj.removeAttached(INCONSCIENT);
        pnj.setNoAi(inconscience.sansIaAvant());
        pnj.setPose(Pose.STANDING);
        pnj.setHealth(pnj.getMaxHealth() / 2);
        RedRising.LOGGER.info("{} ({}) se relève", pnj.getAttached(PERSONNAGE), pnj.getUUID());
    }

    private static void ramenerAuFoyer(Villager pnj) {
        GlobalPos foyer = pnj.getAttached(FOYER);
        if (foyer == null || !foyer.dimension().equals(pnj.level().dimension())) {
            return;
        }
        Vec3 position = Vec3.atBottomCenterOf(foyer.pos());
        pnj.teleportTo(position.x, position.y, position.z);
        pnj.setDeltaMovement(Vec3.ZERO);
    }

    // --- Cellule qui tombe ---

    /**
     * Ce PNJ essentiel était dans une cellule qui tombe (G6.4) : renvoyé à son foyer s'il est hors de la cellule, sinon
     * retiré du monde sans être tué, son personnage en fuite.
     */
    public static void echapper(Villager pnj, BoundingBox cellule) {
        String personnage = pnj.getAttached(PERSONNAGE);
        GlobalPos foyer = pnj.getAttached(FOYER);
        if (personnage == null) {
            return;
        }
        if (inconscient(pnj)) {
            reveiller(pnj);
        }
        if (foyer != null && foyer.dimension().equals(pnj.level().dimension()) && !cellule.isInside(foyer.pos())) {
            ramenerAuFoyer(pnj);
            RedRising.LOGGER.info("Personnage {} : s'échappe de la cellule vers son foyer", personnage);
            return;
        }
        MinecraftServer server = pnj.level().getServer();
        long temps = HorlogeMonde.get(server).temps();
        EtatMondeServeur.modifier(server, etat -> etat.avecFuite(personnage, temps));
        liberer(pnj);
        pnj.discard();
        RedRising.LOGGER.info("Personnage {} : en fuite (sa cellule est tombée)", personnage);
    }

    /**
     * Les personnages touchés par la chute de cette cellule (G6.5) : ceux dont le PNJ est dedans, et ceux qui y avaient
     * leur foyer. À demander avant la chute : un essentiel qui s'échappe perd son foyer.
     */
    public static Set<String> personnagesDe(ServerLevel monde, BoundingBox cellule) {
        Set<String> touches = new HashSet<>();
        for (Villager pnj : monde.getEntities(EntityTypes.VILLAGER, AABB.of(cellule).inflate(0.5), pnj -> pnj.hasAttached(PERSONNAGE))) {
            touches.add(pnj.getAttached(PERSONNAGE));
        }
        foyers(monde.getServer()).forEach((personnage, foyer) -> {
            if (foyer.dimension().equals(monde.dimension()) && cellule.isInside(foyer.pos())) {
                touches.add(personnage);
            }
        });
        return touches;
    }

    // --- Morts ---

    private static void enregistrerMort(MinecraftServer server, String personnage, @Nullable ServerPlayer joueur, String cause) {
        if (EtatMondeServeur.get(server).morts().containsKey(personnage)) {
            return;
        }
        MortPersonnage mort = new MortPersonnage(HorlogeMonde.get(server).temps(), Optional.ofNullable(joueur).map(Entity::getUUID));
        EtatMondeServeur.modifier(server, etat -> etat.avecMort(personnage, mort));
        RedRising.LOGGER.info("Personnage {} : {}{}", personnage, cause, joueur == null ? "" : ", tué par " + joueur.getPlainTextName());
    }

    /** Debug : le laisse vraiment mourir, même essentiel ou inconscient. */
    public static void tuer(Villager pnj, ServerLevel monde) {
        FORCES.add(pnj.getUUID());
        try {
            pnj.hurtServer(monde, pnj.damageSources().genericKill(), Float.MAX_VALUE);
        } finally {
            FORCES.remove(pnj.getUUID());
        }
    }

    // --- Un personnage, un PNJ ---

    public enum Incarnation {
        /** Donné ; s'il était porté par un autre PNJ, celui-ci le perd. */
        DONNE,
        /** Personnage mort : refusé (sauf après {@code /rr personnage oublier}). */
        MORT,
        /** Personnage pas déclaré. */
        INCONNU
    }

    /**
     * Ce PNJ incarne désormais ce personnage, et a son foyer ici. L'ancien porteur le perd : tout de suite s'il est
     * chargé, sinon à son prochain chargement. {@code ancien} reçoit l'identifiant de l'ancien porteur, s'il y en avait un.
     */
    public static Incarnation incarner(Villager pnj, String personnage, Set<UUID> ancien) {
        MinecraftServer server = pnj.level().getServer();
        if (Personnages.get(personnage) == null) {
            return Incarnation.INCONNU;
        }
        if (EtatMondeServeur.get(server).morts().containsKey(personnage)) {
            return Incarnation.MORT;
        }
        UUID porteur = porteurs(server).get(personnage);
        if (porteur != null && !porteur.equals(pnj.getUUID())) {
            ancien.add(porteur);
            for (ServerLevel monde : server.getAllLevels()) {
                if (monde.getEntity(porteur) instanceof Villager autre) {
                    oublierPersonnage(autre);
                }
            }
        }
        liberer(pnj);
        modifierPorteurs(server, porteurs -> porteurs.put(personnage, pnj.getUUID()));
        GlobalPos foyer = GlobalPos.of(pnj.level().dimension(), pnj.blockPosition());
        modifierFoyers(server, foyers -> foyers.put(personnage, foyer));
        // Redonné à un PNJ : il n'est plus en fuite
        EtatMondeServeur.modifier(server, etat -> etat.sansFuite(personnage));
        pnj.setAttached(PERSONNAGE, personnage);
        pnj.setAttached(FOYER, foyer);
        return Incarnation.DONNE;
    }

    /** Ce PNJ n'incarne plus personne. */
    public static void liberer(Villager pnj) {
        String personnage = pnj.getAttached(PERSONNAGE);
        if (personnage != null && pnj.getUUID().equals(porteurs(pnj.level().getServer()).get(personnage))) {
            modifierPorteurs(pnj.level().getServer(), porteurs -> porteurs.remove(personnage));
            modifierFoyers(pnj.level().getServer(), foyers -> foyers.remove(personnage));
        }
        oublierPersonnage(pnj);
    }

    private static void oublierPersonnage(Villager pnj) {
        if (inconscient(pnj)) {
            reveiller(pnj);
        }
        pnj.removeAttached(PERSONNAGE);
        pnj.removeAttached(FOYER);
    }

    public static Map<String, UUID> porteurs(MinecraftServer server) {
        return server.globalAttachments().getAttachedOrElse(PORTEURS, Map.of());
    }

    private static Map<String, GlobalPos> foyers(MinecraftServer server) {
        return server.globalAttachments().getAttachedOrElse(FOYERS, Map.of());
    }

    private static void modifierFoyers(MinecraftServer server, Consumer<Map<String, GlobalPos>> modification) {
        Map<String, GlobalPos> copie = new HashMap<>(foyers(server));
        modification.accept(copie);
        server.globalAttachments().setAttached(FOYERS, copie);
    }

    private static void modifierPorteurs(MinecraftServer server, Consumer<Map<String, UUID>> modification) {
        Map<String, UUID> copie = new HashMap<>(porteurs(server));
        modification.accept(copie);
        server.globalAttachments().setAttached(PORTEURS, copie);
    }
}
