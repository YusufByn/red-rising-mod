package io.github.yusufbyn.redrising.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Repères de Couleur des PNJ (G5.1), comme ceux du joueur (G3.5) : iris, cheveux sur le crâne et sigils sur les mains
 * croisées, teintés de la Couleur du villageois. Rien sur un villageois ordinaire.
 *
 * <p>L'état de rendu d'un villageois ne garde pas l'entité : sa Couleur y est recopiée au moment où le jeu le prépare
 * ({@code VillagerRendererMixin}), dans une donnée supplémentaire que Fabric permet d'y ranger ({@link #COULEUR}).</p>
 */
public final class MarqueVillageoisLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
    /** Couleur du villageois dans son état de rendu ; absente pour un villageois ordinaire. */
    public static final RenderStateDataKey<Couleur> COULEUR = RenderStateDataKey.create(() -> RedRising.MOD_ID + ":couleur");

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "textures/entity/marque_villageois.png");
    /** Dessinée après le modèle et le métier, donc par-dessus à profondeur égale. */
    private static final int ORDRE = 1;

    public MarqueVillageoisLayer(RenderLayerParent<VillagerRenderState, VillagerModel> parent) {
        super(parent);
    }

    /** Appelé depuis {@link RedRisingClient#onInitializeClient()}. */
    public static void init() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, aide, contexte) -> {
            if (renderer instanceof VillagerRenderer villageois) {
                aide.register(new MarqueVillageoisLayer(villageois));
            }
        });
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collecteur, int lumiere, VillagerRenderState etat, float yRot, float xRot) {
        Couleur couleur = etat.getData(COULEUR);
        if (etat.isInvisible || couleur == null) {
            return;
        }
        // Translucide, comme les repères du joueur (voir MarqueCouleurLayer)
        collecteur.order(ORDRE).submitModel(getParentModel(), etat, poseStack, RenderTypes.entityTranslucent(TEXTURE),
                lumiere, LivingEntityRenderer.getOverlayCoords(etat, 0), 0xFF000000 | couleur.getTeinte(), null, etat.outlineColor, null);
    }
}
