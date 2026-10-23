package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.factions.Bornes;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.Jauges;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

/**
 * G2.2 : les 3 jauges sont bornées (plafond de Loyauté du Rouge compris),
 * et survivent à la mort et au rechargement du monde.
 */
public class JaugesPersistanceTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        Jauges attendues = new Jauges(Bornes.PLAFOND_LOYAUTE_ROUGE, 70, 25);

        try (TestSingleplayerContext monde = context.worldBuilder().create()) {
            OutilsTest.attendreArrivee(context);

            monde.getServer().runOnServer(server -> {
                var joueur = OutilsTest.joueur(server);
                verifier("au départ", Jauges.DEPART.borner(CouleurJoueur.get(joueur)), JaugesJoueur.get(joueur));

                CouleurJoueur.set(joueur, Couleur.ROUGE);
                verifierValeur("Loyauté d'un Rouge mise à 100", Bornes.PLAFOND_LOYAUTE_ROUGE, JaugesJoueur.set(joueur, Jauge.LOYAUTE, 100));
                verifierValeur("Soutien mis à 70", 70, JaugesJoueur.set(joueur, Jauge.SOUTIEN, 70));
                verifierValeur("Suspicion moins 10", 0, JaugesJoueur.ajouter(joueur, Jauge.SUSPICION, -10));
                verifierValeur("Suspicion plus 25", 25, JaugesJoueur.ajouter(joueur, Jauge.SUSPICION, 25));
                verifier("après modifications", attendues, JaugesJoueur.get(joueur));
            });

            OutilsTest.mourirEtReapparaitre(context, monde);
            verifier("après la mort", attendues, jaugesDuJoueur(monde));

            sauvegarde = monde.getWorldSave();
        }

        try (TestSingleplayerContext monde = sauvegarde.open()) {
            verifier("après rechargement", attendues, jaugesDuJoueur(monde));

            // Changer de Couleur ne laisse pas une jauge hors de ses nouvelles bornes
            monde.getServer().runOnServer(server -> {
                var joueur = OutilsTest.joueur(server);
                CouleurJoueur.set(joueur, Couleur.OR);
                verifierValeur("Loyauté d'un Or mise à 90", 90, JaugesJoueur.set(joueur, Jauge.LOYAUTE, 90));
                CouleurJoueur.set(joueur, Couleur.ROUGE);
                verifierValeur("Loyauté relue après retour au Rouge", Bornes.PLAFOND_LOYAUTE_ROUGE, JaugesJoueur.get(joueur, Jauge.LOYAUTE));
            });
        }

        RedRising.LOGGER.info("[G2.2] OK : jauges bornées et conservées après la mort et le rechargement");
    }

    private static Jauges jaugesDuJoueur(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(server -> JaugesJoueur.get(OutilsTest.joueur(server)));
    }

    private static void verifier(String moment, Jauges attendues, Jauges obtenues) {
        if (!attendues.equals(obtenues)) {
            throw new AssertionError("Jauges " + moment + " : attendu " + attendues + ", obtenu " + obtenues);
        }
        RedRising.LOGGER.info("[G2.2] Jauges {} : {}", moment, obtenues);
    }

    private static void verifierValeur(String action, int attendue, int obtenue) {
        if (attendue != obtenue) {
            throw new AssertionError(action + " : attendu " + attendue + ", obtenu " + obtenue);
        }
        RedRising.LOGGER.info("[G2.2] {} : {}", action, obtenue);
    }
}
