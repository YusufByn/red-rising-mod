package io.github.yusufbyn.redrising.factions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Ce qu'une action ajoute (ou retire) à chaque jauge, avant bornage. */
public record Variation(int loyaute, int soutien, int suspicion) {
    public static final Variation AUCUNE = new Variation(0, 0, 0);

    /** Pour les versements de jauges d'un jalon (G6.3), sauvegardés dans l'état du monde. */
    public static final Codec<Variation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("loyaute", 0).forGetter(Variation::loyaute),
            Codec.INT.optionalFieldOf("soutien", 0).forGetter(Variation::soutien),
            Codec.INT.optionalFieldOf("suspicion", 0).forGetter(Variation::suspicion)
    ).apply(instance, Variation::new));
}
