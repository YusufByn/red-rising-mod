package io.github.yusufbyn.redrising.pnj;

import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * PNJ par Couleur (G5.1) : des villageois de Minecraft, avec une Couleur et un métier qui lui correspond.
 *
 * <p>La Couleur est la même donnée que celle du joueur ({@link CouleurJoueur#COULEUR}) : un attachment Fabric
 * sauvegardé avec l'entité et synchronisé avec tous les clients, qui dessinent ses repères (yeux, cheveux, mains)
 * teintés de la Couleur. Un villageois sans Couleur reste un villageois ordinaire.</p>
 *
 * <p>Le métier est verrouillé : un villageois qui n'a jamais commercé perd son métier s'il n'a pas de poste de travail,
 * et un PNJ de Couleur n'en a pas. Lui donner un peu d'expérience suffit à le garder, comme après un premier échange.</p>
 */
public final class PnjCouleur {
    /** Métier de chaque Couleur. Les métiers de Minecraft ne couvrent pas tout : Argent et Blanc, Cuivre et Bleu partagent le leur. */
    public static final Map<Couleur, ResourceKey<VillagerProfession>> METIERS = new EnumMap<>(Map.ofEntries(
            Map.entry(Couleur.OR, VillagerProfession.WEAPONSMITH),      // combat, razor
            Map.entry(Couleur.ARGENT, VillagerProfession.LIBRARIAN),    // registres et comptes
            Map.entry(Couleur.CUIVRE, VillagerProfession.CARTOGRAPHER), // cadastre, titres de propriété
            Map.entry(Couleur.BLANC, VillagerProfession.LIBRARIAN),     // justice, enchantement avancé
            Map.entry(Couleur.BLEU, VillagerProfession.CARTOGRAPHER),   // pilotage, navigation
            Map.entry(Couleur.JAUNE, VillagerProfession.CLERIC),        // médecine, potions
            Map.entry(Couleur.VERT, VillagerProfession.TOOLSMITH),      // technologie
            Map.entry(Couleur.VIOLET, VillagerProfession.SHEPHERD),     // art, teintures
            Map.entry(Couleur.ORANGE, VillagerProfession.ARMORER),      // mécanique, armures
            Map.entry(Couleur.GRIS, VillagerProfession.FLETCHER),       // soldat, combat à distance
            Map.entry(Couleur.OBSIDIAN, VillagerProfession.LEATHERWORKER), // tribus des pôles, fourrures
            Map.entry(Couleur.MARRON, VillagerProfession.FARMER),       // service, agriculture
            Map.entry(Couleur.ROSE, VillagerProfession.NITWIT),         // espionnage : pas de commerce
            Map.entry(Couleur.ROUGE, VillagerProfession.MASON)          // mine, pierre
    ));

    /** Expérience donnée pour verrouiller le métier (comme après un premier échange). */
    private static final int EXPERIENCE_VERROU = 1;

    private PnjCouleur() {
    }

    /** Fait apparaître un PNJ de cette Couleur. Renvoie {@code null} si le monde refuse l'entité. */
    public static @Nullable Villager creer(ServerLevel monde, Vec3 position, float regard, Couleur couleur) {
        Villager pnj = EntityTypes.VILLAGER.create(monde, EntitySpawnReason.COMMAND);
        if (pnj == null) {
            return null;
        }
        pnj.snapTo(position.x, position.y, position.z, regard, 0);
        pnj.setYHeadRot(regard);
        pnj.setYBodyRot(regard);
        donnerCouleur(pnj, couleur);
        return monde.addFreshEntity(pnj) ? pnj : null;
    }

    /** Donne (ou change) la Couleur d'un villageois, avec le métier qui va avec. */
    public static void donnerCouleur(Villager pnj, Couleur couleur) {
        pnj.setAttached(CouleurJoueur.COULEUR, couleur);
        pnj.setVillagerData(pnj.getVillagerData().withProfession(pnj.level().registryAccess(), METIERS.get(couleur)));
        pnj.setVillagerXp(Math.max(pnj.getVillagerXp(), EXPERIENCE_VERROU));
    }

    /** Couleur d'un villageois, ou {@code null} pour un villageois ordinaire. */
    public static @Nullable Couleur couleur(Villager pnj) {
        return pnj.getAttached(CouleurJoueur.COULEUR);
    }
}
