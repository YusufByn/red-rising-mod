package io.github.yusufbyn.redrising.couleur;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.net.ChoisirCouleurPayload;
import io.github.yusufbyn.redrising.net.OuvrirChoixCouleurPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * Naissance du joueur (G4) : un joueur qui arrive sans Couleur la reçoit selon le mode du monde.
 *
 * <p>Le mode est une règle de jeu ({@code gamerule}, G4.1) : le jeu la propose déjà à la création du monde,
 * l'enregistre avec lui ({@code data/minecraft/game_rules.dat}) et la rend modifiable par {@code /gamerule}.
 * Un bouton dédié l'affiche aussi dans l'onglet « Jeu » de l'écran de création (mixin client).</p>
 *
 * <p>Choix libre (G4.2) : le serveur demande au client d'ouvrir l'écran de choix, le client renvoie la Couleur choisie.
 * Le serveur reste seul juge : il n'accepte un choix que d'un joueur sans Couleur, dans un monde en choix libre.
 * Tant que le joueur n'a pas choisi, il n'a pas de Couleur, et l'écran revient à chaque connexion.</p>
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

    /** Appelé depuis {@link RedRising#onInitialize()} : charge la classe (donc enregistre la règle) et branche les événements. */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                RedRising.LOGGER.info("Mode de naissance du monde : {}", mode(server).getSerializedName()));

        PayloadTypeRegistry.clientboundPlay().register(OuvrirChoixCouleurPayload.TYPE, OuvrirChoixCouleurPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChoisirCouleurPayload.TYPE, ChoisirCouleurPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ChoisirCouleurPayload.TYPE, (payload, contexte) -> choisir(contexte.player(), payload.couleur()));

        ServerPlayerEvents.JOIN.register(joueur -> {
            if (CouleurJoueur.get(joueur) == null) {
                naitre(joueur);
            }
        });
    }

    private static void naitre(ServerPlayer joueur) {
        switch (mode(joueur.level().getServer())) {
            case CHOIX_LIBRE -> {
                RedRising.LOGGER.info("Naissance de {} : choix libre, écran de choix envoyé", joueur.getPlainTextName());
                ServerPlayNetworking.send(joueur, OuvrirChoixCouleurPayload.INSTANCE);
            }
            case TEST -> {
                // PROVISOIRE : en attendant le test de naissance (G4.3, G4.4), une Couleur au hasard
                Couleur[] toutes = Couleur.values();
                Couleur couleur = toutes[joueur.getRandom().nextInt(toutes.length)];
                CouleurJoueur.set(joueur, couleur);
                RedRising.LOGGER.info("Naissance de {} : {} (test pas encore écrit, Couleur au hasard)", joueur.getPlainTextName(), couleur.getSerializedName());
            }
        }
    }

    /** Réponse de l'écran de choix. Refusée si le joueur a déjà une Couleur ou si le monde n'est pas en choix libre. */
    private static void choisir(ServerPlayer joueur, Couleur couleur) {
        Couleur actuelle = CouleurJoueur.get(joueur);
        ModeNaissance mode = mode(joueur.level().getServer());
        if (actuelle != null || mode != ModeNaissance.CHOIX_LIBRE) {
            RedRising.LOGGER.warn("Choix de Couleur refusé pour {} ({} demandé ; Couleur actuelle : {}, mode : {})", joueur.getPlainTextName(),
                    couleur.getSerializedName(), actuelle == null ? "aucune" : actuelle.getSerializedName(), mode.getSerializedName());
            return;
        }
        CouleurJoueur.set(joueur, couleur);
        joueur.sendOverlayMessage(Component.translatable("naissance.redrising.nee", couleur.getDisplayName().copy().withColor(couleur.getTeinte())));
        RedRising.LOGGER.info("Naissance de {} : {} (choix libre)", joueur.getPlainTextName(), couleur.getSerializedName());
    }
}
