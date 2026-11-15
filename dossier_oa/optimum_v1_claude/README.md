# OPTIMUM ASSOCIÉS — Dossier de transmission à Claude Code

Version 1.0 du dossier de réalisation — 15 novembre 2026.

## Décisions validées
- Application web propriétaire, sur mesure ; React/Next.js + TypeScript, FastAPI/Python, PostgreSQL.
- 1 à 10 utilisateurs internes ; consultants externes référencés sans comptes de saisie.
- Hébergement cloud ; langue française ; FCFA/XOF uniquement.
- Pas de migration des anciens dossiers de gestion. Les références historiques peuvent être saisies manuellement.
- IA exclue de la V1 ; introduite en phase 5.
- V1 : gestion AO de bout en bout, organisations, experts, références, GED, offres et budgets prévisionnels, registre initial des contrats, authentification, alertes et audit.
- Pas de comptabilité complète ni de facturation électronique dans la V1.

## Fichiers
1. `PROMPT_CLAUDE_CODE.md` : consignes de démarrage et d'exécution à donner à Claude Code.
2. `SPECIFICATIONS_V1.md` : besoins, workflow, schéma conceptuel, contrats API et recettes.
3. `PLAN_DE_LIVRAISON.md` : jalons, preuves et protocole de revue.

## Procédure
1. Extraire le dossier dans un répertoire de travail dédié.
2. Ouvrir ce répertoire dans Claude Code.
3. Copier le contenu de `PROMPT_CLAUDE_CODE.md` comme instruction initiale ; demander de lire les autres fichiers.
4. Demander une revue du plan avant toute décision technique structurante non spécifiée.
5. Faire développer et tester lot par lot, avec commits, rapports de tests et démonstrations.

**Statut :** dossier de spécification et de passation ; aucune application n'a encore été construite ou testée.
