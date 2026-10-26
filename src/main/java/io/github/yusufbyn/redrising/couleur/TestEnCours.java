package io.github.yusufbyn.redrising.couleur;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Où en est un joueur dans son test de naissance (G4.4), tant qu'il n'a pas accepté sa Couleur.
 *
 * <p>Gardé sur le joueur (sauvegardé) pour qu'on ne puisse pas tricher en se déconnectant : on retrouve son résultat
 * en revenant, et la relance déjà utilisée ne revient pas.</p>
 *
 * @param resultat        Couleur obtenue, en attente d'acceptation ; {@code null} pendant les questions
 * @param relanceUtilisee le joueur a déjà refait le test une fois
 */
public record TestEnCours(@Nullable Couleur resultat, boolean relanceUtilisee) {
    public static final TestEnCours DEBUT = new TestEnCours(null, false);

    /** Sauvegarde sous la forme {resultat: "rouge", relance_utilisee: false} ; pas de « resultat » pendant les questions. */
    public static final Codec<TestEnCours> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Couleur.CODEC.optionalFieldOf("resultat").forGetter(test -> Optional.ofNullable(test.resultat())),
            Codec.BOOL.fieldOf("relance_utilisee").forGetter(TestEnCours::relanceUtilisee)
    ).apply(instance, (resultat, relanceUtilisee) -> new TestEnCours(resultat.orElse(null), relanceUtilisee)));
}
