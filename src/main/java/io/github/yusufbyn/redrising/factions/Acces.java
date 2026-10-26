package io.github.yusufbyn.redrising.factions;

/**
 * Seuils d'accès aux structures des camps (G5.3, G5.4, GDD section 6). Valeurs en 🟡 : à régler en jeu, toutes ici.
 */
public final class Acces {
    /**
     * Loyauté minimale pour entrer dans un poste de la Société. Égale au plafond du Rouge
     * ({@link Bornes#PLAFOND_LOYAUTE_ROUGE}) : un Rouge irréprochable entre tout juste, la moindre faute le refoule.
     */
    public static final int SEUIL_LOYAUTE_SOCIETE = 40;
    /**
     * Soutien minimal pour entrer dans une cellule du Rising. Atteignable sans tuer : 20 dons de nourriture au peuple
     * (+1 chacun), ou un mélange de dons et de vols à la Société (+2 chacun).
     */
    public static final int SEUIL_SOUTIEN_RISING = 20;

    private Acces() {
    }

    public static boolean peutEntrerSociete(Jauges jauges) {
        return jauges.loyaute() >= SEUIL_LOYAUTE_SOCIETE;
    }

    public static boolean peutEntrerRising(Jauges jauges) {
        return jauges.soutien() >= SEUIL_SOUTIEN_RISING;
    }
}
