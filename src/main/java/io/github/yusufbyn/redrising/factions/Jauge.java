package io.github.yusufbyn.redrising.factions;

import net.minecraft.util.StringRepresentable;

/**
 * Les 3 jauges du joueur (GDD section 6). Deux jauges séparées pour Société et Rising,
 * pour permettre l'agent double ; la Suspicion fait le lien.
 */
public enum Jauge implements StringRepresentable {
    LOYAUTE("loyaute"),
    SOUTIEN("soutien"),
    SUSPICION("suspicion");

    private final String id;

    Jauge(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Clé de traduction du nom affiché, ex. {@code jauge.redrising.loyaute}. */
    public String getTranslationKey() {
        return "jauge.redrising." + id;
    }
}
