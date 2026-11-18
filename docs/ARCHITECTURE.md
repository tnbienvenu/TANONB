# Architecture technique

## Vue d'ensemble

```
┌──────────────────────────── :app (Android) ────────────────────────────┐
│ ui/            Compose + Material 3, MVVM (AppViewModel, SessionFlowVM) │
│ ui/session/    séance guidée : saisie SYS/DIA/PUL, intervalle, résumé    │
│ ui/charts/     graphiques Canvas interactifs                            │
│ data/          Room (SQLCipher), DataStore, dépôts, mappers             │
│ security/      Keystore, clé de base, coffre des secrets, biométrie     │
│ reminders/     AlarmManager, notifications, receivers, WorkManager      │
│ ai/            clients Anthropic / OpenAI (OkHttp), AiService           │
│ report/        PDF (PdfDocument), CSV, partage (FileProvider)           │
│ backup/        sauvegarde chiffrée (SAF)                                │
└───────────────────────────────┬────────────────────────────────────────┘
                                │ dépend de
┌───────────────────────────── :domain (Kotlin pur, JVM) ─────────────────┐
│ model/      modèles métier            stats/     séance, période       │
│ schedule/   créneaux, séances manquées alerts/   moteur d'alertes       │
│ reference/  références et seuils      analysis/  analyse, HBPM, local  │
│ entry/      saisie, horodatage        ai/        prompt, garde-fou      │
│ export/     CSV                       backup/    format + crypto       │
└─────────────────────────────────────────────────────────────────────────┘
```

Le module **`:domain` ne dépend d'aucune API Android** : toutes les règles médicales, statistiques et de sécurité y sont testées sur JVM, de façon déterministe et reproductible.

## Décisions techniques

| Sujet | Options | Choix et justification |
|---|---|---|
| Version d'AGP | AGP 9.x (Gradle 9, Kotlin intégré) / AGP 8.13 | **AGP 8.13.2 + Gradle 8.14.3** : modèle Kotlin éprouvé, compatible compileSdk 36. Conséquence : Compose BOM limité à 2026.06.01. |
| Injection de dépendances | Hilt / Koin / manuelle | **Conteneur manuel** (`AppContainer`) : graphe petit et explicite, pas de génération de code supplémentaire. |
| Chiffrement de la base | SQLCipher / chiffrement champ par champ | **SQLCipher 4.20** via `SupportOpenHelperFactory` : chiffrement complet du fichier (AES-256), compatible Room. Phrase secrète aléatoire de 32 octets, chiffrée par une clé AES-GCM non exportable de l'**Android Keystore** (`noBackupFilesDir/db.key`). |
| Secrets (clés d'API) | security-crypto (déprécié) / Keystore direct | **Keystore direct** (AES-256-GCM) dans des SharedPreferences dédiées. |
| Graphiques | Vico, MPAndroidChart / Canvas | **Canvas Compose** : contrôle total de la représentation des données manquantes (courbe interrompue après 8 jours sans donnée, aucun point interpolé), valeurs exactes au toucher, sans dépendance. |
| PDF | iText (licence AGPL) / PdfDocument | **`android.graphics.pdf.PdfDocument`** : natif, sans licence restrictive. |
| HTTP | Retrofit / Ktor / OkHttp | **OkHttp + kotlinx.serialization** : deux appels POST seulement ; Retrofit (bâti sur OkHttp) n'apporterait que de la configuration. |
| Saisie (1.1.0) | OCR par photo (1.0) / saisie manuelle | **Saisie manuelle uniquement** : l'OCR (CameraX, ML Kit, autorisation caméra) est supprimé en 1.1.0. Les mesures OCR de la 1.0 restent affichées avec leur origine. |
| Moment matin/soir (1.1.0) | choix manuel (1.0) / déduction | **Déduit de l'heure locale de la 1re mesure** et de plages configurables (`PeriodWindows`, par défaut 05:00–12:00 et 17:00–minuit) ; hors plage : « Autre », sans attribution artificielle. |
| Rappels | WorkManager seul / AlarmManager | **AlarmManager** pour l'heure (exacte si « Alarmes et rappels » autorisé, sinon fenêtre de 10 min), **WorkManager** pour la vérification des séances manquées et une reprogrammation de sécurité toutes les 12 h. |

## Données

### Base Room (`cardiosuivi.db`, chiffrée)

| Table | Contenu |
|---|---|
| `sessions` | début, fin, date locale, moment, statut (`IN_PROGRESS`, `COMPLETED`, `INCOMPLETE`), protocole, mesures prévues, bras, symptômes, notes |
| `measurements` | rang 1..n, SYS, DIA, pouls (obligatoire à la saisie depuis 1.1.0, nullable pour l'historique), **horodatage automatique** (ms), source (`MANUAL` ; `OCR`, `OCR_CORRECTED` pour l'historique 1.0), confiance OCR (historique), **v2 :** `modifiedAt` (date de correction), `zoneId`, `utcOffsetSeconds` — suppression en cascade |
| `ai_analyses` (v2) | interprétations reçues d'un fournisseur d'IA : date, fournisseur, modèle, question, réponse |
| `profile` | ligne unique : nom/pseudonyme, naissance, sexe, taille, poids, objectif, antécédents, observations |
| `treatments`, `observations` | traitements et journal |
| `reminder_events` | journal reporté/ignoré/commencé/manqué |

Schéma exporté : `app/schemas/` (version 1 = 1.0.x, version 2 = 1.1.0). Migration `MIGRATION_1_2` non destructive : 3 colonnes ajoutées (NULL pour l'existant) et une table ; aucune donnée existante n'est modifiée. Les séances `IN_PROGRESS` ne sont **jamais** incluses dans les statistiques, rapports, CSV ou sauvegardes.

### Paramètres (DataStore, non sensibles)
Rappels, plages matin/soir, intervalle et nombre de mesures, méthode d'agrégation, sécurité, fournisseur d'IA, consentements horodatés, options de partage, protocole standardisé.

## Calculs (module `:domain/stats`)

- **Valeur de séance** selon la méthode configurée (`ALL_MEASUREMENTS` par défaut, ou `LAST_TWO`) ; si `LAST_TWO` n'est pas applicable (1 mesure), repli explicite et signalé.
- **Moyenne de période** = moyenne des valeurs de séance ; **moyenne journalière** = moyenne des séances d'un jour ; la **moyenne des mesures individuelles** est calculée séparément. Ces trois notions ne sont jamais confondues dans l'interface et les rapports.
- Écart-type d'échantillon (n − 1), coefficient de variation, ARV (variabilité réelle moyenne), régression linéaire (pente en mmHg/semaine, R²).
- Seuils méthodologiques propres à l'application (et non médicaux) : tendance si ≥ 6 séances sur ≥ 7 jours ; comparaison exploitable si ≥ 3 séances par groupe ; détection statistique de valeurs atypiques si ≥ 10 mesures (|x − médiane| > 3 × 1,4826 × MAD).
- **Aucune extrapolation** : les jours sans mesure restent vides ; la complétude est calculée par rapport aux créneaux programmés.

## Parcours de séance

`PREPARATION → REST (5 min, facultatif) → [ENTRY → INTERVAL] × n → SUMMARY → SAVED` (correction possible depuis la saisie suivante, l'intervalle ou le résumé ; l'horodatage d'origine n'est jamais modifié)

Chaque mesure validée est écrite immédiatement (séance « en cours ») : une interruption ne perd rien, la séance peut être reprise depuis l'accueil. La séance n'est « terminée » qu'après validation du résumé. Les alertes sont évaluées **à chaque mesure** et **dès la sélection d'un symptôme**, sans attendre l'enregistrement.

## Architecture prévue pour une consultation depuis iPhone (non développée)

Le module `:domain` et le format `BackupPayload` (JSON versionné) sont indépendants d'Android. Une future consultation sécurisée depuis Safari pourrait reposer sur : export chiffré de bout en bout (même format, clé dérivée côté client), service web statique déchiffrant dans le navigateur (WebCrypto, AES-GCM/PBKDF2 compatibles), sans que le serveur n'ait accès aux données. Aucune synchronisation cloud n'existe en V1.

## Limites connues

- Textes de l'interface en français, directement dans le code Compose (pas encore externalisés dans `strings.xml`).
- Les APK fournis sont signés avec une clé générée pour cette livraison ; voir INSTALLATION.md.
