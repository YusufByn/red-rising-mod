package io.github.yusufbyn.redrising.factions;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ronde d'une patrouille (G5.5) : points dans l'ordre, en boucle. */
class RondeTest {
    private static final BlockPos A = new BlockPos(0, 64, 0);
    private static final BlockPos B = new BlockPos(10, 64, 0);
    private static final BlockPos C = new BlockPos(10, 64, 10);

    @Test
    void tourneEnBoucle() {
        Ronde ronde = Ronde.de(List.of(A, B, C));
        assertEquals(A, ronde.pointEnCours());
        ronde = ronde.suivante();
        assertEquals(B, ronde.pointEnCours());
        ronde = ronde.suivante().suivante();
        assertEquals(A, ronde.pointEnCours());
        assertEquals(0, ronde.index());
    }

    @Test
    void pointAtteintAMoinsDeDeuxBlocs() {
        Ronde ronde = Ronde.de(List.of(A, B));
        assertTrue(ronde.atteint(A));
        assertTrue(ronde.atteint(A.east()));
        assertFalse(ronde.atteint(A.east(2)));
        assertFalse(ronde.atteint(B));
    }

    @Test
    void auMoinsDeuxPoints() {
        assertThrows(IllegalArgumentException.class, () -> Ronde.de(List.of(A)));
    }
}
