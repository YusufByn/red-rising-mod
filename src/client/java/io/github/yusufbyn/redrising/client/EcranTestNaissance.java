package io.github.yusufbyn.redrising.client;

import io.github.yusufbyn.redrising.couleur.TestPersonnalite;
import io.github.yusufbyn.redrising.net.RepondreTestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Questions du test de naissance (G4.4), une par une, avec leurs 4 réponses.
 * Le barème reste invisible : le joueur ne voit que les textes, et c'est le serveur qui compte les points
 * une fois les 7 réponses envoyées. Pas de retour en arrière, comme dans un vrai test.
 *
 * <p>La question en haut, les réponses en bas : le centre de l'écran reste libre pour le joueur, que la caméra
 * montre de dos sur le vitrail de la scène (G4.5).</p>
 */
public final class EcranTestNaissance extends EcranNaissance {
    public static final String CLE_TITRE = "naissance.redrising.test.titre";

    /** Largeur des boutons de réponse (moins sur un petit écran). */
    public static final int LARGEUR_MAX = 320;
    private static final int MARGE = 12;

    private final List<Integer> choix = new ArrayList<>();

    public EcranTestNaissance() {
        super(Component.translatable(CLE_TITRE));
    }

    private int question() {
        return choix.size();
    }

    @Override
    protected void placerBoutons() {
        if (question() >= TestPersonnalite.QUESTIONS.size()) {
            return; // réponses envoyées : on attend le résultat
        }
        int largeur = Math.min(LARGEUR_MAX, width - 40);
        int x = (width - largeur) / 2;
        int hauteurBoutons = TestPersonnalite.NB_REPONSES * (Button.DEFAULT_HEIGHT + ESPACE) - ESPACE;
        int y0 = height - hauteurBoutons - MARGE;
        for (int reponse = 0; reponse < TestPersonnalite.NB_REPONSES; reponse++) {
            int numero = reponse;
            bouton(Component.translatable(TestPersonnalite.cleReponse(question(), reponse)),
                    x, y0 + reponse * (Button.DEFAULT_HEIGHT + ESPACE), largeur, () -> repondre(numero));
        }
    }

    private void repondre(int reponse) {
        choix.add(reponse);
        if (question() < TestPersonnalite.QUESTIONS.size()) {
            rebuildWidgets(); // question suivante
        } else {
            attendreServeur();
            ClientPlayNetworking.send(new RepondreTestPayload(List.copyOf(choix)));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int sourisX, int sourisY, float partialTick) {
        super.extractRenderState(graphics, sourisX, sourisY, partialTick);
        int total = TestPersonnalite.QUESTIONS.size();
        if (question() >= total) {
            graphics.centeredText(font, Component.translatable("naissance.redrising.test.attente"), width / 2, height / 2, TEXTE_DISCRET);
            return;
        }
        // Numéro et texte de la question en haut, sur plusieurs lignes si besoin
        graphics.centeredText(font, Component.translatable("naissance.redrising.test.progression", question() + 1, total),
                width / 2, MARGE, TEXTE_DISCRET);
        List<FormattedCharSequence> lignes = font.split(Component.translatable(TestPersonnalite.cleQuestion(question())), Math.min(LARGEUR_MAX, width - 40));
        int y = MARGE + font.lineHeight + 8;
        for (FormattedCharSequence ligne : lignes) {
            graphics.centeredText(font, ligne, width / 2, y, TEXTE);
            y += font.lineHeight + 2;
        }
    }
}
