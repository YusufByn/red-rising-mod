package io.github.yusufbyn.redrising.couleur;

import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Profil de capacités de chaque Couleur. Vide tant que la Couleur n'a pas été écrite :
 * chacune est déclarée dans son propre ticket (Rouge en G7.6, Gris en G8.2...).
 */
public final class ProfilsCouleurs {
    private static final Map<Couleur, ProfilCouleur> PROFILS = new EnumMap<>(Couleur.class);

    private ProfilsCouleurs() {
    }

    /** Déclare (ou remplace) le profil d'une Couleur. À appeler pendant l'initialisation du mod. */
    public static void declarer(Couleur couleur, ProfilCouleur profil) {
        PROFILS.put(couleur, profil);
    }

    /** Profil d'une Couleur ; {@link ProfilCouleur#VIDE} si elle n'en a pas ou si le joueur n'est pas né. */
    public static ProfilCouleur de(@Nullable Couleur couleur) {
        return couleur == null ? ProfilCouleur.VIDE : PROFILS.getOrDefault(couleur, ProfilCouleur.VIDE);
    }
}
