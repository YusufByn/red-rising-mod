package io.github.yusufbyn.redrising.mixin;

import io.github.yusufbyn.redrising.monde.MineLykos;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Les décorations (géodes, sources, salles de monstres...) qui ne se posent pas dans la mine (G7.2.1) : la règle est dans
 * {@link MineLykos#decorationRefusee}. Toutes les décorations passent par ici, y compris celles qu'une autre choisit au
 * hasard.
 */
@Mixin(ConfiguredFeature.class)
public abstract class ConfiguredFeatureMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void redrising$pasDansLaMine(WorldGenLevel monde, ChunkGenerator generateur, RandomSource hasard, BlockPos origine,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (MineLykos.decorationRefusee(monde, ((ConfiguredFeature<?, ?>) (Object) this).feature(), origine)) {
            cir.setReturnValue(false);
        }
    }
}
