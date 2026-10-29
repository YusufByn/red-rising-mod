package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.monde.PlanMars;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Les épreuves longues de la mine (G7.2), une vingtaine de minutes : seulement avec {@code ./gradlew runClientGameTest
 * -PtestsLongs}, quand le code de la mine ou de la génération change (règle dans {@code CLAUDE.md}). Sans l'option, le test
 * ne fait rien.
 *
 * <ul>
 *     <li>Première graine : toute l'emprise générée (références, parcours depuis le bas du puits, fuites, ciel, autres
 *     structures, générateurs, coût de la pose et de la règle des décorations) ; le réseau éclairé et 2 minutes de monstres ;
 *     les 32 wagonnets, les rails et les butoirs, le coût d'un wagonnet ; tous les repères et les points d'entrée des Gris.</li>
 *     <li>Trois graines : l'emprise entière, le comptage des salles, des minerais et des coffres.</li>
 * </ul>
 */
public class MineLykosLongTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("redrising.testsLongs")) {
            RedRising.LOGGER.info("[G7.2] Épreuves longues de la mine ignorées (./gradlew runClientGameTest -PtestsLongs pour les lancer)");
            return;
        }
        for (int i = 0; i < MineLykosTest.GRAINES.length; i++) {
            long graine = MineLykosTest.GRAINES[i];
            try (TestSingleplayerContext monde = MineLykosTest.mondeMars(context, graine)) {
                if (i == 0) {
                    MineLykosTest.emprise(monde, graine);
                    MineLykosTest.reseauEtMonstres(monde, context);
                    MineLykosTest.reperesDeLEmprise(monde);
                    MineLykosTest.wagonnetsDeLEmprise(monde, context);
                } else {
                    MineLykosTest.charger(monde, PlanMars.EMPRISE_MINE);
                }
                MineLykosTest.comptage(monde, graine);
            }
        }
        RedRising.LOGGER.info("[G7.2] OK : épreuves longues de la mine (emprise, monstres, wagonnets, repères, comptage)");
    }
}
