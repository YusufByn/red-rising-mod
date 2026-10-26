package io.github.yusufbyn.redrising.factions;

import io.github.yusufbyn.redrising.couleur.Couleur;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Accès aux postes de la Société selon la Loyauté (G5.3), aux cellules du Rising selon le Soutien (G5.4). */
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

    @Test
    void nouveauJoueurNEntrePasDansUneCellule() {
        assertFalse(Acces.peutEntrerRising(Jauges.DEPART));
    }

    @Test
    void celluleAtteignableSansTuer() {
        // Par l'aide seule : un don par jour à chaque PNJ du peuple
        Jauges jauges = Jauges.DEPART;
        int dons = 0;
        while (!Acces.peutEntrerRising(jauges)) {
            jauges = jauges.plus(EffetsActions.aider(Couleur.MARRON), Couleur.VERT);
            dons++;
        }
        assertEquals(Acces.SEUIL_SOUTIEN_RISING, dons);
        // L'agent double reste admis à la Société
        assertTrue(Acces.peutEntrerSociete(jauges));
        // Par les vols à la Société
        Jauges voleur = Jauges.DEPART;
        while (!Acces.peutEntrerRising(voleur)) {
            voleur = voleur.plus(EffetsActions.voler(Camp.SOCIETE), Couleur.VERT);
        }
        assertTrue(Acces.peutEntrerRising(voleur));
    }

    @Test
    void uneDenonciationFaitPerdreLAcces() {
        Jauges admis = new Jauges(50, Acces.SEUIL_SOUTIEN_RISING, 0);
        assertTrue(Acces.peutEntrerRising(admis));
        assertFalse(Acces.peutEntrerRising(admis.plus(EffetsActions.denoncer(), Couleur.VERT)));
    }
}
