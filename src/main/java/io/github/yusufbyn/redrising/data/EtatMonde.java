package io.github.yusufbyn.redrising.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Horloge;
import io.github.yusufbyn.redrising.jalons.Issue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * État du monde sauvegardé (G2.4) : époque, issue de chaque jalon passé, camp qui contrôle chaque zone, horloge du monde
 * (temps de jeu et Soutien Rising cumulé, G6.1).
 *
 * <p>Le format est versionné ({@code schemaVersion}) pour que les mondes v1 restent lisibles en v2 (GDD section 10).
 * Règle : toute modification du format sauvegardé incrémente {@link #VERSION_SCHEMA} et ajoute une étape
 * dans {@link #migrer}. Une instance en mémoire est toujours au format actuel.</p>
 *
 * @param jalons issue de chaque jalon passé, par identifiant de jalon (ex. {@code "pendaison_eo"})
 * @param zones  camp qui contrôle chaque zone, par identifiant de zone
 * @param horloge temps de jeu et Soutien Rising cumulé (G6.1, schéma v2)
 */
public record EtatMonde(int schemaVersion, Epoque epoque, Map<String, Issue> jalons, Map<String, Camp> zones, Horloge horloge) {
    /**
     * Version actuelle du format.
     * <ul>
     *     <li>v1 (G2.4) : époque, jalons, zones.</li>
     *     <li>v2 (G6.1) : + horloge.</li>
     * </ul>
     */
    public static final int VERSION_SCHEMA = 2;

    /** État d'un monde neuf : début du tome 1, aucun jalon passé, aucune zone attribuée, horloge à zéro. */
    public static final EtatMonde NOUVEAU = new EtatMonde(VERSION_SCHEMA, Epoque.RED_RISING, Map.of(), Map.of(), Horloge.ZERO);

    /** Format brut, tel qu'il est écrit sur le disque, sans migration. */
    private static final Codec<EtatMonde> CODEC_BRUT = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("schemaVersion").forGetter(EtatMonde::schemaVersion),
            Epoque.CODEC.fieldOf("epoque").forGetter(EtatMonde::epoque),
            Codec.unboundedMap(Codec.STRING, Issue.CODEC).optionalFieldOf("jalons", Map.of()).forGetter(EtatMonde::jalons),
            Codec.unboundedMap(Codec.STRING, Camp.CODEC).optionalFieldOf("zones", Map.of()).forGetter(EtatMonde::zones),
            // Absente d'un monde v1 : lue à zéro, puis la migration pose le numéro v2. Toujours écrite, même à zéro
            Horloge.CODEC.optionalFieldOf("horloge").xmap(lue -> lue.orElse(Horloge.ZERO), Optional::of).forGetter(EtatMonde::horloge)
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
        // Une étape par version, dans l'ordre
        if (etat.schemaVersion() == 1) {
            etat = migrerV1VersV2(etat);
        }
        return DataResult.success(etat);
    }

    /**
     * v1 → v2 (G6.1) : ajoute l'horloge du monde, à zéro. Un monde v1 commence à compter son temps de jeu et son Soutien
     * cumulé à la mise à jour (aucun monde n'avait été publié : pas besoin d'estimer le passé).
     */
    private static EtatMonde migrerV1VersV2(EtatMonde v1) {
        return new EtatMonde(2, v1.epoque(), v1.jalons(), v1.zones(), Horloge.ZERO);
    }

    public EtatMonde avecEpoque(Epoque nouvelle) {
        return new EtatMonde(schemaVersion, nouvelle, jalons, zones, horloge);
    }

    public EtatMonde avecIssue(String jalon, Issue issue) {
        Map<String, Issue> copie = new HashMap<>(jalons);
        copie.put(jalon, issue);
        return new EtatMonde(schemaVersion, epoque, copie, zones, horloge);
    }

    public EtatMonde avecZone(String zone, Camp camp) {
        Map<String, Camp> copie = new HashMap<>(zones);
        copie.put(zone, camp);
        return new EtatMonde(schemaVersion, epoque, jalons, copie, horloge);
    }

    public EtatMonde avecHorloge(Horloge nouvelle) {
        return new EtatMonde(schemaVersion, epoque, jalons, zones, nouvelle);
    }

    /** Copie sans l'issue de ce jalon (comme s'il n'était pas encore passé). */
    public EtatMonde sansIssue(String jalon) {
        Map<String, Issue> copie = new HashMap<>(jalons);
        copie.remove(jalon);
        return new EtatMonde(schemaVersion, epoque, copie, zones, horloge);
    }

    /** Copie où cette zone n'est contrôlée par aucun camp. */
    public EtatMonde sansZone(String zone) {
        Map<String, Camp> copie = new HashMap<>(zones);
        copie.remove(zone);
        return new EtatMonde(schemaVersion, epoque, jalons, copie, horloge);
    }
}
