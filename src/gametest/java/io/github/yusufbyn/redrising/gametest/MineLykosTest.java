package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Boutiques;
import io.github.yusufbyn.redrising.factions.ObjetsRebelles;
import io.github.yusufbyn.redrising.monde.CoffresMine;
import io.github.yusufbyn.redrising.monde.FilonsDeMine;
import io.github.yusufbyn.redrising.monde.MineLykos;
import io.github.yusufbyn.redrising.monde.MonstresDeLaMine;
import io.github.yusufbyn.redrising.monde.ObjetsMine;
import io.github.yusufbyn.redrising.monde.PaletteMine;
import io.github.yusufbyn.redrising.monde.PlanDeMine;
import io.github.yusufbyn.redrising.monde.PlanMars;
import io.github.yusufbyn.redrising.monde.Region;
import io.github.yusufbyn.redrising.monde.RegionsMars;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.ServerLevelData;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * La mine sur de vrais mondes Mars neufs (G7.2) : les épreuves rapides, lancées à chaque suite. Les épreuves longues
 * (emprise entière, 2 minutes de monstres, comptage des minerais, coût des wagonnets) sont dans {@link MineLykosLongTest},
 * lancées seulement avec {@code -PtestsLongs}, quand le code de la mine ou de la génération change.
 *
 * <ul>
 *     <li>Trois graines : la mine loin des forts ; la colonne du puits principal, du haut de la mine au bouchon, sans grotte
 *     ni fluide autour ; le bouchon, entre 3 et 5 blocs sous la surface de la colonne, sous de la roche pleine ; rien du puits
 *     ne voit le ciel ; pas de butte en surface.</li>
 *     <li>Première graine : une mine écartée d'un fort posé exprès sur son chemin ; une salle de forage en capture et le
 *     minerai d'hélium-3 (lumière, pioche en pierre) ; un wagonnet lancé vers le puits s'arrête au butoir, un PNJ qui longe
 *     l'axe n'est pas embarqué ; la descente par le chemin principal et les panneaux du centre.</li>
 * </ul>
 */
public class MineLykosTest implements FabricClientGameTest {
    static final long[] GRAINES = {20_261_028L, 7L, -4_242_424_242L};
    private static final ResourceKey<Structure> MINE = ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "mine"));
    /** 🟡 Plafonds du coût : pose d'un chunk, et temps moyen par tick pendant la génération de l'emprise. */
    private static final double POSE_MOYENNE_MAX_MS = 20;
    private static final double POSE_MAX_MS = 100;
    private static final double TICK_MOYEN_MAX_MS = 50;
    /** 🟡 Plafond du coût de la règle des décorations, par appel (G7.2.2). */
    private static final double DECORATION_MAX_US = 1;

    @Override
    public void runTest(ClientGameTestContext context) {
        for (int i = 0; i < GRAINES.length; i++) {
            long graine = GRAINES[i];
            try (TestSingleplayerContext monde = mondeMars(context, graine)) {
                charger(monde, 2);
                if (i == 0) {
                    fortSurLeChemin(monde);
                    forageEtHelium3(monde, context);
                    voiesEtWagonnets(monde, context);
                    descente(monde, context);
                }
                colonneDuPuits(monde, graine);
                rocheRouge(monde, graine, 2);
                if (i == 0) {
                    coffreQuiSeRemplit(monde);
                }
            }
        }
        RedRising.LOGGER.info("[G7.2.1] OK : mine au bon endroit, puits, voies et repères du centre (épreuves longues : MineLykosLongTest, -PtestsLongs)");
    }

    /**
     * Un monde Mars neuf de cette graine, avec ses structures (les mondes de test de Fabric n'en ont pas), et la distance de
     * la mine au fort le plus proche vérifiée.
     */
    static TestSingleplayerContext mondeMars(ClientGameTestContext context, long graine) {
        TestSingleplayerContext monde = OutilsTest.nouveauMonde(context, context.worldBuilder().adjustSettings(reglages -> {
            reglages.setWorldType(reglages.getNormalPresetList().stream()
                    .filter(type -> type.preset().is(WorldPresets.NORMAL)).findFirst().orElseThrow());
            reglages.setSeed(Long.toString(graine));
            reglages.setGenerateStructures(true);
        }));
        RegionsMars.Tirage tirage = Objects.requireNonNull(monde.getServer().computeOnServer(server -> RegionsMars.dernierTirage()));
        RedRising.LOGGER.info("[G7.2.1] Graine {} : mine à {} blocs du point d'apparition, fort le plus proche à {} chunks",
                graine, tirage.distanceMine(), tirage.fortLePlusProche());
        if (tirage.fortLePlusProche() >= 0 && tirage.fortLePlusProche() < PlanMars.ECART_FORT) {
            throw new AssertionError("Graine " + graine + " : fort à " + tirage.fortLePlusProche() + " chunks de la mine");
        }
        return monde;
    }

    // --- Outils ---

    private static BlockPos centre(MinecraftServer server) {
        return Objects.requireNonNull(RegionsMars.plan(server)).regions().get(Region.MINE);
    }

    private static Structure mine(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValueOrThrow(MINE);
    }

    /** Le plan de la mine, relu dans le morceau du chunk de départ (mêmes hauteurs que la génération). */
    private static PlanDeMine plan(MinecraftServer server) {
        BlockPos centre = centre(server);
        StructureStart depart = Objects.requireNonNull(server.overworld().getChunk(centre.getX() >> 4, centre.getZ() >> 4).getStartForStructure(mine(server)),
                "pas de départ de mine");
        return ((MineLykos.Morceau) depart.getPieces().getFirst()).plan(server.overworld());
    }

    /** Charge (force) les chunks à cette distance (en chunks, en carré) du chunk de la mine, et mesure le temps par tick. */
    static double charger(TestSingleplayerContext monde, int rayon) {
        return charger(monde, rayon, 0, true);
    }

    /** Pareil, autour du chunk de la mine décalé de {@code decalage} chunks (en x et en z) ; {@code garder} : les laisser forcés. */
    static double charger(TestSingleplayerContext monde, int rayon, int decalage, boolean garder) {
        monde.getServer().runOnServer(server -> {
            MineLykos.remettreMesures();
            BlockPos centre = centre(server);
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    server.overworld().setChunkForced((centre.getX() >> 4) + decalage + dx, (centre.getZ() >> 4) + decalage + dz, true);
                }
            }
        });
        double[] total = {0};
        int[] ticks = {0};
        monde.getServer().waitFor(server -> {
            total[0] += server.getAverageTickTimeNanos() / 1e6;
            ticks[0]++;
            BlockPos centre = centre(server);
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (!server.overworld().hasChunk((centre.getX() >> 4) + decalage + dx, (centre.getZ() >> 4) + decalage + dz)) {
                        return false;
                    }
                }
            }
            return true;
        }, 6000);
        if (!garder) {
            monde.getServer().runOnServer(server -> {
                BlockPos centre = centre(server);
                for (int dx = -rayon; dx <= rayon; dx++) {
                    for (int dz = -rayon; dz <= rayon; dz++) {
                        server.overworld().setChunkForced((centre.getX() >> 4) + decalage + dx, (centre.getZ() >> 4) + decalage + dz, false);
                    }
                }
            });
        }
        return total[0] / Math.max(1, ticks[0]);
    }

    // --- Toute l'emprise ---

    /** 🟡 Plafonds (G7.2.5) : temps de tirage du plan, relief lu compris ; poids d'un monde avec l'emprise générée (729 chunks). */
    private static final double TIRAGE_MAX_MS = 500;
    static final double DISQUE_MAX_MO = 40;

    /** Le poids des fichiers de région de ce monde (à lire une fois le monde fermé : tout est alors écrit). */
    static long poidsDuMonde(java.nio.file.Path dossier) {
        try (java.util.stream.Stream<java.nio.file.Path> fichiers = java.nio.file.Files.walk(dossier)) {
            return fichiers.filter(f -> f.toString().endsWith(".mca")).mapToLong(f -> f.toFile().length()).sum();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static void emprise(TestSingleplayerContext monde, long graine) {
        int rayon = PlanMars.EMPRISE_MINE;
        double tickMoyen = charger(monde, rayon);
        double tirage = PlanDeMine.dernierTirageMs();
        String mesures = monde.getServer().computeOnServer(server -> MineLykos.mesures());
        RedRising.LOGGER.info("[G7.2.1] Emprise générée ({} chunks) : pose {} ; {} ms par tick en moyenne", (2 * rayon + 1) * (2 * rayon + 1), mesures,
                String.format(Locale.ROOT, "%.1f", tickMoyen));
        // Coût de la règle des décorations : dans la mine (et un chunk autour), puis ailleurs (11 × 11 chunks loin de la mine)
        double dedansUs = monde.getServer().computeOnServer(server -> MineLykos.DECORATIONS_DEDANS.moyenneUs());
        String dedans = monde.getServer().computeOnServer(server -> MineLykos.DECORATIONS_DEDANS.toString());
        charger(monde, 5, 40, false);
        RedRising.LOGGER.info("[G7.2.5] Tirage du plan {} ms (plafond {})", String.format(Locale.ROOT, "%.0f", tirage), TIRAGE_MAX_MS);
        if (tirage > TIRAGE_MAX_MS) {
            throw new AssertionError("Coût : tirage du plan " + tirage + " ms");
        }
        double horsUs = monde.getServer().computeOnServer(server -> MineLykos.DECORATIONS_HORS.moyenneUs());
        String hors = monde.getServer().computeOnServer(server -> MineLykos.DECORATIONS_HORS.toString() + " (pour 121 chunks)");
        RedRising.LOGGER.info("[G7.2.2] Règle des décorations : dans la mine {} ; hors de la mine {}", dedans, hors);
        if (dedansUs > DECORATION_MAX_US || horsUs > DECORATION_MAX_US) {
            throw new AssertionError("Coût de la règle des décorations : dans la mine " + dedans + ", hors " + hors);
        }
        double moyenne = monde.getServer().computeOnServer(server -> MineLykos.tempsMoyenMs());
        double maximum = monde.getServer().computeOnServer(server -> MineLykos.tempsMaxMs());
        if (moyenne > POSE_MOYENNE_MAX_MS || maximum > POSE_MAX_MS || tickMoyen > TICK_MOYEN_MAX_MS) {
            throw new AssertionError("Coût : pose " + mesures + ", " + tickMoyen + " ms par tick");
        }

        String defauts = monde.getServer().computeOnServer(MineLykosTest::verifierEmprise);
        if (!defauts.isEmpty()) {
            throw new AssertionError("Graine " + graine + " : " + defauts);
        }
        monde.getServer().runOnServer(server -> {
            BlockPos centre = centre(server);
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    server.overworld().setChunkForced((centre.getX() >> 4) + dx, (centre.getZ() >> 4) + dz, false);
                }
            }
        });
    }

    private static String verifierEmprise(MinecraftServer server) {
        ServerLevel niveau = server.overworld();
        Structure mine = mine(server);
        BlockPos centre = centre(server);
        int cx = centre.getX() >> 4;
        int cz = centre.getZ() >> 4;
        int rayon = PlanMars.EMPRISE_MINE;
        List<String> defauts = new ArrayList<>();

        // 1. La référence de la mine dans chaque chunk de l'emprise ; aucune autre structure sur la mine ; aucun générateur
        int sansReference = 0;
        for (int dx = -rayon; dx <= rayon; dx++) {
            for (int dz = -rayon; dz <= rayon; dz++) {
                ChunkAccess chunk = niveau.getChunk(cx + dx, cz + dz);
                if (chunk.getReferencesForStructure(mine).isEmpty()) {
                    sansReference++;
                }
                for (Map.Entry<Structure, LongSet> autre : chunk.getAllReferences().entrySet()) {
                    if (autre.getKey() == mine) {
                        continue;
                    }
                    for (long cle : autre.getValue()) {
                        StructureStart depart = niveau.getChunk(ChunkPos.unpack(cle).x(), ChunkPos.unpack(cle).z(), ChunkStatus.STRUCTURE_STARTS, true)
                                .getStartForStructure(autre.getKey());
                        if (depart != null && depart.isValid() && MineLykos.empiete(depart)) {
                            defauts.add("structure " + autre.getKey().type() + " sur la mine (" + depart.getBoundingBox() + ")");
                        }
                    }
                }
                for (BlockEntity bloc : niveau.getChunk(cx + dx, cz + dz).getBlockEntities().values()) {
                    if (bloc instanceof SpawnerBlockEntity || bloc instanceof TrialSpawnerBlockEntity) {
                        defauts.add("générateur de monstres en " + bloc.getBlockPos().toShortString());
                    }
                }
            }
        }
        if (sansReference > 0) {
            defauts.add(sansReference + " chunk(s) de l'emprise sans la référence de la mine");
        }
        if (niveau.getChunk(cx, cz).getStartForStructure(mine) == null) {
            defauts.add("pas de départ dans le chunk de la mine");
            return String.join(" ; ", defauts);
        }

        // 2. Le vide du plan, tel qu'il est sur les vrais blocs
        PlanDeMine plan = plan(server);
        LongSet interieur = new LongOpenHashSet();
        List<BoundingBox> boites = new ArrayList<>(plan.galeries());
        plan.puits().forEach(p -> boites.add(p.interieur()));
        plan.cavernes().forEach(caverne -> boites.add(caverne.boite()));
        for (PlanDeMine.Salle salle : plan.salles()) {
            boites.add(salle.boite());
            boites.addAll(salle.ouvertures());
        }
        for (BoundingBox boite : boites) {
            for (BlockPos pos : BlockPos.betweenClosed(boite.minX(), boite.minY(), boite.minZ(), boite.maxX(), boite.maxY(), boite.maxZ())) {
                if (plan.interieur(pos.getX(), pos.getY(), pos.getZ())) {
                    interieur.add(pos.asLong());
                }
            }
        }
        LongSet piliers = new LongOpenHashSet();
        int malPoses = 0;
        int fuites = 0;
        int ciel = 0;
        BlockPos.MutableBlockPos voisin = new BlockPos.MutableBlockPos();
        for (long cle : interieur) {
            BlockPos pos = BlockPos.of(cle);
            BlockState etat = niveau.getBlockState(pos);
            if (etat.is(PaletteMine.PILIER_ECHELLE.getBlock()) || etat.is(PaletteMine.BUTOIR.getBlock())) {
                piliers.add(cle);
            } else if (!PaletteMine.poseDansLeVide(etat)) {
                if (malPoses++ < 8) {
                    RedRising.LOGGER.warn("[G7.2.1] Pas vide : {} en {}", etat, pos.toShortString());
                }
            }
            if (pos.getY() >= niveau.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())) {
                ciel++;
            }
            for (Direction direction : Direction.values()) {
                voisin.setWithOffset(pos, direction);
                if (interieur.contains(voisin.asLong())) {
                    continue;
                }
                BlockState mur = niveau.getBlockState(voisin);
                if (mur.isAir() || !mur.getFluidState().isEmpty()) {
                    fuites++;
                }
            }
        }
        if (malPoses > 0) {
            defauts.add(malPoses + " bloc(s) du vide pas vides");
        }
        if (fuites > 0) {
            defauts.add(fuites + " fuite(s) : vide ou fluide contre le vide de la mine");
        }
        if (ciel > 0) {
            defauts.add(ciel + " bloc(s) de la mine qui voient le ciel");
        }

        // 3. Parcours en largeur depuis le bas du puits principal, sur les vrais blocs passables
        PlanDeMine.Puits principal = plan.puitsPrincipal();
        BlockPos depart = new BlockPos(principal.x() + 1, principal.bas(), principal.z() + 1);
        LongSet vus = new LongOpenHashSet();
        Deque<Long> file = new ArrayDeque<>();
        vus.add(depart.asLong());
        file.add(depart.asLong());
        while (!file.isEmpty()) {
            BlockPos pos = BlockPos.of(file.poll());
            for (Direction direction : Direction.values()) {
                long suivant = pos.relative(direction).asLong();
                if (interieur.contains(suivant) && !piliers.contains(suivant) && vus.add(suivant)) {
                    file.add(suivant);
                }
            }
        }
        int attendus = interieur.size() - piliers.size();
        RedRising.LOGGER.info("[G7.2.1] Parcours depuis le bas du puits : {} blocs atteints sur {} ; {} niveaux, {} galeries, {} puits, {} cellules réservées, {} salles",
                vus.size(), attendus, plan.sols().size(), plan.galeries().size(), plan.puits().size(), plan.cellules().size(), plan.salles().size());
        if (vus.size() != attendus) {
            defauts.add("parcours : " + vus.size() + " blocs atteints sur " + attendus);
        }
        return String.join(" ; ", defauts);
    }

    // --- Comptage (G7.2.2) ---

    /** 🟡 Seuils du comptage : longueur des galeries, salles en tout et par niveau. */
    private static final int GALERIES_MIN = 3000;
    private static final int SALLES_MIN = 16;
    private static final int SALLES_PAR_NIVEAU_MIN = 3;
    /** 🟡 Minerais visibles depuis la mine (qui touchent son vide), au moins ; hélium-3 dans tout le volume, au moins. */
    private static final Map<FilonsDeMine.Minerai, Integer> VISIBLES_MIN = Map.of(FilonsDeMine.Minerai.CHARBON, 400, FilonsDeMine.Minerai.FER, 400,
            FilonsDeMine.Minerai.CUIVRE, 150, FilonsDeMine.Minerai.OR, 130, FilonsDeMine.Minerai.DIAMANT, 30, FilonsDeMine.Minerai.REDSTONE, 120, FilonsDeMine.Minerai.HELIUM3, 250);
    private static final int HELIUM3_VOLUME_MIN = 900;
    /** 🟡 Coffres d'outils : nombre, et ce qui n'y est jamais (en plus de la contrebande). */
    private static final int COFFRES_MIN = 36;
    private static final int COFFRES_MAX = 48;
    private static final Set<Item> INTERDITS_DANS_LES_COFFRES = Set.of(Items.IRON_INGOT, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS,
            Items.IRON_BOOTS, Items.DIAMOND, Boutiques.CREDIT, ObjetsMine.HELIUM3, ObjetsMine.MINERAI_HELIUM3_OBJET);
    /** 🟡 Plafonds : fer du volume par rapport à vanilla (sans nos filons), diamants visibles par rapport au squelette sans salles. */
    private static final double FER_VOLUME_MAX = 1.25;
    private static final double DIAMANTS_MAX = 2.0;

    /** Les blocs de chaque minerai (pierre et ardoise des profondeurs). */
    private static Set<Block> blocs(FilonsDeMine.Minerai minerai) {
        return switch (minerai) {
            case CHARBON -> Set.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
            case FER -> Set.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
            case CUIVRE -> Set.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
            case OR -> Set.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE);
            case DIAMANT -> DIAMANTS;
            case REDSTONE -> Set.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
            case HELIUM3 -> Set.of(ObjetsMine.MINERAI_HELIUM3);
        };
    }

    private static final Set<Block> DIAMANTS = Set.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);

    /** Le vide du plan (avec ou sans les salles), en positions. */
    private static LongSet videDuPlan(PlanDeMine plan, boolean salles) {
        LongSet vide = new LongOpenHashSet();
        List<BoundingBox> boites = new ArrayList<>(plan.galeries());
        plan.puits().forEach(p -> boites.add(p.interieur()));
        plan.cavernes().forEach(caverne -> boites.add(caverne.boite()));
        if (salles) {
            for (PlanDeMine.Salle salle : plan.salles()) {
                boites.add(salle.boite());
                boites.addAll(salle.ouvertures());
            }
            }
        for (BoundingBox boite : boites) {
            for (BlockPos pos : BlockPos.betweenClosed(boite.minX(), boite.minY(), boite.minZ(), boite.maxX(), boite.maxY(), boite.maxZ())) {
                boolean dedans = salles ? plan.interieur(pos.getX(), pos.getY(), pos.getZ())
                        : plan.galeries().stream().anyMatch(g -> g.isInside(pos)) || plan.puits().stream().anyMatch(p -> p.interieur().isInside(pos))
                        || plan.caverne().contient(pos.getX(), pos.getY(), pos.getZ());
                if (dedans) {
                    vide.add(pos.asLong());
                }
            }
        }
        return vide;
    }

    /** Les blocs de ces sortes qui touchent ce vide (chacun compté une fois). */
    private static int visibles(ServerLevel niveau, LongSet vide, Set<Block> sortes) {
        LongSet vus = new LongOpenHashSet();
        BlockPos.MutableBlockPos voisin = new BlockPos.MutableBlockPos();
        for (long cle : vide) {
            BlockPos pos = BlockPos.of(cle);
            for (Direction direction : Direction.values()) {
                voisin.setWithOffset(pos, direction);
                if (!vide.contains(voisin.asLong()) && sortes.contains(niveau.getBlockState(voisin).getBlock())) {
                    vus.add(voisin.asLong());
                }
            }
        }
        return vus.size();
    }

    /** Longueur des galeries, salles par niveau et par forme, sur le plan de ce monde (vraies hauteurs du terrain). */
    static void comptage(TestSingleplayerContext monde, long graine) {
        String defauts = monde.getServer().computeOnServer(server -> {
            PlanDeMine plan = plan(server);
            List<String> liste = new ArrayList<>();
            long longueur = plan.galeries().stream().mapToLong(galerie -> Math.max(galerie.getXSpan(), galerie.getZSpan())).sum();
            if (longueur < GALERIES_MIN) {
                liste.add(longueur + " blocs de galeries");
            }
            if (plan.salles().size() < SALLES_MIN) {
                liste.add(plan.salles().size() + " salles");
            }
            StringBuilder parNiveau = new StringBuilder();
            for (int niveau = 0; niveau < plan.sols().size(); niveau++) {
                int n = niveau;
                Map<PlanDeMine.TypeSalle, Long> formes = new java.util.EnumMap<>(PlanDeMine.TypeSalle.class);
                plan.salles().stream().filter(salle -> salle.niveau() == n).forEach(salle -> formes.merge(salle.type(), 1L, Long::sum));
                long total = formes.values().stream().mapToLong(Long::longValue).sum();
                parNiveau.append(" niveau ").append(niveau + 1).append(" : ").append(formes);
                if (total < SALLES_PAR_NIVEAU_MIN) {
                    liste.add(total + " salles au niveau " + (niveau + 1));
                }
            }
            RedRising.LOGGER.info("[G7.2.2] Graine {} : {} blocs de galeries, {} salles ;{}", graine, longueur, plan.salles().size(), parNiveau);

            // Minerais : visibles depuis la mine, dans tout son volume, et ceux de nos filons
            ServerLevel niveau = server.overworld();
            LongSet vide = videDuPlan(plan, true);
            Map<FilonsDeMine.Minerai, Integer> nos = new java.util.EnumMap<>(FilonsDeMine.Minerai.class);
            Map<Block, Integer> manques = new java.util.HashMap<>();
            for (FilonsDeMine.Bloc bloc : plan.filons()) {
                Block reel = niveau.getBlockState(bloc.pos()).getBlock();
                if (blocs(bloc.minerai()).contains(reel)) {
                    nos.merge(bloc.minerai(), 1, Integer::sum);
                } else {
                    manques.merge(reel, 1, Integer::sum);
                }
            }
            RedRising.LOGGER.info("[G7.2.2] Graine {} : blocs de filon pas posés (pas de roche à cet endroit) : {}", graine, manques);
            BlockPos centre = plan.centre();
            int r = PlanMars.EMPRISE_MINE;
            int cx = centre.getX() >> 4;
            int cz = centre.getZ() >> 4;
            Map<Block, Integer> volume = new java.util.HashMap<>();
            BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
            for (int x = (cx - r) << 4; x <= ((cx + r) << 4) + 15; x++) {
                for (int z = (cz - r) << 4; z <= ((cz + r) << 4) + 15; z++) {
                    // Les tranches des niveaux (FilonsDeMine.dansLaTranche) : de 13 blocs sous le dernier sol au haut des boîtes
                    for (int y = plan.sols().getLast() - 13; y <= plan.hautDesBoites(); y++) {
                        volume.merge(niveau.getBlockState(q.set(x, y, z)).getBlock(), 1, Integer::sum);
                    }
                }
            }
            StringBuilder minerais = new StringBuilder();
            for (FilonsDeMine.Minerai minerai : FilonsDeMine.Minerai.values()) {
                int vus = visibles(niveau, vide, blocs(minerai));
                int total = blocs(minerai).stream().mapToInt(bloc -> volume.getOrDefault(bloc, 0)).sum();
                minerais.append(" ").append(minerai).append(" ").append(vus).append(" visibles, ").append(total).append(" dans le volume dont ")
                        .append(nos.getOrDefault(minerai, 0)).append(" des filons ;");
                if (vus < VISIBLES_MIN.get(minerai)) {
                    liste.add(minerai + " : " + vus + " visibles");
                }
                if (minerai == FilonsDeMine.Minerai.HELIUM3 && total < HELIUM3_VOLUME_MIN) {
                    liste.add("hélium-3 : " + total + " dans le volume");
                }
                if (minerai == FilonsDeMine.Minerai.FER) {
                    double rapport = (double) total / Math.max(1, total - nos.getOrDefault(minerai, 0));
                    minerais.append(String.format(Locale.ROOT, " (×%.2f vanilla)", rapport));
                    if (rapport > FER_VOLUME_MAX) {
                        liste.add(String.format(Locale.ROOT, "fer du volume ×%.2f", rapport));
                    }
                }
            }
            // Diamants : jusqu'à 2 fois le vanilla, dans le volume et en visibles
            int diamants = visibles(niveau, vide, DIAMANTS);
            int diamantsVolume = DIAMANTS.stream().mapToInt(bloc -> volume.getOrDefault(bloc, 0)).sum();
            int nosDiamants = nos.getOrDefault(FilonsDeMine.Minerai.DIAMANT, 0);
            int nosVisibles = 0;
            for (FilonsDeMine.Bloc bloc : plan.filons()) {
                if (bloc.minerai() == FilonsDeMine.Minerai.DIAMANT && DIAMANTS.contains(niveau.getBlockState(bloc.pos()).getBlock())) {
                    boolean touche = false;
                    for (Direction d : Direction.values()) {
                        touche |= vide.contains(bloc.pos().relative(d).asLong());
                    }
                    nosVisibles += touche ? 1 : 0;
                }
            }
            double diamantsVolumeRapport = (double) diamantsVolume / Math.max(1, diamantsVolume - nosDiamants);
            double diamantsVisiblesRapport = (double) diamants / Math.max(1, diamants - nosVisibles);
            minerais.append(String.format(Locale.ROOT, " diamants ×%.2f dans le volume, ×%.2f visibles", diamantsVolumeRapport, diamantsVisiblesRapport));
            if (diamantsVolumeRapport > DIAMANTS_MAX || diamantsVisiblesRapport > DIAMANTS_MAX) {
                liste.add(String.format(Locale.ROOT, "diamants ×%.2f dans le volume, ×%.2f visibles", diamantsVolumeRapport, diamantsVisiblesRapport));
            }
            RedRising.LOGGER.info("[G7.2.2] Graine {} : filons{}", graine, minerais);

            // Coffres d'outils : posés, sans camp, rien d'interdit dedans, 4 à 6 pioches en fer dans toute la mine
            int coffres = 0;
            int rares = 0;
            Map<Item, Integer> contenu = new java.util.TreeMap<>(java.util.Comparator.comparing(item -> item.toString()));
            for (PlanDeMine.Coffre coffre : plan.coffres()) {
                if (!(niveau.getBlockEntity(coffre.pos()) instanceof ChestBlockEntity bloc)) {
                    liste.add("pas de coffre en " + coffre.pos().toShortString());
                    continue;
                }
                coffres++;
                rares += coffre.rare() ? 1 : 0;
                if (ActionsFaction.campDuCoffre(bloc) != null) {
                    liste.add("coffre d'un camp en " + coffre.pos().toShortString());
                }
                bloc.unpackLootTable(null);
                for (int i = 0; i < bloc.getContainerSize(); i++) {
                    ItemStack pile = bloc.getItem(i);
                    if (pile.isEmpty()) {
                        continue;
                    }
                    contenu.merge(pile.getItem(), pile.getCount(), Integer::sum);
                    if (INTERDITS_DANS_LES_COFFRES.contains(pile.getItem()) || pile.is(ObjetsRebelles.CONTREBANDE)) {
                        liste.add(pile + " dans le coffre en " + coffre.pos().toShortString());
                    }
                }
            }
            int pioches = contenu.getOrDefault(Items.IRON_PICKAXE, 0);
            if (coffres < COFFRES_MIN || coffres > COFFRES_MAX || pioches < PlanDeMine.COFFRES_RARES_MIN || pioches > PlanDeMine.COFFRES_RARES) {
                liste.add(coffres + " coffres, " + pioches + " pioches en fer");
            }
            RedRising.LOGGER.info("[G7.2.2] Graine {} : {} coffres d'outils ({} rares), contenu {}", graine, coffres, rares, contenu);
            return String.join(" ; ", liste);
        });
        if (!defauts.isEmpty()) {
            throw new AssertionError("Graine " + graine + " : " + defauts);
        }
    }

    // --- Salle de forage et hélium-3 ---

    /**
     * Le joueur dans une salle de forage (capture pour l'œil), puis le minerai d'hélium-3 : il éclaire (3), une pioche en
     * pierre en tire de l'hélium-3, une pioche en bois rien.
     */
    private static void forageEtHelium3(TestSingleplayerContext monde, ClientGameTestContext context) {
        PlanDeMine.Salle forage = monde.getServer().computeOnServer(server -> plan(server).salles().stream()
                .filter(salle -> salle.type() == PlanDeMine.TypeSalle.FORAGE).findFirst().orElseThrow());
        int sol = forage.boite().minY();
        monde.getServer().runCommand("gamemode survival @a[limit=1]");
        monde.getServer().runCommand("effect give @a[limit=1] minecraft:night_vision infinite 0 true");
        monde.getServer().runCommand("effect give @a[limit=1] minecraft:resistance infinite 4 true");
        monde.getServer().runCommand("tp @a[limit=1] " + (forage.x() + 0.5) + " " + sol + " " + (forage.z() + 5.5) + " 180 10");
        context.waitTicks(60);
        monde.getConnection().waitForChunksRender();
        OutilsTest.garderCapture(context.takeScreenshot("mine-forage"));

        BlockPos pos = new BlockPos(forage.x() + 3, sol, forage.z() + 3);
        String defauts = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            ServerPlayer joueur = OutilsTest.joueur(server);
            List<String> liste = new ArrayList<>();
            for (boolean pierre : new boolean[]{true, false}) {
                niveau.setBlockAndUpdate(pos, ObjetsMine.MINERAI_HELIUM3.defaultBlockState());
                if (niveau.getBlockState(pos).getLightEmission() != 3) {
                    liste.add("lumière du minerai " + niveau.getBlockState(pos).getLightEmission());
                }
                niveau.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).forEach(ItemEntity::discard);
                joueur.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(pierre ? Items.STONE_PICKAXE : Items.WOODEN_PICKAXE));
                joueur.gameMode.destroyBlock(pos);
                int helium = niveau.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
                        .filter(objet -> objet.getItem().is(ObjetsMine.HELIUM3)).mapToInt(objet -> objet.getItem().getCount()).sum();
                if (!niveau.getBlockState(pos).isAir()) {
                    liste.add("minerai pas cassé");
                }
                if (pierre ? helium < 1 : helium > 0) {
                    liste.add((pierre ? "pioche en pierre : " : "pioche en bois : ") + helium + " hélium-3");
                }
            }
            return String.join(" ; ", liste);
        });
        if (!defauts.isEmpty()) {
            throw new AssertionError("Hélium-3 : " + defauts);
        }
        RedRising.LOGGER.info("[G7.2.2] Salle de forage en {} {} (capture mine-forage) ; minerai d'hélium-3 : lumière 3, pioche en pierre oui, en bois non",
                forage.x(), forage.z());
    }

    // --- Réseau éclairé et monstres (G7.2.3) ---

    /** 🟡 Durée de l'épreuve des monstres. */
    private static final int DUREE_MONSTRES = 2400;

    /**
     * Lumière d'au moins 1 sur tout le sol du réseau éclairé ; puis 2 minutes dans une galerie sombre, les règles d'apparition
     * réactivées (les mondes de test de Fabric les coupent) : aucune apparition sur le réseau éclairé (positions relevées par
     * la règle), {@value MonstresDeLaMine#PLAFOND} monstres au plus dans la mine à {@value MonstresDeLaMine#RAYON} blocs du
     * joueur, ni creeper ni slime dans la mine.
     */
    static void reseauEtMonstres(TestSingleplayerContext monde, ClientGameTestContext context) {
        // Toute l'emprise chargée : un chunk déchargé se lit à une lumière de 0
        charger(monde, PlanMars.EMPRISE_MINE);
        String sombres = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            List<String> liste = new ArrayList<>();
            List<BlockPos> sol = plan(server).reseauEclaire();
            for (BlockPos pos : sol) {
                // On s'oriente sans torche sur le réseau éclairé (G7.2.5)
                if (niveau.getBrightness(LightLayer.BLOCK, pos) < LUMIERE_AXES_MIN && liste.size() < 10) {
                    liste.add(pos.toShortString());
                }
            }
            int minimum = sol.stream().mapToInt(pos -> niveau.getBrightness(LightLayer.BLOCK, pos)).min().orElse(0);
            long sombres2 = sol.stream().filter(pos -> niveau.getBrightness(LightLayer.BLOCK, pos) < LUMIERE_AXES_MIN).count();
            RedRising.LOGGER.info("[G7.2.3] Réseau éclairé (centre {}) : {} lanternes, {} blocs de sol, lumière minimale {}, {} sous {}", plan(server).centre().toShortString(),
                    plan(server).lanternes().size(), sol.size(), minimum, sombres2, LUMIERE_AXES_MIN);
            return String.join(" ; ", liste);
        });
        if (!sombres.isEmpty()) {
            throw new AssertionError("Sol du réseau éclairé dans le noir : " + sombres);
        }

        libererEmprise(monde);
        monstresEnCaverne(monde, context);

        // Une galerie sombre du niveau 2, loin du puits
        int[] lieu = monde.getServer().computeOnServer(server -> {
            PlanDeMine plan = plan(server);
            for (int i = 0; i < plan.galeries().size(); i++) {
                BoundingBox g = plan.galeries().get(i);
                BlockPos c = g.getCenter();
                if (plan.niveauxGaleries().get(i) == 1 && !plan.axes().contains(g) && Math.hypot(c.getX() - plan.centre().getX(), c.getZ() - plan.centre().getZ()) > 70) {
                    return new int[]{c.getX(), g.minY(), c.getZ()};
                }
            }
            throw new AssertionError("Pas de galerie sombre au niveau 2");
        });
        monde.getServer().runCommand("kill @e[type=!player,type=!minecart]");
        monde.getServer().runCommand("tp @a[limit=1] " + (lieu[0] + 0.5) + " " + lieu[1] + " " + (lieu[2] + 0.5));
        monde.getServer().runCommand("effect give @a[limit=1] minecraft:regeneration infinite 2 true");
        monde.getServer().runOnServer(server -> MonstresDeLaMine.remettre());
        monde.getServer().runCommand("difficulty normal");
        monde.getServer().runCommand("gamerule spawn_mobs true");
        monde.getServer().runCommand("gamerule spawn_monsters true");
        context.waitTicks(DUREE_MONSTRES);
        String defauts = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            PlanDeMine plan = plan(server);
            ServerPlayer joueur = OutilsTest.joueur(server);
            List<String> liste = new ArrayList<>();
            java.util.Set<BlockPos> reseau = new java.util.HashSet<>(plan.reseauEclaire());
            List<BlockPos> apparitions = MonstresDeLaMine.apparitions();
            long surLeReseau = apparitions.stream().filter(reseau::contains).count();
            if (surLeReseau > 0) {
                liste.add(surLeReseau + " apparition(s) sur le réseau éclairé");
            }
            int dansLaMine = MonstresDeLaMine.compter(niveau, plan, joueur);
            if (dansLaMine > MonstresDeLaMine.PLAFOND) {
                liste.add(dansLaMine + " monstres dans la mine autour du joueur");
            }
            Map<String, Integer> sortes = new java.util.TreeMap<>();
            for (net.minecraft.world.entity.Mob mob : niveau.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(joueur.blockPosition()).inflate(160),
                    mob -> mob instanceof net.minecraft.world.entity.monster.Enemy || mob.getType() == net.minecraft.world.entity.EntityTypes.SULFUR_CUBE)) {
                BlockPos p = mob.blockPosition();
                if (plan.interieur(p.getX(), p.getY(), p.getZ())) {
                    sortes.merge(mob.getType().toShortString(), 1, Integer::sum);
                    if (mob.getType() == net.minecraft.world.entity.EntityTypes.CREEPER || mob.getType() == net.minecraft.world.entity.EntityTypes.SLIME
                            || mob.getType() == net.minecraft.world.entity.EntityTypes.SULFUR_CUBE) {
                        liste.add(mob.getType().toShortString() + " dans la mine en " + p.toShortString());
                    }
                }
            }
            RedRising.LOGGER.info("[G7.2.3] Après {} s dans le noir : {} apparitions acceptées dans la mine, aucune sur le réseau éclairé ({}) ; {} monstres dans la mine à {} blocs (plafond {}) : {}",
                    DUREE_MONSTRES / 20, apparitions.size(), surLeReseau, dansLaMine, MonstresDeLaMine.RAYON, MonstresDeLaMine.PLAFOND, sortes);
            return String.join(" ; ", liste);
        });
        double tick = monde.getServer().computeOnServer(server -> server.getAverageTickTimeNanos() / 1e6);
        RedRising.LOGGER.info("[G7.2.3] Temps par tick avec les monstres : {} ms", String.format(Locale.ROOT, "%.2f", tick));
        monde.getServer().runCommand("gamerule spawn_mobs false");
        monde.getServer().runCommand("gamerule spawn_monsters false");
        monde.getServer().runCommand("difficulty peaceful");
        monde.getServer().runCommand("kill @e[type=!player,type=!minecart]");
        if (!defauts.isEmpty()) {
            throw new AssertionError("Monstres de la mine : " + defauts);
        }
    }

    // --- Voies et wagonnets (G7.2.3) ---

    /** 🟡 Vitesse d'arrivée au butoir, au moins (un wagonnet sur rails va à 0,4 bloc par tick au plus). */
    private static final double VITESSE_AU_BUTOIR_MIN = 0.25;

    /**
     * Un wagonnet lancé à pleine vitesse vers le puits s'arrête au butoir ; un PNJ de Couleur qui longe l'axe devant une voie
     * de garage n'est embarqué par aucun wagonnet.
     */
    static void voiesEtWagonnets(TestSingleplayerContext monde, ClientGameTestContext context) {
        // Un wagonnet lancé à pleine vitesse vers le puits, sur une voie du niveau 1
        PlanDeMine.Voie voie = monde.getServer().computeOnServer(server -> plan(server).voies().stream().filter(v -> v.niveau() == 0)
                .max(java.util.Comparator.comparingInt(v -> v.rails().size())).orElseThrow());
        // Lancé juste avant le rail de propulsion le plus proche du puits : il arrive au butoir à pleine vitesse
        BlockPos lancement = voie.rails().get(Math.min(10, voie.rails().size() - 3)).pos();
        java.util.UUID lance = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            Minecart wagonnet = net.minecraft.world.entity.EntityTypes.MINECART.create(niveau, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            wagonnet.setPos(lancement.getX() + 0.5, lancement.getY() + 0.0625, lancement.getZ() + 0.5);
            Direction versLePuits = voie.sens().getOpposite();
            wagonnet.setDeltaMovement(versLePuits.getStepX() * 0.4, 0, versLePuits.getStepZ() * 0.4);
            niveau.addFreshEntity(wagonnet);
            return wagonnet.getUUID();
        });
        monde.getServer().runCommand("tp @a[limit=1] " + (lancement.getX() + 0.5) + " " + lancement.getY() + " " + (lancement.getZ() + 0.5));
        // La vitesse la plus haute à 2 blocs ou moins du butoir
        double[] vitesseAuButoir = {0};
        int[] ticksLancement = {0};
        monde.getServer().waitFor(server -> {
            Minecart wagonnet = (Minecart) server.overworld().getEntity(lance);
            if (wagonnet != null && wagonnet.blockPosition().distManhattan(voie.butoirPuits()) <= 2) {
                vitesseAuButoir[0] = Math.max(vitesseAuButoir[0], wagonnet.getDeltaMovement().horizontalDistance());
            }
            return ++ticksLancement[0] >= 160;
        }, 200);
        String arret = monde.getServer().computeOnServer(server -> {
            Minecart wagonnet = (Minecart) server.overworld().getEntity(lance);
            if (wagonnet == null) {
                return "wagonnet lancé disparu";
            }
            BlockPos butoir = voie.butoirPuits();
            // Au-delà du butoir côté voie : (position - butoir) · sens ≥ 0,5
            double avance = (wagonnet.getX() - (butoir.getX() + 0.5)) * voie.sens().getStepX() + (wagonnet.getZ() - (butoir.getZ() + 0.5)) * voie.sens().getStepZ();
            RedRising.LOGGER.info("[G7.2.3] Wagonnet lancé à 0,4 bloc par tick vers le puits : {} bloc par tick en arrivant au butoir, arrêté à {} bloc(s) de lui, en y {} (voie en y {})",
                    String.format(Locale.ROOT, "%.2f", vitesseAuButoir[0]), String.format(Locale.ROOT, "%.2f", avance), String.format(Locale.ROOT, "%.2f", wagonnet.getY()),
                    butoir.getY());
            wagonnet.discard();
            if (vitesseAuButoir[0] < VITESSE_AU_BUTOIR_MIN) {
                return "le wagonnet n'est pas arrivé au butoir à pleine vitesse (" + vitesseAuButoir[0] + ")";
            }
            return avance >= 0.5 && wagonnet.getY() >= butoir.getY() - 0.1 ? "" : "le wagonnet a passé le butoir (" + avance + ", y " + wagonnet.getY() + ")";
        });
        if (!arret.isEmpty()) {
            throw new AssertionError(arret);
        }

        // Un wagonnet garé, poussé vers la courbe, ressort sur la voie (il ne reste plus coincé)
        PlanDeMine.Garage garage = monde.getServer().computeOnServer(server -> plan(server).garages().stream()
                .max(java.util.Comparator.comparingInt(g -> g.wagonnet().distManhattan(plan(server).centre().atY(g.wagonnet().getY())))).orElseThrow());
        PlanDeMine.Voie voieDuGarage = monde.getServer().computeOnServer(server -> plan(server).voies().stream()
                .filter(v -> v.rails().stream().anyMatch(rail -> rail.pos().distManhattan(garage.wagonnet()) <= 8)).findFirst().orElseThrow());
        BlockPos virage = garage.rails().get(1).pos();
        // Le joueur à l'écart, de l'autre côté de la voie : il ne gêne pas le wagonnet
        BlockPos cote = voieDuGarage.rails().getLast().pos().offset(2 * Integer.signum(voieDuGarage.rails().getLast().pos().getX() - virage.getX()), 0,
                2 * Integer.signum(voieDuGarage.rails().getLast().pos().getZ() - virage.getZ())).relative(voieDuGarage.sens().getOpposite(), 4);
        monde.getServer().runCommand("tp @a[limit=1] " + (cote.getX() + 0.5) + " " + cote.getY() + " " + (cote.getZ() + 0.5));
        monde.getConnection().waitForChunksRender();
        monde.getServer().waitFor(server -> server.overworld().areEntitiesLoaded(ChunkPos.pack(garage.wagonnet().getX() >> 4, garage.wagonnet().getZ() >> 4)), 400);
        java.util.UUID pousse = monde.getServer().computeOnServer(server -> {
            Minecart wagonnet = server.overworld().getEntitiesOfClass(Minecart.class, new AABB(garage.wagonnet()).inflate(8)).getFirst();
            int dx = Integer.signum(virage.getX() - wagonnet.getBlockX());
            int dz = Integer.signum(virage.getZ() - wagonnet.getBlockZ());
            wagonnet.setDeltaMovement(dx * 0.4, 0, dz * 0.4);
            return wagonnet.getUUID();
        });
        boolean[] surLaVoieDeRetour = {false};
        int[] ticksPousse = {0};
        monde.getServer().waitFor(server -> {
            Minecart wagonnet = (Minecart) server.overworld().getEntity(pousse);
            surLaVoieDeRetour[0] |= wagonnet != null && voieDuGarage.rails().stream().anyMatch(rail -> rail.pos().equals(wagonnet.blockPosition()));
            return surLaVoieDeRetour[0] || ++ticksPousse[0] > 200;
        }, 300);
        if (!surLaVoieDeRetour[0]) {
            throw new AssertionError("Un wagonnet garé, poussé vers la courbe, ne revient pas sur la voie (garage du wagonnet en " + garage.wagonnet().toShortString() + ")");
        }
        RedRising.LOGGER.info("[G7.2.5] Wagonnet garé poussé : de retour sur la voie en {} ticks", ticksPousse[0]);
        monde.getServer().runOnServer(server -> Objects.requireNonNull(server.overworld().getEntity(pousse)).discard());

        // Un PNJ de Couleur longe l'axe devant une voie de garage (à côté d'un wagonnet garé)
        Direction sens = voieDuGarage.sens();
        BlockPos surLaVoie = voieDuGarage.rails().stream().min(java.util.Comparator.comparingInt(rail -> rail.pos().distManhattan(garage.wagonnet()))).orElseThrow().pos();
        // Le passage entre la voie et le garage, à un bloc de la ligne du milieu, de ce côté
        int dx = Integer.signum(garage.wagonnet().getX() - surLaVoie.getX());
        int dz = Integer.signum(garage.wagonnet().getZ() - surLaVoie.getZ());
        BlockPos depart = surLaVoie.offset(dx, 0, dz).relative(sens.getOpposite(), 12);
        BlockPos arrivee = surLaVoie.offset(dx, 0, dz);
        monde.getServer().runCommand("tp @a[limit=1] " + (arrivee.getX() + 0.5) + " " + arrivee.getY() + " " + (arrivee.getZ() + 0.5));
        java.util.UUID pnj = monde.getServer().computeOnServer(server -> Objects.requireNonNull(
                io.github.yusufbyn.redrising.pnj.PnjCouleur.creer(server.overworld(), net.minecraft.world.phys.Vec3.atBottomCenterOf(depart), 0,
                        io.github.yusufbyn.redrising.couleur.Couleur.ROUGE)).getUUID());
        boolean[] embarque = {false};
        int[] ticks = {0};
        monde.getServer().waitFor(server -> {
            net.minecraft.world.entity.npc.villager.Villager villageois = (net.minecraft.world.entity.npc.villager.Villager) server.overworld().getEntity(pnj);
            if (villageois == null) {
                return true;
            }
            embarque[0] |= villageois.isPassenger();
            if (ticks[0]++ % 10 == 0) {
                villageois.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                        new net.minecraft.world.entity.ai.memory.WalkTarget(arrivee, 0.6f, 0));
            }
            return embarque[0] || villageois.blockPosition().distManhattan(arrivee) <= 2 || ticks[0] > 600;
        }, 800);
        String pnjDefaut = monde.getServer().computeOnServer(server -> {
            net.minecraft.world.entity.npc.villager.Villager villageois = (net.minecraft.world.entity.npc.villager.Villager) server.overworld().getEntity(pnj);
            String bilan = villageois == null ? "PNJ disparu" : embarque[0] ? "PNJ embarqué dans un wagonnet"
                    : villageois.blockPosition().distManhattan(arrivee) > 2 ? "PNJ arrêté en " + villageois.blockPosition().toShortString() : "";
            RedRising.LOGGER.info("[G7.2.3] PNJ Rouge le long de l'axe, devant la voie de garage en {} : {} ticks, embarqué : {}", garage.wagonnet().toShortString(),
                    ticks[0], embarque[0]);
            if (villageois != null) {
                villageois.discard();
            }
            return bilan;
        });
        if (!pnjDefaut.isEmpty()) {
            throw new AssertionError(pnjDefaut);
        }
    }

    /**
     * Toute l'emprise : les wagonnets posés à la génération, les butoirs et les rails ; puis ce
     * que coûtent les wagonnets et les chunks forcés (temps par tick moyen sur {@value #TICKS_MESURE} ticks, emprise forcée avec
     * puis sans les wagonnets, puis emprise libérée).
     */
    static void wagonnetsDeLEmprise(TestSingleplayerContext monde, ClientGameTestContext context) {
        // Toute l'emprise chargée, et ses entités (elles arrivent après les chunks)
        charger(monde, PlanMars.EMPRISE_MINE);
        monde.getServer().waitFor(server -> {
            BlockPos centre = plan(server).centre();
            int r = PlanMars.EMPRISE_MINE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!server.overworld().areEntitiesLoaded(ChunkPos.pack((centre.getX() >> 4) + dx, (centre.getZ() >> 4) + dz))) {
                        return false;
                    }
                }
            }
            return true;
        }, 2000);
        String defauts = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            PlanDeMine plan = plan(server);
            List<String> liste = new ArrayList<>();
            BlockPos centre = plan.centre();
            int r = PlanMars.EMPRISE_MINE * 16 + 16;
            List<Minecart> wagonnets = niveau.getEntitiesOfClass(Minecart.class, new AABB(centre).inflate(r, 400, r));
            if (wagonnets.size() != plan.garages().size() || plan.garages().size() < 2 * plan.voies().size() - 2) {
                liste.add(wagonnets.size() + " wagonnets dans le monde, " + plan.garages().size() + " voies de garage");
            }
            int rails = 0;
            int propulsion = 0;
            for (PlanDeMine.Voie voie : plan.voies()) {
                for (BlockPos butoir : List.of(voie.butoirPuits(), voie.butoirBout())) {
                    if (!niveau.getBlockState(butoir).is(PaletteMine.BUTOIR.getBlock())) {
                        liste.add("pas de butoir en " + butoir.toShortString());
                    }
                }
                for (PlanDeMine.Rail rail : voie.rails()) {
                    BlockState etat = niveau.getBlockState(rail.pos());
                    if (!etat.equals(PaletteMine.rail(rail.forme(), rail.propulsion()))) {
                        liste.add("rail manquant en " + rail.pos().toShortString() + " (" + etat + ")");
                    }
                    rails++;
                    propulsion += rail.propulsion() ? 1 : 0;
                }
            }
            RedRising.LOGGER.info("[G7.2.3] Voies : {} voies, {} rails dont {} de propulsion, {} wagonnets sur {} voies de garage", plan.voies().size(), rails, propulsion,
                    wagonnets.size(), plan.garages().size());
            return String.join(" ; ", liste.subList(0, Math.min(10, liste.size())));
        });
        if (!defauts.isEmpty()) {
            throw new AssertionError("Voies : " + defauts);
        }

        int nombre = monde.getServer().computeOnServer(server -> plan(server).garages().size());
        double avec = tickMoyen(monde, TICKS_MESURE);
        monde.getServer().runCommand("kill @e[type=minecart]");
        context.waitTicks(40);
        double sans = tickMoyen(monde, TICKS_MESURE);
        monde.getServer().runOnServer(server -> {
            BlockPos centre = plan(server).centre();
            int r = PlanMars.EMPRISE_MINE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    server.overworld().setChunkForced((centre.getX() >> 4) + dx, (centre.getZ() >> 4) + dz, false);
                }
            }
        });
        context.waitTicks(200);
        double libre = tickMoyen(monde, TICKS_MESURE);
        RedRising.LOGGER.info("[G7.2.3] Temps par tick ({} ticks) : emprise forcée (729 chunks) avec les {} wagonnets {} ms, sans eux {} ms, soit {} µs par wagonnet ; emprise libérée {} ms",
                TICKS_MESURE, nombre, String.format(Locale.ROOT, "%.2f", avec), String.format(Locale.ROOT, "%.2f", sans),
                String.format(Locale.ROOT, "%.1f", (avec - sans) * 1000 / nombre), String.format(Locale.ROOT, "%.2f", libre));
    }

    private static void libererEmprise(TestSingleplayerContext monde) {
        monde.getServer().runOnServer(server -> {
            BlockPos centre = centre(server);
            int r = PlanMars.EMPRISE_MINE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    server.overworld().setChunkForced((centre.getX() >> 4) + dx, (centre.getZ() >> 4) + dz, false);
                }
            }
        });
    }

    /** Des captures pour juger la forme à l'œil : un axe, une grande caverne, la colonie. */
    static void captures(TestSingleplayerContext monde, ClientGameTestContext context) {
        PlanDeMine plan = monde.getServer().computeOnServer(MineLykosTest::plan);
        PlanDeMine.Voie voie = plan.voies().stream().max(java.util.Comparator.comparingInt(v -> v.rails().size())).orElseThrow();
        BlockPos axe = voie.rails().get(voie.rails().size() / 2).pos();
        PlanDeMine.Caverne grande = plan.cavernes().stream().skip(1).max(java.util.Comparator.comparingInt(c -> c.demiX() * c.demiZ())).orElseThrow();
        PlanDeMine.Caverne colonie = plan.caverne();
        Object[][] vues = {
                {"mine-axe", axe, voie.sens().toYRot(), -5f},
                {"mine-caverne", new BlockPos(grande.x() - grande.demiX() + 4, grande.sol() + 2, grande.z()), -90f, -10f},
                {"mine-colonie", plan.colonie().apparition(), Direction.getApproximateNearest(colonie.x() - plan.colonie().apparition().getX(), 0, colonie.z() - plan.colonie().apparition().getZ()).toYRot(), -10f}};
        monde.getServer().runCommand("gamemode spectator @a[limit=1]");
        context.getInput().pressKey(options -> options.keyToggleGui);
        // Le test tourne à 5 chunks de distance de vue : le fond d'une grande caverne ne s'afficherait pas
        int distance = context.computeOnClient(client -> client.options.renderDistance().get());
        context.runOnClient(client -> client.options.renderDistance().set(12));
        // Les cavernes sont sombres : pour juger leur forme à l'œil, en vision de nuit (un Rouge l'aura en G7.6)
        monde.getServer().runCommand("effect give @a[limit=1] minecraft:night_vision infinite 0 true");
        for (Object[] vue : vues) {
            BlockPos pos = (BlockPos) vue[1];
            monde.getServer().runCommand("tp @a[limit=1] " + (pos.getX() + 0.5) + " " + (pos.getY() + 0.2) + " " + (pos.getZ() + 0.5) + " " + vue[2] + " " + vue[3]);
            // Pas waitForChunksRender : à cette distance de vue, il ne rend pas la main dans le temps donné
            context.waitTicks(240);
            OutilsTest.garderCapture(context.takeScreenshot((String) vue[0]));
        }
        context.runOnClient(client -> client.options.renderDistance().set(distance));
        monde.getServer().runCommand("effect clear @a[limit=1] minecraft:night_vision");
        context.getInput().pressKey(options -> options.keyToggleGui);
        monde.getServer().runCommand("gamemode creative @a[limit=1]");
    }

    /** 🟡 Lumière de bloc au sol du réseau éclairé : assez pour s'orienter sans torche. */
    private static final int LUMIERE_AXES_MIN = 6;

    /** 🟡 Durée de chaque mesure du temps par tick. */
    static final int TICKS_MESURE = 600;

    /** Le temps par tick moyen sur ce nombre de ticks, relevé tick par tick (pas la moyenne glissante de Minecraft). */
    /**
     * Le plafond de monstres dans une grande caverne sombre (G7.2.5) : le joueur debout, sans armure, au milieu de la plus
     * grande caverne du niveau du bas, pendant {@value #DUREE_MONSTRES} ticks. Combien de monstres l'attaquent à la fois, et
     * les dégâts reçus (sa vie est remise à chaque tick, on compte ce qu'il perd).
     */
    static void monstresEnCaverne(TestSingleplayerContext monde, ClientGameTestContext context) {
        BlockPos lieu = monde.getServer().computeOnServer(server -> {
            PlanDeMine plan = plan(server);
            PlanDeMine.Caverne caverne = plan.cavernes().stream().filter(c -> c.sol() == plan.sols().getLast())
                    .max(java.util.Comparator.comparingInt(c -> c.demiX() * c.demiZ())).orElseThrow();
            RedRising.LOGGER.info("[G7.2.5] Caverne de la mesure : {} × {}, {} de haut", 2 * caverne.demiX(), 2 * caverne.demiZ(), caverne.hauteur());
            return new BlockPos(caverne.x(), caverne.sol(), caverne.z());
        });
        monde.getServer().runCommand("kill @e[type=!player,type=!minecart]");
        monde.getServer().runCommand("tp @a[limit=1] " + (lieu.getX() + 0.5) + " " + lieu.getY() + " " + (lieu.getZ() + 0.5));
        monde.getServer().runCommand("gamemode survival @a[limit=1]");
        monde.getServer().runCommand("difficulty normal");
        monde.getServer().runCommand("gamerule spawn_mobs true");
        monde.getServer().runCommand("gamerule spawn_monsters true");
        context.waitTicks(40);
        double[] degats = {0};
        int[] maxAttaquants = {0};
        int[] maxProches = {0};
        double[] degatsParMinute = new double[DUREE_MONSTRES / 1200 + 1];
        int[] tick = {0};
        monde.getServer().waitFor(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            float perdu = joueur.getMaxHealth() - joueur.getHealth();
            degats[0] += perdu;
            degatsParMinute[tick[0] / 1200] += perdu;
            joueur.setHealth(joueur.getMaxHealth());
            joueur.getFoodData().setFoodLevel(20);
            if (tick[0] % 10 == 0) {
                List<net.minecraft.world.entity.Mob> proches = server.overworld().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, joueur.getBoundingBox().inflate(16),
                        mob -> mob instanceof net.minecraft.world.entity.monster.Enemy);
                maxProches[0] = Math.max(maxProches[0], proches.size());
                maxAttaquants[0] = Math.max(maxAttaquants[0], (int) proches.stream().filter(mob -> mob.getTarget() == joueur).count());
            }
            return ++tick[0] >= DUREE_MONSTRES;
        }, DUREE_MONSTRES + 100);
        RedRising.LOGGER.info("[G7.2.5] Joueur immobile {} s dans une grande caverne sombre, plafond {} : jusqu'à {} monstres à 16 blocs, {} l'attaquent à la fois, {} points de dégâts ({} par minute) ; {} monstres dans la mine",
                DUREE_MONSTRES / 20, MonstresDeLaMine.PLAFOND, maxProches[0], maxAttaquants[0], Math.round(degats[0]), java.util.Arrays.toString(degatsParMinute),
                monde.getServer().computeOnServer(server -> MonstresDeLaMine.compter(server.overworld(), plan(server), OutilsTest.joueur(server))));
        monde.getServer().runCommand("gamerule spawn_mobs false");
        monde.getServer().runCommand("gamerule spawn_monsters false");
        monde.getServer().runCommand("difficulty peaceful");
        monde.getServer().runCommand("kill @e[type=!player,type=!minecart]");
        monde.getServer().runCommand("gamemode creative @a[limit=1]");
        context.waitTicks(20);
    }

    static double tickMoyen(TestSingleplayerContext monde, int ticks) {
        double[] total = {0};
        int[] n = {0};
        monde.getServer().waitFor(server -> {
            long[] temps = server.getTickTimesNanos();
            total[0] += temps[(server.getTickCount() - 1 + temps.length) % temps.length] / 1e6;
            n[0]++;
            return n[0] >= ticks;
        }, ticks + 100);
        return total[0] / n[0];
    }

    // --- Repères, points d'entrée des Gris, descente (G7.2.3) ---

    /**
     * Le chemin principal, du haut au fond : l'échelle du puits principal d'une traite, du bouchon au dernier niveau ; sur
     * chaque niveau, un quai où tenir debout ; au niveau de la colonie, la branche jusqu'à la caverne ; les panneaux du
     * centre (niveaux, colonie). Une capture du quai du niveau 1.
     */
    static void descente(TestSingleplayerContext monde, ClientGameTestContext context) {
        String defauts = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            PlanDeMine plan = plan(server);
            List<String> liste = new ArrayList<>();
            // Descente : l'échelle d'une traite
            PlanDeMine.Puits principal = plan.puitsPrincipal();
            int echelles = 0;
            for (int y = principal.bas(); y <= principal.haut(); y++) {
                if (niveau.getBlockState(PlanDeMine.echelle(principal, y)).is(PaletteMine.ECHELLE.getBlock())) {
                    echelles++;
                } else if (liste.size() < 5) {
                    liste.add("échelle coupée en y " + y);
                }
            }
            // Un quai par niveau, au moins dans une direction (la voûte de la colonie coupe une branche du niveau au-dessus) :
            // sol plein, deux blocs libres
            for (int sol : plan.sols()) {
                boolean praticable = false;
                for (Direction sens : Direction.Plane.HORIZONTAL) {
                    BlockPos quai = plan.centre().atY(sol).relative(sens, 3);
                    praticable |= !niveau.getBlockState(quai.below()).isAir() && niveau.getBlockState(quai).isAir() && niveau.getBlockState(quai.above()).isAir();
                }
                if (!praticable) {
                    liste.add("quai du niveau en y " + sol + " pas praticable");
                }
            }
            // La branche de la colonie : à chaque pas jusqu'à la caverne, un passage libre (deux blocs de haut) sur la largeur
            // de l'axe (le pilier qui tient l'échelle du puits est sur la ligne du milieu : on le contourne)
            PlanDeMine.Caverne caverne = plan.caverne();
            Direction versColonie = Direction.getApproximateNearest(caverne.x() - plan.centre().getX(), 0, caverne.z() - plan.centre().getZ());
            BlockPos.MutableBlockPos pas = plan.centre().atY(caverne.sol()).relative(versColonie, 2).mutable();
            int blocs = 0;
            while (!caverne.contient(pas.getX(), pas.getY(), pas.getZ()) && blocs < 100) {
                boolean libre = false;
                for (int ecart = -2; ecart <= 2; ecart++) {
                    BlockPos cote = pas.relative(versColonie.getClockWise(), ecart);
                    libre |= !niveau.getBlockState(cote).isSolid() && !niveau.getBlockState(cote.above()).isSolid();
                }
                if (!libre) {
                    liste.add("branche de la colonie bouchée en " + pas.toShortString());
                    break;
                }
                pas.move(versColonie);
                blocs++;
            }
            // Les panneaux du centre : un par niveau au quai du puits, et celui de la colonie
            int niveaux = 0;
            int colonie = 0;
            for (PlanDeMine.Panneau panneau : plan.panneaux()) {
                if (panneau.texte() == PlanDeMine.Texte.PUITS_VERS || panneau.texte() == PlanDeMine.Texte.PUITS_DROITE || panneau.texte() == PlanDeMine.Texte.PUITS_GAUCHE) {
                    continue;
                }
                if (!(niveau.getBlockEntity(panneau.pos()) instanceof net.minecraft.world.level.block.entity.SignBlockEntity bloc)
                        || !bloc.getFrontText().getMessage(1, false).equals(MineLykos.texte(panneau).getMessage(1, false))) {
                    liste.add("panneau absent ou mal écrit en " + panneau.pos().toShortString());
                    continue;
                }
                if (panneau.texte() == PlanDeMine.Texte.NIVEAU) {
                    niveaux++;
                } else {
                    colonie++;
                }
            }
            if (niveaux != plan.sols().size() || colonie != 1) {
                liste.add("panneaux du centre : " + niveaux + " de niveau, " + colonie + " de la colonie");
            }
            RedRising.LOGGER.info("[G7.2.3] Descente : échelle de {} barreaux d'une traite (y {} à {}), {} quais, {} blocs jusqu'à la caverne ; panneaux : {} de niveau, {} de la colonie",
                    echelles, principal.bas(), principal.haut(), plan.sols().size(), blocs, niveaux, colonie);
            return String.join(" ; ", liste.subList(0, Math.min(10, liste.size())));
        });
        if (!defauts.isEmpty()) {
            throw new AssertionError("Repères et descente : " + defauts);
        }
        // Capture : le quai du niveau 1, face au panneau
        PlanDeMine.Panneau panneau = monde.getServer().computeOnServer(server -> plan(server).panneaux().stream()
                .filter(p -> p.texte() == PlanDeMine.Texte.NIVEAU && p.numero() == 1).findFirst().orElseThrow());
        BlockPos devant = panneau.pos().relative(panneau.face(), 3).below();
        float lacet = panneau.face().getOpposite().toYRot();
        monde.getServer().runCommand("tp @a[limit=1] " + (devant.getX() + 0.5) + " " + devant.getY() + " " + (devant.getZ() + 0.5) + " " + lacet + " 0");
        context.waitTicks(40);
        monde.getConnection().waitForChunksRender();
        OutilsTest.garderCapture(context.takeScreenshot("mine-quai"));
    }

    /**
     * Les repères de toute l'emprise : tous les panneaux et leur texte (niveaux, colonie, puits secondaires aux deux bouts), la
     * bande de couleur, et les points d'entrée des Gris (au sol du réseau éclairé, où l'on tient debout, au moins un par
     * niveau).
     */
    static void reperesDeLEmprise(TestSingleplayerContext monde) {
        String defauts = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            PlanDeMine plan = plan(server);
            List<String> liste = new ArrayList<>();
            // Panneaux : un par niveau, la colonie, les puits secondaires
            int niveaux = 0;
            int colonie = 0;
            int puitsSecondaires = 0;
            for (PlanDeMine.Panneau panneau : plan.panneaux()) {
                if (!(niveau.getBlockEntity(panneau.pos()) instanceof net.minecraft.world.level.block.entity.SignBlockEntity bloc)) {
                    liste.add("pas de panneau en " + panneau.pos().toShortString());
                    continue;
                }
                if (!bloc.getFrontText().equals(MineLykos.texte(panneau)) && !bloc.getFrontText().getMessage(1, false).equals(MineLykos.texte(panneau).getMessage(1, false))) {
                    liste.add("texte du panneau en " + panneau.pos().toShortString() + " : " + bloc.getFrontText().getMessage(1, false));
                }
                switch (panneau.texte()) {
                    case NIVEAU -> niveaux++;
                    case COLONIE_DROITE, COLONIE_GAUCHE -> colonie++;
                    case PUITS_DROITE, PUITS_GAUCHE -> {
                    }
                    case PUITS_VERS -> puitsSecondaires++;
                }
            }
            if (niveaux != plan.sols().size() || colonie != 1 || puitsSecondaires != 2 * (plan.puits().size() - 1)) {
                liste.add("panneaux : " + niveaux + " de niveau, " + colonie + " de la colonie, " + puitsSecondaires + " de puits secondaires");
            }
            // Bande de couleur, par niveau
            int[] bandes = new int[PlanDeMine.NIVEAUX_MAX];
            for (Map.Entry<BlockPos, Integer> bande : plan.bandes().entrySet()) {
                if (niveau.getBlockState(bande.getKey()).equals(PaletteMine.BANDES.get(bande.getValue()))) {
                    bandes[bande.getValue()]++;
                }
            }
            for (int ligne = PlanDeMine.NIVEAUX_MAX - plan.sols().size(); ligne < PlanDeMine.NIVEAUX_MAX; ligne++) {
                if (bandes[ligne] < 200) {
                    liste.add(bandes[ligne] + " blocs de bande à la ligne " + ligne);
                }
            }
            // Points d'entrée des Gris
            java.util.Set<BlockPos> reseau = new java.util.HashSet<>(plan.reseauEclaire());
            int[] parNiveau = new int[plan.sols().size()];
            for (PlanDeMine.Entree entree : plan.entreesDesGris()) {
                BlockPos pos = entree.pos();
                boolean debout = niveau.getBlockState(pos.below()).isSolid() && !niveau.getBlockState(pos).isSolid() && !niveau.getBlockState(pos.above()).isSolid();
                if (!reseau.contains(pos) || !debout) {
                    liste.add("point d'entrée en " + pos.toShortString() + (debout ? " hors du réseau éclairé" : " où l'on ne tient pas debout"));
                }
                parNiveau[entree.niveau()]++;
            }
            for (int n : parNiveau) {
                if (n == 0) {
                    liste.add("un niveau sans point d'entrée des Gris : " + java.util.Arrays.toString(parNiveau));
                    break;
                }
            }
            RedRising.LOGGER.info("[G7.2.3] Repères : panneaux : {} de niveau, {} de la colonie, {} de puits secondaires ; bande {} ; points d'entrée des Gris par niveau {}",
                    niveaux, colonie, puitsSecondaires, java.util.Arrays.toString(bandes), java.util.Arrays.toString(parNiveau));
            return String.join(" ; ", liste.subList(0, Math.min(10, liste.size())));
        });
        if (!defauts.isEmpty()) {
            throw new AssertionError("Repères : " + defauts);
        }
    }

    // --- Colonne du puits ---

    /**
     * Du haut des galeries au bouchon : rien que de la roche autour du puits (2 blocs), ni vide ni fluide ; le bouchon, entre
     * 🟡 3 et 5 blocs sous la surface de chaque colonne du puits, et rien que du plein entre les deux ; pas de butte en surface
     * (le remplissage du relief ne l'atteint pas).
     */
    private static void colonneDuPuits(TestSingleplayerContext monde, long graine) {
        String defauts = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            PlanDeMine plan = plan(server);
            PlanDeMine.Puits principal = plan.puitsPrincipal();
            List<String> liste = new ArrayList<>();
            int haut = plan.bouchon().minY() - 1;
            int bas = plan.sols().getFirst() + 6;
            for (int y = bas; y <= haut; y++) {
                for (int x = principal.x() - 3; x <= principal.x() + 3; x++) {
                    for (int z = principal.z() - 3; z <= principal.z() + 3; z++) {
                        if (plan.interieur(x, y, z)) {
                            continue;
                        }
                        BlockState etat = niveau.getBlockState(new BlockPos(x, y, z));
                        if (etat.isAir() || !etat.getFluidState().isEmpty()) {
                            liste.add((etat.isAir() ? "vide" : "fluide") + " autour du puits en " + x + " " + y + " " + z);
                        }
                    }
                }
            }
            BoundingBox bouchon = plan.bouchon();
            for (BlockPos pos : BlockPos.betweenClosed(bouchon.minX(), bouchon.minY(), bouchon.minZ(), bouchon.maxX(), bouchon.maxY(), bouchon.maxZ())) {
                if (!niveau.getBlockState(pos).is(PaletteMine.BOUCHON.getBlock())) {
                    liste.add("bouchon incomplet en " + pos.toShortString());
                }
            }
            List<Integer> profondeurs = new ArrayList<>();
            for (int x = principal.x() - 1; x <= principal.x() + 1; x++) {
                for (int z = principal.z() - 1; z <= principal.z() + 1; z++) {
                    int dessus = sol(niveau, x, z) - 1;
                    int profondeur = dessus - bouchon.maxY();
                    profondeurs.add(profondeur);
                    if (profondeur < 3 || profondeur > 5) {
                        liste.add("bouchon à " + profondeur + " bloc(s) sous la surface en " + x + " " + z);
                    }
                    for (int y = bouchon.maxY() + 1; y <= dessus; y++) {
                        BlockState etat = niveau.getBlockState(new BlockPos(x, y, z));
                        if (etat.isAir() || !etat.getFluidState().isEmpty()) {
                            liste.add((etat.isAir() ? "vide" : "fluide") + " au-dessus du bouchon en " + x + " " + y + " " + z);
                        }
                    }
                }
            }
            // Aucun bloc du puits principal ne voit le ciel (sur les trois graines ; toute la mine sur la première)
            int ciel = 0;
            BoundingBox interieur = principal.interieur();
            for (BlockPos pos : BlockPos.betweenClosed(interieur.minX(), interieur.minY(), interieur.minZ(), interieur.maxX(), interieur.maxY(), interieur.maxZ())) {
                if (pos.getY() >= niveau.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())) {
                    ciel++;
                }
            }
            if (ciel > 0) {
                liste.add(ciel + " bloc(s) du puits principal qui voient le ciel");
            }
            int surface = sol(niveau, principal.x(), principal.z());
            // Pas de butte : sur 7 × 7 points autour du puits, le sol (sans arbres ni plantes) est à la hauteur prévue par le
            // relief (médiane des écarts : un rocher n'en déplace que quelques-uns, une butte les ferait tous monter)
            List<Integer> ecarts = new ArrayList<>();
            for (int dx = -12; dx <= 12; dx += 4) {
                for (int dz = -12; dz <= 12; dz += 4) {
                    int x = principal.x() + dx;
                    int z = principal.z() + dz;
                    int prevu = niveau.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, niveau,
                            niveau.getChunkSource().randomState());
                    ecarts.add(sol(niveau, x, z) - prevu);
                }
            }
            ecarts.sort(Integer::compare);
            int mediane = ecarts.get(ecarts.size() / 2);
            if (Math.abs(mediane) > 1) {
                liste.add("terrain autour du puits décalé de " + mediane + " blocs (butte ?) : " + ecarts);
            }
            RedRising.LOGGER.info("[G7.2.1] Colonne du puits, de {} à {} : bouchon en {}, surface à {}, profondeurs du bouchon {}, écart médian au relief prévu {}",
                    bas, haut, bouchon.minY(), surface, profondeurs, mediane);
            return String.join(" ; ", liste);
        });
        if (!defauts.isEmpty()) {
            throw new AssertionError("Graine " + graine + " : " + defauts);
        }
        RedRising.LOGGER.info("[G7.2.1] Graine {} : colonne du puits sans grotte ni fluide, bouchon en place, pas de butte", graine);
    }

    /** Le haut du sol (sans feuilles, troncs ni plantes), plus un : comme la hauteur prévue par le relief. */
    private static int sol(ServerLevel niveau, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, niveau.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1, z);
        while (pos.getY() > niveau.getMinY()) {
            BlockState etat = niveau.getBlockState(pos);
            if (!etat.isAir() && !etat.is(BlockTags.LEAVES) && !etat.is(BlockTags.LOGS) && !etat.canBeReplaced()) {
                return pos.getY() + 1;
            }
            pos.move(Direction.DOWN);
        }
        return niveau.getMinY();
    }

    // --- Forts ---

    /**
     * Un coffre d'outils se remplit (G7.2.5) : première ouverture, sa table ; vidé, rien avant 3 jours de jeu ; après, la table de
     * remplissage, sans aucun fer (vérifié sur plusieurs tirages) ; pas vide, il n'est pas touché.
     */
    private static void coffreQuiSeRemplit(TestSingleplayerContext monde) {
        BlockPos pos = monde.getServer().computeOnServer(server -> plan(server).coffres().getFirst().pos());
        monde.getServer().runOnServer(server -> server.overworld().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true));
        monde.getServer().waitFor(server -> server.overworld().hasChunk(pos.getX() >> 4, pos.getZ() >> 4), 600);
        String defauts = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            ChestBlockEntity coffre = (ChestBlockEntity) Objects.requireNonNull(niveau.getBlockEntity(pos));
            ServerLevelData donnees = (ServerLevelData) niveau.getLevelData();
            List<String> liste = new ArrayList<>();
            CoffresMine.ouverture(niveau, pos);
            coffre.unpackLootTable(null);
            if (coffre.isEmpty()) {
                liste.add("première ouverture : coffre vide");
            }
            coffre.clearContent();
            CoffresMine.ouverture(niveau, pos);
            if (coffre.getLootTable() != null) {
                liste.add("rempli avant le délai");
            }
            Map<Item, Integer> contenu = new java.util.TreeMap<>(java.util.Comparator.comparing(item -> item.toString()));
            for (int tirage = 0; tirage < 20; tirage++) {
                donnees.setGameTime(niveau.getGameTime() + PlanDeMine.DELAI_REMPLISSAGE);
                CoffresMine.ouverture(niveau, pos);
                if (coffre.getLootTable() == null) {
                    liste.add("pas rempli après le délai");
                }
                coffre.unpackLootTable(null);
                for (int i = 0; i < coffre.getContainerSize(); i++) {
                    ItemStack pile = coffre.getItem(i);
                    if (!pile.isEmpty()) {
                        contenu.merge(pile.getItem(), pile.getCount(), Integer::sum);
                        if (pile.getItem().toString().contains("iron") || pile.is(Items.BUCKET) || INTERDITS_DANS_LES_COFFRES.contains(pile.getItem())) {
                            liste.add(pile + " dans un coffre rempli");
                        }
                    }
                }
                // Pas vide : pas touché
                if (tirage == 0) {
                    donnees.setGameTime(niveau.getGameTime() + PlanDeMine.DELAI_REMPLISSAGE);
                    CoffresMine.ouverture(niveau, pos);
                    if (coffre.getLootTable() != null) {
                        liste.add("un coffre pas vide a été rempli");
                    }
                }
                coffre.clearContent();
            }
            coffre.setChanged();
            RedRising.LOGGER.info("[G7.2.5] Coffre qui se remplit (20 tirages) : {}", contenu);
            server.overworld().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
            return String.join(" ; ", liste);
        });
        if (!defauts.isEmpty()) {
            throw new AssertionError("Coffre qui se remplit : " + defauts);
        }
    }

    /**
     * La roche rouge (G7.2.5), sur les chunks à cette distance du centre : plus de pierre ni de deepslate autour du vide de la
     * mine (sous la limite du volume) ; aucun bloc de roche rouge posé par la mine ne voit le ciel ; les strates, et la roche des
     * outils et du four.
     */
    static void rocheRouge(TestSingleplayerContext monde, long graine, int rayon) {
        String defauts = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            PlanDeMine plan = plan(server);
            int cx = plan.centre().getX() >> 4;
            int cz = plan.centre().getZ() >> 4;
            List<String> liste = new ArrayList<>();
            int rouges = 0;
            int gris = 0;
            int auCiel = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = (cx - rayon) << 4; x <= ((cx + rayon) << 4) + 15; x++) {
                for (int z = (cz - rayon) << 4; z <= ((cz + rayon) << 4) + 15; z++) {
                    int surface = sol(niveau, x, z);
                    int limite = Math.min(MineLykos.HAUT_DU_VOLUME, surface - MineLykos.ROUGE_SOUS_LA_SURFACE) - 3;
                    for (int y = niveau.getMinY() + 5; y <= limite; y++) {
                        BlockState etat = niveau.getBlockState(pos.set(x, y, z));
                        rouges += PaletteMine.estRocheRouge(etat) ? 1 : 0;
                        gris += PaletteMine.rocheNaturelle(etat) ? 1 : 0;
                    }
                    // Le bloc de surface n'est jamais une strate posée par la mine (les bandes colorées de surface ne viennent pas d'elle)
                    BlockState dessus = niveau.getBlockState(pos.set(x, surface - 1, z));
                    if (surface - 1 < MineLykos.HAUT_DU_VOLUME - 3 && PaletteMine.estRocheRouge(dessus) && !niveau.getBiome(pos).is(net.minecraft.tags.BiomeTags.IS_BADLANDS)) {
                        auCiel++;
                    }
                }
            }
            if (gris > 0 || rouges == 0 || auCiel > 0) {
                liste.add(rouges + " blocs rouges, " + gris + " blocs de roche grise dans le volume, " + auCiel + " colonnes rouges au ciel");
            }
            for (Item roche : List.of(Items.TERRACOTTA, Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.RED).asItem(),
                    Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.ORANGE).asItem(), Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.BROWN).asItem())) {
                ItemStack pile = new ItemStack(roche);
                if (!pile.is(net.minecraft.tags.ItemTags.STONE_TOOL_MATERIALS) || !pile.is(net.minecraft.tags.ItemTags.STONE_CRAFTING_MATERIALS)) {
                    liste.add(roche + " pas dans les tags de pierre");
                }
            }
            RedRising.LOGGER.info("[G7.2.5] Graine {} : roche rouge ({} chunks) : {} blocs rouges, {} de roche grise, {} colonnes rouges au ciel", graine,
                    (2 * rayon + 1) * (2 * rayon + 1), rouges, gris, auCiel);
            return String.join(" ; ", liste);
        });
        if (!defauts.isEmpty()) {
            throw new AssertionError("Graine " + graine + " : " + defauts);
        }
    }

    /** Un fort posé exprès sur le chemin de la mine (vrai terrain de ce monde) : la mine s'en écarte. */
    static void fortSurLeChemin(TestSingleplayerContext monde) {
        String resultat = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            PlanMars plan = Objects.requireNonNull(RegionsMars.plan(server));
            BlockPos mine = plan.regions().get(Region.MINE);
            PlanMars.Terrain terrain = RegionsMars.terrain(niveau.getChunkSource().getGenerator(), niveau.getChunkSource().randomState(), niveau);
            List<ChunkPos> forts = List.of(new ChunkPos(mine.getX() >> 4, (mine.getZ() >> 4) + 4));
            PlanMars autre = PlanMars.tirer(niveau.getSeed(), plan.apparition(), terrain, PlanMars.ChunksPrepares.AUCUN, forts);
            BlockPos nouvelle = autre.regions().get(Region.MINE);
            int ecart = Math.max(Math.abs((nouvelle.getX() >> 4) - forts.getFirst().x()), Math.abs((nouvelle.getZ() >> 4) - forts.getFirst().z()));
            return ecart >= PlanMars.ECART_FORT ? "" : "mine à " + ecart + " chunks du fort posé";
        });
        if (!resultat.isEmpty()) {
            throw new AssertionError(resultat);
        }
        RedRising.LOGGER.info("[G7.2.1] Un fort sur le chemin de la mine : elle s'en écarte");
    }
}
