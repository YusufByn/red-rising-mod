package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.client.EcranChoixCouleur;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.couleur.ModeNaissance;
import io.github.yusufbyn.redrising.couleur.Naissance;
import io.github.yusufbyn.redrising.net.ChoisirCouleurPayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * G4.2 : dans un monde en choix libre, on choisit sa Couleur à l'arrivée et on joue avec.
 * Passe par le vrai écran : clic sur le bouton « Obsidian ».
 */
public class ChoixLibreTest implements FabricClientGameTest {
    private static final Couleur CHOISIE = Couleur.OBSIDIAN;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext monde = context.worldBuilder()
                .adjustSettings(etat -> etat.getGameRules().set(Naissance.MODE, ModeNaissance.CHOIX_LIBRE, null))
                .create()) {
            // À l'arrivée : l'écran de choix, et pas encore de Couleur
            context.waitForScreen(EcranChoixCouleur.class);
            verifierCouleur(monde, context, "avant le choix", null);
            OutilsTest.garderCapture(context.takeScreenshot("choix-couleur"));

            // On ne peut pas y échapper : Échap ne le ferme pas, et fermé par un autre moyen, il revient
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);
            verifierEcranChoix(context, "après Échap");
            context.setScreen(() -> null);
            context.waitForScreen(EcranChoixCouleur.class);
            RedRising.LOGGER.info("[G4.2] Écran de choix toujours là après Échap et après fermeture forcée");

            // Le choix : l'écran se ferme quand le serveur a donné la Couleur
            context.clickScreenButton(CHOISIE.getTranslationKey());
            context.waitForScreen(null);
            verifierCouleur(monde, context, "après le choix", CHOISIE);

            // Le serveur décide : une seconde demande (client modifié) est refusée
            context.runOnClient(mc -> ClientPlayNetworking.send(new ChoisirCouleurPayload(Couleur.OR)));
            monde.getConnection().waitForServerboundPackets();
            monde.getConnection().waitForClientboundPackets();
            verifierCouleur(monde, context, "après une seconde demande", CHOISIE);

            // On joue avec : la Couleur reste après la mort, et l'écran ne revient pas
            OutilsTest.attendreArrivee(context);
            OutilsTest.mourirEtReapparaitre(context, monde);
            context.waitTicks(20);
            if (context.computeOnClient(mc -> mc.gui.screen()) != null) {
                throw new AssertionError("Un écran s'est ouvert après la réapparition : " + context.computeOnClient(mc -> mc.gui.screen()));
            }
            verifierCouleur(monde, context, "après la réapparition", CHOISIE);
        }
        RedRising.LOGGER.info("[G4.2] OK : Couleur choisie sur l'écran de choix libre, gardée en jeu");
    }

    private static void verifierEcranChoix(ClientGameTestContext context, String moment) {
        if (!context.computeOnClient(mc -> mc.gui.screen() instanceof EcranChoixCouleur)) {
            throw new AssertionError("Écran de choix fermé " + moment);
        }
    }

    /** Même Couleur côté serveur et côté client (synchronisée). */
    private static void verifierCouleur(TestSingleplayerContext monde, ClientGameTestContext context, String moment, @Nullable Couleur attendue) {
        Couleur serveur = monde.getServer().computeOnServer(server -> CouleurJoueur.get(OutilsTest.joueur(server)));
        Couleur client = context.computeOnClient(mc -> CouleurJoueur.get(Objects.requireNonNull(mc.player)));
        if (serveur != attendue || client != attendue) {
            throw new AssertionError("Couleur " + moment + " : attendu " + attendue + ", serveur " + serveur + ", client " + client);
        }
        RedRising.LOGGER.info("[G4.2] Couleur {} : {}", moment, attendue == null ? "aucune" : attendue.getSerializedName());
    }
}
