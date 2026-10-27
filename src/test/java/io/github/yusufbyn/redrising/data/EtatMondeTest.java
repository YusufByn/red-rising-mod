package io.github.yusufbyn.redrising.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Horloge;
import io.github.yusufbyn.redrising.jalons.Issue;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Format sauvegardé de l'état du monde (G2.4), testé en JSON (même codec que la sauvegarde NBT). */
class EtatMondeTest {
    private static DataResult<EtatMonde> lire(String json) {
        return EtatMonde.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @Test
    void allerRetourConserveTout() {
        EtatMonde etat = EtatMonde.NOUVEAU
                .avecEpoque(Epoque.GOLDEN_SON)
                .avecIssue("pendaison_eo", Issue.CANON)
                .avecZone("lykos", Camp.RISING)
                .avecHorloge(new Horloge(48_000, 30, 12));
        JsonElement json = EtatMonde.CODEC.encodeStart(JsonOps.INSTANCE, etat).getOrThrow();
        assertEquals(etat, EtatMonde.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void numeroDeVersionToujoursEcrit() {
        JsonElement json = EtatMonde.CODEC.encodeStart(JsonOps.INSTANCE, EtatMonde.NOUVEAU).getOrThrow();
        assertEquals(EtatMonde.VERSION_SCHEMA, json.getAsJsonObject().get("schemaVersion").getAsInt());
    }

    @Test
    void numeroDeVersionLu() {
        EtatMonde lu = lire("{\"schemaVersion\": 2, \"epoque\": \"morning_star\", \"jalons\": {\"pendaison_eo\": \"divergente\"},"
                + " \"horloge\": {\"temps\": 24000, \"aide\": 7, \"tort\": 3}}").getOrThrow();
        assertEquals(2, lu.schemaVersion());
        assertEquals(Epoque.MORNING_STAR, lu.epoque());
        assertEquals(Map.of("pendaison_eo", Issue.DIVERGENTE), lu.jalons());
        assertEquals(new Horloge(24_000, 7, 3), lu.horloge());
        // Champ absent : valeur par défaut
        assertEquals(Map.of(), lu.zones());
    }

    /** Un état du monde v1, tel que G2.4 l'écrivait : migré en v2, avec une horloge à zéro, le reste intact. */
    @Test
    void mondeV1Migre() {
        EtatMonde lu = lire("{\"schemaVersion\": 1, \"epoque\": \"golden_son\", \"jalons\": {\"pendaison_eo\": \"canon\"},"
                + " \"zones\": {\"lykos\": \"rising\"}}").getOrThrow();
        assertEquals(2, lu.schemaVersion());
        assertEquals(Epoque.GOLDEN_SON, lu.epoque());
        assertEquals(Map.of("pendaison_eo", Issue.CANON), lu.jalons());
        assertEquals(Map.of("lykos", Camp.RISING), lu.zones());
        assertEquals(Horloge.ZERO, lu.horloge());
        // Réécrit au format actuel
        JsonElement json = EtatMonde.CODEC.encodeStart(JsonOps.INSTANCE, lu).getOrThrow();
        assertEquals(2, json.getAsJsonObject().get("schemaVersion").getAsInt());
        assertTrue(json.getAsJsonObject().has("horloge"));
    }

    @Test
    void versionManquanteRefusee() {
        assertTrue(lire("{\"epoque\": \"red_rising\"}").isError());
    }

    @Test
    void versionPlusRecenteRefusee() {
        DataResult<EtatMonde> resultat = lire("{\"schemaVersion\": " + (EtatMonde.VERSION_SCHEMA + 1) + ", \"epoque\": \"red_rising\"}");
        assertTrue(resultat.isError());
    }

    @Test
    void versionInvalideRefusee() {
        assertTrue(lire("{\"schemaVersion\": 0, \"epoque\": \"red_rising\"}").isError());
    }

    @Test
    void modificationsSansEffetDeBord() {
        EtatMonde avant = EtatMonde.NOUVEAU;
        EtatMonde apres = avant.avecIssue("pendaison_eo", Issue.CANON);
        assertEquals(Map.of(), avant.jalons());
        assertEquals(Map.of("pendaison_eo", Issue.CANON), apres.jalons());
    }

    @Test
    void effacerJalonEtZone() {
        EtatMonde etat = EtatMonde.NOUVEAU.avecIssue("pendaison_eo", Issue.CANON).avecZone("lykos", Camp.RISING);
        assertEquals(EtatMonde.NOUVEAU, etat.sansIssue("pendaison_eo").sansZone("lykos"));
        // Effacer ce qui n'existe pas ne change rien
        assertEquals(etat, etat.sansIssue("inconnu").sansZone("inconnue"));
    }
}
