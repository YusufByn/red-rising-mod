package io.github.yusufbyn.redrising.mixin;

import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Boutiques;
import io.github.yusufbyn.redrising.factions.Camp;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Un coffre (ou tonneau) d'un camp détruit, quelle qu'en soit la cause (casse, explosion, commande) : son contenu tombe
 * marqué volé (G5.9). {@code preRemoveSideEffects} est l'endroit où Minecraft fait tomber le contenu d'un contenant,
 * juste avant de retirer le bloc ; on marque avant (HEAD).
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {
    @Inject(method = "preRemoveSideEffects", at = @At("HEAD"))
    private void redrising$marquerAvantDeTomber(BlockPos pos, BlockState etat, CallbackInfo ci) {
        BlockEntity bloc = (BlockEntity) (Object) this;
        if (bloc instanceof Container coffre && bloc.getAttached(ActionsFaction.CAMP) instanceof Camp camp
                && bloc.getLevel() != null && !bloc.getLevel().isClientSide()) {
            Boutiques.marquerVoles(coffre, camp);
        }
    }
}
