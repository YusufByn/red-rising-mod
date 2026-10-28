package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.couleur.Naissance;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.EffetsActions;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.Jauges;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.factions.Temoin;
import io.github.yusufbyn.redrising.factions.Variation;
import io.github.yusufbyn.redrising.jalons.Echeance;
import io.github.yusufbyn.redrising.jalons.EffetsMonde;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Horloge;
import io.github.yusufbyn.redrising.jalons.HorlogeMonde;
import io.github.yusufbyn.redrising.jalons.Issue;
import io.github.yusufbyn.redrising.jalons.Jalon;
import io.github.yusufbyn.redrising.jalons.Jalons;
import io.github.yusufbyn.redrising.jalons.Levier;
import io.github.yusufbyn.redrising.monde.SceneNaissance;
import io.github.yusufbyn.redrising.pnj.PatrouillePnj;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static io.github.yusufbyn.redrising.jalons.Echeance.jours;

/**
 * G6.3 : un jalon de test change les patrouilles. Aussi : le silence des Rouges (actions contre la Société seulement),
 * le versement de jauges décidé une fois par joueur (un Or devenu Rouge ensuite ne reçoit rien ; un joueur dans la scène
 * de naissance au passage reçoit le sien une fois né Rouge), le message du jalon, la fin de l'alerte, le rejeu sans
 * doublon, et rien dans les compteurs d'aide et de tort.
 *
 * <p>Jalons de test déclarés par le test, retirés à la fin, déclenchés par {@code /rr jalon declencher}.</p>
 */
public class EffetsJalonTest implements FabricClientGameTest {
    private static final String ALERTE = "test_g63";
    private static final String NAISSANCE = "test_g63_naissance";
    /** Le Gris, à 15 blocs du joueur : au-delà de la détection normale (12), en deçà de celle de l'alerte (18). */
    private static final BlockPos POSTE_DU_GRIS = new BlockPos(0, -60, 15);
    private static final String ICI = "tp @a[limit=1] 0.5 -60 0.5 0 0";

    @Override
    public void runTest(ClientGameTestContext context) {
        Jalons.declarer(Jalon.builder(ALERTE).epoque(Epoque.RED_RISING).echeance(jours(30), jours(30), jours(30), 0).fixe(Issue.CANON)
                .effets(Issue.CANON, effets -> effets
                        .levier(Levier.ALERTE_PATROUILLES, 1, jours(1))
                        .levier(Levier.SILENCE_ROUGE, 1)
                        .jauges(Couleur.ROUGE, new Variation(0, 10, 0)))
                .build());
        Jalons.declarer(Jalon.builder(NAISSANCE).epoque(Epoque.RED_RISING).echeance(jours(30), jours(30), jours(30), 0).fixe(Issue.CANON)
                .effets(Issue.CANON, effets -> effets.jauges(Couleur.ROUGE, new Variation(0, 10, 0)))
                .build());
        try {
            jouer(context);
        } finally {
            Jalons.retirer(ALERTE);
            Jalons.retirer(NAISSANCE);
        }
        RedRising.LOGGER.info("[G6.3] OK : un jalon de test change les patrouilles");
    }

    private static void jouer(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        Horloge compteurs;
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runCommand("time set noon");
            compteurs = monde.getServer().computeOnServer(HorlogeMonde::get);

            // --- Avant le jalon : à 35 de Suspicion et 15 blocs, le Gris ne contrôle pas ---
            jauges(monde, 50, 0, 35);
            monde.getServer().runCommand(ICI);
            UUID gris = grisDePatrouille(monde);
            context.waitTicks(60);
            if (controle(monde, gris)) {
                throw new AssertionError("Avant le jalon : contrôlé à 35 de Suspicion et 15 blocs");
            }

            // --- Le jalon passe : titre, alerte, silence ; le versement aux Rouges est décidé pour le joueur Or : rien ---
            monde.getServer().runCommand("rr jalon declencher " + ALERTE);
            monde.getServer().waitFor(server -> EffetsMonde.niveau(server, Levier.ALERTE_PATROUILLES) == 1, 20);
            context.waitTicks(3);
            verifierTitre(context, "jalon.redrising." + ALERTE + ".canon.titre");
            monde.getServer().waitFor(server -> PatrouillePnj.suspectControle(villageois(server, gris)) != null, 60);
            RedRising.LOGGER.info("[G6.3] Alerte des patrouilles : contrôlé à 35 de Suspicion et 15 blocs");
            retirer(monde, gris);
            verifierRecus(monde, "joueur Or au passage", Set.of(ALERTE + "/canon/2", ALERTE + "/canon/nouvelle"));
            jauges(monde, 50, 0, 0);

            // --- Silence des Rouges : seulement les actions contre la Société ---
            monde.getServer().runCommand(ICI);
            UUID temoin = pnj(monde, new Vec3(0.5, -60, 6.5), Couleur.ROUGE);
            attendre(monde, "vol de la Société devant un Rouge", sansTemoinNiLoyaute(EffetsActions.voler(Camp.SOCIETE)), () -> voler(monde));
            attendre(monde, "meurtre de l'élite devant un Rouge", sansTemoinNiLoyaute(EffetsActions.tuer(Couleur.OR)),
                    () -> tuer(monde, Couleur.OR));
            // Les Rouges ne couvrent pas celui qui s'en prend à eux
            attendre(monde, "un Rouge tué devant un autre Rouge", EffetsActions.tuer(Couleur.ROUGE), () -> tuer(monde, Couleur.ROUGE));
            retirer(monde, temoin);
            RedRising.LOGGER.info("[G6.3] Silence des Rouges : vol de la Société et meurtre d'un Or sans témoin ; un Rouge tué, vu");

            // --- Devenu Rouge après le passage : le versement est déjà décidé, rien (Loyauté sous le plafond du Rouge) ---
            jauges(monde, 30, 0, 0);
            Jauges avant = lireJauges(monde);
            monde.getServer().runOnServer(server -> Naissance.donnerCouleur(OutilsTest.joueur(server), Couleur.ROUGE));
            context.waitTicks(120);
            verifierJauges(monde, "Or devenu Rouge après le jalon", avant);

            // --- Un jour de jeu plus tard, l'alerte est finie (le silence est permanent) ---
            monde.getServer().runOnServer(server -> HorlogeMonde.modifier(server, horloge -> horloge.avancer(Echeance.JOUR, 1)));
            monde.getServer().waitFor(server -> !EtatMondeServeur.get(server).effets().containsKey(ALERTE + "/canon/0"), 40);
            if (!monde.getServer().computeOnServer(server -> EffetsMonde.silencieuse(server, Couleur.ROUGE))) {
                throw new AssertionError("Le silence des Rouges (permanent) a pris fin avec l'alerte");
            }
            jauges(monde, 50, 0, 35);
            monde.getServer().runOnServer(server -> OutilsTest.joueur(server).removeAttached(PatrouillePnj.SUIVI_CONTROLE));
            monde.getServer().runCommand(ICI);
            UUID gris2 = grisDePatrouille(monde);
            context.waitTicks(60);
            if (controle(monde, gris2)) {
                throw new AssertionError("Après l'alerte : contrôlé à 35 de Suspicion et 15 blocs");
            }
            retirer(monde, gris2);
            RedRising.LOGGER.info("[G6.3] Un jour plus tard : alerte finie, plus de contrôle à 15 blocs ; silence toujours là");

            // --- Dans la scène de naissance au passage : le versement attend la Couleur, puis le joueur naît Rouge ---
            jauges(monde, 30, 20, 0);
            monde.getServer().runOnServer(server -> {
                ServerPlayer joueur = OutilsTest.joueur(server);
                CouleurJoueur.retirer(joueur);
                SceneNaissance.entrer(joueur);
            });
            context.waitTicks(20);
            monde.getServer().runCommand("rr jalon declencher " + NAISSANCE);
            context.waitTicks(120);
            verifierJauges(monde, "dans la scène, sans Couleur", new Jauges(30, 20, 0));
            monde.getServer().runOnServer(server -> Naissance.donnerCouleur(OutilsTest.joueur(server), Couleur.ROUGE));
            monde.getServer().waitFor(server -> JaugesJoueur.get(OutilsTest.joueur(server)).soutien() == 30, 140);
            verifierRecus(monde, "né Rouge après le passage", Set.of(ALERTE + "/canon/2", ALERTE + "/canon/nouvelle",
                    NAISSANCE + "/canon/0", NAISSANCE + "/canon/nouvelle"));
            RedRising.LOGGER.info("[G6.3] Dans la scène au passage : né Rouge, +10 de Soutien et la nouvelle reçus");

            // Les jalons ne nourrissent pas les compteurs d'aide et de tort (seules les actions jouées plus haut le font)
            verifierCompteurs(compteurs, monde.getServer().computeOnServer(HorlogeMonde::get));

            // Comme un crash avant l'application des effets : le drapeau repasse à faux
            monde.getServer().runOnServer(server -> EtatMondeServeur.modifier(server, etat -> etat.avecEffetsEnAttente(NAISSANCE, true)));
            sauvegarde = monde.getWorldSave();
        }

        // --- Rejeu au chargement : rien en double, pas de titre ---
        try (TestSingleplayerContext monde = sauvegarde.open()) {
            monde.getServer().waitFor(server -> !server.getPlayerList().getPlayers().isEmpty(), 100);
            context.waitTicks(120);
            verifierJauges(monde, "rejeu des effets", new Jauges(30, 30, 0));
            if (monde.getServer().computeOnServer(server -> EtatMondeServeur.get(server).effetsEnAttente().contains(NAISSANCE))) {
                throw new AssertionError("Rejeu : les effets n'ont pas été marqués appliqués");
            }
            long versements = monde.getServer().computeOnServer(server -> (long) EtatMondeServeur.get(server).versements().size());
            if (versements != 4) {
                throw new AssertionError("Rejeu : " + versements + " versements (attendu 4)");
            }
        }
        RedRising.LOGGER.info("[G6.3] Rejeu au chargement : ni versement en double, ni effet en double");
    }

    // --- Outils ---

    /** Un Gris de patrouille immobile (sans IA) à 15 blocs du joueur. */
    private static UUID grisDePatrouille(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(server -> {
            Villager gris = Objects.requireNonNull(PatrouillePnj.creer(server.overworld(), List.of(POSTE_DU_GRIS, POSTE_DU_GRIS.east())));
            gris.setNoAi(true);
            return gris.getUUID();
        });
    }

    private static UUID pnj(TestSingleplayerContext monde, Vec3 position, Couleur couleur) {
        return monde.getServer().computeOnServer(server -> {
            Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), position, 180, couleur));
            pnj.setNoAi(true);
            return pnj.getUUID();
        });
    }

    private static Villager villageois(MinecraftServer server, UUID id) {
        return (Villager) Objects.requireNonNull(server.overworld().getEntity(id));
    }

    private static boolean controle(TestSingleplayerContext monde, UUID gris) {
        return monde.getServer().computeOnServer(server -> PatrouillePnj.suspectControle(villageois(server, gris)) != null);
    }

    private static void retirer(TestSingleplayerContext monde, UUID id) {
        monde.getServer().runOnServer(server -> villageois(server, id).discard());
    }

    /** Vol dans un coffre neuf de la Société, juste devant le joueur. */
    private static void voler(TestSingleplayerContext monde) {
        BlockPos pos = new BlockPos(0, -60, -2);
        monde.getServer().runOnServer(server -> {
            server.overworld().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            ChestBlockEntity coffre = (ChestBlockEntity) Objects.requireNonNull(server.overworld().getBlockEntity(pos));
            coffre.setItem(0, new ItemStack(Items.DIAMOND, 5));
            coffre.setAttached(ActionsFaction.CAMP, Camp.SOCIETE);
            ServerPlayer joueur = OutilsTest.joueur(server);
            joueur.openMenu(coffre);
            joueur.getInventory().add(joueur.containerMenu.getSlot(0).remove(2));
            joueur.closeContainer();
        });
    }

    private static void tuer(TestSingleplayerContext monde, Couleur victime) {
        monde.getServer().runOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), new Vec3(2.5, -60, 2.5), 0, victime));
            pnj.hurtServer(server.overworld(), joueur.damageSources().playerAttack(joueur), Float.MAX_VALUE);
        });
    }

    private static Variation sansTemoinNiLoyaute(Variation variation) {
        return EffetsActions.selonTemoins(variation, Temoin.AUCUN);
    }

    /** Joue l'action, puis vérifie que les jauges ont bougé exactement de la variation attendue. */
    private static void attendre(TestSingleplayerContext monde, String action, Variation variation, Runnable jouer) {
        Jauges avant = lireJauges(monde);
        jouer.run();
        Jauges attendues = monde.getServer().computeOnServer(server -> avant.plus(variation, CouleurJoueur.get(OutilsTest.joueur(server))));
        try {
            monde.getServer().waitFor(server -> JaugesJoueur.get(OutilsTest.joueur(server)).equals(attendues), 40);
        } catch (AssertionError e) {
            throw new AssertionError(action + " : jauges attendues " + attendues + ", obtenues " + lireJauges(monde) + " (avant : " + avant + ")", e);
        }
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
        Jauges jauges = lireJauges(monde);
        if (!jauges.equals(attendues)) {
            throw new AssertionError(cas + " : jauges attendues " + attendues + ", obtenues " + jauges);
        }
    }

    private static void verifierRecus(TestSingleplayerContext monde, String cas, Set<String> attendus) {
        Set<String> recus = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).getAttachedOrElse(EffetsMonde.RECUS, Set.of()));
        if (!recus.equals(attendus)) {
            throw new AssertionError(cas + " : versements reçus " + recus + ", attendu " + attendus);
        }
    }

    /**
     * Seules les actions jouées comptent : vol de la Société (+2 d'aide), meurtre d'un Or (+5 d'aide), meurtre d'un Rouge
     * (5 de tort). Les +10 versés au joueur né Rouge ne comptent pas.
     */
    private static void verifierCompteurs(Horloge avant, Horloge apres) {
        long aide = apres.aide() - avant.aide();
        long tort = apres.tort() - avant.tort();
        if (aide != 7 || tort != 5) {
            throw new AssertionError("Compteurs : aide +" + aide + ", tort +" + tort + " (attendu +7 et +5, les jalons ne les nourrissent pas)");
        }
    }

    private static void verifierTitre(ClientGameTestContext context, String cle) {
        Component titre = context.computeOnClient(mc -> {
            try {
                Field champ = Hud.class.getDeclaredField("title");
                champ.setAccessible(true);
                return (Component) champ.get(mc.gui.hud);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        if (titre == null || !(titre.getContents() instanceof TranslatableContents texte) || !texte.getKey().equals(cle)) {
            throw new AssertionError("Titre attendu " + cle + ", affiché : " + titre);
        }
    }
}
