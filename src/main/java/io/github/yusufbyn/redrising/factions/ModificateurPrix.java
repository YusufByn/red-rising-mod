package io.github.yusufbyn.redrising.factions;

import net.minecraft.server.level.ServerPlayer;

/**
 * Modifie le prix d'un article pour un joueur (G5.9) : un facteur sur ce que le joueur donne (sous 1, l'affaire est
 * meilleure). Déclaré de l'extérieur avec {@link Boutiques#declarerModificateur}, sans toucher à la boutique : par exemple
 * les meilleurs échanges de l'Argent avec la Société (G9.4), le charme du Rose (G9.16). Renvoyer 1 quand il ne s'applique pas.
 */
@FunctionalInterface
public interface ModificateurPrix {
    double facteur(ServerPlayer joueur, Article article);
}
