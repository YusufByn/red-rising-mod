package io.github.yusufbyn.redrising.jalons;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.data.EtatMonde;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.monde.SceneNaissance;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.TreeSet;

/**
 * Le moteur des jalons côté serveur (G6.2), une fois par seconde :
 * <ol>
 *     <li>met en route les jalons qui le peuvent ({@link Jalons#peutSeMettreEnRoute}), en gardant l'horloge du moment ;</li>
 *     <li>fait passer ceux qui sont dus, si quelqu'un est là pour les vivre ({@link #quelquUnPourLeVivre}).</li>
 * </ol>
 *
 * <p>Un passage se fait en un seul tick : issue, fin de la mise en route, effets en attente et époque écrits d'un coup
 * dans l'état du monde, sauvegarde immédiate, puis l'événement {@link JalonsEvents#PASSE}. Un jalon qui a une issue ne
 * repasse jamais. Crash avant la sauvegarde : le monde rechargé n'a rien vu, le jalon repassera ; après : ses effets
 * restés en attente sont rejoués au chargement.</p>
 */
public final class MoteurJalons {
    private static final int PERIODE = 20;

    private MoteurJalons() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()}, après {@code EtatMondeServeur.init}. */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % PERIODE == 0) {
                avancer(server);
            }
        });
        // Effets pas encore appliqués au moment de l'arrêt (ou du crash) : l'événement repart, sans nouvelle issue
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            EtatMonde etat = EtatMondeServeur.get(server);
            for (String jalon : new TreeSet<>(etat.effetsEnAttente())) {
                Issue issue = etat.jalons().get(jalon);
                if (issue != null) {
                    RedRising.LOGGER.info("Jalon {} : effets pas encore appliqués, l'événement repart ({})", jalon, issue.getSerializedName());
                    JalonsEvents.PASSE.invoker().passe(server, jalon, issue, JalonsEvents.Raison.REJEU);
                }
            }
        });
    }

    private static void avancer(MinecraftServer server) {
        EtatMonde etat = EtatMondeServeur.get(server);
        EtatMonde misEnRoute = etat;
        for (Jalon jalon : Jalons.tous()) {
            if (Jalons.peutSeMettreEnRoute(jalon, misEnRoute)) {
                misEnRoute = misEnRoute.avecMiseEnRoute(jalon.id(), misEnRoute.horloge());
                RedRising.LOGGER.info("Jalon {} en route (temps de jeu {})", jalon.id(), misEnRoute.horloge().temps());
            }
        }
        if (misEnRoute != etat) {
            EtatMondeServeur.set(server, misEnRoute);
        }
        for (Jalon jalon : Jalons.tous()) {
            EtatMonde actuel = EtatMondeServeur.get(server);
            if (actuel.enRoute().containsKey(jalon.id()) && Jalons.restant(jalon, actuel) <= 0 && quelquUnPourLeVivre(server, jalon)) {
                passer(server, jalon, jalon.issue(Jalons.bilan(jalon, actuel)), JalonsEvents.Raison.ECHEANCE);
            }
        }
    }

    /**
     * Un joueur connecté hors de la scène de naissance, quelle que soit sa dimension (un Or à l'Institut ne bloque pas
     * les jalons) ; pour un jalon {@code surPlace}, un joueur dans cette dimension.
     */
    public static boolean quelquUnPourLeVivre(MinecraftServer server, Jalon jalon) {
        for (ServerPlayer joueur : server.getPlayerList().getPlayers()) {
            ResourceKey<Level> dimension = joueur.level().dimension();
            if (dimension != SceneNaissance.DIMENSION && (jalon.surPlace() == null || dimension == jalon.surPlace())) {
                return true;
            }
        }
        return false;
    }

    /** Le jalon passe avec cette issue : enregistrée, époque changée, sauvegarde immédiate, puis l'événement. */
    public static void passer(MinecraftServer server, Jalon jalon, Issue issue, JalonsEvents.Raison raison) {
        EtatMonde etat = EtatMondeServeur.get(server);
        if (etat.jalons().containsKey(jalon.id())) {
            throw new IllegalStateException("Jalon " + jalon.id() + " déjà passé");
        }
        Bilan bilan = Jalons.bilan(jalon, etat);
        etat = etat.avecIssue(jalon.id(), issue).sansMiseEnRoute(jalon.id()).avecEffetsEnAttente(jalon.id(), true);
        if (jalon.ouvre() != null) {
            etat = etat.avecEpoque(jalon.ouvre());
        }
        EtatMondeServeur.set(server, etat);
        // Sur le disque tout de suite : un crash juste après ne doit pas faire repasser le jalon
        server.saveEverything(true, true, true);
        RedRising.LOGGER.info("Jalon {} passé : {} ({} ; période : temps {}, aide {}, tort {}, rythme {})", jalon.id(),
                issue.getSerializedName(), raison.name().toLowerCase(), bilan.periode().temps(), bilan.aide(), bilan.tort(),
                String.format(Locale.ROOT, "%.2f", bilan.rythme()));
        JalonsEvents.PASSE.invoker().passe(server, jalon.id(), issue, raison);
    }

    /** Les effets de ce jalon sont appliqués (appelé par G6.3) : l'événement ne repartira plus au chargement. */
    public static void effetsAppliques(MinecraftServer server, String jalon) {
        EtatMondeServeur.modifier(server, etat -> etat.avecEffetsEnAttente(jalon, false));
    }
}
