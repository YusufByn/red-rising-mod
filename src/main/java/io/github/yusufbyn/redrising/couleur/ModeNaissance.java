package io.github.yusufbyn.redrising.couleur;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** Comment le joueur reçoit sa Couleur (GDD section 3), choisi à la création du monde (G4.1). */
public enum ModeNaissance implements StringRepresentable {
    /** Test de personnalité caché, résultat en surprise (par défaut). */
    TEST("test"),
    /** Le joueur choisit sa Couleur dans un menu (sert aussi à tester chaque Couleur). */
    CHOIX_LIBRE("choix_libre");

    public static final Codec<ModeNaissance> CODEC = StringRepresentable.fromEnum(ModeNaissance::values);

    private final String id;

    ModeNaissance(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Même clé que l'écran des règles de jeu de Fabric : {@code gamerule.redrising.mode_naissance.<valeur>}. */
    public Component getDisplayName() {
        return Component.translatable(Naissance.CLE_REGLE + "." + id);
    }
}
