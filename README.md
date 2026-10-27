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
- En mode Test, un joueur sans Couleur passe le test de naissance à son arrivée (7 questions), dans la scène de naissance (dimension `redrising:scene_naissance`), puis accepte son résultat ou refait le test une fois. Une fois né, il revient à son point d'apparition.

## Contrôles

- `R` (modifiable, catégorie « Red Rising ») : capacité active de sa Couleur.
- Accroupi + clic droit sur un PNJ de Couleur avec de la nourriture : l'aider. À mains vides sur un PNJ Gris : dénoncer le PNJ du Rising le plus proche.
- Accroupi + clic droit à main vide sur l'entrée cachée d'une cellule du Rising (repérée par des particules rouges, visibles à partir de 20 de Soutien) : entrer, puis sortir de la même façon.
- Options > Personnalisation du skin > « Cheveux de Couleur » : affiche ou non les cheveux teintés (réglage client, `config/redrising-client.properties`).

## Commandes de debug

Réservées aux ops (ou à la triche activée en solo). `[joueur]` est optionnel : sans lui, la commande vise celui qui la tape.

- `/rr couleur get [joueur]`, `/rr couleur set <couleur> [joueur]`
- `/rr jauge get [joueur]`, `/rr jauge set <jauge> <valeur> [joueur]`, `/rr jauge add <jauge> <delta> [joueur]`
- `/rr jalon list`, `/rr jalon set <jalon> <issue>`, `/rr jalon clear <jalon>`
- `/rr pnj spawn <couleur>` (un PNJ de cette Couleur à ta position), `/rr pnj get <pnj>`, `/rr pnj set <pnj> <couleur>`
- `/rr pnj camp <pnj> <societe|rising|aucun>` (camp secret d'un PNJ), `/rr coffre get|set|clear <pos> [camp]` (coffre ou tonneau d'un camp)
- `/rr patrouille creer <pos1> <pos2> [pos3] [pos4]` (un PNJ Gris qui fait la ronde entre ces points), `/rr patrouille get <pnj>`, `/rr patrouille clear <pnj>`
- `/rr controle get [joueur]` (dernier contrôle subi, dernier jour où un contrôle obéi a fait baisser la Suspicion), `/rr controle reset [joueur]`
- `/rr traque get [joueur]`, `/rr traque start [joueur]` (Suspicion au maximum et traque tout de suite), `/rr traque stop [joueur]` (fin de la traque ou de l'interrogatoire, Suspicion à 90), `/rr traque arreter [joueur]` (arrestation et interrogatoire tout de suite)
- `/rr structure poste <pos>` (poste de la Société, coin nord-ouest du sol, porte au nord), `/rr structure cellule <pos> <north|south|east|west>` (cellule du Rising : moitié basse de l'entrée cachée, salle dans la direction donnée), `/rr structure list`, `/rr structure oublier <pos>` (poste ou cellule)
- `/rr naissance get [joueur]` (Couleur, ou test en cours : résultat en attente, relance), `/rr naissance reset [joueur]` (efface Couleur et test, et relance la naissance)
- `/rr etat`, `/rr etat naissance <test|choix_libre>`, `/rr etat epoque <epoque>`, `/rr etat zone set <zone> <camp>`, `/rr etat zone clear <zone>`

## Outils

- `outils/marque_couleur.py` : régénère les textures de l'apparence par Couleur (mains, yeux, cheveux), pour les joueurs et les PNJ.
- `outils/planche_couleurs.py` : après `./gradlew runClientGameTest`, assemble les captures des 14 Couleurs en une planche (`build/captures-gametest/planche-couleurs.png`).
- Les deux demandent Pillow (`pip3 install pillow`).

## Tests

- `./gradlew build` : compile et lance les tests unitaires (`src/test`), dont la distribution du test de naissance (`TestPersonnaliteTest`, à surveiller après toute modification d'une paire de Couleurs).
- `./gradlew runClientGameTest` : ouvre un vrai client et joue les tests en jeu (`src/gametest`), puis se ferme. Les captures d'écran prises par les tests sont copiées dans `build/captures-gametest/`.

## Conventions

- Code et identifiants en anglais technique ou français selon le domaine du lore (Couleur, jauge, jalon), commentaires en français.
- Identifiant du mod : `redrising`. Toute clé de traduction existe en `fr_fr` et `en_us`.
