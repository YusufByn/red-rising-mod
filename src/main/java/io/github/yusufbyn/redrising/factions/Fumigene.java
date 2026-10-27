package io.github.yusufbyn.redrising.factions;

import io.github.yusufbyn.redrising.monde.Vue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fumigène en vol (G5.8) : lancé comme une boule de neige ; là où il touche (bloc ou entité), un nuage de fumée
 * ({@link Vue#enfumer}) qui coupe la vue des PNJ pendant 10 secondes. Il ne blesse personne.
 */
public class Fumigene extends ThrowableItemProjectile {
    /** Utilisé par Minecraft pour recréer l'entité (côté client, au chargement). */
    public Fumigene(EntityType<? extends Fumigene> type, Level monde) {
        super(type, monde);
    }

    public Fumigene(Level monde, LivingEntity lanceur, ItemStack objet) {
        super(ObjetsRebelles.FUMIGENE_LANCE, lanceur, monde, objet);
    }

    @Override
    protected Item getDefaultItem() {
        return ObjetsRebelles.FUMIGENE;
    }

    @Override
    protected void onHit(HitResult impact) {
        super.onHit(impact);
        if (level() instanceof ServerLevel monde && !isRemoved()) {
            // Centre un peu au-dessus de l'impact : le nuage ne s'enfonce pas à moitié dans le sol
            Vec3 centre = impact.getLocation().add(0, Nuage.RAYON / 2, 0);
            Vue.enfumer(monde, centre);
            monde.playSound(null, centre.x, centre.y, centre.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1, 0.6f);
            discard();
        }
    }
}
