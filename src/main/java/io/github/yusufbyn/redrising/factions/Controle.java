package io.github.yusufbyn.redrising.factions;

/**
 * Un contrôle d'identité (G5.5), du « Halte ! Contrôle. » jusqu'à son issue. Logique pure, testée par {@code ControleTest} :
 * elle ne sait pas qui contrôle (un PNJ Gris aujourd'hui, le joueur Gris en G8.3).
 *
 * <p>Le contrôleur vient au suspect, puis le contrôle dure {@value #DUREE} ticks. Le suspect fuit s'il s'éloigne à plus
 * de {@value #DISTANCE_FUITE} blocs, dès le « Halte », approche comprise. Valeurs en 🟡, toutes ici
 * (le seuil de Suspicion est dans {@link Acces#SEUIL_CONTROLE}).</p>
 */
public final class Controle {
    /** Un Gris remarque un suspect à cette distance (et s'il le voit). */
    public static final double DETECTION = 12;
    /** Au-delà, le suspect a fui. Plus grand que {@link #DETECTION} : on n'est pas en fuite dès le « Halte ». */
    public static final double DISTANCE_FUITE = 16;
    /** Le contrôleur est arrivé : le contrôle commence. */
    public static final double CONTACT = 2.5;
    /** Durée du contrôle une fois le contrôleur arrivé (5 secondes). */
    public static final int DUREE = 100;
    /** Le contrôleur n'arrive pas (chemin bloqué) : il abandonne au bout de 20 secondes, sans effet. */
    public static final int APPROCHE_MAX = 400;
    /** Un joueur n'est pas contrôlé plus d'une fois toutes les 5 minutes de jeu. */
    public static final int ATTENTE_ENTRE_CONTROLES = 6000;

    public enum Issue {
        EN_COURS,
        /** Le suspect est resté jusqu'au bout. */
        OBEI,
        FUITE,
        /** Le contrôleur n'a pas pu arriver. */
        ABANDON
    }

    private final long debut;
    /** Tick d'arrivée du contrôleur, -1 tant qu'il approche. */
    private long arrivee = -1;

    public Controle(long debut) {
        this.debut = debut;
    }

    public boolean controleurArrive() {
        return arrivee >= 0;
    }

    /** À appeler régulièrement avec la distance entre contrôleur et suspect. */
    public Issue suivre(double distance, long maintenant) {
        if (distance > DISTANCE_FUITE) {
            return Issue.FUITE;
        }
        if (arrivee < 0) {
            if (distance <= CONTACT) {
                arrivee = maintenant;
                return Issue.EN_COURS;
            }
            return maintenant - debut >= APPROCHE_MAX ? Issue.ABANDON : Issue.EN_COURS;
        }
        return maintenant - arrivee >= DUREE ? Issue.OBEI : Issue.EN_COURS;
    }
}
