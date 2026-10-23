"""Génère les textures de l'apparence par Couleur (G3.5), teintées en jeu avec la Couleur du joueur.

Textures 64x64 calées sur le patron des skins ; seuls les pixels utiles sont opaques :
- marque_large.png / marque_fine.png (bras larges / fins) :
  - mains : bracelet d'un pixel au poignet et sigil 2x2 sur le dos de la main, sur le bras et sa manche ;
  - yeux : les deux iris, à l'emplacement standard des skins par défaut (ligne 12, x = 10 et 13).
- cheveux.png : couche posée sur la couche extérieure de la tête (dessus, frange, côtés, nuque),
  avec un léger relief pour garder un aspect de cheveux. Désactivable côté client.

Usage : python3 outils/marque_couleur.py (demande Pillow)
Placeholder en attendant les textures finales (G17.3, Blockbench).
"""
from pathlib import Path

from PIL import Image

DOSSIER = Path(__file__).resolve().parent.parent / "src/main/resources/assets/redrising/textures/entity"
HAUTEUR_BRAS = 12
PROFONDEUR = 4
BLANC = (255, 255, 255, 255)


def bras(pixels, u, v, largeur, cote_exterieur):
    """Bracelet et sigil d'un bras dont le patron commence en (u, v).

    Les 4 faces latérales se suivent de gauche à droite : côté, avant, autre côté, arrière.
    cote_exterieur vaut 0 pour le premier côté, 1 pour l'autre.
    """
    haut_faces = v + PROFONDEUR
    poignet = haut_faces + HAUTEUR_BRAS - 4  # la main = les 4 dernières lignes
    tour = 2 * PROFONDEUR + 2 * largeur
    for x in range(u, u + tour):
        pixels.add((x, poignet))
    x_cote = u if cote_exterieur == 0 else u + PROFONDEUR + largeur
    for dx in (1, 2):
        for dy in (1, 2):
            pixels.add((x_cote + dx, poignet + dy))


def marque(largeur, nom):
    pixels = set()
    # Bras droit et sa manche : côté extérieur = premier côté
    bras(pixels, 40, 16, largeur, 0)
    bras(pixels, 40, 32, largeur, 0)
    # Bras gauche et sa manche : côté extérieur = autre côté (patron en miroir)
    bras(pixels, 32, 48, largeur, 1)
    bras(pixels, 48, 48, largeur, 1)
    # Iris des deux yeux, face avant de la tête (8..15, 8..15)
    pixels.update({(10, 12), (13, 12)})
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for p in pixels:
        image.putpixel(p, BLANC)
    image.save(DOSSIER / nom)


def cheveux():
    """Couche extérieure de la tête (patron en 32, 0) : dessus 40..47 x 0..7, puis sur la ligne 8..15
    le côté droit 32..39 (avant en x = 39), l'avant 40..47, le côté gauche 48..55 (avant en x = 48), l'arrière 56..63."""
    pixels = set()
    for x in range(40, 48):
        for y in range(0, 8):
            pixels.add((x, y))
    # Frange, et mèches qui encadrent le visage sur les tempes
    for x in range(40, 48):
        pixels.add((x, 8))
    for y in range(9, 12):
        pixels.update({(40, y), (47, y)})
    # Côtés : haut complet, puis la moitié arrière jusqu'à la nuque
    for y in range(8, 13):
        for x in list(range(32, 40)) + list(range(48, 56)):
            pixels.add((x, y))
    for y in range(13, 15):
        for x in list(range(32, 36)) + list(range(52, 56)):
            pixels.add((x, y))
    # Nuque
    for y in range(8, 16):
        for x in range(56, 64):
            pixels.add((x, y))
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for x, y in pixels:
        # Relief : des mèches verticales plus sombres (sur le dessus, en diagonale)
        meche = (x + y) % 3 == 0 if y < 8 else x % 3 == 0
        gris = 210 if meche else 255
        image.putpixel((x, y), (gris, gris, gris, 255))
    image.save(DOSSIER / "cheveux.png")


if __name__ == "__main__":
    DOSSIER.mkdir(parents=True, exist_ok=True)
    marque(4, "marque_large.png")
    marque(3, "marque_fine.png")
    cheveux()
    print("Textures écrites dans", DOSSIER)
