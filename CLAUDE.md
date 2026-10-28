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
- PNJ de Couleur (G5.1) : villageois vanilla + `CouleurJoueur.COULEUR` (l'attachment marche sur toute entité). Passer par `PnjCouleur.donnerCouleur` : il pose aussi le métier et le verrouille (un peu d'expérience), sinon le villageois le perd faute de poste de travail. « Sans métier » = `NITWIT` : un villageois `NONE` prend le métier du premier poste de travail à moins de 2 blocs.
- Rendu d'une entité dont l'état de rendu ne garde pas l'entité (villageois...) : recopier la donnée voulue dans l'état avec un `RenderStateDataKey` de Fabric (`setData`), depuis un mixin sur `extractRenderState` (voir `VillagerRendererMixin`).
- Tests client : le serveur avance au même pas que le client, `/tick sprint` n'accélère rien. Pour sauter du temps, avancer l'horloge du monde (`((ServerLevelData) level.getLevelData()).setGameTime(...)`, voir `ActionsFactionTest`).
- Actions sur les jauges (G5.2) : toutes les valeurs dans `factions/EffetsActions` (logique pure, testée), les déclencheurs dans `factions/ActionsFaction`. Nouvelle action = une méthode dans l'un, un événement dans l'autre.
- Seuils d'accès aux structures : tous dans `factions/Acces` (un seul endroit, valeurs en 🟡 dans le GDD).
- Refuser l'ouverture d'une porte côté serveur (`UseBlockCallback` → `FAIL`) : le client l'a déjà ouverte de son côté, lui renvoyer l'état des deux moitiés (`ClientboundBlockUpdatePacket`, voir `PosteSociete`).
- Construire une structure par le code : poser les blocs avec `Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE`, sinon une porte posée moitié par moitié se casse.
- Commandes `/rr ... [joueur]` : l'argument vise un seul joueur. Depuis la console ou un test, `@a` est refusé : écrire `@a[limit=1]` ou le nom.
- Information secrète (cellules du Rising...) : le client ne la connaît pas. Un geste secret laisse passer côté client (`PASS`) et le serveur décide ; un refus doit rester `PASS` côté serveur aussi, pour que rien ne le trahisse. Un effet réservé à certains joueurs (repère) : `ServerLevel.sendParticles(ServerPlayer, ...)`, envoyé à ce joueur seulement (voir `CelluleRising`).
- Téléporter un joueur depuis un clic sur un bloc (`UseBlockCallback`) : le faire en fin de tick, pas dans l'événement. Le client envoie aussi le clic de la main secondaire ; s'il arrive après la téléportation, Minecraft le refuse avec le message rouge « limite de hauteur de construction » (voir `CelluleRising.PASSAGES`).
- Coffres d'un camp : les entonnoirs (posés ou sur wagonnet) ne les vident pas (`HopperBlockEntityMixin`, règle dans `ActionsFaction.protegeDesEntonnoirs`, coffres doubles compris). Le mixin renvoie un contenant vide, pas `null` : sur `null`, l'API de transfert de Fabric cherche le coffre par un autre chemin et le vide quand même.
- Tests client : lire un champ privé du client (message au-dessus de la barre d'objets...) par réflexion, dans le test seulement (noms officiels en 26.2), voir `CelluleRisingTest`.
- Témoins (G5.5) : une action qui dépend de qui l'a vue passe par `ActionsFaction.agirDevantTemoins` (témoin calculé, puis `EffetsActions.selonTemoins`). Les tests qui ne portent pas sur les témoins attendent `selonTemoins(..., Temoin.AUCUN)` et retirent les PNJ qui verraient le joueur.
- Faire marcher un villageois (patrouille) : donner sa destination au cerveau (`brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(...))`) toutes les demi-secondes ; ses comportements libres ne la remplacent que si elle est absente. Les PNJ en patrouille chargés sont suivis par `ServerEntityEvents.ENTITY_LOAD`/`ENTITY_UNLOAD` (voir `PatrouillePnj`).
- `ServerPlayConnectionEvents.DISCONNECT` peut tourner sur le fil réseau (`Netty Local IO` en solo), pas sur celui du serveur : n'y modifier ni jauges ni attachments. Pour punir une déconnexion, poser un attachment persistant pendant l'action et le traiter à `JOIN` (voir `PatrouillePnj.CONTROLE_EN_COURS`).
- `ServerLivingEntityEvents.ALLOW_DAMAGE` est appelé après le test d'invulnérabilité : rien pour un joueur en créatif. Tests de dégâts : `gamemode survival`, et l'effet Résistance V pour ne pas mourir (l'événement est appelé avant la réduction des dégâts). Voir `TraqueTest`.
- PNJ qui ne doivent pas fuir quand on les frappe (chasseurs) : effacer les mémoires `HURT_BY` et `HURT_BY_ENTITY` de leur cerveau à chaque tick, sinon les villageois paniquent.
- Objets, blocs, entités (G5.8) : inscrits dans les registres au démarrage (`Registry.register(BuiltInRegistries.X, cle, ...)`), avec `Properties.setId(cle)` obligatoire en 26.2, `useBlockDescriptionPrefix()` pour l'objet d'un bloc (Fabric remplit `Item.BY_BLOCK` tout seul). Voir `factions/ObjetsRebelles`. Rendu d'une entité : `EntityRenderers.register` de Minecraft (celui de Fabric est déprécié). Un bloc à texture en partie transparente est dessiné en « cutout » sans rien déclarer.
- Savoir qu'un bloc du mod a disparu, quelle qu'en soit la cause (casse, eau, explosion, piston, sol retiré, commande) : surcharger `affectNeighborsAfterRemoval` dans le bloc (voir `ChausseTrappe`). Pour agir avant la casse par un joueur (savoir qui casse) : `playerWillDestroy`.
- Composant d'objet du mod (marque « volé », G5.9) : `Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ...)` avec `persistent(codec)` et `networkSynchronized(ByteBufCodecs.fromCodec(codec).cast())` pour que le client l'affiche.
- Boutique d'un villageois : l'ouvrir soi-même depuis `UseEntityCallback` côté serveur (`setOffers`, `setTradingPlayer`, `openTradingScreen`) et renvoyer `SUCCESS` : ça court-circuite les remises de Minecraft. Pour compter un échange ou refuser un paiement, sous-classer `MerchantOffer` (`increaseUses`, `satisfiedBy`), voir `OffreBoutique`. Le client ne reçoit que des offres ordinaires.
- Tests client : `tp ... facing entity <uuid> eyes` peut laisser le joueur regarder ailleurs ; calculer lacet et tangage soi-même (voir `BoutiqueTest.regard`).
- Contenu d'un contenant détruit (toutes causes) : il tombe dans `BlockEntity.preRemoveSideEffects`, juste avant le retrait du bloc (voir `BlockEntityMixin`). Blocs qu'une explosion va détruire, avec son responsable (`getIndirectSourceEntity`) : `ServerExplosion.interactWithBlocks` (voir `ServerExplosionMixin`).
- Pas de `getDayTime` sur `ServerLevel` en 26.2 (le temps passe par des « timelines ») : dans les tests, fixer l'heure par `/time set noon`.
- Conclure qu'un PNJ a disparu (Fils d'une cellule...) : seulement si ses entités sont chargées (`ServerLevel.isPositionEntityTicking`), sinon on ne le voit pas alors qu'il est là.
- Ce qu'un PNJ voit : toujours `monde/Vue.voit(pnj, joueur)` (murs et fumée), jamais `hasLineOfSight` directement.
- Tests unitaires : une classe qui charge `Level` (`Level.RESOURCE_KEY_CODEC`, `Level.OVERWORLD`) plante sans Minecraft démarré. Pour un codec de dimension : `ResourceKey.codec(Registries.DIMENSION)`.
- Tests client de traque : un chasseur téléporté loin sort des chunks chargés, il est oublié et la traque en fait apparaître un autre. Pour en garder un seul, tuer les autres (un chasseur tué n'est remplacé que par les renforts).
- Horloge du monde (G6.1) : le temps de jeu du mod (`jalons/HorlogeMonde`) n'est pas `gameTime`. Les règles « une fois par jour » restent sur les jours de `gameTime` ; avancer `gameTime` dans un test ne fait pas avancer le temps de jeu (`/rr etat temps add`). Le Soutien cumulé se compte dans `ActionsFaction.agir` : toute action qui bouge le Soutien doit passer par là. Comparer un `EtatMonde` dans un test : neutraliser l'horloge (`avecHorloge(Horloge.ZERO)`), elle avance dès qu'un joueur est là.
- Jalons (G6.2) : se déclarent avec `Jalons.declarer(Jalon.builder(...)...build())`. Un jalon de test se déclare dans le test et se retire à la fin (`Jalons.retirer`), sinon il reste dans les mondes des tests suivants. Pour faire passer du temps de jeu dans un test : `HorlogeMonde.modifier(server, h -> h.avancer(ticks, 1))`. Effets d'un jalon (G6.3) : déclarés avec le jalon (`.effets(issue, ...)`), posés par `jalons/EffetsMonde` ; un nouveau levier = une valeur de `Levier` et la lecture de son niveau (`EffetsMonde.niveau`) dans le code concerné. Textes des jalons de test : dans `src/gametest/resources/assets/redrising-gametest/lang`.
- Une action vue par des témoins (`ActionsFaction.agirDevantTemoins`) dit si elle est contre la Société : une Couleur qui se tait (G6.3) n'en témoigne pas.
- Punir un joueur hors ligne (on ne peut rien attacher à un joueur absent) : attachment global joueur → peine, appliqué à `JOIN` (voir `CelluleRising.PEINES_EN_ATTENTE`). En solo, le serveur s'arrête avec le joueur : le test pose la peine directement.
- Durées longues (traque, interrogatoire) : compter le temps de jeu écoulé entre deux passages (`getGameTime`), joueur connecté seulement ; un test peut alors avancer l'horloge du monde au lieu d'attendre.

## CI

`.github/workflows/build.yml` : build à chaque push, le .jar sort en artefact `red-rising-jar`. Après un push, vérifier que la CI reste verte.
