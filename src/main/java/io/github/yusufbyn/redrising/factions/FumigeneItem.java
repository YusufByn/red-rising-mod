package io.github.yusufbyn.redrising.factions;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Le fumigène en main (G5.8) : clic droit pour le lancer, comme une boule de neige. Lancé devant un Gris, c'est de la contrebande. */
public class FumigeneItem extends Item {
    public FumigeneItem(Properties proprietes) {
        super(proprietes);
    }

    @Override
    public InteractionResult use(Level monde, Player joueur, InteractionHand main) {
        ItemStack objet = joueur.getItemInHand(main);
        monde.playSound(null, joueur.getX(), joueur.getY(), joueur.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL,
                0.5f, 0.4f / (monde.getRandom().nextFloat() * 0.4f + 0.8f));
        if (monde instanceof ServerLevel serveur) {
            if (joueur instanceof ServerPlayer lanceur) {
                Contrebande.utilisee(lanceur);
            }
            Projectile.spawnProjectileFromRotation(Fumigene::new, serveur, objet, joueur, 0, 1.5f, 1);
        }
        joueur.awardStat(Stats.ITEM_USED.get(this));
        objet.consume(1, joueur);
        return InteractionResult.SUCCESS;
    }
}
