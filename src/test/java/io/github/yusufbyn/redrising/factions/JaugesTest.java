package io.github.yusufbyn.redrising.factions;

import io.github.yusufbyn.redrising.couleur.Couleur;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Bornes des jauges (G2.2) : logique pure, sans lancer Minecraft. */
class JaugesTest {
    @Test
    void bornesParDefautDe0A100() {
        Bornes bornes = Bornes.pour(Couleur.OR, Jauge.LOYAUTE);
        assertEquals(0, bornes.borner(-5));
        assertEquals(42, bornes.borner(42));
        assertEquals(100, bornes.borner(250));
    }

    @Test
    void loyauteDuRougePlafonnee() {
        assertEquals(Bornes.PLAFOND_LOYAUTE_ROUGE, Bornes.pour(Couleur.ROUGE, Jauge.LOYAUTE).borner(100));
        // Le plafond ne touche que la Loyauté
        assertEquals(100, Bornes.pour(Couleur.ROUGE, Jauge.SOUTIEN).borner(100));
        assertEquals(100, Bornes.pour(Couleur.ROUGE, Jauge.SUSPICION).borner(100));
    }

    @Test
    void joueurPasEncoreNeBorneParDefaut() {
        assertEquals(Bornes.PAR_DEFAUT, Bornes.pour(null, Jauge.LOYAUTE));
    }

    @Test
    void additionSansDebordement() {
        // Integer.MAX_VALUE + 1 déborderait en int ; borner prend un long
        assertEquals(100, Bornes.PAR_DEFAUT.borner((long) Integer.MAX_VALUE + 1));
    }

    @Test
    void bornesInvalidesRefusees() {
        assertThrows(IllegalArgumentException.class, () -> new Bornes(50, 10));
        assertThrows(IllegalArgumentException.class, () -> new Bornes(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> new Bornes(0, 101));
    }

    @Test
    void bornerToutesLesJauges() {
        Jauges horsBornes = new Jauges(90, 120, -3);
        assertEquals(new Jauges(Bornes.PLAFOND_LOYAUTE_ROUGE, 100, 0), horsBornes.borner(Couleur.ROUGE));
        assertEquals(new Jauges(90, 100, 0), horsBornes.borner(Couleur.GRIS));
    }

    @Test
    void departDejaDansLesBornesSaufRouge() {
        for (Couleur couleur : Couleur.values()) {
            Jauges bornees = Jauges.DEPART.borner(couleur);
            if (couleur == Couleur.ROUGE) {
                assertEquals(Bornes.PLAFOND_LOYAUTE_ROUGE, bornees.loyaute());
            } else {
                assertEquals(Jauges.DEPART, bornees, couleur.getSerializedName());
            }
        }
    }

    @Test
    void avecNeChangeQueLaJaugeVisee() {
        Jauges depart = new Jauges(10, 20, 30);
        assertEquals(new Jauges(11, 20, 30), depart.avec(Jauge.LOYAUTE, 11));
        assertEquals(new Jauges(10, 21, 30), depart.avec(Jauge.SOUTIEN, 21));
        assertEquals(new Jauges(10, 20, 31), depart.avec(Jauge.SUSPICION, 31));
        assertEquals(30, depart.valeur(Jauge.SUSPICION));
    }
}
