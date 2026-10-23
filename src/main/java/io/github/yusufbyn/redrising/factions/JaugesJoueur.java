package io.github.yusufbyn.redrising.factions;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Jauges attachées au joueur (G2.2) : persistantes, copiées à la mort, toujours dans les bornes de sa Couleur.
 *
 * <p>Les valeurs sont bornées à l'écriture et à la lecture : si la Couleur change (Sculpture, commande de debug),
 * une valeur devenue hors bornes est lue bornée sans attendre la prochaine écriture.</p>
 */
public final class JaugesJoueur {
    public static final AttachmentType<Jauges> JAUGES = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(RedRising.MOD_ID, "jauges"),
            builder -> builder
                    .persistent(Jauges.CODEC)
                    .copyOnDeath()
                    .initializer(() -> Jauges.DEPART)
    );

    private JaugesJoueur() {
    }

    /** Les 3 jauges du joueur, bornées selon sa Couleur. */
    public static Jauges get(Player player) {
        return player.getAttachedOrCreate(JAUGES).borner(CouleurJoueur.get(player));
    }

    public static int get(Player player, Jauge jauge) {
        return get(player).valeur(jauge);
    }

    /** Fixe une jauge ; la valeur est ramenée dans les bornes. Renvoie la valeur réellement enregistrée. */
    public static int set(Player player, Jauge jauge, long valeur) {
        Jauges actuelles = get(player);
        int bornee = Bornes.pour(CouleurJoueur.get(player), jauge).borner(valeur);
        player.setAttached(JAUGES, actuelles.avec(jauge, bornee));
        return bornee;
    }

    /** Ajoute {@code delta} (positif ou négatif) à une jauge, dans les bornes. Renvoie la nouvelle valeur. */
    public static int ajouter(Player player, Jauge jauge, int delta) {
        return set(player, jauge, (long) get(player, jauge) + delta);
    }

    /** Appelé depuis {@link RedRising#onInitialize()} : charge la classe, donc enregistre l'attachment. */
    public static void init() {
    }
}
