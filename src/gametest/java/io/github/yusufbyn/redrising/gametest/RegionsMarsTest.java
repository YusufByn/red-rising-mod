package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.monde.PlanMars;
import io.github.yusufbyn.redrising.monde.Region;
import io.github.yusufbyn.redrising.monde.RegionsMars;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.List;
import java.util.Objects;

/**
 * G7.1.1 : un monde neuf avec du relief a un plan des régions cohérent (pas d'eau, distances, écarts, cité plate, mine
 * hors des chunks de la recherche du point d'apparition), tiré une fois et gardé au rechargement ; deux autres graines
 * donnent aussi un plan cohérent sur le vrai terrain ; {@code /rr region list|tp}. Un monde plat (comme ceux des autres
 * tests) n'a pas de plan, et rien n'en dépend.
 */
public class RegionsMarsTest implements FabricClientGameTest {
    private static final long GRAINE = 20_261_028L;
    private static final long[] AUTRES_GRAINES = {7L, -4_242_424_242L};

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        PlanMars tire;
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context, context.worldBuilder().adjustSettings(reglages -> {
            reglages.setWorldType(reglages.getNormalPresetList().stream()
                    .filter(type -> type.preset().is(WorldPresets.NORMAL)).findFirst().orElseThrow());
            reglages.setSeed(Long.toString(GRAINE));
        }))) {
            tire = monde.getServer().computeOnServer(RegionsMars::plan);
            if (tire == null) {
                throw new AssertionError("Monde neuf avec du relief : pas de plan");
            }
            verifier(monde, tire, GRAINE);
            if (!tire.equals(monde.getServer().computeOnServer(server -> RegionsMars.courant()))) {
                throw new AssertionError("La copie pour la génération n'est pas le plan sauvegardé");
            }
            BlockPos mine = tire.regions().get(Region.MINE);
            double distance = Math.hypot(mine.getX() - tire.apparition().getX(), mine.getZ() - tire.apparition().getZ());
            RedRising.LOGGER.info("[G7.1.1] Graine {} : plan cohérent, mine à {} blocs du point d'apparition", GRAINE, Math.round(distance));

            // Deux autres graines, sur le vrai terrain, sans créer de monde
            for (long graine : AUTRES_GRAINES) {
                PlanMars autre = monde.getServer().computeOnServer(server -> {
                    RandomState aleatoire = RandomState.create(server.registryAccess(), NoiseGeneratorSettings.OVERWORLD, graine);
                    BlockPos estimation = aleatoire.sampler().findSpawnPosition();
                    ServerLevel overworld = server.overworld();
                    BlockPos apparition = estimation.atY(overworld.getChunkSource().getGenerator().getBaseHeight(estimation.getX(), estimation.getZ(),
                            Heightmap.Types.OCEAN_FLOOR_WG, overworld, aleatoire));
                    return PlanMars.tirer(graine, apparition, terrain(server, aleatoire), PlanMars.ChunksPrepares.AUCUN);
                });
                List<String> defauts = monde.getServer().computeOnServer(server -> autre.defauts(
                        terrain(server, RandomState.create(server.registryAccess(), NoiseGeneratorSettings.OVERWORLD, graine)), PlanMars.ChunksPrepares.AUCUN));
                if (!defauts.isEmpty()) {
                    throw new AssertionError("Graine " + graine + " : " + defauts);
                }
                RedRising.LOGGER.info("[G7.1.1] Graine {} : plan cohérent {}", graine, autre.regions());
            }

            monde.getServer().runCommand("rr region list");
            monde.getServer().runCommand("rr region tp mine @a[limit=1]");
            monde.getServer().waitFor(server -> {
                ServerPlayer joueur = OutilsTest.joueur(server);
                return Math.abs(joueur.getX() - (mine.getX() + 0.5)) < 1 && Math.abs(joueur.getZ() - (mine.getZ() + 0.5)) < 1;
            }, 40);
            RedRising.LOGGER.info("[G7.1.1] /rr region list et tp");
            sauvegarde = monde.getWorldSave();
        }

        try (TestSingleplayerContext monde = sauvegarde.open()) {
            monde.getServer().waitFor(server -> !server.getPlayerList().getPlayers().isEmpty(), 100);
            PlanMars relu = monde.getServer().computeOnServer(RegionsMars::plan);
            PlanMars courant = monde.getServer().computeOnServer(server -> RegionsMars.courant());
            if (!tire.equals(relu) || !tire.equals(courant)) {
                throw new AssertionError("Rechargement : plan " + relu + ", copie " + courant + ", attendu " + tire);
            }
        }
        RedRising.LOGGER.info("[G7.1.1] Rechargement : le même plan, ni recalculé ni perdu");

        // Monde plat, comme ceux des autres tests : pas de plan
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            boolean sansPlan = monde.getServer().computeOnServer(server -> RegionsMars.plan(server) == null && RegionsMars.courant() == null
                    && EtatMondeServeur.get(server).plan().isEmpty());
            if (!sansPlan) {
                throw new AssertionError("Monde plat : il a un plan");
            }
            monde.getServer().runCommand("rr region list");
        }
        RedRising.LOGGER.info("[G7.1.1] OK : un monde neuf place les régions de façon cohérente ; un monde plat n'a pas de plan");
    }

    /** Le plan, vérifié au moment du tirage sur l'état réel des chunks (emprise de la mine comprise). */
    private static void verifier(TestSingleplayerContext monde, PlanMars plan, long graine) {
        RegionsMars.Tirage tirage = Objects.requireNonNull(monde.getServer().computeOnServer(server -> RegionsMars.dernierTirage()));
        if (!tirage.defauts().isEmpty()) {
            throw new AssertionError("Graine " + graine + " : " + tirage.defauts());
        }
        RedRising.LOGGER.info("[G7.1.1] Graine {} : chunks déjà préparés au tirage jusqu'à {} chunks (références) et {} (départs) ; mine à {} blocs",
                graine, tirage.rayonReferences(), tirage.rayonDeparts(), tirage.distanceMine());
    }

    private static PlanMars.Terrain terrain(MinecraftServer server, RandomState aleatoire) {
        ServerLevel overworld = server.overworld();
        ChunkGenerator generateur = Objects.requireNonNull(overworld.getChunkSource().getGenerator());
        return RegionsMars.terrain(generateur, aleatoire, overworld);
    }
}
