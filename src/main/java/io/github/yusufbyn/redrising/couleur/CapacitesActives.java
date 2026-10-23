package io.github.yusufbyn.redrising.couleur;

import com.mojang.serialization.Codec;
import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.net.ActiverCapacitePayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Déclenchement des capacités actives et leur recharge (G3.3).
 *
 * <p>La recharge est l'heure de jeu ({@code level.getGameTime()}, en ticks) où la capacité redevient prête.
 * Elle est attachée au joueur : sauvegardée, gardée à la mort (mourir ne recharge pas une capacité)
 * et synchronisée avec lui pour que le HUD l'affiche.</p>
 */
public final class CapacitesActives {
    public static final AttachmentType<Long> PRETE_A = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "capacite_prete_a"),
            builder -> builder
                    .persistent(Codec.LONG)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly())
    );

    private CapacitesActives() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()}. */
    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(ActiverCapacitePayload.TYPE, ActiverCapacitePayload.CODEC);
        // Les réceptions de paquets s'exécutent sur le fil du serveur : on peut toucher au monde directement
        ServerPlayNetworking.registerGlobalReceiver(ActiverCapacitePayload.TYPE, (payload, contexte) -> activer(contexte.player()));
    }

    /** Ticks restants avant que la capacité soit prête (0 si prête). Marche côté client avec la valeur synchronisée. */
    public static long ticksRestants(Player joueur) {
        long preteA = joueur.getAttachedOrElse(PRETE_A, 0L);
        return Math.max(0, preteA - joueur.level().getGameTime());
    }

    /** Tente de déclencher la capacité de la Couleur du joueur. */
    public static void activer(ServerPlayer joueur) {
        CapaciteActive capacite = ProfilsCouleurs.de(CouleurJoueur.get(joueur)).capacite();
        if (capacite == null) {
            joueur.sendOverlayMessage(Component.translatable("capacite.redrising.aucune"));
            return;
        }
        long restants = ticksRestants(joueur);
        if (restants > 0) {
            joueur.sendOverlayMessage(Component.translatable("capacite.redrising.recharge", secondes(restants)));
            return;
        }
        if (capacite.action().declencher(joueur)) {
            joueur.setAttached(PRETE_A, joueur.level().getGameTime() + capacite.recharge());
        }
    }

    /** Secondes entières, arrondies au-dessus (1 tick restant affiche 1 s, pas 0). */
    public static long secondes(long ticks) {
        return (ticks + 19) / 20;
    }
}
