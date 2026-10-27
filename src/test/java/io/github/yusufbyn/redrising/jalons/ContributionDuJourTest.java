package io.github.yusufbyn.redrising.jalons;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Plafond par joueur et par jour du Soutien compté pour le monde (G6.1). */
class ContributionDuJourTest {
    private static final int PLAFOND = ContributionDuJour.PLAFOND;

    @Test
    void sousLePlafondToutCompte() {
        ContributionDuJour.Retenue retenue = ContributionDuJour.AUCUNE.retenir(5, 3);
        assertEquals(5, retenue.compte());
        assertEquals(new ContributionDuJour(3, 5, 0), retenue.contribution());
    }

    @Test
    void auPlafondLeResteEstCoupe() {
        ContributionDuJour.Retenue premiere = ContributionDuJour.AUCUNE.retenir(PLAFOND - 5, 3);
        ContributionDuJour.Retenue deuxieme = premiere.contribution().retenir(10, 3);
        assertEquals(5, deuxieme.compte());
        ContributionDuJour.Retenue troisieme = deuxieme.contribution().retenir(1, 3);
        assertEquals(0, troisieme.compte());
        assertEquals(PLAFOND, troisieme.contribution().aide(3));
    }

    @Test
    void aideEtTortOntChacunLeurPlafond() {
        // Une cellule (-25) puis un PNJ (-10) dénoncés le même jour : 30 comptés
        ContributionDuJour.Retenue cellule = ContributionDuJour.AUCUNE.retenir(-25, 3);
        ContributionDuJour.Retenue pnj = cellule.contribution().retenir(-10, 3);
        assertEquals(-25, cellule.compte());
        assertEquals(-5, pnj.compte());
        // Le tort plafonné n'empêche pas l'aide
        ContributionDuJour.Retenue aide = pnj.contribution().retenir(4, 3);
        assertEquals(4, aide.compte());
        assertEquals(new ContributionDuJour(3, 4, PLAFOND), aide.contribution());
    }

    @Test
    void unNouveauJourRepartDeZero() {
        ContributionDuJour pleine = ContributionDuJour.AUCUNE.retenir(PLAFOND, 3).contribution();
        assertEquals(0, pleine.aide(4));
        ContributionDuJour.Retenue lendemain = pleine.retenir(10, 4);
        assertEquals(10, lendemain.compte());
        assertEquals(new ContributionDuJour(4, 10, 0), lendemain.contribution());
    }
}
