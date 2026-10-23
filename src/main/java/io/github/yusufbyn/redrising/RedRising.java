package io.github.yusufbyn.redrising;

import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Point d'entrée commun (serveur et client). */
public class RedRising implements ModInitializer {
    public static final String MOD_ID = "redrising";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Red Rising : initialisation");
        CouleurJoueur.init();
    }
}
