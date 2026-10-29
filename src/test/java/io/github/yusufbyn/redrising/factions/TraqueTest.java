package io.github.yusufbyn.redrising.factions;

import io.github.yusufbyn.redrising.couleur.Couleur;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Traque (G5.6) : semer, abandon, retrait, renforts. */
class TraqueTest {
    @Test
    void semerLesGris() {
        Traque traque = Traque.DEBUT.avancer(Traque.DUREE_SEMER - 10, false);
        assertEquals(Traque.Issue.EN_COURS, traque.issue(false));
        // Revu juste avant : le compte repart de zéro
        traque = traque.avancer(5, true).avancer(Traque.DUREE_SEMER - 1, false);
        assertEquals(Traque.Issue.EN_COURS, traque.issue(false));
        assertEquals(Traque.Issue.GAGNEE, traque.avancer(1, false).issue(false));
    }

    @Test
    void abandonGagneSeulementHorsDeVue() {
        Traque longue = Traque.DEBUT.avancer(Traque.DUREE_MAX, true);
        // Perché et toujours vu : retrait des Gris, pas une victoire
        assertEquals(Traque.Issue.RETRAIT, longue.issue(true));
        assertEquals(Traque.Issue.GAGNEE, longue.issue(false));
    }

    @Test
    void renfortsEtChasseursTues() {
        assertEquals(3, Traque.DEBUT.chasseursVoulus());
        assertEquals(4, Traque.DEBUT.avancer(Traque.RENFORT, true).chasseursVoulus());
        assertEquals(Traque.CHASSEURS_MAX, Traque.DEBUT.avancer(Traque.RENFORT * 10L, true).chasseursVoulus());
        // Les chasseurs tués ne sont remplacés que par les renforts
        Traque troisTues = Traque.DEBUT.chasseurTue().chasseurTue().chasseurTue();
        assertEquals(0, troisTues.chasseursVoulus());
        assertEquals(1, troisTues.avancer(Traque.RENFORT, true).chasseursVoulus());
    }

    @Test
    void tuerUnChasseurNeRapportePasDeSoutien() {
        assertEquals(new Variation(-10, 0, 10), EffetsActions.tuerChasseur());
        assertEquals(EffetsActions.tuer(Couleur.GRIS).loyaute(), EffetsActions.tuerChasseur().loyaute());
    }

    @Test
    void suspicionApresLaTraque() {
        // Gagner coûte moins mais laisse plus soupçonné ; tous restent au palier de contrôle
        for (int suspicion : new int[]{Traque.SUSPICION_GAGNEE, Traque.SUSPICION_RETRAIT, Traque.SUSPICION_ARRETE}) {
            assertEquals(true, Acces.estControle(new Jauges(50, 0, suspicion)), "Suspicion " + suspicion);
        }
    }

    @Test
    void immuniteApresUnRalentissement() {
        assertTrue(Traque.etourdit(null, 0));
        // Pendant le ralentissement, puis les 3 secondes d'immunité : rien
        assertFalse(Traque.etourdit(100L, 100 + Traque.ETOURDISSEMENT - 1));
        assertFalse(Traque.etourdit(100L, 100 + Traque.ETOURDISSEMENT + Traque.IMMUNITE_ETOURDISSEMENT - 1));
        assertTrue(Traque.etourdit(100L, 100 + Traque.ETOURDISSEMENT + Traque.IMMUNITE_ETOURDISSEMENT));
        // Un cycle (ralenti, puis libre à 5,6 blocs/s en sprint) laisse le temps de faire une vingtaine de blocs
        double blocs = Traque.ETOURDISSEMENT / 20.0 * 1.4 + Traque.IMMUNITE_ETOURDISSEMENT / 20.0 * 5.6;
        assertTrue(blocs >= 19, "blocs par cycle : " + blocs);
    }
}
