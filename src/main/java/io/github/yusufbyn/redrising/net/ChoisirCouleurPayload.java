package io.github.yusufbyn.redrising.net;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client vers serveur : la Couleur choisie sur l'écran de choix libre (G4.2).
 * Ce n'est qu'une demande : le serveur vérifie que le joueur a le droit de choisir (voir {@code Naissance}).
 */
public record ChoisirCouleurPayload(Couleur couleur) implements CustomPacketPayload {
    public static final Type<ChoisirCouleurPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "choisir_couleur"));
    public static final StreamCodec<ByteBuf, ChoisirCouleurPayload> CODEC = Couleur.STREAM_CODEC.map(ChoisirCouleurPayload::new, ChoisirCouleurPayload::couleur);

    @Override
    public Type<ChoisirCouleurPayload> type() {
        return TYPE;
    }
}
