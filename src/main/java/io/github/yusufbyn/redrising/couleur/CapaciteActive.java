package io.github.yusufbyn.redrising.couleur;

import net.minecraft.server.level.ServerPlayer;

/**
 * Capacité active d'une Couleur (G3.3) : déclenchée par la touche d'action, puis indisponible pendant sa recharge.
 *
 * @param recharge durée de recharge en ticks (20 ticks = 1 seconde)
 * @param action   ce que fait la capacité, exécuté sur le serveur
 */
public record CapaciteActive(int recharge, Action action) {
    public CapaciteActive {
        if (recharge < 0) {
            throw new IllegalArgumentException("Recharge négative : " + recharge);
        }
    }

    @FunctionalInterface
    public interface Action {
        /**
         * Déclenche la capacité.
         *
         * @return {@code false} si elle ne s'applique pas ici (pas de cible, mauvais endroit...) : la recharge ne démarre pas
         */
        boolean declencher(ServerPlayer joueur);
    }
}
