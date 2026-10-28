package io.github.yusufbyn.redrising.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.factions.Variation;
import io.github.yusufbyn.redrising.jalons.EffetEnCours;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Horloge;
import io.github.yusufbyn.redrising.jalons.Issue;
import io.github.yusufbyn.redrising.jalons.Levier;
import io.github.yusufbyn.redrising.jalons.Versement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
                .avecHorloge(new Horloge(48_000, 96_000, 30, 12))
                .avecMiseEnRoute("triomphe", new Horloge(24_000, 24_000, 10, 2))
                .avecEffetsEnAttente("pendaison_eo", true)
                .avecEffet("pendaison_eo/canon/0", new EffetEnCours(Levier.ALERTE_PATROUILLES, 1, "pendaison_eo", 100, 24_100))
                .avecVersement(new Versement("pendaison_eo/canon/1", "pendaison_eo", Issue.CANON, Optional.of(Couleur.ROUGE),
                        new Variation(0, 10, 0), false, 100));
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
        EtatMonde lu = lire("{\"schemaVersion\": 4, \"epoque\": \"morning_star\", \"jalons\": {\"pendaison_eo\": \"divergente\"},"
                + " \"horloge\": {\"temps\": 24000, \"presence\": 48000, \"aide\": 7, \"tort\": 3},"
                + " \"en_route\": {\"triomphe\": {\"temps\": 100, \"presence\": 100, \"aide\": 1, \"tort\": 0}},"
                + " \"effets_en_attente\": [\"pendaison_eo\"]}").getOrThrow();
        assertEquals(4, lu.schemaVersion());
        assertEquals(Epoque.MORNING_STAR, lu.epoque());
        assertEquals(Map.of("pendaison_eo", Issue.DIVERGENTE), lu.jalons());
        assertEquals(new Horloge(24_000, 48_000, 7, 3), lu.horloge());
        assertEquals(Map.of("triomphe", new Horloge(100, 100, 1, 0)), lu.enRoute());
        assertEquals(Set.of("pendaison_eo"), lu.effetsEnAttente());
        // Champ absent : valeur par défaut
        assertEquals(Map.of(), lu.zones());
    }

    /** Un état du monde v3, tel que G6.2 l'écrivait : aucun effet en cours ni versement, le reste intact. */
    @Test
    void mondeV3Migre() {
        EtatMonde lu = lire("{\"schemaVersion\": 3, \"epoque\": \"red_rising\", \"jalons\": {\"pendaison_eo\": \"canon\"},"
                + " \"horloge\": {\"temps\": 50000, \"presence\": 60000, \"aide\": 30, \"tort\": 12},"
                + " \"effets_en_attente\": [\"pendaison_eo\"]}").getOrThrow();
        assertEquals(4, lu.schemaVersion());
        assertEquals(new Horloge(50_000, 60_000, 30, 12), lu.horloge());
        assertEquals(Set.of("pendaison_eo"), lu.effetsEnAttente());
        assertEquals(Map.of(), lu.effets());
        assertEquals(List.of(), lu.versements());
    }

    /** Un état du monde v2, tel que G6.1 l'écrivait : présence = temps de jeu, rien en route, effets déjà appliqués. */
    @Test
    void mondeV2Migre() {
        EtatMonde lu = lire("{\"schemaVersion\": 2, \"epoque\": \"golden_son\", \"jalons\": {\"pendaison_eo\": \"canon\"},"
                + " \"horloge\": {\"temps\": 50000, \"aide\": 30, \"tort\": 12}}").getOrThrow();
        assertEquals(EtatMonde.VERSION_SCHEMA, lu.schemaVersion());
        assertEquals(Map.of("pendaison_eo", Issue.CANON), lu.jalons());
        assertEquals(new Horloge(50_000, 50_000, 30, 12), lu.horloge());
        assertEquals(Map.of(), lu.enRoute());
        assertEquals(Set.of(), lu.effetsEnAttente());
    }

    /** Un état du monde v1, tel que G2.4 l'écrivait : migré au format actuel, avec une horloge à zéro, le reste intact. */
    @Test
    void mondeV1Migre() {
        EtatMonde lu = lire("{\"schemaVersion\": 1, \"epoque\": \"golden_son\", \"jalons\": {\"pendaison_eo\": \"canon\"},"
                + " \"zones\": {\"lykos\": \"rising\"}}").getOrThrow();
        assertEquals(EtatMonde.VERSION_SCHEMA, lu.schemaVersion());
        assertEquals(Epoque.GOLDEN_SON, lu.epoque());
        assertEquals(Map.of("pendaison_eo", Issue.CANON), lu.jalons());
        assertEquals(Map.of("lykos", Camp.RISING), lu.zones());
        assertEquals(Horloge.ZERO, lu.horloge());
        // Réécrit au format actuel
        JsonElement json = EtatMonde.CODEC.encodeStart(JsonOps.INSTANCE, lu).getOrThrow();
        assertEquals(EtatMonde.VERSION_SCHEMA, json.getAsJsonObject().get("schemaVersion").getAsInt());
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
