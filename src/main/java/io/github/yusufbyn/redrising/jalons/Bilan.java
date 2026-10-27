package io.github.yusufbyn.redrising.jalons;

import io.github.yusufbyn.redrising.factions.Camp;

import java.util.Map;

/**
 * L'état du monde vu par un jalon (G6.2), au moment où l'on décide de son échéance ou de son issue : ce qui s'est passé
 * depuis sa mise en route, les issues des jalons passés, les zones. Plus tard : attitudes des PNJ canon (G6.5),
 * personnages canon tués (G6.4). Logique pure (testée).
 *
 * @param periode l'horloge depuis la mise en route : temps de jeu, présence, aide et tort de la période
 */
public record Bilan(Horloge periode, Map<String, Issue> issues, Map<String, Camp> zones) {
    public Bilan {
        issues = Map.copyOf(issues);
        zones = Map.copyOf(zones);
    }

    /** Ce qui s'est passé entre deux relevés de l'horloge. */
    public static Bilan entre(Horloge depart, Horloge maintenant, Map<String, Issue> issues, Map<String, Camp> zones) {
        return new Bilan(new Horloge(maintenant.temps() - depart.temps(), maintenant.presence() - depart.presence(),
                maintenant.aide() - depart.aide(), maintenant.tort() - depart.tort()), issues, zones);
    }

    public long aide() {
        return periode.aide();
    }

    public long tort() {
        return periode.tort();
    }

    public long net() {
        return periode.net();
    }

    /**
     * Net de Soutien par joueur et par jour de jeu sur la période : l'intensité moyenne de chaque joueur, qui ne dépend
     * pas du nombre de joueurs (G6.2). Divisé par au moins un jour-joueur, sinon il exploserait dans les premières minutes.
     * Un jour qui pèse 30 (plafond de G6.1) ne le déplace que de 30 / P sur une période de P jours-joueur.
     */
    public double rythme() {
        return (double) net() * Echeance.JOUR / Math.max(periode.presence(), Echeance.JOUR);
    }

    /** L'issue de ce jalon, s'il est passé. */
    public Issue issue(String jalon) {
        return issues.get(jalon);
    }
}
