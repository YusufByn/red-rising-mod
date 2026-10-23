package io.github.yusufbyn.redrising.factions;

import io.github.yusufbyn.redrising.couleur.Couleur;
import org.jspecify.annotations.Nullable;

/**
 * Intervalle autorisé pour une jauge : {@code min} (plancher) et {@code max} (plafond), inclus.
 *
 * <p>Un {@code record} Java est une classe de données immuable, proche d'une {@code dataclass(frozen=True)} en Python :
 * les champs, le constructeur, {@code equals} et {@code toString} sont générés.</p>
 */
public record Bornes(int min, int max) {
    public static final int MIN = 0;
    public static final int MAX = 100;

    /** Bornes de toute jauge sans règle particulière. */
    public static final Bornes PAR_DEFAUT = new Bornes(MIN, MAX);

    // 🟡 Proposé (G2.2), à valider : la Loyauté d'un Rouge ne dépasse pas 40
    public static final int PLAFOND_LOYAUTE_ROUGE = 40;

    public Bornes {
        if (min < MIN || max > MAX || min > max) {
            throw new IllegalArgumentException("Bornes invalides : [" + min + ", " + max + "]");
        }
    }

    /** Ramène une valeur dans l'intervalle. Prend un {@code long} pour qu'une addition ne déborde jamais. */
    public int borner(long valeur) {
        return Math.clamp(valeur, min, max);
    }

    /**
     * Bornes d'une jauge selon la Couleur. {@code couleur} vaut {@code null} pour un joueur pas encore né.
     * Les autres plafonds et planchers (Sculpture, Couleurs suivantes) viendront s'ajouter ici.
     */
    public static Bornes pour(@Nullable Couleur couleur, Jauge jauge) {
        if (couleur == Couleur.ROUGE && jauge == Jauge.LOYAUTE) {
            return new Bornes(MIN, PLAFOND_LOYAUTE_ROUGE);
        }
        return PAR_DEFAUT;
    }
}
