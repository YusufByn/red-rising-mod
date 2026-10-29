package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Boutiques;
import io.github.yusufbyn.redrising.factions.ObjetsRebelles;
import io.github.yusufbyn.redrising.monde.FilonsDeMine;
import io.github.yusufbyn.redrising.monde.MineLykos;
import io.github.yusufbyn.redrising.monde.MonstresDeLaMine;
import io.github.yusufbyn.redrising.monde.ObjetsMine;
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
 * G7.2.1 : la mine sur de vrais mondes Mars neufs.
 *
 * <ul>
 *     <li>Une graine, toute l'emprise générée (17 × 17 chunks) : chaque chunk a la référence de la mine ; parcours en
 *     largeur sur les vrais blocs depuis le bas du puits, qui atteint tout le vide du plan et rien d'autre (aucune fuite vers
 *     une grotte, aucun fluide contre le vide) ; aucun bloc de la mine ne voit le ciel ; aucune autre structure (sauf un
 *     fort) ne croise le volume ; aucun générateur de monstres dans l'emprise ; coût de la pose et temps par tick.</li>
 *     <li>Trois graines : la colonne du puits principal, du haut de la mine au bouchon, sans grotte ni fluide autour ; le
 *     bouchon, entre 3 et 5 blocs sous la surface de la colonne, sous de la roche pleine ; rien du puits ne voit le ciel ;
 *     pas de butte en surface.</li>
 *     <li>Forts : la distance du plus proche, et une mine écartée d'un fort posé exprès sur son chemin.</li>
 *     <li>G7.2.2 : salles, filons et coffres d'outils comptés sur les trois graines (emprise entière) ; une salle de forage
 *     en capture ; le minerai d'hélium-3 (lumière, pioche en pierre).</li>
 * </ul>
 */
public class MineLykosTest implements FabricClientGameTest {
    private static final long[] GRAINES = {20_261_028L, 7L, -4_242_424_242L};
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
            try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context, context.worldBuilder().adjustSettings(reglages -> {
                reglages.setWorldType(reglages.getNormalPresetList().stream()
                        .filter(type -> type.preset().is(WorldPresets.NORMAL)).findFirst().orElseThrow());
                reglages.setSeed(Long.toString(graine));
                // Les mondes de test de Fabric n'ont pas de structures ; ceux des joueurs, si (par défaut)
                reglages.setGenerateStructures(true);
            }))) {
                RegionsMars.Tirage tirage = Objects.requireNonNull(monde.getServer().computeOnServer(server -> RegionsMars.dernierTirage()));
                RedRising.LOGGER.info("[G7.2.1] Graine {} : mine à {} blocs du point d'apparition, fort le plus proche à {} chunks",
                        graine, tirage.distanceMine(), tirage.fortLePlusProche());
                if (tirage.fortLePlusProche() >= 0 && tirage.fortLePlusProche() < PlanMars.ECART_FORT) {
                    throw new AssertionError("Graine " + graine + " : fort à " + tirage.fortLePlusProche() + " chunks de la mine");
                }
                if (i == 0) {
                    emprise(monde, graine);
                    fortSurLeChemin(monde);
                    forageEtHelium3(monde, context);
                    reseauEtMonstres(monde, context);
                } else {
                    charger(monde, PlanMars.EMPRISE_MINE);
                }
                colonneDuPuits(monde, graine);
                comptage(monde, graine);
            }
        }
        RedRising.LOGGER.info("[G7.2.1] OK : mine au bon endroit, toute reliée, sans ouverture vers le ciel, sans autre structure");
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
        return ((MineLykos.Morceau) depart.getPieces().getFirst()).plan();
    }

    /** Charge (force) les chunks à cette distance (en chunks, en carré) du chunk de la mine, et mesure le temps par tick. */
    private static double charger(TestSingleplayerContext monde, int rayon) {
        return charger(monde, rayon, 0, true);
    }

    /** Pareil, autour du chunk de la mine décalé de {@code decalage} chunks (en x et en z) ; {@code garder} : les laisser forcés. */
    private static double charger(TestSingleplayerContext monde, int rayon, int decalage, boolean garder) {
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

    private static void emprise(TestSingleplayerContext monde, long graine) {
        int rayon = PlanMars.EMPRISE_MINE;
        double tickMoyen = charger(monde, rayon);
        String mesures = monde.getServer().computeOnServer(server -> MineLykos.mesures());
        RedRising.LOGGER.info("[G7.2.1] Emprise générée ({} chunks) : pose {} ; {} ms par tick en moyenne", (2 * rayon + 1) * (2 * rayon + 1), mesures,
                String.format(Locale.ROOT, "%.1f", tickMoyen));
        // Coût de la règle des décorations : dans la mine (et un chunk autour), puis ailleurs (11 × 11 chunks loin de la mine)
        double dedansUs = monde.getServer().computeOnServer(server -> MineLykos.DECORATIONS_DEDANS.moyenneUs());
        String dedans = monde.getServer().computeOnServer(server -> MineLykos.DECORATIONS_DEDANS.toString());
        charger(monde, 5, 40, false);
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
        boites.add(plan.caverne().boite());
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
            if (etat.is(Blocks.SPRUCE_LOG)) {
                piliers.add(cle);
            } else if (!(etat.isAir() || etat.is(Blocks.LADDER) || etat.is(Blocks.SPRUCE_FENCE) || etat.is(Blocks.SPRUCE_PLANKS) || etat.is(Blocks.CHEST)
                    || etat.is(Blocks.LANTERN))) {
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
    private static final int GALERIES_MIN = 6000;
    private static final int SALLES_MIN = 30;
    private static final int SALLES_PAR_NIVEAU_MIN = 6;
    /** 🟡 Minerais visibles depuis la mine (qui touchent son vide), au moins ; hélium-3 dans tout le volume, au moins. */
    private static final Map<FilonsDeMine.Minerai, Integer> VISIBLES_MIN = Map.of(FilonsDeMine.Minerai.CHARBON, 400, FilonsDeMine.Minerai.FER, 500,
            FilonsDeMine.Minerai.CUIVRE, 220, FilonsDeMine.Minerai.OR, 160, FilonsDeMine.Minerai.REDSTONE, 210, FilonsDeMine.Minerai.HELIUM3, 60);
    private static final int HELIUM3_VOLUME_MIN = 400;
    /** 🟡 Coffres d'outils : nombre, et ce qui n'y est jamais (en plus de la contrebande). */
    private static final int COFFRES_MIN = 12;
    private static final int COFFRES_MAX = 20;
    private static final Set<Item> INTERDITS_DANS_LES_COFFRES = Set.of(Items.IRON_INGOT, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS,
            Items.IRON_BOOTS, Items.DIAMOND, Boutiques.CREDIT, ObjetsMine.HELIUM3, ObjetsMine.MINERAI_HELIUM3_OBJET);
    /** 🟡 Plafonds : fer du volume par rapport à vanilla (sans nos filons), diamants visibles par rapport au squelette sans salles. */
    private static final double FER_VOLUME_MAX = 1.25;
    private static final double DIAMANTS_VISIBLES_MAX = 1.2;

    /** Les blocs de chaque minerai (pierre et ardoise des profondeurs). */
    private static Set<Block> blocs(FilonsDeMine.Minerai minerai) {
        return switch (minerai) {
            case CHARBON -> Set.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
            case FER -> Set.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
            case CUIVRE -> Set.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
            case OR -> Set.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE);
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
        boites.add(plan.caverne().boite());
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
    private static void comptage(TestSingleplayerContext monde, long graine) {
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
                    // Les tranches des niveaux (FilonsDeMine.dansLaTranche) : de 8 blocs sous le dernier sol au haut des boîtes
                    for (int y = plan.sols().getLast() - 8; y <= plan.hautDesBoites(); y++) {
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
            int diamants = visibles(niveau, vide, DIAMANTS);
            int diamantsSquelette = visibles(niveau, videDuPlan(plan, false), DIAMANTS);
            double rapportDiamants = (double) diamants / Math.max(1, diamantsSquelette);
            minerais.append(String.format(Locale.ROOT, " diamants %d visibles (%d sans les salles, ×%.2f)", diamants, diamantsSquelette, rapportDiamants));
            if (rapportDiamants > DIAMANTS_VISIBLES_MAX) {
                liste.add(String.format(Locale.ROOT, "diamants visibles ×%.2f", rapportDiamants));
            }
            RedRising.LOGGER.info("[G7.2.2] Graine {} : filons{}", graine, minerais);

            // Coffres d'outils : posés, sans camp, rien d'interdit dedans, 1 ou 2 pioches en fer dans toute la mine
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
            if (coffres < COFFRES_MIN || coffres > COFFRES_MAX || pioches < 1 || pioches > PlanDeMine.COFFRES_RARES) {
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
    private static void reseauEtMonstres(TestSingleplayerContext monde, ClientGameTestContext context) {
        String sombres = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            List<String> liste = new ArrayList<>();
            List<BlockPos> sol = plan(server).reseauEclaire();
            for (BlockPos pos : sol) {
                if (niveau.getBrightness(LightLayer.BLOCK, pos) < 1 && liste.size() < 10) {
                    liste.add(pos.toShortString());
                }
            }
            RedRising.LOGGER.info("[G7.2.3] Réseau éclairé : {} lanternes, {} blocs de sol, {} dans le noir", plan(server).lanternes().size(), sol.size(), liste.size());
            return String.join(" ; ", liste);
        });
        if (!sombres.isEmpty()) {
            throw new AssertionError("Sol du réseau éclairé dans le noir : " + sombres);
        }

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
        monde.getServer().runCommand("kill @e[type=!player]");
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
                    mob -> mob instanceof net.minecraft.world.entity.monster.Enemy)) {
                BlockPos p = mob.blockPosition();
                if (plan.interieur(p.getX(), p.getY(), p.getZ())) {
                    sortes.merge(mob.getType().toShortString(), 1, Integer::sum);
                    if (mob.getType() == net.minecraft.world.entity.EntityTypes.CREEPER || mob.getType() == net.minecraft.world.entity.EntityTypes.SLIME) {
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
        monde.getServer().runCommand("kill @e[type=!player]");
        if (!defauts.isEmpty()) {
            throw new AssertionError("Monstres de la mine : " + defauts);
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
                if (!niveau.getBlockState(pos).is(Blocks.POLISHED_ANDESITE)) {
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

    /** Un fort posé exprès sur le chemin de la mine (vrai terrain de ce monde) : la mine s'en écarte. */
    private static void fortSurLeChemin(TestSingleplayerContext monde) {
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
