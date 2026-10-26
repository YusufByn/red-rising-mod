package io.github.yusufbyn.redrising.net;

import io.github.yusufbyn.redrising.RedRising;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Serveur vers client : le joueur naît en choix libre (G4.2), le client doit lui montrer l'écran de choix.
 * Ne transporte rien : la liste des Couleurs est la même des deux côtés.
 */
public record OuvrirChoixCouleurPayload() implements CustomPacketPayload {
    public static final OuvrirChoixCouleurPayload INSTANCE = new OuvrirChoixCouleurPayload();
    public static final Type<OuvrirChoixCouleurPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "ouvrir_choix_couleur"));
    public static final StreamCodec<ByteBuf, OuvrirChoixCouleurPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<OuvrirChoixCouleurPayload> type() {
        return TYPE;
    }
}
