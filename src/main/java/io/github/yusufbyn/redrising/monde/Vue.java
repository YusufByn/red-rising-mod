package io.github.yusufbyn.redrising.monde;

import io.github.yusufbyn.redrising.factions.Nuage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ce que voient les PNJ (G5.8) : une seule règle, utilisée partout où un PNJ « voit » le joueur (témoins, patrouilles,
 * chasseurs, arrestation, apparition des chasseurs). Ligne de vue de Minecraft (les murs), et aucun nuage de fumée
 * entre les deux paires d'yeux ({@link Nuage}).
 *
 * <p>Les nuages vivent en mémoire du serveur, par dimension : ils durent 10 secondes, un rechargement les efface.</p>
 */
public final class Vue {
    /** Fumée affichée toutes les 4 ticks. */
    private static final int PERIODE_FUMEE = 4;

    private static final Map<ResourceKey<Level>, List<Nuage>> NUAGES = new HashMap<>();

    private Vue() {
    }

    /** Appelé depuis {@link io.github.yusufbyn.redrising.RedRising#onInitialize()}. */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            NUAGES.forEach((dimension, nuages) -> {
                ServerLevel monde = server.getLevel(dimension);
                if (monde == null) {
                    nuages.clear();
                    return;
                }
                nuages.removeIf(nuage -> nuage.fin() <= monde.getGameTime());
                if (server.getTickCount() % PERIODE_FUMEE == 0) {
                    nuages.forEach(nuage -> fumer(monde, nuage));
                }
            });
            NUAGES.values().removeIf(List::isEmpty);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> NUAGES.clear());
    }

    /** L'observateur voit-il la cible ? Murs et fumée compris. */
    public static boolean voit(LivingEntity observateur, Entity cible) {
        if (observateur.level() != cible.level() || !observateur.hasLineOfSight(cible)) {
            return false;
        }
        List<Nuage> nuages = NUAGES.get(observateur.level().dimension());
        return nuages == null || !Nuage.cache(observateur.getEyePosition(), cible.getEyePosition(), nuages);
    }

    /** Un nuage de fumée centré ici, pour {@link Nuage#DUREE}. */
    public static void enfumer(ServerLevel monde, Vec3 centre) {
        Nuage nuage = new Nuage(centre, monde.getGameTime() + Nuage.DUREE);
        NUAGES.computeIfAbsent(monde.dimension(), dimension -> new ArrayList<>()).add(nuage);
        fumer(monde, nuage);
    }

    public static List<Nuage> nuages(ServerLevel monde) {
        return List.copyOf(NUAGES.getOrDefault(monde.dimension(), List.of()));
    }

    /** Dissipe tous les nuages de ce monde (debug) ; renvoie leur nombre. */
    public static int dissiper(ServerLevel monde) {
        List<Nuage> nuages = NUAGES.remove(monde.dimension());
        return nuages == null ? 0 : nuages.size();
    }

    /** Fumée envoyée à tous les joueurs proches (visible quel que soit le réglage des particules). */
    private static void fumer(ServerLevel monde, Nuage nuage) {
        double etendue = Nuage.RAYON * 0.6;
        monde.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, false, true, nuage.centre().x, nuage.centre().y, nuage.centre().z,
                10, etendue, etendue, etendue, 0.005);
    }
}
