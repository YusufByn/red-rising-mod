package io.github.yusufbyn.redrising.monde;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.factions.Acces;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import io.github.yusufbyn.redrising.pnj.TraqueGris;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
     * @param reperee   un chasseur a vu un joueur traqué y entrer (G5.6) ; ce qu'il en advient relève de G5.10
     */
    public record Cellule(BoundingBox interieur, BlockPos entree, Direction versSalle, boolean reperee) {
        public static final Codec<Cellule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BoundingBox.CODEC.fieldOf("interieur").forGetter(Cellule::interieur),
                BlockPos.CODEC.fieldOf("entree").forGetter(Cellule::entree),
                Direction.CODEC.fieldOf("vers_salle").forGetter(Cellule::versSalle),
                Codec.BOOL.optionalFieldOf("reperee", false).forGetter(Cellule::reperee)
        ).apply(instance, Cellule::new));

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
            if (!Acces.peutEntrerRising(JaugesJoueur.get(serveur))) {
                // Silencieux : le joueur refusé ne doit pas apprendre qu'il y a une cellule
                return InteractionResult.PASS;
            }
            TraqueGris.entreDansCellule(serveur, cellule);
            entrer(serveur, cellule);
            return InteractionResult.SUCCESS;
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
            for (ServerPlayer joueur : monde.players()) {
                if (joueur.isSpectator()) {
                    continue;
                }
                boolean admis = Acces.peutEntrerRising(JaugesJoueur.get(joueur));
                for (Cellule cellule : cellules) {
                    if (!admis && cellule.interieur().isInside(joueur.blockPosition())) {
                        mettreDehors(joueur, cellule);
                        break;
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
                            : Blocks.STONE.defaultBlockState();
                    monde.setBlock(local(entree, droite, versSalle, a, h, p), bloc, drapeaux);
                }
            }
        }
        BlockState lanterne = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        monde.setBlock(local(entree, droite, versSalle, -1, HAUTEUR - 1, 2), lanterne, drapeaux);
        monde.setBlock(local(entree, droite, versSalle, 1, HAUTEUR - 1, PROFONDEUR - 1), lanterne, drapeaux);

        BlockPos coffre = local(entree, droite, versSalle, DEMI_LARGEUR, 0, PROFONDEUR);
        monde.setBlock(coffre, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        BlockEntity blocCoffre = monde.getBlockEntity(coffre);
        if (blocCoffre != null) {
            blocCoffre.setAttached(ActionsFaction.CAMP, Camp.RISING);
            blocCoffre.setChanged();
        }
        Villager fils = PnjCouleur.creer(monde, Vec3.atBottomCenterOf(local(entree, droite, versSalle, 0, 0, PROFONDEUR - 1)),
                versSalle.getOpposite().toYRot(), Couleur.ROUGE);
        if (fils != null) {
            fils.setAttached(ActionsFaction.CAMP, Camp.RISING);
            fils.setNoAi(true);
        }

        BoundingBox interieur = BoundingBox.fromCorners(local(entree, droite, versSalle, -DEMI_LARGEUR, 0, 1),
                local(entree, droite, versSalle, DEMI_LARGEUR, HAUTEUR - 1, PROFONDEUR));
        Cellule cellule = new Cellule(interieur, entree, versSalle, false);
        List<Cellule> cellules = new ArrayList<>(cellules(monde.getServer()));
        cellules.add(cellule);
        monde.getServer().globalAttachments().setAttached(CELLULES, cellules);
        RedRising.LOGGER.info("Cellule du Rising construite, entrée en {} (vers {})", entree.toShortString(), versSalle.getSerializedName());
        return cellule;
    }

    private static BlockPos local(BlockPos entree, Direction droite, Direction fond, int a, int h, int p) {
        return entree.relative(droite, a).above(h).relative(fond, p);
    }

    /** Un chasseur a vu un joueur traqué entrer dans cette cellule (G5.6). */
    public static void marquerReperee(MinecraftServer server, Cellule cellule) {
        List<Cellule> cellules = new ArrayList<>(cellules(server));
        cellules.replaceAll(c -> c.entree().equals(cellule.entree()) ? new Cellule(c.interieur(), c.entree(), c.versSalle(), true) : c);
        server.globalAttachments().setAttached(CELLULES, cellules);
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
