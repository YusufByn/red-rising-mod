package io.github.yusufbyn.redrising.mixin;

import io.github.yusufbyn.redrising.monde.MineLykos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pas de carvers (grottes et canyons) dans l'emprise de la mine (G7.2.1) : ils creusent après le relief, donc le
 * remplissage du relief autour de la mine ne les arrête pas. Toute la hauteur des chunks de l'emprise, Overworld seulement.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    @Inject(method = "applyCarvers", at = @At("HEAD"), cancellable = true)
    private void redrising$pasDeCarversDansLaMine(WorldGenRegion region, long graine, RandomState aleatoire, BiomeManager biomes,
                                                  StructureManager structures, ChunkAccess chunk, CallbackInfo ci) {
        if (region.getLevel().dimension().equals(Level.OVERWORLD) && MineLykos.dansEmprise(chunk.getPos().x(), chunk.getPos().z())) {
            ci.cancel();
        }
    }
}
