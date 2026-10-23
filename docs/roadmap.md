# Mod Red Rising : roadmap v1

Copie de travail dans le repo. Cocher ici les sous-tickets finis (avec la date). Une version doc existe aussi côté Claude : https://claude.ai/artifact/Wa717uX5qxh8k649p6KRvy

## Mode d'emploi

18 grands tickets (G1 à G18) mènent à la v1. Chaque grand ticket est découpé en sous-tickets (G1.0, G1.1...), chacun avec son critère « fini quand ».

- **Ordre** : on avance dans l'ordre des G. Un G dépend des précédents, pas des suivants.
- **Tickets [DÉCISION]** : les ❓ du GDD, placés juste avant le ticket qu'ils bloquent. On les tranche à ce moment-là, pas avant.
- **Prototype privé** (GDD section 11) : c'est G1 à G7. Une fois G7 fini, on joue un Rouge dans les mines jusqu'à la pendaison d'Eo. Si ce n'est pas fun, on corrige avant d'aller plus loin.
- **Couleur par Couleur** : à partir de G7, chaque région arrive avec les Couleurs qui y naissent. Chaque Couleur se termine par un sous-ticket « test » (force, exclusivités, faiblesse vérifiées en jeu) et une version de test perso.
- Le GDD reste la référence du contenu. Ce doc dit seulement dans quel ordre on le construit.

## Socle : G1 à G6

Rien de visible pour le joueur ici, mais tout le reste se branche dessus.

### G1 Setup technique

- [x] **G1.0 [DÉCISION] Base technique** : Fabric pur ou + Origins, version de Minecraft. Décidé le 23/10, sans Origins : Fabric pur, 26.2, version gelée jusqu'à la v1. *Fini quand* : choix noté dans le GDD.
- [x] **G1.1 Environnement** : Java 25, IntelliJ 2025.3+, template Fabric, Gradle 9.8.2 (Loom 1.18 exige 9.7+). Fait le 23/10 : Minecraft 26.2 se lance avec le mod chargé. *Fini quand* : le mod vide se lance en client dev.
- [x] **G1.2 Structure du code** : packages (couleur, factions, monde, pnj, jalons, net, data), conventions de nommage, fichier de traduction FR/EN. *Fini quand* : arborescence en place et documentée dans le README.
- [x] **G1.3 Build et CI** : repo GitHub privé YusufByn/red-rising-mod, GitHub Actions qui produit le .jar à chaque push. Fait le 23/10 : premier build vert en 1 min 25. *Fini quand* : un .jar téléchargeable sort de la CI.

### G2 Socle données

- [x] **G2.1 Couleur du joueur** : les 14 Couleurs, attachées au joueur, persistantes. Fait le 23/10 : attachment Fabric persistant et copié à la mort, vérifié par le test en jeu `CouleurPersistanceTest` (Couleur provisoire au hasard en attendant G4). *Fini quand* : la Couleur survit à la mort et au reload.
- [x] **G2.2 Les 3 jauges** : Loyauté Société, Soutien Rising, Suspicion, avec bornes et plafonds par Couleur (ex. Loyauté plafonnée pour le Rouge). Fait le 23/10 : jauges 0 à 100 attachées au joueur, plafond Rouge à 40 (valeurs validées dans le GDD), tests unitaires `JaugesTest` et test en jeu `JaugesPersistanceTest`. *Fini quand* : valeurs persistantes et bornées.
- [x] **G2.3 Sync réseau** : payloads serveur vers client pour Couleur et jauges. Fait le 23/10 : sync des attachments Fabric (Couleur vers tous les clients, jauges vers le joueur seul), vérifiée par le test en jeu `SyncClientTest`. *Fini quand* : le client affiche toujours la valeur du serveur.
- [x] **G2.4 Sauvegarde versionnée** : état du monde (issue de chaque jalon, zones contrôlées, époque) avec un numéro de version de schéma. Fait le 23/10 : `EtatMonde` au schéma v1 en attachment global, tests unitaires `EtatMondeTest` et test en jeu `EtatMondePersistanceTest` (version lue dans le fichier du monde). *Fini quand* : un monde se recharge et le numéro de version est lu.
- [x] **G2.5 Commandes debug** : `/rr couleur`, `/rr jauge`, `/rr jalon`, `/rr etat`. Fait le 23/10 : sous-commandes get/set/add/list/clear (liste dans le README), messages FR/EN, test en jeu `CommandesDebugTest`. *Fini quand* : tout l'état se lit et se modifie en jeu.
- [x] **G2.6 HUD des jauges** : affichage minimal, remplaçable plus tard. Fait le 23/10 : `client/HudJauges` en haut à gauche (Couleur, 3 barres, zone hors plafond grisée), vérifié par capture d'écran du test en jeu `HudJaugesTest`. *Fini quand* : les 3 jauges sont visibles en jeu.

### G3 Framework des Couleurs

Un système générique pour déclarer force, exclusivités et faiblesse. Les 14 Couleurs s'écriront ensuite avec, sans recoder la mécanique.

- [x] **G3.1 Capacités passives** : attributs (vitesse, dégâts, vie, minage), effets permanents. Fait le 23/10 : `ProfilCouleur` (builder) déclaré avec `ProfilsCouleurs.declarer`, appliqué par `CapacitesPassives` (connexion, réapparition, changement de Couleur, effets entretenus). Vérifié par `CapacitesPassivesTest` avec une Couleur de test déclarée dans le mod de test. *Fini quand* : une Couleur de test déclarée en quelques lignes applique ses bonus.
- [x] **G3.2 Restrictions d'accès** : objets, crafts, enchantement, blocs utilisables par Couleur. Fait le 23/10 : `interditBloc`, `interditObjet`, `interditCraft` dans `ProfilCouleur`, appliqués par `RestrictionsCouleur` (clics) et `CraftingMenuMixin` (crafts). Vérifié par `RestrictionsCouleurTest` avec de vrais clics droits (table d'enchantement, livre écrit, pupitre, craft d'un livre). *Fini quand* : une Couleur de test ne peut pas enchanter ni lire un livre.
- [x] **G3.3 Capacités actives** : touche d'action, cooldown, sync client. Fait le 23/10 : `.capacite(recharge, action)` dans `ProfilCouleur` (une par Couleur), touche R modifiable, paquet `ActiverCapacitePayload`, recharge attachée au joueur (sauvegardée, gardée à la mort, synchronisée) et affichée dans le HUD. Vérifié par `CapacitesActivesTest` avec un vrai appui sur la touche. *Fini quand* : une capacité de test se déclenche avec cooldown.
- [x] **G3.4 Faiblesses** : malus déclaratifs (fragilité, lenteur, malus selon l'environnement). Fait le 23/10 : malus permanents par valeurs négatives, malus conditionnels avec `.si(Condition, ...)` (conditions de base `A_CIEL_OUVERT`, `SOUS_TERRE`, `DANS_L_EAU`, ou sur mesure), réévalués deux fois par seconde. Vérifié par `FaiblessesTest` (malus à ciel ouvert qui part sous un toit). *Fini quand* : un malus conditionnel fonctionne (ex. malus en surface).
- [x] **G3.5 Apparence par Couleur** : repère visuel du joueur (yeux, cheveux ou marque). Fait le 23/10 : `client/MarqueCouleurLayer`, sigil sur le dos des mains et bracelet au poignet teintés de la Couleur (choix 🟡 dans le GDD). Vérifié à l'œil sur les captures de `MarqueCouleurTest` (face, corps tourné à gauche et à droite). *Fini quand* : la Couleur d'un joueur se voit sur son modèle.

### G4 Naissance

- [ ] **G4.1 Option de création du monde** : Test ou Choix libre. *Fini quand* : l'option est enregistrée dans le monde.
- [ ] **G4.2 Écran de choix libre**. *Fini quand* : on choisit une Couleur et on spawn avec.
- [ ] **G4.3 Moteur du test** : 7 questions, 4 réponses, 2 points cachés par réponse, égalité au hasard. Le script Python du GDD devient un test unitaire Java. *Fini quand* : le test unitaire retrouve 6,8 % à 7,5 % par Couleur.
- [ ] **G4.4 Interface du test** : questions à l'écran, barème invisible, une relance autorisée. *Fini quand* : un test complet donne une Couleur.
- [ ] **G4.5 Scène Dive to the Heart** : décor d'ouverture autour du test. *Fini quand* : le test se déroule dans la scène.
- [ ] **G4.6 Reformulation des 7 questions** : ton à retravailler, paires intouchables. *Fini quand* : textes validés, script relancé si une paire a bougé.

La question Or (promotion) et le spawn par région arrivent avec leurs régions (G7 à G13).

### G5 Factions de base

La version minimale du GDD : 3 jauges, 2 types de structures, 1 événement de traque.

- [ ] **G5.1 PNJ par Couleur** : villageois réutilisés, textures teintées, métiers adaptés. *Fini quand* : un PNJ de chaque Couleur existe.
- [ ] **G5.2 Actions qui bougent les jauges** : liste des actions (aider, voler, dénoncer, tuer un PNJ de telle Couleur) et leur effet. *Fini quand* : chaque action modifie les bonnes jauges.
- [ ] **G5.3 Structure Société générique** : poste de la Société avec accès selon la Loyauté. *Fini quand* : un joueur à Loyauté basse est refoulé.
- [ ] **G5.4 Structure Rising générique** : cellule cachée, accès selon le Soutien. *Fini quand* : la cellule n'est accessible qu'avec assez de Soutien.
- [ ] **G5.5 Patrouilles de Gris** : PNJ Gris qui contrôlent et font monter la Suspicion. *Fini quand* : une patrouille réagit à un joueur suspect.
- [ ] **G5.6 Traque** : Suspicion au max, événement type raid de Gris. *Fini quand* : la traque se déclenche, se gagne et se perd.
- [ ] **G5.7 [DÉCISION] Équipement rebelle** : ce que le Rising donne. *Fini quand* : liste notée dans le GDD.
- [ ] **G5.8 Commerce Société et Rising** : échanges selon les jauges. *Fini quand* : chaque camp a une boutique conditionnelle.

### G6 Moteur de jalons

- [ ] **G6.1 Horloge du monde** : temps de jeu cumulé et Soutien Rising cumulé, sauvegardés. *Fini quand* : les deux compteurs survivent au reload.
- [ ] **G6.2 Jalon générique** : condition de déclenchement, deux issues (canon ou divergente), choix selon l'état du monde, issue enregistrée (G2.4). *Fini quand* : un jalon de test se déclenche avec chacune des deux issues.
- [ ] **G6.3 Événements mondiaux** : effets d'un jalon sur le monde (jauges des villages, densité des patrouilles). *Fini quand* : un jalon de test change les patrouilles.
- [ ] **G6.4 PNJ essentiels** : inconscients au lieu de mourir avant leur jalon, tuables après. *Fini quand* : un PNJ de test se relève avant, meurt après.
- [ ] **G6.5 Attitude des PNJ canon** : valeur qui bouge selon les actions du joueur et pèse sur l'issue. *Fini quand* : l'attitude d'un PNJ de test change l'issue de son jalon.

## G7 Rouge et mines (fin du prototype)

Première Couleur complète. À la fin de G7, un Rouge naît dans une mine, y joue plusieurs heures, assiste à la pendaison d'Eo et découvre la surface.

- [ ] **G7.1 Génération de Mars** : biomes vanilla réglés pour Mars terraformée, emplacement des régions. *Fini quand* : un monde neuf place les régions de façon cohérente.
- [ ] **G7.2 Mines** : réseaux souterrains vastes, puits, galeries, ressources riches. *Fini quand* : une mine tient plusieurs heures d'exploration.
- [ ] **G7.3 Colonie rouge (type Lykos)** : habitations, PNJ Rouges, place de la pendaison. *Fini quand* : la colonie est générée et peuplée.
- [ ] **G7.4 Pitvipers** : mob souterrain. *Fini quand* : modèle, IA, spawn et loot en place.
- [ ] **G7.5 Cellules du Rising dans les mines** : version mine de G5.4, accessible aux Rouges. *Fini quand* : un Rouge trouve et rejoint une cellule.
- [ ] **G7.6 Rouge : force** : vitesse de minage, vision dans le noir, résistance à la chaleur. *Fini quand* : bonus actifs via le framework G3.
- [ ] **G7.7 Rouge : faiblesse** : petit gabarit, Loyauté plafonnée, surface inconnue au départ. *Fini quand* : malus actifs.
- [ ] **G7.8 Spawn Rouge** : apparition dans la colonie. *Fini quand* : un Rouge (test ou choix libre) naît dans une mine.
- [ ] **G7.9 Découverte de la surface** : moment fort de la première sortie. *Fini quand* : la première sortie déclenche l'événement.
- [ ] **G7.10 Pendaison d'Eo** : jalon fixe des premiers jours, non évitable, Soutien Rising en hausse dans les villages rouges, patrouilles en hausse. *Fini quand* : la pendaison se déclenche dans chaque monde avec ses effets.
- [ ] **G7.11 Test Rouge** : partie complète, de la naissance à la surface. *Fini quand* : partie jouée, notes de fun et d'équilibrage écrites, version de test perso gardée.

**Point d'étape** : si le prototype n'est pas fun, on corrige ici avant G8.

## Régions et Couleurs : G8 à G12

Chaque grand ticket livre une région de Mars et les Couleurs qui y naissent. Les cités passent avant les zones industrielles parce que le Marron et le Vert ont besoin des domaines d'Or et des scanners.

### G8 Garnisons : Gris, Bleu

- [ ] **G8.1 Garnisons** : casernes, avant-postes, spawn Gris et Bleu. *Fini quand* : les deux Couleurs naissent dans une garnison générée.
- [ ] **G8.2 Gris : force et faiblesse** : combat à distance, accès aux garnisons, perte de Loyauté en cas de désobéissance. *Fini quand* : bonus, accès et malus actifs.
- [ ] **G8.3 Gris : arrêter ou fermer les yeux** : choix face à un suspect, effet sur les jauges. *Fini quand* : les deux choix ont leurs conséquences.
- [ ] **G8.4 Gris : patrouilles jouées** : le joueur Gris reçoit des rondes. *Fini quand* : une ronde se fait de bout en bout.
- [ ] **G8.5 Test Gris**.
- [ ] **G8.6 Bleu : force et faiblesse** : peu résistant au sol, mauvais en souterrain. *Fini quand* : malus actifs.
- [ ] **G8.7 Bleu : vol maîtrisé et navigation**. *Fini quand* : le Bleu vole mieux que toute autre Couleur.
- [ ] **G8.8 Test Bleu**.

### G9 Cités de la Société : Argent, Cuivre, Blanc, Rose, Violet

- [ ] **G9.1 Cités hiérarchisées** : domaines d'Or en hauteur, quartiers des autres Couleurs en dessous, spawn des 5 Couleurs. *Fini quand* : une cité générée accueille les 5 Couleurs.
- [ ] **G9.2 Domaines d'Or et Or PNJ** : propriétaires, patrimoine, serviteurs. *Fini quand* : chaque domaine a un Or PNJ et son patrimoine.
- [ ] **G9.3 Scanners de la Société** : blocs de contrôle dans les structures, qui font monter la Suspicion. *Fini quand* : passer devant un scanner en étant suspect a un effet.
- [ ] **G9.4 Argent : finance** : gérer le patrimoine des Or PNJ, meilleurs échanges, fragilité au combat. *Fini quand* : bonus et malus actifs.
- [ ] **G9.5 Argent : détournement** : détourner des fonds (dont financer le Rising), Suspicion à chaque fois. *Fini quand* : un détournement fait gagner du Soutien et de la Suspicion.
- [ ] **G9.6 Test Argent**.
- [ ] **G9.7 Cuivre : faux papiers** : baisse de la Suspicion. *Fini quand* : des faux papiers font baisser la Suspicion.
- [ ] **G9.8 Cuivre : titres de propriété** : zone protégée avec bonus. *Fini quand* : une zone titrée applique sa protection.
- [ ] **G9.9 Cuivre : audits** : révéler les détournements (G9.5). *Fini quand* : un audit démasque un détourneur PNJ.
- [ ] **G9.10 Test Cuivre** : vérifier en priorité que c'est fun (point de vigilance du GDD).
- [ ] **G9.11 Blanc : enchantement avancé**, faiblesse physique, peu de craft. *Fini quand* : enchantements exclusifs disponibles.
- [ ] **G9.12 Blanc : jugement** : réduire la Suspicion d'autrui. *Fini quand* : un jugement baisse la Suspicion d'une cible.
- [ ] **G9.13 Test Blanc**.
- [ ] **G9.14 [DÉCISION] PNJ Roses** : suivre, porter, combattre, espionner. *Fini quand* : comportement noté dans le GDD.
- [ ] **G9.15 PNJ Roses** : apprivoisement par un Or, service. *Fini quand* : un Or PNJ possède des Roses qui le servent.
- [ ] **G9.16 Rose : discrétion et charme** : mobs qui détectent moins, villageois et mobs neutres charmés, très fragile. *Fini quand* : bonus et malus actifs.
- [ ] **G9.17 Rose : réseau** : retourner des PNJ Roses, informateurs, repérer structures et menaces. *Fini quand* : un PNJ Rose retourné rapporte des infos.
- [ ] **G9.18 Test Rose**.
- [ ] **G9.19 Violet : teintures et blocs décoratifs uniques**, faiblesse au combat, recherché par les deux camps. *Fini quand* : crafts exclusifs disponibles.
- [ ] **G9.20 Test Violet** (la Sculpture de PNJ arrive en G14).

### G10 Zones industrielles et agricoles : Orange, Vert, Marron, Jaune

- [ ] **G10.1 Zones** : ateliers, fermes, infrastructures, spawn des 4 Couleurs. *Fini quand* : les 4 Couleurs naissent dans une zone générée.
- [ ] **G10.2 [DÉCISION] gravBoots et armures** : qui peut les utiliser. *Fini quand* : règle notée dans le GDD.
- [ ] **G10.3 Orange : mécanique** : craft d'armures et machines, réparation, combat moyen, gros besoin de ressources. *Fini quand* : crafts exclusifs et malus actifs.
- [ ] **G10.4 gravBoots** : craft Orange, modèle, fonctionnement. *Fini quand* : un Orange fabrique des gravBoots qui marchent.
- [ ] **G10.5 Test Orange**.
- [ ] **G10.6 Vert : redstone avancée**, dépendance aux machines, fragilité. *Fini quand* : blocs redstone exclusifs disponibles.
- [ ] **G10.7 Vert : piratage** : scanners (G9.3) et systèmes de la Société, Suspicion en baisse dans une zone. *Fini quand* : un piratage neutralise les scanners d'une zone.
- [ ] **G10.8 Test Vert**.
- [ ] **G10.9 Marron : cuisine et agriculture** : plats à buffs puissants, croissance rapide, combat faible. *Fini quand* : recettes exclusives et bonus actifs.
- [ ] **G10.10 Marron : accès discret aux domaines d'Or** (G9.2). *Fini quand* : un Marron entre sans faire monter la Suspicion.
- [ ] **G10.11 Test Marron** : vérifier en priorité que c'est fun (point de vigilance du GDD).
- [ ] **G10.12 Jaune : médecine** : soins sur PNJ, potions avancées, combat faible. *Fini quand* : soins et potions exclusifs disponibles.
- [ ] **G10.13 Test Jaune** (le soin de convalescence arrive en G14).

### G11 Pôles : Obsidian

- [ ] **G11.1 Pôles et tribus** : glace, villages Obsidian, spawn. *Fini quand* : un Obsidian naît dans une tribu générée.
- [ ] **G11.2 Obsidian : force** : dégâts et résistance au corps à corps les plus élevés, résistance au froid. *Fini quand* : bonus actifs.
- [ ] **G11.3 Obsidian : faiblesse** : ni livres ni enchantement, lent. *Fini quand* : restrictions actives.
- [ ] **G11.4 Test Obsidian**.

### G12 Or et razor

- [ ] **G12.1 Domaine familial** : point de départ des Or, entraînement. *Fini quand* : un Or naît dans son domaine.
- [ ] **G12.2 Or : force** : combat polyvalent, buffs aux alliés, PNJ Roses domestiques. *Fini quand* : bonus actifs, Roses à son service.
- [ ] **G12.3 Or : faiblesse** : repéré partout, cible prioritaire du Rising, dépend des autres pour crafter. *Fini quand* : malus actifs.
- [ ] **G12.4 Razor** : arme qui change de forme, hitbox custom, réservée aux Or, obtenue via la Loyauté. *Fini quand* : le razor marche dans toutes ses formes.
- [ ] **G12.5 [DÉCISION] sangLames** : Couleur(s) associée(s), fonctionnement. *Fini quand* : règle notée dans le GDD.
- [ ] **G12.6 sangLames**. *Fini quand* : l'arme existe selon G12.5.
- [ ] **G12.7 Test Or** (hors Institut, qui arrive en G13).

## Trilogie complète : G13 à G18

Une fois les 14 Couleurs jouables, on construit ce qui les relie : l'Institut, la Sculpture, le canon et sa fin.

### G13 Institut

- [ ] **G13.1 [DÉCISION] Durée de la promotion précédente et moment du jalon Darrow** (en temps de jeu). *Fini quand* : chiffres notés dans le GDD.
- [ ] **G13.2 Dimension Institut** : carte fixe, vallée, châteaux des Maisons. *Fini quand* : on entre et sort de la dimension.
- [ ] **G13.3 Maisons et élèves PNJ** : affectation, alliances, combats entre Maisons. *Fini quand* : une Maison PNJ en attaque une autre.
- [ ] **G13.4 Le Passage** : combat à mort à l'entrée, réussissable sans être une formalité. *Fini quand* : on peut le réussir et l'échouer.
- [ ] **G13.5 Question Or** : choix promotion précédente ou promotion de Darrow à la naissance. *Fini quand* : le choix oriente le départ de l'Or.
- [ ] **G13.6 Promotion précédente** : early game borné dans le temps, diplôme, retour dans la Société. *Fini quand* : une promotion se joue de l'entrée au diplôme.
- [ ] **G13.7 Influence depuis l'extérieur** : parrainer une Maison, ravitaillement, paris, pression sur un Proctor. *Fini quand* : chaque action a un effet visible sur l'Institut.
- [ ] **G13.8 Réinitialisation** de la dimension entre les deux promotions. *Fini quand* : la promotion de Darrow démarre sur une carte propre.
- [ ] **G13.9 Test promotion précédente**.

### G14 Sculpture

- [ ] **G14.1 [DÉCISION] Conditions et coût chiffrés** : seuil de Soutien, ressources, durée de convalescence, Suspicion de base. *Fini quand* : chiffres notés dans le GDD.
- [ ] **G14.2 Sculpteur Violet PNJ**. *Fini quand* : il existe et accepte un client éligible.
- [ ] **G14.3 Conditions** : parrainage du Rising, ressources rares, financement possible par l'Argent. *Fini quand* : un joueur non éligible est refusé.
- [ ] **G14.4 Apprentissage de la culture Or** : épreuves, échec = démasqué vite. *Fini quand* : les épreuves se réussissent et s'échouent.
- [ ] **G14.5 Transformation** : passage à Or, un ou deux savoir-faire gardés par Couleur d'origine (13 cas). *Fini quand* : chaque Couleur garde ce qui est prévu.
- [ ] **G14.6 Coût** : convalescence, perte de ce qui tient au corps, Suspicion de base relevée définitivement. *Fini quand* : tous les coûts s'appliquent.
- [ ] **G14.7 Démasquage** : audit Cuivre, informateur Rose, contrôle Gris ; chute de Loyauté et traque. *Fini quand* : chaque voie de démasquage marche.
- [ ] **G14.8 Jaune : écourter la convalescence**. *Fini quand* : un Jaune réduit la durée.
- [ ] **G14.9 [DÉCISION] Ce qu'apporte un PNJ Sculpté par un Violet**. *Fini quand* : règle notée dans le GDD.
- [ ] **G14.10 Violet : sculpter des PNJ** (création d'infiltrés). *Fini quand* : un infiltré sculpté remplit son rôle.
- [ ] **G14.11 Accès à l'Institut pour les Sculptés** (G13). *Fini quand* : un Sculpté passe le Passage.
- [ ] **G14.12 Test Sculpture** : si tout le monde se fait Sculpter dès que possible, le coût est trop faible. *Fini quand* : partie jouée depuis au moins 3 Couleurs d'origine.

### G15 Personnages canon et promotion de Darrow

- [ ] **G15.1 [DÉCISION] Liste des personnages canon v1 et leurs lieux** (proposition : Darrow, Mustang, Sevro, Cassius, Ares, le Chacal, Octavia, Roque). *Fini quand* : liste notée dans le GDD.
- [ ] **G15.2 Personnages canon** : modèles, lieux, essentiels avant leur jalon (G6.4), attitude (G6.5). *Fini quand* : chaque personnage est croisable et son attitude bouge.
- [ ] **G15.3 Jalon entrée de Darrow à l'Institut** (temps de jeu). *Fini quand* : le jalon se déclenche au moment fixé en G13.1.
- [ ] **G15.4 Promotion de Darrow** : Or joueur dans une Maison (allié, rival, camp du Chacal). *Fini quand* : la promotion se joue avec les personnages canon.
- [ ] **G15.5 Test promotion de Darrow**.

### G16 Fin de trilogie et Luna

- [ ] **G16.1 [DÉCISION] Liste définitive des jalons v1 et contenu de chaque issue divergente**. *Fini quand* : noté dans le GDD.
- [ ] **G16.2 Jalon du Triomphe** : issue canon et issue divergente. *Fini quand* : les deux issues se déclenchent selon l'état du monde.
- [ ] **G16.3 [DÉCISION] Luna** : contenu et moment d'accès. *Fini quand* : noté dans le GDD.
- [ ] **G16.4 Dimension Luna** et moyen d'y aller (vaisseaux Bleu ou autre). *Fini quand* : on atteint Luna au bon moment.
- [ ] **G16.5 Jalon de la chute de Luna** : issues et fins du monde. *Fini quand* : chaque issue mène à une fin.
- [ ] **G16.6 Convergence des branches** : les divergences rejoignent un petit nombre d'états. *Fini quand* : nombre d'états finaux compté et borné.
- [ ] **G16.7 Drapeau « fin proche du canon »** sauvegardé, pour le passage v1 vers v2. *Fini quand* : le drapeau est lisible dans la sauvegarde.
- [ ] **G16.8 Test partie complète** : de la naissance à la chute de Luna, une fois canon, une fois divergente.

### G17 Équilibrage et finitions

- [ ] **G17.1 Équilibrage des 14 Couleurs** : progression horizontale vérifiée, aucune Couleur ne sert d'early game. *Fini quand* : chaque Couleur a été jouée jusqu'à la fin d'une partie.
- [ ] **G17.2 Sons**. *Fini quand* : plus aucun événement clé n'est muet.
- [ ] **G17.3 Textures et modèles finaux** (Blockbench). *Fini quand* : plus aucun placeholder.
- [ ] **G17.4 Traduction EN** complète. *Fini quand* : aucune clé de traduction manquante.
- [ ] **G17.5 Performance** : génération de monde et PNJ. *Fini quand* : pas de lag notable sur une machine moyenne.

### G18 Publication v1

- [ ] **G18.1 Page Modrinth** : description, captures, vidéo, mention fan-made non commercial et crédit à Pierce Brown. *Fini quand* : page prête en brouillon.
- [ ] **G18.2 Licence et mentions légales du mod**. *Fini quand* : fichiers présents dans le repo et le .jar.
- [ ] **G18.3 Release 1.0**. *Fini quand* : le mod est en ligne.
