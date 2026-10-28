package io.github.yusufbyn.redrising.mixin;

import io.github.yusufbyn.redrising.pnj.PnjEssentiels;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * La foudre change un villageois en sorcière sans le tuer (G6.4) : pas un personnage essentiel, qui ne doit disparaître
 * avant son jalon par aucun chemin. Le coup de foudre est annulé en entier (ni sorcière ni feu).
 */
@Mixin(Villager.class)
public abstract class VillagerMixin {
    @Inject(method = "thunderHit", at = @At("HEAD"), cancellable = true)
    private void redrising$pasDeSorciere(ServerLevel monde, LightningBolt eclair, CallbackInfo ci) {
        if (PnjEssentiels.essentiel((Villager) (Object) this)) {
            ci.cancel();
        }
    }
}
