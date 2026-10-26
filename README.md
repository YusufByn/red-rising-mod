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
| `commande` | Commandes de debug `/rr` |
| `mixin` | Code injecté dans Minecraft quand Fabric API n'a pas d'événement |

## Création du monde

- Onglet Jeu > « Naissance » : Test (par défaut, test de personnalité caché) ou Choix libre (menu). Règle de jeu `redrising:mode_naissance`.
- En choix libre, un joueur sans Couleur choisit la sienne à son arrivée dans le monde (écran impossible à fermer sans choisir). Le choix est définitif ; seul `/rr couleur` peut la changer ensuite.
- En mode Test, en attendant le test de naissance (G4.3, G4.4), la Couleur est tirée au hasard.

## Contrôles

- `R` (modifiable, catégorie « Red Rising ») : capacité active de sa Couleur.
- Options > Personnalisation du skin > « Cheveux de Couleur » : affiche ou non les cheveux teintés (réglage client, `config/redrising-client.properties`).

## Commandes de debug

Réservées aux ops (ou à la triche activée en solo). `[joueur]` est optionnel : sans lui, la commande vise celui qui la tape.

- `/rr couleur get [joueur]`, `/rr couleur set <couleur> [joueur]`
- `/rr jauge get [joueur]`, `/rr jauge set <jauge> <valeur> [joueur]`, `/rr jauge add <jauge> <delta> [joueur]`
- `/rr jalon list`, `/rr jalon set <jalon> <issue>`, `/rr jalon clear <jalon>`
- `/rr etat`, `/rr etat naissance <test|choix_libre>`, `/rr etat epoque <epoque>`, `/rr etat zone set <zone> <camp>`, `/rr etat zone clear <zone>`

## Outils

- `outils/marque_couleur.py` : régénère les textures de l'apparence par Couleur (mains, yeux, cheveux).
- `outils/planche_couleurs.py` : après `./gradlew runClientGameTest`, assemble les captures des 14 Couleurs en une planche (`build/captures-gametest/planche-couleurs.png`).
- Les deux demandent Pillow (`pip3 install pillow`).

## Tests

- `./gradlew build` : compile et lance les tests unitaires (`src/test`).
- `./gradlew runClientGameTest` : ouvre un vrai client et joue les tests en jeu (`src/gametest`), puis se ferme. Les captures d'écran prises par les tests sont copiées dans `build/captures-gametest/`.

## Conventions

- Code et identifiants en anglais technique ou français selon le domaine du lore (Couleur, jauge, jalon), commentaires en français.
- Identifiant du mod : `redrising`. Toute clé de traduction existe en `fr_fr` et `en_us`.
