package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.client.ReglagesClient;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * G3.5 : la Couleur se voit sur le modèle (mains, yeux, cheveux). Ce test prend des captures à regarder,
 * copiées dans {@code build/captures-gametest/} ; {@code outils/planche_couleurs.py} les assemble en planche.
 *
 * <ul>
 *     <li>les 14 Couleurs côte à côte (mannequins), sur skin clair (Alex) et skin foncé (Makena) ;</li>
 *     <li>la même rangée avec un casque en fer, puis sans les cheveux (réglage client) ;</li>
 *     <li>le joueur en vue à la troisième personne (corps droit et tourné) et en vue première personne.</li>
 * </ul>
 */
public class ApparenceCouleurTest implements FabricClientGameTest {
    /**
     * Les mannequins sont alignés en z = 9.5, face au joueur posé en (0.5, -60, 0.5) qui regarde vers le sud (+z).
     * Vers le sud, les x croissants sont à gauche de l'écran : l'Or (1re Couleur) est en x = 7, le Rouge en x = -6,
     * rangée centrée sur le joueur.
     */
    private static final double Z_RANGEE = 9.5;
    private static final String SKIN_CLAIR = "entity/player/wide/alex";
    private static final String SKIN_FONCE = "entity/player/wide/makena";

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runCommand("tp @a 0.5 -60 0.5 0 10"); // regard légèrement vers le bas

            // --- Les 14 Couleurs côte à côte ---
            context.getInput().resizeWindow(1920, 1080);
            context.runOnClient(mc -> {
                mc.options.fov().set(50);
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
            });
            rangee(context, monde, SKIN_CLAIR);
            capture(context, monde, "rangee-clair");
            monde.getServer().runCommand("item replace entity @e[type=minecraft:mannequin] armor.head with minecraft:iron_helmet");
            capture(context, monde, "rangee-clair-casque");
            context.runOnClient(mc -> ReglagesClient.OPTION_CHEVEUX.set(false));
            monde.getServer().runCommand("item replace entity @e[type=minecraft:mannequin] armor.head with minecraft:air");
            capture(context, monde, "rangee-clair-sans-cheveux");
            context.runOnClient(mc -> ReglagesClient.OPTION_CHEVEUX.set(true));

            rangee(context, monde, SKIN_FONCE);
            capture(context, monde, "rangee-fonce");
            monde.getServer().runCommand("item replace entity @e[type=minecraft:mannequin] armor.head with minecraft:iron_helmet");
            capture(context, monde, "rangee-fonce-casque");
            retirerMannequins(monde);

            context.runOnClient(mc -> {
                mc.options.fov().set(70);
                mc.gui.hud.toggle();
            });
            context.getInput().resizeWindow(854, 480);

            // --- Le joueur lui-même ---
            monde.getServer().runOnServer(server -> CouleurJoueur.set(OutilsTest.joueur(server), Couleur.ROUGE));
            context.waitFor(mc -> mc.player != null && CouleurJoueur.get(mc.player) == Couleur.ROUGE, 40);

            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.getInput().lookAt(0, 10);
            context.waitTicks(5);
            OutilsTest.garderCapture(context.takeScreenshot("joueur-face"));
            // Corps tourné, tête vers la caméra : on voit le dos des mains. Le jeu est en pause entre deux ticks,
            // donc la rotation imposée tient le temps de la capture.
            for (int angle : new int[]{45, -45}) {
                context.runOnClient(mc -> {
                    var joueur = Objects.requireNonNull(mc.player);
                    joueur.yBodyRot = joueur.getYRot() + angle;
                    joueur.yBodyRotO = joueur.yBodyRot;
                });
                OutilsTest.garderCapture(context.takeScreenshot("joueur-" + (angle > 0 ? "gauche" : "droite")));
            }

            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().lookAt(0, 0);
            context.waitTicks(5);
            OutilsTest.garderCapture(context.takeScreenshot("premiere-personne"));

            // --- Le réglage « Cheveux de Couleur » dans Options > Personnalisation du skin ---
            context.setScreen(() -> new SkinCustomizationScreen(null, Minecraft.getInstance().options));
            context.waitForScreen(SkinCustomizationScreen.class);
            OutilsTest.garderCapture(context.takeScreenshot("reglage-cheveux"));
            cliquerReglageCheveux(context);
            verifierReglage(context, false);
            cliquerReglageCheveux(context);
            verifierReglage(context, true);
            context.setScreen(() -> null);
        }
        RedRising.LOGGER.info("[G3.5] OK : captures de l'apparence prises (à vérifier à l'œil)");
    }

    /** Remplace la rangée de mannequins : un par Couleur, dans l'ordre de l'enum, avec le skin donné. */
    private static void rangee(ClientGameTestContext context, TestSingleplayerContext monde, String skin) {
        retirerMannequins(monde);
        Couleur[] couleurs = Couleur.values();
        for (int i = 0; i < couleurs.length; i++) {
            // Légèrement tournés (160° au lieu de 180°) pour qu'on voie aussi le dos d'une main
            monde.getServer().runCommand("summon minecraft:mannequin " + (7.0 - i) + " -60 " + Z_RANGEE
                    + " {profile:{texture:\"" + skin + "\",model:\"wide\"},hide_description:1b,immovable:1b,Rotation:[160f,0f]}");
        }
        int poses = monde.getServer().computeOnServer(server -> {
            List<? extends Entity> mannequins = server.overworld().getEntities(EntityTypes.MANNEQUIN, entite -> true);
            // x décroissants = de gauche à droite à l'écran = ordre de l'enum
            List<? extends Entity> tries = mannequins.stream().sorted(Comparator.comparingDouble((Entity entite) -> entite.getX()).reversed()).toList();
            for (int i = 0; i < tries.size() && i < couleurs.length; i++) {
                tries.get(i).setAttached(CouleurJoueur.COULEUR, couleurs[i]);
            }
            return tries.size();
        });
        if (poses != couleurs.length) {
            throw new AssertionError("Mannequins posés : " + poses + " au lieu de " + couleurs.length);
        }
        monde.getConnection().waitForChunksRender();
    }

    /**
     * Vrai clic de souris sur le bouton « Cheveux de Couleur ». Il est dans une liste déroulante d'options,
     * que {@code clickScreenButton} ne parcourt pas : on retrouve le bouton dans la liste pour viser son centre.
     */
    private static void cliquerReglageCheveux(ClientGameTestContext context) {
        double[] centre = context.computeOnClient(mc -> {
            var liste = Objects.requireNonNull(mc.gui.screen()).children().stream()
                    .filter(enfant -> enfant instanceof OptionsList)
                    .map(enfant -> (OptionsList) enfant)
                    .findFirst().orElseThrow(() -> new AssertionError("Liste d'options absente"));
            var bouton = Objects.requireNonNull(liste.findOption(ReglagesClient.OPTION_CHEVEUX), "Bouton « Cheveux de Couleur » absent");
            int echelle = mc.getWindow().getGuiScale();
            return new double[]{(bouton.getX() + bouton.getWidth() / 2.0) * echelle, (bouton.getY() + bouton.getHeight() / 2.0) * echelle};
        });
        context.getInput().setCursorPos(centre[0], centre[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void verifierReglage(ClientGameTestContext context, boolean attendu) {
        boolean obtenu = context.computeOnClient(mc -> ReglagesClient.cheveuxCouleur());
        if (obtenu != attendu) {
            throw new AssertionError("Réglage « Cheveux de Couleur » : attendu " + attendu + ", obtenu " + obtenu);
        }
        RedRising.LOGGER.info("[G3.5] Réglage « Cheveux de Couleur » après clic : {}", obtenu);
    }

    /** Retire les mannequins sans les tuer (un mort reste visible le temps de son animation). */
    private static void retirerMannequins(TestSingleplayerContext monde) {
        monde.getServer().runOnServer(server -> server.overworld().getEntities(EntityTypes.MANNEQUIN, entite -> true).forEach(Entity::discard));
    }

    private static void capture(ClientGameTestContext context, TestSingleplayerContext monde, String nom) {
        // Laisse arriver la Couleur (synchronisée) et l'équipement des mannequins avant la capture
        monde.getConnection().waitForClientboundPackets();
        context.waitTicks(10);
        OutilsTest.garderCapture(context.takeScreenshot(nom));
    }
}
