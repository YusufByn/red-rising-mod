package io.github.yusufbyn.redrising.net;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.TestPersonnalite;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Client vers serveur : les réponses au test de naissance (G4.4), le numéro de la réponse choisie à chaque question.
 * Le client ne connaît pas le résultat : c'est le serveur qui compte les points.
 */
public record RepondreTestPayload(List<Integer> choix) implements CustomPacketPayload {
    public static final Type<RepondreTestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "repondre_test"));
    // Taille bornée : une liste plus longue est refusée dès la lecture du paquet
    public static final StreamCodec<ByteBuf, RepondreTestPayload> CODEC = ByteBufCodecs.VAR_INT
            .apply(ByteBufCodecs.list(TestPersonnalite.QUESTIONS.size()))
            .map(RepondreTestPayload::new, RepondreTestPayload::choix);

    @Override
    public Type<RepondreTestPayload> type() {
        return TYPE;
    }
}
