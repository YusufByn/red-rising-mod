package io.github.yusufbyn.redrising.net;

import io.github.yusufbyn.redrising.RedRising;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client vers serveur : le joueur a appuyé sur la touche de capacité (G3.3).
 * Ne transporte rien : le serveur décide seul (Couleur, recharge) de ce qui se passe.
 */
public record ActiverCapacitePayload() implements CustomPacketPayload {
    public static final ActiverCapacitePayload INSTANCE = new ActiverCapacitePayload();
    public static final Type<ActiverCapacitePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "activer_capacite"));
    public static final StreamCodec<ByteBuf, ActiverCapacitePayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<ActiverCapacitePayload> type() {
        return TYPE;
    }
}
