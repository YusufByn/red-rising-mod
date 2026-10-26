package io.github.yusufbyn.redrising.monde;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Scène du test de naissance (G4.5), façon Dive to the Heart (Kingdom Hearts) : un vide noir, et au milieu
 * un vitrail rond posé sur un pilier qui s'enfonce dans l'obscurité. Les 14 Couleurs y forment une roue autour
 * d'un cœur blanc ; le joueur se tient au centre pendant les questions.
 *
 * <p>La scène est une dimension à part ({@code redrising:scene_naissance}), décrite en JSON dans
 * {@code data/redrising/dimension} (vide, sans ciel ni jour) et {@code dimension_type} (noir, brouillard).
 * Le vitrail est construit par le code à la première visite, puis reste dans la sauvegarde du monde.</p>
 */
public final class SceneNaissance {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "scene_naissance"));

    /** Centre du vitrail ; le joueur se tient juste au-dessus. */
    private static final BlockPos CENTRE = new BlockPos(0, 64, 0);
    private static final double RAYON = 8.5;
    private static final int PROFONDEUR_PILIER = 24;
    /** Regard vers le sud et vers le bas : la caméra, placée derrière le joueur (côté client), domine le vitrail. */
    private static final float REGARD_HORIZONTAL = 0;
    private static final float REGARD_VERTICAL = 45;

    /** Verre teinté de chaque Couleur dans la roue (les 16 teintures de Minecraft n'en permettent qu'une approximation). */
    private static final Map<Couleur, DyeColor> VERRE = new EnumMap<>(Map.ofEntries(
            Map.entry(Couleur.OR, DyeColor.YELLOW),
            Map.entry(Couleur.ARGENT, DyeColor.LIGHT_GRAY),
            Map.entry(Couleur.CUIVRE, DyeColor.ORANGE),
            Map.entry(Couleur.BLANC, DyeColor.WHITE),
            Map.entry(Couleur.BLEU, DyeColor.BLUE),
            Map.entry(Couleur.JAUNE, DyeColor.YELLOW),
            Map.entry(Couleur.VERT, DyeColor.GREEN),
            Map.entry(Couleur.VIOLET, DyeColor.PURPLE),
            Map.entry(Couleur.ORANGE, DyeColor.ORANGE),
            Map.entry(Couleur.GRIS, DyeColor.GRAY),
            Map.entry(Couleur.OBSIDIAN, DyeColor.BLACK),
            Map.entry(Couleur.MARRON, DyeColor.BROWN),
            Map.entry(Couleur.ROSE, DyeColor.PINK),
            Map.entry(Couleur.ROUGE, DyeColor.RED)
    ));

    private SceneNaissance() {
    }

    public static boolean dans(ServerPlayer joueur) {
        return joueur.level().dimension().equals(DIMENSION);
    }

    /** Amène le joueur au centre du vitrail (construit s'il ne l'est pas encore). */
    public static void entrer(ServerPlayer joueur) {
        ServerLevel scene = scene(joueur);
        if (scene == null) {
            return;
        }
        construire(scene);
        Vec3 position = Vec3.atBottomCenterOf(CENTRE.above());
        joueur.teleport(new TeleportTransition(scene, position, Vec3.ZERO, REGARD_HORIZONTAL, REGARD_VERTICAL, TeleportTransition.DO_NOTHING));
    }

    /** Ramène le joueur à son point d'apparition (celui du monde pour un nouveau joueur), s'il est dans la scène. */
    public static void sortir(ServerPlayer joueur) {
        if (dans(joueur)) {
            joueur.teleport(joueur.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING));
        }
    }

    private static @Nullable ServerLevel scene(ServerPlayer joueur) {
        ServerLevel scene = joueur.level().getServer().getLevel(DIMENSION);
        if (scene == null) {
            RedRising.LOGGER.error("Dimension {} absente : le test de naissance se passe hors de la scène", DIMENSION.identifier());
        }
        return scene;
    }

    /**
     * Vitrail posé sur du béton blanc (qui fait ressortir la teinte du verre), sur un pilier noir.
     * Éclairé par des blocs de lumière invisibles, un sur trois, juste au-dessus du verre : le vide autour reste noir.
     */
    private static void construire(ServerLevel scene) {
        BlockState coeur = verre(DyeColor.WHITE);
        if (scene.getBlockState(CENTRE).equals(coeur)) {
            return; // déjà construit
        }
        int r = (int) Math.ceil(RAYON);
        Couleur[] couleurs = Couleur.values();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > RAYON) {
                    continue;
                }
                BlockState vitrail;
                if (distance <= 1.5) {
                    vitrail = coeur;
                } else if (distance <= 2.5 || distance > RAYON - 1) {
                    vitrail = verre(DyeColor.BLACK); // cernes du vitrail : autour du cœur et au bord
                } else {
                    // Roue des Couleurs : 14 secteurs, l'Or au sud (devant le joueur), puis dans le sens des aiguilles d'une montre
                    double angle = Math.atan2(-dx, dz); // 0 au sud
                    // (angle + π) va de 0 à 2π ; décalé d'un demi-tour et d'un demi-secteur pour centrer l'Or sur l'angle 0
                    int secteur = (int) Math.floor((angle + Math.PI) / (2 * Math.PI) * couleurs.length + couleurs.length / 2.0 + 0.5) % couleurs.length;
                    vitrail = verre(VERRE.get(couleurs[secteur]));
                }
                BlockPos pos = CENTRE.offset(dx, 0, dz);
                scene.setBlock(pos, vitrail, Block.UPDATE_CLIENTS);
                scene.setBlock(pos.below(), Blocks.CONCRETE.white().defaultBlockState(), Block.UPDATE_CLIENTS);
                if (dx % 3 == 0 && dz % 3 == 0) {
                    scene.setBlock(pos.above(), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LightBlock.MAX_LEVEL), Block.UPDATE_CLIENTS);
                }
                for (int y = 2; y <= PROFONDEUR_PILIER; y++) {
                    scene.setBlock(pos.below(y), Blocks.CONCRETE.black().defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        RedRising.LOGGER.info("Scène de naissance : vitrail construit");
    }

    private static BlockState verre(DyeColor teinte) {
        return Blocks.STAINED_GLASS.pick(teinte).defaultBlockState();
    }
}
