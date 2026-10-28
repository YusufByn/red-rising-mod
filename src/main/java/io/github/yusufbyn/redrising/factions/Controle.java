package io.github.yusufbyn.redrising.factions;

/**
 * Un contrôle d'identité (G5.5), du « Halte ! Contrôle. » jusqu'à son issue. Logique pure, testée par {@code ControleTest} :
 * elle ne sait pas qui contrôle (un PNJ Gris aujourd'hui, le joueur Gris en G8.3).
 *
 * <p>Le contrôleur vient au suspect, puis le contrôle dure un temps donné. Les distances et durées sont des {@link Regles} :
 * {@link #CONTROLE} pour un contrôle, {@code Traque.ARRESTATION} pour une arrestation pendant une traque (G5.6).
 * Valeurs en 🟡 (le seuil de Suspicion est dans {@link Acces#SEUIL_CONTROLE}).</p>
 */
public final class Controle {
    /**
     * @param distanceFuite  au-delà, le suspect a fui ({@code POSITIVE_INFINITY} : jamais)
     * @param contact        le contrôleur est arrivé
     * @param duree          durée une fois arrivé, en ticks
     * @param approcheMax    le contrôleur n'arrive pas : abandon au bout de ce temps
     * @param contactContinu le contact doit être tenu sans interruption : s'éloigner remet le compte à zéro
     */
    public record Regles(double distanceFuite, double contact, int duree, int approcheMax, boolean contactContinu) {
    }

    /** Un Gris remarque un suspect à cette distance (et s'il le voit). */
    public static final double DETECTION = 12;
    /** Un joueur n'est pas contrôlé plus d'une fois toutes les 5 minutes de jeu. */
    public static final int ATTENTE_ENTRE_CONTROLES = 6000;

    /** 🟡 Détection selon l'alerte des patrouilles (G6.3, levier {@code ALERTE_PATROUILLES}) : 12, 18, 24 blocs. */
    public static double detection(int alerte) {
        return switch (alerte) {
            case 0 -> DETECTION;
            case 1 -> 18;
            default -> 24;
        };
    }

    /** 🟡 Attente entre deux contrôles d'un même joueur selon l'alerte : 5 min, 2 min 30, 1 min 15. */
    public static int attenteEntreControles(int alerte) {
        return switch (alerte) {
            case 0 -> ATTENTE_ENTRE_CONTROLES;
            case 1 -> ATTENTE_ENTRE_CONTROLES / 2;
            default -> ATTENTE_ENTRE_CONTROLES / 4;
        };
    }
    /**
     * Contrôle : fuite au-delà de 16 blocs, dès le « Halte » (plus que {@link #DETECTION}, pour qu'on ne soit pas en fuite
     * dès le « Halte ») ; contrôleur arrivé à 2,5 blocs ; 5 secondes de contrôle ; abandon, sans effet, s'il n'arrive pas
     * en 20 secondes.
     */
    public static final Regles CONTROLE = new Regles(16, 2.5, 100, 400, false);

    public enum Issue {
        EN_COURS,
        /** Mené à bout : le suspect est resté jusqu'au bout (contrôle), ou il est pris (arrestation). */
        ABOUTI,
        FUITE,
        /** Le contrôleur n'a pas pu arriver. */
        ABANDON
    }

    private final Regles regles;
    private final long debut;
    /** Tick d'arrivée du contrôleur, -1 tant qu'il approche. */
    private long arrivee = -1;

    public Controle(Regles regles, long debut) {
        this.regles = regles;
        this.debut = debut;
    }

    public boolean controleurArrive() {
        return arrivee >= 0;
    }

    /** À appeler régulièrement avec la distance entre contrôleur et suspect. */
    public Issue suivre(double distance, long maintenant) {
        if (distance > regles.distanceFuite()) {
            return Issue.FUITE;
        }
        if (arrivee >= 0 && regles.contactContinu() && distance > regles.contact()) {
            arrivee = -1;
        }
        if (arrivee < 0) {
            if (distance <= regles.contact()) {
                arrivee = maintenant;
                return Issue.EN_COURS;
            }
            return maintenant - debut >= regles.approcheMax() ? Issue.ABANDON : Issue.EN_COURS;
        }
        return maintenant - arrivee >= regles.duree() ? Issue.ABOUTI : Issue.EN_COURS;
    }
}
