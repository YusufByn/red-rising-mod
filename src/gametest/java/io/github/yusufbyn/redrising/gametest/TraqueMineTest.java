package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.factions.Traque;
import io.github.yusufbyn.redrising.monde.CelluleRising;
import io.github.yusufbyn.redrising.monde.MineLykos;
import io.github.yusufbyn.redrising.monde.PlanDeMine;
import io.github.yusufbyn.redrising.monde.Vue;
import io.github.yusufbyn.redrising.pnj.TraqueGris;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * G7.2.4 : la traque dans la mine, sur un vrai monde Mars (première graine de {@link MineLykosTest}).
 *
 * <ul>
 *     <li>{@code /rr mine traque} : le joueur dans une galerie sombre du niveau du bas, la traque commence ; les chasseurs
 *     apparaissent à des points d'entrée des Gris (sur le réseau éclairé), hors de vue, à 24-64 blocs sur son niveau ou un
 *     voisin (ou au repli).</li>
 *     <li>Recalage : le joueur deux niveaux plus haut ; pas de Gris sur son niveau avant 20 secondes, puis les mêmes
 *     chasseurs le rejoignent, hors de vue (ni disparus, ni remplacés).</li>
 *     <li>Repli : le joueur dans une galerie sombre sans point d'entrée à 24-64 blocs ; les chasseurs apparaissent au plus
 *     proche hors de vue de son niveau.</li>
 *     <li>{@code /rr mine tp} (quai du puits) et {@code /rr mine cellule} (cellule de G5.4 sur un emplacement réservé).</li>
 * </ul>
 */
public class TraqueMineTest implements FabricClientGameTest {
    /** Attente des chasseurs : le temps que les chunks autour du joueur soient générés et actifs. */
    private static final int ATTENTE_CHASSEURS = 1200;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext monde = MineLykosTest.mondeMars(context, MineLykosTest.GRAINES[0])) {
            OutilsTest.attendreArrivee(context);
            // En survie (les chasseurs ne visent pas un joueur en créatif), sans dégâts ni faim
            monde.getServer().runCommand("gamemode survival @a[limit=1]");
            monde.getServer().runCommand("effect give @a[limit=1] minecraft:resistance infinite 4 true");
            monde.getServer().runCommand("effect give @a[limit=1] minecraft:saturation infinite 0 true");
            PlanDeMine plan = Objects.requireNonNull(monde.getServer().computeOnServer(server -> MineLykos.planEnJeu(server.overworld())), "pas de plan");

            traqueAuFond(monde, plan);
            recalage(monde, context, plan);
            repli(monde, context, plan);
            quaiEtCellule(monde, context, plan);
        }
        RedRising.LOGGER.info("[G7.2.4] OK : traque dans la mine (apparition aux points d'entrée, recalage, repli), commandes de la mine");
    }

    // --- Traque au fond ---

    private static void traqueAuFond(TestSingleplayerContext monde, PlanDeMine plan) {
        int bas = plan.sols().size() - 1;
        monde.getServer().runCommand("rr mine traque " + (bas + 1) + " @a[limit=1]");
        String place = monde.getServer().computeOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            BlockPos pos = joueur.blockPosition();
            if (!plan.galeriesSombres(bas, 60).contains(pos) || !joueur.hasAttached(TraqueGris.TRAQUE)) {
                return "joueur en " + pos.toShortString() + ", traque " + joueur.hasAttached(TraqueGris.TRAQUE);
            }
            return null;
        });
        if (place != null) {
            throw new AssertionError("/rr mine traque : " + place);
        }
        String apparition = attendreApparition(monde, plan, Traque.CHASSEURS_DEPART);
        RedRising.LOGGER.info("[G7.2.4] Traque au fond (niveau {}) : {}", bas + 1, apparition);
    }

    /**
     * Attend que le joueur ait ce nombre de chasseurs, et vérifie leur place dans le tick où on les voit (ils n'ont pas
     * encore marché) : à un point d'entrée, hors de vue ; à 24-64 blocs sur son niveau ou un voisin si un point convient,
     * sinon sur son niveau.
     */
    private static String attendreApparition(TestSingleplayerContext monde, PlanDeMine plan, int nombre) {
        String[] rapport = {null};
        monde.getServer().waitFor(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            if (TraqueGris.nombreDeChasseurs(joueur) < nombre) {
                return false;
            }
            int niveau = plan.niveauA(joueur.blockPosition());
            Set<BlockPos> plage = plan.entreesPour(joueur.blockPosition(), niveau, Traque.APPARITION_MINE_MIN, Traque.APPARITION_MINE_MAX)
                    .stream().map(PlanDeMine.Entree::pos).collect(Collectors.toSet());
            List<String> lignes = new ArrayList<>();
            for (Villager chasseur : chasseurs(server)) {
                PlanDeMine.Entree entree = plan.entreesDesGris().stream()
                        .min(Comparator.comparingDouble(e -> e.pos().distSqr(chasseur.blockPosition()))).orElseThrow();
                double distance = Math.sqrt(entree.pos().distSqr(joueur.blockPosition()));
                String ou = chasseur.blockPosition().toShortString() + " (niveau " + (entree.niveau() + 1) + ", " + Math.round(distance) + " blocs)";
                if (entree.pos().distSqr(chasseur.blockPosition()) > 2) {
                    throw new AssertionError("Chasseur hors d'un point d'entrée : " + ou);
                }
                if (Vue.voit(chasseur, joueur)) {
                    throw new AssertionError("Chasseur apparu sous les yeux du joueur : " + ou);
                }
                if (!plage.isEmpty() ? !plage.contains(entree.pos()) : entree.niveau() != niveau) {
                    throw new AssertionError("Chasseur mal placé (" + (plage.isEmpty() ? "repli" : plage.size() + " points à 24-64 blocs") + ") : " + ou);
                }
                lignes.add(ou);
            }
            rapport[0] = (plage.isEmpty() ? "repli, " : plage.size() + " points à 24-64 blocs, ") + "chasseurs en " + String.join(" ; ", lignes);
            return true;
        }, ATTENTE_CHASSEURS);
        return rapport[0];
    }

    // --- Recalage ---

    /** Deux niveaux plus haut : les mêmes chasseurs le rejoignent au bout de 20 secondes, pas avant. */
    private static void recalage(TestSingleplayerContext monde, ClientGameTestContext context, PlanDeMine plan) {
        int haut = plan.sols().size() - 3;
        Set<UUID> avant = monde.getServer().computeOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            // Traque remise à zéro, chasseurs gardés : ni renfort ni victoire pendant l'attente
            joueur.setAttached(TraqueGris.TRAQUE, Traque.DEBUT);
            BlockPos ici = joueur.blockPosition();
            BlockPos place = plan.galeriesSombres(haut, 0).stream()
                    .min(Comparator.comparingDouble(p -> Math.hypot(p.getX() - ici.getX(), p.getZ() - ici.getZ()))).orElseThrow();
            joueur.teleportTo(place.getX() + 0.5, place.getY(), place.getZ() + 0.5);
            return uuids(server);
        });
        context.waitTicks(Traque.RECALAGE - 100);
        String tot = monde.getServer().computeOnServer(server -> {
            if (chasseurs(server).stream().anyMatch(chasseur -> plan.niveauA(chasseur.blockPosition()) == haut)) {
                return "un chasseur a rejoint le joueur avant 20 secondes";
            }
            return uuids(server).equals(avant) ? null : "chasseurs remplacés pendant l'attente";
        });
        if (tot != null) {
            throw new AssertionError("Recalage : " + tot);
        }
        monde.getServer().waitFor(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            List<Villager> chasseurs = chasseurs(server);
            if (chasseurs.stream().anyMatch(chasseur -> plan.niveauA(chasseur.blockPosition()) != haut)) {
                return false;
            }
            if (!uuids(server).equals(avant) || TraqueGris.nombreDeChasseurs(joueur) != avant.size()) {
                throw new AssertionError("Recalage : chasseurs " + uuids(server) + " au lieu de " + avant);
            }
            for (Villager chasseur : chasseurs) {
                if (Vue.voit(chasseur, joueur)) {
                    throw new AssertionError("Recalage : chasseur posé sous les yeux du joueur en " + chasseur.blockPosition().toShortString());
                }
            }
            return true;
        }, 300);
        RedRising.LOGGER.info("[G7.2.4] Recalage : les {} mêmes chasseurs rejoignent le niveau {} après 20 secondes, hors de vue", avant.size(), haut + 1);
        monde.getServer().runCommand("rr traque stop @a[limit=1]");
    }

    // --- Repli ---

    /** Une galerie sombre sans point d'entrée à 24-64 blocs : les chasseurs apparaissent au plus proche hors de vue de son niveau. */
    private static void repli(TestSingleplayerContext monde, ClientGameTestContext context, PlanDeMine plan) {
        String place = monde.getServer().computeOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            BlockPos ici = joueur.blockPosition();
            BlockPos isolee = null;
            for (int niveau = plan.sols().size() - 1; niveau >= 0 && isolee == null; niveau--) {
                int n = niveau;
                isolee = plan.galeriesSombres(n, 0).stream().filter(p -> plan.entreesPour(p, n, Traque.APPARITION_MINE_MIN, Traque.APPARITION_MINE_MAX).isEmpty())
                        .min(Comparator.comparingDouble(p -> Math.hypot(p.getX() - ici.getX(), p.getZ() - ici.getZ()))).orElse(null);
            }
            Objects.requireNonNull(isolee, "aucune galerie sombre isolée");
            joueur.teleportTo(isolee.getX() + 0.5, isolee.getY(), isolee.getZ() + 0.5);
            return isolee.toShortString();
        });
        // Le temps que les chunks de là-bas soient actifs
        context.waitTicks(200);
        monde.getServer().runCommand("rr traque start @a[limit=1]");
        String apparition = attendreApparition(monde, plan, Traque.CHASSEURS_DEPART);
        RedRising.LOGGER.info("[G7.2.4] Repli depuis {} : {}", place, apparition);
        monde.getServer().runCommand("rr traque stop @a[limit=1]");
    }

    // --- Commandes ---

    private static void quaiEtCellule(TestSingleplayerContext monde, ClientGameTestContext context, PlanDeMine plan) {
        monde.getServer().runCommand("rr mine tp 2 @a[limit=1]");
        String quai = monde.getServer().computeOnServer(server -> {
            BlockPos pos = OutilsTest.joueur(server).blockPosition();
            BlockPos centre = plan.centre().atY(plan.sols().get(1));
            return plan.entreesDesGris().stream().anyMatch(e -> e.niveau() == 1 && e.pos().equals(pos)) && pos.distManhattan(centre) <= 3
                    ? null : pos.toShortString();
        });
        if (quai != null) {
            throw new AssertionError("/rr mine tp 2 : joueur en " + quai + ", pas au quai du puits");
        }

        monde.getServer().runCommand("rr mine cellule 1");
        context.waitTicks(5);
        String cellule = monde.getServer().computeOnServer(server -> {
            PlanDeMine.EmplacementCellule emplacement = plan.cellules().getFirst();
            CelluleRising.Cellule construite = CelluleRising.contenant(server, emplacement.salle().getCenter());
            if (construite == null) {
                return "pas de cellule enregistrée dans l'emplacement";
            }
            // De la galerie à l'entrée cachée : on y arrive à pied
            Direction versGalerie = construite.versSalle().getOpposite();
            for (BlockPos pas = construite.entree().relative(versGalerie); !plan.interieur(pas.getX(), pas.getY(), pas.getZ()); pas = pas.relative(versGalerie)) {
                if (!server.overworld().getBlockState(pas).isAir() || !server.overworld().getBlockState(pas.above()).isAir()) {
                    return "passage fermé en " + pas.toShortString();
                }
            }
            return server.overworld().getBlockState(construite.entree()).isAir() ? "entrée cachée ouverte" : null;
        });
        if (cellule != null) {
            throw new AssertionError("/rr mine cellule 1 : " + cellule);
        }
        RedRising.LOGGER.info("[G7.2.4] /rr mine tp (quai du niveau 2) et /rr mine cellule (emplacement 1) : OK");
    }

    // --- Outils ---

    private static List<Villager> chasseurs(MinecraftServer server) {
        BlockPos centre = Objects.requireNonNull(MineLykos.centre());
        return server.overworld().getEntities(EntityTypes.VILLAGER, new AABB(centre).inflate(300), pnj -> pnj.hasAttached(TraqueGris.CHASSEUR));
    }

    private static Set<UUID> uuids(MinecraftServer server) {
        return chasseurs(server).stream().map(Villager::getUUID).collect(Collectors.toCollection(HashSet::new));
    }
}
