package io.github.yusufbyn.redrising.factions;

import io.github.yusufbyn.redrising.couleur.Couleur;

/**
 * Place d'un PNJ dans la Société selon sa Couleur (G5.2) : elle décide de l'effet d'une action sur les jauges.
 */
public enum GroupePnj {
    /** Les piliers de la Société : Or, Argent, Cuivre, Blanc. */
    ELITE,
    /** Le bras armé de la Société : Gris, Obsidian. */
    ORDRE,
    /** Toutes les autres Couleurs. */
    PEUPLE;

    public static GroupePnj de(Couleur couleur) {
        return switch (couleur) {
            case OR, ARGENT, CUIVRE, BLANC -> ELITE;
            case GRIS, OBSIDIAN -> ORDRE;
            case BLEU, JAUNE, VERT, VIOLET, ORANGE, MARRON, ROSE, ROUGE -> PEUPLE;
        };
    }
}
