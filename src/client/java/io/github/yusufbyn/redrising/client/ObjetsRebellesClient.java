package io.github.yusufbyn.redrising.client;

import io.github.yusufbyn.redrising.factions.ObjetsRebelles;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;

/**
 * Côté client des objets rebelles (G5.8) : le fumigène en vol est dessiné comme son objet (comme une boule de neige),
 * et la contrebande porte la mention « Contrebande du Rising » dans sa description.
 */
public final class ObjetsRebellesClient {
    private ObjetsRebellesClient() {
    }

    public static void init() {
        EntityRenderers.register(ObjetsRebelles.FUMIGENE_LANCE, ThrownItemRenderer::new);
        ItemTooltipCallback.EVENT.register((objet, contexte, options, lignes) -> {
            if (objet.is(ObjetsRebelles.CONTREBANDE)) {
                lignes.add(Component.translatable("objet.redrising.contrebande").withStyle(ChatFormatting.DARK_RED));
            }
        });
    }
}
