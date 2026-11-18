# CardioSuivi 1.1.0 — Rapport des modifications

Branche : `claude/cardiosuivi-refonte-ui-1.1.0`. Version : **1.1.0 (versionCode 4)**, mise à jour de la 1.0.1 / 1.0.2
(versionCode 2 / 3). Identifiant inchangé : `com.cardiosuivi.app`. Certificat de signature inchangé
(SHA-256 `c526072e0f951aafd9a6944262bc21c2746cfd3136e2d88b24f1150ae9a500b7`).

> **Référence esthétique iOS 27** : les captures et guides de l'application Santé d'iOS 27 n'étaient pas consultables
> depuis l'environnement de développement ; ils n'ont donc pas pu être vérifiés. La refonte suit les principes
> ergonomiques publics et stables des applications de santé (fond grisé, cartes blanches arrondies, grands chiffres,
> hiérarchie marquée). **Aucune ressource, icône ou image d'Apple n'est utilisée** (icônes Material, licence Apache 2.0).
> Détail du système de design et des écrans : [REFONTE_UI_1.1.md](REFONTE_UI_1.1.md).

## 1. Audit du code 1.0.1 (points touchés par la 1.1.0)

| Élément 1.0.1 | Constat | Traitement 1.1.0 |
|---|---|---|
| OCR (`app/ocr`, `domain/ocr`, `CameraCapture`) | CameraX, ML Kit, autorisation `CAMERA`, import d'image et EXIF ; 3 tests unitaires OCR, 2 scénarios de recette | **Supprimé** (code, écrans, bibliothèques, autorisation, tests). Aucune autre fonctionnalité n'en dépendait (vérifié par compilation et recherche de références). |
| Saisie | SYS et DIA obligatoires, pouls facultatif, SYS ≤ DIA accepté après confirmation | **SYS, DIA, PUL obligatoires**, entiers uniquement, SYS ≤ DIA **refusé** |
| Bras | choix Gauche / Droit / Non précisé à chaque séance | **Bras gauche enregistré automatiquement**, aucun choix |
| Moment | choix Matin / Soir / Autre, prérempli par le milieu entre les heures de rappel | **Déduit de l'heure de la première mesure**, plages configurables, « Autre » hors plage |
| Horodatage | heure de validation (ou de la photo / EXIF) ; pas de fuseau ; correction sans trace | **Heure du téléphone à la validation**, fuseau et décalage UTC enregistrés, **date de correction distincte** |
| Base Room | version 1 | **version 2**, migration non destructive |
| Analyse IA | réponses IA non conservées | **dernière interprétation IA conservée** (base chiffrée) et affichée avec sa date |

## 2. Saisie des mesures

- Formulaire « MESURE n SUR 3 » : trois champs **SYS — Pression systolique (mmHg)**, **DIA — Pression diastolique
  (mmHg)**, **PUL — Fréquence cardiaque (bpm)**, marqués « * », clavier numérique, 3 chiffres au plus ; toute frappe
  autre qu'un chiffre est ignorée.
- Refus (message sous le champ, **valeurs saisies conservées**) : champ vide, valeur non entière, 0, SYS ≤ DIA.
- Bornes de plausibilité **inchangées** (`PlausibilityBounds` : SYS 60–260, DIA 30–160, PUL 30–220) : comme en 1.0,
  une valeur hors bornes demande une **confirmation explicite** (aucun nouveau seuil introduit).
- Boutons « Valider et continuer », « Revenir à la mesure n − 1 » (correction de la mesure précédente puis retour),
  correction depuis l'intervalle et le récapitulatif, suppression d'une mesure avant clôture, confirmation avant abandon
  (avec avertissement si des valeurs sont saisies mais non validées).
- Récapitulatif : chaque mesure avec son **heure à la seconde**, valeur de séance selon la méthode configurée
  (moyenne des 3 ou des 2 dernières : règle existante inchangée), moyennes, amplitudes, écarts-types.

## 3. Horodatage automatique

| Règle | Mise en œuvre |
|---|---|
| Heure du téléphone à la validation | `Timestamp.now(clock)` au clic sur « Valider et continuer » |
| Un horodatage par mesure, secondes conservées | `measuredAt` (milliseconde) par mesure |
| Format français | « mardi 18 novembre 2026 à 07:05:12 » (`Fmt.dateTimeLong`) |
| Pas de saisie manuelle de date/heure | aucun champ date/heure dans le formulaire |
| Fuseau | `zoneId` (ex. Europe/Paris) et `utcOffsetSeconds` par mesure ; restitution dans le fuseau d'origine |
| Anciennes mesures | horodatages 1.0 conservés tels quels (fuseau NULL → affichage dans le fuseau courant, comme avant) |
| Correction | `measuredAt` jamais modifié ; `modifiedAt` = date de la correction (affichée « corrigée le … ») |

## 4. Moment de la journée

`PeriodWindows` : matin **05:00–12:00**, soir **17:00–minuit** par défaut, modifiables dans *Profil › Rappels ›
Plages matin et soir*. Ces valeurs sont des réglages d'organisation (non médicaux) choisies pour contenir les heures de
rappel par défaut (07:00, 19:00) et le délai de tolérance de 3 h des séances manquées ; l'écran signale toute
incohérence entre un rappel et sa plage. **Changement de règle explicite** : en 1.0, toute heure était classée matin ou
soir (milieu entre les deux rappels) ; en 1.1.0, une séance hors plage est « Autre » et ne valide pas un créneau
programmé. Les séances déjà enregistrées ne sont **pas** reclassées. Les statistiques matin/soir sont conservées.

## 5. Migration des données (1.0.1 → 1.1.0)

| Point | Vérification |
|---|---|
| Identifiant | `com.cardiosuivi.app` inchangé |
| Signature | même certificat (vérifié par `apksigner` sur les APK 1.0.1 et 1.1.0) |
| Schéma | Room 1 → 2 : `ALTER TABLE measurements ADD COLUMN modifiedAt / zoneId / utcOffsetSeconds`, `CREATE TABLE ai_analyses` |
| Migration destructive | **aucune** (`fallbackToDestructiveMigration` non utilisé) |
| Anciennes mesures | non modifiées : bras, période, source OCR, pouls absent et horodatages conservés |
| Paramètres, rappels, IA | DataStore inchangé ; nouvelles clés facultatives (plages matin/soir) |
| Sauvegardes | format 2 ; les sauvegardes 1.0.x (format 1) restent restaurables (champs ajoutés facultatifs) |

## 6. Instructions de mise à jour depuis la 1.0.1

1. *(Recommandé)* Dans la 1.0.1 : **Plus › Sauvegarde et restauration › Exporter**, avec un mot de passe ; conservez le
   fichier hors du téléphone.
2. Téléchargez `CardioSuivi-1.1.0-arm64-v8a.apk` (Razr 50s) et vérifiez son empreinte SHA-256 (`dist/SHA256SUMS.txt`).
3. Ouvrez l'APK et choisissez **Mettre à jour** (ne désinstallez pas la 1.0.1 : une désinstallation effacerait la base
   chiffrée). Android accepte la mise à jour car le certificat est le même.
4. Ouvrez CardioSuivi : la base est migrée automatiquement à la première ouverture ; vos séances, traitements, rappels
   et réglages d'IA sont conservés. Les rappels sont reprogrammés automatiquement.
5. Vérifiez *Profil › Rappels › Plages matin et soir* si vous mesurez en dehors de 05:00–12:00 / 17:00–minuit.
6. L'autorisation Caméra n'est plus demandée ; vous pouvez la retirer dans les réglages Android si elle apparaît encore.

En cas de message « Application non installée » : un APK signé avec une autre clé est déjà installé ; ne désinstallez
qu'après avoir exporté une sauvegarde, puis restaurez-la dans la 1.1.0.
