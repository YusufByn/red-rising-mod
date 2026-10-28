package io.github.yusufbyn.redrising.monde;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le plan de la mine (G7.2.1, GDD section 7) : galeries, puits, caverne de la colonie, emplacements des cellules et bouchon,
 * tiré de la graine du monde et de la position de la mine. Logique pure (testée), sans bloc : la pose est faite chunk par
 * chunk par {@link MineLykos}. Valeurs en 🟡 dans le GDD, toutes ici.
 *
 * <ul>
 *     <li><b>Niveaux</b> : jusqu'à {@value #NIVEAUX_MAX}, sols à y = 8, −10, −28, −46 ; un niveau trop près de la surface
 *     (vallée, mer) est retiré.</li>
 *     <li><b>Galeries</b> : sur chaque niveau, une grille de nœuds tous les {@value #ESPACEMENT} blocs ; un arbre couvrant
 *     tiré au hasard (Kruskal), qui commence par l'axe principal (croix à travers le puits, plus large), puis des boucles,
 *     puis des culs-de-sac coupés au hasard, sans jamais couper un nœud obligatoire. Un arbre relie tous ses nœuds : tout est
 *     relié par construction (et vérifié par les tests).</li>
 *     <li><b>Puits</b> : le principal au centre, de bas en haut jusqu'au bouchon ; deux secondaires entre deux niveaux.</li>
 *     <li><b>Caverne de la colonie</b> (G7.3) sur le niveau 3, au bout de l'axe : parcelles, place et échafaud (G7.10),
 *     point d'apparition du Rouge (G7.8), notés ici pour leurs tickets.</li>
 *     <li><b>Cellules</b> (G7.5) : {@value #CELLULES} emplacements laissés en roche pleine, au bout d'un cul-de-sac ou sur
 *     la paroi d'une galerie ; au moins {@value #CULS_DE_SAC_PAR_CELLULE} culs-de-sac sans cellule pour un avec, tous fermés
 *     par la même roche : un cul-de-sac ne trahit pas une cellule.</li>
 * </ul>
 */
public final class PlanDeMine {
    /** 🟡 Sols des niveaux, du haut vers le bas. */
    static final int[] SOLS = {8, -10, -28, -46};
    public static final int NIVEAUX_MAX = 4;
    /** 🟡 Entre le haut de la mine (vide et coque) et le terrain le plus bas de l'emprise. */
    static final int MARGE_SURFACE = 18;
    /** 🟡 Grille : un nœud tous les 20 blocs, de −100 à 100 autour du centre. */
    public static final int ESPACEMENT = 20;
    static final int DEMI_GRILLE = 5;
    /** 🟡 Part des arêtes de l'arbre gardées après la coupe des culs-de-sac, et des boucles ajoutées. */
    static final double PART_GARDEE = 0.6;
    static final double PART_BOUCLES = 0.12;
    /** Galerie : 3 de large, 3 de haut ; axe principal : 5 de large, 4 de haut. Coque d'un bloc. */
    static final int DEMI_LARGEUR = 1;
    static final int HAUTEUR = 3;
    static final int DEMI_LARGEUR_AXE = 2;
    static final int HAUTEUR_AXE = 4;
    /** Puits : 3 × 3 de vide, coque de 2 blocs. */
    public static final int COQUE_PUITS = 2;
    /** 🟡 Étais tous les 6 blocs. */
    static final int PAS_ETAIS = 6;
    /** 🟡 Caverne : à 48 blocs du puits, 72 × 56 au sol, 22 de haut, sur le niveau 3. */
    static final int DISTANCE_CAVERNE = 48;
    static final int DEMI_X_CAVERNE = 36;
    static final int DEMI_Z_CAVERNE = 28;
    static final int HAUTEUR_CAVERNE = 22;
    static final int NIVEAU_CAVERNE = 2;
    /** 🟡 Cellules : 12 emplacements (3 par niveau), salle de 5 × 3 × 5 et ses murs. */
    public static final int CELLULES = 12;
    public static final int CULS_DE_SAC_PAR_CELLULE = 3;
    static final int DISTANCE_CAVERNE_CELLULE = 40;
    static final int ECART_CELLULES = 24;
    /** 🟡 Puits secondaires : deux entre deux niveaux, à 40 blocs au moins du centre. */
    static final int PUITS_SECONDAIRES = 2;
    /** Bouchon : deux couches, sous au moins 3 blocs du terrain d'origine. */
    static final int PROFONDEUR_BOUCHON = 5;
    /**
     * Le remplissage du relief (adaptation {@code encapsulate}) déborde de 12 blocs autour de ses boîtes : il ne doit pas
     * atteindre la surface, sinon il ferait monter le terrain (une butte au-dessus du puits). La boîte du puits s'arrête à
     * cette distance sous la surface ; au-dessus, sa coque de 2 blocs le protège.
     */
    public static final int BOITE_PUITS_SOUS_SURFACE = 14;

    public enum TypeCellule {
        /** Au bout d'un cul-de-sac, dans son prolongement. */
        BOUT,
        /** Sur la paroi d'une galerie, de côté. */
        PAROI
    }

    /** Un puits : 3 × 3 de vide de {@code bas} à {@code haut} compris, autour de (x, z). */
    public record Puits(int x, int z, int bas, int haut, boolean principal) {
        public BoundingBox interieur() {
            return new BoundingBox(x - 1, bas, z - 1, x + 1, haut, z + 1);
        }
    }

    /** La caverne : une voûte sur un sol plat, au bord irrégulier (trois ondulations, phases tirées de la graine). */
    public record Caverne(int x, int z, int sol, int demiX, int demiZ, int hauteur, double phase1, double phase2, double phase3) {
        public BoundingBox boite() {
            return new BoundingBox(x - demiX - 4, sol, z - demiZ - 4, x + demiX + 4, sol + hauteur, z + demiZ + 4);
        }

        public boolean contient(int bx, int by, int bz) {
            if (by < sol || by > sol + hauteur) {
                return false;
            }
            double dx = (double) (bx - x) / demiX;
            double dz = (double) (bz - z) / demiZ;
            double r = Math.sqrt(dx * dx + dz * dz);
            double bord = 1 + 0.1 * bruit(Math.atan2(dz, dx));
            if (r >= bord) {
                return false;
            }
            double voute = Math.max(5, hauteur * Math.sqrt(1 - (r / bord) * (r / bord)));
            return by < sol + voute;
        }

        /** Bord irrégulier : trois ondulations, entre -1 et 1. */
        private double bruit(double angle) {
            return (Math.sin(angle * 3 + phase1) + Math.sin(angle * 4 + phase2) / 2 + Math.sin(angle * 5 + phase3) / 3) / 1.84;
        }
    }

    /** Ce que la caverne réserve aux tickets suivants. */
    public record Colonie(BoundingBox place, BlockPos echafaud, BlockPos apparition, List<BoundingBox> parcelles) {
    }

    /** Un emplacement de cellule : sa salle (5 × 3 × 5 de vide, à creuser en G7.5) et la galerie d'où on y entre. */
    public record EmplacementCellule(int niveau, TypeCellule type, BoundingBox salle, BlockPos galerie) {
        /** La salle et ses murs. */
        public BoundingBox avecMurs() {
            return salle.inflatedBy(1);
        }
    }

    private final BlockPos centre;
    private final List<Integer> sols;
    private final List<BoundingBox> galeries;
    private final List<Integer> niveauxGaleries;
    private final List<Puits> puits;
    private final Caverne caverne;
    private final Colonie colonie;
    private final List<EmplacementCellule> cellules;
    private final List<BlockPos> culsDeSac;
    private final Set<BlockPos> poteaux;
    private final Set<BlockPos> poutres;
    private final BoundingBox bouchon;
    private final int hautDesBoites;
    private final int hautBoitePuits;

    private PlanDeMine(BlockPos centre, List<Integer> sols, List<BoundingBox> galeries, List<Integer> niveauxGaleries, List<Puits> puits,
                       Caverne caverne, Colonie colonie, List<EmplacementCellule> cellules, List<BlockPos> culsDeSac, Set<BlockPos> poteaux,
                       Set<BlockPos> poutres, BoundingBox bouchon, int hautDesBoites, int hautBoitePuits) {
        this.centre = centre;
        this.sols = List.copyOf(sols);
        this.galeries = List.copyOf(galeries);
        this.niveauxGaleries = List.copyOf(niveauxGaleries);
        this.puits = List.copyOf(puits);
        this.caverne = caverne;
        this.colonie = colonie;
        this.cellules = List.copyOf(cellules);
        this.culsDeSac = List.copyOf(culsDeSac);
        this.poteaux = Set.copyOf(poteaux);
        this.poutres = Set.copyOf(poutres);
        this.bouchon = bouchon;
        this.hautDesBoites = hautDesBoites;
        this.hautBoitePuits = hautBoitePuits;
    }

    // --- Tirage ---

    private record Cle(long graine, BlockPos centre, int solMinimum, int solAuPuits) {
    }

    /** Un plan est tiré une fois par mine, puis relu : la pose de chaque chunk le redemande (fils de génération). */
    private static final Map<Cle, PlanDeMine> CACHE = new ConcurrentHashMap<>();

    /**
     * Le plan de la mine centrée ici.
     *
     * @param graine     la graine du monde
     * @param solMinimum le terrain le plus bas de l'emprise (le haut de la mine reste dessous, avec sa marge)
     * @param solAuPuits le terrain le plus bas autour du puits principal (16 blocs) : le bouchon est dessous, et la boîte de
     *                   remplissage du puits s'arrête à {@value #BOITE_PUITS_SOUS_SURFACE} blocs plus bas
     */
    public static PlanDeMine de(long graine, BlockPos centre, int solMinimum, int solAuPuits) {
        return CACHE.computeIfAbsent(new Cle(graine, centre.immutable(), solMinimum, solAuPuits), cle -> tirer(cle.graine(), cle.centre(), cle.solMinimum(), cle.solAuPuits()));
    }

    /** Un nœud de la grille d'un niveau. */
    private record Noeud(int i, int j) {
        int x(BlockPos centre) {
            return centre.getX() + i * ESPACEMENT;
        }

        int z(BlockPos centre) {
            return centre.getZ() + j * ESPACEMENT;
        }
    }

    private record Arete(Noeud a, Noeud b, boolean axe) {
    }

    static PlanDeMine tirer(long graine, BlockPos centre, int solMinimum, int solAuPuits) {
        Random hasard = new Random(graine ^ centre.asLong() * 0x9E3779B97F4A7C15L);
        // Niveaux sous la surface, avec leur marge
        List<Integer> sols = new ArrayList<>();
        for (int sol : SOLS) {
            if (sol + HAUTEUR_AXE + MARGE_SURFACE <= solMinimum) {
                sols.add(sol);
            }
        }
        if (sols.isEmpty()) {
            sols.add(SOLS[SOLS.length - 1]);
        }
        int niveauCaverne = Math.min(NIVEAU_CAVERNE, sols.size() - 1);

        // Caverne : au bout d'une branche de l'axe, dans une direction tirée
        int direction = hasard.nextInt(4);
        int dx = direction == 0 ? 1 : direction == 1 ? -1 : 0;
        int dz = direction == 2 ? 1 : direction == 3 ? -1 : 0;
        boolean caverneSurX = dx != 0;
        Caverne caverne = new Caverne(centre.getX() + dx * DISTANCE_CAVERNE, centre.getZ() + dz * DISTANCE_CAVERNE, sols.get(niveauCaverne),
                caverneSurX ? DEMI_X_CAVERNE : DEMI_Z_CAVERNE, caverneSurX ? DEMI_Z_CAVERNE : DEMI_X_CAVERNE, HAUTEUR_CAVERNE,
                hasard.nextDouble() * 2 * Math.PI, hasard.nextDouble() * 2 * Math.PI, hasard.nextDouble() * 2 * Math.PI);
        BoundingBox zoneCaverne = caverne.boite().inflatedBy(2);

        // Galeries de chaque niveau
        List<Set<Arete>> aretesParNiveau = new ArrayList<>();
        List<Set<Noeud>> noeudsParNiveau = new ArrayList<>();
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            int sol = sols.get(niveau);
            boolean niveauDeLaCaverne = niveau == niveauCaverne;
            List<Arete> permises = new ArrayList<>();
            for (int i = -DEMI_GRILLE; i <= DEMI_GRILLE; i++) {
                for (int j = -DEMI_GRILLE; j <= DEMI_GRILLE; j++) {
                    for (Noeud voisin : List.of(new Noeud(i + 1, j), new Noeud(i, j + 1))) {
                        if (Math.abs(voisin.i()) > DEMI_GRILLE || Math.abs(voisin.j()) > DEMI_GRILLE) {
                            continue;
                        }
                        Noeud noeud = new Noeud(i, j);
                        boolean axe = (j == 0 && voisin.j() == 0) || (i == 0 && voisin.i() == 0);
                        Arete arete = new Arete(noeud, voisin, axe);
                        boolean versCaverne = niveauDeLaCaverne && axe && (caverneSurX ? j == 0 && Integer.signum(i + voisin.i()) == dx
                                : i == 0 && Integer.signum(j + voisin.j()) == dz);
                        // Rien ne croise la caverne, sauf l'axe qui y mène sur son niveau
                        if (!versCaverne && galerie(arete, sol, centre).inflatedBy(1).intersects(zoneCaverne)) {
                            continue;
                        }
                        permises.add(arete);
                    }
                }
            }
            Set<Arete> arbre = arbre(permises, hasard);
            Set<Noeud> relies = composante(arbre, new Noeud(0, 0));
            arbre.removeIf(arete -> !relies.contains(arete.a()));
            // Des boucles en plus, entre nœuds déjà reliés
            List<Arete> autres = new ArrayList<>(permises);
            autres.removeAll(arbre);
            autres.removeIf(arete -> !relies.contains(arete.a()) || !relies.contains(arete.b()));
            Collections.shuffle(autres, hasard);
            Set<Arete> gardees = new LinkedHashSet<>(arbre);
            gardees.addAll(autres.subList(0, (int) Math.round(autres.size() * PART_BOUCLES)));
            aretesParNiveau.add(gardees);
            noeudsParNiveau.add(relies);
        }

        // Puits secondaires entre deux niveaux : nœuds obligatoires des deux
        List<Puits> puits = new ArrayList<>();
        List<Set<Noeud>> obligatoires = new ArrayList<>();
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            obligatoires.add(new HashSet<>(Set.of(new Noeud(0, 0))));
        }
        for (int haut = 0; haut + 1 < sols.size(); haut++) {
            List<Noeud> candidats = new ArrayList<>(noeudsParNiveau.get(haut));
            candidats.retainAll(noeudsParNiveau.get(haut + 1));
            candidats.sort((a, b) -> a.i() != b.i() ? Integer.compare(a.i(), b.i()) : Integer.compare(a.j(), b.j()));
            Collections.shuffle(candidats, hasard);
            int poses = 0;
            for (Noeud noeud : candidats) {
                if (poses == PUITS_SECONDAIRES) {
                    break;
                }
                int x = noeud.x(centre);
                int z = noeud.z(centre);
                boolean loin = Math.hypot(noeud.i(), noeud.j()) * ESPACEMENT >= 40;
                boolean ecarte = puits.stream().noneMatch(autre -> Math.hypot(autre.x() - x, autre.z() - z) < 30);
                BoundingBox interieur = new Puits(x, z, sols.get(haut + 1), sols.get(haut) + HAUTEUR - 1, false).interieur();
                if (loin && ecarte && !interieur.inflatedBy(COQUE_PUITS).intersects(zoneCaverne)) {
                    puits.add(new Puits(x, z, sols.get(haut + 1), sols.get(haut) + HAUTEUR - 1, false));
                    obligatoires.get(haut).add(noeud);
                    obligatoires.get(haut + 1).add(noeud);
                    poses++;
                }
            }
        }

        // Coupe des culs-de-sac, jamais d'un nœud obligatoire
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            couper(aretesParNiveau.get(niveau), obligatoires.get(niveau), hasard);
        }

        // Géométrie des galeries
        List<BoundingBox> galeries = new ArrayList<>();
        List<Integer> niveauxGaleries = new ArrayList<>();
        Set<BlockPos> poteaux = new HashSet<>();
        Set<BlockPos> poutres = new HashSet<>();
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            for (Arete arete : aretesParNiveau.get(niveau)) {
                BoundingBox boite = galerie(arete, sols.get(niveau), centre);
                galeries.add(boite);
                niveauxGaleries.add(niveau);
                etayer(boite, arete, centre, poteaux, poutres);
            }
        }

        // Puits principal, jusqu'au bouchon
        int hautPuits = solAuPuits - PROFONDEUR_BOUCHON - 1;
        puits.addFirst(new Puits(centre.getX(), centre.getZ(), sols.getLast(), Math.max(hautPuits, sols.getFirst() + HAUTEUR_AXE), true));
        BoundingBox bouchon = new BoundingBox(centre.getX() - 1, hautPuits + 1, centre.getZ() - 1, centre.getX() + 1, hautPuits + 2, centre.getZ() + 1);

        Colonie colonie = colonie(caverne, caverneSurX, dx + dz);
        List<BlockPos> culsDeSac = new ArrayList<>();
        List<EmplacementCellule> cellules = cellules(sols, aretesParNiveau, galeries, puits, caverne, centre, hasard, culsDeSac);
        int hautDesBoites = sols.getFirst() + HAUTEUR_AXE + 2;
        int hautBoitePuits = Math.max(hautDesBoites, solAuPuits - BOITE_PUITS_SOUS_SURFACE);
        // Les étais ne restent pas dans la caverne ni dans les puits
        poteaux.removeIf(pos -> caverne.contient(pos.getX(), pos.getY(), pos.getZ()) || dansUnPuits(puits, pos));
        poutres.removeIf(pos -> caverne.contient(pos.getX(), pos.getY(), pos.getZ()) || dansUnPuits(puits, pos));
        return new PlanDeMine(centre, sols, galeries, niveauxGaleries, puits, caverne, colonie, cellules, culsDeSac, poteaux, poutres, bouchon,
                hautDesBoites, hautBoitePuits);
    }

    private static boolean dansUnPuits(List<Puits> puits, BlockPos pos) {
        return puits.stream().anyMatch(p -> p.interieur().isInside(pos));
    }

    /** La boîte de vide d'une galerie entre deux nœuds (axe : plus large et plus haute). */
    private static BoundingBox galerie(Arete arete, int sol, BlockPos centre) {
        int demi = arete.axe() ? DEMI_LARGEUR_AXE : DEMI_LARGEUR;
        int hauteur = arete.axe() ? HAUTEUR_AXE : HAUTEUR;
        int x1 = arete.a().x(centre);
        int z1 = arete.a().z(centre);
        int x2 = arete.b().x(centre);
        int z2 = arete.b().z(centre);
        return new BoundingBox(Math.min(x1, x2) - demi, sol, Math.min(z1, z2) - demi, Math.max(x1, x2) + demi, sol + hauteur - 1,
                Math.max(z1, z2) + demi);
    }

    /** Étais tous les {@value #PAS_ETAIS} blocs : deux poteaux contre les parois, une poutre au plafond. */
    private static void etayer(BoundingBox boite, Arete arete, BlockPos centre, Set<BlockPos> poteaux, Set<BlockPos> poutres) {
        boolean surX = arete.a().j() == arete.b().j();
        int debut = surX ? boite.minX() : boite.minZ();
        int fin = surX ? boite.maxX() : boite.maxZ();
        int origine = surX ? centre.getX() : centre.getZ();
        for (int t = debut + 3; t <= fin - 3; t += PAS_ETAIS) {
            // Pas d'étai dans un carrefour (près d'une ligne de la grille) : il boucherait la galerie qui croise
            int versNoeud = Math.floorMod(t - origine, ESPACEMENT);
            if (Math.min(versNoeud, ESPACEMENT - versNoeud) <= DEMI_LARGEUR_AXE + 1) {
                continue;
            }
            int bas = boite.minY();
            int plafond = boite.maxY();
            int cote1 = surX ? boite.minZ() : boite.minX();
            int cote2 = surX ? boite.maxZ() : boite.maxX();
            for (int y = bas; y < plafond; y++) {
                poteaux.add(surX ? new BlockPos(t, y, cote1) : new BlockPos(cote1, y, t));
                poteaux.add(surX ? new BlockPos(t, y, cote2) : new BlockPos(cote2, y, t));
            }
            for (int c = cote1; c <= cote2; c++) {
                poutres.add(surX ? new BlockPos(t, plafond, c) : new BlockPos(c, plafond, t));
            }
        }
    }

    /** Arbre couvrant (Kruskal) : l'axe d'abord, puis les autres arêtes dans un ordre tiré. */
    private static Set<Arete> arbre(List<Arete> permises, Random hasard) {
        List<Arete> ordre = new ArrayList<>(permises);
        Collections.shuffle(ordre, hasard);
        ordre.sort((a, b) -> Boolean.compare(b.axe(), a.axe()));
        Map<Noeud, Noeud> parents = new HashMap<>();
        Set<Arete> arbre = new LinkedHashSet<>();
        for (Arete arete : ordre) {
            Noeud ra = racine(parents, arete.a());
            Noeud rb = racine(parents, arete.b());
            if (!ra.equals(rb)) {
                parents.put(ra, rb);
                arbre.add(arete);
            }
        }
        return arbre;
    }

    private static Noeud racine(Map<Noeud, Noeud> parents, Noeud noeud) {
        Noeud courant = noeud;
        while (parents.containsKey(courant)) {
            courant = parents.get(courant);
        }
        return courant;
    }

    private static Set<Noeud> composante(Set<Arete> aretes, Noeud depart) {
        Map<Noeud, List<Noeud>> voisins = voisins(aretes);
        Set<Noeud> vus = new HashSet<>(Set.of(depart));
        Deque<Noeud> file = new ArrayDeque<>(List.of(depart));
        while (!file.isEmpty()) {
            for (Noeud voisin : voisins.getOrDefault(file.poll(), List.of())) {
                if (vus.add(voisin)) {
                    file.add(voisin);
                }
            }
        }
        return vus;
    }

    private static Map<Noeud, List<Noeud>> voisins(Set<Arete> aretes) {
        Map<Noeud, List<Noeud>> voisins = new HashMap<>();
        for (Arete arete : aretes) {
            voisins.computeIfAbsent(arete.a(), n -> new ArrayList<>()).add(arete.b());
            voisins.computeIfAbsent(arete.b(), n -> new ArrayList<>()).add(arete.a());
        }
        return voisins;
    }

    /** Coupe des culs-de-sac au hasard jusqu'à {@link #PART_GARDEE} des arêtes ; jamais un nœud obligatoire ni l'axe. */
    private static void couper(Set<Arete> aretes, Set<Noeud> obligatoires, Random hasard) {
        int cible = (int) Math.round(aretes.size() * PART_GARDEE);
        while (aretes.size() > cible) {
            Map<Noeud, List<Noeud>> voisins = voisins(aretes);
            List<Arete> feuilles = new ArrayList<>();
            for (Arete arete : aretes) {
                if (arete.axe()) {
                    continue;
                }
                boolean feuilleA = voisins.get(arete.a()).size() == 1 && !obligatoires.contains(arete.a());
                boolean feuilleB = voisins.get(arete.b()).size() == 1 && !obligatoires.contains(arete.b());
                if (feuilleA || feuilleB) {
                    feuilles.add(arete);
                }
            }
            if (feuilles.isEmpty()) {
                return;
            }
            aretes.remove(feuilles.get(hasard.nextInt(feuilles.size())));
        }
    }

    // --- Colonie et cellules ---

    private static Colonie colonie(Caverne caverne, boolean surX, int sens) {
        int sol = caverne.sol();
        BlockPos echafaud = new BlockPos(caverne.x(), sol, caverne.z());
        BoundingBox place = new BoundingBox(caverne.x() - 10, sol, caverne.z() - 10, caverne.x() + 9, sol, caverne.z() + 9);
        // Le Rouge apparaît près de l'entrée, côté puits, à 6 blocs du bord
        int bord = (surX ? caverne.demiX() : caverne.demiZ()) - 6;
        BlockPos apparition = surX ? new BlockPos(caverne.x() - sens * bord, sol, caverne.z()) : new BlockPos(caverne.x(), sol, caverne.z() - sens * bord);
        // Parcelles de 7 × 7 sur une grille (allées de 2 blocs), entières sous la voûte, hors de la place et de l'entrée ;
        // les plus proches des parois d'abord, jusqu'à 20
        List<BoundingBox> candidates = new ArrayList<>();
        for (int x = caverne.x() - caverne.demiX(); x <= caverne.x() + caverne.demiX(); x += 9) {
            for (int z = caverne.z() - caverne.demiZ(); z <= caverne.z() + caverne.demiZ(); z += 9) {
                BoundingBox parcelle = new BoundingBox(x - 3, sol, z - 3, x + 3, sol + 5, z + 3);
                if (coinsDans(caverne, parcelle) && !parcelle.inflatedBy(2).intersects(place)
                        && Math.hypot(x - apparition.getX(), z - apparition.getZ()) > 10) {
                    candidates.add(parcelle);
                }
            }
        }
        candidates.sort((a, b) -> Double.compare(distance(b, caverne), distance(a, caverne)));
        List<BoundingBox> parcelles = List.copyOf(candidates.subList(0, Math.min(20, candidates.size())));
        return new Colonie(place, echafaud, apparition, parcelles);
    }

    private static double distance(BoundingBox parcelle, Caverne caverne) {
        BlockPos milieu = parcelle.getCenter();
        return Math.hypot((double) (milieu.getX() - caverne.x()) / caverne.demiX(), (double) (milieu.getZ() - caverne.z()) / caverne.demiZ());
    }

    private static boolean coinsDans(Caverne caverne, BoundingBox boite) {
        return caverne.contient(boite.minX(), boite.minY(), boite.minZ()) && caverne.contient(boite.maxX(), boite.minY(), boite.minZ())
                && caverne.contient(boite.minX(), boite.minY(), boite.maxZ()) && caverne.contient(boite.maxX(), boite.minY(), boite.maxZ())
                && caverne.contient(boite.minX(), boite.maxY(), boite.minZ()) && caverne.contient(boite.maxX(), boite.maxY(), boite.maxZ());
    }

    /**
     * Les emplacements des cellules, 3 par niveau : au bout d'un cul-de-sac (dans son prolongement) ou sur la paroi d'une
     * galerie. Leur salle reste en roche pleine, loin de tout vide de la mine. Au plus un cul-de-sac sur
     * {@value #CULS_DE_SAC_PAR_CELLULE} + 1 porte une cellule. Remplit {@code culsDeSac} (le bout de chaque cul-de-sac).
     */
    private static List<EmplacementCellule> cellules(List<Integer> sols, List<Set<Arete>> aretesParNiveau, List<BoundingBox> galeries,
                                                     List<Puits> puits, Caverne caverne, BlockPos centre, Random hasard, List<BlockPos> culsDeSac) {
        List<EmplacementCellule> cellules = new ArrayList<>();
        int parNiveau = Math.ceilDiv(CELLULES, sols.size());
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            int sol = sols.get(niveau);
            Set<Arete> aretes = aretesParNiveau.get(niveau);
            Map<Noeud, List<Noeud>> voisins = voisins(aretes);
            List<Noeud> feuilles = new ArrayList<>();
            voisins.forEach((noeud, liste) -> {
                if (liste.size() == 1 && !(noeud.i() == 0 && noeud.j() == 0)) {
                    feuilles.add(noeud);
                }
            });
            feuilles.sort((a, b) -> a.i() != b.i() ? Integer.compare(a.i(), b.i()) : Integer.compare(a.j(), b.j()));
            for (Noeud feuille : feuilles) {
                culsDeSac.add(new BlockPos(feuille.x(centre), sol, feuille.z(centre)));
            }
            int boutsPermis = feuilles.size() / (CULS_DE_SAC_PAR_CELLULE + 1);
            Collections.shuffle(feuilles, hasard);
            List<Arete> parois = new ArrayList<>(aretes);
            parois.removeIf(Arete::axe);
            parois.sort((a, b) -> a.a().i() != b.a().i() ? Integer.compare(a.a().i(), b.a().i())
                    : a.a().j() != b.a().j() ? Integer.compare(a.a().j(), b.a().j()) : Integer.compare(a.b().i() * 100 + a.b().j(), b.b().i() * 100 + b.b().j()));
            Collections.shuffle(parois, hasard);
            int poses = 0;
            int bouts = 0;
            int essaiFeuille = 0;
            int essaiParoi = 0;
            while (poses < parNiveau && cellules.size() < CELLULES && (essaiFeuille < feuilles.size() || essaiParoi < parois.size())) {
                boolean auBout = hasard.nextBoolean() && bouts < boutsPermis && essaiFeuille < feuilles.size();
                EmplacementCellule candidat = null;
                if (auBout) {
                    Noeud feuille = feuilles.get(essaiFeuille++);
                    Noeud voisin = voisins.get(feuille).getFirst();
                    int sx = Integer.signum(feuille.i() - voisin.i());
                    int sz = Integer.signum(feuille.j() - voisin.j());
                    // Bout de galerie (demi-largeur 1), 2 blocs de roche, puis la salle de 5 × 5
                    int fx = feuille.x(centre);
                    int fz = feuille.z(centre);
                    int cx = fx + sx * (DEMI_LARGEUR + 2 + 3);
                    int cz = fz + sz * (DEMI_LARGEUR + 2 + 3);
                    candidat = new EmplacementCellule(niveau, TypeCellule.BOUT, new BoundingBox(cx - 2, sol, cz - 2, cx + 2, sol + 2, cz + 2), new BlockPos(fx, sol, fz));
                } else if (essaiParoi < parois.size()) {
                    Arete arete = parois.get(essaiParoi++);
                    boolean surX = arete.a().j() == arete.b().j();
                    int mx = (arete.a().x(centre) + arete.b().x(centre)) / 2;
                    int mz = (arete.a().z(centre) + arete.b().z(centre)) / 2;
                    int cote = hasard.nextBoolean() ? 1 : -1;
                    int cx = surX ? mx : mx + cote * (DEMI_LARGEUR + 2 + 3);
                    int cz = surX ? mz + cote * (DEMI_LARGEUR + 2 + 3) : mz;
                    candidat = new EmplacementCellule(niveau, TypeCellule.PAROI, new BoundingBox(cx - 2, sol, cz - 2, cx + 2, sol + 2, cz + 2), new BlockPos(mx, sol, mz));
                } else if (essaiFeuille < feuilles.size()) {
                    essaiFeuille++;
                    continue;
                }
                if (candidat != null && cellulePossible(candidat, galeries, puits, caverne, cellules, centre)) {
                    cellules.add(candidat);
                    poses++;
                    if (candidat.type() == TypeCellule.BOUT) {
                        bouts++;
                    }
                }
            }
        }
        return cellules;
    }

    private static boolean cellulePossible(EmplacementCellule cellule, List<BoundingBox> galeries, List<Puits> puits, Caverne caverne,
                                           List<EmplacementCellule> autres, BlockPos centre) {
        BoundingBox murs = cellule.avecMurs();
        // Deux blocs de roche au moins entre la salle et tout vide de la mine (la galerie d'accès comprise)
        BoundingBox garde = murs.inflatedBy(1);
        if (galeries.stream().anyMatch(galerie -> galerie.intersects(garde)) || puits.stream().anyMatch(p -> p.interieur().inflatedBy(COQUE_PUITS).intersects(garde))
                || garde.intersects(caverne.boite())) {
            return false;
        }
        int x = cellule.salle().getCenter().getX();
        int z = cellule.salle().getCenter().getZ();
        if (Math.abs(x - centre.getX()) > 118 || Math.abs(z - centre.getZ()) > 118) {
            return false;
        }
        BoundingBox zone = caverne.boite();
        double versCaverne = Math.hypot(Math.max(0, Math.max(zone.minX() - x, x - zone.maxX())), Math.max(0, Math.max(zone.minZ() - z, z - zone.maxZ())));
        return versCaverne >= DISTANCE_CAVERNE_CELLULE && autres.stream().noneMatch(autre -> {
            BlockPos c = autre.salle().getCenter();
            return Math.hypot(c.getX() - x, c.getZ() - z) < ECART_CELLULES;
        });
    }

    // --- Lecture ---

    public BlockPos centre() {
        return centre;
    }

    public List<Integer> sols() {
        return sols;
    }

    public List<BoundingBox> galeries() {
        return galeries;
    }

    /** Le niveau (0 en haut) de chaque galerie, dans l'ordre de {@link #galeries()}. */
    public List<Integer> niveauxGaleries() {
        return niveauxGaleries;
    }

    public List<Puits> puits() {
        return puits;
    }

    public Caverne caverne() {
        return caverne;
    }

    public Colonie colonie() {
        return colonie;
    }

    public List<EmplacementCellule> cellules() {
        return cellules;
    }

    /** Le bout de chaque cul-de-sac (nœud de la grille au sol), avec ou sans cellule. */
    public List<BlockPos> culsDeSac() {
        return culsDeSac;
    }

    public Set<BlockPos> poteaux() {
        return poteaux;
    }

    public Set<BlockPos> poutres() {
        return poutres;
    }

    public BoundingBox bouchon() {
        return bouchon;
    }

    /** Haut des boîtes de remplissage du relief sur toute l'emprise (le haut de la mine et un peu au-dessus). */
    public int hautDesBoites() {
        return hautDesBoites;
    }

    /** Haut de la boîte de remplissage autour du puits principal, sous la surface. */
    public int hautBoitePuits() {
        return hautBoitePuits;
    }

    public Puits puitsPrincipal() {
        return puits.getFirst();
    }

    /** Ce bloc est-il dans le vide de la mine (galeries, puits, caverne) ? Les étais et les échelles y sont aussi. */
    public boolean interieur(int x, int y, int z) {
        for (BoundingBox galerie : galeries) {
            if (galerie.isInside(x, y, z)) {
                return true;
            }
        }
        for (Puits p : puits) {
            if (p.interieur().isInside(x, y, z)) {
                return true;
            }
        }
        return caverne.contient(x, y, z);
    }

    /** Les boîtes de vide de tout ce qui croise cette boîte (galeries, puits, et la boîte de la caverne). */
    public List<BoundingBox> boitesVers(BoundingBox zone) {
        List<BoundingBox> boites = new ArrayList<>();
        for (BoundingBox galerie : galeries) {
            if (galerie.intersects(zone)) {
                boites.add(galerie);
            }
        }
        for (Puits p : puits) {
            if (p.interieur().intersects(zone)) {
                boites.add(p.interieur());
            }
        }
        if (caverne.boite().intersects(zone)) {
            boites.add(caverne.boite());
        }
        return boites;
    }

    /** L'échelle d'un puits, à cette hauteur : contre sa paroi nord, au milieu. */
    public static BlockPos echelle(Puits p, int y) {
        return new BlockPos(p.x(), y, p.z() - 1);
    }

    /**
     * Ce qui tient l'échelle : la paroi nord du puits, sauf là où une galerie la traverse (au niveau d'un carrefour). Là,
     * un pilier de bois prend sa place (un bloc dans une galerie d'au moins 3 de large : on passe à côté), pour que
     * l'échelle monte d'une traite, de bas en haut.
     */
    public static BlockPos pilier(Puits p, int y) {
        return new BlockPos(p.x(), y, p.z() - 2);
    }
}
