package io.github.yusufbyn.redrising.jalons;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.UnaryOperator;

/**
 * L'horloge du monde côté serveur (G6.1) : fait avancer le temps de jeu, et compte le Soutien des actions des joueurs.
 * Les valeurs sont dans {@link Horloge} (sauvegardée avec l'état du monde) et {@link ContributionDuJour} (plafond).
 *
 * <ul>
 *     <li>Temps de jeu : seulement quand au moins un joueur est connecté. En solo, la pause (Échap) arrête le serveur,
 *     donc l'horloge aussi. Limite : un joueur absent de son clavier la fait avancer.</li>
 *     <li>Soutien : la variation de chaque action (avant bornage de la jauge : un joueur à 100 qui aide fait encore
 *     avancer le Rising), branchée à un seul endroit, {@code ActionsFaction.agir}. Ne comptent pas : les commandes de
 *     debug, les événements du monde (une cellule qui tombe : la dénonciation porte déjà son tort ; les jalons, G6.3,
 *     sinon ils s'alimenteraient eux-mêmes).</li>
 * </ul>
 */
public final class HorlogeMonde {
    /** Sur le joueur : sa contribution au Soutien cumulé du jour (plafond). Sauvegardée : se reconnecter ne la remet pas à zéro. */
    public static final AttachmentType<ContributionDuJour> CONTRIBUTION = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "contribution_du_jour"),
            builder -> builder.persistent(ContributionDuJour.CODEC).copyOnDeath()
    );

    /** Le temps de jeu avance par paquets d'une seconde (une écriture de l'état du monde par seconde, pas par tick). */
    private static final int PERIODE = 20;

    private HorlogeMonde() {
    }

    /** Appelé depuis {@link RedRising#onInitialize()}. */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % PERIODE == 0 && server.getPlayerCount() > 0) {
                modifier(server, horloge -> horloge.avancer(PERIODE));
            }
        });
    }

    public static Horloge get(MinecraftServer server) {
        return EtatMondeServeur.get(server).horloge();
    }

    public static void modifier(MinecraftServer server, UnaryOperator<Horloge> modification) {
        EtatMondeServeur.modifier(server, etat -> etat.avecHorloge(modification.apply(etat.horloge())));
    }

    /** La variation de Soutien d'une action de ce joueur : elle compte pour le monde, dans la limite du plafond du jour. */
    public static void compterSoutien(ServerPlayer joueur, int soutien) {
        if (soutien == 0) {
            return;
        }
        ContributionDuJour.Retenue retenue = contribution(joueur).retenir(soutien, jour(joueur));
        joueur.setAttached(CONTRIBUTION, retenue.contribution());
        if (retenue.compte() != soutien) {
            RedRising.LOGGER.info("{} : plafond du jour atteint, {} de Soutien compté(s) pour le monde sur {}",
                    joueur.getPlainTextName(), retenue.compte(), soutien);
        }
        if (retenue.compte() != 0) {
            modifier(joueur.level().getServer(), horloge -> horloge.compter(retenue.compte()));
        }
    }

    public static ContributionDuJour contribution(ServerPlayer joueur) {
        return joueur.getAttachedOrElse(CONTRIBUTION, ContributionDuJour.AUCUNE);
    }

    /** Le jour de jeu du plafond : celui des autres règles « une fois par jour » (horloge de l'Overworld). */
    public static long jour(ServerPlayer joueur) {
        return joueur.level().getServer().overworld().getGameTime() / ActionsFaction.TICKS_PAR_JOUR;
    }
}
