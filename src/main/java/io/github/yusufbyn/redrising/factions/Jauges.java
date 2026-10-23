package io.github.yusufbyn.redrising.factions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * Valeurs des 3 jauges d'un joueur. Immuable : chaque modification renvoie une nouvelle instance
 * (c'est ce que recommande la Data Attachment API).
 */
public record Jauges(int loyaute, int soutien, int suspicion) {
    // Décidé en G2.2 (GDD section 6) : on naît neutre envers la Société, inconnu du Rising, pas surveillé
    public static final Jauges DEPART = new Jauges(50, 0, 0);

    /** Sauvegarde sous la forme {loyaute: 50, soutien: 0, suspicion: 0}. */
    public static final Codec<Jauges> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("loyaute").forGetter(Jauges::loyaute),
            Codec.INT.fieldOf("soutien").forGetter(Jauges::soutien),
            Codec.INT.fieldOf("suspicion").forGetter(Jauges::suspicion)
    ).apply(instance, Jauges::new));

    /** Encodage réseau : les 3 valeurs à la suite. */
    public static final StreamCodec<ByteBuf, Jauges> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Jauges::loyaute,
            ByteBufCodecs.VAR_INT, Jauges::soutien,
            ByteBufCodecs.VAR_INT, Jauges::suspicion,
            Jauges::new
    );

    public int valeur(Jauge jauge) {
        return switch (jauge) {
            case LOYAUTE -> loyaute;
            case SOUTIEN -> soutien;
            case SUSPICION -> suspicion;
        };
    }

    /** Copie avec une jauge remplacée, sans bornage. */
    public Jauges avec(Jauge jauge, int valeur) {
        return switch (jauge) {
            case LOYAUTE -> new Jauges(valeur, soutien, suspicion);
            case SOUTIEN -> new Jauges(loyaute, valeur, suspicion);
            case SUSPICION -> new Jauges(loyaute, soutien, valeur);
        };
    }

    /** Copie avec chaque jauge ramenée dans les bornes de la Couleur. */
    public Jauges borner(@Nullable Couleur couleur) {
        return new Jauges(
                Bornes.pour(couleur, Jauge.LOYAUTE).borner(loyaute),
                Bornes.pour(couleur, Jauge.SOUTIEN).borner(soutien),
                Bornes.pour(couleur, Jauge.SUSPICION).borner(suspicion)
        );
    }
}
