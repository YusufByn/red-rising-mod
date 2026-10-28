package io.github.yusufbyn.redrising.jalons;

import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.data.EtatMonde;
import io.github.yusufbyn.redrising.factions.Variation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static io.github.yusufbyn.redrising.jalons.Echeance.jours;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Effets des jalons (G6.3) : leviers posés, niveau en cours, versements décidés une fois par joueur. */
class EffetsTest {
    private static final EffetsJalon EFFETS = effets(builder -> builder
            .levier(Levier.ALERTE_PATROUILLES, 1, jours(1))
            .levier(Levier.SILENCE_ROUGE, 1)
            .jauges(Couleur.ROUGE, new Variation(0, 10, 0)));

    private static EffetsJalon effets(Consumer<EffetsJalon.Builder> declaration) {
        return Jalon.builder("x").epoque(Epoque.RED_RISING).echeance(1, 1, 1, 0).fixe(Issue.CANON)
                .effets(Issue.CANON, declaration).build().effets(Issue.CANON);
    }

    @Test
    void appliquerPoseToutEtMarqueLesEffetsAppliques() {
        EtatMonde etat = EtatMonde.NOUVEAU.avecIssue("x", Issue.CANON).avecEffetsEnAttente("x", true);
        EtatMonde apres = Effets.appliquer(etat, "x", Issue.CANON, EFFETS, 1_000);
        assertEquals(Set.of("x/canon/0", "x/canon/1"), apres.effets().keySet());
        assertEquals(new EffetEnCours(Levier.ALERTE_PATROUILLES, 1, "x", 1_000, 1_000 + jours(1)), apres.effets().get("x/canon/0"));
        assertEquals(EffetEnCours.PERMANENT, apres.effets().get("x/canon/1").fin());
        // Le versement aux Rouges, puis la nouvelle
        assertEquals(2, apres.versements().size());
        assertEquals(new Versement("x/canon/2", "x", Issue.CANON, Optional.of(Couleur.ROUGE), new Variation(0, 10, 0), false, 1_000), apres.versements().get(0));
        assertTrue(apres.versements().get(1).nouvelle());
        assertFalse(apres.effetsEnAttente().contains("x"));
    }

    @Test
    void unRejeuNeDoubleRienNiNeRelanceLaDuree() {
        EtatMonde une = Effets.appliquer(EtatMonde.NOUVEAU, "x", Issue.CANON, EFFETS, 1_000);
        EtatMonde deux = Effets.appliquer(une, "x", Issue.CANON, EFFETS, 5_000);
        assertEquals(une, deux);
    }

    @Test
    void leNiveauLePlusFortLEmporteChacunGardeSaDuree() {
        Map<String, EffetEnCours> effets = Map.of(
                "a", new EffetEnCours(Levier.ALERTE_PATROUILLES, 1, "a", 0, 1_000),
                "b", new EffetEnCours(Levier.ALERTE_PATROUILLES, 2, "b", 0, 500));
        assertEquals(2, Effets.niveau(effets, Levier.ALERTE_PATROUILLES, 100));
        // Le plus fort fini, le suivant reprend
        assertEquals(1, Effets.niveau(effets, Levier.ALERTE_PATROUILLES, 500));
        assertEquals(0, Effets.niveau(effets, Levier.ALERTE_PATROUILLES, 1_000));
        assertEquals(0, Effets.niveau(effets, Levier.SILENCE_ROUGE, 100));
    }

    @Test
    void niveauBorne() {
        assertEquals(2, new EffetEnCours(Levier.ALERTE_PATROUILLES, 9, "a", 0, -1).niveau());
        assertEquals(1, new EffetEnCours(Levier.SILENCE_ROUGE, 3, "a", 0, -1).niveau());
    }

    @Test
    void silence() {
        Map<String, EffetEnCours> effets = Map.of("a", new EffetEnCours(Levier.SILENCE_ROUGE, 1, "a", 0, -1));
        assertTrue(Effets.silencieuse(effets, Couleur.ROUGE, 100));
        assertFalse(Effets.silencieuse(effets, Couleur.GRIS, 100));
        assertFalse(Effets.silencieuse(Map.of(), Couleur.ROUGE, 100));
    }

    @Test
    void versementDecideUneSeuleFoisParJoueur() {
        Versement rouges = new Versement("x/canon/2", "x", Issue.CANON, Optional.of(Couleur.ROUGE), new Variation(0, 10, 0), false, 1_000);
        assertEquals(Versement.Decision.RECEVOIR, rouges.decider(0, Set.of(), Couleur.ROUGE));
        // Pas concerné : marqué reçu sans rien ; devenir Rouge ensuite ne donne rien (déjà décidé)
        assertEquals(Versement.Decision.MARQUER, rouges.decider(0, Set.of(), Couleur.OR));
        assertEquals(Versement.Decision.RIEN, rouges.decider(0, Set.of("x/canon/2"), Couleur.ROUGE));
        // En pleine naissance : on attend sa Couleur
        assertEquals(Versement.Decision.ATTENDRE, rouges.decider(0, Set.of(), null));
        // Arrivé après le jalon : rien
        assertEquals(Versement.Decision.RIEN, rouges.decider(2_000, Set.of(), Couleur.ROUGE));
        // Pour tous
        Versement tous = new Versement("x/canon/3", "x", Issue.CANON, Optional.empty(), new Variation(1, 0, 0), false, 1_000);
        assertEquals(Versement.Decision.RECEVOIR, tous.decider(1_000, Set.of(), Couleur.OR));
    }
}
