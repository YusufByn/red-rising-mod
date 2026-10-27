package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Boutique;
import io.github.yusufbyn.redrising.factions.Boutiques;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.Jauges;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.factions.Traque;
import io.github.yusufbyn.redrising.monde.CelluleRising;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import io.github.yusufbyn.redrising.pnj.TraqueGris;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

/**
 * G5.10 : une cellule dénoncée tombe, avec ses effets sur les jauges. Aussi : la planque (ni vol ni marque), une cellule
 * connue en y entrant ou en y creusant, la dénonciation à mains vides qui épargne les PNJ d'une cellule, la carte sans
 * cellule connue à portée, la quarantaine, une seule baisse de Suspicion par jour, le Fils remplacé, la cellule repérée
 * qui tombe un jour plus tard.
 *
 * <p>Cellules vers le sud : entrée en (x, -60, z), salle de x-2 à x+2 et de z+1 à z+5, Fils en (x, -60, z+4),
 * coffre du Rising en (x-2, -60, z+5), planque en (x+2, -60, z+5).</p>
 */
public class DenonciationTest implements FabricClientGameTest {
    private static final BlockPos A = new BlockPos(0, -60, 40);
    private static final BlockPos B = new BlockPos(40, -60, 40);
    private static final BlockPos C = new BlockPos(80, -60, 40);

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runCommand("gamemode survival @a[limit=1]");
            monde.getServer().runCommand("effect give @a[limit=1] minecraft:resistance infinite 4 true");
            monde.getServer().runCommand("effect give @a[limit=1] minecraft:saturation infinite 0 true");
            monde.getServer().runCommand("attribute @a[limit=1] minecraft:knockback_resistance base set 1");
            monde.getServer().runCommand("difficulty peaceful");
            monde.getServer().runCommand("time set noon");
            for (BlockPos cellule : List.of(A, B, C)) {
                monde.getServer().runCommand("rr structure cellule " + cellule.getX() + " " + cellule.getY() + " " + cellule.getZ() + " south");
            }

            planque(monde);
            connaitre(monde, context);
            Villager gris = mainsVides(monde, context);
            carteSansCellule(monde, context);
            denoncer(monde, context, gris);
            quarantaine(monde, context);
            remplacant(monde);
            reperee(monde, context);
        }
        RedRising.LOGGER.info("[G5.10] OK : une cellule dénoncée tombe, avec ses effets sur les jauges");
    }

    // --- Planque ---

    /** Reprendre ses affaires dans la planque : ni vol, ni marque. Des diamants y restent, pour la saisie. */
    private static void planque(TestSingleplayerContext monde) {
        jauges(monde, 50, 20, 0);
        monde.getServer().runCommand("tp @a 0.5 -60 42.5");
        boolean propre = monde.getServer().computeOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            ChestBlockEntity planque = coffre(server, planque(A));
            if (!planque.hasAttached(CelluleRising.PLANQUE) || planque.hasAttached(ActionsFaction.CAMP)) {
                throw new AssertionError("La planque doit être marquée planque, sans camp");
            }
            planque.setItem(0, new ItemStack(Items.DIAMOND, 5));
            planque.setItem(1, new ItemStack(Items.DIAMOND, 3));
            joueur.openMenu(planque);
            joueur.containerMenu.quickMoveStack(joueur, 0);
            joueur.closeContainer();
            return joueur.getInventory().countItem(Items.DIAMOND) == 5
                    && joueur.getInventory().getItem(joueur.getInventory().findSlotMatchingItem(new ItemStack(Items.DIAMOND))).get(Boutiques.VOLE) == null;
        });
        if (!propre) {
            throw new AssertionError("Diamants repris dans la planque : attendus 5, sans marque");
        }
        verifierJauges(monde, "reprendre ses affaires dans la planque", new Jauges(50, 20, 0));
        monde.getServer().runCommand("clear @a[limit=1]");
        RedRising.LOGGER.info("[G5.10] Planque : on reprend ses affaires sans vol ni marque");
    }

    // --- Connaître une cellule ---

    private static void connaitre(TestSingleplayerContext monde, ClientGameTestContext context) {
        // Par l'entrée : le geste accroupi, main vide
        monde.getServer().runCommand("tp @a 0.5 -60 38.5 0 0");
        context.waitTicks(5);
        clicAccroupi(context);
        monde.getServer().waitFor(server -> A.equals(OutilsTest.joueur(server).getAttachedOrElse(CelluleRising.CONNUES, List.of()).stream().findFirst().orElse(null)), 40);
        // En creusant (ici, téléporté dans la salle sans être admis) : mis dehors, mais il la connaît
        jauges(monde, 50, 0, 0);
        monde.getServer().runCommand("tp @a 40.5 -60 42.5");
        monde.getServer().waitFor(server -> OutilsTest.joueur(server).getAttachedOrElse(CelluleRising.CONNUES, List.of()).contains(B), 40);
        RedRising.LOGGER.info("[G5.10] Cellule connue en y entrant, et en y creusant sans être admis");
    }

    // --- Dénoncer ---

    /** À mains vides près d'une cellule : son Fils n'est pas arrêté (seule la carte atteint une cellule). */
    private static Villager mainsVides(TestSingleplayerContext monde, ClientGameTestContext context) {
        jauges(monde, 50, 60, 30);
        Villager gris = monde.getServer().computeOnServer(server -> {
            Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), new Vec3(3.5, -60, 35.5), 0, Couleur.GRIS));
            pnj.setNoAi(true);
            return pnj;
        });
        cliquerLeGris(monde, context, gris, "0.5 -60 34.5", ItemStack.EMPTY);
        context.waitTicks(10);
        if (fils(monde, A) == null) {
            throw new AssertionError("Dénonciation à mains vides : le Fils d'une cellule a été arrêté");
        }
        verifierJauges(monde, "mains vides, personne à dénoncer", new Jauges(50, 60, 30));
        RedRising.LOGGER.info("[G5.10] À mains vides près d'une cellule : le Fils reste");
        return gris;
    }

    /** Carte en main, aucune cellule connue à 32 blocs du Gris : rien ne se passe, la carte reste. */
    private static void carteSansCellule(TestSingleplayerContext monde, ClientGameTestContext context) {
        Villager loin = monde.getServer().computeOnServer(server -> {
            Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), new Vec3(0.5, -60, -40.5), 0, Couleur.GRIS));
            pnj.setNoAi(true);
            return pnj;
        });
        cliquerLeGris(monde, context, loin, "0.5 -60 -43.5", new ItemStack(Items.MAP));
        context.waitTicks(10);
        int cartes = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).getInventory().countItem(Items.MAP));
        int cellules = monde.getServer().computeOnServer(server -> CelluleRising.cellules(server).size());
        if (cartes != 1 || cellules != 3) {
            throw new AssertionError("Carte sans cellule connue à portée : " + cartes + " carte(s) (attendu 1), " + cellules + " cellules (attendu 3)");
        }
        verifierJauges(monde, "carte sans cellule à portée", new Jauges(50, 60, 30));
        monde.getServer().runOnServer(server -> loin.discard());
        monde.getServer().runCommand("clear @a[limit=1]");
        RedRising.LOGGER.info("[G5.10] Carte sans cellule connue à portée : rien ne se passe, la carte reste");
    }

    /** Carte en main près de la cellule A : elle tombe ; puis une dénonciation de PNJ le même jour ne blanchit plus. */
    private static void denoncer(TestSingleplayerContext monde, ClientGameTestContext context, Villager gris) {
        cliquerLeGris(monde, context, gris, "0.5 -60 34.5", new ItemStack(Items.MAP));
        monde.getServer().waitFor(server -> CelluleRising.cellules(server).size() == 2, 20);
        String etat = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            StringBuilder defauts = new StringBuilder();
            if (!niveau.getBlockState(A).isAir() || !niveau.getBlockState(A.above()).isAir()) {
                defauts.append(" entrée pas défoncée ;");
            }
            if (fils(server, A) != null) {
                defauts.append(" Fils toujours là ;");
            }
            for (BlockPos pos : List.of(coffreRising(A), planque(A))) {
                ChestBlockEntity coffre = coffre(server, pos);
                if (!coffre.isEmpty() || coffre.hasAttached(ActionsFaction.CAMP) || coffre.hasAttached(CelluleRising.PLANQUE)) {
                    defauts.append(" coffre en ").append(pos.toShortString()).append(" pas saisi ;");
                }
            }
            boolean lanterne = BlockPos.betweenClosedStream(A.offset(-2, 0, 1), A.offset(2, 2, 5)).anyMatch(pos -> niveau.getBlockState(pos).is(Blocks.LANTERN));
            if (lanterne) {
                defauts.append(" lanterne restée ;");
            }
            if (OutilsTest.joueur(server).getInventory().countItem(Items.MAP) != 0) {
                defauts.append(" carte pas prise ;");
            }
            return defauts.toString();
        });
        if (!etat.isEmpty()) {
            throw new AssertionError("Cellule dénoncée :" + etat);
        }
        verifierJauges(monde, "cellule dénoncée", new Jauges(60, 35, 20));
        RedRising.LOGGER.info("[G5.10] Carte en main : la cellule tombe (entrée défoncée, Fils arrêté, coffres saisis, carte prise), +10 / -25 / -10");
        // Ce que voit un joueur qui revient : un trou dans la roche, une salle vide et sombre
        monde.getServer().runCommand("tp @a 0.5 -60 37.5 0 5");
        context.waitTicks(20);
        OutilsTest.garderCapture(context.takeScreenshot("cellule-tombee"));

        // Le même jour, dénoncer un PNJ du Rising hors des cellules : Loyauté et Soutien, mais plus de baisse de Suspicion
        Villager rebelle = monde.getServer().computeOnServer(server -> {
            Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), new Vec3(-3.5, -60, 32.5), 0, Couleur.ROUGE));
            pnj.setAttached(ActionsFaction.CAMP, Camp.RISING);
            pnj.setNoAi(true);
            return pnj;
        });
        cliquerLeGris(monde, context, gris, "0.5 -60 34.5", ItemStack.EMPTY);
        monde.getServer().waitFor(server -> rebelle.isRemoved(), 20);
        verifierJauges(monde, "deuxième dénonciation du jour", new Jauges(65, 25, 20));
        monde.getServer().runOnServer(server -> gris.discard());
        RedRising.LOGGER.info("[G5.10] Deuxième dénonciation du jour : la Suspicion ne baisse plus");
    }

    /** En quarantaine, les autres cellules refusent le joueur, quel que soit son Soutien ; 3 jours plus tard, il entre. */
    private static void quarantaine(TestSingleplayerContext monde, ClientGameTestContext context) {
        jauges(monde, 50, 60, 0);
        String devantB = "tp @a 40.5 -60 38.5 0 0";
        monde.getServer().runCommand(devantB);
        context.waitTicks(5);
        clicAccroupi(context);
        context.waitTicks(10);
        if (dansB(monde)) {
            throw new AssertionError("En quarantaine : la cellule B l'a laissé entrer");
        }
        avancerLHorloge(monde, CelluleRising.DUREE_QUARANTAINE);
        monde.getServer().runCommand(devantB);
        context.waitTicks(5);
        clicAccroupi(context);
        monde.getServer().waitFor(server -> OutilsTest.joueur(server).blockPosition().getZ() > B.getZ(), 20);
        RedRising.LOGGER.info("[G5.10] Quarantaine : refusé dans les autres cellules, puis accepté 3 jours plus tard");
    }

    /** Fils de B tué : un remplaçant arrive au bout de 3 jours de jeu sans Fils. */
    private static void remplacant(TestSingleplayerContext monde) {
        monde.getServer().runCommand("tp @a 40.5 -60 42.5");
        Villager fils = Objects.requireNonNull(fils(monde, B));
        monde.getServer().runCommand("kill " + fils.getStringUUID());
        monde.getServer().waitFor(server -> CelluleRising.contenant(server, B).sansFils() >= 0, 250);
        avancerLHorloge(monde, CelluleRising.DELAI_REMPLACANT);
        monde.getServer().waitFor(server -> fils(server, B) != null, 250);
        RedRising.LOGGER.info("[G5.10] Fils tué : un remplaçant est arrivé après 3 jours de jeu");
    }

    /** Entré dans C sous les yeux d'un chasseur : repérée ; la Société y descend un jour plus tard, et le joueur perd 10 de Soutien. */
    private static void reperee(TestSingleplayerContext monde, ClientGameTestContext context) {
        jauges(monde, 50, 40, 0);
        monde.getServer().runCommand("tp @a 80.5 -60 38.5 0 0");
        monde.getServer().runCommand("rr traque start @a[limit=1]");
        monde.getServer().waitFor(server -> TraqueGris.nombreDeChasseurs(OutilsTest.joueur(server)) >= Traque.CHASSEURS_DEPART, 40);
        monde.getServer().runOnServer(server -> server.overworld().getEntities(EntityTypes.VILLAGER, new AABB(C).inflate(200),
                pnj -> pnj.hasAttached(TraqueGris.CHASSEUR)).getFirst().teleportTo(80.5, -60, 32.5));
        context.waitTicks(5);
        clicAccroupi(context);
        monde.getServer().waitFor(server -> CelluleRising.contenant(server, C).reperee(), 20);
        // Pas de Soutien perdu sur le moment : le joueur reste dans la cellule
        int soutien = monde.getServer().computeOnServer(server -> JaugesJoueur.get(OutilsTest.joueur(server)).soutien());
        if (soutien != 40) {
            throw new AssertionError("Cellule repérée : Soutien " + soutien + " sur le moment (attendu 40)");
        }
        monde.getServer().runCommand("rr traque stop @a[limit=1]");
        context.waitTicks(10);
        if (monde.getServer().computeOnServer(server -> CelluleRising.cellules(server).size()) != 2) {
            throw new AssertionError("La cellule repérée est tombée trop tôt");
        }
        avancerLHorloge(monde, CelluleRising.DELAI_DESCENTE);
        monde.getServer().waitFor(server -> CelluleRising.contenant(server, C) == null, 250);
        if (monde.getServer().computeOnServer(server -> fils(server, C)) != null) {
            throw new AssertionError("Descente sur la cellule repérée : le Fils est resté");
        }
        soutien = monde.getServer().computeOnServer(server -> JaugesJoueur.get(OutilsTest.joueur(server)).soutien());
        if (soutien != 30) {
            throw new AssertionError("Descente sur la cellule repérée : Soutien " + soutien + " (attendu 30)");
        }
        RedRising.LOGGER.info("[G5.10] Cellule repérée : descente de la Société un jour de jeu plus tard, et Soutien -10 à ce moment");
    }

    // --- Outils ---

    private static BlockPos coffreRising(BlockPos entree) {
        return entree.offset(-2, 0, 5);
    }

    private static BlockPos planque(BlockPos entree) {
        return entree.offset(2, 0, 5);
    }

    private static ChestBlockEntity coffre(MinecraftServer server, BlockPos pos) {
        return (ChestBlockEntity) Objects.requireNonNull(server.overworld().getBlockEntity(pos), "pas de coffre en " + pos.toShortString());
    }

    private static Villager fils(TestSingleplayerContext monde, BlockPos entree) {
        return monde.getServer().computeOnServer(server -> fils(server, entree));
    }

    private static Villager fils(MinecraftServer server, BlockPos entree) {
        return server.overworld().getEntities(EntityTypes.VILLAGER, new AABB(entree.offset(-2, 0, 1)).minmax(new AABB(entree.offset(2, 2, 5))),
                pnj -> pnj.isAlive() && pnj.getAttached(Boutiques.BOUTIQUE) == Boutique.RISING).stream().findFirst().orElse(null);
    }

    private static boolean dansB(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).blockPosition().getZ() > B.getZ());
    }

    /** Accroupi, l'objet en main (ou main vide), un vrai clic droit sur le Gris. */
    private static void cliquerLeGris(TestSingleplayerContext monde, ClientGameTestContext context, Villager gris, String position, ItemStack enMain) {
        monde.getServer().runOnServer(server -> OutilsTest.joueur(server).getInventory().setSelectedItem(enMain.copy()));
        monde.getServer().runCommand("tp @a " + position + " " + OutilsTest.regard(position, gris));
        context.waitTicks(5);
        clicAccroupi(context);
    }

    /** Accroupi, un clic droit sur ce qui est devant, puis debout. */
    private static void clicAccroupi(ClientGameTestContext context) {
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(2);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(2);
        context.getInput().releaseKey(options -> options.keyShift);
    }

    private static void jauges(TestSingleplayerContext monde, int loyaute, int soutien, int suspicion) {
        monde.getServer().runOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            JaugesJoueur.set(joueur, Jauge.LOYAUTE, loyaute);
            JaugesJoueur.set(joueur, Jauge.SOUTIEN, soutien);
            JaugesJoueur.set(joueur, Jauge.SUSPICION, suspicion);
        });
    }

    private static void verifierJauges(TestSingleplayerContext monde, String cas, Jauges attendues) {
        Jauges jauges = monde.getServer().computeOnServer(server -> JaugesJoueur.get(OutilsTest.joueur(server)));
        if (!jauges.equals(attendues)) {
            throw new AssertionError(cas + " : jauges attendues " + attendues + ", obtenues " + jauges);
        }
    }

    /** Avance l'horloge du monde (le serveur suit le pas du client : /tick sprint n'accélère rien). */
    private static void avancerLHorloge(TestSingleplayerContext monde, long ticks) {
        monde.getServer().runOnServer(server -> {
            ServerLevel niveau = server.overworld();
            ((ServerLevelData) niveau.getLevelData()).setGameTime(niveau.getGameTime() + ticks);
        });
    }
}
