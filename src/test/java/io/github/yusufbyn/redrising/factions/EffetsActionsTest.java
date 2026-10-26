package io.github.yusufbyn.redrising.factions;

import io.github.yusufbyn.redrising.couleur.Couleur;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Effets des actions sur les jauges (G5.2) : logique pure, sans lancer Minecraft. */
class EffetsActionsTest {
    private static final Set<Couleur> ELITE = EnumSet.of(Couleur.OR, Couleur.ARGENT, Couleur.CUIVRE, Couleur.BLANC);
    private static final Set<Couleur> ORDRE = EnumSet.of(Couleur.GRIS, Couleur.OBSIDIAN);

    @Test
    void groupesDesCouleurs() {
        for (Couleur couleur : Couleur.values()) {
            GroupePnj attendu = ELITE.contains(couleur) ? GroupePnj.ELITE : ORDRE.contains(couleur) ? GroupePnj.ORDRE : GroupePnj.PEUPLE;
            assertEquals(attendu, GroupePnj.de(couleur), couleur.name());
        }
    }

    @Test
    void tuer() {
        assertEquals(new Variation(-15, 5, 15), EffetsActions.tuer(Couleur.OR));
        assertEquals(new Variation(-10, 4, 10), EffetsActions.tuer(Couleur.GRIS));
        assertEquals(new Variation(-3, -5, 5), EffetsActions.tuer(Couleur.ROUGE));
    }

    @Test
    void aider() {
        assertEquals(new Variation(2, 0, 0), EffetsActions.aider(Couleur.BLANC));
        assertEquals(new Variation(2, 0, 0), EffetsActions.aider(Couleur.OBSIDIAN));
        assertEquals(new Variation(0, 1, 0), EffetsActions.aider(Couleur.MARRON));
    }

    @Test
    void volerEtDenoncer() {
        assertEquals(new Variation(-5, 2, 5), EffetsActions.voler(Camp.SOCIETE));
        assertEquals(new Variation(0, -5, 0), EffetsActions.voler(Camp.RISING));
        assertEquals(new Variation(5, -10, -5), EffetsActions.denoncer());
        assertEquals(new Variation(0, 0, -2), EffetsActions.jourPasse());
    }

    @Test
    void variationBornee() {
        // Un Rouge qui dénonce reste sous son plafond de Loyauté ; Soutien et Suspicion ne passent pas sous 0
        Jauges rouge = new Jauges(38, 3, 2).plus(EffetsActions.denoncer(), Couleur.ROUGE);
        assertEquals(new Jauges(Bornes.PLAFOND_LOYAUTE_ROUGE, 0, 0), rouge);
        // Et rien ne dépasse 100
        assertEquals(new Jauges(0, 100, 100), new Jauges(10, 98, 90).plus(EffetsActions.tuer(Couleur.OR), Couleur.VERT));
    }
}
