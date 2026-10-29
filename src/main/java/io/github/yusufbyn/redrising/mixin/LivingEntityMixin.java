package io.github.yusufbyn.redrising.mixin;

import io.github.yusufbyn.redrising.pnj.GrisEtMonstres;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Aucun monstre ne prend un Gris pour cible (G7.2.4, règle dans {@link GrisEtMonstres}). {@code canAttack} est appelé par
 * les conditions de ciblage ({@code TargetingConditions.test}, en combat) et par chaque but de ciblage qui garde sa cible
 * ({@code TargetGoal.canContinueToUse}) ; {@code Mob.canAttack} rappelle celui-ci.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void redrising$pasLesGris(LivingEntity cible, CallbackInfoReturnable<Boolean> cir) {
        if (GrisEtMonstres.ignore((LivingEntity) (Object) this, cible)) {
            cir.setReturnValue(false);
        }
    }
}
