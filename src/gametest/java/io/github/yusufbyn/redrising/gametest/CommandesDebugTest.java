package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.data.EtatMonde;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.factions.Bornes;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.Jauges;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Issue;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * G2.5 : tout l'état se lit et se modifie avec les commandes {@code /rr}.
 */
public class CommandesDebugTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext monde = context.worldBuilder()
                .adjustSettings(reglages -> reglages.setAllowCommands(true))
                .create()) {
            OutilsTest.attendreArrivee(context);

            // Tapée par le joueur dans le vrai client : vérifie aussi la permission
            context.runOnClient(mc -> Objects.requireNonNull(mc.player).connection.sendCommand("rr couleur set or"));
            monde.getServer().waitFor(server -> CouleurJoueur.get(OutilsTest.joueur(server)) == Couleur.OR, 100);
            RedRising.LOGGER.info("[G2.5] Commande tapée dans le client : Couleur passée à or");

            monde.getServer().runOnServer(server -> {
                // Couleur
                attendreMessage(server, "rr couleur get", "commande.redrising.couleur.get");
                executer(server, "rr couleur set rouge");
                verifier("Couleur", Couleur.ROUGE, CouleurJoueur.get(OutilsTest.joueur(server)));
                attendreMessage(server, "rr couleur set bidon", "commande.redrising.inconnu");
                verifier("Couleur après une valeur inconnue", Couleur.ROUGE, CouleurJoueur.get(OutilsTest.joueur(server)));

                // Jauges
                attendreMessage(server, "rr jauge set loyaute 90", "commande.redrising.jauge.bornee");
                executer(server, "rr jauge set soutien 60");
                executer(server, "rr jauge add suspicion 15");
                executer(server, "rr jauge add suspicion -5");
                attendreMessage(server, "rr jauge get", "commande.redrising.jauge.get");
                verifier("Jauges", new Jauges(Bornes.PLAFOND_LOYAUTE_ROUGE, 60, 10), JaugesJoueur.get(OutilsTest.joueur(server)));

                // Jalons
                executer(server, "rr jalon set pendaison_eo divergente");
                executer(server, "rr jalon set entree_darrow canon");
                attendreMessage(server, "rr jalon list", "commande.redrising.jalon.list");
                executer(server, "rr jalon clear entree_darrow");
                attendreMessage(server, "rr jalon clear entree_darrow", "commande.redrising.jalon.absent");

                // État du monde
                executer(server, "rr etat epoque golden_son");
                executer(server, "rr etat zone set lykos rising");
                executer(server, "rr etat zone set agea societe");
                executer(server, "rr etat zone clear agea");
                attendreMessage(server, "rr etat", "commande.redrising.etat");
                verifier("État du monde",
                        EtatMonde.NOUVEAU.avecEpoque(Epoque.GOLDEN_SON).avecIssue("pendaison_eo", Issue.DIVERGENTE).avecZone("lykos", Camp.RISING),
                        EtatMondeServeur.get(server));

                // Cible explicite, depuis la console du serveur
                executerConsole(server, "rr couleur set vert " + OutilsTest.joueur(server).getPlainTextName());
                verifier("Couleur visée depuis la console", Couleur.VERT, CouleurJoueur.get(OutilsTest.joueur(server)));
            });
        }

        RedRising.LOGGER.info("[G2.5] OK : tout l'état se lit et se modifie avec /rr");
    }

    /** Exécute une commande comme le joueur, et renvoie les messages qu'elle lui a envoyés. */
    private static List<Component> executer(MinecraftServer server, String commande) {
        Capture capture = new Capture();
        server.getCommands().performPrefixedCommand(OutilsTest.joueur(server).createCommandSourceStack().withSource(capture), commande);
        RedRising.LOGGER.info("[G2.5] /{} -> {}", commande, capture.messages.stream().map(Component::getString).toList());
        return capture.messages;
    }

    private static void executerConsole(MinecraftServer server, String commande) {
        Capture capture = new Capture();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture), commande);
        RedRising.LOGGER.info("[G2.5] (console) /{} -> {}", commande, capture.messages.stream().map(Component::getString).toList());
    }

    /** Vérifie que la commande répond avec le message de clé de traduction attendue. */
    private static void attendreMessage(MinecraftServer server, String commande, String cle) {
        List<Component> messages = executer(server, commande);
        List<String> cles = new ArrayList<>();
        messages.forEach(message -> collecterCles(message, cles));
        if (!cles.contains(cle)) {
            throw new AssertionError("/" + commande + " : message " + cle + " attendu, reçu " + cles);
        }
    }

    /** Clés de traduction d'un message, y compris dans ses morceaux et ses arguments. */
    private static void collecterCles(Component message, List<String> cles) {
        if (message.getContents() instanceof TranslatableContents traduisible) {
            cles.add(traduisible.getKey());
            for (Object argument : traduisible.getArgs()) {
                if (argument instanceof Component sousMessage) {
                    collecterCles(sousMessage, cles);
                }
            }
        }
        message.getSiblings().forEach(morceau -> collecterCles(morceau, cles));
    }

    private static void verifier(String quoi, Object attendu, Object obtenu) {
        if (!attendu.equals(obtenu)) {
            throw new AssertionError(quoi + " : attendu " + attendu + ", obtenu " + obtenu);
        }
        RedRising.LOGGER.info("[G2.5] {} : {}", quoi, obtenu);
    }

    /** Destinataire des messages d'une commande, qui les garde au lieu de les afficher. */
    private static final class Capture implements CommandSource {
        final List<Component> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
