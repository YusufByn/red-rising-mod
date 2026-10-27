package io.github.yusufbyn.redrising.mixin;

import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Boutiques;
import io.github.yusufbyn.redrising.factions.Camp;
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
 * Côté client, le contenu n'est pas un coffre du monde : rien n'est compté. Le contenu d'un coffre d'un camp est marqué
 * volé (G5.9) à l'ouverture et à la fermeture.
 */
@Mixin(ChestMenu.class)
public abstract class ChestMenuMixin {
    /** Nombre d'objets à l'ouverture ; -1 si le coffre n'appartient à aucun camp. */
    @Unique
    private int redrising$objetsAOuverture = -1;

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;I)V",
            at = @At("TAIL"))
    private void redrising$compterAOuverture(MenuType<?> type, int id, Inventory inventaire, Container coffre, int lignes, CallbackInfo ci) {
        Camp camp = ActionsFaction.campDuCoffre(coffre);
        if (camp != null) {
            redrising$objetsAOuverture = ActionsFaction.nombreObjets(coffre);
            // Ce qui en sortira est marqué volé (G5.9)
            Boutiques.marquerVoles(coffre, camp);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void redrising$comparerAFermeture(Player joueur, CallbackInfo ci) {
        if (redrising$objetsAOuverture >= 0) {
            Container coffre = ((ChestMenu) (Object) this).getContainer();
            ActionsFaction.coffreFerme(joueur, coffre, redrising$objetsAOuverture);
            redrising$objetsAOuverture = -1;
            // Ce qu'on y a déposé appartient désormais au camp
            Camp camp = ActionsFaction.campDuCoffre(coffre);
            if (camp != null) {
                Boutiques.marquerVoles(coffre, camp);
            }
        }
    }
}
