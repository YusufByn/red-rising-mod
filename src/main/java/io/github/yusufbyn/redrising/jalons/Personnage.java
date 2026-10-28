package io.github.yusufbyn.redrising.jalons;

import io.github.yusufbyn.redrising.factions.Camp;
import org.jspecify.annotations.Nullable;

/**
 * Un personnage canon (G6.4) : un identifiant, et le jalon jusqu'auquel il est essentiel (GDD section 8). Déclaré dans
 * {@link Personnages} ; le PNJ qui l'incarne ne porte que son identifiant.
 *
 * @param jalon le jalon qui le rend essentiel tant qu'il n'a pas d'issue (y compris un jalon fixe : Eo jusqu'à sa
 *              pendaison), ou {@code null} : jamais essentiel
 * @param camp  son camp au départ (G6.5), ou {@code null} : les actions qu'il voit ne changent pas son attitude. À lire
 *              par {@link Personnages#camp}, jamais directement : il devra pouvoir changer
 */
public record Personnage(String id, @Nullable String jalon, @Nullable Camp camp) {
    public Personnage(String id, @Nullable String jalon) {
        this(id, jalon, null);
    }
}
