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
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Poste de la Société (G5.3) : une petite salle en quartz avec un comptoir, un PNJ Cuivre et un coffre de la Société.
 * On n'y entre qu'avec assez de Loyauté ({@link Acces#SEUIL_LOYAUTE_SOCIETE}).
 *
 * <ul>
 *     <li><b>Porte</b> : elle ne s'ouvre pas pour un joueur refusé.</li>
 *     <li><b>Intérieur surveillé</b> : un joueur refusé qui s'y trouve quand même (mur percé, fenêtre, ou Loyauté tombée
 *     une fois dedans) est reconduit devant l'entrée.</li>
 * </ul>
 *
 * <p>Pour l'instant, un poste est construit par le code ({@code /rr structure poste}) ; la génération du monde
 * les placera à partir de G7.1. Les postes construits sont enregistrés avec le monde (attachment global),
 * pour rester surveillés après un rechargement. Postes dans l'Overworld seulement (Mars, en v1).</p>
 */
public final class PosteSociete {
    /**
     * Un poste enregistré.
     *
     * @param interieur la salle (sans les murs) : un joueur refusé n'a rien à y faire
     * @param porte     moitié basse de la porte
     * @param sortie    où reconduire un joueur refusé : devant la porte, dehors
     */
    public record Poste(BoundingBox interieur, BlockPos porte, BlockPos sortie) {
        public static final Codec<Poste> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BoundingBox.CODEC.fieldOf("interieur").forGetter(Poste::interieur),
                BlockPos.CODEC.fieldOf("porte").forGetter(Poste::porte),
                BlockPos.CODEC.fieldOf("sortie").forGetter(Poste::sortie)
        ).apply(instance, Poste::new));

        public boolean estLaPorte(BlockPos pos) {
            return pos.equals(porte) || pos.equals(porte.above());
        }
    }

    private static final AttachmentType<List<Poste>> POSTES = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "postes_societe"),
            builder -> builder.persistent(Poste.CODEC.listOf())
    );

    /** Côté extérieur (7×7 blocs au sol, 6 de haut avec le toit). */
    private static final int COTE = 7;
    private static final int HAUTEUR_MURS = 4;
    /** Surveillance de l'intérieur toutes les 10 ticks (une demi-seconde). */
    private static final int PERIODE_SURVEILLANCE = 10;

    private PosteSociete() {
    }

    public static List<Poste> postes(MinecraftServer server) {
        return server.globalAttachments().getAttachedOrElse(POSTES, List.of());
    }

    /** Appelé depuis {@link RedRising#onInitialize()}. */
    public static void init() {
        UseBlockCallback.EVENT.register((joueur, monde, main, visee) -> {
            if (!(joueur instanceof ServerPlayer serveur) || joueur.isSpectator()) {
                return InteractionResult.PASS;
            }
            Poste poste = posteDeLaPorte(serveur.level().getServer(), monde, visee.getBlockPos());
            if (poste == null || Acces.peutEntrerSociete(JaugesJoueur.get(serveur))) {
                return InteractionResult.PASS;
            }
            refuser(serveur);
            // Le client a déjà ouvert la porte de son côté : on lui renvoie l'état réel des deux moitiés
            serveur.connection.send(new ClientboundBlockUpdatePacket(monde, poste.porte()));
            serveur.connection.send(new ClientboundBlockUpdatePacket(monde, poste.porte().above()));
            return InteractionResult.FAIL;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % PERIODE_SURVEILLANCE != 0) {
                return;
            }
            List<Poste> postes = postes(server);
            if (postes.isEmpty()) {
                return;
            }
            for (ServerPlayer joueur : server.overworld().players()) {
                if (joueur.isSpectator() || Acces.peutEntrerSociete(JaugesJoueur.get(joueur))) {
                    continue;
                }
                for (Poste poste : postes) {
                    if (poste.interieur().isInside(joueur.blockPosition())) {
                        reconduire(joueur, poste);
                        break;
                    }
                }
            }
        });
    }

    private static @Nullable Poste posteDeLaPorte(MinecraftServer server, Level monde, BlockPos pos) {
        if (monde.dimension() != Level.OVERWORLD) {
            return null;
        }
        for (Poste poste : postes(server)) {
            if (poste.estLaPorte(pos)) {
                return poste;
            }
        }
        return null;
    }

    private static void refuser(ServerPlayer joueur) {
        joueur.sendOverlayMessage(Component.translatable("structure.redrising.poste.refuse"));
    }

    /** Devant la porte, dos au poste. */
    private static void reconduire(ServerPlayer joueur, Poste poste) {
        Vec3 sortie = Vec3.atBottomCenterOf(poste.sortie());
        joueur.teleport(new TeleportTransition(joueur.level(), sortie, Vec3.ZERO, 180, 0, TeleportTransition.DO_NOTHING));
        refuser(joueur);
        RedRising.LOGGER.info("{} reconduit hors d'un poste de la Société (Loyauté {})", joueur.getPlainTextName(), JaugesJoueur.get(joueur).loyaute());
    }

    /**
     * Construit un poste dont le coin nord-ouest du sol est en {@code origine}, et l'enregistre.
     * La porte est au milieu du mur nord ; on entre en allant vers le sud.
     */
    public static Poste construire(ServerLevel monde, BlockPos origine) {
        int x0 = origine.getX();
        int y0 = origine.getY();
        int z0 = origine.getZ();
        int fin = COTE - 1;
        int toit = y0 + HAUTEUR_MURS + 1;
        // Sans mises à jour entre voisins pendant la construction (sinon la porte, posée en deux fois, se casse)
        int drapeaux = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int dx = 0; dx <= fin; dx++) {
            for (int dz = 0; dz <= fin; dz++) {
                boolean bord = dx == 0 || dz == 0 || dx == fin || dz == fin;
                monde.setBlock(new BlockPos(x0 + dx, y0, z0 + dz), Blocks.POLISHED_ANDESITE.defaultBlockState(), drapeaux);
                monde.setBlock(new BlockPos(x0 + dx, toit, z0 + dz), Blocks.SMOOTH_QUARTZ.defaultBlockState(), drapeaux);
                for (int y = y0 + 1; y < toit; y++) {
                    BlockState bloc = bord ? Blocks.QUARTZ_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    monde.setBlock(new BlockPos(x0 + dx, y, z0 + dz), bloc, drapeaux);
                }
            }
        }
        int milieu = COTE / 2;
        // Fenêtres à l'est et à l'ouest, lumière au plafond
        for (int y = y0 + 2; y <= y0 + 3; y++) {
            monde.setBlock(new BlockPos(x0, y, z0 + milieu), Blocks.GLASS_PANE.defaultBlockState(), drapeaux);
            monde.setBlock(new BlockPos(x0 + fin, y, z0 + milieu), Blocks.GLASS_PANE.defaultBlockState(), drapeaux);
        }
        monde.setBlock(new BlockPos(x0 + milieu, toit, z0 + milieu), Blocks.GLOWSTONE.defaultBlockState(), drapeaux);
        // Porte au nord
        BlockPos porte = new BlockPos(x0 + milieu, y0 + 1, z0);
        BlockState bas = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        monde.setBlock(porte, bas, drapeaux);
        monde.setBlock(porte.above(), bas.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), drapeaux);
        // Comptoir, avec le PNJ Cuivre derrière, et le coffre de la Société dans un coin
        for (int dx = milieu - 1; dx <= milieu + 1; dx++) {
            monde.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + milieu), Blocks.QUARTZ_SLAB.defaultBlockState(), drapeaux);
        }
        BlockPos coffre = new BlockPos(x0 + fin - 1, y0 + 1, z0 + fin - 1);
        monde.setBlock(coffre, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        BlockEntity blocCoffre = monde.getBlockEntity(coffre);
        if (blocCoffre != null) {
            blocCoffre.setAttached(ActionsFaction.CAMP, Camp.SOCIETE);
            blocCoffre.setChanged();
        }
        Villager commis = PnjCouleur.creer(monde, Vec3.atBottomCenterOf(new BlockPos(x0 + milieu, y0 + 1, z0 + milieu + 1)), 180, Couleur.CUIVRE);
        if (commis != null) {
            commis.setAttached(ActionsFaction.CAMP, Camp.SOCIETE);
            commis.setNoAi(true); // reste à son comptoir
        }

        Poste poste = new Poste(new BoundingBox(x0 + 1, y0 + 1, z0 + 1, x0 + fin - 1, toit - 1, z0 + fin - 1),
                porte, porte.north(2));
        List<Poste> postes = new ArrayList<>(postes(monde.getServer()));
        postes.add(poste);
        monde.getServer().globalAttachments().setAttached(POSTES, postes);
        RedRising.LOGGER.info("Poste de la Société construit en {}", origine.toShortString());
        return poste;
    }

    /** Oublie le poste qui contient cette position (les blocs restent). Renvoie {@code false} s'il n'y en a pas. */
    public static boolean oublier(MinecraftServer server, BlockPos pos) {
        List<Poste> postes = new ArrayList<>(postes(server));
        boolean retire = postes.removeIf(poste -> poste.interieur().inflatedBy(1).isInside(pos));
        if (retire) {
            server.globalAttachments().setAttached(POSTES, postes);
        }
        return retire;
    }
}
