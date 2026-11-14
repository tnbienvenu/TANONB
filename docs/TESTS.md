# Tests et état de validation

## Exécuter les tests

```bash
./gradlew :domain:test                 # 52 tests JVM (règles métier)
./gradlew :app:testDebugUnitTest       # 14 tests Robolectric / JVM
./gradlew :app:lintDebug               # analyse statique Android
./gradlew :app:connectedDebugAndroidTest   # tests instrumentés : appareil ou émulateur requis
```

## Couverture

| Fichier | Ce qui est vérifié |
|---|---|
| `domain/.../StatisticsTest` | moyennes (3 mesures, 2 dernières, repli signalé), écart-type, amplitude, évolution entre prises, pression pulsée, pouls absent non inventé ; distinction moyenne de séance / journalière / mesures individuelles ; exclusion des séances en cours ; matin/soir et suffisance ; tendance (minimums, pente exacte) ; semaines et mois ; complétude (réalisées, incomplètes, manquées, à venir) ; dépassement d'objectif ; valeurs atypiques |
| `domain/.../AlertEngineTest` | symptôme urgent → urgence même avec tension normale ; ≥ 180/110 → nouvelle mesure et contact ; diastolique seule ≥ 110 ; plausibilité ; écart > 10 mmHg ; objectif / seuil à domicile ; grade 2 ; hypotension et pouls avec symptômes ; séance normale sans alerte ; période < 3 jours non interprétée |
| `domain/.../SevenSegmentReaderTest` | lecteur 7 segments sur écrans synthétiques (dégradé, bruit, flou, cadre, libellés, 4 échelles) : ≥ 90 % de lectures exactes sur 40 écrans, **aucune valeur fausse présentée comme sûre**, les 10 chiffres décodés, image vide → aucune valeur |
| `domain/.../RealPhotoOcrTest` | évaluation sur photos réelles non versionnées : `CARDIO_OCR_SAMPLES=/dossier` (fichiers nommés `sys_dia_pouls.jpg`) |
| `domain/.../OcrParserTest` | disposition avec libellés, sans libellés (position), confusions 7 segments, rangée côte à côte, valeurs inversées signalées sans permutation, rien de lisible → aucune valeur inventée, pouls absent, valeur implausible non proposée, rejet des heures/dates, consensus (accord / désaccord) |
| `domain/.../ScheduleCalculatorTest` | calendrier par défaut, prochaines séances, même jour, changement d'heure (heure locale conservée), attribution matin/soir, seules les séances validées comptent, délai de grâce |
| `domain/.../BackupAndExportTest` | chiffrement/déchiffrement, mauvais mot de passe, altération du contenu et de l'en-tête, mot de passe trop court, sorties aléatoires, 600 000 itérations ; CSV (séances en cours exclues, pouls vide, décimales, échappement, injection de formules) |
| `domain/.../AnalysisAndAiTest` | constats et points à discuter ; 6 sections pour chaque question prédéfinie ; données vides ; contexte IA sans nom ni date de naissance, options de partage ; garde-fou anti-prescription ; protocole standardisé (1er jour écarté, statuts) |
| `app/.../DatabaseTest` | cycle de vie d'une séance, suppression en cascade, restauration complète (remplacement), suppression de toutes les données |
| `app/.../AiClientsTest` | format des requêtes Anthropic (en-têtes, modèle, effort) et OpenAI, analyse des réponses, refus, erreurs 401/429, clé invalide sans requête |
| `app/.../AppServicesTest` | programmation des alarmes (dates et heures exactes), rappels désactivés, garde-fou sans appel réseau, seuil d'Otsu, binarisation, verrouillage différé |
| `app/src/androidTest/.../EncryptedStorageTest` | **à exécuter sur appareil** : Keystore réel, fichier de base illisible en clair, échec avec une mauvaise clé |

## Résultats obtenus dans l'environnement de développement (14/11/2026)

- `:domain:test` : **59 tests, 0 échec** (+ 1 test sur photos réelles, ignoré sans dossier d'échantillons).
- `:app:testDebugUnitTest` : **14 tests, 0 échec**.
- `:app:lintDebug` : **0 erreur** (avertissements restants : versions plus récentes disponibles, suggestions de style).
- `assembleDebug` et `assembleRelease` (R8) : **réussis** ; APK signés vérifiés avec `apksigner`.

## Non vérifié à ce stade (à faire sur le Razr 50s)

L'environnement de compilation ne dispose ni d'émulateur (pas de virtualisation matérielle) ni d'appareil connecté. **Rien de ce qui suit n'a pu être exécuté** :

- tests instrumentés (`EncryptedStorageTest`) ;
- lancement réel de l'application, navigation, rendu sur l'écran principal et l'écran externe du Razr 50s ;
- caméra, qualité de l'OCR sur photos réelles de votre tensiomètre ;
- déclenchement réel des rappels (y compris après redémarrage, mode Ne pas déranger, optimisation batterie Motorola) ;
- verrouillage biométrique, génération et partage PDF (WhatsApp, e-mail), sauvegarde/restauration via le sélecteur de fichiers ;
- appels réels aux API Anthropic et OpenAI ;
- comportement de la version minifiée (R8) à l'exécution.

### Recette proposée sur l'appareil
1. Installer `app-arm64-v8a-release.apk`, accepter l'avertissement, autoriser notifications et alarmes exactes.
2. Séance complète : 3 photos de l'écran du tensiomètre ; noter pour chaque photo si SYS/DIA/PUL sont lus correctement.
3. Séance manuelle incomplète (2 mesures) ; vérifier le statut « Incomplète ».
4. Rappel de test (Plus → Rappels) ; « Reporter » 10 min ; « Ignorer ».
5. Programmer un rappel 3 min plus tard, redémarrer le téléphone, vérifier le déclenchement.
6. Saisir une mesure 185/112 : vérifier la recommandation de nouvelle mesure ; cocher « douleur dans la poitrine » : vérifier l'alerte d'urgence et le bouton d'appel.
7. Générer un PDF et le partager par e-mail ; exporter un CSV et l'ouvrir dans un tableur.
8. Créer une sauvegarde chiffrée, supprimer les données, restaurer.
9. Activer le verrouillage biométrique, quitter l'application plus d'une minute, revenir.
10. (Facultatif) Configurer une clé d'API, tester la connexion, poser une question.

## Lecture des écrans LCD (version 1.0.1)

Premier essai réel (Razr 50s, tensiomètre à écran LCD 7 segments, libellés en chinois) : ML Kit seul n'a rien lu.
Un lecteur 7 segments dédié a été ajouté (`SevenSegmentReader`). Sur la photo de l'essai : 125/85/78 lus exactement,
image entière comme zone recadrée. Sur 200 écrans synthétiques variés non utilisés pour la mise au point :
80 % de lectures exactes, 0 valeur fausse non signalée.
