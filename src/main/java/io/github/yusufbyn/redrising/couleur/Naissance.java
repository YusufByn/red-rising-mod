package io.github.yusufbyn.redrising.couleur;

import io.github.yusufbyn.redrising.RedRising;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * Naissance du joueur (G4). Pour l'instant, le mode choisi à la création du monde (G4.1).
 *
 * <p>Le mode est une règle de jeu ({@code gamerule}) : le jeu la propose déjà à la création du monde,
 * l'enregistre avec lui ({@code data/minecraft/game_rules.dat}) et la rend modifiable par {@code /gamerule}.
 * Un bouton dédié l'affiche aussi dans l'onglet « Jeu » de l'écran de création (mixin client).</p>
 */
public final class Naissance {
    public static final String CLE_REGLE = "gamerule." + RedRising.MOD_ID + ".mode_naissance";

    public static final GameRule<ModeNaissance> MODE = GameRuleBuilder.forEnum(ModeNaissance.TEST)
            .category(GameRuleCategory.PLAYER)
            .codec(ModeNaissance.CODEC)
            .buildAndRegister(Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "mode_naissance"));

    private Naissance() {
    }

    public static ModeNaissance mode(MinecraftServer server) {
        return server.getGameRules().get(MODE);
    }

    public static void changerMode(MinecraftServer server, ModeNaissance mode) {
        server.getGameRules().set(MODE, mode, server);
    }

    /** Appelé depuis {@link RedRising#onInitialize()} : charge la classe, donc enregistre la règle. */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                RedRising.LOGGER.info("Mode de naissance du monde : {}", mode(server).getSerializedName()));
    }
}
