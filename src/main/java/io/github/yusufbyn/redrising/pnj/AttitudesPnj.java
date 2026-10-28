package io.github.yusufbyn.redrising.pnj;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.data.EtatMonde;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.EffetsActions;
import io.github.yusufbyn.redrising.jalons.Attitude;
import io.github.yusufbyn.redrising.jalons.Personnage;
import io.github.yusufbyn.redrising.jalons.Personnages;
import io.github.yusufbyn.redrising.monde.Vue;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * L'attitude des personnages canon envers les joueurs (G6.5, GDD section 8), côté serveur. Les valeurs et les règles
 * (bornes, plafond des gains) sont dans {@link Attitude}.
 *
 * <ul>
 *     <li><b>Sur lui</b> : l'aider (nourriture) le fait monter, le mettre à terre le fait baisser.</li>
 *     <li><b>Ce qu'il voit</b> (s'il a un camp) : voler, tuer ou dénoncer contre son camp, ou contre le camp adverse. Mêmes
 *     règles de vue que les témoins (portée, murs, fumée, pas à terre), mais le silence d'une Couleur (G6.3) ne joue
 *     pas : il ne touche que ce que la Société apprend, pas ce que le personnage pense du joueur.</li>
 *     <li><b>Sa cellule qui tombe</b> (il y était, ou elle était son foyer) : dénoncée, ou repérée puis descente.</li>
 *     <li>Le joueur le sent sans voir de chiffre : particules (pour lui seul) et une ligne dans le chat.</li>
 * </ul>
 */
public final class AttitudesPnj {
    private AttitudesPnj() {
    }

    /** Ce joueur a aidé ce PNJ (une fois par jour : la règle d'aide). Rien si le PNJ n'incarne personne. */
    public static void aide(ServerPlayer joueur, Villager pnj) {
        changer(joueur, pnj, Attitude.AIDE, "l'a aidé");
    }

    /** Change l'attitude du personnage de ce PNJ envers ce joueur, avec particules et message. */
    public static void changer(ServerPlayer joueur, Villager pnj, int variation, String cause) {
        String personnage = pnj.getAttached(PnjEssentiels.PERSONNAGE);
        if (personnage != null) {
            changer(joueur.level().getServer(), personnage, joueur.getUUID(), variation, pnj, cause);
        }
    }

    /**
     * Ce joueur a agi contre ce camp ({@code lese}) : chaque personnage avec un camp qui le voit change d'attitude.
     * {@code exclu} : la victime (elle ne se voit pas elle-même ; ce qu'elle subit est compté à part).
     */
    public static void actionVue(ServerPlayer joueur, @Nullable Entity exclu, @Nullable Camp lese, Attitude.Acte acte) {
        if (lese == null) {
            return;
        }
        MinecraftServer server = joueur.level().getServer();
        EtatMonde etat = EtatMondeServeur.get(server);
        double portee = EffetsActions.PORTEE_TEMOIN;
        for (Villager pnj : joueur.level().getEntities(EntityTypes.VILLAGER, joueur.getBoundingBox().inflate(portee),
                pnj -> pnj != exclu && pnj.isAlive() && pnj.hasAttached(PnjEssentiels.PERSONNAGE) && pnj.distanceTo(joueur) <= portee)) {
            Personnage personnage = Personnages.get(pnj.getAttached(PnjEssentiels.PERSONNAGE));
            if (personnage == null || PnjEssentiels.inconscient(pnj) || !Vue.voit(pnj, joueur)) {
                continue;
            }
            int variation = Attitude.vu(acte, lese, Personnages.camp(personnage, etat));
            if (variation != 0) {
                changer(server, personnage.id(), joueur.getUUID(), variation, pnj,
                        "a vu " + acte.name().toLowerCase() + " contre " + lese.getSerializedName());
            }
        }
    }

    /**
     * La cellule de ces personnages est tombée par la faute de ce joueur (dénonciation, ou descente sur une cellule qu'il
     * a fait repérer). Hors ligne : l'attitude est dans l'état du monde, elle change tout de suite (un jalon qui passe
     * avant son retour en tient compte) ; il l'apprend à son retour, avec le message de la cellule tombée.
     */
    public static void celluleTombee(MinecraftServer server, Set<String> personnages, UUID joueur, int variation, String cause) {
        for (String personnage : personnages) {
            changer(server, personnage, joueur, variation, null, cause);
        }
    }

    private static void changer(MinecraftServer server, String personnage, UUID joueur, int variation, @Nullable Villager pnj, String cause) {
        long jour = server.overworld().getGameTime() / ActionsFaction.TICKS_PAR_JOUR;
        Attitude avant = EtatMondeServeur.get(server).attitude(personnage, joueur);
        Attitude apres = avant.changer(variation, jour);
        EtatMondeServeur.modifier(server, etat -> etat.avecAttitude(personnage, joueur, apres));
        ServerPlayer present = server.getPlayerList().getPlayer(joueur);
        RedRising.LOGGER.info("Attitude de {} envers {} : {} → {} ({}, {})", personnage,
                present == null ? joueur : present.getPlainTextName(), avant.valeur(), apres.valeur(), cause, variation);
        // Plafond du jour atteint, ou déjà à la borne : rien de visible
        if (present == null || apres.valeur() == avant.valeur()) {
            return;
        }
        boolean mieux = apres.valeur() > avant.valeur();
        Component nom = Component.translatableWithFallback("personnage.redrising.nom." + personnage, personnage);
        present.sendSystemMessage(Component.translatable(mieux ? "personnage.redrising.attitude.mieux" : "personnage.redrising.attitude.pire", nom)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (pnj != null && pnj.level() == present.level() && pnj.level() instanceof ServerLevel monde) {
            monde.sendParticles(present, mieux ? ParticleTypes.HEART : ParticleTypes.ANGRY_VILLAGER, false, false,
                    pnj.getX(), pnj.getY() + pnj.getBbHeight() + 0.3, pnj.getZ(), 3, 0.3, 0.2, 0.3, 0);
        }
    }
}
