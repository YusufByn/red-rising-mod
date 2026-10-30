package io.github.yusufbyn.redrising.factions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Une traque en cours (G5.6) : la Suspicion a atteint le maximum, des Gris chassent le joueur. Logique pure, testée par
 * {@code TraqueTest} ; les chasseurs sont menés par {@code pnj/TraqueGris}. Toutes les valeurs en 🟡 sont ici.
 *
 * <p>Les durées ne comptent que quand le joueur est connecté : se déconnecter met la traque en pause.</p>
 *
 * @param duree     temps de traque écoulé, en ticks
 * @param horsDeVue depuis combien de ticks aucun chasseur ne voit le joueur
 * @param tues      chasseurs tués par le joueur (ils ne sont remplacés que par les renforts)
 */
public record Traque(long duree, long horsDeVue, int tues) {
    /** Semer les Gris : une minute de suite sans qu'aucun chasseur ne voie le joueur. */
    public static final int DUREE_SEMER = 1200;
    /** Au bout de 10 minutes, les chasseurs abandonnent : victoire si aucun ne voit le joueur, sinon simple retrait. */
    public static final int DUREE_MAX = 12000;
    public static final int CHASSEURS_DEPART = 3;
    /** Un renfort par minute, jusqu'à 6 chasseurs. */
    public static final int RENFORT = 1200;
    public static final int CHASSEURS_MAX = 6;
    /** Les chasseurs apparaissent à cette distance du joueur, hors de sa vue si possible. */
    public static final double APPARITION_MIN = 24;
    public static final double APPARITION_MAX = 32;
    /** Les Gris en patrouille à cette distance rejoignent la traque. */
    public static final double RALLIEMENT_PATROUILLES = 48;
    /** Un chasseur voit le joueur à cette distance, s'il a une ligne de vue. */
    public static final double VUE = 48;
    /** Tir étourdissant : portée, une flèche toutes les 2 secondes par chasseur, ralentissement de 2 secondes. */
    public static final double PORTEE_TIR = 20;
    public static final int CADENCE_TIR = 40;
    public static final int ETOURDISSEMENT = 40;
    /**
     * Après chaque ralentissement, 3 secondes sans pouvoir être ralenti à nouveau (G7.2.4) : de quoi atteindre un carrefour
     * et casser la vue, même dans une galerie droite. Partout, pas seulement dans la mine.
     */
    public static final int IMMUNITE_ETOURDISSEMENT = 60;
    /** Dans la mine (G7.2.4) : les chasseurs apparaissent aux points d'entrée des Gris, à cette distance du joueur. */
    public static final double APPARITION_MINE_MIN = 24;
    public static final double APPARITION_MINE_MAX = 64;
    /** Parmi les points d'entrée qui conviennent, le chasseur prend un des 3 plus proches, au hasard. */
    public static final int CHOIX_ENTREES = 3;
    /** Un point d'entrée pas encore chargé (le joueur vient d'arriver) : on l'attend au plus 5 secondes avant le repli. */
    public static final int ATTENTE_POINTS = 100;
    /** Aucun chasseur sur le niveau du joueur pendant 20 secondes : ceux des autres niveaux le rejoignent (par les puits). */
    public static final int RECALAGE = 400;
    /** Arrestation : un chasseur à moins de 2,5 blocs pendant 3 secondes de suite. Ni fuite ni abandon. */
    public static final Controle.Regles ARRESTATION = new Controle.Regles(Double.POSITIVE_INFINITY, 2.5, 60, Integer.MAX_VALUE, true);
    /** L'arrêté est emmené au poste de la Société le plus proche dans ce rayon, sinon interrogé sur place. */
    public static final double RAYON_POSTE = 500;
    public static final int DUREE_INTERROGATOIRE = 600;
    /** Suspicion après la traque : gagnée (on reste recherché), retrait des Gris (ils te voyaient encore), arrestation (dette payée). */
    public static final int SUSPICION_GAGNEE = 70;
    public static final int SUSPICION_RETRAIT = 90;
    public static final int SUSPICION_ARRETE = 50;

    public static final Traque DEBUT = new Traque(0, 0, 0);

    public static final Codec<Traque> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("duree").forGetter(Traque::duree),
            Codec.LONG.fieldOf("hors_de_vue").forGetter(Traque::horsDeVue),
            Codec.INT.fieldOf("tues").forGetter(Traque::tues)
    ).apply(instance, Traque::new));

    public enum Issue {
        EN_COURS,
        /** Gris semés (ou abandon sans être vu). */
        GAGNEE,
        /** Abandon alors qu'un chasseur voit encore le joueur. */
        RETRAIT
    }

    /** {@code delta} ticks ont passé ; {@code vu} : un chasseur voit le joueur. */
    public Traque avancer(long delta, boolean vu) {
        return new Traque(duree + delta, vu ? 0 : horsDeVue + delta, tues);
    }

    public Traque chasseurTue() {
        return new Traque(duree, horsDeVue, tues + 1);
    }

    public Issue issue(boolean vu) {
        if (horsDeVue >= DUREE_SEMER) {
            return Issue.GAGNEE;
        }
        if (duree >= DUREE_MAX) {
            return vu ? Issue.RETRAIT : Issue.GAGNEE;
        }
        return Issue.EN_COURS;
    }

    /**
     * Une flèche de chasseur touche le joueur : est-il ralenti ? Non pendant le ralentissement en cours et les
     * {@link #IMMUNITE_ETOURDISSEMENT} ticks qui le suivent. {@code dernier} : tick du dernier ralentissement, ou
     * {@code null} s'il n'y en a pas eu.
     */
    public static boolean etourdit(@Nullable Long dernier, long maintenant) {
        return dernier == null || maintenant - dernier >= ETOURDISSEMENT + IMMUNITE_ETOURDISSEMENT;
    }

    /** Chasseurs qui doivent être sur le joueur : 3, plus un par minute (6 au plus), moins ceux qu'il a tués. */
    public int chasseursVoulus() {
        int envoyes = (int) Math.min(CHASSEURS_MAX, CHASSEURS_DEPART + duree / RENFORT);
        return Math.max(0, envoyes - tues);
    }

    /** Progression vers la victoire (semer les Gris), de 0 à 1. */
    public float progression() {
        return Math.min(1f, (float) horsDeVue / DUREE_SEMER);
    }
}
