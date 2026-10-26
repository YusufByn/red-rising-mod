package io.github.yusufbyn.redrising.mixin;

import io.github.yusufbyn.redrising.factions.ActionsFaction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vol dans un coffre d'un camp (G5.2). {@code ChestMenu} est l'écran des coffres simples et des tonneaux :
 * on compte les objets du coffre à l'ouverture (fin du constructeur), puis on compare à la fermeture ({@code removed}).
 * Côté client, le contenu n'est pas un coffre du monde : rien n'est compté.
 */
@Mixin(ChestMenu.class)
public abstract class ChestMenuMixin {
    /** Nombre d'objets à l'ouverture ; -1 si le coffre n'appartient à aucun camp. */
    @Unique
    private int redrising$objetsAOuverture = -1;

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;I)V",
            at = @At("TAIL"))
    private void redrising$compterAOuverture(MenuType<?> type, int id, Inventory inventaire, Container coffre, int lignes, CallbackInfo ci) {
        if (ActionsFaction.campDuCoffre(coffre) != null) {
            redrising$objetsAOuverture = ActionsFaction.nombreObjets(coffre);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void redrising$comparerAFermeture(Player joueur, CallbackInfo ci) {
        if (redrising$objetsAOuverture >= 0) {
            ActionsFaction.coffreFerme(joueur, ((ChestMenu) (Object) this).getContainer(), redrising$objetsAOuverture);
            redrising$objetsAOuverture = -1;
        }
    }
}
