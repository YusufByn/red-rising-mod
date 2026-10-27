package io.github.yusufbyn.redrising.jalons;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Un jalon (G6.2) : événement mondial à deux issues, canon ou divergente (GDD section 8). Se déclare en quelques lignes,
 * puis s'enregistre avec {@link Jalons#declarer} :
 *
 * <pre>{@code
 * Jalon.builder("triomphe")
 *         .epoque(Epoque.GOLDEN_SON)                 // ne se met en route que pendant cette époque
 *         .apres("entree_institut")                  // jalons déjà passés, quelle que soit leur issue
 *         .echeance(jours(10), jours(5), jours(15), 0.2) // base, minimum, maximum, pente
 *         .issue(bilan -> bilan.rythme() >= 0 ? Issue.CANON : Issue.DIVERGENTE)
 *         .ouvre(Epoque.MORNING_STAR)                // passage d'époque, quelle que soit l'issue
 *         .build();
 * }</pre>
 *
 * <ul>
 *     <li>Jalon fixe (pendaison d'Eo) : {@code .fixe(Issue.CANON)} au lieu de {@code .issue(...)}.</li>
 *     <li>Dépendances : {@code .apres("a", "b")} attend a et b ; {@code .apres("x", Issue.DIVERGENTE)}, seulement si x a
 *     eu cette issue (jalon propre à une branche) ; {@code .apresUnDe("a", "b")}, l'un des deux (branches qui se
 *     rejoignent). Par défaut, les deux issues d'un jalon mènent au même jalon suivant.</li>
 *     <li>Où il se vit : par défaut, il attend un joueur connecté hors de la scène de naissance, quelle que soit sa
 *     dimension ; {@code .surPlace(Level.OVERWORLD)} pour un jalon qui doit être vécu sur place.</li>
 * </ul>
 */
public record Jalon(String id, Epoque epoque, List<Dependance> dependances, Echeance echeance,
                    @Nullable Function<Bilan, Issue> choix, @Nullable Issue fixe, @Nullable Epoque ouvre,
                    @Nullable ResourceKey<Level> surPlace) {
    /**
     * L'un de ces jalons est passé, avec cette issue ({@code null} : n'importe laquelle).
     */
    public record Dependance(Set<String> unDe, @Nullable Issue issue) {
        public boolean satisfaite(Map<String, Issue> passes) {
            return unDe.stream().anyMatch(jalon -> passes.containsKey(jalon) && (issue == null || passes.get(jalon) == issue));
        }
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Toutes ses dépendances sont satisfaites par ces jalons passés. */
    public boolean dependancesSatisfaites(Map<String, Issue> passes) {
        return dependances.stream().allMatch(dependance -> dependance.satisfaite(passes));
    }

    /** L'issue que ce jalon prendrait avec ce bilan (toujours la même pour un jalon fixe). */
    public Issue issue(Bilan bilan) {
        return fixe != null ? fixe : Objects.requireNonNull(Objects.requireNonNull(choix).apply(bilan), "issue nulle pour " + id);
    }

    public static final class Builder {
        private final String id;
        private @Nullable Epoque epoque;
        private final List<Dependance> dependances = new ArrayList<>();
        private @Nullable Echeance echeance;
        private @Nullable Function<Bilan, Issue> choix;
        private @Nullable Issue fixe;
        private @Nullable Epoque ouvre;
        private @Nullable ResourceKey<Level> surPlace;

        private Builder(String id) {
            this.id = id;
        }

        public Builder epoque(Epoque epoque) {
            this.epoque = epoque;
            return this;
        }

        /** Attend que chacun de ces jalons soit passé, quelle que soit son issue. */
        public Builder apres(String... jalons) {
            for (String jalon : jalons) {
                dependances.add(new Dependance(Set.of(jalon), null));
            }
            return this;
        }

        /** Attend que ce jalon soit passé avec cette issue. */
        public Builder apres(String jalon, Issue issue) {
            dependances.add(new Dependance(Set.of(jalon), issue));
            return this;
        }

        /** Attend que l'un de ces jalons soit passé (branches qui se rejoignent). */
        public Builder apresUnDe(String... jalons) {
            dependances.add(new Dependance(Set.of(jalons), null));
            return this;
        }

        /** Délais en ticks de temps de jeu ({@link Echeance#jours}). */
        public Builder echeance(long base, long minimum, long maximum, double pente) {
            this.echeance = new Echeance(base, minimum, maximum, pente);
            return this;
        }

        /** Choix de l'issue au moment du jalon, selon le bilan de la période. */
        public Builder issue(Function<Bilan, Issue> choix) {
            this.choix = choix;
            return this;
        }

        /** Jalon fixe : toujours cette issue. */
        public Builder fixe(Issue issue) {
            this.fixe = issue;
            return this;
        }

        /** Le monde passe à cette époque quand le jalon passe. */
        public Builder ouvre(Epoque epoque) {
            this.ouvre = epoque;
            return this;
        }

        /** Le jalon doit être vécu sur place : il attend un joueur dans cette dimension. */
        public Builder surPlace(ResourceKey<Level> dimension) {
            this.surPlace = dimension;
            return this;
        }

        public Jalon build() {
            if (epoque == null || echeance == null) {
                throw new IllegalStateException("Jalon " + id + " : époque et échéance obligatoires");
            }
            if ((choix == null) == (fixe == null)) {
                throw new IllegalStateException("Jalon " + id + " : soit .issue(...), soit .fixe(...)");
            }
            return new Jalon(id, epoque, List.copyOf(dependances), echeance, choix, fixe, ouvre, surPlace);
        }
    }
}
