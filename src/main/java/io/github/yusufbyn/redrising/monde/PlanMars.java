package io.github.yusufbyn.redrising.monde;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Le plan des régions de Mars (G7.1.1, GDD section 7) : où est chaque {@link Region}, tiré de la graine au premier
 * démarrage d'un monde neuf, puis sauvegardé dans l'état du monde. Logique pure (testée) : le terrain est lu par
 * {@link Terrain}, sans générer de chunks. Le côté serveur est {@link RegionsMars}. Valeurs en 🟡 dans le GDD, toutes ici.
 *
 * <p>Tirage : chaque région, dans l'ordre de {@link Region}, essaie jusqu'à {@value #ESSAIS} positions à une distance
 * donnée du point d'apparition, dans une direction au hasard (de la graine), et garde la première sans défaut. Sans
 * position parfaite, la moins mauvaise : un plan existe toujours.</p>
 *
 * @param apparition le point d'apparition du monde au moment du tirage
 * @param regions    le centre de chaque région (y : hauteur du terrain)
 */
public record PlanMars(BlockPos apparition, Map<Region, BlockPos> regions) {
    public static final Codec<PlanMars> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("apparition").forGetter(PlanMars::apparition),
            Codec.unboundedMap(Region.CODEC, BlockPos.CODEC).fieldOf("regions").forGetter(PlanMars::regions)
    ).apply(instance, PlanMars::new));

    /** 🟡 Distance au point d'apparition (minimum, maximum) du centre de chaque région, en blocs. */
    public static final Map<Region, int[]> DISTANCES = Map.of(
            // Pas sous le point d'apparition : on la trouverait en creusant au spawn, et les joueurs sans lit y réapparaissent
            Region.MINE, new int[]{200, 400},
            Region.GARNISON, new int[]{500, 700},
            Region.CITE, new int[]{800, 1000},
            Region.ZONES, new int[]{1100, 1300});
    /** 🟡 Les pôles commencent à cette latitude (|z|) ; le plan met la région des pôles au nord, un peu au-delà. */
    public static final int LATITUDE_POLES = 3000;
    public static final int Z_REGION_POLES = -(LATITUDE_POLES + 400);
    /** 🟡 Écart minimal entre deux régions. */
    public static final int ECART_MINIMAL = 300;
    /** 🟡 Cité sur terrain plat : au plus cet écart de hauteur sur 64 × 64 blocs autour de son centre. */
    public static final int PLATITUDE_CITE = 12;
    /** Pas d'eau au centre ni à cette distance autour (une région n'est pas dans l'océan ni dans un fleuve). */
    static final int RAYON_TERRE = 48;
    /**
     * Le chunk de départ de la mine est au moins à cette distance (en chunks, en carré) du point d'apparition estimé par
     * Minecraft, avant sa recherche. Pendant cette recherche (11 × 11 chunks autour de l'estimation), Minecraft génère des
     * chunks, et décide pour eux et leurs voisins à 8 chunks quelles structures y commencent : la mine ne doit pas y être,
     * sinon elle serait jugée absente avant que le plan existe (G7.2.1).
     */
    public static final int CHUNKS_HORS_RECHERCHE = 14;
    static final int ESSAIS = 64;

    public PlanMars {
        regions = Map.copyOf(regions);
    }

    /** Ce que le plan lit du terrain, sans générer de chunks. */
    public interface Terrain {
        /** Océan, fleuve ou terrain sous le niveau de la mer. */
        boolean eau(int x, int z);

        /** Hauteur du terrain (sol, sous l'eau éventuelle). */
        int hauteur(int x, int z);
    }

    /**
     * Tire le plan de ce monde.
     *
     * @param apparition le point d'apparition choisi par Minecraft
     * @param estimation le point d'apparition estimé par Minecraft avant sa recherche (voir {@link #CHUNKS_HORS_RECHERCHE})
     */
    public static PlanMars tirer(long graine, BlockPos apparition, BlockPos estimation, Terrain terrain) {
        Map<Region, BlockPos> regions = new EnumMap<>(Region.class);
        for (Region region : Region.values()) {
            regions.put(region, placer(region, graine, apparition, estimation, regions, terrain));
        }
        return new PlanMars(apparition, regions);
    }

    private static BlockPos placer(Region region, long graine, BlockPos apparition, BlockPos estimation, Map<Region, BlockPos> placees,
                                   Terrain terrain) {
        if (region == Region.POLES) {
            // Une bande de latitude (G7.1.2) : la région est au nord, en face du point d'apparition
            return enSurface(apparition.getX(), Z_REGION_POLES, terrain);
        }
        int[] distance = DISTANCES.get(region);
        Random hasard = new Random(graine ^ (0x5DEECE66DL * (region.ordinal() + 1)));
        BlockPos meilleure = null;
        int meilleurScore = Integer.MAX_VALUE;
        for (int essai = 0; essai < ESSAIS; essai++) {
            double angle = hasard.nextDouble() * 2 * Math.PI;
            double rayon = distance[0] + hasard.nextDouble() * (distance[1] - distance[0]);
            BlockPos candidat = enSurface(apparition.getX() + (int) Math.round(Math.cos(angle) * rayon),
                    apparition.getZ() + (int) Math.round(Math.sin(angle) * rayon), terrain);
            List<String> defauts = defauts(region, candidat, placees, apparition, estimation, terrain);
            if (defauts.isEmpty()) {
                return candidat;
            }
            int score = defauts.size() * 1000 + (region == Region.CITE ? ecartDeHauteur(candidat, terrain) : 0);
            if (score < meilleurScore) {
                meilleurScore = score;
                meilleure = candidat;
            }
        }
        return meilleure;
    }

    /**
     * Ce qui ne va pas pour cette région à cette position (vide : rien). Sert au tirage, et aux tests pour vérifier un plan.
     * Les pôles ne sont pas vérifiés ici : leurs biomes viennent en G7.1.2.
     */
    public static List<String> defauts(Region region, BlockPos pos, Map<Region, BlockPos> autres, BlockPos apparition, BlockPos estimation,
                                       Terrain terrain) {
        List<String> defauts = new ArrayList<>();
        if (region == Region.POLES) {
            return defauts;
        }
        int[] distance = DISTANCES.get(region);
        double auSpawn = Math.hypot(pos.getX() - apparition.getX(), pos.getZ() - apparition.getZ());
        // Une marge d'un bloc pour l'arrondi des coordonnées
        if (auSpawn < distance[0] - 1 || auSpawn > distance[1] + 1) {
            defauts.add("à " + Math.round(auSpawn) + " blocs du point d'apparition");
        }
        if (enEau(pos, terrain)) {
            defauts.add("dans l'eau");
        }
        for (Map.Entry<Region, BlockPos> autre : autres.entrySet()) {
            if (autre.getKey() != region && autre.getKey() != Region.POLES
                    && Math.hypot(pos.getX() - autre.getValue().getX(), pos.getZ() - autre.getValue().getZ()) < ECART_MINIMAL) {
                defauts.add("trop près de " + autre.getKey().getSerializedName());
            }
        }
        if (region == Region.MINE) {
            int dx = Math.abs((pos.getX() >> 4) - (estimation.getX() >> 4));
            int dz = Math.abs((pos.getZ() >> 4) - (estimation.getZ() >> 4));
            if (Math.max(dx, dz) < CHUNKS_HORS_RECHERCHE) {
                defauts.add("dans les chunks de la recherche du point d'apparition");
            }
        }
        if (region == Region.CITE && ecartDeHauteur(pos, terrain) > PLATITUDE_CITE) {
            defauts.add("terrain pas plat (" + ecartDeHauteur(pos, terrain) + " blocs d'écart)");
        }
        return defauts;
    }

    /** Tous les défauts du plan (vide : cohérent). */
    public List<String> defauts(BlockPos estimation, Terrain terrain) {
        List<String> defauts = new ArrayList<>();
        for (Region region : Region.values()) {
            BlockPos pos = regions.get(region);
            if (pos == null) {
                defauts.add(region.getSerializedName() + " : absente");
                continue;
            }
            defauts(region, pos, regions, apparition, estimation, terrain)
                    .forEach(defaut -> defauts.add(region.getSerializedName() + " : " + defaut));
        }
        return defauts;
    }

    private static boolean enEau(BlockPos pos, Terrain terrain) {
        int x = pos.getX();
        int z = pos.getZ();
        return terrain.eau(x, z) || terrain.eau(x + RAYON_TERRE, z) || terrain.eau(x - RAYON_TERRE, z)
                || terrain.eau(x, z + RAYON_TERRE) || terrain.eau(x, z - RAYON_TERRE);
    }

    /** Écart entre le plus haut et le plus bas, sur une grille de 5 × 5 points espacés de 16 blocs autour du centre. */
    static int ecartDeHauteur(BlockPos pos, Terrain terrain) {
        int bas = Integer.MAX_VALUE;
        int haut = Integer.MIN_VALUE;
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                int hauteur = terrain.hauteur(pos.getX() + i * 16, pos.getZ() + j * 16);
                bas = Math.min(bas, hauteur);
                haut = Math.max(haut, hauteur);
            }
        }
        return haut - bas;
    }

    private static BlockPos enSurface(int x, int z, Terrain terrain) {
        return new BlockPos(x, terrain.hauteur(x, z), z);
    }
}
