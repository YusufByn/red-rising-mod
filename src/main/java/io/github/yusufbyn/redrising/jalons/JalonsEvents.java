package io.github.yusufbyn.redrising.jalons;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;

/**
 * Événement d'un jalon passé (G6.2), auquel s'abonnent ses effets sur le monde (G6.3).
 *
 * <p>Règle pour les effets : ils doivent supporter d'être rejoués. L'événement part avec les effets « en attente »
 * ({@code EtatMonde.effetsEnAttente}) ; l'abonné qui les a appliqués appelle {@link MoteurJalons#effetsAppliques}. Si le
 * serveur tombe avant, l'événement repart au chargement suivant (raison {@link Raison#REJEU} : ni nouvelle issue ni
 * changement d'époque, les effets seulement).</p>
 */
public final class JalonsEvents {
    public static final Event<Passage> PASSE = EventFactory.createArrayBacked(Passage.class, abonnes -> (server, jalon, issue, raison) -> {
        for (Passage abonne : abonnes) {
            abonne.passe(server, jalon, issue, raison);
        }
    });

    private JalonsEvents() {
    }

    @FunctionalInterface
    public interface Passage {
        void passe(MinecraftServer server, String jalon, Issue issue, Raison raison);
    }

    /** Pourquoi l'événement part. */
    public enum Raison {
        /** Le jalon est arrivé à son échéance. */
        ECHEANCE,
        /** Forcé par {@code /rr jalon declencher}. */
        COMMANDE,
        /** Au chargement du monde : le jalon était passé, ses effets pas encore appliqués. */
        REJEU
    }
}
