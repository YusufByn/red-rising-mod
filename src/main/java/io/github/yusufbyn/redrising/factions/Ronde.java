package io.github.yusufbyn.redrising.factions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Ronde d'une patrouille (G5.5) : des points à visiter dans l'ordre, en boucle. Logique pure, testée par {@code RondeTest}.
 * Elle ne sait pas qui marche : un PNJ Gris aujourd'hui ({@code PatrouillePnj}), le joueur Gris en G8.4.
 *
 * @param points au moins deux points
 * @param index  point en cours (celui vers lequel on marche)
 */
public record Ronde(List<BlockPos> points, int index) {
    /** Un point est atteint à moins de 2 blocs (on ne tombe jamais pile dessus en marchant). */
    public static final double TOLERANCE = 2;

    public static final Codec<Ronde> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.listOf().fieldOf("points").forGetter(Ronde::points),
            Codec.INT.fieldOf("index").forGetter(Ronde::index)
    ).apply(instance, Ronde::new));

    public Ronde {
        if (points.size() < 2) {
            throw new IllegalArgumentException("Une ronde a au moins deux points : " + points);
        }
        points = List.copyOf(points);
        index = Math.floorMod(index, points.size());
    }

    /** Nouvelle ronde, qui commence par le premier point. */
    public static Ronde de(List<BlockPos> points) {
        return new Ronde(points, 0);
    }

    public BlockPos pointEnCours() {
        return points.get(index);
    }

    public boolean atteint(BlockPos position) {
        return position.closerThan(pointEnCours(), TOLERANCE);
    }

    /** La ronde passée au point suivant ; après le dernier, on revient au premier. */
    public Ronde suivante() {
        return new Ronde(points, index + 1);
    }
}
