package io.github.yusufbyn.redrising.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.yusufbyn.redrising.couleur.ModeNaissance;
import io.github.yusufbyn.redrising.couleur.Naissance;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bouton « Naissance : Test / Choix libre » dans l'onglet « Jeu » de l'écran de création du monde (G4.1),
 * sous les réglages vanilla. Il modifie la règle de jeu {@link Naissance#MODE} du monde en préparation :
 * c'est elle qui est enregistrée avec le monde.
 *
 * <p>{@code GameTab} est une classe interne privée : on la vise par son nom. {@code @Local} (MixinExtras) récupère
 * la variable locale {@code RowHelper} du constructeur, qui sert à empiler les boutons de l'onglet.</p>
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
public abstract class CreateWorldGameTabMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void redrising$ajouterModeNaissance(CreateWorldScreen ecran, CallbackInfo ci, @Local GridLayout.RowHelper lignes) {
        WorldCreationUiState etat = ecran.getUiState();
        CycleButton<ModeNaissance> bouton = CycleButton.builder(ModeNaissance::getDisplayName, etat.getGameRules().get(Naissance.MODE))
                .withValues(ModeNaissance.values())
                .withTooltip(mode -> Tooltip.create(Component.translatable(Naissance.CLE_REGLE + ".description")))
                .create(0, 0, 210, 20, Component.translatable(Naissance.CLE_REGLE),
                        (cycle, mode) -> etat.getGameRules().set(Naissance.MODE, mode, null));
        // Si la règle change ailleurs (écran « Règles du jeu »), le bouton suit
        etat.addListener(nouvelEtat -> bouton.setValue(nouvelEtat.getGameRules().get(Naissance.MODE)));
        lignes.addChild(bouton);
    }
}
