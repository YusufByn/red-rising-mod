package io.github.yusufbyn.redrising.factions;

import com.mojang.serialization.Codec;
import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.pnj.TraqueGris;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Contrebande du Rising (G5.7, code en G5.8) : les objets du tag {@link ObjetsRebelles#CONTREBANDE}.
 *
 * <ul>
 *     <li>Tenue en main (principale ou secondaire) ou utilisée devant un Gris (même règle que les témoins, fumée comprise) :
 *     Suspicion +5, au plus une fois par minute. Les autres témoins ne la reconnaissent pas.</li>
 *     <li>Rangée dans l'inventaire : invisible pour l'instant (la fouille viendra plus tard).</li>
 *     <li>Pendant une traque, sans effet : la Suspicion y est déjà au maximum.</li>
 * </ul>
 */
public final class Contrebande {
    /** Sur le joueur : tick de jeu du dernier repérage. Sauvegardé, pour qu'une reconnexion ne remette pas le compteur à zéro. */
    public static final AttachmentType<Long> DERNIER_REPERAGE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "contrebande_reperee"),
            builder -> builder.persistent(Codec.LONG).copyOnDeath()
    );

    /** 🟡 Une minute entre deux repérages. */
    public static final long RECHARGE = 1200;
    /** Les mains sont regardées toutes les secondes. */
    private static final int PERIODE = 20;

    private Contrebande() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()}. */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % PERIODE != 0) {
                return;
            }
            for (ServerPlayer joueur : server.getPlayerList().getPlayers()) {
                if (joueur.isAlive() && !joueur.isSpectator() && enMain(joueur)) {
                    exposer(joueur);
                }
            }
        });
    }

    public static boolean enMain(Player joueur) {
        return joueur.getMainHandItem().is(ObjetsRebelles.CONTREBANDE) || joueur.getOffhandItem().is(ObjetsRebelles.CONTREBANDE);
    }

    /** Le joueur vient d'utiliser un objet de contrebande (fumigène lancé, chausse-trappe posée). */
    static void utilisee(ServerPlayer joueur) {
        exposer(joueur);
    }

    /** Assez de temps depuis le dernier repérage ? */
    public static boolean peutEtreReperee(@Nullable Long dernier, long maintenant) {
        return dernier == null || maintenant - dernier >= RECHARGE;
    }

    /** La contrebande est visible : si un Gris la voit (et pas trop tôt après la dernière fois), la Suspicion monte. */
    private static void exposer(ServerPlayer joueur) {
        if (joueur.hasAttached(TraqueGris.TRAQUE)) {
            return;
        }
        long maintenant = joueur.level().getServer().overworld().getGameTime();
        if (!peutEtreReperee(joueur.getAttached(DERNIER_REPERAGE), maintenant) || ActionsFaction.temoin(joueur, null) != Temoin.GRIS) {
            return;
        }
        joueur.setAttached(DERNIER_REPERAGE, maintenant);
        joueur.sendOverlayMessage(Component.translatable("faction.redrising.contrebande"));
        ActionsFaction.agir(joueur, "contrebande vue par un Gris", EffetsActions.contrebandeVue());
    }
}
