package io.github.yusufbyn.redrising.monde;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.yusufbyn.redrising.RedRising;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.BlockTags;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.network.chat.Component;
import java.util.Map;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.MonsterRoomFeature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
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
 *     principal jusqu'à 14 blocs sous le terrain le plus bas autour. Leurs boîtes servent au remplissage du relief ({@code encapsulate},
 *     dans {@code data/redrising/worldgen/structure/mine.json}) : ni grotte de bruit ni aquifère dans le volume.</li>
 *     <li><b>Pose</b> : à la dernière étape de la génération ({@code top_layer_modification}), après les minerais, les
 *     géodes et les sources. Chaque morceau pose les blocs du plan qui touchent son chunk : coque de roche (seulement à
 *     la place du vide et des fluides), vide (galeries, puits, caverne, salles, niches de garage), étais, échelles, lanternes,
 *     voies et wagonnets, coffres, bouchon ; et avant tout, les
 *     filons de son chunk.</li>
 *     <li><b>Autour</b> : pas de carvers dans l'emprise, ni d'autre structure (sauf un fort), ni de décoration souterraine
 *     autre que les minerais dans le volume de la mine (mixins sur {@code NoiseBasedChunkGenerator.applyCarvers},
 *     {@code ChunkGenerator.tryGenerateStructure}, {@code ConfiguredFeature.place}).</li>
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
    /** Le long du puits principal, jusqu'à cette distance : ni structure ni décoration souterraine, jusqu'en haut. */
    static final int MARGE_PUITS = 32;

    /** Coût de la règle des décorations ({@link #decorationRefusee}), dans l'emprise (et un chunk autour) et ailleurs. */
    public static final Compteur DECORATIONS_DEDANS = new Compteur();
    public static final Compteur DECORATIONS_HORS = new Compteur();

    /** Un nombre d'appels et leur temps total (en nanosecondes), pour le test du coût. */
    public static final class Compteur {
        private final java.util.concurrent.atomic.LongAdder appels = new java.util.concurrent.atomic.LongAdder();
        private final java.util.concurrent.atomic.LongAdder nanos = new java.util.concurrent.atomic.LongAdder();

        void ajouter(long duree) {
            appels.increment();
            nanos.add(duree);
        }

        public long appels() {
            return appels.sum();
        }

        /** Temps total, en millisecondes. */
        public double totalMs() {
            return nanos.sum() / 1e6;
        }

        /** Temps moyen par appel, en microsecondes (la mesure elle-même comprise). */
        public double moyenneUs() {
            long n = appels.sum();
            return n == 0 ? 0 : nanos.sum() / 1e3 / n;
        }

        public void remettre() {
            appels.reset();
            nanos.reset();
        }

        @Override
        public String toString() {
            return appels() + " appels, " + String.format(java.util.Locale.ROOT, "%.2f", moyenneUs()) + " µs en moyenne, "
                    + String.format(java.util.Locale.ROOT, "%.1f", totalMs()) + " ms en tout";
        }
    }

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
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> planEnJeu = null);
    }

    /** La mine, dans le registre des structures du monde. */
    public static final ResourceKey<Structure> CLE = ResourceKey.create(Registries.STRUCTURE, id("mine"));

    /** Le plan de la mine du monde en cours, relu une fois dans le morceau de son chunk de départ (serveur seulement). */
    private static volatile @Nullable PlanDeMine planEnJeu;

    /**
     * Le plan de la mine de ce monde (G7.2.3), pour les règles qui tournent pendant le jeu (monstres, traque) ; {@code null}
     * sans plan des régions, hors de l'Overworld, ou si la mine n'a pas de départ. Il est relu dans le morceau du chunk de
     * départ (mêmes hauteurs que la génération), chargé au besoin jusqu'à l'étape des départs de structures.
     */
    public static @Nullable PlanDeMine planEnJeu(ServerLevel niveau) {
        // La dimension d'abord : le plan en cache est celui de l'Overworld
        if (!niveau.dimension().equals(Level.OVERWORLD)) {
            return null;
        }
        PlanDeMine plan = planEnJeu;
        BlockPos centre = centre();
        if (centre == null || plan != null) {
            return centre == null ? null : plan;
        }
        Structure mine = niveau.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(CLE);
        StructureStart depart = mine == null ? null
                : niveau.getChunk(centre.getX() >> 4, centre.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS, true).getStartForStructure(mine);
        if (depart == null || !depart.isValid() || !(depart.getPieces().getFirst() instanceof Morceau morceau)) {
            return null;
        }
        planEnJeu = morceau.plan();
        return planEnJeu;
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

    /**
     * Une décoration à ne pas poser (G7.2.1). Une décoration peut écrire dans le chunk voisin, même quand celui-ci est fini et
     * que la mine y est posée (mesuré : une géode dans une galerie). Donc, dans l'emprise et un chunk autour, sous
     * {@value #HAUT_DU_VOLUME} ou à {@value #MARGE_PUITS} blocs du puits principal, rien de souterrain ne se pose (géodes,
     * sources, lacs souterrains, concrétions, lichen...), sauf les minerais : ils ne remplacent que de la roche, jamais le
     * vide. Les salles de monstres, nulle part dans l'emprise (un générateur à nu). La surface (arbres, herbe) ne change pas.
     */
    public static boolean decorationRefusee(WorldGenLevel monde, Feature<?> decoration, BlockPos origine) {
        long debut = System.nanoTime();
        BlockPos centre = centre();
        // Hors de l'emprise et d'un chunk autour : rien n'est refusé (le cas de presque toutes les décorations du monde)
        boolean pres = centre != null && Math.abs((origine.getX() >> 4) - (centre.getX() >> 4)) <= PlanMars.EMPRISE_MINE + 1
                && Math.abs((origine.getZ() >> 4) - (centre.getZ() >> 4)) <= PlanMars.EMPRISE_MINE + 1;
        boolean refus = pres && refuser(monde, decoration, origine, centre);
        (pres ? DECORATIONS_DEDANS : DECORATIONS_HORS).ajouter(System.nanoTime() - debut);
        return refus;
    }

    private static boolean refuser(WorldGenLevel monde, Feature<?> decoration, BlockPos origine, BlockPos centre) {
        if (decoration instanceof OreFeature || decoration instanceof ScatteredOreFeature || !monde.getLevel().dimension().equals(Level.OVERWORLD)) {
            return false;
        }
        if (decoration instanceof MonsterRoomFeature) {
            return Math.abs((origine.getX() >> 4) - (centre.getX() >> 4)) <= PlanMars.EMPRISE_MINE
                    && Math.abs((origine.getZ() >> 4) - (centre.getZ() >> 4)) <= PlanMars.EMPRISE_MINE;
        }
        boolean presDuPuits = Math.abs(origine.getX() - centre.getX()) <= MARGE_PUITS && Math.abs(origine.getZ() - centre.getZ()) <= MARGE_PUITS;
        if (origine.getY() >= HAUT_DU_VOLUME && !presDuPuits) {
            return false;
        }
        return origine.getY() < monde.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origine.getX(), origine.getZ()) - 2;
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
        // La surface de la colonne du puits (3 × 3) : son point le plus bas, sous lequel est le bouchon
        int surfacePuits = Integer.MAX_VALUE;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                surfacePuits = Math.min(surfacePuits, hauteur(contexte, centre.getX() + x, centre.getZ() + z));
            }
        }
        long graine = contexte.seed();
        PlanDeMine plan = PlanDeMine.de(graine, centre, solMinimum, solAuPuits, surfacePuits);
        int solMin = solMinimum;
        int solPuits = solAuPuits;
        int surface = surfacePuits;
        BlockPos position = new BlockPos(centre.getX(), plan.sols().getFirst(), centre.getZ());
        RedRising.LOGGER.info("Mine : départ en {}, {} niveau(x), terrain le plus bas {}, autour du puits {}, surface du puits {} (générateur {})",
                position.toShortString(), plan.sols().size(), solMin, solPuits, surface, generateur.getClass().getSimpleName());
        return Optional.of(new GenerationStub(position, morceaux -> {
            int cx = centre.getX() >> 4;
            int cz = centre.getZ() >> 4;
            int bas = plan.sols().getLast() - 4;
            for (int dx = -PlanMars.EMPRISE_MINE; dx <= PlanMars.EMPRISE_MINE; dx++) {
                for (int dz = -PlanMars.EMPRISE_MINE; dz <= PlanMars.EMPRISE_MINE; dz++) {
                    ChunkPos chunk = new ChunkPos(cx + dx, cz + dz);
                    BoundingBox boite = new BoundingBox(chunk.getMinBlockX(), bas, chunk.getMinBlockZ(), chunk.getMaxBlockX(), plan.hautDesBoites(),
                            chunk.getMaxBlockZ());
                    morceaux.addPiece(new Morceau(boite, true, graine, centre, solMin, solPuits, surface));
                }
            }
            // Le puits principal et sa marge de 2 blocs, jusqu'à 14 blocs sous le terrain le plus bas autour (pas de butte) ;
            // au-dessus, jusqu'au bouchon, sa coque le garde fermé
            if (plan.hautBoitePuits() > plan.hautDesBoites()) {
                int bord = 1 + PlanDeMine.COQUE_PUITS;
                morceaux.addPiece(new Morceau(new BoundingBox(centre.getX() - bord, plan.hautDesBoites(), centre.getZ() - bord,
                        centre.getX() + bord, plan.hautBoitePuits(), centre.getZ() + bord), false, graine, centre, solMin, solPuits, surface));
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
        DECORATIONS_DEDANS.remettre();
        DECORATIONS_HORS.remettre();
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
        private final int surfacePuits;

        Morceau(BoundingBox boite, boolean pose, long graine, BlockPos centre, int solMinimum, int solAuPuits, int surfacePuits) {
            super(MORCEAU, 0, boite);
            this.pose = pose;
            this.graine = graine;
            this.centre = centre;
            this.solMinimum = solMinimum;
            this.solAuPuits = solAuPuits;
            this.surfacePuits = surfacePuits;
        }

        Morceau(CompoundTag donnees) {
            super(MORCEAU, donnees);
            this.pose = donnees.getBooleanOr("pose", true);
            this.graine = donnees.getLongOr("graine", 0);
            this.centre = new BlockPos(donnees.getIntOr("cx", 0), donnees.getIntOr("cy", 0), donnees.getIntOr("cz", 0));
            this.solMinimum = donnees.getIntOr("sol_minimum", 0);
            this.solAuPuits = donnees.getIntOr("sol_au_puits", 0);
            // Morceaux d'avant le correctif du bouchon (sans surface du puits) : le plan d'alors, calé sur le terrain autour
            this.surfacePuits = donnees.getIntOr("surface_puits", solAuPuits);
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
            donnees.putInt("surface_puits", surfacePuits);
        }

        public PlanDeMine plan() {
            return PlanDeMine.de(graine, centre, solMinimum, solAuPuits, surfacePuits);
        }

        @Override
        public void postProcess(WorldGenLevel monde, StructureManager structures, ChunkGenerator generateur, RandomSource hasard, BoundingBox chunk,
                                ChunkPos position, BlockPos pivot) {
            if (!pose) {
                return;
            }
            long debut = System.nanoTime();
            poser(monde, plan(), chunk, hasard);
            long duree = System.nanoTime() - debut;
            POSES.incrementAndGet();
            TEMPS_TOTAL.addAndGet(duree);
            TEMPS_MAX.accumulate(duree);
        }
    }

    // --- Pose ---

    /** Le vide de la mine (les blocs posés, eux, sont tous dans {@link PaletteMine}). */
    private static final BlockState VIDE = Blocks.CAVE_AIR.defaultBlockState();
    /** Tables de butin des coffres d'outils ({@code data/redrising/loot_table/chests/}). */
    public static final ResourceKey<LootTable> COFFRE_OUTILS = ResourceKey.create(Registries.LOOT_TABLE, id("chests/mine_outils"));
    public static final ResourceKey<LootTable> COFFRE_RARE = ResourceKey.create(Registries.LOOT_TABLE, id("chests/mine_outils_rare"));

    /** Pose, dans ce chunk, tout ce que le plan y met. */
    static void poser(WorldGenLevel monde, PlanDeMine plan, BoundingBox chunk, RandomSource hasard) {
        BoundingBox colonne = new BoundingBox(chunk.minX(), monde.getMinY(), chunk.minZ(), chunk.maxX(), monde.getMaxY(), chunk.maxZ());
        // Filons d'abord (G7.2.2) : un chunk sans vide de la mine peut en avoir (hélium-3 caché)
        poserFilons(monde, plan, chunk);
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
                        if (plan.interieurParmi(proches, x, y, z)) {
                            continue;
                        }
                        pos.set(x, y, z);
                        BlockState etat = monde.getBlockState(pos);
                        if (etat.isAir() || !etat.getFluidState().isEmpty() || etat.canBeReplaced()) {
                            monde.setBlock(pos, PaletteMine.roche(y), Block.UPDATE_CLIENTS);
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
                        if (plan.interieurParmi(proches, x, y, z)) {
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
                monde.setBlock(poteau, PaletteMine.POTEAU, Block.UPDATE_CLIENTS);
            }
        }
        for (BlockPos poutre : plan.poutres()) {
            if (colonne.isInside(poutre)) {
                monde.setBlock(poutre, PaletteMine.POUTRE, Block.UPDATE_CLIENTS);
            }
        }
        for (PlanDeMine.Puits puits : plan.puits()) {
            for (int y = puits.bas(); y <= puits.haut(); y++) {
                BlockPos echelle = PlanDeMine.echelle(puits, y);
                BlockPos pilier = PlanDeMine.pilier(puits, y);
                if (colonne.isInside(pilier) && plan.interieur(pilier.getX(), y, pilier.getZ())) {
                    monde.setBlock(pilier, PaletteMine.PILIER_ECHELLE, Block.UPDATE_CLIENTS);
                }
                if (colonne.isInside(echelle)) {
                    monde.setBlock(echelle, PaletteMine.ECHELLE, Block.UPDATE_CLIENTS);
                }
            }
        }
        // Lanternes du réseau éclairé (G7.2.3)
        for (PlanDeMine.Lanterne lanterne : plan.lanternes()) {
            if (colonne.isInside(lanterne.pos())) {
                monde.setBlock(lanterne.pos(), PaletteMine.lanterne(lanterne.suspendue()), Block.UPDATE_CLIENTS);
            }
        }
        // Voies (G7.2.3) : butoirs, rails (une torche de redstone sous chaque rail de propulsion), voies de garage et wagonnets
        for (PlanDeMine.Voie voie : plan.voies()) {
            for (BlockPos butoir : List.of(voie.butoirPuits(), voie.butoirBout())) {
                if (colonne.isInside(butoir)) {
                    monde.setBlock(butoir, PaletteMine.BUTOIR, Block.UPDATE_CLIENTS);
                }
            }
            voie.rails().forEach(rail -> poserRail(monde, colonne, rail));
        }
        for (PlanDeMine.Garage garage : plan.garages()) {
            garage.rails().forEach(rail -> poserRail(monde, colonne, rail));
            BlockPos place = garage.wagonnet();
            if (colonne.isInside(place)) {
                Minecart wagonnet = EntityTypes.MINECART.create(monde.getLevel(), EntitySpawnReason.STRUCTURE);
                if (wagonnet != null) {
                    wagonnet.setPos(place.getX() + 0.5, place.getY() + 0.0625, place.getZ() + 0.5);
                    monde.addFreshEntity(wagonnet);
                }
            }
        }
        // Repères (G7.2.3) : la bande de couleur (à la place de la roche seulement), les panneaux (cirés : on ne les réécrit pas)
        for (Map.Entry<BlockPos, Integer> bande : plan.bandes().entrySet()) {
            BlockPos mur = bande.getKey();
            if (colonne.isInside(mur)) {
                BlockState roche = monde.getBlockState(mur);
                if (roche.is(BlockTags.STONE_ORE_REPLACEABLES) || roche.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
                    monde.setBlock(mur, PaletteMine.BANDES.get(bande.getValue()), Block.UPDATE_CLIENTS);
                }
            }
        }
        for (PlanDeMine.Panneau panneau : plan.panneaux()) {
            if (colonne.isInside(panneau.pos())) {
                BlockPos place = panneau.pos();
                monde.setBlock(place, PaletteMine.panneau(panneau.face()), Block.UPDATE_CLIENTS);
                // Le texte par les données du chunk, relues quand il est fini : SignBlockEntity.setText préviendrait le monde
                // d'une mise à jour, ce qu'on ne peut pas faire depuis un fil de génération (blocage constaté)
                RegistryOps<Tag> ops = monde.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                CompoundTag donnees = new CompoundTag();
                donnees.putString("id", "minecraft:sign");
                donnees.putInt("x", place.getX());
                donnees.putInt("y", place.getY());
                donnees.putInt("z", place.getZ());
                donnees.put("front_text", SignText.DIRECT_CODEC.encodeStart(ops, texte(panneau)).getOrThrow());
                donnees.put("back_text", SignText.DIRECT_CODEC.encodeStart(ops, new SignText()).getOrThrow());
                donnees.putBoolean("is_waxed", true);
                monde.getChunk(place).setBlockEntityNbt(donnees);
            }
        }
        // Coffres d'outils (G7.2.2) : sans camp, leur contenu tiré à la première ouverture
        for (PlanDeMine.Coffre coffre : plan.coffres()) {
            if (colonne.isInside(coffre.pos())) {
                monde.setBlock(coffre.pos(), PaletteMine.coffre(coffre.face()), Block.UPDATE_CLIENTS);
                RandomizableContainer.setBlockEntityLootTable(monde, hasard, coffre.pos(), coffre.rare() ? COFFRE_RARE : COFFRE_OUTILS);
            }
        }
        BoundingBox bouchon = intersection(plan.bouchon(), colonne);
        if (bouchon != null) {
            BlockPos.betweenClosed(bouchon.minX(), bouchon.minY(), bouchon.minZ(), bouchon.maxX(), bouchon.maxY(), bouchon.maxZ())
                    .forEach(bloc -> monde.setBlock(bloc, PaletteMine.BOUCHON, Block.UPDATE_CLIENTS));
        }
    }

    /**
     * Les filons de ce chunk (G7.2.2), posés avant le vide : un filon n'est jamais dans le vide du plan, et la coque ne remplace
     * que le vide et les fluides. Seulement à la place de la roche (et des poches de gravier ou de terre), en pierre ou en
     * ardoise des profondeurs selon elle.
     */
    private static void poserFilons(WorldGenLevel monde, PlanDeMine plan, BoundingBox chunk) {
        for (FilonsDeMine.Bloc bloc : plan.filonsDuChunk(chunk.minX() >> 4, chunk.minZ() >> 4)) {
            BlockState minerai = minerai(bloc.minerai(), monde.getBlockState(bloc.pos()), bloc.pos().getY());
            if (minerai != null) {
                monde.setBlock(bloc.pos(), minerai, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Le bloc de ce minerai qui remplace cette roche (variante pierre ou ardoise des profondeurs), ou {@code null} si ce n'est pas de la roche. */
    private static @Nullable BlockState minerai(FilonsDeMine.Minerai minerai, BlockState roche, int y) {
        boolean poche = roche.is(Blocks.GRAVEL) || roche.is(Blocks.DIRT);
        if (!poche && !roche.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) && !roche.is(BlockTags.STONE_ORE_REPLACEABLES)) {
            return null;
        }
        boolean ardoise = poche ? y < 0 : roche.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
        Block bloc = switch (minerai) {
            case CHARBON -> ardoise ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
            case FER -> ardoise ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
            case CUIVRE -> ardoise ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE;
            case OR -> ardoise ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE;
            case REDSTONE -> ardoise ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE;
            // Une seule variante : la mine ne le pose que sous y = 0
            case HELIUM3 -> ObjetsMine.MINERAI_HELIUM3;
        };
        return bloc.defaultBlockState();
    }

    /** Le texte d'un panneau : des clés de traduction, que le client affiche dans sa langue. */
    public static SignText texte(PlanDeMine.Panneau panneau) {
        SignText texte = new SignText();
        return switch (panneau.texte()) {
            case NIVEAU -> texte.setMessage(1, Component.translatable("panneau.redrising.niveau", panneau.numero()));
            case COLONIE_DROITE -> texte.setMessage(1, Component.translatable("panneau.redrising.colonie_droite"));
            case COLONIE_GAUCHE -> texte.setMessage(1, Component.translatable("panneau.redrising.colonie_gauche"));
            case PUITS_VERS -> texte.setMessage(1, Component.translatable("panneau.redrising.puits"))
                    .setMessage(2, Component.translatable("panneau.redrising.puits_vers", panneau.numero()));
        };
    }

    /** Un rail dans le sens de sa voie ; un rail de propulsion allumé, avec sa torche de redstone deux blocs dessous. */
    private static void poserRail(WorldGenLevel monde, BoundingBox colonne, PlanDeMine.Rail rail) {
        if (!colonne.isInside(rail.pos())) {
            return;
        }
        monde.setBlock(rail.pos(), PaletteMine.rail(rail.surX(), rail.propulsion()), Block.UPDATE_CLIENTS);
        if (rail.propulsion()) {
            monde.setBlock(rail.pos().below(2), PaletteMine.TORCHE_PROPULSION, Block.UPDATE_CLIENTS);
        }
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
