package io.github.yusufbyn.redrising.net;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Serveur vers client : le résultat du test de naissance (G4.4), à accepter ou, si c'est encore permis, à relancer.
 */
public record ResultatTestPayload(Couleur couleur, boolean relancePossible) implements CustomPacketPayload {
    public static final Type<ResultatTestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "resultat_test"));
    public static final StreamCodec<ByteBuf, ResultatTestPayload> CODEC = StreamCodec.composite(
            Couleur.STREAM_CODEC, ResultatTestPayload::couleur,
            ByteBufCodecs.BOOL, ResultatTestPayload::relancePossible,
            ResultatTestPayload::new
    );

    @Override
    public Type<ResultatTestPayload> type() {
        return TYPE;
    }
}
