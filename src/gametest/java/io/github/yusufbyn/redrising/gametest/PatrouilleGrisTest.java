package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.Controle;
import io.github.yusufbyn.redrising.factions.EffetsActions;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.Jauges;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.factions.Ronde;
import io.github.yusufbyn.redrising.factions.Temoin;
import io.github.yusufbyn.redrising.pnj.PatrouillePnj;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * G5.5 : témoins, ronde d'une patrouille de Gris et contrôles. Une patrouille réagit à un joueur suspect :
 * à 39 de Suspicion elle l'ignore, à 40 elle le contrôle ; obéir fait baisser la Suspicion une fois par jour,
 * fuir la fait monter, se déconnecter pendant un contrôle aussi.
 */
public class PatrouilleGrisTest implements FabricClientGameTest {
    /** Coffre de la Société pour les vols, loin de la patrouille. */
    private static final BlockPos COFFRE = new BlockPos(-40, -60, 0);
    private static final String DEVANT_LE_COFFRE = "tp @a -39.5 -60 -2.5 0 0";
    /** Ronde en triangle : 8 blocs entre chaque point. */
    private static final List<BlockPos> RONDE = List.of(new BlockPos(10, -60, 0), new BlockPos(18, -60, 0), new BlockPos(18, -60, 8));

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        UUID gris;

        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            temoins(monde, context);

            // --- Ronde, de nuit : le Gris passe par ses trois points et revient au premier ---
            jauges(monde, 50, 0, 0);
            monde.getServer().runCommand("time set midnight");
            monde.getServer().runCommand("rr patrouille creer 10 -60 0 18 -60 0 18 -60 8");
            gris = monde.getServer().computeOnServer(server -> patrouilleur(server).getUUID());
            List<Integer> parcours = new ArrayList<>();
            for (int i = 0; i < 200 && !(parcours.contains(2) && parcours.getLast() == 0); i++) {
                context.waitTicks(10);
                int index = monde.getServer().computeOnServer(server -> ronde(server, gris).index());
                if (parcours.isEmpty() || parcours.getLast() != index) {
                    parcours.add(index);
                }
            }
            if (!parcours.equals(List.of(0, 1, 2, 0))) {
                throw new AssertionError("Ronde de nuit : points visités " + parcours + ", attendu [0, 1, 2, 0]");
            }
            RedRising.LOGGER.info("[G5.5] Ronde de nuit faite : points {}", parcours);
            monde.getServer().runCommand("time set noon");

            controles(monde, context, gris);

            // Se déconnecter pendant un contrôle compte comme une fuite : on quitte le monde juste après le « Halte »
            reprendreLesControles(monde);
            pres(monde, gris);
            attendreDebutControle(monde, context, gris, "contrôle quitté");
            sauvegarde = monde.getWorldSave();
        }

        // --- Rechargement : le Gris a gardé sa ronde et repart ; le joueur garde son suivi de contrôle ---
        try (TestSingleplayerContext monde = sauvegarde.open()) {
            // La fuite est comptée au retour du joueur
            monde.getServer().waitFor(server -> JaugesJoueur.get(OutilsTest.joueur(server)).equals(new Jauges(40, 0, 60)), 40);
            verifierJauges(monde, "déconnexion pendant un contrôle, compté au retour", new Jauges(40, 0, 60));
            OutilsTest.attendreArrivee(context);
            int depart = monde.getServer().computeOnServer(server -> ronde(server, gris).index());
            monde.getServer().waitFor(server -> ronde(server, gris).index() != depart, 600);
            long jourBlanchi = monde.getServer().computeOnServer(server -> PatrouillePnj.suivi(OutilsTest.joueur(server)).dernierJourBlanchi());
            if (jourBlanchi < 0) {
                throw new AssertionError("Suivi des contrôles perdu au rechargement");
            }
            RedRising.LOGGER.info("[G5.5] Après rechargement : ronde reprise (point {} puis suivant), suivi des contrôles gardé", depart + 1);
        }
        RedRising.LOGGER.info("[G5.5] OK : témoins, ronde de nuit, contrôle ignoré à 39, obéi à 40 (une baisse par jour), fuite");
    }

    // --- Témoins ---

    /** Un vol dans un coffre de la Société, sans témoin, devant un Rouge, devant un Gris, et avec un Gris derrière un mur. */
    private static void temoins(TestSingleplayerContext monde, ClientGameTestContext context) {
        monde.getServer().runCommand(DEVANT_LE_COFFRE);
        context.waitTicks(5);
        voler(monde, context, "sans témoin", null, false, Temoin.AUCUN, null);
        voler(monde, context, "devant un Rouge", Couleur.ROUGE, false, Temoin.ORDINAIRE, "faction.redrising.temoin");
        voler(monde, context, "devant un Gris", Couleur.GRIS, false, Temoin.GRIS, "faction.redrising.temoin.gris");
        voler(monde, context, "un Gris derrière un mur", Couleur.GRIS, true, Temoin.AUCUN, null);
    }

    /**
     * Le joueur (au sud du coffre, à 2 blocs) prend 2 diamants. Le témoin éventuel est à 6 blocs derrière le joueur ;
     * {@code mur} : un mur de pierre entre eux.
     */
    private static void voler(TestSingleplayerContext monde, ClientGameTestContext context, String cas, Couleur temoin,
                              boolean mur, Temoin attendu, String message) {
        jauges(monde, 50, 0, 0);
        BlockPos posTemoin = new BlockPos(-40, -60, -8);
        UUID pnj = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            niveau.setBlockAndUpdate(COFFRE, Blocks.CHEST.defaultBlockState());
            ChestBlockEntity coffre = (ChestBlockEntity) Objects.requireNonNull(niveau.getBlockEntity(COFFRE));
            coffre.setItem(0, new ItemStack(Items.DIAMOND, 5));
            coffre.setAttached(ActionsFaction.CAMP, Camp.SOCIETE);
            for (int x = -43; x <= -37; x++) {
                for (int y = -60; y <= -57; y++) {
                    niveau.setBlockAndUpdate(new BlockPos(x, y, -5), mur ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
            if (temoin == null) {
                return null;
            }
            Villager v = Objects.requireNonNull(PnjCouleur.creer(niveau, Vec3.atBottomCenterOf(posTemoin), 0, temoin));
            v.setNoAi(true);
            return v.getUUID();
        });
        context.waitTicks(5);
        context.runOnClient(mc -> mc.gui.hud.setOverlayMessage(Component.literal("témoin"), false));
        Jauges avant = lireJauges(monde);
        monde.getServer().runOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            joueur.openMenu((ChestBlockEntity) Objects.requireNonNull(server.overworld().getBlockEntity(COFFRE)));
            joueur.getInventory().add(joueur.containerMenu.getSlot(0).remove(2));
            joueur.closeContainer();
        });
        Jauges attendues = avant.plus(EffetsActions.selonTemoins(EffetsActions.voler(Camp.SOCIETE), attendu), Couleur.OR);
        if (!lireJauges(monde).equals(attendues)) {
            throw new AssertionError("Vol " + cas + " : jauges attendues " + attendues + ", obtenues " + lireJauges(monde));
        }
        if (message != null) {
            context.waitTicks(5);
            verifierMessage(context, "vol " + cas, message);
        }
        if (pnj != null) {
            monde.getServer().runOnServer(server -> Objects.requireNonNull(server.overworld().getEntity(pnj)).discard());
        }
        RedRising.LOGGER.info("[G5.5] Vol {} : {} → {}", cas, avant, attendues);
    }

    // --- Contrôles ---

    private static void controles(TestSingleplayerContext monde, ClientGameTestContext context, UUID gris) {
        // Suspicion 39 : le Gris passe sans s'arrêter
        jauges(monde, 50, 0, 39);
        pres(monde, gris);
        context.waitTicks(60);
        if (controle(monde, gris)) {
            throw new AssertionError("Suspicion 39 : le Gris ne devait pas contrôler");
        }
        RedRising.LOGGER.info("[G5.5] Suspicion 39 : pas de contrôle");

        // Suspicion 40 : contrôle ; le joueur ne bouge pas, la Suspicion baisse de 5
        jauges(monde, 50, 0, 40);
        pres(monde, gris);
        attendreDebutControle(monde, context, gris, "Suspicion 40");
        attendreFinControle(monde, gris);
        verifierJauges(monde, "contrôle obéi", new Jauges(50, 0, 35));
        context.waitTicks(3);
        verifierMessage(context, "contrôle obéi", "faction.redrising.controle.fin");

        // Même jour, deuxième contrôle obéi : plus de baisse
        reprendreLesControles(monde);
        jauges(monde, 50, 0, 40);
        pres(monde, gris);
        attendreDebutControle(monde, context, gris, "deuxième contrôle du jour");
        attendreFinControle(monde, gris);
        verifierJauges(monde, "deuxième contrôle obéi le même jour", new Jauges(50, 0, 40));

        // Fuite, dès le « Halte » : Loyauté -5, Suspicion +10
        reprendreLesControles(monde);
        pres(monde, gris);
        attendreDebutControle(monde, context, gris, "contrôle fui");
        Vec3 ici = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).position());
        monde.getServer().runCommand("tp @a " + (ici.x + Controle.CONTROLE.distanceFuite() + 8) + " -60 " + ici.z);
        attendreFinControle(monde, gris);
        verifierJauges(monde, "contrôle fui", new Jauges(45, 0, 50));
        context.waitTicks(3);
        verifierMessage(context, "contrôle fui", "faction.redrising.controle.fuite");
    }

    /** Le joueur à 5 blocs du Gris, sur le sol du monde plat. */
    private static void pres(TestSingleplayerContext monde, UUID gris) {
        Vec3 pos = monde.getServer().computeOnServer(server -> Objects.requireNonNull(server.overworld().getEntity(gris)).position());
        monde.getServer().runCommand("tp @a " + (pos.x + 5) + " -60 " + pos.z);
    }

    private static boolean controle(TestSingleplayerContext monde, UUID gris) {
        return monde.getServer().computeOnServer(server -> PatrouillePnj.suspectControle(
                (Villager) Objects.requireNonNull(server.overworld().getEntity(gris))) != null);
    }

    private static void attendreDebutControle(TestSingleplayerContext monde, ClientGameTestContext context, UUID gris, String cas) {
        monde.getServer().waitFor(server -> PatrouillePnj.suspectControle((Villager) Objects.requireNonNull(server.overworld().getEntity(gris))) != null, 60);
        context.waitTicks(3);
        verifierMessage(context, cas, "faction.redrising.controle.halte");
        RedRising.LOGGER.info("[G5.5] {} : « Halte ! Contrôle. »", cas);
    }

    private static void attendreFinControle(TestSingleplayerContext monde, UUID gris) {
        monde.getServer().waitFor(server -> PatrouillePnj.suspectControle((Villager) Objects.requireNonNull(server.overworld().getEntity(gris))) == null,
                Controle.CONTROLE.approcheMax() + Controle.CONTROLE.duree());
    }

    /** Efface l'attente entre deux contrôles, en gardant le jour du dernier blanchiment. */
    private static void reprendreLesControles(TestSingleplayerContext monde) {
        monde.getServer().runOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            joueur.setAttached(PatrouillePnj.SUIVI_CONTROLE, new PatrouillePnj.SuiviControle(
                    -Controle.ATTENTE_ENTRE_CONTROLES, PatrouillePnj.suivi(joueur).dernierJourBlanchi()));
        });
    }

    // --- Outils ---

    private static Villager patrouilleur(MinecraftServer server) {
        return server.overworld().getEntities(EntityTypes.VILLAGER, new AABB(RONDE.getFirst()).inflate(4), pnj -> pnj.hasAttached(PatrouillePnj.RONDE)).getFirst();
    }

    private static Ronde ronde(MinecraftServer server, UUID gris) {
        return Objects.requireNonNull(Objects.requireNonNull(server.overworld().getEntity(gris)).getAttached(PatrouillePnj.RONDE));
    }

    private static void jauges(TestSingleplayerContext monde, int loyaute, int soutien, int suspicion) {
        monde.getServer().runOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            JaugesJoueur.set(joueur, Jauge.LOYAUTE, loyaute);
            JaugesJoueur.set(joueur, Jauge.SOUTIEN, soutien);
            JaugesJoueur.set(joueur, Jauge.SUSPICION, suspicion);
        });
    }

    private static Jauges lireJauges(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(server -> JaugesJoueur.get(OutilsTest.joueur(server)));
    }

    private static void verifierJauges(TestSingleplayerContext monde, String cas, Jauges attendues) {
        Jauges jauges = lireJauges(monde);
        if (!jauges.equals(attendues)) {
            throw new AssertionError(cas + " : jauges attendues " + attendues + ", obtenues " + jauges);
        }
        RedRising.LOGGER.info("[G5.5] {} : {}", cas, jauges);
    }

    /** Dernier message au-dessus de la barre d'objets (champ privé du HUD, lu par réflexion : test seulement). */
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
        if (message == null || !(message.getContents() instanceof TranslatableContents texte) || !texte.getKey().equals(cle)) {
            throw new AssertionError(cas + " : message attendu " + cle + ", affiché : " + message);
        }
    }
}
