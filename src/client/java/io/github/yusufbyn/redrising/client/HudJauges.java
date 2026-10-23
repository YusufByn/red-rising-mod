package io.github.yusufbyn.redrising.client;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.factions.Bornes;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.Jauges;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * HUD minimal des jauges (G2.6), en haut à gauche : la Couleur, puis une barre par jauge.
 * Provisoire, prévu pour être remplacé (textures finales en G17).
 *
 * <p>Lit les valeurs synchronisées par le serveur (G2.3) avec les mêmes méthodes que lui.
 * Caché avec le reste de l'interface (F1) et quand l'écran de debug (F3) est ouvert.</p>
 */
public final class HudJauges implements HudElement {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "jauges");

    private static final int MARGE = 4;
    private static final int LARGEUR_BARRE = 60;
    private static final int HAUTEUR_BARRE = 5;
    private static final int ESPACE = 4;

    // Couleurs ARGB (l'alpha FF est obligatoire, sinon rien ne s'affiche)
    private static final int TEXTE = 0xFFFFFFFF;
    private static final int FOND_BARRE = 0x80000000;
    private static final int HORS_BORNES = 0xFF404040;
    private static final int SOCIETE = 0xFFD4AF37;
    private static final int RISING = 0xFFC0392B;
    private static final int SUSPICION = 0xFFA0A0B0;

    /** Appelé depuis {@link RedRisingClient#onInitializeClient()}. */
    public static void init() {
        // Après la barre de boss : par-dessus le HUD vanilla, et caché comme lui avec F1
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, ID, new HudJauges());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer joueur = mc.player;
        if (joueur == null || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        Font font = mc.font;
        Couleur couleur = CouleurJoueur.get(joueur);
        Jauges jauges = JaugesJoueur.get(joueur);

        int y = MARGE;
        if (couleur != null) {
            graphics.text(font, couleur.getDisplayName(), MARGE, y, TEXTE, true);
            y += font.lineHeight + 2;
        }

        // Les noms sont alignés : les barres commencent après le plus long
        int largeurNoms = 0;
        for (Jauge jauge : Jauge.values()) {
            largeurNoms = Math.max(largeurNoms, font.width(nom(jauge)));
        }
        int xBarre = MARGE + largeurNoms + ESPACE;

        for (Jauge jauge : Jauge.values()) {
            int valeur = jauges.valeur(jauge);
            graphics.text(font, nom(jauge), MARGE, y, TEXTE, true);
            int yBarre = y + (font.lineHeight - HAUTEUR_BARRE) / 2;
            barre(graphics, xBarre, yBarre, valeur, Bornes.pour(couleur, jauge), teinte(jauge));
            graphics.text(font, String.valueOf(valeur), xBarre + LARGEUR_BARRE + ESPACE, y, TEXTE, true);
            y += font.lineHeight + 1;
        }
    }

    /** Barre de 0 à 100 ; les zones hors des bornes de la Couleur (ex. Loyauté d'un Rouge au-dessus de 40) sont grisées. */
    private static void barre(GuiGraphicsExtractor graphics, int x, int y, int valeur, Bornes bornes, int teinte) {
        graphics.fill(x, y, x + LARGEUR_BARRE, y + HAUTEUR_BARRE, FOND_BARRE);
        graphics.fill(x, y, x + largeur(bornes.min()), y + HAUTEUR_BARRE, HORS_BORNES);
        graphics.fill(x + largeur(bornes.max()), y, x + LARGEUR_BARRE, y + HAUTEUR_BARRE, HORS_BORNES);
        graphics.fill(x, y, x + largeur(valeur), y + HAUTEUR_BARRE, teinte);
    }

    private static int largeur(int valeur) {
        return valeur * LARGEUR_BARRE / Bornes.MAX;
    }

    private static Component nom(Jauge jauge) {
        return Component.translatable(jauge.getTranslationKey());
    }

    private static int teinte(Jauge jauge) {
        return switch (jauge) {
            case LOYAUTE -> SOCIETE;
            case SOUTIEN -> RISING;
            case SUSPICION -> SUSPICION;
        };
    }
}
