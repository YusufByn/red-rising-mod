package io.github.yusufbyn.redrising.factions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Déroulement d'un contrôle (G5.5), et paliers de Suspicion. */
class ControleTest {
    @Test
    void obeiApresLaDureeDuControle() {
        Controle controle = new Controle(0);
        assertEquals(Controle.Issue.EN_COURS, controle.suivre(Controle.DETECTION, 0));
        assertEquals(Controle.Issue.EN_COURS, controle.suivre(2, 40));
        assertTrue(controle.controleurArrive());
        assertEquals(Controle.Issue.EN_COURS, controle.suivre(3, 40 + Controle.DUREE - 1));
        assertEquals(Controle.Issue.OBEI, controle.suivre(3, 40 + Controle.DUREE));
    }

    @Test
    void fuiteDesLeHaltePendantLApproche() {
        Controle controle = new Controle(0);
        assertEquals(Controle.Issue.EN_COURS, controle.suivre(Controle.DISTANCE_FUITE, 10));
        assertEquals(Controle.Issue.FUITE, controle.suivre(Controle.DISTANCE_FUITE + 0.5, 20));
        assertFalse(controle.controleurArrive());
    }

    @Test
    void fuitePendantLeControle() {
        Controle controle = new Controle(0);
        controle.suivre(1, 10);
        assertEquals(Controle.Issue.FUITE, controle.suivre(Controle.DISTANCE_FUITE + 1, 20));
    }

    @Test
    void abandonSiLeControleurNArrivePas() {
        Controle controle = new Controle(0);
        assertEquals(Controle.Issue.EN_COURS, controle.suivre(8, Controle.APPROCHE_MAX - 1));
        assertEquals(Controle.Issue.ABANDON, controle.suivre(8, Controle.APPROCHE_MAX));
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
