# Plan de livraison et contrôle qualité — V1

## Étape 0 — Installation et ADR
- Dépôt et versions identifiées ; scripts locaux reproductibles ; Docker Compose PostgreSQL + stockage objet local compatible S3 + services.
- CI minimale : formatage, typage, lint, tests, détection de secrets, vérification dépendances.
- `docs/adr/` pour décisions sur authentification, sessions, stockage, file de tâches, ORM, migrations, tests.
- Critère de passage : installation sur environnement vierge documentée et réellement exécutée.

## Étape 1 — Identité et organisations
- Utilisateurs, rôles, permissions, sessions ; bootstrap sécurisé ; organisations et contacts ; interface connexion/liste/édition.
- Contrôles : 401/403 ; déconnexion ; limitation login ; audit des changements de droits ; accès restreint aux données fiscales.

## Étape 2 — Pipeline AO et qualification
- Liste/filtres/pagination, fiches AO et CRUD, grille Go/No-Go versionnée, audit, protection concurrence, alertes échéances initiales.
- Critères : scénario complet et tests négatifs, erreurs compréhensibles.

## Étape 3 — Experts et références
- CV structurés et pièces, spécialités, références et ABE, recherche, sélections pour offres, export modèle initial sans invention.
- Critères : expert externe sans compte ; référence non vérifiée signalée.

## Étape 4 — GED et offres
- Téléversements privés, versions, classement, téléchargement autorisé ; checklists, collecte pièces, budget HT XOF, validations, soumission et résultats.
- Critères : impossibilité de soumettre sans contrôles ou dérogations ; exactitude montants et historique des versions.

## Étape 5 — Registre des contrats et dashboard
- Conversion gagnée → DRAFT, registre et jalons ; compteurs et alertes ; exports autorisés ; paramétrage métier.
- Critères : double conversion empêchée ; indicateurs dérivés de données réelles.

## Étape 6 — Qualification de la version
- Tests unitaires, intégration, E2E navigateur, sécurité et droits, sauvegarde/restauration, charge dimensionnée pour 1–10 utilisateurs.
- Recette par Direction et responsables ; documentation d'exploitation, procédures incidents, transfert des accès au maître d'ouvrage.

## Pour chaque livraison, remettre
1. Version et commit git ; liste des US réellement terminées.
2. Schéma/migrations et documentation API à jour.
3. Résultats des commandes exécutées, y compris échecs.
4. Scénarios de recette avec preuves reproductibles.
5. Limitations, risques et anomalies ouvertes.
6. Manuel court des fonctions nouvelles.
7. Prochain lot proposé, sans développement implicite hors périmètre.

## Conditions de réception V1
- Parcours AO → offre → résultat → projet de contrat effectif.
- Permissions/états métier contrôlés exclusivement côté serveur et couverts par tests.
- Données enregistrées et consultables après redémarrage ; fichiers sécurisés et versionnés.
- Sauvegarde et restauration démontrées ; documentation de déploiement ; absence de secrets dans le dépôt.
- Aucune anomalie critique/bloquante ouverte ; autres anomalies consignées et acceptées formellement.
- Réception utilisateur signée ou consignée par Direction.

## Points à arbitrer avant exploitation de production
- Hébergeur/région du stockage et conformité des transferts éventuels de données personnelles.
- Domaine et politique de courriels d'alerte.
- Barème d'arrondi et règles fiscales spécifiques aux offres (aucun taux par défaut inventé).
- Formats officiels de CV et fiches de références utilisés par OPTIMUM ASSOCIÉS.
- Liste effective des collaborateurs, rôles et délégations de validation.
- Politique de conservation et classification des documents.
- Dimensionnement cloud après mesure, devis et budget d'exploitation.
