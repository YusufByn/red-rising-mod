package io.github.yusufbyn.redrising.jalons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.factions.Variation;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * Ce qu'un jalon donne une fois à chaque joueur (G6.3) : une variation de jauges (pour une Couleur, ou pour tous), ou une
 * nouvelle (le message du jalon). Sauvegardé dans l'état du monde ; ce que chaque joueur a reçu est sur lui.
 * Logique pure (testée).
 *
 * <ul>
 *     <li>Seuls les joueurs arrivés dans le monde avant le jalon sont concernés.</li>
 *     <li>Décidé une seule fois par joueur, à la première livraison où il a une Couleur : concerné, il le reçoit ;
 *     pas concerné, il est marqué reçu sans rien. Changer de Couleur plus tard (Sculpture) ne donne rien après coup.
 *     Un joueur en pleine naissance attend d'avoir sa Couleur.</li>
 * </ul>
 *
 * @param id    identifiant stable ({@code jalon/issue/numéro}, ou {@code jalon/issue/nouvelle})
 * @param pour  la Couleur concernée, ou vide : tous
 * @param temps temps de jeu du passage du jalon
 */
public record Versement(String id, String jalon, Issue issue, Optional<Couleur> pour, Variation variation, boolean nouvelle, long temps) {
    public static final Codec<Versement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(Versement::id),
            Codec.STRING.fieldOf("jalon").forGetter(Versement::jalon),
            Issue.CODEC.fieldOf("issue").forGetter(Versement::issue),
            Couleur.CODEC.optionalFieldOf("pour").forGetter(Versement::pour),
            Variation.CODEC.optionalFieldOf("variation", Variation.AUCUNE).forGetter(Versement::variation),
            Codec.BOOL.optionalFieldOf("nouvelle", false).forGetter(Versement::nouvelle),
            Codec.LONG.fieldOf("temps").forGetter(Versement::temps)
    ).apply(instance, Versement::new));

    /** Ce qu'une livraison fait de ce versement pour un joueur. */
    public enum Decision {
        /** Déjà reçu, ou arrivé après le jalon : rien, jamais. */
        RIEN,
        /** Pas encore de Couleur (en pleine naissance) : on réessaiera. */
        ATTENDRE,
        /** Pas concerné : marqué reçu, sans rien. */
        MARQUER,
        /** Concerné : il le reçoit, et c'est marqué. */
        RECEVOIR
    }

    /**
     * @param arrivee temps de jeu de la première arrivée du joueur dans le monde
     * @param recus   identifiants des versements qu'il a déjà reçus (ou marqués)
     * @param couleur sa Couleur, ou {@code null} s'il n'est pas encore né
     */
    public Decision decider(long arrivee, Set<String> recus, @Nullable Couleur couleur) {
        if (recus.contains(id) || arrivee > temps) {
            return Decision.RIEN;
        }
        if (couleur == null) {
            return Decision.ATTENDRE;
        }
        return pour.isEmpty() || pour.get() == couleur ? Decision.RECEVOIR : Decision.MARQUER;
    }
}
