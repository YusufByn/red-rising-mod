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
 * Les textes des questions viendront avec l'interface ; ici, seules comptent les paires de Couleurs.</p>
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
            // 1. Une tempête approche : renforcer l'abri, donner les ordres, soigner, étudier d'où elle vient
            question(r(ORANGE, ROUGE), r(OR, GRIS), r(JAUNE, MARRON), r(VERT, BLEU)),
            // 2. Un seul outil pour toute ta vie : lame, clé à molette, pinceau, bourse pleine
            question(r(OBSIDIAN, OR), r(ORANGE, VERT), r(VIOLET, BLANC), r(ARGENT, CUIVRE)),
            // 3. Quelqu'un t'a trahi : frapper, faire juger, ruiner en secret, partir
            question(r(OBSIDIAN, ROUGE), r(BLANC, CUIVRE), r(ARGENT, ROSE), r(BLEU, VIOLET)),
            // 4. Où te sens-tu le mieux : sous terre, dans le ciel, dans la foule, dans le silence
            question(r(ROUGE, ORANGE), r(BLEU, OR), r(ROSE, ARGENT), r(JAUNE, BLANC)),
            // 5. Que dira-t-on de toi à ta mort : jamais reculé, sauvé des vies, construit, changé le monde
            question(r(OBSIDIAN, GRIS), r(JAUNE, MARRON), r(VERT, VIOLET), r(OR, ROSE)),
            // 6. Un ordre injuste : obéir, obéir en apparence, refuser en face, négocier
            question(r(GRIS, CUIVRE), r(ROSE, VERT), r(OBSIDIAN, ROUGE), r(ARGENT, MARRON)),
            // 7. Une nuit sans sommeil : réparer, faire sa ronde, regarder les étoiles, veiller ou prier
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
