package io.github.yusufbyn.redrising.factions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registre des chausse-trappes (G5.8) : au plus trois par joueur, la plus ancienne disparaît. */
class RegistreChausseTrappesTest {
    private static final ResourceKey<Level> MONDE = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private static RegistreChausseTrappes.Piege piege(int x, UUID poseur, long pose) {
        return new RegistreChausseTrappes.Piege(MONDE, new BlockPos(x, 64, 0), poseur, pose);
    }

    @Test
    void auPlusTroisParJoueurLaPlusAncienneDisparait() {
        RegistreChausseTrappes registre = RegistreChausseTrappes.VIDE;
        for (int i = 0; i < RegistreChausseTrappes.MAX_PAR_JOUEUR; i++) {
            RegistreChausseTrappes.Pose pose = registre.poser(piege(i, ALICE, i));
            assertTrue(pose.retirees().isEmpty());
            registre = pose.registre();
        }
        RegistreChausseTrappes.Pose quatrieme = registre.poser(piege(10, ALICE, 10));
        assertEquals(List.of(piege(0, ALICE, 0)), quatrieme.retirees());
        assertEquals(RegistreChausseTrappes.MAX_PAR_JOUEUR, quatrieme.registre().de(ALICE).size());
        assertNull(quatrieme.registre().a(MONDE, new BlockPos(0, 64, 0)));
    }

    @Test
    void chaqueJoueurASaLimite() {
        RegistreChausseTrappes registre = RegistreChausseTrappes.VIDE;
        for (int i = 0; i < RegistreChausseTrappes.MAX_PAR_JOUEUR; i++) {
            registre = registre.poser(piege(i, ALICE, i)).registre();
        }
        RegistreChausseTrappes.Pose deBob = registre.poser(piege(20, BOB, 20));
        assertTrue(deBob.retirees().isEmpty());
        assertEquals(RegistreChausseTrappes.MAX_PAR_JOUEUR + 1, deBob.registre().pieges().size());
    }

    @Test
    void reposerAuMemeEndroitRemplace() {
        RegistreChausseTrappes registre = RegistreChausseTrappes.VIDE.poser(piege(0, ALICE, 0)).registre();
        registre = registre.poser(piege(0, BOB, 5)).registre();
        assertEquals(1, registre.pieges().size());
        assertEquals(BOB, registre.a(MONDE, new BlockPos(0, 64, 0)).poseur());
    }

    @Test
    void retirer() {
        RegistreChausseTrappes registre = RegistreChausseTrappes.VIDE.poser(piege(0, ALICE, 0)).registre().poser(piege(1, ALICE, 1)).registre();
        registre = registre.retirer(MONDE, new BlockPos(0, 64, 0));
        assertEquals(List.of(piege(1, ALICE, 1)), registre.pieges());
    }
}
