package io.github.yusufbyn.redrising.monde;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            PlanMars plan = PlanMars.tirer(graine, APPARITION, MER_ET_COLLINES, AUCUN, List.of());
            assertEquals(Region.values().length, plan.regions().size());
            assertEquals(List.of(), plan.defauts(MER_ET_COLLINES, AUCUN, List.of()), "graine " + graine);
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
        assertEquals(PlanMars.tirer(42, APPARITION, MER_ET_COLLINES, AUCUN, List.of()), PlanMars.tirer(42, APPARITION, MER_ET_COLLINES, AUCUN, List.of()));
        assertNotEquals(PlanMars.tirer(42, APPARITION, MER_ET_COLLINES, AUCUN, List.of()).regions(),
                PlanMars.tirer(43, APPARITION, MER_ET_COLLINES, AUCUN, List.of()).regions());
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
            BlockPos mine = PlanMars.tirer(graine, APPARITION, MER_ET_COLLINES, prepares, List.of()).regions().get(Region.MINE);
            double distance = Math.hypot(mine.getX() - APPARITION.getX(), mine.getZ() - APPARITION.getZ());
            assertTrue(distance >= 199 && distance <= PlanMars.DISTANCE_MAX_MINE + 1, "graine " + graine + " : mine à " + distance);
            assertEquals(List.of(), PlanMars.defautsEmprise(mine, prepares), "graine " + graine);
        }
    }

    @Test
    void laMineSEloigneSiIlLeFaut() {
        // Références préparées jusqu'à 14 chunks : aucune place entre 200 et 400, la mine s'éloigne
        PlanMars.ChunksPrepares prepares = prepares(APPARITION, 14, 14);
        BlockPos mine = PlanMars.tirer(3, APPARITION, MER_ET_COLLINES, prepares, List.of()).regions().get(Region.MINE);
        double distance = Math.hypot(mine.getX() - APPARITION.getX(), mine.getZ() - APPARITION.getZ());
        assertTrue(distance > 400 && distance <= PlanMars.DISTANCE_MAX_MINE + 1, "mine à " + distance);
        assertEquals(List.of(), PlanMars.defautsEmprise(mine, prepares));
    }

    /** Des pentes partout : aucune colonne de puits n'est plate. */
    private static final PlanMars.Terrain PENTES = new PlanMars.Terrain() {
        @Override
        public boolean eau(int x, int z) {
            return false;
        }

        @Override
        public int hauteur(int x, int z) {
            return 80 + 3 * Math.floorMod(x, 20);
        }
    };

    @Test
    void sansPlaceAPlatLaMineResteADistanceEtSAplanit() {
        for (long graine = 0; graine < 50; graine++) {
            PlanMars plan = PlanMars.tirer(graine, APPARITION, PENTES, AUCUN, List.of());
            BlockPos mine = plan.regions().get(Region.MINE);
            double distance = Math.hypot(mine.getX() - APPARITION.getX(), mine.getZ() - APPARITION.getZ());
            assertTrue(distance <= PlanMars.DISTANCE_MAX_MINE + 1, "graine " + graine + " : mine à " + distance);
            assertEquals(List.of(), PlanMars.defauts(Region.MINE, mine, Map.of(), APPARITION, PENTES, AUCUN, List.of()), "graine " + graine);
            AplanissementPuits aplani = AplanissementPuits.de(mine, PENTES::hauteur);
            assertNotNull(aplani, "graine " + graine);
            assertFalse(AplanissementPuits.surUnePente(mine, AplanissementPuits.relief(aplani, PENTES::hauteur)), "graine " + graine);
        }
    }

    @Test
    void uneMineNeSEloigneJamaisAuDelaDeLaLimite() {
        // Chunks préparés trop loin pour tenir : la mine reste à la limite, le défaut se voit
        PlanMars.ChunksPrepares prepares = prepares(APPARITION, 60, 60);
        PlanMars plan = PlanMars.tirer(4, APPARITION, PENTES, prepares, List.of());
        BlockPos mine = plan.regions().get(Region.MINE);
        assertTrue(Math.hypot(mine.getX() - APPARITION.getX(), mine.getZ() - APPARITION.getZ()) <= PlanMars.DISTANCE_MAX_MINE + 1, "mine en " + mine);
        assertFalse(PlanMars.defautsEmprise(mine, prepares).isEmpty());
    }

    @Test
    void uneMineSurPlaceAPlatNestPasAplanie() {
        assertNull(AplanissementPuits.de(new BlockPos(300, 66, 0), MER_ET_COLLINES::hauteur));
    }

    @Test
    void laZoneAplanieEstPlateAuCentreEtRejointLeRelief() {
        BlockPos centre = new BlockPos(100, 80, 100);
        AplanissementPuits aplani = AplanissementPuits.de(centre, PENTES::hauteur);
        assertNotNull(aplani);
        java.util.function.IntBinaryOperator relief = AplanissementPuits.relief(aplani, PENTES::hauteur);
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z++) {
                double distance = Math.hypot(x, z);
                int hauteur = relief.applyAsInt(centre.getX() + x, centre.getZ() + z);
                if (distance <= AplanissementPuits.RAYON_PLAT) {
                    assertEquals(aplani.sol(), hauteur, x + ", " + z);
                }
                if (distance >= AplanissementPuits.RAYON_RACCORD) {
                    assertEquals(PENTES.hauteur(centre.getX() + x, centre.getZ() + z), hauteur, x + ", " + z);
                }
            }
        }
        // Le raccord, pour 40 blocs d'écart entre la partie plate et le relief : pas de marche de plus de 2 blocs
        AplanissementPuits ecart = new AplanissementPuits(centre, 120);
        for (int x = 0; x < AplanissementPuits.RAYON_RACCORD; x++) {
            assertTrue(Math.abs(ecart.hauteur(centre.getX() + x + 1, centre.getZ(), 80) - ecart.hauteur(centre.getX() + x, centre.getZ(), 80)) <= 2, "à " + x);
        }
    }

    @Test
    void laMineSEcarteDesForts() {
        // Un fort juste là où la mine tomberait : elle va ailleurs, assez loin pour que leurs emprises ne se croisent pas
        BlockPos sansFort = PlanMars.tirer(11, APPARITION, MER_ET_COLLINES, AUCUN, List.of()).regions().get(Region.MINE);
        List<ChunkPos> forts = List.of(new ChunkPos((sansFort.getX() >> 4) + 3, sansFort.getZ() >> 4));
        PlanMars plan = PlanMars.tirer(11, APPARITION, MER_ET_COLLINES, AUCUN, forts);
        BlockPos mine = plan.regions().get(Region.MINE);
        int ecart = Math.max(Math.abs((mine.getX() >> 4) - forts.getFirst().x()), Math.abs((mine.getZ() >> 4) - forts.getFirst().z()));
        assertTrue(ecart >= PlanMars.ECART_FORT, "mine à " + ecart + " chunks du fort");
        assertEquals(List.of(), plan.defauts(MER_ET_COLLINES, AUCUN, forts));
        assertTrue(PlanMars.defauts(Region.MINE, sansFort, Map.of(), APPARITION, MER_ET_COLLINES, AUCUN, forts).stream()
                .anyMatch(defaut -> defaut.contains("fort")));
    }

    @Test
    void polesAuNord() {
        BlockPos poles = PlanMars.tirer(5, APPARITION, MER_ET_COLLINES, AUCUN, List.of()).regions().get(Region.POLES);
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
        PlanMars plan = PlanMars.tirer(9, APPARITION, ocean, AUCUN, List.of());
        assertEquals(Region.values().length, plan.regions().size());
        assertTrue(plan.defauts(ocean, AUCUN, List.of()).stream().anyMatch(defaut -> defaut.contains("dans l'eau")));
    }

    @Test
    void defautsDetectes() {
        List<String> defauts = PlanMars.defauts(Region.MINE, APPARITION.offset(10, 0, 0), Map.of(), APPARITION, MER_ET_COLLINES,
                prepares(APPARITION, 5, 13), List.of());
        assertTrue(defauts.stream().anyMatch(defaut -> defaut.contains("point d'apparition")), defauts.toString());
        assertTrue(defauts.stream().anyMatch(defaut -> defaut.contains("chunk de départ")), defauts.toString());
        assertTrue(defauts.stream().anyMatch(defaut -> defaut.contains("emprise")), defauts.toString());
        List<String> tropPres = PlanMars.defauts(Region.CITE, APPARITION.offset(800, 0, 100), Map.of(Region.GARNISON, APPARITION.offset(600, 0, 0)),
                APPARITION, MER_ET_COLLINES, AUCUN, List.of());
        assertTrue(tropPres.stream().anyMatch(defaut -> defaut.contains("trop près de garnison")), tropPres.toString());
    }

    @Test
    void neufDepartsEnTuilesCouvrentLEmprise() {
        int r = PlanMars.EMPRISE_MINE;
        int departs = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                departs += PlanMars.departDeMine(dx, dz) ? 1 : 0;
                // Chaque chunk est à la portée d'un départ (8 chunks), qui est bien un départ
                assertTrue(Math.abs(dx - PlanMars.tuile(dx)) <= 8 && Math.abs(dz - PlanMars.tuile(dz)) <= 8, dx + ", " + dz);
                assertTrue(PlanMars.departDeMine(PlanMars.tuile(dx), PlanMars.tuile(dz)), dx + ", " + dz);
            }
        }
        assertEquals(9, departs);
    }

    @Test
    void unDepartDeTuileDejaPrepareEstUnDefaut() {
        BlockPos mine = new BlockPos(0, 64, 0);
        // Seul le chunk de départ d'un coin de l'emprise a passé l'étape des départs
        PlanMars.ChunksPrepares coin = new PlanMars.ChunksPrepares() {
            @Override
            public boolean departs(int cx, int cz) {
                return cx == PlanMars.EMPRISE_MINE && cz == -PlanMars.EMPRISE_MINE;
            }

            @Override
            public boolean references(int cx, int cz) {
                return false;
            }
        };
        assertEquals(List.of("chunk de départ déjà préparé"), PlanMars.defautsEmprise(mine, coin));
    }
}
