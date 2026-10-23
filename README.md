# Red Rising (mod Minecraft)

Mod Fabric fan-made, non commercial, dans l'univers Red Rising de Pierce Brown.

- Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Java 25
- Référence du contenu : GDD du projet Claude. Ordre de construction : roadmap G1 à G18.

## Lancer en dev

1. Ouvrir ce dossier dans IntelliJ IDEA 2025.3 ou plus (File > Open).
2. Laisser Gradle synchroniser (le JDK 25 est téléchargé automatiquement si besoin).
3. Tâche Gradle `runClient` (onglet Gradle > Tasks > fabric > runClient).

## Structure

Code commun dans `src/main`, code client uniquement (HUD, écrans, rendu) dans `src/client`.

| Package | Rôle |
|---|---|
| `couleur` | Couleurs du joueur, capacités, restrictions, faiblesses |
| `factions` | Jauges Loyauté, Soutien, Suspicion, structures, traque |
| `monde` | Génération de Mars, régions, dimensions |
| `pnj` | PNJ par Couleur, PNJ canon, Roses |
| `jalons` | Horloge du monde, jalons canon ou divergents |
| `net` | Payloads réseau serveur vers client |
| `data` | Sauvegarde versionnée de l'état du monde |

## Conventions

- Code et identifiants en anglais technique ou français selon le domaine du lore (Couleur, jauge, jalon), commentaires en français.
- Identifiant du mod : `redrising`. Toute clé de traduction existe en `fr_fr` et `en_us`.
