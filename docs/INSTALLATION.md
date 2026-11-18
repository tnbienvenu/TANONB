# Installation

## 1. Installer l'APK sur le Motorola Razr 50s (ou un autre téléphone Android 10+)

Fichiers fournis (signés, version 1.0.0) :

| Fichier | Pour qui |
|---|---|
| `app-arm64-v8a-release.apk` (≈ 21 Mo) | **Motorola Razr 50s** et la grande majorité des téléphones récents |
| `app-armeabi-v7a-release.apk` | anciens téléphones 32 bits |
| `app-universal-release.apk` (≈ 57 Mo) | tout appareil, en cas de doute |

1. Copiez l'APK sur le téléphone (câble USB, Google Drive, e-mail…).
2. Ouvrez-le depuis l'application **Fichiers**.
3. Android demande d'autoriser l'installation depuis cette source : **Paramètres → Autoriser depuis cette source**, puis revenez et appuyez sur **Installer**.
4. Play Protect peut afficher un avertissement pour une application non publiée sur le Play Store : choisissez « Installer quand même » si vous avez obtenu l'APK de cette compilation.

## 2. Premier lancement

1. Lisez et acceptez l'avertissement (l'application ne remplace pas un avis médical).
2. Autorisez les **notifications** (indispensable pour les rappels).
3. **Plus → Autorisations** : vérifiez
   - **Alarmes et rappels** : à activer pour des rappels à l'heure exacte (sinon décalage possible de quelques minutes) ;
   - **Optimisation de la batterie** : sur certains téléphones, exclure CardioSuivi améliore la fiabilité des rappels.
4. **Plus → Profil personnel** : renseignez au moins l'**objectif tensionnel** fixé avec votre médecin.
5. **Plus → Traitements** : vos traitements (recopiés depuis l'ordonnance).
6. **Plus → Rappels** : jours et horaires (initialement mercredi, jeudi, vendredi à 07:00 et 19:00).
7. Facultatif : **Paramètres → Verrouillage biométrique**.

### Particularités du Razr 50s (pliable à clapet)
- L'interface est en colonne unique, largeur limitée et défilante : elle s'adapte à l'écran principal déplié comme à l'écran externe.
- La saisie des trois valeurs (SYS, DIA, PUL) utilise le clavier numérique : le téléphone déplié (écran principal) est plus confortable, l'écran externe reste utilisable.

## 3. Assistant IA (facultatif)

1. Créez une clé d'API chez Anthropic (console.anthropic.com) ou OpenAI (platform.openai.com), idéalement dédiée et plafonnée en dépenses.
2. **Plus → Assistant IA → Configurer l'IA** : choisissez le fournisseur, collez la clé (elle est chiffrée par le Keystore), testez la connexion (aucune donnée de santé n'est envoyée lors du test).
3. Lors de la première question, un **consentement explicite** est demandé. Le bouton « Données transmises » montre exactement ce qui part.

Modèles par défaut : `claude-opus-5-5` (Anthropic) et `gpt-5` (OpenAI) — modifiables dans l'écran de configuration.

## 4. Sauvegarde et changement de téléphone

La sauvegarde Android dans le cloud est **désactivée** (données de santé). Utilisez **Plus → Sauvegarde et restauration** : un fichier `.csbk` chiffré par votre mot de passe. Sans ce mot de passe, la sauvegarde est irrécupérable.

## 5. Compiler soi-même

Prérequis : JDK 17 ou plus récent, Android SDK (plateforme 36, build-tools 36), accès à `google()` et Maven Central.

```bash
echo "sdk.dir=/chemin/vers/android-sdk" > local.properties
./gradlew :domain:test :app:testDebugUnitTest   # tests JVM
./gradlew assembleDebug                         # APK de débogage (signé avec la clé de debug locale)
```

APK release signé : créez `keystore.properties` à la racine (non versionné) :

```properties
storeFile=/chemin/vers/cardiosuivi-release.jks
storePassword=...
keyAlias=cardiosuivi
keyPassword=...
```

puis `./gradlew assembleRelease` → `app/build/outputs/apk/release/`.

> **Important** : une mise à jour ne s'installe par-dessus l'application existante que si elle est signée avec **la même clé**. Conservez le fichier `.jks` et ses mots de passe. Avec une autre clé, il faudra désinstaller (exportez d'abord une sauvegarde chiffrée, puis restaurez-la).

Versions retenues : AGP 8.13.2, Gradle 8.14.3, Kotlin 2.3.21, Compose BOM 2026.06.01 (le BOM 2026.08+ exige AGP 9.1 et compileSdk 37), Room 2.8.5, SQLCipher 4.20.0, CameraX 1.6.2, ML Kit Text Recognition 16.0.1, WorkManager 2.11.2, OkHttp 4.12.0.

## 6. Publication sur le Google Play Store (ultérieure)

Éléments à prévoir : compte développeur, bundle AAB (`./gradlew bundleRelease`), politique de confidentialité publiée, formulaire « Sécurité des données », déclaration de l'autorisation `SCHEDULE_EXACT_ALARM`, déclaration des applications de santé, et analyse réglementaire (voir [SECURITE_CONFIDENTIALITE.md](SECURITE_CONFIDENTIALITE.md)).
