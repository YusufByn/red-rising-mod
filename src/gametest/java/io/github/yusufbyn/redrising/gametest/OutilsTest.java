package io.github.yusufbyn.redrising.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Gestes communs des tests en jeu. */
final class OutilsTest {
    private OutilsTest() {
    }

    /** Le seul joueur du monde de test (côté serveur). */
    static ServerPlayer joueur(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    /** Juste après l'entrée dans le monde, certaines commandes (dont /kill) n'ont pas encore d'effet. */
    static void attendreArrivee(ClientGameTestContext context) {
        context.waitTicks(100);
    }

    /** Tue le joueur et clique sur « Réapparaître », comme un joueur le ferait. */
    static void mourirEtReapparaitre(ClientGameTestContext context, TestSingleplayerContext monde) {
        monde.getServer().runCommand("kill @a");
        context.waitForScreen(DeathScreen.class);
        // Le bouton « Réapparaître » n'est actif qu'après un court délai
        context.waitTicks(40);
        context.clickScreenButton("deathScreen.respawn");
        context.waitForScreen(null);
    }
}
