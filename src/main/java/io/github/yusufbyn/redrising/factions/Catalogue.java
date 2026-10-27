package io.github.yusufbyn.redrising.factions;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Ce que vendent et achètent les boutiques (G5.9). Toutes les valeurs sont 🟡, à régler en jeu, ici seulement.
 *
 * <ul>
 *     <li><b>Société</b>, en crédits ({@link Boutiques#CREDIT}) : elle achète de la production brute (minerais, charbon,
 *     récoltes) et vend des produits finis. Aucun objet n'est sur les deux listes : pas d'achat bas et de revente haute.
 *     Elle n'achète jamais de contrebande, ni ce qu'on lui a volé.</li>
 *     <li><b>Rising</b>, en troc : il ne prend pas d'argent, il prend ce qui manque aux mines (poudre, fer, silex).
 *     Il ne paie jamais en crédits : rien ne revient vers la Société.</li>
 * </ul>
 */
public final class Catalogue {
    private Catalogue() {
    }

    private static final List<Article> ARTICLES = List.of(
            // Société, ce qu'elle vend (Loyauté 40 : l'entrée du poste)
            vend("pain", 1, Items.BREAD, 3, Acces.SEUIL_LOYAUTE_SOCIETE, 8),
            vend("torches", 1, Items.TORCH, 8, Acces.SEUIL_LOYAUTE_SOCIETE, 4),
            vend("carte", 2, Items.MAP, 1, Acces.SEUIL_LOYAUTE_SOCIETE, 2),
            // Société, son équipement (Loyauté 60)
            vend("pioche_fer", 6, Items.IRON_PICKAXE, 1, Acces.SEUIL_EQUIPEMENT_SOCIETE, 1),
            vend("epee_fer", 6, Items.IRON_SWORD, 1, Acces.SEUIL_EQUIPEMENT_SOCIETE, 1),
            vend("plastron_fer", 10, Items.IRON_CHESTPLATE, 1, Acces.SEUIL_EQUIPEMENT_SOCIETE, 1),
            vend("bouclier", 4, Items.SHIELD, 1, Acces.SEUIL_EQUIPEMENT_SOCIETE, 1),
            // Société, ce qu'elle achète, plafonné par jour : 16 crédits par jour au plus
            achete("charbon", Items.COAL, 8, 4),
            achete("fer_brut", Items.RAW_IRON, 4, 4),
            achete("or_brut", Items.RAW_GOLD, 2, 2),
            achete("ble", Items.WHEAT, 16, 3),
            achete("pommes_de_terre", Items.POTATO, 16, 3),
            // Rising, en troc
            new Article("fumigene", Boutique.RISING, Items.GUNPOWDER, 2, Items.IRON_INGOT, 1,
                    ObjetsRebelles.FUMIGENE, 1, Jauge.SOUTIEN, Acces.SEUIL_FUMIGENE, 2),
            new Article("chausse_trappes", Boutique.RISING, Items.IRON_INGOT, 4, Items.FLINT, 1,
                    ObjetsRebelles.CHAUSSE_TRAPPE_OBJET, 3, Jauge.SOUTIEN, Acces.SEUIL_CHAUSSE_TRAPPE, 1)
    );

    /** Articles de cette boutique, dans l'ordre d'affichage. */
    public static List<Article> de(Boutique boutique) {
        return ARTICLES.stream().filter(article -> article.boutique() == boutique).toList();
    }

    public static Article article(String id) {
        return ARTICLES.stream().filter(article -> article.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Article inconnu : " + id));
    }

    private static Article vend(String id, int credits, Item produit, int nombre, int seuil, int parJour) {
        return new Article(id, Boutique.SOCIETE, Boutiques.CREDIT, credits, null, 0, produit, nombre, Jauge.LOYAUTE, seuil, parJour);
    }

    private static Article achete(String id, Item matiere, int nombre, int plafond) {
        return new Article(id, Boutique.SOCIETE, matiere, nombre, null, 0, Boutiques.CREDIT, 1, Jauge.LOYAUTE, Acces.SEUIL_LOYAUTE_SOCIETE, plafond);
    }
}
