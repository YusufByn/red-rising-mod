package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.couleur.ProfilCouleur;
import io.github.yusufbyn.redrising.couleur.ProfilsCouleurs;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * G3.2 : une Couleur de test ne peut pas enchanter ni lire un livre (ni fabriquer de livre),
 * alors qu'une Couleur sans restriction le peut. Tout passe par de vrais clics droits dans le client.
 *
 * <p>Le profil n'est déclaré que dans ce mod de test, sur une Couleur différente de {@link CapacitesPassivesTest}.</p>
 */
public class RestrictionsCouleurTest implements FabricClientGameTest {
    private static final Couleur RESTREINTE = Couleur.BLEU;
    private static final Couleur LIBRE = Couleur.OR;
    /** Devant le joueur, qui apparaît en (0.5, -60, 0.5) dans le monde plat de test. */
    private static final BlockPos BLOC = new BlockPos(0, -60, 2);

    @Override
    public void runTest(ClientGameTestContext context) {
        // La Couleur de test, en quelques lignes : ni enchanter, ni lire, ni fabriquer de livre
        ProfilsCouleurs.declarer(RESTREINTE, ProfilCouleur.builder()
                .interditBloc(Blocks.ENCHANTING_TABLE, Blocks.LECTERN)
                .interditObjet(Items.WRITTEN_BOOK, Items.WRITABLE_BOOK)
                .interditCraft(Items.BOOK)
                .build());

        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runCommand("tp @a 0.5 -60 0.5");

            // --- Enchanter ---
            poserBloc(context, monde, "enchanting_table");
            changerCouleur(context, monde, LIBRE);
            clicDroitSurBloc(context);
            context.waitForScreen(EnchantmentScreen.class);
            fermerEcran(context);
            RedRising.LOGGER.info("[G3.2] Or : la table d'enchantement s'ouvre");

            changerCouleur(context, monde, RESTREINTE);
            clicDroitSurBloc(context);
            verifierAucunEcran(context, monde, "table d'enchantement");
            OutilsTest.garderCapture(context.takeScreenshot("restriction-enchantement"));

            // --- Lire un livre écrit (en main) ---
            donnerLivreEcrit(context, monde);
            changerCouleur(context, monde, LIBRE);
            clicDroitEnLAir(context);
            context.waitForScreen(BookViewScreen.class);
            fermerEcran(context);
            RedRising.LOGGER.info("[G3.2] Or : le livre écrit s'ouvre");

            changerCouleur(context, monde, RESTREINTE);
            clicDroitEnLAir(context);
            verifierAucunEcran(context, monde, "livre écrit");

            // --- Pupitre : y poser un livre, puis le lire ---
            poserBloc(context, monde, "lectern");
            clicDroitSurBloc(context); // Bleu, livre en main
            context.waitTicks(10);
            if (pupitreALivre(monde)) {
                throw new AssertionError("Le Bleu a pu poser un livre sur le pupitre");
            }
            RedRising.LOGGER.info("[G3.2] Bleu : impossible de poser le livre sur le pupitre");

            changerCouleur(context, monde, LIBRE);
            clicDroitSurBloc(context); // l'Or pose le livre
            monde.getServer().waitFor(server -> pupitreALivre(monde), 40);
            changerCouleur(context, monde, RESTREINTE);
            viderMain(context, monde);
            clicDroitSurBloc(context); // le Bleu essaie de lire le livre posé
            verifierAucunEcran(context, monde, "pupitre avec un livre");
            changerCouleur(context, monde, LIBRE);
            clicDroitSurBloc(context);
            context.waitForScreen(LecternScreen.class);
            fermerEcran(context);
            RedRising.LOGGER.info("[G3.2] Or : le livre du pupitre s'ouvre");

            // --- Fabriquer un livre (grille 2×2 de l'inventaire) ---
            verifierCraftLivre(monde, LIBRE, true);
            verifierCraftLivre(monde, RESTREINTE, false);
        }

        RedRising.LOGGER.info("[G3.2] OK : la Couleur de test ne peut ni enchanter, ni lire, ni fabriquer de livre");
    }

    private static void poserBloc(ClientGameTestContext context, TestSingleplayerContext monde, String bloc) {
        monde.getServer().runCommand("setblock " + BLOC.getX() + " " + BLOC.getY() + " " + BLOC.getZ() + " " + bloc);
        monde.getConnection().waitForChunksRender();
        context.waitTicks(5);
    }

    private static void changerCouleur(ClientGameTestContext context, TestSingleplayerContext monde, Couleur couleur) {
        monde.getServer().runOnServer(server -> CouleurJoueur.set(OutilsTest.joueur(server), couleur));
        // Le client doit avoir reçu la Couleur : il refuse lui-même les clics interdits
        context.waitFor(mc -> mc.player != null && CouleurJoueur.get(mc.player) == couleur, 40);
    }

    private static void clicDroitSurBloc(ClientGameTestContext context) {
        context.getInput().lookAt(BLOC);
        context.getInput().pressKey(options -> options.keyUse);
    }

    private static void clicDroitEnLAir(ClientGameTestContext context) {
        context.getInput().lookAt(0, -90); // vers le ciel, aucun bloc visé
        context.getInput().pressKey(options -> options.keyUse);
    }

    private static void fermerEcran(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
    }

    /** Ni écran côté client, ni menu ouvert côté serveur. */
    private static void verifierAucunEcran(ClientGameTestContext context, TestSingleplayerContext monde, String quoi) {
        context.waitTicks(10);
        Screen ecran = context.computeOnClient(mc -> mc.gui.screen());
        boolean menuServeur = monde.getServer().computeOnServer(server -> {
            var joueur = OutilsTest.joueur(server);
            return joueur.containerMenu != joueur.inventoryMenu;
        });
        if (ecran != null || menuServeur) {
            throw new AssertionError("Bleu : " + quoi + " ouvert (écran " + ecran + ", menu serveur " + menuServeur + ")");
        }
        RedRising.LOGGER.info("[G3.2] Bleu : {} refusé", quoi);
    }

    private static void donnerLivreEcrit(ClientGameTestContext context, TestSingleplayerContext monde) {
        monde.getServer().runOnServer(server -> {
            ItemStack livre = new ItemStack(Items.WRITTEN_BOOK, 1);
            livre.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                    Filterable.passThrough("Red Rising"), "Pierce Brown", 0,
                    List.of(Filterable.passThrough(Component.literal("Brise la chaîne."))), true));
            OutilsTest.joueur(server).setItemInHand(InteractionHand.MAIN_HAND, livre);
        });
        context.waitFor(mc -> mc.player != null && mc.player.getMainHandItem().is(Items.WRITTEN_BOOK), 40);
    }

    private static void viderMain(ClientGameTestContext context, TestSingleplayerContext monde) {
        monde.getServer().runOnServer(server -> OutilsTest.joueur(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        context.waitFor(mc -> mc.player != null && mc.player.getMainHandItem().isEmpty(), 40);
    }

    private static boolean pupitreALivre(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(server -> {
            var etat = server.overworld().getBlockState(BLOC);
            return etat.is(Blocks.LECTERN) && etat.getValue(LecternBlock.HAS_BOOK);
        });
    }

    /** 3 papiers et 1 cuir dans la grille 2×2 : le résultat est un livre, sauf si la Couleur l'interdit. */
    private static void verifierCraftLivre(TestSingleplayerContext monde, Couleur couleur, boolean attendu) {
        boolean livre = monde.getServer().computeOnServer(server -> {
            var joueur = OutilsTest.joueur(server);
            CouleurJoueur.set(joueur, couleur);
            CraftingContainer grille = joueur.inventoryMenu.getCraftSlots();
            grille.setItem(0, new ItemStack(Items.PAPER, 1));
            grille.setItem(1, new ItemStack(Items.PAPER, 1));
            grille.setItem(2, new ItemStack(Items.PAPER, 1));
            grille.setItem(3, new ItemStack(Items.LEATHER, 1)); // chaque setItem recalcule le résultat
            boolean resultat = joueur.inventoryMenu.getResultSlot().getItem().is(Items.BOOK);
            grille.clearContent();
            return resultat;
        });
        if (livre != attendu) {
            throw new AssertionError("Craft d'un livre par " + couleur + " : attendu " + attendu + ", obtenu " + livre);
        }
        RedRising.LOGGER.info("[G3.2] {} : craft d'un livre {}", couleur.getSerializedName(), livre ? "possible" : "refusé");
    }
}
