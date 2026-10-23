"""Assemble les captures de ApparenceCouleurTest en une planche annotée des 14 Couleurs (G3.5).

À lancer après ./gradlew runClientGameTest :  python3 outils/planche_couleurs.py
Écrit build/captures-gametest/planche-couleurs.png (demande Pillow).
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

DOSSIER = Path(__file__).resolve().parent.parent / "build/captures-gametest"
COULEURS = ["Or", "Argent", "Cuivre", "Blanc", "Bleu", "Jaune", "Vert", "Violet",
            "Orange", "Gris", "Obsidian", "Marron", "Rose", "Rouge"]
RANGEES = [("rangee-clair", "Skin clair"), ("rangee-clair-casque", "Skin clair, casque"),
           ("rangee-clair-sans-cheveux", "Skin clair, cheveux désactivés"),
           ("rangee-fonce", "Skin foncé"), ("rangee-fonce-casque", "Skin foncé, casque")]
# Géométrie du test : fenêtre 1920x1080, champ de vision vertical 50°, mannequins à 9 blocs, un bloc d'écart
LARGEUR = 1920
DISTANCE = 9
PIXELS_PAR_BLOC = (LARGEUR / 2) / (math.tan(math.radians(25)) * 16 / 9) / DISTANCE
HAUT, BAS = 270, 570  # bande verticale qui contient les mannequins (regard 10° vers le bas)
ENTETE, TITRE = 40, 34


def police(taille):
    """Une police qui a les accents (celle par défaut de Pillow ne les a pas)."""
    for chemin in ("/System/Library/Fonts/Supplemental/Arial.ttf", "/Library/Fonts/Arial.ttf",
                   "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "C:/Windows/Fonts/arial.ttf"):
        if Path(chemin).exists():
            return ImageFont.truetype(chemin, taille)
    return ImageFont.load_default(taille)


def capture(nom):
    fichiers = sorted(DOSSIER.glob(f"*_{nom}.png"))
    if not fichiers:
        raise SystemExit(f"Capture {nom} introuvable dans {DOSSIER}")
    return Image.open(fichiers[-1]).convert("RGB")


def main():
    hauteur_bande = BAS - HAUT
    planche = Image.new("RGB", (LARGEUR, ENTETE + len(RANGEES) * (TITRE + hauteur_bande)), "white")
    dessin = ImageDraw.Draw(planche)
    for i, nom in enumerate(COULEURS):
        centre = LARGEUR / 2 + (i - 6.5) * PIXELS_PAR_BLOC
        dessin.text((centre, ENTETE / 2), nom, fill="black", anchor="mm", font=police(22))
    y = ENTETE
    for fichier, titre in RANGEES:
        dessin.text((10, y + TITRE / 2), titre, fill="black", anchor="lm", font=police(20))
        planche.paste(capture(fichier).crop((0, HAUT, LARGEUR, BAS)), (0, y + TITRE))
        y += TITRE + hauteur_bande
    sortie = DOSSIER / "planche-couleurs.png"
    planche.save(sortie)
    print("Planche écrite :", sortie)


if __name__ == "__main__":
    main()
