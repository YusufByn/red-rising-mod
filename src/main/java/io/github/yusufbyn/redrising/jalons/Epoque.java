package io.github.yusufbyn.redrising.jalons;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Époque du monde : un tome de la première trilogie (GDD section 8). Le monde démarre au tome 1. */
public enum Epoque implements StringRepresentable {
    RED_RISING("red_rising"),
    GOLDEN_SON("golden_son"),
    MORNING_STAR("morning_star");

    public static final Codec<Epoque> CODEC = StringRepresentable.fromEnum(Epoque::values);

    private final String id;

    Epoque(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
