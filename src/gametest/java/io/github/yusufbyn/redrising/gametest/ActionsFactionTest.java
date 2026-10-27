package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.EffetsActions;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.Jauges;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.factions.Temoin;
import io.github.yusufbyn.redrising.factions.Variation;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
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

import java.util.Objects;
import java.util.UUID;

/**
 * G5.2 : chaque action modifie les bonnes jauges. G6.1 : le Soutien d'un vol ne compte qu'une fois par coffre et par jour. Aider et dénoncer passent par de vrais clics (accroupi, clic droit) ;
 * tuer et voler sont joués côté serveur (coup du joueur, coffre ouvert puis refermé par lui), sans témoin
 * (les témoins de G5.5 sont vérifiés par {@code PatrouilleGrisTest}).
 */
public class ActionsFactionTest implements FabricClientGameTest {
    /** PNJ placé juste devant le joueur, qui regarde vers le sud et un peu vers le bas. */
    private static final Vec3 DEVANT = new Vec3(0.5, -60, 2.5);

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runCommand("tp @a 0.5 -60 0.5 0 20");
            // Départ au milieu de chaque jauge, pour voir les hausses comme les baisses
            monde.getServer().runOnServer(server -> {
                ServerPlayer joueur = OutilsTest.joueur(server);
                for (Jauge jauge : Jauge.values()) {
                    JaugesJoueur.set(joueur, jauge, 50);
                }
            });

            // --- Tuer : un PNJ de l'élite, de l'ordre, du peuple (sans témoin) ---
            for (Couleur victime : new Couleur[]{Couleur.OR, Couleur.GRIS, Couleur.ROUGE}) {
                attendre(monde, "tuer un PNJ " + victime.getSerializedName(), sansTemoin(EffetsActions.tuer(victime)), () ->
                        monde.getServer().runOnServer(server -> {
                            ServerPlayer joueur = OutilsTest.joueur(server);
                            Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), new Vec3(4.5, -60, 4.5), 0, victime));
                            pnj.hurtServer(server.overworld(), joueur.damageSources().playerAttack(joueur), Float.MAX_VALUE);
                        }));
            }

            // --- Aider : un PNJ Marron (peuple), du pain en main, accroupi + clic droit ---
            UUID marron = pnjDevant(monde, Couleur.MARRON);
            monde.getServer().runOnServer(server -> OutilsTest.joueur(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 3)));
            context.waitTicks(5);
            attendre(monde, "aider un PNJ marron", EffetsActions.aider(Couleur.MARRON), () -> clicAccroupi(context));
            verifierPain(monde, 2);
            verifierAucunEcran(context, "après l'aide (pas d'écran de commerce)");
            // Une seule fois par jour : le deuxième don est refusé, le pain reste
            attendre(monde, "aider le même PNJ le même jour", Variation.AUCUNE, () -> clicAccroupi(context));
            verifierPain(monde, 2);
            retirer(monde, marron);

            // --- Dénoncer : un PNJ Gris, mains vides ; deux PNJ du Rising à 6 et 10 blocs, le plus proche est arrêté ---
            UUID gris = pnjDevant(monde, Couleur.GRIS);
            UUID proche = pnjRebelle(monde, new Vec3(6.5, -60, 0.5));
            UUID loin = pnjRebelle(monde, new Vec3(-9.5, -60, 0.5));
            // Camp posé par la commande de debug pour l'un, directement pour l'autre
            monde.getServer().runCommand("rr pnj camp " + proche + " rising");
            monde.getServer().runOnServer(server -> Objects.requireNonNull(server.overworld().getEntity(loin)).setAttached(ActionsFaction.CAMP, Camp.RISING));
            monde.getServer().runOnServer(server -> OutilsTest.joueur(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
            context.waitTicks(5);
            attendre(monde, "dénoncer", EffetsActions.denoncer(), () -> clicAccroupi(context));
            boolean[] presents = monde.getServer().computeOnServer(server -> new boolean[]{
                    server.overworld().getEntity(proche) != null, server.overworld().getEntity(loin) != null});
            if (presents[0] || !presents[1]) {
                throw new AssertionError("Dénonciation : le PNJ du Rising le plus proche doit être arrêté, et lui seul (proche présent : "
                        + presents[0] + ", loin présent : " + presents[1] + ")");
            }
            verifierAucunEcran(context, "après la dénonciation");
            RedRising.LOGGER.info("[G5.2] Dénonciation : le PNJ du Rising le plus proche a été arrêté, l'autre est resté");
            retirer(monde, gris); // sinon il serait témoin des vols (le PNJ du Rising restant ne compte pas)

            // --- Voler (sans témoin) : prendre dans un coffre de la Société, puis du Rising ; ouvrir sans rien prendre ne compte pas ---
            voler(monde, new BlockPos(3, -60, -3), "societe", true, sansTemoin(EffetsActions.voler(Camp.SOCIETE)));
            voler(monde, new BlockPos(5, -60, -3), "rising", true, sansTemoin(EffetsActions.voler(Camp.RISING)));
            voler(monde, new BlockPos(7, -60, -3), "societe", false, Variation.AUCUNE);

            // --- G6.1 : la ferme à Soutien. Déposer son charbon dans le même coffre, le reprendre : vol sans Soutien ---
            BlockPos coffreSociete = new BlockPos(3, -60, -3);
            BlockPos coffreRising = new BlockPos(5, -60, -3);
            reprendreSonCharbon(monde, coffreSociete, "societe", sansTemoin(EffetsActions.volSansSoutien(EffetsActions.voler(Camp.SOCIETE))));
            // Même règle pour le tort d'un vol au Rising
            reprendreSonCharbon(monde, coffreRising, "rising", Variation.AUCUNE);

            // --- Un jour de jeu passe : la Suspicion retombe ---
            // Dans un test client, le serveur avance au pas du client (/tick sprint n'accélère rien) :
            // on avance l'horloge du monde jusqu'à 20 ticks de la fin du jour, puis on laisse passer la fin du jour.
            long finDuJour = monde.getServer().computeOnServer(server -> {
                long temps = server.overworld().getGameTime();
                long fin = temps - temps % ActionsFaction.TICKS_PAR_JOUR + ActionsFaction.TICKS_PAR_JOUR;
                ((ServerLevelData) server.overworld().getLevelData()).setGameTime(fin - 20);
                return fin;
            });
            attendre(monde, "un jour de jeu", EffetsActions.jourPasse(), () ->
                    monde.getServer().waitFor(server -> server.overworld().getGameTime() > finDuJour + 2, 100));

            // --- Le lendemain, le même coffre rapporte de nouveau du Soutien (et en coûte, pour le Rising) ---
            reprendreSonCharbon(monde, coffreSociete, "societe", sansTemoin(EffetsActions.voler(Camp.SOCIETE)));
            reprendreSonCharbon(monde, coffreRising, "rising", sansTemoin(EffetsActions.voler(Camp.RISING)));
        }
        RedRising.LOGGER.info("[G5.2] OK : tuer, aider, dénoncer, voler et le déclin de la Suspicion modifient les bonnes jauges");
    }

    private static Variation sansTemoin(Variation variation) {
        return EffetsActions.selonTemoins(variation, Temoin.AUCUN);
    }

    /** Joue l'action, puis vérifie que les jauges ont bougé exactement de la variation attendue (bornée selon la Couleur). */
    private static void attendre(TestSingleplayerContext monde, String action, Variation variation, Runnable jouer) {
        Jauges avant = jauges(monde);
        jouer.run();
        Jauges attendues = avant.plus(variation, Couleur.OR);
        // L'effet d'un clic arrive au serveur un peu plus tard
        try {
            monde.getServer().waitFor(server -> jaugesDe(OutilsTest.joueur(server)).equals(attendues), 40);
        } catch (AssertionError e) {
            throw new AssertionError(action + " : jauges attendues " + attendues + ", obtenues " + jauges(monde) + " (avant : " + avant + ")", e);
        }
        RedRising.LOGGER.info("[G5.2] {} : {} → {}", action, avant, attendues);
    }

    private static Jauges jauges(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(server -> jaugesDe(OutilsTest.joueur(server)));
    }

    private static Jauges jaugesDe(ServerPlayer joueur) {
        if (CouleurJoueur.get(joueur) != Couleur.OR) {
            throw new IllegalStateException("Le joueur de test doit être Or");
        }
        return JaugesJoueur.get(joueur);
    }

    /** Accroupi, un clic droit sur ce qui est devant (le PNJ), puis debout. */
    private static void clicAccroupi(ClientGameTestContext context) {
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(2);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(2);
        context.getInput().releaseKey(options -> options.keyShift);
    }

    /** Un PNJ immobile juste devant le joueur, tourné vers lui. */
    private static UUID pnjDevant(TestSingleplayerContext monde, Couleur couleur) {
        UUID id = monde.getServer().computeOnServer(server -> {
            Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), DEVANT, 180, couleur));
            pnj.setNoAi(true);
            return pnj.getUUID();
        });
        monde.getConnection().waitForClientboundEntityUpdates(EntityTypes.VILLAGER);
        return id;
    }

    private static UUID pnjRebelle(TestSingleplayerContext monde, Vec3 position) {
        return monde.getServer().computeOnServer(server -> {
            Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), position, 0, Couleur.ROUGE));
            pnj.setNoAi(true);
            return pnj.getUUID();
        });
    }

    private static void retirer(TestSingleplayerContext monde, UUID id) {
        monde.getServer().runOnServer(server -> Objects.requireNonNull(server.overworld().getEntity(id)).discard());
    }

    /** Coffre de ce camp (posé par la commande de debug) avec 5 diamants ; le joueur l'ouvre, prend 2 diamants ou rien, le referme. */
    private static void voler(TestSingleplayerContext monde, BlockPos pos, String camp, boolean prendre, Variation attendue) {
        monde.getServer().runOnServer(server -> {
            server.overworld().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            ((ChestBlockEntity) Objects.requireNonNull(server.overworld().getBlockEntity(pos))).setItem(0, new ItemStack(Items.DIAMOND, 5));
        });
        monde.getServer().runCommand("rr coffre set " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + camp);
        String action = (prendre ? "voler dans un coffre " : "ouvrir sans rien prendre un coffre ") + camp;
        attendre(monde, action, attendue, () -> monde.getServer().runOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            joueur.openMenu((ChestBlockEntity) Objects.requireNonNull(server.overworld().getBlockEntity(pos)));
            if (prendre) {
                joueur.getInventory().add(joueur.containerMenu.getSlot(0).remove(2));
            }
            joueur.closeContainer();
        }));
    }

    /**
     * La boucle signalée en G6.1 : le joueur dépose son propre charbon dans un coffre d'un camp et le referme (rien de
     * pris : rien ne compte), puis le rouvre et reprend son charbon (un vol, avec la variation attendue).
     */
    private static void reprendreSonCharbon(TestSingleplayerContext monde, BlockPos pos, String camp, Variation attendue) {
        attendre(monde, "déposer son charbon dans un coffre " + camp, Variation.AUCUNE, () -> monde.getServer().runOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            joueur.openMenu((ChestBlockEntity) Objects.requireNonNull(server.overworld().getBlockEntity(pos)));
            joueur.containerMenu.getSlot(1).set(new ItemStack(Items.COAL, 8));
            joueur.closeContainer();
        }));
        attendre(monde, "reprendre son charbon dans un coffre " + camp, attendue, () -> monde.getServer().runOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            joueur.openMenu((ChestBlockEntity) Objects.requireNonNull(server.overworld().getBlockEntity(pos)));
            joueur.getInventory().add(joueur.containerMenu.getSlot(1).remove(8));
            joueur.closeContainer();
        }));
    }

    private static void verifierPain(TestSingleplayerContext monde, int attendu) {
        int pain = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).getMainHandItem().getCount());
        if (pain != attendu) {
            throw new AssertionError("Pain en main : attendu " + attendu + ", obtenu " + pain);
        }
    }

    private static void verifierAucunEcran(ClientGameTestContext context, String moment) {
        if (context.computeOnClient(mc -> mc.gui.screen()) != null) {
            throw new AssertionError("Écran ouvert " + moment + " : " + context.computeOnClient(mc -> mc.gui.screen()));
        }
    }
}
