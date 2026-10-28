package io.github.yusufbyn.redrising.monde;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Les filons riches de la mine (G7.2.2, GDD section 7) : tirés avec le plan ({@link PlanDeMine}), logique pure (testée), sans
 * bloc ; la pose choisit la variante (pierre ou ardoise des profondeurs) selon la roche qu'elle remplace
 * ({@link MineLykos}). Valeurs en 🟡 dans le GDD, toutes ici.
 *
 * <ul>
 *     <li><b>Filons visibles</b> : un départ dans le mur d'une galerie ou d'une salle (les salles comptent plus : ce sont
 *     les chantiers), puis la moitié du filon le long du mur (on le voit), l'autre moitié dans la roche (on le suit en
 *     creusant).</li>
 *     <li><b>Hélium-3</b> : dans les salles de forage (sol et parois, tout visible), en poches dans les galeries lointaines,
 *     et environ {@value #HELIUM3_CACHE} blocs dispersés dans la roche, en petits filons qu'aucun vide ne touche.</li>
 *     <li><b>Épuisement</b> : rien à moins de {@value #EPUISEMENT} blocs de la caverne de la colonie, ni du chemin entre le
 *     puits principal et la caverne (l'axe travaillé depuis des générations).</li>
 * </ul>
 */
public final class FilonsDeMine {
    public enum Minerai {
        CHARBON, FER, CUIVRE, OR, REDSTONE, HELIUM3
    }

    /** Un bloc de filon. */
    public record Bloc(BlockPos pos, Minerai minerai) {
    }

    /**
     * 🟡 Une sorte de filon visible : son minerai, les niveaux (comptés pour 4 niveaux, 0 en haut ; avec moins de niveaux,
     * ceux du bas), le nombre de filons dans toute la mine, leur taille.
     */
    record Sorte(Minerai minerai, int premier, int dernier, int nombre, int tailleMin, int tailleMax) {
    }

    static final List<Sorte> SORTES = List.of(
            new Sorte(Minerai.CHARBON, 0, 3, 48, 20, 30),
            new Sorte(Minerai.FER, 1, 3, 36, 14, 20),
            new Sorte(Minerai.CUIVRE, 0, 1, 16, 15, 25),
            new Sorte(Minerai.OR, 2, 3, 14, 8, 12),
            new Sorte(Minerai.REDSTONE, 3, 3, 14, 10, 14),
            // Poches d'hélium-3 dans les galeries lointaines
            new Sorte(Minerai.HELIUM3, 2, 3, 16, 4, 6));
    /** 🟡 Hélium-3 des salles de forage : de 10 à 14 blocs chacune, au sol et sur les parois. */
    static final int HELIUM3_FORAGE_MIN = 10;
    static final int HELIUM3_FORAGE_MAX = 14;
    /** 🟡 Hélium-3 caché dans la roche (niveaux 3 et 4) : environ 300 blocs, en filons de 3 à 6. */
    public static final int HELIUM3_CACHE = 300;
    /** 🟡 Les poches d'hélium-3 : à 60 blocs au moins du puits principal. */
    static final int LOINTAIN = 60;
    /** 🟡 Épuisement : autour de la colonie et du chemin du puits à la colonie. */
    public static final int EPUISEMENT = 40;
    /** Les salles comptent pour 4 galeries dans le choix d'un départ. */
    static final int POIDS_SALLE = 4;

    private final PlanDeMine plan;
    private final Random hasard;
    private final List<Bloc> blocs = new ArrayList<>();
    private final Set<BlockPos> pris = new LinkedHashSet<>();
    /** Les boîtes de vide autour de chaque chunk (comme pour la pose) : tester le vide est bien plus rapide. */
    private final Map<Long, List<BoundingBox>> videsParChunk = new HashMap<>();

    private FilonsDeMine(PlanDeMine plan, Random hasard) {
        this.plan = plan;
        this.hasard = hasard;
    }

    /** Les filons de ce plan (appelé à la construction du plan, avec son tirage). */
    static List<Bloc> tirer(PlanDeMine plan, Random hasard) {
        FilonsDeMine filons = new FilonsDeMine(plan, hasard);
        filons.visibles();
        filons.forages();
        filons.caches();
        return List.copyOf(filons.blocs);
    }

    // --- Les trois sortes ---

    /** Les filons visibles de chaque sorte, répartis entre les niveaux où elle se trouve. */
    private void visibles() {
        int niveaux = plan.sols().size();
        for (Sorte sorte : SORTES) {
            List<Integer> permis = new ArrayList<>();
            for (int niveau = 0; niveau < niveaux; niveau++) {
                int ligne = PlanDeMine.NIVEAUX_MAX - niveaux + niveau;
                if (ligne >= sorte.premier() && ligne <= sorte.dernier()) {
                    permis.add(niveau);
                }
            }
            if (permis.isEmpty()) {
                continue;
            }
            for (int n = 0; n < sorte.nombre(); n++) {
                int niveau = permis.get(n % permis.size());
                int taille = sorte.tailleMin() + hasard.nextInt(sorte.tailleMax() - sorte.tailleMin() + 1);
                boolean lointain = sorte.minerai() == Minerai.HELIUM3;
                // Quelques essais pour trouver un départ qui convient
                for (int essai = 0; essai < 20; essai++) {
                    BlockPos depart = departDansUnMur(niveau, lointain);
                    if (depart != null) {
                        pousser(depart, sorte.minerai(), taille, (taille + 1) / 2, niveau);
                        break;
                    }
                }
            }
        }
    }

    /** L'hélium-3 des salles de forage : tout le long du sol et des parois. */
    private void forages() {
        for (PlanDeMine.Salle salle : plan.salles()) {
            if (salle.type() != PlanDeMine.TypeSalle.FORAGE) {
                continue;
            }
            int taille = HELIUM3_FORAGE_MIN + hasard.nextInt(HELIUM3_FORAGE_MAX - HELIUM3_FORAGE_MIN + 1);
            int poses = 0;
            for (int essai = 0; essai < 40 && poses < taille; essai++) {
                BlockPos depart = mur(interieurDe(salle.boite(), salle), hasard.nextInt(3) == 0 ? Direction.DOWN : horizontale());
                if (depart != null && permis(depart) && dansLaTranche(depart, salle.niveau())) {
                    poses += pousser(depart, Minerai.HELIUM3, Math.min(4, taille - poses), Integer.MAX_VALUE, salle.niveau());
                }
            }
        }
    }

    /** L'hélium-3 caché : de petits filons dans la roche des niveaux 3 et 4, qu'aucun vide ne touche. */
    private void caches() {
        int niveaux = plan.sols().size();
        List<Integer> permis = new ArrayList<>();
        for (int niveau = 0; niveau < niveaux; niveau++) {
            if (PlanDeMine.NIVEAUX_MAX - niveaux + niveau >= 2) {
                permis.add(niveau);
            }
        }
        int poses = 0;
        BlockPos centre = plan.centre();
        int demi = PlanDeMine.ESPACEMENT * PlanDeMine.DEMI_GRILLE + 10;
        for (int essai = 0; essai < 4000 && poses < HELIUM3_CACHE && !permis.isEmpty(); essai++) {
            int niveau = permis.get(essai % permis.size());
            int sol = plan.sols().get(niveau);
            BlockPos depart = new BlockPos(centre.getX() - demi + hasard.nextInt(2 * demi + 1), sol - 5 + hasard.nextInt(14),
                    centre.getZ() - demi + hasard.nextInt(2 * demi + 1));
            if (permis(depart) && !touche(depart) && !epuise(depart)) {
                poses += pousser(depart, Minerai.HELIUM3, 3 + hasard.nextInt(4), 0, niveau);
            }
        }
    }

    // --- Croissance d'un filon ---

    /**
     * Fait pousser un filon depuis {@code depart} : chaque bloc à côté d'un bloc déjà pris, dans la roche. Les
     * {@code visibles} premiers touchent le vide de la mine (le long du mur), les suivants non (dans la roche) ; s'il n'y en a
     * plus de la sorte voulue, n'importe lequel. Le filon reste dans la tranche de son niveau. Renvoie le nombre de blocs posés.
     */
    private int pousser(BlockPos depart, Minerai minerai, int taille, int visibles, int niveau) {
        if (!pris.add(depart)) {
            return 0;
        }
        List<BlockPos> filon = new ArrayList<>(List.of(depart));
        blocs.add(new Bloc(depart, minerai));
        while (filon.size() < taille) {
            boolean voulu = filon.size() < visibles;
            List<BlockPos> preferes = new ArrayList<>();
            List<BlockPos> autres = new ArrayList<>();
            for (BlockPos bloc : filon) {
                for (Direction direction : Direction.values()) {
                    BlockPos voisin = bloc.relative(direction);
                    if (pris.contains(voisin) || !permis(voisin) || !dansLaTranche(voisin, niveau)) {
                        continue;
                    }
                    (touche(voisin) == voulu ? preferes : autres).add(voisin);
                }
            }
            List<BlockPos> choix = preferes.isEmpty() ? autres : preferes;
            if (choix.isEmpty()) {
                break;
            }
            BlockPos suivant = choix.get(hasard.nextInt(choix.size()));
            pris.add(suivant);
            filon.add(suivant);
            blocs.add(new Bloc(suivant, minerai));
        }
        return filon.size();
    }

    /** Un départ dans le mur d'une galerie ou d'une salle de ce niveau, hors de la zone épuisée (et loin du puits). */
    private @Nullable BlockPos departDansUnMur(int niveau, boolean lointain) {
        List<PlanDeMine.Salle> salles = plan.salles().stream().filter(salle -> salle.niveau() == niveau).toList();
        List<BoundingBox> galeries = new ArrayList<>();
        for (int i = 0; i < plan.galeries().size(); i++) {
            if (plan.niveauxGaleries().get(i) == niveau) {
                galeries.add(plan.galeries().get(i));
            }
        }
        int poids = galeries.size() + POIDS_SALLE * salles.size();
        if (poids == 0) {
            return null;
        }
        int tirage = hasard.nextInt(poids);
        BlockPos dedans;
        if (tirage < galeries.size()) {
            dedans = interieurDe(galeries.get(tirage), null);
        } else {
            PlanDeMine.Salle salle = salles.get((tirage - galeries.size()) / POIDS_SALLE);
            dedans = interieurDe(salle.boite(), salle);
        }
        BlockPos depart = mur(dedans, horizontale());
        if (depart == null || !permis(depart) || epuise(depart) || !dansLaTranche(depart, niveau)) {
            return null;
        }
        BlockPos puits = plan.centre();
        if (lointain && Math.hypot(depart.getX() - puits.getX(), depart.getZ() - puits.getZ()) < LOINTAIN) {
            return null;
        }
        return depart;
    }

    /** Un bloc du vide tiré dans cette boîte (dans la salle, si c'en est une), ou {@code null}. */
    private @Nullable BlockPos interieurDe(BoundingBox boite, PlanDeMine.@Nullable Salle salle) {
        for (int essai = 0; essai < 10; essai++) {
            BlockPos pos = new BlockPos(boite.minX() + hasard.nextInt(boite.getXSpan()), boite.minY() + hasard.nextInt(boite.getYSpan()),
                    boite.minZ() + hasard.nextInt(boite.getZSpan()));
            if (salle == null || salle.contient(pos.getX(), pos.getY(), pos.getZ())) {
                return pos;
            }
        }
        return null;
    }

    /** Depuis ce bloc du vide, le premier bloc de roche dans cette direction (à 24 blocs au plus), ou {@code null}. */
    private @Nullable BlockPos mur(@Nullable BlockPos dedans, Direction direction) {
        if (dedans == null) {
            return null;
        }
        BlockPos.MutableBlockPos pos = dedans.mutable();
        for (int pas = 0; pas < 24; pas++) {
            pos.move(direction);
            if (!vide(pos.getX(), pos.getY(), pos.getZ())) {
                return pos.immutable();
            }
        }
        return null;
    }

    private Direction horizontale() {
        return Direction.from2DDataValue(hasard.nextInt(4));
    }

    // --- Règles ---

    /**
     * Un bloc de roche qui peut porter un filon : pas dans le vide, dans l'emprise et sous le haut de la mine, ni dans les
     * murs d'une cellule (et un bloc autour), ni dans le bouchon.
     */
    private boolean permis(BlockPos pos) {
        BlockPos centre = plan.centre();
        if (Math.abs(pos.getX() - centre.getX()) > 120 || Math.abs(pos.getZ() - centre.getZ()) > 120 || pos.getY() < -62
                || pos.getY() > plan.hautDesBoites() || plan.bouchon().isInside(pos) || vide(pos.getX(), pos.getY(), pos.getZ())) {
            return false;
        }
        for (PlanDeMine.EmplacementCellule cellule : plan.cellules()) {
            if (cellule.avecMurs().inflatedBy(1).isInside(pos)) {
                return false;
            }
        }
        return true;
    }

    /**
     * La tranche d'un niveau : de 8 blocs sous son sol à 9 au-dessus (la moitié de l'écart entre deux niveaux). Un filon y
     * reste : chaque minerai garde ses niveaux.
     */
    public static boolean dansLaTranche(BlockPos pos, int niveau, List<Integer> sols) {
        int sol = sols.get(niveau);
        return pos.getY() >= sol - 8 && pos.getY() <= sol + 9;
    }

    private boolean dansLaTranche(BlockPos pos, int niveau) {
        return dansLaTranche(pos, niveau, plan.sols());
    }

    /** Ce bloc touche-t-il le vide de la mine (un de ses six voisins) ? */
    private boolean touche(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos voisin = pos.relative(direction);
            if (vide(voisin.getX(), voisin.getY(), voisin.getZ())) {
                return true;
            }
        }
        return false;
    }

    /** Dans la zone épuisée : près de la caverne de la colonie, ou du chemin du puits principal à la caverne. */
    private boolean epuise(BlockPos pos) {
        BoundingBox caverne = plan.caverne().boite();
        double versCaverne = Math.hypot(Math.max(0, Math.max(caverne.minX() - pos.getX(), pos.getX() - caverne.maxX())),
                Math.max(0, Math.max(caverne.minZ() - pos.getZ(), pos.getZ() - caverne.maxZ())));
        if (versCaverne < EPUISEMENT) {
            return true;
        }
        // Distance au segment puits → centre de la caverne
        double ax = plan.centre().getX();
        double az = plan.centre().getZ();
        double bx = plan.caverne().x() - ax;
        double bz = plan.caverne().z() - az;
        double t = Math.clamp(((pos.getX() - ax) * bx + (pos.getZ() - az) * bz) / (bx * bx + bz * bz), 0, 1);
        return Math.hypot(pos.getX() - (ax + t * bx), pos.getZ() - (az + t * bz)) < EPUISEMENT;
    }

    private boolean vide(int x, int y, int z) {
        long cle = ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xFFFFFFFFL);
        List<BoundingBox> boites = videsParChunk.computeIfAbsent(cle, c -> {
            int cx = x >> 4 << 4;
            int cz = z >> 4 << 4;
            return plan.boitesVers(new BoundingBox(cx, -64, cz, cx + 15, 320, cz + 15));
        });
        return plan.interieurParmi(boites, x, y, z);
    }
}
