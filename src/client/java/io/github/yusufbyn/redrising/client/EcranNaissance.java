package io.github.yusufbyn.redrising.client;

import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.monde.SceneNaissance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Base des écrans de naissance (G4) : choix libre, questions du test, résultat.
 * Provisoires, prévus pour être remplacés (interface finale en G17).
 *
 * <ul>
 *     <li>On ne peut pas les fermer sans être né : Échap ne fait rien, et {@link NaissanceClient} rouvre l'écran
 *     s'il disparaît (mort, autre écran).</li>
 *     <li>Dans la scène de naissance (G4.5), pas de flou ni de voile : on voit le vitrail derrière les questions.</li>
 *     <li>Un clic qui envoie une demande au serveur désactive les boutons : le serveur répond en donnant la Couleur
 *     (l'écran se ferme) ou en demandant l'écran suivant (il remplace celui-ci).</li>
 * </ul>
 */
public abstract class EcranNaissance extends Screen {
    protected static final int TEXTE = 0xFFFFFFFF;
    protected static final int TEXTE_DISCRET = 0xFFA0A0A0;
    protected static final int ESPACE = 4;

    private final List<Button> boutons = new ArrayList<>();

    protected EcranNaissance(Component titre) {
        super(titre);
    }

    @Override
    protected final void init() {
        boutons.clear();
        placerBoutons();
    }

    /** Ajoute les boutons de l'écran avec {@link #bouton}. */
    protected abstract void placerBoutons();

    protected Button bouton(Component texte, int x, int y, int largeur, Runnable action) {
        Button bouton = Button.builder(texte, b -> action.run()).bounds(x, y, largeur, Button.DEFAULT_HEIGHT).build();
        boutons.add(bouton);
        return addRenderableWidget(bouton);
    }

    /** Haut du premier bouton (pour placer le texte au-dessus). */
    protected int hautDesBoutons() {
        return boutons.isEmpty() ? height / 2 : boutons.getFirst().getY();
    }

    /** En attendant la réponse du serveur, pas de second clic. */
    protected void attendreServeur() {
        boutons.forEach(bouton -> bouton.active = false);
    }

    @Override
    public void tick() {
        if (minecraft.player != null && CouleurJoueur.get(minecraft.player) != null) {
            onClose();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int sourisX, int sourisY, float partialTick) {
        if (dansLaScene(minecraft)) {
            // Juste une ombre légère derrière le texte, en haut de l'écran
            graphics.fillGradient(0, 0, width, height / 2, 0x80000000, 0x00000000);
        } else {
            super.extractBackground(graphics, sourisX, sourisY, partialTick);
        }
    }

    /** Le joueur est dans la scène de naissance (dimension lue côté client). */
    public static boolean dansLaScene(Minecraft mc) {
        return mc.level != null && mc.level.dimension().equals(SceneNaissance.DIMENSION);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
