#!/usr/bin/env python3
"""Biométrie réelle sur l'émulateur : code PIN + empreinte virtuelle, puis verrouillage de CardioSuivi."""
import sys, time, subprocess
sys.path.insert(0, "/home/user/TANONB/recette")
from pilote_adb import *

PKG = sys.argv[1] if len(sys.argv) > 1 else "com.cardiosuivi.app.debug"
out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/recette_bio.tsv"


def finger(n=1):
    subprocess.run(ADB + ["emu", "finger", "touch", str(n)], capture_output=True)
    time.sleep(3)


def screen_has(*labels):
    t = " ".join(texts())
    return any(l in t for l in labels)


# 1. Code PIN de l'appareil
sh("locksettings", "set-pin", "1234")
time.sleep(3)
record("BIO01", "Appareil – code PIN", "locksettings set-pin 1234", "code de verrouillage défini", "défini", True, "journal adb")

# 2. Enregistrement d'une empreinte (Réglages Android)
sh("am", "start", "-a", "android.settings.BIOMETRIC_ENROLL")
added = False
t0 = time.time()
while time.time() - t0 < 900 and not added:
    dismiss_anr()
    t = " ".join(texts())
    if "Fingerprint added" in t or "Empreinte ajoutée" in t:
        added = True; break
    if any(k in t for k in ["Re-enter your PIN", "Confirm your PIN", "Enter your PIN", "Saisissez votre code"]):
        sh("input", "text", "1234"); sh("input", "keyevent", "66"); time.sleep(4); continue
    clicked = False
    for b in ["Fingerprint", "Fingerprint Unlock", "I agree", "Agree", "More", "Next", "Start", "Continue"]:
        n = find(b, exact=True)
        if n is not None and n.get("clickable") == "true" or (n is not None and b in ("I agree", "More", "Next", "Start", "Continue", "Agree")):
            sh("input", "tap", *map(str, center(n))); time.sleep(4); clicked = True; break
    if clicked: continue
    if any(k in t for k in ["Touch the sensor", "Lift", "Put your finger", "Keep lifting", "touch"]):
        finger(1); continue
    time.sleep(3)
record("BIO02", "Appareil – empreinte", "emu finger touch 1 (assistant Réglages)", "empreinte virtuelle enregistrée",
       "ajoutée" if added else "non ajoutée (assistant non abouti)", added)
n = find("Done", exact=True)
if n is not None: sh("input", "tap", *map(str, center(n)))
sh("input", "keyevent", "KEYCODE_HOME"); time.sleep(3)

if added:
    # 3. Activation du verrouillage dans CardioSuivi
    sh("am", "force-stop", PKG)
    sh("am", "start", "-n", f"{PKG}/com.cardiosuivi.app.MainActivity")
    if wait("Bienvenue dans CardioSuivi", 60):
        for _ in range(3):
            if find("J'ai compris") is None: scroll_down()
        tap("J'ai compris"); tap("Commencer", exact=True)
    wait("NOUVELLE SÉANCE", 240)
    tap("Plus"); tap("Paramètres et sécurité")
    tap("Verrouillage biométrique")
    prompt = wait("Déverrouiller CardioSuivi", 120)
    finger(1)
    time.sleep(5)
    record("BIO03", "Activation du verrouillage", "interrupteur + empreinte 1", "fenêtre biométrique Android puis activation",
           "fenêtre affichée" if prompt else "fenêtre absente", prompt)
    # 4. Verrouillage après 60 s en arrière-plan, empreinte inconnue puis connue
    sh("input", "keyevent", "KEYCODE_HOME"); time.sleep(75)
    sh("am", "start", "-n", f"{PKG}/com.cardiosuivi.app.MainActivity")
    locked = wait("CardioSuivi est verrouillé", 180)
    record("BIO04", "Verrouillage après 60 s en arrière-plan", "retour après 75 s", "écran « CardioSuivi est verrouillé »", "affiché" if locked else "absent", locked)
    wait("Déverrouiller CardioSuivi", 60)
    finger(2)
    still = screen_has("CardioSuivi est verrouillé", "Not recognized", "Empreinte non reconnue", "Try again")
    record("BIO05", "Empreinte inconnue", "emu finger touch 2 (non enregistrée)", "accès refusé, application toujours verrouillée", "verrouillée" if still else "DÉVERROUILLÉE", still)
    finger(1)
    unlocked = wait("NOUVELLE SÉANCE", 120)
    record("BIO06", "Empreinte connue", "emu finger touch 1", "déverrouillage, tableau de bord", "déverrouillé" if unlocked else "toujours verrouillé", unlocked)
    # 5. Courte sortie (< 60 s) : pas de verrouillage
    sh("input", "keyevent", "KEYCODE_HOME"); time.sleep(15)
    sh("am", "start", "-n", f"{PKG}/com.cardiosuivi.app.MainActivity")
    time.sleep(20)
    short = not screen_has("CardioSuivi est verrouillé")
    record("BIO07", "Courte sortie (< 60 s)", "retour après 15 s", "pas de verrouillage (sélecteur de photo, partage…)", "non verrouillée" if short else "verrouillée", short)

open(out, "w").write("\n".join(LOG) + "\n")
