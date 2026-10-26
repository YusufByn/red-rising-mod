package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.CapacitesActives;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.couleur.ProfilCouleur;
import io.github.yusufbyn.redrising.couleur.ProfilsCouleurs;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

import java.util.Arrays;
import java.util.Objects;

/**
 * G3.3 : une capacité de test se déclenche avec la touche d'action, puis reste indisponible pendant sa recharge ;
 * le client connaît la recharge (HUD).
 *
 * <p>Le profil n'est déclaré que dans ce mod de test, sur une Couleur qu'aucun autre test n'utilise.</p>
 */
public class CapacitesActivesTest implements FabricClientGameTest {
    private static final Couleur COULEUR_TEST = Couleur.JAUNE;
    private static final int RECHARGE = 100; // 5 secondes
    private static final int GAIN = 5;

    @Override
    public void runTest(ClientGameTestContext context) {
        // La capacité de test, en quelques lignes : +5 de Soutien, 5 s de recharge
        ProfilsCouleurs.declarer(COULEUR_TEST, ProfilCouleur.builder()
                .capacite(RECHARGE, joueur -> {
                    JaugesJoueur.ajouter(joueur, Jauge.SOUTIEN, GAIN);
                    return true;
                })
                .build());

        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runOnServer(server -> {
                var joueur = OutilsTest.joueur(server);
                CouleurJoueur.set(joueur, COULEUR_TEST);
                JaugesJoueur.set(joueur, Jauge.SOUTIEN, 0);
            });
            context.waitFor(mc -> mc.player != null && CouleurJoueur.get(mc.player) == COULEUR_TEST, 40);

            // 1er appui : la capacité se déclenche
            appuyer(context);
            attendreSoutien(monde, GAIN, "après le 1er appui");
            // La recharge est arrivée côté client (c'est elle que le HUD affiche)
            context.waitFor(mc -> mc.player != null && CapacitesActives.ticksRestants(mc.player) > 0, 40);
            long rechargeClient = context.computeOnClient(mc -> CapacitesActives.ticksRestants(Objects.requireNonNull(mc.player)));
            RedRising.LOGGER.info("[G3.3] Recharge vue par le client : {} ticks", rechargeClient);

            // 2e appui pendant la recharge : rien ne se passe
            appuyer(context);
            context.waitTicks(10);
            verifierSoutien(monde, GAIN, "pendant la recharge");
            OutilsTest.garderCapture(context.takeScreenshot("capacite-recharge"));

            // Après la recharge : elle se déclenche de nouveau
            monde.getServer().waitFor(server -> CapacitesActives.ticksRestants(OutilsTest.joueur(server)) == 0, RECHARGE + 20);
            context.waitFor(mc -> mc.player != null && CapacitesActives.ticksRestants(mc.player) == 0, 40);
            appuyer(context);
            attendreSoutien(monde, 2 * GAIN, "après la recharge");

            // Une Couleur sans capacité : la touche ne fait rien
            monde.getServer().runOnServer(server -> CouleurJoueur.set(OutilsTest.joueur(server), Couleur.OR));
            context.waitFor(mc -> mc.player != null && CouleurJoueur.get(mc.player) == Couleur.OR, 40);
            monde.getServer().waitFor(server -> CapacitesActives.ticksRestants(OutilsTest.joueur(server)) == 0, RECHARGE + 20);
            appuyer(context);
            context.waitTicks(10);
            verifierSoutien(monde, 2 * GAIN, "Couleur sans capacité");
        }

        RedRising.LOGGER.info("[G3.3] OK : capacité déclenchée par la touche, avec recharge synchronisée");
    }

    /** Vrai appui sur la touche de capacité, trouvée par son nom dans les contrôles. */
    private static void appuyer(ClientGameTestContext context) {
        context.getInput().pressKey(CapacitesActivesTest::touche);
    }

    private static KeyMapping touche(Options options) {
        return Arrays.stream(options.keyMappings)
                .filter(touche -> touche.getName().equals("key.redrising.capacite"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Touche de capacité absente des contrôles"));
    }

    private static void attendreSoutien(TestSingleplayerContext monde, int attendu, String moment) {
        monde.getServer().waitFor(server -> JaugesJoueur.get(OutilsTest.joueur(server), Jauge.SOUTIEN) == attendu, 40);
        RedRising.LOGGER.info("[G3.3] Soutien {} : {}", moment, attendu);
    }

    private static void verifierSoutien(TestSingleplayerContext monde, int attendu, String moment) {
        int soutien = monde.getServer().computeOnServer(server -> JaugesJoueur.get(OutilsTest.joueur(server), Jauge.SOUTIEN));
        if (soutien != attendu) {
            throw new AssertionError("Soutien " + moment + " : attendu " + attendu + ", obtenu " + soutien);
        }
        RedRising.LOGGER.info("[G3.3] Soutien {} : {} (inchangé)", moment, soutien);
    }
}
