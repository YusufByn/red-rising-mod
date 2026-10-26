package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.client.EcranResultatNaissance;
import io.github.yusufbyn.redrising.client.EcranTestNaissance;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.couleur.Naissance;
import io.github.yusufbyn.redrising.couleur.TestEnCours;
import io.github.yusufbyn.redrising.couleur.TestPersonnalite;
import io.github.yusufbyn.redrising.monde.SceneNaissance;
import io.github.yusufbyn.redrising.net.ConclureTestPayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * G4.4 et G4.5 : un test de naissance complet donne une Couleur, et il se déroule dans sa scène. Passe par les vrais écrans : clics sur les réponses,
 * puis sur « Refaire le test » et « Accepter ».
 *
 * <p>Les réponses sont choisies pour qu'une seule Couleur soit en tête (pas de hasard) :
 * Obsidian au premier passage, Jaune après la relance.</p>
 */
public class TestNaissanceEnJeuTest implements FabricClientGameTest {
    /** Lame, frapper, refuser en face... : Obsidian 4 points, seule en tête. */
    private static final int[] REPONSES_OBSIDIAN = {1, 0, 0, 1, 0, 2, 2};
    /** Soigner, veiller, le silence d'un temple... : Jaune 4 points, seule en tête. */
    private static final int[] REPONSES_JAUNE = {2, 3, 1, 3, 1, 2, 3};

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave sauvegarde;

        // Monde par défaut : mode Test
        try (TestSingleplayerContext monde = context.worldBuilder().create()) {
            context.waitForScreen(EcranTestNaissance.class);
            // Le jeu est en pause pendant le test : on laisse juste au vitrail le temps de s'afficher avant la capture
            context.waitTicks(20);
            verifierEtat(monde, "à l'arrivée", null, TestEnCours.DEBUT, true);
            OutilsTest.garderCapture(context.takeScreenshot("test-question"));
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);
            context.waitForScreen(EcranTestNaissance.class);

            // Premier passage : le résultat attend d'être accepté
            repondre(context, REPONSES_OBSIDIAN);
            context.waitForScreen(EcranResultatNaissance.class);
            verifierEtat(monde, "après le premier passage", null, new TestEnCours(Couleur.OBSIDIAN, false), true);
            OutilsTest.garderCapture(context.takeScreenshot("test-resultat"));

            // Relance : tout le test recommence
            context.clickScreenButton(EcranResultatNaissance.CLE_RELANCER);
            context.waitForScreen(EcranTestNaissance.class);
            verifierEtat(monde, "après la relance", null, new TestEnCours(null, true), true);

            repondre(context, REPONSES_JAUNE);
            context.waitForScreen(EcranResultatNaissance.class);
            verifierEtat(monde, "après le second passage", null, new TestEnCours(Couleur.JAUNE, true), true);
            if (context.tryClickScreenButton(EcranResultatNaissance.CLE_RELANCER)) {
                throw new AssertionError("Bouton « Refaire le test » proposé une seconde fois");
            }
            OutilsTest.garderCapture(context.takeScreenshot("test-resultat-definitif"));

            // Le serveur décide : une seconde relance (client modifié) est refusée
            context.runOnClient(mc -> ClientPlayNetworking.send(new ConclureTestPayload(true)));
            monde.getConnection().waitForServerboundPackets();
            verifierEtat(monde, "après une seconde relance forcée", null, new TestEnCours(Couleur.JAUNE, true), true);

            sauvegarde = monde.getWorldSave();
        }

        // Se déconnecter devant son résultat ne rend pas la relance : on retrouve le même résultat, définitif
        try (TestSingleplayerContext monde = sauvegarde.open()) {
            context.waitForScreen(EcranResultatNaissance.class);
            verifierEtat(monde, "après rechargement", null, new TestEnCours(Couleur.JAUNE, true), true);
            if (context.tryClickScreenButton(EcranResultatNaissance.CLE_RELANCER)) {
                throw new AssertionError("Bouton « Refaire le test » revenu après rechargement");
            }

            // Accepter : le joueur est né, l'écran se ferme
            context.clickScreenButton(EcranResultatNaissance.CLE_ACCEPTER);
            context.waitForScreen(null);
            verifierEtat(monde, "après acceptation", Couleur.JAUNE, null, false);
            Couleur client = context.computeOnClient(mc -> CouleurJoueur.get(Objects.requireNonNull(mc.player)));
            if (client != Couleur.JAUNE) {
                throw new AssertionError("Couleur côté client : " + client);
            }

            // Debug : /rr naissance reset remet le joueur au début du test
            monde.getServer().runCommand("rr naissance reset @a[limit=1]");
            context.waitForScreen(EcranTestNaissance.class);
            verifierEtat(monde, "après /rr naissance reset", null, TestEnCours.DEBUT, true);
            monde.getServer().runOnServer(server -> Naissance.donnerCouleur(OutilsTest.joueur(server), Couleur.JAUNE));
            context.waitForScreen(null);
        }
        RedRising.LOGGER.info("[G4.4] OK : test complet passé par les écrans, relance unique, Couleur Jaune acceptée");
        RedRising.LOGGER.info("[G4.5] OK : test passé dans la scène de naissance, retour au point d'apparition une fois né");
    }

    /** Clique, question après question, sur la réponse voulue (par son texte). */
    private static void repondre(ClientGameTestContext context, int[] reponses) {
        for (int question = 0; question < reponses.length; question++) {
            context.clickScreenButton(TestPersonnalite.cleReponse(question, reponses[question]));
        }
    }

    /** {@code dansLaScene} : le test se passe dans la scène de naissance (G4.5), qu'on quitte une fois né. */
    private static void verifierEtat(TestSingleplayerContext monde, String moment, @Nullable Couleur couleur, @Nullable TestEnCours test, boolean dansLaScene) {
        record Etat(@Nullable Couleur couleur, @Nullable TestEnCours test, boolean dansLaScene) {
        }
        Etat attendu = new Etat(couleur, test, dansLaScene);
        Etat obtenu = monde.getServer().computeOnServer(server -> {
            var joueur = OutilsTest.joueur(server);
            return new Etat(CouleurJoueur.get(joueur), Naissance.testEnCours(joueur), SceneNaissance.dans(joueur));
        });
        if (!attendu.equals(obtenu)) {
            throw new AssertionError("Naissance " + moment + " : attendu " + attendu + ", obtenu " + obtenu);
        }
        RedRising.LOGGER.info("[G4.4] Naissance {} : {}", moment, obtenu);
    }
}
