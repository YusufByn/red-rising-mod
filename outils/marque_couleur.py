"""Génère les textures de la marque de Couleur (G3.5), une par type de bras (large, fin).

Textures 64x64 calées sur le patron des skins : seuls les pixels de la marque sont opaques (blancs),
le jeu les teinte avec la Couleur du joueur. Pour chaque bras (et sa manche, couche extérieure du skin) :
un bracelet d'un pixel autour du poignet et un sigil 2x2 sur le dos de la main.

Usage : python3 outils/marque_couleur.py
Placeholder en attendant les textures finales (G17.3, Blockbench).
"""
from pathlib import Path

from PIL import Image

DOSSIER = Path(__file__).resolve().parent.parent / "src/main/resources/assets/redrising/textures/entity"
HAUTEUR_BRAS = 12
PROFONDEUR = 4


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


def texture(largeur, nom):
    pixels = set()
    # Bras droit et sa manche : côté extérieur = premier côté
    bras(pixels, 40, 16, largeur, 0)
    bras(pixels, 40, 32, largeur, 0)
    # Bras gauche et sa manche : côté extérieur = autre côté (patron en miroir)
    bras(pixels, 32, 48, largeur, 1)
    bras(pixels, 48, 48, largeur, 1)
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for p in pixels:
        image.putpixel(p, (255, 255, 255, 255))
    image.save(DOSSIER / nom)


if __name__ == "__main__":
    DOSSIER.mkdir(parents=True, exist_ok=True)
    texture(4, "marque_large.png")
    texture(3, "marque_fine.png")
    print("Textures écrites dans", DOSSIER)
