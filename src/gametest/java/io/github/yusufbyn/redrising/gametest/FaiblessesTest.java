package io.github.yusufbyn.redrising.gametest;

import io.github.yusufbyn.redrising.RedRising;
import io.github.yusufbyn.redrising.couleur.Condition;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.couleur.ProfilCouleur;
import io.github.yusufbyn.redrising.couleur.ProfilsCouleurs;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * G3.4 : un malus conditionnel fonctionne. La Couleur de test est fragile en permanence et,
 * à ciel ouvert seulement, lente et affaiblie : le malus part sous un toit et revient dehors.
 *
 * <p>Le profil n'est déclaré que dans ce mod de test, sur une Couleur qu'aucun autre test n'utilise.</p>
 */
public class FaiblessesTest implements FabricClientGameTest {
    private static final Couleur COULEUR_TEST = Couleur.ROSE;
    /** Toit de pierre deux blocs au-dessus de la tête du joueur, posé en (0.5, -60, 0.5). */
    private static final String TOIT = "fill -3 -57 -3 3 -57 3 ";

    private record Etat(double vitesse, double vieMax, boolean faiblesse, boolean cielOuvert) {
        static Etat de(ServerPlayer joueur) {
            return new Etat(
                    joueur.getAttributeValue(Attributes.MOVEMENT_SPEED),
                    joueur.getMaxHealth(),
                    joueur.hasEffect(MobEffects.WEAKNESS),
                    Condition.A_CIEL_OUVERT.test(joueur));
        }

        boolean memeQue(double vitesseAttendue, double vieMaxAttendue, boolean faiblesseAttendue) {
            return Math.abs(vitesse - vitesseAttendue) < 1e-6 && Math.abs(vieMax - vieMaxAttendue) < 1e-6 && faiblesse == faiblesseAttendue;
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        // La Couleur de test, en quelques lignes : fragile, et dehors lente et affaiblie
        ProfilsCouleurs.declarer(COULEUR_TEST, ProfilCouleur.builder()
                .attribut(Attributes.MAX_HEALTH, -4, Operation.ADD_VALUE)                         // fragilité : 2 cœurs de moins
                .si(Condition.A_CIEL_OUVERT, malus -> malus
                        .attribut(Attributes.MOVEMENT_SPEED, -0.3, Operation.ADD_MULTIPLIED_BASE) // lenteur à ciel ouvert
                        .effet(MobEffects.WEAKNESS))                                              // faiblesse à ciel ouvert
                .build());

        try (TestSingleplayerContext monde = OutilsTest.nouveauMonde(context)) {
            OutilsTest.attendreArrivee(context);
            monde.getServer().runCommand("tp @a 0.5 -60 0.5");

            Etat base = monde.getServer().computeOnServer(server -> {
                var joueur = OutilsTest.joueur(server);
                CouleurJoueur.set(joueur, Couleur.OR); // Couleur sans profil
                return Etat.de(joueur);
            });
            RedRising.LOGGER.info("[G3.4] Sans profil : {}", base);
            double vitesseMalus = base.vitesse() * 0.7;
            double vieFragile = base.vieMax() - 4;

            monde.getServer().runOnServer(server -> CouleurJoueur.set(OutilsTest.joueur(server), COULEUR_TEST));
            attendre(monde, "à ciel ouvert", vitesseMalus, vieFragile, true);

            monde.getServer().runCommand(TOIT + "stone");
            attendre(monde, "sous un toit", base.vitesse(), vieFragile, false);

            monde.getServer().runCommand(TOIT + "air");
            attendre(monde, "toit retiré", vitesseMalus, vieFragile, true);

            OutilsTest.mourirEtReapparaitre(context, monde);
            attendre(monde, "après la mort, dehors", vitesseMalus, vieFragile, true);
            float vie = monde.getServer().computeOnServer(server -> OutilsTest.joueur(server).getHealth());
            if (vie != vieFragile) {
                throw new AssertionError("Vie après la mort : attendu " + vieFragile + ", obtenu " + vie);
            }

            monde.getServer().runOnServer(server -> CouleurJoueur.set(OutilsTest.joueur(server), Couleur.OR));
            attendre(monde, "retour à une Couleur sans profil", base.vitesse(), base.vieMax(), false);
        }

        RedRising.LOGGER.info("[G3.4] OK : malus permanent et malus conditionnel (à ciel ouvert) fonctionnent");
    }

    /** Attend au plus 2 secondes que l'entretien (2 fois par seconde) ait mis l'état attendu. */
    private static void attendre(TestSingleplayerContext monde, String moment, double vitesse, double vieMax, boolean faiblesse) {
        try {
            monde.getServer().waitFor(server -> Etat.de(OutilsTest.joueur(server)).memeQue(vitesse, vieMax, faiblesse), 40);
        } catch (AssertionError e) {
            Etat obtenu = monde.getServer().computeOnServer(server -> Etat.de(OutilsTest.joueur(server)));
            throw new AssertionError("État " + moment + " : attendu vitesse " + vitesse + ", vie max " + vieMax
                    + ", faiblesse " + faiblesse + " ; obtenu " + obtenu, e);
        }
        RedRising.LOGGER.info("[G3.4] {} : {}", moment, monde.getServer().computeOnServer(server -> Etat.de(OutilsTest.joueur(server))));
    }
}
