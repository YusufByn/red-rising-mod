package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.monde.AplanissementPuits;
import io.github.yusufbyn.redrising.monde.PaletteMine;
import io.github.yusufbyn.redrising.monde.PlanMars;
import io.github.yusufbyn.redrising.monde.Region;
import io.github.yusufbyn.redrising.monde.RegionsMars;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

/**
 * Les épreuves longues de la mine (G7.2), une vingtaine de minutes : seulement avec {@code ./gradlew runClientGameTest
 * -PtestsLongs}, quand le code de la mine ou de la génération change (règle dans {@code CLAUDE.md}). Sans l'option, le test
 * ne fait rien.
 *
 * <ul>
 *     <li>Première graine : toute l'emprise générée (références, parcours depuis le bas du puits, fuites, ciel, autres
 *     structures, générateurs, coût de la pose et de la règle des décorations) ; le réseau éclairé et 2 minutes de monstres ;
 *     les 32 wagonnets, les rails et les butoirs, le coût d'un wagonnet ; tous les repères et les points d'entrée des Gris.</li>
 *     <li>Trois graines : l'emprise entière, la roche rouge sur toute l'emprise, le comptage des salles, des minerais et des coffres.</li>
 *     <li>Le site : {@value #GRAINES_DISTANCE} graines sans créer de monde, la mine à {@value PlanMars#DISTANCE_MAX_MINE} blocs au plus
 *XX
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
                    MineLykosTest.rocheRouge(monde, graine, PlanMars.EMPRISE_MINE);
                    MineLykosTest.reseauEtMonstres(monde, context);
                    MineLykosTest.reperesDeLEmprise(monde);
                    MineLykosTest.captures(monde, context);
                    MineLykosTest.wagonnetsDeLEmprise(monde, context);
                } else {
                    MineLykosTest.charger(monde, PlanMars.EMPRISE_MINE);
                    MineLykosTest.rocheRouge(monde, graine, PlanMars.EMPRISE_MINE);
                }
                MineLykosTest.comptage(monde, graine);
            }
        }
        poidsSurDisque(context);
        distanceSurDesGraines(context);
        zoneAplanie(context);
        RedRising.LOGGER.info("[G7.2] OK : épreuves longues de la mine (emprise, roche rouge, monstres, wagonnets, repères, comptage, site)");
    }

    /** 🟡 Graines du balayage du site (environ 5 secondes chacune). */
    private static final int GRAINES_DISTANCE = 20;

    /** Les chunks que Minecraft prépare au plus au tirage, autour du point d'apparition (mesuré : départs jusqu'à 11 chunks, références jusqu'à 3). */
    private static PlanMars.ChunksPrepares preparesAutourDe(BlockPos apparition) {
        int cx = apparition.getX() >> 4;
        int cz = apparition.getZ() >> 4;
        return new PlanMars.ChunksPrepares() {
            @Override
            public boolean departs(int x, int z) {
                return Math.max(Math.abs(x - cx), Math.abs(z - cz)) <= 11;
            }

            @Override
            public boolean references(int x, int z) {
                return Math.max(Math.abs(x - cx), Math.abs(z - cz)) <= 3;
            }
        };
    }

    /**
     * Le plafond de distance (G7.2.5) sur le vrai terrain de graines différentes, sans créer de monde : la mine reste à
     * {@value PlanMars#DISTANCE_MAX_MINE} blocs au plus du point d'apparition, et sans défaut.
     */
    private static void distanceSurDesGraines(ClientGameTestContext context) {
        try (TestSingleplayerContext monde = MineLykosTest.mondeMars(context, MineLykosTest.GRAINES[0])) {
            String defauts = monde.getServer().computeOnServer(server -> {
                ServerLevel niveau = server.overworld();
                List<String> liste = new ArrayList<>();
                long plusLoin = 0;
                for (long i = 1; i <= GRAINES_DISTANCE; i++) {
                    long graine = i * 7919;
                    RandomState aleatoire = RandomState.create(server.registryAccess(), NoiseGeneratorSettings.OVERWORLD, graine);
                    PlanMars.Terrain terrain = RegionsMars.terrain(niveau.getChunkSource().getGenerator(), aleatoire, niveau);
                    BlockPos estimation = aleatoire.sampler().findSpawnPosition();
                    BlockPos apparition = estimation.atY(terrain.hauteur(estimation.getX(), estimation.getZ()));
                    PlanMars.ChunksPrepares prepares = preparesAutourDe(apparition);
                    BlockPos mine = PlanMars.tirer(graine, apparition, terrain, prepares, List.of()).regions().get(Region.MINE);
                    long distance = Math.round(Math.hypot(mine.getX() - apparition.getX(), mine.getZ() - apparition.getZ()));
                    plusLoin = Math.max(plusLoin, distance);
                    if (distance > PlanMars.DISTANCE_MAX_MINE + 1) {
                        liste.add("graine " + graine + " : mine à " + distance + " blocs");
                    }
                    List<String> site = PlanMars.defauts(Region.MINE, mine, Map.of(), apparition, terrain, prepares, List.of());
                    if (!site.isEmpty()) {
                        liste.add("graine " + graine + " : " + site);
                    }
                }
                RedRising.LOGGER.info("[G7.2.5] Site sur {} graines : la mine à {} blocs au plus du point d'apparition (plafond {})", GRAINES_DISTANCE, plusLoin, PlanMars.DISTANCE_MAX_MINE);
                return String.join(" ; ", liste);
            });
            if (!defauts.isEmpty()) {
                throw new AssertionError(defauts);
            }
        }
    }

    /**
     * Une zone du puits aplanie (G7.2.5). Sur 300 graines, aucune n'en a eu besoin : on déplace la mine d'un monde neuf sur une
     * pente, avant que ses chunks soient générés, et on rouvre le monde pour que la génération lise le plan modifié. Partie plate
     * à la hauteur prévue, puits et bouchon en place, terrain intact au-delà du raccord, surface d'origine, roche rouge.
     */
    private static void zoneAplanie(ClientGameTestContext context) {
        long graine = MineLykosTest.GRAINES[1];
        TestWorldSave sauvegarde;
        try (TestSingleplayerContext monde = MineLykosTest.mondeMars(context, graine)) {
            monde.getServer().runOnServer(server -> {
                ServerLevel niveau = server.overworld();
                PlanMars plan = Objects.requireNonNull(RegionsMars.plan(server));
                PlanMars.Terrain terrain = RegionsMars.terrain(niveau.getChunkSource().getGenerator(), niveau.getChunkSource().randomState(), niveau);
                PlanMars.ChunksPrepares prepares = preparesAutourDe(plan.apparition());
                BlockPos pente = null;
                int travaux = 0;
                for (int angle = 0; angle < 360; angle += 5) {
                    for (int rayon = 400; rayon <= PlanMars.DISTANCE_MAX_MINE; rayon += 16) {
                        int x = plan.apparition().getX() + (int) Math.round(Math.cos(Math.toRadians(angle)) * rayon);
                        int z = plan.apparition().getZ() + (int) Math.round(Math.sin(Math.toRadians(angle)) * rayon);
                        BlockPos candidat = new BlockPos(x, terrain.hauteur(x, z), z);
                        AplanissementPuits aplani = AplanissementPuits.de(candidat, terrain::hauteur);
                        if (aplani != null && aplani.travaux(terrain::hauteur) > travaux
                                && PlanMars.defauts(Region.MINE, candidat, Map.of(), plan.apparition(), terrain, prepares, RegionsMars.forts(niveau)).isEmpty()) {
                            pente = candidat;
                            travaux = aplani.travaux(terrain::hauteur);
                        }
                    }
                }
                if (pente == null) {
                    throw new AssertionError("Graine " + graine + " : aucune pente à portée");
                }
                Map<Region, BlockPos> regions = new EnumMap<>(plan.regions());
                regions.put(Region.MINE, pente);
                PlanMars deplace = new PlanMars(plan.apparition(), regions);
                EtatMondeServeur.modifier(server, etat -> etat.avecPlan(deplace));
                RedRising.LOGGER.info("[G7.2.5] Mine déplacée sur une pente en {} : jusqu'à {} blocs déplacés", pente.toShortString(), travaux);
            });
            sauvegarde = monde.getWorldSave();
        }
        try (TestSingleplayerContext monde = sauvegarde.open()) {
            monde.getServer().waitFor(server -> !server.getPlayerList().getPlayers().isEmpty(), 100);
            MineLykosTest.charger(monde, 4);
            MineLykosTest.colonneDuPuits(monde, graine);
            MineLykosTest.rocheRouge(monde, graine, 4);
            String defauts = monde.getServer().computeOnServer(server -> {
                ServerLevel niveau = server.overworld();
                BlockPos centre = Objects.requireNonNull(RegionsMars.courant()).regions().get(Region.MINE);
                IntBinaryOperator naturel = (x, z) -> niveau.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, niveau, niveau.getChunkSource().randomState());
                AplanissementPuits aplani = AplanissementPuits.de(centre, naturel);
                if (aplani == null) {
                    return "la pente n'est pas vue comme une pente";
                }
                int plat = 0;
                int ailleurs = 0;
                int nus = 0;
                int loin = 0;
                int decales = 0;
                int marche = 0;
                String ouMarche = "";
                for (int x = -48; x <= 48; x++) {
                    for (int z = -48; z <= 48; z++) {
                        int cx = centre.getX() + x;
                        int cz = centre.getZ() + z;
                        int sol = MineLykosTest.sol(niveau, cx, cz);
                        double distance = Math.hypot(x, z);
                        if (distance <= AplanissementPuits.RAYON_PLAT) {
                            plat += sol == aplani.sol() ? 1 : 0;
                            ailleurs += sol == aplani.sol() ? 0 : 1;
                            nus += niveau.getBlockState(new BlockPos(cx, sol - 1, cz)).is(PaletteMine.roche(sol - 1).getBlock()) ? 1 : 0;
                        } else if (distance >= AplanissementPuits.RAYON_RACCORD + 2) {
                            loin++;
                            decales += Math.abs(sol - naturel.applyAsInt(cx, cz)) > 1 ? 1 : 0;
                        } else if (x < 48) {
                            int voisin = MineLykosTest.sol(niveau, cx + 1, cz);
                            if (Math.abs(sol - voisin) > marche) {
                                marche = Math.abs(sol - voisin);
                                ouMarche = cx + " " + cz + " : " + sol + " " + niveau.getBlockState(new BlockPos(cx, sol - 1, cz)).getBlock().getDescriptionId() + " / " + voisin + " "
                                        + niveau.getBlockState(new BlockPos(cx + 1, voisin - 1, cz)).getBlock().getDescriptionId() + " ; naturels " + naturel.applyAsInt(cx, cz) + " et " + naturel.applyAsInt(cx + 1, cz);
                            }
                        }
                    }
                }
                RedRising.LOGGER.info("[G7.2.5] Zone aplanie à {} : {} colonnes à la hauteur prévue, {} autres, {} en roche nue ; au-delà du raccord, {} décalées sur {} ; plus grande marche du raccord {} bloc(s), en {}",
                        aplani.sol(), plat, ailleurs, nus, decales, loin, marche, ouMarche);
                List<String> liste = new ArrayList<>();
                // Un rocher ou une souche peut dépasser ; le terrain lui-même, non
                if (ailleurs > (plat + ailleurs) / 10) {
                    liste.add(ailleurs + " colonnes de la partie plate pas à la hauteur prévue (" + plat + " bonnes)");
                }
                if (decales > loin / 10) {
                    liste.add(decales + " colonnes décalées sur " + loin + " au-delà du raccord");
                }
                return String.join(" ; ", liste);
            });
            if (!defauts.isEmpty()) {
                throw new AssertionError(defauts);
            }
            // Pour juger à l'œil : la zone vue d'en haut
            BlockPos centre = monde.getServer().computeOnServer(server -> Objects.requireNonNull(RegionsMars.courant()).regions().get(Region.MINE));
            monde.getServer().runCommand("gamemode spectator @a[limit=1]");
            monde.getServer().runCommand("time set noon");
            context.getInput().pressKey(options -> options.keyToggleGui);
            monde.getServer().runCommand("tp @a[limit=1] " + (centre.getX() - 45.5) + " " + (centre.getY() + 22) + " " + (centre.getZ() + 0.5) + " -90 20");
            context.waitTicks(240);
            OutilsTest.garderCapture(context.takeScreenshot("mine-aplanie"));
            context.getInput().pressKey(options -> options.keyToggleGui);
        }
    }

    /**
     * Le poids de la mine sur disque (G7.2.5) : deux mondes de la même graine, mesurés une fois fermés (tout est alors écrit),
     * l'un avec l'emprise générée, l'autre avec le même nombre de chunks loin de la mine.
     */
    private static void poidsSurDisque(ClientGameTestContext context) {
        long graine = MineLykosTest.GRAINES[1];
        int rayon = PlanMars.EMPRISE_MINE;
        long[] poids = new long[2];
        for (int essai = 0; essai < 2; essai++) {
            java.nio.file.Path dossier;
            try (TestSingleplayerContext monde = MineLykosTest.mondeMars(context, graine)) {
                MineLykosTest.charger(monde, rayon, essai == 0 ? 0 : 60, false);
                dossier = monde.getServer().computeOnServer(server -> server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
            }
            poids[essai] = MineLykosTest.poidsDuMonde(dossier);
        }
        double mine = poids[0] / 1e6;
        double loin = poids[1] / 1e6;
        RedRising.LOGGER.info("[G7.2.5] Sur disque ({} chunks) : {} Mo avec la mine, {} Mo loin d'elle, soit {} Mo de plus (plafond {} Mo pour le monde avec la mine)", (2 * rayon + 1) * (2 * rayon + 1),
                String.format(java.util.Locale.ROOT, "%.1f", mine), String.format(java.util.Locale.ROOT, "%.1f", loin), String.format(java.util.Locale.ROOT, "%.1f", mine - loin), MineLykosTest.DISQUE_MAX_MO);
        if (mine > MineLykosTest.DISQUE_MAX_MO) {
            throw new AssertionError("Le monde avec la mine pèse " + mine + " Mo sur disque");
        }
    }
}
