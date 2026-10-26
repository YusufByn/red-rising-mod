package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.couleur.ProfilCouleur;
import io.github.yusufbyn.redrising.couleur.ProfilsCouleurs;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * G3.1 : une Couleur de test déclarée en quelques lignes applique ses bonus,
 * et les garde après la mort, le lait et le rechargement ; changer de Couleur les retire.
 *
 * <p>Le profil n'est déclaré que dans ce mod de test : aucune vraie Couleur n'a encore de capacités.
 * Ce test doit rester le dernier de la liste, car le profil reste déclaré ensuite.</p>
 */
public class CapacitesPassivesTest implements FabricClientGameTest {
    private static final Couleur COULEUR_TEST = Couleur.VERT;

    /** Valeurs d'un joueur sans aucun bonus. */
    private record Mesure(double vitesse, double degats, double vieMax, double minage, boolean visionNocturne) {
        static Mesure de(ServerPlayer joueur) {
            MobEffectInstance vision = joueur.getEffect(MobEffects.NIGHT_VISION);
            return new Mesure(
                    joueur.getAttributeValue(Attributes.MOVEMENT_SPEED),
                    joueur.getAttributeValue(Attributes.ATTACK_DAMAGE),
                    joueur.getMaxHealth(),
                    joueur.getAttributeValue(Attributes.BLOCK_BREAK_SPEED),
                    vision != null && vision.isInfiniteDuration());
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        // La Couleur de test, en quelques lignes
        ProfilsCouleurs.declarer(COULEUR_TEST, ProfilCouleur.builder()
                .attribut(Attributes.MOVEMENT_SPEED, 0.2, Operation.ADD_MULTIPLIED_BASE)   // vitesse +20 %
                .attribut(Attributes.ATTACK_DAMAGE, 2, Operation.ADD_VALUE)                // dégâts +2
                .attribut(Attributes.MAX_HEALTH, 4, Operation.ADD_VALUE)                   // vie +2 cœurs
                .attribut(Attributes.BLOCK_BREAK_SPEED, 0.5, Operation.ADD_MULTIPLIED_BASE) // minage +50 %
                .effet(MobEffects.NIGHT_VISION)
                .build());

        TestWorldSave sauvegarde;
        Mesure base;
        Mesure attendue;

        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);

            base = monde.getServer().computeOnServer(server -> {
                var joueur = OutilsTest.joueur(server);
                CouleurJoueur.set(joueur, Couleur.OR); // Couleur sans profil : valeurs de base
                return Mesure.de(joueur);
            });
            attendue = new Mesure(base.vitesse() * 1.2, base.degats() + 2, base.vieMax() + 4, base.minage() * 1.5, true);
            RedRising.LOGGER.info("[G3.1] Sans bonus : {}", base);

            monde.getServer().runOnServer(server -> {
                var joueur = OutilsTest.joueur(server);
                CouleurJoueur.set(joueur, COULEUR_TEST);
                joueur.setHealth(joueur.getMaxHealth());
            });
            verifier("après passage au Vert", attendue, mesure(monde));
            verifierVie("après passage au Vert", attendue.vieMax(), monde);

            // Le lait ou /effect clear retirent l'effet : il revient dans la seconde
            monde.getServer().runCommand("effect clear @a");
            if (mesure(monde).visionNocturne()) {
                throw new AssertionError("/effect clear n'a pas retiré la vision nocturne : le test ne prouverait rien");
            }
            context.waitTicks(30);
            verifier("après /effect clear", attendue, mesure(monde));

            OutilsTest.mourirEtReapparaitre(context, monde);
            verifier("après la mort", attendue, mesure(monde));
            verifierVie("après la mort (vie pleine)", attendue.vieMax(), monde);

            sauvegarde = monde.getWorldSave();
        }

        try (TestSingleplayerContext monde = sauvegarde.open()) {
            OutilsTest.attendreArrivee(context);
            verifier("après rechargement", attendue, mesure(monde));
            // La vie au-dessus de 20 n'est pas rognée au chargement
            verifierVie("après rechargement", attendue.vieMax(), monde);

            monde.getServer().runOnServer(server -> CouleurJoueur.set(OutilsTest.joueur(server), Couleur.OR));
            verifier("après retour à une Couleur sans bonus", base, mesure(monde));
            verifierVie("après retour à une Couleur sans bonus", base.vieMax(), monde);
        }

        RedRising.LOGGER.info("[G3.1] OK : capacités passives appliquées, conservées et retirées");
    }

    private static Mesure mesure(TestSingleplayerContext monde) {
        return monde.getServer().computeOnServer(server -> Mesure.de(OutilsTest.joueur(server)));
    }

    private static void verifier(String moment, Mesure attendue, Mesure obtenue) {
        boolean egal = proche(attendue.vitesse(), obtenue.vitesse())
                && proche(attendue.degats(), obtenue.degats())
                && proche(attendue.vieMax(), obtenue.vieMax())
                && proche(attendue.minage(), obtenue.minage())
                && attendue.visionNocturne() == obtenue.visionNocturne();
        if (!egal) {
            throw new AssertionError("Capacités " + moment + " : attendu " + attendue + ", obtenu " + obtenue);
        }
        RedRising.LOGGER.info("[G3.1] Capacités {} : {}", moment, obtenue);
    }

    private static void verifierVie(String moment, double attendue, TestSingleplayerContext monde) {
        float vie = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).getHealth());
        if (!proche(attendue, vie)) {
            throw new AssertionError("Vie " + moment + " : attendu " + attendue + ", obtenu " + vie);
        }
        RedRising.LOGGER.info("[G3.1] Vie {} : {}", moment, vie);
    }

    private static boolean proche(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }
}
