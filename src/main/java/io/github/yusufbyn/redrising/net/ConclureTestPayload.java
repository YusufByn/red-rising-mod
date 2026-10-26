package io.github.yusufbyn.redrising.net;

import io.github.yusufbyn.redrising.RedRising;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client vers serveur : réponse à l'écran de résultat du test (G4.4).
 * {@code relancer} : refaire le test (une seule fois) au lieu d'accepter la Couleur.
 */
public record ConclureTestPayload(boolean relancer) implements CustomPacketPayload {
    public static final Type<ConclureTestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "conclure_test"));
    public static final StreamCodec<ByteBuf, ConclureTestPayload> CODEC = ByteBufCodecs.BOOL.map(ConclureTestPayload::new, ConclureTestPayload::relancer);

    @Override
    public Type<ConclureTestPayload> type() {
        return TYPE;
    }
}
