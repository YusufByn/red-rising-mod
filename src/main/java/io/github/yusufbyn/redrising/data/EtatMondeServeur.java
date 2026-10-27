package io.github.yusufbyn.redrising.data;

import io.github.yusufbyn.redrising.RedRising;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.function.UnaryOperator;

/**
 * Stockage de l'{@link EtatMonde} avec le monde (G2.4).
 *
 * <p>Utilise les attachments « globaux » de Fabric : une donnée liée au serveur entier (pas à un joueur
 * ni à une dimension), sauvegardée dans le dossier {@code data} du monde.</p>
 */
public final class EtatMondeServeur {
    public static final AttachmentType<EtatMonde> ETAT = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "etat_monde"),
            builder -> builder.persistent(EtatMonde.CODEC)
    );

    private EtatMondeServeur() {
    }

    public static EtatMonde get(MinecraftServer server) {
        return server.globalAttachments().getAttachedOrElse(ETAT, EtatMonde.NOUVEAU);
    }

    public static void set(MinecraftServer server, EtatMonde etat) {
        server.globalAttachments().setAttached(ETAT, etat);
    }

    public static void modifier(MinecraftServer server, UnaryOperator<EtatMonde> modification) {
        set(server, modification.apply(get(server)));
    }

    /** Appelé depuis {@link RedRising#onInitialize()}. */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            EtatMonde etat = server.globalAttachments().getAttached(ETAT);
            if (etat == null) {
                // Monde neuf : on écrit l'état tout de suite, pour que le numéro de version soit sur le disque dès le départ
                set(server, EtatMonde.NOUVEAU);
                RedRising.LOGGER.info("État du monde : nouveau, schéma v{}", EtatMonde.VERSION_SCHEMA);
            } else {
                RedRising.LOGGER.info("État du monde : chargé (schéma v{}, époque {}, {} jalon(s), {} zone(s), {})",
                        etat.schemaVersion(), etat.epoque().getSerializedName(), etat.jalons().size(), etat.zones().size(), etat.horloge());
            }
        });
    }
}
