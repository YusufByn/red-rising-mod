package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.factions.Acces;
import io.github.yusufbyn.redrising.factions.Boutique;
import io.github.yusufbyn.redrising.factions.Boutiques;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.Jauges;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.factions.ModificateurPrix;
import io.github.yusufbyn.redrising.factions.ObjetsRebelles;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

/**
 * G5.9 : chaque camp a une boutique conditionnelle. Commerce ouvert par de vrais clics ; les échanges eux-mêmes passent
 * par le menu de commerce côté serveur (le même chemin qu'un clic dans l'écran). Paliers de la Société (40, 60),
 * vente plafonnée par jour puis rouverte le lendemain, achat, objet volé refusé, refus hors palier, modificateur de prix,
 * stock du Rising, et aucune jauge qui bouge en commerçant. Coffre d'un camp détruit par une TNT : contenu marqué, vol compté
 * si le joueur l'a allumée.
 */
public class BoutiqueTest implements FabricClientGameTest {
    /** Coin du poste : commis en (23.5, -60, -36.5), derrière le comptoir ; coffre de la Société en (25, -60, -35). */
    private static final BlockPos POSTE = new BlockPos(20, -61, -40);
    private static final BlockPos COFFRE_SOCIETE = new BlockPos(25, -60, -35);
    private static final BlockPos ENTREE_CELLULE = new BlockPos(0, -60, 40);

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runCommand("gamemode survival @a[limit=1]");
            monde.getServer().runCommand("effect give @a[limit=1] minecraft:resistance infinite 4 true");
            monde.getServer().runCommand("effect give @a[limit=1] minecraft:saturation infinite 0 true");
            monde.getServer().runCommand("time set noon");
            monde.getServer().runCommand("rr structure poste " + POSTE.getX() + " " + POSTE.getY() + " " + POSTE.getZ());
            monde.getServer().runCommand("rr structure cellule " + ENTREE_CELLULE.getX() + " " + ENTREE_CELLULE.getY() + " " + ENTREE_CELLULE.getZ() + " south");

            societe(monde, context);
            refus(monde, context);
            explosion(monde);
            rising(monde, context);
        }
        RedRising.LOGGER.info("[G5.9] OK : chaque camp a une boutique conditionnelle");
    }

    // --- Société ---

    private static void societe(TestSingleplayerContext monde, ClientGameTestContext context) {
        Villager commis = marchand(monde, Boutique.SOCIETE);
        jauges(monde, 50, 0, 0);
        donner(monde, new ItemStack(Items.COAL, 64));

        // Palier 40 : rations, torches, cartes, et ce qu'elle achète ; pas d'équipement
        ouvrir(monde, context, commis, "23.5 -60 -38.5");
        if (offre(monde, Items.IRON_PICKAXE, false) >= 0 || offre(monde, Items.BREAD, false) < 0) {
            throw new AssertionError("Société à 50 de Loyauté : rations attendues, pas d'équipement");
        }
        OutilsTest.garderCapture(context.takeScreenshot("boutique-societe"));
        RedRising.LOGGER.info("[G5.9] Société : ouverte par un vrai clic, palier 40 sans équipement");

        // Vendre du charbon : 4 fois par jour, la 5e est refusée
        int charbon = offre(monde, Items.COAL, true);
        for (int i = 1; i <= 4; i++) {
            if (!echanger(monde, charbon)) {
                throw new AssertionError("Vente de charbon n° " + i + " refusée");
            }
        }
        if (echanger(monde, charbon)) {
            throw new AssertionError("Vente de charbon au-delà du plafond du jour acceptée");
        }
        verifierCompte(monde, "après le plafond", Boutiques.CREDIT, 4);
        verifierCompte(monde, "après le plafond", Items.COAL, 64 - 4 * 8);
        RedRising.LOGGER.info("[G5.9] Société : 4 ventes de charbon, la 5e refusée (plafond du jour)");

        // Acheter des rations avec les crédits gagnés
        int pain = offre(monde, Items.BREAD, false);
        if (!echanger(monde, pain)) {
            throw new AssertionError("Achat de pain refusé");
        }
        verifierCompte(monde, "achat de pain", Items.BREAD, 3);
        verifierCompte(monde, "achat de pain", Boutiques.CREDIT, 3);
        verifierJauges(monde, "après les échanges avec la Société", new Jauges(50, 0, 0));
        fermer(monde, context);
        RedRising.LOGGER.info("[G5.9] Société : pain acheté avec des crédits, jauges inchangées");

        // Le lendemain : la vente rouvre
        avancerLHorloge(monde, 24000);
        ouvrir(monde, context, commis, "23.5 -60 -38.5");
        if (!echanger(monde, offre(monde, Items.COAL, true))) {
            throw new AssertionError("Vente de charbon refusée le lendemain");
        }
        verifierCompte(monde, "lendemain", Boutiques.CREDIT, 4);
        fermer(monde, context);
        RedRising.LOGGER.info("[G5.9] Société : le lendemain, la vente rouvre");

        // Palier 60 : l'équipement apparaît
        jauges(monde, Acces.SEUIL_EQUIPEMENT_SOCIETE, 0, 0);
        ouvrir(monde, context, commis, "23.5 -60 -38.5");
        if (offre(monde, Items.IRON_PICKAXE, false) < 0) {
            throw new AssertionError("Société à 60 de Loyauté : équipement attendu");
        }
        fermer(monde, context);
        RedRising.LOGGER.info("[G5.9] Société : palier 60, l'équipement apparaît");

        // Modificateur de prix (comme l'Argent, G9.4) : le charbon à moitié prix
        ModificateurPrix moitie = (joueur, article) -> article.id().equals("charbon") ? 0.5 : 1;
        Boutiques.declarerModificateur(moitie);
        try {
            ouvrir(monde, context, commis, "23.5 -60 -38.5");
            int prix = monde.getServer().computeOnServer(server -> menu(server).getOffers().get(offreSurServeur(server, Items.COAL, true)).getCostA().getCount());
            if (prix != 4) {
                throw new AssertionError("Charbon avec un modificateur de 0,5 : " + prix + " demandés (attendu 4)");
            }
            fermer(monde, context);
        } finally {
            Boutiques.retirerModificateur(moitie);
        }
        RedRising.LOGGER.info("[G5.9] Société : un modificateur de prix fait baisser le prix affiché");

        // Volé dans le coffre de la Société : marqué, et la Société le refuse
        jauges(monde, 50, 0, 0);
        monde.getServer().runCommand("clear @a[limit=1] minecraft:coal");
        monde.getServer().runOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            ChestBlockEntity coffre = (ChestBlockEntity) Objects.requireNonNull(server.overworld().getBlockEntity(COFFRE_SOCIETE));
            coffre.setItem(0, new ItemStack(Items.COAL, 8));
            joueur.openMenu(coffre);
            joueur.containerMenu.quickMoveStack(joueur, 0);
            joueur.closeContainer();
        });
        Camp marque = monde.getServer().computeOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            for (int i = 0; i < joueur.getInventory().getContainerSize(); i++) {
                if (joueur.getInventory().getItem(i).is(Items.COAL)) {
                    return Boutiques.voleA(joueur.getInventory().getItem(i));
                }
            }
            return null;
        });
        if (marque != Camp.SOCIETE) {
            throw new AssertionError("Charbon pris dans le coffre de la Société : marque " + marque + ", attendue " + Camp.SOCIETE);
        }
        jauges(monde, 50, 0, 0);
        ouvrir(monde, context, commis, "23.5 -60 -38.5");
        int offreCharbon = offre(monde, Items.COAL, true);
        boolean refuse = monde.getServer().computeOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            MerchantMenu menu = menu(server);
            menu.setSelectionHint(offreCharbon);
            menu.tryMoveItems(offreCharbon);
            boolean vide = menu.getSlot(2).getItem().isEmpty();
            // Contrôle : le même charbon sans la marque est accepté (c'est bien la marque qui fait refuser)
            ItemStack vole = menu.getSlot(0).getItem().copy();
            ItemStack propre = vole.copy();
            propre.remove(Boutiques.VOLE);
            menu.getSlot(0).set(propre);
            boolean accepteSansMarque = !menu.getSlot(2).getItem().isEmpty();
            menu.getSlot(0).set(ItemStack.EMPTY);
            joueur.getInventory().add(vole);
            return vide && accepteSansMarque;
        });
        fermer(monde, context);
        if (!refuse) {
            throw new AssertionError("La Société accepte du charbon qu'on lui a volé");
        }
        RedRising.LOGGER.info("[G5.9] Société : charbon volé dans son coffre, marqué, refusé au comptoir");
        monde.getServer().runCommand("clear @a[limit=1]");
    }

    /** Un PNJ marqué boutique de la Société, hors du poste, et un joueur sous le seuil : refusé, pas d'écran. */
    private static void refus(TestSingleplayerContext monde, ClientGameTestContext context) {
        monde.getServer().runCommand("tp @a 0.5 -60 0.5");
        Villager pnj = monde.getServer().computeOnServer(server -> {
            Villager commis = Objects.requireNonNull(PnjCouleur.creer(server.overworld(),
                    new Vec3(0.5, -60, 3.5), 180, Couleur.CUIVRE));
            commis.setNoAi(true);
            return commis;
        });
        monde.getServer().runCommand("rr boutique set " + pnj.getStringUUID() + " societe");
        jauges(monde, Acces.SEUIL_LOYAUTE_SOCIETE - 1, 0, 0);
        monde.getServer().runCommand("tp @a 0.5 -60 1.5 " + OutilsTest.regard("0.5 -60 1.5", pnj));
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(10);
        boolean ouvert = context.computeOnClient(mc -> mc.gui.screen() instanceof MerchantScreen);
        if (ouvert) {
            throw new AssertionError("Boutique de la Société ouverte sous le seuil");
        }
        verifierMessage(context, "sous le seuil", "boutique.redrising.refus.societe");
        monde.getServer().runOnServer(server -> pnj.discard());
        RedRising.LOGGER.info("[G5.9] Sous le seuil : la Société refuse de commercer");
    }

    // --- Explosion d'un coffre d'un camp ---

    /**
     * Un coffre de la Société isolé (aucun PNJ que l'explosion tuerait) : une TNT allumée par le joueur le détruit. Son
     * contenu tombe marqué, et c'est un vol (sans témoin ici). Une TNT sans responsable (allumée par la redstone) : contenu
     * marqué, aucun vol.
     */
    private static void explosion(TestSingleplayerContext monde) {
        monde.getServer().runCommand("tp @a 40.5 -60 -15.5");
        jauges(monde, 50, 0, 0);
        BlockPos coffre = new BlockPos(40, -60, 0);
        BlockPos autreCoffre = new BlockPos(60, -60, 0);
        for (BlockPos pos : List.of(coffre, autreCoffre)) {
            monde.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:chest");
            monde.getServer().runCommand("rr coffre set " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " societe");
            monde.getServer().runOnServer(server -> ((ChestBlockEntity) Objects.requireNonNull(server.overworld().getBlockEntity(pos)))
                    .setItem(0, new ItemStack(Items.COAL, 8)));
        }

        // Allumée par le joueur
        monde.getServer().runOnServer(server -> {
            PrimedTnt tnt = new PrimedTnt(server.overworld(), 41.5, -60, 0.5, OutilsTest.joueur(server));
            tnt.setFuse(10);
            server.overworld().addFreshEntity(tnt);
        });
        monde.getServer().waitFor(server -> !server.overworld().getBlockState(coffre).is(Blocks.CHEST), 60);
        verifierCharbonTombe(monde, coffre, "TNT du joueur");
        // Sans témoin : Suspicion à moitié, pas de Loyauté perdue, le Soutien du vol à la Société
        verifierJauges(monde, "coffre de la Société détruit par la TNT du joueur", new Jauges(50, 2, 3));
        RedRising.LOGGER.info("[G5.9] Coffre de la Société détruit par la TNT du joueur : contenu marqué, vol compté");

        // Sans responsable
        monde.getServer().runOnServer(server -> {
            PrimedTnt tnt = new PrimedTnt(server.overworld(), 61.5, -60, 0.5, null);
            tnt.setFuse(10);
            server.overworld().addFreshEntity(tnt);
        });
        monde.getServer().waitFor(server -> !server.overworld().getBlockState(autreCoffre).is(Blocks.CHEST), 60);
        verifierCharbonTombe(monde, autreCoffre, "TNT sans responsable");
        verifierJauges(monde, "coffre détruit par une TNT sans responsable", new Jauges(50, 2, 3));
        RedRising.LOGGER.info("[G5.9] Coffre détruit par une TNT sans responsable : contenu marqué, aucun vol");
        jauges(monde, 50, 0, 0);
    }

    /** Le charbon du coffre est tombé au sol, marqué « Volé à la Société ». */
    private static void verifierCharbonTombe(TestSingleplayerContext monde, BlockPos coffre, String cas) {
        monde.getServer().waitFor(server -> !charbonTombe(server, coffre).isEmpty(), 20);
        boolean marque = monde.getServer().computeOnServer(server ->
                charbonTombe(server, coffre).stream().allMatch(objet -> Boutiques.voleA(objet.getItem()) == Camp.SOCIETE));
        if (!marque) {
            throw new AssertionError(cas + " : le charbon du coffre est tombé sans la marque « Volé à la Société »");
        }
        monde.getServer().runOnServer(server -> charbonTombe(server, coffre).forEach(ItemEntity::discard));
    }

    private static List<ItemEntity> charbonTombe(MinecraftServer server, BlockPos coffre) {
        return server.overworld().getEntities(EntityTypes.ITEM, new AABB(coffre).inflate(8), objet -> objet.getItem().is(Items.COAL));
    }

    // --- Rising ---

    private static void rising(TestSingleplayerContext monde, ClientGameTestContext context) {
        Villager fils = marchand(monde, Boutique.RISING);
        jauges(monde, 50, Acces.SEUIL_FUMIGENE, 0);
        donner(monde, new ItemStack(Items.GUNPOWDER, 6));
        donner(monde, new ItemStack(Items.IRON_INGOT, 3));
        // Dans la salle, juste derrière l'entrée
        String dedans = ENTREE_CELLULE.getX() + 0.5 + " " + ENTREE_CELLULE.getY() + " " + (ENTREE_CELLULE.getZ() + 1.5);
        ouvrir(monde, context, fils, dedans);
        if (offre(monde, ObjetsRebelles.FUMIGENE, false) < 0 || offre(monde, ObjetsRebelles.CHAUSSE_TRAPPE_OBJET, false) >= 0) {
            throw new AssertionError("Rising à 20 de Soutien : fumigène attendu, pas de chausse-trappes");
        }
        int fumigene = offre(monde, ObjetsRebelles.FUMIGENE, false);
        for (int i = 1; i <= 2; i++) {
            if (!echanger(monde, fumigene)) {
                throw new AssertionError("Fumigène n° " + i + " refusé");
            }
        }
        if (echanger(monde, fumigene)) {
            throw new AssertionError("Troisième fumigène du jour accepté");
        }
        verifierCompte(monde, "fumigènes", ObjetsRebelles.FUMIGENE, 2);
        verifierCompte(monde, "fumigènes", Items.GUNPOWDER, 2);
        verifierJauges(monde, "après les échanges avec le Rising", new Jauges(50, Acces.SEUIL_FUMIGENE, 0));
        fermer(monde, context);
        RedRising.LOGGER.info("[G5.9] Rising : deux fumigènes contre poudre et fer, le troisième refusé ; jauges inchangées");

        jauges(monde, 50, Acces.SEUIL_CHAUSSE_TRAPPE, 0);
        ouvrir(monde, context, fils, dedans);
        if (offre(monde, ObjetsRebelles.CHAUSSE_TRAPPE_OBJET, false) < 0) {
            throw new AssertionError("Rising à 40 de Soutien : chausse-trappes attendues");
        }
        fermer(monde, context);
        RedRising.LOGGER.info("[G5.9] Rising : palier 40, les chausse-trappes apparaissent");
    }

    // --- Outils ---

    private static Villager marchand(TestSingleplayerContext monde, Boutique boutique) {
        return monde.getServer().computeOnServer(server -> server.overworld().getEntities(EntityTypes.VILLAGER,
                new AABB(BlockPos.ZERO).inflate(200), pnj -> pnj.getAttached(Boutiques.BOUTIQUE) == boutique).getFirst());
    }

    /** Devant le marchand, regard sur ses yeux, un vrai clic droit : l'écran de commerce s'ouvre. */
    private static void ouvrir(TestSingleplayerContext monde, ClientGameTestContext context, Villager marchand, String position) {
        monde.getServer().runCommand("tp @a " + position + " " + OutilsTest.regard(position, marchand));
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(10);
        if (!context.computeOnClient(mc -> mc.gui.screen() instanceof MerchantScreen)) {
            String vise = context.computeOnClient(mc -> String.valueOf(mc.hitResult) + " " + (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult e ? e.getEntity() : ""));
            OutilsTest.garderCapture(context.takeScreenshot("boutique-echec"));
            throw new AssertionError("Écran de commerce pas ouvert ; visé : " + vise);
        }
    }

    private static void fermer(TestSingleplayerContext monde, ClientGameTestContext context) {
        monde.getServer().runOnServer(server -> OutilsTest.joueur(server).closeContainer());
        context.waitForScreen(null);
    }

    private static MerchantMenu menu(MinecraftServer server) {
        return (MerchantMenu) OutilsTest.joueur(server).containerMenu;
    }

    /** Index de l'offre qui donne cet objet (ou, si {@code paiement}, qui le demande) ; -1 sinon. */
    private static int offre(TestSingleplayerContext monde, Item objet, boolean paiement) {
        return monde.getServer().computeOnServer(server -> offreSurServeur(server, objet, paiement));
    }

    private static int offreSurServeur(MinecraftServer server, Item objet, boolean paiement) {
        List<MerchantOffer> offres = menu(server).getOffers();
        for (int i = 0; i < offres.size(); i++) {
            ItemStack vise = paiement ? offres.get(i).getCostA() : offres.get(i).getResult();
            if (vise.is(objet)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Un échange, comme dans l'écran : choisir l'offre (le paiement passe de l'inventaire aux cases d'échange), puis
     * prendre le résultat. Renvoie {@code false} si l'échange n'a pas eu lieu. Le paiement restant revient à l'inventaire.
     */
    private static boolean echanger(TestSingleplayerContext monde, int index) {
        return monde.getServer().computeOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            MerchantMenu menu = menu(server);
            menu.setSelectionHint(index);
            menu.tryMoveItems(index);
            boolean fait = !menu.quickMoveStack(joueur, 2).isEmpty();
            for (int case_ = 0; case_ <= 1; case_++) {
                joueur.getInventory().add(menu.getSlot(case_).getItem().copy());
                menu.getSlot(case_).set(ItemStack.EMPTY);
            }
            return fait;
        });
    }

    private static void donner(TestSingleplayerContext monde, ItemStack objet) {
        monde.getServer().runOnServer(server -> OutilsTest.joueur(server).getInventory().add(objet.copy()));
    }

    private static void verifierCompte(TestSingleplayerContext monde, String cas, Item objet, int attendu) {
        int compte = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).getInventory().countItem(objet));
        if (compte != attendu) {
            throw new AssertionError(cas + " : " + compte + " " + objet + ", attendu " + attendu);
        }
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

    // --- Lecture de l'écran du client (champ privé, par réflexion : test seulement) ---

    private static void verifierMessage(ClientGameTestContext context, String cas, String cle) {
        Component message = context.computeOnClient(mc -> {
            try {
                Field champ = Hud.class.getDeclaredField("overlayMessageString");
                champ.setAccessible(true);
                return (Component) champ.get(mc.gui.hud);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        if (message == null || !(message.getContents() instanceof TranslatableContents traduit) || !traduit.getKey().equals(cle)) {
            throw new AssertionError(cas + " : message attendu " + cle + ", affiché : " + message);
        }
    }
}
