package io.github.yusufbyn.redrising.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.net.ActiverCapacitePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Touche d'action de la capacité de Couleur (G3.3), R par défaut, modifiable dans les contrôles.
 * Le client se contente de prévenir le serveur, qui décide (Couleur, recharge) et répond par un message.
 */
public final class ToucheCapacite {
    public static final String NOM = "key.redrising.capacite";
    private static final KeyMapping.Category CATEGORIE = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "general"));

    private ToucheCapacite() {
    }

    /** Appelé depuis {@link RedRisingClient#onInitializeClient()}. */
    public static void init() {
        KeyMapping touche = KeyMappingHelper.registerKeyMapping(new KeyMapping(NOM, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORIE));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // consumeClick rend un appui à la fois : plusieurs appuis dans le même tick partent tous
            while (touche.consumeClick()) {
                if (client.player != null && ClientPlayNetworking.canSend(ActiverCapacitePayload.TYPE)) {
                    ClientPlayNetworking.send(ActiverCapacitePayload.INSTANCE);
                }
            }
        });
    }
}
