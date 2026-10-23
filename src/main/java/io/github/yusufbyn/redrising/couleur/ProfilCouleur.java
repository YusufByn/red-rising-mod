package io.github.yusufbyn.redrising.couleur;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Ce qu'une Couleur a de propre : capacités passives (G3.1), restrictions d'accès (G3.2), capacité active (G3.3)
 * et faiblesses (G3.4).
 * Se déclare en quelques lignes, puis s'enregistre avec {@link ProfilsCouleurs#declarer} :
 *
 * <pre>{@code
 * ProfilCouleur.builder()
 *         .attribut(Attributes.BLOCK_BREAK_SPEED, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE) // minage +50 %
 *         .attribut(Attributes.MAX_HEALTH, -4, AttributeModifier.Operation.ADD_VALUE)                   // 2 cœurs de moins
 *         .effet(MobEffects.NIGHT_VISION)
 *         .interditBloc(Blocks.ENCHANTING_TABLE)  // ne peut pas enchanter
 *         .interditObjet(Items.WRITTEN_BOOK)      // ne peut pas lire un livre
 *         .interditCraft(Items.BOOK)              // ne peut pas fabriquer de livre
 *         .capacite(20 * 30, joueur -> { ...; return true; }) // touche d'action, recharge 30 s
 *         .si(Condition.A_CIEL_OUVERT, malus -> malus                        // seulement à ciel ouvert
 *                 .attribut(Attributes.MOVEMENT_SPEED, -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
 *                 .effet(MobEffects.WEAKNESS))
 *         .build();
 * }</pre>
 *
 * <p>Opérations : {@code ADD_VALUE} ajoute une valeur fixe, {@code ADD_MULTIPLIED_BASE} ajoute un pourcentage
 * de la valeur de base (0.2 = +20 %), {@code ADD_MULTIPLIED_TOTAL} multiplie le total.
 * Une valeur négative donne un malus : c'est ainsi que s'écrivent les faiblesses permanentes (fragilité :
 * vie max en moins ; lenteur : vitesse en moins). Les faiblesses liées à l'environnement passent par {@code si}.</p>
 *
 * <p>Restrictions : un bloc interdit ne s'utilise pas (clic droit), un objet interdit ne s'utilise pas
 * (ni en l'air, ni sur un bloc), un craft interdit ne donne pas de résultat.</p>
 *
 * @param blocsInterdits  blocs que la Couleur ne peut pas utiliser
 * @param objetsInterdits objets que la Couleur ne peut pas utiliser
 * @param craftsInterdits objets que la Couleur ne peut pas fabriquer
 * @param capacite        capacité de la touche d'action, ou {@code null} si la Couleur n'en a pas
 * @param conditionnels   bonus et malus qui ne s'appliquent que quand leur condition est vraie
 */
public record ProfilCouleur(List<BonusAttribut> attributs, List<EffetPermanent> effets,
                            Set<Block> blocsInterdits, Set<Item> objetsInterdits, Set<Item> craftsInterdits,
                            @Nullable CapaciteActive capacite, List<BonusConditionnel> conditionnels) {
    /** Profil sans capacité ni restriction : Couleur pas encore écrite, ou joueur pas encore né. */
    public static final ProfilCouleur VIDE = new ProfilCouleur(List.of(), List.of(), Set.of(), Set.of(), Set.of(), null, List.of());

    public ProfilCouleur {
        attributs = List.copyOf(attributs);
        effets = List.copyOf(effets);
        blocsInterdits = Set.copyOf(blocsInterdits);
        objetsInterdits = Set.copyOf(objetsInterdits);
        craftsInterdits = Set.copyOf(craftsInterdits);
        conditionnels = List.copyOf(conditionnels);
    }

    public record BonusAttribut(Holder<Attribute> attribut, double valeur, AttributeModifier.Operation operation) {
    }

    /** @param niveau 0 pour le niveau I, 1 pour le niveau II... */
    public record EffetPermanent(Holder<MobEffect> effet, int niveau) {
    }

    /** Bonus ou malus actifs seulement quand {@code condition} est vraie (ex. malus à ciel ouvert). */
    public record BonusConditionnel(Condition condition, List<BonusAttribut> attributs, List<EffetPermanent> effets) {
        public BonusConditionnel {
            attributs = List.copyOf(attributs);
            effets = List.copyOf(effets);
        }

        public static final class Builder {
            private final List<BonusAttribut> attributs = new ArrayList<>();
            private final List<EffetPermanent> effets = new ArrayList<>();

            private Builder() {
            }

            public Builder attribut(Holder<Attribute> attribut, double valeur, AttributeModifier.Operation operation) {
                ajouterSansDoublon(attributs, new BonusAttribut(attribut, valeur, operation));
                return this;
            }

            public Builder effet(Holder<MobEffect> effet) {
                return effet(effet, 0);
            }

            public Builder effet(Holder<MobEffect> effet, int niveau) {
                effets.add(new EffetPermanent(effet, niveau));
                return this;
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<BonusAttribut> attributs = new ArrayList<>();
        private final List<EffetPermanent> effets = new ArrayList<>();
        private final Set<Block> blocsInterdits = new HashSet<>();
        private final Set<Item> objetsInterdits = new HashSet<>();
        private final Set<Item> craftsInterdits = new HashSet<>();
        private @Nullable CapaciteActive capacite;
        private final List<BonusConditionnel> conditionnels = new ArrayList<>();

        private Builder() {
        }

        public Builder attribut(Holder<Attribute> attribut, double valeur, AttributeModifier.Operation operation) {
            ajouterSansDoublon(attributs, new BonusAttribut(attribut, valeur, operation));
            return this;
        }

        /** Bonus ou malus qui ne valent que quand {@code condition} est vraie, réévaluée deux fois par seconde. */
        public Builder si(Condition condition, Consumer<BonusConditionnel.Builder> contenu) {
            BonusConditionnel.Builder bloc = new BonusConditionnel.Builder();
            contenu.accept(bloc);
            conditionnels.add(new BonusConditionnel(condition, bloc.attributs, bloc.effets));
            return this;
        }

        public Builder effet(Holder<MobEffect> effet) {
            return effet(effet, 0);
        }

        public Builder effet(Holder<MobEffect> effet, int niveau) {
            effets.add(new EffetPermanent(effet, niveau));
            return this;
        }

        public Builder interditBloc(Block... blocs) {
            blocsInterdits.addAll(List.of(blocs));
            return this;
        }

        public Builder interditObjet(Item... objets) {
            objetsInterdits.addAll(List.of(objets));
            return this;
        }

        public Builder interditCraft(Item... resultats) {
            craftsInterdits.addAll(List.of(resultats));
            return this;
        }

        /** Capacité de la touche d'action ; {@code recharge} en ticks (20 = 1 seconde). Une seule par Couleur. */
        public Builder capacite(int recharge, CapaciteActive.Action action) {
            if (capacite != null) {
                throw new IllegalStateException("Capacité active déjà déclarée");
            }
            capacite = new CapaciteActive(recharge, action);
            return this;
        }

        public ProfilCouleur build() {
            return new ProfilCouleur(attributs, effets, blocsInterdits, objetsInterdits, craftsInterdits, capacite, conditionnels);
        }
    }

    private static void ajouterSansDoublon(List<BonusAttribut> attributs, BonusAttribut nouveau) {
        for (BonusAttribut existant : attributs) {
            if (existant.attribut().equals(nouveau.attribut()) && existant.operation() == nouveau.operation()) {
                throw new IllegalArgumentException("Bonus déjà déclaré pour " + nouveau.attribut().getRegisteredName() + " (" + nouveau.operation() + ")");
            }
        }
        attributs.add(nouveau);
    }
}
