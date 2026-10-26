package io.github.yusufbyn.redrising.client;

import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.net.ChoisirCouleurPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Écran de choix libre de la Couleur (G4.2) : les 14 Couleurs en deux colonnes, de la plus haute à la plus basse.
 * Provisoire, prévu pour être remplacé (interface finale en G17).
 *
 * <p>On ne peut pas le fermer sans choisir (Échap ne fait rien). Un clic envoie la demande au serveur ;
 * l'écran se ferme quand la Couleur arrive, c'est-à-dire quand le serveur l'a acceptée.</p>
 *
 * <p>Ouvert par {@link NaissanceClient} à la demande du serveur.</p>
 */
public final class EcranChoixCouleur extends Screen {
    public static final String CLE_TITRE = "naissance.redrising.choix.titre";

    private static final int LARGEUR_BOUTON = 120;
    private static final int ESPACE = 4;
    private static final int COLONNES = 2;
    private static final int TEXTE = 0xFFFFFFFF;
    private static final int TEXTE_DISCRET = 0xFFA0A0A0;

    private final List<Button> boutons = new ArrayList<>();

    public EcranChoixCouleur() {
        super(Component.translatable(CLE_TITRE));
    }

    @Override
    protected void init() {
        boutons.clear();
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
            boutons.add(addRenderableWidget(Button.builder(nom, bouton -> choisir(couleur))
                    .bounds(x, y, LARGEUR_BOUTON, Button.DEFAULT_HEIGHT)
                    .build()));
        }
    }

    private void choisir(Couleur couleur) {
        // En attendant la réponse du serveur, pas de second clic
        boutons.forEach(bouton -> bouton.active = false);
        ClientPlayNetworking.send(new ChoisirCouleurPayload(couleur));
    }

    @Override
    public void tick() {
        if (minecraft.player != null && CouleurJoueur.get(minecraft.player) != null) {
            onClose();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int sourisX, int sourisY, float partialTick) {
        super.extractRenderState(graphics, sourisX, sourisY, partialTick);
        int yTitre = boutons.isEmpty() ? 16 : boutons.getFirst().getY() - 32;
        graphics.centeredText(font, title, width / 2, yTitre, TEXTE);
        graphics.centeredText(font, Component.translatable("naissance.redrising.choix.explication"), width / 2, yTitre + font.lineHeight + 4, TEXTE_DISCRET);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
