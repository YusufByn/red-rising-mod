package io.github.yusufbyn.redrising.jalons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Horloge du monde (G6.1), sauvegardée dans l'état du monde : ce qui fera passer les époques et pèsera sur les jalons (G6.2).
 * Logique pure (testée).
 *
 * <ul>
 *     <li><b>temps</b> : ticks de jeu écoulés pendant qu'au moins un joueur était connecté. Ni le sommeil ni
 *     {@code /time set} ne le font sauter, et il ne bouge pas sur un serveur sans joueur.</li>
 *     <li><b>aide</b> et <b>tort</b> : Soutien Rising cumulé de tous les joueurs, gains d'un côté, pertes de l'autre
 *     (en valeur absolue). Ils ne font que monter ; le net se calcule ({@link #net()}). Une perte puis un regain comptent
 *     chacun de leur côté : le net ne bouge pas, rien n'est compté deux fois.</li>
 * </ul>
 */
public record Horloge(long temps, long aide, long tort) {
    public static final Horloge ZERO = new Horloge(0, 0, 0);

    public static final Codec<Horloge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("temps").forGetter(Horloge::temps),
            Codec.LONG.fieldOf("aide").forGetter(Horloge::aide),
            Codec.LONG.fieldOf("tort").forGetter(Horloge::tort)
    ).apply(instance, Horloge::new));

    public Horloge {
        temps = Math.max(0, temps);
        aide = Math.max(0, aide);
        tort = Math.max(0, tort);
    }

    /** Du temps de jeu a passé. */
    public Horloge avancer(long ticks) {
        return new Horloge(temps + ticks, aide, tort);
    }

    /** Une variation de Soutien d'un joueur, déjà plafonnée ({@link ContributionDuJour}) : un gain va à l'aide, une perte au tort. */
    public Horloge compter(int soutien) {
        return soutien >= 0 ? new Horloge(temps, aide + soutien, tort) : new Horloge(temps, aide, tort - soutien);
    }

    /** Ce que le Rising a gagné, moins ce qu'il a perdu. */
    public long net() {
        return aide - tort;
    }

    public Horloge avecTemps(long nouveau) {
        return new Horloge(nouveau, aide, tort);
    }

    public Horloge avecAide(long nouvelle) {
        return new Horloge(temps, nouvelle, tort);
    }

    public Horloge avecTort(long nouveau) {
        return new Horloge(temps, aide, nouveau);
    }
}
