package io.github.yusufbyn.redrising.mixin;

import io.github.yusufbyn.redrising.pnj.GrisEtMonstres;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Un Gris ne fuit aucun monstre (G7.2.4, règle dans {@link GrisEtMonstres}). Ce capteur remplit la mémoire
 * {@code NEAREST_HOSTILE} d'un villageois (zombie à 8 blocs, ravageur à 12...), et {@code VillagerPanicTrigger} le fait
 * paniquer dès qu'elle est remplie : pour un Gris, aucun hostile ne correspond.
 */
@Mixin(VillagerHostilesSensor.class)
public abstract class VillagerHostilesSensorMixin {
    @Inject(method = "isMatchingEntity", at = @At("HEAD"), cancellable = true)
    private void redrising$grisSansPeur(ServerLevel monde, LivingEntity villageois, LivingEntity autre, CallbackInfoReturnable<Boolean> cir) {
        if (GrisEtMonstres.gris(villageois)) {
            cir.setReturnValue(false);
        }
    }
}
