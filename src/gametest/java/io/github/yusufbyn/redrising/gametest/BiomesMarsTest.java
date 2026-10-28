package io.github.yusufbyn.redrising.gametest;

import com.google.gson.JsonObject;
import com.mojang.serialization.Lifecycle;
import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.monde.Latitude;
import io.github.yusufbyn.redrising.monde.PlanMars;
import io.github.yusufbyn.redrising.monde.Region;
import io.github.yusufbyn.redrising.monde.RegionsMars;
import io.github.yusufbyn.redrising.monde.SourceBiomesMars;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * G7.1.2 : un monde neuf a le type Mars (le type par défaut) : rien ne change loin des pôles ; une bande de transition où
 * le froid gagne peu à peu (pas de mur), puis des pôles gelés au nord et au sud ; il n'est pas « expérimental », ni à la
 * création ni au rechargement (pas d'écran d'avertissement) ; chaque région du plan est sur un biome qui lui convient ;
 * un serveur dédié avec le {@code level-type} par défaut crée aussi Mars.
 * Les biomes sont lus par la source du monde, sans générer de chunks.
 */
public class BiomesMarsTest implements FabricClientGameTest {
    private static final long GRAINE = 7_000_123L;
    private static final int DEBUT = Latitude.debutTransition();

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context, context.worldBuilder().adjustSettings(reglages -> {
            // Le type par défaut (« Normal ») : celui de Mars
            reglages.setWorldType(reglages.getNormalPresetList().stream()
                    .filter(type -> type.preset().is(WorldPresets.NORMAL)).findFirst().orElseThrow());
            reglages.setSeed(Long.toString(GRAINE));
        }))) {
            verifierStable(monde, "à la création");
            String stabilite = monde.getServer().computeOnServer(BiomesMarsTest::reglesDeStabilite);
            if (!stabilite.isEmpty()) {
                throw new AssertionError(stabilite);
            }
            RedRising.LOGGER.info("[G7.1.2] Source de Mars stable sans mixin ; la règle de Minecraft n'est pas changée");
            String dedie = monde.getServer().computeOnServer(BiomesMarsTest::serveurDedie);
            if (!dedie.isEmpty()) {
                throw new AssertionError(dedie);
            }
            RedRising.LOGGER.info("[G7.1.2] Serveur dédié : le level-type par défaut (et ses anciens noms) donne Mars");
            String defauts = monde.getServer().computeOnServer(BiomesMarsTest::verifierBiomes);
            if (!defauts.isEmpty()) {
                throw new AssertionError(defauts);
            }
            RedRising.LOGGER.info("[G7.1.2] Rien de changé loin des pôles ; transition progressive ; pôles nord et sud gelés");
            String regions = monde.getServer().computeOnServer(BiomesMarsTest::verifierRegions);
            if (!regions.isEmpty()) {
                throw new AssertionError(regions);
            }
            RedRising.LOGGER.info("[G7.1.2] Chaque région du plan est sur un biome qui lui convient");
            sauvegarde = monde.getWorldSave();
        }

        try (TestSingleplayerContext monde = sauvegarde.open()) {
            monde.getServer().waitFor(server -> !server.getPlayerList().getPlayers().isEmpty(), 100);
            verifierStable(monde, "au rechargement");
            context.waitFor(mc -> mc.gui.screen() == null, 100);
        }
        RedRising.LOGGER.info("[G7.1.2] OK : transition et pôles gelés, monde stable (pas d'avertissement), régions sur leur biome");
    }

    private static void verifierStable(TestSingleplayerContext monde, String moment) {
        Lifecycle cycle = monde.getServer().computeOnServer(server -> server.getWorldData().worldGenSettingsLifecycle());
        boolean mars = monde.getServer().computeOnServer(server -> server.overworld().getChunkSource().getGenerator().getBiomeSource() instanceof SourceBiomesMars);
        if (!cycle.equals(Lifecycle.stable()) || !mars) {
            throw new AssertionError("Monde " + moment + " : " + cycle + ", source de Mars " + mars);
        }
    }

    /**
     * La vérification de Minecraft, appelée directement : notre source autour de celle de Minecraft est stable sans aucun
     * mixin ; la règle de Minecraft n'est pas changée (sa source avec les réglages du Nether, dans l'Overworld, reste
     * expérimentale ; avec ceux de l'Overworld, stable).
     */
    private static String reglesDeStabilite(MinecraftServer server) {
        try {
            Method stable = WorldDimensions.class.getDeclaredMethod("isStableOverworld", LevelStem.class);
            stable.setAccessible(true);
            Holder<DimensionType> type = server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
            Holder<NoiseGeneratorSettings> reglages = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            BiomeSource minecraft = MultiNoiseBiomeSource.createFromPreset(server.registryAccess()
                    .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
            BiomeSource nether = MultiNoiseBiomeSource.createFromPreset(server.registryAccess()
                    .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER));
            Map<String, BiomeSource> sources = Map.of(
                    "Mars autour de Minecraft", new SourceBiomesMars(minecraft, source(server).polaires()),
                    "Minecraft, réglages du Nether", nether,
                    "Minecraft seule", minecraft);
            Map<String, Boolean> attendu = Map.of("Mars autour de Minecraft", true, "Minecraft, réglages du Nether", false, "Minecraft seule", true);
            List<String> defauts = new ArrayList<>();
            for (Map.Entry<String, BiomeSource> cas : sources.entrySet()) {
                boolean obtenu = (boolean) stable.invoke(null, new LevelStem(type, new NoiseBasedChunkGenerator(cas.getValue(), reglages)));
                if (obtenu != attendu.get(cas.getKey())) {
                    defauts.add(cas.getKey() + " : stable " + obtenu);
                }
            }
            return String.join(" ; ", defauts);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Ce que fait un serveur dédié avec {@code level-type} : par défaut {@code minecraft:normal}
     * ({@code DedicatedServerProperties}), le type que le mod remplace. On appelle le même code que lui
     * ({@code DedicatedServerProperties.WorldDimensionData.create}), avec la valeur par défaut et les anciens noms.
     */
    private static String serveurDedie(MinecraftServer server) {
        try {
            Class<?> donnees = Class.forName("net.minecraft.server.dedicated.DedicatedServerProperties$WorldDimensionData");
            Constructor<?> constructeur = donnees.getDeclaredConstructor(JsonObject.class, String.class);
            constructeur.setAccessible(true);
            Method creer = donnees.getMethod("create", HolderLookup.Provider.class);
            creer.setAccessible(true);
            List<String> defauts = new ArrayList<>();
            for (String type : List.of(WorldPresets.NORMAL.identifier().toString(), "normal", "default")) {
                WorldDimensions dimensions = (WorldDimensions) creer.invoke(constructeur.newInstance(new JsonObject(), type), server.registryAccess());
                if (!(dimensions.overworld().getBiomeSource() instanceof SourceBiomesMars)) {
                    defauts.add("level-type=" + type + " : " + dimensions.overworld().getBiomeSource());
                }
            }
            return String.join(" ; ", defauts);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static SourceBiomesMars source(MinecraftServer server) {
        return (SourceBiomesMars) server.overworld().getChunkSource().getGenerator().getBiomeSource();
    }

    private static Climate.Sampler echantillonneur(MinecraftServer server) {
        return server.overworld().getChunkSource().randomState().sampler();
    }

    private static Holder<Biome> biome(MinecraftServer server, int x, int y, int z) {
        return source(server).getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), echantillonneur(server));
    }

    private static boolean remplace(MinecraftServer server, int x, int y, int z) {
        Holder<Biome> base = source(server).base().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), echantillonneur(server));
        return !base.equals(biome(server, x, y, z));
    }

    /** Part des points remplacés par un biome froid ou gelé, sur une ligne de 4 km à cette latitude. */
    private static double partRemplacee(MinecraftServer server, int centreX, int z) {
        int remplaces = 0;
        int total = 0;
        for (int x = centreX - 2048; x <= centreX + 2048; x += 32) {
            total++;
            if (remplace(server, x, 64, z)) {
                remplaces++;
            }
        }
        return (double) remplaces / total;
    }

    private static String verifierBiomes(MinecraftServer server) {
        List<String> defauts = new ArrayList<>();
        int x0 = server.overworld().getLevelData().getRespawnData().pos().getX();
        for (int sens : new int[]{1, -1}) {
            String pole = sens > 0 ? "sud" : "nord";
            // Loin des pôles : la carte de Minecraft, telle quelle
            for (int z : new int[]{0, 1000, DEBUT - 16}) {
                if (partRemplacee(server, x0, sens * z) > 0) {
                    defauts.add(pole + " : biomes changés à |z| = " + z);
                }
            }
            // La bande : le froid gagne peu à peu
            double debut = partRemplacee(server, x0, sens * (DEBUT + 40));
            double milieu = partRemplacee(server, x0, sens * (DEBUT + PlanMars.TRANSITION_POLES / 2));
            double fin = partRemplacee(server, x0, sens * (PlanMars.LATITUDE_POLES - 40));
            RedRising.LOGGER.info("[G7.1.2] Pôle {} : froid à {} % au début de la bande, {} % au milieu, {} % à la fin", pole,
                    Math.round(debut * 100), Math.round(milieu * 100), Math.round(fin * 100));
            if (!(debut < 0.35 && milieu > 0.1 && milieu < 0.9 && fin > 0.65 && debut < milieu && milieu < fin)) {
                defauts.add(pole + " : transition " + debut + " / " + milieu + " / " + fin);
            }
            // Au-delà : seulement des biomes gelés
            SourceBiomesMars.Polaires polaires = source(server).polaires();
            Set<Holder<Biome>> geles = Set.of(polaires.plaine(), polaires.picsDeGlace(), polaires.pics(), polaires.ocean(), polaires.oceanProfond(),
                    polaires.riviere(), polaires.plage());
            for (int z : new int[]{PlanMars.LATITUDE_POLES, 3400, 4500}) {
                for (int x = x0 - 2048; x <= x0 + 2048; x += 64) {
                    Holder<Biome> biome = biome(server, x, 64, sens * z);
                    if (!geles.contains(biome)) {
                        defauts.add(pole + " : " + biome.getRegisteredName() + " en (" + x + ", " + sens * z + ")");
                        break;
                    }
                }
            }
        }
        return String.join(" ; ", defauts);
    }

    /** Les régions du Sud de Mars sur la terre, dans la carte de Minecraft ; celle des pôles sur un biome gelé, pas en mer. */
    private static String verifierRegions(MinecraftServer server) {
        PlanMars plan = Objects.requireNonNull(RegionsMars.plan(server), "pas de plan");
        List<String> defauts = new ArrayList<>();
        SourceBiomesMars.Polaires polaires = source(server).polaires();
        for (Map.Entry<Region, BlockPos> region : plan.regions().entrySet()) {
            BlockPos pos = region.getValue();
            Holder<Biome> biome = biome(server, pos.getX(), pos.getY(), pos.getZ());
            boolean eau = biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER);
            boolean convient = region.getKey() == Region.POLES
                    ? !eau && (biome.equals(polaires.plaine()) || biome.equals(polaires.picsDeGlace()) || biome.equals(polaires.pics()) || biome.equals(polaires.plage()))
                    : !eau && !remplace(server, pos.getX(), pos.getY(), pos.getZ());
            RedRising.LOGGER.info("[G7.1.2] Région {} en {} : {}", region.getKey().getSerializedName(), pos.toShortString(), biome.getRegisteredName());
            if (!convient) {
                defauts.add(region.getKey().getSerializedName() + " sur " + biome.getRegisteredName());
            }
        }
        return String.join(" ; ", defauts);
    }
}
