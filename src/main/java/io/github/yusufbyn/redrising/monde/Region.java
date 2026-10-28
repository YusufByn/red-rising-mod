package io.github.yusufbyn.redrising.monde;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Les régions de Mars (GDD section 7), placées par le {@link PlanMars} (G7.1.1). L'ordre est celui du tirage : chaque
 * région s'écarte de celles déjà placées.
 */
public enum Region implements StringRepresentable {
    /** Mine et colonie rouge (Lykos) : Rouge. */
    MINE("mine"),
    /** Casernes, avant-postes : Gris, Bleu. */
    GARNISON("garnison"),
    /** Cité de la Société : Or, Argent, Cuivre, Blanc, Rose, Violet. */
    CITE("cite"),
    /** Zones industrielles et agricoles : Orange, Vert, Marron, Jaune. */
    ZONES("zones"),
    /** Glace, tribus : Obsidian. */
    POLES("poles");

    public static final Codec<Region> CODEC = StringRepresentable.fromEnum(Region::values);

    private final String id;

    Region(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
