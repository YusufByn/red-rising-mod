package io.github.yusufbyn.redrising.couleur;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Capacités passives d'une Couleur (G3.1) : bonus d'attributs et effets permanents.
 * Se déclare en quelques lignes, puis s'enregistre avec {@link ProfilsCouleurs#declarer} :
 *
 * <pre>{@code
 * ProfilCouleur.builder()
 *         .attribut(Attributes.BLOCK_BREAK_SPEED, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE) // minage +50 %
 *         .attribut(Attributes.MAX_HEALTH, -4, AttributeModifier.Operation.ADD_VALUE)                   // 2 cœurs de moins
 *         .effet(MobEffects.NIGHT_VISION)
 *         .build();
 * }</pre>
 *
 * <p>Opérations : {@code ADD_VALUE} ajoute une valeur fixe, {@code ADD_MULTIPLIED_BASE} ajoute un pourcentage
 * de la valeur de base (0.2 = +20 %), {@code ADD_MULTIPLIED_TOTAL} multiplie le total.
 * Une valeur négative donne un malus (les faiblesses complètes arrivent en G3.4).</p>
 */
public record ProfilCouleur(List<BonusAttribut> attributs, List<EffetPermanent> effets) {
    /** Profil sans aucune capacité : Couleur pas encore écrite, ou joueur pas encore né. */
    public static final ProfilCouleur VIDE = new ProfilCouleur(List.of(), List.of());

    public ProfilCouleur {
        attributs = List.copyOf(attributs);
        effets = List.copyOf(effets);
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

        public ProfilCouleur build() {
            return new ProfilCouleur(attributs, effets);
        }
    }
}
