package io.github.yusufbyn.redrising.jalons;

import io.github.yusufbyn.redrising.data.EtatMonde;
import io.github.yusufbyn.redrising.factions.Camp;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Attitude des personnages canon (G6.5) : bornes, plafond des gains du jour, actions vues, moyenne pour le monde. */
class AttitudeTest {
    private static final UUID A = UUID.fromString("2654e3c3-150d-3857-a426-0b141796a4e0");
    private static final UUID B = UUID.fromString("0f3a8d2e-4b1c-4e7a-9d6f-1a2b3c4d5e6f");
    private static final UUID C = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Test
    void gainsPlafonnesParJour() {
        // Aide (+5) puis deux vols vus contre l'ennemi (+3, +3) le même jour : 10, pas 11
        Attitude attitude = Attitude.NEUTRE.changer(Attitude.AIDE, 4).changer(Attitude.VU_POUR, 4).changer(Attitude.VU_POUR, 4);
        assertEquals(10, attitude.valeur());
        assertEquals(10, attitude.gainsDuJour());
        // Plafond atteint : plus rien ce jour-là
        assertEquals(10, attitude.changer(Attitude.AIDE, 4).valeur());
        // Le lendemain, ça rouvre
        assertEquals(15, attitude.changer(Attitude.AIDE, 5).valeur());
    }

    @Test
    void pertesNonPlafonnees() {
        // Deux K.-O. le même jour : -40, et une perte n'ouvre pas de nouveaux gains
        Attitude attitude = Attitude.NEUTRE.changer(Attitude.AIDE, 1).changer(Attitude.AIDE, 1).changer(Attitude.KO, 1).changer(Attitude.KO, 1);
        assertEquals(10 - 40, attitude.valeur());
        assertEquals(-30, attitude.changer(Attitude.AIDE, 1).valeur());
    }

    @Test
    void bornes() {
        assertEquals(Attitude.MINIMUM, Attitude.NEUTRE.fixer(-90).changer(Attitude.CELLULE_DENONCEE, 0).valeur());
        Attitude haut = Attitude.NEUTRE.fixer(98).changer(Attitude.AIDE, 0);
        assertEquals(Attitude.MAXIMUM, haut.valeur());
        // Seul ce qui a été gagné compte dans le plafond
        assertEquals(2, haut.gainsDuJour());
        assertEquals(Attitude.MAXIMUM, Attitude.NEUTRE.fixer(500).valeur());
    }

    @Test
    void actionsVues() {
        assertEquals(Attitude.VU_CONTRE, Attitude.vu(Attitude.Acte.VOL, Camp.RISING, Camp.RISING));
        assertEquals(Attitude.VU_VIOLENCE, Attitude.vu(Attitude.Acte.VIOLENCE, Camp.RISING, Camp.RISING));
        assertEquals(Attitude.VU_CONTRE, Attitude.vu(Attitude.Acte.DENONCIATION, Camp.RISING, Camp.RISING));
        assertEquals(Attitude.VU_POUR, Attitude.vu(Attitude.Acte.VOL, Camp.SOCIETE, Camp.RISING));
        assertEquals(Attitude.VU_POUR, Attitude.vu(Attitude.Acte.VIOLENCE, Camp.RISING, Camp.SOCIETE));
        // Sans camp, ou une action contre personne : rien
        assertEquals(0, Attitude.vu(Attitude.Acte.VOL, Camp.SOCIETE, null));
        assertEquals(0, Attitude.vu(Attitude.Acte.VOL, null, Camp.RISING));
    }

    @Test
    void moyenneDesJoueursQuiLOntCroise() {
        assertEquals(0, Attitude.moyenne(List.of()));
        EtatMonde etat = EtatMonde.NOUVEAU.avecAttitude("darrow", A, Attitude.NEUTRE.fixer(30))
                .avecAttitude("darrow", B, Attitude.NEUTRE.fixer(-10))
                .avecAttitude("sevro", C, Attitude.NEUTRE.fixer(50));
        assertEquals(10, etat.attitudeMoyenne("darrow"), 1e-9);
        assertEquals(0, etat.attitudeMoyenne("mustang"), 1e-9);
        assertEquals(30, etat.attitude("darrow", A).valeur());
        assertEquals(Attitude.NEUTRE, etat.attitude("darrow", C));
        // Le bilan d'un jalon la lit
        Bilan bilan = Bilan.depuis(Horloge.ZERO, etat);
        assertEquals(10, bilan.attitude("darrow"), 1e-9);
        assertEquals(50, bilan.attitude("sevro"), 1e-9);
        assertEquals(0, bilan.attitude("mustang"), 1e-9);
    }

    @Test
    void campLuParUneSeuleFonction() {
        assertEquals(Camp.RISING, Personnages.camp(new Personnage("darrow", "triomphe", Camp.RISING), EtatMonde.NOUVEAU));
        assertNull(Personnages.camp(new Personnage("passant", null), EtatMonde.NOUVEAU));
    }

    @Test
    void attitudeSansEffetSurLHorloge() {
        // L'attitude ne passe pas par les jauges : ni aide ni tort dans l'horloge du monde
        EtatMonde etat = EtatMonde.NOUVEAU.avecAttitude("darrow", A, Attitude.NEUTRE.changer(Attitude.KO, 0));
        assertEquals(Horloge.ZERO, etat.horloge());
    }
}
