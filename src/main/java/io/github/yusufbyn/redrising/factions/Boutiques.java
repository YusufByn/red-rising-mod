package io.github.yusufbyn.redrising.factions;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.monde.CelluleRising;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Commerce de la Société et du Rising (G5.9). Le catalogue est dans {@link Catalogue}.
 *
 * <ul>
 *     <li><b>Qui vend</b> : un PNJ marqué {@link #BOUTIQUE} (le commis Cuivre du poste, le Fils de la cellule). Un clic
 *     droit normal ouvre l'écran de commerce de Minecraft ; accroupi, ce sont les gestes d'aide et de dénonciation.</li>
 *     <li><b>Offres refaites à chaque ouverture</b>, pour ce joueur : seulement les articles de son palier, avec ses
 *     échanges du jour. Les échanges de métier du villageois, ses remises (réputation, villageois guéri, Héros du village)
 *     et l'expérience ne jouent pas.</li>
 *     <li><b>Stock et plafond par joueur et par jour de jeu</b> ({@link AchatsDuJour}) : pas besoin de poste de travail.</li>
 *     <li><b>Objets volés</b> : marqués dans les coffres d'un camp ({@link #VOLE}) ; chaque camp refuse ses propres biens volés.</li>
 *     <li>Acheter et vendre ne touchent jamais aux jauges (sinon on achèterait de la Loyauté ou du Soutien).</li>
 * </ul>
 */
public final class Boutiques {
    /** Monnaie de la Société : elle seule en donne (contre de la production brute) et en prend. */
    public static final Item CREDIT = Registry.register(BuiltInRegistries.ITEM, cleObjet("credit"),
            new Item(new Item.Properties().setId(cleObjet("credit"))));

    /**
     * Marque « volé à ce camp », posée sur tout ce qui est dans un coffre d'un camp quand on l'ouvre ou le casse.
     * Un composant d'objet : il est sauvegardé avec l'objet et envoyé au client (pour la description).
     */
    public static final DataComponentType<Camp> VOLE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "vole"),
            DataComponentType.<Camp>builder().persistent(Camp.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(Camp.CODEC).cast()).build());

    /** Sur le PNJ qui tient une boutique. */
    public static final AttachmentType<Boutique> BOUTIQUE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "boutique"),
            builder -> builder.persistent(Boutique.CODEC)
    );

    /** Sur le joueur : ses échanges du jour (stock et plafond). Sauvegardé : se reconnecter ne remet pas le stock. */
    public static final AttachmentType<AchatsDuJour> ACHATS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "achats_du_jour"),
            builder -> builder.persistent(AchatsDuJour.CODEC).copyOnDeath()
    );

    private static final List<ModificateurPrix> MODIFICATEURS = new ArrayList<>();

    private Boutiques() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()}. */
    public static void init() {
        UseEntityCallback.EVENT.register((joueur, monde, main, entite, visee) -> {
            if (main != InteractionHand.MAIN_HAND || joueur.isShiftKeyDown() || joueur.isSpectator() || !(entite instanceof Villager pnj)) {
                return InteractionResult.PASS;
            }
            // Le client ne sait pas qui tient une boutique : il laisse faire, et Minecraft envoie le clic au serveur
            Boutique boutique = pnj.getAttached(BOUTIQUE);
            if (boutique == null || !(joueur instanceof ServerPlayer serveur)) {
                return InteractionResult.PASS;
            }
            ouvrir(serveur, pnj, boutique);
            return InteractionResult.SUCCESS;
        });
    }

    /** Ouvre la boutique pour ce joueur, ou le refuse (seuil de la structure, ou le PNJ commerce déjà). */
    public static void ouvrir(ServerPlayer joueur, Villager pnj, Boutique boutique) {
        if (pnj.isTrading()) {
            return;
        }
        // Le Rising refuse aussi un joueur en quarantaine (il a dénoncé une cellule, G5.10)
        if (!boutique.admet(JaugesJoueur.get(joueur)) || (boutique == Boutique.RISING && CelluleRising.enQuarantaine(joueur))) {
            joueur.sendOverlayMessage(Component.translatable("boutique.redrising.refus." + boutique.getSerializedName()));
            return;
        }
        pnj.setOffers(offres(joueur, boutique));
        pnj.setTradingPlayer(joueur);
        // Niveau 0 : pas de barre d'expérience de métier dans l'écran
        pnj.openTradingScreen(joueur, Component.translatable("boutique.redrising." + boutique.getSerializedName()), 0);
    }

    /** Les offres de cette boutique pour ce joueur : son palier, ses échanges du jour, les modificateurs de prix. */
    public static MerchantOffers offres(ServerPlayer joueur, Boutique boutique) {
        Jauges jauges = JaugesJoueur.get(joueur);
        AchatsDuJour achats = achats(joueur);
        long jour = jour(joueur);
        MerchantOffers offres = new MerchantOffers();
        for (Article article : Catalogue.de(boutique)) {
            if (!article.ouvertA(jauges)) {
                continue;
            }
            double facteur = facteur(joueur, article);
            ItemCost prixA = new ItemCost(article.prixA(), PrixBoutique.ajuster(article.nombreA(), facteur, article.prixA().getDefaultMaxStackSize()));
            Optional<ItemCost> prixB = article.prixB() == null ? Optional.empty()
                    : Optional.of(new ItemCost(article.prixB(), PrixBoutique.ajuster(article.nombreB(), facteur, article.prixB().getDefaultMaxStackSize())));
            ItemStack produit = new ItemStack(article.produit(), article.nombreProduit());
            offres.add(new OffreBoutique(joueur, article, prixA, prixB, produit, Math.min(achats.nombre(article.id(), jour), article.parJour())));
        }
        return offres;
    }

    /** Un échange vient d'avoir lieu (appelé par l'offre) : il compte dans le stock du jour. */
    static void echange(ServerPlayer joueur, Article article) {
        joueur.setAttached(ACHATS, achats(joueur).plusUn(article.id(), jour(joueur)));
        RedRising.LOGGER.info("{} échange avec la boutique {} : {}", joueur.getPlainTextName(), article.boutique().getSerializedName(), article.id());
    }

    public static AchatsDuJour achats(ServerPlayer joueur) {
        return joueur.getAttachedOrElse(ACHATS, AchatsDuJour.AUCUN);
    }

    private static long jour(ServerPlayer joueur) {
        return joueur.level().getServer().overworld().getGameTime() / ActionsFaction.TICKS_PAR_JOUR;
    }

    // --- Prix ---

    /** Ajoute un modificateur de prix (Argent, Rose...) ; il s'applique à toutes les boutiques ouvertes ensuite. */
    public static void declarerModificateur(ModificateurPrix modificateur) {
        MODIFICATEURS.add(modificateur);
    }

    public static void retirerModificateur(ModificateurPrix modificateur) {
        MODIFICATEURS.remove(modificateur);
    }

    private static double facteur(ServerPlayer joueur, Article article) {
        return PrixBoutique.facteur(MODIFICATEURS.stream().map(modificateur -> modificateur.facteur(joueur, article)).toList());
    }

    // --- Objets volés ---

    /** Le camp à qui cet objet a été volé, ou {@code null}. */
    public static @Nullable Camp voleA(ItemStack objet) {
        return objet.get(VOLE);
    }

    /** Tout ce qui est dans ce coffre d'un camp porte sa marque : en sortir, c'est l'emporter marqué. */
    public static void marquerVoles(Container coffre, Camp camp) {
        for (int i = 0; i < coffre.getContainerSize(); i++) {
            ItemStack objet = coffre.getItem(i);
            if (!objet.isEmpty() && objet.get(VOLE) != camp) {
                objet.set(VOLE, camp);
            }
        }
    }

    private static ResourceKey<Item> cleObjet(String nom) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(RedRising.MOD_ID, nom));
    }
}
