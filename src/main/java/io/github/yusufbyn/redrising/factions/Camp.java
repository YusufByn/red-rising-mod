package io.github.yusufbyn.redrising.factions;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Camp qui contrôle une zone du monde. */
public enum Camp implements StringRepresentable {
    SOCIETE("societe"),
    RISING("rising");

    public static final Codec<Camp> CODEC = StringRepresentable.fromEnum(Camp::values);

    private final String id;

    Camp(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
