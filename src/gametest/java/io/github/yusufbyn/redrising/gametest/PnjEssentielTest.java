package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.data.EtatMonde;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.EffetsActions;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.Jauges;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.factions.Temoin;
import io.github.yusufbyn.redrising.jalons.Bilan;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Horloge;
import io.github.yusufbyn.redrising.jalons.HorlogeMonde;
import io.github.yusufbyn.redrising.jalons.Issue;
import io.github.yusufbyn.redrising.jalons.Jalon;
import io.github.yusufbyn.redrising.jalons.Jalons;
import io.github.yusufbyn.redrising.jalons.Personnages;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import io.github.yusufbyn.redrising.pnj.PnjEssentiels;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static io.github.yusufbyn.redrising.jalons.Echeance.jours;

/**
 * G6.4 : un PNJ de test se relève avant son jalon, meurt après. Aussi : à terre (couché, invulnérable, K.-O. sans
 * Soutien), réveil au bout de 30 secondes avec la moitié de sa vie ; ramené à son foyer depuis la lave et après
 * {@code /kill} ; un jalon fixe rend essentiel, un personnage sans jalon meurt ; la dénonciation passe au suivant ; ni
 * sorcière ni villageois zombie avant le jalon, et après, la conversion compte comme sa mort ; un personnage, un seul
 * PNJ ; inconscience et morts gardées au rechargement.
 *
 * <p>Jalons et personnages de test déclarés par le test, retirés à la fin.</p>
 */
public class PnjEssentielTest implements FabricClientGameTest {
    private static final String JALON = "test_g64";
    private static final String JALON_FIXE = "test_g64_fixe";
    private static final String PERSO = "perso_g64";
    private static final String PERSO_FIXE = "perso_g64_fixe";
    private static final String PERSO_SANS = "perso_g64_sans";
    private static final String PERSO_RISING = "perso_g64_rising";
    private static final String PERSO_FOUDRE = "perso_g64_foudre";
    private static final String PERSO_ZOMBIE = "perso_g64_zombie";
    private static final List<String> PERSONNAGES = List.of(PERSO, PERSO_FIXE, PERSO_SANS, PERSO_RISING, PERSO_FOUDRE, PERSO_ZOMBIE);
    private static final Vec3 FOYER = new Vec3(4.5, -60, 4.5);
    /** Le Gris à qui l'on dénonce, juste devant le joueur. */
    private static final Vec3 DEVANT = new Vec3(0.5, -60, 2.5);

    @Override
    public void runTest(ClientGameTestContext context) {
        Jalons.declarer(Jalon.builder(JALON).epoque(Epoque.RED_RISING).echeance(jours(30), jours(30), jours(30), 0).fixe(Issue.CANON).build());
        Jalons.declarer(Jalon.builder(JALON_FIXE).epoque(Epoque.RED_RISING).echeance(jours(30), jours(30), jours(30), 0).fixe(Issue.CANON).build());
        Personnages.declarer(PERSO, JALON);
        Personnages.declarer(PERSO_FIXE, JALON_FIXE);
        Personnages.declarer(PERSO_SANS, null);
        Personnages.declarer(PERSO_RISING, JALON);
        Personnages.declarer(PERSO_FOUDRE, JALON);
        Personnages.declarer(PERSO_ZOMBIE, JALON);
        try {
            jouer(context);
        } finally {
            Jalons.retirer(JALON);
            Jalons.retirer(JALON_FIXE);
            PERSONNAGES.forEach(Personnages::retirer);
        }
        RedRising.LOGGER.info("[G6.4] OK : un PNJ de test se relève avant son jalon, meurt après");
    }

    private static void jouer(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        UUID a;
        UUID zombieVictime;
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runCommand("difficulty hard");
            monde.getServer().runCommand("time set noon");
            monde.getServer().runCommand("tp @a 0.5 -60 0.5 0 20");
            jauges(monde, 50, 50, 0);

            // --- Mis à terre par le joueur : couché, invulnérable, K.-O. sans Soutien, compteurs intacts ---
            a = pnj(monde, FOYER, Couleur.OR, PERSO);
            Horloge compteurs = monde.getServer().computeOnServer(HorlogeMonde::get);
            Jauges avant = lireJauges(monde);
            frapper(monde, a);
            verifierATerre(monde, a, "coup mortel avant le jalon");
            verifierJauges(monde, "K.-O. sans témoin", avant.plus(EffetsActions.selonTemoins(EffetsActions.ko(Couleur.OR), Temoin.AUCUN), Couleur.OR));
            Jauges apresKo = lireJauges(monde);
            frapper(monde, a);
            verifierJauges(monde, "deuxième coup, à terre", apresKo);
            context.waitTicks(20);
            verifierATerre(monde, a, "une seconde plus tard");
            Horloge apres = monde.getServer().computeOnServer(HorlogeMonde::get);
            if (apres.aide() != compteurs.aide() || apres.tort() != compteurs.tort()) {
                throw new AssertionError("Un K.-O. a nourri les compteurs : " + compteurs + " → " + apres);
            }
            // 30 secondes de jeu plus tard : debout, la moitié de sa vie, sans IA comme avant
            avancer(monde, Personnages.DUREE_INCONSCIENCE);
            monde.getServer().waitFor(server -> !PnjEssentiels.inconscient(villageois(server, a)), 30);
            String reveil = monde.getServer().computeOnServer(server -> {
                Villager pnj = villageois(server, a);
                return pnj.getPose() + " " + pnj.getHealth() + "/" + pnj.getMaxHealth() + " sansIA=" + pnj.isNoAi();
            });
            if (!reveil.equals("STANDING 10.0/20.0 sansIA=true")) {
                throw new AssertionError("Réveil : " + reveil);
            }
            RedRising.LOGGER.info("[G6.4] Avant le jalon : à terre (K.-O. 0 / 0 / +8, deuxième coup sans effet), relevé 30 s plus tard ({})", reveil);

            // --- Hors combat : lave, /kill → ramené à son foyer, à terre ---
            monde.getServer().runOnServer(server -> {
                server.overworld().setBlockAndUpdate(new BlockPos(12, -60, 12), Blocks.LAVA.defaultBlockState());
                villageois(server, a).teleportTo(12.5, -60, 12.5);
            });
            monde.getServer().waitFor(server -> PnjEssentiels.inconscient(villageois(server, a)), 200);
            verifierAuFoyer(monde, a, "lave");
            monde.getServer().runCommand("rr pnj reveiller " + a);
            monde.getServer().runCommand("kill " + a);
            monde.getServer().waitFor(server -> PnjEssentiels.inconscient(villageois(server, a)), 20);
            verifierAuFoyer(monde, a, "/kill");
            monde.getServer().runCommand("rr pnj reveiller " + a);
            RedRising.LOGGER.info("[G6.4] Lave et /kill : ramené à son foyer, à terre");

            // --- Jalon fixe : essentiel avant ; personnage sans jalon : il meurt ---
            UUID fixe = pnj(monde, new Vec3(-4.5, -60, 4.5), Couleur.ROUGE, PERSO_FIXE);
            frapper(monde, fixe);
            verifierATerre(monde, fixe, "personnage d'un jalon fixe");
            UUID sans = pnj(monde, new Vec3(-4.5, -60, -4.5), Couleur.ROUGE, PERSO_SANS);
            frapper(monde, sans);
            monde.getServer().waitFor(server -> server.overworld().getEntity(sans) == null, 20);
            verifierMort(monde, PERSO_SANS, true);
            monde.getServer().runCommand("rr pnj reveiller " + fixe);
            RedRising.LOGGER.info("[G6.4] Jalon fixe : essentiel avant lui ; sans jalon : mort, enregistrée");

            // --- Dénonciation à mains vides : l'essentiel est ignoré, on prend le suivant ; plus personne : rien ---
            UUID gris = pnjDevant(monde, Couleur.GRIS);
            UUID rebelleEssentiel = pnj(monde, new Vec3(6.5, -60, 0.5), Couleur.ROUGE, PERSO_RISING);
            UUID rebelle = pnj(monde, new Vec3(-9.5, -60, 0.5), Couleur.ROUGE, null);
            monde.getServer().runOnServer(server -> {
                villageois(server, rebelleEssentiel).setAttached(ActionsFaction.CAMP, Camp.RISING);
                villageois(server, rebelle).setAttached(ActionsFaction.CAMP, Camp.RISING);
            });
            jauges(monde, 50, 50, 20);
            clicAccroupi(context);
            monde.getServer().waitFor(server -> server.overworld().getEntity(rebelle) == null, 20);
            if (monde.getServer().computeOnServer(server -> server.overworld().getEntity(rebelleEssentiel) == null)) {
                throw new AssertionError("Dénonciation : le personnage essentiel a été arrêté");
            }
            verifierJauges(monde, "dénonciation du suivant", new Jauges(50, 50, 20).plus(EffetsActions.denoncer(), Couleur.OR));
            Jauges apresDenonciation = lireJauges(monde);
            clicAccroupi(context);
            context.waitTicks(20);
            verifierJauges(monde, "plus personne à dénoncer que l'essentiel", apresDenonciation);
            retirer(monde, gris);
            RedRising.LOGGER.info("[G6.4] Dénonciation : l'essentiel ignoré, le suivant arrêté ; ensuite, rien");

            // --- Foudre : pas de sorcière ---
            UUID foudre = pnj(monde, new Vec3(20.5, -60, 0.5), Couleur.ROUGE, PERSO_FOUDRE);
            monde.getServer().runCommand("summon minecraft:lightning_bolt 20.5 -60 0.5");
            context.waitTicks(20);
            boolean sorciere = monde.getServer().computeOnServer(server -> !server.overworld().getEntities(EntityTypes.WITCH,
                    new AABB(new BlockPos(20, -60, 0)).inflate(6), sorcier -> true).isEmpty());
            if (sorciere || monde.getServer().computeOnServer(server -> !(server.overworld().getEntity(foudre) instanceof Villager))) {
                throw new AssertionError("Foudre : le personnage essentiel a été changé en sorcière");
            }
            RedRising.LOGGER.info("[G6.4] Foudre : le personnage essentiel reste un villageois");

            // --- Zombie avant le jalon : à terre, pas de villageois zombie ---
            zombieVictime = pnj(monde, new Vec3(30.5, -60, 0.5), Couleur.ROUGE, PERSO_ZOMBIE);
            tueParUnZombie(monde, zombieVictime);
            verifierATerre(monde, zombieVictime, "tué par un zombie avant le jalon");
            if (villageoisZombies(monde) != 0) {
                throw new AssertionError("Zombie avant le jalon : un villageois zombie est apparu");
            }
            monde.getServer().runCommand("rr pnj reveiller " + zombieVictime);
            RedRising.LOGGER.info("[G6.4] Zombie avant le jalon : à terre, pas de conversion");

            // --- Un personnage, un PNJ : redonné à un autre, l'ancien le perd ---
            UUID nouveau = pnj(monde, new Vec3(-12.5, -60, 4.5), Couleur.ROUGE, null);
            monde.getServer().runCommand("rr pnj personnage " + nouveau + " " + PERSO_FIXE);
            String porteurs = monde.getServer().computeOnServer(server -> villageois(server, fixe).getAttached(PnjEssentiels.PERSONNAGE)
                    + " / " + villageois(server, nouveau).getAttached(PnjEssentiels.PERSONNAGE));
            if (!porteurs.equals("null / " + PERSO_FIXE)) {
                throw new AssertionError("Personnage redonné : ancien / nouveau = " + porteurs);
            }
            // Un personnage mort ne se redonne pas
            monde.getServer().runCommand("rr pnj personnage " + nouveau + " " + PERSO_SANS);
            if (!PERSO_FIXE.equals(monde.getServer().computeOnServer(server -> villageois(server, nouveau).getAttached(PnjEssentiels.PERSONNAGE)))) {
                throw new AssertionError("Un personnage mort a été redonné");
            }
            RedRising.LOGGER.info("[G6.4] Un personnage, un PNJ : redonné, l'ancien le perd ; mort, refusé");

            // --- Rechargement en pleine inconscience ---
            monde.getServer().runCommand("rr pnj ko " + a);
            sauvegarde = monde.getWorldSave();
        }

        try (TestSingleplayerContext monde = sauvegarde.open()) {
            monde.getServer().waitFor(server -> !server.getPlayerList().getPlayers().isEmpty(), 100);
            context.waitTicks(20);
            verifierATerre(monde, a, "après rechargement");
            avancer(monde, Personnages.DUREE_INCONSCIENCE);
            monde.getServer().waitFor(server -> !PnjEssentiels.inconscient(villageois(server, a)), 30);
            RedRising.LOGGER.info("[G6.4] Rechargement : toujours à terre, puis relevé à l'heure");

            // --- Après le jalon : il meurt, et sa mort est enregistrée pour le bilan ---
            monde.getServer().runCommand("rr jalon declencher " + JALON);
            monde.getServer().waitFor(server -> EtatMondeServeur.get(server).jalons().containsKey(JALON), 20);
            frapper(monde, a);
            monde.getServer().waitFor(server -> server.overworld().getEntity(a) == null, 20);
            verifierMort(monde, PERSO, true);

            // Zombie après le jalon : villageois zombie, mort enregistrée (pas par un joueur), le converti n'incarne plus rien
            tueParUnZombie(monde, zombieVictime);
            monde.getServer().waitFor(server -> server.overworld().getEntity(zombieVictime) == null, 20);
            verifierMort(monde, PERSO_ZOMBIE, false);
            String converti = monde.getServer().computeOnServer(server -> server.overworld().getEntities(EntityTypes.ZOMBIE_VILLAGER,
                            new AABB(new BlockPos(30, -60, 0)).inflate(6), zombie -> true).stream()
                    .map(zombie -> zombie.getAttached(PnjEssentiels.PERSONNAGE) + " " + zombie.getAttached(PnjEssentiels.FOYER))
                    .findFirst().orElse("aucun"));
            if (!converti.equals("null null")) {
                throw new AssertionError("Zombie après le jalon : villageois zombie " + converti + " (attendu : présent, sans personnage ni foyer)");
            }
            RedRising.LOGGER.info("[G6.4] Après le jalon : tué (par le joueur), et converti par un zombie ; morts enregistrées");
            sauvegarde = monde.getWorldSave();
        }

        try (TestSingleplayerContext monde = sauvegarde.open()) {
            monde.getServer().waitFor(server -> !server.getPlayerList().getPlayers().isEmpty(), 100);
            verifierMort(monde, PERSO, true);
            verifierMort(monde, PERSO_ZOMBIE, false);
        }
        RedRising.LOGGER.info("[G6.4] Morts gardées au rechargement");
    }

    // --- Outils ---

    /** Un PNJ sans IA de cette Couleur ; avec un personnage (et son foyer ici), si {@code personnage} n'est pas nul. */
    private static UUID pnj(TestSingleplayerContext monde, Vec3 position, Couleur couleur, String personnage) {
        UUID id = monde.getServer().computeOnServer(server -> {
            Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), position, 180, couleur));
            pnj.setNoAi(true);
            return pnj.getUUID();
        });
        if (personnage != null) {
            monde.getServer().runCommand("rr pnj personnage " + id + " " + personnage);
        }
        return id;
    }

    private static UUID pnjDevant(TestSingleplayerContext monde, Couleur couleur) {
        UUID id = pnj(monde, DEVANT, couleur, null);
        monde.getConnection().waitForClientboundEntityUpdates(EntityTypes.VILLAGER);
        return id;
    }

    private static Villager villageois(MinecraftServer server, UUID id) {
        return (Villager) Objects.requireNonNull(server.overworld().getEntity(id), "PNJ absent : " + id);
    }

    private static void retirer(TestSingleplayerContext monde, UUID id) {
        monde.getServer().runOnServer(server -> villageois(server, id).discard());
    }

    private static void frapper(TestSingleplayerContext monde, UUID id) {
        monde.getServer().runOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            villageois(server, id).hurtServer(server.overworld(), joueur.damageSources().playerAttack(joueur), Float.MAX_VALUE);
        });
    }

    private static void tueParUnZombie(TestSingleplayerContext monde, UUID id) {
        monde.getServer().runOnServer(server -> {
            ServerLevel niveau = server.overworld();
            Villager victime = villageois(server, id);
            Zombie zombie = Objects.requireNonNull(EntityTypes.ZOMBIE.create(niveau, EntitySpawnReason.COMMAND));
            zombie.snapTo(victime.getX() + 1, victime.getY(), victime.getZ(), 0, 0);
            zombie.setNoAi(true);
            niveau.addFreshEntity(zombie);
            victime.hurtServer(niveau, zombie.damageSources().mobAttack(zombie), Float.MAX_VALUE);
            zombie.discard();
        });
    }

    private static long villageoisZombies(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(server -> (long) server.overworld().getEntities(EntityTypes.ZOMBIE_VILLAGER,
                new AABB(BlockPos.ZERO).inflate(64), zombie -> true).size());
    }

    private static void avancer(TestSingleplayerContext monde, long ticks) {
        monde.getServer().runOnServer(server -> {
            ServerLevel niveau = server.overworld();
            ((ServerLevelData) niveau.getLevelData()).setGameTime(niveau.getGameTime() + ticks);
        });
    }

    private static void verifierATerre(TestSingleplayerContext monde, UUID id, String cas) {
        String etat = monde.getServer().computeOnServer(server -> {
            Villager pnj = villageois(server, id);
            return pnj.isAlive() + " " + PnjEssentiels.inconscient(pnj) + " " + pnj.getPose();
        });
        if (!etat.equals("true true SLEEPING")) {
            throw new AssertionError(cas + " : vivant, à terre, pose = " + etat + " (attendu : true true SLEEPING)");
        }
    }

    private static void verifierAuFoyer(TestSingleplayerContext monde, UUID id, String cas) {
        double distance = monde.getServer().computeOnServer(server -> villageois(server, id).position().distanceTo(FOYER));
        if (distance > 1) {
            throw new AssertionError(cas + " : pas ramené à son foyer (à " + distance + " blocs)");
        }
        verifierATerre(monde, id, cas);
    }

    private static void verifierMort(TestSingleplayerContext monde, String personnage, boolean parUnJoueur) {
        EtatMonde etat = monde.getServer().computeOnServer(EtatMondeServeur::get);
        Bilan bilan = Bilan.entre(Horloge.ZERO, etat.horloge(), etat.jalons(), etat.zones(), etat.morts(), etat.enFuite().keySet());
        if (!bilan.tue(personnage) || bilan.tueParUnJoueur(personnage) != parUnJoueur) {
            throw new AssertionError("Mort de " + personnage + " : " + etat.morts().get(personnage) + " (tué par un joueur attendu : " + parUnJoueur + ")");
        }
    }

    private static void clicAccroupi(ClientGameTestContext context) {
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(2);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(2);
        context.getInput().releaseKey(options -> options.keyShift);
    }

    private static void jauges(TestSingleplayerContext monde, int loyaute, int soutien, int suspicion) {
        monde.getServer().runOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            JaugesJoueur.set(joueur, Jauge.LOYAUTE, loyaute);
            JaugesJoueur.set(joueur, Jauge.SOUTIEN, soutien);
            JaugesJoueur.set(joueur, Jauge.SUSPICION, suspicion);
        });
    }

    private static Jauges lireJauges(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(server -> JaugesJoueur.get(OutilsTest.joueur(server)));
    }

    private static void verifierJauges(TestSingleplayerContext monde, String cas, Jauges attendues) {
        try {
            monde.getServer().waitFor(server -> JaugesJoueur.get(OutilsTest.joueur(server)).equals(attendues), 20);
        } catch (AssertionError e) {
            throw new AssertionError(cas + " : jauges attendues " + attendues + ", obtenues " + lireJauges(monde), e);
        }
    }
}
