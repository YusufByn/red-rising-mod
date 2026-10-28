package io.github.yusufbyn.redrising.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.jalons.EffetEnCours;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Horloge;
import io.github.yusufbyn.redrising.jalons.Issue;
import io.github.yusufbyn.redrising.jalons.MortPersonnage;
import io.github.yusufbyn.redrising.jalons.Versement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * État du monde sauvegardé (G2.4) : époque, issue de chaque jalon passé, camp qui contrôle chaque zone, horloge du monde
 * (temps de jeu et Soutien Rising cumulé, G6.1), jalons en route et effets de jalon pas encore appliqués (G6.2), effets
 * en cours et versements des jalons (G6.3), morts des personnages canon (G6.4).
 *
 * <p>Le format est versionné ({@code schemaVersion}) pour que les mondes v1 restent lisibles en v2 (GDD section 10).
 * Règle : toute modification du format sauvegardé incrémente {@link #VERSION_SCHEMA} et ajoute une étape
 * dans {@link #migrer}. Une instance en mémoire est toujours au format actuel.</p>
 *
 * @param jalons  issue de chaque jalon passé, par identifiant de jalon (ex. {@code "pendaison_eo"})
 * @param zones   camp qui contrôle chaque zone, par identifiant de zone
 * @param horloge temps de jeu, présence et Soutien Rising cumulé (G6.1, schéma v2 ; présence en v3)
 * @param enRoute jalons en route : l'horloge au moment où chacun s'est mis en route (G6.2, schéma v3)
 * @param effetsEnAttente jalons passés dont les effets (G6.3) ne sont pas encore appliqués : l'événement repart au
 *                        chargement (G6.2, schéma v3). Un jalon passé absent de cet ensemble a ses effets appliqués.
 * @param effets     leviers posés par les jalons, par identifiant d'effet (G6.3, schéma v4)
 * @param versements ce que les jalons donnent une fois à chaque joueur : jauges et nouvelles (G6.3, schéma v4)
 * @param morts      personnages canon morts, par identifiant de personnage (G6.4, schéma v5)
 */
public record EtatMonde(int schemaVersion, Epoque epoque, Map<String, Issue> jalons, Map<String, Camp> zones, Horloge horloge,
                        Map<String, Horloge> enRoute, Set<String> effetsEnAttente, Map<String, EffetEnCours> effets,
                        List<Versement> versements, Map<String, MortPersonnage> morts) {
    /**
     * Version actuelle du format.
     * <ul>
     *     <li>v1 (G2.4) : époque, jalons, zones.</li>
     *     <li>v2 (G6.1) : + horloge.</li>
     *     <li>v3 (G6.2) : + présence dans l'horloge, jalons en route, effets en attente.</li>
     *     <li>v4 (G6.3) : + effets en cours, versements.</li>
     *     <li>v5 (G6.4) : + morts des personnages canon.</li>
     * </ul>
     */
    public static final int VERSION_SCHEMA = 5;

    /** État d'un monde neuf : début du tome 1, aucun jalon passé ni en route, aucune zone attribuée, horloge à zéro. */
    public static final EtatMonde NOUVEAU = new EtatMonde(VERSION_SCHEMA, Epoque.RED_RISING, Map.of(), Map.of(), Horloge.ZERO, Map.of(), Set.of(), Map.of(), List.of(), Map.of());

    /** Format brut, tel qu'il est écrit sur le disque, sans migration. */
    private static final Codec<EtatMonde> CODEC_BRUT = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("schemaVersion").forGetter(EtatMonde::schemaVersion),
            Epoque.CODEC.fieldOf("epoque").forGetter(EtatMonde::epoque),
            Codec.unboundedMap(Codec.STRING, Issue.CODEC).optionalFieldOf("jalons", Map.of()).forGetter(EtatMonde::jalons),
            Codec.unboundedMap(Codec.STRING, Camp.CODEC).optionalFieldOf("zones", Map.of()).forGetter(EtatMonde::zones),
            // Absente d'un monde v1 : lue à zéro, puis la migration pose le numéro v2. Toujours écrite, même à zéro
            Horloge.CODEC.optionalFieldOf("horloge").xmap(lue -> lue.orElse(Horloge.ZERO), Optional::of).forGetter(EtatMonde::horloge),
            Codec.unboundedMap(Codec.STRING, Horloge.CODEC).optionalFieldOf("en_route", Map.of()).forGetter(EtatMonde::enRoute),
            // Écrit trié, pour un fichier stable
            Codec.STRING.listOf().<Set<String>>xmap(Set::copyOf, ensemble -> List.copyOf(new TreeSet<>(ensemble)))
                    .optionalFieldOf("effets_en_attente", Set.of()).forGetter(EtatMonde::effetsEnAttente),
            Codec.unboundedMap(Codec.STRING, EffetEnCours.CODEC).optionalFieldOf("effets", Map.of()).forGetter(EtatMonde::effets),
            Versement.CODEC.listOf().optionalFieldOf("versements", List.of()).forGetter(EtatMonde::versements),
            Codec.unboundedMap(Codec.STRING, MortPersonnage.CODEC).optionalFieldOf("morts", Map.of()).forGetter(EtatMonde::morts)
    ).apply(instance, EtatMonde::new));

    /** Codec de sauvegarde : à la lecture, le numéro de version est lu puis l'état est migré au format actuel. */
    public static final Codec<EtatMonde> CODEC = CODEC_BRUT.flatXmap(EtatMonde::migrer, DataResult::success);

    public EtatMonde {
        // Copies immuables : un record ne doit pas partager une Map modifiable
        jalons = Map.copyOf(jalons);
        zones = Map.copyOf(zones);
        enRoute = Map.copyOf(enRoute);
        effetsEnAttente = Set.copyOf(effetsEnAttente);
        effets = Map.copyOf(effets);
        versements = List.copyOf(versements);
        morts = Map.copyOf(morts);
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
        if (etat.schemaVersion() == 2) {
            etat = migrerV2VersV3(etat);
        }
        if (etat.schemaVersion() == 3) {
            etat = migrerV3VersV4(etat);
        }
        if (etat.schemaVersion() == 4) {
            etat = migrerV4VersV5(etat);
        }
        return DataResult.success(etat);
    }

    /**
     * v1 → v2 (G6.1) : ajoute l'horloge du monde, à zéro. Un monde v1 commence à compter son temps de jeu et son Soutien
     * cumulé à la mise à jour (aucun monde n'avait été publié : pas besoin d'estimer le passé).
     */
    private static EtatMonde migrerV1VersV2(EtatMonde v1) {
        return new EtatMonde(2, v1.epoque(), v1.jalons(), v1.zones(), Horloge.ZERO, Map.of(), Set.of(), Map.of(), List.of(), Map.of());
    }

    /**
     * v2 → v3 (G6.2) : la présence vaut le temps de jeu (comme si on avait joué à un seul joueur) ; aucun jalon en route
     * (ils se mettent en route au premier passage du moteur) ; les jalons déjà passés ont leurs effets appliqués
     * (il n'y avait pas encore d'effets).
     */
    private static EtatMonde migrerV2VersV3(EtatMonde v2) {
        return new EtatMonde(3, v2.epoque(), v2.jalons(), v2.zones(), v2.horloge().avecPresence(v2.horloge().temps()), Map.of(), Set.of(), Map.of(), List.of(), Map.of());
    }

    /** v3 → v4 (G6.3) : aucun effet en cours ni versement (aucun jalon n'avait d'effet). */
    private static EtatMonde migrerV3VersV4(EtatMonde v3) {
        return new EtatMonde(4, v3.epoque(), v3.jalons(), v3.zones(), v3.horloge(), v3.enRoute(), v3.effetsEnAttente(), Map.of(), List.of(), Map.of());
    }

    /** v4 → v5 (G6.4) : aucun personnage mort (il n'y en avait pas encore). */
    private static EtatMonde migrerV4VersV5(EtatMonde v4) {
        return new EtatMonde(5, v4.epoque(), v4.jalons(), v4.zones(), v4.horloge(), v4.enRoute(), v4.effetsEnAttente(), v4.effets(),
                v4.versements(), Map.of());
    }

    public EtatMonde avecEpoque(Epoque nouvelle) {
        return new EtatMonde(schemaVersion, nouvelle, jalons, zones, horloge, enRoute, effetsEnAttente, effets, versements, morts);
    }

    public EtatMonde avecIssue(String jalon, Issue issue) {
        Map<String, Issue> copie = new HashMap<>(jalons);
        copie.put(jalon, issue);
        return new EtatMonde(schemaVersion, epoque, copie, zones, horloge, enRoute, effetsEnAttente, effets, versements, morts);
    }

    public EtatMonde avecZone(String zone, Camp camp) {
        Map<String, Camp> copie = new HashMap<>(zones);
        copie.put(zone, camp);
        return new EtatMonde(schemaVersion, epoque, jalons, copie, horloge, enRoute, effetsEnAttente, effets, versements, morts);
    }

    public EtatMonde avecHorloge(Horloge nouvelle) {
        return new EtatMonde(schemaVersion, epoque, jalons, zones, nouvelle, enRoute, effetsEnAttente, effets, versements, morts);
    }

    /** Ce jalon se met en route : on garde l'horloge de ce moment, d'où se mesure son échéance. */
    public EtatMonde avecMiseEnRoute(String jalon, Horloge depart) {
        Map<String, Horloge> copie = new HashMap<>(enRoute);
        copie.put(jalon, depart);
        return new EtatMonde(schemaVersion, epoque, jalons, zones, horloge, copie, effetsEnAttente, effets, versements, morts);
    }

    public EtatMonde sansMiseEnRoute(String jalon) {
        Map<String, Horloge> copie = new HashMap<>(enRoute);
        copie.remove(jalon);
        return new EtatMonde(schemaVersion, epoque, jalons, zones, horloge, copie, effetsEnAttente, effets, versements, morts);
    }

    /** Les effets de ce jalon passé restent à appliquer (ou, {@code false}, sont appliqués). */
    public EtatMonde avecEffetsEnAttente(String jalon, boolean enAttente) {
        Set<String> copie = new HashSet<>(effetsEnAttente);
        if (enAttente) {
            copie.add(jalon);
        } else {
            copie.remove(jalon);
        }
        return new EtatMonde(schemaVersion, epoque, jalons, zones, horloge, enRoute, copie, effets, versements, morts);
    }

    /** Pose (ou remplace) cet effet. */
    public EtatMonde avecEffet(String id, EffetEnCours effet) {
        Map<String, EffetEnCours> copie = new HashMap<>(effets);
        copie.put(id, effet);
        return new EtatMonde(schemaVersion, epoque, jalons, zones, horloge, enRoute, effetsEnAttente, copie, versements, morts);
    }

    public EtatMonde sansEffet(String id) {
        Map<String, EffetEnCours> copie = new HashMap<>(effets);
        copie.remove(id);
        return new EtatMonde(schemaVersion, epoque, jalons, zones, horloge, enRoute, effetsEnAttente, copie, versements, morts);
    }

    /** Ajoute ce versement, s'il n'y en a pas déjà un de même identifiant (un rejeu ne double rien). */
    public EtatMonde avecVersement(Versement versement) {
        if (versements.stream().anyMatch(existant -> existant.id().equals(versement.id()))) {
            return this;
        }
        List<Versement> copie = new ArrayList<>(versements);
        copie.add(versement);
        return new EtatMonde(schemaVersion, epoque, jalons, zones, horloge, enRoute, effetsEnAttente, effets, copie, morts);
    }

    /** La mort de ce personnage (la première compte : une mort enregistrée n'est pas remplacée). */
    public EtatMonde avecMort(String personnage, MortPersonnage mort) {
        if (morts.containsKey(personnage)) {
            return this;
        }
        Map<String, MortPersonnage> copie = new HashMap<>(morts);
        copie.put(personnage, mort);
        return new EtatMonde(schemaVersion, epoque, jalons, zones, horloge, enRoute, effetsEnAttente, effets, versements, copie);
    }

    /** Oublie la mort de ce personnage (debug) : il peut de nouveau être incarné. */
    public EtatMonde sansMort(String personnage) {
        Map<String, MortPersonnage> copie = new HashMap<>(morts);
        copie.remove(personnage);
        return new EtatMonde(schemaVersion, epoque, jalons, zones, horloge, enRoute, effetsEnAttente, effets, versements, copie);
    }

    /** Copie sans l'issue de ce jalon (comme s'il n'était pas encore passé) : ni effets en attente, ni mise en route. */
    public EtatMonde sansIssue(String jalon) {
        Map<String, Issue> copie = new HashMap<>(jalons);
        copie.remove(jalon);
        return new EtatMonde(schemaVersion, epoque, copie, zones, horloge, enRoute, effetsEnAttente, effets, versements, morts)
                .avecEffetsEnAttente(jalon, false).sansMiseEnRoute(jalon);
    }

    /** Copie où cette zone n'est contrôlée par aucun camp. */
    public EtatMonde sansZone(String zone) {
        Map<String, Camp> copie = new HashMap<>(zones);
        copie.remove(zone);
        return new EtatMonde(schemaVersion, epoque, jalons, copie, horloge, enRoute, effetsEnAttente, effets, versements, morts);
    }
}
