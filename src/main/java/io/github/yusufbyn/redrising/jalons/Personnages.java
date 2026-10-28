package io.github.yusufbyn.redrising.jalons;

import io.github.yusufbyn.redrising.data.EtatMonde;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Les personnages canon déclarés (G6.4) : {@code Personnages.declarer("darrow", "triomphe")}. Les vrais viendront en
 * G15.2. Logique pure (testée) ; le côté serveur est {@code pnj/PnjEssentiels}.
 */
public final class Personnages {
    /** 🟡 Durée de l'inconscience d'un essentiel mis à terre : 30 secondes de jeu. */
    public static final long DUREE_INCONSCIENCE = 600;

    private static final Map<String, Personnage> DECLARES = new LinkedHashMap<>();

    private Personnages() {
    }

    /** @param jalon le jalon qui le rend essentiel, ou {@code null} : jamais essentiel */
    public static void declarer(String id, @Nullable String jalon) {
        if (DECLARES.putIfAbsent(id, new Personnage(id, jalon)) != null) {
            throw new IllegalStateException("Personnage déjà déclaré : " + id);
        }
    }

    /** Pour les tests : un personnage de test ne doit pas rester dans les mondes suivants. */
    public static void retirer(String id) {
        DECLARES.remove(id);
    }

    public static Collection<Personnage> tous() {
        return DECLARES.values();
    }

    public static @Nullable Personnage get(String id) {
        return DECLARES.get(id);
    }

    /** Essentiel : il a un jalon, qui n'a pas encore d'issue. Ne meurt pas : il tombe inconscient. */
    public static boolean essentiel(Personnage personnage, EtatMonde etat) {
        return personnage.jalon() != null && !etat.jalons().containsKey(personnage.jalon());
    }
}
