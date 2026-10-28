package io.github.yusufbyn.redrising.jalons;

import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.data.EtatMonde;
import io.github.yusufbyn.redrising.factions.Variation;

import java.util.Map;
import java.util.Optional;

/**
 * Effets des jalons sur l'état du monde (G6.3). Logique pure (testée) ; le côté serveur est {@link EffetsMonde}.
 *
 * <ul>
 *     <li>Poser les effets d'un jalon ne fait qu'ajouter ce qui manque (identifiants stables) : un rejeu ne double rien
 *     et ne relance pas une durée.</li>
 *     <li>Deux effets sur le même levier : le niveau le plus fort l'emporte ; chacun garde sa durée.</li>
 * </ul>
 */
public final class Effets {
    private Effets() {
    }

    /**
     * Pose les effets de cette issue du jalon (leviers, versements, nouvelle) et marque ses effets appliqués, en une
     * seule écriture.
     */
    public static EtatMonde appliquer(EtatMonde etat, String jalon, Issue issue, EffetsJalon effets, long temps) {
        String prefixe = jalon + "/" + issue.getSerializedName() + "/";
        int numero = 0;
        for (EffetsJalon.LevierDeclare levier : effets.leviers()) {
            String id = prefixe + numero++;
            if (!etat.effets().containsKey(id)) {
                long fin = levier.duree() == EffetEnCours.PERMANENT ? EffetEnCours.PERMANENT : temps + levier.duree();
                etat = etat.avecEffet(id, new EffetEnCours(levier.levier(), levier.niveau(), jalon, temps, fin));
            }
        }
        for (EffetsJalon.VersementDeclare versement : effets.versements()) {
            etat = etat.avecVersement(new Versement(prefixe + numero++, jalon, issue, Optional.ofNullable(versement.pour()),
                    versement.variation(), false, temps));
        }
        etat = etat.avecVersement(new Versement(prefixe + "nouvelle", jalon, issue, Optional.empty(),
                Variation.AUCUNE, true, temps));
        return etat.avecEffetsEnAttente(jalon, false);
    }

    /** Le niveau en cours de ce levier : le plus fort des effets actifs, 0 sinon. */
    public static int niveau(Map<String, EffetEnCours> effets, Levier levier, long temps) {
        int niveau = 0;
        for (EffetEnCours effet : effets.values()) {
            if (effet.levier() == levier && effet.actif(temps)) {
                niveau = Math.max(niveau, effet.niveau());
            }
        }
        return niveau;
    }

    /** Les PNJ de cette Couleur se taisent-ils (un levier de silence en cours) ? */
    public static boolean silencieuse(Map<String, EffetEnCours> effets, Couleur couleur, long temps) {
        for (Levier levier : Levier.values()) {
            if (levier.silence() == couleur && niveau(effets, levier, temps) > 0) {
                return true;
            }
        }
        return false;
    }
}
