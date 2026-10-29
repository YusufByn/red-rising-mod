package io.github.yusufbyn.redrising.mixin;

import io.github.yusufbyn.redrising.monde.MonstresDeLaMine;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Les monstres de la mine (G7.2.3) : la règle est dans {@link MonstresDeLaMine#refusee}. {@code isValidPositionForMob} est
 * appelé pour chaque apparition naturelle, le monstre déjà à sa place (après {@code snapTo}, avant {@code finalizeSpawn} et
 * l'ajout au monde) ; la lumière a déjà été vérifiée par Minecraft.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    @Inject(method = "isValidPositionForMob", at = @At("HEAD"), cancellable = true)
    private static void redrising$monstresDeLaMine(ServerLevel niveau, Mob mob, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (MonstresDeLaMine.refusee(niveau, mob)) {
            cir.setReturnValue(false);
        }
    }
}
