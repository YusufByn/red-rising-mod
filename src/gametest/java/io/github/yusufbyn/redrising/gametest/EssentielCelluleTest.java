package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.data.EtatMonde;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.jalons.Bilan;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Horloge;
import io.github.yusufbyn.redrising.jalons.Issue;
import io.github.yusufbyn.redrising.jalons.Jalon;
import io.github.yusufbyn.redrising.jalons.Jalons;
import io.github.yusufbyn.redrising.jalons.Personnages;
import io.github.yusufbyn.redrising.monde.CelluleRising;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import io.github.yusufbyn.redrising.pnj.PnjEssentiels;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static io.github.yusufbyn.redrising.jalons.Echeance.jours;

/**
 * G6.4 : un personnage essentiel dans une cellule qui tombe n'est pas arrêté. Son foyer hors de la cellule : il s'y
 * retrouve. Son foyer dans la cellule : en fuite, sans mort enregistrée, et toujours en fuite au rechargement ; le même
 * cas par la descente sur une cellule repérée ; redonné à un PNJ, il n'est plus en fuite.
 *
 * <p>Cellules vers le sud : entrée en (x, -60, 40), salle de x-2 à x+2 et de z 41 à 45.</p>
 */
public class EssentielCelluleTest implements FabricClientGameTest {
    private static final String JALON = "test_g64_cellule";
    private static final String DEHORS = "perso_g64_dehors";
    private static final String DEDANS = "perso_g64_dedans";
    private static final String DESCENTE = "perso_g64_descente";
    private static final BlockPos A = new BlockPos(0, -60, 40);
    private static final BlockPos B = new BlockPos(40, -60, 40);
    private static final BlockPos C = new BlockPos(80, -60, 40);
    private static final Vec3 FOYER_DEHORS = new Vec3(0.5, -60, 30.5);

    @Override
    public void runTest(ClientGameTestContext context) {
        Jalons.declarer(Jalon.builder(JALON).epoque(Epoque.RED_RISING).echeance(jours(30), jours(30), jours(30), 0).fixe(Issue.CANON).build());
        for (String personnage : List.of(DEHORS, DEDANS, DESCENTE)) {
            Personnages.declarer(personnage, JALON);
        }
        try {
            jouer(context);
        } finally {
            Jalons.retirer(JALON);
            List.of(DEHORS, DEDANS, DESCENTE).forEach(Personnages::retirer);
        }
        RedRising.LOGGER.info("[G6.4] OK : un essentiel d'une cellule qui tombe s'échappe vers son foyer, ou part en fuite");
    }

    private static void jouer(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runCommand("time set noon");
            for (BlockPos cellule : List.of(A, B, C)) {
                monde.getServer().runCommand("rr structure cellule " + cellule.getX() + " " + cellule.getY() + " " + cellule.getZ() + " south");
            }

            // --- Foyer hors de la cellule : renvoyé à son foyer ---
            monde.getServer().runCommand("tp @a 0.5 -60 34.5");
            UUID dehors = pnj(monde, FOYER_DEHORS, DEHORS);
            monde.getServer().runOnServer(server -> villageois(server, dehors).teleportTo(0.5, -60, 43.5));
            context.waitTicks(5);
            monde.getServer().runCommand("rr structure tomber 0 -60 40");
            monde.getServer().waitFor(server -> CelluleRising.contenant(server, A.offset(0, 0, 3)) == null, 20);
            String etat = monde.getServer().computeOnServer(server -> {
                Villager pnj = villageois(server, dehors);
                return pnj.isAlive() + " " + pnj.getAttached(PnjEssentiels.PERSONNAGE) + " " + (pnj.position().distanceTo(FOYER_DEHORS) < 1);
            });
            if (!etat.equals("true " + DEHORS + " true")) {
                throw new AssertionError("Foyer hors de la cellule : vivant, personnage, au foyer = " + etat);
            }
            verifierFuite(monde, DEHORS, false);
            RedRising.LOGGER.info("[G6.4] Cellule tombée, foyer dehors : l'essentiel s'est échappé vers son foyer");

            // --- Foyer dans la cellule : en fuite, sans mort enregistrée ---
            monde.getServer().runCommand("tp @a 40.5 -60 34.5");
            UUID dedans = pnj(monde, new Vec3(40.5, -60, 43.5), DEDANS);
            context.waitTicks(5);
            monde.getServer().runCommand("rr structure tomber 40 -60 40");
            monde.getServer().waitFor(server -> server.overworld().getEntity(dedans) == null, 20);
            verifierFuite(monde, DEDANS, true);
            RedRising.LOGGER.info("[G6.4] Cellule tombée, foyer dedans : en fuite, pas mort");

            // --- Le même cas par la descente sur une cellule repérée ---
            monde.getServer().runCommand("tp @a 80.5 -60 34.5");
            pnj(monde, new Vec3(80.5, -60, 43.5), DESCENTE);
            monde.getServer().runOnServer(server -> CelluleRising.marquerReperee(server,
                    Objects.requireNonNull(CelluleRising.contenant(server, C.offset(0, 0, 3))), OutilsTest.joueur(server)));
            monde.getServer().runOnServer(server -> {
                ServerLevel niveau = server.overworld();
                ((ServerLevelData) niveau.getLevelData()).setGameTime(niveau.getGameTime() + CelluleRising.DELAI_DESCENTE);
            });
            monde.getServer().waitFor(server -> CelluleRising.contenant(server, C.offset(0, 0, 3)) == null, 250);
            verifierFuite(monde, DESCENTE, true);
            RedRising.LOGGER.info("[G6.4] Descente sur une cellule repérée : l'essentiel de la salle est en fuite");
            monde.getServer().runCommand("rr personnage list");
            sauvegarde = monde.getWorldSave();
        }

        try (TestSingleplayerContext monde = sauvegarde.open()) {
            monde.getServer().waitFor(server -> !server.getPlayerList().getPlayers().isEmpty(), 100);
            verifierFuite(monde, DEDANS, true);
            verifierFuite(monde, DESCENTE, true);
            // Redonné à un PNJ : il n'est plus en fuite
            pnj(monde, new Vec3(10.5, -60, 30.5), DEDANS);
            verifierFuite(monde, DEDANS, false);
        }
        RedRising.LOGGER.info("[G6.4] Rechargement : toujours en fuite ; redonné à un PNJ, plus en fuite");
    }

    private static UUID pnj(TestSingleplayerContext monde, Vec3 position, String personnage) {
        UUID id = monde.getServer().computeOnServer(server -> {
            Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), position, 180, Couleur.ROUGE));
            pnj.setNoAi(true);
            return pnj.getUUID();
        });
        monde.getServer().runCommand("rr pnj personnage " + id + " " + personnage);
        return id;
    }

    private static Villager villageois(MinecraftServer server, UUID id) {
        return (Villager) Objects.requireNonNull(server.overworld().getEntity(id), "PNJ absent : " + id);
    }

    private static void verifierFuite(TestSingleplayerContext monde, String personnage, boolean attendu) {
        EtatMonde etat = monde.getServer().computeOnServer(EtatMondeServeur::get);
        Bilan bilan = Bilan.depuis(Horloge.ZERO, etat);
        if (bilan.enFuite(personnage) != attendu || bilan.tue(personnage)) {
            throw new AssertionError(personnage + " : en fuite " + bilan.enFuite(personnage) + " (attendu " + attendu + "), mort " + bilan.tue(personnage));
        }
    }
}
