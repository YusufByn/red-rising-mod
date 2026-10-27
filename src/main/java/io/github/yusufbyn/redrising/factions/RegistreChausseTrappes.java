package io.github.yusufbyn.redrising.factions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Chausse-trappes posées dans le monde (G5.8), avec leur poseur. Logique pure (testée) ; enregistré avec le monde
 * par {@code ChausseTrappes}.
 *
 * <p>Au plus {@value #MAX_PAR_JOUEUR} par joueur : en poser une de plus fait disparaître sa plus ancienne (on ne tapisse
 * pas les abords d'une cellule). Le registre sait aussi qui reprend la sienne en la cassant.</p>
 */
public record RegistreChausseTrappes(List<Piege> pieges) {
    /** 🟡 Chausse-trappes posées par joueur. */
    public static final int MAX_PAR_JOUEUR = 3;

    public static final RegistreChausseTrappes VIDE = new RegistreChausseTrappes(List.of());

    /** Une chausse-trappe : où, posée par qui, à quel tick de jeu. */
    public record Piege(ResourceKey<Level> dimension, BlockPos pos, UUID poseur, long pose) {
        public static final Codec<Piege> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                // Comme Level.RESOURCE_KEY_CODEC, sans charger la classe Level (tests unitaires sans Minecraft démarré)
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Piege::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Piege::pos),
                UUIDUtil.CODEC.fieldOf("poseur").forGetter(Piege::poseur),
                Codec.LONG.fieldOf("pose").forGetter(Piege::pose)
        ).apply(instance, Piege::new));

        public boolean est(ResourceKey<Level> dimension, BlockPos pos) {
            return this.dimension.equals(dimension) && this.pos.equals(pos);
        }
    }

    public static final Codec<RegistreChausseTrappes> CODEC = Piege.CODEC.listOf().xmap(RegistreChausseTrappes::new, RegistreChausseTrappes::pieges);

    public RegistreChausseTrappes {
        pieges = List.copyOf(pieges);
    }

    /** Résultat d'une pose : le nouveau registre, et les chausse-trappes à faire disparaître (les plus anciennes du poseur). */
    public record Pose(RegistreChausseTrappes registre, List<Piege> retirees) {
    }

    public Pose poser(Piege piege) {
        List<Piege> restants = new ArrayList<>(pieges);
        restants.removeIf(autre -> autre.est(piege.dimension(), piege.pos()));
        List<Piege> siens = new ArrayList<>(restants.stream().filter(autre -> autre.poseur().equals(piege.poseur())).toList());
        siens.sort(Comparator.comparingLong(Piege::pose));
        List<Piege> retirees = new ArrayList<>();
        while (siens.size() >= MAX_PAR_JOUEUR) {
            Piege ancienne = siens.removeFirst();
            restants.remove(ancienne);
            retirees.add(ancienne);
        }
        restants.add(piege);
        return new Pose(new RegistreChausseTrappes(restants), retirees);
    }

    public @Nullable Piege a(ResourceKey<Level> dimension, BlockPos pos) {
        return pieges.stream().filter(piege -> piege.est(dimension, pos)).findFirst().orElse(null);
    }

    public RegistreChausseTrappes retirer(ResourceKey<Level> dimension, BlockPos pos) {
        return new RegistreChausseTrappes(pieges.stream().filter(piege -> !piege.est(dimension, pos)).toList());
    }

    public List<Piege> de(UUID poseur) {
        return pieges.stream().filter(piege -> piege.poseur().equals(poseur)).toList();
    }
}
