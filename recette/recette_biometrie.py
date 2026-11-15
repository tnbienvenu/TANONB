#!/usr/bin/env python3
"""Biométrie réelle sur l'émulateur : code PIN + empreinte virtuelle, puis verrouillage de CardioSuivi."""
import sys, time, subprocess
sys.path.insert(0, "/home/user/TANONB/recette")
from pilote_adb import *

PKG = sys.argv[1] if len(sys.argv) > 1 else "com.cardiosuivi.app.debug"
out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/recette_bio.tsv"
import atexit
atexit.register(lambda: open(out, "w").write("\n".join(LOG) + "\n"))


def finger(n=1):
    subprocess.run(ADB + ["emu", "finger", "touch", str(n)], capture_output=True)
    time.sleep(3)


def screen_has(*labels):
    t = " ".join(texts())
    return any(l in t for l in labels)


# 1. Code PIN de l'appareil
v = sh("locksettings", "verify", "--old", "1234")
if "verified successfully" not in v.lower():
    sh("locksettings", "set-pin", "1234"); time.sleep(3)
    v = sh("locksettings", "verify", "--old", "1234")
pin_ok = "verified successfully" in v.lower()
record("BIO01", "Appareil – code PIN", "locksettings set-pin 1234", "code de verrouillage défini", v.strip()[:60] or "?", pin_ok, "journal adb")

# 2. Enregistrement d'une empreinte (Réglages Android). Les écrans de l'assistant sont protégés (capture noire,
# arbre d'accessibilité vide) : si une empreinte existe déjà (assistant conduit à la main : appui sur le bouton de
# pied de page puis « adb emu finger touch 1 »), on la réutilise.
def enrolled():
    import re
    m = re.search(r'"count":(\d+)', sh("dumpsys", "fingerprint"))
    return m is not None and int(m.group(1)) > 0
added = enrolled()
if not added:
    sh("am", "start", "-a", "android.settings.BIOMETRIC_ENROLL")
    t0 = time.time()
    while time.time() - t0 < 900 and not added:
        sh("input", "text", "1234"); sh("input", "keyevent", "66"); time.sleep(15)
        sh("input", "tap", "590", "1660"); time.sleep(15)
        finger(1); added = enrolled()
record("BIO02", "Appareil – empreinte", "assistant Réglages + emu finger touch 1", "empreinte virtuelle enregistrée (dumpsys fingerprint)",
       "ajoutée" if added else "non ajoutée (assistant non abouti)", added)
sh("input", "keyevent", "KEYCODE_HOME"); time.sleep(3)

if added:
    # 3. Activation du verrouillage dans CardioSuivi
    sh("am", "force-stop", PKG)
    sh("am", "start", "-n", f"{PKG}/com.cardiosuivi.app.MainActivity")
    if wait("Bienvenue dans CardioSuivi", 60):
        for _ in range(3):
            if find("J'ai compris") is None: scroll_down()
        tap("J'ai compris")
        for _ in range(4):
            if find("Commencer", exact=True) is not None: break
            scroll_down()
        tap("Commencer", exact=True)
    wait("Résumé de votre santé cardiovasculaire", 240)
    tap("Profil", exact=True); tap_scroll("Paramètres et sécurité")
    tap("Verrouillage biométrique")
    prompt = wait("Déverrouiller CardioSuivi", 120)
    finger(1)
    time.sleep(5)
    record("BIO03", "Activation du verrouillage", "interrupteur + empreinte 1", "fenêtre biométrique Android puis activation",
           "fenêtre affichée" if prompt else "fenêtre absente", prompt)
    # 4. Verrouillage après 60 s en arrière-plan, empreinte inconnue puis connue
    sh("input", "keyevent", "KEYCODE_HOME"); time.sleep(75)
    sh("am", "start", "-n", f"{PKG}/com.cardiosuivi.app.MainActivity")
    locked = wait("Déverrouiller CardioSuivi", 180) or wait("CardioSuivi est verrouillé", 5)  # l'invite système masque l'écran de verrouillage
    record("BIO04", "Verrouillage après 60 s en arrière-plan", "retour après 75 s", "écran « CardioSuivi est verrouillé »", "affiché" if locked else "absent", locked)
    wait("Déverrouiller CardioSuivi", 60)
    finger(2)
    still = screen_has("Déverrouiller CardioSuivi", "CardioSuivi est verrouillé") and not screen_has("NOUVELLE SÉANCE")
    record("BIO05", "Empreinte inconnue", "emu finger touch 2 (non enregistrée)", "accès refusé, application toujours verrouillée", "verrouillée" if still else "DÉVERROUILLÉE", still)
    finger(1)
    unlocked = wait("Sécurité", 120) and not screen_has("Déverrouiller CardioSuivi")  # retour sur l'écran Paramètres
    record("BIO06", "Empreinte connue", "emu finger touch 1", "déverrouillage, tableau de bord", "déverrouillé" if unlocked else "toujours verrouillé", unlocked)
    # 5. Courte sortie (< 60 s) : pas de verrouillage
    sh("input", "keyevent", "KEYCODE_HOME"); time.sleep(15)
    sh("am", "start", "-n", f"{PKG}/com.cardiosuivi.app.MainActivity")
    time.sleep(20)
    short = not screen_has("CardioSuivi est verrouillé", "Déverrouiller CardioSuivi")
    record("BIO07", "Courte sortie (< 60 s)", "retour après 15 s", "pas de verrouillage (sélecteur de photo, partage…)", "non verrouillée" if short else "verrouillée", short)

open(out, "w").write("\n".join(LOG) + "\n")
