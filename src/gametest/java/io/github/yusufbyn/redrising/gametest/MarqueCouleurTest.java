package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

import java.util.Objects;

/**
 * G3.5 : la Couleur d'un joueur se voit sur son modèle. Captures du joueur vu de face (vue à la troisième personne),
 * corps droit puis tourné, copiées dans {@code build/captures-gametest/} pour être regardées.
 */
public class MarqueCouleurTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext monde = context.worldBuilder().create()) {
            OutilsTest.attendreArrivee(context);
            monde.getConnection().waitForChunksRender();
            monde.getServer().runOnServer(server -> CouleurJoueur.set(OutilsTest.joueur(server), Couleur.ROUGE));
            context.waitFor(mc -> mc.player != null && CouleurJoueur.get(mc.player) == Couleur.ROUGE, 40);

            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.getInput().lookAt(0, 10);
            context.waitTicks(5);
            OutilsTest.garderCapture(context.takeScreenshot("marque-face"));

            // Corps tourné, tête vers la caméra : on voit le dos des mains. Le jeu est en pause entre deux ticks,
            // donc la rotation imposée tient le temps de la capture.
            for (int angle : new int[]{45, -45}) {
                context.runOnClient(mc -> {
                    var joueur = Objects.requireNonNull(mc.player);
                    joueur.yBodyRot = joueur.getYRot() + angle;
                    joueur.yBodyRotO = joueur.yBodyRot;
                });
                OutilsTest.garderCapture(context.takeScreenshot("marque-" + (angle > 0 ? "gauche" : "droite")));
            }

            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        }
        RedRising.LOGGER.info("[G3.5] OK : captures de la marque prises (à vérifier à l'œil)");
    }
}
