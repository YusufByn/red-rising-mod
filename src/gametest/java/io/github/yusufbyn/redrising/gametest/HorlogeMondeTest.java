package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.data.EtatMonde;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.factions.Variation;
import io.github.yusufbyn.redrising.jalons.ContributionDuJour;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Horloge;
import io.github.yusufbyn.redrising.jalons.HorlogeMonde;
import io.github.yusufbyn.redrising.jalons.Issue;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ServerLevelData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * G6.1 : l'horloge du monde. Le temps de jeu avance quand un joueur est connecté, et ni {@code /time} ni le sommeil ne
 * le font sauter ; le Soutien des actions compte pour le monde (aide, tort, plafond du jour) ; les deux compteurs
 * survivent au rechargement ; un monde v1 (sans horloge) est migré en v2.
 */
public class HorlogeMondeTest implements FabricClientGameTest {
    private static final BlockPos LIT = new BlockPos(2, -60, 3);

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        Horloge avantDeQuitter;
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runCommand("difficulty peaceful");

            // --- Le temps de jeu avance avec le joueur connecté ---
            long debut = temps(monde);
            context.waitTicks(100);
            long ecoule = temps(monde) - debut;
            if (ecoule < 80 || ecoule > 120) {
                throw new AssertionError("Temps de jeu : " + ecoule + " ticks comptés en 100 (attendu 100, à une seconde près)");
            }

            // --- /time ne le fait pas sauter ---
            long avant = temps(monde);
            monde.getServer().runCommand("time set midnight");
            monde.getServer().runCommand("time add 12000");
            context.waitTicks(10);
            if (temps(monde) - avant > 40) {
                throw new AssertionError("Temps de jeu : /time l'a fait sauter de " + (temps(monde) - avant));
            }
            RedRising.LOGGER.info("[G6.1] Temps de jeu : {} ticks en 100, /time ne le fait pas sauter", ecoule);

            dormir(monde, context);
            soutien(monde);

            avantDeQuitter = monde.getServer().computeOnServer(HorlogeMonde::get);
            sauvegarde = monde.getWorldSave();
        }

        // --- Rechargement : les deux compteurs sont relus ---
        try (TestSingleplayerContext monde = sauvegarde.open()) {
            Horloge relue = monde.getServer().computeOnServer(HorlogeMonde::get);
            if (relue.aide() != avantDeQuitter.aide() || relue.tort() != avantDeQuitter.tort()
                    || relue.temps() < avantDeQuitter.temps() || relue.temps() > avantDeQuitter.temps() + 2_000) {
                throw new AssertionError("Horloge après rechargement : " + relue + " (avant de quitter : " + avantDeQuitter + ")");
            }
            RedRising.LOGGER.info("[G6.1] Après rechargement : {} (avant de quitter : {})", relue, avantDeQuitter);
        }

        mondeV1(sauvegarde);
        RedRising.LOGGER.info("[G6.1] OK : temps de jeu et Soutien Rising cumulé comptés, plafonnés, sauvegardés ; monde v1 migré");
    }

    /** Dormir fait passer la nuit (l'heure du jour saute), pas le temps de jeu : il avance au rythme des ticks dormis. */
    private static void dormir(TestSingleplayerContext monde, ClientGameTestContext context) {
        // Les mondes de test arrêtent le cycle du jour : sans lui, dormir ne fait pas passer la nuit
        monde.getServer().runCommand("gamerule advance_time true");
        monde.getServer().runCommand("time set midnight");
        monde.getServer().runCommand("setblock 2 -60 2 minecraft:red_bed[facing=south,part=foot]");
        monde.getServer().runCommand("setblock 2 -60 3 minecraft:red_bed[facing=south,part=head]");
        monde.getServer().runCommand("tp @a 2.5 -60 0.5");
        context.waitTicks(5);
        String probleme = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).startSleepInBed(LIT)
                .left().map(Object::toString).orElse(""));
        if (!probleme.isEmpty()) {
            throw new AssertionError("Le joueur ne peut pas dormir : " + probleme);
        }
        long avant = temps(monde);
        long tickAvant = monde.getServer().computeOnServer(server -> server.overworld().getGameTime());
        long heureAvant = monde.getServer().computeOnServer(HorlogeMondeTest::heureDuJour);
        monde.getServer().waitFor(server -> !OutilsTest.joueur(server).isSleeping(), 400);
        long dormis = monde.getServer().computeOnServer(server -> server.overworld().getGameTime()) - tickAvant;
        long sautDeLHeure = monde.getServer().computeOnServer(HorlogeMondeTest::heureDuJour) - heureAvant;
        long compte = temps(monde) - avant;
        // De minuit au matin : l'heure du jour saute de plusieurs milliers de ticks, bien plus que les ticks dormis
        if (sautDeLHeure < dormis + 1_000) {
            throw new AssertionError("Sommeil : la nuit n'est pas passée (heure du jour +" + sautDeLHeure + " pour " + dormis + " ticks dormis)");
        }
        if (Math.abs(compte - dormis) > 40) {
            throw new AssertionError("Sommeil : " + compte + " ticks de temps de jeu comptés pour " + dormis + " ticks dormis");
        }
        monde.getServer().runCommand("gamerule advance_time false");
        RedRising.LOGGER.info("[G6.1] Sommeil : la nuit est passée, temps de jeu +{} pour {} ticks dormis", compte, dormis);
    }

    /** Le Soutien des actions : gains à l'aide, pertes au tort, plafond de 30 par jour, rouvert le lendemain. */
    private static void soutien(TestSingleplayerContext monde) {
        monde.getServer().runOnServer(server -> {
            HorlogeMonde.modifier(server, horloge -> horloge.avecAide(0).avecTort(0));
            ServerPlayer joueur = OutilsTest.joueur(server);
            joueur.removeAttached(HorlogeMonde.CONTRIBUTION);
            // Déjà au maximum : le Soutien de l'action compte quand même pour le monde (avant bornage de la jauge)
            JaugesJoueur.set(joueur, Jauge.SOUTIEN, 100);
            ActionsFaction.agir(joueur, "aide (test)", new Variation(0, 12, 0));
            ActionsFaction.agir(joueur, "tort (test)", new Variation(0, -7, 0));
        });
        verifier(monde, "une aide puis un tort", 12, 7);

        monde.getServer().runOnServer(server -> ActionsFaction.agir(OutilsTest.joueur(server), "grosse aide (test)", new Variation(0, 25, 0)));
        verifier(monde, "plafond du jour", ContributionDuJour.PLAFOND, 7);

        // Le lendemain, le plafond est rouvert
        monde.getServer().runOnServer(server -> {
            ServerLevel niveau = server.overworld();
            ((ServerLevelData) niveau.getLevelData()).setGameTime(niveau.getGameTime() + ActionsFaction.TICKS_PAR_JOUR);
            ActionsFaction.agir(OutilsTest.joueur(server), "aide le lendemain (test)", new Variation(0, 5, 0));
        });
        verifier(monde, "le lendemain", ContributionDuJour.PLAFOND + 5, 7);

        // Les commandes de debug ne comptent pas
        monde.getServer().runCommand("rr jauge add soutien -20 @a[limit=1]");
        verifier(monde, "commande de debug", ContributionDuJour.PLAFOND + 5, 7);
        RedRising.LOGGER.info("[G6.1] Soutien cumulé : aide et tort séparés, plafond de {} par jour, rouvert le lendemain", ContributionDuJour.PLAFOND);
    }

    /**
     * Réécrit l'état du monde au format v1 (celui de G2.4, sans horloge) dans le fichier du monde, puis le rouvre :
     * il est migré en v2, le reste intact, l'horloge à zéro ; en quittant, le fichier est réécrit en v2.
     */
    private static void mondeV1(TestWorldSave sauvegarde) {
        Path fichier = sauvegarde.getSaveDirectory().resolve("data/fabric/attachments.dat");
        CompoundTag racine = lire(fichier);
        CompoundTag attachments = racine.getCompoundOrEmpty("data").getCompoundOrEmpty("fabric:attachments");
        CompoundTag v1 = new CompoundTag();
        v1.putInt("schemaVersion", 1);
        v1.putString("epoque", "golden_son");
        CompoundTag jalons = new CompoundTag();
        jalons.putString("pendaison_eo", "canon");
        v1.put("jalons", jalons);
        attachments.put("redrising:etat_monde", v1);
        ecrire(fichier, racine);
        RedRising.LOGGER.info("[G6.1] Fichier réécrit au format v1 : {}", v1);

        try (TestSingleplayerContext monde = sauvegarde.open()) {
            EtatMonde etat = monde.getServer().computeOnServer(EtatMondeServeur::get);
            if (etat.schemaVersion() != EtatMonde.VERSION_SCHEMA || etat.epoque() != Epoque.GOLDEN_SON
                    || !etat.jalons().equals(Map.of("pendaison_eo", Issue.CANON))
                    || etat.horloge().aide() != 0 || etat.horloge().tort() != 0 || etat.horloge().temps() > 2_000) {
                throw new AssertionError("Monde v1 rouvert : " + etat);
            }
            RedRising.LOGGER.info("[G6.1] Monde v1 rouvert et migré : {}", etat);
        }
        String contenu = lire(fichier).toString();
        if (!contenu.contains("schemaVersion:" + EtatMonde.VERSION_SCHEMA) || !contenu.contains("horloge")) {
            throw new AssertionError("Monde v1 : fichier pas réécrit en v" + EtatMonde.VERSION_SCHEMA + " : " + contenu);
        }
    }

    /** L'heure du jour de l'Overworld, en ticks : en 26.2 elle est tenue par une horloge (« clock »), plus par le niveau. */
    private static long heureDuJour(MinecraftServer server) {
        return server.clockManager().getTotalTicks(server.overworld().dimensionType().defaultClock().orElseThrow());
    }

    private static long temps(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(server -> HorlogeMonde.get(server).temps());
    }

    private static void verifier(TestSingleplayerContext monde, String cas, long aide, long tort) {
        Horloge horloge = monde.getServer().computeOnServer(HorlogeMonde::get);
        if (horloge.aide() != aide || horloge.tort() != tort) {
            throw new AssertionError(cas + " : aide " + horloge.aide() + ", tort " + horloge.tort() + " (attendu " + aide + ", " + tort + ")");
        }
    }

    private static CompoundTag lire(Path fichier) {
        try {
            return NbtIo.readCompressed(fichier, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new AssertionError("Lecture impossible : " + fichier, e);
        }
    }

    private static void ecrire(Path fichier, CompoundTag racine) {
        try {
            NbtIo.writeCompressed(racine, fichier);
        } catch (IOException e) {
            throw new AssertionError("Écriture impossible : " + fichier, e);
        }
    }
}
