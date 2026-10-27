package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.factions.Acces;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.Jauges;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.factions.Traque;
import io.github.yusufbyn.redrising.monde.CelluleRising;
import io.github.yusufbyn.redrising.pnj.TraqueGris;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * G5.6 : la traque se déclenche, se gagne et se perd. Semer les Gris (caché dans une boîte), se cacher dans une cellule
 * (sans être vu, puis sous les yeux d'un chasseur), se percher (étourdi, jamais pris, puis retrait des Gris à 90),
 * être arrêté (emmené au poste, interrogé sans pouvoir mourir, inventaire intact), et les rechargements.
 *
 * <p>Pour les longues attentes (une minute hors de vue, dix minutes de traque, l'interrogatoire), on avance l'horloge
 * du monde : la traque compte le temps de jeu écoulé entre deux passages.</p>
 */
public class TraqueTest implements FabricClientGameTest {
    private static final BlockPos ENTREE_CELLULE = new BlockPos(0, -60, 40);
    /** Coin du poste de la Société : sa sortie est en (23, -60, -42). */
    private static final BlockPos POSTE = new BlockPos(20, -61, -40);
    private static final BlockPos SORTIE_POSTE = new BlockPos(23, -60, -42);

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            // En survie (les chasseurs ne visent pas un joueur en créatif), sans dégâts, sans faim, sans recul
            monde.getServer().runCommand("gamemode survival @a[limit=1]");
            monde.getServer().runCommand("effect give @a[limit=1] minecraft:resistance infinite 4 true");
            monde.getServer().runCommand("effect give @a[limit=1] minecraft:saturation infinite 0 true");
            monde.getServer().runCommand("attribute @a[limit=1] minecraft:knockback_resistance base set 1");
            monde.getServer().runCommand("weather clear");
            monde.getServer().runCommand("time set noon");

            semer(monde, context);
            tuerUnChasseur(monde);
            percher(monde, context);
            cellule(monde, context);
            arrestation(monde, context);

            // Traque en cours au moment de quitter
            monde.getServer().runCommand("tp @a 0.5 -60 -80");
            monde.getServer().runCommand("rr traque start @a[limit=1]");
            monde.getServer().waitFor(server -> TraqueGris.nombreDeChasseurs(OutilsTest.joueur(server)) >= Traque.CHASSEURS_DEPART, 40);
            sauvegarde = monde.getWorldSave();
        }

        // --- Rechargement en pleine traque : elle reprend, avec de nouveaux chasseurs (les anciens ont disparu) ---
        try (TestSingleplayerContext monde = sauvegarde.open()) {
            if (!monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).hasAttached(TraqueGris.TRAQUE))) {
                throw new AssertionError("Traque perdue au rechargement");
            }
            monde.getServer().waitFor(server -> TraqueGris.nombreDeChasseurs(OutilsTest.joueur(server)) >= Traque.CHASSEURS_DEPART, 40);
            int chasseursDansLeMonde = monde.getServer().computeOnServer(server -> chasseurs(server).size());
            int chasseursSuivis = monde.getServer().computeOnServer(server -> TraqueGris.nombreDeChasseurs(OutilsTest.joueur(server)));
            if (chasseursDansLeMonde != chasseursSuivis) {
                throw new AssertionError("Après rechargement : " + chasseursDansLeMonde + " chasseurs dans le monde, " + chasseursSuivis + " suivis");
            }
            context.waitTicks(10);
            verifierBarre(context, "après rechargement", true);
            RedRising.LOGGER.info("[G5.6] Rechargement en pleine traque : reprise, {} nouveaux chasseurs", chasseursSuivis);

            monde.getServer().runCommand("rr traque arreter @a[limit=1]");
            context.waitTicks(10);
            sauvegarde = monde.getWorldSave();
        }

        // --- Rechargement en plein interrogatoire : il reprend, le joueur est toujours tenu au poste ---
        try (TestSingleplayerContext monde = sauvegarde.open()) {
            context.waitTicks(20);
            long restant = monde.getServer().computeOnServer(server -> {
                TraqueGris.Interrogatoire interrogatoire = OutilsTest.joueur(server).getAttached(TraqueGris.INTERROGATOIRE);
                return interrogatoire == null ? -1 : interrogatoire.restant();
            });
            if (restant <= 0) {
                throw new AssertionError("Interrogatoire perdu au rechargement");
            }
            verifierPosition(monde, "interrogatoire après rechargement", SORTIE_POSTE);
            RedRising.LOGGER.info("[G5.6] Rechargement en plein interrogatoire : reprise ({} ticks restants)", restant);
            monde.getServer().runCommand("rr traque stop @a[limit=1]");
        }
        RedRising.LOGGER.info("[G5.6] OK : la traque se déclenche, se gagne (semer, cellule), se perd (arrestation), et le perché ne gagne pas à l'abandon");
    }

    // --- Déclenchement et semer les Gris ---

    private static void semer(TestSingleplayerContext monde, ClientGameTestContext context) {
        jauges(monde, 50, 0, Acces.SEUIL_TRAQUE);
        monde.getServer().waitFor(server -> TraqueGris.nombreDeChasseurs(OutilsTest.joueur(server)) >= Traque.CHASSEURS_DEPART, 40);
        context.waitTicks(5);
        verifierTitre(context, "traque.redrising.titre");
        verifierBarre(context, "début de traque", true);
        RedRising.LOGGER.info("[G5.6] Déclenchement à 100 de Suspicion : titre, barre, {} chasseurs", Traque.CHASSEURS_DEPART);

        // Dans une boîte de pierre fermée, loin : plus aucun chasseur ne voit le joueur
        boite(monde, new BlockPos(60, -60, 60));
        monde.getServer().runCommand("tp @a 61.5 -60 61.5");
        context.waitTicks(60);
        verifierBarreCache(context);
        if (!enTraque(monde)) {
            throw new AssertionError("Traque finie trop tôt (moins d'une minute hors de vue)");
        }
        // Une minute hors de vue
        avancerLHorloge(monde, Traque.DUREE_SEMER);
        attendreFin(monde, "semer les Gris");
        verifierJauges(monde, "Gris semés", new Jauges(50, 0, Traque.SUSPICION_GAGNEE));
        context.waitTicks(5);
        verifierMessage(context, "Gris semés", "traque.redrising.gagnee");
        verifierBarre(context, "après la traque", false);
        if (monde.getServer().computeOnServer(server -> chasseurs(server).size()) != 0) {
            throw new AssertionError("Des chasseurs restent après la traque");
        }
        RedRising.LOGGER.info("[G5.6] Gris semés : Suspicion {}, chasseurs partis", Traque.SUSPICION_GAGNEE);
        monde.getServer().runCommand("tp @a 0.5 -60 0.5");
    }

    private static void tuerUnChasseur(TestSingleplayerContext monde) {
        monde.getServer().runCommand("rr traque start @a[limit=1]");
        monde.getServer().waitFor(server -> TraqueGris.nombreDeChasseurs(OutilsTest.joueur(server)) >= Traque.CHASSEURS_DEPART, 40);
        int[] apres = monde.getServer().computeOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            Villager chasseur = chasseurs(server).getFirst();
            chasseur.hurtServer(server.overworld(), joueur.damageSources().playerAttack(joueur), Float.MAX_VALUE);
            return new int[]{JaugesJoueur.get(joueur).soutien(), Objects.requireNonNull(joueur.getAttached(TraqueGris.TRAQUE)).tues()};
        });
        if (apres[0] != 0 || apres[1] != 1) {
            throw new AssertionError("Chasseur tué : Soutien " + apres[0] + " (attendu 0), tués " + apres[1] + " (attendu 1)");
        }
        RedRising.LOGGER.info("[G5.6] Chasseur tué : pas de Soutien, compté dans la traque");
        monde.getServer().runCommand("rr traque stop @a[limit=1]");
    }

    // --- Se percher ---

    /** Sur un pilier de 3 blocs : étourdi par les flèches, jamais pris ; à 10 minutes, les Gris le voient encore et se retirent. */
    private static void percher(TestSingleplayerContext monde, ClientGameTestContext context) {
        monde.getServer().runOnServer(server -> {
            for (int y = -60; y <= -58; y++) {
                server.overworld().setBlockAndUpdate(new BlockPos(0, y, -20), Blocks.STONE.defaultBlockState());
            }
        });
        monde.getServer().runCommand("tp @a 0.5 -57 -19.5");
        context.waitTicks(5);
        jauges(monde, 50, 0, Acces.SEUIL_TRAQUE);
        monde.getServer().waitFor(server -> {
            MobEffectInstance lenteur = OutilsTest.joueur(server).getEffect(MobEffects.SLOWNESS);
            return lenteur != null && lenteur.getAmplifier() == 4;
        }, 900);
        RedRising.LOGGER.info("[G5.6] Perché : touché et étourdi par une flèche de chasseur");
        context.waitTicks(200);
        if (monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).hasAttached(TraqueGris.INTERROGATOIRE)) || !enTraque(monde)) {
            throw new AssertionError("Perché : le joueur ne devait pas être arrêté");
        }
        verifierPosition(monde, "perché", new BlockPos(0, -57, -20));
        // Dix minutes de traque, toujours vu : les Gris se retirent, ce n'est pas une victoire
        avancerLHorloge(monde, Traque.DUREE_MAX);
        attendreFin(monde, "abandon, perché et vu");
        verifierJauges(monde, "retrait des Gris", new Jauges(50, 0, Traque.SUSPICION_RETRAIT));
        context.waitTicks(5);
        verifierMessage(context, "retrait des Gris", "traque.redrising.retrait");
        RedRising.LOGGER.info("[G5.6] Perché : jamais pris, puis retrait des Gris à {} de Suspicion", Traque.SUSPICION_RETRAIT);
        monde.getServer().runCommand("tp @a 0.5 -60 0.5");
    }

    // --- Cellule ---

    private static void cellule(TestSingleplayerContext monde, ClientGameTestContext context) {
        monde.getServer().runCommand("rr structure cellule " + ENTREE_CELLULE.getX() + " " + ENTREE_CELLULE.getY() + " " + ENTREE_CELLULE.getZ() + " south");
        String devant = "tp @a 0.5 -60 38.5 0 0";

        // Sans être vu : les chasseurs sont loin, le joueur entre, la traque est gagnée
        monde.getServer().runCommand(devant);
        jauges(monde, 50, Acces.SEUIL_SOUTIEN_RISING, 0);
        monde.getServer().runCommand("rr traque start @a[limit=1]");
        monde.getServer().waitFor(server -> TraqueGris.nombreDeChasseurs(OutilsTest.joueur(server)) >= Traque.CHASSEURS_DEPART, 40);
        monde.getServer().runOnServer(server -> chasseurs(server).forEach(chasseur -> chasseur.teleportTo(chasseur.getX() + 100, -60, chasseur.getZ())));
        context.waitTicks(10);
        clicAccroupi(context);
        attendreFin(monde, "cellule sans être vu");
        verifierJauges(monde, "caché dans une cellule", new Jauges(50, Acces.SEUIL_SOUTIEN_RISING, Traque.SUSPICION_GAGNEE));
        context.waitTicks(5);
        verifierMessage(context, "caché dans une cellule", "traque.redrising.cachee");
        RedRising.LOGGER.info("[G5.6] Entré dans une cellule sans être vu : traque gagnée");

        // Sous les yeux d'un chasseur : cellule repérée, la traque continue ; dedans, hors de vue, on finit par semer
        monde.getServer().runCommand(devant);
        monde.getServer().runCommand("rr traque start @a[limit=1]");
        monde.getServer().waitFor(server -> TraqueGris.nombreDeChasseurs(OutilsTest.joueur(server)) >= Traque.CHASSEURS_DEPART, 40);
        monde.getServer().runOnServer(server -> chasseurs(server).getFirst().teleportTo(0.5, -60, 32.5));
        context.waitTicks(5);
        clicAccroupi(context);
        context.waitTicks(10);
        boolean reperee = monde.getServer().computeOnServer(server -> CelluleRising.cellules(server).getFirst().reperee());
        if (!reperee || !enTraque(monde)) {
            throw new AssertionError("Entré sous les yeux d'un chasseur : cellule repérée " + reperee + ", traque en cours " + enTraque(monde));
        }
        RedRising.LOGGER.info("[G5.6] Entré dans une cellule sous les yeux d'un chasseur : cellule repérée, traque en cours");
        avancerLHorloge(monde, Traque.DUREE_SEMER);
        attendreFin(monde, "semer depuis la cellule");
        RedRising.LOGGER.info("[G5.6] Dans la cellule, hors de vue : Gris semés");
        monde.getServer().runCommand("tp @a 0.5 -60 0.5");
    }

    // --- Arrestation ---

    private static void arrestation(TestSingleplayerContext monde, ClientGameTestContext context) {
        monde.getServer().runCommand("rr structure poste " + POSTE.getX() + " " + POSTE.getY() + " " + POSTE.getZ());
        monde.getServer().runCommand("tp @a 0.5 -60 -60.5");
        monde.getServer().runOnServer(server -> OutilsTest.joueur(server).getInventory().add(new ItemStack(Items.DIAMOND, 5)));
        jauges(monde, 50, 0, Acces.SEUIL_TRAQUE);
        // Le joueur ne bouge pas : étourdi, rattrapé, arrêté
        monde.getServer().waitFor(server -> OutilsTest.joueur(server).hasAttached(TraqueGris.INTERROGATOIRE), 1200);
        context.waitTicks(5);
        verifierPosition(monde, "arrêté, emmené au poste", SORTIE_POSTE);
        verifierJauges(monde, "arrestation", new Jauges(35, 0, Acces.SEUIL_TRAQUE));
        verifierTitre(context, "traque.redrising.arrestation");
        RedRising.LOGGER.info("[G5.6] Arrêté : emmené devant le poste, Loyauté -15");

        // Ne peut pas mourir pendant l'interrogatoire (sans la résistance du test)
        monde.getServer().runCommand("effect clear @a[limit=1] minecraft:resistance");
        monde.getServer().runOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            joueur.hurtServer(server.overworld(), joueur.damageSources().generic(), 1000);
        });
        monde.getServer().runCommand("kill @a[limit=1]");
        context.waitTicks(5);
        boolean vivant = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).isAlive());
        if (!vivant) {
            throw new AssertionError("Le joueur est mort pendant l'interrogatoire");
        }
        // Tenu sur place
        monde.getServer().runCommand("tp @a 30.5 -60 -42.5");
        context.waitTicks(15);
        verifierPosition(monde, "tenu pendant l'interrogatoire", SORTIE_POSTE);
        RedRising.LOGGER.info("[G5.6] Interrogatoire : ni dégâts ni mort, tenu sur place");

        avancerLHorloge(monde, Traque.DUREE_INTERROGATOIRE);
        monde.getServer().waitFor(server -> !OutilsTest.joueur(server).hasAttached(TraqueGris.INTERROGATOIRE), 40);
        monde.getServer().runCommand("effect give @a[limit=1] minecraft:resistance infinite 4 true");
        verifierJauges(monde, "relâché", new Jauges(35, 0, Traque.SUSPICION_ARRETE));
        int diamants = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).getInventory().countItem(Items.DIAMOND));
        if (diamants != 5) {
            throw new AssertionError("Inventaire touché par l'arrestation : " + diamants + " diamants");
        }
        context.waitTicks(5);
        verifierMessage(context, "relâché", "traque.redrising.relache");
        RedRising.LOGGER.info("[G5.6] Relâché : Suspicion {}, inventaire intact", Traque.SUSPICION_ARRETE);
    }

    // --- Outils ---

    private static List<Villager> chasseurs(MinecraftServer server) {
        return server.overworld().getEntities(EntityTypes.VILLAGER, new AABB(BlockPos.ZERO).inflate(400), pnj -> pnj.hasAttached(TraqueGris.CHASSEUR));
    }

    private static boolean enTraque(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).hasAttached(TraqueGris.TRAQUE));
    }

    private static void attendreFin(TestSingleplayerContext monde, String cas) {
        try {
            monde.getServer().waitFor(server -> !OutilsTest.joueur(server).hasAttached(TraqueGris.TRAQUE), 40);
        } catch (AssertionError e) {
            throw new AssertionError("Traque toujours en cours (" + cas + ")", e);
        }
    }

    /** Avance l'horloge du monde (le serveur suit le pas du client : /tick sprint n'accélère rien). */
    private static void avancerLHorloge(TestSingleplayerContext monde, long ticks) {
        monde.getServer().runOnServer(server -> {
            ServerLevel niveau = server.overworld();
            ((ServerLevelData) niveau.getLevelData()).setGameTime(niveau.getGameTime() + ticks);
        });
    }

    /** Boîte de pierre fermée de 3×3×4 blocs, vide au centre (x+1, z+1). */
    private static void boite(TestSingleplayerContext monde, BlockPos coin) {
        monde.getServer().runOnServer(server -> {
            for (int dx = 0; dx <= 2; dx++) {
                for (int dy = -1; dy <= 2; dy++) {
                    for (int dz = 0; dz <= 2; dz++) {
                        boolean vide = dx == 1 && dz == 1 && dy >= 0 && dy <= 1;
                        server.overworld().setBlockAndUpdate(coin.offset(dx, dy, dz), vide ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState());
                    }
                }
            }
        });
    }

    /** Accroupi, main vide, un clic droit sur ce qui est devant, puis debout. */
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

    private static void verifierJauges(TestSingleplayerContext monde, String cas, Jauges attendues) {
        Jauges jauges = monde.getServer().computeOnServer(server -> JaugesJoueur.get(OutilsTest.joueur(server)));
        if (!jauges.equals(attendues)) {
            throw new AssertionError(cas + " : jauges attendues " + attendues + ", obtenues " + jauges);
        }
    }

    private static void verifierPosition(TestSingleplayerContext monde, String cas, BlockPos attendue) {
        BlockPos pos = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).blockPosition());
        if (pos.distSqr(attendue) > 2) {
            throw new AssertionError(cas + " : joueur en " + pos.toShortString() + ", attendu en " + attendue.toShortString());
        }
    }

    // --- Lecture de l'écran du client (champs privés, par réflexion : test seulement) ---

    private static Object champ(Object objet, Class<?> classe, String nom) {
        try {
            Field champ = classe.getDeclaredField(nom);
            champ.setAccessible(true);
            return champ.get(objet);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean cle(Component texte, String cle) {
        return texte != null && texte.getContents() instanceof TranslatableContents traduit && traduit.getKey().equals(cle);
    }

    private static void verifierTitre(ClientGameTestContext context, String cle) {
        Component titre = context.computeOnClient(mc -> (Component) champ(mc.gui.hud, Hud.class, "title"));
        if (!cle(titre, cle)) {
            throw new AssertionError("Titre attendu " + cle + ", affiché : " + titre);
        }
    }

    private static void verifierMessage(ClientGameTestContext context, String cas, String cle) {
        Component message = context.computeOnClient(mc -> (Component) champ(mc.gui.hud, Hud.class, "overlayMessageString"));
        if (!cle(message, cle)) {
            throw new AssertionError(cas + " : message attendu " + cle + ", affiché : " + message);
        }
    }

    private static Map<?, ?> barres(ClientGameTestContext context) {
        return context.computeOnClient(mc -> Map.copyOf((Map<?, ?>) champ(mc.gui.hud.getBossOverlay(), BossHealthOverlay.class, "events")));
    }

    private static void verifierBarre(ClientGameTestContext context, String cas, boolean visible) {
        boolean affichee = barres(context).values().stream().anyMatch(barre -> {
            Component nom = ((BossEvent) barre).getName();
            return cle(nom, "traque.redrising.barre.vu") || cle(nom, "traque.redrising.barre.cache");
        });
        if (affichee != visible) {
            throw new AssertionError("Barre de traque (" + cas + ") : attendue " + (visible ? "visible" : "absente"));
        }
    }

    private static void verifierBarreCache(ClientGameTestContext context) {
        boolean cache = barres(context).values().stream().anyMatch(barre -> cle(((BossEvent) barre).getName(), "traque.redrising.barre.cache")
                && ((BossEvent) barre).getColor() == BossEvent.BossBarColor.YELLOW);
        if (!cache) {
            throw new AssertionError("Barre de traque : attendue jaune, « hors de vue »");
        }
    }
}
