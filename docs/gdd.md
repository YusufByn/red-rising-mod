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
- ✅ Choix libre en place (G4.2) : à son arrivée, un joueur sans Couleur voit l'écran des 14 Couleurs (de la plus haute à la plus basse, noms teintés). Pas de fermeture sans choisir, choix définitif (pas de relance : elle est propre au test). En solo, le jeu est en pause pendant le choix ; en multijoueur, le joueur attend dans le monde.
- ✅ Test en place (G4.4) : à son arrivée, un joueur sans Couleur passe les 7 questions, une par une, sans retour en arrière ; les réponses sont dans l'ordre du GDD, le barème n'est jamais montré (le serveur compte les points). Puis l'écran du résultat : la Couleur en grand, « Accepter » ou « Refaire le test ». **La relance refait tout le test** (un simple nouveau tirage ne changerait rien hors égalité) ; elle n'est offerte qu'une fois, et le second résultat est définitif. Résultat en attente et relance utilisée sont gardés sur le joueur : se déconnecter ramène au même résultat, sans relance en plus. En solo, le jeu est en pause pendant le test.

### Règles du test ✅
- Scène d'ouverture façon Dive to the Heart (Kingdom Hearts).
- ✅ Scène en place (G4.5, à embellir en G17) : une dimension à part, vide et noire, où le joueur se tient au centre d'un vitrail rond posé sur un pilier qui s'enfonce dans l'obscurité. Le vitrail est une roue des 14 Couleurs (verre teinté, l'Or devant le joueur, puis dans l'ordre de la hiérarchie) autour d'un cœur blanc. Pendant le test, la caméra passe derrière le joueur, qui se voit sur le vitrail, et l'interface de jeu disparaît. Une fois né, le joueur est ramené à son point d'apparition. Le choix libre, outil de développement, se fait sans la scène.
- **7 questions, 4 réponses, 2 Couleurs par réponse.** Chaque réponse donne 1 point caché à chacune de ses 2 Couleurs.
- La Couleur avec le plus de points gagne. Égalité départagée au hasard.
- Le joueur ne voit jamais le barème.
- **Une relance autorisée** au spawn.
- Chaque Couleur apparaît exactement 4 fois. Distribution vérifiée par script : toutes les Couleurs sortent entre **6,8 % et 7,5 %** (idéal 7,1 %).
- Une version à 5 questions a été testée et rejetée : Jaune à 1,3 %, Marron à 11,2 %.

⚠️ **Les formulations peuvent changer librement. Les paires de Couleurs, non : toute modification d'une paire impose de relancer le script** (depuis G4.3 : `./gradlew build`, qui lance `TestPersonnaliteTest`).

### Questions ✅ (formulations validées le 26/10, G4.6)

La voix de la scène s'adresse au joueur, au tutoiement. Textes neutres en genre (la question 5 est une épitaphe au passé simple). Version anglaise dans `en_us.json`.

**1. Une tempête de poussière fond sur la colonie. Il te reste une heure.**
- J'étaie les murs et je cloue les volets. → Orange, Rouge
- Je dis à chacun où se tenir. → Or, Gris
- Je m'occupe de ceux qui sont déjà blessés. → Jaune, Marron
- Je cherche à comprendre d'où elle vient. → Vert, Bleu

**2. Tu ne garderas qu'un seul objet, toute ta vie. Lequel ?**
- Une lame. → Obsidian, Or
- Une clé à molette. → Orange, Vert
- Un pinceau. → Violet, Blanc
- Une bourse pleine. → Argent, Cuivre

**3. Quelqu'un en qui tu avais confiance te trahit.**
- Je frappe. Tout de suite. → Obsidian, Rouge
- J'exige un jugement. → Blanc, Cuivre
- Je le ruine, sans qu'on remonte jusqu'à moi. → Argent, Rose
- Je pars, loin, sans me retourner. → Bleu, Violet

**4. Ferme les yeux. Où te sens-tu chez toi ?**
- Sous terre, dans la chaleur de la roche. → Rouge, Orange
- Dans le ciel, au-dessus de tout. → Bleu, Or
- Au cœur d'une cité, dans la foule. → Rose, Argent
- Dans le silence d'un temple ou d'un laboratoire. → Jaune, Blanc

**5. Que gravera-t-on sur ta tombe ?**
- « Ne recula jamais. » → Obsidian, Gris
- « Sauva bien des vies. » → Jaune, Marron
- « Bâtit ce qui dure encore. » → Vert, Violet
- « Changea le monde. » → Or, Rose

**6. Un supérieur te donne un ordre injuste.**
- J'obéis. Sans ordre, tout s'écroule. → Gris, Cuivre
- Je fais mine d'obéir, et je fais autre chose. → Rose, Vert
- Je refuse, en face. → Obsidian, Rouge
- Je négocie un arrangement. → Argent, Marron

**7. Le sommeil ne vient pas. Que fais-tu de ta nuit ?**
- Je répare ce qui est cassé. → Orange, Marron
- Je fais ma ronde : tout doit être en ordre. → Cuivre, Gris
- Je regarde les étoiles. → Bleu, Violet
- Je veille un malade, ou je prie. → Jaune, Blanc

### Question supplémentaire pour les Or ✅
Choix (pas de hasard) : **promotion précédente** ou **promotion de Darrow** (voir section 7).

### Script de vérification de la distribution

✅ Repris en Java (G4.3) : les paires sont dans `couleur/TestPersonnalite`, et le test unitaire `TestPersonnaliteTest` refait ce calcul à chaque build (il échoue si une Couleur sort de 6,8 % à 7,5 %, et affiche alors toute la distribution). C'est lui qui fait foi ; le script ci-dessous reste comme trace. Résultat actuel : de 6,82 % (Rouge) à 7,46 % (Or).

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
  - ✅ Mis en place (G5.1) : un PNJ de Couleur est un villageois avec une Couleur. Il porte les mêmes repères que le joueur, teintés (iris, cheveux sur le crâne, sigils sur les mains croisées ; les chapeaux de métier couvrent une partie des cheveux). Son métier est verrouillé (il ne le perd pas faute de poste de travail). Un villageois sans Couleur reste ordinaire.
  - ✅ Métiers (validés le 26/10 ; les 13 métiers de Minecraft ne couvrent pas les 14 Couleurs, deux sont partagés) : Or sans métier (il dépend des autres pour crafter), Argent bibliothécaire (registres et comptes), Cuivre cartographe (cadastre, titres de propriété), Blanc bibliothécaire (justice, enchantement), Bleu cartographe (navigation), Jaune prêtre (médecine, potions), Vert outilleur (technologie), Violet berger (teintures), Orange armurier (mécanique), Gris archer (combat à distance), Obsidian tanneur (tribus des pôles, fourrures), Marron fermier (agriculture), Rose sans métier (espionnage), Rouge maçon (pierre, mine). « Sans métier » = le villageois « nitwit » de Minecraft, sans commerce : un villageois sans métier ordinaire prendrait celui du premier poste de travail posé à côté (vérifié en jeu).

### Actions qui bougent les jauges (G5.2)
- ✅ Groupes de PNJ selon leur Couleur : **élite** (Or, Argent, Cuivre, Blanc), **ordre** (Gris, Obsidian), **peuple** (toutes les autres).
- ✅ Actions et gestes :
  - **Tuer** un PNJ de Couleur.
  - **Aider** : accroupi, clic droit sur un PNJ de Couleur avec de la **nourriture** en main (pour l'instant) ; elle lui est donnée. Une fois par PNJ et par jour de jeu.
  - **Voler** : prendre des objets dans un coffre (ou tonneau) d'un camp ; compté une fois par ouverture. **Casser** un coffre d'un camp compte aussi comme un vol (G5.3 : sinon on vide un poste en perçant un mur). Les coffres doubles ne sont pas encore pris en compte pour le vol.
  - ✅ **Soutien d'un vol : une fois par coffre et par jour de jeu**, pour chaque joueur, comme l'aide (décidé le 27/10 en G6.1). Sinon déposer son propre charbon dans un coffre de la Société et le reprendre était une ferme à Soutien (40 de Soutien en une vingtaine de tours, sans témoin). Loyauté et Suspicion s'appliquent à chaque vol : voler reste risqué, mais ne rapporte plus en boucle. Même règle pour le tort d'un vol au Rising. Pour une explosion qui détruit plusieurs coffres d'un camp, le Soutien compte si l'un d'eux ne l'a pas encore été dans la journée.
  - **Dénoncer** : accroupi, clic droit **à mains vides** sur un PNJ Gris (aider = avec un objet, dénoncer = sans, pas d'ambiguïté) ; le PNJ du Rising le plus proche, à moins de 16 blocs, est arrêté (il disparaît). Les PNJ d'une cellule ne sont pas atteints : seule la dénonciation de la cellule, carte en main, les arrête (G5.10). La baisse de Suspicion d'une dénonciation (PNJ ou cellule) ne compte qu'une fois par jour de jeu, jamais pendant une traque (G5.10).
  - La **Suspicion retombe d'elle-même** chaque jour de jeu (sinon tout joueur du Rising finit traqué).
- 🟡 Valeurs de base (Loyauté / Soutien / Suspicion), à régler en jeu, toutes dans `factions/EffetsActions`. Pour tuer et voler, c'est l'effet **avec un témoin ordinaire** ; les témoins (G5.5, plus bas) le modifient :

| Action | Élite | Ordre | Peuple |
|---|---|---|---|
| Tuer | −15 / +5 / +15 | −10 / +4 / +10 | −3 / −5 / +5 |
| Aider | +2 / 0 / 0 | +2 / 0 / 0 | 0 / +1 / 0 |

  - Voler : coffre de la Société −5 / +2 / +5 ; coffre du Rising 0 / −5 / 0.
  - Dénoncer : +5 / −10 / −5.
  - Déclin : Suspicion −2 par jour de jeu (24 000 ticks).
- Les camps des PNJ et des coffres sont posés par les structures (G5.3, G5.4) ; sinon `/rr pnj camp` et `/rr coffre`.

### Témoins (G5.5)
- ✅ **Qui compte** : un PNJ de Couleur vivant, à moins de 16 blocs, avec une ligne de vue sur le joueur au moment de l'action. Ne comptent pas : les PNJ du Rising (ils ne parlent pas à la Société), la victime, les villageois sans Couleur, les autres joueurs. S'il y a plusieurs témoins, un Gris l'emporte. Le témoin compte au moment de l'action : le tuer ensuite n'efface rien.
- ✅ **Actions concernées** : tuer, voler (ouvrir ou casser le coffre), effraction d'un poste. Aider et dénoncer ne le sont pas : le PNJ aidé ou le Gris à qui l'on parle est lui-même témoin.
- ✅ **Silence d'une Couleur** (effet de jalon, G6.3) : ses PNJ ne sont plus témoins des actions **contre la Société** : voler un coffre de la Société (ouvrir, casser, faire sauter), l'effraction d'un poste, tuer un PNJ de l'élite ou de l'ordre (chasseurs compris). Ils restent témoins de ce qui les touche : tuer un PNJ du peuple, voler un coffre du Rising. Les Rouges ne couvrent pas celui qui s'en prend à eux.
- ✅ Le joueur est prévenu : « Quelqu'un a tout vu » ou « Un Gris a tout vu » (formulé pour ne pas s'accorder avec le joueur).
- 🟡 **Effet** (dans `EffetsActions.selonTemoins`) ; le Soutien ne change jamais, le Rising apprend tout par les siens :

| Témoin | Loyauté | Soutien | Suspicion |
|---|---|---|---|
| Aucun | 0 | inchangé | moitié, arrondie au-dessus (agir en cachette n'est pas gratuit) |
| Ordinaire | inchangée | inchangé | inchangée |
| Au moins un Gris | inchangée | inchangé | doublée |

  Ce que ça donne avec les valeurs de base :

| Action | Sans témoin | Témoin ordinaire | Gris |
|---|---|---|---|
| Tuer un PNJ de l'élite | 0 / +5 / +8 | −15 / +5 / +15 | −15 / +5 / +30 |
| Tuer un PNJ de l'ordre | 0 / +4 / +5 | −10 / +4 / +10 | −10 / +4 / +20 |
| Tuer un PNJ du peuple | 0 / −5 / +3 | −3 / −5 / +5 | −3 / −5 / +10 |
| Voler la Société | 0 / +2 / +3 | −5 / +2 / +5 | −5 / +2 / +10 |
| Voler le Rising | 0 / −5 / 0 | 0 / −5 / 0 | 0 / −5 / 0 |
| Effraction d'un poste | 0 / 0 / +3 | 0 / 0 / +5 | 0 / 0 / +10 |

- ✅ **Effraction** : être reconduit hors d'un poste où l'on est entré sans y être admis (mur percé, fenêtre). Entré admis puis tombé sous le seuil une fois dedans, ce n'en est pas une. Le commis Cuivre est presque toujours témoin.

### Patrouilles et contrôles (G5.5)
- ✅ **Patrouille** : un PNJ Gris qui suit une ronde (des points dans l'ordre, en boucle, avec 3 secondes d'arrêt à chacun), de jour comme de nuit. Créée par `/rr patrouille creer` en attendant les garnisons (G8.1). La ronde (`factions/Ronde`) ne sait pas qui marche : le joueur Gris recevra les mêmes rondes en G8.4 ; le contrôle (`factions/Controle`) servira au joueur Gris qui contrôle un suspect en G8.3.
- 🟡 **Paliers de Suspicion** (seuils dans `factions/Acces`) : 0 à 39, les Gris t'ignorent ; **40 à 99, contrôle** ; 100, traque (G5.6).
- ✅ **Contrôle** : un Gris en patrouille qui voit un joueur au palier de contrôle à moins de 12 blocs l'arrête (« Halte ! Contrôle. »), vient à lui, puis le contrôle pendant 5 secondes avant de reprendre sa ronde. Un joueur n'est pas contrôlé plus d'une fois toutes les 5 minutes de jeu. Le contrôle est abandonné, sans effet, si le Gris n'arrive pas en 20 secondes, ou s'il disparaît (tué, retiré) en plein contrôle (corrigé en G6.3 : le joueur était puni comme un fuyard à sa reconnexion suivante). Pendant une alerte des patrouilles (G6.3), la détection, l'attente et le seuil changent (voir « Effets des jalons »).
  - **Obéir** (rester jusqu'au bout) : Suspicion −5, **une fois par jour de jeu au plus** (sinon on se blanchit en allant voir les patrouilles). Message « Contrôle terminé : tu peux circuler ».
  - **Fuir** : s'éloigner à plus de 16 blocs du Gris, **dès le « Halte »**, approche comprise : Loyauté −5, Suspicion +10. Une seule limite de distance, plus grande que la distance de détection, pour qu'on ne soit pas en fuite dès le « Halte ».
  - **Se déconnecter** pendant un contrôle (ou passer un portail) compte comme une fuite (correction du 27/10). La fuite d'un joueur déconnecté est comptée à son retour. Mourir pendant un contrôle l'annule, sans effet.
  - Attaquer le Gris : c'est « tuer un PNJ de l'ordre », avec un Gris témoin. Le contrôleur tué par son suspect est lui-même ce témoin, même seul avec lui (−10 / +4 / +20 avec les valeurs de base ; corrigé le 28/10 : il était exclu des témoins comme toute victime, et le tuer coûtait moins que fuir). Le contrôle s'arrête là, sans fuite en plus. Un contrôleur tué par un monstre ou un autre joueur : le contrôle s'arrête sans effet pour le suspect.
- 🟡 Distances et durées dans `factions/Controle`, effets dans `EffetsActions`.
- Pour plus tard : une fouille qui trouverait les objets volés (marqués depuis G5.9, voir Commerce) et la contrebande du Rising (section 9).

### Poste de la Société (G5.3)
- ✅ Structure générique : une salle en quartz (7×7) avec un comptoir, un PNJ Cuivre (camp Société) et un coffre de la Société. Construite par `/rr structure poste` en attendant la génération (G7.1).
- 🟡 **Seuil d'entrée : Loyauté 40 ou plus** (à régler en jeu, dans `factions/Acces`). Égal au plafond du Rouge : un Rouge irréprochable entre tout juste, la moindre faute (un vol de la Société) le refoule jusqu'à ce qu'il se rattrape. Un nouveau joueur (50) entre ; tuer un PNJ de l'élite (−15) suffit à être refoulé.
- ✅ Refoulement : la porte ne s'ouvre pas, avec le message « La Société te refuse l'entrée » ; l'intérieur est surveillé (toutes les demi-secondes) : un joueur refusé qui s'y trouve quand même (mur percé, fenêtre, Loyauté tombée une fois dedans) est reconduit devant l'entrée. Essayer la porte n'a pas d'effet sur les jauges ; entrer sans être admis est une effraction (G5.5, voir Témoins).

### Cellule du Rising (G5.4)
- ✅ Structure générique : une vraie salle du monde (5×3×5, sol en dalles d'ardoise des profondeurs, lanternes) noyée dans la pierre, avec un PNJ Rouge (camp Rising) et un coffre du Rising. Construite par `/rr structure cellule` en attendant la génération dans les mines (G7.5).
- 🟡 **Seuil d'entrée : Soutien 20 ou plus** (à régler en jeu, dans `factions/Acces`). Atteignable sans tuer : 20 dons de nourriture au peuple (+1 chacun, une fois par PNJ et par jour : environ 3 jours avec 7 PNJ), ou un mélange de dons et de vols à la Société (+2 chacun, mais Loyauté et Suspicion en pâtissent). Par l'aide seule, la Loyauté ne bouge pas : l'agent double reste admis au poste de la Société. Une dénonciation (−10) fait perdre l'accès. Même seuil pour le Rouge pour l'instant ; son avantage (« accès aux cellules ») viendra avec les cellules dans ses mines (G7.5).
- ✅ **Entrée cachée** : deux blocs de pierre ordinaires (vanilla, même F3 n'y voit que de la pierre) dans le mur. Accroupi, main vide, clic droit : un joueur admis passe dans la salle. Un joueur refusé n'apprend rien : il ne se passe rien, comme sur n'importe quelle pierre (pas de message, pas de son).
- ✅ **Repère** : quelques particules rouges sur l'entrée toutes les 2 secondes, envoyées par le serveur aux seuls joueurs admis à moins de 12 blocs.
- ✅ **Sortie** : même geste depuis l'intérieur, quel que soit le Soutien. **Intérieur surveillé** : un joueur refusé dans la salle (Soutien tombé une fois dedans, mur percé) est mis devant l'entrée, avec le message « Le Rising te met dehors » (il connaît déjà la cellule).
- ✅ **Trouver une cellule en creusant est permis** : on en fera du contenu avec la dénonciation d'une cellule (G5.10), la traque (G5.6) et le Triomphe.
- ✅ **Planque** (G5.10, décidé le 27/10) : un second coffre au fond de la salle, **sans camp** (à gauche ; le coffre du Rising est à droite). Les affaires du joueur : ni marque, ni vol ; les entonnoirs ne la vident pas. En multijoueur, elle est commune aux joueurs admis dans la cellule (une planque personnelle, façon coffre de l'Ender, reste possible plus tard). Saisie (vidée) quand la cellule tombe : dénoncer la cellule où l'on cache ses affaires, c'est les perdre.

### Dénoncer une cellule (G5.10, décidé le 27/10)
- ✅ **Cellule connue** : le joueur s'est trouvé dans la salle, par l'entrée ou en creusant (la surveillance de l'intérieur l'enregistre, admis ou non).
- ✅ **Geste** : accroupi, clic droit sur un Gris **avec une carte en main** (vierge ou remplie : on lui montre l'emplacement, il garde la carte). La cellule connue la plus proche du Gris, à moins de 32 blocs, tombe. Aucune cellule connue à portée : rien ne se passe, la carte reste. À mains vides, c'est toujours un PNJ du Rising qui est dénoncé, jamais ceux d'une cellule : une trahison à −25 doit être voulue, pas un accident.
- ✅ **La cellule tombe** : ses PNJ sont arrêtés (le Fils disparaît, sa boutique avec lui), le coffre du Rising et la planque sont saisis (vidés, sans camp), les lanternes retirées, **l'entrée défoncée** (deux blocs d'air). Il reste un trou dans la roche et une salle vide et sombre, où l'on entre librement. Plus de geste, de repère ni de surveillance ; aucun message : le décor parle.
- 🟡 **Jauges** : +10 / −25 / −10. La baisse de Suspicion ne compte qu'**une fois par jour de jeu** (toutes dénonciations confondues) et jamais pendant une traque : un loyaliste à 0 de Soutien qui fouille les mines ne se blanchit pas à volonté.
- 🟡 **Ce qu'apprend le Rising** : il apprend tout par les siens (comme pour les témoins). En plus du −25, **quarantaine de 3 jours de jeu** : toutes les cellules et leurs boutiques refusent le dénonciateur, quel que soit son Soutien (ses planques ailleurs lui sont donc inaccessibles).
- 🟡 **Cellule repérée pendant une traque** : la Société y fait une **descente un jour de jeu plus tard** (la cellule tombe comme dénoncée ; le temps de vider sa planque). Le joueur qui l'a fait repérer perd 10 de Soutien **au moment de la descente** (une imprudence, pas une trahison : ni Loyauté ni Suspicion). Pas sur le moment : sinon un joueur juste au seuil serait mis dehors de la cellule, sous les yeux des chasseurs. Hors ligne au moment de la descente, il perd ses 10 de Soutien à son retour, comme la fuite d'un contrôle (G6.1).
- ✅ **L'interrogatoire ne révèle rien pour l'instant** : une arrestation coûte déjà ; y ajouter la chute d'une cellule sans choix du joueur lui retirerait toute prise. Piste : un choix « parler / se taire » à l'interrogatoire (écran), plus tard.
- 🟡 **Fils tué** (par le joueur ou un monstre) : un remplaçant arrive après 3 jours de jeu sans Fils (la cellule retrouve sa boutique).
- ✅ **Creeper** : une explosion de creeper n'a pas de responsable (voir Commerce).
- ✅ **Entonnoirs** : un entonnoir (posé ou sur wagonnet) ne vide pas un coffre ou un tonneau d'un camp, ni un coffre double dont une moitié appartient à un camp. Il peut toujours le remplir (c'est un don).

### Traque (G5.6)
- ✅ **Déclenchement** : la Suspicion atteint 100, quelle qu'en soit la cause. Le joueur voit le titre « Les Gris te traquent » (« Sème-les ou cache-toi ») et entend le cor des raids. Une barre de boss est rouge quand un chasseur le voit, jaune sinon ; elle se remplit vers la victoire.
- ✅ **Chasseurs** : des PNJ Gris qui apparaissent à 24-32 blocs, hors de vue si possible ; les Gris en patrouille à moins de 48 blocs s'y joignent, puis reprennent leur ronde. Ils courent, encaissent mieux qu'un villageois (vie et armure en plus) et ne paniquent pas quand on les frappe. Quand ils voient le joueur, ils le poursuivent ; sinon, ils fouillent autour de sa dernière position connue (une ronde autour de ce point).
- ✅ **Tir étourdissant** : le Gris est la Couleur du combat à distance. Un chasseur qui voit le joueur à moins de 20 blocs tire une flèche toutes les 2 secondes ; touché, le joueur est fortement ralenti 2 secondes. Un joueur perché est donc atteint.
- ✅ **Tuer un chasseur** ne rapporte pas de Soutien (sinon la traque rapporte : −10 / 0 / +10 avec témoin ordinaire, et les autres chasseurs sont des Gris). Un chasseur tué n'est remplacé que par les renforts.
- ✅ **Gagner** :
  - **semer les Gris** : 60 secondes de suite sans qu'aucun chasseur ne voie le joueur ;
  - **se cacher dans une cellule du Rising** (si on y est admis) : sans être vu, victoire immédiate ; sous les yeux d'un chasseur, la cellule est **repérée** (descente de la Société un jour de jeu plus tard, Soutien −10 à ce moment : G5.10) et la traque continue (les murs cachent le joueur, il peut y semer les Gris) ;
  - **abandon au bout de 10 minutes** : victoire seulement si aucun chasseur ne voit le joueur à ce moment ; sinon les Gris se retirent sans que ce soit une victoire (un joueur perché, intouchable, ne gagne pas).
- ✅ **Perdre : l'arrestation** — un chasseur au contact (2,5 blocs, en le voyant : pas à travers un mur) pendant 3 secondes de suite. L'arrêté est emmené devant le poste de la Société le plus proche (à moins de 500 blocs, sinon sur place) et **interrogé 30 secondes** : tenu sur place, ralenti, aveuglé, et **il ne peut ni subir de dégâts ni mourir**. Rien n'est perdu : ni objets, ni expérience, ni point de réapparition, et ce n'est pas une mort. Loyauté −15.
- ✅ La traque et l'interrogatoire sont enregistrés sur le joueur : se déconnecter les met en pause, ils reprennent à son retour (de nouveaux chasseurs arrivent). Mourir pendant une traque ne l'arrête pas.
- 🟡 Valeurs (toutes dans `factions/Traque`, sauf la Loyauté de l'arrestation dans `EffetsActions`) : 3 chasseurs, un renfort par minute jusqu'à 6 ; vue 48 blocs ; tir à 20 blocs toutes les 2 secondes, ralentissement de 2 secondes ; semer en 60 secondes ; abandon à 10 minutes ; interrogatoire de 30 secondes.
- ❓ **Point ouvert, à régler en jouant** : une flèche toutes les 2 secondes et 2 secondes de ralentissement peuvent tenir le joueur ralenti sans interruption (surtout à plusieurs chasseurs). Pistes : une immunité courte après un ralentissement, ou des tirs seulement quand le joueur est hors d'atteinte (perché, de l'autre côté d'un obstacle).
- 🟡 **Suspicion après la traque** (tous restent au palier de contrôle) :

| Issue | Suspicion | Autres effets |
|---|---|---|
| Gagnée (semés, cellule, abandon hors de vue) | 70 | aucun |
| Retrait des Gris (abandon, toujours vu) | 90 | aucun |
| Arrêté | 50 | Loyauté −15, 30 s d'interrogatoire, emmené au poste |

- Relèvent d'autres tickets : les raids sur les cellules et des traques plus dures après certains jalons (G6.3, Triomphe G16.2) ; chasseurs partis des garnisons et prison (G8.1) ; fouille et objets volés confisqués (plus tard) ; traques entre joueurs (multijoueur, plus tard). Pour l'instant, les chasseurs apparaissent en surface (hauteur du terrain) ; dans la mine, ils apparaîtront dans les galeries (G7.2.4).

### Commerce (G5.9, décidé le 27/10)
- ✅ **Monnaie** : la Société paie et vend en **crédits** (objet du mod, la monnaie du lore ; elle seule en donne). Le **Rising ne prend pas d'argent** : il troque contre ce qui manque aux mines (poudre, fer, silex). Écartés : les émeraudes de Minecraft (villageois ordinaires et fermes à émeraudes rendraient les prix dérisoires, et un Rising qui prend de l'argent sonne faux), le troc partout (chaînes d'échanges impossibles à équilibrer, rien à gérer pour l'Argent).
- ✅ **Qui vend** : le commis Cuivre du poste et le Fils Rouge de la cellule (marqués « boutique » à la construction ; `/rr boutique set` pour les mondes déjà construits). Clic droit normal : l'écran de commerce de Minecraft ; accroupi, les gestes d'aide et de dénonciation ne changent pas.
- ✅ **Offres refaites à chaque ouverture, pour ce joueur** : seulement les articles de son palier (les autres n'apparaissent pas, les paliers du Rising restent secrets), avec ses échanges du jour. Les échanges de métier du villageois (cartes du cartographe, argile du maçon) disparaissent pour ces deux PNJ ; les remises de Minecraft (réputation, villageois guéri, Héros du village) et l'expérience ne jouent pas.
- ✅ **Stock et plafond par joueur et par jour de jeu**, sans poste de travail : un compteur d'échanges gardé sur le joueur, remis à zéro chaque jour. Il vaut aussi pour ce que la Société **achète** (sinon une ferme de blé donnerait des crédits sans limite).
- ✅ **Accès** : le seuil de la structure (Loyauté 40, Soutien 20). Un joueur entré par effraction n'achète rien : « La Société ne commerce pas avec toi ».
- ✅ **Acheter et vendre ne touchent jamais aux jauges** (sinon on achèterait de la Loyauté ou du Soutien). Donner au Rising contre un fumigène n'est pas de l'aide.
- ✅ **Pas de boucle d'argent infini** :
  - la Société n'achète que de la production brute et ne vend que des produits finis : aucun objet n'est sur les deux listes ;
  - le Rising ne paie jamais en crédits : rien ne revient vers la Société ;
  - **objets volés marqués** : tout ce qui est dans un coffre d'un camp porte la marque « Volé à la Société » (ou « au Rising ») dès qu'on l'ouvre, et ce qui tombe d'un coffre d'un camp détruit, quelle qu'en soit la cause (casse, explosion, commande). **Chaque camp refuse ses propres biens volés**.
  - **Explosion** (correction du 27/10) : faire sauter un coffre d'un camp est un vol, une fois par explosion et par camp, avec les témoins habituels, quand on remonte au joueur responsable : celui qui a allumé la TNT (ou tiré la flèche enflammée). Un creeper n'a pas de responsable (un joueur poursuivi près d'un poste ne l'a pas voulu), une TNT allumée par la redstone non plus : contenu marqué, pas de vol. Un objet volé ne s'empile plus avec les autres (la fouille le retrouvera) ;
  - la Société n'achète jamais de contrebande.
- 🟡 **Catalogue** (valeurs dans `factions/Catalogue`, seuils dans `factions/Acces`) :

| Boutique | Palier | Article | Prix | Par jour |
|---|---|---|---|---|
| Société vend | Loyauté 40 | 3 pains | 1 crédit | 8 |
| Société vend | Loyauté 40 | 8 torches | 1 crédit | 4 |
| Société vend | Loyauté 40 | carte vierge | 2 crédits | 2 |
| Société vend | Loyauté 60 | pioche en fer, épée en fer | 6 crédits | 1 chacune |
| Société vend | Loyauté 60 | plastron en fer | 10 crédits | 1 |
| Société vend | Loyauté 60 | bouclier | 4 crédits | 1 |
| Société achète | Loyauté 40 | 8 charbons → 1 crédit | | 4 (plafond) |
| Société achète | Loyauté 40 | 4 fer brut → 1 crédit | | 4 |
| Société achète | Loyauté 40 | 2 or brut → 1 crédit | | 2 |
| Société achète | Loyauté 40 | 16 blés → 1 crédit | | 3 |
| Société achète | Loyauté 40 | 16 pommes de terre → 1 crédit | | 3 |
| Société achète | Loyauté 40 | 2 hélium-3 → 3 crédits (G7.2.2) | | 4 |
| Rising | Soutien 20 | 1 fumigène | 2 poudres + 1 lingot de fer | **2** |
| Rising | Soutien 40 | 3 chausse-trappes | 4 lingots de fer + 1 silex | 1 |
| Rising | Soutien 60 | (réservé : insigne d'Arès, appel aux Fils) | | |

  Soit 16 crédits par jour au plus en vendant à la Société, 28 avec l'hélium-3 de la mine (G7.2.2). Palier 80 de la Société réservé à son équipement (razor pour l'Or, armures de l'Orange, G10).
- ✅ **Prix du fumigène** : il efface un témoin. Le frein principal est le stock (2 par jour de jeu) ; le prix (poudre et fer) donne du poids à chacun. S'il reste trop fort, baisser le stock à 1.
- ✅ **Prix qui ne bougent pas avec les jauges** : elles n'ouvrent que des paliers.
- ✅ **Crochet** : prix final = prix de base × les facteurs des **modificateurs de prix** (`Boutiques.declarerModificateur`), arrondi au-dessus, au moins 1. Le facteur porte toujours sur ce que le joueur donne (sous 1, meilleure affaire dans les deux sens). Les meilleurs échanges de l'Argent (G9.4) et le charme du Rose (G9.16) déclareront chacun le leur.
- ✅ **Quand la cellule tombe (G5.10)** : sa boutique disparaît avec son PNJ arrêté ; ce que le joueur y a acheté lui reste (sauf une fouille future). Les autres cellules gardent leur boutique.
- ❓ **Limites connues, à revoir avec la fouille** :
  - **Blanchiment par craft** : un objet volé passé dans un craft réversible (charbon ↔ bloc de charbon, fer ↔ bloc de fer, blé ↔ botte de foin) perd sa marque, et la Société le rachète. Non corrigé.
  - Les autres PNJ de Couleur gardent leurs échanges de Minecraft, sans réapprovisionnement faute de poste de travail.
  - Côté écran : le client ne connaît pas la marque des boutiques ; un objet volé posé en paiement peut montrer un résultat que le serveur refuse au clic.

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

### Génération de Mars (décidée le 28/10, découpage de G7.1 et G7.2)
- ✅ **Mars reste l'Overworld**, avec les biomes vanilla, et un type de monde « Mars » par défaut à la création (G7.1.2). Les tests en jeu gardent leur type de monde.
- ✅ **Plan des régions** tiré de la graine, en ne lisant que les biomes et la hauteur du terrain (aucun chunk généré), puis **sauvegardé** dans l'état du monde au premier démarrage : une version suivante du mod ne déplace pas les régions d'un monde existant, et la génération lit le plan sauvegardé, jamais un recalcul. Un monde sans plan (tests, monde d'avant G7) marche comme avant : `/rr structure` et le reste ne dépendent pas des régions.
- ✅ **Tirage** (G7.1.1) : juste après le choix du point d'apparition d'un monde neuf avec du relief (un monde plat n'a pas de plan), environ 2 secondes à la création. Chaque région essaie 64 positions au hasard (de la graine) et garde la première sans défaut, sinon la moins mauvaise. Pendant sa recherche du point d'apparition, Minecraft a déjà préparé des chunks : ils ne recevraient plus les morceaux de la mine (trous, galeries coupées). Le tirage relève leur état réel : aucun chunk de l'emprise de la mine (son chunk de départ et 8 chunks autour, la portée d'une structure en 26.2) ne doit avoir passé l'étape des références de structures, ni son chunk de départ celle des départs. Mesuré sur 5 graines : références jusqu'à 3 chunks du point d'apparition, départs jusqu'à 11 ; la mine tombe à 238 à 393 blocs, et à 567 sur une graine (la mine s'éloigne par bandes de 200 blocs, jusqu'à 1 000, si rien ne convient entre 200 et 400). Debug : `/rr region list`, `/rr region tp <region> [joueur]`.
- 🟡 **Distances** : la mine à 200 à 400 blocs du point d'apparition (pas dessous : on la trouverait en creusant au spawn, et les joueurs sans lit y réapparaissent au-dessus), la garnison à environ 600, la cité à environ 900, les zones à environ 1 200 (bandes de 200 blocs : 500 à 700, 800 à 1 000, 1 100 à 1 300), au moins 300 blocs entre deux régions, les pôles au-delà de |z| = 3 000 (la région des pôles au nord, à z = −3 400, en face du point d'apparition). Pas de région dans l'océan ni un fleuve (ni à 48 blocs autour) ; la cité sur du terrain plat (au plus 12 blocs d'écart sur 64 × 64).
- ✅ **Pôles et transition** (G7.1.2, décidés le 28/10) : une source de biomes autour de celle de Minecraft (`monde/SourceBiomesMars`), dans le type de monde par défaut (on remplace `minecraft:normal` ; les tests en jeu, en monde plat, gardent le leur). Le relief ne change pas, seulement les biomes. Pas de mur : 🟡 une bande de 400 blocs (|z| de 2 600 à 3 000) où la part de froid monte de 0 à 100 % (biomes froids mêlés : plaine et taïga enneigées, pentes enneigées, océans, fleuves et plages gelés), puis des pôles gelés seulement au nord et au sud (plaine enneigée, pics de glace, pics gelés, océans gelés). Les endroits qui basculent d'abord suivent un bruit de Minecraft qui varie sur une centaine de blocs (son « étrangeté », propre à la graine) : des taches de froid aux bords naturels. Le monde reste stable (pas d'écran d'avertissement) sans mixin : en 26.2, Minecraft ne juge expérimentale que sa propre source avec d'autres réglages que ceux de l'Overworld. La région des pôles (Obsidian) est au nord, sur la terre ferme ; les autres régions restent hors de la bande.
- ✅ **Une seule grande mine (Lykos) par monde** en v1, générée par le code au chargement des chunks (pas de gros bloc posé d'un coup), avec l'hélium-3 (minerai et objet du mod) et des minerais vanilla plus riches.
- ✅ **Squelette de la mine** (G7.2.1, décidé le 28/10) :
  - 🟡 **4 niveaux**, sols à y = 8, −10, −28, −46 (un niveau trop près de la surface est retiré : le haut de la mine reste 18 blocs sous le terrain le plus bas de l'emprise). Sur chaque niveau, une grille de nœuds tous les 20 blocs (de −100 à +100 autour du centre) ; un arbre couvrant tiré de la graine, l'axe principal en premier (croix de 5 × 4 à travers le puits), 12 % de boucles, puis des culs-de-sac coupés jusqu'à 60 % de l'arbre, jamais un nœud obligatoire (puits, accès). Galeries de 3 × 3, étayées tous les 6 blocs (poteaux et poutre d'épicéa), hors des carrefours.
  - **Puits** : le principal au centre, de bas en haut, avec une échelle d'une traite (un pilier de bois la tient là où une galerie traverse sa paroi) ; 2 secondaires entre chaque paire de niveaux.
  - **Caverne de la colonie** sur le niveau 3, à 48 blocs du puits au bout de l'axe : 🟡 72 × 56 au sol, 22 de haut, en voûte au bord irrégulier ; zones notées pour la suite : 20 parcelles de 7 × 7 (G7.3), place de 20 × 20 et échafaud au centre (G7.10), apparition du Rouge près de l'entrée (G7.8).
  - **12 emplacements de cellules** (3 par niveau) laissés en roche pleine, au bout d'un cul-de-sac ou sur la paroi d'une galerie, à 40 blocs au moins de la caverne, 24 entre eux ; au moins 3 culs-de-sac sans cellule pour 1 avec, tous fermés par la même roche : un cul-de-sac ne trahit rien. G7.5 en construit quelques-uns, les autres sont la réserve.
  - **Bouchon** : 2 couches d'andésite polie en haut du puits principal, sous 🟡 3 blocs du terrain d'origine de la colonne du puits (son point le plus bas sur 3 × 3, lu dans le bruit du relief sans générer de chunk) : rien ne se voit en surface, et le puits mène bien vers le haut, même sur une butte (correctif du 28/10 : le bouchon se calait sur le terrain le plus bas autour du puits, à 28 blocs sous la surface sur une graine). Mesuré sur trois graines : 3 à 5 blocs sous la surface. Il se casse comme le reste.
  - **Ce qui croise le volume** : le relief est rempli de roche dans les boîtes de la mine (adaptation `encapsulate`, comme les trial chambers) : ni grotte de bruit ni aquifère ; pas de carvers dans les 17 × 17 chunks de l'emprise ; aucune autre structure (trial chambers, mineshafts, ancient cities...) ni salle de monstres dans l'emprise ; les forts restent (accès à l'End) et le plan des régions écarte la mine d'eux. Les filons passent avant la mine : les galeries les coupent, la coque remplace tout fluide par de la roche. ⚠️ Une décoration peut écrire dans le chunk voisin même quand la mine y est déjà posée (mesuré : une géode dans une galerie, une fois sur trois) : les décorations souterraines autres que les minerais (géodes, sources, lacs souterrains, concrétions, lichen) ne se posent pas dans l'emprise et un chunk autour, sous y = 32 et à 32 blocs du puits principal. Les minerais restent : ils ne remplacent que de la roche. La surface ne change pas.
  - ⚠️ **La boîte du puits s'arrête 14 blocs sous le terrain le plus bas autour du puits**, pas au bouchon : le remplissage du relief déborde de 12 blocs et ferait monter le terrain (mesuré : une butte jusqu'à 5 blocs autour du puits). Au-dessus, la coque de 2 blocs du puits, posée à la place de tout vide et de tout fluide, garde la colonne fermée (vérifié sur trois graines).
  - **Coût** : 0,2 ms par chunk en moyenne pour la pose, 2 ms au plus ; le serveur reste sous 10 ms par tick pendant la génération des 289 chunks.
- ✅ **Salles et ressources** (G7.2.2, décidé le 28/10). Point de départ mesuré (graine 20261028, minerais vanilla seuls dans le volume de la mine) : 7,7 km de galeries ; minerais visibles depuis la mine (qui touchent son vide) : charbon 48, fer 251, cuivre 112, or 106, redstone 144, lapis 105, diamant 89 ; presque pas de charbon sous y = 0 (37 blocs dans tout le volume).
  - 🟡 **Salles d'extraction**, une par case libre de la grille (carré de 20 × 20 entre les galeries), ouvertes de côté sur une galerie, jamais au bout d'un cul-de-sac, à 24 blocs au moins de toute cellule (et 2 blocs de roche), hors de la caverne et des coques des puits. Trois formes : **chambre à piliers** (13 × 13 ou 15 × 15, 5 de haut, 4 piliers de roche de 3 × 3), **front de taille** (5 de large, 4 de haut, 13 à 15 de long, le long d'une galerie derrière un mur d'un bloc, deux ouvertures, étayé), **salle de forage** (ronde, 13 de diamètre, voûte de 8, cadre de bois au centre ; niveaux 3 et 4). 8 par niveau (32) : niveau 1, 5 chambres et 3 fronts ; niveau 2, 4 et 4 ; niveau 3, 3, 3 et 2 forages ; niveau 4, 2, 2 et 4 forages. Avec moins de niveaux, les lignes du bas, pour 32 en tout.
  - 🟡 **Épuisement** : à moins de 40 blocs de la caverne de la colonie et du chemin entre le puits principal et la caverne, aucun filon ajouté (galeries travaillées depuis des générations). Pas toute la croix de l'axe principal : 40 blocs de part et d'autre de ses deux branches, sur une grille de 200 × 200, auraient vidé près des deux tiers de la mine. La richesse vient en s'éloignant et en descendant.
  - 🟡 **Filons riches**, posés par la mine, en partie dans un mur (on les voit, on les suit en creusant) : la moitié d'un filon le long du mur, l'autre dans la roche, chaque filon dans la tranche de son niveau (8 blocs sous le sol, 9 au-dessus). Charbon 48 filons de 20 à 30 blocs, fer 36 de 14 à 20, cuivre 16 de 15 à 25, or 14 de 8 à 12, redstone 14 de 10 à 14 (`monde/FilonsDeMine`). Mesuré sur les trois graines du test : charbon 605 à 669 visibles, fer 550 à 724, cuivre 267 à 338, or 180 à 200, redstone 241 à 262 ; fer du volume ×1,03 à ×1,05 ; diamants visibles ×1,07 à ×1,16. Visibles visés : charbon ≥ 400 (tous niveaux), fer ≥ 500 (niveaux 2 à 4), cuivre ≥ 220 (1 et 2), or ≥ 160 (3 et 4), redstone ≥ 210 (4). Lapis et diamant inchangés, pas d'émeraude. Plafonds : diamants visibles ≤ 1,2 × vanilla, fer du volume ≤ 1,25 × vanilla : c'est le fer visible qui double, pas le fer total ; les montagnes restent plus riches en fer, la surface garde le bois, la nourriture et les émeraudes.
  - ✅ **Hélium-3** (bloc « Minerai d'hélium-3 », variante deepslate, dureté 4,5, pioche en pierre, Fortune, 🟡 lumière 3 ; objet « Hélium-3 ») aux niveaux 3 et 4 : salles de forage et poches dans les galeries lointaines (≥ 60 visibles), plus 🟡 environ 300 blocs dispersés dans la roche, en petits filons à trouver en creusant (≥ 400 dans le volume). Mesuré : 104 à 110 visibles, 431 à 440 dans le volume. Textures provisoires (ardoise des profondeurs et cristaux cyan, éclat cyan). Avec la vente, 150 blocs s'épuiseraient en 2 à 3 semaines de jeu, plus vite à plusieurs.
  - ✅ **Usage de l'hélium-3 en v1** : **vendu à la Société** (ce qu'elle veut de Mars) : le commis Cuivre l'achète, 🟡 2 hélium-3 pour 3 crédits, 4 ventes par jour, sans toucher aux jauges (règle du commerce). Plus tard, ingrédient des autres Couleurs (vol du Bleu, G8.7 ; gravBoots de l'Orange, G10.4). Écarté : combustible et lumière (les filons de charbon règlent le noir du fond, et brûler une ressource finie n'a pas de sens).
  - ✅ **Coffres d'outils**, 🟡 dans une salle sur deux (12 à 20), contre un mur, jamais dans une galerie ni près d'une cellule. **Sans camp, même après G7.3** : ce sont les restes des mineurs, pas les biens de la Société ; les prendre n'est pas un vol. Contenu (table de butin) : une pioche en pierre usée, des torches, du pain ou des pommes de terre cuites, des planches d'épicéa ; souvent du charbon, des bâtons, une pelle en pierre, parfois un seau ; rarement 1 à 4 pépites de fer. **1 ou 2 pioches en fer** très usées dans toute la mine (coffres rares, niveau 4) : dans les salles à plus de 80 blocs de la colonie, 2 au plus ; s'il n'y en a aucune (la colonie est grande), une seule, dans la salle du niveau 4 la plus éloignée. La distance ne baisse pas pour les autres graines. Jamais de lingot de fer, d'armure, de diamant, de crédits, de contrebande ni d'hélium-3. Le kit de départ du Rouge reste à G7.8. Tables de butin : `data/redrising/loot_table/chests/mine_outils.json` et `mine_outils_rare.json`. Mesuré sur les trois graines du test : 16 coffres, 11 à 18 pépites de fer en tout (1 à 2 lingots). Pioches en fer : voir le compte rendu de G7.2.2 dans la roadmap.
- ✅ **Vie de la mine** (G7.2.3, décidé le 29/10). Mesuré avant (mine toute sombre, graine 20261028, 2 minutes dans une galerie du niveau 2) : la limite de Minecraft (70 monstres) est atteinte en 30 secondes, dont 45 à 54 dans la mine (environ 25 creepers, des zombies, squelettes, araignées, quelques endermen), aucun à moins de 24 blocs du joueur, 13 à 19 entre 24 et 64 ; 1,7 ms par tick sans monstres, 5,2 ms avec. 49 wagonnets sur des rails : rien de mesurable.
  - 🟡 **Réseau éclairé** : l'axe principal de chaque niveau (une lanterne suspendue sous la poutre d'un étai sur deux, tous les 12 blocs), les paliers des puits, le chemin du puits à la colonie et l'entrée de la caverne (la caverne elle-même en G7.3). Depuis 1.18, un monstre n'apparaît qu'à une lumière de bloc de 0 : aucun n'y apparaît, mais ils peuvent y entrer en marchant. Le reste (galeries et salles) est sombre ; les salles de forage ont une lanterne sous leur cadre. Le danger va avec la richesse : loin de la colonie, riche, sombre et dangereux.
  - 🟡 **Plafond de monstres** : 16 par joueur, comptés dans le volume de la mine à 128 blocs ou moins de lui. Au-delà, une apparition naturelle dans la mine est refusée (mixin sur l'apparition naturelle de Minecraft, règle dans `monde/MonstresDeLaMine`). Mesuré (2 minutes dans une galerie sombre du niveau 2) : 35 apparitions dans la mine, aucune sur le réseau éclairé, 13 monstres dans la mine à la fin. ⚠️ Le temps par tick ne baisse pas (5,3 ms) : la limite de Minecraft (70 monstres) se remplit ailleurs, en surface et dans les grottes ; le plafond règle le danger de la mine, pas le coût.
  - ✅ **Ni creepers ni slimes dans la mine** : les creepers ouvriraient les murs (et, après G7.5, les cellules : 2 blocs de roche) ; les slimes apparaissent sans condition de lumière dans un slime chunk sous y = 40 (vérifié dans le code de 26.2), ils traverseraient le réseau éclairé. Les pitvipers arrivent en G7.4.
  - 🟡 **Rails** sur les 4 branches de l'axe de chaque niveau, au milieu ; un rail de propulsion tous les 16 blocs, alimenté par une torche de redstone noyée dans la roche dessous (pas de bloc de redstone : 9 poudres à qui le mine). Un **butoir** (bloc plein) au bout de chaque voie, avant le quai du puits principal (le puits troue le sol de l'axe) et au bout de la branche.
  - 🟡 **32 wagonnets** posés à la génération (2 par branche), **sur une voie de garage** à côté du passage, pas sur la voie de l'axe : un villageois ou un monstre qui touche un wagonnet monte dedans. Un joueur peut les emporter ; ceux qui partent ne sont pas recréés. Vérifié dans le code de 26.2 : un wagonnet n'embarque une créature qui le touche que s'il roule (au moins 0,1 bloc par tick) ; une créature qui pousse un wagonnet garé le met en route, d'où la voie de garage. La branche de la colonie n'a pas de voie (la caverne est à une douzaine de blocs du puits) : le compte de 32 est complété sur les voies les plus longues. Mesuré (graine 20261028) : 14 voies, 1 280 rails dont 80 de propulsion, 32 wagonnets ; un wagonnet lancé vers le puits arrive au butoir à 0,31 bloc par tick et s'arrête devant ; 3 ms par tick, wagonnets chargés.
  - 🟡 **Repères** : panneaux aux paliers des puits (« Niveau 2 », « Colonie → » sur la branche de la colonie, « Puits → niveau 3 » aux puits secondaires), texte traduisible ; une bande de couleur le long des murs de l'axe, une teinte par niveau (terre cuite blanche, jaune, orange, rouge). Rien dans les galeries sombres.
  - 🟡 **Points d'entrée des Gris** (pour G7.2.4) : les paliers des puits et les nœuds de l'axe, sur le réseau éclairé ; la traque choisira le plus proche à 24 blocs au moins, hors de vue, sur le niveau du joueur ou un voisin. Les Gris descendent par les puits. À trancher en G7.2.4 : les zombies attaquent les villageois, donc les Gris.
- ✅ **Pas d'ouverture naturelle** : les grottes vanilla sont bouchées dans le volume de la mine, et son puits principal est fermé par un simple bouchon. Pas de coque ni de bloc incassable : un Rouge, qui mine plus vite que tout le monde, peut toujours creuser vers le haut.
- ✅ **Découverte de la surface** (G7.9) : elle se déclenche à la **première exposition au ciel**, quel que soit le chemin (puits, galerie creusée, cellule). L'accès à la surface se tranche en G7.9.
- ✅ **Traque dans la mine** (G7.2.4) : les chasseurs y apparaissent sous terre, dans les galeries.
- ⚠️ **Cellules en nombre fini** dans une seule mine : il faudra des emplacements de réserve ou des cellules qui se reforment (G7.5), pour les cellules tombées et pour le retour des personnages en fuite (G6.4).

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

### Horloge du monde (décidée le 27/10 en G6.1)
- ✅ **Temps de jeu** : un compteur du mod, qui avance seulement quand au moins un joueur est connecté. Pas `gameTime` brut : sur un serveur dédié sans joueur (pause désactivée par l'admin, chunks forcés), le monde vieillirait sans personne. Ni le sommeil ni `/time` ne le font sauter ; en solo, la pause (Échap) l'arrête. Les règles « par jour » déjà en place (déclin de la Suspicion, stock des boutiques, aide) restent sur les jours de `gameTime`. Limite : un joueur absent de son clavier le fait avancer.
- ✅ **Soutien Rising cumulé : deux compteurs pour le monde**, qui ne font que monter : **aide** (somme des gains de Soutien) et **tort** (somme des pertes, en valeur absolue). Le net (aide − tort) se calcule. G6.2 pourra distinguer « accélérer » (aide, ou net) et « dévier » (tort).
  - Ce qui compte : la variation de Soutien de chaque **action d'un joueur**, avant bornage de la jauge (un joueur à 100 qui aide fait encore avancer le Rising ; un joueur à 0 qui dénonce lui fait encore du tort), y compris la cellule repérée (−10 à la descente, ou au retour).
  - Ce qui ne compte pas : les commandes de debug, la baisse quotidienne (elle ne touche pas le Soutien), les événements du monde (une cellule qui tombe : la dénonciation porte déjà ses −25 ; les événements de jalon, G6.3, sinon les jalons s'alimenteraient eux-mêmes).
  - Une perte puis un regain comptent chacun de leur côté : le net ne bouge pas, rien n'est compté deux fois, et regagner demande de vraies actions.
- 🟡 **Plafond par joueur et par jour de jeu : 30 d'aide et 30 de tort** comptés pour le monde (`jalons/ContributionDuJour`). Filet contre les boucles qu'on n'aurait pas vues ; en multijoueur, un seul joueur ne peut ni porter ni écraser le Rising en un jour. La jauge du joueur, elle, bouge normalement.
- ✅ **Multijoueur** : un seul compteur pour le monde (somme des joueurs). Par joueur, seulement le suivi du plafond du jour.
- ✅ **Multijoueur, tranché en G6.2** : un compteur unique pour le monde grandit plus vite à plusieurs joueurs. Les jalons le rapportent donc à la **présence** (temps de jeu × joueurs connectés) : leur rythme est un net par joueur et par jour, et leurs seuils valent en solo comme à plusieurs (voir « Moteur des jalons »).
- Sauvegarde : dans l'état du monde (schéma v2, voir section 10). Lecture et modification : `/rr etat`, `/rr etat temps`, `/rr etat presence`, `/rr etat soutien`, `/rr etat contribution`.

### Moteur des jalons (décidé le 27/10 en G6.2)
- ✅ **Déclaration** en quelques lignes (`jalons/Jalon`, enregistré par `Jalons.declarer`) : identifiant, époque, dépendances, échéance, choix de l'issue (`.issue(bilan -> ...)`) ou issue fixe (`.fixe(Issue.CANON)`, pour la pendaison d'Eo), époque ouverte (`.ouvre(...)`), lieu (`.surPlace(...)`). Les vrais jalons et leurs chiffres : G7.10 (Eo) et G16.1.
- ✅ **Mise en route** : dès que ses dépendances sont passées et que le monde est à son époque. On garde alors l'horloge du moment ; tout se mesure depuis cette mise en route (la « période »).
- ✅ **Rythme** : net de Soutien de la période par joueur et par jour de jeu (net ÷ présence, au moins un jour-joueur). Il ne dépend pas du nombre de joueurs. Limite : un joueur absent de son clavier dilue le rythme des autres.
- ✅ **Échéance** : délai = borné(base − pente × rythme, minimum, maximum), recalculé une fois par seconde. Aider le Rising avance le jalon, le combattre le recule ; le minimum l'empêche d'arriver tout de suite, le maximum garantit qu'il arrive.
- ✅ **Issue** : jugée au moment du jalon, sur le rythme de toute la période (`Bilan` : aide, tort, net, rythme, issues passées, zones, personnages tués ou en fuite (G6.4), attitude des personnages canon (G6.5)). Pas de bascule de dernière minute : avec le plafond de 30 par jour (G6.1), un dernier jour ne déplace le rythme que de 30 / P sur une période de P jours-joueur. Il ne fait basculer qu'une issue déjà sur le fil ; les seuils de G16.1 en tiendront compte.
- ✅ **Ordre et branches** : `.apres("x")` attend que x soit passé, quelle que soit son issue (par défaut, les deux issues mènent au même jalon suivant : les branches se rejoignent) ; `.apres("x", Issue.DIVERGENTE)` pour un jalon propre à une branche ; `.apresUnDe("a", "b")` pour rejoindre deux branches. Plusieurs jalons peuvent être en route en même temps. Une époque différente selon l'issue s'ajoutera si G16.1 en a besoin.
- ✅ **Un seul passage** : issue, fin de la mise en route, époque et effets en attente écrits d'un coup, sauvegarde immédiate du monde, puis l'événement `JalonsEvents.PASSE`. Un jalon qui a une issue ne repasse jamais. Crash avant la sauvegarde : le monde rechargé n'a rien vu, le jalon repassera.
- ✅ **Effets après un crash** : chaque jalon passé a un drapeau « effets appliqués » (dans l'état du monde, l'ensemble `effets_en_attente`). L'événement part avec le drapeau à faux ; au chargement, un jalon passé dont les effets ne sont pas appliqués renvoie l'événement (raison « rejeu » : ni nouvelle issue ni changement d'époque). G6.3 pose le drapeau (`MoteurJalons.effetsAppliques`). **Règle pour G6.3 : des effets qui supportent d'être rejoués.**
- ✅ **Qui doit être là** : aucun joueur connecté, le temps de jeu ne bouge pas, rien n'est dû. Par défaut, un jalon dû attend un joueur connecté hors de la scène de naissance, quelle que soit sa dimension (un Or à l'Institut, G13, ne bloque pas les jalons). `.surPlace(Level.OVERWORLD)` pour un jalon qui doit être vécu sur place (Eo, G7.10). En multijoueur, un joueur dans la scène pendant que les autres jouent arrive dans le monde déjà changé.
- ✅ **G6.2 n'a aucun effet sur le monde** : il enregistre l'issue et envoie l'événement, auquel G6.3 s'abonnera.
- Debug : `/rr jalon prochains` (temps restant, délai et ce qui le compose, issue si c'était maintenant), `/rr jalon declencher <jalon> [issue]` (passage complet), `/rr jalon effets <jalon> appliques|en_attente`.

### Effets des jalons (décidés le 28/10 en G6.3)
- ✅ **Deux sortes d'effets, rejouables** (règle de G6.2) : des **leviers**, états qu'on pose (« les patrouilles sont en alerte ») et que le code concerné lit ; des **versements**, donnés une fois à chaque joueur. Déclarés avec le jalon, par issue : `.effets(Issue.CANON, effets -> effets.levier(...).jauges(...))`. Rien de chiffré pour les vrais jalons (G7.10, G16.1).
- ✅ **Ce qu'un jalon peut changer aujourd'hui** (les patrouilles générées viennent en G8.1, les villages n'ont pas de jauge) :
  - **Alerte des patrouilles** (`ALERTE_PATROUILLES`, niveaux 1 et 2) : les Gris existants deviennent plus vigilants. La densité des patrouilles s'ajoutera en G8.1.
  - **Silence d'une Couleur** (`SILENCE_ROUGE`) : ses PNJ ne témoignent plus des actions contre la Société (liste dans « Témoins »). Traduction de « Soutien Rising en hausse dans les villages rouges ».
  - **Versement de jauges** aux joueurs d'une Couleur, ou à tous.
  - Écartées pour l'instant : les zones (rien ne les lit avant G7 ou G8), des traques plus dures (plus tard, Triomphe).
- 🟡 **Valeurs de l'alerte** (dans `Controle` et `Acces`) :

| Niveau | Détection | Attente entre deux contrôles | Seuil de contrôle |
|---|---|---|---|
| 0 (normal) | 12 blocs | 5 min | 40 |
| 1 | 18 blocs | 2 min 30 | 30 |
| 2 | 24 blocs | 1 min 15 | 20 |

- ✅ **Durée** : un levier est permanent ou dure un temps de jeu donné (une alerte ne s'use pas quand personne ne joue). Les effets finis sont retirés de l'état du monde.
- ✅ **Deux jalons sur le même levier** : le niveau le plus fort l'emporte, chacun garde sa durée ; quand le plus fort finit, le suivant reprend. Les versements, eux, s'ajoutent (ce sont des événements).
- ✅ **Rejeu sans doublon** : identifiants stables (`jalon/issue/numéro`) ; poser n'ajoute que ce qui manque et ne relance pas une durée ; leviers, versements et drapeau « effets appliqués » sont écrits d'un coup.
- ✅ **Versements** :
  - seuls les joueurs **arrivés dans le monde avant le jalon** sont concernés (temps de jeu de la première connexion, sur le joueur) ;
  - **décidé une seule fois par joueur**, à la première livraison où il a une Couleur : concerné, il le reçoit ; pas concerné, le versement est marqué reçu sans rien. Changer de Couleur plus tard (Sculpture) ne donne rien après coup. Un joueur en pleine naissance attend d'avoir sa Couleur ;
  - livrés au passage, à l'arrivée du joueur (hors ligne au passage : à son retour), puis toutes les 5 secondes ;
  - ils ne nourrissent ni l'aide ni le tort (G6.1 : les jalons ne s'alimentent pas eux-mêmes).
- ✅ **Message en jeu** : au passage (pas au rejeu), un titre à l'écran et une ligne dans le chat (`jalon.redrising.<jalon>.<issue>`, `.titre`). Un joueur absent (ou en pleine naissance) le reçoit ensuite dans le chat, « Pendant ton absence : … » : c'est un versement comme les autres.
- Debug : `/rr etat` et `/rr effet list` montrent les effets en cours ; `/rr effet poser <levier> <niveau> [ticks]`, `/rr effet retirer <id>`, `/rr effet recus get|reset [joueur]`.

### Personnages canon ✅
PNJ qu'on peut **croiser et influencer**. Trois règles :
1. **Essentiels avant leur jalon** (tombent inconscients au lieu de mourir, façon Skyrim), tuables après. Tuer un personnage important déclenche l'issue divergente du jalon suivant.
2. **Eo** : non sauvable (mort fixe, dans les premiers jours).
3. **Pas d'arbres de dialogue.** Chaque personnage a une **attitude** envers le joueur, qui bouge selon ses actions (aider, donner des objets, couvrir, dénoncer, s'allier à leurs ennemis). Cette attitude pèse sur l'issue de leur jalon.

- Une poignée de personnages par époque, dans des lieux précis (mine de Lykos, Institut, domaines de la Société).
- 🟡 Liste v1 proposée : Darrow, Mustang, Sevro, Cassius, Ares, le Chacal, Octavia, Roque.

### PNJ essentiels (décidés le 28/10 en G6.4)
- ✅ **Déclaration** : un registre de personnages, en code comme les jalons (`Personnages.declarer("darrow", "triomphe")`) ; le PNJ ne porte que l'identifiant de son personnage. Les vrais personnages viendront en G15.2.
- ✅ **Essentiel** tant que son jalon n'a pas d'issue, tuable ensuite. Un jalon fixe compte : **Eo est essentielle jusqu'à sa pendaison** (sinon on la tue avant et le jalon fixe casse). Seul un personnage déclaré sans aucun jalon n'est jamais essentiel.
- 🟡 **À terre au lieu de mourir** : couché, immobile, invulnérable et témoin de rien pendant **30 secondes de jeu** ; il se relève avec **la moitié de sa vie**. Celui qui l'a mis à terre lit « … est à terre, sans connaissance », et des particules tournent au-dessus de lui.
- ✅ **Pas de boucle** : un K.-O. vaut la Loyauté et la Suspicion du meurtre de sa Couleur, avec les témoins habituels (silence compris ; son contrôleur mis à terre par lui : témoin Gris), **jamais de Soutien**, à chaque fois. Frapper un essentiel coûte toujours et ne rapporte rien ; rien n'entre dans les compteurs d'aide et de tort.
- ✅ **Morts hors combat** : une mort sans attaquant (lave, feu, vide, noyade, suffocation, `/kill`) le ramène d'abord à son **foyer** (là où il a reçu son personnage), où il tombe à terre. Chunk déchargé pendant l'inconscience : l'heure du réveil est sauvegardée, il se relève au rechargement s'il est passé.
- ✅ **Conversions** : la foudre ne change pas un essentiel en sorcière (le coup de foudre est annulé). Un zombie ne le convertit pas : sa mort est annulée. Après son jalon, une conversion compte comme sa mort (pas par un joueur), et le converti n'incarne plus le personnage (sinon on guérirait un personnage mort).
- ✅ **Arrestations** : la dénonciation à mains vides ignore un essentiel et prend le PNJ du Rising suivant ; s'il n'y en a pas, rien ne se passe.
- ✅ **Un essentiel dans une cellule qui tombe** (décidé le 28/10) : il n'est pas arrêté. Par tous les chemins de chute (dénonciation, descente après une cellule repérée, `/rr structure tomber`) :
  - son foyer est hors de la cellule : il y est renvoyé, il s'est échappé ;
  - son foyer est dans la cellule : il est retiré du monde sans être tué, et son personnage est **en fuite**. Pas mort pour le `Bilan`, mais lisible avec `bilan.enFuite(...)` (un Ares en fuite peut peser sur une issue). Sauvegardé dans l'état du monde, visible dans `/rr personnage list`, effacé quand on redonne le personnage à un PNJ.
  - ⚠️ **Retour obligatoire** : un personnage en fuite doit réapparaître (par exemple dans une autre cellule après quelques jours), sinon une dénonciation retire un essentiel avant son jalon. À faire avec les cellules des mines (G7.5) et le placement des personnages (G15.2).
- ✅ **Un personnage, un seul PNJ vivant** : `/rr pnj personnage` le retire à l'ancien porteur (tout de suite s'il est chargé, sinon à son prochain chargement). Un personnage mort ne se redonne pas sans `/rr personnage oublier`.
- ✅ **Après le jalon** : sa mort est enregistrée dans l'état du monde (temps de jeu, joueur qui l'a tué), pour le `Bilan` des jalons suivants (`bilan.tue(...)`, `bilan.tueParUnJoueur(...)`). Le meurtre vaut celui de sa Couleur ; son poids propre passe par l'attitude des autres personnages qui le voient (G6.5).
- Debug : `/rr personnage list|tuer <pnj>|oublier <personnage>`, `/rr pnj personnage <pnj> <personnage>|aucun`, `/rr pnj ko|reveiller <pnj>`.

### Attitude des personnages canon (décidée le 28/10 en G6.5)
- ✅ **Une valeur de −100 à +100, par personnage et par joueur**, à 0 au départ, dans l'état du monde (schéma v7). Jamais de chiffre affiché en jeu.
- ✅ **Pour un jalon, une valeur pour le monde** : `bilan.attitude("darrow")`, la moyenne des joueurs qui ont une attitude envers lui (ceux qui l'ont croisé), 0 sinon. Le nombre de joueurs ne compte pas, comme pour le rythme (G6.2). Chaque jalon dit ce qui compte pour lui : `.issue(bilan -> bilan.attitude("darrow") >= 20 ? Issue.CANON : Issue.DIVERGENTE)`. Les seuils des vrais jalons : G16.1. C'est l'attitude du moment du jalon (elle se construit sur toute la partie). Un personnage mort ou en fuite garde la sienne.
- ⚠️ **Limites de la moyenne en multijoueur** : un ami seul pèse autant que plusieurs joueurs indifférents, et un nouveau venu qui croise le personnage une fois dilue la moyenne. À revoir quand le multijoueur sera testé.
- ✅ **Camp** : facultatif, donné à la déclaration (`Personnages.declarer("darrow", "triomphe", Camp.RISING)`) et lu par une seule fonction (`Personnages.camp`). Sans camp, ce qu'il voit ne change rien. ⚠️ Le camp devra pouvoir changer avec l'état du monde (Mustang, Cassius, Roque, Darrow Sculpté) : à traiter en G15.1 ou G16.1.
- 🟡 **Ce qui la fait bouger** (valeurs dans `jalons/Attitude`) :

| Action | Attitude |
|---|---|
| L'aider (nourriture, accroupi ; une fois par jour, la règle d'aide) | +5 |
| Le mettre à terre (essentiel), à chaque fois | −20 |
| Il voit voler ou dénoncer contre son camp | −5 |
| Il voit tuer (ou mettre à terre) quelqu'un de son camp | −10 |
| Il voit s'en prendre au camp adverse (vol, meurtre, dénonciation) | +3 |
| Dénoncer sa cellule (il y était, ou c'était son foyer), même sans l'avoir vu | −30 |
| Mener les Gris à sa cellule, qui tombe à la descente | −10 |

- ✅ **Ce qu'il voit** : mêmes règles que les témoins (16 blocs, ligne de vue, ni mur ni fumée, pas à terre ; pas la victime). Le **silence d'une Couleur** (G6.3) ne joue pas : il ne touche que ce que la Société apprend. Un personnage Rouge voit toujours, pour lui-même, pendant `SILENCE_ROUGE`.
- ✅ **Cellule repérée** : la perte tombe à la descente. Hors ligne, elle est écrite tout de suite dans l'état du monde (un jalon qui passe avant son retour en tient compte), et il l'apprend à son retour avec le message de la cellule tombée, comme pour le Soutien.
- 🟡 **Pas de boucle** : gains plafonnés à **+10 par jour de jeu**, par personnage et par joueur (rouverts le lendemain) ; pertes non plafonnées ; bornes −100 et +100. L'attitude ne passe pas par les jauges : elle ne nourrit ni l'aide ni le tort. Pas de déclin avec le temps pour l'instant.
- ✅ **Ce que le joueur voit** : des cœurs ou un nuage de colère au-dessus du personnage (pour lui seul), et une ligne grise dans le chat, « … te regarde d'un meilleur œil » ou « … te regarde de travers ». Dans le chat et pas au-dessus de la barre d'objets, pour ne pas effacer « Tu as été vu ». Rien quand le plafond du jour est atteint.
- Plus tard, avec leur ticket : donner des objets autres que la nourriture, « couvrir » quelqu'un.
- Debug : `/rr personnage attitude get <personnage> [joueur]`, `/rr personnage attitude set|add <personnage> <valeur> [joueur]` (bornés, sans plafond du jour) ; `/rr personnage list` montre l'attitude moyenne.

---

## 9. Équipement

- ✅ Contenu voulu : razor, sangLames, gravBoots, armures.
- ✅ Razor réservé aux Or (lore), obtenable via la Loyauté Société.
- ✅ gravBoots et armures craftés par l'Orange.
- ❓ Qui peut utiliser gravBoots et armures.
- ❓ sangLames : Couleur(s) associée(s), fonctionnement.
- ✅ Équipement rebelle côté Rising : voir plus bas (G5.7).
- Le razor est l'objet le plus technique (arme qui change de forme, hitbox custom).

### Équipement rebelle (décidé le 27/10 en G5.7, code en G5.8)
- ✅ **Principe** : les Fils d'Arès vivent cachés et ne tiennent pas un combat contre les Gris ; leur matériel est volé ou bricolé, pour frapper puis disparaître. Rien n'y est meilleur que l'équipement de la Société sur son terrain (dégâts, protection, déplacement) : il sert seulement à échapper aux Gris. Les objets portent le signe d'Arès (le casque à pointes solaires).
- ✅ **Écarté exprès** : masque ou fausse identité (faux papiers du Cuivre), cape d'invisibilité (technologie d'Or, discrétion du Rose), armures et bottes (Orange), soins (Jaune), brouilleur de scanners (Vert), armes et explosifs (le jeu du Rising ne doit pas devenir un jeu de combat ; chevauchement avec les sangLames, encore ❓).
- ✅ **Contrebande** (règle commune à tout objet rebelle) :
  - l'objet est marqué « Contrebande du Rising » (dans sa description) ; il ne se fabrique pas, il ne vient que du Rising (boutique de la cellule, G5.9) ;
  - **tenu en main ou utilisé devant un Gris** (même règle que les témoins : 16 blocs, ligne de vue) : Suspicion +5, au plus une fois par minute ; les autres témoins ne le reconnaissent pas ;
  - **rangé dans l'inventaire** : invisible pour l'instant ; la fouille (plus tard) le confisquera, Suspicion +10 par objet trouvé ;
  - la **planque** d'une cellule (son second coffre, sans camp, G5.10) est sûre : ni marque ni vol, les entonnoirs ne la vident pas, la fouille ne s'y appliquera pas. Reprendre ses affaires dans le coffre du Rising, en revanche, est un vol.
- ✅ **Fumigène** (🟡 Soutien 20, le seuil d'entrée d'une cellule) : grenade à fumée, lancée, à usage unique. Nuage d'environ 5 blocs pendant 10 secondes, qui coupe la ligne de vue des PNJ au travers : un chasseur perd le joueur (il fouille autour de sa dernière position connue), le témoin d'un vol ne voit rien. Lancé devant un Gris, c'est de la contrebande (sans effet de plus pendant une traque, la Suspicion y est déjà au maximum).
  - ✅ **Règle de vue** (G5.8) : partout où un PNJ « voit » le joueur (témoins, patrouille qui repère un suspect, chasseurs, arrestation, apparition des chasseurs hors de vue), il faut à la fois la ligne de vue de Minecraft (les murs) et aucun nuage entre les deux paires d'yeux.
  - ✅ **Au contact, la fumée ne cache rien** (2,5 blocs, la distance d'une arrestation) : un chasseur collé au joueur dans le nuage l'arrête quand même, quelqu'un à un pas voit le vol. La fumée sert à fuir, pas à rester au contact sans être pris.
  - ✅ **Un contrôle déjà commencé n'est pas touché** : la fuite se mesure à la distance, le Gris sait qui il contrôle. La fumée empêche seulement d'être repéré.
  - ✅ Entrer dans une cellule derrière un nuage, c'est y entrer sans être vu (traque gagnée, cellule pas repérée). Combinaison forte, voulue (le geste des Fils d'Arès) ; à revoir en jouant.
  - ✅ Les monstres de Minecraft voient à travers : seuls les PNJ du mod sont concernés.
- ✅ **Chausse-trappes** (🟡 Soutien 40) : bloc de ferraille bas, posé au sol, discret. Un joueur (le poseur compris ; ni créatif ni spectateur) ou un PNJ de Couleur qui marche dessus est ralenti 3 secondes, et la chausse-trappe **disparaît** une fois déclenchée. **Les monstres et les animaux ne la déclenchent pas** (sinon, dans les mines, araignées et zombies videraient les pièges). Un Gris qui s'y prend fouille autour du piège, il sait qu'un rebelle est passé : un chasseur repart de là, une patrouille fouille 20 secondes avant de reprendre sa ronde. **Au plus 3 posées par joueur** : en poser une quatrième fait disparaître la plus ancienne (on ne tapisse pas les abords d'une cellule). Le poseur peut reprendre la sienne en la cassant ; cassée par un autre, elle est perdue. Disparue autrement (eau, explosion, piston, sol retiré), elle ne compte plus dans les trois du poseur (correction du 27/10).
- ✅ **Obtention** : les seuils de Soutien valent pour l'obtention (la boutique, G5.9), pas pour l'usage : un joueur dont le Soutien retombe garde ce qu'il a. En attendant la boutique : `/give` ou l'onglet « Red Rising » de l'inventaire créatif.
- 🟡 Valeurs (G5.8), à régler en jeu, chacune à un seul endroit du code :

| Valeur | Réglage | Où |
|---|---|---|
| Seuils d'obtention | fumigène 20, chausse-trappe 40 | `factions/Acces` |
| Nuage | rayon 2,5 blocs, 10 secondes | `factions/Nuage` |
| Vue gardée au contact | 2,5 blocs | `factions/Nuage` |
| Ralentissement d'une chausse-trappe | 3 secondes, Lenteur III | `factions/ChausseTrappes` |
| Chausse-trappes par joueur | 3 | `factions/RegistreChausseTrappes` |
| Fouille d'une patrouille prise au piège | 20 secondes | `pnj/PatrouillePnj` |
| Contrebande vue par un Gris | Suspicion +5, une fois par minute | `EffetsActions`, `factions/Contrebande` |
- **Pistes pour plus tard** (non décidées) :
  - **Insigne d'Arès**, avec la fouille : gardé dans l'inventaire, les témoins du peuple couvrent le joueur (ils comptent comme « aucun témoin ») et le repère des cellules se voit de plus loin ; preuve d'appartenance, il coûterait cher à une fouille (Suspicion +25). À surveiller : proche de l'effet des faux papiers du Cuivre.
  - **Appel aux Fils**, avec le soutien armé : à usage unique, deux PNJ Rouges du Rising viennent se battre 60 secondes ; une fois par jour de jeu, Soutien −5 si l'un d'eux est tué ou arrêté. Demande des PNJ alliés qui combattent.

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
  - ✅ Schéma v8 (G7.1.1) : + `plan` (point d'apparition au tirage, centre de chaque région), facultatif. Un monde v7 est migré sans plan : ses chunks autour du point d'apparition existent déjà, on ne pose pas de régions après coup.
  - ✅ Schéma v7 (G6.5) : + `attitudes` (personnage → joueur → valeur, jour et gains du jour). Un monde v6 est migré sans attitude. Global : `foyers_personnages` (pour savoir qui avait son foyer dans une cellule qui tombe).
  - ✅ Schéma v6 (G6.4) : + `en_fuite` (personnage → temps de jeu de sa fuite). Un monde v5 est migré sans fuite.
  - ✅ Schéma v5 (G6.4) : + `morts` (personnage → temps de jeu, joueur qui l'a tué). Un monde v4 est migré sans mort. Sur le PNJ : `personnage`, `foyer`, `inconscient` ; global : `porteurs_personnages`.
  - ✅ Schéma v4 (G6.3) : + `effets` (identifiant → levier, niveau, jalon, début, fin) et `versements` (jauges et nouvelles des jalons). Un monde v3 est migré avec des listes vides. Sur le joueur : `arrivee` et `versements_recus`.
  - ✅ Schéma v3 (G6.2) : + `presence` dans l'horloge, `en_route` (jalon → horloge au moment de sa mise en route), `effets_en_attente` (jalons passés dont les effets ne sont pas appliqués). Un monde v2 est migré : présence = temps de jeu (comme à un seul joueur), rien en route, effets déjà appliqués.
  - ✅ Schéma v2 (G6.1) : + `horloge` (`temps`, `aide`, `tort`, voir section 8). Un monde v1 est migré en v2 à la lecture, avec une horloge à zéro (aucun monde n'avait été publié : pas besoin d'estimer le passé).
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
- [ ] Équipement : accès gravBoots / armures, sangLames (équipement rebelle tranché en G5.7)
- [ ] Luna : contenu et moment d'accès
- [ ] Reformulation des 7 questions du test (sans toucher aux paires)
- [ ] Vérifier en jeu que Cuivre et Marron sont fun
