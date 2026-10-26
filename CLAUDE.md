# Red Rising (mod Minecraft)

Mod Fabric fan-made, solo, gratuit et non commercial, dans l'univers Red Rising de Pierce Brown. RPG sandbox : le joueur naît avec une des 14 Couleurs et avance selon elle. Auteur : Yusuf (GitHub YusufByn), qui code surtout en Python/JS : explique les concepts Java/Fabric quand ils sont nouveaux.

## Où est quoi

- `docs/gdd.md` : game design document. Référence du **contenu** (Couleurs, jauges, factions, jalons...). Légende : ✅ décidé, 🟡 proposé, ❓ à trancher.
- `docs/roadmap.md` : ordre de **construction**, grands tickets G1 à G18 découpés en sous-tickets (G2.1, G2.2...), chacun avec un critère « fini quand ».
- Lis les deux avant de commencer un ticket.

## Stack (décidée, ne pas changer sans demander)

- Minecraft **26.2** (non obfusqué : noms officiels Mojang, pas de Yarn, pas de `mappings`, `implementation` et pas `modImplementation`, `jar` et pas `remapJar`).
- Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Loom `net.fabricmc.fabric-loom` 1.18, **Gradle 9.8.2** (Loom 1.18 exige Gradle 9.7+), Java 25.
- **Fabric pur, pas d'Origins.** Le système des Couleurs est écrit dans le mod (G3).
- Versions gelées jusqu'à la v1.
- Mod id `redrising`, package `io.github.yusufbyn.redrising`. Code commun dans `src/main`, code client (HUD, écrans, rendu) dans `src/client`.
- Packages : `couleur`, `factions`, `monde`, `pnj`, `jalons`, `net`, `data`, `commande` (rôles dans le README).

## Méthode de travail

1. Un sous-ticket à la fois, dans l'ordre de la roadmap. Annonce lequel tu attaques.
2. Tickets `[DÉCISION]` : ne tranche pas seul. Propose une option argumentée et attends la réponse de Yusuf, puis note la décision dans `docs/gdd.md`.
3. Avant d'écrire du code qui touche une API Minecraft ou Fabric, vérifie la signature dans les sources réelles (sources générées par Loom, ou les jars dans le cache Gradle). 26.2 est récent : ne te fie pas à la mémoire d'anciennes versions (1.20, 1.21), beaucoup de noms ont changé.
4. Un sous-ticket est fini seulement quand :
   - `./gradlew build` passe sans erreur ;
   - le critère « fini quand » est vérifié en jeu : de préférence par un test client automatisé dans `src/gametest` (`./gradlew runClientGameTest`, un vrai client s'ouvre, joue le scénario et se ferme ; non lancé par `build` ni par la CI), sinon `./gradlew runClient` puis lecture de `run/logs/latest.log` ;
   - la case est cochée dans `docs/roadmap.md` avec la date ;
   - un commit est fait : `G2.1 : <résumé court>`.
5. Tests unitaires (JUnit 5, `src/test`, lancés par `./gradlew build`) pour la logique pure : moteur du test de naissance, bornes des jauges, choix d'issue des jalons.
6. Commentaires et textes en français. Toute clé de traduction existe en `fr_fr` et `en_us`.

## Pistes techniques pour G2

- Données attachées au joueur (Couleur, jauges) : regarder **Fabric Data Attachment API** (`fabric-data-attachment-api-v1`), qui gère la persistance, la copie à la mort et la synchro client. Vérifier son API exacte en 26.2.
- État du monde (G2.4, fait) : `data/EtatMonde`, attachment global Fabric avec `schemaVersion`. Toute modification du format sauvegardé incrémente `EtatMonde.VERSION_SCHEMA` et ajoute une migration dans `EtatMonde.migrer`.
- Commandes (G2.5, fait) : Fabric Command API v2, tout sous `/rr` dans `commande/CommandesDebug`. Tout nouvel état du mod doit y être lisible et modifiable.

## Pièges déjà rencontrés

- **Réseau de l'école** : il bloque les serveurs Mojang et Fabric (minecraft.net, piston-meta.mojang.com, fabricmc.net). Symptôme : `Failed to setup Minecraft ... Failed download after 3 attempts`. Solution : partage de connexion, puis tout reste en cache.
  Loom rafraîchit le manifeste des versions (`~/.gradle/caches/fabric-loom/mojang_versions_manifest.json`) une fois par jour, et l'efface si le téléchargement échoue : même symptôme, alors que tout le reste est en cache. Sans partage de connexion : `./gradlew ... --offline` (Loom garde alors son cache, quel que soit son âge) ; si le fichier a déjà été effacé, le recréer avec l'entrée de la version 26.2 (sha1 de `~/.gradle/caches/fabric-loom/26.2/mojang_minecraft_info.json`).
- **Configuration cache Gradle activé** : dans `build.gradle`, ne pas lire `inputs` ou `project` à l'intérieur des closures exécutées au moment des tâches. Passer par une variable locale (voir `processResources`).
- Toujours passer par `./gradlew` (wrapper Gradle 9.8.2 committé), jamais un `gradle` système.
- Les erreurs `401` et `Realms` dans les logs de `runClient` sont normales (compte hors ligne en dev).
- `Minecraft.screen` n'existe plus en 26.2 : l'écran courant est `minecraft.gui.screen()`.
- Fabric recopie les attachments du joueur dans sa propre écoute de `ServerPlayerEvents.AFTER_RESPAWN` : pour lire une donnée du joueur à la réapparition, s'enregistrer dans une phase ordonnée après `Event.DEFAULT_PHASE` (voir `CouleurJoueur`).
- Nouvelle dépendance hors Minecraft/Fabric (ex. JUnit) : la déclarer dans le bloc `exclusiveContent` de `build.gradle` (Maven Central), sinon Gradle interroge d'abord `libraries.minecraft.net`, bloqué à l'école.
- Bonus d'attributs : modificateurs **permanents** (`addOrReplacePermanentModifier`), pas transitoires. Un modificateur transitoire n'est pas sauvegardé : au rechargement, la vie est relue avant qu'il soit remis, et un bonus de vie est perdu.
- Capacité active : une seule par Couleur pour l'instant (`.capacite(recharge, action)`), déclenchée par la touche R ; le serveur décide de tout (Couleur, recharge). L'action renvoie `false` si elle ne s'applique pas, et la recharge ne démarre pas.
- Faiblesses : permanentes = valeurs négatives dans `.attribut(...)` ; liées à l'environnement = `.si(Condition.X, malus -> ...)`. `Condition.SOUS_TERRE` et `DANS_L_EAU` existent mais n'ont pas encore de test en jeu.
- Pas de `Level.canSeeSky` en 26.2 : passer par la heightmap (`level.getHeight(Heightmap.Types..., x, z)`), voir `Condition.A_CIEL_OUVERT`.
- Capacités d'une Couleur : se déclarent avec `ProfilsCouleurs.declarer(Couleur, ProfilCouleur.builder()...build())`, au même endroit pour chaque Couleur, dans son ticket.
- Mixins : dans le package `mixin`, déclarés dans `src/main/resources/redrising.mixins.json`. Seulement quand Fabric API n'a pas d'événement ; vérifier le bytecode de la méthode visée (`javap -c`) avant d'écrire l'injection.
- Refus d'un clic (`UseBlockCallback`, `UseItemCallback`) : quand le client refuse, il n'envoie rien au serveur. Tout retour au joueur (message) doit donc être fait aussi côté client : `player.sendOverlayMessage` marche des deux côtés.
- Rendu du joueur : la peau est dessinée en **translucide** (après tout le « cutout »). Une couche ajoutée par-dessus doit donc être translucide elle aussi, sinon la couche extérieure du skin (manches, veste) la recouvre.
- Commandes : une coordonnée entière (`summon ... 7 -60 9`) est centrée sur le bloc (7,5). Écrire `7.0` pour une position exacte.
- Mixins client : dans `src/client/.../client/mixin`, déclarés dans `src/client/resources/redrising.client.mixins.json` (environnement client).
- Tests client qui passent par les vrais écrans (création de monde, ouverture) : pour quitter un monde, refaire comme Fabric (`level.disconnect`, `mc.disconnect(GenericMessageScreen, false)`, attendre `getSingleplayerServer() == null`, puis écran titre). `disconnectWithSavingScreen` ne ramène pas à l'écran titre. Les listes déroulantes d'options ne sont pas parcourues par `clickScreenButton` : cliquer à la souris (voir `ApparenceCouleurTest`).
- Tests client : juste après l'entrée dans le monde, `/kill` n'a pas d'effet. Attendre une centaine de ticks avant.
- Tests client : créer le monde avec `OutilsTest.nouveauMonde(context)` (joueur déjà né, Couleur Or). Avec `context.worldBuilder().create()`, l'écran du test de naissance s'ouvre et met le jeu en pause : à réserver aux tests de la naissance.
- Dimensions en 26.2 : le format JSON de `dimension_type` a changé (`attributes` pour le ciel, le brouillard, les lits..., `skybox`, `timelines`). Partir des fichiers vanilla du jar (`data/minecraft/dimension_type/*.json`), voir `data/redrising/dimension_type/scene_naissance.json`.
- Tests client : pendant le test de naissance le jeu est en pause, et `waitForChunksRender` peut ne jamais aboutir. Attendre quelques ticks à la place.
- Donner une Couleur sans passer par la naissance (debug, tests) : `Naissance.donnerCouleur`, qui abandonne le test en cours et sort le joueur de la scène. `CouleurJoueur.set` seul le laisserait dans la scène.
- PNJ de Couleur (G5.1) : villageois vanilla + `CouleurJoueur.COULEUR` (l'attachment marche sur toute entité). Passer par `PnjCouleur.donnerCouleur` : il pose aussi le métier et le verrouille (un peu d'expérience), sinon le villageois le perd faute de poste de travail.
- Rendu d'une entité dont l'état de rendu ne garde pas l'entité (villageois...) : recopier la donnée voulue dans l'état avec un `RenderStateDataKey` de Fabric (`setData`), depuis un mixin sur `extractRenderState` (voir `VillagerRendererMixin`).
- Commandes `/rr ... [joueur]` : l'argument vise un seul joueur. Depuis la console ou un test, `@a` est refusé : écrire `@a[limit=1]` ou le nom.

## CI

`.github/workflows/build.yml` : build à chaque push, le .jar sort en artefact `red-rising-jar`. Après un push, vérifier que la CI reste verte.
