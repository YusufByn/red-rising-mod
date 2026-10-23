package io.github.yusufbyn.redrising.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;
import org.jspecify.annotations.Nullable;

/**
 * Apparence par Couleur sur le modèle du joueur (G3.5), teintée de sa Couleur :
 * <ul>
 *     <li>mains : bracelet au poignet et sigil sur le dos de la main ;</li>
 *     <li>yeux : les deux iris, à l'emplacement standard des skins par défaut ;</li>
 *     <li>cheveux : couche sur la couche extérieure de la tête, désactivable dans les réglages client.</li>
 * </ul>
 * Visible par tous, puisque la Couleur est synchronisée avec tous les clients (G2.3). Marche aussi pour
 * les mannequins qui portent une Couleur (captures de test, futurs PNJ).
 *
 * <p>Une « couche » (render layer) se dessine par-dessus le modèle : on redessine le joueur avec une texture
 * transparente sauf aux pixels voulus (textures générées par {@code outils/marque_couleur.py}).</p>
 */
public final class MarqueCouleurLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier TEXTURE_LARGE = Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "textures/entity/marque_large.png");
    private static final Identifier TEXTURE_FINE = Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "textures/entity/marque_fine.png");
    private static final Identifier TEXTURE_CHEVEUX = Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "textures/entity/cheveux.png");
    /** Dessinée après le modèle (ordre 0), donc par-dessus la peau à profondeur égale. */
    private static final int ORDRE = 1;

    public MarqueCouleurLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    /** Appelé depuis {@link RedRisingClient#onInitializeClient()}. */
    public static void init() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, aide, contexte) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                aide.register(new MarqueCouleurLayer(avatar));
            }
        });
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collecteur, int lumiere, AvatarRenderState etat, float yRot, float xRot) {
        if (etat.isInvisible) {
            return;
        }
        Couleur couleur = couleurDe(etat);
        if (couleur == null) {
            return;
        }
        int teinte = 0xFF000000 | couleur.getTeinte();
        dessiner(poseStack, collecteur, lumiere, etat, textureMarque(etat.skin.model()), teinte);
        if (ReglagesClient.cheveuxCouleur()) {
            dessiner(poseStack, collecteur, lumiere, etat, TEXTURE_CHEVEUX, teinte);
        }
    }

    /** Texture des mains et des yeux selon le type de bras (Steve : larges, Alex : fins). */
    public static Identifier textureMarque(PlayerModelType type) {
        return type == PlayerModelType.SLIM ? TEXTURE_FINE : TEXTURE_LARGE;
    }

    /**
     * Comme {@code renderColoredCutoutModel}, mais en translucide : la peau du joueur est dessinée en translucide,
     * après tout ce qui est « cutout » ; une marque en cutout passerait sous la couche extérieure du skin.
     */
    private void dessiner(PoseStack poseStack, SubmitNodeCollector collecteur, int lumiere, AvatarRenderState etat, Identifier texture, int teinte) {
        collecteur.order(ORDRE).submitModel(getParentModel(), etat, poseStack, RenderTypes.entityTranslucent(texture),
                lumiere, LivingEntityRenderer.getOverlayCoords(etat, 0), teinte, null, etat.outlineColor, null);
    }

    /**
     * L'état de rendu ne garde que l'identifiant de l'entité : on la retrouve dans le monde du client
     * pour lire sa Couleur synchronisée. Rien pour une entité sans Couleur (joueur pas encore né, mannequin ordinaire).
     */
    private static @Nullable Couleur couleurDe(AvatarRenderState etat) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Entity entite = mc.level.getEntity(etat.id);
        return entite == null ? null : entite.getAttached(CouleurJoueur.COULEUR);
    }
}
