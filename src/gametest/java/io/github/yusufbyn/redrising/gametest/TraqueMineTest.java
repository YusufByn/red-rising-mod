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
 *     proche hors de vue de son niveau, ou, si aucun n'est actif, par une place au sol de la mine.</li>
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
     * encore marché) : hors de vue ; à un point d'entrée à 24-64 blocs sur son niveau ou un voisin, sinon le repli (un point de
     * son niveau ou d'un voisin à n'importe quelle distance, ou une place au sol active de son niveau).
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
                double distance = Math.sqrt(chasseur.blockPosition().distSqr(joueur.blockPosition()));
                String ou = chasseur.blockPosition().toShortString() + " (niveau " + (plan.niveauA(chasseur.blockPosition()) + 1) + ", " + Math.round(distance) + " blocs)";
                if (Vue.voit(chasseur, joueur)) {
                    throw new AssertionError("Chasseur apparu sous les yeux du joueur : " + ou);
                }
                boolean aUnPoint = entree.pos().distSqr(chasseur.blockPosition()) <= 2;
                boolean dansLaPlage = aUnPoint && plage.contains(entree.pos());
                // Sinon, le repli : un point de son niveau ou d'un voisin, ou un tunnel sombre de son niveau si aucun point n'est actif
                boolean repli = aUnPoint && Math.abs(entree.niveau() - niveau) <= 1;
                boolean parUnTunnel = auSol(plan, chasseur.blockPosition(), niveau) && distance >= Traque.APPARITION_MINE_MIN;
                if (!dansLaPlage && !repli && !parUnTunnel) {
                    throw new AssertionError("Chasseur mal placé (" + plage.size() + " points à 24-64 blocs) : " + ou);
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
            // Leurs chunks restent actifs : à 5 chunks de distance de vue, ils seraient déchargés (et remplacés : ce n'est pas ce qu'on teste)
            for (Villager chasseur : chasseurs(server)) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        server.overworld().setChunkForced((chasseur.getBlockX() >> 4) + dx, (chasseur.getBlockZ() >> 4) + dz, true);
                    }
                }
            }
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

    /**
     * Une galerie sombre sans point d'entrée à 24-64 blocs. Les chasseurs y arrivent par le point d'entrée le plus proche s'il
     * est actif, sinon (la distance de vue du test est petite) par une place au sol active de son niveau. Puis ce point forcé
     * actif : ils y apparaissent.
     */
    private static void repli(TestSingleplayerContext monde, ClientGameTestContext context, PlanDeMine plan) {
        BlockPos[] isolee = new BlockPos[1];
        int[] niveau = new int[1];
        monde.getServer().runOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            BlockPos ici = joueur.blockPosition();
            for (int n = plan.sols().size() - 1; n >= 0 && isolee[0] == null; n--) {
                int n2 = n;
                isolee[0] = plan.galeriesSombres(n, 0).stream().filter(p -> plan.entreesPour(p, n2, Traque.APPARITION_MINE_MIN, Traque.APPARITION_MINE_MAX).isEmpty())
                        .min(Comparator.comparingDouble(p -> Math.hypot(p.getX() - ici.getX(), p.getZ() - ici.getZ()))).orElse(null);
                niveau[0] = n;
            }
            Objects.requireNonNull(isolee[0], "aucune galerie sombre isolée");
            joueur.teleportTo(isolee[0].getX() + 0.5, isolee[0].getY(), isolee[0].getZ() + 0.5);
        });
        // Le temps que les chunks de là-bas soient actifs
        context.waitTicks(200);
        monde.getServer().runCommand("rr traque start @a[limit=1]");
        String libre = attendreRepli(monde, plan, niveau[0], false);
        RedRising.LOGGER.info("[G7.2.4] Repli depuis {} : {}", isolee[0].toShortString(), libre);
        monde.getServer().runCommand("rr traque stop @a[limit=1]");

        BlockPos proche = plan.entreesDeRepli(isolee[0], niveau[0]).getFirst().pos();
        monde.getServer().runOnServer(server -> server.overworld().setChunkForced(proche.getX() >> 4, proche.getZ() >> 4, true));
        monde.getServer().waitFor(server -> server.overworld().isPositionEntityTicking(proche), 200);
        monde.getServer().runCommand("rr traque start @a[limit=1]");
        String force = attendreRepli(monde, plan, niveau[0], true);
        RedRising.LOGGER.info("[G7.2.4] Repli depuis {}, point d'entrée le plus proche actif : {}", isolee[0].toShortString(), force);
        monde.getServer().runCommand("rr traque stop @a[limit=1]");
        monde.getServer().runOnServer(server -> server.overworld().setChunkForced(proche.getX() >> 4, proche.getZ() >> 4, false));
    }

    /** Attend les chasseurs : hors de vue, sur le niveau du joueur, à un point d'entrée ou sur un tunnel sombre. */
    private static String attendreRepli(TestSingleplayerContext monde, PlanDeMine plan, int niveau, boolean aUnPoint) {
        String[] rapport = {null};
        monde.getServer().waitFor(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            if (TraqueGris.nombreDeChasseurs(joueur) < Traque.CHASSEURS_DEPART) {
                return false;
            }
            List<String> lignes = new ArrayList<>();
            for (Villager chasseur : chasseurs(server)) {
                String ou = chasseur.blockPosition().toShortString() + " (" + Math.round(Math.sqrt(chasseur.blockPosition().distSqr(joueur.blockPosition()))) + " blocs)";
                if (Vue.voit(chasseur, joueur) || plan.niveauA(chasseur.blockPosition()) != niveau) {
                    throw new AssertionError("Repli : chasseur mal placé en " + ou);
                }
                boolean surUnPoint = plan.entreesDesGris().stream().anyMatch(e -> e.pos().distSqr(chasseur.blockPosition()) <= 2);
                boolean surUnTunnel = auSol(plan, chasseur.blockPosition(), niveau);
                if (aUnPoint ? !surUnPoint : !surUnPoint && !surUnTunnel) {
                    throw new AssertionError("Repli : chasseur ni à un point d'entrée ni au sol de la mine, en " + ou);
                }
                lignes.add(ou);
            }
            rapport[0] = "chasseurs en " + String.join(" ; ", lignes);
            return true;
        }, ATTENTE_CHASSEURS);
        return rapport[0];
    }

    /** Au sol de la mine, sur ce niveau (à un bloc près : il vient d'apparaître et a pu faire un pas). */
    private static boolean auSol(PlanDeMine plan, BlockPos pos, int niveau) {
        return plan.niveauA(pos) == niveau && plan.interieur(pos.getX(), pos.getY(), pos.getZ());
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
        // Le message dit la direction dans la langue du jeu (bug « vers south »)
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!net.minecraft.locale.Language.getInstance().has("commande.redrising.direction." + direction.getSerializedName())) {
                throw new AssertionError("Direction non traduite : " + direction);
            }
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
