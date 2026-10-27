package io.github.yusufbyn.redrising.client;

import io.github.yusufbyn.redrising.factions.Boutiques;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.ObjetsRebelles;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;

/**
 * Côté client des objets rebelles (G5.8) : le fumigène en vol est dessiné comme son objet (comme une boule de neige),
 * la contrebande porte la mention « Contrebande du Rising » dans sa description, et un objet volé dans un coffre d'un camp
 * la mention « Volé à la Société » ou « Volé au Rising » (G5.9).
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
            // Marque d'un objet volé dans un coffre d'un camp (G5.9)
            Camp vole = Boutiques.voleA(objet);
            if (vole != null) {
                lignes.add(Component.translatable("objet.redrising.vole." + vole.getSerializedName()).withStyle(ChatFormatting.GOLD));
            }
        });
    }
}
