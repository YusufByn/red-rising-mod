package io.github.yusufbyn.redrising.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Issue;

import java.util.HashMap;
import java.util.Map;

/**
 * État du monde sauvegardé (G2.4) : époque, issue de chaque jalon passé, camp qui contrôle chaque zone.
 *
 * <p>Le format est versionné ({@code schemaVersion}) pour que les mondes v1 restent lisibles en v2 (GDD section 10).
 * Règle : toute modification du format sauvegardé incrémente {@link #VERSION_SCHEMA} et ajoute une étape
 * dans {@link #migrer}. Une instance en mémoire est toujours au format actuel.</p>
 *
 * @param jalons issue de chaque jalon passé, par identifiant de jalon (ex. {@code "pendaison_eo"})
 * @param zones  camp qui contrôle chaque zone, par identifiant de zone
 */
public record EtatMonde(int schemaVersion, Epoque epoque, Map<String, Issue> jalons, Map<String, Camp> zones) {
    /** Version actuelle du format. */
    public static final int VERSION_SCHEMA = 1;

    /** État d'un monde neuf : début du tome 1, aucun jalon passé, aucune zone attribuée. */
    public static final EtatMonde NOUVEAU = new EtatMonde(VERSION_SCHEMA, Epoque.RED_RISING, Map.of(), Map.of());

    /** Format brut, tel qu'il est écrit sur le disque, sans migration. */
    private static final Codec<EtatMonde> CODEC_BRUT = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("schemaVersion").forGetter(EtatMonde::schemaVersion),
            Epoque.CODEC.fieldOf("epoque").forGetter(EtatMonde::epoque),
            Codec.unboundedMap(Codec.STRING, Issue.CODEC).optionalFieldOf("jalons", Map.of()).forGetter(EtatMonde::jalons),
            Codec.unboundedMap(Codec.STRING, Camp.CODEC).optionalFieldOf("zones", Map.of()).forGetter(EtatMonde::zones)
    ).apply(instance, EtatMonde::new));

    /** Codec de sauvegarde : à la lecture, le numéro de version est lu puis l'état est migré au format actuel. */
    public static final Codec<EtatMonde> CODEC = CODEC_BRUT.flatXmap(EtatMonde::migrer, DataResult::success);

    public EtatMonde {
        // Copies immuables : un record ne doit pas partager une Map modifiable
        jalons = Map.copyOf(jalons);
        zones = Map.copyOf(zones);
    }

    /** Ramène un état lu sur le disque au format actuel. */
    static DataResult<EtatMonde> migrer(EtatMonde lu) {
        if (lu.schemaVersion() > VERSION_SCHEMA) {
            return DataResult.error(() -> "État du monde au schéma v" + lu.schemaVersion()
                    + ", plus récent que ce mod (v" + VERSION_SCHEMA + ") : monde créé par une version plus récente");
        }
        if (lu.schemaVersion() < 1) {
            return DataResult.error(() -> "État du monde : schemaVersion invalide (" + lu.schemaVersion() + ")");
        }
        EtatMonde etat = lu;
        // Migrations futures, une étape par version, dans l'ordre :
        // if (etat.schemaVersion() == 1) etat = migrerV1VersV2(etat);
        return DataResult.success(etat);
    }

    public EtatMonde avecEpoque(Epoque nouvelle) {
        return new EtatMonde(schemaVersion, nouvelle, jalons, zones);
    }

    public EtatMonde avecIssue(String jalon, Issue issue) {
        Map<String, Issue> copie = new HashMap<>(jalons);
        copie.put(jalon, issue);
        return new EtatMonde(schemaVersion, epoque, copie, zones);
    }

    public EtatMonde avecZone(String zone, Camp camp) {
        Map<String, Camp> copie = new HashMap<>(zones);
        copie.put(zone, camp);
        return new EtatMonde(schemaVersion, epoque, jalons, copie);
    }
}
