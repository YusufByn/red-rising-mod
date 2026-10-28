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
 * bouchon sous la surface, même graine même plan.
 */
class PlanDeMineTest {
    private static final BlockPos CENTRE = new BlockPos(-740, 67, -317);
    private static final long[] GRAINES = {20_261_028L, 7L, -4_242_424_242L, 123L, 99_999L, 1L, 2L, 3L};

    @Test
    void toutEstReliéAuPuitsPrincipal() {
        for (long graine : GRAINES) {
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66);
            List<BoundingBox> boites = new ArrayList<>(plan.galeries());
            plan.puits().forEach(p -> boites.add(p.interieur()));
            boites.add(plan.caverne().boite());
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
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66);
            assertEquals(PlanDeMine.NIVEAUX_MAX, plan.sols().size());
            for (BoundingBox galerie : plan.galeries()) {
                assertTrue(Math.abs(galerie.minX() - CENTRE.getX()) <= 120 && Math.abs(galerie.maxX() - CENTRE.getX()) <= 120
                        && Math.abs(galerie.minZ() - CENTRE.getZ()) <= 120 && Math.abs(galerie.maxZ() - CENTRE.getZ()) <= 120, galerie.toString());
                assertTrue(galerie.minY() >= -50 && galerie.maxY() <= 20, galerie.toString());
            }
            BoundingBox caverne = plan.caverne().boite();
            assertTrue(Math.abs(caverne.minX() - CENTRE.getX()) <= 120 && Math.abs(caverne.maxX() - CENTRE.getX()) <= 120, caverne.toString());
            // Le bouchon : 2 couches, avec au moins 3 blocs du terrain d'origine au-dessus
            assertEquals(2, plan.bouchon().getYSpan());
            assertTrue(plan.bouchon().maxY() <= 66 - 4);
            assertEquals(plan.bouchon().minY() - 1, plan.puitsPrincipal().haut());
            // La boîte du puits reste à 14 blocs sous la surface
            assertTrue(plan.hautBoitePuits() <= 66 - PlanDeMine.BOITE_PUITS_SOUS_SURFACE);
        }
    }

    @Test
    void unNiveauDeMoinsSousUneVallee() {
        // Terrain le plus bas à y = 25 : le niveau du haut (sol 8) serait trop près de la surface
        PlanDeMine plan = PlanDeMine.tirer(1, CENTRE, 25, 66);
        assertFalse(plan.sols().contains(8));
        assertTrue(plan.hautDesBoites() + 12 < 25);
    }

    @Test
    void douzeCellulesEnRochePleineEtCulsDeSacMuets() {
        for (long graine : GRAINES) {
            PlanDeMine plan = PlanDeMine.tirer(graine, CENTRE, 62, 66);
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
    void colonieReservee() {
        PlanDeMine plan = PlanDeMine.tirer(42, CENTRE, 62, 66);
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
        PlanDeMine a = PlanDeMine.tirer(5, CENTRE, 62, 66);
        PlanDeMine b = PlanDeMine.tirer(5, CENTRE, 62, 66);
        assertEquals(a.galeries(), b.galeries());
        assertEquals(a.cellules(), b.cellules());
        assertEquals(a.puits(), b.puits());
        assertSame(PlanDeMine.de(5, CENTRE, 62, 66), PlanDeMine.de(5, CENTRE, 62, 66));
    }
}
