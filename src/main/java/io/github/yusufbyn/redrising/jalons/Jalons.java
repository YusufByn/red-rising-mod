package io.github.yusufbyn.redrising.jalons;

import io.github.yusufbyn.redrising.data.EtatMonde;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Les jalons déclarés (G6.2), et ce qu'on en déduit de l'état du monde : mise en route, échéance, issue.
 * Logique pure (testée) ; le moteur côté serveur est {@link MoteurJalons}. Les vrais jalons viendront avec leur ticket
 * (pendaison d'Eo en G7.10, liste en G16.1).
 */
public final class Jalons {
    private static final Map<String, Jalon> DECLARES = new LinkedHashMap<>();

    private Jalons() {
    }

    public static void declarer(Jalon jalon) {
        if (DECLARES.putIfAbsent(jalon.id(), jalon) != null) {
            throw new IllegalStateException("Jalon déjà déclaré : " + jalon.id());
        }
    }

    /** Pour les tests : un jalon de test ne doit pas rester dans les mondes suivants. */
    public static void retirer(String id) {
        DECLARES.remove(id);
    }

    /** Dans l'ordre de déclaration. */
    public static Collection<Jalon> tous() {
        return DECLARES.values();
    }

    public static @Nullable Jalon get(String id) {
        return DECLARES.get(id);
    }

    /** Pas passé, pas déjà en route, à l'époque du monde, dépendances passées. */
    public static boolean peutSeMettreEnRoute(Jalon jalon, EtatMonde etat) {
        return !etat.jalons().containsKey(jalon.id()) && !etat.enRoute().containsKey(jalon.id())
                && etat.epoque() == jalon.epoque() && jalon.dependancesSatisfaites(etat.jalons());
    }

    /** Le bilan depuis sa mise en route (ou depuis le début du monde, s'il n'est pas en route : jalon forcé). */
    public static Bilan bilan(Jalon jalon, EtatMonde etat) {
        Horloge depart = etat.enRoute().getOrDefault(jalon.id(), Horloge.ZERO);
        return Bilan.entre(depart, etat.horloge(), etat.jalons(), etat.zones());
    }

    /** Délai total depuis sa mise en route, au rythme actuel. */
    public static long delai(Jalon jalon, EtatMonde etat) {
        return jalon.echeance().delai(bilan(jalon, etat).rythme());
    }

    /** Temps de jeu avant son échéance (négatif ou nul : dû). Il doit être en route. */
    public static long restant(Jalon jalon, EtatMonde etat) {
        Horloge depart = Objects.requireNonNull(etat.enRoute().get(jalon.id()), "jalon pas en route : " + jalon.id());
        return depart.temps() + delai(jalon, etat) - etat.horloge().temps();
    }
}
