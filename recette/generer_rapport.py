#!/usr/bin/env python3
"""Assemble le tableau de recette (Markdown) à partir des journaux des tests exécutés sur Android.

Usage : generer_rapport.py anomalies.json sortie.md journal1.tsv [journal2.tsv ...]
Chaque journal : Id, fonctionnalité, données, attendu, observé, RÉUSSI/ÉCHOUÉ/NON EXÉCUTÉ, preuve.
Un scénario exécuté plusieurs fois (avant puis après correction) est présenté avec son dernier
résultat et l'historique des échecs antérieurs.
"""
import csv, sys, json

anomalies_path, out, journals = sys.argv[1], sys.argv[2], sys.argv[3:]
rows = []
for j in journals:
    rows += [r for r in csv.reader(open(j, encoding='utf-8'), delimiter='\t') if len(r) >= 7]
anom = json.load(open(anomalies_path, encoding='utf-8'))
by_test = {}
for a in anom:
    for t in a.get('tests', []):
        by_test.setdefault(t, []).append(a)

order, last, history = [], {}, {}
for r in rows:
    tid = r[0]
    if tid not in last:
        order.append(tid)
    history.setdefault(tid, []).append(r)
    last[tid] = r

LABEL = {'RÉUSSI': '✅ réussi', 'ÉCHOUÉ': '❌ échoué', 'NON EXÉCUTÉ': '⏸ non exécuté'}
def esc(s): return s.replace('|', '\\|').replace('\n', ' ')

ok = sum(1 for t in order if last[t][5] == 'RÉUSSI')
ko = sum(1 for t in order if last[t][5] == 'ÉCHOUÉ')
ne = len(order) - ok - ko
retest = sum(1 for t in order if any(h[5] == 'ÉCHOUÉ' for h in history[t][:-1]))
lines = [f"Scénarios élémentaires : **{len(order)}** — réussis **{ok}**, échoués **{ko}**, non exécutés **{ne}** "
         f"(dont **{retest}** échoués lors d'une exécution antérieure puis réussis après correction ou reprise ; "
         f"{len(rows)} exécutions au total).\n",
         "| Id | Fonctionnalité | Données saisies | Résultat attendu | Résultat observé (dernière exécution) | Statut | Preuve | Gravité / correction |",
         "|---|---|---|---|---|---|---|---|"]
for tid in order:
    _, feat, data, exp, obs, st, proof = last[tid][:7]
    fails = [h for h in history[tid][:-1] if h[5] == 'ÉCHOUÉ']
    if fails:
        obs = obs + f" — *auparavant échoué ({len(fails)}×) : {fails[-1][4]}*"
    a = by_test.get(tid, [])
    gc = '; '.join(f"{x['id']} ({x['gravite']}) : {x['correction']}" for x in a) or '—'
    lines.append(f"| {tid} | {esc(feat)} | {esc(data)} | {esc(exp)} | {esc(obs)} | {LABEL.get(st, st)} | {esc(proof)} | {esc(gc)} |")
open(out, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
print(f"{len(order)} scénarios, {ok} réussis, {ko} échoués, {ne} non exécutés, {retest} repris")
