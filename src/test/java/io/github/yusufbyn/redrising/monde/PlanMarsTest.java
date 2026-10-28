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
    private static final PlanMars.ChunksPrepares AUCUN = PlanMars.ChunksPrepares.AUCUN;

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
            PlanMars plan = PlanMars.tirer(graine, APPARITION, MER_ET_COLLINES, AUCUN);
            assertEquals(Region.values().length, plan.regions().size());
            assertEquals(List.of(), plan.defauts(MER_ET_COLLINES, AUCUN), "graine " + graine);
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
        assertEquals(PlanMars.tirer(42, APPARITION, MER_ET_COLLINES, AUCUN), PlanMars.tirer(42, APPARITION, MER_ET_COLLINES, AUCUN));
        assertNotEquals(PlanMars.tirer(42, APPARITION, MER_ET_COLLINES, AUCUN).regions(),
                PlanMars.tirer(43, APPARITION, MER_ET_COLLINES, AUCUN).regions());
    }

    /** Chunks préparés en carré autour d'un chunk : départs jusqu'à {@code departs}, références jusqu'à {@code references}. */
    private static PlanMars.ChunksPrepares prepares(BlockPos centre, int departs, int references) {
        int cx = centre.getX() >> 4;
        int cz = centre.getZ() >> 4;
        return new PlanMars.ChunksPrepares() {
            @Override
            public boolean departs(int x, int z) {
                return Math.max(Math.abs(x - cx), Math.abs(z - cz)) <= departs;
            }

            @Override
            public boolean references(int x, int z) {
                return Math.max(Math.abs(x - cx), Math.abs(z - cz)) <= references;
            }
        };
    }

    @Test
    void empriseDeLaMineHorsDesChunksPrepares() {
        // Recherche du point d'apparition à 80 blocs de lui : références jusqu'à 8 chunks, départs jusqu'à 16 autour
        PlanMars.ChunksPrepares prepares = prepares(APPARITION.offset(-80, 0, 0), 16, 8);
        for (long graine = 0; graine < 50; graine++) {
            BlockPos mine = PlanMars.tirer(graine, APPARITION, MER_ET_COLLINES, prepares).regions().get(Region.MINE);
            double distance = Math.hypot(mine.getX() - APPARITION.getX(), mine.getZ() - APPARITION.getZ());
            assertTrue(distance >= 199 && distance <= PlanMars.DISTANCE_MAX_MINE + 1, "graine " + graine + " : mine à " + distance);
            assertEquals(List.of(), PlanMars.defautsEmprise(mine, prepares), "graine " + graine);
        }
    }

    @Test
    void laMineSEloigneSiIlLeFaut() {
        // Références préparées jusqu'à 30 chunks (480 blocs) : aucune place entre 200 et 400, la mine s'éloigne
        PlanMars.ChunksPrepares prepares = prepares(APPARITION, 30, 30);
        BlockPos mine = PlanMars.tirer(3, APPARITION, MER_ET_COLLINES, prepares).regions().get(Region.MINE);
        double distance = Math.hypot(mine.getX() - APPARITION.getX(), mine.getZ() - APPARITION.getZ());
        assertTrue(distance > 400 && distance <= PlanMars.DISTANCE_MAX_MINE + 1, "mine à " + distance);
        assertEquals(List.of(), PlanMars.defautsEmprise(mine, prepares));
    }

    @Test
    void polesAuNord() {
        BlockPos poles = PlanMars.tirer(5, APPARITION, MER_ET_COLLINES, AUCUN).regions().get(Region.POLES);
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
        PlanMars plan = PlanMars.tirer(9, APPARITION, ocean, AUCUN);
        assertEquals(Region.values().length, plan.regions().size());
        assertTrue(plan.defauts(ocean, AUCUN).stream().anyMatch(defaut -> defaut.contains("dans l'eau")));
    }

    @Test
    void defautsDetectes() {
        List<String> defauts = PlanMars.defauts(Region.MINE, APPARITION.offset(10, 0, 0), Map.of(), APPARITION, MER_ET_COLLINES,
                prepares(APPARITION, 5, 13));
        assertTrue(defauts.stream().anyMatch(defaut -> defaut.contains("point d'apparition")), defauts.toString());
        assertTrue(defauts.stream().anyMatch(defaut -> defaut.contains("chunk de départ")), defauts.toString());
        assertTrue(defauts.stream().anyMatch(defaut -> defaut.contains("emprise")), defauts.toString());
        List<String> tropPres = PlanMars.defauts(Region.CITE, APPARITION.offset(800, 0, 100), Map.of(Region.GARNISON, APPARITION.offset(600, 0, 0)),
                APPARITION, MER_ET_COLLINES, AUCUN);
        assertTrue(tropPres.stream().anyMatch(defaut -> defaut.contains("trop près de garnison")), tropPres.toString());
    }
}
