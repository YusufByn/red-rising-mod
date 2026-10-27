package io.github.yusufbyn.redrising.jalons;

/**
 * Quand un jalon en route arrive (G6.2), en temps de jeu depuis sa mise en route :
 * {@code délai = borné(base − pente × rythme, minimum, maximum)}. Aider le Rising (rythme positif) l'avance, le combattre
 * le recule ; les bornes l'empêchent d'arriver tout de suite comme de ne jamais arriver. Logique pure (testée).
 *
 * @param base    délai pour un rythme nul, en ticks de temps de jeu
 * @param minimum délai le plus court
 * @param maximum délai le plus long
 * @param pente   jours de jeu gagnés (ou perdus) par point de rythme (net de Soutien par joueur et par jour)
 */
public record Echeance(long base, long minimum, long maximum, double pente) {
    /** Un jour de jeu, en ticks. */
    public static final long JOUR = 24_000;

    public Echeance {
        if (minimum < 0 || minimum > maximum) {
            throw new IllegalArgumentException("Échéance : minimum " + minimum + " et maximum " + maximum + " incohérents");
        }
    }

    /** Des jours de jeu en ticks (pour déclarer : {@code jours(2.5)}). */
    public static long jours(double jours) {
        return Math.round(jours * JOUR);
    }

    /** Délai depuis la mise en route, pour ce rythme. */
    public long delai(double rythme) {
        double brut = base - pente * rythme * JOUR;
        return Math.clamp(Math.round(brut), minimum, maximum);
    }
}
