package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.jalons.Attitude;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Horloge;
import io.github.yusufbyn.redrising.jalons.HorlogeMonde;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static io.github.yusufbyn.redrising.jalons.Echeance.jours;

/**
 * G6.5 : l'attitude d'un personnage de test change l'issue de son jalon (canon à 20 ou plus). Sans rien faire : divergente ;
 * après l'avoir aidé trois jours et volé la Société sous ses yeux : canon. Aussi : aide refusée le même jour, plafond des
 * gains du jour, silence des Rouges sans effet sur l'attitude, vol du Rising vu, mise à terre, compteurs d'aide et de tort
 * intacts, descente sur une cellule (personnage dedans, et personnage dont c'était le foyer), cellule dénoncée carte en
 * main, rechargement.
 */
public class AttitudePnjTest implements FabricClientGameTest {
    private static final String JALON = "test_g65";
    private static final String PERSO = "perso_g65";
    private static final String DANS_LA_CELLULE = "perso_g65_cellule";
    private static final String FOYER_CELLULE = "perso_g65_foyer";
    private static final String DENONCE = "perso_g65_denonce";
    private static final Vec3 DEVANT = new Vec3(0.5, -60, 2.5);
    /** Cellule vers le sud : entrée en (40, -60, 40), salle de x 38 à 42 et de z 41 à 45. */
    private static final BlockPos CELLULE = new BlockPos(40, -60, 40);
    /** Cellule dénoncée carte en main : entrée en (80, -60, 40). */
    private static final BlockPos CELLULE_DENONCEE = new BlockPos(80, -60, 40);

    @Override
    public void runTest(ClientGameTestContext context) {
        Jalons.declarer(Jalon.builder(JALON).epoque(Epoque.RED_RISING).echeance(jours(30), jours(30), jours(30), 0)
                .issue(bilan -> bilan.attitude(PERSO) >= 20 ? Issue.CANON : Issue.DIVERGENTE).build());
        for (String personnage : List.of(PERSO, DANS_LA_CELLULE, FOYER_CELLULE, DENONCE)) {
            Personnages.declarer(personnage, JALON, Camp.RISING);
        }
        try {
            jouer(context);
        } finally {
            Jalons.retirer(JALON);
            List.of(PERSO, DANS_LA_CELLULE, FOYER_CELLULE, DENONCE).forEach(Personnages::retirer);
        }
        RedRising.LOGGER.info("[G6.5] OK : l'attitude d'un PNJ de test change l'issue de son jalon");
    }

    private static void jouer(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runCommand("time set noon");
            monde.getServer().runCommand("tp @a 0.5 -60 0.5 0 20");
            UUID perso = pnjDevant(monde);
            monde.getServer().runCommand("rr pnj camp " + perso + " rising");
            monde.getServer().runCommand("rr pnj personnage " + perso + " " + PERSO);

            // --- Sans rien faire : divergente ---
            verifierIssue(monde, Issue.DIVERGENTE, "sans rien faire");
            RedRising.LOGGER.info("[G6.5] Sans rien faire : le jalon passe en divergente");

            // --- Jour 1 : aide (+5), aide refusée, deux vols de la Société sous ses yeux (+3, +3) : plafond à 10 ---
            monde.getServer().runOnServer(server -> OutilsTest.joueur(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 8)));
            context.waitTicks(5);
            aider(context);
            verifierAttitude(monde, PERSO, 5, "aide");
            aider(context);
            verifierAttitude(monde, PERSO, 5, "aide refusée le même jour");
            voler(monde, new BlockPos(0, -60, -2), Camp.SOCIETE);
            verifierAttitude(monde, PERSO, 8, "vol de la Société vu");
            voler(monde, new BlockPos(1, -60, -2), Camp.SOCIETE);
            verifierAttitude(monde, PERSO, 10, "second vol vu : plafond du jour");
            RedRising.LOGGER.info("[G6.5] Jour 1 : aide, aide refusée, vols vus ; gains plafonnés à 10");

            // --- Jours 2 et 3 : 10 + 5 + 3 + 5 = 23, le jalon passe en canon ---
            jourSuivant(monde);
            aider(context);
            voler(monde, new BlockPos(-1, -60, -2), Camp.SOCIETE);
            jourSuivant(monde);
            aider(context);
            verifierAttitude(monde, PERSO, 23, "trois jours d'aide et de vols vus");
            verifierIssue(monde, Issue.CANON, "après trois jours");
            RedRising.LOGGER.info("[G6.5] Aidé trois jours, la Société volée sous ses yeux : le jalon passe en canon");

            // --- Silence des Rouges : le personnage Rouge voit toujours, pour lui-même ---
            monde.getServer().runCommand("rr effet poser silence_rouge 1");
            voler(monde, new BlockPos(2, -60, -2), Camp.SOCIETE);
            verifierAttitude(monde, PERSO, 26, "vol de la Société vu pendant le silence des Rouges");
            RedRising.LOGGER.info("[G6.5] Silence des Rouges : l'attitude monte quand même");

            // --- Vol du Rising sous ses yeux : -5 ---
            voler(monde, new BlockPos(-2, -60, -2), Camp.RISING);
            verifierAttitude(monde, PERSO, 21, "vol du Rising vu");

            // --- Mis à terre : -20 ; ni l'attitude ni la commande ne touchent aux compteurs d'aide et de tort ---
            Horloge avant = monde.getServer().computeOnServer(HorlogeMonde::get);
            monde.getServer().runOnServer(server -> {
                ServerPlayer joueur = OutilsTest.joueur(server);
                villageois(server, perso).hurtServer(server.overworld(), joueur.damageSources().playerAttack(joueur), Float.MAX_VALUE);
            });
            verifierAttitude(monde, PERSO, 1, "mis à terre");
            monde.getServer().runCommand("rr personnage attitude add " + PERSO + " 5 @a[limit=1]");
            verifierAttitude(monde, PERSO, 6, "/rr personnage attitude add");
            Horloge apres = monde.getServer().computeOnServer(HorlogeMonde::get);
            if (apres.aide() != avant.aide() || apres.tort() != avant.tort()) {
                throw new AssertionError("Compteurs : aide " + avant.aide() + " → " + apres.aide() + ", tort " + avant.tort() + " → " + apres.tort());
            }
            monde.getServer().runCommand("rr pnj reveiller " + perso);
            RedRising.LOGGER.info("[G6.5] Vol du Rising vu, mise à terre : l'attitude baisse ; compteurs intacts");

            // --- Descente sur une cellule repérée par le joueur : -10 pour celui qui y était et celui dont c'était le foyer ---
            monde.getServer().runCommand("rr structure cellule " + CELLULE.getX() + " " + CELLULE.getY() + " " + CELLULE.getZ() + " south");
            monde.getServer().runCommand("tp @a 40.5 -60 34.5");
            pnjPersonnage(monde, new Vec3(40.5, -60, 43.5), DANS_LA_CELLULE);
            UUID dehors = pnjPersonnage(monde, new Vec3(39.5, -60, 43.5), FOYER_CELLULE);
            monde.getServer().runOnServer(server -> villageois(server, dehors).teleportTo(36.5, -60, 34.5));
            monde.getServer().runOnServer(server -> CelluleRising.marquerReperee(server,
                    Objects.requireNonNull(CelluleRising.contenant(server, CELLULE.offset(0, 0, 3))), OutilsTest.joueur(server)));
            monde.getServer().runOnServer(server -> {
                ServerLevel niveau = server.overworld();
                ((ServerLevelData) niveau.getLevelData()).setGameTime(niveau.getGameTime() + CelluleRising.DELAI_DESCENTE);
            });
            monde.getServer().waitFor(server -> CelluleRising.contenant(server, CELLULE.offset(0, 0, 3)) == null, 250);
            verifierAttitude(monde, DANS_LA_CELLULE, Attitude.CELLULE_REPEREE, "descente, personnage dans la cellule");
            verifierAttitude(monde, FOYER_CELLULE, Attitude.CELLULE_REPEREE, "descente, foyer dans la cellule");
            RedRising.LOGGER.info("[G6.5] Descente : -10 pour celui qui y était et celui dont c'était le foyer");

            // --- Dénonciation carte en main d'une cellule connue : -30, même sans l'avoir vu ---
            monde.getServer().runCommand("rr structure cellule " + CELLULE_DENONCEE.getX() + " " + CELLULE_DENONCEE.getY() + " " + CELLULE_DENONCEE.getZ() + " south");
            pnjPersonnage(monde, new Vec3(80.5, -60, 43.5), DENONCE);
            UUID gris = monde.getServer().computeOnServer(server -> {
                Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), new Vec3(80.5, -60, 31.5), 0, Couleur.GRIS));
                pnj.setNoAi(true);
                ServerPlayer joueur = OutilsTest.joueur(server);
                joueur.setAttached(CelluleRising.CONNUES, List.of(CELLULE_DENONCEE));
                joueur.getInventory().setSelectedItem(new ItemStack(Items.MAP));
                return pnj.getUUID();
            });
            String position = "80.5 -60 34.5";
            String regard = monde.getServer().computeOnServer(server -> OutilsTest.regard(position, villageois(server, gris)));
            monde.getServer().runCommand("tp @a " + position + " " + regard);
            monde.getConnection().waitForClientboundEntityUpdates(EntityTypes.VILLAGER);
            aider(context);
            monde.getServer().waitFor(server -> CelluleRising.contenant(server, CELLULE_DENONCEE.offset(0, 0, 3)) == null, 40);
            verifierAttitude(monde, DENONCE, Attitude.CELLULE_DENONCEE, "dénonciation de sa cellule, carte en main");
            RedRising.LOGGER.info("[G6.5] Cellule dénoncée carte en main : -30 pour le personnage qui y était");
            monde.getServer().runCommand("rr personnage list");
            sauvegarde = monde.getWorldSave();
        }

        try (TestSingleplayerContext monde = sauvegarde.open()) {
            monde.getServer().waitFor(server -> !server.getPlayerList().getPlayers().isEmpty(), 100);
            verifierAttitude(monde, PERSO, 6, "après rechargement");
            verifierAttitude(monde, DANS_LA_CELLULE, Attitude.CELLULE_REPEREE, "après rechargement");
        }
        RedRising.LOGGER.info("[G6.5] Rechargement : l'attitude est gardée");
    }

    /** Fait passer le jalon par la commande, vérifie son issue, puis l'efface (le personnage redevient essentiel). */
    private static void verifierIssue(TestSingleplayerContext monde, Issue attendue, String cas) {
        monde.getServer().runCommand("rr jalon declencher " + JALON);
        Issue issue = monde.getServer().computeOnServer(server -> EtatMondeServeur.get(server).jalons().get(JALON));
        if (issue != attendue) {
            throw new AssertionError(cas + " : issue " + issue + ", attendu " + attendue);
        }
        monde.getServer().runCommand("rr jalon clear " + JALON);
    }

    private static void verifierAttitude(TestSingleplayerContext monde, String personnage, int attendue, String cas) {
        try {
            monde.getServer().waitFor(server -> EtatMondeServeur.get(server).attitude(personnage, OutilsTest.joueur(server).getUUID()).valeur() == attendue, 20);
        } catch (AssertionError e) {
            int valeur = monde.getServer().computeOnServer(server -> EtatMondeServeur.get(server).attitude(personnage, OutilsTest.joueur(server).getUUID()).valeur());
            throw new AssertionError(cas + " : attitude de " + personnage + " " + valeur + ", attendu " + attendue, e);
        }
    }

    private static void jourSuivant(TestSingleplayerContext monde) {
        monde.getServer().runOnServer(server -> {
            ServerLevel niveau = server.overworld();
            ((ServerLevelData) niveau.getLevelData()).setGameTime(niveau.getGameTime() + ActionsFaction.TICKS_PAR_JOUR);
        });
    }

    /** Accroupi, un clic droit sur le PNJ devant le joueur (avec ce qu'il a en main), puis debout. */
    private static void aider(ClientGameTestContext context) {
        // Un vol ouvre l'écran du coffre côté client : le clic n'atteindrait pas le PNJ avant sa fermeture
        context.waitFor(mc -> mc.gui.screen() == null);
        context.waitTicks(2);
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(2);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(2);
        context.getInput().releaseKey(options -> options.keyShift);
        context.waitTicks(2);
    }

    /** Vol dans un coffre neuf de ce camp, derrière le joueur (le personnage, devant, le voit). */
    private static void voler(TestSingleplayerContext monde, BlockPos pos, Camp camp) {
        monde.getServer().runOnServer(server -> {
            server.overworld().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            ChestBlockEntity coffre = (ChestBlockEntity) Objects.requireNonNull(server.overworld().getBlockEntity(pos));
            coffre.setItem(0, new ItemStack(Items.DIAMOND, 5));
            coffre.setAttached(ActionsFaction.CAMP, camp);
            ServerPlayer joueur = OutilsTest.joueur(server);
            joueur.openMenu(coffre);
            joueur.getInventory().add(joueur.containerMenu.getSlot(0).remove(2));
            joueur.closeContainer();
        });
    }

    /** Un PNJ Rouge immobile juste devant le joueur, tourné vers lui. */
    private static UUID pnjDevant(TestSingleplayerContext monde) {
        UUID id = monde.getServer().computeOnServer(server -> {
            Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), DEVANT, 180, Couleur.ROUGE));
            pnj.setNoAi(true);
            return pnj.getUUID();
        });
        monde.getConnection().waitForClientboundEntityUpdates(EntityTypes.VILLAGER);
        return id;
    }

    /** Un PNJ Rouge immobile qui reçoit ce personnage, avec son foyer ici. */
    private static UUID pnjPersonnage(TestSingleplayerContext monde, Vec3 position, String personnage) {
        UUID id = monde.getServer().computeOnServer(server -> {
            Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), position, 180, Couleur.ROUGE));
            pnj.setNoAi(true);
            return pnj.getUUID();
        });
        monde.getServer().runCommand("rr pnj personnage " + id + " " + personnage);
        if (monde.getServer().computeOnServer(server -> villageois(server, id).getAttached(PnjEssentiels.PERSONNAGE)) == null) {
            throw new AssertionError("Personnage pas donné : " + personnage);
        }
        return id;
    }

    private static Villager villageois(MinecraftServer server, UUID id) {
        return (Villager) Objects.requireNonNull(server.overworld().getEntity(id), "PNJ absent : " + id);
    }
}
