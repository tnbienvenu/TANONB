# Recette fonctionnelle CardioSuivi — outillage

## Environnement d'exécution

| Élément | Valeur |
|---|---|
| Appareil | Émulateur Android officiel (Android Emulator 37.2.13), image **Android 14 (API 34) `google_apis` x86_64** |
| Accélération | **Aucune** (pas de KVM dans l'environnement) : émulation logicielle QEMU/TCG, environ 10 à 50 fois plus lente qu'un téléphone |
| Écran | 1080×2640 à 420 dpi (comme le Razr 50s déplié), ramené par `wm size 720x1760` / `wm density 280` pendant les tests : **même mise en page en dp (≈ 411×1006 dp)**, moins de pixels à calculer |
| Services Google | désactivés (CardioSuivi n'en dépend pas : OCR ML Kit embarqué) pour alléger l'émulateur |
| Animations | désactivées (paramètres développeur) |

## Méthodes

1. **Tests d'interface instrumentés** (`app/src/androidTest/.../recette/`) : Jetpack Compose UI Test et UiAutomator.
   L'application est réellement lancée sur l'émulateur. Les tests touchent les boutons, saisissent du texte, répondent
   aux fenêtres système, lisent l'écran et la base, puis enregistrent captures et journal (`journal.tsv`).
2. **Pilotage externe par adb/uiautomator** (`recette_release.py`, `recette_redemarrage.py`) pour l'**APK release signé**
   (écran protégé : captures noires, arbre d'accessibilité lisible) et le redémarrage du téléphone.
3. **Serveur IA simulé** (MockWebServer sur l'appareil) : aucune requête vers un fournisseur réel.
4. **Jeu OCR de référence** (non versionné) :
   - 36 images issues de l'unique photographie réelle disponible (votre photo, valeurs 125/85/78 vérifiées visuellement),
     sous 4 cadrages × 9 variantes (rotation, luminosité, flou, compression, demi-résolution, perspective) ;
   - 30 écrans synthétiques identifiés comme tels.

Données : uniquement **fictives**, identifiées « RECETTE-FICTIF », « MEDICAMENT-FICTIF », etc.

## Relancer

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
/tmp/recette_run.sh R01_DemarrageEtProfilTest R03_SeancesTest ...   # voir le script dans le rapport
python3 recette/recette_release.py
python3 recette/recette_redemarrage.py
```
