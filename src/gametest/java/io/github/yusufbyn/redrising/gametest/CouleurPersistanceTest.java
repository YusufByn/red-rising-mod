package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

/**
 * G2.1 : la Couleur survit à la mort et au rechargement du monde.
 *
 * <p>Lancé par {@code ./gradlew runClientGameTest} : un vrai client s'ouvre, crée un monde,
 * tue le joueur, clique sur « Réapparaître », quitte puis rouvre le monde.</p>
 */
public class CouleurPersistanceTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        Couleur depart;

        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            depart = couleurDuJoueur(monde);
            if (depart == null) {
                throw new AssertionError("Le joueur n'a pas de Couleur en arrivant dans le monde");
            }

            OutilsTest.attendreArrivee(context);
            OutilsTest.mourirEtReapparaitre(context, monde);
            verifier("après la mort", depart, couleurDuJoueur(monde));

            sauvegarde = monde.getWorldSave();
        }

        try (TestSingleplayerContext monde = sauvegarde.open()) {
            verifier("après rechargement", depart, couleurDuJoueur(monde));
        }

        RedRising.LOGGER.info("[G2.1] OK : Couleur {} conservée après la mort et le rechargement", depart.getSerializedName());
    }

    private static Couleur couleurDuJoueur(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(server -> CouleurJoueur.get(OutilsTest.joueur(server)));
    }

    private static void verifier(String moment, Couleur attendue, Couleur obtenue) {
        if (attendue != obtenue) {
            throw new AssertionError("Couleur " + moment + " : attendu " + attendue + ", obtenu " + obtenue);
        }
        RedRising.LOGGER.info("[G2.1] Couleur {} : {}", moment, obtenue.getSerializedName());
    }
}
