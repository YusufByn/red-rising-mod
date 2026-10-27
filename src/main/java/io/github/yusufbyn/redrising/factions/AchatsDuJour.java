package io.github.yusufbyn.redrising.factions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Échanges d'un joueur avec les boutiques (G5.9) pendant un jour de jeu : article → nombre d'échanges. Sert de stock
 * (ce qu'une boutique vend) et de plafond (ce que la Société achète). Un autre jour, tout repart de zéro.
 * Logique pure (testée).
 */
public record AchatsDuJour(long jour, Map<String, Integer> nombres) {
    public static final AchatsDuJour AUCUN = new AchatsDuJour(-1, Map.of());

    public static final Codec<AchatsDuJour> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("jour").forGetter(AchatsDuJour::jour),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("nombres").forGetter(AchatsDuJour::nombres)
    ).apply(instance, AchatsDuJour::new));

    public AchatsDuJour {
        nombres = Map.copyOf(nombres);
    }

    /** Échanges de cet article ce jour-là (0 si on a changé de jour). */
    public int nombre(String article, long aujourdhui) {
        return aujourdhui == jour ? nombres.getOrDefault(article, 0) : 0;
    }

    /** Un échange de plus ; un nouveau jour efface les compteurs de la veille. */
    public AchatsDuJour plusUn(String article, long aujourdhui) {
        Map<String, Integer> suite = new HashMap<>(aujourdhui == jour ? nombres : Map.of());
        suite.merge(article, 1, Integer::sum);
        return new AchatsDuJour(aujourdhui, suite);
    }
}
