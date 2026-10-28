package io.github.yusufbyn.redrising.jalons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.yusufbyn.redrising.factions.Camp;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * L'attitude d'un personnage canon envers un joueur (G6.5, GDD section 8) : de {@value #MINIMUM} à {@value #MAXIMUM},
 * 0 au départ. Elle est dans l'état du monde, et pèse sur l'issue des jalons par {@link Bilan#attitude}. Logique pure
 * (testée) ; le côté serveur est {@code pnj/AttitudesPnj}. Valeurs en 🟡 dans le GDD : à régler en jeu, toutes ici.
 *
 * <p>Pas de boucle : les gains ne dépassent pas {@value #PLAFOND_GAINS_PAR_JOUR} par jour de jeu (horloge de
 * l'Overworld, comme les autres règles « une fois par jour ») ; les pertes ne sont pas plafonnées, elles ne s'exploitent
 * pas. L'attitude ne passe pas par les jauges : elle ne nourrit ni l'aide ni le tort de l'horloge du monde.</p>
 *
 * @param valeur      l'attitude
 * @param jour        le jour de jeu des derniers gains
 * @param gainsDuJour ce qui a été gagné ce jour-là
 */
public record Attitude(int valeur, long jour, int gainsDuJour) {
    public static final int MINIMUM = -100;
    public static final int MAXIMUM = 100;
    /** 🟡 Gains au plus par jour de jeu, pour un personnage et un joueur. */
    public static final int PLAFOND_GAINS_PAR_JOUR = 10;

    /** 🟡 Le joueur l'a aidé (nourriture, une fois par jour : la règle d'aide de G5.2). */
    public static final int AIDE = 5;
    /** 🟡 Le joueur l'a mis à terre (essentiel), à chaque fois. */
    public static final int KO = -20;
    /** 🟡 Il a vu le joueur voler ou dénoncer son camp. */
    public static final int VU_CONTRE = -5;
    /** 🟡 Il a vu le joueur tuer (ou mettre à terre) quelqu'un de son camp. */
    public static final int VU_VIOLENCE = -10;
    /** 🟡 Il a vu le joueur s'en prendre au camp adverse. */
    public static final int VU_POUR = 3;
    /** 🟡 Le joueur a dénoncé sa cellule (il y était, ou elle était son foyer), même s'il ne l'a pas vu. */
    public static final int CELLULE_DENONCEE = -30;
    /** 🟡 Le joueur a mené les Gris à sa cellule, tombée à la descente. */
    public static final int CELLULE_REPEREE = -10;

    /** Pas encore croisé : 0, aucun gain. */
    public static final Attitude NEUTRE = new Attitude(0, -1, 0);

    public static final Codec<Attitude> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("valeur").forGetter(Attitude::valeur),
            Codec.LONG.fieldOf("jour").forGetter(Attitude::jour),
            Codec.INT.fieldOf("gains_du_jour").forGetter(Attitude::gainsDuJour)
    ).apply(instance, Attitude::new));

    /** Ce qu'un personnage voit faire au joueur. */
    public enum Acte {
        VOL,
        /** Tuer, ou mettre à terre un essentiel. */
        VIOLENCE,
        DENONCIATION
    }

    /**
     * L'attitude après ce changement, pendant ce jour de jeu : gains plafonnés pour la journée, pertes entières, toujours
     * entre {@value #MINIMUM} et {@value #MAXIMUM}.
     */
    public Attitude changer(int variation, long jourDeJeu) {
        int gains = jourDeJeu == jour ? gainsDuJour : 0;
        if (variation > 0) {
            int accorde = Math.min(variation, Math.max(0, PLAFOND_GAINS_PAR_JOUR - gains));
            int nouvelle = borner(valeur + accorde);
            return new Attitude(nouvelle, jourDeJeu, gains + (nouvelle - valeur));
        }
        return new Attitude(borner(valeur + variation), jourDeJeu, gains);
    }

    /** Debug : cette valeur, bornée, sans toucher au plafond du jour. */
    public Attitude fixer(int nouvelle) {
        return new Attitude(borner(nouvelle), jour, gainsDuJour);
    }

    /**
     * Ce que change une action vue par un personnage de ce camp. Sans camp (ou une action contre personne) : rien.
     *
     * @param lese le camp contre qui le joueur a agi
     */
    public static int vu(Acte acte, @Nullable Camp lese, @Nullable Camp camp) {
        if (lese == null || camp == null) {
            return 0;
        }
        if (lese != camp) {
            return VU_POUR;
        }
        return acte == Acte.VIOLENCE ? VU_VIOLENCE : VU_CONTRE;
    }

    /**
     * L'attitude d'un personnage pour le monde : la moyenne des joueurs qui l'ont croisé (qui ont une attitude envers lui),
     * 0 sinon. Le nombre de joueurs ne compte pas, comme pour le rythme (G6.2).
     */
    public static double moyenne(Collection<Attitude> attitudes) {
        return attitudes.stream().mapToInt(Attitude::valeur).average().orElse(0);
    }

    private static int borner(int valeur) {
        return Math.clamp(valeur, MINIMUM, MAXIMUM);
    }
}
