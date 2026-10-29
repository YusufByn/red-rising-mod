package io.github.yusufbyn.redrising.pnj;

import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Les monstres et les Gris (G7.2.4, GDD section 7) : décision de jeu, pas une règle de Minecraft. Dans la mine, 16 monstres
 * tueraient (ou changeraient en zombies) les chasseurs, et attirer les Gris dans le noir suffirait à gagner une traque.
 *
 * <ul>
 *     <li>Aucun monstre ne prend un PNJ Gris pour cible : chasseurs, patrouilles, tous ({@code LivingEntityMixin}, sur
 *     {@code canAttack}, par où passent le choix d'une cible et la poursuite d'une cible déjà prise).</li>
 *     <li>Un Gris ne fuit aucun monstre : le capteur des villageois ne lui signale aucun hostile
 *     ({@code VillagerHostilesSensorMixin}). Sans hostile ni coup reçu, un villageois ne panique pas.</li>
 * </ul>
 *
 * <p>Les PNJ des autres Couleurs restent des villageois ordinaires face aux monstres (question à reprendre en G7.3 pour
 * ceux de la colonie).</p>
 */
public final class GrisEtMonstres {
    private GrisEtMonstres() {
    }

    /** Un PNJ Gris ? */
    public static boolean gris(Entity entite) {
        return entite instanceof Villager pnj && pnj.getAttached(CouleurJoueur.COULEUR) == Couleur.GRIS;
    }

    /** Cet attaquant renonce-t-il à cette cible ? Vrai pour un monstre face à un Gris. */
    public static boolean ignore(Entity attaquant, Entity cible) {
        return attaquant instanceof Enemy && gris(cible);
    }
}
