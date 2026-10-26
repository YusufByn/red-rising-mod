package io.github.yusufbyn.redrising.factions;

import io.github.yusufbyn.redrising.couleur.Couleur;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Accès aux postes de la Société selon la Loyauté (G5.3). */
class AccesTest {
    @Test
    void nouveauJoueurEntre() {
        assertTrue(Acces.peutEntrerSociete(Jauges.DEPART));
    }

    @Test
    void rougeIrreprochableEntreToutJuste() {
        // Le Rouge naît plafonné à 40 : il entre, mais une seule faute contre la Société le refoule
        Jauges rouge = Jauges.DEPART.borner(Couleur.ROUGE);
        assertTrue(Acces.peutEntrerSociete(rouge));
        assertFalse(Acces.peutEntrerSociete(rouge.plus(EffetsActions.voler(Camp.SOCIETE), Couleur.ROUGE)));
    }

    @Test
    void meurtreDUnPnjDeLEliteRefoule() {
        assertFalse(Acces.peutEntrerSociete(Jauges.DEPART.plus(EffetsActions.tuer(Couleur.OR), Couleur.VERT)));
    }
}
