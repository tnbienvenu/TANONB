#!/usr/bin/env python3
"""
Pilotage d'une application Android depuis l'hôte par adb + uiautomator (arbre d'accessibilité).
Utilisé pour la recette de l'APK RELEASE (écran protégé : les captures sont noires, l'arbre reste lisible)
et pour les scénarios système (redémarrage du téléphone).
"""
import subprocess, time, re, sys, xml.etree.ElementTree as ET

SERIAL = "emulator-5554"
ADB = ["/opt/android-sdk/platform-tools/adb", "-s", SERIAL]
LOG = []


def sh(*args, timeout=180):
    return subprocess.run(ADB + ["shell", *args], capture_output=True, text=True, timeout=timeout).stdout


def nodes():
    for _ in range(3):
        sh("uiautomator", "dump", "/data/local/tmp/ui.xml")
        raw = sh("cat", "/data/local/tmp/ui.xml")
        try:
            root = ET.fromstring(raw)
            return [n for n in root.iter("node")]
        except ET.ParseError:
            time.sleep(3)
    return []


def texts():
    return [(n.get("text") or "") + "|" + (n.get("content-desc") or "") for n in nodes()]


def find(label, exact=False):
    for n in nodes():
        t = n.get("text") or n.get("content-desc") or ""
        if (t == label) if exact else (label in t):
            return n
    return None


def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def dismiss_anr():
    n = find("isn't responding")
    if n is not None:
        w = find("Wait", exact=True)
        if w is not None:
            sh("input", "tap", *map(str, center(w)))
            time.sleep(2)


def wait(label, timeout=180):
    t0 = time.time()
    while time.time() - t0 < timeout:
        dismiss_anr()
        if find(label) is not None:
            return True
        time.sleep(3)
    return False


def tap(label, exact=False, timeout=120):
    if not wait(label, timeout):
        raise AssertionError(f"Élément introuvable : {label}")
    n = find(label, exact)
    sh("input", "tap", *map(str, center(n)))
    time.sleep(2)


def tap_res(res, timeout=30):
    t0 = time.time()
    while time.time() - t0 < timeout:
        for n in nodes():
            if n.get("resource-id") == res:
                sh("input", "tap", *map(str, center(n)))
                time.sleep(2)
                return True
        time.sleep(2)
    return False


def type_in(label, value):
    tap(label)
    sh("input", "text", value.replace(" ", "%s"))
    time.sleep(1)


def scroll_down():
    sh("input", "swipe", "360", "1400", "360", "500", "400")
    time.sleep(2)


def record(tid, feature, data, expected, observed, ok, proof="journal adb"):
    LOG.append("\t".join([tid, feature, data, expected, observed, "RÉUSSI" if ok else "ÉCHOUÉ", proof]))
    print(("OK  " if ok else "KO  ") + tid + " " + observed, flush=True)
