package io.github.yusufbyn.redrising.jalons;

import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.data.EtatMonde;
import io.github.yusufbyn.redrising.factions.EffetsActions;
import io.github.yusufbyn.redrising.factions.Variation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Personnages canon (G6.4) : essentiels avant leur jalon, K.-O. sans Soutien, morts dans le bilan. */
class PersonnagesTest {
    @AfterEach
    void oublier() {
        Personnages.retirer("test");
    }

    @Test
    void essentielTantQueSonJalonNaPasDIssue() {
        Personnage darrow = new Personnage("darrow", "triomphe");
        assertTrue(Personnages.essentiel(darrow, EtatMonde.NOUVEAU));
        assertFalse(Personnages.essentiel(darrow, EtatMonde.NOUVEAU.avecIssue("triomphe", Issue.DIVERGENTE)));
    }

    @Test
    void unJalonFixeRendEssentielAussi() {
        // Eo : essentielle jusqu'à sa pendaison (jalon fixe), sinon on la tue avant et le jalon casse
        Personnage eo = new Personnage("eo", "pendaison_eo");
        assertTrue(Personnages.essentiel(eo, EtatMonde.NOUVEAU));
        assertFalse(Personnages.essentiel(eo, EtatMonde.NOUVEAU.avecIssue("pendaison_eo", Issue.CANON)));
    }

    @Test
    void sansJalonJamaisEssentiel() {
        assertFalse(Personnages.essentiel(new Personnage("passant", null), EtatMonde.NOUVEAU));
    }

    @Test
    void koSansSoutien() {
        // Loyauté et Suspicion du meurtre de sa Couleur, jamais de Soutien (même pour un Gris, qui en rapporterait)
        assertEquals(new Variation(-10, 0, 10), EffetsActions.ko(Couleur.GRIS));
        assertEquals(new Variation(-3, 0, 5), EffetsActions.ko(Couleur.ROUGE));
    }

    @Test
    void mortsDansLeBilan() {
        UUID joueur = UUID.fromString("2654e3c3-150d-3857-a426-0b141796a4e0");
        EtatMonde etat = EtatMonde.NOUVEAU.avecMort("darrow", new MortPersonnage(100, Optional.of(joueur)))
                .avecMort("eo", new MortPersonnage(50, Optional.empty()));
        Bilan bilan = Bilan.depuis(Horloge.ZERO, etat);
        assertTrue(bilan.tue("darrow"));
        assertTrue(bilan.tueParUnJoueur("darrow"));
        assertTrue(bilan.tue("eo"));
        assertFalse(bilan.tueParUnJoueur("eo"));
        assertFalse(bilan.tue("sevro"));
    }

    @Test
    void enFuiteDansLeBilan() {
        // Un Ares en fuite peut peser sur une issue : pas mort, mais lisible
        EtatMonde etat = EtatMonde.NOUVEAU.avecFuite("ares", 1_000);
        Bilan bilan = Bilan.depuis(Horloge.ZERO, etat);
        assertTrue(bilan.enFuite("ares"));
        assertFalse(bilan.tue("ares"));
        // Redonné à un PNJ : plus en fuite
        assertEquals(Map.of(), etat.sansFuite("ares").enFuite());
    }

    @Test
    void laPremiereMortCompte() {
        EtatMonde etat = EtatMonde.NOUVEAU.avecMort("eo", new MortPersonnage(50, Optional.empty()))
                .avecMort("eo", new MortPersonnage(80, Optional.empty()));
        assertEquals(Map.of("eo", new MortPersonnage(50, Optional.empty())), etat.morts());
        assertEquals(Map.of(), etat.sansMort("eo").morts());
    }

    @Test
    void declarationEnDoubleRefusee() {
        Personnages.declarer("test", "x");
        assertThrows(IllegalStateException.class, () -> Personnages.declarer("test", null));
    }
}
