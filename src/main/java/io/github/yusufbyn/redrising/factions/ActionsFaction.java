package io.github.yusufbyn.redrising.factions;

import com.mojang.serialization.Codec;
import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.monde.Vue;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import io.github.yusufbyn.redrising.pnj.TraqueGris;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Les actions du joueur qui bougent ses jauges (G5.2), branchées sur les événements du jeu.
 * Les effets chiffrés sont dans {@link EffetsActions} ; ici, seulement « quand » et « sur qui ».
 *
 * <ul>
 *     <li><b>Tuer</b> un PNJ de Couleur.</li>
 *     <li><b>Aider</b> : accroupi, clic droit sur un PNJ de Couleur avec de la nourriture en main ; elle lui est donnée.
 *     Une fois par PNJ et par jour de jeu.</li>
 *     <li><b>Voler</b> : prendre des objets dans un coffre (ou un tonneau) d'un camp ; compté une fois par ouverture.
 *     Casser un coffre d'un camp compte aussi comme un vol (sinon on viderait un poste en perçant un mur).</li>
 *     <li><b>Dénoncer</b> : accroupi, clic droit à mains vides sur un PNJ Gris ; le PNJ du Rising le plus proche
 *     (à moins de {@value EffetsActions#PORTEE_DENONCIATION} blocs) est arrêté.</li>
 *     <li>Chaque jour de jeu, la Suspicion retombe un peu.</li>
 * </ul>
 *
 * <p>Tuer, voler et l'effraction d'un poste dépendent des témoins (G5.5) : voir {@link #temoin} et
 * {@link EffetsActions#selonTemoins}.</p>
 */
public final class ActionsFaction {
    /** Camp d'un PNJ ou d'un coffre (secret : pas envoyé aux clients). Les structures le poseront (G5.3, G5.4). */
    public static final AttachmentType<Camp> CAMP = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "camp"),
            builder -> builder.persistent(Camp.CODEC)
    );

    /** Sur un PNJ : jour de jeu où chaque joueur l'a aidé pour la dernière fois. */
    private static final AttachmentType<Map<UUID, Long>> AIDE_LE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "aide_le"),
            builder -> builder.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG))
    );

    public static final long TICKS_PAR_JOUR = 24000;

    private ActionsFaction() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()}. */
    public static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entite, source) -> {
            if (entite instanceof Villager pnj && source.getEntity() instanceof ServerPlayer joueur) {
                Couleur couleur = PnjCouleur.couleur(pnj);
                if (couleur != null) {
                    // Un chasseur tué pendant une traque ne rapporte pas de Soutien (G5.6)
                    boolean chasseur = pnj.hasAttached(TraqueGris.CHASSEUR);
                    agirDevantTemoins(joueur, chasseur ? "tue un chasseur" : "tue un PNJ " + couleur.getSerializedName(),
                            chasseur ? EffetsActions.tuerChasseur() : EffetsActions.tuer(couleur), pnj);
                }
            }
        });

        UseEntityCallback.EVENT.register((joueur, monde, main, entite, visee) -> {
            if (main != InteractionHand.MAIN_HAND || !joueur.isShiftKeyDown() || joueur.isSpectator() || !(entite instanceof Villager pnj)) {
                return InteractionResult.PASS;
            }
            // La Couleur des PNJ est connue du client : il fait le même choix que le serveur et n'ouvre pas le commerce
            Couleur couleur = PnjCouleur.couleur(pnj);
            if (couleur == null) {
                return InteractionResult.PASS;
            }
            ItemStack objet = joueur.getItemInHand(main);
            if (objet.has(DataComponents.FOOD)) {
                if (joueur instanceof ServerPlayer serveur) {
                    aider(serveur, pnj, couleur, objet);
                }
                return InteractionResult.SUCCESS;
            }
            if (objet.isEmpty() && couleur == Couleur.GRIS) {
                if (joueur instanceof ServerPlayer serveur) {
                    denoncer(serveur);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.AFTER.register((monde, joueur, pos, etat, bloc) -> {
            Camp camp = bloc == null ? null : bloc.getAttached(CAMP);
            if (camp != null && joueur instanceof ServerPlayer serveur) {
                agirDevantTemoins(serveur, "casse un coffre " + camp.getSerializedName(), EffetsActions.voler(camp), null);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.overworld().getGameTime() % TICKS_PAR_JOUR == 0) {
                for (ServerPlayer joueur : server.getPlayerList().getPlayers()) {
                    JaugesJoueur.appliquer(joueur, EffetsActions.jourPasse());
                }
            }
        });
    }

    private static void aider(ServerPlayer joueur, Villager pnj, Couleur couleur, ItemStack nourriture) {
        long jour = joueur.level().getGameTime() / TICKS_PAR_JOUR;
        Map<UUID, Long> aides = pnj.getAttachedOrElse(AIDE_LE, Map.of());
        if (aides.getOrDefault(joueur.getUUID(), -1L) == jour) {
            joueur.sendOverlayMessage(Component.translatable("faction.redrising.aide.deja"));
            return;
        }
        Map<UUID, Long> copie = new HashMap<>(aides);
        copie.put(joueur.getUUID(), jour);
        pnj.setAttached(AIDE_LE, copie);
        nourriture.consume(1, joueur);
        joueur.sendOverlayMessage(Component.translatable("faction.redrising.aide", couleur.getDisplayName()));
        agir(joueur, "aide un PNJ " + couleur.getSerializedName(), EffetsActions.aider(couleur));
    }

    private static void denoncer(ServerPlayer joueur) {
        double portee = EffetsActions.PORTEE_DENONCIATION;
        Villager rebelle = joueur.level().getEntities(EntityTypes.VILLAGER, joueur.getBoundingBox().inflate(portee),
                        pnj -> pnj.getAttached(CAMP) == Camp.RISING && pnj.distanceTo(joueur) <= portee)
                .stream()
                .min(Comparator.comparingDouble(pnj -> pnj.distanceToSqr(joueur)))
                .orElse(null);
        if (rebelle == null) {
            joueur.sendOverlayMessage(Component.translatable("faction.redrising.denonce.personne"));
            return;
        }
        // Arrêté : emmené par les Gris (il disparaît, sans mourir ni rien laisser)
        rebelle.discard();
        joueur.sendOverlayMessage(Component.translatable("faction.redrising.denonce", rebelle.getDisplayName()));
        agir(joueur, "dénonce un PNJ du Rising", EffetsActions.denoncer());
    }

    /** Camp d'un coffre, ou {@code null} s'il n'appartient à personne (ou n'est pas un coffre du monde). */
    public static @Nullable Camp campDuCoffre(Container coffre) {
        return coffre instanceof BlockEntity bloc ? bloc.getAttached(CAMP) : null;
    }

    /**
     * Le bloc en {@code pos} est-il un coffre ou un tonneau d'un camp, que les entonnoirs ne doivent pas vider
     * (mixin {@code HopperBlockEntityMixin}) ? Pour un coffre double, il suffit qu'une des deux moitiés appartienne à un camp.
     */
    public static boolean protegeDesEntonnoirs(Level monde, BlockPos pos, BlockState etat) {
        if (aUnCamp(monde, pos)) {
            return true;
        }
        return etat.getBlock() instanceof ChestBlock && etat.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                && aUnCamp(monde, ChestBlock.getConnectedBlockPos(pos, etat));
    }

    private static boolean aUnCamp(Level monde, BlockPos pos) {
        BlockEntity bloc = monde.getBlockEntity(pos);
        return bloc != null && bloc.getAttached(CAMP) != null;
    }

    public static int nombreObjets(Container coffre) {
        int total = 0;
        for (int i = 0; i < coffre.getContainerSize(); i++) {
            total += coffre.getItem(i).getCount();
        }
        return total;
    }

    /**
     * Fermeture d'un coffre (mixin {@code ChestMenuMixin}) : s'il contient moins d'objets qu'à l'ouverture,
     * le joueur a volé son camp. Une seule fois par ouverture, quel que soit le butin.
     */
    public static void coffreFerme(Player joueur, Container coffre, int objetsAOuverture) {
        Camp camp = campDuCoffre(coffre);
        if (camp != null && joueur instanceof ServerPlayer serveur && nombreObjets(coffre) < objetsAOuverture) {
            agirDevantTemoins(serveur, "vole un coffre " + camp.getSerializedName(), EffetsActions.voler(camp), null);
        }
    }

    /** Le joueur est reconduit hors d'un poste de la Société où il était entré sans y être admis ({@code PosteSociete}). */
    public static void effraction(ServerPlayer joueur) {
        agirDevantTemoins(joueur, "effraction d'un poste", EffetsActions.effraction(), null);
    }

    /**
     * Qui voit le joueur : les PNJ de Couleur à moins de {@value EffetsActions#PORTEE_TEMOIN} blocs, qui le voient
     * ({@link Vue} : ni mur ni fumée entre eux). Ne comptent pas : les PNJ du Rising (ils ne parlent pas à la Société), {@code exclu} (la victime),
     * les villageois sans Couleur. Un Gris l'emporte sur un témoin ordinaire.
     */
    public static Temoin temoin(ServerPlayer joueur, @Nullable Entity exclu) {
        double portee = EffetsActions.PORTEE_TEMOIN;
        Temoin temoin = Temoin.AUCUN;
        for (Villager pnj : joueur.level().getEntities(EntityTypes.VILLAGER, joueur.getBoundingBox().inflate(portee),
                pnj -> pnj != exclu && pnj.isAlive() && pnj.distanceTo(joueur) <= portee)) {
            Couleur couleur = PnjCouleur.couleur(pnj);
            if (couleur == null || pnj.getAttached(CAMP) == Camp.RISING || !Vue.voit(pnj, joueur)) {
                continue;
            }
            if (couleur == Couleur.GRIS) {
                return Temoin.GRIS;
            }
            temoin = Temoin.ORDINAIRE;
        }
        return temoin;
    }

    /** Applique une action selon ses témoins, et prévient le joueur s'il a été vu. */
    private static void agirDevantTemoins(ServerPlayer joueur, String action, Variation variation, @Nullable Entity exclu) {
        Temoin temoin = temoin(joueur, exclu);
        switch (temoin) {
            case AUCUN -> {
            }
            case ORDINAIRE -> joueur.sendOverlayMessage(Component.translatable("faction.redrising.temoin"));
            case GRIS -> joueur.sendOverlayMessage(Component.translatable("faction.redrising.temoin.gris"));
        }
        agir(joueur, action + " (témoin : " + temoin.name().toLowerCase() + ")", EffetsActions.selonTemoins(variation, temoin));
    }

    public static void agir(ServerPlayer joueur, String action, Variation variation) {
        Jauges apres = JaugesJoueur.appliquer(joueur, variation);
        RedRising.LOGGER.info("{} {} : {} → {}", joueur.getPlainTextName(), action, variation, apres);
    }
}
