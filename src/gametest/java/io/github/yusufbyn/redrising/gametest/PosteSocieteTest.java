package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.factions.Acces;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.EffetsActions;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.Jauges;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.monde.PosteSociete;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;

import java.util.Objects;

/**
 * G5.3 : un joueur à Loyauté basse est refoulé du poste de la Société (porte fermée, intérieur surveillé),
 * un joueur à 50 et un Rouge irréprochable (40) entrent ; casser le coffre du poste depuis dehors compte comme un vol.
 * Les clics sur la porte sont de vrais clics.
 */
public class PosteSocieteTest implements FabricClientGameTest {
    /** Coin nord-ouest du sol : porte en (0, -60, 4), sortie en (0, -60, 2), salle de x -2..2 et z 5..9. */
    private static final BlockPos ORIGINE = new BlockPos(-3, -61, 4);
    private static final BlockPos PORTE = new BlockPos(0, -60, 4);
    private static final BlockPos COFFRE = new BlockPos(2, -60, 9);
    /** Devant la porte, tourné vers elle (sud). */
    private static final String DEVANT_LA_PORTE = "tp @a 0.5 -60 2.5 0 0";
    private static final String DANS_LA_SALLE = "tp @a 0.5 -60 5.5 0 10"; // entre la porte et le comptoir

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave sauvegarde;

        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runCommand("rr structure poste " + ORIGINE.getX() + " " + ORIGINE.getY() + " " + ORIGINE.getZ());
            monde.getServer().runCommand(DEVANT_LA_PORTE);
            monde.getConnection().waitForChunksRender();

            // --- Loyauté 50 : la porte s'ouvre, on reste dans la salle ---
            loyaute(monde, 50);
            clicSurLaPorte(context);
            verifierPorte(monde, context, "Loyauté 50", true);
            monde.getServer().runCommand(DANS_LA_SALLE);
            context.waitTicks(30);
            verifierDedans(monde, "Loyauté 50", true);
            OutilsTest.garderCapture(context.takeScreenshot("poste-societe"));

            // --- Loyauté 35 : la porte reste fermée, et un joueur mis dans la salle est reconduit dehors ---
            fermerLaPorte(monde);
            monde.getServer().runCommand(DEVANT_LA_PORTE);
            loyaute(monde, 35);
            clicSurLaPorte(context);
            verifierPorte(monde, context, "Loyauté 35", false);
            monde.getServer().runCommand(DANS_LA_SALLE);
            context.waitTicks(30);
            verifierDedans(monde, "Loyauté 35", false);

            // --- Rouge irréprochable : plafonné à 40, il entre tout juste ---
            monde.getServer().runOnServer(server -> CouleurJoueur.set(OutilsTest.joueur(server), Couleur.ROUGE));
            loyaute(monde, 50);
            int loyauteRouge = jauges(monde).loyaute();
            if (loyauteRouge != Acces.SEUIL_LOYAUTE_SOCIETE) {
                throw new AssertionError("Loyauté d'un Rouge mise à 50 : attendu " + Acces.SEUIL_LOYAUTE_SOCIETE + ", obtenu " + loyauteRouge);
            }
            monde.getServer().runCommand(DEVANT_LA_PORTE);
            clicSurLaPorte(context);
            verifierPorte(monde, context, "Rouge à 40", true);
            monde.getServer().runCommand(DANS_LA_SALLE);
            context.waitTicks(30);
            verifierDedans(monde, "Rouge à 40", true);

            // --- Casser le coffre du poste depuis dehors (mur percé) : un vol de la Société ---
            monde.getServer().runOnServer(server -> CouleurJoueur.set(OutilsTest.joueur(server), Couleur.OR));
            loyaute(monde, 50);
            monde.getServer().runCommand("tp @a 3.5 -60 9.5 90 0"); // dehors, contre le mur est
            Jauges avant = jauges(monde);
            boolean casse = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).gameMode.destroyBlock(COFFRE));
            Jauges attendues = avant.plus(EffetsActions.voler(Camp.SOCIETE), Couleur.OR);
            if (!casse || !jauges(monde).equals(attendues)) {
                throw new AssertionError("Coffre du poste cassé (" + casse + ") : jauges attendues " + attendues + ", obtenues " + jauges(monde));
            }
            RedRising.LOGGER.info("[G5.3] Coffre de la Société cassé depuis dehors : {} → {}", avant, attendues);

            sauvegarde = monde.getWorldSave();
        }

        // --- Rechargement : le poste est toujours surveillé ---
        try (TestSingleplayerContext monde = sauvegarde.open()) {
            int postes = monde.getServer().computeOnServer(server -> PosteSociete.postes(server).size());
            if (postes != 1) {
                throw new AssertionError("Postes après rechargement : " + postes);
            }
            loyaute(monde, 35);
            monde.getServer().runCommand(DANS_LA_SALLE);
            context.waitTicks(30);
            verifierDedans(monde, "Loyauté 35 après rechargement", false);
        }
        RedRising.LOGGER.info("[G5.3] OK : poste de la Société, entrée à partir de {} de Loyauté, refoulement et vol par casse", Acces.SEUIL_LOYAUTE_SOCIETE);
    }

    private static void loyaute(TestSingleplayerContext monde, int valeur) {
        monde.getServer().runOnServer(server -> JaugesJoueur.set(OutilsTest.joueur(server), Jauge.LOYAUTE, valeur));
    }

    private static Jauges jauges(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(server -> JaugesJoueur.get(OutilsTest.joueur(server)));
    }

    /** Clic droit sur la porte, que le joueur regarde. */
    private static void clicSurLaPorte(ClientGameTestContext context) {
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(10);
    }

    private static void fermerLaPorte(TestSingleplayerContext monde) {
        monde.getServer().runOnServer(server -> {
            var niveau = server.overworld();
            niveau.setBlockAndUpdate(PORTE, niveau.getBlockState(PORTE).setValue(DoorBlock.OPEN, false));
        });
    }

    /** Porte ouverte ou non, côté serveur et côté client (le client doit avoir reçu la correction s'il a été refusé). */
    private static void verifierPorte(TestSingleplayerContext monde, ClientGameTestContext context, String cas, boolean ouverte) {
        boolean serveur = monde.getServer().computeOnServer(server -> {
            var etat = server.overworld().getBlockState(PORTE);
            return etat.is(Blocks.DARK_OAK_DOOR) && etat.getValue(DoorBlock.OPEN);
        });
        boolean client = context.computeOnClient(mc -> Objects.requireNonNull(mc.level).getBlockState(PORTE).getValue(DoorBlock.OPEN));
        if (serveur != ouverte || client != ouverte) {
            throw new AssertionError("Porte (" + cas + ") : attendu " + (ouverte ? "ouverte" : "fermée") + ", serveur " + serveur + ", client " + client);
        }
        RedRising.LOGGER.info("[G5.3] Porte ({}) : {}", cas, ouverte ? "ouverte" : "fermée");
    }

    private static void verifierDedans(TestSingleplayerContext monde, String cas, boolean dedans) {
        BlockPos pos = monde.getServer().computeOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            return joueur.blockPosition();
        });
        boolean estDedans = monde.getServer().computeOnServer(server -> PosteSociete.postes(server).getFirst().interieur().isInside(pos));
        if (estDedans != dedans) {
            throw new AssertionError("Joueur (" + cas + ") en " + pos.toShortString() + " : attendu " + (dedans ? "dans la salle" : "reconduit dehors"));
        }
        RedRising.LOGGER.info("[G5.3] Joueur ({}) : {}", cas, dedans ? "reste dans la salle" : "reconduit dehors, en " + pos.toShortString());
    }
}
