package io.github.yusufbyn.redrising.jalons;

import io.github.yusufbyn.redrising.data.EtatMonde;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static io.github.yusufbyn.redrising.jalons.Echeance.JOUR;
import static io.github.yusufbyn.redrising.jalons.Echeance.jours;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Moteur des jalons (G6.2) : échéance, rythme, issue, dépendances, mise en route. */
class JalonsTest {
    /** Base 2 jours, entre 1 et 3, un vingtième de jour par point de rythme. */
    private static final Echeance ECHEANCE = new Echeance(jours(2), jours(1), jours(3), 0.05);

    private static Jalon.Builder jalon(String id) {
        return Jalon.builder(id).epoque(Epoque.RED_RISING).echeance(jours(2), jours(1), jours(3), 0.05)
                .issue(bilan -> bilan.rythme() >= 0 ? Issue.CANON : Issue.DIVERGENTE);
    }

    private static Bilan bilan(long presence, long aide, long tort) {
        return new Bilan(new Horloge(presence, presence, aide, tort), Map.of(), Map.of(), Map.of(), Set.of());
    }

    @AfterEach
    void oublier() {
        Jalons.retirer("test");
        Jalons.retirer("suite");
    }

    // --- Échéance ---

    @Test
    void rythmeNulBase() {
        assertEquals(jours(2), ECHEANCE.delai(0));
    }

    @Test
    void aiderAvanceCombattreRecule() {
        assertEquals(jours(1.5), ECHEANCE.delai(10));
        assertEquals(jours(2.5), ECHEANCE.delai(-10));
    }

    @Test
    void jamaisHorsDesBornes() {
        assertEquals(jours(1), ECHEANCE.delai(1_000));
        assertEquals(jours(3), ECHEANCE.delai(-1_000));
    }

    @Test
    void bornesIncoherentesRefusees() {
        assertThrows(IllegalArgumentException.class, () -> new Echeance(jours(2), jours(3), jours(1), 0));
    }

    // --- Rythme ---

    @Test
    void rythmeParJoueurEtParJour() {
        // 30 de net en 2 jours à un joueur, ou en 1 jour à deux joueurs : 15 par joueur et par jour
        assertEquals(15, bilan(2 * JOUR, 40, 10).rythme(), 1e-9);
        assertEquals(15, new Bilan(new Horloge(JOUR, 2 * JOUR, 30, 0), Map.of(), Map.of(), Map.of(), Set.of()).rythme(), 1e-9);
    }

    @Test
    void auMoinsUnJourJoueur() {
        // Dans les premières minutes, 10 de net ne font pas un rythme de plusieurs centaines
        assertEquals(10, bilan(JOUR / 100, 10, 0).rythme(), 1e-9);
    }

    @Test
    void unDernierJourPesePeu() {
        // Sur 5 jours-joueur, un jour de plafond (30) déplace le rythme de 30 / 5 au plus
        double avant = bilan(5 * JOUR, 0, 10).rythme();
        double apres = bilan(5 * JOUR, 30, 10).rythme();
        assertEquals(6, apres - avant, 1e-9);
    }

    @Test
    void bilanDeLaPeriode() {
        Bilan bilan = Bilan.entre(new Horloge(100, 100, 5, 2), new Horloge(JOUR + 100, JOUR + 100, 25, 7), Map.of(), Map.of(), Map.of(), Set.of());
        assertEquals(20, bilan.aide());
        assertEquals(5, bilan.tort());
        assertEquals(15, bilan.net());
        assertEquals(JOUR, bilan.periode().temps());
    }

    // --- Issue ---

    @Test
    void issueSelonLeBilan() {
        Jalon jalon = jalon("test").build();
        assertEquals(Issue.CANON, jalon.issue(bilan(JOUR, 10, 0)));
        assertEquals(Issue.DIVERGENTE, jalon.issue(bilan(JOUR, 0, 10)));
    }

    @Test
    void jalonFixeToujoursLaMemeIssue() {
        Jalon eo = Jalon.builder("test").epoque(Epoque.RED_RISING).echeance(jours(1), jours(1), jours(1), 0).fixe(Issue.CANON).build();
        assertEquals(Issue.CANON, eo.issue(bilan(JOUR, 0, 500)));
    }

    @Test
    void declarationIncompleteRefusee() {
        assertThrows(IllegalStateException.class, () -> Jalon.builder("test").epoque(Epoque.RED_RISING).fixe(Issue.CANON).build());
        assertThrows(IllegalStateException.class, () -> jalon("test").fixe(Issue.CANON).build());
    }

    // --- Dépendances et mise en route ---

    @Test
    void dependances() {
        assertTrue(jalon("test").apres("a", "b").build().dependancesSatisfaites(Map.of("a", Issue.CANON, "b", Issue.DIVERGENTE)));
        assertFalse(jalon("test").apres("a", "b").build().dependancesSatisfaites(Map.of("a", Issue.CANON)));
        // Jalon propre à une branche
        assertFalse(jalon("test").apres("a", Issue.DIVERGENTE).build().dependancesSatisfaites(Map.of("a", Issue.CANON)));
        assertTrue(jalon("test").apres("a", Issue.DIVERGENTE).build().dependancesSatisfaites(Map.of("a", Issue.DIVERGENTE)));
        // Branches qui se rejoignent
        assertTrue(jalon("test").apresUnDe("a", "b").build().dependancesSatisfaites(Map.of("b", Issue.CANON)));
        assertFalse(jalon("test").apresUnDe("a", "b").build().dependancesSatisfaites(Map.of("c", Issue.CANON)));
    }

    @Test
    void miseEnRoute() {
        Jalon suite = jalon("suite").apres("test").build();
        EtatMonde etat = EtatMonde.NOUVEAU;
        assertFalse(Jalons.peutSeMettreEnRoute(suite, etat), "le jalon précédent n'est pas passé");
        etat = etat.avecIssue("test", Issue.CANON);
        assertTrue(Jalons.peutSeMettreEnRoute(suite, etat));
        assertFalse(Jalons.peutSeMettreEnRoute(suite, etat.avecEpoque(Epoque.GOLDEN_SON)), "pas à son époque");
        assertFalse(Jalons.peutSeMettreEnRoute(suite, etat.avecMiseEnRoute("suite", Horloge.ZERO)), "déjà en route");
        assertFalse(Jalons.peutSeMettreEnRoute(suite, etat.avecIssue("suite", Issue.CANON)), "déjà passé");
    }

    @Test
    void restantDepuisLaMiseEnRoute() {
        Jalon jalon = jalon("test").build();
        Horloge depart = new Horloge(JOUR, JOUR, 0, 0);
        EtatMonde etat = EtatMonde.NOUVEAU.avecMiseEnRoute("test", depart).avecHorloge(depart.avancer(JOUR, 1));
        assertEquals(JOUR, Jalons.restant(jalon, etat));
        // 20 d'aide en ce jour : rythme 20, délai 1 jour, dû
        assertEquals(0, Jalons.restant(jalon, etat.avecHorloge(etat.horloge().compter(20))));
    }

    @Test
    void declarationEnDoubleRefusee() {
        Jalons.declarer(jalon("test").build());
        assertThrows(IllegalStateException.class, () -> Jalons.declarer(jalon("test").build()));
    }
}
