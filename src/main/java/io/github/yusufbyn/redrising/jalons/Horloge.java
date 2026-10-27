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
 *     <li><b>presence</b> : temps de jeu multiplié par le nombre de joueurs connectés (ticks-joueur, G6.2). Deux joueurs
 *     pendant une heure comptent deux heures. Sert à rapporter le Soutien au nombre de joueurs ({@code Bilan.rythme}).</li>
 *     <li><b>aide</b> et <b>tort</b> : Soutien Rising cumulé de tous les joueurs, gains d'un côté, pertes de l'autre
 *     (en valeur absolue). Ils ne font que monter ; le net se calcule ({@link #net()}). Une perte puis un regain comptent
 *     chacun de leur côté : le net ne bouge pas, rien n'est compté deux fois.</li>
 * </ul>
 */
public record Horloge(long temps, long presence, long aide, long tort) {
    public static final Horloge ZERO = new Horloge(0, 0, 0, 0);

    public static final Codec<Horloge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("temps").forGetter(Horloge::temps),
            // Absente au schéma v2 : lue à zéro, puis la migration la pose (EtatMonde.migrerV2VersV3)
            Codec.LONG.optionalFieldOf("presence", 0L).forGetter(Horloge::presence),
            Codec.LONG.fieldOf("aide").forGetter(Horloge::aide),
            Codec.LONG.fieldOf("tort").forGetter(Horloge::tort)
    ).apply(instance, Horloge::new));

    public Horloge {
        temps = Math.max(0, temps);
        presence = Math.max(0, presence);
        aide = Math.max(0, aide);
        tort = Math.max(0, tort);
    }

    /** Du temps de jeu a passé, avec ce nombre de joueurs connectés. */
    public Horloge avancer(long ticks, int joueurs) {
        return new Horloge(temps + ticks, presence + ticks * joueurs, aide, tort);
    }

    /** Une variation de Soutien d'un joueur, déjà plafonnée ({@link ContributionDuJour}) : un gain va à l'aide, une perte au tort. */
    public Horloge compter(int soutien) {
        return soutien >= 0 ? new Horloge(temps, presence, aide + soutien, tort) : new Horloge(temps, presence, aide, tort - soutien);
    }

    /** Ce que le Rising a gagné, moins ce qu'il a perdu. */
    public long net() {
        return aide - tort;
    }

    public Horloge avecTemps(long nouveau) {
        return new Horloge(nouveau, presence, aide, tort);
    }

    public Horloge avecPresence(long nouvelle) {
        return new Horloge(temps, nouvelle, aide, tort);
    }

    public Horloge avecAide(long nouvelle) {
        return new Horloge(temps, presence, nouvelle, tort);
    }

    public Horloge avecTort(long nouveau) {
        return new Horloge(temps, presence, aide, nouveau);
    }
}
