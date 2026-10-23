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

/**
 * Ce qu'une Couleur a de propre : capacités passives (G3.1), restrictions d'accès (G3.2) et capacité active (G3.3).
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
 *         .build();
 * }</pre>
 *
 * <p>Opérations : {@code ADD_VALUE} ajoute une valeur fixe, {@code ADD_MULTIPLIED_BASE} ajoute un pourcentage
 * de la valeur de base (0.2 = +20 %), {@code ADD_MULTIPLIED_TOTAL} multiplie le total.
 * Une valeur négative donne un malus (les faiblesses complètes arrivent en G3.4).</p>
 *
 * <p>Restrictions : un bloc interdit ne s'utilise pas (clic droit), un objet interdit ne s'utilise pas
 * (ni en l'air, ni sur un bloc), un craft interdit ne donne pas de résultat.</p>
 *
 * @param blocsInterdits  blocs que la Couleur ne peut pas utiliser
 * @param objetsInterdits objets que la Couleur ne peut pas utiliser
 * @param craftsInterdits objets que la Couleur ne peut pas fabriquer
 * @param capacite        capacité de la touche d'action, ou {@code null} si la Couleur n'en a pas
 */
public record ProfilCouleur(List<BonusAttribut> attributs, List<EffetPermanent> effets,
                            Set<Block> blocsInterdits, Set<Item> objetsInterdits, Set<Item> craftsInterdits,
                            @Nullable CapaciteActive capacite) {
    /** Profil sans capacité ni restriction : Couleur pas encore écrite, ou joueur pas encore né. */
    public static final ProfilCouleur VIDE = new ProfilCouleur(List.of(), List.of(), Set.of(), Set.of(), Set.of(), null);

    public ProfilCouleur {
        attributs = List.copyOf(attributs);
        effets = List.copyOf(effets);
        blocsInterdits = Set.copyOf(blocsInterdits);
        objetsInterdits = Set.copyOf(objetsInterdits);
        craftsInterdits = Set.copyOf(craftsInterdits);
    }

    public record BonusAttribut(Holder<Attribute> attribut, double valeur, AttributeModifier.Operation operation) {
    }

    /** @param niveau 0 pour le niveau I, 1 pour le niveau II... */
    public record EffetPermanent(Holder<MobEffect> effet, int niveau) {
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

        private Builder() {
        }

        public Builder attribut(Holder<Attribute> attribut, double valeur, AttributeModifier.Operation operation) {
            for (BonusAttribut existant : attributs) {
                if (existant.attribut().equals(attribut) && existant.operation() == operation) {
                    throw new IllegalArgumentException("Bonus déjà déclaré pour " + attribut.getRegisteredName() + " (" + operation + ")");
                }
            }
            attributs.add(new BonusAttribut(attribut, valeur, operation));
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
            return new ProfilCouleur(attributs, effets, blocsInterdits, objetsInterdits, craftsInterdits, capacite);
        }
    }
}
