#!/usr/bin/env python3
"""Assemble docs/RECETTE.md : en-tête rédigé + tableau des anomalies + tableau détaillé généré depuis les journaux."""
import json, subprocess, os
R = os.path.dirname(os.path.abspath(__file__))
J = [f"{R}/preuves/appareil/journal.tsv", f"{R}/journal_hote.tsv", f"{R}/journal_release.tsv", f"{R}/journal_biometrie.tsv"]
subprocess.run(["python3", f"{R}/generer_rapport.py", f"{R}/anomalies.json", "/tmp/tableau_recette.md", *J], check=True)
an = json.load(open(f"{R}/anomalies.json", encoding="utf-8"))
rows = "\n".join(f"| {a['id']} | {a['gravite']} | {a['titre']} — {a['constat']} | {a['correction']} | {a['statut']} ({', '.join(a['tests'])}) |" for a in an)
head = open(f"{R}/rapport_entete.md", encoding="utf-8").read().replace("{ANOMALIES}", rows)
open(f"{R}/../docs/RECETTE.md", "w", encoding="utf-8").write(head + open("/tmp/tableau_recette.md", encoding="utf-8").read())
print("docs/RECETTE.md écrit")
