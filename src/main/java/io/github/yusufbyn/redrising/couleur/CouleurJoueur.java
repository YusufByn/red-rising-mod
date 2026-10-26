package io.github.yusufbyn.redrising.couleur;

import io.github.yusufbyn.redrising.RedRising;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Couleur attachée au joueur (G2.1), via la Data Attachment API de Fabric.
 *
 * <p>Un « attachment » est une donnée que Fabric colle sur une entité : il la sauvegarde avec le joueur
 * ({@code persistent}) et la recopie sur le nouveau corps après la mort ({@code copyOnDeath}).
 * Pas de valeur par défaut : tant que la naissance ({@link Naissance}) n'a pas eu lieu, le joueur n'a pas de Couleur.</p>
 *
 * <p>Synchronisée avec tous les clients (G2.3) : la Couleur d'un joueur se verra sur son modèle (G3.5).
 * Fabric envoie la valeur à la connexion, quand un autre joueur arrive en vue, et à chaque {@link #set}.</p>
 */
public final class CouleurJoueur {
    public static final AttachmentType<Couleur> COULEUR = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "couleur"),
            builder -> builder
                    .persistent(Couleur.CODEC)
                    .copyOnDeath()
                    .syncWith(Couleur.STREAM_CODEC, AttachmentSyncPredicate.all())
    );

    /**
     * Phase de {@code AFTER_RESPAWN} placée après celle où Fabric recopie les attachments sur le nouveau corps.
     * Toute écoute de la réapparition qui lit la Couleur ou les jauges doit s'y enregistrer.
     */
    public static final Identifier PHASE_APRES_COPIE = Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "apres_copie");

    private CouleurJoueur() {
    }

    /** Couleur du joueur, ou {@code null} s'il n'est pas encore né. */
    public static @Nullable Couleur get(Player player) {
        return player.getAttached(COULEUR);
    }

    /** Change la Couleur ; côté serveur, les capacités passives suivent aussitôt. */
    public static void set(Player player, Couleur couleur) {
        player.setAttached(COULEUR, couleur);
        if (player instanceof ServerPlayer joueur) {
            CapacitesPassives.appliquer(joueur);
        }
    }

    /** Appelé depuis {@link RedRising#onInitialize()} : charge la classe (donc enregistre l'attachment) et branche les événements. */
    public static void init() {
        // Un joueur sans Couleur à l'arrivée est pris en charge par la naissance (Naissance)
        ServerPlayerEvents.JOIN.register(player -> {
            Couleur couleur = get(player);
            if (couleur != null) {
                RedRising.LOGGER.info("Couleur de {} : {} (chargée)", player.getPlainTextName(), couleur.getSerializedName());
            }
        });

        // Fabric recopie les attachments dans sa propre écoute d'AFTER_RESPAWN (phase par défaut) :
        // on se place dans une phase après la sienne, sinon on risque de lire le nouveau corps encore vide.
        ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE_APRES_COPIE);
        ServerPlayerEvents.AFTER_RESPAWN.register(PHASE_APRES_COPIE, (oldPlayer, newPlayer, alive) -> {
            Couleur couleur = get(newPlayer);
            RedRising.LOGGER.info("Couleur de {} après réapparition : {}", newPlayer.getPlainTextName(),
                    couleur == null ? "aucune" : couleur.getSerializedName());
        });
    }
}
