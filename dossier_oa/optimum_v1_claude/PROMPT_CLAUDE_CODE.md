# PROMPT DIRECTEUR — CLAUDE CODE

Tu interviens comme architecte logiciel senior et ingénieur full-stack chargé de réaliser la V1 de l'application professionnelle de gestion du bureau d'études OPTIMUM ASSOCIÉS.

## 0. Lecture obligatoire
Lis intégralement `README.md`, `SPECIFICATIONS_V1.md` et `PLAN_DE_LIVRAISON.md`. Ces documents constituent la base contractuelle de travail. Ne substitue pas des fonctionnalités imaginées à celles qui y figurent. En cas d'ambiguïté bloquante, documente le point et demande une décision ciblée ; pour les choix techniques réversibles et non bloquants, adopte une option prudente et consigne-la dans `docs/adr/`.

## 1. Contraintes non négociables
- Application propriétaire sur mesure, interface française responsive et accessible, orientée ordinateur et smartphone.
- Next.js/TypeScript frontend, Python/FastAPI backend, PostgreSQL, migrations Alembic, stockage objet S3 privé (émulation locale possible), tâches de fond explicites.
- 1 à 10 utilisateurs internes ; aucun accès direct aux consultants externes.
- FCFA/XOF seulement : montants en nombres entiers non négatifs, distinguer HT/taxes/TTC et conserver les données fiscales déclarées, sans inventer de taux.
- Cloud à terme, Docker Compose pour développement local. Trois configurations : local, recette, production.
- Aucune reprise des anciens dossiers ; base initiale vide, sauf référentiels et compte bootstrap. Pas de données professionnelles réelles dans les jeux de démonstration.
- IA, OCR avancé, intégration Kobo, facturation/FNE, gestion opérationnelle détaillée et timesheets hors V1 ; préparer l'extensibilité sans coder de faux modules.
- Authentification et autorisation strictes côté serveur ; audit ; versionnement documentaire ; sauvegarde et restauration testables.
- Ne jamais mettre de jetons, mots de passe ou clés API en dur dans le dépôt. Aucun déploiement externe ni dépense cloud sans autorisation explicite.

## 2. Mode de travail
1. Inspecte l'environnement de développement et les versions réellement installées.
2. Propose un court plan d'implémentation et une matrice de traçabilité US → endpoints → tables → tests.
3. Réalise le projet par petits lots livrables : fondations, IAM, organisations, AO/qualification, experts/références, offres/GED, conversion contrat, tableaux de bord/notifications, recette.
4. Pour chaque lot : migrations réversibles lorsque possible, API, interface, permissions, tests unitaires/intégration/E2E, documentation et démonstration reproductible.
5. Lance effectivement les commandes de validation disponibles. Ne déclare jamais un test « passé » s'il n'a pas été exécuté. Indique les blocages d'environnement et différencie tests simulés et tests réels.
6. Garde l'historique des décisions et des limitations ; ne masque jamais une fonctionnalité inachevée derrière une interface fictive.
7. Avant chaque fin de lot, fournis : fichiers modifiés, commandes exécutées, résultats observés, tests manquants, risques, capture ou procédure de vérification, et prochaine étape.

## 3. Critères stricts de réalisation
- Les formulaires réels persistent en base ; ils ne s'appuient pas sur des données de démonstration codées en dur.
- Toute API contrôle les autorisations, l'appartenance de la ressource et les états métier, y compris via accès direct par identifiant.
- Les transitions Go/No-Go, soumission et conversion sont des commandes métier atomiques, historisées et protégées contre répétition/concurrence.
- Les documents résident dans un stockage privé ; leurs versions précédentes sont conservées ; la lecture et le téléchargement vérifient les droits ; validation du type réel, taille, analyse antivirus configurable.
- Les exports sont filtrés par droits et protègent contre les injections de formules dans les feuilles CSV/Excel.
- Les tâches planifiées sont idempotentes ; alertes J-15/J-7/J-3/J-1 configurables ; heure locale d'Abidjan correctement traitée.
- Prévoir des sauvegardes quotidiennes et une procédure de restauration réellement vérifiée sur environnement isolé avant réception.
- Aucune « réussite de recette » sans preuves (journaux, captures des parcours, commande de tests, rapports).

## 4. Premier lot demandé
Commence par la **fondation exécutable**, puis implémente le **premier parcours complet** : utilisateur habilité → organisation cliente → création d'opportunité → grille de qualification → décision Go/No-Go → historique d'audit. Tu peux préparer la structure des autres modules mais ne marque pas les user stories non implémentées comme terminées.

**Livrables minimaux du premier lot :**
- Dépôt structuré `apps/web`, `apps/api`, `infrastructure`, `docs`, `tests`.
- Instructions `README` et `.env.example`, sans secrets.
- `docker compose up` documenté pour les dépendances locales.
- Migrations de base pour utilisateurs, rôles, permissions, organisations, opportunités, grilles et décisions Go/No-Go, audit.
- Interface de connexion, organisations, registre AO, création/édition AO, qualification et décision.
- OpenAPI et tests de permissions (notamment 401/403), tests de transitions et de concurrence.
- Compte d'administration initial créé via commande de bootstrap sécurisée, sans identifiants par défaut permanents.

Ne lance pas la construction des phases V2–V5. Demande une revue du premier lot achevé avant d'ouvrir le lot suivant.
