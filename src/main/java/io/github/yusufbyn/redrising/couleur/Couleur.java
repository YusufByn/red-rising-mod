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
    OR("or", 0xD4AF37),
    ARGENT("argent", 0xC0C0C8),
    CUIVRE("cuivre", 0xB87333),
    BLANC("blanc", 0xF2F2F2),
    BLEU("bleu", 0x2E6FD8),
    JAUNE("jaune", 0xE8D13A),
    VERT("vert", 0x2E9B44),
    VIOLET("violet", 0x7B3FA0),
    ORANGE("orange", 0xE8792B),
    GRIS("gris", 0x808080),
    OBSIDIAN("obsidian", 0x1C1C24),
    MARRON("marron", 0x6B4423),
    ROSE("rose", 0xE88AAE),
    ROUGE("rouge", 0xB22222);

    /** Codec (sérialisation) basé sur l'identifiant texte : "or", "rouge"... */
    public static final Codec<Couleur> CODEC = StringRepresentable.fromEnum(Couleur::values);

    /**
     * Encodage réseau compact (le numéro de la Couleur). Contrairement à la sauvegarde, la position dans l'enum suffit :
     * le serveur et le client ont toujours la même version du mod.
     */
    public static final StreamCodec<ByteBuf, Couleur> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Couleur::ordinal);

    private final String id;
    private final int teinte;

    Couleur(String id, int teinte) {
        this.id = id;
        this.teinte = teinte;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Teinte de la Couleur en RGB ({@code 0xRRGGBB}) : marque sur le modèle (G3.5), nom dans le HUD. */
    public int getTeinte() {
        return teinte;
    }

    /** Clé de traduction du nom affiché, ex. {@code couleur.redrising.rouge}. */
    public String getTranslationKey() {
        return "couleur.redrising." + id;
    }

    public Component getDisplayName() {
        return Component.translatable(getTranslationKey());
    }
}
