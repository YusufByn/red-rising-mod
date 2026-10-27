package io.github.yusufbyn.redrising.factions;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.pnj.PatrouillePnj;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import io.github.yusufbyn.redrising.pnj.TraqueGris;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Chausse-trappes (G5.8) : qui les déclenche, ce qu'elles font, qui les reprend.
 *
 * <ul>
 *     <li>Se déclenchent pour un joueur (poseur compris ; ni créatif ni spectateur) ou un PNJ de Couleur, pas pour les
 *     monstres ni les animaux (sinon, dans les mines, araignées et zombies videraient les pièges).</li>
 *     <li>Ralentissent 3 secondes, puis disparaissent.</li>
 *     <li>Un Gris qui s'y prend fouille autour : un chasseur repart de là, une patrouille fouille 20 secondes
 *     avant de reprendre sa ronde.</li>
 *     <li>Au plus {@value RegistreChausseTrappes#MAX_PAR_JOUEUR} par joueur ; le poseur reprend la sienne en la cassant,
 *     cassée par un autre, elle est perdue.</li>
 * </ul>
 */
public final class ChausseTrappes {
    /** Registre de toutes les chausse-trappes posées, enregistré avec le monde. */
    private static final AttachmentType<RegistreChausseTrappes> REGISTRE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "chausse_trappes"),
            builder -> builder.persistent(RegistreChausseTrappes.CODEC)
    );

    /** 🟡 Ralentissement : 3 secondes de Lenteur III. */
    public static final int RALENTISSEMENT = 60;
    private static final int AMPLIFICATEUR = 2;

    private ChausseTrappes() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()}. */
    public static void init() {
        PlayerBlockBreakEvents.AFTER.register((monde, joueur, pos, etat, blocEntite) -> {
            if (!(monde instanceof ServerLevel serveur) || !etat.is(ObjetsRebelles.CHAUSSE_TRAPPE)) {
                return;
            }
            RegistreChausseTrappes registre = registre(serveur.getServer());
            RegistreChausseTrappes.Piege piege = registre.a(serveur.dimension(), pos);
            if (piege == null) {
                return;
            }
            enregistrer(serveur.getServer(), registre.retirer(serveur.dimension(), pos));
            if (piege.poseur().equals(joueur.getUUID()) && !joueur.isCreative()) {
                Block.popResource(serveur, pos, new ItemStack(ObjetsRebelles.CHAUSSE_TRAPPE_OBJET));
            }
        });
    }

    /** Posée par un joueur : inscrite au registre ; s'il en a déjà trop, ses plus anciennes disparaissent. */
    static void posee(ServerLevel monde, BlockPos pos, ServerPlayer poseur) {
        Contrebande.utilisee(poseur);
        MinecraftServer server = monde.getServer();
        RegistreChausseTrappes.Pose pose = registre(server).poser(
                new RegistreChausseTrappes.Piege(monde.dimension(), pos.immutable(), poseur.getUUID(), monde.getGameTime()));
        enregistrer(server, pose.registre());
        for (RegistreChausseTrappes.Piege ancienne : pose.retirees()) {
            retirerLeBloc(server, ancienne);
        }
    }

    /** Quelqu'un marche dessus : si elle se déclenche pour lui, il est ralenti et elle disparaît. */
    static void marcheDessus(ServerLevel monde, BlockPos pos, LivingEntity victime) {
        if (!seDeclenchePour(victime)) {
            return;
        }
        victime.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, RALENTISSEMENT, AMPLIFICATEUR));
        monde.removeBlock(pos, false);
        monde.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1, 0.8f);
        enregistrer(monde.getServer(), registre(monde.getServer()).retirer(monde.dimension(), pos));
        if (victime instanceof Villager gris && PnjCouleur.couleur(gris) == Couleur.GRIS) {
            // Un rebelle est passé par là : le Gris fouille autour
            if (gris.hasAttached(TraqueGris.CHASSEUR)) {
                TraqueGris.piegeDeclenche(gris, pos);
            } else {
                PatrouillePnj.piegeDeclenche(gris, pos);
            }
        }
        RedRising.LOGGER.info("Chausse-trappe déclenchée en {} par {}", pos.toShortString(), victime.getPlainTextName());
    }

    /** Joueurs (ni créatif ni spectateur) et PNJ de Couleur ; pas les monstres ni les animaux. */
    public static boolean seDeclenchePour(LivingEntity entite) {
        if (entite instanceof ServerPlayer joueur) {
            return !joueur.isSpectator() && !joueur.isCreative();
        }
        return entite instanceof Villager pnj && PnjCouleur.couleur(pnj) != null;
    }

    public static RegistreChausseTrappes registre(MinecraftServer server) {
        return server.globalAttachments().getAttachedOrElse(REGISTRE, RegistreChausseTrappes.VIDE);
    }

    /** Retire les chausse-trappes d'un poseur, ou toutes (debug) ; renvoie leur nombre. */
    public static int effacer(MinecraftServer server, @Nullable UUID poseur) {
        RegistreChausseTrappes registre = registre(server);
        List<RegistreChausseTrappes.Piege> aRetirer = poseur == null ? registre.pieges() : registre.de(poseur);
        for (RegistreChausseTrappes.Piege piege : aRetirer) {
            retirerLeBloc(server, piege);
            registre = registre.retirer(piege.dimension(), piege.pos());
        }
        enregistrer(server, registre);
        return aRetirer.size();
    }

    private static void enregistrer(MinecraftServer server, RegistreChausseTrappes registre) {
        server.globalAttachments().setAttached(REGISTRE, registre);
    }

    /** Le bloc disparaît s'il est toujours là (il a pu être cassé autrement : explosion, sol retiré). */
    private static void retirerLeBloc(MinecraftServer server, RegistreChausseTrappes.Piege piege) {
        ServerLevel monde = server.getLevel(piege.dimension());
        if (monde != null && monde.getBlockState(piege.pos()).is(ObjetsRebelles.CHAUSSE_TRAPPE)) {
            monde.removeBlock(piege.pos(), false);
        }
    }
}
