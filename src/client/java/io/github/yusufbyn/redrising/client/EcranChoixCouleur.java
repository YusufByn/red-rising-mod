package io.github.yusufbyn.redrising.client;

import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.net.ChoisirCouleurPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Écran de choix libre de la Couleur (G4.2) : les 14 Couleurs en deux colonnes, de la plus haute à la plus basse.
 * Un clic envoie la demande au serveur ; l'écran se ferme quand la Couleur arrive, c'est-à-dire quand le serveur l'a acceptée.
 */
public final class EcranChoixCouleur extends EcranNaissance {
    public static final String CLE_TITRE = "naissance.redrising.choix.titre";

    private static final int LARGEUR_BOUTON = 120;
    private static final int COLONNES = 2;

    public EcranChoixCouleur() {
        super(Component.translatable(CLE_TITRE));
    }

    @Override
    protected void placerBoutons() {
        Couleur[] couleurs = Couleur.values();
        int lignes = (couleurs.length + COLONNES - 1) / COLONNES;
        int largeurGrille = COLONNES * LARGEUR_BOUTON + (COLONNES - 1) * ESPACE;
        int hauteurGrille = lignes * (Button.DEFAULT_HEIGHT + ESPACE) - ESPACE;
        int x0 = (width - largeurGrille) / 2;
        // Grille centrée, sous le titre et la phrase d'explication
        int y0 = Math.max(48, (height - hauteurGrille) / 2 + 12);
        for (int i = 0; i < couleurs.length; i++) {
            Couleur couleur = couleurs[i];
            int x = x0 + (i % COLONNES) * (LARGEUR_BOUTON + ESPACE);
            int y = y0 + (i / COLONNES) * (Button.DEFAULT_HEIGHT + ESPACE);
            Component nom = couleur.getDisplayName().copy().withColor(couleur.getTeinte());
            bouton(nom, x, y, LARGEUR_BOUTON, () -> choisir(couleur));
        }
    }

    private void choisir(Couleur couleur) {
        attendreServeur();
        ClientPlayNetworking.send(new ChoisirCouleurPayload(couleur));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int sourisX, int sourisY, float partialTick) {
        super.extractRenderState(graphics, sourisX, sourisY, partialTick);
        int yTitre = hautDesBoutons() - 32;
        graphics.centeredText(font, title, width / 2, yTitre, TEXTE);
        graphics.centeredText(font, Component.translatable("naissance.redrising.choix.explication"), width / 2, yTitre + font.lineHeight + 4, TEXTE_DISCRET);
    }
}
