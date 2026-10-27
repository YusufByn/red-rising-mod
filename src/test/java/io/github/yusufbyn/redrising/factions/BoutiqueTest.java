package io.github.yusufbyn.redrising.factions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Boutiques (G5.9) : stock du jour, prix ajustés par les modificateurs, seuil d'accès. */
class BoutiqueTest {
    @Test
    void stockDuJour() {
        AchatsDuJour achats = AchatsDuJour.AUCUN.plusUn("fumigene", 3).plusUn("fumigene", 3).plusUn("pain", 3);
        assertEquals(2, achats.nombre("fumigene", 3));
        assertEquals(1, achats.nombre("pain", 3));
        assertEquals(0, achats.nombre("charbon", 3));
    }

    @Test
    void unNouveauJourRemetAZero() {
        AchatsDuJour achats = AchatsDuJour.AUCUN.plusUn("fumigene", 3).plusUn("fumigene", 3);
        assertEquals(0, achats.nombre("fumigene", 4));
        AchatsDuJour lendemain = achats.plusUn("pain", 4);
        assertEquals(0, lendemain.nombre("fumigene", 4));
        assertEquals(1, lendemain.nombre("pain", 4));
    }

    @Test
    void prixSansModificateur() {
        assertEquals(1, PrixBoutique.facteur(List.of()));
        assertEquals(8, PrixBoutique.ajuster(8, 1, 64));
    }

    @Test
    void prixArrondiAuDessusAuMoinsUnAuPlusUnePile() {
        assertEquals(6, PrixBoutique.ajuster(8, 0.75, 64));
        assertEquals(4, PrixBoutique.ajuster(7, 0.5, 64));
        assertEquals(1, PrixBoutique.ajuster(1, 0.5, 64));
        assertEquals(16, PrixBoutique.ajuster(10, 2, 16));
    }

    @Test
    void lesModificateursSeMultiplient() {
        double facteur = PrixBoutique.facteur(List.of(0.8, 0.5));
        assertEquals(0.4, facteur, 1e-9);
        assertEquals(4, PrixBoutique.ajuster(10, facteur, 64));
    }

    @Test
    void memeSeuilQueLaStructure() {
        assertTrue(Boutique.SOCIETE.admet(new Jauges(Acces.SEUIL_LOYAUTE_SOCIETE, 0, 0)));
        assertFalse(Boutique.SOCIETE.admet(new Jauges(Acces.SEUIL_LOYAUTE_SOCIETE - 1, 100, 0)));
        assertTrue(Boutique.RISING.admet(new Jauges(0, Acces.SEUIL_SOUTIEN_RISING, 0)));
        assertFalse(Boutique.RISING.admet(new Jauges(100, Acces.SEUIL_SOUTIEN_RISING - 1, 0)));
    }
}
