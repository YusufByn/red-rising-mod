package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.monde.MineLykos;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
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

/**
 * G7.2.1 : la mine sur de vrais mondes Mars neufs.
 *
 * <ul>
 *     <li>Une graine, toute l'emprise générée (17 × 17 chunks) : chaque chunk a la référence de la mine ; parcours en
 *     largeur sur les vrais blocs depuis le bas du puits, qui atteint tout le vide du plan et rien d'autre (aucune fuite vers
 *     une grotte, aucun fluide contre le vide) ; aucun bloc de la mine ne voit le ciel ; aucune autre structure (sauf un
 *     fort) ne croise le volume ; aucun générateur de monstres dans l'emprise ; coût de la pose et temps par tick.</li>
 *     <li>Trois graines : la colonne du puits principal, du haut de la mine au bouchon, sans grotte ni fluide autour ; le
 *     bouchon, la roche d'origine au-dessus, et pas de butte en surface.</li>
 *     <li>Forts : la distance du plus proche, et une mine écartée d'un fort posé exprès sur son chemin.</li>
 * </ul>
 */
public class MineLykosTest implements FabricClientGameTest {
    private static final long[] GRAINES = {20_261_028L, 7L, -4_242_424_242L};
    private static final ResourceKey<Structure> MINE = ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "mine"));
    /** 🟡 Plafonds du coût : pose d'un chunk, et temps moyen par tick pendant la génération de l'emprise. */
    private static final double POSE_MOYENNE_MAX_MS = 20;
    private static final double POSE_MAX_MS = 100;
    private static final double TICK_MOYEN_MAX_MS = 50;

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
                } else {
                    charger(monde, 1);
                }
                colonneDuPuits(monde, graine);
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
        monde.getServer().runOnServer(server -> {
            MineLykos.remettreMesures();
            BlockPos centre = centre(server);
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    server.overworld().setChunkForced((centre.getX() >> 4) + dx, (centre.getZ() >> 4) + dz, true);
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
                    if (!server.overworld().hasChunk((centre.getX() >> 4) + dx, (centre.getZ() >> 4) + dz)) {
                        return false;
                    }
                }
            }
            return true;
        }, 6000);
        return total[0] / Math.max(1, ticks[0]);
    }

    // --- Toute l'emprise ---

    private static void emprise(TestSingleplayerContext monde, long graine) {
        int rayon = PlanMars.EMPRISE_MINE;
        double tickMoyen = charger(monde, rayon);
        String mesures = monde.getServer().computeOnServer(server -> MineLykos.mesures());
        RedRising.LOGGER.info("[G7.2.1] Emprise générée ({} chunks) : pose {} ; {} ms par tick en moyenne", (2 * rayon + 1) * (2 * rayon + 1), mesures,
                String.format(Locale.ROOT, "%.1f", tickMoyen));
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
            } else if (!(etat.isAir() || etat.is(Blocks.LADDER) || etat.is(Blocks.SPRUCE_FENCE) || etat.is(Blocks.SPRUCE_PLANKS))) {
                malPoses++;
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
        RedRising.LOGGER.info("[G7.2.1] Parcours depuis le bas du puits : {} blocs atteints sur {} ; {} niveaux, {} galeries, {} puits, {} cellules réservées",
                vus.size(), attendus, plan.sols().size(), plan.galeries().size(), plan.puits().size(), plan.cellules().size());
        if (vus.size() != attendus) {
            defauts.add("parcours : " + vus.size() + " blocs atteints sur " + attendus);
        }
        return String.join(" ; ", defauts);
    }

    // --- Colonne du puits ---

    /**
     * Du haut des galeries au bouchon : rien que de la roche autour du puits (2 blocs), ni vide ni fluide ; le bouchon ;
     * au moins 3 blocs de la roche d'origine au-dessus ; pas de butte en surface (le remplissage du relief ne l'atteint pas).
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
            int surface = niveau.getHeight(Heightmap.Types.OCEAN_FLOOR, principal.x(), principal.z());
            if (surface - 1 - bouchon.maxY() < 3) {
                liste.add("seulement " + (surface - 1 - bouchon.maxY()) + " bloc(s) au-dessus du bouchon");
            }
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
            RedRising.LOGGER.info("[G7.2.1] Colonne du puits, de {} à {} : bouchon en {}, surface à {}, écart médian au relief prévu {}", bas, haut,
                    bouchon.minY(), surface, mediane);
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
