#!/usr/bin/env python3
"""Recette de l'APK RELEASE signé (minifié R8) : parcours principaux, écran protégé, sérialisation, PDF, redémarrage."""
import sys, time, subprocess
sys.path.insert(0, "/home/user/TANONB/recette")
from pilote_adb import *

PKG = "com.cardiosuivi.app"
APK = "/home/user/TANONB/app/build/outputs/apk/release/app-x86_64-release.apk"
out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/recette_release.tsv"
import atexit
atexit.register(lambda: open(out, "w").write("\n".join(LOG) + "\n"))

subprocess.run(ADB + ["uninstall", PKG], capture_output=True)
r = subprocess.run(ADB + ["install", "-r", APK], capture_output=True, text=True)
record("RL00", "APK release – installation", "app-x86_64-release.apk signé", "installation réussie", r.stdout.strip()[-40:], "Success" in r.stdout)
sh("pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
sh("am", "start", "-n", f"{PKG}/.MainActivity")
ok = wait("Bienvenue dans CardioSuivi", 240)
record("RL01", "Release – démarrage", "premier lancement", "avertissement initial affiché", "affiché" if ok else "absent", ok)
subprocess.run(ADB + ["exec-out", "screencap", "-p"], stdout=open("/tmp/rl_secure.png", "wb"))
from PIL import Image
ext = Image.open("/tmp/rl_secure.png").convert("L").getextrema()
record("RL02", "Release – écran protégé", "capture d'écran système", "capture noire (FLAG_SECURE actif par défaut)", f"niveaux de gris {ext}", ext[1] <= 5, "/tmp/rl_secure.png")
for _ in range(3):
    if find("J'ai compris") is None: scroll_down()
tap("J'ai compris")
for _ in range(4):
    if find("Commencer", exact=True) is not None: break
    scroll_down()
tap("Commencer", exact=True)
ok = wait("NOUVELLE SÉANCE", 240)
record("RL03", "Release – tableau de bord", "acceptation", "tableau de bord", "affiché" if ok else "absent", ok)

# Séance manuelle de 2 mesures (fictives) puis arrêt
tap("NOUVELLE SÉANCE")
tap("Je suis déjà reposé(e) : commencer")
tap_res("com.android.permissioncontroller:id/permission_allow_foreground_only_button", 20) or tap_res("com.android.permissioncontroller:id/permission_allow_one_time_button", 5)
for i, (s, d, p) in enumerate([("142", "91", "71"), ("138", "88", "69")]):
    tap("Saisie manuelle", timeout=240)
    type_in("Systolique (SYS", s); type_in("Diastolique (DIA", d); type_in("Fréquence cardiaque (PUL", p)
    sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(1)
    tap("Valider ces valeurs")
    ok = wait(f"Mesure {i+1} enregistrée", 180)
    record(f"RL04-{i+1}", "Release – saisie de mesure", f"{s}/{d}/{p}", f"« Mesure {i+1} enregistrée »", "OK" if ok else "échec", ok)
    if i == 0: tap("Mesurer maintenant")
tap("Arrêter ici")
ok = wait("Moyenne des 2 mesures", 120) or wait("140,0/89,5", 30)
record("RL05", "Release – calculs", "142/91/71 et 138/88/69", "moyenne 140,0/89,5 · 70,0", "conforme" if find("140,0/89,5") is not None else "non trouvé", find("140,0/89,5") is not None)
for _ in range(4):
    if find("Enregistrer la séance incomplète") is None: scroll_down()
tap("Enregistrer la séance incomplète")
ok = wait("Terminer", 120)
record("RL06", "Release – enregistrement", "séance incomplète", "écran « Séance enregistrée »", "OK" if ok else "échec", ok)
tap("Terminer")

# Sérialisation (kotlinx.serialization après R8) : aperçu des données IA
tap("Plus"); tap("Assistant IA")
tap("Données transmises")
ok = wait("generatedAt", 120)
record("RL07", "Release – sérialisation JSON (R8)", "aperçu « Données transmises »", "JSON du contexte affiché sans plantage", "affiché" if ok else "absent", ok)
tap("Fermer", exact=True)
sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(2)

# Rapport PDF (PdfDocument après R8)
tap("Rapports PDF et CSV")
tap("Générer le rapport PDF")
ok = wait("Rapport PDF généré.", 240)
record("RL08", "Release – rapport PDF", "période 30 jours", "« Rapport PDF généré. »", "OK" if ok else "échec", ok)
crash = subprocess.run(ADB + ["logcat", "-d", "-b", "crash"], capture_output=True, text=True).stdout
record("RL09", "Release – plantages", "journal crash du système", "aucun plantage de com.cardiosuivi.app", "aucun" if PKG not in crash else "PLANTAGE", PKG not in crash)

# Persistance après arrêt forcé du processus
sh("am", "force-stop", PKG)
sh("am", "start", "-n", f"{PKG}/.MainActivity")
ok = wait("NOUVELLE SÉANCE", 240)
tap("Historique")
ok2 = wait("140/90 mmHg", 120)
record("RL10", "Release – persistance (processus tué)", "force-stop puis relance", "séance 140/90 toujours dans l'historique (base chiffrée)", "présente" if ok2 else "absente", ok and ok2)

open(out, "w").write("\n".join(LOG) + "\n")
