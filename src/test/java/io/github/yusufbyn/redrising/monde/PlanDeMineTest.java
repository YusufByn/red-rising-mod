package io.github.yusufbyn.redrising.monde;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan de la mine (G7.2.1) : tout relié au puits principal (parcours en largeur sur les boîtes de vide), dans l'emprise et
 * entre y −50 et 20, 12 emplacements de cellules en roche pleine, au moins 3 culs-de-sac sans cellule pour 1 avec,
 * bouchon sous la surface de la colonne du puits, même graine même plan. Salles d'extraction (G7.2.2) : nombre et formes
 * par niveau, chacune entière et reliée par ses ouvertures, sur aucune ligne de la grille hors des galeries (jamais au bout
 * d'un cul-de-sac), loin des cellules et des puits ; longueur des galeries.
 */
class PlanDeMineTest {
    private static final BlockPos CENTRE = new BlockPos(-740, 67, -317);
    private static final long[] GRAINES = {20_261_028L, 7L, -4_242_424_242L, 123L, 99_999L, 1L, 2L, 3L};

    @Test
    void toutEstReliéAuPuitsPrincipal() {
        for (long graine : GRAINES) {
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66, 66);
            List<BoundingBox> boites = new ArrayList<>(plan.galeries());
            plan.puits().forEach(p -> boites.add(p.interieur()));
            boites.add(plan.caverne().boite());
            for (PlanDeMine.Salle salle : plan.salles()) {
                boites.add(salle.boite());
                boites.addAll(salle.ouvertures());
            }
            // Parcours en largeur : deux boîtes se touchent si elles se croisent
            Set<Integer> vus = new HashSet<>(Set.of(plan.galeries().size()));
            Deque<Integer> file = new ArrayDeque<>(List.of(plan.galeries().size()));
            while (!file.isEmpty()) {
                BoundingBox courante = boites.get(file.poll());
                for (int i = 0; i < boites.size(); i++) {
                    if (!vus.contains(i) && boites.get(i).intersects(courante)) {
                        vus.add(i);
                        file.add(i);
                    }
                }
            }
            assertEquals(boites.size(), vus.size(), "graine " + graine + " : " + (boites.size() - vus.size()) + " élément(s) isolé(s)");
            // Chaque cellule s'ouvre sur une galerie
            for (PlanDeMine.EmplacementCellule cellule : plan.cellules()) {
                BlockPos acces = cellule.galerie();
                assertTrue(plan.interieur(acces.getX(), acces.getY(), acces.getZ()), "graine " + graine + " : accès de cellule hors galerie " + acces);
            }
        }
    }

    @Test
    void dansLEmpriseEtSousLaSurface() {
        for (long graine : GRAINES) {
            // La colonne du puits sur une butte : 28 blocs au-dessus du terrain le plus bas autour (graine 7 en jeu)
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66, 94);
            assertEquals(PlanDeMine.NIVEAUX_MAX, plan.sols().size());
            for (BoundingBox galerie : plan.galeries()) {
                assertTrue(Math.abs(galerie.minX() - CENTRE.getX()) <= 120 && Math.abs(galerie.maxX() - CENTRE.getX()) <= 120
                        && Math.abs(galerie.minZ() - CENTRE.getZ()) <= 120 && Math.abs(galerie.maxZ() - CENTRE.getZ()) <= 120, galerie.toString());
                assertTrue(galerie.minY() >= -50 && galerie.maxY() <= 20, galerie.toString());
            }
            BoundingBox caverne = plan.caverne().boite();
            assertTrue(Math.abs(caverne.minX() - CENTRE.getX()) <= 120 && Math.abs(caverne.maxX() - CENTRE.getX()) <= 120, caverne.toString());
            // Le bouchon : 2 couches, sous 3 blocs du terrain d'origine de la colonne du puits (dernier bloc en 93)
            assertEquals(2, plan.bouchon().getYSpan());
            assertEquals(93 - PlanDeMine.ROCHE_SUR_BOUCHON, plan.bouchon().maxY());
            assertEquals(plan.bouchon().minY() - 1, plan.puitsPrincipal().haut());
            // La boîte du puits reste à 14 blocs sous le terrain le plus bas autour (pas de butte)
            assertTrue(plan.hautBoitePuits() <= 66 - PlanDeMine.BOITE_PUITS_SOUS_SURFACE);
        }
    }

    @Test
    void unNiveauDeMoinsSousUneVallee() {
        // Terrain le plus bas à y = 25 : le niveau du haut (sol 8) serait trop près de la surface
        PlanDeMine plan = PlanDeMine.tirer(1, CENTRE, 25, 66, 66);
        assertFalse(plan.sols().contains(8));
        assertTrue(plan.hautDesBoites() + 12 < 25);
    }

    @Test
    void puitsDansUnCreux() {
        // Colonne du puits plus basse que le haut de la mine : le puits monte au moins jusqu'au niveau du haut
        PlanDeMine plan = PlanDeMine.tirer(1, CENTRE, 62, 10, 10);
        assertEquals(plan.sols().getFirst() + 4, plan.puitsPrincipal().haut());
        assertEquals(plan.puitsPrincipal().haut() + 1, plan.bouchon().minY());
    }

    @Test
    void douzeCellulesEnRochePleineEtCulsDeSacMuets() {
        for (long graine : GRAINES) {
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66, 66);
            assertEquals(PlanDeMine.CELLULES, plan.cellules().size(), "graine " + graine);
            Set<BlockPos> boutsAvecCellule = new HashSet<>();
            boolean paroi = false;
            for (PlanDeMine.EmplacementCellule cellule : plan.cellules()) {
                // La salle et ses murs restent en roche : aucun vide de la mine à moins de 2 blocs
                BoundingBox garde = cellule.avecMurs().inflatedBy(1);
                for (BlockPos pos : BlockPos.betweenClosed(garde.minX(), garde.minY(), garde.minZ(), garde.maxX(), garde.maxY(), garde.maxZ())) {
                    assertFalse(plan.interieur(pos.getX(), pos.getY(), pos.getZ()), "graine " + graine + " : vide près d'une cellule en " + pos);
                }
                if (cellule.type() == PlanDeMine.TypeCellule.BOUT) {
                    boutsAvecCellule.add(cellule.galerie());
                } else {
                    paroi = true;
                }
            }
            int sansCellule = plan.culsDeSac().size() - boutsAvecCellule.size();
            assertTrue(sansCellule >= PlanDeMine.CULS_DE_SAC_PAR_CELLULE * boutsAvecCellule.size(),
                    "graine " + graine + " : " + sansCellule + " culs-de-sac sans cellule pour " + boutsAvecCellule.size() + " avec");
            assertTrue(paroi, "graine " + graine + " : aucune cellule sur une paroi");
        }
    }

    @Test
    void sallesDExtraction() {
        for (long graine : GRAINES) {
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66, 66);
            List<PlanDeMine.Salle> salles = plan.salles();
            assertTrue(salles.size() >= 30, "graine " + graine + " : " + salles.size() + " salles");
            for (int niveau = 0; niveau < plan.sols().size(); niveau++) {
                int n = niveau;
                long parNiveau = salles.stream().filter(salle -> salle.niveau() == n).count();
                assertTrue(parNiveau >= 6, "graine " + graine + " : " + parNiveau + " salles au niveau " + (niveau + 1));
            }
            for (PlanDeMine.Salle salle : salles) {
                String nom = "graine " + graine + " : " + salle.type() + " en " + salle.x() + " " + salle.z();
                // Forages aux niveaux 3 et 4 seulement
                assertTrue(salle.type() != PlanDeMine.TypeSalle.FORAGE || salle.niveau() >= 2, nom);
                // Toute la salle se parcourt depuis la galerie, par ses ouvertures
                BlockPos depart = null;
                List<BoundingBox> vides = new ArrayList<>(salle.ouvertures());
                vides.add(salle.boite());
                for (BoundingBox ouverture : salle.ouvertures()) {
                    for (BlockPos pos : BlockPos.betweenClosed(ouverture.minX(), ouverture.minY(), ouverture.minZ(), ouverture.maxX(), ouverture.maxY(), ouverture.maxZ())) {
                        if (plan.galeries().stream().anyMatch(galerie -> galerie.isInside(pos))) {
                            depart = pos.immutable();
                        }
                    }
                }
                assertTrue(depart != null, nom + " : ouverture qui ne touche pas de galerie");
                Set<BlockPos> vus = new HashSet<>(Set.of(depart));
                Deque<BlockPos> file = new ArrayDeque<>(List.of(depart));
                while (!file.isEmpty()) {
                    BlockPos pos = file.poll();
                    for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                        BlockPos suivant = pos.relative(direction);
                        boolean dedans = vides.stream().anyMatch(vide -> vide.isInside(suivant));
                        if (dedans && plan.interieur(suivant.getX(), suivant.getY(), suivant.getZ()) && vus.add(suivant)) {
                            file.add(suivant);
                        }
                    }
                }
                BoundingBox b = salle.boite();
                for (BlockPos pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                    if (salle.contient(pos.getX(), pos.getY(), pos.getZ())) {
                        assertTrue(vus.contains(pos), nom + " : " + pos + " pas atteint");
                    }
                }
                // Sur une ligne de la grille (la place d'une galerie), seulement dans une galerie : jamais au bout d'un cul-de-sac
                for (BoundingBox vide : vides) {
                    for (BlockPos pos : BlockPos.betweenClosed(vide.minX(), vide.minY(), vide.minZ(), vide.maxX(), vide.maxY(), vide.maxZ())) {
                        int lx = Math.floorMod(pos.getX() - CENTRE.getX() + 1, PlanDeMine.ESPACEMENT);
                        int lz = Math.floorMod(pos.getZ() - CENTRE.getZ() + 1, PlanDeMine.ESPACEMENT);
                        if (lx <= 2 || lz <= 2) {
                            assertTrue(plan.galeries().stream().anyMatch(galerie -> galerie.isInside(pos)), nom + " : sur une ligne hors galerie en " + pos);
                        }
                    }
                    for (PlanDeMine.Puits p : plan.puits()) {
                        assertFalse(p.interieur().inflatedBy(PlanDeMine.COQUE_PUITS).intersects(vide), nom + " : dans la coque d'un puits");
                    }
                }
                for (PlanDeMine.EmplacementCellule cellule : plan.cellules()) {
                    BlockPos c = cellule.salle().getCenter();
                    assertTrue(Math.hypot(c.getX() - salle.x(), c.getZ() - salle.z()) >= PlanDeMine.ECART_SALLE_CELLULE, nom + " : près d'une cellule");
                }
            }
            // Longueur des galeries
            long longueur = plan.galeries().stream().mapToLong(galerie -> Math.max(galerie.getXSpan(), galerie.getZSpan())).sum();
            assertTrue(longueur >= 6000, "graine " + graine + " : " + longueur + " blocs de galeries");
        }
    }

    @Test
    void filons() {
        for (long graine : GRAINES) {
            long debut = System.nanoTime();
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66, 66);
            double ms = (System.nanoTime() - debut) / 1e6;
            java.util.Map<FilonsDeMine.Minerai, Integer> total = new java.util.EnumMap<>(FilonsDeMine.Minerai.class);
            java.util.Map<FilonsDeMine.Minerai, Integer> visibles = new java.util.EnumMap<>(FilonsDeMine.Minerai.class);
            Set<BlockPos> vus = new HashSet<>();
            for (FilonsDeMine.Bloc bloc : plan.filons()) {
                BlockPos pos = bloc.pos();
                String nom = "graine " + graine + " : " + bloc.minerai() + " en " + pos;
                assertTrue(vus.add(pos), nom + " en double");
                assertFalse(plan.interieur(pos.getX(), pos.getY(), pos.getZ()), nom + " dans le vide");
                for (PlanDeMine.EmplacementCellule cellule : plan.cellules()) {
                    assertFalse(cellule.avecMurs().inflatedBy(1).isInside(pos), nom + " dans les murs d'une cellule");
                }
                // Le niveau du bloc (sa tranche) : chaque minerai à ses niveaux
                int niveau = -1;
                for (int i = 0; i < plan.sols().size(); i++) {
                    if (FilonsDeMine.dansLaTranche(pos, i, plan.sols())) {
                        niveau = i;
                    }
                }
                assertTrue(niveau >= 0, nom + " hors des tranches");
                switch (bloc.minerai()) {
                    case CUIVRE -> assertTrue(niveau <= 1, nom);
                    case OR, HELIUM3 -> assertTrue(niveau >= 2, nom);
                    case REDSTONE -> assertTrue(niveau == 3, nom);
                    case FER -> assertTrue(niveau >= 1, nom);
                    default -> {
                    }
                }
                total.merge(bloc.minerai(), 1, Integer::sum);
                boolean touche = false;
                for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                    BlockPos voisin = pos.relative(direction);
                    touche |= plan.interieur(voisin.getX(), voisin.getY(), voisin.getZ());
                }
                if (touche) {
                    visibles.merge(bloc.minerai(), 1, Integer::sum);
                }
            }
            System.out.println("Filons, graine " + graine + " (" + Math.round(ms) + " ms) : " + total + " dont visibles " + visibles);
            assertTrue(total.getOrDefault(FilonsDeMine.Minerai.HELIUM3, 0) >= 400, "graine " + graine + " : hélium-3 " + total);
            assertTrue(visibles.getOrDefault(FilonsDeMine.Minerai.HELIUM3, 0) >= 60, "graine " + graine + " : hélium-3 visible " + visibles);
            assertTrue(total.getOrDefault(FilonsDeMine.Minerai.HELIUM3, 0) - visibles.getOrDefault(FilonsDeMine.Minerai.HELIUM3, 0)
                    >= FilonsDeMine.HELIUM3_CACHE * 8 / 10, "graine " + graine + " : hélium-3 caché " + total + " " + visibles);
        }
    }

    @Test
    void coffresDOutils() {
        for (long graine : GRAINES) {
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66, 66);
            List<PlanDeMine.Coffre> coffres = plan.coffres();
            assertTrue(coffres.size() >= 12 && coffres.size() <= 20, "graine " + graine + " : " + coffres.size() + " coffres");
            int rares = 0;
            Set<PlanDeMine.Salle> salles = new HashSet<>();
            for (PlanDeMine.Coffre coffre : coffres) {
                BlockPos pos = coffre.pos();
                String nom = "graine " + graine + " : coffre en " + pos;
                PlanDeMine.Salle salle = plan.salles().stream().filter(s -> s.contient(pos.getX(), pos.getY(), pos.getZ())).findFirst().orElse(null);
                assertTrue(salle != null, nom + " hors d'une salle");
                assertTrue(salles.add(salle), nom + " : deux coffres dans la même salle");
                assertEquals(salle.boite().minY(), pos.getY(), nom + " pas au sol");
                BlockPos mur = pos.relative(coffre.face().getOpposite());
                assertTrue(plan.interieur(pos.getX(), pos.getY(), pos.getZ()) && !plan.interieur(mur.getX(), mur.getY(), mur.getZ()), nom + " pas contre un mur");
                assertFalse(plan.poteaux().contains(pos), nom + " sur un étai");
                for (BoundingBox ouverture : salle.ouvertures()) {
                    assertFalse(ouverture.inflatedBy(1).isInside(pos), nom + " devant une ouverture");
                }
                if (coffre.rare()) {
                    rares++;
                    assertEquals(plan.sols().size() - 1, salle.niveau(), nom + " : coffre rare hors du niveau du bas");
                }
            }
            assertTrue(rares >= 1 && rares <= PlanDeMine.COFFRES_RARES, "graine " + graine + " : " + rares + " coffres rares");
            // Les rares : à plus de 80 blocs de la colonie s'il y a de telles salles au niveau du bas, sinon la plus éloignée
            BoundingBox caverne = plan.caverne().boite();
            java.util.function.ToDoubleFunction<PlanDeMine.Salle> versColonie = salle -> Math.hypot(
                    Math.max(0, Math.max(caverne.minX() - salle.x(), salle.x() - caverne.maxX())),
                    Math.max(0, Math.max(caverne.minZ() - salle.z(), salle.z() - caverne.maxZ())));
            List<PlanDeMine.Salle> duBas = plan.salles().stream().filter(salle -> salle.niveau() == plan.sols().size() - 1).toList();
            boolean lointaines = duBas.stream().anyMatch(salle -> versColonie.applyAsDouble(salle) > PlanDeMine.DISTANCE_COFFRE_RARE);
            double plusLoin = duBas.stream().mapToDouble(versColonie).max().orElseThrow();
            for (PlanDeMine.Coffre coffre : coffres) {
                if (coffre.rare()) {
                    PlanDeMine.Salle salle = plan.salles().stream().filter(s -> s.contient(coffre.pos().getX(), coffre.pos().getY(), coffre.pos().getZ())).findFirst().orElseThrow();
                    double distance = versColonie.applyAsDouble(salle);
                    assertTrue(lointaines ? distance > PlanDeMine.DISTANCE_COFFRE_RARE : distance == plusLoin,
                            "graine " + graine + " : coffre rare à " + Math.round(distance) + " blocs de la colonie");
                }
            }
            System.out.println("Coffres, graine " + graine + " : " + coffres.size() + " dont " + rares + " rares");
        }
    }

    @Test
    void colonieReservee() {
        PlanDeMine plan = PlanDeMine.tirer(42, CENTRE, 62, 66, 66);
        PlanDeMine.Caverne caverne = plan.caverne();
        PlanDeMine.Colonie colonie = plan.colonie();
        BlockPos echafaud = colonie.echafaud();
        BlockPos apparition = colonie.apparition();
        assertTrue(caverne.contient(echafaud.getX(), echafaud.getY() + 1, echafaud.getZ()));
        assertTrue(caverne.contient(apparition.getX(), apparition.getY() + 1, apparition.getZ()));
        assertTrue(colonie.parcelles().size() >= 15, colonie.parcelles().size() + " parcelles");
        assertEquals(PlanDeMine.NIVEAUX_MAX > 2 ? plan.sols().get(2) : plan.sols().getLast(), caverne.sol());
    }

    @Test
    void memeGraineMemePlan() {
        PlanDeMine a = PlanDeMine.tirer(5, CENTRE, 62, 66, 66);
        PlanDeMine b = PlanDeMine.tirer(5, CENTRE, 62, 66, 66);
        assertEquals(a.galeries(), b.galeries());
        assertEquals(a.cellules(), b.cellules());
        assertEquals(a.puits(), b.puits());
        assertEquals(a.salles(), b.salles());
        assertEquals(a.filons(), b.filons());
        assertEquals(a.coffres(), b.coffres());
        assertSame(PlanDeMine.de(5, CENTRE, 62, 66, 66), PlanDeMine.de(5, CENTRE, 62, 66, 66));
    }
}
