# Mod Minecraft Red Rising : Game Design Document

Document de référence du projet. Il regroupe tout ce qui a été décidé, les propositions encore à valider et les sujets ouverts. À mettre à jour à chaque décision.

**Légende**
- ✅ Décidé
- 🟡 Proposé, pas encore confirmé explicitement
- ❓ À trancher

---

## 1. Vision

- ✅ Mod Minecraft **solo**, projet plaisir avec potentiel portfolio s'il est téléchargé.
- ✅ **Gratuit et non commercial** (IP Red Rising de Pierce Brown). Pas de Patreon, pas de dons liés au mod.
- ✅ Publication visée : Modrinth.
- ✅ Genre : **RPG sandbox**. Pas de trame de quêtes, pas d'arbres de dialogue. Le joueur naît avec une Couleur et avance dans son monde selon elle.
- ✅ **Toutes les Couleurs dès la v1** (14). Développement Couleur par Couleur, publication une fois l'ensemble prêt.
- ✅ Le canon est un décor et un calendrier, pas un scénario : le joueur n'est pas Darrow, c'est quelqu'un d'autre qui naît à la même époque.

**Principe de design central : progression horizontale.** Chaque Couleur est viable jusqu'au bout. Aucune n'est une étape de l'early game. Chaque Couleur a une force, des exclusivités et une faiblesse réelle.

---

## 2. Versions

| Version | Contenu |
|---|---|
| **v1** | Première trilogie (Red Rising, Golden Son, Morning Star). Mars + Institut + Luna. |
| **v1.x** | Correctifs, équilibrage des Couleurs. |
| **v2** | Seconde saga (Iron Gold, Dark Age, Light Bringer, puis Red God s'il est sorti). Plusieurs mondes (Mars, Luna, Mercure...). Choix de la planète de départ. |

**Passage v1 → v2** ✅
- Départ direct à l'époque d'Iron Gold possible pour tout le monde.
- Un monde v1 peut continuer en v2 **seulement si sa fin est proche du canon** (République existante). Les autres fins restent des fins.

**Red God** : pas de date de sortie annoncée à l'été 2026, 2027 au plus tôt. Ne pas planifier dessus.

---

## 3. Naissance et attribution de la Couleur

### Mode de naissance ✅
Option à la création du monde :
- **Test** (par défaut) : test de personnalité caché, résultat en surprise.
- **Choix libre** : le joueur choisit sa Couleur dans un menu. Sert aussi pendant le développement pour tester chaque Couleur.
- ✅ Mise en place (G4.1) : bouton « Naissance » dans l'onglet Jeu de l'écran de création du monde. L'option est une règle de jeu (`redrising:mode_naissance`, valeurs `test` et `choix_libre`) enregistrée avec le monde ; elle reste modifiable par `/gamerule` ou `/rr etat naissance`, et ne vaut que pour les naissances suivantes.

### Règles du test ✅
- Scène d'ouverture façon Dive to the Heart (Kingdom Hearts).
- **7 questions, 4 réponses, 2 Couleurs par réponse.** Chaque réponse donne 1 point caché à chacune de ses 2 Couleurs.
- La Couleur avec le plus de points gagne. Égalité départagée au hasard.
- Le joueur ne voit jamais le barème.
- **Une relance autorisée** au spawn.
- Chaque Couleur apparaît exactement 4 fois. Distribution vérifiée par script : toutes les Couleurs sortent entre **6,8 % et 7,5 %** (idéal 7,1 %).
- Une version à 5 questions a été testée et rejetée : Jaune à 1,3 %, Marron à 11,2 %.

⚠️ **Les formulations peuvent changer librement. Les paires de Couleurs, non : toute modification d'une paire impose de relancer le script.**

### Questions ✅ (formulations à retravailler pour le ton)

**1. Une tempête approche. Que fais-tu ?**
- Je renforce l'abri → Orange, Rouge
- Je donne les ordres → Or, Gris
- Je soigne ceux qui sont déjà blessés → Jaune, Marron
- J'étudie d'où elle vient → Vert, Bleu

**2. On te donne un seul outil pour toute ta vie.**
- Une lame → Obsidian, Or
- Une clé à molette → Orange, Vert
- Un pinceau → Violet, Blanc
- Une bourse pleine → Argent, Cuivre

**3. Quelqu'un t'a trahi.**
- Je le frappe, maintenant → Obsidian, Rouge
- Je le fais juger → Blanc, Cuivre
- Je le ruine sans qu'il sache d'où ça vient → Argent, Rose
- Je pars, loin → Bleu, Violet

**4. Où te sens-tu le mieux ?**
- Sous terre, dans la chaleur → Rouge, Orange
- Dans le ciel → Bleu, Or
- Dans la foule d'une cité → Rose, Argent
- Dans le silence d'un temple ou d'un labo → Jaune, Blanc

**5. Que dira-t-on de toi à ta mort ?**
- Il n'a jamais reculé → Obsidian, Gris
- Il a sauvé des vies → Jaune, Marron
- Il a construit quelque chose qui dure → Vert, Violet
- Il a changé le monde → Or, Rose

**6. On te donne un ordre injuste.**
- J'obéis, c'est la règle → Gris, Cuivre
- J'obéis en apparence et je fais autre chose → Rose, Vert
- Je refuse en face → Obsidian, Rouge
- Je négocie → Argent, Marron

**7. Une nuit sans sommeil. Que fais-tu ?**
- Je répare ce qui est cassé → Orange, Marron
- Je fais ma ronde, tout doit être en ordre → Cuivre, Gris
- Je regarde les étoiles → Bleu, Violet
- Je veille un malade ou je prie → Jaune, Blanc

### Question supplémentaire pour les Or ✅
Choix (pas de hasard) : **promotion précédente** ou **promotion de Darrow** (voir section 7).

### Script de vérification de la distribution

```python
import itertools
from collections import Counter

Q = [
    [["Orange","Rouge"],["Or","Gris"],["Jaune","Marron"],["Vert","Bleu"]],
    [["Obsidian","Or"],["Orange","Vert"],["Violet","Blanc"],["Argent","Cuivre"]],
    [["Obsidian","Rouge"],["Blanc","Cuivre"],["Argent","Rose"],["Bleu","Violet"]],
    [["Rouge","Orange"],["Bleu","Or"],["Rose","Argent"],["Jaune","Blanc"]],
    [["Obsidian","Gris"],["Jaune","Marron"],["Vert","Violet"],["Or","Rose"]],
    [["Gris","Cuivre"],["Rose","Vert"],["Obsidian","Rouge"],["Argent","Marron"]],
    [["Orange","Marron"],["Cuivre","Gris"],["Bleu","Violet"],["Jaune","Blanc"]],
]

def run(Q):
    colors = sorted({c for q in Q for a in q for c in a})
    prob = Counter()
    appearances = Counter(c for q in Q for a in q for c in a)
    combos = list(itertools.product(range(4), repeat=len(Q)))
    for combo in combos:
        score = Counter()
        for qi, ai in enumerate(combo):
            for c in Q[qi][ai]:
                score[c] += 1
        best = max(score.values())
        winners = [c for c in score if score[c] == best]
        for c in winners:
            prob[c] += 1 / len(winners)  # égalité départagée au hasard
    for c in colors:
        print(f"{c:9s} apparitions={appearances[c]} proba={prob[c]/len(combos)*100:5.1f}%")

run(Q)
```

---

## 4. Les 14 Couleurs ✅

Format : force, exclusivités, faiblesse. La faiblesse est aussi importante que le reste, c'est elle qui tient la progression horizontale.

| Couleur | Force | Exclusif | Faiblesse |
|---|---|---|---|
| **Or** | Combat polyvalent, commandement | Razor, PNJ Roses domestiques, buffs aux alliés | Repéré partout, cible prioritaire du Rising, dépend des autres pour crafter |
| **Argent** | Finance | Gérer le patrimoine des Or PNJ, détourner des fonds (ex. financer le Rising), meilleurs échanges villageois | Fragile au combat, chaque détournement fait monter la Suspicion |
| **Cuivre** | Administration | Faux papiers, titres de propriété sur une zone (protection, bonus), audits qui révèlent les détournements | Quasi inutile au combat, puissance entièrement indirecte |
| **Blanc** | Justice, religion | Enchantement avancé, réduire la Suspicion d'autrui par un jugement | Faible physiquement, peu d'accès au craft |
| **Bleu** | Pilotage | Vol maîtrisé, navigation, plus tard vaisseaux vers Luna | Peu résistant au sol, mauvais en souterrain |
| **Jaune** | Médecine, science | Soins sur autrui et PNJ, potions avancées, soigner la convalescence post-Sculpture | Combat faible |
| **Vert** | Technologie | Redstone avancée, pirater scanners et systèmes de la Société (baisse la Suspicion dans une zone) | Fragile, dépendant de ses machines |
| **Violet** | Art, Sculpture | Sculpter des PNJ humains (création d'infiltrés), teintures et blocs décoratifs uniques | Faible au combat, recherché par les deux camps |
| **Orange** | Mécanique | Craft des gravBoots, armures et machines, réparation | Combat moyen, besoin de ressources en quantité |
| **Gris** | Soldat | Combat à distance, patrouilles, arrêter ou fermer les yeux, accès aux garnisons | Obéissance : perd de la Loyauté s'il désobéit |
| **Obsidian** | Guerrier | Dégâts et résistance au corps à corps les plus élevés, résistance au froid des pôles | Illettré (lore) : ni livres ni enchantement, lent |
| **Marron** | Service, agriculture | Cuisine avec buffs puissants, agriculture rapide, accès discret aux domaines d'Or | Combat faible, peu de prestige |
| **Rose** | Espionnage | Discrétion (mobs détectent moins), charme (villageois, certains mobs neutres), réseau d'informateurs, retourner les PNJ Roses, repérer structures et menaces | Très fragile au combat direct |
| **Rouge** | Mine | Vitesse de minage, vision dans le noir, résistance à la chaleur, accès aux cellules du Rising | Petit gabarit, ignore la surface au départ, Loyauté Société plafonnée |

**Points de vigilance**
- Cuivre et Marron sont les plus minces. Ils tiennent grâce aux factions, à vérifier en jeu.
- Rose : aspect esclave sexuel du lore **retiré**. Couleur jouable orientée espionnage (archétype Théodora).

### Apparence (G3.5)
- ✅ **Trois repères**, tous teintés de la Couleur, visibles par tous les joueurs :
  - **mains** : sigil 2×2 sur le dos de chaque main et bracelet d'un pixel au poignet (lore : les sigils sur les mains), aussi en vue première personne ;
  - **yeux** : les deux iris, à l'emplacement standard des skins par défaut (ligne 12 du visage, x = 10 et 13) ;
  - **cheveux** : couche posée par-dessus les cheveux du skin (couche extérieure de la tête : dessus, frange, tempes, nuque). Réglage client « Cheveux de Couleur » (Options > Personnalisation du skin) pour la désactiver.
- ✅ Lisible avec un casque : les mèches teintées dépassent du casque, les yeux et les mains restent visibles.
- ✅ Teintes (validées le 23/10, après réglage en G3.5 pour séparer Or/Jaune, Blanc/Argent/Gris, Cuivre/Marron et rendre l'Obsidian visible sur peau foncée) : Or `#FFBA14` (or vif et saturé), Argent `#A9C1E0`, Cuivre `#DD8240` (cuivré, orangé), Blanc `#FFFFFF`, Bleu `#2F6BFF`, Jaune `#FFF35A`, Vert `#2FA84F`, Violet `#A050E8`, Orange `#F05A1A`, Gris `#8E8E8E`, Obsidian `#4A2D7A` (violet sombre plutôt que noir, sinon invisible sur peau foncée), Marron `#8A5530`, Rose `#FF7EB6`, Rouge `#C8201E`. Le nom de la Couleur dans le HUD prend la même teinte.
- Planche de contrôle : `./gradlew runClientGameTest` puis `python3 outils/planche_couleurs.py` (14 Couleurs sur skin clair et foncé, avec et sans casque, sans cheveux).
- Placeholder jusqu'aux textures finales (G17.3).

### PNJ Roses ✅
- Fonctionnent comme une mécanique d'apprivoisement : un Or les possède, ils le suivent et le servent.
- Ils peuvent être **raisonnés / retournés** par un joueur Rose ou un joueur du Rising, et deviennent alors informateurs dans les réseaux d'espions.
- ❓ Comportement exact à définir (suivre, porter, combattre, espionner).

---

## 5. Sculpture ✅

- **Direction unique : vers l'Or.** Pas de Sculpture vers d'autres Couleurs (personne ne voudrait descendre).
- Réalisée par un **PNJ Sculpteur Violet**.
- Un **joueur Violet** peut Sculpter des PNJ humains (pas lui-même).
- **Une seule Sculpture par personnage** en v1.
- C'est la **seule porte d'entrée à l'Institut** pour les autres Couleurs (chemin de Darrow).

### Conditions 🟡
- **Parrain** : le Rising (Fils d'Arès) organise. Soutien Rising élevé obligatoire.
- **Ressources rares**, trouvées ou financées (l'Argent peut détourner de quoi payer).
- **Apprentissage de la culture Or** : épreuves à réussir, sinon démasqué vite (dans le livre, Darrow apprend les manières des Or avec un Rose).

### Coût 🟡
- **Convalescence** : plusieurs jours de jeu en état affaibli (le Jaune peut l'écourter).
- **Perte de ce qui tient au corps** : bonus physiques, résistances, accès liés à l'apparence.
- **Suspicion de base plus élevée, définitivement.** Démasquable par un audit Cuivre, un informateur Rose, un contrôle Gris. Les faux papiers Cuivre la font baisser. Démasqué : chute de la Loyauté Société et traque des Gris.

### Ce qu'on garde ✅
Une partie des mécaniques de l'ancienne Couleur, pas toutes : un ou deux savoir-faire. Exemples : un ancien Rouge garde vision dans le noir et endurance en mine, un ancien Orange garde une partie de ses crafts.

**Règle d'équilibrage** : un Or Sculpté n'est pas un Or natif en mieux. C'est une autre façon de jouer, plus tendue (Suspicion, dette envers le Rising, risque constant). Si en test tout le monde se fait Sculpter dès que possible, le coût est trop faible.

---

## 6. Factions et jauges ✅ (base proposée, validée dans l'esprit)

### Trois jauges
- **Loyauté Société** : ce que les Or et le système pensent de toi.
- **Soutien Rising** : ta valeur pour la rébellion.
- **Suspicion** : à quel point tu es surveillé.

Deux jauges séparées (pas une barre Société ↔ Rising) pour permettre **l'agent double**, cœur de Red Rising. La Suspicion fait le lien : plus tu joues double, plus elle monte.

### Valeurs ✅ (proposées en G2.2, validées le 23/10)
- Échelle : entiers de **0 à 100** pour les trois jauges.
- Départ : **Loyauté 50, Soutien 0, Suspicion 0** (neutre envers la Société, inconnu du Rising, pas surveillé).
- Plafonds et planchers par Couleur : **Rouge, Loyauté plafonnée à 40**. Seule règle pour l'instant ; les autres (ex. Suspicion de base relevée après la Sculpture) s'ajouteront au même endroit (`Bornes.pour`).

### Où vivent les factions
- **Société** : structures visibles générées (domaines d'Or, garnisons de Gris, comptoirs).
- **Rising** : caché (cellules dans les mines, souterrains, camps de Rouges).
- PNJ : réutiliser les villageois avec variantes par Couleur (textures teintées, métiers adaptés) plutôt que 14 PNJ from scratch.

### Conséquences
- Loyauté haute : accès aux domaines, commerces, équipement de la Société (razor réservé aux Or).
- Soutien haut : bases cachées, équipement rebelle, soutien armé.
- Suspicion au maximum : les Gris te traquent (événement type raid).

**Version minimale** : 3 jauges, 2 types de structures, 1 événement de traque. Tout le reste s'y greffe.

---

## 7. Monde physique

### Mars ✅
- **Tout le monde naît sur Mars** en v1. La diversité vient des régions.
- Mars est **terraformée** dans les livres (forêts, océans, montagnes, villes) : réutiliser au maximum les biomes vanilla, concentrer l'effort sur structures et zones spécifiques.
- Point d'apparition selon la Couleur.

| Région | Contenu | Apparition |
|---|---|---|
| **Mines** | Souterraines, **vastes**, colonies de Rouges (type Lykos), **pitvipers** | Rouge |
| **Cités de la Société** | Hiérarchisées, domaines d'Or en hauteur, quartiers des autres Couleurs en dessous | Or, Argent, Cuivre, Blanc, Rose, Violet |
| **Zones industrielles et agricoles** | Ateliers, fermes, infrastructures | Orange, Vert, Marron, Jaune |
| **Garnisons** | Casernes, avant-postes | Gris, Bleu |
| **Pôles** | Glace, tribus | Obsidian |

- Moment fort à garder : un Rouge commence persuadé que la surface est inhabitable. La découvrir est son premier vrai moment, comme Darrow.
- Vigilance : les mines doivent être assez riches pour qu'un Rouge y joue plusieurs heures, sinon la Couleur devient un simple tutoriel.

### Institut ✅
- **Dimension séparée** (carte fixe : vallée, châteaux des Maisons).
- **Lore accurate : réservé aux Or**, natifs ou Sculptés.
- Entrée par **le Passage** (combat à mort entre élèves) : épreuve réussissable sans être une formalité.
- **Tous les Or passent par l'Institut**, deux profils au choix :
  - **Promotion précédente** : l'Institut est l'early game, dès le début du monde. Une fois diplômé, le joueur vit dans la Société et peut suivre et influencer l'Institut de Darrow de l'extérieur (parrainer une Maison, ravitaillement, paris, pression sur un Proctor).
  - **Promotion de Darrow** : entrée au jalon avec lui, dans une Maison pas forcément la sienne (allié, rival, camp du Chacal). Early game dans le domaine familial : entraînement, préparation au Passage.
- Même dimension réinitialisée entre les deux promotions.
- La promotion précédente n'a pas de personnages canon : elle repose uniquement sur le système (Maisons, combats, alliances). Bon terrain d'essai avant la promotion de Darrow.
- **Contrainte** : la promotion précédente doit être terminée avant celle de Darrow. Durée bornée (ordre de grandeur à fixer, ex. quelques heures de jeu). Jalon d'entrée de Darrow calé sur le **temps de jeu**, pas sur les jauges.
- Coût à assumer : une dimension entière vue par une fraction des joueurs (environ 1 sur 14 au départ + les Sculptés).

### Luna 🟡
Dimension atteignable plus tard dans l'aventure, au rythme des jalons.

---

## 8. Chronologie et canon

### Point de départ ✅
Le monde démarre **au début du tome 1, avant la mort d'Eo**. La Société domine tout, le Rising se limite aux Fils d'Arès et à quelques cellules cachées, surtout chez les Rouges de Mars.

### Jalons
- ✅ **Pendaison d'Eo : point fixe.** Elle arrive dans les premiers jours du monde, avant que le joueur ait les moyens d'intervenir. Il en est témoin, il ne peut pas l'empêcher. Événement mondial : Soutien Rising en hausse dans les villages rouges, patrouilles de Gris en hausse.
- ✅ Après Eo, **les actions du joueur peuvent faire dévier le canon.**
- 🟡 Liste des jalons v1 : pendaison d'Eo (fixe), entrée de Darrow à l'Institut, le Triomphe (fin de Golden Son), chute de Luna (fin de Morning Star).

### Mécanique de déviation 🟡
- Chaque jalon a **deux issues** : canon ou divergente.
- L'issue dépend de l'état du monde au moment du jalon (Soutien Rising global, Loyauté Société, zones contrôlées).
- Exemple : au Triomphe, Rising fort → il est blessé mais survit comme dans le livre. Rising faible → écrasé, les cellules tombent, phase d'occupation plus dure.
- Les jalons sont des **événements mondiaux**, pas des quêtes.
- Passage d'époque selon le temps de jeu et le Soutien Rising cumulé : aider le Rising accélère, le combattre ralentit ou fait diverger.
- Limiter l'explosion des états : les branches doivent se rejoindre (divergence temporaire, retour vers un nombre réduit d'états) plutôt que former un arbre complet.

### Personnages canon ✅
PNJ qu'on peut **croiser et influencer**. Trois règles :
1. **Essentiels avant leur jalon** (tombent inconscients au lieu de mourir, façon Skyrim), tuables après. Tuer un personnage important déclenche l'issue divergente du jalon suivant.
2. **Eo** : non sauvable (mort fixe, dans les premiers jours).
3. **Pas d'arbres de dialogue.** Chaque personnage a une **attitude** envers le joueur, qui bouge selon ses actions (aider, donner des objets, couvrir, dénoncer, s'allier à leurs ennemis). Cette attitude pèse sur l'issue de leur jalon.

- Une poignée de personnages par époque, dans des lieux précis (mine de Lykos, Institut, domaines de la Société).
- 🟡 Liste v1 proposée : Darrow, Mustang, Sevro, Cassius, Ares, le Chacal, Octavia, Roque.

---

## 9. Équipement

- ✅ Contenu voulu : razor, sangLames, gravBoots, armures.
- ✅ Razor réservé aux Or (lore), obtenable via la Loyauté Société.
- ✅ gravBoots et armures craftés par l'Orange.
- ❓ Qui peut utiliser gravBoots et armures.
- ❓ sangLames : Couleur(s) associée(s), fonctionnement.
- ❓ Équipement rebelle côté Rising.
- Le razor est l'objet le plus technique (arme qui change de forme, hitbox custom).

---

## 10. Technique

- ✅ **Fabric pur, Minecraft 26.2** (décidé le 23/10/2026, ticket G1.0). Version gelée pendant le développement ; un portage vers la version du moment sera évalué juste avant la publication.
  - Fabric : loader le plus suivi sur les nouvelles versions, léger, Fabric API couvre ce qu'il faut (données attachées au joueur avec persistance et sync, payloads réseau, commandes, événements).
  - 26.x : première branche non obfusquée (noms officiels Mojang). Les versions 1.21.x et antérieures sont incompatibles avec 26.x, donc une impasse pour un projet qui démarre.
  - Pas d'Origins : sur 26.x il n'existe que des portages non officiels, et Origins ne couvrirait que les pouvoirs. Le framework des Couleurs (G3) est écrit dans le mod.
- ✅ Prérequis : Java 25, IntelliJ IDEA 2025.3 ou plus (pour les mixins), plugin Gradle `net.fabricmc.fabric-loom` 1.18, Gradle 9.8.2 (Loom 1.18 exige Gradle 9.7 minimum). Projet local : Bureau > red rising mod.
- ⚠️ Le réseau de l'école bloque les serveurs Mojang et Fabric : le premier téléchargement (ou un changement de version) se fait sur un autre réseau. Ensuite tout est en cache.
- ✅ Méthode qualité : chaque ticket est compilé et lancé en jeu (`runClient`) avant d'être coché. Tests unitaires pour la logique pure (test de naissance, jauges, jalons).
- ✅ Même en solo, Minecraft tourne en client-serveur : synchronisation réseau obligatoire pour toutes les données joueur.
- ✅ **Format de sauvegarde versionné dès la v1** : enregistrer proprement l'état du monde (issue de chaque jalon, jauges, zones contrôlées, Couleur, Sculpture) pour permettre le passage v1 → v2 sans migrations douloureuses.
  - ✅ Mis en place en G2.4 (schéma v1) : l'état du monde est un attachment global Fabric (`data/fabric/attachments.dat`, clé `redrising:etat_monde`) avec `schemaVersion`, `epoque` (`red_rising`, `golden_son`, `morning_star`), `jalons` (identifiant de jalon → `canon` ou `divergente`) et `zones` (identifiant de zone → `societe` ou `rising`). Couleur et jauges sont attachées au joueur, dans son fichier.
  - Règle : toute modification du format incrémente `EtatMonde.VERSION_SCHEMA` et ajoute une étape de migration dans `EtatMonde.migrer`. Un monde au schéma plus récent que le mod est refusé.
- Assets : Blockbench pour modèles et textures. Poste de temps le plus lourd avec les sons et l'équilibrage.

---

## 11. Scope et méthode

- Le projet est passé d'un mod de Couleurs à un RPG : 14 classes, 3 jauges, factions, jalons à branches, PNJ canon, 2 dimensions custom, génération de Mars. Pour un dev seul, ça se compte en années.
- **Prototype privé recommandé** avant tout le reste : une région de Mars, l'époque du tome 1, la pendaison d'Eo, quelques Couleurs. Non publié, juste pour vérifier que c'est fun.
- Garder des versions de test personnelles à chaque Couleur terminée, même si la publication attend la v1 complète.
- Ordre de construction : voir `docs/roadmap.md` (grands tickets G1 à G18).

---

## 12. À trancher (récap)

- [x] Base technique : Fabric pur, Minecraft 26.2 (G1.0)
- [ ] Liste définitive des jalons v1 et contenu de chaque issue divergente
- [ ] Liste définitive des personnages canon v1 et leurs lieux
- [ ] Sculpture : conditions et coût exacts (chiffres)
- [ ] Durée de l'Institut (promotion précédente) et moment du jalon Darrow
- [ ] Ce qu'un PNJ Sculpté par un Violet apporte concrètement
- [ ] Comportement exact des PNJ Roses
- [ ] Équipement : accès gravBoots / armures, sangLames, équipement rebelle
- [ ] Luna : contenu et moment d'accès
- [ ] Reformulation des 7 questions du test (sans toucher aux paires)
- [ ] Vérifier en jeu que Cuivre et Marron sont fun
