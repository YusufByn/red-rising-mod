package io.github.yusufbyn.redrising.mixin;

import io.github.yusufbyn.redrising.couleur.RestrictionsCouleur;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crafts interdits par Couleur (G3.2).
 *
 * <p>Un mixin ajoute du code dans une méthode de Minecraft. Ici, à la fin de {@code slotChangedCraftingGrid},
 * qui calcule le résultat d'une grille de craft (établi et grille 2×2 de l'inventaire) et l'envoie au client :
 * si la Couleur du joueur interdit ce résultat, on vide la case et on renvoie la case vide au client.</p>
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void redrising$interdireCraft(AbstractContainerMenu menu, ServerLevel level, Player player,
                                                 CraftingContainer grille, ResultContainer resultat,
                                                 @Nullable RecipeHolder<CraftingRecipe> recetteSuggeree, CallbackInfo ci) {
        ItemStack objet = resultat.getItem(0);
        if (!RestrictionsCouleur.craftInterdit(player, objet)) {
            return;
        }
        resultat.setItem(0, ItemStack.EMPTY);
        menu.setRemoteSlot(0, ItemStack.EMPTY);
        if (player instanceof ServerPlayer joueur) {
            joueur.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
        }
        RestrictionsCouleur.prevenir(player, Component.translatable("restriction.redrising.craft", objet.getHoverName()));
    }
}
