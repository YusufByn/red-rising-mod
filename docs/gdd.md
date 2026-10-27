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
  - **Dénoncer** : accroupi, clic droit **à mains vides** sur un PNJ Gris (aider = avec un objet, dénoncer = sans, pas d'ambiguïté) ; le PNJ du Rising le plus proche, à moins de 16 blocs, est arrêté (il disparaît).
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
- ✅ Le joueur est prévenu : « Quelqu'un t'a vu » ou « Un Gris t'a vu ».
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
- ✅ **Contrôle** : un Gris en patrouille qui voit un joueur au palier de contrôle à moins de 12 blocs l'arrête (« Halte ! Contrôle. »), vient à lui, puis le contrôle pendant 5 secondes avant de reprendre sa ronde. Un joueur n'est pas contrôlé plus d'une fois toutes les 5 minutes de jeu. Le contrôle est abandonné, sans effet, si le Gris n'arrive pas en 20 secondes.
  - **Obéir** (rester jusqu'au bout) : Suspicion −5, **une fois par jour de jeu au plus** (sinon on se blanchit en allant voir les patrouilles). Message « Contrôle terminé : tu peux circuler ».
  - **Fuir** : s'éloigner à plus de 16 blocs du Gris, **dès le « Halte »**, approche comprise : Loyauté −5, Suspicion +10. Une seule limite de distance, plus grande que la distance de détection, pour qu'on ne soit pas en fuite dès le « Halte ».
  - **Se déconnecter** pendant un contrôle (ou passer un portail) compte comme une fuite (correction du 27/10). La fuite d'un joueur déconnecté est comptée à son retour. Mourir pendant un contrôle l'annule, sans effet.
  - Attaquer le Gris : c'est « tuer un PNJ de l'ordre », avec un Gris témoin.
- 🟡 Distances et durées dans `factions/Controle`, effets dans `EffetsActions`.
- Pour plus tard : une fouille qui trouverait les objets volés (marqués à la sortie d'un coffre de la Société).

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
- ✅ **Trouver une cellule en creusant est permis** : on en fera du contenu avec la dénonciation d'une cellule (G5.9), la traque (G5.6) et le Triomphe.
- 🟡 **Dénoncer une cellule (G5.9, esquisse)** : une cellule où le joueur est entré au moins une fois (par l'entrée ou en creusant) lui est connue. Même geste que dénoncer un PNJ (accroupi, main vide, clic droit sur un Gris) : si une cellule connue est à moins de 32 blocs du Gris, c'est elle qui est dénoncée, sinon le PNJ du Rising le plus proche. La cellule tombe : ses PNJ sont arrêtés, son coffre saisi (vidé, sans camp), elle sort de la liste. Jauges : +10 / −25 / −10.
- ✅ **Entonnoirs** : un entonnoir (posé ou sur wagonnet) ne vide pas un coffre ou un tonneau d'un camp, ni un coffre double dont une moitié appartient à un camp. Il peut toujours le remplir (c'est un don).

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
