package io.github.yusufbyn.redrising.client;

import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.net.ConclureTestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Résultat du test de naissance (G4.4) : la Couleur obtenue, en grand et teintée.
 * On l'accepte, ou on refait tout le test, une seule fois (la relance du GDD).
 * Texte en haut, boutons en bas : le joueur reste visible au centre, sur le vitrail de la scène (G4.5).
 */
public final class EcranResultatNaissance extends EcranNaissance {
    public static final String CLE_ACCEPTER = "naissance.redrising.resultat.accepter";
    public static final String CLE_RELANCER = "naissance.redrising.resultat.relancer";

    private static final int LARGEUR_BOUTON = 200;
    private static final float ECHELLE_NOM = 3;
    private static final int MARGE = 12;

    private final Couleur couleur;
    private final boolean relancePossible;

    public EcranResultatNaissance(Couleur couleur, boolean relancePossible) {
        super(Component.translatable("naissance.redrising.resultat.titre"));
        this.couleur = couleur;
        this.relancePossible = relancePossible;
    }

    @Override
    protected void placerBoutons() {
        int x = (width - LARGEUR_BOUTON) / 2;
        int nbBoutons = relancePossible ? 2 : 1;
        int y = height - MARGE - nbBoutons * (Button.DEFAULT_HEIGHT + ESPACE) + ESPACE;
        bouton(Component.translatable(CLE_ACCEPTER), x, y, LARGEUR_BOUTON, () -> conclure(false));
        if (relancePossible) {
            bouton(Component.translatable(CLE_RELANCER), x, y + Button.DEFAULT_HEIGHT + ESPACE, LARGEUR_BOUTON, () -> conclure(true));
        }
    }

    private void conclure(boolean relancer) {
        attendreServeur();
        ClientPlayNetworking.send(new ConclureTestPayload(relancer));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int sourisX, int sourisY, float partialTick) {
        super.extractRenderState(graphics, sourisX, sourisY, partialTick);
        graphics.centeredText(font, title, width / 2, MARGE, TEXTE);
        int yNom = MARGE + font.lineHeight + 8;
        // Le nom de la Couleur en grand : on agrandit le repère de dessin le temps de l'écrire
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2f, yNom);
        graphics.pose().scale(ECHELLE_NOM, ECHELLE_NOM);
        graphics.centeredText(font, couleur.getDisplayName(), 0, 0, 0xFF000000 | couleur.getTeinte());
        graphics.pose().popMatrix();
        Component note = Component.translatable(relancePossible ? "naissance.redrising.resultat.relance_possible" : "naissance.redrising.resultat.definitif");
        graphics.centeredText(font, note, width / 2, yNom + (int) (font.lineHeight * ECHELLE_NOM) + 8, TEXTE_DISCRET);
    }
}
