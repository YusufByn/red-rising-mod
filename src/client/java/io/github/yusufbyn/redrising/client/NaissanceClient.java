package io.github.yusufbyn.redrising.client;

import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.net.OuvrirChoixCouleurPayload;
import io.github.yusufbyn.redrising.net.OuvrirTestPayload;
import io.github.yusufbyn.redrising.net.ResultatTestPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Côté client de la naissance (G4.2, G4.4) : ouvre l'écran que le serveur demande (choix libre, test, résultat).
 *
 * <p>L'écran ne s'ouvre pas dès la réception de la demande : elle arrive parfois pendant l'écran de chargement du monde,
 * qui le remplacerait en se fermant. On note la demande, et on l'ouvre au premier tick où aucun écran n'est affiché,
 * ou à la place d'un écran de naissance (le résultat remplace les questions). Tant que le joueur n'a pas de Couleur,
 * le dernier écran demandé revient s'il disparaît (mort avant d'être né, par exemple). La demande tombe dès que
 * le joueur a une Couleur.</p>
 */
public final class NaissanceClient {
    /** Fabrique du dernier écran demandé par le serveur ; {@code null} si aucune naissance en cours. */
    private static @Nullable Supplier<Screen> ecranDemande;
    /** La demande est nouvelle : pas encore ouverte. */
    private static boolean nouvelleDemande;

    private NaissanceClient() {
    }

    /** Appelé depuis {@link RedRisingClient#onInitializeClient()}. */
    public static void init() {
        // Les réceptions de paquets s'exécutent sur le fil du client : on peut toucher à l'interface directement
        ClientPlayNetworking.registerGlobalReceiver(OuvrirChoixCouleurPayload.TYPE, (payload, contexte) -> demander(EcranChoixCouleur::new));
        ClientPlayNetworking.registerGlobalReceiver(OuvrirTestPayload.TYPE, (payload, contexte) -> demander(EcranTestNaissance::new));
        ClientPlayNetworking.registerGlobalReceiver(ResultatTestPayload.TYPE, (payload, contexte) ->
                demander(() -> new EcranResultatNaissance(payload.couleur(), payload.relancePossible())));
        // Une demande ne survit pas au changement de monde
        ClientPlayConnectionEvents.DISCONNECT.register((connexion, client) -> ecranDemande = null);
        ClientTickEvents.END_CLIENT_TICK.register(NaissanceClient::ouvrirSiDemande);
    }

    private static void demander(Supplier<Screen> ecran) {
        ecranDemande = ecran;
        nouvelleDemande = true;
    }

    private static void ouvrirSiDemande(Minecraft client) {
        if (ecranDemande == null || client.player == null) {
            return;
        }
        if (CouleurJoueur.get(client.player) != null) {
            ecranDemande = null;
            return;
        }
        Screen actuel = client.gui.screen();
        if (actuel == null || (nouvelleDemande && actuel instanceof EcranNaissance)) {
            client.gui.setScreen(ecranDemande.get());
            nouvelleDemande = false;
        }
    }
}
