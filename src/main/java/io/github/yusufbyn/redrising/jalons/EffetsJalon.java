package io.github.yusufbyn.redrising.jalons;

import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.factions.Variation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Les effets d'une issue d'un jalon (G6.3), déclarés avec lui :
 *
 * <pre>{@code
 * .effets(Issue.CANON, effets -> effets
 *         .levier(Levier.ALERTE_PATROUILLES, 1, jours(5))   // 5 jours de temps de jeu
 *         .levier(Levier.SILENCE_ROUGE, 1)                  // permanent
 *         .jauges(Couleur.ROUGE, new Variation(0, 10, 0)))  // chaque Rouge, une fois
 * }</pre>
 *
 * <p>Ce sont des états qu'on pose (un rejeu ne double rien) et des versements donnés une fois à chaque joueur.
 * Rien ne nourrit les compteurs d'aide et de tort (G6.1).</p>
 */
public record EffetsJalon(List<LevierDeclare> leviers, List<VersementDeclare> versements) {
    public static final EffetsJalon AUCUN = new EffetsJalon(List.of(), List.of());

    /** @param duree en temps de jeu, ou {@link EffetEnCours#PERMANENT} */
    public record LevierDeclare(Levier levier, int niveau, long duree) {
    }

    /** @param pour la Couleur concernée, ou {@code null} : tous les joueurs */
    public record VersementDeclare(@Nullable Couleur pour, Variation variation) {
    }

    public static final class Builder {
        private final List<LevierDeclare> leviers = new ArrayList<>();
        private final List<VersementDeclare> versements = new ArrayList<>();

        /** Un levier posé pour un temps de jeu ({@link Echeance#jours}). */
        public Builder levier(Levier levier, int niveau, long duree) {
            leviers.add(new LevierDeclare(levier, niveau, duree));
            return this;
        }

        /** Un levier posé pour toujours. */
        public Builder levier(Levier levier, int niveau) {
            return levier(levier, niveau, EffetEnCours.PERMANENT);
        }

        /** Une variation de jauges, une fois, à chaque joueur de cette Couleur. */
        public Builder jauges(Couleur pour, Variation variation) {
            versements.add(new VersementDeclare(pour, variation));
            return this;
        }

        /** Une variation de jauges, une fois, à chaque joueur. */
        public Builder jauges(Variation variation) {
            versements.add(new VersementDeclare(null, variation));
            return this;
        }

        EffetsJalon build() {
            return new EffetsJalon(List.copyOf(leviers), List.copyOf(versements));
        }
    }
}
