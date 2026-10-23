package io.github.yusufbyn.redrising.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.yusufbyn.redrising.client.MarqueCouleurLayer;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marque de Couleur sur la main en vue première personne (G3.5).
 *
 * <p>{@code renderHand} dessine le bras du joueur (et sa manche, qui en est un enfant) quand la main est vide.
 * Juste après, on redessine le même bras, dans la même pose, avec la texture de la marque teintée.</p>
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "renderHand", at = @At("TAIL"))
    private void redrising$marqueEnPremierePersonne(PoseStack poseStack, SubmitNodeCollector collecteur, int lumiere,
                                                    Identifier skin, ModelPart bras, boolean mancheVisible, CallbackInfo ci) {
        var joueur = Minecraft.getInstance().player;
        if (joueur == null) {
            return;
        }
        Couleur couleur = CouleurJoueur.get(joueur);
        if (couleur == null) {
            return;
        }
        Identifier texture = MarqueCouleurLayer.textureMarque(joueur.getSkin().model());
        collecteur.submitModelPart(bras, poseStack, RenderTypes.entityTranslucent(texture), lumiere, OverlayTexture.NO_OVERLAY,
                null, 0xFF000000 | couleur.getTeinte(), null);
    }
}
