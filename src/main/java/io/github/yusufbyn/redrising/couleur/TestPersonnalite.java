package io.github.yusufbyn.redrising.couleur;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static io.github.yusufbyn.redrising.couleur.Couleur.*;

/**
 * Moteur du test de naissance (G4.3, GDD section 3) : 7 questions, 4 réponses chacune,
 * chaque réponse donne 1 point caché à chacune de ses 2 Couleurs. La Couleur qui a le plus de points gagne,
 * une égalité est départagée au hasard.
 *
 * <p>Logique pure, sans joueur ni monde : l'interface (G4.4) envoie les réponses, le serveur appelle {@link #resultat}.
 * Les textes des questions sont dans les fichiers de langue ({@link #cleQuestion}, {@link #cleReponse}) ;
 * ici, seules comptent les paires de Couleurs.</p>
 *
 * <p>⚠️ Les paires ne se modifient pas à la légère : le test unitaire {@code TestPersonnaliteTest} vérifie que
 * chaque Couleur sort entre 6,8 % et 7,5 % des cas. Il remplace le script Python du GDD.</p>
 */
public final class TestPersonnalite {
    /** Une réponse : le point caché va à ces deux Couleurs. */
    public record Reponse(Couleur premiere, Couleur seconde) {
    }

    public record Question(List<Reponse> reponses) {
    }

    public static final int NB_REPONSES = 4;

    /** Dans l'ordre du GDD ; les réponses aussi (le joueur ne voit pas les Couleurs). */
    public static final List<Question> QUESTIONS = List.of(
            // 1. Tempête de poussière sur la colonie : étayer les murs, dire à chacun où se tenir, s'occuper des blessés, comprendre d'où elle vient
            question(r(ORANGE, ROUGE), r(OR, GRIS), r(JAUNE, MARRON), r(VERT, BLEU)),
            // 2. Un seul objet pour toute ta vie : lame, clé à molette, pinceau, bourse pleine
            question(r(OBSIDIAN, OR), r(ORANGE, VERT), r(VIOLET, BLANC), r(ARGENT, CUIVRE)),
            // 3. Trahison : frapper tout de suite, exiger un jugement, ruiner sans laisser de trace, partir loin
            question(r(OBSIDIAN, ROUGE), r(BLANC, CUIVRE), r(ARGENT, ROSE), r(BLEU, VIOLET)),
            // 4. Où te sens-tu chez toi : sous terre, dans le ciel, dans la foule d'une cité, dans le silence d'un temple
            question(r(ROUGE, ORANGE), r(BLEU, OR), r(ROSE, ARGENT), r(JAUNE, BLANC)),
            // 5. Épitaphe : ne recula jamais, sauva bien des vies, bâtit ce qui dure, changea le monde
            question(r(OBSIDIAN, GRIS), r(JAUNE, MARRON), r(VERT, VIOLET), r(OR, ROSE)),
            // 6. Ordre injuste d'un supérieur : obéir, faire mine d'obéir, refuser en face, négocier
            question(r(GRIS, CUIVRE), r(ROSE, VERT), r(OBSIDIAN, ROUGE), r(ARGENT, MARRON)),
            // 7. Nuit sans sommeil : réparer, faire sa ronde, regarder les étoiles, veiller un malade ou prier
            question(r(ORANGE, MARRON), r(CUIVRE, GRIS), r(BLEU, VIOLET), r(JAUNE, BLANC))
    );

    private TestPersonnalite() {
    }

    /**
     * Points cachés de chaque Couleur, à partir de la réponse choisie à chaque question
     * ({@code choix[i]} : numéro de la réponse à la question i, de 0 à 3). Les Couleurs sans point valent 0.
     *
     * @throws IllegalArgumentException si les réponses ne correspondent pas aux questions
     *         (elles viendront du client, qui peut être modifié)
     */
    public static Map<Couleur, Integer> scores(int... choix) {
        if (choix.length != QUESTIONS.size()) {
            throw new IllegalArgumentException(choix.length + " réponses pour " + QUESTIONS.size() + " questions");
        }
        Map<Couleur, Integer> scores = new EnumMap<>(Couleur.class);
        for (Couleur couleur : Couleur.values()) {
            scores.put(couleur, 0);
        }
        for (int i = 0; i < choix.length; i++) {
            if (choix[i] < 0 || choix[i] >= NB_REPONSES) {
                throw new IllegalArgumentException("Réponse " + choix[i] + " à la question " + (i + 1) + " (de 0 à " + (NB_REPONSES - 1) + ")");
            }
            Reponse reponse = QUESTIONS.get(i).reponses().get(choix[i]);
            scores.merge(reponse.premiere(), 1, Integer::sum);
            scores.merge(reponse.seconde(), 1, Integer::sum);
        }
        return scores;
    }

    /** Les Couleurs qui ont le plus de points (plusieurs en cas d'égalité), dans l'ordre de l'enum. */
    public static List<Couleur> enTete(Map<Couleur, Integer> scores) {
        int meilleur = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Couleur> enTete = new ArrayList<>();
        scores.forEach((couleur, points) -> {
            if (points == meilleur) {
                enTete.add(couleur);
            }
        });
        return enTete;
    }

    /** Clé de traduction de la question i (à partir de 0), ex. {@code naissance.redrising.test.q1}. */
    public static String cleQuestion(int question) {
        return "naissance.redrising.test.q" + (question + 1);
    }

    /** Clé de traduction d'une réponse, ex. {@code naissance.redrising.test.q1.r4}. */
    public static String cleReponse(int question, int reponse) {
        return cleQuestion(question) + ".r" + (reponse + 1);
    }

    /** La Couleur de naissance : celle qui a le plus de points, tirée au hasard parmi les ex aequo. */
    public static Couleur resultat(RandomSource aleatoire, int... choix) {
        List<Couleur> enTete = enTete(scores(choix));
        return enTete.get(aleatoire.nextInt(enTete.size()));
    }

    private static Question question(Reponse... reponses) {
        return new Question(List.of(reponses));
    }

    private static Reponse r(Couleur premiere, Couleur seconde) {
        return new Reponse(premiere, seconde);
    }
}
