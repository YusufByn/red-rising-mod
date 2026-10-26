package io.github.yusufbyn.redrising.factions;

import io.github.yusufbyn.redrising.couleur.Couleur;

/**
 * Effet des actions du joueur sur ses jauges (G5.2, GDD section 6) : logique pure, testée par {@code EffetsActionsTest}.
 * Valeurs en 🟡 dans le GDD : à régler en jeu, toutes ici.
 *
 * <p>La Suspicion monte quoi qu'il arrive pour l'instant : les témoins arriveront avec les patrouilles de Gris (G5.5).</p>
 */
public final class EffetsActions {
    /** La Suspicion retombe d'elle-même, chaque jour de jeu (sinon tout joueur du Rising finit traqué). */
    public static final int DECLIN_SUSPICION_PAR_JOUR = 2;
    /** Distance à laquelle un PNJ du Rising peut être dénoncé à un Gris. */
    public static final double PORTEE_DENONCIATION = 16;

    private EffetsActions() {
    }

    /** Le joueur a tué un PNJ de cette Couleur. */
    public static Variation tuer(Couleur victime) {
        return switch (GroupePnj.de(victime)) {
            case ELITE -> new Variation(-15, 5, 15);
            case ORDRE -> new Variation(-10, 4, 10);
            case PEUPLE -> new Variation(-3, -5, 5);
        };
    }

    /** Le joueur a donné de la nourriture à un PNJ de cette Couleur. */
    public static Variation aider(Couleur aide) {
        return switch (GroupePnj.de(aide)) {
            case ELITE, ORDRE -> new Variation(2, 0, 0);
            case PEUPLE -> new Variation(0, 1, 0);
        };
    }

    /** Le joueur a pris des objets dans un coffre de ce camp. */
    public static Variation voler(Camp proprietaire) {
        return switch (proprietaire) {
            case SOCIETE -> new Variation(-5, 2, 5);
            case RISING -> new Variation(0, -5, 0);
        };
    }

    /** Le joueur a dénoncé un PNJ du Rising à un Gris. */
    public static Variation denoncer() {
        return new Variation(5, -10, -5);
    }

    /** Une journée de jeu a passé. */
    public static Variation jourPasse() {
        return new Variation(0, 0, -DECLIN_SUSPICION_PAR_JOUR);
    }
}
