package io.github.yusufbyn.redrising.factions;

import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * Nuage de fumée d'un fumigène (G5.8) : une sphère qui coupe la vue des PNJ au travers, jusqu'au tick {@code fin}.
 * Logique pure (testée) ; les nuages du monde sont tenus par {@code monde/Vue}.
 *
 * <p>Au contact, la fumée ne cache rien : un chasseur collé au joueur dans le nuage le tient toujours (l'arrestation
 * ne change pas), et quelqu'un à un pas voit le vol. La fumée sert à fuir, pas à rester au contact sans être pris.</p>
 */
public record Nuage(Vec3 centre, long fin) {
    /** 🟡 Rayon du nuage, en blocs. */
    public static final double RAYON = 2.5;
    /** 🟡 Durée du nuage : 10 secondes. */
    public static final long DUREE = 200;
    /** 🟡 En deçà de cette distance (celle du contact d'une arrestation), la fumée ne coupe pas la vue. */
    public static final double VUE_AU_CONTACT = 2.5;

    /** Le segment {@code a → b} passe-t-il dans le nuage ? (point du segment le plus proche du centre) */
    public boolean traverse(Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double longueur2 = ab.lengthSqr();
        double t = longueur2 == 0 ? 0 : Math.clamp(centre.subtract(a).dot(ab) / longueur2, 0, 1);
        return a.add(ab.scale(t)).distanceToSqr(centre) <= RAYON * RAYON;
    }

    /** Vrai si au moins un nuage cache {@code b} à {@code a} (yeux contre yeux), sauf au contact. */
    public static boolean cache(Vec3 a, Vec3 b, Collection<Nuage> nuages) {
        if (a.distanceTo(b) <= VUE_AU_CONTACT) {
            return false;
        }
        return nuages.stream().anyMatch(nuage -> nuage.traverse(a, b));
    }
}
