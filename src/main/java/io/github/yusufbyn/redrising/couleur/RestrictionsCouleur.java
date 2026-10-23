package io.github.yusufbyn.redrising.couleur;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Restrictions d'accès par Couleur (G3.2) : blocs, objets et crafts interdits, déclarés dans {@link ProfilCouleur}.
 *
 * <p>Les clics sont refusés des deux côtés (client et serveur) : la Couleur est synchronisée avec le client (G2.3),
 * donc il refuse de lui-même et n'ouvre pas d'écran pour rien ; il n'envoie alors rien au serveur.
 * Les crafts sont bloqués côté serveur par {@code CraftingMenuMixin}.</p>
 */
public final class RestrictionsCouleur {
    private RestrictionsCouleur() {
    }

    /** Appelé depuis {@link io.github.yusufbyn.redrising.RedRising#onInitialize()}. */
    public static void init() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            Block bloc = level.getBlockState(hitResult.getBlockPos()).getBlock();
            if (blocInterdit(player, bloc)) {
                prevenir(player, Component.translatable("restriction.redrising.bloc", bloc.getName()));
                return InteractionResult.FAIL;
            }
            // Un objet interdit ne s'utilise pas non plus sur un bloc (ex. poser un livre sur un pupitre)
            ItemStack enMain = player.getItemInHand(hand);
            if (objetInterdit(player, enMain)) {
                prevenir(player, Component.translatable("restriction.redrising.objet", enMain.getHoverName()));
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack enMain = player.getItemInHand(hand);
            if (objetInterdit(player, enMain)) {
                prevenir(player, Component.translatable("restriction.redrising.objet", enMain.getHoverName()));
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    public static boolean blocInterdit(Player player, Block bloc) {
        return profil(player).blocsInterdits().contains(bloc);
    }

    public static boolean objetInterdit(Player player, ItemStack objet) {
        return !objet.isEmpty() && profil(player).objetsInterdits().contains(objet.getItem());
    }

    public static boolean craftInterdit(Player player, ItemStack resultat) {
        return !resultat.isEmpty() && profil(player).craftsInterdits().contains(resultat.getItem());
    }

    /**
     * Message au-dessus de la barre d'objets, du côté qui refuse : le client l'affiche lui-même
     * (quand il refuse un clic, le serveur n'en entend jamais parler), le serveur l'envoie (crafts).
     */
    public static void prevenir(Player player, Component message) {
        player.sendOverlayMessage(message);
    }

    private static ProfilCouleur profil(Player player) {
        return ProfilsCouleurs.de(CouleurJoueur.get(player));
    }
}
