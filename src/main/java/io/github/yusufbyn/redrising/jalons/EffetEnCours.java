package io.github.yusufbyn.redrising.jalons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Un levier posé dans le monde par un jalon (G6.3), sauvegardé dans l'état du monde. Logique pure (testée).
 *
 * @param jalon d'où il vient (ou {@code debug})
 * @param debut temps de jeu où il a été posé
 * @param fin   temps de jeu où il s'arrête, ou -1 : permanent
 */
public record EffetEnCours(Levier levier, int niveau, String jalon, long debut, long fin) {
    public static final long PERMANENT = -1;

    public static final Codec<EffetEnCours> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Levier.CODEC.fieldOf("levier").forGetter(EffetEnCours::levier),
            Codec.INT.fieldOf("niveau").forGetter(EffetEnCours::niveau),
            Codec.STRING.fieldOf("jalon").forGetter(EffetEnCours::jalon),
            Codec.LONG.fieldOf("debut").forGetter(EffetEnCours::debut),
            Codec.LONG.optionalFieldOf("fin", PERMANENT).forGetter(EffetEnCours::fin)
    ).apply(instance, EffetEnCours::new));

    public EffetEnCours {
        niveau = Math.clamp(niveau, 0, levier.niveauMax());
    }

    /** Encore en cours à ce temps de jeu. */
    public boolean actif(long temps) {
        return fin == PERMANENT || temps < fin;
    }
}
