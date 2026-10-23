package io.github.yusufbyn.redrising.client.mixin;

import io.github.yusufbyn.redrising.client.ReglagesClient;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ajoute le réglage « Cheveux de Couleur » (G3.5) dans Options > Personnalisation du skin,
 * sous les bascules vanilla des parties du skin.
 *
 * <p>Le mixin hérite de {@code OptionsSubScreen} pour avoir accès à sa liste d'options ({@code list}, protégée).
 * Le constructeur n'est jamais appelé : il est là parce que Java l'exige.</p>
 */
@Mixin(SkinCustomizationScreen.class)
public abstract class SkinCustomizationScreenMixin extends OptionsSubScreen {
    private SkinCustomizationScreenMixin(Screen parent, Options options, Component titre) {
        super(parent, options, titre);
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void redrising$ajouterCheveux(CallbackInfo ci) {
        this.list.addBig(ReglagesClient.OPTION_CHEVEUX);
    }
}
