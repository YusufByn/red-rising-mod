package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.ModeNaissance;
import io.github.yusufbyn.redrising.couleur.Naissance;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * G4.1 : l'option Test / Choix libre se règle à la création du monde et y est enregistrée.
 * Passe par le vrai écran de création : clic sur « Naissance », puis sur « Créer le monde ».
 */
public class ModeNaissanceTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        // Monde créé sans toucher à l'option : Test, la valeur par défaut du GDD
        try (TestSingleplayerContext monde = context.worldBuilder().create()) {
            verifier("monde par défaut", ModeNaissance.TEST, monde.getServer().computeOnServer(Naissance::mode));
        }

        // Écran de création : onglet « Jeu », bouton « Naissance »
        context.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen())));
        context.waitForScreen(CreateWorldScreen.class);
        context.clickScreenButton(Naissance.CLE_REGLE);
        ModeNaissance choisi = context.computeOnClient(mc ->
                ((CreateWorldScreen) Objects.requireNonNull(mc.gui.screen())).getUiState().getGameRules().get(Naissance.MODE));
        verifier("après un clic sur le bouton", ModeNaissance.CHOIX_LIBRE, choisi);
        OutilsTest.garderCapture(context.takeScreenshot("creation-monde-naissance"));

        // Créer le monde et y entrer
        context.clickScreenButton("selectWorld.create");
        context.waitFor(mc -> mc.player != null && mc.gui.screen() == null, 20 * 60);
        verifier("dans le monde créé", ModeNaissance.CHOIX_LIBRE,
                context.computeOnClient(mc -> Naissance.mode(Objects.requireNonNull(mc.getSingleplayerServer()))));
        Path dossier = context.computeOnClient(mc -> Objects.requireNonNull(mc.getSingleplayerServer()).getWorldPath(LevelResource.ROOT));
        String nomDossier = dossier.normalize().getFileName().toString();

        // Quitter : le monde est sauvegardé, l'option doit être dans son fichier
        quitterMonde(context);
        verifierFichier(dossier.resolve("data/minecraft/game_rules.dat"));

        // Rouvrir le monde : l'option est relue
        context.runOnClient(mc -> mc.createWorldOpenFlows().openWorld(nomDossier, () -> mc.gui.setScreen(new TitleScreen())));
        context.waitFor(mc -> mc.player != null && mc.gui.screen() == null, 20 * 60);
        verifier("après réouverture", ModeNaissance.CHOIX_LIBRE,
                context.computeOnClient(mc -> Naissance.mode(Objects.requireNonNull(mc.getSingleplayerServer()))));
        quitterMonde(context);

        RedRising.LOGGER.info("[G4.1] OK : mode de naissance choisi à la création, enregistré dans le monde et relu");
    }

    /** Comme la fermeture d'un monde par Fabric : déconnexion, attente de l'arrêt du serveur (sauvegarde), écran titre. */
    private static void quitterMonde(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            Objects.requireNonNull(mc.level).disconnect(Component.translatable("menu.savingLevel"));
            mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")), false);
        });
        context.waitFor(mc -> mc.level == null && mc.getSingleplayerServer() == null, 20 * 60);
        context.waitTicks(2);
        context.setScreen(TitleScreen::new);
    }

    private static void verifierFichier(Path fichier) {
        String contenu;
        try {
            contenu = NbtIo.readCompressed(fichier, NbtAccounter.unlimitedHeap()).toString();
        } catch (IOException e) {
            throw new AssertionError("Lecture impossible : " + fichier, e);
        }
        if (!contenu.contains("redrising:mode_naissance") || !contenu.contains("choix_libre")) {
            throw new AssertionError("Mode de naissance absent de " + fichier + " : " + contenu);
        }
        RedRising.LOGGER.info("[G4.1] Fichier {} : contient redrising:mode_naissance = choix_libre", fichier.getFileName());
    }

    private static void verifier(String moment, ModeNaissance attendu, ModeNaissance obtenu) {
        if (attendu != obtenu) {
            throw new AssertionError("Mode de naissance " + moment + " : attendu " + attendu + ", obtenu " + obtenu);
        }
        RedRising.LOGGER.info("[G4.1] Mode de naissance {} : {}", moment, obtenu.getSerializedName());
    }
}
