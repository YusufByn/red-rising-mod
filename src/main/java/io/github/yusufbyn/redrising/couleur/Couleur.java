package io.github.yusufbyn.redrising.couleur;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Les 14 Couleurs de la Société (GDD section 4), de la plus haute à la plus basse.
 *
 * <p>Chaque Couleur est sauvegardée par son identifiant texte ({@link #getSerializedName()}),
 * jamais par sa position dans l'enum : on peut réordonner les valeurs sans casser les mondes existants.
 * En revanche, un identifiant ne doit plus jamais changer une fois publié.</p>
 */
public enum Couleur implements StringRepresentable {
    OR("or"),
    ARGENT("argent"),
    CUIVRE("cuivre"),
    BLANC("blanc"),
    BLEU("bleu"),
    JAUNE("jaune"),
    VERT("vert"),
    VIOLET("violet"),
    ORANGE("orange"),
    GRIS("gris"),
    OBSIDIAN("obsidian"),
    MARRON("marron"),
    ROSE("rose"),
    ROUGE("rouge");

    /** Codec (sérialisation) basé sur l'identifiant texte : "or", "rouge"... */
    public static final Codec<Couleur> CODEC = StringRepresentable.fromEnum(Couleur::values);

    /**
     * Encodage réseau compact (le numéro de la Couleur). Contrairement à la sauvegarde, la position dans l'enum suffit :
     * le serveur et le client ont toujours la même version du mod.
     */
    public static final StreamCodec<ByteBuf, Couleur> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Couleur::ordinal);

    private final String id;

    Couleur(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Clé de traduction du nom affiché, ex. {@code couleur.redrising.rouge}. */
    public String getTranslationKey() {
        return "couleur.redrising." + id;
    }

    public Component getDisplayName() {
        return Component.translatable(getTranslationKey());
    }
}
