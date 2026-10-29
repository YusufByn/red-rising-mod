package io.github.yusufbyn.redrising.monde;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.List;

/**
 * La palette de la mine : tous les blocs que la mine pose en plus du vide, des minerais et de la roche d'origine (étais,
 * échelles, butoirs, lanternes, bandes, bouchon, panneaux, voies, coffres, roche de la coque). Déclarés ici seulement :
 * la pose ({@link MineLykos}) et les tests en jeu les lisent ici.
 *
 * <p>L'architecture actuelle est fonctionnelle et provisoire (GDD section 7). L'allure finale viendra avec la direction
 * artistique des lieux (G17.7) en changeant cette classe, sans toucher au plan ({@link PlanDeMine}) ni aux tests. Ce que
 * la pose, le plan et les tests supposent de chaque bloc est écrit sur lui : à garder en changeant la palette.</p>
 */
public final class PaletteMine {
    /** Étai : le poteau, contre la paroi, sous chaque bout de poutre. Il ne doit pas être plein (on le frôle en marchant). */
    public static final BlockState POTEAU = Blocks.SPRUCE_FENCE.defaultBlockState();
    /** Étai : la poutre, au plafond, d'une paroi à l'autre. Pleine : les lanternes de l'axe y sont suspendues. */
    public static final BlockState POUTRE = Blocks.SPRUCE_PLANKS.defaultBlockState();
    /** Le pilier qui tient l'échelle là où une galerie traverse la paroi d'un puits. Plein. */
    public static final BlockState PILIER_ECHELLE = Blocks.SPRUCE_LOG.defaultBlockState();
    /** Échelle des puits, contre la paroi nord : grimpable, tournée vers le sud. */
    public static final BlockState ECHELLE = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
    /** Butoir au bout de chaque voie : plein (il arrête un wagonnet ; le réseau éclairé ne le compte pas comme sol). */
    public static final BlockState BUTOIR = Blocks.SPRUCE_LOG.defaultBlockState();
    /** Bouchon en haut du puits principal : plein. */
    public static final BlockState BOUCHON = Blocks.POLISHED_ANDESITE.defaultBlockState();
    /**
     * 🟡 Bande de couleur le long des murs de l'axe, une teinte par niveau (du haut vers le bas, comptés pour 4 niveaux).
     * Elle ne remplace que la roche ; quatre blocs différents, pour qu'on lise le niveau à la couleur.
     */
    public static final List<BlockState> BANDES = List.of(Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE).defaultBlockState(),
            Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW).defaultBlockState(), Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE).defaultBlockState(),
            Blocks.DYED_TERRACOTTA.pick(DyeColor.RED).defaultBlockState());
    /** Sous chaque rail de propulsion, deux blocs plus bas, noyée dans la roche : elle l'alimente à travers le bloc entre eux. */
    public static final BlockState TORCHE_PROPULSION = Blocks.REDSTONE_TORCH.defaultBlockState();

    private PaletteMine() {
    }

    /** Roche de la coque (à la place d'une grotte ou d'un fluide autour du vide) : pierre au-dessus de y = 0, ardoise dessous. */
    public static BlockState roche(int y) {
        return y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    /**
     * Lanterne du réseau éclairé : une lumière de bloc assez forte pour que tout le sol du réseau soit à 1 au moins (aucun
     * monstre n'y apparaît). Suspendue sous une poutre, ou posée au sol.
     */
    public static BlockState lanterne(boolean suspendue) {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, suspendue);
    }

    /** Panneau des repères : un panneau mural (son texte est écrit par la pose dans les données du chunk, en {@code minecraft:sign}). */
    public static BlockState panneau(Direction face) {
        return Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, face);
    }

    /** Rail d'une voie ; un rail de propulsion est posé allumé (sa torche, dessous, le garde allumé). */
    public static BlockState rail(boolean surX, boolean propulsion) {
        RailShape forme = surX ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        return propulsion
                ? Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, forme).setValue(PoweredRailBlock.POWERED, true)
                : Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, forme);
    }

    /** Coffre d'outils, contre un mur : un contenant à table de butin (contenu tiré à la première ouverture). */
    public static BlockState coffre(Direction face) {
        return Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, face);
    }

    /** Ce bloc est-il un de ceux que la mine pose dans son vide (tests : un bloc du vide est de l'air ou l'un d'eux) ? */
    public static boolean poseDansLeVide(BlockState etat) {
        return etat.isAir() || etat.is(POTEAU.getBlock()) || etat.is(POUTRE.getBlock()) || etat.is(PILIER_ECHELLE.getBlock())
                || etat.is(ECHELLE.getBlock()) || etat.is(BUTOIR.getBlock()) || etat.is(lanterne(true).getBlock())
                || etat.is(panneau(Direction.NORTH).getBlock()) || etat.is(rail(true, false).getBlock())
                || etat.is(rail(true, true).getBlock()) || etat.is(coffre(Direction.NORTH).getBlock());
    }

    /** Ce bloc est-il la bande de couleur d'un niveau ? */
    public static boolean bande(BlockState etat) {
        return BANDES.stream().anyMatch(bande -> etat.is(bande.getBlock()));
    }
}
