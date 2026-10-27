package io.github.yusufbyn.redrising.factions;

import io.github.yusufbyn.redrising.RedRising;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Équipement rebelle (G5.7, code en G5.8) : fumigène et chausse-trappe, enregistrés auprès de Minecraft.
 *
 * <p>Minecraft tient des <i>registres</i> (objets, blocs, types d'entités...) : chaque chose y est inscrite sous un
 * identifiant ({@code redrising:fumigene}), au démarrage, avant que les registres soient figés. C'est cet identifiant que
 * reprennent {@code /give}, les textures et les traductions. Les champs ci-dessous s'inscrivent au chargement de la classe,
 * provoqué par {@link #init()}.</p>
 *
 * <p>Ces objets ne se fabriquent pas (aucune recette) : ils viendront de la boutique du Rising (G5.9). En attendant :
 * {@code /give} ou l'onglet « Red Rising » de l'inventaire créatif.</p>
 */
public final class ObjetsRebelles {
    public static final ChausseTrappe CHAUSSE_TRAPPE = Registry.register(BuiltInRegistries.BLOCK, cleBloc("chausse_trappe"),
            new ChausseTrappe(BlockBehaviour.Properties.of()
                    .setId(cleBloc("chausse_trappe"))
                    .mapColor(MapColor.METAL)
                    .noCollision()
                    .noOcclusion()
                    .strength(0.5f)
                    .sound(SoundType.CHAIN)
                    // Rien ne tombe quand on la casse : le poseur récupère la sienne par le code (ChausseTrappes)
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY)));

    public static final Item FUMIGENE = Registry.register(BuiltInRegistries.ITEM, cleObjet("fumigene"),
            new FumigeneItem(new Item.Properties().setId(cleObjet("fumigene")).stacksTo(16)));

    public static final Item CHAUSSE_TRAPPE_OBJET = Registry.register(BuiltInRegistries.ITEM, cleObjet("chausse_trappe"),
            new BlockItem(CHAUSSE_TRAPPE, new Item.Properties().setId(cleObjet("chausse_trappe")).useBlockDescriptionPrefix()));

    /** Le fumigène en vol (comme une boule de neige). */
    public static final EntityType<Fumigene> FUMIGENE_LANCE = Registry.register(BuiltInRegistries.ENTITY_TYPE, cleEntite("fumigene"),
            EntityType.Builder.<Fumigene>of(Fumigene::new, MobCategory.MISC)
                    .noLootTable()
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(cleEntite("fumigene")));

    /** Objets de contrebande (règle dans {@link Contrebande}) ; liste dans {@code data/redrising/tags/item/contrebande.json}. */
    public static final TagKey<Item> CONTREBANDE = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "contrebande"));

    private ObjetsRebelles() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()} : charge la classe (donc inscrit tout), et ajoute l'onglet créatif. */
    public static void init() {
        ResourceKey<CreativeModeTab> onglet = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "red_rising"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, onglet, FabricCreativeModeTab.builder()
                .title(Component.translatable("onglet.redrising.red_rising"))
                .icon(() -> new ItemStack(FUMIGENE))
                .displayItems((parametres, sortie) -> {
                    sortie.accept(FUMIGENE);
                    sortie.accept(CHAUSSE_TRAPPE_OBJET);
                })
                .build());
    }

    private static ResourceKey<Block> cleBloc(String nom) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(RedRising.MOD_ID, nom));
    }

    private static ResourceKey<Item> cleObjet(String nom) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(RedRising.MOD_ID, nom));
    }

    private static ResourceKey<EntityType<?>> cleEntite(String nom) {
        return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(RedRising.MOD_ID, nom));
    }
}
