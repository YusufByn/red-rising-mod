package io.github.yusufbyn.redrising.monde;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.factions.Acces;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Boutique;
import io.github.yusufbyn.redrising.factions.Boutiques;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.EffetsActions;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.jalons.Attitude;
import io.github.yusufbyn.redrising.pnj.AttitudesPnj;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import io.github.yusufbyn.redrising.pnj.PnjEssentiels;
import io.github.yusufbyn.redrising.pnj.TraqueGris;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Cellule du Rising (G5.4) : une salle cachée derrière un mur de pierre ordinaire, avec un PNJ Rouge et un coffre du Rising.
 * On n'y entre qu'avec assez de Soutien ({@link Acces#SEUIL_SOUTIEN_RISING}).
 *
 * <ul>
 *     <li><b>Entrée cachée</b> : deux blocs de pierre vanilla dans le mur (même F3 n'y voit que de la pierre).
 *     Accroupi, main vide, clic droit dessus : un joueur admis passe dans la salle. Pour un joueur refusé, rien ne se passe,
 *     comme sur n'importe quelle pierre : pas de message, pas de son.</li>
 *     <li><b>Repère</b> : quelques particules rouges sur l'entrée, envoyées par le serveur aux seuls joueurs admis.</li>
 *     <li><b>Sortie</b> : même geste depuis l'intérieur, quel que soit le Soutien.</li>
 *     <li><b>Intérieur surveillé</b> : un joueur refusé qui s'y trouve (Soutien tombé une fois dedans, mur percé)
 *     est mis dehors, devant l'entrée. Il connaît déjà la cellule : là, un message le lui dit.</li>
 *     <li><b>Planque</b> (G5.10) : un second coffre, sans camp, pour les affaires du joueur : ni marque, ni vol.</li>
 *     <li><b>Cellule connue</b> (G5.10) : un joueur qui s'est trouvé dans la salle (par l'entrée ou en creusant) la connaît ;
 *     il peut la dénoncer à un Gris, carte en main. Elle <b>tombe</b> alors ({@link #tomber}), et le Rising met le
 *     dénonciateur en <b>quarantaine</b> : aucune cellule ne l'accepte pendant 3 jours de jeu.</li>
 *     <li><b>Cellule repérée</b> pendant une traque : la Société y fait une descente un jour de jeu plus tard.</li>
 *     <li><b>Fils tué</b> : un remplaçant arrive au bout de 3 jours de jeu sans Fils.</li>
 * </ul>
 *
 * <p>La salle est une vraie salle du monde : la trouver en creusant est permis (dénoncer une cellule, raids : G5.5 et suivants).
 * Construite par le code ({@code /rr structure cellule}) en attendant la génération dans les mines (G7.5).
 * Cellules enregistrées avec le monde, dans l'Overworld seulement.</p>
 */
public final class CelluleRising {
    /**
     * Une cellule enregistrée.
     *
     * @param interieur la salle (sans les murs)
     * @param entree    moitié basse de l'entrée cachée (l'autre moitié est au-dessus)
     * @param versSalle direction de la salle vue de l'entrée, depuis dehors
     * @param reperee   un chasseur a vu un joueur traqué y entrer (G5.6)
     * @param descente  tick de jeu de la descente de la Société sur une cellule repérée (G5.10), -1 sinon
     * @param sansFils  tick de jeu depuis lequel la cellule n'a plus de Fils (tué), -1 s'il est là
     * @param reperePar le joueur qui a mené les Gris à la cellule : il perd du Soutien à la descente
     */
    public record Cellule(BoundingBox interieur, BlockPos entree, Direction versSalle, boolean reperee, long descente, long sansFils,
                          Optional<UUID> reperePar) {
        public static final Codec<Cellule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BoundingBox.CODEC.fieldOf("interieur").forGetter(Cellule::interieur),
                BlockPos.CODEC.fieldOf("entree").forGetter(Cellule::entree),
                Direction.CODEC.fieldOf("vers_salle").forGetter(Cellule::versSalle),
                Codec.BOOL.optionalFieldOf("reperee", false).forGetter(Cellule::reperee),
                Codec.LONG.optionalFieldOf("descente", -1L).forGetter(Cellule::descente),
                Codec.LONG.optionalFieldOf("sans_fils", -1L).forGetter(Cellule::sansFils),
                UUIDUtil.CODEC.optionalFieldOf("repere_par").forGetter(Cellule::reperePar)
        ).apply(instance, Cellule::new));

        public Cellule avecSansFils(long tick) {
            return new Cellule(interieur, entree, versSalle, reperee, descente, tick, reperePar);
        }

        public boolean estLEntree(BlockPos pos) {
            return pos.equals(entree) || pos.equals(entree.above());
        }

        /** Où arrive un joueur qui entre : juste derrière l'entrée. */
        public BlockPos dedans() {
            return entree.relative(versSalle);
        }

        /** Où arrive un joueur qui sort (ou qu'on met dehors) : juste devant l'entrée. */
        public BlockPos dehors() {
            return entree.relative(versSalle.getOpposite());
        }
    }

    private static final AttachmentType<List<Cellule>> CELLULES = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "cellules_rising"),
            builder -> builder.persistent(Cellule.CODEC.listOf())
    );

    /** Sur le joueur : les entrées des cellules qu'il connaît (il s'est trouvé dans la salle). */
    public static final AttachmentType<List<BlockPos>> CONNUES = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "cellules_connues"),
            builder -> builder.persistent(BlockPos.CODEC.listOf()).copyOnDeath()
    );

    /** Sur le joueur : tick de jeu de la fin de sa quarantaine (il a dénoncé une cellule). */
    public static final AttachmentType<Long> QUARANTAINE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "quarantaine_rising"),
            builder -> builder.persistent(Codec.LONG).copyOnDeath()
    );

    /** Sur le coffre de la planque d'une cellule (sans camp) : les entonnoirs ne la vident pas. */
    public static final AttachmentType<Boolean> PLANQUE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "planque"),
            builder -> builder.persistent(Codec.BOOL)
    );

    /**
     * Joueurs hors ligne au moment d'une descente sur une cellule qu'ils avaient fait repérer : nombre de peines dues,
     * appliquées à leur retour (on ne peut rien attacher à un joueur absent).
     */
    private static final AttachmentType<Map<UUID, Integer>> PEINES_EN_ATTENTE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "peines_descente"),
            builder -> builder.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT))
    );

    /** 🟡 Descente de la Société sur une cellule repérée : un jour de jeu plus tard. */
    public static final long DELAI_DESCENTE = ActionsFaction.TICKS_PAR_JOUR;
    /** 🟡 Quarantaine du dénonciateur : 3 jours de jeu. */
    public static final long DUREE_QUARANTAINE = 3 * ActionsFaction.TICKS_PAR_JOUR;
    /** 🟡 Un nouveau Fils arrive après 3 jours de jeu sans Fils. */
    public static final long DELAI_REMPLACANT = 3 * ActionsFaction.TICKS_PAR_JOUR;
    /** Descentes et Fils regardés toutes les 10 secondes. */
    private static final int PERIODE_CELLULES = 200;

    /** Salle de 5 blocs de large, 3 de haut, 5 de profondeur ; murs, sol et toit d'un bloc. */
    private static final int DEMI_LARGEUR = 2;
    private static final int HAUTEUR = 3;
    private static final int PROFONDEUR = 5;
    private static final int PERIODE_SURVEILLANCE = 10;
    /** Particules du repère toutes les 2 secondes, pour les joueurs admis à moins de 12 blocs. */
    private static final int PERIODE_REPERE = 40;
    private static final double PORTEE_REPERE = 12;
    private static final DustParticleOptions REPERE = new DustParticleOptions(0xC0181A, 1.0f);

    /**
     * Passages demandés pendant ce tick : joueur → (position d'arrivée, regard). Appliqués en fin de tick : le client envoie
     * le clic de la main secondaire juste après celui de la main principale ; s'il arrive après la téléportation, Minecraft
     * le refuse avec le message « limite de hauteur » (le serveur attend que le client confirme sa nouvelle position).
     */
    private static final Map<UUID, Passage> PASSAGES = new HashMap<>();

    private record Passage(BlockPos arrivee, Direction regard) {
    }

    private CelluleRising() {
    }

    public static List<Cellule> cellules(MinecraftServer server) {
        return server.globalAttachments().getAttachedOrElse(CELLULES, List.of());
    }

    /** Appelé depuis {@link RedRising#onInitialize()}. */
    public static void init() {
        UseBlockCallback.EVENT.register((joueur, monde, main, visee) -> {
            // Le client ne sait pas où sont les cellules : il laisse passer, et le serveur décide
            if (!(joueur instanceof ServerPlayer serveur) || main != InteractionHand.MAIN_HAND || joueur.isSpectator()
                    || !joueur.isShiftKeyDown() || !joueur.getMainHandItem().isEmpty()) {
                return InteractionResult.PASS;
            }
            Cellule cellule = celluleDeLEntree(serveur.level().getServer(), monde, visee.getBlockPos());
            if (cellule == null) {
                return InteractionResult.PASS;
            }
            if (cellule.interieur().isInside(serveur.blockPosition())) {
                sortir(serveur, cellule);
                return InteractionResult.SUCCESS;
            }
            if (!admis(serveur)) {
                // Silencieux : le joueur refusé ne doit pas apprendre qu'il y a une cellule
                return InteractionResult.PASS;
            }
            TraqueGris.entreDansCellule(serveur, cellule);
            entrer(serveur, cellule);
            return InteractionResult.SUCCESS;
        });

        // Revenu après une descente sur une cellule qu'il avait fait repérer
        ServerPlayConnectionEvents.JOIN.register((connexion, envoi, server) -> {
            ServerPlayer joueur = connexion.getPlayer();
            int peines = peinesEnAttente(server, joueur.getUUID());
            if (peines == 0) {
                return;
            }
            Map<UUID, Integer> attente = new HashMap<>(server.globalAttachments().getAttachedOrElse(PEINES_EN_ATTENTE, Map.of()));
            attente.remove(joueur.getUUID());
            server.globalAttachments().setAttached(PEINES_EN_ATTENTE, attente);
            joueur.sendOverlayMessage(Component.translatable("faction.redrising.cellule.tombee_absent"));
            for (int i = 0; i < peines; i++) {
                ActionsFaction.agir(joueur, "a mené les Gris à une cellule (tombée en son absence)", EffetsActions.celluleReperee());
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PASSAGES.forEach((id, passage) -> {
                ServerPlayer joueur = server.getPlayerList().getPlayer(id);
                if (joueur != null) {
                    teleporter(joueur, passage.arrivee(), passage.regard());
                }
            });
            PASSAGES.clear();
            int tick = server.getTickCount();
            if (tick % PERIODE_SURVEILLANCE != 0) {
                return;
            }
            List<Cellule> cellules = cellules(server);
            if (cellules.isEmpty()) {
                return;
            }
            ServerLevel monde = server.overworld();
            if (tick % PERIODE_CELLULES == 0) {
                entretenir(monde, cellules);
                cellules = cellules(server);
            }
            for (ServerPlayer joueur : monde.players()) {
                if (joueur.isSpectator()) {
                    continue;
                }
                boolean admis = admis(joueur);
                for (Cellule cellule : cellules) {
                    if (cellule.interieur().isInside(joueur.blockPosition())) {
                        // Par l'entrée ou en creusant : il la connaît désormais
                        connaitre(joueur, cellule);
                        if (!admis) {
                            mettreDehors(joueur, cellule);
                            break;
                        }
                    }
                    if (admis && tick % PERIODE_REPERE == 0 && joueur.distanceToSqr(Vec3.atCenterOf(cellule.entree())) <= PORTEE_REPERE * PORTEE_REPERE) {
                        montrerLeRepere(monde, joueur, cellule);
                    }
                }
            }
        });
    }

    private static @Nullable Cellule celluleDeLEntree(MinecraftServer server, Level monde, BlockPos pos) {
        if (monde.dimension() != Level.OVERWORLD) {
            return null;
        }
        for (Cellule cellule : cellules(server)) {
            if (cellule.estLEntree(pos)) {
                return cellule;
            }
        }
        return null;
    }

    private static void entrer(ServerPlayer joueur, Cellule cellule) {
        connaitre(joueur, cellule);
        PASSAGES.put(joueur.getUUID(), new Passage(cellule.dedans(), cellule.versSalle()));
        RedRising.LOGGER.info("{} entre dans une cellule du Rising (Soutien {})", joueur.getPlainTextName(), JaugesJoueur.get(joueur).soutien());
    }

    /** Sortie par le geste : en fin de tick, comme l'entrée. */
    private static void sortir(ServerPlayer joueur, Cellule cellule) {
        PASSAGES.put(joueur.getUUID(), new Passage(cellule.dehors(), cellule.versSalle().getOpposite()));
    }

    /** Pas de clic en jeu ici : téléportation immédiate. */
    private static void mettreDehors(ServerPlayer joueur, Cellule cellule) {
        teleporter(joueur, cellule.dehors(), cellule.versSalle().getOpposite());
        joueur.sendOverlayMessage(Component.translatable("structure.redrising.cellule.dehors"));
        RedRising.LOGGER.info("{} mis hors d'une cellule du Rising (Soutien {})", joueur.getPlainTextName(), JaugesJoueur.get(joueur).soutien());
    }

    private static void teleporter(ServerPlayer joueur, BlockPos pos, Direction regard) {
        joueur.teleport(new TeleportTransition(joueur.level(), Vec3.atBottomCenterOf(pos), Vec3.ZERO, regard.toYRot(), 0, TeleportTransition.DO_NOTHING));
    }

    /** Particules rouges sur les deux faces de l'entrée, envoyées à ce joueur seulement. */
    private static void montrerLeRepere(ServerLevel monde, ServerPlayer joueur, Cellule cellule) {
        Vec3 centre = Vec3.atBottomCenterOf(cellule.entree()).add(0, 1, 0);
        Vec3 decalage = cellule.versSalle().getUnitVec3().scale(0.6);
        for (Vec3 face : new Vec3[]{centre.subtract(decalage), centre.add(decalage)}) {
            monde.sendParticles(joueur, REPERE, false, false, face.x, face.y, face.z, 6, 0.25, 0.6, 0.25, 0);
        }
    }

    /**
     * Construit une cellule et l'enregistre. {@code entree} est la moitié basse de l'entrée cachée,
     * {@code versSalle} la direction dans laquelle on entre (la salle est derrière le mur, de ce côté).
     * Tout le volume est rempli de pierre, puis la salle est creusée : dans une mine, la cellule se fond dans la roche.
     */
    public static Cellule construire(ServerLevel monde, BlockPos entree, Direction versSalle) {
        Direction droite = versSalle.getClockWise();
        int drapeaux = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        // Repère local : a vers la droite, h vers le haut (0 = sol de la salle), p vers le fond (0 = mur de l'entrée)
        for (int a = -DEMI_LARGEUR - 1; a <= DEMI_LARGEUR + 1; a++) {
            for (int h = -1; h <= HAUTEUR; h++) {
                for (int p = 0; p <= PROFONDEUR + 1; p++) {
                    boolean salle = Math.abs(a) <= DEMI_LARGEUR && h >= 0 && h < HAUTEUR && p >= 1 && p <= PROFONDEUR;
                    boolean solDeLaSalle = Math.abs(a) <= DEMI_LARGEUR && h == -1 && p >= 1 && p <= PROFONDEUR;
                    BlockState bloc = salle ? Blocks.AIR.defaultBlockState()
                            : solDeLaSalle ? Blocks.DEEPSLATE_TILES.defaultBlockState()
                            : MineLykos.rocheIci(monde, local(entree, droite, versSalle, a, h, p));
                    monde.setBlock(local(entree, droite, versSalle, a, h, p), bloc, drapeaux);
                }
            }
        }
        BlockState lanterne = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        monde.setBlock(local(entree, droite, versSalle, -1, HAUTEUR - 1, 2), lanterne, drapeaux);
        monde.setBlock(local(entree, droite, versSalle, 1, HAUTEUR - 1, PROFONDEUR - 1), lanterne, drapeaux);

        // Au fond : le coffre du Rising à droite, la planque du joueur (sans camp) à gauche
        BlockPos coffre = local(entree, droite, versSalle, DEMI_LARGEUR, 0, PROFONDEUR);
        monde.setBlock(coffre, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        BlockEntity blocCoffre = monde.getBlockEntity(coffre);
        if (blocCoffre != null) {
            blocCoffre.setAttached(ActionsFaction.CAMP, Camp.RISING);
            blocCoffre.setChanged();
        }
        BlockPos planque = local(entree, droite, versSalle, -DEMI_LARGEUR, 0, PROFONDEUR);
        monde.setBlock(planque, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        BlockEntity blocPlanque = monde.getBlockEntity(planque);
        if (blocPlanque != null) {
            blocPlanque.setAttached(PLANQUE, true);
            blocPlanque.setChanged();
        }

        BoundingBox interieur = BoundingBox.fromCorners(local(entree, droite, versSalle, -DEMI_LARGEUR, 0, 1),
                local(entree, droite, versSalle, DEMI_LARGEUR, HAUTEUR - 1, PROFONDEUR));
        Cellule cellule = new Cellule(interieur, entree, versSalle, false, -1, -1, Optional.empty());
        installerLeFils(monde, cellule);
        List<Cellule> cellules = new ArrayList<>(cellules(monde.getServer()));
        cellules.add(cellule);
        monde.getServer().globalAttachments().setAttached(CELLULES, cellules);
        RedRising.LOGGER.info("Cellule du Rising construite, entrée en {} (vers {})", entree.toShortString(), versSalle.getSerializedName());
        return cellule;
    }

    private static BlockPos local(BlockPos entree, Direction droite, Direction fond, int a, int h, int p) {
        return entree.relative(droite, a).above(h).relative(fond, p);
    }

    /** Le Fils (PNJ Rouge du Rising, qui tient la boutique) au fond de la salle, tourné vers l'entrée. */
    private static void installerLeFils(ServerLevel monde, Cellule cellule) {
        Direction droite = cellule.versSalle().getClockWise();
        Villager fils = PnjCouleur.creer(monde, Vec3.atBottomCenterOf(local(cellule.entree(), droite, cellule.versSalle(), 0, 0, PROFONDEUR - 1)),
                cellule.versSalle().getOpposite().toYRot(), Couleur.ROUGE);
        if (fils != null) {
            fils.setAttached(ActionsFaction.CAMP, Camp.RISING);
            fils.setAttached(Boutiques.BOUTIQUE, Boutique.RISING);
            fils.setNoAi(true);
        }
    }

    /**
     * Un chasseur a vu un joueur traqué entrer dans cette cellule (G5.6) : elle est repérée, et la Société y fera une
     * descente un jour de jeu plus tard (G5.10). Le temps de vider sa planque. Le joueur ne perd son Soutien qu'à la
     * descente : sur le moment, le Rising ne le met pas dehors, sous les yeux des chasseurs.
     */
    public static void marquerReperee(MinecraftServer server, Cellule cellule, ServerPlayer joueur) {
        long descente = server.overworld().getGameTime() + DELAI_DESCENTE;
        remplacer(server, cellule, c -> c.reperee() ? c : new Cellule(c.interieur(), c.entree(), c.versSalle(), true,
                descente, c.sansFils(), Optional.of(joueur.getUUID())));
    }

    private static void remplacer(MinecraftServer server, Cellule cellule, UnaryOperator<Cellule> changement) {
        List<Cellule> cellules = new ArrayList<>(cellules(server));
        cellules.replaceAll(c -> c.entree().equals(cellule.entree()) ? changement.apply(c) : c);
        server.globalAttachments().setAttached(CELLULES, cellules);
    }

    // --- G5.10 : connaître, dénoncer, tomber ---

    /** Admis dans les cellules : assez de Soutien, et pas en quarantaine. */
    public static boolean admis(ServerPlayer joueur) {
        return Acces.peutEntrerRising(JaugesJoueur.get(joueur)) && !enQuarantaine(joueur);
    }

    public static boolean enQuarantaine(ServerPlayer joueur) {
        Long fin = joueur.getAttached(QUARANTAINE);
        return fin != null && joueur.level().getServer().overworld().getGameTime() < fin;
    }

    /** Le Rising refuse ce joueur dans toutes ses cellules (et leurs boutiques) pendant {@link #DUREE_QUARANTAINE}. */
    public static void mettreEnQuarantaine(ServerPlayer joueur) {
        joueur.setAttached(QUARANTAINE, joueur.level().getServer().overworld().getGameTime() + DUREE_QUARANTAINE);
    }

    private static void connaitre(ServerPlayer joueur, Cellule cellule) {
        List<BlockPos> connues = joueur.getAttachedOrElse(CONNUES, List.of());
        if (!connues.contains(cellule.entree())) {
            List<BlockPos> suite = new ArrayList<>(connues);
            suite.add(cellule.entree());
            joueur.setAttached(CONNUES, suite);
            RedRising.LOGGER.info("{} connaît désormais la cellule en {}", joueur.getPlainTextName(), cellule.entree().toShortString());
        }
    }

    /** Les cellules (encore debout) que ce joueur connaît. */
    public static List<Cellule> connues(ServerPlayer joueur) {
        List<BlockPos> entrees = joueur.getAttachedOrElse(CONNUES, List.of());
        return cellules(joueur.level().getServer()).stream().filter(cellule -> entrees.contains(cellule.entree())).toList();
    }

    /** La cellule connue de ce joueur la plus proche de {@code depuis}, à moins de {@code portee} blocs, ou {@code null}. */
    public static @Nullable Cellule connueLaPlusProche(ServerPlayer joueur, Vec3 depuis, double portee) {
        if (joueur.level().dimension() != Level.OVERWORLD) {
            return null;
        }
        return connues(joueur).stream()
                .filter(cellule -> Vec3.atCenterOf(cellule.entree()).distanceTo(depuis) <= portee)
                .min(Comparator.comparingDouble(cellule -> Vec3.atCenterOf(cellule.entree()).distanceToSqr(depuis)))
                .orElse(null);
    }

    /** Ce PNJ se trouve-t-il dans une cellule ? (la dénonciation à mains vides ne l'atteint pas) */
    public static boolean dansUneCellule(Villager pnj) {
        return pnj.level().dimension() == Level.OVERWORLD
                && cellules(pnj.level().getServer()).stream().anyMatch(cellule -> cellule.interieur().isInside(pnj.blockPosition()));
    }

    /**
     * La cellule tombe (dénoncée, ou descente sur une cellule repérée) : ses PNJ sont arrêtés (ils disparaissent, la boutique
     * avec eux), le coffre du Rising et la planque sont saisis (vidés, sans camp), les lanternes retirées, l'entrée défoncée.
     * Il reste un trou et une salle vide et sombre, où l'on entre librement. La cellule sort de la liste.
     *
     * @return les personnages canon touchés (dans la salle, ou leur foyer y était), pour leur attitude (G6.5)
     */
    public static Set<String> tomber(ServerLevel monde, Cellule cellule, String cause) {
        int drapeaux = Block.UPDATE_ALL;
        Set<String> touches = PnjEssentiels.personnagesDe(monde, cellule.interieur());
        for (Villager pnj : monde.getEntities(EntityTypes.VILLAGER, AABB.of(cellule.interieur()).inflate(0.5), pnj -> true)) {
            if (PnjEssentiels.essentiel(pnj)) {
                // Un personnage essentiel n'est pas arrêté (G6.4) : il s'échappe vers son foyer, ou part en fuite
                PnjEssentiels.echapper(pnj, cellule.interieur());
            } else {
                pnj.discard();
            }
        }
        BlockPos.betweenClosedStream(cellule.interieur()).forEach(pos -> {
            BlockEntity bloc = monde.getBlockEntity(pos);
            if (bloc instanceof Container coffre) {
                coffre.clearContent();
                bloc.removeAttached(ActionsFaction.CAMP);
                bloc.removeAttached(PLANQUE);
                bloc.setChanged();
            }
            if (monde.getBlockState(pos).is(Blocks.LANTERN)) {
                monde.setBlock(pos, Blocks.AIR.defaultBlockState(), drapeaux);
            }
        });
        monde.setBlock(cellule.entree(), Blocks.AIR.defaultBlockState(), drapeaux);
        monde.setBlock(cellule.entree().above(), Blocks.AIR.defaultBlockState(), drapeaux);
        List<Cellule> cellules = new ArrayList<>(cellules(monde.getServer()));
        cellules.removeIf(c -> c.entree().equals(cellule.entree()));
        monde.getServer().globalAttachments().setAttached(CELLULES, cellules);
        RedRising.LOGGER.info("Cellule du Rising en {} tombée ({})", cellule.entree().toShortString(), cause);
        return touches;
    }

    /** Celui qui a mené les Gris à une cellule tombée perd du Soutien : tout de suite s'il est là, sinon à son retour. */
    private static void punirReperePar(MinecraftServer server, UUID id) {
        ServerPlayer joueur = server.getPlayerList().getPlayer(id);
        if (joueur != null) {
            ActionsFaction.agir(joueur, "a mené les Gris à une cellule", EffetsActions.celluleReperee());
        } else {
            mettreEnAttente(server, id);
        }
    }

    /** Une peine de descente due à ce joueur hors ligne, appliquée à son retour (public pour le test : en solo, le joueur ne peut pas être absent). */
    public static void mettreEnAttente(MinecraftServer server, UUID id) {
        Map<UUID, Integer> attente = new HashMap<>(server.globalAttachments().getAttachedOrElse(PEINES_EN_ATTENTE, Map.of()));
        attente.merge(id, 1, Integer::sum);
        server.globalAttachments().setAttached(PEINES_EN_ATTENTE, attente);
        RedRising.LOGGER.info("Descente : {} est hors ligne, peine due à son retour", id);
    }

    /** Nombre de peines de descente dues à ce joueur, appliquées à son retour. */
    public static int peinesEnAttente(MinecraftServer server, UUID id) {
        return server.globalAttachments().getAttachedOrElse(PEINES_EN_ATTENTE, Map.of()).getOrDefault(id, 0);
    }

    /** Toutes les 10 secondes : descentes dues, et Fils remplacé après {@link #DELAI_REMPLACANT} sans Fils. */
    private static void entretenir(ServerLevel monde, List<Cellule> cellules) {
        long maintenant = monde.getGameTime();
        for (Cellule cellule : cellules) {
            if (cellule.descente() >= 0 && maintenant >= cellule.descente()) {
                Set<String> touches = tomber(monde, cellule, "descente de la Société");
                // Celui qui a mené les Gris à la cellule : le Rising le sait, tout de suite ou à son retour
                cellule.reperePar().ifPresent(id -> {
                    punirReperePar(monde.getServer(), id);
                    AttitudesPnj.celluleTombee(monde.getServer(), touches, id, Attitude.CELLULE_REPEREE, "a mené les Gris à sa cellule");
                });
                continue;
            }
            // Seulement si les PNJ de la salle sont chargés : sinon on ne sait pas si le Fils est là
            if (!monde.isPositionEntityTicking(cellule.dedans())) {
                continue;
            }
            boolean filsLa = !monde.getEntities(EntityTypes.VILLAGER, AABB.of(cellule.interieur()).inflate(0.5),
                    pnj -> pnj.isAlive() && pnj.getAttached(Boutiques.BOUTIQUE) == Boutique.RISING).isEmpty();
            if (filsLa) {
                if (cellule.sansFils() >= 0) {
                    remplacer(monde.getServer(), cellule, c -> c.avecSansFils(-1));
                }
            } else if (cellule.sansFils() < 0) {
                remplacer(monde.getServer(), cellule, c -> c.avecSansFils(maintenant));
            } else if (maintenant - cellule.sansFils() >= DELAI_REMPLACANT) {
                installerLeFils(monde, cellule);
                remplacer(monde.getServer(), cellule, c -> c.avecSansFils(-1));
                RedRising.LOGGER.info("Cellule en {} : un nouveau Fils est arrivé", cellule.entree().toShortString());
            }
        }
    }

    /** La cellule qui contient cette position (salle et murs), ou {@code null}. */
    public static @Nullable Cellule contenant(MinecraftServer server, BlockPos pos) {
        return cellules(server).stream().filter(cellule -> cellule.interieur().inflatedBy(1).isInside(pos)).findFirst().orElse(null);
    }

    /** Oublie la cellule qui contient cette position (les blocs restent). Renvoie {@code false} s'il n'y en a pas. */
    public static boolean oublier(MinecraftServer server, BlockPos pos) {
        List<Cellule> cellules = new ArrayList<>(cellules(server));
        boolean retire = cellules.removeIf(cellule -> cellule.interieur().inflatedBy(1).isInside(pos));
        if (retire) {
            server.globalAttachments().setAttached(CELLULES, cellules);
        }
        return retire;
    }
}
