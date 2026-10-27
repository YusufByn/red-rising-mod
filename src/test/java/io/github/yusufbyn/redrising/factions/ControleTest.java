package io.github.yusufbyn.redrising.factions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Déroulement d'un contrôle (G5.5), et paliers de Suspicion. */
class ControleTest {
    @Test
    void obeiApresLaDureeDuControle() {
        Controle controle = new Controle(Controle.CONTROLE, 0);
        assertEquals(Controle.Issue.EN_COURS, controle.suivre(Controle.DETECTION, 0));
        assertEquals(Controle.Issue.EN_COURS, controle.suivre(2, 40));
        assertTrue(controle.controleurArrive());
        assertEquals(Controle.Issue.EN_COURS, controle.suivre(3, 40 + Controle.CONTROLE.duree() - 1));
        assertEquals(Controle.Issue.ABOUTI, controle.suivre(3, 40 + Controle.CONTROLE.duree()));
    }

    @Test
    void fuiteDesLeHaltePendantLApproche() {
        Controle controle = new Controle(Controle.CONTROLE, 0);
        assertEquals(Controle.Issue.EN_COURS, controle.suivre(Controle.CONTROLE.distanceFuite(), 10));
        assertEquals(Controle.Issue.FUITE, controle.suivre(Controle.CONTROLE.distanceFuite() + 0.5, 20));
        assertFalse(controle.controleurArrive());
    }

    @Test
    void fuitePendantLeControle() {
        Controle controle = new Controle(Controle.CONTROLE, 0);
        controle.suivre(1, 10);
        assertEquals(Controle.Issue.FUITE, controle.suivre(Controle.CONTROLE.distanceFuite() + 1, 20));
    }

    @Test
    void abandonSiLeControleurNArrivePas() {
        Controle controle = new Controle(Controle.CONTROLE, 0);
        assertEquals(Controle.Issue.EN_COURS, controle.suivre(8, Controle.CONTROLE.approcheMax() - 1));
        assertEquals(Controle.Issue.ABANDON, controle.suivre(8, Controle.CONTROLE.approcheMax()));
    }

    @Test
    void arrestationContactTenuSansInterruption() {
        Controle arret = new Controle(Traque.ARRESTATION, 0);
        assertEquals(Controle.Issue.EN_COURS, arret.suivre(2, 10));
        // Le joueur s'éloigne un instant : le compte repart de zéro
        assertEquals(Controle.Issue.EN_COURS, arret.suivre(4, 50));
        assertFalse(arret.controleurArrive());
        assertEquals(Controle.Issue.EN_COURS, arret.suivre(2, 60));
        assertEquals(Controle.Issue.EN_COURS, arret.suivre(2, 60 + Traque.ARRESTATION.duree() - 1));
        assertEquals(Controle.Issue.ABOUTI, arret.suivre(2, 60 + Traque.ARRESTATION.duree()));
    }

    @Test
    void arrestationSansFuiteNiAbandon() {
        Controle arret = new Controle(Traque.ARRESTATION, 0);
        // Un joueur perché, hors d'atteinte, reste « en cours » aussi longtemps que dure la traque
        assertEquals(Controle.Issue.EN_COURS, arret.suivre(500, 1_000_000));
    }

    @Test
    void palierDeControle() {
        assertFalse(Acces.estControle(new Jauges(50, 0, 39)));
        assertTrue(Acces.estControle(new Jauges(50, 0, 40)));
        assertTrue(Acces.estControle(new Jauges(50, 0, 99)));
        // Au maximum, c'est la traque (G5.6)
        assertFalse(Acces.estControle(new Jauges(50, 0, 100)));
    }
}
