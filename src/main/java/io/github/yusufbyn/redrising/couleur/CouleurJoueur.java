package io.github.yusufbyn.redrising.couleur;

import io.github.yusufbyn.redrising.RedRising;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Couleur attachée au joueur (G2.1), via la Data Attachment API de Fabric.
 *
 * <p>Un « attachment » est une donnée que Fabric colle sur une entité : il la sauvegarde avec le joueur
 * ({@code persistent}) et la recopie sur le nouveau corps après la mort ({@code copyOnDeath}).
 * Pas de valeur par défaut : tant que la naissance (G4) n'a pas eu lieu, le joueur n'a pas de Couleur.</p>
 */
public final class CouleurJoueur {
    public static final AttachmentType<Couleur> COULEUR = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "couleur"),
            builder -> builder
                    .persistent(Couleur.CODEC)
                    .copyOnDeath()
    );

    private CouleurJoueur() {
    }

    /** Couleur du joueur, ou {@code null} s'il n'est pas encore né. */
    public static @Nullable Couleur get(Player player) {
        return player.getAttached(COULEUR);
    }

    public static void set(Player player, Couleur couleur) {
        player.setAttached(COULEUR, couleur);
    }

    /** Appelé depuis {@link RedRising#onInitialize()} : charge la classe (donc enregistre l'attachment) et branche les événements. */
    public static void init() {
        ServerPlayerEvents.JOIN.register(player -> {
            Couleur couleur = get(player);
            if (couleur == null) {
                // PROVISOIRE : en attendant la naissance (G4), une Couleur au hasard pour pouvoir tester la persistance
                Couleur[] toutes = Couleur.values();
                couleur = toutes[player.getRandom().nextInt(toutes.length)];
                set(player, couleur);
                RedRising.LOGGER.info("Couleur de {} : {} (nouvelle, attribuée au hasard)", player.getPlainTextName(), couleur.getSerializedName());
            } else {
                RedRising.LOGGER.info("Couleur de {} : {} (chargée)", player.getPlainTextName(), couleur.getSerializedName());
            }
        });

        // Fabric recopie les attachments dans sa propre écoute d'AFTER_RESPAWN (phase par défaut) :
        // on se place dans une phase après la sienne, sinon on risque de lire le nouveau corps encore vide.
        Identifier apresCopie = Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "apres_copie");
        ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(Event.DEFAULT_PHASE, apresCopie);
        ServerPlayerEvents.AFTER_RESPAWN.register(apresCopie, (oldPlayer, newPlayer, alive) -> {
            Couleur couleur = get(newPlayer);
            RedRising.LOGGER.info("Couleur de {} après réapparition : {}", newPlayer.getPlainTextName(),
                    couleur == null ? "aucune" : couleur.getSerializedName());
        });
    }
}
