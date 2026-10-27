package io.github.yusufbyn.redrising.factions;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nuage d'un fumigène (G5.8) : il coupe la vue au travers, sauf au contact. */
class NuageTest {
    private static final Nuage NUAGE = new Nuage(new Vec3(0, 65, 0), 200);

    @Test
    void coupeLaVueQuiLeTraverse() {
        assertTrue(NUAGE.traverse(new Vec3(-10, 65, 0), new Vec3(10, 65, 0)));
        // Frôle le bord (2 blocs du centre, rayon 2,5)
        assertTrue(NUAGE.traverse(new Vec3(-10, 65, 2), new Vec3(10, 65, 2)));
    }

    @Test
    void neCoupePasCeQuiPasseACote() {
        assertFalse(NUAGE.traverse(new Vec3(-10, 65, 3), new Vec3(10, 65, 3)));
        // Le nuage est derrière la cible : le segment s'arrête avant
        assertFalse(NUAGE.traverse(new Vec3(-20, 65, 0), new Vec3(-5, 65, 0)));
    }

    @Test
    void observateurOuCibleDansLeNuage() {
        assertTrue(NUAGE.traverse(new Vec3(0, 65, 0), new Vec3(10, 65, 0)));
        assertTrue(NUAGE.traverse(new Vec3(10, 65, 0), new Vec3(1, 65, 1)));
    }

    @Test
    void auContactLaFumeeNeCacheRien() {
        // À 2 blocs l'un de l'autre, au cœur du nuage
        assertFalse(Nuage.cache(new Vec3(-1, 65, 0), new Vec3(1, 65, 0), List.of(NUAGE)));
        // Juste au-delà du contact
        assertTrue(Nuage.cache(new Vec3(-1.5, 65, 0), new Vec3(1.5, 65, 0), List.of(NUAGE)));
    }

    @Test
    void sansNuageRienNEstCache() {
        assertFalse(Nuage.cache(new Vec3(-10, 65, 0), new Vec3(10, 65, 0), List.of()));
    }
}
