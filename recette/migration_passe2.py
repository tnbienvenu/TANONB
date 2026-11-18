#!/usr/bin/env python3
"""
Passe 2 de la mise à jour réelle 1.0.1 → 1.1.0 : profil, traitement et fournisseur d'IA.
Chaque valeur est d'abord VÉRIFIÉE à l'écran dans la 1.0.1 (avant mise à jour), puis dans la 1.1.0.
Saisie lente (par 2 caractères) : la saisie rapide d'adb était tronquée sur l'émulateur (« MIGRAT »).
Usage : migration_passe2.py <apk_1.0.1> <apk_1.1.0> <journal.tsv>
"""
import re, sys, time, subprocess
sys.path.insert(0, "/home/user/TANONB/recette")
from pilote_adb import *

PKG = "com.cardiosuivi.app"
APK_OLD, APK_NEW, out = sys.argv[1], sys.argv[2], sys.argv[3]
import atexit
atexit.register(lambda: open(out, "w").write("\n".join(LOG) + "\n"))
NAME, TREAT = "MIGFICTIF", "TRTFICTIF"


def version():
    m = re.search(r"versionName=(\S+)", sh("dumpsys", "package", PKG))
    return m.group(1) if m else "?"


def slow_type(label, value):
    tap(label)
    for i in range(0, len(value), 2):
        sh("input", "text", value[i:i + 2]); time.sleep(1.2)
    time.sleep(1)


def radios():
    return [n.get("checked") for n in nodes() if n.get("checkable") == "true"][:3]


def back():
    sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(2)


subprocess.run(ADB + ["uninstall", PKG], capture_output=True)
r = subprocess.run(ADB + ["install", APK_OLD], capture_output=True, text=True)
sh("pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
sh("am", "start", "-n", f"{PKG}/.MainActivity")
wait("Bienvenue dans CardioSuivi", 240)
# Le premier démarrage de la 1.0.1 provoque un ANR système sur cet émulateur logiciel : on répond « Attendre ».
t0 = time.time()
while time.time() - t0 < 150:
    dismiss_anr(); time.sleep(5)
for _ in range(3):
    if find("J'ai compris") is None: scroll_down()
tap("J'ai compris")
for _ in range(4):
    if find("Commencer", exact=True) is not None: break
    scroll_down()
tap("Commencer", exact=True)
wait("NOUVELLE SÉANCE", 240); time.sleep(10)

# --- Saisies 1.0.1 avec vérification avant mise à jour ---
tap("Plus"); tap_scroll("Profil personnel")
slow_type("Nom ou pseudonyme", NAME)
back()
tap_scroll("Enregistrer", exact=True); time.sleep(3)
back(); tap_scroll("Profil personnel"); time.sleep(3)
pre_name = find(NAME) is not None
back()
tap_scroll("Traitements et observations")
tap_scroll("Ajouter un traitement")
slow_type("Médicament", TREAT)
back()
tap("Enregistrer", exact=True)
pre_treat = wait(TREAT, 60)
back()
tap_scroll("Assistant IA", exact=True)
tap("Configurer l'IA"); time.sleep(3)
rs = [n for n in nodes() if n.get("checkable") == "true"]
if len(rs) >= 2:
    sh("input", "tap", *map(str, center(rs[1]))); time.sleep(3)
pre_ai = radios()
record("MP01", "Valeurs saisies et vérifiées dans la 1.0.1", f"nom {NAME}, traitement {TREAT}, IA Anthropic (bouton radio)",
       "les 3 valeurs visibles dans la 1.0.1 avant mise à jour", f"nom={pre_name}, traitement={pre_treat}, radios={pre_ai}",
       pre_name and pre_treat and pre_ai == ["false", "true", "false"])
back(); back()

# --- Mise à jour ---
r = subprocess.run(ADB + ["install", "-r", APK_NEW], capture_output=True, text=True)
record("MP02", "Mise à jour 1.0.1 → 1.1.0", "adb install -r", "version 1.1.0", f"{r.stdout.strip()[-10:]} version {version()}", "Success" in r.stdout and version() == "1.1.0")
sh("am", "start", "-n", f"{PKG}/.MainActivity")
ok = wait(f"Bonjour {NAME}", 300)
record("MP03", "Profil conservé", f"nom « {NAME} » saisi en 1.0.1", f"accueil « Bonjour {NAME} »", "affiché" if ok else "absent", ok)
tap("Profil", exact=True)
tap_scroll("Traitements et observations")
ok = wait(TREAT, 60)
record("MP04", "Traitement conservé", f"« {TREAT} » saisi en 1.0.1", "présent dans la 1.1.0", "présent" if ok else "absent", ok)
back()
tap_scroll("Configuration de l'IA"); time.sleep(3)
post = radios()
record("MP05", "Fournisseur d'IA conservé", "Anthropic sélectionné en 1.0.1", "Anthropic toujours sélectionné", f"radios (Aucun, Anthropic, OpenAI) = {post}", post == ["false", "true", "false"])
crash = subprocess.run(ADB + ["logcat", "-d", "-b", "crash"], capture_output=True, text=True).stdout
record("MP06", "Aucun plantage", "journal crash", f"aucun plantage de {PKG}", "aucun" if f"Process: {PKG}," not in crash else "PLANTAGE", f"Process: {PKG}," not in crash)
open(out, "w").write("\n".join(LOG) + "\n")
