package io.github.yusufbyn.redrising.mixin;

import io.github.yusufbyn.redrising.monde.MineLykos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.MonsterRoomFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pas de salle de monstres dans l'emprise de la mine (G7.2.1) : une galerie qui en couperait une laisserait un générateur
 * à nu. Toute la hauteur des chunks de l'emprise, Overworld seulement.
 */
@Mixin(MonsterRoomFeature.class)
public abstract class MonsterRoomFeatureMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void redrising$pasDansLaMine(FeaturePlaceContext<NoneFeatureConfiguration> contexte, CallbackInfoReturnable<Boolean> cir) {
        if (contexte.level().getLevel().dimension().equals(Level.OVERWORLD)
                && MineLykos.dansEmprise(contexte.origin().getX() >> 4, contexte.origin().getZ() >> 4)) {
            cir.setReturnValue(false);
        }
    }
}
