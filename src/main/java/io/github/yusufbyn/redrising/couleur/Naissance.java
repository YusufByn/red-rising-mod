package io.github.yusufbyn.redrising.couleur;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.monde.SceneNaissance;
import io.github.yusufbyn.redrising.net.ChoisirCouleurPayload;
import io.github.yusufbyn.redrising.net.ConclureTestPayload;
import io.github.yusufbyn.redrising.net.OuvrirChoixCouleurPayload;
import io.github.yusufbyn.redrising.net.OuvrirTestPayload;
import io.github.yusufbyn.redrising.net.RepondreTestPayload;
import io.github.yusufbyn.redrising.net.ResultatTestPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
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
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Naissance du joueur (G4) : un joueur qui arrive sans Couleur la reçoit selon le mode du monde.
 *
 * <p>Le mode est une règle de jeu ({@code gamerule}, G4.1) : le jeu la propose déjà à la création du monde,
 * l'enregistre avec lui ({@code data/minecraft/game_rules.dat}) et la rend modifiable par {@code /gamerule}.
 * Un bouton dédié l'affiche aussi dans l'onglet « Jeu » de l'écran de création (mixin client).</p>
 *
 * <p>Dans les deux modes, le serveur demande au client d'ouvrir un écran, le client renvoie les choix du joueur,
 * et le serveur reste seul juge : il vérifie que le joueur n'a pas encore de Couleur et que le monde est dans le bon mode.
 * Tant que le joueur n'est pas né, l'écran revient à chaque connexion.</p>
 * <ul>
 *     <li>Choix libre (G4.2) : un écran des 14 Couleurs.</li>
 *     <li>Test (G4.4) : les 7 questions ({@link TestPersonnalite}), puis le résultat, à accepter ou à relancer une fois
 *     (on refait alors tout le test). Le serveur compte les points ; le client ne voit que la Couleur obtenue.
 *     Le test se passe dans sa scène ({@link SceneNaissance}, G4.5), d'où le joueur sort à sa naissance.</li>
 * </ul>
 */
public final class Naissance {
    public static final String CLE_REGLE = "gamerule." + RedRising.MOD_ID + ".mode_naissance";

    public static final GameRule<ModeNaissance> MODE = GameRuleBuilder.forEnum(ModeNaissance.TEST)
            .category(GameRuleCategory.PLAYER)
            .codec(ModeNaissance.CODEC)
            .buildAndRegister(Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "mode_naissance"));

    /** Test en cours (résultat en attente, relance utilisée), retiré quand le joueur accepte sa Couleur. Jamais envoyé au client. */
    public static final AttachmentType<TestEnCours> TEST_EN_COURS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "test_en_cours"),
            builder -> builder
                    .persistent(TestEnCours.CODEC)
                    .copyOnDeath()
    );

    private Naissance() {
    }

    public static ModeNaissance mode(MinecraftServer server) {
        return server.getGameRules().get(MODE);
    }

    public static void changerMode(MinecraftServer server, ModeNaissance mode) {
        server.getGameRules().set(MODE, mode, server);
    }

    /** Test en cours du joueur, ou {@code null} s'il n'en passe pas. */
    public static @Nullable TestEnCours testEnCours(ServerPlayer joueur) {
        return joueur.getAttached(TEST_EN_COURS);
    }

    /** Appelé depuis {@link RedRising#onInitialize()} : charge la classe (donc enregistre la règle) et branche les événements. */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                RedRising.LOGGER.info("Mode de naissance du monde : {}", mode(server).getSerializedName()));

        PayloadTypeRegistry.clientboundPlay().register(OuvrirChoixCouleurPayload.TYPE, OuvrirChoixCouleurPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OuvrirTestPayload.TYPE, OuvrirTestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ResultatTestPayload.TYPE, ResultatTestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChoisirCouleurPayload.TYPE, ChoisirCouleurPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RepondreTestPayload.TYPE, RepondreTestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ConclureTestPayload.TYPE, ConclureTestPayload.CODEC);
        // Les réceptions de paquets s'exécutent sur le fil du serveur : on peut toucher au joueur directement
        ServerPlayNetworking.registerGlobalReceiver(ChoisirCouleurPayload.TYPE, (payload, contexte) -> choisir(contexte.player(), payload.couleur()));
        ServerPlayNetworking.registerGlobalReceiver(RepondreTestPayload.TYPE, (payload, contexte) -> repondre(contexte.player(), payload.choix()));
        ServerPlayNetworking.registerGlobalReceiver(ConclureTestPayload.TYPE, (payload, contexte) -> conclure(contexte.player(), payload.relancer()));

        ServerPlayerEvents.JOIN.register(joueur -> {
            if (CouleurJoueur.get(joueur) == null) {
                naitre(joueur);
            }
        });
        // Mort avant d'être né (multijoueur, /kill) : on réapparaît au point d'apparition, la naissance reprend
        ServerPlayerEvents.AFTER_RESPAWN.register(CouleurJoueur.PHASE_APRES_COPIE, (ancien, joueur, vivant) -> {
            if (CouleurJoueur.get(joueur) == null) {
                naitre(joueur);
            }
        });
    }

    /** Lance (ou reprend) la naissance d'un joueur sans Couleur, selon le mode du monde. */
    public static void naitre(ServerPlayer joueur) {
        switch (mode(joueur.level().getServer())) {
            case CHOIX_LIBRE -> {
                RedRising.LOGGER.info("Naissance de {} : choix libre, écran de choix envoyé", joueur.getPlainTextName());
                ServerPlayNetworking.send(joueur, OuvrirChoixCouleurPayload.INSTANCE);
            }
            case TEST -> {
                SceneNaissance.entrer(joueur);
                TestEnCours test = testEnCours(joueur);
                if (test != null && test.resultat() != null) {
                    // Revenu après s'être déconnecté devant son résultat : on le lui remontre, sans relance offerte en plus
                    RedRising.LOGGER.info("Naissance de {} : test, résultat en attente ({}) renvoyé", joueur.getPlainTextName(), test.resultat().getSerializedName());
                    ServerPlayNetworking.send(joueur, new ResultatTestPayload(test.resultat(), !test.relanceUtilisee()));
                } else {
                    if (test == null) {
                        joueur.setAttached(TEST_EN_COURS, TestEnCours.DEBUT);
                    }
                    RedRising.LOGGER.info("Naissance de {} : test envoyé", joueur.getPlainTextName());
                    ServerPlayNetworking.send(joueur, OuvrirTestPayload.INSTANCE);
                }
            }
        }
    }

    /**
     * Donne directement une Couleur au joueur, comme s'il était né avec (commande {@code /rr couleur set}, tests) :
     * le test en cours est abandonné et le joueur quitte la scène de naissance s'il y était.
     */
    public static void donnerCouleur(ServerPlayer joueur, Couleur couleur) {
        joueur.removeAttached(TEST_EN_COURS);
        CouleurJoueur.set(joueur, couleur);
        SceneNaissance.sortir(joueur);
    }

    /** Remet le joueur « pas encore né » (Couleur et test effacés) et relance sa naissance. Pour le debug. */
    public static void renaitre(ServerPlayer joueur) {
        CouleurJoueur.retirer(joueur);
        joueur.removeAttached(TEST_EN_COURS);
        naitre(joueur);
    }

    /** Réponse de l'écran de choix. Refusée si le joueur a déjà une Couleur ou si le monde n'est pas en choix libre. */
    private static void choisir(ServerPlayer joueur, Couleur couleur) {
        if (!peutNaitre(joueur, ModeNaissance.CHOIX_LIBRE, "choix de " + couleur.getSerializedName())) {
            return;
        }
        devenir(joueur, couleur);
        RedRising.LOGGER.info("Naissance de {} : {} (choix libre)", joueur.getPlainTextName(), couleur.getSerializedName());
    }

    /** Réponses au test : le serveur compte les points et renvoie le résultat, qui attend d'être accepté. */
    private static void repondre(ServerPlayer joueur, List<Integer> choix) {
        TestEnCours test = testEnCours(joueur);
        if (!peutNaitre(joueur, ModeNaissance.TEST, "réponses au test")) {
            return;
        }
        if (test == null || test.resultat() != null) {
            refuser(joueur, "réponses au test", "aucun test en cours ou résultat déjà donné");
            return;
        }
        Couleur resultat;
        try {
            resultat = TestPersonnalite.resultat(joueur.getRandom(), choix.stream().mapToInt(Integer::intValue).toArray());
        } catch (IllegalArgumentException e) {
            refuser(joueur, "réponses au test", e.getMessage());
            return;
        }
        joueur.setAttached(TEST_EN_COURS, new TestEnCours(resultat, test.relanceUtilisee()));
        ServerPlayNetworking.send(joueur, new ResultatTestPayload(resultat, !test.relanceUtilisee()));
        RedRising.LOGGER.info("Naissance de {} : test terminé, résultat {} (réponses {})", joueur.getPlainTextName(), resultat.getSerializedName(), choix);
    }

    /** Réponse à l'écran de résultat : accepter la Couleur, ou refaire le test si la relance n'a pas encore servi. */
    private static void conclure(ServerPlayer joueur, boolean relancer) {
        TestEnCours test = testEnCours(joueur);
        String demande = relancer ? "relance du test" : "acceptation du résultat";
        if (!peutNaitre(joueur, ModeNaissance.TEST, demande)) {
            return;
        }
        if (test == null || test.resultat() == null) {
            refuser(joueur, demande, "pas de résultat en attente");
            return;
        }
        if (!relancer) {
            joueur.removeAttached(TEST_EN_COURS);
            devenir(joueur, test.resultat());
            RedRising.LOGGER.info("Naissance de {} : {} (test{})", joueur.getPlainTextName(), test.resultat().getSerializedName(),
                    test.relanceUtilisee() ? ", après relance" : "");
        } else if (test.relanceUtilisee()) {
            refuser(joueur, demande, "relance déjà utilisée");
        } else {
            joueur.setAttached(TEST_EN_COURS, new TestEnCours(null, true));
            ServerPlayNetworking.send(joueur, OuvrirTestPayload.INSTANCE);
            RedRising.LOGGER.info("Naissance de {} : relance du test (résultat {} écarté)", joueur.getPlainTextName(), test.resultat().getSerializedName());
        }
    }

    /** Le joueur n'a pas encore de Couleur et le monde est dans ce mode ; sinon la demande est refusée (et notée dans le log). */
    private static boolean peutNaitre(ServerPlayer joueur, ModeNaissance modeAttendu, String demande) {
        Couleur actuelle = CouleurJoueur.get(joueur);
        ModeNaissance mode = mode(joueur.level().getServer());
        if (actuelle != null) {
            refuser(joueur, demande, "déjà né " + actuelle.getSerializedName());
            return false;
        }
        if (mode != modeAttendu) {
            refuser(joueur, demande, "monde en mode " + mode.getSerializedName());
            return false;
        }
        return true;
    }

    private static void refuser(ServerPlayer joueur, String demande, String raison) {
        RedRising.LOGGER.warn("Naissance de {} : {} refusé ({})", joueur.getPlainTextName(), demande, raison);
    }

    private static void devenir(ServerPlayer joueur, Couleur couleur) {
        CouleurJoueur.set(joueur, couleur);
        SceneNaissance.sortir(joueur);
        joueur.sendOverlayMessage(Component.translatable("naissance.redrising.nee", couleur.getDisplayName().copy().withColor(couleur.getTeinte())));
    }
}
