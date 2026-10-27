package io.github.yusufbyn.redrising.factions;

import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * Un article d'une boutique (G5.9) : ce que le joueur donne (une ou deux sortes d'objets, comme un échange de villageois),
 * ce qu'il reçoit, à partir de quel seuil de quelle jauge, et combien de fois par jour de jeu.
 *
 * <p>La Société achète aussi : l'article donne alors des crédits, et {@code parJour} est le plafond de ce qu'elle
 * achète (sinon une ferme de blé donnerait des crédits sans limite).</p>
 *
 * @param id identifiant stable (compteur du jour, tests)
 */
public record Article(String id, Boutique boutique, Item prixA, int nombreA, @Nullable Item prixB, int nombreB,
                      Item produit, int nombreProduit, Jauge jauge, int seuil, int parJour) {
    /** Visible (et achetable) par un joueur qui a ces jauges. */
    public boolean ouvertA(Jauges jauges) {
        return jauges.valeur(jauge) >= seuil;
    }
}
