package io.github.yusufbyn.redrising.jalons;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Issue d'un jalon une fois passé : conforme aux livres ou divergente (GDD section 8). */
public enum Issue implements StringRepresentable {
    CANON("canon"),
    DIVERGENTE("divergente");

    public static final Codec<Issue> CODEC = StringRepresentable.fromEnum(Issue::values);

    private final String id;

    Issue(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
