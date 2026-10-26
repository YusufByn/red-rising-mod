package io.github.yusufbyn.redrising.mixin;

import io.github.yusufbyn.redrising.factions.ActionsFaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Un entonnoir ne vide pas un coffre ou un tonneau d'un camp (G5.4), sinon on volerait sans jamais l'ouvrir.
 * {@code getSourceContainer} cherche le contenant au-dessus de l'entonnoir ; il sert à l'entonnoir posé comme au wagonnet
 * à entonnoir (tous deux passent par {@code suckInItems}). Remplir un coffre d'un camp reste possible : c'est un don.
 *
 * <p>On renvoie un contenant vide (0 case), pas {@code null} : sur {@code null}, l'API de transfert de Fabric
 * (son propre mixin sur {@code suckInItems}) cherche le coffre par un autre chemin et le viderait quand même.</p>
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Unique
    private static final Container REDRISING$RIEN_A_PRENDRE = new SimpleContainer(0);

    @Inject(method = "getSourceContainer", at = @At("HEAD"), cancellable = true)
    private static void redrising$protegerCoffreDeCamp(Level monde, Hopper entonnoir, BlockPos pos, BlockState etat, CallbackInfoReturnable<Container> cir) {
        if (ActionsFaction.protegeDesEntonnoirs(monde, pos, etat)) {
            cir.setReturnValue(REDRISING$RIEN_A_PRENDRE);
        }
    }
}
