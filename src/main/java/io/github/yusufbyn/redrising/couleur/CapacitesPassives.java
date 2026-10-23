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
 * Applique au joueur les capacités passives (G3.1) et les faiblesses (G3.4) de sa Couleur.
 *
 * <p>Les bonus d'attributs sont des modificateurs « permanents », sauvegardés avec le joueur : ainsi un bonus de vie
 * est déjà là quand la vie est relue au chargement (sinon elle serait rognée au maximum sans bonus).
 * Ils sont tous retirés puis remis à chaque application, donc changer de Couleur ne laisse rien derrière.</p>
 *
 * <p>Deux fois par seconde, l'entretien réévalue les conditions (bonus et malus conditionnels mis ou retirés)
 * et remet les effets disparus (lait, {@code /effect clear}). Les effets sont de durée infinie, sans particules.</p>
 */
public final class CapacitesPassives {
    /** Préfixe des identifiants de nos modificateurs d'attributs : {@code redrising:couleur/...}. */
    private static final String PREFIXE = "couleur/";
    private static final int PERIODE_ENTRETIEN = 10;

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
                server.getPlayerList().getPlayers().forEach(CapacitesPassives::entretenir);
            }
        });
    }

    /** Retire les capacités de l'ancienne Couleur et met celles de la Couleur actuelle. */
    public static void appliquer(ServerPlayer joueur) {
        ProfilCouleur profil = ProfilsCouleurs.de(CouleurJoueur.get(joueur));
        retirerAttributs(joueur);
        for (ProfilCouleur.BonusAttribut bonus : profil.attributs()) {
            mettre(joueur, bonus, idModificateur("", bonus));
        }
        entretenir(joueur);
    }

    /** Conditions réévaluées, effets remis ou retirés, vie ramenée sous le maximum. */
    private static void entretenir(ServerPlayer joueur) {
        ProfilCouleur profil = ProfilsCouleurs.de(CouleurJoueur.get(joueur));
        List<ProfilCouleur.EffetPermanent> effetsVoulus = new ArrayList<>(profil.effets());

        for (int i = 0; i < profil.conditionnels().size(); i++) {
            ProfilCouleur.BonusConditionnel bloc = profil.conditionnels().get(i);
            boolean actif = bloc.condition().test(joueur);
            for (ProfilCouleur.BonusAttribut bonus : bloc.attributs()) {
                Identifier id = idModificateur("si" + i + "/", bonus);
                if (actif) {
                    mettre(joueur, bonus, id);
                } else {
                    AttributeInstance instance = joueur.getAttribute(bonus.attribut());
                    if (instance != null) {
                        instance.removeModifier(id);
                    }
                }
            }
            if (actif) {
                effetsVoulus.addAll(bloc.effets());
            }
        }

        retirerEffetsEnTrop(joueur, effetsVoulus);
        for (ProfilCouleur.EffetPermanent effet : effetsVoulus) {
            if (!joueur.hasEffect(effet.effet())) {
                // ambient = true, particules = false, icône = true : c'est ce qui marque nos effets
                joueur.addEffect(new MobEffectInstance(effet.effet(), MobEffectInstance.INFINITE_DURATION, effet.niveau(), true, false, true));
            }
        }

        // Un bonus de vie retiré (ou un malus ajouté) ne doit pas laisser plus de vie que le maximum
        if (joueur.getHealth() > joueur.getMaxHealth()) {
            joueur.setHealth(joueur.getMaxHealth());
        }
    }

    /** Met le modificateur s'il manque ; ne touche à rien s'il est déjà là (évite de le renvoyer au client pour rien). */
    private static void mettre(ServerPlayer joueur, ProfilCouleur.BonusAttribut bonus, Identifier id) {
        AttributeInstance instance = joueur.getAttribute(bonus.attribut());
        if (instance == null) {
            RedRising.LOGGER.warn("Attribut {} absent du joueur, bonus ignoré", bonus.attribut().getRegisteredName());
            return;
        }
        AttributeModifier existant = instance.getModifier(id);
        if (existant == null || existant.amount() != bonus.valeur()) {
            instance.addOrReplacePermanentModifier(new AttributeModifier(id, bonus.valeur(), bonus.operation()));
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

    /** Retire nos effets permanents qui ne sont plus voulus (changement de Couleur, condition devenue fausse). */
    private static void retirerEffetsEnTrop(ServerPlayer joueur, List<ProfilCouleur.EffetPermanent> voulus) {
        List<MobEffectInstance> enTrop = new ArrayList<>();
        for (MobEffectInstance instance : joueur.getActiveEffects()) {
            boolean notre = instance.isInfiniteDuration() && instance.isAmbient() && !instance.isVisible();
            boolean prevu = voulus.stream().anyMatch(effet -> effet.effet().equals(instance.getEffect()) && effet.niveau() == instance.getAmplifier());
            if (notre && !prevu) {
                enTrop.add(instance);
            }
        }
        enTrop.forEach(instance -> joueur.removeEffect(instance.getEffect()));
    }

    /** Ex. {@code redrising:couleur/minecraft/max_health/add_value}, ou {@code redrising:couleur/si0/...} pour un bonus conditionnel. */
    private static Identifier idModificateur(String sousDossier, ProfilCouleur.BonusAttribut bonus) {
        Identifier attribut = bonus.attribut().unwrapKey().orElseThrow().identifier();
        return Identifier.fromNamespaceAndPath(RedRising.MOD_ID,
                PREFIXE + sousDossier + attribut.getNamespace() + "/" + attribut.getPath() + "/" + bonus.operation().getSerializedName());
    }

    private static boolean estANous(Identifier id) {
        return id.getNamespace().equals(RedRising.MOD_ID) && id.getPath().startsWith(PREFIXE);
    }
}
