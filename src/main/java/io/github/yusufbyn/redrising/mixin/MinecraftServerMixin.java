package io.github.yusufbyn.redrising.mixin;

import io.github.yusufbyn.redrising.monde.RegionsMars;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Le plan des régions de Mars se tire juste après le choix du point d'apparition d'un monde neuf (G7.1.1), avant que
 * Minecraft prépare les chunks autour de lui. Fabric n'a pas d'événement à ce moment-là : {@code ServerLevelEvents.LOAD}
 * arrive avant le choix du point d'apparition, {@code SERVER_STARTED} après la préparation. {@code RETURN} : la méthode
 * a plusieurs sorties (monde de debug, point trouvé dans la recherche ou pas).
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("RETURN"))
    private static void redrising$planDeMars(ServerLevel monde, ServerLevelData donnees, boolean coffreBonus, boolean debug,
                                             LevelLoadListener ecoute, CallbackInfo ci) {
        if (!debug) {
            RegionsMars.apresApparition(monde);
        }
    }
}
