package io.github.yusufbyn.redrising.factions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Optional;

/**
 * Une offre de boutique (G5.9), côté serveur : un échange de villageois ordinaire (même écran), qui compte l'échange dans
 * le stock du jour du joueur, ne donne pas d'expérience, et refuse en paiement ce qui a été volé au camp de la boutique.
 * Le client reçoit une offre ordinaire : c'est le serveur qui décide.
 */
public class OffreBoutique extends MerchantOffer {
    private final ServerPlayer joueur;
    private final Article article;

    public OffreBoutique(ServerPlayer joueur, Article article, ItemCost prixA, Optional<ItemCost> prixB, ItemStack produit,
                         int faitsAujourdhui) {
        super(prixA, prixB, produit, faitsAujourdhui, article.parJour(), 0, 0);
        this.joueur = joueur;
        this.article = article;
    }

    @Override
    public void increaseUses() {
        super.increaseUses();
        Boutiques.echange(joueur, article);
    }

    @Override
    public boolean shouldRewardExp() {
        return false;
    }

    @Override
    public boolean satisfiedBy(ItemStack paiementA, ItemStack paiementB) {
        Camp camp = article.boutique().camp();
        if (Boutiques.voleA(paiementA) == camp || Boutiques.voleA(paiementB) == camp) {
            return false;
        }
        return super.satisfiedBy(paiementA, paiementB);
    }
}
