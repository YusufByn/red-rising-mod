package io.github.yusufbyn.redrising.mixin;

import io.github.yusufbyn.redrising.factions.ActionsFaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Une explosion qui va détruire des coffres d'un camp (G5.9) : c'est un vol si l'on remonte au joueur responsable
 * (voir {@link ActionsFaction#explosion}). {@code interactWithBlocks} reçoit les blocs que l'explosion va détruire,
 * avant qu'ils le soient.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void redrising$coffresDetruits(List<BlockPos> positions, CallbackInfo ci) {
        ActionsFaction.explosion((ServerExplosion) (Object) this, positions);
    }
}
