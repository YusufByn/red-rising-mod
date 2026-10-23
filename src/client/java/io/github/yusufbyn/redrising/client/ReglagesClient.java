package io.github.yusufbyn.redrising.client;

import io.github.yusufbyn.redrising.RedRising;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.OptionInstance;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Réglages propres à ce client, enregistrés dans {@code config/redrising-client.properties}.
 * Ils ne concernent que l'affichage chez ce joueur, jamais le jeu des autres.
 */
public final class ReglagesClient {
    private static final Path FICHIER = FabricLoader.getInstance().getConfigDir().resolve("redrising-client.properties");
    private static final String CLE_CHEVEUX = "cheveux_couleur";

    private static boolean cheveuxCouleur = true;

    /**
     * Cheveux teintés de la Couleur sur les modèles des joueurs (G3.5). Affiché dans
     * Options > Personnalisation du skin (voir {@code SkinCustomizationScreenMixin}).
     */
    public static final OptionInstance<Boolean> OPTION_CHEVEUX = OptionInstance.createBoolean(
            "options.redrising.cheveux", true, valeur -> {
                cheveuxCouleur = valeur;
                enregistrer();
            });

    private ReglagesClient() {
    }

    public static boolean cheveuxCouleur() {
        return cheveuxCouleur;
    }

    /** Appelé depuis {@link RedRisingClient#onInitializeClient()}. */
    public static void charger() {
        if (!Files.exists(FICHIER)) {
            return;
        }
        Properties proprietes = new Properties();
        try (Reader lecteur = Files.newBufferedReader(FICHIER)) {
            proprietes.load(lecteur);
        } catch (IOException e) {
            RedRising.LOGGER.warn("Réglages client illisibles ({}), valeurs par défaut", FICHIER, e);
            return;
        }
        cheveuxCouleur = Boolean.parseBoolean(proprietes.getProperty(CLE_CHEVEUX, "true"));
        OPTION_CHEVEUX.set(cheveuxCouleur);
    }

    private static void enregistrer() {
        Properties proprietes = new Properties();
        proprietes.setProperty(CLE_CHEVEUX, Boolean.toString(cheveuxCouleur));
        try (Writer ecrivain = Files.newBufferedWriter(FICHIER)) {
            proprietes.store(ecrivain, "Réglages client du mod Red Rising");
        } catch (IOException e) {
            RedRising.LOGGER.warn("Impossible d'enregistrer les réglages client ({})", FICHIER, e);
        }
    }
}
