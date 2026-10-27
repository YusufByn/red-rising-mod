package io.github.yusufbyn.redrising.factions;

import io.github.yusufbyn.redrising.couleur.Couleur;

/**
 * Effet des actions du joueur sur ses jauges (G5.2, GDD section 6) : logique pure, testée par {@code EffetsActionsTest}.
 * Valeurs en 🟡 dans le GDD : à régler en jeu, toutes ici.
 *
 * <p>Tuer, voler et l'effraction passent ensuite par {@link #selonTemoins} (G5.5) : ce que la Société en apprend
 * dépend de qui a vu le joueur.</p>
 */
public final class EffetsActions {
    /** La Suspicion retombe d'elle-même, chaque jour de jeu (sinon tout joueur du Rising finit traqué). */
    public static final int DECLIN_SUSPICION_PAR_JOUR = 2;
    /** Distance à laquelle un PNJ du Rising peut être dénoncé à un Gris. */
    public static final double PORTEE_DENONCIATION = 16;
    /** 🟡 Une cellule connue est dénoncée si elle est à cette distance du Gris (G5.10). */
    public static final double PORTEE_DENONCIATION_CELLULE = 32;
    /** Un PNJ de Couleur voit le joueur agir à cette distance (et s'il a une ligne de vue). */
    public static final double PORTEE_TEMOIN = 16;

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

    /**
     * Un vol dans un coffre où le Soutien a déjà été compté aujourd'hui pour ce joueur (G6.1) : Loyauté et Suspicion
     * seulement, pour que reprendre ses propres objets dans un coffre d'un camp ne soit pas une ferme à Soutien.
     */
    public static Variation volSansSoutien(Variation vol) {
        return new Variation(vol.loyaute(), 0, vol.suspicion());
    }

    /** Le joueur a dénoncé un PNJ du Rising à un Gris. */
    public static Variation denoncer() {
        return new Variation(5, -10, -5);
    }

    /** Une journée de jeu a passé. */
    public static Variation jourPasse() {
        return new Variation(0, 0, -DECLIN_SUSPICION_PAR_JOUR);
    }

    /** Le joueur a été reconduit hors d'un poste de la Société où il était entré sans y être admis. */
    public static Variation effraction() {
        return new Variation(0, 0, 5);
    }

    /** Le joueur est resté jusqu'au bout d'un contrôle (au plus une fois par jour de jeu, voir {@code PatrouillePnj}). */
    public static Variation controleObei() {
        return new Variation(0, 0, -5);
    }

    /** Le joueur a tué un chasseur pendant une traque (G5.6) : comme un Gris, mais sans Soutien (sinon la traque rapporte). */
    public static Variation tuerChasseur() {
        Variation gris = tuer(Couleur.GRIS);
        return new Variation(gris.loyaute(), 0, gris.suspicion());
    }

    /** Le joueur a été arrêté au bout d'une traque (G5.6). La Suspicion est ensuite fixée par {@code Traque.SUSPICION_ARRETE}. */
    public static Variation arrestation() {
        return new Variation(-15, 0, 0);
    }

    /** Le joueur a fui un contrôle. */
    public static Variation fuiteControle() {
        return new Variation(-5, 0, 10);
    }

    /** Dénoncer une cellule du Rising à un Gris, carte en main (G5.10). */
    public static Variation denoncerCellule() {
        return new Variation(10, -25, -10);
    }

    /** Le joueur a mené les Gris à une cellule pendant une traque (G5.10) : pas une trahison voulue, mais une imprudence. */
    public static Variation celluleReperee() {
        return new Variation(0, -10, 0);
    }

    /**
     * La baisse de Suspicion d'une dénonciation (PNJ ou cellule) ne compte qu'une fois par jour de jeu, et jamais pendant
     * une traque (G5.10) : sinon dénoncer serait une machine à se blanchir. Loyauté et Soutien s'appliquent toujours.
     */
    public static Variation denonciationSansBlanchiment(Variation variation) {
        return new Variation(variation.loyaute(), variation.soutien(), Math.max(0, variation.suspicion()));
    }

    /** Un Gris a vu de la contrebande du Rising en main, ou utilisée (G5.8) ; une fois par minute au plus ({@link Contrebande}). */
    public static Variation contrebandeVue() {
        return new Variation(0, 0, 5);
    }

    /**
     * Ce que la Société apprend d'une action selon ses témoins (G5.5).
     * <ul>
     *     <li>Sans témoin : pas de Loyauté perdue, mais la Suspicion monte quand même de moitié (arrondie au-dessus),
     *     pour qu'agir en cachette ne soit pas gratuit.</li>
     *     <li>Témoin ordinaire : l'effet entier.</li>
     *     <li>Un Gris : Suspicion doublée.</li>
     * </ul>
     * Le Soutien ne dépend pas des témoins : le Rising apprend tout par les siens.
     */
    public static Variation selonTemoins(Variation variation, Temoin temoin) {
        return switch (temoin) {
            case AUCUN -> new Variation(0, variation.soutien(), Math.ceilDiv(variation.suspicion(), 2));
            case ORDINAIRE -> variation;
            case GRIS -> new Variation(variation.loyaute(), variation.soutien(), variation.suspicion() * 2);
        };
    }
}
