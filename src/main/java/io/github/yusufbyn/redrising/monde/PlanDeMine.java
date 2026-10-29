package io.github.yusufbyn.redrising.monde;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
 *     <li><b>Puits</b> : le principal au centre, de bas en haut jusqu'au bouchon, sous la surface de sa propre colonne ; deux
 *     secondaires entre deux niveaux.</li>
 *     <li><b>Caverne de la colonie</b> (G7.3) sur le niveau 3, au bout de l'axe : parcelles, place et échafaud (G7.10),
 *     point d'apparition du Rouge (G7.8), notés ici pour leurs tickets.</li>
 *     <li><b>Cellules</b> (G7.5) : {@value #CELLULES} emplacements laissés en roche pleine, au bout d'un cul-de-sac ou sur
 *     la paroi d'une galerie ; au moins {@value #CULS_DE_SAC_PAR_CELLULE} culs-de-sac sans cellule pour un avec, tous fermés
 *     par la même roche : un cul-de-sac ne trahit pas une cellule.</li>
 *     <li><b>Salles d'extraction</b> (G7.2.2) : une par case libre de la grille, ouverte de côté sur une galerie ; chambres à
 *     piliers, fronts de taille, salles de forage (niveaux 3 et 4), {@value #SALLES_PAR_NIVEAU_4} par niveau.</li>
 *     <li><b>Filons</b> (G7.2.2) : minerais riches et hélium-3, tirés avec le plan ({@link FilonsDeMine}).</li>
 *     <li><b>Coffres d'outils</b> (G7.2.2) : dans une salle sur deux, contre un mur ; les restes des mineurs, sans camp.</li>
 *     <li><b>Réseau éclairé</b> (G7.2.3) : lanternes le long de l'axe de chaque niveau, aux paliers des puits secondaires, à
 *     l'entrée de la caverne et sous le cadre des salles de forage ; aucun monstre n'apparaît sur ce réseau.</li>
 *     <li><b>Voies</b> (G7.2.3) : des rails au milieu de chaque branche de l'axe, un butoir à chaque bout (avant le quai du
 *     puits principal), un rail de propulsion tous les {@value #PAS_PROPULSION} blocs ; deux voies de garage par branche, dans
 *     une niche à côté du passage, avec un wagonnet chacune.</li>
 *     <li><b>Repères</b> (G7.2.3) : panneaux aux paliers des puits (niveau, colonie, puits secondaires), une bande de couleur
 *     le long des murs de l'axe, une teinte par niveau ; et les <b>points d'entrée des Gris</b> pour la traque (G7.2.4).</li>
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
    /**
     * 🟡 Salles d'extraction par niveau (chambres, fronts, forages), du haut vers le bas pour 4 niveaux ; avec moins de
     * niveaux, les lignes du bas, agrandies pour garder le même total.
     */
    static final int[][] SALLES = {{5, 3, 0}, {4, 4, 0}, {3, 3, 2}, {2, 2, 4}};
    public static final int SALLES_PAR_NIVEAU_4 = 8;
    /** 🟡 Entre le centre d'une salle et celui d'une cellule. */
    public static final int ECART_SALLE_CELLULE = 24;
    /** Chambre : 15 ou 13 de côté, 5 de haut. Front : 5 de large, 4 de haut. Forage : 13 de diamètre, voûte de 8. */
    static final int HAUTEUR_CHAMBRE = 5;
    static final int LARGEUR_FRONT = 5;
    static final int HAUTEUR_FRONT = 4;
    static final double RAYON_FORAGE = 6.5;
    static final int HAUTEUR_FORAGE = 8;
    /**
     * 🟡 Coffres d'outils : au plus {@value #COFFRES_RARES} avec la table rare (une pioche en fer très usée), au niveau du bas,
     * à plus de {@value #DISTANCE_COFFRE_RARE} blocs de la colonie.
     */
    public static final int COFFRES_RARES = 2;
    public static final int DISTANCE_COFFRE_RARE = 80;
    /** 🟡 Lanternes de l'axe : une tous les 12 blocs le long de sa ligne du milieu. */
    public static final int PAS_LANTERNES = 12;
    /** 🟡 Voies : un rail de propulsion tous les 16 blocs ; le quai du puits principal (3 blocs) avant le butoir. */
    public static final int PAS_PROPULSION = 16;
    static final int QUAI = 3;
    /** Voie de garage : une niche de 4 de long et 2 de haut, à 3 et 4 blocs de la ligne du milieu (hors du passage). */
    static final int LONGUEUR_GARAGE = 4;
    /** 🟡 Wagonnets posés à la génération, un par voie de garage : deux par voie, et le compte est complété sur les plus longues. */
    public static final int WAGONNETS = 32;
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
        /** Sur la paroi d'une galerie, de côté. */
        PAROI
    }

    public enum TypeSalle {
        /** Chambre à piliers : un carré, quatre piliers de roche de 3 × 3 autour du centre. */
        CHAMBRE,
        /** Front de taille : une longue salle le long d'une galerie, derrière un mur d'un bloc, deux ouvertures. */
        FRONT,
        /** Salle de forage : ronde, en voûte, un cadre de bois au centre (l'hélium-3, niveaux 3 et 4). */
        FORAGE
    }

    /**
     * Une salle d'extraction : sa boîte, son centre (x, z), et les ouvertures qui la relient à la galerie (vides en entier ;
     * elles mordent d'un bloc sur la galerie).
     */
    public record Salle(int niveau, TypeSalle type, BoundingBox boite, int x, int z, List<BoundingBox> ouvertures) {
        public boolean contient(int bx, int by, int bz) {
            if (!boite.isInside(bx, by, bz)) {
                return false;
            }
            return switch (type) {
                case FRONT -> true;
                // Piliers centrés à 3 blocs du centre, de 3 × 3 : une allée de 3 au milieu, d'autres contre les murs
                case CHAMBRE -> {
                    int dx = Math.abs(bx - x);
                    int dz = Math.abs(bz - z);
                    yield !(dx >= 2 && dx <= 4 && dz >= 2 && dz <= 4);
                }
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

    /** Une lanterne : suspendue (sous une poutre ou le plafond) ou posée au sol. */
    public record Lanterne(BlockPos pos, boolean suspendue) {
    }

    /** Un rail : sa position, son sens (le long de x ou de z), de propulsion ou non. */
    public record Rail(BlockPos pos, boolean surX, boolean propulsion) {
    }

    /** Une voie de l'axe : ses rails, ses deux butoirs (le premier côté puits), sa direction (vers l'extérieur) et son niveau. */
    public record Voie(int niveau, Direction sens, List<Rail> rails, BlockPos butoirPuits, BlockPos butoirBout) {
    }

    /** Une voie de garage : sa niche, ses rails, et la place de son wagonnet. */
    public record Garage(BoundingBox niche, List<Rail> rails, BlockPos wagonnet) {
    }

    /** Ce que dit un panneau : le niveau, la colonie (et de quel côté), ou le niveau où mène un puits secondaire. */
    public enum Texte {
        NIVEAU, COLONIE_DROITE, COLONIE_GAUCHE, PUITS_VERS
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
    private final List<Salle> salles;
    /** La salle de chaque boîte, par identité : ce sont les boîtes que rend {@link #boitesVers}. */
    private final Map<BoundingBox, Salle> sallesParBoite = new IdentityHashMap<>();
    private final BoundingBox boiteCaverne;
    private final List<FilonsDeMine.Bloc> filons;
    private final List<Coffre> coffres;
    private final List<BoundingBox> axes;
    private final List<Integer> niveauxAxes;
    private final List<Lanterne> lanternes;
    private final List<Voie> voies = new ArrayList<>();
    /** Les voies de garage ; vides tant qu'elles ne sont pas tirées (elles sont tirées en dernier, et creusent le vide). */
    private List<Garage> garages = List.of();
    private final List<Panneau> panneaux = new ArrayList<>();
    /** La bande de couleur de l'axe : chaque bloc de mur, et la ligne de son niveau (0 à 3, comptée pour 4 niveaux). */
    private final Map<BlockPos, Integer> bandes = new java.util.LinkedHashMap<>();
    private final List<Entree> entrees = new ArrayList<>();
    /** Les blocs de filon de chaque chunk, pour la pose. */
    private final Map<Long, List<FilonsDeMine.Bloc>> filonsParChunk = new HashMap<>();
    private final Set<BlockPos> poteaux;
    private final Set<BlockPos> poutres;
    private final BoundingBox bouchon;
    private final int hautDesBoites;
    private final int hautBoitePuits;

    private PlanDeMine(BlockPos centre, List<Integer> sols, List<BoundingBox> galeries, List<Integer> niveauxGaleries, List<Puits> puits,
                       Caverne caverne, Colonie colonie, List<EmplacementCellule> cellules, List<BlockPos> culsDeSac, List<Salle> salles,
                       Set<BlockPos> poteaux, Set<BlockPos> poutres, BoundingBox bouchon, int hautDesBoites, int hautBoitePuits, List<BoundingBox> axes,
                       List<Integer> niveauxAxes, Random hasard) {
        this.centre = centre;
        this.sols = List.copyOf(sols);
        this.galeries = List.copyOf(galeries);
        this.niveauxGaleries = List.copyOf(niveauxGaleries);
        this.puits = List.copyOf(puits);
        this.caverne = caverne;
        this.colonie = colonie;
        this.cellules = List.copyOf(cellules);
        this.culsDeSac = List.copyOf(culsDeSac);
        this.salles = List.copyOf(salles);
        this.salles.forEach(salle -> sallesParBoite.put(salle.boite(), salle));
        this.boiteCaverne = caverne.boite();
        this.poteaux = Set.copyOf(poteaux);
        this.poutres = Set.copyOf(poutres);
        this.bouchon = bouchon;
        this.hautDesBoites = hautDesBoites;
        this.hautBoitePuits = hautBoitePuits;
        // Les filons en dernier : ils lisent tout le reste du plan (le vide, les cellules, le bouchon)
        this.filons = FilonsDeMine.tirer(this, hasard);
        for (FilonsDeMine.Bloc bloc : filons) {
            filonsParChunk.computeIfAbsent(cleChunk(bloc.pos().getX() >> 4, bloc.pos().getZ() >> 4), cle -> new ArrayList<>()).add(bloc);
        }
        this.coffres = coffres(hasard);
        this.axes = List.copyOf(axes);
        this.niveauxAxes = List.copyOf(niveauxAxes);
        this.lanternes = placerLanternes();
        this.garages = poserVoies();
        poserReperes();
        choisirEntrees();
    }

    // --- Repères et points d'entrée des Gris (G7.2.3) ---

    /**
     * Les panneaux : au quai du puits principal sur chaque niveau (« Niveau N »), au quai de la branche de la colonie sur son
     * niveau (« Colonie → », la flèche du côté de la colonie pour qui lit), aux deux bouts de chaque puits secondaire
     * (« Puits → niveau N »). La bande de couleur : le mur de chaque côté de l'axe, à hauteur d'yeux.
     */
    private void poserReperes() {
        int niveauColonie = sols.indexOf(caverne.sol());
        Direction versColonie = Direction.getApproximateNearest(caverne.x() - centre.getX(), 0, caverne.z() - centre.getZ());
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            int sol = sols.get(niveau);
            Panneau niveauPose = null;
            for (Direction sens : Direction.Plane.HORIZONTAL) {
                for (Direction cote : List.of(sens.getClockWise(), sens.getCounterClockWise())) {
                    BlockPos pos = centre.atY(sol + 1).relative(sens, 3).relative(cote, 2);
                    BlockPos appui = pos.relative(cote);
                    if (!interieur(pos.getX(), pos.getY(), pos.getZ()) || interieur(appui.getX(), appui.getY(), appui.getZ())
                            || (niveauPose != null && niveauPose.pos().equals(pos))) {
                        continue;
                    }
                    if (niveauPose == null && (niveau != niveauColonie || sens != versColonie)) {
                        niveauPose = new Panneau(pos, cote.getOpposite(), Texte.NIVEAU, niveau + 1);
                        panneaux.add(niveauPose);
                    } else if (niveau == niveauColonie && sens == versColonie && panneaux.stream().noneMatch(p -> p.texte() != Texte.NIVEAU && p.texte() != Texte.PUITS_VERS)) {
                        // Pour qui regarde le mur (vers cote), sa droite est cote.getClockWise()
                        Texte texte = versColonie == cote.getClockWise() ? Texte.COLONIE_DROITE : Texte.COLONIE_GAUCHE;
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
            int haut = sols.indexOf(p.haut() - HAUTEUR + 1);
            panneauDePuits(p, p.bas() + 1, haut + 1);
            panneauDePuits(p, p.haut() - HAUTEUR + 2, bas + 1);
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
                    if (!interieur(pos.getX(), pos.getY(), pos.getZ()) && !caverne.boite().isInside(pos)) {
                        bandes.putIfAbsent(pos, ligne);
                    }
                }
            }
        }
    }

    /**
     * Un panneau « Puits → niveau N » à cette hauteur : contre une paroi du puits (à côté de l'échelle, pas sur elle) ; à un
     * carrefour, où des galeries traversent les parois, contre le mur d'une de ces galeries (ou de l'axe), à 3 à 5 blocs du puits.
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
                    // Une galerie (3 de large) ou l'axe (5 de large)
                    for (int ecart : new int[]{DEMI_LARGEUR, DEMI_LARGEUR_AXE}) {
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
     * chaque direction, les nœuds de l'axe (sauf sur un butoir), le fond de chaque puits secondaire. La traque (G7.2.4)
     * choisira le plus proche à 24 blocs au moins, hors de vue, sur le niveau du joueur ou un voisin.
     */
    private void choisirEntrees() {
        Set<BlockPos> sol = new HashSet<>(reseauEclaire());
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            BlockPos milieu = centre.atY(sols.get(niveau));
            for (Direction sens : Direction.Plane.HORIZONTAL) {
                ajouterEntree(sol, milieu.relative(sens, 3), niveau);
                for (int k = 1; k <= DEMI_GRILLE; k++) {
                    ajouterEntree(sol, milieu.relative(sens, k * ESPACEMENT), niveau);
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
     * (le quai) jusqu'au premier obstacle de la ligne du milieu (fin de l'axe, trou d'un puits secondaire, pilier d'une échelle,
     * caverne). Un butoir à chaque bout ; un rail de propulsion tous les {@value #PAS_PROPULSION} blocs (une torche de redstone
     * noyée deux blocs dessous). Puis deux voies de garage par voie, au tiers et aux deux tiers. Rend les garages.
     */
    private List<Garage> poserVoies() {
        List<Garage> liste = new ArrayList<>();
        Set<BlockPos> blocsDeFilon = new HashSet<>();
        filons.forEach(bloc -> blocsDeFilon.add(bloc.pos()));
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            int sol = sols.get(niveau);
            for (Direction sens : Direction.Plane.HORIZONTAL) {
                BlockPos depart = centre.atY(sol).relative(sens, 2 + QUAI);
                BlockPos.MutableBlockPos pos = depart.mutable();
                int longueur = 0;
                // Jusqu'au premier obstacle : vide de l'axe au sol et au-dessus, sol plein dessous, pas de pilier ni de caverne
                while (interieur(pos.getX(), sol, pos.getZ()) && interieur(pos.getX(), sol + 1, pos.getZ()) && !interieur(pos.getX(), sol - 1, pos.getZ())
                        && !caverne.contient(pos.getX(), sol, pos.getZ()) && puits.stream().noneMatch(p -> pilier(p, sol).equals(pos))
                        && enLigne(pos, sol)) {
                    longueur++;
                    pos.move(sens);
                }
                // Butoir côté puits, rails, butoir au bout : au moins 12 rails
                if (longueur < 14) {
                    continue;
                }
                boolean surX = sens.getAxis() == Direction.Axis.X;
                List<Rail> rails = new ArrayList<>();
                for (int t = 1; t < longueur - 1; t++) {
                    BlockPos rail = depart.relative(sens, t);
                    // Propulsion : la torche deux blocs dessous, posée sur la roche, sans vide autour
                    boolean propulsion = (t - 1) % PAS_PROPULSION == PAS_PROPULSION / 2 && !interieur(rail.getX(), sol - 2, rail.getZ())
                            && !interieur(rail.getX(), sol - 3, rail.getZ());
                    rails.add(new Rail(rail, surX, propulsion));
                }
                Voie voie = new Voie(niveau, sens, rails, depart, depart.relative(sens, longueur - 1));
                voies.add(voie);
                for (int k = 1; k <= 2; k++) {
                    Garage garage = garage(voie, rails.size() * k / 3, k == 1 ? 1 : -1, blocsDeFilon, liste);
                    if (garage != null) {
                        liste.add(garage);
                    }
                }
            }
        }
        // Le compte complété sur les voies les plus longues (une branche peut ne pas avoir de voie : la caverne est proche du puits)
        List<Voie> parLongueur = new ArrayList<>(voies);
        parLongueur.sort((a, b) -> Integer.compare(b.rails().size(), a.rails().size()));
        for (int tour = 0; tour < 4 && liste.size() < WAGONNETS; tour++) {
            for (Voie voie : parLongueur) {
                if (liste.size() == WAGONNETS) {
                    break;
                }
                Garage garage = garage(voie, voie.rails().size() * (2 * tour + 1) / 8, tour % 2 == 0 ? 1 : -1, blocsDeFilon, liste);
                if (garage != null) {
                    liste.add(garage);
                }
            }
        }
        return List.copyOf(liste.subList(0, Math.min(WAGONNETS, liste.size())));
    }

    /** Ce bloc est-il sur la ligne du milieu de l'axe (la croix qui passe par le puits principal) ? */
    private boolean enLigne(BlockPos pos, int sol) {
        return axes.stream().anyMatch(axe -> axe.isInside(pos.getX(), sol, pos.getZ()));
    }

    /**
     * Une voie de garage à côté de la voie, vers le rail {@code indice} (décalée de quelques rails au besoin), du côté
     * {@code cote} : une niche à 3 et 4 blocs de la ligne du milieu, qui ne touche rien d'autre que l'axe (ni galerie, ni
     * salle, ni puits, ni caverne, ni cellule, ni filon, ni autre garage) ; ses rails à 4 blocs, le wagonnet au milieu.
     */
    private @Nullable Garage garage(Voie voie, int indice, int cote, Set<BlockPos> blocsDeFilon, List<Garage> autres) {
        Direction sens = voie.sens();
        Direction travers = sens.getClockWise();
        int sol = sols.get(voie.niveau());
        for (int decalage : new int[]{0, 2, -2, 4, -4, 6, -6, 8, -8}) {
            int i = indice + decalage;
            if (i < 2 || i + LONGUEUR_GARAGE >= voie.rails().size() - 2) {
                continue;
            }
            BlockPos debut = voie.rails().get(i).pos();
            BlockPos fin = debut.relative(sens, LONGUEUR_GARAGE - 1);
            BlockPos a = debut.relative(travers, 3 * cote);
            BlockPos b = fin.relative(travers, 4 * cote).above();
            BoundingBox niche = BoundingBox.fromCorners(a, b);
            // La niche et un bloc de roche autour (sauf du côté de l'axe)
            BoundingBox garde = BoundingBox.fromCorners(debut.relative(sens.getOpposite()).relative(travers, 3 * cote).below(),
                    fin.relative(sens).relative(travers, 5 * cote).above(2));
            boolean libre = galeries.stream().noneMatch(g -> g.intersects(garde))
                    && puits.stream().noneMatch(p -> p.interieur().inflatedBy(COQUE_PUITS).intersects(garde))
                    && salles.stream().noneMatch(salle -> salle.boite().intersects(garde) || salle.ouvertures().stream().anyMatch(o -> o.intersects(garde)))
                    && !garde.intersects(caverne.boite())
                    && cellules.stream().noneMatch(cellule -> cellule.avecMurs().inflatedBy(1).intersects(garde))
                    && autres.stream().noneMatch(autre -> autre.niche().inflatedBy(1).intersects(garde))
                    && blocsDeFilon.stream().noneMatch(niche::isInside)
                    // Pas de poteau d'étai devant l'entrée de la niche
                    && poteaux.stream().noneMatch(poteau -> poteau.getY() == sol && BoundingBox.fromCorners(debut.relative(travers, 2 * cote),
                    fin.relative(travers, 2 * cote)).isInside(poteau));
            if (!libre) {
                continue;
            }
            List<Rail> rails = new ArrayList<>();
            for (int t = 0; t < LONGUEUR_GARAGE; t++) {
                rails.add(new Rail(debut.relative(sens, t).relative(travers, 4 * cote), sens.getAxis() == Direction.Axis.X, false));
            }
            return new Garage(niche, rails, rails.get(1).pos());
        }
        return null;
    }

    // --- Réseau éclairé (G7.2.3) ---

    /**
     * Les lanternes : le long de la ligne du milieu de chaque galerie de l'axe, tous les {@value #PAS_LANTERNES} blocs
     * (suspendues sous la poutre d'un étai s'il y en a une, sinon sous le plafond de roche ; pas au-dessus d'un puits ni dans
     * la caverne) ; au palier du bas de chaque puits secondaire (posée au sol, dans un coin) et en haut (sous le plafond) ; deux
     * à l'entrée de la caverne ; une sous le cadre de chaque salle de forage.
     */
    private List<Lanterne> placerLanternes() {
        Map<BlockPos, Lanterne> liste = new java.util.LinkedHashMap<>();
        for (int i = 0; i < axes.size(); i++) {
            BoundingBox axe = axes.get(i);
            boolean surX = axe.getXSpan() > axe.getZSpan();
            int debut = surX ? axe.minX() : axe.minZ();
            int fin = surX ? axe.maxX() : axe.maxZ();
            int milieu = surX ? (axe.minZ() + axe.maxZ()) / 2 : (axe.minX() + axe.maxX()) / 2;
            for (int cible = debut + PAS_LANTERNES / 2; cible <= fin; cible += PAS_LANTERNES) {
                // À la place prévue, sinon décalée de quelques blocs le long de l'axe (au-dessus d'un puits, sous un vide)
                for (int decalage : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5}) {
                    int t = cible + decalage;
                    int x = surX ? t : milieu;
                    int z = surX ? milieu : t;
                    int haut = axe.maxY();
                    BlockPos pos = poutres.contains(new BlockPos(x, haut, z)) ? new BlockPos(x, haut - 1, z) : new BlockPos(x, haut, z);
                    if (t < debut || t > fin || caverne.contient(x, axe.minY(), z)) {
                        continue;
                    }
                    if (dansUnPuits(puits, pos) || dansUnPuits(puits, pos.below()) || (pos.getY() == haut && interieur(x, haut + 1, z))) {
                        continue;
                    }
                    liste.putIfAbsent(pos, new Lanterne(pos, true));
                    break;
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
            // En haut : sous le plafond de la galerie, à côté du trou
            int y = p.haut();
            while (interieur(p.x() + 1, y + 1, p.z() + 1)) {
                y++;
            }
            BlockPos haut = new BlockPos(p.x() + 1, y, p.z() + 1);
            liste.putIfAbsent(haut, new Lanterne(haut, true));
        }
        // Entrée de la caverne : au bout de l'axe qui y mène, deux lanternes au sol de part et d'autre
        int sens = Integer.signum((caverne.x() - centre.getX()) + (caverne.z() - centre.getZ()));
        boolean surX = caverne.x() != centre.getX();
        for (int t = 0; t <= DISTANCE_CAVERNE; t++) {
            int x = surX ? centre.getX() + sens * t : centre.getX();
            int z = surX ? centre.getZ() : centre.getZ() + sens * t;
            if (caverne.contient(x, caverne.sol(), z)) {
                for (int cote = -1; cote <= 1; cote += 2) {
                    BlockPos pos = surX ? new BlockPos(x + 2 * sens, caverne.sol(), z + 3 * cote) : new BlockPos(x + 3 * cote, caverne.sol(), z + 2 * sens);
                    if (caverne.contient(pos.getX(), pos.getY(), pos.getZ())) {
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
        return List.copyOf(liste.values());
    }

    /**
     * Le sol du réseau éclairé, où aucun monstre ne doit apparaître : chaque bloc de vide au ras du sol des galeries de l'axe
     * (hors de la caverne, éclairée en G7.3, du trou des puits, des piliers qui tiennent leur échelle et des butoirs), et le
     * fond de chaque puits secondaire.
     */
    public List<BlockPos> reseauEclaire() {
        Set<BlockPos> sol = new LinkedHashSet<>();
        for (BoundingBox axe : axes) {
            for (BlockPos pos : BlockPos.betweenClosed(axe.minX(), axe.minY(), axe.minZ(), axe.maxX(), axe.minY(), axe.maxZ())) {
                if (!caverne.contient(pos.getX(), pos.getY(), pos.getZ()) && !dansUnPuits(puits, pos) && !dansUnPuits(puits, pos.below())
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
        return List.copyOf(sol);
    }

    /**
     * Les coffres d'outils : une salle sur deux, un coffre au sol contre un mur, loin des ouvertures et hors des étais.
     * D'abord les coffres rares : 1 à {@value #COFFRES_RARES}, dans les salles du niveau du bas à plus de
     * {@value #DISTANCE_COFFRE_RARE} blocs de la colonie ; s'il n'y en a aucune, un seul, dans la salle du niveau du bas la
     * plus éloignée. Puis les autres salles, dans un ordre tiré.
     */
    private List<Coffre> coffres(Random hasard) {
        BoundingBox zoneCaverne = caverne.boite();
        List<Salle> ordre = new ArrayList<>(salles);
        Collections.shuffle(ordre, hasard);
        List<Salle> duBas = new ArrayList<>(ordre.stream().filter(salle -> salle.niveau() == sols.size() - 1).toList());
        duBas.sort((a, b) -> Double.compare(distance(zoneCaverne, b.x(), b.z()), distance(zoneCaverne, a.x(), a.z())));
        long lointaines = duBas.stream().filter(salle -> distance(zoneCaverne, salle.x(), salle.z()) > DISTANCE_COFFRE_RARE).count();
        long voulus = Math.clamp(lointaines, 1, COFFRES_RARES);
        List<Coffre> liste = new ArrayList<>();
        Set<Salle> servies = new HashSet<>();
        for (Salle salle : duBas) {
            if (liste.size() == voulus) {
                break;
            }
            Coffre coffre = coffreContreUnMur(salle, true, hasard);
            if (coffre != null) {
                liste.add(coffre);
                servies.add(salle);
            }
        }
        for (Salle salle : ordre) {
            if (liste.size() == salles.size() / 2) {
                break;
            }
            if (servies.contains(salle)) {
                continue;
            }
            Coffre coffre = coffreContreUnMur(salle, false, hasard);
            if (coffre != null) {
                liste.add(coffre);
            }
        }
        return liste;
    }

    private static double distance(BoundingBox boite, int x, int z) {
        return Math.hypot(Math.max(0, Math.max(boite.minX() - x, x - boite.maxX())), Math.max(0, Math.max(boite.minZ() - z, z - boite.maxZ())));
    }

    /** Un coffre au sol de cette salle, contre un mur, tourné vers la salle, ou {@code null} faute de place. */
    private @Nullable Coffre coffreContreUnMur(Salle salle, boolean rare, Random hasard) {
        BoundingBox b = salle.boite();
        int sol = b.minY();
        for (int essai = 0; essai < 60; essai++) {
            BlockPos pos = new BlockPos(b.minX() + hasard.nextInt(b.getXSpan()), sol, b.minZ() + hasard.nextInt(b.getZSpan()));
            if (!salle.contient(pos.getX(), sol, pos.getZ()) || !salle.contient(pos.getX(), sol + 1, pos.getZ()) || poteaux.contains(pos)
                    || salle.ouvertures().stream().anyMatch(ouverture -> ouverture.inflatedBy(2).isInside(pos))) {
                continue;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos voisin = pos.relative(direction);
                if (!interieur(voisin.getX(), voisin.getY(), voisin.getZ())) {
                    return new Coffre(pos, direction.getOpposite(), rare);
                }
            }
        }
        return null;
    }

    private static long cleChunk(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    // --- Tirage ---

    private record Cle(long graine, BlockPos centre, int solMinimum, int solAuPuits, int surfacePuits) {
    }

    /** Un plan est tiré une fois par mine, puis relu : la pose de chaque chunk le redemande (fils de génération). */
    private static final Map<Cle, PlanDeMine> CACHE = new ConcurrentHashMap<>();

    /**
     * Le plan de la mine centrée ici.
     *
     * @param graine     la graine du monde
     * @param solMinimum le terrain le plus bas de l'emprise (le haut de la mine reste dessous, avec sa marge)
     * @param solAuPuits   le terrain le plus bas autour du puits principal (16 blocs) : la boîte de remplissage du puits
     *                     s'arrête à {@value #BOITE_PUITS_SOUS_SURFACE} blocs plus bas
     * @param surfacePuits la surface de la colonne du puits principal (3 × 3, son point le plus bas, hauteur au-dessus du
     *                     dernier bloc) : le bouchon est dessous, sous {@value #ROCHE_SUR_BOUCHON} blocs de roche d'origine
     */
    public static PlanDeMine de(long graine, BlockPos centre, int solMinimum, int solAuPuits, int surfacePuits) {
        return CACHE.computeIfAbsent(new Cle(graine, centre.immutable(), solMinimum, solAuPuits, surfacePuits),
                cle -> tirer(cle.graine(), cle.centre(), cle.solMinimum(), cle.solAuPuits(), cle.surfacePuits()));
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

    static PlanDeMine tirer(long graine, BlockPos centre, int solMinimum, int solAuPuits, int surfacePuits) {
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
        List<BoundingBox> axes = new ArrayList<>();
        List<Integer> niveauxAxes = new ArrayList<>();
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            for (Arete arete : aretesParNiveau.get(niveau)) {
                BoundingBox boite = galerie(arete, sols.get(niveau), centre);
                galeries.add(boite);
                niveauxGaleries.add(niveau);
                etayer(boite, arete, centre, poteaux, poutres);
                if (arete.axe()) {
                    axes.add(boite);
                    niveauxAxes.add(niveau);
                }
            }
        }

        // Puits principal, jusqu'au bouchon : 3 blocs de roche, puis les 2 couches du bouchon, sous la surface de sa colonne
        int hautPuits = Math.max(surfacePuits - 1 - ROCHE_SUR_BOUCHON - 2, sols.getFirst() + HAUTEUR_AXE);
        puits.addFirst(new Puits(centre.getX(), centre.getZ(), sols.getLast(), hautPuits, true));
        BoundingBox bouchon = new BoundingBox(centre.getX() - 1, hautPuits + 1, centre.getZ() - 1, centre.getX() + 1, hautPuits + 2, centre.getZ() + 1);

        Colonie colonie = colonie(caverne, caverneSurX, dx + dz);
        List<BlockPos> culsDeSac = new ArrayList<>();
        List<EmplacementCellule> cellules = cellules(sols, aretesParNiveau, galeries, puits, caverne, centre, hasard, culsDeSac);
        List<Salle> salles = salles(sols, aretesParNiveau, galeries, puits, zoneCaverne, cellules, centre, hasard, poteaux, poutres);
        int hautDesBoites = sols.getFirst() + HAUTEUR_AXE + 2;
        int hautBoitePuits = Math.max(hautDesBoites, solAuPuits - BOITE_PUITS_SOUS_SURFACE);
        // Les étais ne restent pas dans la caverne ni dans les puits
        poteaux.removeIf(pos -> caverne.contient(pos.getX(), pos.getY(), pos.getZ()) || dansUnPuits(puits, pos));
        poutres.removeIf(pos -> caverne.contient(pos.getX(), pos.getY(), pos.getZ()) || dansUnPuits(puits, pos));
        return new PlanDeMine(centre, sols, galeries, niveauxGaleries, puits, caverne, colonie, cellules, culsDeSac, salles, poteaux, poutres,
                bouchon, hautDesBoites, hautBoitePuits, axes, niveauxAxes, hasard);
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

    // --- Salles d'extraction ---

    /** Un côté d'une case de la grille : la galerie qui le longe, et la direction de la galerie vers l'intérieur de la case. */
    private record Cote(BoundingBox galerie, int nx, int nz) {
        /** Le pas vers l'intérieur de la case (+1 ou −1), le long de l'axe qui traverse le côté. */
        int sens() {
            return nx != 0 ? nx : nz;
        }

        /** Le bord de la galerie du côté de la case. */
        int bord() {
            return nx > 0 ? galerie.maxX() : nx < 0 ? galerie.minX() : nz > 0 ? galerie.maxZ() : galerie.minZ();
        }

        /** Une boîte de {@code a} à {@code b} en traversant le côté, de {@code long1} à {@code long2} le long du côté. */
        BoundingBox bande(int a, int b, int long1, int long2, int bas, int haut) {
            return nx != 0 ? new BoundingBox(Math.min(a, b), bas, long1, Math.max(a, b), haut, long2)
                    : new BoundingBox(long1, bas, Math.min(a, b), long2, haut, Math.max(a, b));
        }
    }

    /**
     * Les salles d'extraction de chaque niveau ({@link #SALLES}) : les cases de la grille dans un ordre tiré ; les forages
     * d'abord (les plus grands), puis les chambres, puis les fronts. Une case prend une salle si une galerie la longe et que
     * la salle reste loin de tout le reste ({@link #salleLibre}). Retire les étais des galeries devant les ouvertures, et
     * ajoute ceux des fronts et le cadre des forages.
     */
    private static List<Salle> salles(List<Integer> sols, List<Set<Arete>> aretesParNiveau, List<BoundingBox> galeries, List<Puits> puits,
                                      BoundingBox zoneCaverne, List<EmplacementCellule> cellules, BlockPos centre, Random hasard,
                                      Set<BlockPos> poteaux, Set<BlockPos> poutres) {
        List<Salle> salles = new ArrayList<>();
        for (int niveau = 0; niveau < sols.size(); niveau++) {
            int sol = sols.get(niveau);
            int[] ligne = SALLES[SALLES.length - sols.size() + niveau];
            int voulu = Math.ceilDiv(SALLES_PAR_NIVEAU_4 * NIVEAUX_MAX, sols.size());
            Map<List<Noeud>, Arete> aretes = new HashMap<>();
            for (Arete arete : aretesParNiveau.get(niveau)) {
                aretes.put(List.of(arete.a(), arete.b()), arete);
            }
            List<Noeud> cases = new ArrayList<>();
            for (int i = -DEMI_GRILLE; i < DEMI_GRILLE; i++) {
                for (int j = -DEMI_GRILLE; j < DEMI_GRILLE; j++) {
                    cases.add(new Noeud(i, j));
                }
            }
            Collections.shuffle(cases, hasard);
            Set<Noeud> prises = new HashSet<>();
            for (TypeSalle type : List.of(TypeSalle.FORAGE, TypeSalle.CHAMBRE, TypeSalle.FRONT)) {
                int nombre = (int) Math.round((double) ligne[type.ordinal()] * voulu / SALLES_PAR_NIVEAU_4);
                int poses = 0;
                for (Noeud coin : cases) {
                    if (poses == nombre) {
                        break;
                    }
                    if (prises.contains(coin)) {
                        continue;
                    }
                    Salle salle = salleDansLaCase(niveau, type, coin, sol, aretes, centre, hasard, galeries, puits, zoneCaverne, cellules, salles);
                    if (salle != null) {
                        salles.add(salle);
                        prises.add(coin);
                        poses++;
                    }
                }
            }
        }
        for (Salle salle : salles) {
            poteaux.removeIf(pos -> salle.ouvertures().stream().anyMatch(ouverture -> ouverture.isInside(pos)));
            poutres.removeIf(pos -> salle.ouvertures().stream().anyMatch(ouverture -> ouverture.isInside(pos)));
            BoundingBox b = salle.boite();
            if (salle.type() == TypeSalle.FRONT) {
                // Étais tous les 6 blocs, comme dans les galeries
                boolean surX = b.getXSpan() > b.getZSpan();
                int debut = surX ? b.minX() : b.minZ();
                int fin = surX ? b.maxX() : b.maxZ();
                for (int t = debut + 3; t <= fin - 3; t += PAS_ETAIS) {
                    for (int y = b.minY(); y < b.maxY(); y++) {
                        poteaux.add(surX ? new BlockPos(t, y, b.minZ()) : new BlockPos(b.minX(), y, t));
                        poteaux.add(surX ? new BlockPos(t, y, b.maxZ()) : new BlockPos(b.maxX(), y, t));
                    }
                    for (int c = surX ? b.minZ() : b.minX(); c <= (surX ? b.maxZ() : b.maxX()); c++) {
                        poutres.add(surX ? new BlockPos(t, b.maxY(), c) : new BlockPos(c, b.maxY(), t));
                    }
                }
            } else if (salle.type() == TypeSalle.FORAGE) {
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

    /** Une salle de ce type dans la case dont le coin est le nœud {@code coin}, ou {@code null} si elle n'y tient pas. */
    private static @Nullable Salle salleDansLaCase(int niveau, TypeSalle type, Noeud coin, int sol, Map<List<Noeud>, Arete> aretes, BlockPos centre,
                                                   Random hasard, List<BoundingBox> galeries, List<Puits> puits, BoundingBox zoneCaverne,
                                                   List<EmplacementCellule> cellules, List<Salle> salles) {
        int i = coin.i();
        int j = coin.j();
        // Les côtés de la case que longe une galerie
        List<Cote> cotes = new ArrayList<>();
        ajouterCote(cotes, aretes, new Noeud(i, j), new Noeud(i + 1, j), sol, centre, 0, 1);
        ajouterCote(cotes, aretes, new Noeud(i, j + 1), new Noeud(i + 1, j + 1), sol, centre, 0, -1);
        ajouterCote(cotes, aretes, new Noeud(i, j), new Noeud(i, j + 1), sol, centre, 1, 0);
        ajouterCote(cotes, aretes, new Noeud(i + 1, j), new Noeud(i + 1, j + 1), sol, centre, -1, 0);
        Collections.shuffle(cotes, hasard);
        int cx = centre.getX() + i * ESPACEMENT + ESPACEMENT / 2;
        int cz = centre.getZ() + j * ESPACEMENT + ESPACEMENT / 2;
        for (Cote cote : cotes) {
            // La plus grande d'abord ; plus petite le long de l'axe (5 de large) ou près d'un puits
            List<Salle> essais = switch (type) {
                case CHAMBRE -> List.of(chambre(niveau, cx, cz, sol, 7, cote), chambre(niveau, cx, cz, sol, 6, cote));
                case FRONT -> List.of(front(niveau, cx, cz, sol, 7, cote), front(niveau, cx, cz, sol, 6, cote));
                case FORAGE -> List.of(forage(niveau, cx, cz, sol, cote));
            };
            for (Salle salle : essais) {
                if (salleLibre(salle, galeries, puits, zoneCaverne, cellules, salles)) {
                    return salle;
                }
            }
        }
        return null;
    }

    private static void ajouterCote(List<Cote> cotes, Map<List<Noeud>, Arete> aretes, Noeud a, Noeud b, int sol, BlockPos centre, int nx, int nz) {
        Arete arete = aretes.get(List.of(a, b));
        if (arete != null) {
            cotes.add(new Cote(galerie(arete, sol, centre), nx, nz));
        }
    }

    /** Chambre à piliers centrée dans la case, de demi-côté {@code demi}, et son couloir. */
    private static Salle chambre(int niveau, int cx, int cz, int sol, int demi, Cote cote) {
        BoundingBox boite = new BoundingBox(cx - demi, sol, cz - demi, cx + demi, sol + HAUTEUR_CHAMBRE - 1, cz + demi);
        return new Salle(niveau, TypeSalle.CHAMBRE, boite, cx, cz, List.of(couloir(cote, cx, cz, sol)));
    }

    /** Salle de forage au centre de la case, et son couloir. */
    private static Salle forage(int niveau, int cx, int cz, int sol, Cote cote) {
        int r = (int) RAYON_FORAGE;
        BoundingBox boite = new BoundingBox(cx - r, sol, cz - r, cx + r, sol + HAUTEUR_FORAGE - 1, cz + r);
        return new Salle(niveau, TypeSalle.FORAGE, boite, cx, cz, List.of(couloir(cote, cx, cz, sol)));
    }

    /** Un couloir de 3 de large et 3 de haut, du bord de la galerie à 4 blocs du centre de la salle (par l'allée du milieu). */
    private static BoundingBox couloir(Cote cote, int cx, int cz, int sol) {
        int fin = (cote.nx() != 0 ? cx : cz) - cote.sens() * 4;
        int milieu = cote.nx() != 0 ? cz : cx;
        return cote.bande(cote.bord(), fin, milieu - DEMI_LARGEUR, milieu + DEMI_LARGEUR, sol, sol + HAUTEUR - 1);
    }

    /**
     * Front de taille le long de la galerie : un mur d'un bloc, puis {@value #LARGEUR_FRONT} de large, sur 2 × {@code demi} + 1
     * de long, centré sur la case ; deux ouvertures de 2 blocs aux bouts, à travers le mur.
     */
    private static Salle front(int niveau, int cx, int cz, int sol, int demi, Cote cote) {
        int debut = cote.bord() + 2 * cote.sens();
        int fond = debut + (LARGEUR_FRONT - 1) * cote.sens();
        int milieu = cote.nx() != 0 ? cz : cx;
        BoundingBox boite = cote.bande(debut, fond, milieu - demi, milieu + demi, sol, sol + HAUTEUR_FRONT - 1);
        List<BoundingBox> ouvertures = List.of(
                cote.bande(cote.bord(), debut, milieu - demi, milieu - demi + 1, sol, sol + HAUTEUR - 1),
                cote.bande(cote.bord(), debut, milieu + demi - 1, milieu + demi, sol, sol + HAUTEUR - 1));
        BlockPos milieuBoite = boite.getCenter();
        return new Salle(niveau, TypeSalle.FRONT, boite, milieuBoite.getX(), milieuBoite.getZ(), ouvertures);
    }

    /**
     * Une salle loin de tout : sa boîte à un bloc de roche au moins des galeries ; elle et ses ouvertures hors des coques des
     * puits et à distance de la caverne, à 2 blocs de roche des cellules (et {@value #ECART_SALLE_CELLULE} blocs entre les
     * centres), à 2 blocs d'une autre salle.
     */
    private static boolean salleLibre(Salle salle, List<BoundingBox> galeries, List<Puits> puits, BoundingBox zoneCaverne,
                                      List<EmplacementCellule> cellules, List<Salle> salles) {
        BoundingBox autour = salle.boite().inflatedBy(1);
        if (galeries.stream().anyMatch(galerie -> galerie.intersects(autour))) {
            return false;
        }
        List<BoundingBox> vides = new ArrayList<>(salle.ouvertures());
        vides.add(salle.boite());
        for (BoundingBox vide : vides) {
            if (vide.inflatedBy(1).intersects(zoneCaverne) || puits.stream().anyMatch(p -> p.interieur().inflatedBy(COQUE_PUITS).intersects(vide))
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

    public Puits puitsPrincipal() {
        return puits.getFirst();
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
        for (Garage garage : garages) {
            if (garage.niche().isInside(x, y, z)) {
                return true;
            }
        }
        return caverne.contient(x, y, z);
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
            if (salle != null ? salle.contient(x, y, z) : boite != boiteCaverne || caverne.contient(x, y, z)) {
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
        for (Garage garage : garages) {
            if (garage.niche().intersects(zone)) {
                boites.add(garage.niche());
            }
        }
        if (boiteCaverne.intersects(zone)) {
            boites.add(boiteCaverne);
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
