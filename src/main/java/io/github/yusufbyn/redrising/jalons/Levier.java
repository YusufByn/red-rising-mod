package io.github.yusufbyn.redrising.jalons;

import com.mojang.serialization.Codec;
import io.github.yusufbyn.redrising.couleur.Couleur;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * Ce qu'un jalon peut changer dans le monde (G6.3) : un état qu'on pose, avec un niveau (0 : rien). Le code concerné
 * lit le niveau en cours ({@code EffetsMonde.niveau}). Ce que veut dire chaque niveau est écrit à un seul endroit,
 * près du code qui le lit.
 */
public enum Levier implements StringRepresentable {
    /** Patrouilles plus vigilantes : détection, attente entre deux contrôles, seuil de contrôle ({@code Controle}, {@code Acces}). Niveaux 1 et 2. */
    ALERTE_PATROUILLES("alerte_patrouilles", 2, null),
    /** Les Rouges ne sont plus témoins pour la Société des actions contre elle ({@code ActionsFaction.temoin}). */
    SILENCE_ROUGE("silence_rouge", 1, Couleur.ROUGE);

    public static final Codec<Levier> CODEC = StringRepresentable.fromEnum(Levier::values);

    private final String id;
    private final int niveauMax;
    private final @Nullable Couleur silence;

    Levier(String id, int niveauMax, @Nullable Couleur silence) {
        this.id = id;
        this.niveauMax = niveauMax;
        this.silence = silence;
    }

    public int niveauMax() {
        return niveauMax;
    }

    /** Pour un levier de silence : la Couleur qui se tait. */
    public @Nullable Couleur silence() {
        return silence;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
