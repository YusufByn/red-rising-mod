package io.github.yusufbyn.redrising.factions;

import com.mojang.serialization.Codec;
import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.jalons.Attitude;
import io.github.yusufbyn.redrising.jalons.EffetsMonde;
import io.github.yusufbyn.redrising.jalons.HorlogeMonde;
import io.github.yusufbyn.redrising.monde.CelluleRising;
import io.github.yusufbyn.redrising.monde.Vue;
import io.github.yusufbyn.redrising.pnj.AttitudesPnj;
import io.github.yusufbyn.redrising.pnj.PatrouillePnj;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import io.github.yusufbyn.redrising.pnj.PnjEssentiels;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *     Casser un coffre d'un camp compte aussi comme un vol (sinon on viderait un poste en perçant un mur). Le Soutien
 *     d'un vol ne compte qu'une fois par coffre et par jour de jeu, pour chaque joueur (G6.1) ; Loyauté et Suspicion,
 *     à chaque vol.</li>
 *     <li><b>Dénoncer</b> : accroupi, clic droit à mains vides sur un PNJ Gris ; le PNJ du Rising le plus proche
 *     (à moins de {@value EffetsActions#PORTEE_DENONCIATION} blocs, hors des cellules) est arrêté. Avec une carte en main :
 *     la cellule connue la plus proche du Gris tombe (G5.10). La baisse de Suspicion ne compte qu'une fois par jour.</li>
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

    /** Sur le joueur : jour de jeu de la dernière dénonciation qui a fait baisser sa Suspicion (une par jour, G5.10). */
    public static final AttachmentType<Long> DERNIER_BLANCHIMENT = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "dernier_blanchiment"),
            builder -> builder.persistent(Codec.LONG).copyOnDeath()
    );

    /** Sur un PNJ : jour de jeu où chaque joueur l'a aidé pour la dernière fois. */
    private static final AttachmentType<Map<UUID, Long>> AIDE_LE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "aide_le"),
            builder -> builder.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG))
    );

    /** Sur un coffre d'un camp : jour de jeu où le Soutien d'un vol y a été compté pour chaque joueur (G6.1). */
    private static final AttachmentType<Map<UUID, Long>> VOL_COMPTE_LE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "vol_compte_le"),
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
                    // Le Gris qui contrôle ce joueur, tué par lui : il était lui-même témoin (GDD, Contrôle). Le contrôle
                    // s'arrête là, sans fuite en plus (PatrouillePnj.oublier)
                    boolean sonControleur = joueur.getUUID().equals(PatrouillePnj.suspectControle(pnj));
                    agirDevantTemoins(joueur, chasseur ? "tue un chasseur" : sonControleur ? "tue le Gris qui le contrôle" : "tue un PNJ " + couleur.getSerializedName(),
                            chasseur ? EffetsActions.tuerChasseur() : EffetsActions.tuer(couleur), pnj, contreLaSociete(couleur),
                            sonControleur ? Temoin.GRIS : Temoin.AUCUN, campDe(pnj, couleur), Attitude.Acte.VIOLENCE);
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
            // Carte en main : on montre au Gris l'emplacement d'une cellule connue (G5.10)
            if ((objet.is(Items.MAP) || objet.is(Items.FILLED_MAP)) && couleur == Couleur.GRIS) {
                if (joueur instanceof ServerPlayer serveur) {
                    denoncerCellule(serveur, pnj, objet);
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
                agirDevantTemoins(serveur, "casse un coffre " + camp.getSerializedName(), volDuJour(serveur, List.of(bloc), EffetsActions.voler(camp)), null,
                        camp == Camp.SOCIETE, Temoin.AUCUN, camp, Attitude.Acte.VOL);
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
        long jour = jour(joueur);
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
        AttitudesPnj.aide(joueur, pnj);
    }

    private static void denoncer(ServerPlayer joueur) {
        double portee = EffetsActions.PORTEE_DENONCIATION;
        Villager rebelle = joueur.level().getEntities(EntityTypes.VILLAGER, joueur.getBoundingBox().inflate(portee),
                        // Pas les PNJ d'une cellule : seule la dénonciation de la cellule, carte en main, les atteint.
                        // Pas un personnage essentiel (G6.4) : on prend le suivant
                        pnj -> pnj.getAttached(CAMP) == Camp.RISING && pnj.distanceTo(joueur) <= portee && !CelluleRising.dansUneCellule(pnj)
                                && !PnjEssentiels.essentiel(pnj))
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
        agir(joueur, "dénonce un PNJ du Rising", denonciation(joueur, EffetsActions.denoncer()));
        AttitudesPnj.actionVue(joueur, rebelle, Camp.RISING, Attitude.Acte.DENONCIATION);
    }

    /**
     * Carte en main, à un Gris : la cellule connue la plus proche du Gris (à moins de
     * {@value EffetsActions#PORTEE_DENONCIATION_CELLULE} blocs) tombe, le Gris garde la carte, et le Rising met le
     * dénonciateur en quarantaine. Aucune cellule connue à portée : rien ne se passe, la carte reste au joueur.
     */
    private static void denoncerCellule(ServerPlayer joueur, Villager gris, ItemStack carte) {
        CelluleRising.Cellule cellule = CelluleRising.connueLaPlusProche(joueur, gris.position(), EffetsActions.PORTEE_DENONCIATION_CELLULE);
        if (cellule == null) {
            return;
        }
        carte.consume(1, joueur);
        Set<String> touches = CelluleRising.tomber(joueur.level().getServer().overworld(), cellule, "dénoncée par " + joueur.getPlainTextName());
        CelluleRising.mettreEnQuarantaine(joueur);
        joueur.sendOverlayMessage(Component.translatable("faction.redrising.denonce.cellule"));
        agir(joueur, "dénonce une cellule du Rising", denonciation(joueur, EffetsActions.denoncerCellule()));
        // Le Rising sait qui l'a trahi : ceux de la cellule l'apprennent, même sans l'avoir vu (G6.5)
        AttitudesPnj.actionVue(joueur, gris, Camp.RISING, Attitude.Acte.DENONCIATION);
        AttitudesPnj.celluleTombee(joueur.level().getServer(), touches, joueur.getUUID(), Attitude.CELLULE_DENONCEE, "a dénoncé sa cellule");
    }

    /** La baisse de Suspicion d'une dénonciation : une fois par jour de jeu, jamais pendant une traque. */
    private static Variation denonciation(ServerPlayer joueur, Variation variation) {
        long jour = jour(joueur);
        if (joueur.hasAttached(TraqueGris.TRAQUE) || Long.valueOf(jour).equals(joueur.getAttached(DERNIER_BLANCHIMENT))) {
            return EffetsActions.denonciationSansBlanchiment(variation);
        }
        joueur.setAttached(DERNIER_BLANCHIMENT, jour);
        return variation;
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

    /** Un coffre d'un camp, ou la planque d'une cellule (G5.10). */
    private static boolean aUnCamp(Level monde, BlockPos pos) {
        BlockEntity bloc = monde.getBlockEntity(pos);
        return bloc != null && (bloc.getAttached(CAMP) != null || bloc.hasAttached(CelluleRising.PLANQUE));
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
            agirDevantTemoins(serveur, "vole un coffre " + camp.getSerializedName(),
                    volDuJour(serveur, List.of((BlockEntity) coffre), EffetsActions.voler(camp)), null, camp == Camp.SOCIETE, Temoin.AUCUN,
                    camp, Attitude.Acte.VOL);
        }
    }

    /**
     * Le Soutien d'un vol ne compte qu'une fois par coffre et par jour de jeu, pour chaque joueur (G6.1), comme l'aide :
     * sinon déposer ses propres objets dans un coffre d'un camp et les reprendre serait une ferme à Soutien. La Loyauté
     * et la Suspicion s'appliquent à chaque vol : voler reste risqué. Pour plusieurs coffres (explosion), le Soutien
     * compte si l'un d'eux ne l'a pas encore été aujourd'hui.
     */
    private static Variation volDuJour(ServerPlayer joueur, List<BlockEntity> coffres, Variation variation) {
        long jour = jour(joueur);
        boolean nouveau = false;
        for (BlockEntity coffre : coffres) {
            Map<UUID, Long> comptes = coffre.getAttachedOrElse(VOL_COMPTE_LE, Map.of());
            if (comptes.getOrDefault(joueur.getUUID(), -1L) != jour) {
                Map<UUID, Long> copie = new HashMap<>(comptes);
                copie.put(joueur.getUUID(), jour);
                coffre.setAttached(VOL_COMPTE_LE, copie);
                coffre.setChanged();
                nouveau = true;
            }
        }
        return nouveau ? variation : EffetsActions.volSansSoutien(variation);
    }

    /** Jour de jeu (horloge de l'Overworld) : celui des règles « une fois par jour ». */
    private static long jour(ServerPlayer joueur) {
        return joueur.level().getServer().overworld().getGameTime() / TICKS_PAR_JOUR;
    }

    /**
     * Le joueur a mis à terre un personnage essentiel (G6.4) : un K.-O., avec les témoins du meurtre de sa Couleur
     * (silence compris), sans Soutien. Son contrôleur mis à terre par lui : témoin Gris, comme pour un meurtre.
     */
    public static void mettreKo(ServerPlayer joueur, Villager pnj) {
        Couleur couleur = PnjCouleur.couleur(pnj);
        if (couleur == null) {
            return;
        }
        boolean sonControleur = joueur.getUUID().equals(PatrouillePnj.suspectControle(pnj));
        agirDevantTemoins(joueur, "met à terre " + pnj.getAttached(PnjEssentiels.PERSONNAGE), EffetsActions.ko(couleur), pnj,
                contreLaSociete(couleur), sonControleur ? Temoin.GRIS : Temoin.AUCUN, campDe(pnj, couleur), Attitude.Acte.VIOLENCE);
    }

    /**
     * Le camp d'un PNJ, pour l'attitude des personnages qui le voient attaquer (G6.5) : celui qu'il porte (Rising, ou un
     * Gris d'un poste), sinon la Société pour l'élite et l'ordre ; aucun pour le peuple sans camp.
     */
    private static @Nullable Camp campDe(Villager pnj, Couleur couleur) {
        Camp camp = pnj.getAttached(CAMP);
        if (camp != null) {
            return camp;
        }
        return contreLaSociete(couleur) ? Camp.SOCIETE : null;
    }

    /**
     * Tuer un PNJ de cette Couleur est-il une action contre la Société (G6.3) ? Oui pour l'élite et l'ordre (chasseurs
     * compris) ; non pour le peuple : les Rouges ne couvrent pas celui qui s'en prend à eux.
     */
    static boolean contreLaSociete(Couleur victime) {
        return GroupePnj.de(victime) != GroupePnj.PEUPLE;
    }

    /** Le joueur est reconduit hors d'un poste de la Société où il était entré sans y être admis ({@code PosteSociete}). */
    public static void effraction(ServerPlayer joueur) {
        agirDevantTemoins(joueur, "effraction d'un poste", EffetsActions.effraction(), null, true, Temoin.AUCUN, null, Attitude.Acte.VOL);
    }

    /**
     * Qui voit le joueur : les PNJ de Couleur à moins de {@value EffetsActions#PORTEE_TEMOIN} blocs, qui le voient
     * ({@link Vue} : ni mur ni fumée entre eux). Ne comptent pas : les PNJ du Rising (ils ne parlent pas à la Société), {@code exclu} (la victime),
     * les villageois sans Couleur. Un Gris l'emporte sur un témoin ordinaire.
     */
    public static Temoin temoin(ServerPlayer joueur, @Nullable Entity exclu) {
        return temoin(joueur, exclu, false);
    }

    /**
     * Pour une action contre la Société ({@code contreLaSociete}), les PNJ d'une Couleur qui se tait (levier de silence,
     * G6.3) ne comptent pas non plus. Ils voient toujours ce qui les touche : tuer un PNJ du peuple, voler le Rising.
     */
    public static Temoin temoin(ServerPlayer joueur, @Nullable Entity exclu, boolean contreLaSociete) {
        double portee = EffetsActions.PORTEE_TEMOIN;
        Temoin temoin = Temoin.AUCUN;
        for (Villager pnj : joueur.level().getEntities(EntityTypes.VILLAGER, joueur.getBoundingBox().inflate(portee),
                pnj -> pnj != exclu && pnj.isAlive() && pnj.distanceTo(joueur) <= portee)) {
            Couleur couleur = PnjCouleur.couleur(pnj);
            if (couleur == null || pnj.getAttached(CAMP) == Camp.RISING || PnjEssentiels.inconscient(pnj) || !Vue.voit(pnj, joueur)
                    || (contreLaSociete && EffetsMonde.silencieuse(joueur.level().getServer(), couleur))) {
                continue;
            }
            if (couleur == Couleur.GRIS) {
                return Temoin.GRIS;
            }
            temoin = Temoin.ORDINAIRE;
        }
        return temoin;
    }

    /**
     * Une explosion va détruire des blocs (G5.9) : leur contenu tombera marqué ({@code BlockEntityMixin}). Si elle détruit
     * un coffre d'un camp et qu'on remonte au joueur responsable, c'est un vol, une fois par camp, avec ses témoins.
     * Le responsable : celui qui a allumé la TNT (ou tiré la flèche enflammée). Pas de responsable pour un creeper
     * (un joueur poursuivi près d'un poste ne l'a pas voulu) ni pour une TNT allumée par la redstone : le contenu tombe
     * marqué, ça suffit.
     */
    public static void explosion(ServerExplosion explosion, List<BlockPos> detruits) {
        ServerPlayer responsable = responsable(explosion);
        if (responsable == null) {
            return;
        }
        Map<Camp, List<BlockEntity>> voles = new EnumMap<>(Camp.class);
        for (BlockPos pos : detruits) {
            if (explosion.level().getBlockEntity(pos) instanceof Container coffre && coffre instanceof BlockEntity bloc
                    && bloc.getAttached(CAMP) instanceof Camp camp) {
                voles.computeIfAbsent(camp, c -> new ArrayList<>()).add(bloc);
            }
        }
        voles.forEach((camp, coffres) -> agirDevantTemoins(responsable, "fait sauter un coffre " + camp.getSerializedName(),
                volDuJour(responsable, coffres, EffetsActions.voler(camp)), null, camp == Camp.SOCIETE, Temoin.AUCUN, camp, Attitude.Acte.VOL));
    }

    private static @Nullable ServerPlayer responsable(ServerExplosion explosion) {
        return explosion.getIndirectSourceEntity() instanceof ServerPlayer joueur ? joueur : null;
    }

    /**
     * Applique une action selon ses témoins, et prévient le joueur s'il a été vu. {@code contreLaSociete} : une Couleur
     * qui se tait (G6.3) n'en témoigne pas. {@code auMoins} : un témoin sûr, quoi que voient les autres (la victime
     * elle-même, quand c'est le Gris qui contrôlait le joueur). {@code lese} : le camp contre qui le joueur agit, pour
     * l'attitude des personnages canon qui le voient (G6.5), silence ou pas ; {@code null} : aucun.
     */
    private static void agirDevantTemoins(ServerPlayer joueur, String action, Variation variation, @Nullable Entity exclu,
                                          boolean contreLaSociete, Temoin auMoins, @Nullable Camp lese, Attitude.Acte acte) {
        Temoin vus = temoin(joueur, exclu, contreLaSociete);
        Temoin temoin = vus.ordinal() >= auMoins.ordinal() ? vus : auMoins;
        switch (temoin) {
            case AUCUN -> {
            }
            case ORDINAIRE -> joueur.sendOverlayMessage(Component.translatable("faction.redrising.temoin"));
            case GRIS -> joueur.sendOverlayMessage(Component.translatable("faction.redrising.temoin.gris"));
        }
        agir(joueur, action + " (témoin : " + temoin.name().toLowerCase() + ")", EffetsActions.selonTemoins(variation, temoin));
        AttitudesPnj.actionVue(joueur, exclu, lese, acte);
    }

    /** Applique la variation d'une action ; son Soutien compte aussi pour l'horloge du monde (G6.1). */
    public static void agir(ServerPlayer joueur, String action, Variation variation) {
        Jauges apres = JaugesJoueur.appliquer(joueur, variation);
        RedRising.LOGGER.info("{} {} : {} → {}", joueur.getPlainTextName(), action, variation, apres);
        HorlogeMonde.compterSoutien(joueur, variation.soutien());
    }
}
