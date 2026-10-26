package io.github.yusufbyn.redrising.factions;

/**
 * Seuils d'accès aux structures des camps (G5.3, GDD section 6). Valeurs en 🟡 : à régler en jeu, toutes ici.
 */
public final class Acces {
    /**
     * Loyauté minimale pour entrer dans un poste de la Société. Égale au plafond du Rouge
     * ({@link Bornes#PLAFOND_LOYAUTE_ROUGE}) : un Rouge irréprochable entre tout juste, la moindre faute le refoule.
     */
    public static final int SEUIL_LOYAUTE_SOCIETE = 40;

    private Acces() {
    }

    public static boolean peutEntrerSociete(Jauges jauges) {
        return jauges.loyaute() >= SEUIL_LOYAUTE_SOCIETE;
    }
}
