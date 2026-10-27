package io.github.yusufbyn.redrising.factions;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le bloc de la chausse-trappe (G5.8) : de la ferraille au ras du sol, sans collision (on marche dedans, comme dans une
 * plaque de pression). Elle tient sur un sol solide, et casse si on le retire. La règle (qui la déclenche, ce qu'elle fait,
 * le registre des poseurs) est dans {@link ChausseTrappes}.
 */
public class ChausseTrappe extends Block {
    public static final MapCodec<ChausseTrappe> CODEC = simpleCodec(ChausseTrappe::new);
    /** Un pixel d'épaisseur, une marge d'un pixel sur les bords. */
    private static final VoxelShape FORME = Block.box(1, 0, 1, 15, 1, 15);

    public ChausseTrappe(Properties proprietes) {
        super(proprietes);
    }

    @Override
    protected MapCodec<? extends ChausseTrappe> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState etat, BlockGetter monde, BlockPos pos, CollisionContext contexte) {
        return FORME;
    }

    @Override
    protected boolean canSurvive(BlockState etat, LevelReader monde, BlockPos pos) {
        return canSupportCenter(monde, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState etat, LevelReader monde, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos voisin, BlockState etatVoisin, RandomSource aleatoire) {
        if (direction == Direction.DOWN && !etat.canSurvive(monde, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(etat, monde, ticks, pos, direction, voisin, etatVoisin, aleatoire);
    }

    @Override
    public void setPlacedBy(Level monde, BlockPos pos, BlockState etat, LivingEntity poseur, ItemStack objet) {
        super.setPlacedBy(monde, pos, etat, poseur, objet);
        if (monde instanceof ServerLevel serveur && poseur instanceof ServerPlayer joueur) {
            ChausseTrappes.posee(serveur, pos, joueur);
        }
    }

    @Override
    protected void entityInside(BlockState etat, Level monde, BlockPos pos, Entity entite, InsideBlockEffectApplier effets, boolean precis) {
        if (monde instanceof ServerLevel serveur && entite instanceof LivingEntity vivant) {
            ChausseTrappes.marcheDessus(serveur, pos, vivant);
        }
    }
}
