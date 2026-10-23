package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.data.EtatMonde;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Issue;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * G2.4 : l'état du monde est sauvegardé avec son numéro de version, puis relu au rechargement.
 */
public class EtatMondePersistanceTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        EtatMonde attendu = EtatMonde.NOUVEAU
                .avecEpoque(Epoque.GOLDEN_SON)
                .avecIssue("pendaison_eo", Issue.CANON)
                .avecZone("lykos", Camp.RISING);

        try (TestSingleplayerContext monde = context.worldBuilder().create()) {
            verifier("dans un monde neuf", EtatMonde.NOUVEAU, etatDuMonde(monde));
            monde.getServer().runOnServer(server -> EtatMondeServeur.set(server, attendu));
            sauvegarde = monde.getWorldSave();
        }

        verifierFichier(sauvegarde.getSaveDirectory());

        try (TestSingleplayerContext monde = sauvegarde.open()) {
            verifier("après rechargement", attendu, etatDuMonde(monde));
        }

        RedRising.LOGGER.info("[G2.4] OK : état du monde sauvegardé avec schemaVersion et relu");
    }

    private static EtatMonde etatDuMonde(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(EtatMondeServeur::get);
    }

    /** Le numéro de version est bien écrit dans le fichier du monde, pas seulement en mémoire. */
    private static void verifierFichier(Path dossierMonde) {
        Path fichier = dossierMonde.resolve("data/fabric/attachments.dat");
        if (!Files.exists(fichier)) {
            throw new AssertionError("Fichier des attachments globaux absent : " + fichier);
        }
        String contenu;
        try {
            contenu = NbtIo.readCompressed(fichier, NbtAccounter.unlimitedHeap()).toString();
        } catch (IOException e) {
            throw new AssertionError("Lecture impossible : " + fichier, e);
        }
        if (!contenu.contains("redrising:etat_monde") || !contenu.contains("schemaVersion:" + EtatMonde.VERSION_SCHEMA)) {
            throw new AssertionError("schemaVersion absent du fichier : " + contenu);
        }
        RedRising.LOGGER.info("[G2.4] Fichier {} : {}", dossierMonde.relativize(fichier), contenu);
    }

    private static void verifier(String moment, EtatMonde attendu, EtatMonde obtenu) {
        if (!attendu.equals(obtenu)) {
            throw new AssertionError("État du monde " + moment + " : attendu " + attendu + ", obtenu " + obtenu);
        }
        RedRising.LOGGER.info("[G2.4] État du monde {} : {}", moment, obtenu);
    }
}
