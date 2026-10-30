package io.github.yusufbyn.redrising.monde;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * La zone du puits aplanie (G7.2.5, GDD section 7) : la mine reste à {@value PlanMars#DISTANCE_MAX_MINE} blocs au plus du point
 * d'apparition ; sans place plate à cette distance, on aplanit le terrain autour du puits au lieu de s'éloigner. Logique pure :
 * déduite du centre et du relief, donc rien à sauvegarder dans le plan des régions. La pose est dans {@link MineLykos}.
 *
 * @param centre le puits principal
 * @param sol    la hauteur du terrain sur la partie plate (même convention que {@link PlanMars.Terrain#hauteur})
 */
public record AplanissementPuits(BlockPos centre, int sol) {
    /** 🟡 Rayon de la partie plate autour du puits. */
    public static final int RAYON_PLAT = 8;
    /** 🟡 Au-delà de ce rayon, le terrain n'est plus touché ; entre les deux, il rejoint le relief en pente douce. */
    public static final int RAYON_RACCORD = 40;
    /** La partie plate reste au-dessus de la mer (la hauteur juste au-dessus de son niveau, 63). */
    static final int SOL_MIN = 64;
    private static final int PAS = 4;

    /** La colonne du puits (3 × 3) dépasse-t-elle l'écart permis ? Le bouchon se cale sur son point le plus bas : 3 à 5 blocs sous toute la colonne. */
    public static boolean surUnePente(BlockPos centre, IntBinaryOperator terrain) {
        int bas = Integer.MAX_VALUE;
        int haut = Integer.MIN_VALUE;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                int hauteur = terrain.applyAsInt(centre.getX() + x, centre.getZ() + z);
                bas = Math.min(bas, hauteur);
                haut = Math.max(haut, hauteur);
            }
        }
        return haut - bas > PlanMars.PENTE_MAX_PUITS;
    }

    /** L'aplanissement de ce puits sur ce relief, ou {@code null} s'il est déjà assez plat. */
    public static @Nullable AplanissementPuits de(BlockPos centre, IntBinaryOperator terrain) {
        if (!surUnePente(centre, terrain)) {
            return null;
        }
        // La médiane du relief sous la partie plate : le moins de terre à déplacer, à un rocher près
        List<Integer> hauteurs = new ArrayList<>();
        for (int x = -RAYON_PLAT; x <= RAYON_PLAT; x += PAS) {
            for (int z = -RAYON_PLAT; z <= RAYON_PLAT; z += PAS) {
                hauteurs.add(terrain.applyAsInt(centre.getX() + x, centre.getZ() + z));
            }
        }
        hauteurs.sort(Integer::compare);
        return new AplanissementPuits(centre.immutable(), Math.max(SOL_MIN, hauteurs.get(hauteurs.size() / 2)));
    }

    /** La plus grande hauteur déplacée sur toute la zone (0 : rien à faire), pour garder la place qui demande le moins de travaux. */
    public int travaux(IntBinaryOperator terrain) {
        int travaux = 0;
        for (int x = -RAYON_RACCORD; x <= RAYON_RACCORD; x += 8) {
            for (int z = -RAYON_RACCORD; z <= RAYON_RACCORD; z += 8) {
                int cx = centre.getX() + x;
                int cz = centre.getZ() + z;
                int hauteur = terrain.applyAsInt(cx, cz);
                travaux = Math.max(travaux, Math.abs(hauteur(cx, cz, hauteur) - hauteur));
            }
        }
        return travaux;
    }

    /** La hauteur du terrain une fois aplani sur cette colonne, d'après sa hauteur naturelle. */
    public int hauteur(int x, int z, int naturelle) {
        double distance = Math.hypot(x - centre.getX(), z - centre.getZ());
        if (distance >= RAYON_RACCORD) {
            return naturelle;
        }
        double t = Math.clamp((distance - RAYON_PLAT) / (RAYON_RACCORD - RAYON_PLAT), 0.0, 1.0);
        double part = t * t * (3 - 2 * t);
        return (int) Math.round(sol + (naturelle - sol) * part);
    }

    /** Le relief une fois aplani ({@code aplanissement} {@code null} : le relief lui-même). */
    public static IntBinaryOperator relief(@Nullable AplanissementPuits aplanissement, IntBinaryOperator naturel) {
        return aplanissement == null ? naturel : (x, z) -> aplanissement.hauteur(x, z, naturel.applyAsInt(x, z));
    }
}
