package io.github.yusufbyn.redrising.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.yusufbyn.redrising.monde.MineLykos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Aucune autre structure ne croise la mine (G7.2.1) : trial chambers, mineshafts, ancient cities... Un départ dont la boîte
 * croise son volume (ou la colonne du puits) est jugé invalide, comme si Minecraft n'avait pas trouvé de place. Les forts
 * restent (l'accès à l'End) : le plan des régions les évite. Overworld seulement.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @WrapOperation(method = "tryGenerateStructure", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/StructureStart;isValid()Z"))
    private boolean redrising$pasSurLaMine(StructureStart depart, Operation<Boolean> original, @Local(argsOnly = true) ResourceKey<Level> dimension) {
        return original.call(depart) && !(dimension.equals(Level.OVERWORLD) && MineLykos.empiete(depart));
    }
}
