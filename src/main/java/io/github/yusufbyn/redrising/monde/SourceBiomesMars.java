package io.github.yusufbyn.redrising.monde;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.yusufbyn.redrising.RedRising;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.stream.Stream;

/**
 * La source de biomes de Mars (G7.1.2) : celle de Minecraft ({@code base}), avec des pôles gelés au nord et au sud et une
 * bande de transition avant eux ({@link Latitude}). Le relief ne change pas (il ne dépend pas des biomes) : seuls les
 * biomes, donc les surfaces, la neige, la glace, la végétation et les créatures.
 *
 * <p>Déclarée dans le type de monde par défaut ({@code data/minecraft/worldgen/world_preset/normal.json}). Le monde
 * reste « stable » sans rien faire (pas d'écran d'avertissement au chargement) : en 26.2, Minecraft ne juge expérimental
 * qu'un Overworld dont la source est la sienne avec d'autres réglages que ceux de l'Overworld
 * ({@code WorldDimensions.isStableOverworld}) ; toute autre source passe. Vérifié par {@code BiomesMarsTest}.</p>
 *
 */
public final class SourceBiomesMars extends BiomeSource {
    /**
     * Les biomes qui remplacent ceux de Minecraft dans le froid, par genre (dans le JSON du type de monde).
     */
    public record Polaires(Holder<Biome> plaine, Holder<Biome> taiga, Holder<Biome> pentes, Holder<Biome> pics, Holder<Biome> picsDeGlace,
                           Holder<Biome> ocean, Holder<Biome> oceanProfond, Holder<Biome> riviere, Holder<Biome> plage) {
        static final MapCodec<Polaires> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Biome.CODEC.fieldOf("plaine").forGetter(Polaires::plaine),
                Biome.CODEC.fieldOf("taiga").forGetter(Polaires::taiga),
                Biome.CODEC.fieldOf("pentes").forGetter(Polaires::pentes),
                Biome.CODEC.fieldOf("pics").forGetter(Polaires::pics),
                Biome.CODEC.fieldOf("pics_de_glace").forGetter(Polaires::picsDeGlace),
                Biome.CODEC.fieldOf("ocean").forGetter(Polaires::ocean),
                Biome.CODEC.fieldOf("ocean_profond").forGetter(Polaires::oceanProfond),
                Biome.CODEC.fieldOf("riviere").forGetter(Polaires::riviere),
                Biome.CODEC.fieldOf("plage").forGetter(Polaires::plage)
        ).apply(instance, Polaires::new));

        List<Holder<Biome>> tous() {
            return List.of(plaine, taiga, pentes, pics, picsDeGlace, ocean, oceanProfond, riviere, plage);
        }

        /**
         * Le biome froid (bande de transition) ou gelé (pôles) qui remplace celui de Minecraft : de même genre (océan,
         * fleuve, plage, montagne, forêt), pour que la carte garde sa forme.
         */
        Holder<Biome> remplacer(Holder<Biome> biome, Latitude.Climat climat, long temperature) {
            if (biome.is(BiomeTags.IS_DEEP_OCEAN)) {
                return oceanProfond;
            }
            if (biome.is(BiomeTags.IS_OCEAN)) {
                return ocean;
            }
            if (biome.is(BiomeTags.IS_RIVER)) {
                return riviere;
            }
            if (biome.is(BiomeTags.IS_BEACH)) {
                return plage;
            }
            if (biome.is(BiomeTags.IS_MOUNTAIN)) {
                return climat == Latitude.Climat.GELE ? pics : pentes;
            }
            if (climat == Latitude.Climat.GELE) {
                // Sur les pôles, des pics de glace là où Minecraft est le plus froid, de la plaine enneigée ailleurs
                return temperature < 0 ? picsDeGlace : plaine;
            }
            return biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_FOREST) ? taiga : plaine;
        }
    }

    public static final MapCodec<SourceBiomesMars> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("base").forGetter(SourceBiomesMars::base),
            Polaires.CODEC.forGetter(SourceBiomesMars::polaires)
    ).apply(instance, SourceBiomesMars::new));

    private final BiomeSource base;
    private final Polaires polaires;

    /**
     * @param base     la source de Minecraft
     * @param polaires les biomes froids et gelés qui remplacent les siens
     */
    public SourceBiomesMars(BiomeSource base, Polaires polaires) {
        this.base = base;
        this.polaires = polaires;
    }

    /** Appelé depuis {@link RedRising#onInitialize()}, avant le chargement des données des mondes. */
    public static void init() {
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "mars"), CODEC);
    }

    public BiomeSource base() {
        return base;
    }

    public Polaires polaires() {
        return polaires;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(base.possibleBiomes().stream(), polaires.tous().stream()).distinct();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        Holder<Biome> biome = base.getNoiseBiome(x, y, z, sampler);
        int bloc = QuartPos.toBlock(z);
        // Le cas courant, sans calcul de plus : loin des pôles
        if (Math.abs(bloc) < Latitude.debutTransition()) {
            return biome;
        }
        Climate.TargetPoint point = sampler.sample(x, y, z);
        Latitude.Climat climat = Latitude.climat(bloc, point.weirdness());
        return climat == Latitude.Climat.NORMAL ? biome : polaires.remplacer(biome, climat, point.temperature());
    }

    @Override
    public void addDebugInfo(List<String> lignes, BlockPos pos, Climate.Sampler sampler) {
        base.addDebugInfo(lignes, pos, sampler);
    }
}
