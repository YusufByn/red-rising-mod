package io.github.yusufbyn.redrising.factions;

import java.util.Collection;

/**
 * Prix d'un article (G5.9) : prix de base × les facteurs des modificateurs, arrondi au-dessus, au moins 1, au plus une pile.
 * Le facteur porte toujours sur <b>ce que le joueur donne</b> : sous 1, l'affaire est meilleure dans les deux sens
 * (moins de crédits pour une ration, moins de charbon pour un crédit). Logique pure (testée).
 */
public final class PrixBoutique {
    private PrixBoutique() {
    }

    public static double facteur(Collection<Double> facteurs) {
        return facteurs.stream().mapToDouble(Double::doubleValue).reduce(1, (a, b) -> a * b);
    }

    public static int ajuster(int base, double facteur, int pileMax) {
        // Petite marge : 8 × 0,75 doit donner 6, pas 7 à cause des arrondis des nombres à virgule
        int prix = (int) Math.ceil(base * facteur - 1e-9);
        return Math.clamp(prix, 1, pileMax);
    }
}
