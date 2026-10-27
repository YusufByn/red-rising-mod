package io.github.yusufbyn.redrising.factions;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Les deux boutiques (G5.9) : le comptoir de la Société (commis Cuivre du poste, en crédits) et la réserve du Rising
 * (le Fils de la cellule, en troc). Chacune applique le seuil d'entrée de sa structure.
 */
public enum Boutique implements StringRepresentable {
    SOCIETE("societe", Camp.SOCIETE),
    RISING("rising", Camp.RISING);

    public static final Codec<Boutique> CODEC = StringRepresentable.fromEnum(Boutique::values);

    private final String id;
    private final Camp camp;

    Boutique(String id, Camp camp) {
        this.id = id;
        this.camp = camp;
    }

    public Camp camp() {
        return camp;
    }

    /** Même seuil que l'entrée de la structure : un joueur entré par effraction n'achète rien. */
    public boolean admet(Jauges jauges) {
        return switch (this) {
            case SOCIETE -> Acces.peutEntrerSociete(jauges);
            case RISING -> Acces.peutEntrerRising(jauges);
        };
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
