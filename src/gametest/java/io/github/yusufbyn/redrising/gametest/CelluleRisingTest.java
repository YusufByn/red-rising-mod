package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.factions.Acces;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.monde.CelluleRising;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * G5.4 : la cellule du Rising n'est accessible qu'avec assez de Soutien. Vrais clics (accroupi, main vide) sur l'entrée cachée :
 * à 19, rien ne se passe (ni message, ni repère) ; à 20, le repère apparaît et le joueur passe dans la salle, puis ressort.
 * Un joueur refusé dans la salle est mis dehors. Les entonnoirs ne vident pas les coffres et tonneaux d'un camp.
 */
public class CelluleRisingTest implements FabricClientGameTest {
    /** Entrée cachée en (0, -60, 4) et (0, -59, 4), salle vers le sud : x -2..2, y -60..-58, z 5..9. */
    private static final BlockPos ENTREE = new BlockPos(0, -60, 4);
    private static final BlockPos DEHORS = new BlockPos(0, -60, 3);
    /** Devant le mur, tourné vers l'entrée (sud). */
    private static final String DEVANT_L_ENTREE = "tp @a 0.5 -60 2.5 0 0";
    /** Dans la salle, tourné vers l'entrée (nord). */
    private static final String FACE_A_LA_SORTIE = "tp @a 0.5 -60 5.5 180 0";
    private static final Component TEMOIN = Component.literal("témoin");

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave sauvegarde;

        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runCommand("weather clear"); // pas de gouttes de pluie dans le compte des particules
            monde.getServer().runCommand("rr structure cellule " +ENTREE.getX() + " " + ENTREE.getY() + " " + ENTREE.getZ() + " south");
            monde.getServer().runCommand(DEVANT_L_ENTREE);
            monde.getConnection().waitForChunksRender();
            verifierVraieSalle(monde);

            // --- Soutien 19 : ni repère, ni passage, ni message ---
            soutien(monde, Acces.SEUIL_SOUTIEN_RISING - 1);
            context.waitTicks(20);
            verifierRepere(context, "Soutien 19", false);
            context.runOnClient(mc -> mc.gui.hud.setOverlayMessage(TEMOIN, false));
            clicAccroupi(context);
            context.waitTicks(20);
            verifierDedans(monde, "Soutien 19, geste sur l'entrée", false);
            if (!TEMOIN.equals(messageAffiche(context))) {
                throw new AssertionError("Un message est apparu pour un joueur refusé : " + messageAffiche(context));
            }
            RedRising.LOGGER.info("[G5.4] Soutien 19 : aucun repère, le geste ne fait rien, aucun message");

            // --- Soutien 20 : repère, passage dans la salle, puis sortie ---
            soutien(monde, Acces.SEUIL_SOUTIEN_RISING);
            verifierRepere(context, "Soutien 20", true);
            context.runOnClient(mc -> mc.gui.hud.setOverlayMessage(TEMOIN, false));
            clicAccroupi(context);
            attendreDedans(monde, "Soutien 20, geste sur l'entrée", true);
            context.waitTicks(10);
            // Le clic de la main secondaire, qui suit celui de la main principale, ne doit pas déclencher de message de Minecraft
            if (!TEMOIN.equals(messageAffiche(context))) {
                throw new AssertionError("Message affiché après l'entrée : " + messageAffiche(context));
            }
            OutilsTest.garderCapture(context.takeScreenshot("cellule-rising"));
            monde.getServer().runCommand(FACE_A_LA_SORTIE);
            clicAccroupi(context);
            attendreDedans(monde, "Soutien 20, geste de sortie", false);
            verifierPosition(monde, "sortie", DEHORS);

            // --- Soutien qui retombe une fois dedans : mis dehors, avec un message ---
            monde.getServer().runCommand(FACE_A_LA_SORTIE);
            context.waitTicks(30);
            verifierDedans(monde, "Soutien 20, dans la salle", true);
            soutien(monde, 15);
            attendreDedans(monde, "Soutien retombé à 15", false);
            verifierPosition(monde, "mis dehors", DEHORS);
            context.waitTicks(5);
            if (!(messageAffiche(context).getContents() instanceof TranslatableContents texte) || !texte.getKey().equals("structure.redrising.cellule.dehors")) {
                throw new AssertionError("Message attendu à la mise dehors, obtenu : " + messageAffiche(context));
            }
            RedRising.LOGGER.info("[G5.4] Soutien retombé dans la salle : mis dehors, avec le message");

            entonnoirs(monde, context);
            sauvegarde = monde.getWorldSave();
        }

        // --- Rechargement : la cellule est toujours là, toujours gardée ---
        try (TestSingleplayerContext monde = sauvegarde.open()) {
            int cellules = monde.getServer().computeOnServer(server -> CelluleRising.cellules(server).size());
            if (cellules != 1) {
                throw new AssertionError("Cellules après rechargement : " + cellules);
            }
            OutilsTest.attendreArrivee(context);
            soutien(monde, Acces.SEUIL_SOUTIEN_RISING - 1);
            monde.getServer().runCommand(FACE_A_LA_SORTIE);
            attendreDedans(monde, "Soutien 19 après rechargement", false);
            soutien(monde, Acces.SEUIL_SOUTIEN_RISING);
            monde.getServer().runCommand(DEVANT_L_ENTREE);
            monde.getConnection().waitForChunksRender();
            clicAccroupi(context);
            attendreDedans(monde, "Soutien 20 après rechargement", true);
        }
        RedRising.LOGGER.info("[G5.4] OK : cellule du Rising à partir de {} de Soutien, entrée silencieuse, entonnoirs bloqués", Acces.SEUIL_SOUTIEN_RISING);
    }

    private static void soutien(TestSingleplayerContext monde, int valeur) {
        monde.getServer().runOnServer(server -> JaugesJoueur.set(OutilsTest.joueur(server), Jauge.SOUTIEN, valeur));
    }

    /** Accroupi, main vide, un clic droit sur ce qui est devant (l'entrée), puis debout. */
    private static void clicAccroupi(ClientGameTestContext context) {
        context.waitTicks(5);
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(2);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(2);
        context.getInput().releaseKey(options -> options.keyShift);
    }

    /** La salle est dans le monde, derrière l'entrée : on y trouve de l'air, et le PNJ du Rising. */
    private static void verifierVraieSalle(TestSingleplayerContext monde) {
        boolean[] salle = monde.getServer().computeOnServer(server -> new boolean[]{
                server.overworld().getBlockState(ENTREE).is(Blocks.STONE) && server.overworld().getBlockState(ENTREE.above()).is(Blocks.STONE),
                server.overworld().getBlockState(ENTREE.south()).isAir()});
        if (!salle[0] || !salle[1]) {
            throw new AssertionError("Entrée en pierre (" + salle[0] + ") et salle juste derrière (" + salle[1] + ")");
        }
    }

    /**
     * Le repère (particules rouges) est-il visible ? Le serveur l'envoie toutes les 2 secondes : on compte les particules
     * du client pendant 3 secondes. Le monde plat n'en produit pas d'autres.
     */
    private static void verifierRepere(ClientGameTestContext context, String cas, boolean visible) {
        int max = 0;
        for (int i = 0; i < 60; i++) {
            context.waitTicks(1);
            max = Math.max(max, context.computeOnClient(mc -> {
                String compte = mc.particleEngine.countParticles(); // « ... T<total> »
                return Integer.parseInt(compte.substring(compte.lastIndexOf('T') + 1).trim());
            }));
        }
        if ((max > 0) != visible) {
            throw new AssertionError("Repère (" + cas + ") : attendu " + (visible ? "visible" : "invisible") + ", " + max + " particule(s)");
        }
        RedRising.LOGGER.info("[G5.4] Repère ({}) : {} ({} particule(s) au plus)", cas, visible ? "visible" : "invisible", max);
    }

    /** Dernier message affiché au-dessus de la barre d'objets (champ privé du HUD, lu par réflexion : test seulement). */
    private static Component messageAffiche(ClientGameTestContext context) {
        return context.computeOnClient(mc -> {
            try {
                Field champ = Hud.class.getDeclaredField("overlayMessageString");
                champ.setAccessible(true);
                return (Component) champ.get(mc.gui.hud);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static boolean dedans(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(server ->
                CelluleRising.cellules(server).getFirst().interieur().isInside(OutilsTest.joueur(server).blockPosition()));
    }

    private static void verifierDedans(TestSingleplayerContext monde, String cas, boolean attendu) {
        if (dedans(monde) != attendu) {
            BlockPos pos = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).blockPosition());
            throw new AssertionError("Joueur (" + cas + ") en " + pos.toShortString() + " : attendu " + (attendu ? "dans la salle" : "dehors"));
        }
        RedRising.LOGGER.info("[G5.4] Joueur ({}) : {}", cas, attendu ? "dans la salle" : "dehors");
    }

    /** L'effet d'un clic ou de la surveillance arrive un peu plus tard. */
    private static void attendreDedans(TestSingleplayerContext monde, String cas, boolean attendu) {
        try {
            monde.getServer().waitFor(server -> CelluleRising.cellules(server).getFirst().interieur()
                    .isInside(OutilsTest.joueur(server).blockPosition()) == attendu, 40);
        } catch (AssertionError e) {
            // Délai dépassé : message plus parlant
            verifierDedans(monde, cas, attendu);
        }
        RedRising.LOGGER.info("[G5.4] Joueur ({}) : {}", cas, attendu ? "dans la salle" : "dehors");
    }

    private static void verifierPosition(TestSingleplayerContext monde, String cas, BlockPos attendue) {
        BlockPos pos = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).blockPosition());
        if (!pos.equals(attendue)) {
            throw new AssertionError("Joueur (" + cas + ") en " + pos.toShortString() + ", attendu en " + attendue.toShortString());
        }
    }

    /**
     * Sous chaque contenant, un entonnoir (ou un wagonnet à entonnoir) ; 5 diamants dans chaque contenant.
     * Ceux d'un camp (coffre, tonneau, coffre double dont une moitié est d'un camp) gardent tout ; les témoins se vident.
     */
    private static void entonnoirs(TestSingleplayerContext monde, ClientGameTestContext context) {
        BlockPos coffreCamp = new BlockPos(20, -59, 0);
        BlockPos tonneauCamp = new BlockPos(22, -59, 0);
        BlockPos coffreDouble = new BlockPos(24, -59, 0); // l'autre moitié (d'un camp) est à côté
        BlockPos coffreTemoin = new BlockPos(28, -59, 0);
        BlockPos coffreCampWagonnet = new BlockPos(30, -59, 0);
        BlockPos coffreTemoinWagonnet = new BlockPos(32, -59, 0);
        BlockPos autreMoitie = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            for (BlockPos pos : new BlockPos[]{coffreCamp, tonneauCamp, coffreDouble, coffreTemoin}) {
                niveau.setBlockAndUpdate(pos.below(), Blocks.HOPPER.defaultBlockState());
            }
            niveau.setBlockAndUpdate(coffreCamp, Blocks.CHEST.defaultBlockState());
            niveau.setBlockAndUpdate(tonneauCamp, Blocks.BARREL.defaultBlockState());
            niveau.setBlockAndUpdate(coffreTemoin, Blocks.CHEST.defaultBlockState());
            niveau.setBlockAndUpdate(coffreCampWagonnet, Blocks.CHEST.defaultBlockState());
            niveau.setBlockAndUpdate(coffreTemoinWagonnet, Blocks.CHEST.defaultBlockState());
            BlockState gauche = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT);
            BlockPos autre = ChestBlock.getConnectedBlockPos(coffreDouble, gauche);
            niveau.setBlockAndUpdate(coffreDouble, gauche);
            niveau.setBlockAndUpdate(autre, gauche.setValue(ChestBlock.TYPE, ChestType.RIGHT));
            for (BlockPos pos : new BlockPos[]{coffreCamp, tonneauCamp, coffreDouble, autre, coffreTemoin, coffreCampWagonnet, coffreTemoinWagonnet}) {
                ((Container) Objects.requireNonNull(niveau.getBlockEntity(pos))).setItem(0, new ItemStack(Items.DIAMOND, 5));
            }
            for (BlockPos pos : new BlockPos[]{coffreCamp, tonneauCamp, autre, coffreCampWagonnet}) {
                BlockEntity bloc = Objects.requireNonNull(niveau.getBlockEntity(pos));
                bloc.setAttached(ActionsFaction.CAMP, Camp.SOCIETE);
                bloc.setChanged();
            }
            return autre;
        });
        monde.getServer().runCommand("summon hopper_minecart 30.5 -60 0.5");
        monde.getServer().runCommand("summon hopper_minecart 32.5 -60 0.5");
        // Un entonnoir déplace un objet toutes les 8 ticks : 5 diamants en 40 ticks
        context.waitTicks(80);
        verifierDiamants(monde, "coffre d'un camp", coffreCamp, 5);
        verifierDiamants(monde, "tonneau d'un camp", tonneauCamp, 5);
        verifierDiamants(monde, "coffre double, moitié sans camp", coffreDouble, 5);
        verifierDiamants(monde, "coffre double, moitié d'un camp", autreMoitie, 5);
        verifierDiamants(monde, "coffre d'un camp sur un wagonnet à entonnoir", coffreCampWagonnet, 5);
        verifierDiamants(monde, "coffre témoin", coffreTemoin, 0);
        verifierDiamants(monde, "coffre témoin sur un wagonnet à entonnoir", coffreTemoinWagonnet, 0);
    }

    private static void verifierDiamants(TestSingleplayerContext monde, String cas, BlockPos pos, int attendu) {
        int diamants = monde.getServer().computeOnServer(server ->
                ((Container) Objects.requireNonNull(server.overworld().getBlockEntity(pos))).countItem(Items.DIAMOND));
        if (diamants != attendu) {
            throw new AssertionError("Entonnoir sous un " + cas + " : " + diamants + " diamant(s), attendu " + attendu);
        }
        RedRising.LOGGER.info("[G5.4] Entonnoir sous un {} : {} diamant(s)", cas, diamants);
    }
}
