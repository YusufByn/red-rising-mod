package io.github.yusufbyn.redrising.monde;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.yusufbyn.redrising.RedRising;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;

/**
 * La mine des Rouges (Lykos, G7.2.1) : une structure de Minecraft, générée chunk par chunk avec le reste du monde, à la
 * position du plan des régions ({@link RegionsMars#courant()}). Le plan des galeries est dans {@link PlanDeMine}.
 *
 * <ul>
 *     <li><b>Placement</b> : seulement le chunk de la mine dans le plan sauvegardé ; un monde sans plan n'a pas de mine.</li>
 *     <li><b>Morceaux</b> : un par chunk de l'emprise (départ ± 8 chunks), sur les hauteurs de la mine, et un autour du puits
 *     principal jusqu'à 14 blocs sous la surface. Leurs boîtes servent au remplissage du relief ({@code encapsulate},
 *     dans {@code data/redrising/worldgen/structure/mine.json}) : ni grotte de bruit ni aquifère dans le volume.</li>
 *     <li><b>Pose</b> : à la dernière étape de la génération ({@code top_layer_modification}), après les minerais, les
 *     géodes et les sources. Chaque morceau pose les blocs du plan qui touchent son chunk : coque de roche (seulement à
 *     la place du vide et des fluides), vide, étais, échelles, bouchon.</li>
 *     <li><b>Autour</b> : pas de carvers dans l'emprise, ni d'autre structure (sauf un fort) ni de salle de monstres
 *     (mixins sur {@code NoiseBasedChunkGenerator.applyCarvers}, {@code ChunkGenerator.tryGenerateStructure},
 *     {@code MonsterRoomFeature.place}).</li>
 * </ul>
 */
public final class MineLykos extends Structure {
    public static final MapCodec<MineLykos> CODEC = simpleCodec(MineLykos::new);
    public static final StructureType<MineLykos> TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, id("mine"), () -> CODEC);
    public static final StructurePieceType MORCEAU = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, id("mine_morceau"),
            (StructurePieceType.ContextlessType) Morceau::new);
    public static final StructurePlacementType<Placement> PLACEMENT = Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, id("plan"),
            () -> Placement.CODEC);

    /** 🟡 Hauteur au-dessus de laquelle une autre structure ne gêne plus la mine (sauf le long du puits). */
    public static final int HAUT_DU_VOLUME = 32;

    /** Temps de pose des morceaux, pour le test du coût (en nanosecondes). */
    private static final AtomicLong POSES = new AtomicLong();
    private static final AtomicLong TEMPS_TOTAL = new AtomicLong();
    private static final LongAccumulator TEMPS_MAX = new LongAccumulator(Math::max, 0);

    public MineLykos(StructureSettings reglages) {
        super(reglages);
    }

    /** Appelé depuis {@link RedRising#onInitialize()} : les registres (champs ci-dessus) sont remplis au chargement de la classe. */
    public static void init() {
        RedRising.LOGGER.debug("Mine : {} {} {}", TYPE, MORCEAU, PLACEMENT);
    }

    @Override
    public StructureType<?> type() {
        return TYPE;
    }

    // --- Emprise ---

    /** La position de la mine dans le plan en cours, ou {@code null} (pas de plan). */
    public static @Nullable BlockPos centre() {
        PlanMars plan = RegionsMars.courant();
        return plan == null ? null : plan.regions().get(Region.MINE);
    }

    /** Ce chunk est-il dans l'emprise de la mine (son chunk de départ et 8 chunks autour) ? Faux sans plan. */
    public static boolean dansEmprise(int cx, int cz) {
        BlockPos centre = centre();
        return centre != null && Math.abs(cx - (centre.getX() >> 4)) <= PlanMars.EMPRISE_MINE && Math.abs(cz - (centre.getZ() >> 4)) <= PlanMars.EMPRISE_MINE;
    }

    /**
     * Une autre structure qui croiserait la mine : dans l'emprise, sous {@value #HAUT_DU_VOLUME}, ou le long du puits
     * principal jusqu'en haut. Un fort n'est jamais refusé (l'accès à l'End) : le plan des régions l'évite.
     */
    public static boolean empiete(StructureStart depart) {
        BlockPos centre = centre();
        if (centre == null || depart.getStructure().type() == TYPE || depart.getStructure().type() == StructureType.STRONGHOLD) {
            return false;
        }
        BoundingBox boite = depart.getBoundingBox();
        int cx = centre.getX() >> 4;
        int cz = centre.getZ() >> 4;
        int r = PlanMars.EMPRISE_MINE;
        BoundingBox volume = new BoundingBox((cx - r) << 4, -64, (cz - r) << 4, ((cx + r) << 4) + 15, HAUT_DU_VOLUME, ((cz + r) << 4) + 15);
        int bord = 1 + PlanDeMine.COQUE_PUITS + 1;
        BoundingBox colonne = new BoundingBox(centre.getX() - bord, -64, centre.getZ() - bord, centre.getX() + bord, 400, centre.getZ() + bord);
        return boite.intersects(volume) || boite.intersects(colonne);
    }

    // --- Génération ---

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext contexte) {
        BlockPos centre = centre();
        if (centre == null || contexte.chunkPos().x() != centre.getX() >> 4 || contexte.chunkPos().z() != centre.getZ() >> 4) {
            return Optional.empty();
        }
        ChunkGenerator generateur = contexte.chunkGenerator();
        int r = PlanMars.EMPRISE_MINE * 16;
        // Le terrain le plus bas de l'emprise, et autour du puits, lus par le bruit du relief (sans chunk généré)
        int solMinimum = Integer.MAX_VALUE;
        for (int x = centre.getX() - r; x <= centre.getX() + r; x += 16) {
            for (int z = centre.getZ() - r; z <= centre.getZ() + r; z += 16) {
                solMinimum = Math.min(solMinimum, hauteur(contexte, x, z));
            }
        }
        int solAuPuits = Integer.MAX_VALUE;
        for (int x = -16; x <= 16; x += 8) {
            for (int z = -16; z <= 16; z += 8) {
                solAuPuits = Math.min(solAuPuits, hauteur(contexte, centre.getX() + x, centre.getZ() + z));
            }
        }
        long graine = contexte.seed();
        PlanDeMine plan = PlanDeMine.de(graine, centre, solMinimum, solAuPuits);
        int solMin = solMinimum;
        int solPuits = solAuPuits;
        BlockPos position = new BlockPos(centre.getX(), plan.sols().getFirst(), centre.getZ());
        RedRising.LOGGER.info("Mine : départ en {}, {} niveau(x), terrain le plus bas {}, au puits {} (générateur {})", position.toShortString(),
                plan.sols().size(), solMin, solPuits, generateur.getClass().getSimpleName());
        return Optional.of(new GenerationStub(position, morceaux -> {
            int cx = centre.getX() >> 4;
            int cz = centre.getZ() >> 4;
            int bas = plan.sols().getLast() - 4;
            for (int dx = -PlanMars.EMPRISE_MINE; dx <= PlanMars.EMPRISE_MINE; dx++) {
                for (int dz = -PlanMars.EMPRISE_MINE; dz <= PlanMars.EMPRISE_MINE; dz++) {
                    ChunkPos chunk = new ChunkPos(cx + dx, cz + dz);
                    BoundingBox boite = new BoundingBox(chunk.getMinBlockX(), bas, chunk.getMinBlockZ(), chunk.getMaxBlockX(), plan.hautDesBoites(),
                            chunk.getMaxBlockZ());
                    morceaux.addPiece(new Morceau(boite, true, graine, centre, solMin, solPuits));
                }
            }
            // Le puits principal et sa marge de 2 blocs, jusqu'à 14 blocs sous la surface
            if (plan.hautBoitePuits() > plan.hautDesBoites()) {
                int bord = 1 + PlanDeMine.COQUE_PUITS;
                morceaux.addPiece(new Morceau(new BoundingBox(centre.getX() - bord, plan.hautDesBoites(), centre.getZ() - bord,
                        centre.getX() + bord, plan.hautBoitePuits(), centre.getZ() + bord), false, graine, centre, solMin, solPuits));
            }
        }));
    }

    private static int hauteur(GenerationContext contexte, int x, int z) {
        return contexte.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, contexte.heightAccessor(), contexte.randomState());
    }

    /** Mesures de la pose (nombre de morceaux posés, temps moyen et maximal en ms), pour le test du coût. */
    public static String mesures() {
        long poses = POSES.get();
        return poses + " morceaux, " + String.format(java.util.Locale.ROOT, "%.1f", poses == 0 ? 0 : TEMPS_TOTAL.get() / 1e6 / poses)
                + " ms en moyenne, " + String.format(java.util.Locale.ROOT, "%.1f", TEMPS_MAX.get() / 1e6) + " ms au plus";
    }

    public static long poses() {
        return POSES.get();
    }

    public static double tempsMoyenMs() {
        long poses = POSES.get();
        return poses == 0 ? 0 : TEMPS_TOTAL.get() / 1e6 / poses;
    }

    public static double tempsMaxMs() {
        return TEMPS_MAX.get() / 1e6;
    }

    public static void remettreMesures() {
        POSES.set(0);
        TEMPS_TOTAL.set(0);
        TEMPS_MAX.reset();
    }

    // --- Morceau ---

    /**
     * Un morceau de la mine. {@code pose} : il pose les blocs du plan dans son chunk (un morceau par chunk de l'emprise) ;
     * sinon il ne sert qu'au remplissage du relief (celui du puits). Il garde de quoi retrouver le plan (même graine,
     * mêmes hauteurs), qui est tiré une fois puis relu.
     */
    public static final class Morceau extends StructurePiece {
        private final boolean pose;
        private final long graine;
        private final BlockPos centre;
        private final int solMinimum;
        private final int solAuPuits;

        Morceau(BoundingBox boite, boolean pose, long graine, BlockPos centre, int solMinimum, int solAuPuits) {
            super(MORCEAU, 0, boite);
            this.pose = pose;
            this.graine = graine;
            this.centre = centre;
            this.solMinimum = solMinimum;
            this.solAuPuits = solAuPuits;
        }

        Morceau(CompoundTag donnees) {
            super(MORCEAU, donnees);
            this.pose = donnees.getBooleanOr("pose", true);
            this.graine = donnees.getLongOr("graine", 0);
            this.centre = new BlockPos(donnees.getIntOr("cx", 0), donnees.getIntOr("cy", 0), donnees.getIntOr("cz", 0));
            this.solMinimum = donnees.getIntOr("sol_minimum", 0);
            this.solAuPuits = donnees.getIntOr("sol_au_puits", 0);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext contexte, CompoundTag donnees) {
            donnees.putBoolean("pose", pose);
            donnees.putLong("graine", graine);
            donnees.putInt("cx", centre.getX());
            donnees.putInt("cy", centre.getY());
            donnees.putInt("cz", centre.getZ());
            donnees.putInt("sol_minimum", solMinimum);
            donnees.putInt("sol_au_puits", solAuPuits);
        }

        public PlanDeMine plan() {
            return PlanDeMine.de(graine, centre, solMinimum, solAuPuits);
        }

        @Override
        public void postProcess(WorldGenLevel monde, StructureManager structures, ChunkGenerator generateur, RandomSource hasard, BoundingBox chunk,
                                ChunkPos position, BlockPos pivot) {
            if (!pose) {
                return;
            }
            long debut = System.nanoTime();
            poser(monde, plan(), chunk);
            long duree = System.nanoTime() - debut;
            POSES.incrementAndGet();
            TEMPS_TOTAL.addAndGet(duree);
            TEMPS_MAX.accumulate(duree);
        }
    }

    // --- Pose ---

    private static final BlockState VIDE = Blocks.CAVE_AIR.defaultBlockState();
    private static final BlockState POTEAU = Blocks.SPRUCE_FENCE.defaultBlockState();
    private static final BlockState POUTRE = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState PILIER = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState ECHELLE = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
    private static final BlockState BOUCHON = Blocks.POLISHED_ANDESITE.defaultBlockState();

    /** Pose, dans ce chunk, tout ce que le plan y met. */
    static void poser(WorldGenLevel monde, PlanDeMine plan, BoundingBox chunk) {
        BoundingBox colonne = new BoundingBox(chunk.minX(), monde.getMinY(), chunk.minZ(), chunk.maxX(), monde.getMaxY(), chunk.maxZ());
        List<BoundingBox> proches = plan.boitesVers(colonne.inflatedBy(PlanDeMine.COQUE_PUITS));
        if (proches.isEmpty()) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // 1. Coque : de la roche à la place du vide et des fluides, autour de tout le vide de la mine
        for (BoundingBox boite : proches) {
            int coque = estUnPuits(plan, boite) ? PlanDeMine.COQUE_PUITS : 1;
            BoundingBox zone = intersection(boite.inflatedBy(coque), colonne);
            if (zone == null) {
                continue;
            }
            for (int x = zone.minX(); x <= zone.maxX(); x++) {
                for (int z = zone.minZ(); z <= zone.maxZ(); z++) {
                    for (int y = zone.minY(); y <= zone.maxY(); y++) {
                        if (interieur(plan, proches, x, y, z)) {
                            continue;
                        }
                        pos.set(x, y, z);
                        BlockState etat = monde.getBlockState(pos);
                        if (etat.isAir() || !etat.getFluidState().isEmpty() || etat.canBeReplaced()) {
                            monde.setBlock(pos, y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
        // 2. Vide
        for (BoundingBox boite : proches) {
            BoundingBox zone = intersection(boite, colonne);
            if (zone == null) {
                continue;
            }
            for (int x = zone.minX(); x <= zone.maxX(); x++) {
                for (int z = zone.minZ(); z <= zone.maxZ(); z++) {
                    for (int y = zone.minY(); y <= zone.maxY(); y++) {
                        if (interieur(plan, proches, x, y, z)) {
                            pos.set(x, y, z);
                            if (!monde.getBlockState(pos).is(Blocks.CAVE_AIR)) {
                                monde.setBlock(pos, VIDE, Block.UPDATE_CLIENTS);
                            }
                        }
                    }
                }
            }
        }
        // 3. Étais, échelles et leurs piliers, bouchon
        for (BlockPos poteau : plan.poteaux()) {
            if (colonne.isInside(poteau)) {
                monde.setBlock(poteau, POTEAU, Block.UPDATE_CLIENTS);
            }
        }
        for (BlockPos poutre : plan.poutres()) {
            if (colonne.isInside(poutre)) {
                monde.setBlock(poutre, POUTRE, Block.UPDATE_CLIENTS);
            }
        }
        for (PlanDeMine.Puits puits : plan.puits()) {
            for (int y = puits.bas(); y <= puits.haut(); y++) {
                BlockPos echelle = PlanDeMine.echelle(puits, y);
                BlockPos pilier = PlanDeMine.pilier(puits, y);
                if (colonne.isInside(pilier) && plan.interieur(pilier.getX(), y, pilier.getZ())) {
                    monde.setBlock(pilier, PILIER, Block.UPDATE_CLIENTS);
                }
                if (colonne.isInside(echelle)) {
                    monde.setBlock(echelle, ECHELLE, Block.UPDATE_CLIENTS);
                }
            }
        }
        BoundingBox bouchon = intersection(plan.bouchon(), colonne);
        if (bouchon != null) {
            BlockPos.betweenClosed(bouchon.minX(), bouchon.minY(), bouchon.minZ(), bouchon.maxX(), bouchon.maxY(), bouchon.maxZ())
                    .forEach(bloc -> monde.setBlock(bloc, BOUCHON, Block.UPDATE_CLIENTS));
        }
    }

    /** Comme {@link PlanDeMine#interieur}, mais seulement parmi les éléments proches du chunk (bien plus rapide). */
    private static boolean interieur(PlanDeMine plan, List<BoundingBox> proches, int x, int y, int z) {
        BoundingBox caverne = plan.caverne().boite();
        for (BoundingBox boite : proches) {
            if (boite.isInside(x, y, z) && (!boite.equals(caverne) || plan.caverne().contient(x, y, z))) {
                return true;
            }
        }
        return false;
    }

    private static boolean estUnPuits(PlanDeMine plan, BoundingBox boite) {
        return plan.puits().stream().anyMatch(puits -> puits.interieur().equals(boite));
    }

    private static @Nullable BoundingBox intersection(BoundingBox a, BoundingBox b) {
        if (!a.intersects(b)) {
            return null;
        }
        return new BoundingBox(Math.max(a.minX(), b.minX()), Math.max(a.minY(), b.minY()), Math.max(a.minZ(), b.minZ()),
                Math.min(a.maxX(), b.maxX()), Math.min(a.maxY(), b.maxY()), Math.min(a.maxZ(), b.maxZ()));
    }

    // --- Placement ---

    /** Le placement de la mine : le chunk de la mine dans le plan des régions sauvegardé ; nulle part sans plan. */
    public static final class Placement extends StructurePlacement {
        static final MapCodec<Placement> CODEC = RecordCodecBuilder.mapCodec(instance -> placementCodec(instance).apply(instance, Placement::new));

        Placement(Vec3i decalage, FrequencyReductionMethod reduction, float frequence, int sel, Optional<ExclusionZone> exclusion) {
            super(decalage, reduction, frequence, sel, exclusion);
        }

        @Override
        protected boolean isPlacementChunk(ChunkGeneratorStructureState etat, int x, int z) {
            BlockPos centre = centre();
            return centre != null && x == centre.getX() >> 4 && z == centre.getZ() >> 4;
        }

        @Override
        public StructurePlacementType<?> type() {
            return PLACEMENT;
        }
    }

    private static Identifier id(String nom) {
        return Identifier.fromNamespaceAndPath(RedRising.MOD_ID, nom);
    }
}
