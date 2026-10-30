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
    void reseauEclaire() {
        for (long graine : GRAINES) {
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66, 66);
            List<PlanDeMine.Lanterne> lanternes = plan.lanternes();
            for (PlanDeMine.Lanterne lanterne : lanternes) {
                BlockPos pos = lanterne.pos();
                String nom = "graine " + graine + " : lanterne en " + pos;
                assertTrue(plan.interieur(pos.getX(), pos.getY(), pos.getZ()), nom + " hors du vide");
                // Tenue : suspendue sous une poutre ou la roche, posée sur la roche
                BlockPos appui = lanterne.suspendue() ? pos.above() : pos.below();
                assertTrue(plan.poutres().contains(appui) || !plan.interieur(appui.getX(), appui.getY(), appui.getZ()), nom + " sans appui");
                for (PlanDeMine.Puits p : plan.puits()) {
                    assertFalse(p.principal() && p.interieur().isInside(pos), nom + " au-dessus du puits principal");
                }
            }
            // Chaque bloc du sol du réseau à 14 blocs ou moins d'une lanterne (en suivant les axes) : lumière d'au moins 1
            List<BlockPos> sol = plan.reseauEclaire();
            assertTrue(sol.size() > 5000, "graine " + graine + " : " + sol.size() + " blocs de sol éclairé");
            for (BlockPos pos : sol) {
                int plusProche = Integer.MAX_VALUE;
                for (PlanDeMine.Lanterne lanterne : lanternes) {
                    plusProche = Math.min(plusProche, pos.distManhattan(lanterne.pos()));
                }
                assertTrue(plusProche <= 14, "graine " + graine + " : " + pos + " à " + plusProche + " blocs de la lanterne la plus proche");
            }
            System.out.println("Réseau éclairé, graine " + graine + " : " + lanternes.size() + " lanternes, " + sol.size() + " blocs de sol");
        }
    }

    @Test
    void voiesEtGarages() {
        for (long graine : GRAINES) {
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66, 66);
            assertTrue(plan.voies().size() >= 12, "graine " + graine + " : " + plan.voies().size() + " voies");
            assertEquals(PlanDeMine.WAGONNETS, plan.garages().size(), "graine " + graine + " : voies de garage");
            Set<BlockPos> filons = new HashSet<>();
            plan.filons().forEach(bloc -> filons.add(bloc.pos()));
            for (PlanDeMine.Voie voie : plan.voies()) {
                String nom = "graine " + graine + " : voie " + voie.sens() + " du niveau " + (voie.niveau() + 1);
                // Le butoir côté puits juste après le quai, puis les rails d'affilée jusqu'au butoir du bout
                assertEquals(2 + 3, voie.butoirPuits().distManhattan(CENTRE.atY(voie.butoirPuits().getY())), nom);
                BlockPos attendu = voie.butoirPuits();
                for (PlanDeMine.Rail rail : voie.rails()) {
                    attendu = attendu.relative(voie.sens());
                    assertEquals(attendu, rail.pos(), nom + " : rails pas d'affilée");
                    BlockPos dessous = rail.pos().below();
                    assertTrue(plan.interieur(rail.pos().getX(), rail.pos().getY(), rail.pos().getZ()), nom + " : rail hors du vide en " + rail.pos());
                    assertFalse(plan.interieur(dessous.getX(), dessous.getY(), dessous.getZ()), nom + " : rail sur le vide en " + rail.pos());
                }
                assertEquals(attendu.relative(voie.sens()), voie.butoirBout(), nom + " : butoir du bout");
            }
            for (PlanDeMine.Garage garage : plan.garages()) {
                BoundingBox niche = garage.niche();
                String nom = "graine " + graine + " : garage " + niche;
                for (PlanDeMine.EmplacementCellule cellule : plan.cellules()) {
                    assertFalse(cellule.avecMurs().inflatedBy(1).intersects(niche), nom + " près d'une cellule");
                }
                for (BlockPos pos : BlockPos.betweenClosed(niche.minX(), niche.minY(), niche.minZ(), niche.maxX(), niche.maxY(), niche.maxZ())) {
                    assertFalse(filons.contains(pos), nom + " sur un filon");
                }
                // Le wagonnet garé hors du passage : à 4 blocs de la ligne du milieu de l'axe
                BlockPos wagonnet = garage.wagonnet();
                int ecart = Math.min(Math.abs(wagonnet.getX() - CENTRE.getX()), Math.abs(wagonnet.getZ() - CENTRE.getZ()));
                assertEquals(4, ecart, nom + " : wagonnet à " + ecart + " blocs de la ligne du milieu");
            }
        }
    }

    @Test
    void reperesEtEntreesDesGris() {
        for (long graine : GRAINES) {
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66, 66);
            long niveaux = plan.panneaux().stream().filter(p -> p.texte() == PlanDeMine.Texte.NIVEAU).count();
            long colonie = plan.panneaux().stream().filter(p -> p.texte() == PlanDeMine.Texte.COLONIE_DROITE || p.texte() == PlanDeMine.Texte.COLONIE_GAUCHE).count();
            long puits = plan.panneaux().stream().filter(p -> p.texte() == PlanDeMine.Texte.PUITS_VERS).count();
            assertEquals(plan.sols().size(), niveaux, "graine " + graine + " : panneaux de niveau");
            assertEquals(1, colonie, "graine " + graine + " : panneau de la colonie");
            assertEquals(2L * (plan.puits().size() - 1), puits, "graine " + graine + " : panneaux de puits secondaires (un à chaque bout)");
            Set<BlockPos> places = new HashSet<>();
            for (PlanDeMine.Panneau panneau : plan.panneaux()) {
                BlockPos pos = panneau.pos();
                BlockPos appui = pos.relative(panneau.face().getOpposite());
                assertTrue(places.add(pos), "graine " + graine + " : deux panneaux en " + pos);
                assertTrue(plan.interieur(pos.getX(), pos.getY(), pos.getZ()) && !plan.interieur(appui.getX(), appui.getY(), appui.getZ()),
                        "graine " + graine + " : panneau sans mur en " + pos);
            }
            for (BlockPos mur : plan.bandes().keySet()) {
                assertFalse(plan.interieur(mur.getX(), mur.getY(), mur.getZ()), "graine " + graine + " : bande dans le vide en " + mur);
            }
            Set<BlockPos> reseau = new HashSet<>(plan.reseauEclaire());
            int[] parNiveau = new int[plan.sols().size()];
            for (PlanDeMine.Entree entree : plan.entreesDesGris()) {
                assertTrue(reseau.contains(entree.pos()), "graine " + graine + " : point d'entrée hors du réseau éclairé en " + entree.pos());
                parNiveau[entree.niveau()]++;
            }
            for (int n : parNiveau) {
                assertTrue(n >= 4, "graine " + graine + " : points d'entrée par niveau " + java.util.Arrays.toString(parNiveau));
            }
        }
    }

    @Test
    void niveauxParTranchesEtEntreesDeLaTraque() {
        for (long graine : GRAINES) {
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66, 66);
            List<Integer> sols = plan.sols();
            for (int niveau = 0; niveau < sols.size(); niveau++) {
                int sol = sols.get(niveau);
                assertEquals(niveau, plan.niveauA(CENTRE.atY(sol)), "graine " + graine);
                assertEquals(niveau, plan.niveauA(CENTRE.atY(sol - PlanDeMine.DEMI_TRANCHE)), "graine " + graine);
                assertEquals(niveau - 1, plan.niveauA(CENTRE.atY(sol + PlanDeMine.DEMI_TRANCHE)), "graine " + graine);
            }
            assertEquals(-1, plan.niveauA(CENTRE.atY(sols.getLast() - PlanDeMine.DEMI_TRANCHE - 1)), "graine " + graine);
            assertEquals(-1, plan.niveauA(CENTRE.atY(sols.getFirst()).east((PlanMars.EMPRISE_MINE + 1) * 16)), "graine " + graine);
            // Sur l'échelle du puits principal, de bas en haut : le niveau ne change qu'une fois entre deux sols
            PlanDeMine.Puits principal = plan.puitsPrincipal();
            int changements = 0;
            int avant = plan.niveauA(PlanDeMine.echelle(principal, sols.getLast()));
            for (int y = sols.getLast(); y <= sols.getFirst(); y++) {
                int niveau = plan.niveauA(PlanDeMine.echelle(principal, y));
                assertTrue(niveau == avant || niveau == avant - 1, "graine " + graine + " : saut de niveau à y = " + y);
                changements += niveau != avant ? 1 : 0;
                avant = niveau;
            }
            assertEquals(sols.size() - 1, changements, "graine " + graine);

            // Points d'entrée de la traque : à 24-64 blocs, le niveau du joueur d'abord, du plus proche au plus loin
            int bas = sols.size() - 1;
            BlockPos quai = CENTRE.atY(sols.get(bas)).east(30);
            List<PlanDeMine.Entree> choix = plan.entreesPour(quai, bas, 24, 64);
            assertFalse(choix.isEmpty(), "graine " + graine);
            boolean voisins = false;
            double precedente = 0;
            for (PlanDeMine.Entree entree : choix) {
                double distance = Math.sqrt(entree.pos().distSqr(quai));
                assertTrue(distance >= 24 && distance <= 64, "graine " + graine + " : " + distance);
                assertTrue(Math.abs(entree.niveau() - bas) <= 1, "graine " + graine);
                if (entree.niveau() != bas) {
                    voisins = true;
                    precedente = 0;
                }
                assertFalse(voisins && entree.niveau() == bas, "graine " + graine + " : niveau du joueur après un voisin");
                assertTrue(distance >= precedente, "graine " + graine + " : pas dans l'ordre");
                precedente = distance;
            }
            // Repli : les points du niveau du joueur et des voisins, le sien d'abord
            List<PlanDeMine.Entree> repli = plan.entreesDeRepli(quai, bas);
            assertEquals(plan.entreesDesGris().stream().filter(e -> e.niveau() >= bas - 1).count(), repli.size(), "graine " + graine);
            for (int i = 1; i < repli.size(); i++) {
                int avantEcart = Math.abs(repli.get(i - 1).niveau() - bas);
                int ecart = Math.abs(repli.get(i).niveau() - bas);
                assertTrue(ecart > avantEcart || ecart == avantEcart
                        && repli.get(i).pos().distSqr(quai) >= repli.get(i - 1).pos().distSqr(quai), "graine " + graine + " : repli pas dans l'ordre");
            }
            // Des galeries sombres loin du puits, et, sur un niveau au moins, une sans point d'entrée à portée (le repli de la traque)
            assertFalse(plan.galeriesSombres(bas, 60).isEmpty(), "graine " + graine);
            boolean isolee = false;
            for (int niveau = 0; niveau < sols.size(); niveau++) {
                int n = niveau;
                isolee |= plan.galeriesSombres(n, 0).stream().anyMatch(place -> plan.entreesPour(place, n, 24, 64).isEmpty());
            }
            assertTrue(isolee, "graine " + graine + " : aucune galerie sombre isolée");
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
        assertEquals(a.voies(), b.voies());
        assertEquals(a.garages(), b.garages());
        assertEquals(a.panneaux(), b.panneaux());
        assertEquals(a.entreesDesGris(), b.entreesDesGris());
        assertSame(PlanDeMine.de(5, CENTRE, (x, z) -> 62, 66, 66), PlanDeMine.de(5, CENTRE, (x, z) -> 62, 66, 66));
    }
}
