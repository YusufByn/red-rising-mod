"""Dessine les textures provisoires des objets du mod : fumigène et chausse-trappe (G5.8), crédit de la Société (G5.9).

À lancer depuis la racine du projet :  python3 outils/objets.py  (demande Pillow).
Écrit textures/item/fumigene.png, textures/item/credit.png et textures/block/chausse_trappe.png (16x16). Les pixels transparents de la
chausse-trappe laissent voir le sol : Minecraft la dessine alors en « cutout », sans rien à déclarer.
"""
from pathlib import Path

from PIL import Image

TEXTURES = Path(__file__).resolve().parent.parent / "src/main/resources/assets/redrising/textures"
VIDE = (0, 0, 0, 0)


def teinte(code, alpha=255):
    code = code.lstrip("#")
    return int(code[0:2], 16), int(code[2:4], 16), int(code[4:6], 16), alpha


def fumigene():
    """Bidon gris, bande rouge (la teinte des Rouges), goupille, filet de fumée."""
    image = Image.new("RGBA", (16, 16), VIDE)
    p = image.load()
    corps, clair, sombre = teinte("4a4a4a"), teinte("707070"), teinte("2e2e2e")
    rouge, rouge_clair = teinte("c8201e"), teinte("e04a3a")
    for y in range(6, 15):
        for x in range(5, 11):
            p[x, y] = corps
        p[5, y] = sombre
        p[10, y] = sombre
        p[6, y] = clair
    for x in range(5, 11):
        p[x, 14] = sombre
    for y in (9, 10):
        for x in range(5, 11):
            p[x, y] = rouge
        p[6, y] = rouge_clair
    for x in range(6, 10):
        p[x, 5] = sombre
    p[7, 4] = p[8, 4] = sombre
    goupille = teinte("b08a2a")
    for x, y in ((10, 4), (11, 3), (11, 5), (12, 4)):
        p[x, y] = goupille
    for x, y, couleur in ((7, 3, teinte("bcbcbc", 200)), (8, 2, teinte("d8d8d8", 230)), (7, 1, teinte("e8e8e8", 160)),
                          (6, 2, teinte("bcbcbc", 200)), (9, 1, teinte("e8e8e8", 160)), (8, 0, teinte("e8e8e8", 160))):
        p[x, y] = couleur
    return image


def chausse_trappe():
    """Pointes de fer rouillé éparpillées sur un fond transparent (le modèle n'utilise que la face du dessus)."""
    image = Image.new("RGBA", (16, 16), VIDE)
    p = image.load()
    fer, fer_sombre = teinte("8e8e8e"), teinte("5a5a5a")
    rouille, rouille_sombre = teinte("8a5a3a"), teinte("5e3c26")

    def pointe(cx, cy, en_croix):
        p[cx, cy] = fer_sombre
        bras = [(-1, -1), (1, 1), (1, -1), (-1, 1)] if en_croix else [(0, -1), (0, 1), (-1, 0), (1, 0)]
        for i, (dx, dy) in enumerate(bras):
            p[cx + dx, cy + dy] = rouille if i % 2 == 0 else fer
        dx, dy = (2, 2) if en_croix else (0, 2)
        if 1 <= cx + dx <= 14 and 1 <= cy + dy <= 14:
            p[cx + dx, cy + dy] = rouille_sombre

    for cx, cy, en_croix in ((3, 3, True), (10, 2, False), (7, 7, True), (12, 9, True), (3, 11, False), (9, 13, False)):
        pointe(cx, cy, en_croix)
    return image


def credit():
    """Pièce dorée cerclée de sombre, frappée de la pyramide de la Société (les Couleurs, de l'Or au sommet)."""
    image = Image.new("RGBA", (16, 16), VIDE)
    p = image.load()
    bord, or_, or_clair, grave = teinte("6e4a12"), teinte("e0b030"), teinte("f6dc78"), teinte("a87818")
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 6.6:
                p[x, y] = bord if d > 5.6 else or_
    for x, y in ((5, 4), (4, 5), (4, 6), (5, 3), (6, 3)):
        p[x, y] = or_clair
    # Pyramide gravée : trois marches
    for y, (debut, fin) in zip(range(5, 11), ((7, 8), (7, 8), (6, 9), (6, 9), (5, 10), (5, 10))):
        for x in range(debut, fin + 1):
            p[x, y] = grave
    return image


if __name__ == "__main__":
    fumigene().save(TEXTURES / "item/fumigene.png")
    chausse_trappe().save(TEXTURES / "block/chausse_trappe.png")
    credit().save(TEXTURES / "item/credit.png")
    print("Textures écrites dans", TEXTURES)
