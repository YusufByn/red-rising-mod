package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * G5.1 : un PNJ de chaque Couleur existe, avec son métier et ses repères teintés.
 * Prend une capture des 14 PNJ côte à côte, copiée dans {@code build/captures-gametest/}.
 */
public class PnjCouleurTest implements FabricClientGameTest {
    /** Rangée face au joueur, comme les mannequins de G3.5 : l'Or à gauche de l'écran (x = 7), le Rouge à droite. */
    private static final double Z_RANGEE = 9.5;
    /** Tournés vers le nord, donc vers le joueur. */
    private static final float VERS_LE_JOUEUR = 180;
    /** PNJ laissé libre (avec son IA), derrière le joueur, pour vérifier qu'il garde son métier. */
    private static final Couleur LIBRE = Couleur.ROUGE;

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave sauvegarde;

        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runCommand("tp @a 0.5 -60 0.5 0 10");

            monde.getServer().runOnServer(server -> {
                Couleur[] couleurs = Couleur.values();
                for (int i = 0; i < couleurs.length; i++) {
                    Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), new Vec3(7.0 - i, -60, Z_RANGEE), VERS_LE_JOUEUR, couleurs[i]));
                    pnj.setNoAi(true); // immobiles et tournés vers le joueur pour la capture
                }
                Objects.requireNonNull(PnjCouleur.creer(server.overworld(), new Vec3(0.5, -60, -6.5), 0, LIBRE));
            });
            verifierServeur(monde, "à la création", 15);

            // Les clients reçoivent la Couleur de chaque PNJ (synchronisée avec tous)
            context.waitFor(mc -> villageoisColoresClient(mc.level) == 15, 100);
            RedRising.LOGGER.info("[G5.1] Couleur des 15 PNJ reçue par le client");

            context.getInput().resizeWindow(1920, 1080);
            context.runOnClient(mc -> {
                mc.options.fov().set(50);
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
            });
            monde.getConnection().waitForChunksRender();
            context.waitTicks(10);
            OutilsTest.garderCapture(context.takeScreenshot("pnj-couleurs"));
            context.runOnClient(mc -> {
                mc.options.fov().set(70);
                mc.gui.hud.toggle();
            });
            context.getInput().resizeWindow(854, 480);

            // Le PNJ libre n'a pas de poste de travail : son métier doit tenir quand même
            context.waitTicks(200);
            verifierServeur(monde, "après 200 ticks", 15);

            // Commande de debug : un PNJ Vert devant le joueur
            monde.getServer().runCommand("execute positioned 0.5 -60 3.5 rotated 0 0 run rr pnj spawn vert");
            verifierServeur(monde, "après /rr pnj spawn", 16);

            sauvegarde = monde.getWorldSave();
        }

        // Rechargement : Couleurs et métiers sont sauvegardés avec les PNJ
        try (TestSingleplayerContext monde = sauvegarde.open()) {
            monde.getServer().waitFor(server -> pnjColores(server).size() == 16, 200);
            verifierServeur(monde, "après rechargement", 16);
            postesDeTravail(monde);
        }
        RedRising.LOGGER.info("[G5.1] OK : un PNJ de chaque Couleur, métier gardé, sauvegardé");
    }

    /**
     * Les Couleurs sans métier (Or, Rose) ne prennent pas celui d'un poste de travail posé à côté d'elles.
     * Témoin : un villageois ordinaire sans métier, avec le même poste à côté, doit le prendre (sinon le test ne prouve rien).
     */
    private static void postesDeTravail(TestSingleplayerContext monde) {
        // Rien ne doit déranger le témoin pendant qu'il va à son poste : pas de monstres (un zombie le fait fuir ou le tue,
        // échec aléatoire vu le 27/10), plein jour, joueur tout près (la zone est chargée et simulée)
        monde.getServer().runCommand("difficulty peaceful");
        monde.getServer().runCommand("time set noon");
        monde.getServer().runCommand("tp @a -26.5 -60 -4.5");
        Couleur[] sansMetier = {Couleur.OR, Couleur.ROSE};
        int[] temoin = monde.getServer().computeOnServer(server -> {
            ServerLevel niveau = server.overworld();
            for (int i = 0; i <= sansMetier.length; i++) {
                BlockPos poste = new BlockPos(-20 - 6 * i, -60, -12);
                niveau.setBlockAndUpdate(poste, Blocks.LECTERN.defaultBlockState());
                // Juste à côté : Minecraft donne le métier à moins de 2 blocs du centre du poste
                Vec3 position = Vec3.atBottomCenterOf(poste.south());
                if (i < sansMetier.length) {
                    Objects.requireNonNull(PnjCouleur.creer(niveau, position, 180, sansMetier[i]));
                } else {
                    Villager ordinaire = Objects.requireNonNull(EntityTypes.VILLAGER.create(niveau, EntitySpawnReason.COMMAND));
                    ordinaire.snapTo(position.x, position.y, position.z, 180, 0);
                    ordinaire.setVillagerData(ordinaire.getVillagerData().withProfession(niveau.registryAccess(), VillagerProfession.NONE));
                    niveau.addFreshEntity(ordinaire);
                    return new int[]{ordinaire.getId()};
                }
            }
            throw new IllegalStateException();
        });
        // Le témoin prend son poste (il lui faut le repérer et y aller : jusqu'à une minute)
        try {
            monde.getServer().waitFor(server -> {
                var entite = server.overworld().getEntity(temoin[0]);
                return entite instanceof Villager v && v.getVillagerData().profession().is(VillagerProfession.LIBRARIAN);
            }, 20 * 60);
        } catch (AssertionError e) {
            String etat = monde.getServer().computeOnServer(server -> {
                StringBuilder texte = new StringBuilder();
                texte.append("\n  jour ").append(server.overworld().isBrightOutside()).append(", difficulté ").append(server.overworld().getDifficulty());
                for (Villager v : server.overworld().getEntities(EntityTypes.VILLAGER, v -> true)) {
                    texte.append("\n  ").append(v.getId() == temoin[0] ? "témoin" : PnjCouleur.couleur(v))
                            .append(" en ").append(v.blockPosition().toShortString())
                            .append(" : ").append(v.getVillagerData().profession().getRegisteredName())
                            .append(", job ").append(v.getBrain().getMemoryInternal(MemoryModuleType.JOB_SITE))
                            .append(", potentiel ").append(v.getBrain().getMemoryInternal(MemoryModuleType.POTENTIAL_JOB_SITE))
                            .append(", IA ").append(!v.isNoAi()).append(", vivant ").append(v.isAlive());
                }
                return texte.toString();
            });
            throw new AssertionError("Le témoin n'a pas pris de poste de travail en une minute :" + etat, e);
        }
        RedRising.LOGGER.info("[G5.1] Témoin : un villageois ordinaire sans métier a pris le poste de travail posé à côté");
        // Les PNJ de Couleur ont eu le même temps, avec chacun son poste à côté : ils n'ont pas dû changer
        verifierServeur(monde, "avec des postes de travail à côté", 18);
    }

    /** Chaque PNJ de Couleur a le métier de sa Couleur ; chaque Couleur a au moins un PNJ. */
    private static void verifierServeur(TestSingleplayerContext monde, String moment, int attendus) {
        String erreur = monde.getServer().computeOnServer(server -> {
            Map<Villager, Couleur> pnj = pnjColores(server);
            if (pnj.size() != attendus) {
                return pnj.size() + " PNJ de Couleur au lieu de " + attendus;
            }
            Map<Couleur, Integer> parCouleur = new EnumMap<>(Couleur.class);
            for (Map.Entry<Villager, Couleur> entree : pnj.entrySet()) {
                var metier = entree.getKey().getVillagerData().profession();
                if (!metier.is(PnjCouleur.METIERS.get(entree.getValue()))) {
                    return "PNJ " + entree.getValue() + " avec le métier " + metier.getRegisteredName();
                }
                parCouleur.merge(entree.getValue(), 1, Integer::sum);
            }
            return parCouleur.size() == Couleur.values().length ? null : "Couleurs présentes : " + parCouleur.keySet();
        });
        if (erreur != null) {
            throw new AssertionError("PNJ " + moment + " : " + erreur);
        }
        RedRising.LOGGER.info("[G5.1] PNJ {} : {} PNJ, chaque Couleur présente avec son métier", moment, attendus);
    }

    private static Map<Villager, Couleur> pnjColores(MinecraftServer server) {
        Map<Villager, Couleur> pnj = new HashMap<>();
        for (Villager villageois : server.overworld().getEntities(EntityTypes.VILLAGER, v -> true)) {
            Couleur couleur = PnjCouleur.couleur(villageois);
            if (couleur != null) {
                pnj.put(villageois, couleur);
            }
        }
        return pnj;
    }

    private static int villageoisColoresClient(@Nullable ClientLevel monde) {
        if (monde == null) {
            return 0;
        }
        int total = 0;
        for (var entite : monde.entitiesForRendering()) {
            if (entite instanceof Villager && entite.getAttached(CouleurJoueur.COULEUR) != null) {
                total++;
            }
        }
        return total;
    }
}
