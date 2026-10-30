package io.github.yusufbyn.redrising.monde;

import com.mojang.serialization.Codec;
import io.github.yusufbyn.redrising.RedRising;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Les coffres d'outils de la mine se remplissent de nouveau (G7.2.5, GDD section 7) : quand on en ouvre un, s'il est vide et que
 * son dernier remplissage date de {@link PlanDeMine#DELAI_REMPLISSAGE} ticks (3 jours de jeu), il reçoit la table {@link MineLykos#COFFRE_RECHARGE} :
 * outils de base et nourriture, jamais de fer. Tout se décide à l'ouverture : rien ne tourne tant qu'on n'ouvre pas, et un coffre
 * où l'on range ses affaires n'est jamais touché.
 */
public final class CoffresMine {
    /** Sur un coffre d'outils : le tick de son dernier remplissage. */
    public static final AttachmentType<Long> DERNIER_REMPLISSAGE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "dernier_remplissage"),
            builder -> builder.persistent(Codec.LONG)
    );

    private CoffresMine() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()}. */
    public static void init() {
        UseBlockCallback.EVENT.register((joueur, monde, main, visee) -> {
            if (monde instanceof ServerLevel niveau) {
                ouverture(niveau, visee.getBlockPos());
            }
            return InteractionResult.PASS;
        });
    }

    /** Un joueur ouvre ce bloc : si c'est un coffre d'outils de la mine, il se remplit au besoin (avant que son contenu soit lu). */
    public static void ouverture(ServerLevel niveau, BlockPos pos) {
        if (!(niveau.getBlockEntity(pos) instanceof ChestBlockEntity coffre)) {
            return;
        }
        PlanDeMine plan = MineLykos.planEnJeu(niveau);
        if (plan == null || plan.coffres().stream().noneMatch(c -> c.pos().equals(pos))) {
            return;
        }
        long maintenant = niveau.getGameTime();
        if (coffre.getLootTable() != null || !coffre.hasAttached(DERNIER_REMPLISSAGE)) {
            // Première ouverture : la table d'origine sera lue par Minecraft
            coffre.setAttached(DERNIER_REMPLISSAGE, maintenant);
            coffre.setChanged();
        } else if (PlanDeMine.coffreDoitSeRemplir(coffre.getAttached(DERNIER_REMPLISSAGE), coffre.isEmpty(), maintenant)) {
            coffre.setLootTable(MineLykos.COFFRE_RECHARGE);
            coffre.setLootTableSeed(niveau.getRandom().nextLong());
            coffre.setAttached(DERNIER_REMPLISSAGE, maintenant);
            coffre.setChanged();
        }
    }
}
