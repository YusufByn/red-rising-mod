package io.github.yusufbyn.redrising.monde;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le froid selon la latitude (G7.1.2) : rien avant la bande, une montée progressive dedans, gelé au-delà, aux deux pôles. */
class LatitudeTest {
    private static final int DEBUT = PlanMars.LATITUDE_POLES - PlanMars.TRANSITION_POLES;

    /** Part des valeurs du bruit (de -10 000 à 10 000) qui deviennent froides à cette latitude. */
    private static double partFroide(int z) {
        int froids = 0;
        int total = 0;
        for (long variation = -10_000; variation <= 10_000; variation += 100) {
            total++;
            if (Latitude.climat(z, variation) != Latitude.Climat.NORMAL) {
                froids++;
            }
        }
        return (double) froids / total;
    }

    @Test
    void rienAvantLaBande() {
        assertEquals(0, partFroide(0));
        assertEquals(0, partFroide(DEBUT - 1));
        assertEquals(0, partFroide(-(DEBUT - 1)));
    }

    @Test
    void pasDeMurDansLaBande() {
        // La part de froid monte peu à peu : jamais un saut de plus de 5 % sur 20 blocs
        double avant = 0;
        for (int z = DEBUT; z < PlanMars.LATITUDE_POLES; z += 20) {
            double part = partFroide(z);
            assertTrue(part >= avant, "à " + z + " : " + part + " après " + avant);
            assertTrue(part - avant <= 0.05 + 1e-9, "saut à " + z + " : " + avant + " → " + part);
            avant = part;
        }
        // Au milieu de la bande, un mélange
        double milieu = partFroide(DEBUT + PlanMars.TRANSITION_POLES / 2);
        assertTrue(milieu > 0.3 && milieu < 0.7, "milieu : " + milieu);
    }

    @Test
    void lesDeuxPolesGeles() {
        for (int z : new int[]{PlanMars.LATITUDE_POLES, -PlanMars.LATITUDE_POLES, 5000, -5000}) {
            for (long temperature : new long[]{-10_000, 0, 10_000}) {
                assertEquals(Latitude.Climat.GELE, Latitude.climat(z, temperature), "z " + z);
            }
        }
        // Dans la bande, seulement du froid, jamais du gelé
        assertEquals(Latitude.Climat.FROID, Latitude.climat(PlanMars.LATITUDE_POLES - 1, -10_000));
    }

    @Test
    void lesEndroitsDuBasDuBruitBasculentLesPremiers() {
        int z = DEBUT + PlanMars.TRANSITION_POLES / 4;
        assertEquals(Latitude.Climat.FROID, Latitude.climat(z, -9_000));
        assertEquals(Latitude.Climat.NORMAL, Latitude.climat(z, 9_000));
    }
}
