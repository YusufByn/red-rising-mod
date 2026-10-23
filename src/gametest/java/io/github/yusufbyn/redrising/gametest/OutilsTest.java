package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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

    /**
     * Copie une capture d'écran dans {@code build/captures-gametest/}, hors du dossier de lancement des tests
     * (vidé à chaque lancement), pour pouvoir la regarder après coup.
     */
    static void garderCapture(Path capture) {
        // Le client de test tourne dans build/run/clientGameTest
        Path dossier = Path.of("").toAbsolutePath().resolve("../../captures-gametest").normalize();
        try {
            Files.createDirectories(dossier);
            Path copie = dossier.resolve(capture.getFileName());
            Files.copy(capture, copie, StandardCopyOption.REPLACE_EXISTING);
            RedRising.LOGGER.info("Capture : {}", copie);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
