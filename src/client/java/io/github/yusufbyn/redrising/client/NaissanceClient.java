package io.github.yusufbyn.redrising.client;

import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.net.OuvrirChoixCouleurPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Côté client de la naissance (G4.2) : ouvre l'écran de choix libre quand le serveur le demande.
 *
 * <p>L'écran ne s'ouvre pas dès la réception de la demande : elle arrive pendant l'écran de chargement du monde,
 * qui le remplacerait en se fermant. On note la demande, et on ouvre l'écran au premier tick où aucun autre écran
 * n'est affiché. Même chose après une mort avant d'avoir choisi : l'écran revient après la réapparition.
 * La demande tombe dès que le joueur a une Couleur.</p>
 */
public final class NaissanceClient {
    /** Le serveur a demandé un choix, et le joueur n'a pas encore de Couleur. */
    private static boolean choixDemande;

    private NaissanceClient() {
    }

    /** Appelé depuis {@link RedRisingClient#onInitializeClient()}. */
    public static void init() {
        // Les réceptions de paquets s'exécutent sur le fil du client : on peut toucher à l'interface directement
        ClientPlayNetworking.registerGlobalReceiver(OuvrirChoixCouleurPayload.TYPE, (payload, contexte) -> choixDemande = true);
        // Une demande ne survit pas au changement de monde
        ClientPlayConnectionEvents.DISCONNECT.register((connexion, client) -> choixDemande = false);
        ClientTickEvents.END_CLIENT_TICK.register(NaissanceClient::ouvrirSiDemande);
    }

    private static void ouvrirSiDemande(Minecraft client) {
        if (!choixDemande || client.player == null) {
            return;
        }
        if (CouleurJoueur.get(client.player) != null) {
            choixDemande = false;
        } else if (client.gui.screen() == null) {
            client.gui.setScreen(new EcranChoixCouleur());
        }
    }
}
