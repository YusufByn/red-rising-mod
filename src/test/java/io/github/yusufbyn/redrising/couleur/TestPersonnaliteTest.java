package io.github.yusufbyn.redrising.couleur;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moteur du test de naissance (G4.3). Remplace le script Python du GDD (section 3) :
 * à relancer ({@code ./gradlew build}) après toute modification d'une paire de Couleurs.
 */
class TestPersonnaliteTest {
    /** Bornes du GDD, en pourcentage des façons de répondre au test. */
    private static final double MIN = 6.8;
    private static final double MAX = 7.5;

    @Test
    void septQuestionsDeQuatreReponsesADeuxCouleurs() {
        assertEquals(7, TestPersonnalite.QUESTIONS.size());
        for (TestPersonnalite.Question question : TestPersonnalite.QUESTIONS) {
            assertEquals(TestPersonnalite.NB_REPONSES, question.reponses().size());
            for (TestPersonnalite.Reponse reponse : question.reponses()) {
                assertNotEquals(reponse.premiere(), reponse.seconde(), "Une réponse donne ses 2 points à la même Couleur");
            }
        }
    }

    @Test
    void chaqueCouleurApparaitQuatreFois() {
        Map<Couleur, Integer> apparitions = new EnumMap<>(Couleur.class);
        for (TestPersonnalite.Question question : TestPersonnalite.QUESTIONS) {
            for (TestPersonnalite.Reponse reponse : question.reponses()) {
                apparitions.merge(reponse.premiere(), 1, Integer::sum);
                apparitions.merge(reponse.seconde(), 1, Integer::sum);
            }
        }
        for (Couleur couleur : Couleur.values()) {
            assertEquals(4, apparitions.getOrDefault(couleur, 0), "Apparitions de " + couleur);
        }
    }

    /**
     * Comme le script du GDD : on essaie les 4^7 = 16 384 façons de répondre. Une égalité entre n Couleurs
     * donne 1/n de victoire à chacune (le hasard les départage à parts égales).
     */
    @Test
    void distributionEntre6v8Et7v5Pourcent() {
        Map<Couleur, Double> victoires = new EnumMap<>(Couleur.class);
        int nbQuestions = TestPersonnalite.QUESTIONS.size();
        int combinaisons = (int) Math.pow(TestPersonnalite.NB_REPONSES, nbQuestions);
        for (int n = 0; n < combinaisons; n++) {
            // n écrit en base 4 : un chiffre par question
            int[] choix = new int[nbQuestions];
            int reste = n;
            for (int i = 0; i < nbQuestions; i++) {
                choix[i] = reste % TestPersonnalite.NB_REPONSES;
                reste /= TestPersonnalite.NB_REPONSES;
            }
            List<Couleur> enTete = TestPersonnalite.enTete(TestPersonnalite.scores(choix));
            for (Couleur couleur : enTete) {
                victoires.merge(couleur, 1.0 / enTete.size(), Double::sum);
            }
        }

        Map<Couleur, Double> pourcents = new EnumMap<>(Couleur.class);
        for (Couleur couleur : Couleur.values()) {
            pourcents.put(couleur, victoires.getOrDefault(couleur, 0.0) * 100 / combinaisons);
        }
        String distribution = pourcents.entrySet().stream()
                .map(e -> String.format("%s %.2f %%", e.getKey().getSerializedName(), e.getValue()))
                .collect(Collectors.joining(", "));
        for (Couleur couleur : Couleur.values()) {
            double p = pourcents.get(couleur);
            assertTrue(p >= MIN && p <= MAX, couleur + " hors de " + MIN + " à " + MAX + " % ; distribution : " + distribution);
        }
    }

    @Test
    void pointsDesReponses() {
        // Obsidian aux questions 2, 3, 5 et 6 : 4 points, seule en tête
        Map<Couleur, Integer> scores = TestPersonnalite.scores(1, 0, 0, 1, 0, 2, 2);
        assertEquals(4, scores.get(Couleur.OBSIDIAN));
        assertEquals(3, scores.get(Couleur.OR));
        assertEquals(0, scores.get(Couleur.ROSE));
        assertEquals(14, scores.values().stream().mapToInt(Integer::intValue).sum(), "7 réponses de 2 points");
        assertEquals(List.of(Couleur.OBSIDIAN), TestPersonnalite.enTete(scores));
        assertEquals(Couleur.OBSIDIAN, TestPersonnalite.resultat(RandomSource.create(1), 1, 0, 0, 1, 0, 2, 2));
    }

    @Test
    void egaliteDepartageeAuHasard() {
        // Toujours la 1re réponse : Orange, Rouge et Obsidian à 3 points
        int[] choix = {0, 0, 0, 0, 0, 0, 0};
        Set<Couleur> exAequo = EnumSet.of(Couleur.OBSIDIAN, Couleur.ORANGE, Couleur.ROUGE);
        assertEquals(exAequo, EnumSet.copyOf(TestPersonnalite.enTete(TestPersonnalite.scores(choix))));

        RandomSource aleatoire = RandomSource.create(42);
        Set<Couleur> sorties = EnumSet.noneOf(Couleur.class);
        for (int i = 0; i < 200; i++) {
            sorties.add(TestPersonnalite.resultat(aleatoire, choix));
        }
        // Chaque ex aequo sort, et jamais une autre Couleur
        assertEquals(exAequo, sorties);
    }

    /** Chaque question et chaque réponse a son texte en français et en anglais (G4.4). */
    @Test
    void textesTraduits() throws IOException {
        for (String langue : List.of("fr_fr", "en_us")) {
            JsonObject textes;
            try (InputStream fichier = getClass().getResourceAsStream("/assets/redrising/lang/" + langue + ".json")) {
                assertNotNull(fichier, "Fichier de langue absent : " + langue);
                textes = JsonParser.parseReader(new InputStreamReader(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            for (int question = 0; question < TestPersonnalite.QUESTIONS.size(); question++) {
                assertTrue(textes.has(TestPersonnalite.cleQuestion(question)), langue + " : " + TestPersonnalite.cleQuestion(question));
                for (int reponse = 0; reponse < TestPersonnalite.NB_REPONSES; reponse++) {
                    assertTrue(textes.has(TestPersonnalite.cleReponse(question, reponse)), langue + " : " + TestPersonnalite.cleReponse(question, reponse));
                }
            }
        }
    }

    @Test
    void reponsesInvalidesRefusees() {
        assertThrows(IllegalArgumentException.class, () -> TestPersonnalite.scores(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> TestPersonnalite.scores(0, 0, 0, 0, 0, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> TestPersonnalite.scores(-1, 0, 0, 0, 0, 0, 0));
    }
}
