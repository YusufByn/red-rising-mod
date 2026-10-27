package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.data.EtatMonde;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.jalons.Echeance;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Horloge;
import io.github.yusufbyn.redrising.jalons.HorlogeMonde;
import io.github.yusufbyn.redrising.jalons.Issue;
import io.github.yusufbyn.redrising.jalons.Jalon;
import io.github.yusufbyn.redrising.jalons.Jalons;
import io.github.yusufbyn.redrising.jalons.JalonsEvents;
import io.github.yusufbyn.redrising.jalons.MoteurJalons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static io.github.yusufbyn.redrising.jalons.Echeance.jours;

/**
 * G6.2 : un jalon de test se déclenche avec chacune des deux issues. Aussi : l'aide l'avance et le tort le recule ;
 * il passe quelle que soit la dimension du joueur, sauf un jalon {@code surPlace} qui attend un joueur dans l'Overworld ;
 * l'époque change ; un jalon fixe garde son issue ; le passage est sur le disque tout de suite ; un jalon passé ne
 * repasse pas au rechargement, mais ses effets restés en attente font repartir l'événement, une fois ; un jalon en
 * route garde son départ au rechargement ; {@code /rr jalon declencher}.
 *
 * <p>Les jalons de test sont déclarés par le test et retirés à la fin : les vrais mondes ne les voient jamais.</p>
 */
public class JalonTest implements FabricClientGameTest {
    private static final String TEST = "test_g62";
    private static final String SUR_PLACE = "test_g62_sur_place";
    private static final String FIXE = "test_g62_fixe";

    /** Événements reçus : « jalon issue raison ». Rempli sur le fil du serveur, lu par le test. */
    private static final List<String> EVENEMENTS = Collections.synchronizedList(new ArrayList<>());
    private static boolean abonne;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!abonne) {
            JalonsEvents.PASSE.register((server, jalon, issue, raison) ->
                    EVENEMENTS.add(jalon + " " + issue.getSerializedName() + " " + raison.name().toLowerCase()));
            abonne = true;
        }
        // Base 2 jours, entre 1 et 3 ; ouvre Golden Son
        Jalons.declarer(Jalon.builder(TEST).epoque(Epoque.RED_RISING).echeance(jours(2), jours(1), jours(3), 0.05)
                .issue(bilan -> bilan.rythme() >= 0 ? Issue.CANON : Issue.DIVERGENTE).ouvre(Epoque.GOLDEN_SON).build());
        // À vivre sur place, dû au bout d'un jour
        Jalons.declarer(Jalon.builder(SUR_PLACE).epoque(Epoque.RED_RISING).echeance(jours(1), jours(1), jours(1), 0)
                .fixe(Issue.DIVERGENTE).surPlace(Level.OVERWORLD).build());
        // Fixe, après le premier, à l'époque qu'il ouvre
        Jalons.declarer(Jalon.builder(FIXE).epoque(Epoque.GOLDEN_SON).apres(TEST).echeance(jours(1), jours(1), jours(1), 0)
                .fixe(Issue.CANON).build());
        try {
            issueCanon(context);
            issueDivergente(context);
        } finally {
            Jalons.retirer(TEST);
            Jalons.retirer(SUR_PLACE);
            Jalons.retirer(FIXE);
        }
        RedRising.LOGGER.info("[G6.2] OK : un jalon de test se déclenche avec chacune des deux issues");
    }

    // --- Monde 1 : l'aide l'avance, issue canon ---

    private static void issueCanon(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            attendreEnRoute(monde, TEST, SUR_PLACE);
            EVENEMENTS.clear();

            // Le joueur est dans le Nether : un jalon ordinaire passe quand même, un jalon « sur place » attend
            monde.getServer().runCommand("execute in minecraft:the_nether run tp @a[limit=1] 0.5 128 0.5");
            context.waitTicks(20);
            // Un jour de jeu, rythme nul : le jalon de test (2 jours) n'est pas dû ; celui sur place l'est, mais attend
            avancer(monde, Echeance.JOUR, 0, 0);
            context.waitTicks(60);
            verifierEvenements("un jour, joueur dans le Nether", List.of());
            // 20 d'aide sur ce jour : rythme 20, délai ramené à 1 jour, il passe (le joueur est toujours dans le Nether)
            avancer(monde, 0, 20, 0);
            monde.getServer().waitFor(server -> EtatMondeServeur.get(server).jalons().containsKey(TEST), 60);
            verifierEvenements("aide", List.of(TEST + " canon echeance"));
            EtatMonde etat = etat(monde);
            if (etat.epoque() != Epoque.GOLDEN_SON || !etat.effetsEnAttente().contains(TEST) || etat.enRoute().containsKey(TEST)) {
                throw new AssertionError("Après le passage : " + etat);
            }
            // Sauvegardé tout de suite
            String fichier = fichier(monde.getWorldSave().getSaveDirectory());
            if (!fichier.contains(TEST + ":\"canon\"")) {
                throw new AssertionError("Jalon passé absent du fichier du monde : " + fichier);
            }
            RedRising.LOGGER.info("[G6.2] Aide : le jalon passe au bout d'un jour au lieu de deux, en canon, joueur dans le Nether ; époque Golden Son ; sur le disque");

            // De retour dans l'Overworld : le jalon « sur place » passe
            monde.getServer().runCommand("execute in minecraft:overworld run tp @a[limit=1] 0.5 -60 0.5");
            monde.getServer().waitFor(server -> EtatMondeServeur.get(server).jalons().containsKey(SUR_PLACE), 60);
            verifierEvenements("retour dans l'Overworld", List.of(TEST + " canon echeance", SUR_PLACE + " divergente echeance"));
            RedRising.LOGGER.info("[G6.2] Jalon sur place : attend le retour du joueur dans l'Overworld, puis passe");

            // Le jalon fixe se met en route (époque Golden Son, après le premier) ; beaucoup de tort : canon quand même
            attendreEnRoute(monde, FIXE);
            avancer(monde, Echeance.JOUR, 0, 500);
            monde.getServer().waitFor(server -> EtatMondeServeur.get(server).jalons().containsKey(FIXE), 60);
            if (etat(monde).jalons().get(FIXE) != Issue.CANON) {
                throw new AssertionError("Jalon fixe : issue " + etat(monde).jalons().get(FIXE));
            }
            RedRising.LOGGER.info("[G6.2] Jalon fixe : canon malgré 500 de tort");

            // Les effets de deux jalons sont appliqués (ce que fera G6.3), ceux du premier restent en attente
            monde.getServer().runOnServer(server -> {
                MoteurJalons.effetsAppliques(server, SUR_PLACE);
                MoteurJalons.effetsAppliques(server, FIXE);
            });
            sauvegarde = monde.getWorldSave();
        }

        // Rechargement : rien ne repasse ; l'événement du jalon aux effets en attente repart, une fois, sans rien changer
        EVENEMENTS.clear();
        EtatMonde avant;
        try (TestSingleplayerContext monde = sauvegarde.open()) {
            monde.getServer().waitFor(server -> !server.getPlayerList().getPlayers().isEmpty(), 100);
            context.waitTicks(60);
            verifierEvenements("rechargement, effets en attente", List.of(TEST + " canon rejeu"));
            avant = etat(monde);
            if (avant.epoque() != Epoque.GOLDEN_SON || avant.jalons().size() != 3 || !avant.effetsEnAttente().equals(Set.of(TEST))) {
                throw new AssertionError("Après le rejeu : " + avant);
            }
            monde.getServer().runOnServer(server -> MoteurJalons.effetsAppliques(server, TEST));
        }
        EVENEMENTS.clear();
        try (TestSingleplayerContext monde = sauvegarde.open()) {
            monde.getServer().waitFor(server -> !server.getPlayerList().getPlayers().isEmpty(), 100);
            context.waitTicks(60);
            verifierEvenements("rechargement, effets appliqués", List.of());
        }
        RedRising.LOGGER.info("[G6.2] Rechargement : aucun jalon ne repasse ; effets en attente : l'événement repart une fois, puis plus");
    }

    // --- Monde 2 : le tort le recule, issue divergente ---

    private static void issueDivergente(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        Horloge depart;
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            attendreEnRoute(monde, TEST, SUR_PLACE);
            EVENEMENTS.clear();
            // Forcer un jalon par la commande, avec l'issue voulue
            monde.getServer().runCommand("rr jalon prochains");
            monde.getServer().runCommand("rr jalon declencher " + SUR_PLACE + " canon");
            monde.getServer().waitFor(server -> EtatMondeServeur.get(server).jalons().get(SUR_PLACE) == Issue.CANON, 20);
            verifierEvenements("jalon forcé", List.of(SUR_PLACE + " canon commande"));
            EVENEMENTS.clear();
            // 20 de tort en un jour : rythme -20, délai repoussé à 3 jours
            avancer(monde, Echeance.JOUR, 0, 20);
            depart = etat(monde).enRoute().get(TEST);
            sauvegarde = monde.getWorldSave();
        }
        try (TestSingleplayerContext monde = sauvegarde.open()) {
            monde.getServer().waitFor(server -> !server.getPlayerList().getPlayers().isEmpty(), 100);
            // Le jalon forcé a ses effets en attente : son événement repart au chargement
            verifierEvenements("rechargement après un jalon forcé", List.of(SUR_PLACE + " canon rejeu"));
            EVENEMENTS.clear();
            if (!depart.equals(etat(monde).enRoute().get(TEST))) {
                throw new AssertionError("Jalon en route : départ " + etat(monde).enRoute().get(TEST) + " après rechargement, " + depart + " avant");
            }
            // Deux jours (la base) : rythme -10, délai 2,5 jours, pas encore
            avancer(monde, Echeance.JOUR, 0, 0);
            context.waitTicks(60);
            verifierEvenements("deux jours de tort", List.of());
            // Trois jours (le maximum) : il passe, en divergente
            avancer(monde, Echeance.JOUR, 0, 0);
            monde.getServer().waitFor(server -> EtatMondeServeur.get(server).jalons().containsKey(TEST), 60);
            verifierEvenements("trois jours de tort", List.of(TEST + " divergente echeance"));
        }
        RedRising.LOGGER.info("[G6.2] Tort : le jalon passe plus tard (pas à 2 jours, à 3), en divergente ; départ gardé au rechargement ; /rr jalon declencher");
    }

    // --- Outils ---

    /** Avance l'horloge du monde comme si un joueur avait joué ce temps, avec ce Soutien. */
    private static void avancer(TestSingleplayerContext monde, long ticks, int aide, int tort) {
        monde.getServer().runOnServer(server -> HorlogeMonde.modifier(server, horloge ->
                horloge.avancer(ticks, 1).compter(aide).compter(-tort)));
    }

    private static void attendreEnRoute(TestSingleplayerContext monde, String... jalons) {
        monde.getServer().waitFor(server -> {
            EtatMonde etat = EtatMondeServeur.get(server);
            for (String jalon : jalons) {
                if (!etat.enRoute().containsKey(jalon)) {
                    return false;
                }
            }
            return true;
        }, 60);
    }

    private static EtatMonde etat(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(EtatMondeServeur::get);
    }

    private static void verifierEvenements(String cas, List<String> attendus) {
        List<String> recus;
        synchronized (EVENEMENTS) {
            recus = List.copyOf(EVENEMENTS);
        }
        if (!recus.equals(attendus)) {
            throw new AssertionError(cas + " : événements " + recus + ", attendu " + attendus);
        }
    }

    private static String fichier(Path dossierMonde) {
        Path fichier = dossierMonde.resolve("data/fabric/attachments.dat");
        try {
            return NbtIo.readCompressed(fichier, NbtAccounter.unlimitedHeap()).toString();
        } catch (IOException e) {
            throw new AssertionError("Lecture impossible : " + fichier, e);
        }
    }
}
