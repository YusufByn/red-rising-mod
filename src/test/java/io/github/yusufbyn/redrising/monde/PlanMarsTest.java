package io.github.yusufbyn.redrising.monde;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plan des régions de Mars (G7.1.1), sur des terrains inventés : distances, eau, écarts, cité plate, mine hors recherche. */
class PlanMarsTest {
    private static final BlockPos APPARITION = new BlockPos(40, 70, -24);

    /** Mer à l'est (x > 900), collines au sud (z > 400), plaine ailleurs. */
    private static final PlanMars.Terrain MER_ET_COLLINES = new PlanMars.Terrain() {
        @Override
        public boolean eau(int x, int z) {
            return x > 900;
        }

        @Override
        public int hauteur(int x, int z) {
            return z > 400 ? 70 + (int) (30 * Math.sin(x / 9.0) * Math.cos(z / 7.0)) : 66;
        }
    };

    @Test
    void planCoherentEtComplet() {
        for (long graine : new long[]{1, 42, -7_335_412_901L}) {
            PlanMars plan = PlanMars.tirer(graine, APPARITION, APPARITION, MER_ET_COLLINES);
            assertEquals(Region.values().length, plan.regions().size());
            assertEquals(List.of(), plan.defauts(APPARITION, MER_ET_COLLINES), "graine " + graine);
            plan.regions().forEach((region, pos) -> {
                if (region != Region.POLES) {
                    assertTrue(pos.getX() + 48 <= 900, region + " dans la mer : " + pos);
                }
            });
            assertTrue(PlanMars.ecartDeHauteur(plan.regions().get(Region.CITE), MER_ET_COLLINES) <= PlanMars.PLATITUDE_CITE);
        }
    }

    @Test
    void memeGraineMemePlan() {
        assertEquals(PlanMars.tirer(42, APPARITION, APPARITION, MER_ET_COLLINES), PlanMars.tirer(42, APPARITION, APPARITION, MER_ET_COLLINES));
        assertNotEquals(PlanMars.tirer(42, APPARITION, APPARITION, MER_ET_COLLINES).regions(),
                PlanMars.tirer(43, APPARITION, APPARITION, MER_ET_COLLINES).regions());
    }

    @Test
    void mineNiSousLeSpawnNiDansLaRecherche() {
        // Point d'apparition trouvé à 80 blocs de l'estimation : la mine reste hors des 14 chunks autour de l'estimation
        BlockPos estimation = APPARITION.offset(-80, 0, 0);
        for (long graine = 0; graine < 50; graine++) {
            PlanMars plan = PlanMars.tirer(graine, APPARITION, estimation, MER_ET_COLLINES);
            BlockPos mine = plan.regions().get(Region.MINE);
            double distance = Math.hypot(mine.getX() - APPARITION.getX(), mine.getZ() - APPARITION.getZ());
            assertTrue(distance >= 199 && distance <= 401, "graine " + graine + " : mine à " + distance);
            int chunks = Math.max(Math.abs((mine.getX() >> 4) - (estimation.getX() >> 4)), Math.abs((mine.getZ() >> 4) - (estimation.getZ() >> 4)));
            assertTrue(chunks >= PlanMars.CHUNKS_HORS_RECHERCHE, "graine " + graine + " : mine à " + chunks + " chunks de l'estimation");
        }
    }

    @Test
    void polesAuNord() {
        BlockPos poles = PlanMars.tirer(5, APPARITION, APPARITION, MER_ET_COLLINES).regions().get(Region.POLES);
        assertEquals(APPARITION.getX(), poles.getX());
        assertTrue(poles.getZ() < -PlanMars.LATITUDE_POLES);
    }

    @Test
    void toujoursUnPlanMemeSansBonnePlace() {
        // Tout est océan : chaque région a quand même une position (la moins mauvaise), et le défaut est visible
        PlanMars.Terrain ocean = new PlanMars.Terrain() {
            @Override
            public boolean eau(int x, int z) {
                return true;
            }

            @Override
            public int hauteur(int x, int z) {
                return 40;
            }
        };
        PlanMars plan = PlanMars.tirer(9, APPARITION, APPARITION, ocean);
        assertEquals(Region.values().length, plan.regions().size());
        assertTrue(plan.defauts(APPARITION, ocean).stream().anyMatch(defaut -> defaut.contains("dans l'eau")));
    }

    @Test
    void defautsDetectes() {
        List<String> defauts = PlanMars.defauts(Region.MINE, APPARITION.offset(10, 0, 0), Map.of(), APPARITION, APPARITION, MER_ET_COLLINES);
        assertTrue(defauts.stream().anyMatch(defaut -> defaut.contains("point d'apparition")), defauts.toString());
        assertTrue(defauts.stream().anyMatch(defaut -> defaut.contains("recherche")), defauts.toString());
        List<String> tropPres = PlanMars.defauts(Region.CITE, APPARITION.offset(800, 0, 100), Map.of(Region.GARNISON, APPARITION.offset(600, 0, 0)),
                APPARITION, APPARITION, MER_ET_COLLINES);
        assertTrue(tropPres.stream().anyMatch(defaut -> defaut.contains("trop près de garnison")), tropPres.toString());
    }
}
