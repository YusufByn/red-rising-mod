package io.github.yusufbyn.redrising.jalons;

import org.jspecify.annotations.Nullable;

/**
 * Un personnage canon (G6.4) : un identifiant, et le jalon jusqu'auquel il est essentiel (GDD section 8). Déclaré dans
 * {@link Personnages} ; le PNJ qui l'incarne ne porte que son identifiant.
 *
 * @param jalon le jalon qui le rend essentiel tant qu'il n'a pas d'issue (y compris un jalon fixe : Eo jusqu'à sa
 *              pendaison), ou {@code null} : jamais essentiel
 */
public record Personnage(String id, @Nullable String jalon) {
}
