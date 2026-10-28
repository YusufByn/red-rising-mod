package io.github.yusufbyn.redrising.monde;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Le plan des régions de Mars côté serveur (G7.1.1). La logique est dans {@link PlanMars}.
 *
 * <ul>
 *     <li><b>Quand</b> : une seule fois, dans un monde neuf, juste après le choix du point d'apparition et avant la
 *     préparation des chunks autour de lui (mixin {@code MinecraftServerMixin} sur {@code setInitialSpawn}). Puis il est
 *     sauvegardé dans l'état du monde, et jamais recalculé.</li>
 *     <li><b>Sans plan</b> : un monde plat (les tests en jeu) ou créé avant G7 n'en a pas, et tout marche comme avant.</li>
 *     <li><b>Pour la génération</b> (G7.2.1) : {@link #courant()} est une copie du plan sauvegardé, posée au chargement
 *     de l'Overworld par le fil du serveur. Le plan est immuable et le champ {@code volatile} : les fils de génération
 *     le lisent sans verrou.</li>
 * </ul>
 */
public final class RegionsMars {
    private static volatile @Nullable PlanMars courant;

    private RegionsMars() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()}. */
    public static void init() {
        // Avant toute génération : les attachments globaux (donc l'état du monde) sont lus dès la construction du serveur
        ServerLevelEvents.LOAD.register((server, monde) -> {
            if (monde.dimension().equals(Level.OVERWORLD)) {
                courant = EtatMondeServeur.get(server).plan().orElse(null);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> courant = null);
    }

    /** Le plan du monde en cours, pour la génération ({@code null} : pas de plan). */
    public static @Nullable PlanMars courant() {
        return courant;
    }

    public static @Nullable PlanMars plan(MinecraftServer server) {
        return EtatMondeServeur.get(server).plan().orElse(null);
    }

    /**
     * Le point d'apparition d'un monde neuf vient d'être choisi (mixin) : on tire le plan, si l'Overworld a un vrai relief
     * (pas un monde plat) et qu'il n'en a pas déjà un.
     */
    public static void apresApparition(ServerLevel monde) {
        MinecraftServer server = monde.getServer();
        ChunkGenerator generateur = monde.getChunkSource().getGenerator();
        if (!monde.dimension().equals(Level.OVERWORLD) || !(generateur instanceof NoiseBasedChunkGenerator) || plan(server) != null) {
            return;
        }
        long debut = System.nanoTime();
        RandomState aleatoire = monde.getChunkSource().randomState();
        BlockPos apparition = monde.getLevelData().getRespawnData().pos();
        Prepares prepares = prepares(monde, ChunkPos.containing(apparition));
        PlanMars.Terrain terrain = terrain(generateur, aleatoire, monde);
        List<ChunkPos> forts = forts(monde);
        PlanMars plan = PlanMars.tirer(monde.getSeed(), apparition, terrain, prepares, forts);
        EtatMondeServeur.modifier(server, etat -> etat.avecPlan(plan));
        courant = plan;
        BlockPos mine = plan.regions().get(Region.MINE);
        dernierTirage = new Tirage(prepares.rayonReferences(), prepares.rayonDeparts(),
                Math.round(Math.hypot(mine.getX() - apparition.getX(), mine.getZ() - apparition.getZ())), fortLePlusProche(mine, forts),
                plan.defauts(terrain, prepares, forts));
        RedRising.LOGGER.info("Plan de Mars tiré en {} ms (apparition {}) : {}", (System.nanoTime() - debut) / 1_000_000,
                apparition.toShortString(), plan.regions());
        RedRising.LOGGER.info("Plan de Mars : {}", dernierTirage);
    }

    /**
     * Ce que le dernier tirage a vu (pour les tests et le journal) : jusqu'où, autour du point d'apparition, des chunks
     * avaient déjà passé les étapes des références et des départs de structures (en chunks, en carré, -1 : aucun), la
     * distance de la mine au point d'apparition, celle du fort le plus proche (en chunks, en carré), et les défauts du plan.
     */
    public record Tirage(int rayonReferences, int rayonDeparts, long distanceMine, int fortLePlusProche, List<String> defauts) {
    }

    /**
     * Les chunks de départ des forts de ce monde, calculés depuis la graine (les anneaux de Minecraft, sans générer de
     * chunks). Minecraft les calcule de toute façon au démarrage ; la première demande attend ce calcul.
     */
    public static List<ChunkPos> forts(ServerLevel monde) {
        ChunkGeneratorStructureState etat = monde.getChunkSource().getGeneratorState();
        List<ChunkPos> forts = new ArrayList<>();
        for (Holder<StructureSet> ensemble : etat.possibleStructureSets()) {
            if (ensemble.value().placement() instanceof ConcentricRingsStructurePlacement anneaux) {
                List<ChunkPos> positions = etat.getRingPositionsFor(anneaux);
                if (positions != null) {
                    forts.addAll(positions);
                }
            }
        }
        return forts;
    }

    /** Distance (en chunks, en carré) du fort le plus proche de la mine, ou -1 sans fort. */
    static int fortLePlusProche(BlockPos mine, List<ChunkPos> forts) {
        return forts.stream().mapToInt(fort -> Math.max(Math.abs(fort.x() - (mine.getX() >> 4)), Math.abs(fort.z() - (mine.getZ() >> 4))))
                .min().orElse(-1);
    }

    private static volatile @Nullable Tirage dernierTirage;

    public static @Nullable Tirage dernierTirage() {
        return dernierTirage;
    }

    /** Portée du relevé des chunks déjà préparés, en chunks autour du point d'apparition : bien au-delà de la recherche (5). */
    private static final int RELEVE = 48;

    /** Les chunks déjà préparés autour du point d'apparition, relevés sur leur état réel dans la carte des chunks. */
    private record Prepares(LongSet departs, LongSet references, int rayonDeparts, int rayonReferences) implements PlanMars.ChunksPrepares {
        @Override
        public boolean departs(int cx, int cz) {
            return departs.contains(ChunkPos.pack(cx, cz));
        }

        @Override
        public boolean references(int cx, int cz) {
            return references.contains(ChunkPos.pack(cx, cz));
        }
    }

    private static Prepares prepares(ServerLevel monde, ChunkPos centre) {
        LongSet departs = new LongOpenHashSet();
        LongSet references = new LongOpenHashSet();
        int rayonDeparts = -1;
        int rayonReferences = -1;
        for (int dx = -RELEVE; dx <= RELEVE; dx++) {
            for (int dz = -RELEVE; dz <= RELEVE; dz++) {
                long cle = ChunkPos.pack(centre.x() + dx, centre.z() + dz);
                ChunkHolder porteur = monde.getChunkSource().chunkMap.getUpdatingChunkIfPresent(cle);
                ChunkStatus etat = porteur == null ? null : porteur.getPersistedStatus();
                if (etat == null) {
                    continue;
                }
                int rayon = Math.max(Math.abs(dx), Math.abs(dz));
                if (etat.isOrAfter(ChunkStatus.STRUCTURE_STARTS)) {
                    departs.add(cle);
                    rayonDeparts = Math.max(rayonDeparts, rayon);
                }
                if (etat.isOrAfter(ChunkStatus.STRUCTURE_REFERENCES)) {
                    references.add(cle);
                    rayonReferences = Math.max(rayonReferences, rayon);
                }
            }
        }
        return new Prepares(departs, references, rayonDeparts, rayonReferences);
    }

    /**
     * Le terrain tel que le générateur le fera, lu sans générer de chunks : la hauteur par le bruit du relief, l'eau par
     * le biome (océan, fleuve) ou un sol sous le niveau de la mer.
     */
    public static PlanMars.Terrain terrain(ChunkGenerator generateur, RandomState aleatoire, LevelHeightAccessor hauteurs) {
        BiomeSource biomes = generateur.getBiomeSource();
        int niveauDeLaMer = generateur.getSeaLevel();
        return new PlanMars.Terrain() {
            @Override
            public boolean eau(int x, int z) {
                Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(niveauDeLaMer), QuartPos.fromBlock(z), aleatoire.sampler());
                return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || hauteur(x, z) < niveauDeLaMer - 1;
            }

            @Override
            public int hauteur(int x, int z) {
                return generateur.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, hauteurs, aleatoire);
            }
        };
    }
}
