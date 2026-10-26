package io.github.yusufbyn.redrising.factions;

/** Ce qu'une action ajoute (ou retire) à chaque jauge, avant bornage. */
public record Variation(int loyaute, int soutien, int suspicion) {
    public static final Variation AUCUNE = new Variation(0, 0, 0);
}
