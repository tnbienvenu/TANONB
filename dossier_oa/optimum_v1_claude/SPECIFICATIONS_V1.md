# Spécifications de conception V1 — OPTIMUM ASSOCIÉS

**Date de référence :** 15 novembre 2026. **Nature :** cible à implémenter ; pas un rapport de développement réalisé.

## A. Contexte et périmètre
Logiciel web propriétaire de gestion du cycle commercial d'un bureau d'études, jusqu'à la création d'un projet de contrat. Utilisateurs : Direction, chef de projet/responsable études, RAF, assistant administratif et administrateur technique séparé. Consultants externes : fiches de ressources, sans authentification. Devise XOF seulement, affichage FCFA. Usage 1–10 utilisateurs internes. Cloud au déploiement, local via Docker Compose. Pas de reprise des anciens AO/contrats ; saisie manuelle des références historiques autorisée. IA en phase 5.

### Modules V1
E01 (US-001–007) : utilisateurs, sessions, permissions, MFA pour comptes sensibles, audit accès.
E02 (US-008–012) : tableaux de bord, compteurs, échéances, notifications, accès aux actions.
E03 (US-013–023) : AO, scoring, Go/No-Go, responsables, échéances, historique, exports.
E04 (US-024–028) : organisations, contacts, dédoublonnage suggéré, rattachement aux AO, groupements.
E05 (US-029–035) : experts, diplômes, expériences, CV, filtres, association offres, génération sur modèle.
E06 (US-036–040) : références, ABE, filtres, sélection, fiches DOCX/PDF.
E07 (US-041–048) : GED, métadonnées, versions, téléchargements privés, archive, ZIP.
E08 (US-049–056) : checklist de conformité, pièces partenaires, budget d'offre, marge, approbation, soumission, résultats.
E09 (US-057–061) : conversion d'AO gagné en projet de contrat, données de contrat, pièces, jalons, registre.
E10 (US-062–067) : paramètres, référentiels, alertes, audit, sauvegardes, réversibilité.

### Hors périmètre V1
Timesheets, suivi du terrain, dépenses d'exécution, FNE/factures, encaissements, contrats de sous-traitance détaillés, workflow de validation des rapports, OCR avancé, IA et intégrations Kobo/agendas.

## B. Écrans de référence
SCR-01 Connexion ; 02 Dashboard ; 03 Registre AO ; 04 Création AO ; 05 Fiche AO ; 06 Qualification ; 07 Préparation offre ; 08 Budget offre ; 09 Soumission/résultat ; 10 Organisations ; 11 Fiche organisation ; 12 Groupements ; 13 Experts ; 14 Fiche expert ; 15 Références ; 16 Fiche référence ; 17 GED ; 18 Document/version ; 19 Contrats ; 20 Fiche contrat ; 21 Notifications ; 22 Comptes ; 23 Permissions ; 24 Paramètres ; 25 Audit ; 26 Profil ; 27 Exports.

Priorité ergonomique : registre AO avec recherche par objet/code/client, filtres état, responsable, échéance ; page AO avec onglets général/qualification/équipe/documents/budget/historique ; formulaire AO à sections ; checklist avec responsabilité et pièce justificative ; budgets en lignes calculées ; états explicites vide/chargement/erreur/conflit.

## C. Données : champs et relations minimales

### Organisation (`organizations`)
`id UUID`, `legal_name` requis, `display_name`, `country_code`, `tax_identifier` (sensible), `email`, `phone`, `address`, `is_active`, `created_at`, `updated_at`. Rôles multiple via `organization_roles` (CLIENT, FUNDER, PARTNER, SUPPLIER), contacts séparés. Recherche de doublons par nom normalisé et identifiant fiscal ; pas de fusion automatique.

### Utilisateur
`id UUID`, `email` unique insensible à la casse, `full_name`, `password_hash` ou identité gérée par un IdP, `is_active`, `created_at`; `roles`, `permissions`, `sessions`/tokens ; audit IAM. MFA comptes à risque. L'administrateur technique n'obtient pas automatiquement le droit de consulter les contrats et pièces sensibles.

### Opportunité (`opportunities`)
`id UUID`, `code` interne unique et immuable, `title` obligatoire max 500, `external_reference`, `client_id` obligatoire, `funder_id` facultatif, `procedure_type`, `sector_id`, `location`, `country_code` défaut CI, `source_url`, `published_at`, `deadline_at TIMESTAMPTZ` obligatoire, `estimated_amount_xof BIGINT NULL CHECK >=0`, `owner_id` utilisateur actif, `description`, `status`, `row_version`, `created_at`, `updated_at`. Un budget inconnu = NULL (jamais 0). Sauvegarder le fuseau d'origine des échéances si différent d'Abidjan ; présenter Africa/Abidjan. Création après échéance : avertissement et justification à conserver.

### Qualification
`qualification_criteria` : libellé, pondération, caractère éliminatoire, version de grille, actif. `qualification_scores` : AO, critère, note, justification, auteur, révision. `go_no_go_decisions` : AO, décision, justification, décideur, horodatage, score figé, version de grille. Proposition de pondérations initiales : technique 30, références 25, experts 20, marge 15, préparation 10 ; somme 100, notes 0–5. Score = somme(note × poids / 5). Seuil automatique éventuel non décidé : ne pas en inventer ; l'arbitrage appartient à la Direction et les conditions éliminatoires prévalent.

### Expert
`experts` : nom requis, type interne/externe, métier, contacts restreints, disponibilité ; `expert_skills`, `degrees`, `expert_experiences`, `expert_languages`, `expert_documents`, `offer_experts`. Aucune génération de qualification ou expérience qui n'existe pas dans les données source. Consultants externes ≠ utilisateurs.

### Référence
`project_references`: intitulé, organisation cliente, secteur, période, zone, portée, montant en XOF si connu, part propre du bureau si connue, état de vérification, autorisation d'usage, ABE facultative. `offer_references` relie offre et référence. Référence historique = saisie individuelle ; pas de migration de dossiers anciens.

### Offre et budget
`offers` liées aux opportunités ; `offer_checklist_items` : libellé, obligatoire, responsable, échéance, statut, justificatif/dispense, validation ; `offer_budgets`, `offer_budget_lines` : catégorie, unité, quantité décimale, prix unitaire XOF, coût unitaire XOF, montant calculé, notes. Calcul au serveur avec Decimal et arrondis paramétrés : somme quantités × prix ; marge brute prévisionnelle = prix d'offre HT - coûts directs estimés. Versionner les budgets approuvés et distinguer vente / coût ; garder fiscalité déclarative sans taux inventé.

### GED
`document_folders`, `documents`, `document_versions`, `document_links`, `document_access_events`. Pour chaque version : taille, type MIME réel, hash SHA-256, storage_key privé, auteur, date, catégorie, confidentialité. Ajouter des versions, ne pas écraser. Antimalware ; téléchargement après permission. Rattachement polymorphe encadré par types autorisés ; cohérence en base ou validation transactionnelle. Ne pas exposer les clés privées de stockage.

### Contrat initial
`contracts`: AO d'origine facultatif, code unique, titre, client, montant HT XOF, responsable, référence externe, dates de signature/notification/d'effet, OS facultatif, statut DRAFT, jalons/livrables initiaux. Conversion d'un AO gagné en contrat brouillon idempotente : au maximum un contrat initial par AO dans la V1. Aucun statut actif sans validation explicite.

### Journaux/alertes
`audit_events` append-only, auteur, action, entité, identifiant, horodatage UTC, identifiant de corrélation, avant/après masqué si sensible ; `notifications` : utilisateur, type, ressource, échéance, délivré/lu/acquitté. Règles de notification J-15/J-7/J-3/J-1 par défaut, paramétrables et sans doublons par échéance et seuil.

## D. Workflow et invariants
`DETECTED -> QUALIFYING -> GO | NO_GO`; `GO -> PREPARING -> SUBMITTED -> UNDER_EVALUATION -> WON | LOST`, et `CANCELLED` depuis états autorisés. Les transitions anticipées et retours doivent être définis explicitement et journalisés. No-Go nécessite motif et décision de Direction. Réouverture No-Go uniquement par Direction, motivée. Soumission exige validation, canal/date et accusé ou dérogation documentée. L'issue gagnée exige preuve ou résultat documenté. Conversion vers contrat possible seulement si WON ; crée un DRAFT ; transaction et idempotence obligatoires. Ne jamais permettre un changement de statut générique hors commande métier.

## E. API initiale (sous `/api/v1`)
- Auth : `POST /auth/login`, `/auth/logout`, `/auth/password-reset/request`, `/auth/password-reset/confirm`, `/auth/mfa/verify`; `GET /auth/me`.
- IAM : `GET/POST /users`, `GET/PATCH /users/{id}`, rôles/permissions administratifs.
- Organisations : `GET/POST /organizations`, `GET/PATCH /organizations/{id}`, contacts et rôles.
- AO : `GET/POST /opportunities`, `GET/PATCH /opportunities/{id}`, `GET /opportunities/{id}/events`.
- Qualification : `GET /opportunities/{id}/qualification`, `PUT /opportunities/{id}/qualification/scores`, `POST /opportunities/{id}/qualification/decision`.
- Groupements : `GET/POST /opportunities/{id}/consortiums`.
- Experts : `GET/POST /experts`, `GET/PATCH /experts/{id}`.
- Références : `GET/POST /references`, `GET/PATCH /references/{id}`.
- Offres : `GET/POST /offers`, `GET/PATCH /offers/{id}`, `GET/POST /offers/{id}/checklist-items`, `GET/PUT /offers/{id}/budget`, `POST /offers/{id}/budget-lines`, `POST /offers/{id}/approve`, `POST /offers/{id}/submit`.
- Résultat : `POST /opportunities/{id}/result`.
- Contrat : `POST /opportunities/{id}/convert-to-contract`, `GET/POST /contracts`, `GET/PATCH /contracts/{id}`, `GET/POST /contracts/{id}/milestones`.
- Documents : demande de téléversement, finalisation vérifiée, versions, métadonnées et téléchargement protégé.
- Dashboard, alertes, audit, exports : `GET /dashboard/summary`, `GET /notifications`, `POST /notifications/{id}/acknowledge`, `GET /audit-events`, `POST /exports`, `GET /exports/{id}`.

API : pagination et filtrage côté serveur, conventions d'erreurs `401/403/404/409/422/429`, erreurs métier codées et corrélation ; validation OpenAPI ; idempotence et versionnement optimiste pour commandes critiques ; limitation du débit pour authentification et exports.

## F. Permissions de départ
Direction : décision et validation finale, lecture financière, contrôle du portefeuille ; chef de projet : AO et offres sur périmètre autorisé, experts et dossiers affectés ; RAF : pièces administratives, budgets, organisations et contrats brouillons ; assistant : saisie limitée ; administrateur technique : comptes/infrastructure, pas automatiquement accès documentaire confidentiel. Toute permission se vérifie côté serveur sur action ET ressource. Les droits seront configurables et les utilisateurs auront seulement les privilèges nécessaires.

## G. Recette minimale obligatoirement exécutée
1. Non-authentifié → 401 sur une ressource privée.
2. Non-habilité → 403 sans écriture ni modification de statut.
3. AO sans date limite → 422.
4. AO budget inconnu → NULL, distinct de zéro.
5. Deux mises à jour concurrentes → conflit 409 ; aucune perte silencieuse.
6. Go/No-Go : score exact, condition éliminatoire respectée, décision motivée et audit.
7. No-Go → préparation interdite sans réouverture autorisée.
8. Checklist obligatoire manquante → refus de soumission, sauf dérogation habilitée auditée.
9. Version fichier 2 créée → version 1 toujours récupérable par habilité.
10. URL/id fichier deviné par utilisateur tiers → accès interdit.
11. Expert externe → pas de compte automatiquement créé.
12. Détection de référence sans ABE → absence non masquée.
13. Budgets : montants et marges calculés exactement et versions approuvées conservées.
14. Deux conversions AO gagné concurrentes → un seul contrat DRAFT.
15. Export → uniquement données visibles par l'auteur, sécurité CSV.
16. Alertes rejouées → pas de doublons.
17. Sauvegarde et restauration → tables ET documents recouvrés, contrôle d'intégrité.
18. Parcours utilisateur réel sur navigateur et petit écran → fonctionnement attesté.

## H. Exigences opérationnelles
Cibles de cadrage à valider : disponibilité mensuelle 99,5 % hors maintenance annoncée ; p95 des opérations usuelles <= 3 s, hors transferts lourds ; RPO 24 h et RTO 8 h, sous réserve de démonstration technique. Sauvegardes quotidiennes avec chiffrement et test de restauration. Logs sans secrets ; contrôle des dépendances, sécurité des uploads, MFA comptes sensibles, HTTPS, gestion des sessions. Référence sécurité : OWASP ASVS, version stable vérifiée au lancement. Conformité des traitements de données personnelles en Côte d'Ivoire à instruire avant production.

## I. Exigences de génération documentaire
CV, fiches de référence, exports d'offres : modèles modifiables par administrateur métier, sources de données identifiées, champs absents visibles comme absents et pas remplis par invention, prévisualisation et validation humaine avant usage officiel. DOCX et PDF uniquement pour les sorties demandées. Les attestations ne sont jamais générées artificiellement.
