package io.github.yusufbyn.redrising.jalons;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Horloge du monde (G6.1) : temps de jeu, aide et tort au Rising. */
class HorlogeTest {
    @Test
    void gainsEtPertesDeChaqueCote() {
        Horloge horloge = Horloge.ZERO.compter(12).compter(-7).compter(3);
        assertEquals(15, horloge.aide());
        assertEquals(7, horloge.tort());
        assertEquals(8, horloge.net());
    }

    @Test
    void perduPuisRegagneNeChangePasLeNet() {
        Horloge horloge = Horloge.ZERO.compter(-10).compter(10);
        assertEquals(new Horloge(0, 10, 10), horloge);
        assertEquals(0, horloge.net());
    }

    @Test
    void tempsQuiAvance() {
        assertEquals(40, Horloge.ZERO.avancer(20).avancer(20).temps());
    }

    @Test
    void jamaisNegatif() {
        assertEquals(Horloge.ZERO, new Horloge(-5, -1, -2));
        assertEquals(0, Horloge.ZERO.avecTemps(10).avancer(-30).temps());
    }

    @Test
    void allerRetour() {
        Horloge horloge = new Horloge(123_456, 78, 9);
        JsonElement json = Horloge.CODEC.encodeStart(JsonOps.INSTANCE, horloge).getOrThrow();
        assertEquals(horloge, Horloge.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
}
