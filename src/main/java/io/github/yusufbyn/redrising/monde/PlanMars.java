package io.github.yusufbyn.redrising.monde;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
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

    /** 🟡 Distance au point d'apparition (minimum, maximum) du centre de chaque région, en blocs (la mine : voir {@link #DISTANCE_MAX_MINE}). */
    public static final Map<Region, int[]> DISTANCES = Map.of(
            // Pas sous le point d'apparition : on la trouverait en creusant au spawn, et les joueurs sans lit y réapparaissent
            Region.MINE, new int[]{200, 400},
            Region.GARNISON, new int[]{500, 700},
            Region.CITE, new int[]{800, 1000},
            Region.ZONES, new int[]{1100, 1300});
    /** 🟡 Les pôles commencent à cette latitude (|z|) ; le plan met la région des pôles au nord, un peu au-delà. */
    public static final int LATITUDE_POLES = 3000;
    /** 🟡 Avant les pôles, une bande de transition de cette largeur où le froid gagne peu à peu ({@link Latitude}). */
    public static final int TRANSITION_POLES = 400;
    public static final int Z_REGION_POLES = -(LATITUDE_POLES + 400);
    /** 🟡 Écart minimal entre deux régions. */
    public static final int ECART_MINIMAL = 300;
    /** 🟡 Cité sur terrain plat : au plus cet écart de hauteur sur 64 × 64 blocs autour de son centre. */
    public static final int PLATITUDE_CITE = 12;
    /** Pas d'eau au centre ni à cette distance autour (une région n'est pas dans l'océan ni dans un fleuve). */
    static final int RAYON_TERRE = 48;
    /**
     * 🟡 Emprise de la mine autour de son chunk central, en chunks (en carré) : 27 × 27 chunks, 432 × 432 blocs (G7.2.5). Un
     * départ de structure ne porte qu'à 8 chunks ({@code ChunkStatus.MAX_STRUCTURE_DISTANCE}, 272 blocs de côté) : la mine a
     * donc 9 départs, en tuiles ({@link #tuile}).
     */
    public static final int EMPRISE_MINE = 13;
    /**
     * 🟡 Si aucune place ne convient entre 200 et 400 blocs (chunks déjà préparés, eau), la mine s'éloigne par bandes de
     * 200 blocs, jusqu'à cette distance.
     */
    public static final int DISTANCE_MAX_MINE = 1000;
    /**
     * Un fort (stronghold) n'est jamais refusé par la mine (c'est l'accès à l'End) : la mine s'en écarte. Son chunk de
     * départ reste à plus de son emprise et de la portée d'un fort (8 chunks) de tout fort.
     */
    public static final int ECART_FORT = EMPRISE_MINE + 8 + 1;
    /** 🟡 Autour du puits de la mine, le terrain reste à cette hauteur au moins : le niveau du haut y tient (G7.2.5). */
    public static final int TERRAIN_MIN_PUITS = 31;
    static final int RAYON_TERRAIN_PUITS = 48;
    /** 🟡 Écart de hauteur permis sur la colonne du puits (3 × 3). */
    public static final int PENTE_MAX_PUITS = 2;
    static final int ESSAIS = 64;
    /** La région des pôles cherche la terre ferme jusqu'à 32 pas de 64 blocs de part et d'autre (2 km). */
    static final int PAS_POLES = 32;

    public PlanMars {
        regions = Map.copyOf(regions);
    }

    /**
     * Les chunks que Minecraft a déjà préparés au moment du tirage (pendant sa recherche du point d'apparition, il génère
     * des chunks, et donc leurs étapes « départs de structures » et « références » autour d'eux). Une structure ne peut
     * plus y commencer ni y poser ses morceaux : la mine doit les éviter (G7.2.1).
     */
    public interface ChunksPrepares {
        /** Rien de préparé (tests). */
        ChunksPrepares AUCUN = new ChunksPrepares() {
            @Override
            public boolean departs(int cx, int cz) {
                return false;
            }

            @Override
            public boolean references(int cx, int cz) {
                return false;
            }
        };

        /** Ce chunk a passé l'étape des départs de structures. */
        boolean departs(int cx, int cz);

        /** Ce chunk a passé l'étape des références de structures. */
        boolean references(int cx, int cz);
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
     * @param prepares   les chunks déjà préparés au moment du tirage (la mine les évite)
     * @param forts      les chunks de départ des forts, calculés depuis la graine (la mine s'en écarte)
     */
    public static PlanMars tirer(long graine, BlockPos apparition, Terrain terrain, ChunksPrepares prepares, Collection<ChunkPos> forts) {
        Map<Region, BlockPos> regions = new EnumMap<>(Region.class);
        for (Region region : Region.values()) {
            regions.put(region, placer(region, graine, apparition, regions, terrain, prepares, forts));
        }
        return new PlanMars(apparition, regions);
    }

    private static BlockPos placer(Region region, long graine, BlockPos apparition, Map<Region, BlockPos> placees, Terrain terrain,
                                   ChunksPrepares prepares, Collection<ChunkPos> forts) {
        if (region == Region.POLES) {
            // Les pôles sont une bande de latitude (G7.1.2) : la région (Obsidian) est au nord, en face du point
            // d'apparition, sur la terre ferme la plus proche le long de la même latitude
            for (int pas = 0; pas <= PAS_POLES; pas++) {
                for (int sens : new int[]{1, -1}) {
                    BlockPos candidat = enSurface(apparition.getX() + sens * pas * 64, Z_REGION_POLES, terrain);
                    if (!enEau(candidat, terrain)) {
                        return candidat;
                    }
                }
            }
            return enSurface(apparition.getX(), Z_REGION_POLES, terrain);
        }
        int[] distance = DISTANCES.get(region);
        Random hasard = new Random(graine ^ (0x5DEECE66DL * (region.ordinal() + 1)));
        BlockPos meilleure = null;
        int meilleurScore = Integer.MAX_VALUE;
        // La mine s'éloigne par bandes de 200 blocs si rien ne convient (jamais plus près)
        int fin = region == Region.MINE ? DISTANCE_MAX_MINE : distance[1];
        for (int bas = distance[0], haut = distance[1]; haut <= fin; bas = haut, haut += 200) {
            for (int essai = 0; essai < ESSAIS; essai++) {
                double angle = hasard.nextDouble() * 2 * Math.PI;
                double rayon = bas + hasard.nextDouble() * (haut - bas);
                BlockPos candidat = enSurface(apparition.getX() + (int) Math.round(Math.cos(angle) * rayon),
                        apparition.getZ() + (int) Math.round(Math.sin(angle) * rayon), terrain);
                List<String> defauts = defauts(region, candidat, placees, apparition, terrain, prepares, forts);
                if (defauts.isEmpty()) {
                    return candidat;
                }
                int score = defauts.size() * 1000 + (region == Region.CITE ? ecartDeHauteur(candidat, terrain) : 0);
                if (score < meilleurScore) {
                    meilleurScore = score;
                    meilleure = candidat;
                }
            }
        }
        return meilleure;
    }

    /**
     * Ce qui ne va pas pour cette région à cette position (vide : rien). Sert au tirage, et aux tests pour vérifier un plan.
     */
    public static List<String> defauts(Region region, BlockPos pos, Map<Region, BlockPos> autres, BlockPos apparition, Terrain terrain,
                                       ChunksPrepares prepares, Collection<ChunkPos> forts) {
        List<String> defauts = new ArrayList<>();
        if (region == Region.POLES) {
            if (Math.abs(pos.getZ()) < LATITUDE_POLES) {
                defauts.add("hors des pôles");
            }
            if (enEau(pos, terrain)) {
                defauts.add("dans l'eau");
            }
            return defauts;
        }
        if (Math.abs(pos.getZ()) >= Latitude.debutTransition() - RAYON_TERRE) {
            defauts.add("dans le froid des pôles");
        }
        int[] distance = DISTANCES.get(region);
        double auSpawn = Math.hypot(pos.getX() - apparition.getX(), pos.getZ() - apparition.getZ());
        // Une marge d'un bloc pour l'arrondi des coordonnées ; la mine peut s'éloigner jusqu'à DISTANCE_MAX_MINE
        int maximum = region == Region.MINE ? DISTANCE_MAX_MINE : distance[1];
        if (auSpawn < distance[0] - 1 || auSpawn > maximum + 1) {
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
            defauts.addAll(defautsEmprise(pos, prepares));
            // La colonne du puits presque plate : le bouchon se cale sur son point le plus bas, à 3-5 blocs sous toute la colonne
            int bas = Integer.MAX_VALUE;
            int haut = Integer.MIN_VALUE;
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    int hauteur = terrain.hauteur(pos.getX() + x, pos.getZ() + z);
                    bas = Math.min(bas, hauteur);
                    haut = Math.max(haut, hauteur);
                }
            }
            if (haut - bas > PENTE_MAX_PUITS) {
                defauts.add("puits sur une pente");
            }
            for (int x = -RAYON_TERRAIN_PUITS; x <= RAYON_TERRAIN_PUITS; x += 16) {
                for (int z = -RAYON_TERRAIN_PUITS; z <= RAYON_TERRAIN_PUITS; z += 16) {
                    if (terrain.hauteur(pos.getX() + x, pos.getZ() + z) < TERRAIN_MIN_PUITS) {
                        defauts.add("terrain trop bas autour du puits");
                        x = RAYON_TERRAIN_PUITS;
                        break;
                    }
                }
            }
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            for (ChunkPos fort : forts) {
                if (Math.max(Math.abs(fort.x() - cx), Math.abs(fort.z() - cz)) < ECART_FORT) {
                    defauts.add("trop près d'un fort en " + fort);
                    break;
                }
            }
        }
        if (region == Region.CITE && ecartDeHauteur(pos, terrain) > PLATITUDE_CITE) {
            defauts.add("terrain pas plat (" + ecartDeHauteur(pos, terrain) + " blocs d'écart)");
        }
        return defauts;
    }

    /**
     * La mine sur des chunks déjà préparés : aucun de ses 9 chunks de départ ne doit avoir passé l'étape des départs (autour
     * de la zone préparée, une frange de chunks a passé cette étape-là seulement : mesuré, des tuiles entières manquaient),
     * ni aucun chunk de son emprise ({@value #EMPRISE_MINE} chunks autour) celle des références, sinon des morceaux manqueraient.
     */
    static List<String> defautsEmprise(BlockPos mine, ChunksPrepares prepares) {
        List<String> defauts = new ArrayList<>();
        int cx = mine.getX() >> 4;
        int cz = mine.getZ() >> 4;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (prepares.departs(cx + i * EMPRISE_MINE, cz + j * EMPRISE_MINE)) {
                    defauts.add("chunk de départ déjà préparé");
                }
            }
        }
        int touches = 0;
        for (int dx = -EMPRISE_MINE; dx <= EMPRISE_MINE; dx++) {
            for (int dz = -EMPRISE_MINE; dz <= EMPRISE_MINE; dz++) {
                if (prepares.references(cx + dx, cz + dz)) {
                    touches++;
                }
            }
        }
        if (touches > 0) {
            defauts.add(touches + " chunk(s) de l'emprise déjà préparé(s)");
        }
        return defauts;
    }

    /**
     * La tuile d'un chunk de la mine : l'écart (en x ou en z) entre son départ et le chunk central. Chaque chunk est dans une
     * seule tuile, à {@value #EMPRISE_MINE} / 2 chunks au plus de son départ, donc à sa portée.
     */
    public static int tuile(int ecart) {
        return 2 * Math.abs(ecart) <= EMPRISE_MINE ? 0 : ecart > 0 ? EMPRISE_MINE : -EMPRISE_MINE;
    }

    /** Ce chunk (écart au chunk central) est-il un des 9 départs de la mine ? */
    public static boolean departDeMine(int dx, int dz) {
        return Math.abs(dx) <= EMPRISE_MINE && Math.abs(dz) <= EMPRISE_MINE && dx % EMPRISE_MINE == 0 && dz % EMPRISE_MINE == 0;
    }

    /** Tous les défauts du plan (vide : cohérent). */
    public List<String> defauts(Terrain terrain, ChunksPrepares prepares, Collection<ChunkPos> forts) {
        List<String> defauts = new ArrayList<>();
        for (Region region : Region.values()) {
            BlockPos pos = regions.get(region);
            if (pos == null) {
                defauts.add(region.getSerializedName() + " : absente");
                continue;
            }
            defauts(region, pos, regions, apparition, terrain, prepares, forts)
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
