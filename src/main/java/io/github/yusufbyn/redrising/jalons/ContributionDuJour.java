package io.github.yusufbyn.redrising.jalons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Ce qu'un joueur a apporté au Soutien cumulé du monde ({@link Horloge}) pendant un jour de jeu (G6.1), pour le plafond :
 * au plus {@link #PLAFOND} d'aide et {@link #PLAFOND} de tort par jour. C'est un filet contre les boucles qu'on n'aurait
 * pas vues, et en multijoueur un seul joueur ne peut ni porter ni écraser le Rising en un jour. Un autre jour, tout
 * repart de zéro. Logique pure (testée).
 */
public record ContributionDuJour(long jour, int aide, int tort) {
    /** 🟡 Aide (ou tort) qu'un joueur peut ajouter au compteur du monde par jour de jeu. */
    public static final int PLAFOND = 30;

    public static final ContributionDuJour AUCUNE = new ContributionDuJour(-1, 0, 0);

    public static final Codec<ContributionDuJour> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("jour").forGetter(ContributionDuJour::jour),
            Codec.INT.fieldOf("aide").forGetter(ContributionDuJour::aide),
            Codec.INT.fieldOf("tort").forGetter(ContributionDuJour::tort)
    ).apply(instance, ContributionDuJour::new));

    /**
     * Résultat de {@link #retenir} : la contribution mise à jour, et la part de la variation qui compte pour le monde
     * (même signe que la variation, réduite par le plafond, 0 s'il est atteint).
     */
    public record Retenue(ContributionDuJour contribution, int compte) {
    }

    /** Aide (ou tort) déjà comptée ce jour-là (0 si on a changé de jour). */
    public int aide(long aujourdhui) {
        return aujourdhui == jour ? aide : 0;
    }

    public int tort(long aujourdhui) {
        return aujourdhui == jour ? tort : 0;
    }

    /** Une variation de Soutien du joueur ce jour-là : ce qui en compte pour le monde, dans la limite du plafond. */
    public Retenue retenir(int soutien, long aujourdhui) {
        int aideDuJour = aide(aujourdhui);
        int tortDuJour = tort(aujourdhui);
        if (soutien >= 0) {
            int compte = Math.min(soutien, PLAFOND - aideDuJour);
            return new Retenue(new ContributionDuJour(aujourdhui, aideDuJour + compte, tortDuJour), compte);
        }
        int compte = Math.min(-soutien, PLAFOND - tortDuJour);
        return new Retenue(new ContributionDuJour(aujourdhui, aideDuJour, tortDuJour + compte), -compte);
    }
}
