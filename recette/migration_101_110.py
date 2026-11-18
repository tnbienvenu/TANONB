#!/usr/bin/env python3
"""
Mise à jour réelle CardioSuivi 1.0.1 → 1.1.0 sur l'émulateur, avec les APK RELEASE signés (même certificat).
1. Installation de la 1.0.1, saisie de données FICTIVES par son interface (profil, séance du soir au bras droit avec une
   mesure sans pouls, traitement, rappel du lundi, fournisseur d'IA).
2. Installation de la 1.1.0 PAR-DESSUS (adb install -r, sans désinstallation).
3. Vérification dans l'interface 1.1.0 que tout est conservé à l'identique (valeurs, horodatages à la seconde, bras,
   période, traitement, rappels, paramètres IA), puis nouvelle séance 1.1.0.
Usage : migration_101_110.py <apk_1.0.1> <apk_1.1.0> <journal.tsv>
"""
import re, sys, time, subprocess
sys.path.insert(0, "/home/user/TANONB/recette")
from pilote_adb import *

PKG = "com.cardiosuivi.app"
APK_OLD, APK_NEW, out = sys.argv[1], sys.argv[2], sys.argv[3]
import atexit
atexit.register(lambda: open(out, "w").write("\n".join(LOG) + "\n"))
TIME = re.compile(r"\b\d\d:\d\d:\d\d\b")


def collect_times():
    seen = []
    for _ in range(5):
        for t in texts():
            for m in TIME.findall(t):
                if m not in seen:
                    seen.append(m)
        scroll_down()
    for _ in range(5):
        scroll_up()
    return seen


def version():
    d = sh("dumpsys", "package", PKG)
    m = re.search(r"versionName=(\S+)", d)
    return m.group(1) if m else "?"


subprocess.run(ADB + ["uninstall", PKG], capture_output=True)
subprocess.run(ADB + ["logcat", "-b", "crash", "-c"], capture_output=True)
r = subprocess.run(ADB + ["install", APK_OLD], capture_output=True, text=True)
record("MG00", "Installation 1.0.1", APK_OLD.split("/")[-1], "installation de la 1.0.1", f"{r.stdout.strip()[-20:]} version {version()}", "Success" in r.stdout and version() == "1.0.1")
sh("pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
sh("am", "start", "-n", f"{PKG}/.MainActivity")
wait("Bienvenue dans CardioSuivi", 240)
for _ in range(3):
    if find("J'ai compris") is None: scroll_down()
tap("J'ai compris")
for _ in range(4):
    if find("Commencer", exact=True) is not None: break
    scroll_down()
tap("Commencer", exact=True)
wait("NOUVELLE SÉANCE", 240)

# Profil
tap("Plus"); tap_scroll("Profil personnel")
type_in("Nom ou pseudonyme", "MIGRATION-FICTIF")
sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(1)
tap_scroll("Enregistrer", exact=True)
sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(2)
# Traitement
tap_scroll("Traitements et observations")
tap_scroll("Ajouter un traitement")
type_in("Médicament", "TRAITEMENT-FICTIF")
sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(1)
tap("Enregistrer", exact=True)
wait("TRAITEMENT-FICTIF", 60)
sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(2)
# Rappels : ajout du lundi
tap_scroll("Rappels", exact=True)
wait("Jours de mesure", 60)
tap("lun.", exact=True); time.sleep(2)
sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(2)
# Fournisseur d'IA (sans clé, aucune donnée transmise)
tap_scroll("Assistant IA", exact=True)
tap("Configurer l'IA")
tap("Anthropic Claude"); time.sleep(2)
sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(2)
sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(2)
tap("Accueil", exact=True)

# Séance 1.0.1 : soir, bras droit, 3 mesures dont une sans pouls
tap("NOUVELLE SÉANCE")
wait("Avant de commencer", 120)
tap("Soir", exact=True); tap("Bras droit", exact=True)
tap_scroll("Je suis déjà reposé(e) : commencer")
tap_res("com.android.permissioncontroller:id/permission_allow_foreground_only_button", 20) or tap_res("com.android.permissioncontroller:id/permission_allow_one_time_button", 5)
for i, (s, d, p) in enumerate([("142", "91", "71"), ("138", "88", ""), ("135", "86", "70")]):
    tap("Saisie manuelle", timeout=240)
    type_in("Systolique (SYS", s); type_in("Diastolique (DIA", d)
    if p: type_in("Fréquence cardiaque (PUL", p)
    sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(1)
    tap("Valider ces valeurs")
    if i < 2:
        wait(f"Mesure {i+1} enregistrée", 180)
        tap("Mesurer maintenant")
wait("Moyennes de la séance", 180)
tap_scroll("Valider la séance")
wait("Terminer", 120); tap("Terminer")
# Relevé des valeurs et horodatages affichés par la 1.0.1
tap("Historique", exact=True)
tap("138/88", timeout=120)
wait("Détail de la séance", 120)
before_times = collect_times()
before_texts = " ".join(texts())
record("MG01", "Données saisies en 1.0.1", "profil, traitement, lundi, IA Anthropic, séance soir bras droit 142/91/71, 138/88/—, 135/86/70",
       "séance enregistrée avec 3 horodatages", f"horodatages {before_times}", len(before_times) >= 3 and "Bras droit" in before_texts)
sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(2)

# --- Mise à jour vers 1.1.0 (sans désinstaller) ---
r = subprocess.run(ADB + ["install", "-r", APK_NEW], capture_output=True, text=True)
record("MG02", "Mise à jour 1.0.1 → 1.1.0", "adb install -r (même certificat)", "mise à jour acceptée, version 1.1.0",
       f"{r.stdout.strip()[-20:]} {r.stderr.strip()[-80:]} version {version()}", "Success" in r.stdout and version() == "1.1.0")
sh("am", "start", "-n", f"{PKG}/.MainActivity")
ok = wait("Bonjour MIGRATION-FICTIF", 300)
record("MG03", "Démarrage après mise à jour", "première ouverture 1.1.0 (migration Room 1→2)", "accueil direct, profil conservé, sans réinitialisation",
       "accueil « Bonjour MIGRATION-FICTIF »" if ok else "absent", ok and find("Bienvenue dans CardioSuivi") is None)
tap("Mesures", exact=True)
ok = wait("138/88", 120)
tap("138/88", timeout=60)
wait("Détail de la séance", 120)
after_times = collect_times()
t = " ".join(texts())
vals = all(v in t for v in ["142/91 mmHg · 71 bpm", "138/88 mmHg · pouls non renseigné", "135/86 mmHg · 70 bpm"])
record("MG04", "Mesures conservées", "séance 1.0.1", "3 mesures identiques, pouls absent conservé (non inventé)", "conformes" if vals else f"différentes : {t[:300]}", vals)
same = [x for x in before_times if x in after_times]
record("MG05", "Horodatages conservés", f"1.0.1 : {before_times}", "mêmes heures à la seconde après mise à jour", f"1.1.0 : {after_times}", len(same) == len(before_times) and len(before_times) >= 3)
record("MG06", "Bras et période conservés", "séance 1.0.1 « Soir », « Bras droit »", "aucune réécriture rétroactive (pas de « bras gauche »)",
       "bras droit + soir" if ("Bras droit" in t and "soir" in t) else f"autre : {t[:200]}", "Bras droit" in t and "soir" in t and "Bras gauche" not in t)
sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(2)
tap("Profil", exact=True)
tap_scroll("Traitements et observations")
ok = wait("TRAITEMENT-FICTIF", 60)
record("MG07", "Traitements conservés", "traitement saisi en 1.0.1", "présent", "présent" if ok else "absent", ok)
sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(2)
tap_scroll("Rappels", exact=True)
wait("Jours de mesure", 60)
mon = find("lun.", exact=True)
sel = mon is not None and (mon.get("checked") == "true" or mon.get("selected") == "true")
if not sel and mon is not None:
    # FilterChip : l'état « sélectionné » est porté par le nœud cliquable parent
    for n in nodes():
        if n.get("clickable") == "true" and n.get("bounds") and any((c.get("text") == "lun.") for c in n.iter("node")):
            sel = n.get("checked") == "true" or n.get("selected") == "true"
record("MG08", "Rappels conservés", "lundi ajouté en 1.0.1", "lundi toujours sélectionné ; horaires 07:00 / 19:00", f"lundi sélectionné={sel}", sel and wait("à 07:00", 10) and wait("à 19:00", 10))
alarms = [l for l in sh("dumpsys", "alarm").splitlines() if PKG in l and "RTC_WAKEUP" in l]
record("MG09", "Alarmes reprogrammées après mise à jour", "dumpsys alarm", "alarmes de rappel présentes (MY_PACKAGE_REPLACED)", f"{len(alarms)} alarme(s)", len(alarms) >= 1)
sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(2)
tap_scroll("Configuration de l'IA")
an = find("Anthropic Claude")
selected = False
for n in nodes():
    if n.get("class", "").endswith("RadioButton") or n.get("checkable") == "true":
        pass
txt = texts()
# Le bouton radio sélectionné est le premier nœud « checked » de la liste des fournisseurs.
radios = [n for n in nodes() if n.get("checkable") == "true"]
labels = ["Aucun", "Anthropic", "OpenAI"]
state = [r.get("checked") for r in radios[:3]]
record("MG10", "Paramètres IA conservés", "fournisseur Anthropic choisi en 1.0.1", "Anthropic toujours sélectionné, aucun consentement ajouté",
       f"états des boutons radio (Aucun, Anthropic, OpenAI) = {state}", state[:3] == ["false", "true", "false"])
sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(2)

# Nouvelle séance 1.1.0 à côté des données 1.0.1
tap("Mesures", exact=True)
tap("Nouvelle séance", exact=True)
tap_scroll("Je suis déjà reposé(e) : commencer")
for i, (s, d, p) in enumerate([("131", "84", "74"), ("129", "83", "72"), ("128", "82", "72")]):
    type_in("SYS — ", s); type_in("DIA — ", d); type_in("PUL — ", p)
    sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(1)
    tap("Valider et continuer")
    if i < 2:
        wait(f"Mesure {i+1} enregistrée", 180)
        tap("Mesurer maintenant")
wait("Récapitulatif des mesures", 180)
tap_scroll("Valider la séance")
wait("Terminer", 120); tap("Terminer")
tap("Mesures", exact=True)
ok = wait("129/83", 120) and wait("138/88", 30)
record("MG11", "Nouvelle séance après mise à jour", "131/84/74, 129/83/72, 128/82/72", "deux séances dans l'historique (1.0.1 et 1.1.0)", "présentes" if ok else "absentes", ok)
crash = subprocess.run(ADB + ["logcat", "-d", "-b", "crash"], capture_output=True, text=True).stdout
record("MG12", "Aucun plantage", "journal crash", f"aucun plantage de {PKG}", "aucun" if f"Process: {PKG}," not in crash else "PLANTAGE", f"Process: {PKG}," not in crash)
open(out, "w").write("\n".join(LOG) + "\n")
