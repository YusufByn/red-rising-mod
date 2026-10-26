package io.github.yusufbyn.redrising.net;

import io.github.yusufbyn.redrising.RedRising;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Serveur vers client : le joueur doit passer le test de naissance (G4.4), depuis la première question.
 * Ne transporte rien : les questions sont les mêmes des deux côtés.
 */
public record OuvrirTestPayload() implements CustomPacketPayload {
    public static final OuvrirTestPayload INSTANCE = new OuvrirTestPayload();
    public static final Type<OuvrirTestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "ouvrir_test"));
    public static final StreamCodec<ByteBuf, OuvrirTestPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<OuvrirTestPayload> type() {
        return TYPE;
    }
}
