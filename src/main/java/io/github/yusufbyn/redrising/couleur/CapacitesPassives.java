package io.github.yusufbyn.redrising.couleur;

import io.github.yusufbyn.redrising.RedRising;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Applique au joueur les capacités passives de sa Couleur (G3.1).
 *
 * <p>Les bonus d'attributs sont des modificateurs « permanents », sauvegardés avec le joueur : ainsi un bonus de vie
 * est déjà là quand la vie est relue au chargement (sinon elle serait rognée au maximum sans bonus).
 * Ils sont tous retirés puis remis à chaque application, donc changer de Couleur ne laisse rien derrière.</p>
 *
 * <p>Les effets sont de durée infinie, sans particules. Ils sont remis chaque seconde s'ils ont disparu
 * (lait, {@code /effect clear}).</p>
 */
public final class CapacitesPassives {
    /** Préfixe des identifiants de nos modificateurs d'attributs : {@code redrising:couleur/...}. */
    private static final String PREFIXE = "couleur/";
    private static final int PERIODE_ENTRETIEN = 20;

    private CapacitesPassives() {
    }

    /** Appelé depuis {@link io.github.yusufbyn.redrising.RedRising#onInitialize()}. */
    public static void init() {
        ServerPlayerEvents.JOIN.register(CapacitesPassives::appliquer);

        // Après la copie des attachments par Fabric, sinon la Couleur n'est pas encore sur le nouveau corps
        ServerPlayerEvents.AFTER_RESPAWN.register(CouleurJoueur.PHASE_APRES_COPIE, (oldPlayer, newPlayer, alive) -> {
            appliquer(newPlayer);
            if (!alive) {
                // On réapparaît avec toute sa vie, bonus de vie compris
                newPlayer.setHealth(newPlayer.getMaxHealth());
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % PERIODE_ENTRETIEN == 0) {
                server.getPlayerList().getPlayers().forEach(CapacitesPassives::entretenirEffets);
            }
        });
    }

    /** Retire les capacités de l'ancienne Couleur et met celles de la Couleur actuelle. */
    public static void appliquer(ServerPlayer joueur) {
        ProfilCouleur profil = ProfilsCouleurs.de(CouleurJoueur.get(joueur));
        retirerAttributs(joueur);
        for (ProfilCouleur.BonusAttribut bonus : profil.attributs()) {
            AttributeInstance instance = joueur.getAttribute(bonus.attribut());
            if (instance == null) {
                RedRising.LOGGER.warn("Attribut {} absent du joueur, bonus ignoré", bonus.attribut().getRegisteredName());
                continue;
            }
            instance.addOrReplacePermanentModifier(new AttributeModifier(idModificateur(bonus), bonus.valeur(), bonus.operation()));
        }
        retirerEffetsEnTrop(joueur, profil);
        entretenirEffets(joueur);
        // Un bonus de vie retiré ne doit pas laisser plus de vie que le nouveau maximum
        if (joueur.getHealth() > joueur.getMaxHealth()) {
            joueur.setHealth(joueur.getMaxHealth());
        }
    }

    private static void retirerAttributs(ServerPlayer joueur) {
        BuiltInRegistries.ATTRIBUTE.listElements().forEach(attribut -> {
            AttributeInstance instance = joueur.getAttribute(attribut);
            if (instance == null) {
                return;
            }
            List<Identifier> nos = new ArrayList<>();
            for (AttributeModifier modificateur : instance.getModifiers()) {
                if (estANous(modificateur.id())) {
                    nos.add(modificateur.id());
                }
            }
            nos.forEach(instance::removeModifier);
        });
    }

    /** Remet les effets permanents de la Couleur qui ont disparu. */
    private static void entretenirEffets(ServerPlayer joueur) {
        for (ProfilCouleur.EffetPermanent effet : ProfilsCouleurs.de(CouleurJoueur.get(joueur)).effets()) {
            if (!joueur.hasEffect(effet.effet())) {
                // ambient = true, particules = false, icône = true : c'est ce qui marque nos effets
                joueur.addEffect(new MobEffectInstance(effet.effet(), MobEffectInstance.INFINITE_DURATION, effet.niveau(), true, false, true));
            }
        }
    }

    /** Retire nos effets permanents qui ne font pas partie du profil (après un changement de Couleur). */
    private static void retirerEffetsEnTrop(ServerPlayer joueur, ProfilCouleur profil) {
        List<MobEffectInstance> enTrop = new ArrayList<>();
        for (MobEffectInstance instance : joueur.getActiveEffects()) {
            boolean notre = instance.isInfiniteDuration() && instance.isAmbient() && !instance.isVisible();
            boolean prevu = profil.effets().stream().anyMatch(effet -> effet.effet().equals(instance.getEffect()) && effet.niveau() == instance.getAmplifier());
            if (notre && !prevu) {
                enTrop.add(instance);
            }
        }
        enTrop.forEach(instance -> joueur.removeEffect(instance.getEffect()));
    }

    private static Identifier idModificateur(ProfilCouleur.BonusAttribut bonus) {
        Identifier attribut = bonus.attribut().unwrapKey().orElseThrow().identifier();
        return Identifier.fromNamespaceAndPath(RedRising.MOD_ID,
                PREFIXE + attribut.getNamespace() + "/" + attribut.getPath() + "/" + bonus.operation().getSerializedName());
    }

    private static boolean estANous(Identifier id) {
        return id.getNamespace().equals(RedRising.MOD_ID) && id.getPath().startsWith(PREFIXE);
    }
}
