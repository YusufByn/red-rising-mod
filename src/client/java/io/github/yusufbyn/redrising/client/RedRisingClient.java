package io.github.yusufbyn.redrising.client;

import io.github.yusufbyn.redrising.RedRising;
import net.fabricmc.api.ClientModInitializer;

/** Point d'entrée client uniquement (HUD, écrans, rendu). */
public class RedRisingClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RedRising.LOGGER.info("Red Rising : initialisation client");
        ReglagesClient.charger();
        HudJauges.init();
        ToucheCapacite.init();
        MarqueCouleurLayer.init();
    }
}
