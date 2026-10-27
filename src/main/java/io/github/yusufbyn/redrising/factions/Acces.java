package io.github.yusufbyn.redrising.factions;

/**
 * Seuils d'accès aux structures des camps (G5.3, G5.4) et paliers de Suspicion (G5.5), GDD section 6. Valeurs en 🟡 : à régler en jeu, toutes ici.
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
    /** 🟡 Soutien pour obtenir un fumigène du Rising (G5.7) ; lu par la boutique (G5.9). Le seuil vaut pour l'obtention, pas l'usage. */
    public static final int SEUIL_FUMIGENE = 20;
    /** 🟡 Soutien pour obtenir des chausse-trappes du Rising (G5.7) ; lu par la boutique (G5.9). */
    public static final int SEUIL_CHAUSSE_TRAPPE = 40;
    /** 🟡 Loyauté pour acheter l'équipement de la Société (outils et armure de fer, G5.9). */
    public static final int SEUIL_EQUIPEMENT_SOCIETE = 60;
    /** Suspicion à partir de laquelle les patrouilles de Gris contrôlent le joueur. */
    public static final int SEUIL_CONTROLE = 40;
    /** Suspicion au maximum : la traque (G5.6) remplace les contrôles. */
    public static final int SEUIL_TRAQUE = Bornes.MAX;

    private Acces() {
    }

    public static boolean peutEntrerSociete(Jauges jauges) {
        return jauges.loyaute() >= SEUIL_LOYAUTE_SOCIETE;
    }

    public static boolean peutEntrerRising(Jauges jauges) {
        return jauges.soutien() >= SEUIL_SOUTIEN_RISING;
    }

    /** Palier de contrôle : de {@link #SEUIL_CONTROLE} à juste sous {@link #SEUIL_TRAQUE}. */
    public static boolean estControle(Jauges jauges) {
        return jauges.suspicion() >= SEUIL_CONTROLE && jauges.suspicion() < SEUIL_TRAQUE;
    }
}
