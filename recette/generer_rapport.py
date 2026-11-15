#!/usr/bin/env python3
"""Assemble le rapport de recette (Markdown) à partir du journal des tests exécutés sur Android."""
import csv, sys, os, json
journal, anomalies_path, out = sys.argv[1], sys.argv[2], sys.argv[3]
rows = [r for r in csv.reader(open(journal, encoding='utf-8'), delimiter='\t') if len(r) >= 7]
anom = json.load(open(anomalies_path, encoding='utf-8'))
by_test = {}
for a in anom:
    for t in a.get('tests', []): by_test.setdefault(t, []).append(a)
lines = []
ok = sum(1 for r in rows if r[5] == 'RÉUSSI'); ko = sum(1 for r in rows if r[5] == 'ÉCHOUÉ')
lines.append(f"Vérifications exécutées sur Android : **{len(rows)}** — réussies **{ok}**, échouées **{ko}**.\n")
lines.append("| Id | Fonctionnalité | Données saisies | Résultat attendu | Résultat observé | Statut | Preuve | Gravité / correction |")
lines.append("|---|---|---|---|---|---|---|---|")
def esc(s): return s.replace('|', '\\|')
for r in rows:
    tid, feat, data, exp, obs, st, proof = r[:7]
    a = by_test.get(tid, [])
    gc = '; '.join(f"{x['id']} ({x['gravite']}) : {x['correction']}" for x in a) or '—'
    lines.append(f"| {tid} | {esc(feat)} | {esc(data)} | {esc(exp)} | {esc(obs)} | {'✅ réussi' if st=='RÉUSSI' else '❌ échoué'} | {esc(proof)} | {esc(gc)} |")
open(out, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
print(f"{len(rows)} lignes, {ok} réussies, {ko} échouées")
