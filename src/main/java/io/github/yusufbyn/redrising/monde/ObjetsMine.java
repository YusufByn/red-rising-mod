package io.github.yusufbyn.redrising.monde;

import io.github.yusufbyn.redrising.RedRising;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * L'hélium-3 de la mine (G7.2.2, GDD section 7) : le minerai (variante ardoise des profondeurs seulement, la mine le pose
 * aux niveaux 3 et 4, sous y = 0) et l'objet qu'il donne. La Société l'achète (commis Cuivre, {@code factions/Catalogue}).
 *
 * <ul>
 *     <li>Minerai : dureté 4,5 (comme les minerais d'ardoise des profondeurs), pioche en pierre au moins
 *     ({@code needs_stone_tool}), 🟡 lumière 3 (on le repère dans le noir), 1 à 3 points d'expérience.</li>
 *     <li>Ce qu'il donne : {@code data/redrising/loot_table/blocks/minerai_helium3.json} (1 hélium-3, Fortune ; le bloc
 *     avec Toucher de soie).</li>
 * </ul>
 */
public final class ObjetsMine {
    /** 🟡 Lumière du minerai. */
    static final int LUMIERE_MINERAI = 3;

    public static final Block MINERAI_HELIUM3 = Registry.register(BuiltInRegistries.BLOCK, cleBloc("minerai_helium3"),
            new DropExperienceBlock(UniformInt.of(1, 3), BlockBehaviour.Properties.of()
                    .setId(cleBloc("minerai_helium3"))
                    .mapColor(MapColor.DEEPSLATE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(4.5f, 3.0f)
                    .sound(SoundType.DEEPSLATE)
                    .lightLevel(etat -> LUMIERE_MINERAI)));

    public static final Item MINERAI_HELIUM3_OBJET = Registry.register(BuiltInRegistries.ITEM, cleObjet("minerai_helium3"),
            new BlockItem(MINERAI_HELIUM3, new Item.Properties().setId(cleObjet("minerai_helium3")).useBlockDescriptionPrefix()));

    public static final Item HELIUM3 = Registry.register(BuiltInRegistries.ITEM, cleObjet("helium3"),
            new Item(new Item.Properties().setId(cleObjet("helium3"))));

    private ObjetsMine() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()} : charge la classe, donc inscrit tout. */
    public static void init() {
        RedRising.LOGGER.debug("Mine : {} {}", MINERAI_HELIUM3, HELIUM3);
    }

    private static ResourceKey<Block> cleBloc(String nom) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(RedRising.MOD_ID, nom));
    }

    private static ResourceKey<Item> cleObjet(String nom) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(RedRising.MOD_ID, nom));
    }
}
