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
            plan.cavernes().forEach(caverne -> boites.add(caverne.boite()));
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
                assertTrue(dansLaMine(galerie), galerie.toString());
                assertTrue(galerie.minY() >= -50 && galerie.maxY() <= 28, galerie.toString());
            }
            for (PlanDeMine.Caverne caverne : plan.cavernes()) {
                assertTrue(dansLaMine(caverne.boite()), caverne.toString());
            }
            // Le bouchon : 2 couches, sous 3 blocs du terrain d'origine de la colonne du puits (dernier bloc en 93)
            assertEquals(2, plan.bouchon().getYSpan());
            assertEquals(93 - PlanDeMine.ROCHE_SUR_BOUCHON, plan.bouchon().maxY());
            assertEquals(plan.bouchon().minY() - 1, plan.puitsPrincipal().haut());
            // La boîte du puits reste à 14 blocs sous le terrain le plus bas autour (pas de butte)
            assertTrue(plan.hautBoitePuits() <= 66 - PlanDeMine.BOITE_PUITS_SOUS_SURFACE);
        }
    }

    private static boolean dansLaMine(BoundingBox boite) {
        return Math.abs(boite.minX() - CENTRE.getX()) <= PlanDeMine.DEMI_MINE && Math.abs(boite.maxX() - CENTRE.getX()) <= PlanDeMine.DEMI_MINE
                && Math.abs(boite.minZ() - CENTRE.getZ()) <= PlanDeMine.DEMI_MINE && Math.abs(boite.maxZ() - CENTRE.getZ()) <= PlanDeMine.DEMI_MINE;
    }

    @Test
    void unNiveauDeMoinsSousUneVallee() {
        // Terrain à y = 25 autour du puits : le niveau du haut (sol 6) serait trop près de la surface
        PlanDeMine plan = PlanDeMine.tirer(1, CENTRE, 25, 66, 66);
        assertFalse(plan.sols().contains(6));
        assertTrue(plan.hautDesBoites() + 12 < 25);
    }

    @Test
    void leVideResteSousLeTerrainLocal() {
        // Une vallée profonde (y = 10) à l'est de la mine, à 60 blocs du puits ; ailleurs, y = 80
        java.util.function.IntBinaryOperator terrain = (x, z) -> x > CENTRE.getX() + 60 ? 10 : 80;
        for (long graine : GRAINES) {
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, terrain, 80, 80);
            assertEquals(PlanDeMine.NIVEAUX_MAX, plan.sols().size(), "graine " + graine);
            // Aucun vide à moins de 18 blocs sous le terrain au-dessus de lui
            for (BoundingBox galerie : plan.galeries()) {
                int dessus = galerie.maxX() + 16 > CENTRE.getX() + 60 ? 10 : 80;
                assertTrue(galerie.maxY() + 18 <= dessus, "graine " + graine + " : " + galerie);
            }
            for (PlanDeMine.Caverne caverne : plan.cavernes()) {
                int dessus = caverne.boite().maxX() + 16 > CENTRE.getX() + 60 ? 10 : 80;
                assertTrue(caverne.sol() + caverne.hauteur() + 18 <= dessus, "graine " + graine + " : " + caverne);
            }
            // Le niveau du haut reste, à l'ouest ; la colonie aussi, et elle reste la plus grande
            assertTrue(plan.cavernes().stream().anyMatch(caverne -> caverne.sol() == 6), "graine " + graine);
            assertTrue(plan.caverne().x() <= CENTRE.getX(), "graine " + graine + " : colonie sous la vallée");
        }
    }

    @Test
    void puitsDansUnCreux() {
        // Colonne du puits plus basse que le haut de la mine : le puits monte au moins jusqu'au niveau du haut
        PlanDeMine plan = PlanDeMine.tirer(1, CENTRE, 62, 10, 10);
        assertEquals(plan.sols().getFirst() + 7, plan.puitsPrincipal().haut());
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
            assertTrue(salles.size() >= 16, "graine " + graine + " : " + salles.size() + " salles");
            for (int niveau = 0; niveau < plan.sols().size(); niveau++) {
                int n = niveau;
                long parNiveau = salles.stream().filter(salle -> salle.niveau() == n).count();
                assertTrue(parNiveau >= 3, "graine " + graine + " : " + parNiveau + " salles au niveau " + (niveau + 1));
            }
            for (PlanDeMine.Salle salle : salles) {
                String nom = "graine " + graine + " : " + salle.type() + " en " + salle.x() + " " + salle.z();
                // Forages aux niveaux 2 et 3 seulement
                assertTrue(salle.type() != PlanDeMine.TypeSalle.FORAGE || salle.niveau() >= 1, nom);
                // Toute la salle se parcourt depuis sa caverne, par son ouverture
                BlockPos depart = null;
                List<BoundingBox> vides = new ArrayList<>(salle.ouvertures());
                vides.add(salle.boite());
                for (BoundingBox ouverture : salle.ouvertures()) {
                    for (BlockPos pos : BlockPos.betweenClosed(ouverture.minX(), ouverture.minY(), ouverture.minZ(), ouverture.maxX(), ouverture.maxY(), ouverture.maxZ())) {
                        if (plan.cavernes().stream().anyMatch(caverne -> caverne.contient(pos.getX(), pos.getY(), pos.getZ()))) {
                            depart = pos.immutable();
                        }
                    }
                }
                assertTrue(depart != null, nom + " : ouverture qui ne touche pas de caverne");
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
                for (BoundingBox vide : vides) {
                    for (PlanDeMine.Puits p : plan.puits()) {
                        assertFalse(p.interieur().inflatedBy(PlanDeMine.COQUE_PUITS).intersects(vide), nom + " : dans la coque d'un puits");
                    }
                }
                for (PlanDeMine.EmplacementCellule cellule : plan.cellules()) {
                    BlockPos c = cellule.salle().getCenter();
                    assertTrue(Math.hypot(c.getX() - salle.x(), c.getZ() - salle.z()) >= PlanDeMine.ECART_SALLE_CELLULE, nom + " : près d'une cellule");
                }
            }
            // Longueur des tunnels et des axes, et les cavernes
            long longueur = plan.galeries().stream().mapToLong(galerie -> Math.max(galerie.getXSpan(), galerie.getZSpan())).sum();
            assertTrue(longueur >= 3000, "graine " + graine + " : " + longueur + " blocs de galeries");
            for (int niveau = 0; niveau < plan.sols().size(); niveau++) {
                int sol = plan.sols().get(niveau);
                long cavernes = plan.cavernes().stream().filter(caverne -> caverne.sol() == sol).count();
                assertTrue(cavernes >= 4, "graine " + graine + " : " + cavernes + " cavernes au niveau " + (niveau + 1));
            }
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
                    case CUIVRE -> assertTrue(niveau == 0, nom);
                    case OR -> assertTrue(niveau != 1, nom);
                    case HELIUM3 -> assertTrue(niveau >= 1, nom);
                    case REDSTONE -> assertTrue(niveau == 2, nom);
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
            assertTrue(total.getOrDefault(FilonsDeMine.Minerai.HELIUM3, 0) >= 900, "graine " + graine + " : hélium-3 " + total);
            assertTrue(visibles.getOrDefault(FilonsDeMine.Minerai.HELIUM3, 0) >= 250, "graine " + graine + " : hélium-3 visible " + visibles);
            assertTrue(total.getOrDefault(FilonsDeMine.Minerai.HELIUM3, 0) - visibles.getOrDefault(FilonsDeMine.Minerai.HELIUM3, 0)
                    >= FilonsDeMine.HELIUM3_CACHE * 8 / 10, "graine " + graine + " : hélium-3 caché " + total + " " + visibles);
            // Les diamants ajoutés : surtout au fond
            int[] diamants = new int[plan.sols().size()];
            for (FilonsDeMine.Bloc bloc : plan.filons()) {
                if (bloc.minerai() == FilonsDeMine.Minerai.DIAMANT) {
                    for (int i = 0; i < plan.sols().size(); i++) {
                        if (FilonsDeMine.dansLaTranche(bloc.pos(), i, plan.sols())) {
                            diamants[i]++;
                            break;
                        }
                    }
                }
            }
            assertTrue(diamants[plan.sols().size() - 1] > diamants[0] && diamants[0] > 0, "graine " + graine + " : diamants par niveau " + java.util.Arrays.toString(diamants));
        }
    }

    @Test
    void coffresDOutils() {
        for (long graine : GRAINES) {
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66, 66);
            List<PlanDeMine.Coffre> coffres = plan.coffres();
            assertTrue(coffres.size() >= 36 && coffres.size() <= 48, "graine " + graine + " : " + coffres.size() + " coffres");
            BoundingBox zone = plan.caverne().boite();
            int rares = 0;
            int[] raresParNiveau = new int[plan.sols().size()];
            for (PlanDeMine.Coffre coffre : coffres) {
                BlockPos pos = coffre.pos();
                String nom = "graine " + graine + " : coffre en " + pos;
                assertTrue(plan.sols().contains(pos.getY()), nom + " pas au sol");
                assertFalse(plan.interieur(pos.getX(), pos.getY() - 1, pos.getZ()), nom + " sur le vide");
                BlockPos mur = pos.relative(coffre.face().getOpposite());
                assertTrue(plan.interieur(pos.getX(), pos.getY(), pos.getZ()) && !plan.interieur(mur.getX(), mur.getY(), mur.getZ()), nom + " pas contre un mur");
                assertFalse(plan.poteaux().contains(pos), nom + " sur un étai");
                assertFalse(plan.colonie().reserve().isInside(pos), nom + " dans la colonie");
                assertTrue(plan.lanternes().contains(new PlanDeMine.Lanterne(pos.above(), false)), nom + " sans lanterne");
                for (PlanDeMine.Salle salle : plan.salles()) {
                    for (BoundingBox ouverture : salle.ouvertures()) {
                        assertFalse(ouverture.inflatedBy(1).isInside(pos), nom + " devant une ouverture");
                    }
                }
                if (coffre.rare()) {
                    rares++;
                    raresParNiveau[plan.sols().indexOf(pos.getY())]++;
                    double versColonie = Math.hypot(Math.max(0, Math.max(zone.minX() - pos.getX(), pos.getX() - zone.maxX())),
                            Math.max(0, Math.max(zone.minZ() - pos.getZ(), pos.getZ() - zone.maxZ())));
                    assertTrue(versColonie > PlanDeMine.DISTANCE_COFFRE_RARE, nom + " : coffre rare à " + Math.round(versColonie) + " blocs de la colonie");
                }
            }
            assertTrue(rares >= PlanDeMine.COFFRES_RARES_MIN && rares <= PlanDeMine.COFFRES_RARES, "graine " + graine + " : " + rares + " coffres rares");
            assertTrue(raresParNiveau[plan.sols().size() - 1] >= 2, "graine " + graine + " : rares par niveau " + java.util.Arrays.toString(raresParNiveau));
            System.out.println("Coffres, graine " + graine + " : " + coffres.size() + " dont " + rares + " rares " + java.util.Arrays.toString(raresParNiveau));
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
                assertTrue(plan.poutres().contains(appui) || plan.chaines().contains(appui) || !plan.interieur(appui.getX(), appui.getY(), appui.getZ())
                        || plan.coffres().stream().anyMatch(coffre -> coffre.pos().equals(appui)), nom + " sans appui");
                for (PlanDeMine.Puits p : plan.puits()) {
                    assertFalse(p.principal() && p.interieur().isInside(pos), nom + " au-dessus du puits principal");
                }
            }
            // Chaque bloc du sol du réseau à 9 blocs ou moins d'une lanterne : lumière d'au moins 6, on s'oriente sans torche
            List<BlockPos> sol = plan.reseauEclaire();
            assertTrue(sol.size() > 5000, "graine " + graine + " : " + sol.size() + " blocs de sol éclairé");
            for (BlockPos pos : sol) {
                int plusProche = Integer.MAX_VALUE;
                for (PlanDeMine.Lanterne lanterne : lanternes) {
                    plusProche = Math.min(plusProche, pos.distManhattan(lanterne.pos()));
                }
                assertTrue(plusProche <= 9, "graine " + graine + " : " + pos + " à " + plusProche + " blocs de la lanterne la plus proche");
            }
            System.out.println("Réseau éclairé, graine " + graine + " : " + lanternes.size() + " lanternes, " + sol.size() + " blocs de sol");
        }
    }

    @Test
    void voiesEtGarages() {
        for (long graine : GRAINES) {
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66, 66);
            assertTrue(plan.voies().size() >= 10, "graine " + graine + " : " + plan.voies().size() + " voies");
            assertTrue(plan.garages().size() >= 2 * plan.voies().size() - 2, "graine " + graine + " : " + plan.garages().size() + " voies de garage");
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
            Set<BlockPos> ouDesRails = new HashSet<>();
            plan.voies().forEach(voie -> voie.rails().forEach(rail -> ouDesRails.add(rail.pos())));
            for (PlanDeMine.Garage garage : plan.garages()) {
                String nom = "graine " + graine + " : garage du wagonnet en " + garage.wagonnet();
                // La courbe part d'un bout de voie : le premier rail du garage touche un rail de la voie
                BlockPos premier = garage.rails().getFirst().pos();
                assertTrue(java.util.Arrays.stream(net.minecraft.core.Direction.values()).anyMatch(d -> ouDesRails.contains(premier.relative(d))), nom + " pas relié à la voie");
                for (PlanDeMine.Rail rail : garage.rails()) {
                    BlockPos pos = rail.pos();
                    assertTrue(plan.interieur(pos.getX(), pos.getY(), pos.getZ()) && !plan.interieur(pos.getX(), pos.getY() - 1, pos.getZ()), nom + " : rail mal posé en " + pos);
                }
                // Rails d'affilée, du premier au butoir
                BlockPos avant = premier;
                for (PlanDeMine.Rail rail : garage.rails().subList(1, garage.rails().size())) {
                    assertEquals(1, avant.distManhattan(rail.pos()), nom + " : rails pas d'affilée");
                    avant = rail.pos();
                }
                assertEquals(1, avant.distManhattan(garage.butoir()), nom + " : butoir");
                assertFalse(plan.puits().stream().anyMatch(p -> p.interieur().isInside(garage.wagonnet())), nom);
            }
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
        // Sur le niveau 2 ; la plus grande caverne, au sol et en hauteur
        assertEquals(plan.sols().get(1), caverne.sol());
        assertEquals(PlanDeMine.HAUTEUR_COLONIE, caverne.hauteur());
        for (PlanDeMine.Caverne autre : plan.cavernes().subList(1, plan.cavernes().size())) {
            assertTrue(autre.demiX() * autre.demiZ() < caverne.demiX() * caverne.demiZ() && autre.hauteur() < caverne.hauteur(), autre.toString());
        }
        // De la roche pleine derrière ses parois : sur son niveau, seul l'axe qui y mène entre dans la réserve
        for (long graine : GRAINES) {
            PlanDeMine p = PlanDeMine.tirer(graine, CENTRE, 62, 66, 66);
            BoundingBox reserve = p.colonie().reserve();
            int sol = p.caverne().sol();
            long entrant = p.galeries().stream().filter(g -> g.minY() == sol && g.intersects(reserve)).count();
            assertEquals(1, entrant, "graine " + graine + " : galeries dans la réserve");
            assertTrue(p.salles().stream().noneMatch(salle -> salle.boite().intersects(reserve)), "graine " + graine + " : salle dans la réserve");
            assertTrue(p.cellules().stream().noneMatch(cellule -> cellule.avecMurs().intersects(reserve)), "graine " + graine + " : cellule dans la réserve");
            assertTrue(p.filons().stream().noneMatch(bloc -> reserve.isInside(bloc.pos())), "graine " + graine + " : filon dans la réserve");
            assertTrue(p.puits().stream().noneMatch(puits -> puits.interieur().intersects(reserve)), "graine " + graine + " : puits dans la réserve");
        }
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

    @Test
    void unCoffreSeRemplitVideEtApresLeDelai() {
        long t = 1_000_000;
        assertFalse(PlanDeMine.coffreDoitSeRemplir(null, true, t), "jamais ouvert : sa table d'origine");
        assertFalse(PlanDeMine.coffreDoitSeRemplir(t - PlanDeMine.DELAI_REMPLISSAGE, false, t), "pas vide : on y range ses affaires");
        assertFalse(PlanDeMine.coffreDoitSeRemplir(t - PlanDeMine.DELAI_REMPLISSAGE + 1, true, t), "trop tôt");
        assertTrue(PlanDeMine.coffreDoitSeRemplir(t - PlanDeMine.DELAI_REMPLISSAGE, true, t));
    }
}
