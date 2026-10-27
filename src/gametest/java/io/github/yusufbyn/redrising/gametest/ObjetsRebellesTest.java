package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.ChausseTrappes;
import io.github.yusufbyn.redrising.factions.Contrebande;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.factions.ObjetsRebelles;
import io.github.yusufbyn.redrising.factions.RegistreChausseTrappes;
import io.github.yusufbyn.redrising.factions.Temoin;
import io.github.yusufbyn.redrising.factions.Traque;
import io.github.yusufbyn.redrising.monde.Vue;
import io.github.yusufbyn.redrising.pnj.PatrouillePnj;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * G5.8 : objets rebelles. Le nuage du fumigène coupe la vue des PNJ (témoins, patrouille, chasseurs) sans rien cacher
 * au contact (l'arrestation tient) ; un vrai lancer crée le nuage. La chausse-trappe : trois par joueur au plus, un zombie
 * passe dessus sans la déclencher, un Gris en patrouille s'y prend (ralenti, il fouille), le poseur aussi ; cassée,
 * elle revient à son poseur seulement ; le registre survit au rechargement. La contrebande en main devant un Gris :
 * +5 de Suspicion, une fois par minute, rien rangée ni dans la fumée.
 */
public class ObjetsRebellesTest implements FabricClientGameTest {
    private static final int Y = -60;

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave sauvegarde;
        BlockPos trappeDuZombie = new BlockPos(3, Y, 61);
        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            // En survie (les chausse-trappes épargnent le créatif, les chasseurs ne le visent pas), sans dégâts ni faim
            monde.getServer().runCommand("gamemode survival @a[limit=1]");
            monde.getServer().runCommand("effect give @a[limit=1] minecraft:resistance infinite 4 true");
            monde.getServer().runCommand("effect give @a[limit=1] minecraft:saturation infinite 0 true");
            monde.getServer().runCommand("attribute @a[limit=1] minecraft:knockback_resistance base set 1");
            monde.getServer().runCommand("weather clear");
            monde.getServer().runCommand("time set noon");

            fumeeEtTemoins(monde);
            fumeeEtPatrouille(monde, context);
            fumeeEtTraque(monde, context);
            vraiLancer(monde, context);
            contrebande(monde, context);
            chausseTrappes(monde, context, trappeDuZombie);
            sauvegarde = monde.getWorldSave();
        }

        // --- Rechargement : le registre des chausse-trappes est enregistré avec le monde ---
        try (TestSingleplayerContext monde = sauvegarde.open()) {
            boolean garde = monde.getServer().computeOnServer(server -> {
                RegistreChausseTrappes.Piege piege = ChausseTrappes.registre(server).a(server.overworld().dimension(), trappeDuZombie);
                return piege != null && piege.poseur().equals(OutilsTest.joueur(server).getUUID())
                        && server.overworld().getBlockState(trappeDuZombie).is(ObjetsRebelles.CHAUSSE_TRAPPE);
            });
            if (!garde) {
                throw new AssertionError("Chausse-trappe perdue au rechargement");
            }
            RedRising.LOGGER.info("[G5.8] Rechargement : la chausse-trappe et son poseur sont gardés");
        }
        RedRising.LOGGER.info("[G5.8] OK : le nuage coupe la vue des PNJ, une chausse-trappe ralentit puis disparaît, la contrebande devant un Gris fait monter la Suspicion");
    }

    // --- Fumée ---

    /** Un Rouge à 10 blocs : témoin ; nuage entre les deux : plus de témoin ; à 2 blocs dans le nuage : témoin. */
    private static void fumeeEtTemoins(TestSingleplayerContext monde) {
        monde.getServer().runCommand("tp @a 0.5 " + Y + " 0.5");
        Villager rouge = monde.getServer().computeOnServer(server -> {
            Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), new Vec3(10.5, Y, 0.5), 0, Couleur.ROUGE));
            pnj.setNoAi(true);
            return pnj;
        });
        verifierTemoin(monde, "Rouge à 10 blocs, sans fumée", Temoin.ORDINAIRE);
        monde.getServer().runOnServer(server -> Vue.enfumer(server.overworld(), new Vec3(5.5, Y + 1.5, 0.5)));
        verifierTemoin(monde, "Rouge à 10 blocs, nuage entre les deux", Temoin.AUCUN);
        RedRising.LOGGER.info("[G5.8] Fumée et témoins : un nuage entre le joueur et un Rouge, plus de témoin");

        // Au contact, la fumée ne cache rien
        monde.getServer().runOnServer(server -> {
            rouge.teleportTo(2.5, Y, 0.5);
            Vue.enfumer(server.overworld(), new Vec3(1.5, Y + 1.5, 0.5));
        });
        verifierTemoin(monde, "Rouge à 2 blocs, dans le nuage", Temoin.ORDINAIRE);

        // Le nuage se dissipe au bout de 10 secondes
        monde.getServer().runOnServer(server -> rouge.teleportTo(10.5, Y, 0.5));
        avancerLHorloge(monde, 200);
        attendre(monde, "nuages dissipés", server -> Vue.nuages(server.overworld()).isEmpty(), 10);
        verifierTemoin(monde, "Rouge à 10 blocs, nuage dissipé", Temoin.ORDINAIRE);
        RedRising.LOGGER.info("[G5.8] Fumée et témoins : témoin au contact malgré la fumée, et de nouveau quand le nuage s'est dissipé");
        monde.getServer().runOnServer(server -> rouge.discard());
    }

    /** Suspicion 50, Gris en patrouille à 8 blocs derrière un nuage : pas de contrôle ; nuage dissipé : « Halte ». */
    private static void fumeeEtPatrouille(TestSingleplayerContext monde, ClientGameTestContext context) {
        monde.getServer().runCommand("tp @a 0.5 " + Y + " 20.5");
        jauges(monde, 50, 0, 50);
        Villager gris = monde.getServer().computeOnServer(server -> {
            Vue.enfumer(server.overworld(), new Vec3(4.5, Y + 1.5, 21.5));
            return PatrouillePnj.creer(server.overworld(), List.of(new BlockPos(8, Y, 20), new BlockPos(8, Y, 23)));
        });
        Objects.requireNonNull(gris);
        context.waitTicks(100);
        if (monde.getServer().computeOnServer(server -> PatrouillePnj.suspectControle(gris)) != null) {
            throw new AssertionError("Contrôle à travers la fumée");
        }
        RedRising.LOGGER.info("[G5.8] Fumée et patrouille : pas de contrôle derrière le nuage");
        monde.getServer().runCommand("rr fumee clear");
        attendre(monde, "« Halte » une fois le nuage dissipé", server -> PatrouillePnj.suspectControle(gris) != null, 60);
        // Le joueur obéit : le contrôle se termine, la patrouille repart
        attendre(monde, "fin du contrôle", server -> PatrouillePnj.suspectControle(gris) == null, 600);
        RedRising.LOGGER.info("[G5.8] Fumée et patrouille : nuage dissipé, « Halte »");
        monde.getServer().runOnServer(server -> {
            PatrouillePnj.retirerRonde(gris);
            gris.discard();
        });
        jauges(monde, 50, 0, 0);
    }

    /** Un chasseur à 12 blocs : nuage entre eux, plus vu ni visé ; nuage dissipé, vu et touché ; au contact dans le nuage, arrêté. */
    private static void fumeeEtTraque(TestSingleplayerContext monde, ClientGameTestContext context) {
        monde.getServer().runCommand("tp @a 0.5 " + Y + " -40.5");
        monde.getServer().runCommand("rr traque start @a[limit=1]");
        monde.getServer().waitFor(server -> TraqueGris.nombreDeChasseurs(OutilsTest.joueur(server)) >= Traque.CHASSEURS_DEPART, 40);
        // Un seul chasseur, immobile, à 12 blocs ; les autres sont tués (un chasseur tué n'est remplacé que par les renforts)
        Villager chasseur = monde.getServer().computeOnServer(server -> {
            List<Villager> chasseurs = chasseurs(server);
            ServerPlayer joueur = OutilsTest.joueur(server);
            for (Villager autre : chasseurs.subList(1, chasseurs.size())) {
                autre.hurtServer(server.overworld(), joueur.damageSources().playerAttack(joueur), Float.MAX_VALUE);
            }
            Villager premier = chasseurs.getFirst();
            premier.setNoAi(true);
            premier.teleportTo(12.5, Y, -40.5);
            Vue.enfumer(server.overworld(), new Vec3(6.5, Y + 1.5, -40.5));
            return premier;
        });
        context.waitTicks(60);
        verifierBarre(context, "chasseur derrière le nuage", "traque.redrising.barre.cache");
        String touche = monde.getServer().computeOnServer(server -> etourdi(OutilsTest.joueur(server)) || !fleches(server).isEmpty()
                ? "étourdi " + etourdi(OutilsTest.joueur(server)) + ", flèches " + fleches(server).stream()
                .map(fleche -> fleche.blockPosition().toShortString() + " tirée par " + ((net.minecraft.world.entity.projectile.Projectile) fleche).getOwner()
                        + " (chasseur en " + chasseur.blockPosition().toShortString() + ")").toList()
                : null);
        if (touche != null) {
            throw new AssertionError("Le chasseur a tiré à travers la fumée : " + touche);
        }
        RedRising.LOGGER.info("[G5.8] Fumée et traque : barre jaune, aucune flèche à travers le nuage");

        monde.getServer().runCommand("rr fumee clear");
        context.waitTicks(15);
        verifierBarre(context, "nuage dissipé", "traque.redrising.barre.vu");
        attendre(monde, "flèche une fois le nuage dissipé", server -> etourdi(OutilsTest.joueur(server)), 100);
        RedRising.LOGGER.info("[G5.8] Fumée et traque : nuage dissipé, vu et étourdi");

        // Au contact, dans le nuage : arrêté quand même
        monde.getServer().runOnServer(server -> {
            chasseur.teleportTo(2.5, Y, -40.5);
            Vue.enfumer(server.overworld(), new Vec3(1.5, Y + 1.5, -40.5));
        });
        attendre(monde, "arrestation au contact dans le nuage", server -> OutilsTest.joueur(server).hasAttached(TraqueGris.INTERROGATOIRE), 160);
        RedRising.LOGGER.info("[G5.8] Fumée et traque : au contact dans le nuage, arrêté");
        monde.getServer().runCommand("rr traque stop @a[limit=1]");
        monde.getServer().runCommand("rr fumee clear");
        monde.getServer().runCommand("effect clear @a[limit=1] minecraft:slowness");
        monde.getServer().runCommand("effect clear @a[limit=1] minecraft:blindness");
        jauges(monde, 50, 0, 0);
    }

    /** Un vrai clic droit avec un fumigène : il part, et le nuage apparaît où il tombe. */
    private static void vraiLancer(TestSingleplayerContext monde, ClientGameTestContext context) {
        monde.getServer().runCommand("tp @a 0.5 " + Y + " -80.5 0 45");
        enMain(monde, new ItemStack(ObjetsRebelles.FUMIGENE, 2));
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        attendre(monde, "nuage d'un fumigène lancé", server -> !Vue.nuages(server.overworld()).isEmpty(), 60);
        int restants = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).getInventory().getSelectedItem().getCount());
        if (restants != 1) {
            throw new AssertionError("Fumigène lancé : " + restants + " en main (attendu 1)");
        }
        Vec3 centre = monde.getServer().computeOnServer(server -> Vue.nuages(server.overworld()).getFirst().centre());
        RedRising.LOGGER.info("[G5.8] Vrai lancer : nuage en {}", BlockPos.containing(centre).toShortString());
        // Le nuage vu d'un peu plus loin
        monde.getServer().runCommand("tp @a 0.5 " + Y + " -88.5 0 10");
        context.waitTicks(30);
        OutilsTest.garderCapture(context.takeScreenshot("fumigene-nuage"));
        monde.getServer().runCommand("rr fumee clear");
        monde.getServer().runCommand("clear @a[limit=1]");
    }

    // --- Contrebande ---

    private static void contrebande(TestSingleplayerContext monde, ClientGameTestContext context) {
        monde.getServer().runCommand("tp @a 0.5 " + Y + " 100.5");
        jauges(monde, 50, 0, 0);
        monde.getServer().runCommand("rr contrebande reset @a[limit=1]");
        Villager gris = monde.getServer().computeOnServer(server -> {
            Villager pnj = Objects.requireNonNull(PnjCouleur.creer(server.overworld(), new Vec3(8.5, Y, 100.5), 0, Couleur.GRIS));
            pnj.setNoAi(true);
            return pnj;
        });
        // Fumigène en main devant un Gris : +5
        enMain(monde, new ItemStack(ObjetsRebelles.FUMIGENE));
        attendreSuspicion(monde, "contrebande en main devant un Gris", 5);
        context.waitTicks(2);
        verifierMessage(context, "contrebande repérée", "faction.redrising.contrebande");
        // De nouveau dans la minute : rien
        context.waitTicks(60);
        verifierSuspicion(monde, "dans la minute", 5);
        // Une minute plus tard : +5
        avancerLHorloge(monde, Contrebande.RECHARGE);
        attendreSuspicion(monde, "une minute plus tard", 10);
        RedRising.LOGGER.info("[G5.8] Contrebande en main devant un Gris : +5, une fois par minute");

        // Rangée dans l'inventaire : rien
        monde.getServer().runOnServer(server -> {
            ServerPlayer joueur = OutilsTest.joueur(server);
            joueur.getInventory().setItem(20, joueur.getInventory().getSelectedItem().copy());
            joueur.getInventory().setSelectedItem(ItemStack.EMPTY);
        });
        avancerLHorloge(monde, Contrebande.RECHARGE);
        context.waitTicks(60);
        verifierSuspicion(monde, "contrebande rangée", 10);

        // En main, mais dans la fumée : rien ; nuage dissipé : +5
        avancerLHorloge(monde, Contrebande.RECHARGE);
        monde.getServer().runOnServer(server -> Vue.enfumer(server.overworld(), new Vec3(4.5, Y + 1.5, 100.5)));
        enMain(monde, new ItemStack(ObjetsRebelles.FUMIGENE));
        context.waitTicks(60);
        verifierSuspicion(monde, "contrebande en main, dans la fumée", 10);
        monde.getServer().runCommand("rr fumee clear");
        attendreSuspicion(monde, "contrebande en main, nuage dissipé", 15);
        RedRising.LOGGER.info("[G5.8] Contrebande : rien rangée dans l'inventaire ni cachée par la fumée");
        monde.getServer().runOnServer(server -> gris.discard());
        monde.getServer().runCommand("clear @a[limit=1]");
        jauges(monde, 50, 0, 0);
    }

    // --- Chausse-trappes ---

    private static void chausseTrappes(TestSingleplayerContext monde, ClientGameTestContext context, BlockPos trappeDuZombie) {
        // Quatre posées par de vrais clics : la première disparaît
        enMain(monde, new ItemStack(ObjetsRebelles.CHAUSSE_TRAPPE_OBJET, 6));
        for (int x = 0; x <= 9; x += 3) {
            poser(monde, context, x);
        }
        List<BlockPos> siennes = monde.getServer().computeOnServer(server ->
                ChausseTrappes.registre(server).de(OutilsTest.joueur(server).getUUID()).stream().map(RegistreChausseTrappes.Piege::pos).toList());
        List<BlockPos> attendues = List.of(new BlockPos(3, Y, 61), new BlockPos(6, Y, 61), new BlockPos(9, Y, 61));
        if (!siennes.equals(attendues) || estUnePiege(monde, new BlockPos(0, Y, 61))) {
            throw new AssertionError("Quatre chausse-trappes posées : registre " + siennes + ", première encore là : " + estUnePiege(monde, new BlockPos(0, Y, 61)));
        }
        RedRising.LOGGER.info("[G5.8] Chausse-trappes : quatre posées, la plus ancienne a disparu");
        monde.getServer().runCommand("tp @a 6.5 " + Y + " 58.5 0 45");
        context.waitTicks(10);
        OutilsTest.garderCapture(context.takeScreenshot("chausse-trappes"));
        monde.getServer().runCommand("clear @a[limit=1]");
        monde.getServer().runCommand("tp @a 20.5 " + Y + " 50.5");

        // Un zombie dessus : rien ne se passe
        monde.getServer().runCommand("summon minecraft:zombie 3.5 " + Y + " 61.5");
        Entity zombie = monde.getServer().computeOnServer(server ->
                server.overworld().getEntities(EntityTypes.ZOMBIE, new AABB(trappeDuZombie).inflate(2), mort -> true).getFirst());
        context.waitTicks(3);
        boolean dessus = monde.getServer().computeOnServer(server -> zombie.getBoundingBox().intersects(new AABB(trappeDuZombie)));
        context.waitTicks(40);
        if (!dessus || !estUnePiege(monde, trappeDuZombie) || monde.getServer().computeOnServer(server -> ((LivingEntity) zombie).hasEffect(MobEffects.SLOWNESS))) {
            throw new AssertionError("Zombie sur la chausse-trappe : dessus " + dessus + ", elle devait rester et ne pas le ralentir");
        }
        monde.getServer().runOnServer(server -> zombie.discard());
        RedRising.LOGGER.info("[G5.8] Chausse-trappe : un zombie passe dessus sans la déclencher");

        // Un Gris en patrouille passe dessus : ralenti, elle disparaît, il fouille autour
        BlockPos trappeDuGris = new BlockPos(6, Y, 61);
        Villager gris = monde.getServer().computeOnServer(server ->
                PatrouillePnj.creer(server.overworld(), List.of(new BlockPos(6, Y, 56), new BlockPos(6, Y, 66))));
        Objects.requireNonNull(gris);
        attendre(monde, "Gris en patrouille sur la chausse-trappe", server -> !estUnePiege(server, trappeDuGris), 600);
        boolean reaction = monde.getServer().computeOnServer(server -> ralenti(gris) && PatrouillePnj.fouilleRestante(gris) > 0
                && ChausseTrappes.registre(server).a(server.overworld().dimension(), trappeDuGris) == null);
        if (!reaction) {
            throw new AssertionError("Gris pris dans la chausse-trappe : il devait être ralenti et fouiller, et elle sortir du registre");
        }
        monde.getServer().runOnServer(server -> {
            PatrouillePnj.retirerRonde(gris);
            gris.discard();
        });
        RedRising.LOGGER.info("[G5.8] Chausse-trappe : un Gris en patrouille s'y prend, ralenti, il fouille autour ; elle disparaît");

        // Le poseur aussi
        BlockPos trappeDuJoueur = new BlockPos(9, Y, 61);
        monde.getServer().runCommand("tp @a 9.5 " + Y + " 61.5");
        attendre(monde, "le poseur sur sa chausse-trappe", server -> !estUnePiege(server, trappeDuJoueur), 20);
        if (!monde.getServer().computeOnServer(server -> ralenti(OutilsTest.joueur(server)))) {
            throw new AssertionError("Le poseur devait être ralenti par sa chausse-trappe");
        }
        RedRising.LOGGER.info("[G5.8] Chausse-trappe : le poseur aussi est ralenti");

        // Cassée par son poseur : rendue ; posée par un autre (ici par commande) : perdue
        monde.getServer().runCommand("tp @a 20.5 " + Y + " 50.5");
        enMain(monde, new ItemStack(ObjetsRebelles.CHAUSSE_TRAPPE_OBJET));
        poser(monde, context, 12);
        monde.getServer().runCommand("setblock 15 " + Y + " 61 redrising:chausse_trappe");
        monde.getServer().runCommand("tp @a 13.5 " + Y + " 59.5");
        context.waitTicks(5);
        int rendue = casser(monde, new BlockPos(12, Y, 61));
        int perdue = casser(monde, new BlockPos(15, Y, 61));
        if (rendue != 1 || perdue != 0) {
            throw new AssertionError("Chausse-trappe cassée : " + rendue + " rendue au poseur (attendu 1), " + perdue + " pour un autre (attendu 0)");
        }
        RedRising.LOGGER.info("[G5.8] Chausse-trappe cassée : rendue à son poseur, perdue pour un autre");
    }

    /** Debout en (x, 60), regard vers le sol devant : un clic droit pose la chausse-trappe en (x, 61). */
    private static void poser(TestSingleplayerContext monde, ClientGameTestContext context, int x) {
        monde.getServer().runCommand("tp @a " + x + ".5 " + Y + " 60.5 0 70");
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        BlockPos pos = new BlockPos(x, Y, 61);
        attendre(monde, "chausse-trappe posée en " + pos.toShortString(), server -> estUnePiege(server, pos), 20);
    }

    /** Casse le bloc comme le joueur (événements de casse compris) ; renvoie le nombre de chausse-trappes tombées au sol. */
    private static int casser(TestSingleplayerContext monde, BlockPos pos) {
        return monde.getServer().computeOnServer(server -> {
            OutilsTest.joueur(server).gameMode.destroyBlock(pos);
            List<ItemEntity> objets = server.overworld().getEntities(EntityTypes.ITEM, new AABB(pos).inflate(2),
                    objet -> objet.getItem().is(ObjetsRebelles.CHAUSSE_TRAPPE_OBJET));
            int nombre = objets.stream().mapToInt(objet -> objet.getItem().getCount()).sum();
            objets.forEach(Entity::discard);
            return nombre;
        });
    }

    // --- Outils ---

    private static void verifierTemoin(TestSingleplayerContext monde, String cas, Temoin attendu) {
        Temoin temoin = monde.getServer().computeOnServer(server -> ActionsFaction.temoin(OutilsTest.joueur(server), null));
        if (temoin != attendu) {
            throw new AssertionError(cas + " : témoin " + temoin + ", attendu " + attendu);
        }
    }

    private static List<Villager> chasseurs(MinecraftServer server) {
        return server.overworld().getEntities(EntityTypes.VILLAGER, new AABB(BlockPos.ZERO).inflate(400), pnj -> pnj.hasAttached(TraqueGris.CHASSEUR));
    }

    private static List<? extends Entity> fleches(MinecraftServer server) {
        return server.overworld().getEntities(EntityTypes.ARROW, new AABB(BlockPos.ZERO).inflate(400), fleche -> true);
    }

    /** Étourdi par une flèche de chasseur (Lenteur V). */
    private static boolean etourdi(LivingEntity entite) {
        MobEffectInstance lenteur = entite.getEffect(MobEffects.SLOWNESS);
        return lenteur != null && lenteur.getAmplifier() == 4;
    }

    /** Ralenti par une chausse-trappe (Lenteur III). */
    private static boolean ralenti(LivingEntity entite) {
        MobEffectInstance lenteur = entite.getEffect(MobEffects.SLOWNESS);
        return lenteur != null && lenteur.getAmplifier() == 2;
    }

    private static boolean estUnePiege(TestSingleplayerContext monde, BlockPos pos) {
        return monde.getServer().computeOnServer(server -> estUnePiege(server, pos));
    }

    private static boolean estUnePiege(MinecraftServer server, BlockPos pos) {
        return server.overworld().getBlockState(pos).is(ObjetsRebelles.CHAUSSE_TRAPPE);
    }

    private static void enMain(TestSingleplayerContext monde, ItemStack objet) {
        monde.getServer().runOnServer(server -> OutilsTest.joueur(server).getInventory().setSelectedItem(objet));
    }

    private static void attendre(TestSingleplayerContext monde, String cas, Predicate<MinecraftServer> condition, int ticks) {
        try {
            monde.getServer().waitFor(condition, ticks);
        } catch (AssertionError e) {
            throw new AssertionError("Pas arrivé : " + cas, e);
        }
    }

    private static void attendreSuspicion(TestSingleplayerContext monde, String cas, int attendue) {
        attendre(monde, cas + " (Suspicion " + attendue + ")", server -> JaugesJoueur.get(OutilsTest.joueur(server)).suspicion() == attendue, 60);
    }

    private static void verifierSuspicion(TestSingleplayerContext monde, String cas, int attendue) {
        int suspicion = monde.getServer().computeOnServer(server -> JaugesJoueur.get(OutilsTest.joueur(server)).suspicion());
        if (suspicion != attendue) {
            throw new AssertionError(cas + " : Suspicion " + suspicion + ", attendue " + attendue);
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

    /** Avance l'horloge du monde (le serveur suit le pas du client : /tick sprint n'accélère rien). */
    private static void avancerLHorloge(TestSingleplayerContext monde, long ticks) {
        monde.getServer().runOnServer(server -> {
            ServerLevel niveau = server.overworld();
            ((ServerLevelData) niveau.getLevelData()).setGameTime(niveau.getGameTime() + ticks);
        });
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

    private static void verifierMessage(ClientGameTestContext context, String cas, String cle) {
        Component message = context.computeOnClient(mc -> (Component) champ(mc.gui.hud, Hud.class, "overlayMessageString"));
        if (!cle(message, cle)) {
            throw new AssertionError(cas + " : message attendu " + cle + ", affiché : " + message);
        }
    }

    private static void verifierBarre(ClientGameTestContext context, String cas, String cle) {
        Map<?, ?> barres = context.computeOnClient(mc -> Map.copyOf((Map<?, ?>) champ(mc.gui.hud.getBossOverlay(), BossHealthOverlay.class, "events")));
        boolean affichee = barres.values().stream().anyMatch(barre -> cle(((BossEvent) barre).getName(), cle));
        if (!affichee) {
            throw new AssertionError("Barre de traque (" + cas + ") : attendue « " + cle + " »");
        }
    }
}
