package io.github.yusufbyn.redrising.client.mixin;

import io.github.yusufbyn.redrising.client.MarqueVillageoisLayer;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Recopie la Couleur d'un PNJ (G5.1) dans son état de rendu, pour {@link MarqueVillageoisLayer}.
 * Fabric n'a pas d'événement à cet endroit ; l'état de rendu, lui, peut porter des données en plus (Fabric).
 */
@Mixin(VillagerRenderer.class)
public abstract class VillagerRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/npc/villager/Villager;Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;F)V",
            at = @At("TAIL"))
    private void redrising$recopierCouleur(Villager villageois, VillagerRenderState etat, float partialTick, CallbackInfo ci) {
        etat.setData(MarqueVillageoisLayer.COULEUR, villageois.getAttached(CouleurJoueur.COULEUR));
    }
}
