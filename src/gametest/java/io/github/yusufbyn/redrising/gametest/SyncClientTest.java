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
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * G2.3 : le client lit toujours la Couleur et les jauges du serveur,
 * au départ, après chaque changement, après la mort et après le rechargement.
 */
public class SyncClientTest implements FabricClientGameTest {
    /** Nombre de ticks laissés aux paquets pour arriver au client. */
    private static final int DELAI_SYNC = 100;

    /** Ce qu'un côté (serveur ou client) sait du joueur. */
    private record Etat(@Nullable Couleur couleur, Jauges jauges) {
        static Etat de(Player joueur) {
            return new Etat(CouleurJoueur.get(joueur), JaugesJoueur.get(joueur));
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave sauvegarde;

        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            verifierClientIdentique("au départ", context, monde);

            monde.getServer().runOnServer(server -> {
                var joueur = OutilsTest.joueur(server);
                CouleurJoueur.set(joueur, Couleur.VIOLET);
                JaugesJoueur.set(joueur, Jauge.LOYAUTE, 60);
                JaugesJoueur.set(joueur, Jauge.SOUTIEN, 30);
                JaugesJoueur.set(joueur, Jauge.SUSPICION, 10);
            });
            verifierClientIdentique("après modification des jauges", context, monde);

            // Seule la Couleur change : le client doit borner la Loyauté (60) comme le serveur
            monde.getServer().runOnServer(server -> CouleurJoueur.set(OutilsTest.joueur(server), Couleur.ROUGE));
            Etat apresRouge = verifierClientIdentique("après passage au Rouge", context, monde);
            if (apresRouge.jauges().loyaute() != Bornes.PLAFOND_LOYAUTE_ROUGE) {
                throw new AssertionError("Loyauté du Rouge non plafonnée côté client : " + apresRouge);
            }

            OutilsTest.mourirEtReapparaitre(context, monde);
            verifierClientIdentique("après la mort", context, monde);

            sauvegarde = monde.getWorldSave();
        }

        try (TestSingleplayerContext monde = sauvegarde.open()) {
            Etat apresRechargement = verifierClientIdentique("après rechargement", context, monde);
            if (apresRechargement.couleur() != Couleur.ROUGE) {
                throw new AssertionError("Couleur perdue au rechargement : " + apresRechargement);
            }
        }

        RedRising.LOGGER.info("[G2.3] OK : le client lit toujours les valeurs du serveur");
    }

    /** Attend que le client lise la même chose que le serveur ; échoue avec les deux états sinon. */
    private static Etat verifierClientIdentique(String moment, ClientGameTestContext context, TestSingleplayerContext monde) {
        Etat serveur = monde.getServer().computeOnServer(server -> Etat.de(OutilsTest.joueur(server)));
        try {
            context.waitFor(mc -> serveur.equals(etatClient(mc)), DELAI_SYNC);
        } catch (AssertionError e) {
            Etat client = context.computeOnClient(SyncClientTest::etatClient);
            throw new AssertionError("Client désynchronisé " + moment + " : serveur " + serveur + ", client " + client, e);
        }
        RedRising.LOGGER.info("[G2.3] Client identique au serveur {} : {}", moment, serveur);
        return serveur;
    }

    private static @Nullable Etat etatClient(Minecraft mc) {
        return mc.player == null ? null : Etat.de(mc.player);
    }
}
