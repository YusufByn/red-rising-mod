package io.github.yusufbyn.redrising.jalons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * La mort d'un personnage canon (G6.4), enregistrée dans l'état du monde pour le {@link Bilan} des jalons suivants.
 *
 * @param temps  temps de jeu de la mort
 * @param joueur le joueur qui l'a tué, ou vide (monstre, conversion, chute...)
 */
public record MortPersonnage(long temps, Optional<UUID> joueur) {
    public static final Codec<MortPersonnage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("temps").forGetter(MortPersonnage::temps),
            UUIDUtil.STRING_CODEC.optionalFieldOf("joueur").forGetter(MortPersonnage::joueur)
    ).apply(instance, MortPersonnage::new));
}
