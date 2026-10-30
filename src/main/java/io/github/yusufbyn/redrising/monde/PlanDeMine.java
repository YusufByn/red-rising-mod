package io.github.yusufbyn.redrising.monde;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntBinaryOperator;

/**
 * Le plan de la mine (G7.2.1, forme refaite en G7.2.5, GDD section 7) : cavernes, tunnels, puits, colonie, emplacements des
 * cellules et bouchon, tiré de la graine du monde, de la position de la mine et du relief. Logique pure (testée), sans bloc :
 * la pose est faite chunk par chunk par {@link MineLykos}. Valeurs en 🟡 dans le GDD, toutes ici.
 *
 * <ul>
 *     <li><b>Niveaux</b> : {@value #NIVEAUX_MAX}, sols à y = 6, −22, −50. Le vide reste à {@value #MARGE_SURFACE} blocs sous le
 *     terrain <b>au-dessus de lui</b> (terrain local) : une caverne sous une vallée est abaissée ou retirée.</li>
 *     <li><b>Axes</b> : une croix à travers le puits principal sur chaque niveau, 9 de large et 7 de haut, éclairée, avec des
 *     rails.</li>
 *     <li><b>Cavernes</b> : sur une grille de nœuds tous les {@value #ESPACEMENT} blocs, hors des axes, reliées par un arbre
 *     couvrant (tout est relié par construction, et vérifié par les tests) de <b>tunnels</b> sombres de 5 × 5, plus des boucles.</li>
 *     <li><b>Puits</b> : le principal au centre, de bas en haut jusqu'au bouchon, sous la surface de sa propre colonne ; deux
 *     secondaires entre deux niveaux, sur le côté des axes.</li>
 *     <li><b>Colonie</b> (G7.3) sur le niveau 2, la plus grande caverne, au bout d'une branche de l'axe : place et échafaud
 *     (G7.10), point d'apparition du Rouge (G7.8), roche pleine réservée derrière ses parois pour les logements.</li>
 *     <li><b>Culs-de-sac</b> : courts, fermés par la même roche ; un cul-de-sac ne trahit pas une cellule.</li>
 *     <li><b>Cellules</b> (G7.5) : {@value #CELLULES} emplacements laissés en roche pleine, au bout d'un cul-de-sac ou sur la
 *     paroi d'un tunnel ; au moins {@value #CULS_DE_SAC_PAR_CELLULE} culs-de-sac sans cellule pour un avec.</li>
 *     <li><b>Salles d'extraction</b> : des baies dans les parois des cavernes, fronts de taille et salles de forage (niveaux
 *     2 et 3).</li>
 *     <li><b>Filons</b> (G7.2.2) : minerais riches et hélium-3, tirés avec le plan ({@link FilonsDeMine}).</li>
 *     <li><b>Coffres d'outils</b> (G7.2.2, G7.2.5) : les restes des mineurs, sans camp, visibles (une lanterne dessus).</li>
 *     <li><b>Réseau éclairé</b> (G7.2.3) : les axes (de quoi s'orienter sans torche), les paliers des puits secondaires,
 *     l'entrée de la colonie, le cadre des salles de forage ; aucun monstre n'apparaît sur ce réseau.</li>
 *     <li><b>Voies</b> (G7.2.3) : des rails au milieu de chaque branche de l'axe, un butoir à chaque bout, un rail de propulsion
 *     tous les {@value #PAS_PROPULSION} blocs ; à chaque bout, une voie de garage reliée à la voie par une courbe, avec un wagonnet.</li>
 *     <li><b>Repères</b> (G7.2.3) : panneaux aux paliers des puits et le long des axes, une bande de couleur le long de leurs
 *     murs, une teinte par niveau ; et les <b>points d'entrée des Gris</b> pour la traque (G7.2.4).</li>
 * </ul>
 */
public final class PlanDeMine {
    /** 🟡 Sols des niveaux, du haut vers le bas. */
    static final int[] SOLS = {6, -22, -50};
    public static final int NIVEAUX_MAX = 3;
    /** Le niveau d'une position (G7.2.4) : une tranche de 28 blocs centrée sur son sol (la moitié de l'écart entre deux sols). */
    public static final int DEMI_TRANCHE = 14;
    /** 🟡 Entre le haut du vide et le terrain au-dessus de lui. */
    static final int MARGE_SURFACE = 18;
    /** Un filon reste à cette distance sous le terrain. */
    static final int MARGE_FILON = 8;
    /** Le vide de la mine reste à cette distance du centre (l'emprise fait 216 blocs de part et d'autre). */
    public static final int DEMI_MINE = 204;
    /** 🟡 Grille des cavernes : un nœud tous les 72 blocs, à ±72 et ±144 du centre (hors des axes). */
    public static final int ESPACEMENT = 72;
    static final int[] NOEUDS = {-2, -1, 1, 2};
    /** 🟡 Décalage des cavernes autour de leur nœud. */
    static final int JEU_CAVERNE = 6;
    /** 🟡 Cavernes par niveau, du haut vers le bas (la colonie en plus sur son niveau). */
    static final int[] CAVERNES = {6, 5, 8};
    /** 🟡 Cavernes : de 30 à 56 de large, de 12 à 20 de haut. Sous 10 de haut (terrain trop bas), pas de caverne. */
    static final int DEMI_CAVERNE_MIN = 15;
    static final int DEMI_CAVERNE_MAX = 28;
    static final int HAUTEUR_CAVERNE_MIN = 12;
    static final int HAUTEUR_CAVERNE_MAX = 20;
    static final int HAUTEUR_CAVERNE_PLANCHER = 10;
    /** 🟡 Part des liaisons restantes ajoutées en boucles, et leur longueur maximale. */
    static final double PART_BOUCLES = 0.25;
    static final int LONGUEUR_BOUCLE = 120;
    /** Axe principal : 9 de large, 7 de haut, de 196 blocs de chaque côté du puits au plus. */
    static final int DEMI_LARGEUR_AXE = 4;
    static final int HAUTEUR_AXE = 7;
    static final int LONGUEUR_AXE = 196;
    /** Tunnel secondaire : 5 de large, 5 de haut. */
    static final int DEMI_LARGEUR_TUNNEL = 2;
    static final int HAUTEUR_TUNNEL = 5;
    /** 🟡 Culs-de-sac : 3 de large, 4 de haut, de 12 à 20 de long ; 10 par niveau. */
    static final int DEMI_LARGEUR_CUL = 1;
    static final int HAUTEUR_CUL = 4;
    static final int LONGUEUR_CUL_MIN = 12;
    static final int LONGUEUR_CUL_MAX = 20;
    static final int CULS_PAR_NIVEAU = 10;
    /** Puits : 3 × 3 de vide, coque de 2 blocs. */
    public static final int COQUE_PUITS = 2;
    /** Un puits secondaire ressort de 3 blocs dans le niveau du haut. */
    static final int HAUTEUR_TROU = 3;
    /** 🟡 Arches (étais) tous les 12 blocs. */
    static final int PAS_ARCHES = 12;
    /** 🟡 Colonie : au bout d'une branche de l'axe, centrée à 76 blocs du puits, 96 × 72 au sol, jusqu'à 34 de haut, sur le niveau 2. */
    static final int DISTANCE_COLONIE = 76;
    static final int DEMI_LONG_COLONIE = 48;
    static final int DEMI_LARGE_COLONIE = 36;
    public static final int HAUTEUR_COLONIE = 34;
    /** Sous cette hauteur, la colonie ne serait plus la plus grande : le plan des régions évite ces terrains. */
    static final int HAUTEUR_COLONIE_MIN = 22;
    static final int NIVEAU_COLONIE = 1;
    /** 🟡 Roche pleine réservée autour de la colonie, pour les logements creusés dans ses parois (G7.3). */
    public static final int RESERVE_COLONIE = 12;
    /** 🟡 Cellules : 12 emplacements (4 par niveau), salle de 5 × 3 × 5 et ses murs. */
    public static final int CELLULES = 12;
    public static final int CULS_DE_SAC_PAR_CELLULE = 3;
    static final int DISTANCE_COLONIE_CELLULE = 40;
    static final int ECART_CELLULES = 24;
    /** 🟡 Puits secondaires : deux entre deux niveaux. */
    static final int PUITS_SECONDAIRES = 2;
    /** Distances au centre où un puits secondaire peut percer un axe (hors des arches et des panneaux). */
    static final int[] DISTANCES_PUITS = {42, 66, 90, 114, 138};
    /** 🟡 Baies par niveau, du haut vers le bas : fronts de taille, salles de forage. */
    static final int[] FRONTS = {6, 4, 3};
    static final int[] FORAGES = {0, 3, 5};
    /** 🟡 Entre le centre d'une salle et celui d'une cellule. */
    public static final int ECART_SALLE_CELLULE = 24;
    /** Front : 13 de long, 7 de profondeur, 5 de haut. Forage : 13 de diamètre, voûte de 8. */
    static final int DEMI_LONG_FRONT = 6;
    static final int PROFONDEUR_FRONT = 7;
    static final int HAUTEUR_FRONT = 5;
    static final double RAYON_FORAGE = 6.5;
    static final int HAUTEUR_FORAGE = 8;
    /** 🟡 Coffres d'outils : environ 42, à 8 blocs au moins les uns des autres. */
    public static final int COFFRES_VISES = 42;
    static final int ECART_COFFRES = 8;
    /** 🟡 Coffres rares (une pioche en fer très usée) : de 4 à 6, à plus de {@value #DISTANCE_COFFRE_RARE} blocs de la colonie. */
    public static final int COFFRES_RARES_MIN = 4;
    public static final int COFFRES_RARES = 6;
    static final int COFFRES_RARES_VISES = 5;
    /** 🟡 Trois jours de jeu (une heure réelle) entre deux remplissages d'un coffre ({@link CoffresMine}). */
    public static final long DELAI_REMPLISSAGE = 3 * 24000;
    public static final int DISTANCE_COFFRE_RARE = 80;
    /** 🟡 Éclairage de l'axe : tous les 6 blocs, une paire de lanternes sur les côtés ou une au milieu, à 3 blocs du sol. */
    public static final int PAS_LANTERNES = 6;
    static final int HAUTEUR_LANTERNE = 3;
    /** Panneaux « Puits » le long de l'axe : tous les 24 blocs. */
    static final int PAS_PANNEAUX = 24;
    /** 🟡 Voies : un rail de propulsion tous les 16 blocs ; le quai du puits principal (3 blocs) avant le butoir. */
    public static final int PAS_PROPULSION = 16;
    static final int QUAI = 3;
    /** Voie de garage : 6 rails, à 2 blocs de la ligne du milieu, reliés à la voie par une courbe à son bout. */
    static final int LONGUEUR_GARAGE = 6;
    /** Une voie assez longue a un garage à chaque bout ; sinon, un seul. */
    static final int LONGUEUR_DEUX_GARAGES = 24;
    /** 🟡 Bouchon : deux couches, sous 3 blocs du terrain d'origine (au point le plus bas de la colonne du puits). */
    public static final int ROCHE_SUR_BOUCHON = 3;
    /**
     * Le remplissage du relief (adaptation {@code encapsulate}) déborde de 12 blocs autour de ses boîtes : il ne doit pas
     * atteindre la surface, sinon il ferait monter le terrain (une butte au-dessus du puits). La boîte du puits s'arrête à
     * cette distance sous la surface ; au-dessus, sa coque de 2 blocs le protège.
     */
    public static final int BOITE_PUITS_SOUS_SURFACE = 14;

    public enum TypeCellule {
        /** Au bout d'un cul-de-sac, dans son prolongement. */
        BOUT,
        /** Sur la paroi d'un tunnel, de côté. */
        PAROI
    }

    public enum TypeSalle {
        /** Front de taille : une salle droite, ouverte dans la paroi d'une caverne. */
        FRONT,
        /** Salle de forage : ronde, en voûte, un cadre de bois au centre (l'hélium-3, niveaux 2 et 3). */
        FORAGE
    }

    /**
     * Une salle d'extraction : sa boîte, son centre (x, z), et les ouvertures qui la relient à sa caverne (vides en entier ;
     * elles mordent sur la caverne).
     */
    public record Salle(int niveau, TypeSalle type, BoundingBox boite, int x, int z, List<BoundingBox> ouvertures) {
        public boolean contient(int bx, int by, int bz) {
            if (!boite.isInside(bx, by, bz)) {
                return false;
            }
            return switch (type) {
                case FRONT -> true;
                case FORAGE -> {
                    double r = Math.hypot(bx - x, bz - z);
                    if (r > RAYON_FORAGE) {
                        yield false;
                    }
                    double voute = Math.max(3, HAUTEUR_FORAGE * Math.sqrt(1 - (r / RAYON_FORAGE) * (r / RAYON_FORAGE)));
                    yield by < boite.minY() + voute;
                }
            };
        }
    }

    /** Un coffre d'outils : au sol d'une salle, contre un mur, tourné vers la salle ; {@code rare} : la table avec la pioche en fer. */
    public record Coffre(BlockPos pos, Direction face, boolean rare) {
    }

    /** Une lanterne : suspendue (sous une poutre ou au bout d'une chaîne) ou posée. */
    public record Lanterne(BlockPos pos, boolean suspendue) {
    }

    /** Un rail : sa position, sa forme, de propulsion ou non. */
    public record Rail(BlockPos pos, RailShape forme, boolean propulsion) {
    }

    /** Une voie de l'axe : ses rails, ses deux butoirs (le premier côté puits), sa direction (vers l'extérieur) et son niveau. */
    public record Voie(int niveau, Direction sens, List<Rail> rails, BlockPos butoirPuits, BlockPos butoirBout) {
    }

    /**
     * Une voie de garage : ses rails (la courbe qui part de la voie d'abord, puis les rails parallèles à elle), la place de
     * son wagonnet et le butoir à son bout.
     */
    public record Garage(List<Rail> rails, BlockPos wagonnet, BlockPos butoir) {
    }

    /** Ce que dit un panneau : le niveau, la colonie ou le puits (et de quel côté), ou le niveau où mène un puits secondaire. */
    public enum Texte {
        NIVEAU, COLONIE_DROITE, COLONIE_GAUCHE, PUITS_DROITE, PUITS_GAUCHE, PUITS_VERS
    }

    /** Un panneau contre un mur : sa place, vers où il regarde, son texte et le numéro de niveau qu'il affiche (1 en haut). */
    public record Panneau(BlockPos pos, Direction face, Texte texte, int numero) {
    }

    /** Un point d'entrée des Gris (G7.2.4) : au sol du réseau éclairé, et son niveau (0 en haut). */
    public record Entree(BlockPos pos, int niveau) {
    }

    /** Un puits : 3 × 3 de vide de {@code bas} à {@code haut} compris, autour de (x, z). */
    public record Puits(int x, int z, int bas, int haut, boolean principal) {
        public BoundingBox interieur() {
            return new BoundingBox(x - 1, bas, z - 1, x + 1, haut, z + 1);
        }
    }

    /** Une caverne : une voûte sur un sol plat, au bord irrégulier (trois ondulations, phases tirées de la graine). */
    public record Caverne(int x, int z, int sol, int demiX, int demiZ, int hauteur, double phase1, double phase2, double phase3) {
        /** Le bord fait jusqu'à 1,1 fois le demi-axe : la boîte contient tout le vide. */
        public BoundingBox boite() {
            int bordX = (int) Math.ceil(demiX * 1.1) + 1;
            int bordZ = (int) Math.ceil(demiZ * 1.1) + 1;
            return new BoundingBox(x - bordX, sol, z - bordZ, x + bordX, sol + hauteur, z + bordZ);
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

    /** Ce que la colonie réserve aux tickets suivants : place et échafaud, apparition du Rouge, roche pleine pour les logements. */
    public record Colonie(BoundingBox place, BlockPos echafaud, BlockPos apparition, BoundingBox reserve) {
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
    /** Toutes les cavernes ; la colonie d'abord. */
    private final List<Caverne> cavernes;
    private final Colonie colonie;
    private final List<EmplacementCellule> cellules;
    private final List<BlockPos> culsDeSac;
    private final List<Salle> salles;
    /** La salle ou la caverne de chaque boîte, par identité : ce sont les boîtes que rend {@link #boitesVers}. */
    private final Map<BoundingBox, Salle> sallesParBoite = new IdentityHashMap<>();
    private final Map<BoundingBox, Caverne> cavernesParBoite = new IdentityHashMap<>();
    private final List<FilonsDeMine.Bloc> filons;
    private final List<Coffre> coffres;
    private final List<BoundingBox> axes;
    private final List<Integer> niveauxAxes;
    private final List<Lanterne> lanternes;
    /** Les chaînes qui tiennent les lanternes du milieu de l'axe. */
    private final Set<BlockPos> chaines = new HashSet<>();
    private final List<Voie> voies = new ArrayList<>();
    private final List<Garage> garages = new ArrayList<>();
    private final List<Panneau> panneaux = new ArrayList<>();
    /** La bande de couleur de l'axe : chaque bloc de mur, et la ligne de son niveau (0 à 2, comptée pour 3 niveaux). */
    private final Map<BlockPos, Integer> bandes = new java.util.LinkedHashMap<>();
    private final List<Entree> entrees = new ArrayList<>();
    /** Les blocs de filon de chaque chunk, pour la pose. */
    private final Map<Long, List<FilonsDeMine.Bloc>> filonsParChunk = new HashMap<>();
    private final Set<BlockPos> poteaux;
    private final Set<BlockPos> poutres;
    private final BoundingBox bouchon;
    private final int hautDesBoites;
    private final int hautBoitePuits;
    /** Le terrain au-dessus de la mine, lu une fois tous les 32 blocs. */
    private final Relief relief;

    private PlanDeMine(Relief relief, BlockPos centre, List<Integer> sols, List<BoundingBox> galeries, List<Integer> niveauxGaleries, List<Puits> puits,
                       List<Caverne> cavernes, Colonie colonie, List<EmplacementCellule> cellules, List<BlockPos> culsDeSac, List<Salle> salles,
                       Set<BlockPos> poteaux, Set<BlockPos> poutres, BoundingBox bouchon, int hautDesBoites, int hautBoitePuits, List<BoundingBox> axes,
                       List<Integer> niveauxAxes, Random hasard) {
        this.relief = relief;
        this.centre = centre;
        this.sols = List.copyOf(sols);
        this.galeries = List.copyOf(galeries);
        this.niveauxGaleries = List.copyOf(niveauxGaleries);
        this.puits = List.copyOf(puits);
        this.cavernes = List.copyOf(cavernes);
        this.colonie = colonie;
        this.cellules = List.copyOf(cellules);
        this.culsDeSac = List.copyOf(culsDeSac);
        this.salles = List.copyOf(salles);
        this.salles.forEach(salle -> sallesParBoite.put(salle.boite(), salle));
        this.cavernes.forEach(caverne -> cavernesParBoite.put(caverne.boite(), caverne));
        this.poteaux = new HashSet<>(poteaux);
        this.poutres = new HashSet<>(poutres);
        this.bouchon = bouchon;
        this.hautDesBoites = hautDesBoites;
        this.hautBoitePuits = hautBoitePuits;
        // Les filons en dernier : ils lisent tout le reste du plan (le vide, les cellules, le bouchon)
        this.filons = FilonsDeMine.tirer(this, hasard);
        for (FilonsDeMine.Bloc bloc : filons) {
            filonsParChunk.computeIfAbsent(cleChunk(bloc.pos().getX() >> 4, bloc.pos().getZ() >> 4), cle -> new ArrayList<>()).add(bloc);
        }
        this.axes = List.copyOf(axes);
        this.niveauxAxes = List.copyOf(niveauxAxes);
        this.coffres = coffres(hasard);
        this.lanternes = placerLanternes();
        poserVoies();
        poserReperes();
        choisirEntrees();
    }

    // --- Repères et points d'entrée des Gris (G7.2.3) ---

    /**
     * Les panneaux : au quai du puits principal sur chaque niveau (« Niveau N »), au quai de la branche de la colonie sur son
     * niveau (« Colonie → », la flèche du côté de la colonie pour qui lit), le long de chaque branche de l'axe tous les
     * {@value #PAS_PANNEAUX} blocs (« Puits → », pour s'orienter sans torche), aux deux bouts de chaque puits secondaire
     * (« Puits → niveau N »). La bande de couleur : le mur de chaque côté de l'axe, à hauteur d'yeux.
     */
    private void poserReperes() {
        int niveauColonie = sols.indexOf(caverne().sol());
        Direction versColonie = Direction.getApproximateNearest(caverne().x() - centre.getX(), 0, caverne().z() - centre.getZ());
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            int sol = sols.get(niveau);
            Panneau niveauPose = null;
            for (Direction sens : Direction.Plane.HORIZONTAL) {
                for (Direction cote : List.of(sens.getClockWise(), sens.getCounterClockWise())) {
                    BlockPos pos = centre.atY(sol + 1).relative(sens, QUAI + 3).relative(cote, DEMI_LARGEUR_AXE);
                    BlockPos appui = pos.relative(cote);
                    if (!interieur(pos.getX(), pos.getY(), pos.getZ()) || interieur(appui.getX(), appui.getY(), appui.getZ())
                            || (niveauPose != null && niveauPose.pos().equals(pos))) {
                        continue;
                    }
                    if (niveauPose == null && (niveau != niveauColonie || sens != versColonie)) {
                        niveauPose = new Panneau(pos, cote.getOpposite(), Texte.NIVEAU, niveau + 1);
                        panneaux.add(niveauPose);
                    } else if (niveau == niveauColonie && sens == versColonie && panneaux.stream().noneMatch(p -> p.texte() == Texte.COLONIE_DROITE || p.texte() == Texte.COLONIE_GAUCHE)) {
                        // Pour qui regarde le mur (vers cote), sa droite est cote.getClockWise()
                        Texte texte = versColonie == cote.getClockWise() ? Texte.COLONIE_DROITE : Texte.COLONIE_GAUCHE;
                        panneaux.add(new Panneau(pos, cote.getOpposite(), texte, niveau + 1));
                    }
                }
            }
            // Le long de l'axe : le chemin du puits, sur le mur de gauche en s'éloignant
            for (Direction sens : Direction.Plane.HORIZONTAL) {
                Direction cote = sens.getCounterClockWise();
                for (int d = PAS_PANNEAUX + 3; d <= LONGUEUR_AXE; d += PAS_PANNEAUX) {
                    BlockPos pos = centre.atY(sol + 1).relative(sens, d).relative(cote, DEMI_LARGEUR_AXE);
                    BlockPos appui = pos.relative(cote);
                    if (dansUnAxe(pos) && interieur(pos.getX(), pos.getY(), pos.getZ()) && !interieur(appui.getX(), appui.getY(), appui.getZ())
                            && !dansUnPuits(puits, pos)) {
                        Texte texte = sens.getOpposite() == cote.getClockWise() ? Texte.PUITS_DROITE : Texte.PUITS_GAUCHE;
                        panneaux.add(new Panneau(pos, cote.getOpposite(), texte, niveau + 1));
                    }
                }
            }
        }
        for (Puits p : puits) {
            if (p.principal()) {
                continue;
            }
            int bas = sols.indexOf(p.bas());
            int haut = sols.indexOf(p.haut() - HAUTEUR_TROU + 1);
            panneauDePuits(p, p.bas() + 1, haut + 1);
            panneauDePuits(p, p.haut() - HAUTEUR_TROU + 2, bas + 1);
        }
        // La bande : de chaque côté de l'axe, à un bloc au-dessus du sol, là où le mur est de la roche
        for (int i = 0; i < axes.size(); i++) {
            BoundingBox axe = axes.get(i);
            int ligne = NIVEAUX_MAX - sols.size() + niveauxAxes.get(i);
            boolean surX = axe.getXSpan() > axe.getZSpan();
            int y = axe.minY() + 1;
            for (int t = surX ? axe.minX() : axe.minZ(); t <= (surX ? axe.maxX() : axe.maxZ()); t++) {
                for (int travers : new int[]{(surX ? axe.minZ() : axe.minX()) - 1, (surX ? axe.maxZ() : axe.maxX()) + 1}) {
                    BlockPos pos = surX ? new BlockPos(t, y, travers) : new BlockPos(travers, y, t);
                    if (!interieur(pos.getX(), pos.getY(), pos.getZ()) && !caverne().boite().isInside(pos)) {
                        bandes.putIfAbsent(pos, ligne);
                    }
                }
            }
        }
    }

    private boolean dansUnAxe(BlockPos pos) {
        return axes.stream().anyMatch(axe -> axe.isInside(pos));
    }

    /**
     * Un panneau « Puits → niveau N » à cette hauteur : contre une paroi du puits (à côté de l'échelle, pas sur elle) ; là où
     * le puits s'ouvre sur l'axe, contre le mur de l'axe, à 3 à 5 blocs du puits.
     */
    private void panneauDePuits(Puits p, int y, int numero) {
        BlockPos milieu = new BlockPos(p.x(), y, p.z());
        List<BlockPos[]> candidats = new ArrayList<>();
        for (Direction paroi : Direction.Plane.HORIZONTAL) {
            for (int k : new int[]{1, -1, 0}) {
                BlockPos pos = milieu.relative(paroi).relative(paroi.getClockWise(), k);
                candidats.add(new BlockPos[]{pos, pos.relative(paroi)});
            }
        }
        for (int distance = 3; distance <= 5; distance++) {
            for (Direction galerie : Direction.Plane.HORIZONTAL) {
                for (Direction cote : List.of(galerie.getClockWise(), galerie.getCounterClockWise())) {
                    // Le puits est sur le côté de l'axe : son mur est à un bloc de la ligne du puits d'un côté, à 7 de l'autre
                    for (int ecart : new int[]{1, 7}) {
                        BlockPos pos = milieu.relative(galerie, distance).relative(cote, ecart);
                        candidats.add(new BlockPos[]{pos, pos.relative(cote)});
                    }
                }
            }
        }
        for (BlockPos[] candidat : candidats) {
            BlockPos pos = candidat[0];
            BlockPos appui = candidat[1];
            if (interieur(pos.getX(), pos.getY(), pos.getZ()) && !interieur(appui.getX(), appui.getY(), appui.getZ())
                    && !pos.equals(echelle(p, y)) && puits.stream().noneMatch(autre -> pilier(autre, y).equals(appui) || pilier(autre, y).equals(pos))
                    && lanternes.stream().noneMatch(l -> l.pos().equals(pos)) && panneaux.stream().noneMatch(autre -> autre.pos().equals(pos))) {
                Direction face = Direction.getApproximateNearest(pos.getX() - appui.getX(), 0, pos.getZ() - appui.getZ());
                panneaux.add(new Panneau(pos, face, Texte.PUITS_VERS, numero));
                return;
            }
        }
    }

    /**
     * Les points d'entrée des Gris, tous au sol du réseau éclairé, où un Gris tient debout : le quai du puits principal dans
     * chaque direction, des points le long de l'axe (à côté des rails), le fond de chaque puits secondaire. La traque
     * (G7.2.4) y fait apparaître ses chasseurs, hors de vue, sur le niveau du joueur ou un voisin.
     */
    private void choisirEntrees() {
        Set<BlockPos> sol = new HashSet<>(reseauEclaire());
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            BlockPos milieu = centre.atY(sols.get(niveau));
            for (Direction sens : Direction.Plane.HORIZONTAL) {
                ajouterEntree(sol, milieu.relative(sens, 3), niveau);
                for (int d = 24; d <= LONGUEUR_AXE; d += 24) {
                    ajouterEntree(sol, milieu.relative(sens, d).relative(sens.getClockWise(), 2), niveau);
                }
            }
        }
        for (Puits p : puits) {
            if (!p.principal()) {
                ajouterEntree(sol, new BlockPos(p.x(), p.bas(), p.z()), sols.indexOf(p.bas()));
            }
        }
    }

    private void ajouterEntree(Set<BlockPos> sol, BlockPos pos, int niveau) {
        if (sol.contains(pos) && interieur(pos.getX(), pos.getY() + 1, pos.getZ()) && !interieur(pos.getX(), pos.getY() - 1, pos.getZ())) {
            entrees.add(new Entree(pos, niveau));
        }
    }

    // --- Voies (G7.2.3) ---

    /**
     * Les voies de l'axe : sur chaque niveau, dans chaque direction, de {@value #QUAI} blocs après le trou du puits principal
     * (le quai) jusqu'au premier obstacle de la ligne du milieu (fin de l'axe, colonie). Un butoir à chaque bout ; un rail de
     * propulsion tous les {@value #PAS_PROPULSION} blocs (une torche de redstone noyée deux blocs dessous). À chaque bout
     * (un seul si la voie est courte), une voie de garage.
     */
    private void poserVoies() {
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            int sol = sols.get(niveau);
            for (Direction sens : Direction.Plane.HORIZONTAL) {
                BlockPos depart = centre.atY(sol).relative(sens, 2 + QUAI);
                BlockPos.MutableBlockPos pos = depart.mutable();
                int longueur = 0;
                // Jusqu'au premier obstacle : vide de l'axe au sol et au-dessus, sol plein dessous, pas de colonie
                while (interieur(pos.getX(), sol, pos.getZ()) && interieur(pos.getX(), sol + 1, pos.getZ()) && !interieur(pos.getX(), sol - 1, pos.getZ())
                        && !caverne().contient(pos.getX(), sol, pos.getZ()) && enLigne(pos, sol)) {
                    longueur++;
                    pos.move(sens);
                }
                // Butoir côté puits, rails, butoir au bout : au moins 12 rails
                if (longueur < 14) {
                    continue;
                }
                boolean surX = sens.getAxis() == Direction.Axis.X;
                RailShape droit = surX ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
                List<Rail> rails = new ArrayList<>();
                for (int t = 1; t < longueur - 1; t++) {
                    BlockPos rail = depart.relative(sens, t);
                    // Propulsion : la torche deux blocs dessous, posée sur la roche, sans vide autour
                    boolean propulsion = (t - 1) % PAS_PROPULSION == PAS_PROPULSION / 2 && t > LONGUEUR_GARAGE && t < longueur - 1 - LONGUEUR_GARAGE
                            && !interieur(rail.getX(), sol - 2, rail.getZ()) && !interieur(rail.getX(), sol - 3, rail.getZ());
                    rails.add(new Rail(rail, droit, propulsion));
                }
                // Un garage à chaque bout : le dernier rail de la voie devient la courbe qui y mène
                Direction cote = niveau % 2 == 0 ? sens.getClockWise() : sens.getCounterClockWise();
                Garage duPuits = garage(rails.getFirst().pos(), sens, cote);
                if (duPuits != null) {
                    rails.set(0, new Rail(rails.getFirst().pos(), courbe(sens, cote), false));
                    garages.add(duPuits);
                }
                if (longueur >= LONGUEUR_DEUX_GARAGES) {
                    Garage duBout = garage(rails.getLast().pos(), sens.getOpposite(), cote.getOpposite());
                    if (duBout != null) {
                        rails.set(rails.size() - 1, new Rail(rails.getLast().pos(), courbe(sens.getOpposite(), cote.getOpposite()), false));
                        garages.add(duBout);
                    }
                }
                voies.add(new Voie(niveau, sens, List.copyOf(rails), depart, depart.relative(sens, longueur - 1)));
            }
        }
    }

    /** Ce bloc est-il sur la ligne du milieu de l'axe (la croix qui passe par le puits principal) ? */
    private boolean enLigne(BlockPos pos, int sol) {
        return axes.stream().anyMatch(axe -> axe.isInside(pos.getX(), sol, pos.getZ()));
    }

    /** La forme d'un rail courbe qui relie ces deux directions. */
    private static RailShape courbe(Direction a, Direction b) {
        boolean nord = a == Direction.NORTH || b == Direction.NORTH;
        boolean est = a == Direction.EAST || b == Direction.EAST;
        return nord ? est ? RailShape.NORTH_EAST : RailShape.NORTH_WEST : est ? RailShape.SOUTH_EAST : RailShape.SOUTH_WEST;
    }

    /**
     * Une voie de garage au bout d'une voie, comme une boucle : le bout de la voie ({@code bout}) tourne vers {@code cote},
     * un rail de travers, une courbe, puis {@value #LONGUEUR_GARAGE} rails parallèles à la voie, dans le sens {@code retour}
     * (vers le reste de la voie), et un butoir. Un wagonnet n'y entre que s'il arrive au bout de la voie ; poussé, il en
     * ressort par la courbe sur la voie. Rend {@code null} si le sol ne s'y prête pas.
     */
    private @Nullable Garage garage(BlockPos bout, Direction retour, Direction cote) {
        boolean retourSurX = retour.getAxis() == Direction.Axis.X;
        RailShape parallele = retourSurX ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        RailShape travers = retourSurX ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST;
        BlockPos milieu = bout.relative(cote);
        BlockPos virage = bout.relative(cote, 2);
        List<Rail> rails = new ArrayList<>(List.of(new Rail(milieu, travers, false), new Rail(virage, courbe(cote.getOpposite(), retour), false)));
        for (int t = 1; t <= LONGUEUR_GARAGE; t++) {
            rails.add(new Rail(virage.relative(retour, t), parallele, false));
        }
        BlockPos butoir = virage.relative(retour, LONGUEUR_GARAGE + 1);
        for (Rail rail : rails) {
            if (!auSol(rail.pos())) {
                return null;
            }
        }
        if (!auSol(butoir)) {
            return null;
        }
        return new Garage(List.copyOf(rails), virage.relative(retour, LONGUEUR_GARAGE / 2), butoir);
    }

    /** Du vide sur deux blocs, le sol plein dessous, hors des puits. */
    private boolean auSol(BlockPos pos) {
        return interieur(pos.getX(), pos.getY(), pos.getZ()) && interieur(pos.getX(), pos.getY() + 1, pos.getZ())
                && !interieur(pos.getX(), pos.getY() - 1, pos.getZ()) && !dansUnPuits(puits, pos) && !poteaux.contains(pos)
                && puits.stream().noneMatch(p -> pilier(p, pos.getY()).equals(pos));
    }

    // --- Réseau éclairé (G7.2.3) ---

    /**
     * Les lanternes : le long de chaque branche de l'axe, tous les {@value #PAS_LANTERNES} blocs, tantôt une paire sur les
     * côtés (sous une console fixée à l'arche), tantôt une au milieu (au bout d'une chaîne), à {@value #HAUTEUR_LANTERNE} blocs
     * du sol : pas au-dessus d'un puits ni dans la colonie. Au palier du bas de chaque puits secondaire (posée au sol, dans un
     * coin) et en haut (sous le plafond) ; deux à l'entrée de la colonie ; une sous le cadre de chaque salle de forage.
     */
    private List<Lanterne> placerLanternes() {
        Map<BlockPos, Lanterne> liste = new java.util.LinkedHashMap<>();
        for (int i = 0; i < axes.size(); i++) {
            BoundingBox axe = axes.get(i);
            int sol = axe.minY();
            for (Direction sens : Direction.Plane.HORIZONTAL) {
                Direction cote = sens.getClockWise();
                BlockPos milieu = centre.atY(sol + HAUTEUR_LANTERNE);
                for (int d = PAS_LANTERNES; d <= LONGUEUR_AXE + DEMI_LARGEUR_AXE; d += PAS_LANTERNES) {
                    BlockPos ligne = milieu.relative(sens, d);
                    if (!axe.isInside(ligne) || caverne().contient(ligne.getX(), sol, ligne.getZ())) {
                        continue;
                    }
                    // Une paire à chaque arche, une au milieu entre deux ; au bout de la branche, les trois (il n'y a pas d'autre lanterne après)
                    boolean auBout = !axe.isInside(milieu.relative(sens, d + PAS_LANTERNES));
                    if (d % (2 * PAS_LANTERNES) == 0 || auBout) {
                        for (int l : new int[]{DEMI_LARGEUR_AXE - 1, -(DEMI_LARGEUR_AXE - 1)}) {
                            BlockPos lanterne = ligne.relative(cote, l);
                            if (!dansUnPuits(puits, lanterne) && !dansUnPuits(puits, lanterne.below())) {
                                liste.putIfAbsent(lanterne, new Lanterne(lanterne, true));
                                poutres.add(lanterne.above());
                            }
                        }
                    }
                    if ((d % (2 * PAS_LANTERNES) != 0 || auBout) && !dansUnPuits(puits, ligne)) {
                        liste.putIfAbsent(ligne, new Lanterne(ligne, true));
                        for (BlockPos chaine = ligne.above(); chaine.getY() <= axe.maxY(); chaine = chaine.above()) {
                            chaines.add(chaine);
                        }
                    }
                }
            }
        }
        for (Puits p : puits) {
            if (p.principal()) {
                continue;
            }
            // En bas : au sol, dans le coin opposé à l'échelle (paroi nord)
            BlockPos bas = new BlockPos(p.x() + 1, p.bas(), p.z() + 1);
            liste.putIfAbsent(bas, new Lanterne(bas, false));
            // En haut : sous le plafond de l'axe, à côté du trou
            int y = p.haut();
            while (interieur(p.x() + 1, y + 1, p.z() + 1)) {
                y++;
            }
            BlockPos haut = new BlockPos(p.x() + 1, y, p.z() + 1);
            liste.putIfAbsent(haut, new Lanterne(haut, true));
        }
        // Entrée de la colonie : au bout de l'axe qui y mène, deux lanternes au sol de part et d'autre
        Caverne colonieCaverne = caverne();
        int sens = Integer.signum((colonieCaverne.x() - centre.getX()) + (colonieCaverne.z() - centre.getZ()));
        boolean surX = colonieCaverne.x() != centre.getX();
        for (int t = 0; t <= DISTANCE_COLONIE; t++) {
            int x = surX ? centre.getX() + sens * t : centre.getX();
            int z = surX ? centre.getZ() : centre.getZ() + sens * t;
            if (colonieCaverne.contient(x, colonieCaverne.sol(), z)) {
                for (int cote = -1; cote <= 1; cote += 2) {
                    BlockPos pos = surX ? new BlockPos(x + 2 * sens, colonieCaverne.sol(), z + 3 * cote) : new BlockPos(x + 3 * cote, colonieCaverne.sol(), z + 2 * sens);
                    if (colonieCaverne.contient(pos.getX(), pos.getY(), pos.getZ())) {
                        liste.putIfAbsent(pos, new Lanterne(pos, false));
                    }
                }
                break;
            }
        }
        for (Salle salle : salles) {
            if (salle.type() == TypeSalle.FORAGE) {
                BlockPos pos = new BlockPos(salle.x(), salle.boite().minY() + 3, salle.z());
                liste.putIfAbsent(pos, new Lanterne(pos, true));
            }
        }
        // La lanterne posée sur chaque coffre : on le voit de loin
        for (Coffre coffre : coffres) {
            BlockPos pos = coffre.pos().above();
            liste.putIfAbsent(pos, new Lanterne(pos, false));
        }
        return List.copyOf(liste.values());
    }

    /**
     * Le sol du réseau éclairé, où aucun monstre ne doit apparaître : chaque bloc de vide au ras du sol de l'axe (hors de la
     * colonie, éclairée en G7.3, du trou des puits, des piliers qui tiennent leur échelle et des butoirs), et le fond de chaque
     * puits secondaire.
     */
    public List<BlockPos> reseauEclaire() {
        Set<BlockPos> sol = new LinkedHashSet<>();
        for (BoundingBox axe : axes) {
            for (BlockPos pos : BlockPos.betweenClosed(axe.minX(), axe.minY(), axe.minZ(), axe.maxX(), axe.minY(), axe.maxZ())) {
                if (!caverne().contient(pos.getX(), pos.getY(), pos.getZ()) && !dansUnPuits(puits, pos) && !dansUnPuits(puits, pos.below())
                        && puits.stream().noneMatch(p -> pilier(p, pos.getY()).equals(pos) && pos.getY() >= p.bas() && pos.getY() <= p.haut())) {
                    sol.add(pos.immutable());
                }
            }
        }
        for (Puits p : puits) {
            if (!p.principal()) {
                BlockPos.betweenClosed(p.x() - 1, p.bas(), p.z() - 1, p.x() + 1, p.bas(), p.z() + 1).forEach(pos -> sol.add(pos.immutable()));
            }
        }
        // Les butoirs sont des blocs pleins
        for (Voie voie : voies) {
            sol.remove(voie.butoirPuits());
            sol.remove(voie.butoirBout());
        }
        for (Garage garage : garages) {
            sol.remove(garage.butoir());
        }
        return List.copyOf(sol);
    }

    /**
     * Les coffres d'outils, visibles : contre le mur, une lanterne posée dessus. Un à la paroi de chaque caverne (pas la
     * colonie), une salle sur deux, le reste le long des tunnels, jusqu'à {@value #COFFRES_VISES}. Puis les coffres rares :
     * {@value #COFFRES_RARES_VISES} (entre {@value #COFFRES_RARES_MIN} et {@value #COFFRES_RARES}), la plupart au niveau du bas,
     * à plus de {@value #DISTANCE_COFFRE_RARE} blocs de la colonie.
     */
    private List<Coffre> coffres(Random hasard) {
        List<Coffre> liste = new ArrayList<>();
        for (Caverne caverne : cavernes.subList(1, cavernes.size())) {
            for (int essai = 0; essai < 8; essai++) {
                Direction sens = Direction.from2DDataValue(hasard.nextInt(4));
                BlockPos pos = new BlockPos(caverne.x(), caverne.sol(), caverne.z());
                while (caverne.contient(pos.getX() + sens.getStepX(), caverne.sol(), pos.getZ() + sens.getStepZ())) {
                    pos = pos.relative(sens);
                }
                if (ajouterCoffre(liste, pos, sens)) {
                    break;
                }
            }
        }
        List<Salle> ordre = new ArrayList<>(salles);
        Collections.shuffle(ordre, hasard);
        for (Salle salle : ordre.subList(0, ordre.size() / 2)) {
            coffreContreUnMur(salle, liste, hasard);
        }
        // Le long des tunnels (hors des axes)
        List<BoundingBox> tunnels = galeries.stream().filter(g -> axes.stream().noneMatch(axe -> axe == g) && g.getYSpan() == HAUTEUR_TUNNEL).toList();
        for (int essai = 0; essai < 1000 && liste.size() < COFFRES_VISES && !tunnels.isEmpty(); essai++) {
            BoundingBox tunnel = tunnels.get(hasard.nextInt(tunnels.size()));
            boolean surX = tunnel.getXSpan() > tunnel.getZSpan();
            int t = surX ? tunnel.minX() + hasard.nextInt(tunnel.getXSpan()) : tunnel.minZ() + hasard.nextInt(tunnel.getZSpan());
            boolean plus = hasard.nextBoolean();
            BlockPos pos = surX ? new BlockPos(t, tunnel.minY(), plus ? tunnel.maxZ() : tunnel.minZ()) : new BlockPos(plus ? tunnel.maxX() : tunnel.minX(), tunnel.minY(), t);
            Direction versMur = surX ? (plus ? Direction.SOUTH : Direction.NORTH) : (plus ? Direction.EAST : Direction.WEST);
            ajouterCoffre(liste, pos, versMur);
        }
        // Les rares : loin de la colonie, la plupart au fond
        BoundingBox zone = caverne().boite();
        List<Coffre> lointains = new ArrayList<>(liste.stream().filter(c -> distance(zone, c.pos().getX(), c.pos().getZ()) > DISTANCE_COFFRE_RARE).toList());
        Collections.shuffle(lointains, hasard);
        lointains.sort((a, b) -> Integer.compare(a.pos().getY(), b.pos().getY()));
        Set<BlockPos> rares = new HashSet<>();
        // Un en haut, un au milieu, le reste au fond
        for (int niveau = 0; niveau < sols.size() - 1 && rares.size() < COFFRES_RARES_VISES - 1; niveau++) {
            int sol = sols.get(niveau);
            lointains.stream().filter(c -> c.pos().getY() == sol).findFirst().ifPresent(c -> rares.add(c.pos()));
        }
        for (Coffre coffre : lointains) {
            if (rares.size() == COFFRES_RARES_VISES) {
                break;
            }
            rares.add(coffre.pos());
        }
        return liste.stream().map(c -> new Coffre(c.pos(), c.face(), rares.contains(c.pos()))).toList();
    }

    /** Un coffre doit-il se remplir ? Vide, et son dernier remplissage assez vieux ({@code null} : jamais ouvert). */
    public static boolean coffreDoitSeRemplir(@Nullable Long dernier, boolean vide, long maintenant) {
        return vide && dernier != null && maintenant - dernier >= DELAI_REMPLISSAGE;
    }

    private static double distance(BoundingBox boite, int x, int z) {
        return Math.hypot(Math.max(0, Math.max(boite.minX() - x, x - boite.maxX())), Math.max(0, Math.max(boite.minZ() - z, z - boite.maxZ())));
    }

    /**
     * Un coffre ici, tourné à l'opposé du mur, s'il tient : au sol, le mur derrière lui, de la place au-dessus pour sa
     * lanterne, hors de la colonie et des axes, loin d'un autre coffre et d'une cellule, devant aucun étai ni ouverture.
     */
    private boolean ajouterCoffre(List<Coffre> liste, BlockPos pos, Direction versMur) {
        BlockPos mur = pos.relative(versMur);
        boolean libre = interieur(pos.getX(), pos.getY(), pos.getZ()) && interieur(pos.getX(), pos.getY() + 1, pos.getZ())
                && interieur(pos.getX(), pos.getY() + 2, pos.getZ()) && !interieur(pos.getX(), pos.getY() - 1, pos.getZ())
                && !interieur(mur.getX(), mur.getY(), mur.getZ()) && !poteaux.contains(pos) && !poutres.contains(pos.above())
                && !colonie.reserve().isInside(pos) && axes.stream().noneMatch(axe -> axe.isInside(pos)) && !dansUnPuits(puits, pos)
                && liste.stream().noneMatch(autre -> autre.pos().closerThan(pos, ECART_COFFRES))
                && cellules.stream().noneMatch(cellule -> cellule.avecMurs().inflatedBy(3).isInside(pos))
                && salles.stream().noneMatch(salle -> salle.ouvertures().stream().anyMatch(ouverture -> ouverture.inflatedBy(1).isInside(pos)));
        if (libre) {
            liste.add(new Coffre(pos, versMur.getOpposite(), false));
        }
        return libre;
    }

    /** Un coffre au sol de cette salle, contre un mur, s'il y a la place. */
    private void coffreContreUnMur(Salle salle, List<Coffre> liste, Random hasard) {
        BoundingBox b = salle.boite();
        int sol = b.minY();
        for (int essai = 0; essai < 60; essai++) {
            BlockPos pos = new BlockPos(b.minX() + hasard.nextInt(b.getXSpan()), sol, b.minZ() + hasard.nextInt(b.getZSpan()));
            if (!salle.contient(pos.getX(), sol, pos.getZ())) {
                continue;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos voisin = pos.relative(direction);
                if (!interieur(voisin.getX(), voisin.getY(), voisin.getZ()) && ajouterCoffre(liste, pos, direction)) {
                    return;
                }
            }
        }
    }

    private static long cleChunk(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    // --- Tirage ---

    private record Cle(long graine, BlockPos centre, int solAuPuits, int surfacePuits) {
    }

    /**
     * Le terrain sur l'emprise, lu une fois tous les 32 blocs, sans chunk généré. Mesuré : environ 1 ms par point lu, et le
     * tirage bloque un fil de génération. Une boîte compte tous les points autour d'elle : on se trompe du côté prudent.
     */
    private static final class Relief {
        private static final int PAS = 32;
        private final int x0;
        private final int z0;
        private final int[][] hauteurs;

        Relief(BlockPos centre, IntBinaryOperator terrain) {
            int rayon = (PlanMars.EMPRISE_MINE + 1) * 16;
            this.x0 = centre.getX() - rayon;
            this.z0 = centre.getZ() - rayon;
            int n = 2 * rayon / PAS + 2;
            this.hauteurs = new int[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    hauteurs[i][j] = terrain.applyAsInt(x0 + i * PAS, z0 + j * PAS);
                }
            }
        }

        /** Le terrain le plus bas sous cette boîte (avec ses points de grille autour). */
        int min(BoundingBox boite) {
            int i1 = Math.clamp(Math.floorDiv(boite.minX() - x0, PAS), 0, hauteurs.length - 1);
            int i2 = Math.clamp(Math.floorDiv(boite.maxX() - x0, PAS) + 1, 0, hauteurs.length - 1);
            int j1 = Math.clamp(Math.floorDiv(boite.minZ() - z0, PAS), 0, hauteurs.length - 1);
            int j2 = Math.clamp(Math.floorDiv(boite.maxZ() - z0, PAS) + 1, 0, hauteurs.length - 1);
            int min = Integer.MAX_VALUE;
            for (int i = i1; i <= i2; i++) {
                for (int j = j1; j <= j2; j++) {
                    min = Math.min(min, hauteurs[i][j]);
                }
            }
            return min;
        }

        /** Le vide de cette boîte reste-t-il assez loin sous le terrain ? */
        boolean sousLeTerrain(BoundingBox boite) {
            return boite.maxY() + MARGE_SURFACE <= min(boite);
        }
    }

    /** Un plan est tiré une fois par mine, puis relu : la pose de chaque chunk le redemande (fils de génération). */
    private static final Map<Cle, PlanDeMine> CACHE = new ConcurrentHashMap<>();
    private static volatile double dernierTirageMs;

    /**
     * Le plan de la mine centrée ici.
     *
     * @param graine     la graine du monde
     * @param terrain    la hauteur du terrain en (x, z), lue sans générer de chunk (le vide reste dessous, avec sa marge)
     * @param solAuPuits   le terrain le plus bas autour du puits principal (16 blocs) : la boîte de remplissage du puits
     *                     s'arrête à {@value #BOITE_PUITS_SOUS_SURFACE} blocs plus bas
     * @param surfacePuits la surface de la colonne du puits principal (3 × 3, son point le plus bas, hauteur au-dessus du
     *                     dernier bloc) : le bouchon est dessous, sous {@value #ROCHE_SUR_BOUCHON} blocs de roche d'origine
     */
    public static PlanDeMine de(long graine, BlockPos centre, IntBinaryOperator terrain, int solAuPuits, int surfacePuits) {
        return CACHE.computeIfAbsent(new Cle(graine, centre.immutable(), solAuPuits, surfacePuits), cle -> {
            long debut = System.nanoTime();
            PlanDeMine plan = tirer(cle.graine(), cle.centre(), terrain, cle.solAuPuits(), cle.surfacePuits());
            dernierTirageMs = (System.nanoTime() - debut) / 1e6;
            return plan;
        });
    }

    /** Le temps du dernier tirage d'un plan, relief lu compris (tests du coût). */
    public static double dernierTirageMs() {
        return dernierTirageMs;
    }

    /** Le plan sur un terrain plat (tests). */
    static PlanDeMine tirer(long graine, BlockPos centre, int terrain, int solAuPuits, int surfacePuits) {
        return tirer(graine, centre, (x, z) -> terrain, solAuPuits, surfacePuits);
    }

    /** Une liaison possible entre deux cavernes ou une caverne et l'axe : ses tunnels et leur longueur. */
    private record Liaison(int a, int b, List<BoundingBox> boites, int longueur) {
    }

    static PlanDeMine tirer(long graine, BlockPos centre, IntBinaryOperator terrain, int solAuPuits, int surfacePuits) {
        Relief relief = new Relief(centre, terrain);
        Random hasard = new Random(graine ^ centre.asLong() * 0x9E3779B97F4A7C15L);
        // Les niveaux que le terrain autour du puits permet (le plan des régions évite les sites trop bas)
        BoundingBox autourDuPuits = new BoundingBox(centre.getX() - 16, 0, centre.getZ() - 16, centre.getX() + 16, 0, centre.getZ() + 16);
        List<Integer> sols = new ArrayList<>();
        for (int sol : SOLS) {
            if (sol + HAUTEUR_AXE + MARGE_SURFACE <= relief.min(autourDuPuits)) {
                sols.add(sol);
            }
        }
        if (sols.isEmpty()) {
            sols.add(SOLS[SOLS.length - 1]);
        }
        int niveauColonie = Math.max(0, sols.indexOf(SOLS[NIVEAU_COLONIE]));

        // La colonie : au bout de la branche où le terrain lui laisse le plus de hauteur
        List<Direction> directions = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        Collections.shuffle(directions, hasard);
        int solColonie = sols.get(niveauColonie);
        Direction versColonie = directions.getFirst();
        int meilleurTerrain = Integer.MIN_VALUE;
        for (Direction direction : directions) {
            BoundingBox emprise = colonie(centre, direction, solColonie, HAUTEUR_COLONIE, 0, 0, 0).boite();
            int terrainDessus = relief.min(emprise);
            if (terrainDessus > meilleurTerrain) {
                meilleurTerrain = terrainDessus;
                versColonie = direction;
            }
        }
        int hauteurColonie = Math.clamp(meilleurTerrain - MARGE_SURFACE - solColonie, HAUTEUR_COLONIE_MIN, HAUTEUR_COLONIE);
        Caverne colonieCaverne = colonie(centre, versColonie, solColonie, hauteurColonie,
                hasard.nextDouble() * 2 * Math.PI, hasard.nextDouble() * 2 * Math.PI, hasard.nextDouble() * 2 * Math.PI);
        BoundingBox reserve = colonieCaverne.boite().inflatedBy(RESERVE_COLONIE);

        List<BoundingBox> galeries = new ArrayList<>();
        List<Integer> niveauxGaleries = new ArrayList<>();
        List<BoundingBox> axes = new ArrayList<>();
        List<Integer> niveauxAxes = new ArrayList<>();
        List<BoundingBox> tunnels = new ArrayList<>();
        List<Integer> niveauxTunnels = new ArrayList<>();
        List<Caverne> cavernes = new ArrayList<>(List.of(colonieCaverne));
        Map<Integer, Map<Direction, Integer>> longueursAxes = new HashMap<>();

        for (int niveau = 0; niveau < sols.size(); niveau++) {
            int sol = sols.get(niveau);
            // Les branches de l'axe
            Map<Direction, Integer> longueurs = new HashMap<>();
            for (Direction sens : Direction.Plane.HORIZONTAL) {
                int longueur = longueurAxe(centre, sens, sol, relief, colonieCaverne, sens == versColonie && niveau == niveauColonie);
                if (longueur >= 8) {
                    longueurs.put(sens, longueur);
                    BoundingBox axe = branche(centre, sens, longueur, sol);
                    axes.add(axe);
                    niveauxAxes.add(niveau);
                    galeries.add(axe);
                    niveauxGaleries.add(niveau);
                }
            }
            longueursAxes.put(niveau, longueurs);

            // Les cavernes, sur les nœuds hors des axes
            int voulues = CAVERNES[NIVEAUX_MAX - sols.size() + niveau];
            List<int[]> noeuds = new ArrayList<>();
            for (int i : NOEUDS) {
                for (int j : NOEUDS) {
                    noeuds.add(new int[]{i, j});
                }
            }
            Collections.shuffle(noeuds, hasard);
            List<Caverne> ici = new ArrayList<>();
            for (int[] noeud : noeuds) {
                if (ici.size() == voulues) {
                    break;
                }
                Caverne caverne = caverne(centre, noeud, sol, relief, hasard);
                if (caverne == null) {
                    continue;
                }
                BoundingBox boite = caverne.boite();
                if (boite.inflatedBy(4).intersects(reserve) || boite.intersects(colonieCaverne.boite().inflatedBy(3))
                        || ici.stream().anyMatch(autre -> autre.boite().inflatedBy(4).intersects(boite))) {
                    continue;
                }
                ici.add(caverne);
            }

            // Les tunnels : un arbre couvrant (Kruskal), les plus courts d'abord, avec du hasard ; puis des boucles
            int axeId = ici.size();
            List<Liaison> liaisons = new ArrayList<>();
            for (int a = 0; a < ici.size(); a++) {
                Caverne c = ici.get(a);
                // Vers la branche de l'axe la plus proche, tout droit
                Direction versAxeX = c.z() > centre.getZ() ? Direction.NORTH : Direction.SOUTH;
                Direction brancheX = c.x() > centre.getX() ? Direction.EAST : Direction.WEST;
                if (Math.abs(c.x() - centre.getX()) + 6 <= longueurs.getOrDefault(brancheX, 0)) {
                    ajouterLiaison(liaisons, a, axeId, List.of(tunnel(c.x(), c.z(), c.x(), centre.getZ(), sol)), relief, reserve, colonieCaverne);
                }
                Direction brancheZ = c.z() > centre.getZ() ? Direction.SOUTH : Direction.NORTH;
                if (Math.abs(c.z() - centre.getZ()) + 6 <= longueurs.getOrDefault(brancheZ, 0)) {
                    ajouterLiaison(liaisons, a, axeId, List.of(tunnel(c.x(), c.z(), centre.getX(), c.z(), sol)), relief, reserve, colonieCaverne);
                }
                // Vers les cavernes voisines, en L
                for (int b = a + 1; b < ici.size(); b++) {
                    Caverne d = ici.get(b);
                    if (Math.hypot(c.x() - d.x(), c.z() - d.z()) <= 2.2 * ESPACEMENT) {
                        ajouterLiaison(liaisons, a, b, List.of(tunnel(c.x(), c.z(), d.x(), c.z(), sol), tunnel(d.x(), c.z(), d.x(), d.z(), sol)),
                                relief, reserve, colonieCaverne);
                    }
                }
            }
            Collections.shuffle(liaisons, hasard);
            Map<Liaison, Integer> poids = new IdentityHashMap<>();
            liaisons.forEach(liaison -> poids.put(liaison, liaison.longueur() + hasard.nextInt(40)));
            liaisons.sort((x, y) -> Integer.compare(poids.get(x), poids.get(y)));
            int[] parents = new int[ici.size() + 1];
            for (int k = 0; k < parents.length; k++) {
                parents[k] = k;
            }
            List<Liaison> gardees = new ArrayList<>();
            List<Liaison> restes = new ArrayList<>();
            for (Liaison liaison : liaisons) {
                int ra = racine(parents, liaison.a());
                int rb = racine(parents, liaison.b());
                if (ra != rb) {
                    parents[ra] = rb;
                    gardees.add(liaison);
                } else if (liaison.longueur() <= LONGUEUR_BOUCLE) {
                    restes.add(liaison);
                }
            }
            gardees.addAll(restes.subList(0, (int) Math.round(restes.size() * PART_BOUCLES)));
            for (Liaison liaison : gardees) {
                for (BoundingBox boite : liaison.boites()) {
                    tunnels.add(boite);
                    niveauxTunnels.add(niveau);
                    galeries.add(boite);
                    niveauxGaleries.add(niveau);
                }
            }
            // Une caverne qu'aucune liaison ne relie à l'axe n'est pas gardée (et ses tunnels non plus : ils ne touchent que des cavernes retirées)
            for (int a = 0; a < ici.size(); a++) {
                if (racine(parents, a) == racine(parents, axeId)) {
                    cavernes.add(ici.get(a));
                }
            }
            int niveauCourant = niveau;
            for (int k = galeries.size() - 1; k >= 0; k--) {
                if (niveauxGaleries.get(k) == niveauCourant && !axes.contains(galeries.get(k))) {
                    BoundingBox boite = galeries.get(k);
                    boolean utile = axes.stream().anyMatch(axe -> axe.intersects(boite))
                            || cavernes.stream().anyMatch(caverne -> caverne.sol() == sol && caverne.boite().intersects(boite));
                    if (!utile) {
                        tunnels.remove(boite);
                        niveauxTunnels.removeLast();
                        galeries.remove(k);
                        niveauxGaleries.remove(k);
                    }
                }
            }
        }

        // Puits secondaires : sur le côté d'une branche de l'axe présente sur les deux niveaux, jamais dans une caverne
        List<Puits> puits = new ArrayList<>();
        for (int haut = 0; haut + 1 < sols.size(); haut++) {
            List<Object[]> candidats = new ArrayList<>();
            for (Direction sens : Direction.Plane.HORIZONTAL) {
                for (int d : DISTANCES_PUITS) {
                    candidats.add(new Object[]{sens, d});
                }
            }
            Collections.shuffle(candidats, hasard);
            Set<Direction> prises = new HashSet<>();
            for (Object[] candidat : candidats) {
                if (prises.size() == PUITS_SECONDAIRES) {
                    break;
                }
                Direction sens = (Direction) candidat[0];
                int d = (Integer) candidat[1];
                if (prises.contains(sens) || longueursAxes.get(haut).getOrDefault(sens, 0) < d + 8 || longueursAxes.get(haut + 1).getOrDefault(sens, 0) < d + 8) {
                    continue;
                }
                int x = centre.getX() + sens.getStepX() * d + (sens.getAxis() == Direction.Axis.Z ? 3 : 0);
                int z = centre.getZ() + sens.getStepZ() * d + (sens.getAxis() == Direction.Axis.X ? 3 : 0);
                Puits p = new Puits(x, z, sols.get(haut + 1), sols.get(haut) + HAUTEUR_TROU - 1, false);
                BoundingBox garde = p.interieur().inflatedBy(COQUE_PUITS);
                boolean libre = cavernes.stream().noneMatch(caverne -> caverne.boite().intersects(garde)) && !garde.intersects(reserve)
                        && tunnels.stream().noneMatch(tunnel -> tunnel.intersects(garde))
                        && puits.stream().noneMatch(autre -> Math.hypot(autre.x() - x, autre.z() - z) < 30);
                if (libre) {
                    puits.add(p);
                    prises.add(sens);
                }
            }
        }
        // Puits principal, jusqu'au bouchon : 3 blocs de roche, puis les 2 couches du bouchon, sous la surface de sa colonne
        int hautPuits = Math.max(surfacePuits - 1 - ROCHE_SUR_BOUCHON - 2, sols.getFirst() + HAUTEUR_AXE);
        puits.addFirst(new Puits(centre.getX(), centre.getZ(), sols.getLast(), hautPuits, true));
        BoundingBox bouchon = new BoundingBox(centre.getX() - 1, hautPuits + 1, centre.getZ() - 1, centre.getX() + 1, hautPuits + 2, centre.getZ() + 1);

        // Culs-de-sac
        List<BoundingBox> culs = new ArrayList<>();
        List<BlockPos> culsDeSac = new ArrayList<>();
        List<int[]> axesCulsDirection = new ArrayList<>();
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            int sol = sols.get(niveau);
            List<BoundingBox> sources = new ArrayList<>();
            for (int k = 0; k < galeries.size(); k++) {
                if (niveauxGaleries.get(k) == niveau) {
                    sources.add(galeries.get(k));
                }
            }
            int poses = 0;
            for (int essai = 0; essai < 400 && poses < CULS_PAR_NIVEAU && !sources.isEmpty(); essai++) {
                BoundingBox source = sources.get(hasard.nextInt(sources.size()));
                boolean surX = source.getXSpan() > source.getZSpan();
                int t = surX ? source.minX() + hasard.nextInt(source.getXSpan()) : source.minZ() + hasard.nextInt(source.getZSpan());
                int cote = hasard.nextBoolean() ? 1 : -1;
                int mur = surX ? (cote > 0 ? source.maxZ() : source.minZ()) : (cote > 0 ? source.maxX() : source.minX());
                int longueur = LONGUEUR_CUL_MIN + hasard.nextInt(LONGUEUR_CUL_MAX - LONGUEUR_CUL_MIN + 1);
                int bout = mur + cote * longueur;
                BoundingBox cul = surX ? new BoundingBox(t - DEMI_LARGEUR_CUL, sol, Math.min(mur, bout), t + DEMI_LARGEUR_CUL, sol + HAUTEUR_CUL - 1, Math.max(mur, bout))
                        : new BoundingBox(Math.min(mur, bout), sol, t - DEMI_LARGEUR_CUL, Math.max(mur, bout), sol + HAUTEUR_CUL - 1, t + DEMI_LARGEUR_CUL);
                // Tout, sauf le bout qui part de la galerie, loin du reste du vide
                BoundingBox loin = surX ? new BoundingBox(cul.minX(), sol, Math.min(mur + 3 * cote, bout), cul.maxX(), cul.maxY(), Math.max(mur + 3 * cote, bout))
                        : new BoundingBox(Math.min(mur + 3 * cote, bout), sol, cul.minZ(), Math.max(mur + 3 * cote, bout), cul.maxY(), cul.maxZ());
                BoundingBox garde = loin.inflatedBy(3);
                boolean libre = dansLaMine(cul, centre) && relief.sousLeTerrain(cul) && !cul.intersects(reserve)
                        && galeries.stream().noneMatch(g -> g.intersects(garde)) && culs.stream().noneMatch(c -> c.intersects(garde))
                        && cavernes.stream().noneMatch(c -> c.boite().intersects(garde))
                        && puits.stream().noneMatch(p -> p.interieur().inflatedBy(COQUE_PUITS).intersects(garde));
                if (libre) {
                    culs.add(cul);
                    axesCulsDirection.add(new int[]{surX ? t : bout - cote * DEMI_LARGEUR_CUL, surX ? bout - cote * DEMI_LARGEUR_CUL : t, surX ? 0 : cote, surX ? cote : 0, niveau});
                    culsDeSac.add(new BlockPos(surX ? t : bout - cote * DEMI_LARGEUR_CUL, sol, surX ? bout - cote * DEMI_LARGEUR_CUL : t));
                    poses++;
                }
            }
        }
        for (int k = 0; k < culs.size(); k++) {
            galeries.add(culs.get(k));
            niveauxGaleries.add(axesCulsDirection.get(k)[4]);
        }

        // Arches : sur les axes et les tunnels, hors des croisements
        Set<BlockPos> poteaux = new HashSet<>();
        Set<BlockPos> poutres = new HashSet<>();
        for (int k = 0; k < axes.size(); k++) {
            etayer(axes.get(k), centre, galeries, cavernes, puits, poteaux, poutres);
        }
        for (BoundingBox tunnel : tunnels) {
            etayer(tunnel, centre, galeries, cavernes, puits, poteaux, poutres);
        }

        Colonie colonie = colonie(colonieCaverne, versColonie, reserve);
        List<EmplacementCellule> cellules = cellules(sols, culs, axesCulsDirection, tunnels, niveauxTunnels, galeries, puits, cavernes, reserve, relief, centre, hasard);
        List<Salle> salles = salles(sols, cavernes, galeries, puits, reserve, cellules, relief, centre, hasard, poteaux, poutres);
        // Le haut de tout le vide, avec un peu de marge
        int hautDuVide = sols.getFirst() + HAUTEUR_AXE;
        for (Caverne caverne : cavernes) {
            hautDuVide = Math.max(hautDuVide, caverne.sol() + caverne.hauteur());
        }
        int hautDesBoites = hautDuVide + 2;
        int hautBoitePuits = Math.max(hautDesBoites, solAuPuits - BOITE_PUITS_SOUS_SURFACE);
        // Les étais ne restent ni dans les cavernes ni dans les puits
        poteaux.removeIf(pos -> cavernes.stream().anyMatch(c -> c.contient(pos.getX(), pos.getY(), pos.getZ())) || dansUnPuits(puits, pos));
        poutres.removeIf(pos -> cavernes.stream().anyMatch(c -> c.contient(pos.getX(), pos.getY(), pos.getZ())) || dansUnPuits(puits, pos));
        return new PlanDeMine(relief, centre, sols, galeries, niveauxGaleries, puits, cavernes, colonie, cellules, culsDeSac, salles, poteaux, poutres,
                bouchon, hautDesBoites, hautBoitePuits, axes, niveauxAxes, hasard);
    }

    private static boolean dansLaMine(BoundingBox boite, BlockPos centre) {
        return boite.minX() >= centre.getX() - DEMI_MINE && boite.maxX() <= centre.getX() + DEMI_MINE
                && boite.minZ() >= centre.getZ() - DEMI_MINE && boite.maxZ() <= centre.getZ() + DEMI_MINE;
    }

    private static boolean dansUnPuits(List<Puits> puits, BlockPos pos) {
        return puits.stream().anyMatch(p -> p.interieur().isInside(pos));
    }

    /** La colonie au bout de cette branche de l'axe, allongée dans son sens. */
    private static Caverne colonie(BlockPos centre, Direction sens, int sol, int hauteur, double phase1, double phase2, double phase3) {
        boolean surX = sens.getAxis() == Direction.Axis.X;
        return new Caverne(centre.getX() + sens.getStepX() * DISTANCE_COLONIE, centre.getZ() + sens.getStepZ() * DISTANCE_COLONIE, sol,
                surX ? DEMI_LONG_COLONIE : DEMI_LARGE_COLONIE, surX ? DEMI_LARGE_COLONIE : DEMI_LONG_COLONIE, hauteur, phase1, phase2, phase3);
    }

    /** La boîte d'une branche de l'axe : du centre (et sa croisée) jusqu'à {@code longueur} blocs dans ce sens. */
    private static BoundingBox branche(BlockPos centre, Direction sens, int longueur, int sol) {
        BlockPos bout = centre.relative(sens, longueur);
        int d = DEMI_LARGEUR_AXE;
        return new BoundingBox(Math.min(centre.getX(), bout.getX()) - d, sol, Math.min(centre.getZ(), bout.getZ()) - d,
                Math.max(centre.getX(), bout.getX()) + d, sol + HAUTEUR_AXE - 1, Math.max(centre.getZ(), bout.getZ()) + d);
    }

    /**
     * La longueur d'une branche de l'axe : jusqu'à {@value #LONGUEUR_AXE} blocs, tant que le terrain la laisse dessous et
     * qu'elle ne perce pas la colonie (au-dessus de son dôme, elle s'arrête avant) ; la branche de la colonie entre dans la
     * caverne de 6 blocs.
     */
    private static int longueurAxe(BlockPos centre, Direction sens, int sol, Relief relief, Caverne colonie, boolean versColonie) {
        Direction cote = sens.getClockWise();
        for (int t = 8; t <= LONGUEUR_AXE; t++) {
            BlockPos bout = centre.relative(sens, t);
            for (int l = -DEMI_LARGEUR_AXE - 1; l <= DEMI_LARGEUR_AXE + 1; l += DEMI_LARGEUR_AXE + 1) {
                BlockPos pos = bout.relative(cote, l);
                if (versColonie && l == 0 && colonie.contient(pos.getX(), sol, pos.getZ())) {
                    return t + 6;
                }
                boolean dome = colonie.contient(pos.getX(), sol - 2, pos.getZ()) || colonie.contient(pos.getX(), sol + HAUTEUR_AXE + 1, pos.getZ())
                        || colonie.contient(pos.getX(), sol, pos.getZ());
                if (!versColonie && dome) {
                    return t - 3;
                }
            }
            BoundingBox tranche = new BoundingBox(bout.getX(), sol, bout.getZ(), bout.getX(), sol + HAUTEUR_AXE - 1, bout.getZ());
            if (t % 8 == 0 && !relief.sousLeTerrain(tranche)) {
                return t - 8;
            }
        }
        return LONGUEUR_AXE;
    }

    /** Une caverne autour de ce nœud, abaissée sous le terrain, ou {@code null} si elle n'y tient pas. */
    private static @Nullable Caverne caverne(BlockPos centre, int[] noeud, int sol, Relief relief, Random hasard) {
        int x = centre.getX() + noeud[0] * ESPACEMENT - JEU_CAVERNE + hasard.nextInt(2 * JEU_CAVERNE + 1);
        int z = centre.getZ() + noeud[1] * ESPACEMENT - JEU_CAVERNE + hasard.nextInt(2 * JEU_CAVERNE + 1);
        int demiX = DEMI_CAVERNE_MIN + hasard.nextInt(DEMI_CAVERNE_MAX - DEMI_CAVERNE_MIN + 1);
        int demiZ = DEMI_CAVERNE_MIN + hasard.nextInt(DEMI_CAVERNE_MAX - DEMI_CAVERNE_MIN + 1);
        int voulue = HAUTEUR_CAVERNE_MIN + hasard.nextInt(HAUTEUR_CAVERNE_MAX - HAUTEUR_CAVERNE_MIN + 1);
        double phase1 = hasard.nextDouble() * 2 * Math.PI;
        double phase2 = hasard.nextDouble() * 2 * Math.PI;
        double phase3 = hasard.nextDouble() * 2 * Math.PI;
        Caverne essai = new Caverne(x, z, sol, demiX, demiZ, voulue, phase1, phase2, phase3);
        int hauteur = Math.min(voulue, relief.min(essai.boite()) - MARGE_SURFACE - sol);
        if (hauteur < HAUTEUR_CAVERNE_PLANCHER) {
            return null;
        }
        return new Caverne(x, z, sol, demiX, demiZ, hauteur, phase1, phase2, phase3);
    }

    /** Un tunnel droit de (x1, z1) à (x2, z2). */
    private static BoundingBox tunnel(int x1, int z1, int x2, int z2, int sol) {
        int d = DEMI_LARGEUR_TUNNEL;
        return new BoundingBox(Math.min(x1, x2) - d, sol, Math.min(z1, z2) - d, Math.max(x1, x2) + d, sol + HAUTEUR_TUNNEL - 1, Math.max(z1, z2) + d);
    }

    /** Une liaison possible : aucun de ses tunnels ne touche la colonie ni sa réserve, tous sous le terrain. */
    private static void ajouterLiaison(List<Liaison> liaisons, int a, int b, List<BoundingBox> boites, Relief relief, BoundingBox reserve, Caverne colonie) {
        int longueur = 0;
        for (BoundingBox boite : boites) {
            if (boite.intersects(reserve) || boite.intersects(colonie.boite().inflatedBy(3)) || !relief.sousLeTerrain(boite)) {
                return;
            }
            longueur += Math.max(boite.getXSpan(), boite.getZSpan());
        }
        liaisons.add(new Liaison(a, b, boites, longueur));
    }

    private static int racine(int[] parents, int n) {
        int courant = n;
        while (parents[courant] != courant) {
            courant = parents[courant];
        }
        return courant;
    }

    /**
     * Une arche tous les {@value #PAS_ARCHES} blocs (comptés depuis le centre de la mine) : deux poteaux contre les parois, une
     * poutre au plafond. Pas d'arche là où un autre vide croise la galerie (elle le boucherait).
     */
    private static void etayer(BoundingBox boite, BlockPos centre, List<BoundingBox> galeries, List<Caverne> cavernes, List<Puits> puits,
                               Set<BlockPos> poteaux, Set<BlockPos> poutres) {
        boolean surX = boite.getXSpan() > boite.getZSpan();
        int debut = surX ? boite.minX() : boite.minZ();
        int fin = surX ? boite.maxX() : boite.maxZ();
        int origine = surX ? centre.getX() : centre.getZ();
        int cote1 = surX ? boite.minZ() : boite.minX();
        int cote2 = surX ? boite.maxZ() : boite.maxX();
        for (int t = debut + Math.floorMod(origine - debut, PAS_ARCHES); t <= fin; t += PAS_ARCHES) {
            BoundingBox plan = surX ? new BoundingBox(t - 1, boite.minY(), cote1 - 1, t + 1, boite.maxY(), cote2 + 1)
                    : new BoundingBox(cote1 - 1, boite.minY(), t - 1, cote2 + 1, boite.maxY(), t + 1);
            boolean croise = galeries.stream().anyMatch(g -> g != boite && g.intersects(plan))
                    || cavernes.stream().anyMatch(c -> c.boite().intersects(plan))
                    || puits.stream().anyMatch(p -> p.interieur().intersects(plan));
            if (croise) {
                continue;
            }
            for (int y = boite.minY(); y < boite.maxY(); y++) {
                poteaux.add(surX ? new BlockPos(t, y, cote1) : new BlockPos(cote1, y, t));
                poteaux.add(surX ? new BlockPos(t, y, cote2) : new BlockPos(cote2, y, t));
            }
            for (int c = cote1; c <= cote2; c++) {
                poutres.add(surX ? new BlockPos(t, boite.maxY(), c) : new BlockPos(c, boite.maxY(), t));
            }
        }
    }

    // --- Colonie et cellules ---

    private static Colonie colonie(Caverne caverne, Direction versColonie, BoundingBox reserve) {
        int sol = caverne.sol();
        BlockPos echafaud = new BlockPos(caverne.x(), sol, caverne.z());
        BoundingBox place = new BoundingBox(caverne.x() - 10, sol, caverne.z() - 10, caverne.x() + 9, sol, caverne.z() + 9);
        // Le Rouge apparaît près de l'entrée, côté puits, à 6 blocs du bord
        int bord = (versColonie.getAxis() == Direction.Axis.X ? caverne.demiX() : caverne.demiZ()) - 6;
        BlockPos apparition = new BlockPos(caverne.x(), sol, caverne.z()).relative(versColonie.getOpposite(), bord);
        return new Colonie(place, echafaud, apparition, reserve);
    }

    /**
     * Les emplacements des cellules, {@value #CELLULES} / 3 par niveau : au bout d'un cul-de-sac (dans son prolongement) ou sur
     * la paroi d'un tunnel. Leur salle reste en roche pleine, loin de tout vide de la mine. Au plus un cul-de-sac sur
     * {@value #CULS_DE_SAC_PAR_CELLULE} + 1 porte une cellule.
     */
    private static List<EmplacementCellule> cellules(List<Integer> sols, List<BoundingBox> culs, List<int[]> bouts, List<BoundingBox> tunnels,
                                                     List<Integer> niveauxTunnels, List<BoundingBox> galeries, List<Puits> puits, List<Caverne> cavernes,
                                                     BoundingBox reserve, Relief relief, BlockPos centre, Random hasard) {
        List<EmplacementCellule> cellules = new ArrayList<>();
        int parNiveau = Math.ceilDiv(CELLULES, sols.size());
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            int sol = sols.get(niveau);
            List<int[]> feuilles = new ArrayList<>();
            for (int[] bout : bouts) {
                if (bout[4] == niveau) {
                    feuilles.add(bout);
                }
            }
            Collections.shuffle(feuilles, hasard);
            List<BoundingBox> parois = new ArrayList<>();
            for (int k = 0; k < tunnels.size(); k++) {
                if (niveauxTunnels.get(k) == niveau) {
                    parois.add(tunnels.get(k));
                }
            }
            int boutsPermis = feuilles.size() / (CULS_DE_SAC_PAR_CELLULE + 1);
            int poses = 0;
            int nbBouts = 0;
            int essaiFeuille = 0;
            for (int essai = 0; essai < 600 && poses < parNiveau && cellules.size() < CELLULES; essai++) {
                EmplacementCellule candidat = null;
                if (hasard.nextBoolean() && nbBouts < boutsPermis && essaiFeuille < feuilles.size()) {
                    int[] bout = feuilles.get(essaiFeuille++);
                    int cx = bout[0] + bout[2] * (DEMI_LARGEUR_CUL + 2 + 3);
                    int cz = bout[1] + bout[3] * (DEMI_LARGEUR_CUL + 2 + 3);
                    candidat = new EmplacementCellule(niveau, TypeCellule.BOUT, new BoundingBox(cx - 2, sol, cz - 2, cx + 2, sol + 2, cz + 2), new BlockPos(bout[0], sol, bout[1]));
                } else if (!parois.isEmpty()) {
                    BoundingBox tunnel = parois.get(hasard.nextInt(parois.size()));
                    boolean surX = tunnel.getXSpan() > tunnel.getZSpan();
                    int t = surX ? tunnel.minX() + hasard.nextInt(tunnel.getXSpan()) : tunnel.minZ() + hasard.nextInt(tunnel.getZSpan());
                    int mx = surX ? t : (tunnel.minX() + tunnel.maxX()) / 2;
                    int mz = surX ? (tunnel.minZ() + tunnel.maxZ()) / 2 : t;
                    int cote = hasard.nextBoolean() ? 1 : -1;
                    int cx = surX ? mx : mx + cote * (DEMI_LARGEUR_TUNNEL + 2 + 3);
                    int cz = surX ? mz + cote * (DEMI_LARGEUR_TUNNEL + 2 + 3) : mz;
                    candidat = new EmplacementCellule(niveau, TypeCellule.PAROI, new BoundingBox(cx - 2, sol, cz - 2, cx + 2, sol + 2, cz + 2), new BlockPos(mx, sol, mz));
                }
                if (candidat != null && cellulePossible(candidat, galeries, puits, cavernes, reserve, cellules, relief, centre)) {
                    cellules.add(candidat);
                    poses++;
                    if (candidat.type() == TypeCellule.BOUT) {
                        nbBouts++;
                    }
                }
            }
        }
        return cellules;
    }

    private static boolean cellulePossible(EmplacementCellule cellule, List<BoundingBox> galeries, List<Puits> puits, List<Caverne> cavernes, BoundingBox reserve,
                                           List<EmplacementCellule> autres, Relief relief, BlockPos centre) {
        BoundingBox murs = cellule.avecMurs();
        // Deux blocs de roche au moins entre la salle et tout vide de la mine (la galerie d'accès comprise)
        BoundingBox garde = murs.inflatedBy(1);
        if (galeries.stream().anyMatch(galerie -> galerie.intersects(garde)) || puits.stream().anyMatch(p -> p.interieur().inflatedBy(COQUE_PUITS).intersects(garde))
                || cavernes.stream().anyMatch(caverne -> caverne.boite().intersects(garde)) || garde.intersects(reserve)
                || !dansLaMine(murs, centre) || !relief.sousLeTerrain(murs)) {
            return false;
        }
        int x = cellule.salle().getCenter().getX();
        int z = cellule.salle().getCenter().getZ();
        return distance(reserve, x, z) >= DISTANCE_COLONIE_CELLULE - RESERVE_COLONIE && autres.stream().noneMatch(autre -> {
            BlockPos c = autre.salle().getCenter();
            return Math.hypot(c.getX() - x, c.getZ() - z) < ECART_CELLULES;
        });
    }

    // --- Salles d'extraction ---

    /**
     * Les baies de chaque niveau ({@link #FRONTS}, {@link #FORAGES}) : la paroi d'une caverne (autre que la colonie), dans une
     * direction tirée ; la salle derrière, à condition qu'elle reste loin de tout le reste ({@link #salleLibre}). Ajoute les
     * étais des fronts et le cadre des forages.
     */
    private static List<Salle> salles(List<Integer> sols, List<Caverne> cavernes, List<BoundingBox> galeries, List<Puits> puits, BoundingBox reserve,
                                      List<EmplacementCellule> cellules, Relief relief, BlockPos centre, Random hasard, Set<BlockPos> poteaux, Set<BlockPos> poutres) {
        List<Salle> salles = new ArrayList<>();
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            int sol = sols.get(niveau);
            int ligne = NIVEAUX_MAX - sols.size() + niveau;
            List<Caverne> ici = cavernes.stream().filter(caverne -> caverne.sol() == sol && caverne != cavernes.getFirst()).toList();
            if (ici.isEmpty()) {
                continue;
            }
            for (TypeSalle type : List.of(TypeSalle.FORAGE, TypeSalle.FRONT)) {
                int voulu = type == TypeSalle.FORAGE ? FORAGES[ligne] : FRONTS[ligne];
                int poses = 0;
                for (int essai = 0; essai < 200 && poses < voulu; essai++) {
                    Caverne caverne = ici.get(hasard.nextInt(ici.size()));
                    Direction sens = Direction.from2DDataValue(hasard.nextInt(4));
                    // La paroi, au sol, depuis le centre de la caverne
                    BlockPos pos = new BlockPos(caverne.x(), sol, caverne.z());
                    while (caverne.contient(pos.getX(), sol, pos.getZ())) {
                        pos = pos.relative(sens);
                    }
                    Salle salle = type == TypeSalle.FRONT ? front(niveau, pos, sens, sol) : forage(niveau, pos, sens, sol);
                    if (salleLibre(salle, caverne, cavernes, galeries, puits, reserve, cellules, salles, relief, centre)) {
                        salles.add(salle);
                        poses++;
                    }
                }
            }
        }
        for (Salle salle : salles) {
            BoundingBox b = salle.boite();
            if (salle.type() == TypeSalle.FORAGE) {
                // Le cadre de bois du forage : quatre poteaux et un plateau de 3 × 3
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        poutres.add(new BlockPos(salle.x() + dx, b.minY() + 4, salle.z() + dz));
                        if (dx != 0 && dz != 0) {
                            for (int y = b.minY(); y < b.minY() + 4; y++) {
                                poteaux.add(new BlockPos(salle.x() + dx, y, salle.z() + dz));
                            }
                        }
                    }
                }
            }
        }
        return salles;
    }

    /** Une boîte qui part de {@code depart} sur {@code longueur} blocs dans le sens {@code sens}, de ±{@code demi} en travers. */
    private static BoundingBox bande(BlockPos depart, Direction sens, int longueur, int demi, int hauteur) {
        BlockPos bout = depart.relative(sens, longueur);
        Direction cote = sens.getClockWise();
        BlockPos a = depart.relative(cote, demi);
        BlockPos b = bout.relative(cote, -demi);
        return new BoundingBox(Math.min(a.getX(), b.getX()), depart.getY(), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()),
                depart.getY() + hauteur - 1, Math.max(a.getZ(), b.getZ()));
    }

    /** Front de taille derrière la paroi : une salle droite, en travers du sens, et une ouverture de 5 de large. */
    private static Salle front(int niveau, BlockPos paroi, Direction sens, int sol) {
        BlockPos debut = paroi.relative(sens, 2);
        BoundingBox boite = bande(debut, sens, PROFONDEUR_FRONT - 1, DEMI_LONG_FRONT, HAUTEUR_FRONT);
        BoundingBox ouverture = bande(paroi.relative(sens, -2), sens, 4, 2, 4);
        BlockPos milieu = boite.getCenter();
        return new Salle(niveau, TypeSalle.FRONT, boite, milieu.getX(), milieu.getZ(), List.of(ouverture));
    }

    /** Salle de forage derrière la paroi, et son couloir de 5 de large. */
    private static Salle forage(int niveau, BlockPos paroi, Direction sens, int sol) {
        int r = (int) RAYON_FORAGE;
        BlockPos milieu = paroi.relative(sens, r + 3);
        BoundingBox boite = new BoundingBox(milieu.getX() - r, sol, milieu.getZ() - r, milieu.getX() + r, sol + HAUTEUR_FORAGE - 1, milieu.getZ() + r);
        BoundingBox couloir = bande(paroi.relative(sens, -2), sens, 7, 2, 4);
        return new Salle(niveau, TypeSalle.FORAGE, boite, milieu.getX(), milieu.getZ(), List.of(couloir));
    }

    /**
     * Une salle loin de tout : sa boîte hors de sa caverne et à un bloc de roche au moins des galeries ; elle et son ouverture
     * hors des coques des puits et de la réserve de la colonie, à 2 blocs de roche des cellules (et
     * {@value #ECART_SALLE_CELLULE} blocs entre les centres), à 2 blocs d'une autre salle, sous le terrain.
     */
    private static boolean salleLibre(Salle salle, Caverne sa, List<Caverne> cavernes, List<BoundingBox> galeries, List<Puits> puits, BoundingBox reserve,
                                      List<EmplacementCellule> cellules, List<Salle> salles, Relief relief, BlockPos centre) {
        BoundingBox autour = salle.boite().inflatedBy(1);
        if (!dansLaMine(salle.boite(), centre) || !relief.sousLeTerrain(salle.boite()) || galeries.stream().anyMatch(galerie -> galerie.intersects(autour))) {
            return false;
        }
        // Hors de sa caverne (sa boîte n'est pas vide en entier), et des autres
        for (BlockPos pos : BlockPos.betweenClosed(autour.minX(), autour.minY(), autour.minZ(), autour.maxX(), autour.minY(), autour.maxZ())) {
            if (sa.contient(pos.getX(), pos.getY(), pos.getZ())) {
                return false;
            }
        }
        if (cavernes.stream().anyMatch(autre -> autre != sa && autre.boite().intersects(autour))) {
            return false;
        }
        List<BoundingBox> vides = new ArrayList<>(salle.ouvertures());
        vides.add(salle.boite());
        for (BoundingBox vide : vides) {
            if (vide.intersects(reserve) || puits.stream().anyMatch(p -> p.interieur().inflatedBy(COQUE_PUITS).intersects(vide))
                    || cellules.stream().anyMatch(cellule -> cellule.avecMurs().inflatedBy(1).intersects(vide))
                    || salles.stream().anyMatch(autre -> autre.boite().inflatedBy(2).intersects(vide))) {
                return false;
            }
        }
        return cellules.stream().noneMatch(cellule -> {
            BlockPos c = cellule.salle().getCenter();
            return Math.hypot(c.getX() - salle.x(), c.getZ() - salle.z()) < ECART_SALLE_CELLULE;
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

    /** La caverne de la colonie, la plus grande. */
    public Caverne caverne() {
        return cavernes.getFirst();
    }

    /** Toutes les cavernes ; la colonie d'abord. */
    public List<Caverne> cavernes() {
        return cavernes;
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

    public List<Salle> salles() {
        return salles;
    }

    public List<Coffre> coffres() {
        return coffres;
    }

    /** Les galeries de l'axe principal (5 de large), et leur niveau dans le même ordre. */
    public List<BoundingBox> axes() {
        return axes;
    }

    public List<Integer> niveauxAxes() {
        return niveauxAxes;
    }

    public List<Lanterne> lanternes() {
        return lanternes;
    }

    public List<Voie> voies() {
        return voies;
    }

    public List<Garage> garages() {
        return garages;
    }

    public Set<BlockPos> chaines() {
        return chaines;
    }

    public List<Panneau> panneaux() {
        return panneaux;
    }

    public Map<BlockPos, Integer> bandes() {
        return bandes;
    }

    /** Les points d'entrée des Gris (G7.2.4). */
    public List<Entree> entreesDesGris() {
        return entrees;
    }

    /** Tous les blocs de filon (minerais riches et hélium-3). */
    public List<FilonsDeMine.Bloc> filons() {
        return filons;
    }

    /** Les blocs de filon de ce chunk. */
    public List<FilonsDeMine.Bloc> filonsDuChunk(int cx, int cz) {
        return filonsParChunk.getOrDefault(cleChunk(cx, cz), List.of());
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

    /** Le terrain le plus bas au-dessus de cette boîte (ses y ne comptent pas). */
    public int surfaceMin(BoundingBox boite) {
        return relief.min(boite);
    }

    public Puits puitsPrincipal() {
        return puits.getFirst();
    }

    /**
     * Le niveau (0 en haut) où se trouve cette position, ou −1 hors de la mine (G7.2.4). Par tranches de hauteur : un niveau
     * va de {@value #DEMI_TRANCHE} blocs sous son sol à {@value #DEMI_TRANCHE} blocs au-dessus (exclu), dans l'emprise. Les
     * tranches se touchent (28 blocs entre deux sols) : sur l'échelle d'un puits, le niveau change une fois, à mi-hauteur,
     * et ne dépend ni du bloc où l'on se tient ni d'une galerie voisine. La tranche du haut monte jusqu'au haut des boîtes
     * (les voûtes du niveau 1) ; toute la colonie, dont la voûte monte haut, est sur son niveau. Au-dessus (le haut du puits
     * principal) et sous la tranche du bas : hors de la mine.
     */
    public int niveauA(BlockPos pos) {
        if (Math.abs((pos.getX() >> 4) - (centre.getX() >> 4)) > PlanMars.EMPRISE_MINE
                || Math.abs((pos.getZ() >> 4) - (centre.getZ() >> 4)) > PlanMars.EMPRISE_MINE) {
            return -1;
        }
        if (caverne().contient(pos.getX(), pos.getY(), pos.getZ())) {
            return sols.indexOf(caverne().sol());
        }
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            int sol = sols.get(niveau);
            int haut = niveau == 0 ? Math.max(sol + DEMI_TRANCHE, hautDesBoites) : sol + DEMI_TRANCHE;
            if (pos.getY() >= sol - DEMI_TRANCHE && pos.getY() < haut) {
                return niveau;
            }
        }
        return -1;
    }

    /**
     * Les points d'entrée des Gris pour un joueur sur ce niveau (G7.2.4) : ceux de son niveau à une distance entre
     * {@code min} et {@code max}, du plus proche au plus loin, puis ceux des niveaux voisins, de même. La traque y prend un
     * des premiers hors de vue.
     */
    public List<Entree> entreesPour(BlockPos joueur, int niveau, double min, double max) {
        List<Entree> choix = new ArrayList<>();
        for (int ecart : new int[]{0, 1}) {
            List<Entree> ici = new ArrayList<>();
            for (Entree entree : entrees) {
                double distance = Math.sqrt(entree.pos().distSqr(joueur));
                if (Math.abs(entree.niveau() - niveau) == ecart && distance >= min && distance <= max) {
                    ici.add(entree);
                }
            }
            ici.sort((a, b) -> Double.compare(a.pos().distSqr(joueur), b.pos().distSqr(joueur)));
            choix.addAll(ici);
        }
        return choix;
    }

    /**
     * Des places au sol dans les galeries sombres de ce niveau (debug, tests : {@code /rr mine traque}) : le milieu de chaque
     * galerie hors de l'axe, hors du réseau éclairé, à {@code distanceMin} blocs au moins du puits principal (à plat).
     */
    public List<BlockPos> galeriesSombres(int niveau, double distanceMin) {
        Set<BlockPos> eclaire = new HashSet<>(reseauEclaire());
        List<BlockPos> places = new ArrayList<>();
        for (int i = 0; i < galeries.size(); i++) {
            BoundingBox galerie = galeries.get(i);
            BlockPos milieu = new BlockPos(galerie.getCenter().getX(), galerie.minY(), galerie.getCenter().getZ());
            if (niveauxGaleries.get(i) == niveau && !eclaire.contains(milieu) && axes.stream().noneMatch(axe -> axe.isInside(milieu))
                    && interieur(milieu.getX(), milieu.getY(), milieu.getZ()) && !interieur(milieu.getX(), milieu.getY() - 1, milieu.getZ())
                    && Math.hypot(milieu.getX() - centre.getX(), milieu.getZ() - centre.getZ()) >= distanceMin) {
                places.add(milieu);
            }
        }
        return places;
    }

    /**
     * Le dernier recours de la traque, quand aucun point d'entrée n'est actif (petite distance de vue) : des places au sol des
     * galeries et des cavernes de ce niveau, tous les 6 blocs, entre {@code min} et {@code max} de cette position, de la plus
     * proche à la plus loin.
     */
    public List<BlockPos> placesAuSol(BlockPos pos, int niveau, double min, double max) {
        List<BlockPos> places = new ArrayList<>();
        int sol = sols.get(niveau);
        for (int x = Math.floorDiv(pos.getX() - (int) max, 6) * 6; x <= pos.getX() + max; x += 6) {
            for (int z = Math.floorDiv(pos.getZ() - (int) max, 6) * 6; z <= pos.getZ() + max; z += 6) {
                double distance = Math.hypot(x - pos.getX(), z - pos.getZ());
                if (distance >= min && distance <= max && interieur(x, sol, z) && interieur(x, sol + 1, z) && !interieur(x, sol - 1, z)) {
                    places.add(new BlockPos(x, sol, z));
                }
            }
        }
        places.sort((a, b) -> Double.compare(a.distSqr(pos), b.distSqr(pos)));
        return places;
    }

    /**
     * Le repli de la traque, quand aucun point ne convient à 24-64 blocs : les points d'entrée de ce niveau et des voisins, à
     * n'importe quelle distance, ceux de ce niveau d'abord ; du plus proche au plus loin dans chaque groupe. Un Gris plus
     * loin en hauteur ne servirait à rien (il ne sait pas monter une échelle).
     */
    public List<Entree> entreesDeRepli(BlockPos pos, int niveau) {
        List<Entree> toutes = new ArrayList<>(entrees.stream().filter(entree -> Math.abs(entree.niveau() - niveau) <= 1).toList());
        toutes.sort((a, b) -> {
            int ecart = Integer.compare(Math.abs(a.niveau() - niveau), Math.abs(b.niveau() - niveau));
            return ecart != 0 ? ecart : Double.compare(a.pos().distSqr(pos), b.pos().distSqr(pos));
        });
        return toutes;
    }

    /** Ce bloc est-il dans le vide de la mine (galeries, puits, caverne, salles) ? Les étais et les échelles y sont aussi. */
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
        for (Salle salle : salles) {
            if (salle.contient(x, y, z) || salle.ouvertures().stream().anyMatch(ouverture -> ouverture.isInside(x, y, z))) {
                return true;
            }
        }
        for (Caverne caverne : cavernes) {
            if (caverne.contient(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Comme {@link #interieur}, mais seulement parmi ces boîtes, rendues par {@link #boitesVers} (bien plus rapide). Les
     * boîtes de la caverne et des salles ne sont pas vides en entier.
     */
    public boolean interieurParmi(List<BoundingBox> boites, int x, int y, int z) {
        for (BoundingBox boite : boites) {
            if (!boite.isInside(x, y, z)) {
                continue;
            }
            Salle salle = sallesParBoite.get(boite);
            Caverne caverne = cavernesParBoite.get(boite);
            if (salle != null ? salle.contient(x, y, z) : caverne == null || caverne.contient(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /** Les boîtes de vide de tout ce qui croise cette boîte (galeries, puits, salles et ouvertures, boîte de la caverne). */
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
        for (Salle salle : salles) {
            if (salle.boite().intersects(zone)) {
                boites.add(salle.boite());
            }
            for (BoundingBox ouverture : salle.ouvertures()) {
                if (ouverture.intersects(zone)) {
                    boites.add(ouverture);
                }
            }
        }
        cavernesParBoite.keySet().forEach(boite -> {
            if (boite.intersects(zone)) {
                boites.add(boite);
            }
        });
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
