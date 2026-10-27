package io.github.yusufbyn.redrising.factions;

/**
 * Qui a vu le joueur agir (G5.5) : décide de ce que la Société apprend. Voir {@link EffetsActions#selonTemoins}.
 * S'il y a plusieurs témoins, le plus gênant compte (un Gris).
 */
public enum Temoin {
    /** Personne : la Société ne sait rien, mais les soupçons montent un peu. */
    AUCUN,
    /** Au moins un PNJ de Couleur (hors Rising), aucun Gris. */
    ORDINAIRE,
    /** Au moins un Gris : c'est la police. */
    GRIS
}
