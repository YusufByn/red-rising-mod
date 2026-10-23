package io.github.yusufbyn.redrising.couleur;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Condition d'environnement pour un bonus ou un malus conditionnel (G3.4). Réévaluée deux fois par seconde.
 * Les conditions de base sont ci-dessous ; une condition sur mesure s'écrit en une ligne :
 * {@code joueur -> joueur.level().isDarkOutside()}.
 */
@FunctionalInterface
public interface Condition {
    boolean test(ServerPlayer joueur);

    /** Rien de solide au-dessus de la tête (les feuilles ne comptent pas), dans une dimension qui a un ciel. */
    Condition A_CIEL_OUVERT = joueur -> {
        ServerLevel level = joueur.level();
        BlockPos pos = joueur.blockPosition();
        return level.dimensionType().hasSkyLight()
                && pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
    };

    /** À couvert et sous le niveau de la mer : mines, grottes. */
    Condition SOUS_TERRE = joueur -> !A_CIEL_OUVERT.test(joueur) && joueur.getBlockY() < joueur.level().getSeaLevel();

    Condition DANS_L_EAU = ServerPlayer::isInWater;
}
