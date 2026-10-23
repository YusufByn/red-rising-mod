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
    OR("or", 0xFFBA14),
    ARGENT("argent", 0xA9C1E0),
    CUIVRE("cuivre", 0xDD8240),
    BLANC("blanc", 0xFFFFFF),
    BLEU("bleu", 0x2F6BFF),
    JAUNE("jaune", 0xFFF35A),
    VERT("vert", 0x2FA84F),
    VIOLET("violet", 0xA050E8),
    ORANGE("orange", 0xF05A1A),
    GRIS("gris", 0x8E8E8E),
    OBSIDIAN("obsidian", 0x4A2D7A),
    MARRON("marron", 0x8A5530),
    ROSE("rose", 0xFF7EB6),
    ROUGE("rouge", 0xC8201E);

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
