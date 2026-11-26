# ScanDoc

Application Android native (Kotlin, Jetpack Compose) de **numérisation de documents**, sur le modèle d'Adobe Scan : mêmes parcours, mêmes options et une charte graphique proche (fond blanc, accent bleu, bandeau en dégradé, barre de navigation flottante avec bouton central). Elle a son propre nom et son propre logo. Tout fonctionne **hors ligne**, sans compte ni abonnement.

## Créer un document (bouton bleu central)

| Option | Fonctionnement |
|---|---|
| **Combiner des fichiers** | Choix de documents ScanDoc et/ou de PDF et images de l'appareil, ordre réglable, puis création d'un nouveau document |
| **Créer avec photos** | Écran « Ajouter à partir des photos » : grille de la galerie, sélection numérotée dans l'ordre, interrupteur **Recadrage automatique** (détection des bords et redressement de la perspective) |
| **Créer une numérisation** | Appareil photo intégré : contour du document détecté en direct, prise de plusieurs pages à la suite, flash, recadrage automatique |

Les fichiers partagés depuis une autre application (Partager → ScanDoc : images ou PDF) créent aussi un document.

## Options sur un document

| Écran | Options |
|---|---|
| Accueil (dernier document) | Partager · Enregistrer au format Word · Enregistrer au format JPEG · Autres |
| Cartes « Récents » | Partager · Modifier · Remplir et signer · Autres |
| Menu « Autres » | Afficher le PDF, Partager le PDF / JPEG / Word, Enregistrer le PDF (Téléchargements), Enregistrer en Word, Enregistrer en JPEG (galerie), Remplir et signer, Copier le texte, Imprimer, **Compresser le PDF**, **Protéger par un mot de passe**, Combiner avec d'autres fichiers, Renommer, Supprimer |
| Modifier (barre d'outils) | Ajouter (appareil photo, photos, fichiers), Recadrer (4 coins avec loupe), Pivoter, Filtres (Original, Couleur auto, Niveaux de gris, Noir et blanc, Lumineux ; une page ou toutes), **Nettoyer** (gomme, stylo, surligneur), **Redimensionner** (taille d'origine, A4, A3, A5, Lettre, Légal, carte de visite), Réorganiser, Signer, Texte, Supprimer la page |
| Remplir et signer | Signature manuscrite (mémorisée), texte libre, date, ✓, ✗ : déplacer au doigt, pincer pour agrandir, sur chaque page |
| Fichiers | Recherche, tri (date, nom, création, nombre de pages) |
| Préférences | Recadrage automatique, filtre par défaut, OCR, qualité du PDF, taille de page par défaut, bandeau d'accueil |

## Détails techniques

- **PDF** : écrits sans bibliothèque (`export/PdfWriter.kt`). Une image JPEG par page et une couche de texte invisible issue de l'OCR : le PDF est **consultable** (recherche, copier-coller). Taille de page au choix. Chiffrement standard PDF (RC4 128 bits) pour le mot de passe.
- **OCR** : ML Kit Text Recognition (alphabet latin), embarqué et hors ligne. Il sert aussi aux exports Word (`.docx` modifiable) et texte.
- **Recadrage automatique** : détection des bords sans OpenCV (`imaging/DocumentDetector.kt`). Seuillage d'Otsu, plus grande zone claire, enveloppe convexe et quadrilatère d'aire maximale. La perspective est ensuite corrigée.
- **Filtres** (`imaging/Filters.kt`) : le fond du papier est estimé par blocs puis divisé, ce qui supprime les ombres et blanchit le papier.
- **Non destructif** : la photo d'origine est conservée. Recadrage, rotation, filtre, retouches et signatures restent modifiables.
- **Stockage** : Room (base locale) et fichiers privés de l'application. Les exports vont dans `Téléchargements/ScanDoc` (PDF, Word) et `Images/ScanDoc` (JPEG).

## Installation

APK dans `dist/` :

- `ScanDoc-1.0.0-arm64-v8a.apk` : la grande majorité des téléphones ;
- `ScanDoc-1.0.0-universal.apk` : tout appareil, en cas de doute.

Sur le téléphone : ouvrez l'APK depuis **Fichiers**, puis autorisez l'installation depuis cette source. Android 10 ou plus récent est nécessaire.

## Compiler

JDK 17+ et Android SDK 36 :

```bash
cd ScanDoc
echo "sdk.dir=/chemin/vers/android-sdk" > local.properties
./gradlew :app:testDebugUnitTest   # tests (détection, filtres, PDF, chiffrement, Word)
./gradlew :app:assembleRelease     # APK (signé avec la clé de debug si keystore.properties est absent)
```

Pour une signature définitive, créez `keystore.properties` (non versionné) avec `storeFile`, `storePassword`, `keyAlias` et `keyPassword`.
