package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.Jauges;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * G2.6 : les 3 jauges sont visibles en jeu. Prend une capture d'écran du HUD,
 * copiée dans {@code build/captures-gametest/} pour être regardée après le test.
 */
public class HudJaugesTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext monde = context.worldBuilder().create()) {
            OutilsTest.attendreArrivee(context);
            monde.getConnection().waitForChunksRender();

            Jauges attendues = new Jauges(35, 70, 20);
            monde.getServer().runOnServer(server -> {
                var joueur = OutilsTest.joueur(server);
                CouleurJoueur.set(joueur, Couleur.ROUGE);
                JaugesJoueur.set(joueur, Jauge.LOYAUTE, attendues.loyaute());
                JaugesJoueur.set(joueur, Jauge.SOUTIEN, attendues.soutien());
                JaugesJoueur.set(joueur, Jauge.SUSPICION, attendues.suspicion());
            });
            // Le HUD affiche ce que le client a reçu
            context.waitFor(mc -> mc.player != null && attendues.equals(JaugesJoueur.get(mc.player)), 100);

            OutilsTest.garderCapture(context.takeScreenshot("hud-jauges"));
        }
        RedRising.LOGGER.info("[G2.6] OK : HUD affiché avec les jauges 35 / 70 / 20");
    }
}
