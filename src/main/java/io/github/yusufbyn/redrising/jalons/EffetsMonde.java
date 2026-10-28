package io.github.yusufbyn.redrising.jalons;

import com.mojang.serialization.Codec;
import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.data.EtatMonde;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Les effets des jalons côté serveur (G6.3), abonnés à {@link JalonsEvents#PASSE}.
 *
 * <ol>
 *     <li>Au passage (ou au rejeu) : leviers, versements et nouvelle posés, et effets marqués appliqués, en une seule
 *     écriture de l'état du monde ({@link Effets#appliquer}).</li>
 *     <li>Livraison des versements à chaque joueur connecté : au passage, à son arrivée, puis toutes les 5 secondes
 *     (un joueur en pleine naissance attend d'avoir sa Couleur). Une livraison peut se refaire sans risque : ce que le
 *     joueur a reçu est sur lui.</li>
 * </ol>
 *
 * <p>Les versements passent par {@code JaugesJoueur.appliquer}, pas par {@code ActionsFaction.agir} : ils ne nourrissent
 * ni l'aide ni le tort (G6.1, les jalons ne s'alimentent pas eux-mêmes).</p>
 */
public final class EffetsMonde {
    /** Sur le joueur : temps de jeu de sa première arrivée dans le monde. Un versement plus ancien ne le concerne pas. */
    public static final AttachmentType<Long> ARRIVEE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "arrivee"),
            builder -> builder.persistent(Codec.LONG).copyOnDeath()
    );

    /** Sur le joueur : les versements qu'il a reçus, ou qui ont été décidés pour lui sans rien lui donner. */
    public static final AttachmentType<Set<String>> RECUS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "versements_recus"),
            builder -> builder.persistent(Codec.STRING.listOf().<Set<String>>xmap(Set::copyOf, recus -> List.copyOf(new TreeSet<>(recus))))
                    .copyOnDeath()
    );

    private static final int PERIODE_FIN = 20;
    private static final int PERIODE_LIVRAISON = 100;

    private EffetsMonde() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()}, après {@code MoteurJalons.init}. */
    public static void init() {
        JalonsEvents.PASSE.register((server, jalon, issue, raison) -> {
            Jalon declare = Jalons.get(jalon);
            EffetsJalon effets = declare == null ? EffetsJalon.AUCUN : declare.effets(issue);
            long temps = HorlogeMonde.get(server).temps();
            EtatMondeServeur.modifier(server, etat -> Effets.appliquer(etat, jalon, issue, effets, temps));
            RedRising.LOGGER.info("Jalon {} : effets posés ({} levier(s), {} versement(s)){}", jalon, effets.leviers().size(),
                    effets.versements().size(), raison == JalonsEvents.Raison.REJEU ? ", au rejeu" : "");
            for (ServerPlayer joueur : server.getPlayerList().getPlayers()) {
                livrer(joueur, raison != JalonsEvents.Raison.REJEU);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((connexion, envoi, server) -> {
            ServerPlayer joueur = connexion.getPlayer();
            if (!joueur.hasAttached(ARRIVEE)) {
                joueur.setAttached(ARRIVEE, HorlogeMonde.get(server).temps());
            }
            livrer(joueur, false);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % PERIODE_FIN == 0) {
                retirerLesEffetsFinis(server);
            }
            if (server.getTickCount() % PERIODE_LIVRAISON == 0) {
                for (ServerPlayer joueur : server.getPlayerList().getPlayers()) {
                    livrer(joueur, false);
                }
            }
        });
    }

    /** Le niveau en cours de ce levier (0 : rien). */
    public static int niveau(MinecraftServer server, Levier levier) {
        EtatMonde etat = EtatMondeServeur.get(server);
        return Effets.niveau(etat.effets(), levier, etat.horloge().temps());
    }

    /** Les PNJ de cette Couleur se taisent-ils devant la Société ? */
    public static boolean silencieuse(MinecraftServer server, Couleur couleur) {
        EtatMonde etat = EtatMondeServeur.get(server);
        return Effets.silencieuse(etat.effets(), couleur, etat.horloge().temps());
    }

    private static void retirerLesEffetsFinis(MinecraftServer server) {
        EtatMonde avant = EtatMondeServeur.get(server);
        EtatMonde etat = avant;
        long temps = etat.horloge().temps();
        for (Map.Entry<String, EffetEnCours> effet : avant.effets().entrySet()) {
            if (!effet.getValue().actif(temps)) {
                etat = etat.sansEffet(effet.getKey());
                RedRising.LOGGER.info("Effet {} fini ({} niveau {})", effet.getKey(), effet.getValue().levier().getSerializedName(), effet.getValue().niveau());
            }
        }
        if (etat != avant) {
            EtatMondeServeur.set(server, etat);
        }
    }

    /**
     * Livre à ce joueur ce qui l'attend. {@code auPassage} : le jalon vient de passer, la nouvelle s'affiche en titre ;
     * sinon elle arrive dans le chat, « pendant ton absence ».
     */
    public static void livrer(ServerPlayer joueur, boolean auPassage) {
        EtatMonde etat = EtatMondeServeur.get(joueur.level().getServer());
        Long arrivee = joueur.getAttached(ARRIVEE);
        if (arrivee == null || etat.versements().isEmpty()) {
            return;
        }
        Set<String> recus = joueur.getAttachedOrElse(RECUS, Set.of());
        Couleur couleur = CouleurJoueur.get(joueur);
        Set<String> nouveauxRecus = new HashSet<>(recus);
        for (Versement versement : etat.versements()) {
            switch (versement.decider(arrivee, recus, couleur)) {
                case RIEN, ATTENDRE -> {
                }
                case MARQUER -> nouveauxRecus.add(versement.id());
                case RECEVOIR -> {
                    nouveauxRecus.add(versement.id());
                    if (versement.nouvelle()) {
                        annoncer(joueur, versement, auPassage);
                    } else {
                        JaugesJoueur.appliquer(joueur, versement.variation());
                        RedRising.LOGGER.info("{} reçoit le versement {} : {}", joueur.getPlainTextName(), versement.id(), versement.variation());
                    }
                }
            }
        }
        if (nouveauxRecus.size() != recus.size()) {
            joueur.setAttached(RECUS, nouveauxRecus);
        }
    }

    /** Le message du jalon : titre à l'écran au passage, et une ligne dans le chat pour en garder la trace. */
    private static void annoncer(ServerPlayer joueur, Versement nouvelle, boolean auPassage) {
        String cle = "jalon.redrising." + nouvelle.jalon() + "." + nouvelle.issue().getSerializedName();
        Component texte = Component.translatable(cle);
        if (auPassage) {
            joueur.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(cle + ".titre")));
            joueur.connection.send(new ClientboundSetSubtitleTextPacket(texte));
            joueur.sendSystemMessage(texte);
        } else {
            joueur.sendSystemMessage(Component.translatable("jalon.redrising.pendant_absence", texte));
        }
    }
}
