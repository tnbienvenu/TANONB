#!/usr/bin/env python3
"""Redémarrage du téléphone : les alarmes de rappel doivent être reprogrammées par le BootReceiver."""
import sys, time, subprocess
sys.path.insert(0, "/home/user/TANONB/recette")
from pilote_adb import *

PKG = sys.argv[1] if len(sys.argv) > 1 else "com.cardiosuivi.app"
out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/recette_reboot.tsv"


def app_alarms():
    d = sh("dumpsys", "alarm")
    return [l.strip() for l in d.splitlines() if PKG in l and "RTC_WAKEUP" in l]


before = app_alarms()
record("RB01", "Avant redémarrage", "rappels par défaut", "alarmes de l'application présentes", f"{len(before)} alarme(s)", len(before) >= 1)
subprocess.run(ADB + ["reboot"], capture_output=True)
time.sleep(60)
t0 = time.time()
while time.time() - t0 < 1800:
    r = subprocess.run(ADB + ["shell", "getprop", "sys.boot_completed"], capture_output=True, text=True)
    if r.stdout.strip() == "1":
        break
    time.sleep(15)
boot = int(time.time() - t0) + 60
time.sleep(240)  # laisse le temps au BOOT_COMPLETED d'être distribué
subprocess.run(ADB + ["root"], capture_output=True); time.sleep(5)
after = app_alarms()
open("/tmp/recette_reboot_alarms.txt", "w").write("AVANT\n" + "\n".join(before) + "\nAPRÈS\n" + "\n".join(after))
lc = subprocess.run(ADB + ["logcat", "-d"], capture_output=True, text=True).stdout
boot_rcv = "BOOT_COMPLETED" in lc
record("RB02", "Redémarrage du téléphone", f"adb reboot (démarrage {boot} s)", "alarmes reprogrammées sans ouvrir l'application",
       f"{len(after)} alarme(s) après redémarrage", len(after) >= 1, "/tmp/recette_reboot_alarms.txt")
open(out, "w").write("\n".join(LOG) + "\n")
