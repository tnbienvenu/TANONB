# Rapport de recette fonctionnelle — CardioSuivi 1.0.1 → 1.0.2

*Recette exécutée le 15/11/2026 sur émulateur Android. Rapport généré à partir des journaux d'exécution
(`recette/preuves/appareil/journal.tsv`, `recette/journal_hote.tsv`, `recette/journal_release.tsv`,
`recette/journal_biometrie.tsv`) par `recette/generer_rapport.py`.*

## 1. Verdict

| | |
|---|---|
| Scénarios élémentaires | **142** : **136 réussis**, **0 échoué**, **6 non exécutés** (impossibles dans cet environnement, voir § 7) |
| Anomalies de l'application trouvées | **9** (A01–A09) : 2 majeures, 6 modérées, 1 mineure — **toutes corrigées et vérifiées par un nouveau passage** |
| APK release signé | 1.0.2 (versionCode 3), même certificat que 1.0.1 ; parcours principaux rejoués sur l'APK release (RL00–RL10) |
| Statut global | **Validé sur émulateur Android 14 uniquement. L'application n'est PAS déclarée entièrement validée** : aucun test n'a pu être exécuté sur un Motorola Razr 50s physique (charnière, mode flex, appareil photo réel, performances réelles). |

## 2. Environnement réellement utilisé

| Élément | Valeur |
|---|---|
| Appareil | Émulateur Android officiel 37.2.13, image **Android 14 (API 34) `google_apis` x86_64** (AVD « razr34 ») |
| Accélération | **Aucune** (pas de KVM) : émulation logicielle, 10 à 50 × plus lente qu'un téléphone. Les durées observées ne sont pas représentatives |
| Écran | 1080×2640 à 420 dpi (Razr 50s déplié), exécuté en 720×1760 à 280 dpi : **même mise en page en dp (≈ 411 × 1006 dp)** |
| Variantes d'écran | rotation, 720×1280, 1600×2560, second écran simulé (écran externe) |
| Services Google | désactivés (CardioSuivi n'en dépend pas : OCR ML Kit embarqué) |
| Builds testés | debug x86_64 (tests instrumentés) puis **release x86_64 signé et minifié (R8)** |

## 3. Méthodes

1. **Tests d'interface instrumentés** (`app/src/androidTest/java/com/cardiosuivi/app/recette/`, Compose UI Test + UiAutomator) :
   l'application est réellement lancée ; les tests touchent les boutons, saisissent du texte, répondent aux fenêtres système
   (autorisations, partage, sélecteur de photo), lisent l'écran et la base, puis enregistrent captures et journal.
2. **Pilotage externe par adb** (`recette/*.py`) : APK release (écran protégé), redémarrage du téléphone, biométrie
   (code PIN + empreinte virtuelle `adb emu finger touch`), vérifications complémentaires.
3. **Serveur IA simulé** (MockWebServer sur l'appareil) : **aucune requête vers Anthropic ou OpenAI, aucune donnée médicale transmise**.
4. **Données fictives** identifiées comme telles (« RECETTE-FICTIF », « MEDICAMENT-FICTIF »…). Résultats attendus calculés
   indépendamment à la main (ex. jeu J-6…J-1 : moyenne 7 j attendue 133/84 ; méthode « deux dernières » 131/83 ; écart matin−soir −5,0/−2,0).
5. **Jeu OCR de référence** (images non versionnées, pour ne pas publier votre photo) : 36 variantes de l'unique photo réelle
   disponible (valeurs vérifiées visuellement) — 4 cadrages × 9 altérations (rotation ±4°, luminosité, flou, JPEG 25 %,
   demi-résolution, perspective) — et 30 écrans 7 segments synthétiques clairement identifiés.

Chaque formulaire a suivi la séquence demandée : valeurs normales, champs vides, valeurs incorrectes et limites, enregistrement,
fermeture/réouverture, vérification de la conservation, modification (profil R02, traitements R11, observations R11e,
rappels R15, paramètres R08b/R13, IA R12, sauvegarde R14).

## 4. Couverture des points demandés

| Demande | Scénarios | Résultat |
|---|---|---|
| Écrans, menus, formulaires, boutons, paramètres | R01–R21, RL | réussis |
| Séances de 3 mesures, calculs | R03 (moyennes, intervalle, alerte de fin), R04 (validation), R06 (incomplète), R08 (méthodes) | réussis |
| Interruptions de séance | R05 (quitter, relancer, reprendre), R21b (rotation en cours de séance) | réussis |
| Erreurs de saisie | R02a–f, R04a–h, R11a–b, R14e, R12a | réussis (A03 corrigée) |
| Rappels, reports, séance manquée | R15, R16 (notification réelle, Reporter 10 min, Ignorer, Commencer), R17 | réussis (A05, A09 corrigées) |
| Redémarrage du téléphone | RB01 : alarmes recréées sans ouvrir l'application | réussi |
| OCR | R18 (66 images), R19 (import + validation + enregistrement) | voir § 5 |
| Graphiques | R09 (8 vues, valeur touchée, effectifs, filtres) | réussis |
| Rapports PDF / CSV / partage | R10 (3 pages PDF contrôlées, CSV, feuille de partage Android) | réussis |
| Sauvegardes, sauvegardes corrompues | R14a–f (6 fichiers corrompus refusés sans perte), RL | réussis |
| Biométrie | R13f (non configurée), BIO01–BIO07 | réussis |
| Intégration IA (serveur simulé) | R12a–l (consentement, refus, garde-fou prescription, 401, refus du modèle, hors connexion) | réussis |
| Autorisations refusées | R01c, R20a–e (notifications, alarmes exactes, caméra) | réussis |
| Absence d'Internet | R12j–k | réussis |
| Orientation, tailles d'écran, pliable | R21a–d, R21-720x1280, R21-1600x2560 | réussis (A04 corrigée) ; charnière/flex **non exécutés** |
| APK release signé | RL00–RL10 (écran protégé, séance, JSON, PDF, persistance) | réussis |

## 5. OCR : taux mesurés séparément (après correction, exécution sur l'émulateur)

Lecture d'un champ : **correcte**, **erreur** (valeur fausse proposée) ou **rejet** (champ laissé vide).

| Jeu | Images | Lectures exactes (3 champs) | Images avec ≥ 1 erreur | Images incomplètes sans erreur | Rejet total |
|---|---|---|---|---|---|
| Réel (36 variantes d'une photo) | 36 | **22 (61 %)** | 6 (17 %) | 8 (22 %) | 0 |
| Synthétique | 30 | **27 (90 %)** | 3 (10 %) | 0 | 0 |

| Par champ | SYS correct / erreur / rejet | DIA correct / erreur / rejet | PUL correct / erreur / rejet |
|---|---|---|---|
| Réel (36) | 32 / 1 / 3 | 34 / 2 / 0 | 25 / 3 / 8 |
| Synthétique (30) | 27 / 3 / 0 | 28 / 1 / 1 | 29 / 0 / 1 |

Sécurité de l'affichage : **sur les 9 images comportant une erreur, toutes avaient au moins deux champs marqués « à vérifier »**
(aucune erreur présentée comme sûre). Sur le jeu synthétique, 18 des 27 lectures exactes sont proposées sans alerte
(indicateur désormais discriminant, A06). Sur la photo réelle, toutes les lectures restent marquées « à vérifier »
(étiquettes en chinois non reconnues : affectation par position). La validation manuelle reste obligatoire dans tous les cas.
Avant correction (A06/A07) : 48 lectures exactes sur 66 et les 66 entièrement marquées « à vérifier » ; après : 49/66.
Durée médiane d'analyse sur l'émulateur sans accélération : 25 à 30 s (non représentative d'un téléphone).
**Limite** : un seul tensiomètre réel ; les taux sur photos réelles ne sont pas généralisables (NE04).

## 6. Anomalies de l'application

| Id | Gravité | Anomalie | Correction | Vérification |
|---|---|---|---|---|
{ANOMALIES}

### Incidents d'outillage (pas des anomalies de l'application)

| Scénario | Constat | Analyse | Traitement |
|---|---|---|---|
| R16e | « Commencer » depuis la notification : écran non détecté par le test | Écran bien affiché (capture R16_commencer.png, hiérarchie complète) ; UiAutomator interrogeait la fenêtre active du volet de notifications | Vérifié par pilotage adb sur une vraie notification : séance du matin ouverte en 37–57 s (chronologie en preuve) |
| BIO04–06 | Verrouillage « absent », empreinte inconnue « déverrouillée » | L'invite système « Déverrouiller CardioSuivi » recouvre l'écran de verrouillage ; le script cherchait le texte masqué | Rejoué avec horodatage et journaux du service biométrique : verrouillage, refus de l'empreinte 2, acceptation de l'empreinte 1 |
| R01c | Fenêtre d'autorisation non affichée | Autorisation accordée d'avance par le script de lancement | Révocation avant le test, réussi |
| R16b (1er passage) | Notification non reçue en 6 min | Alarme approchée (fenêtre de 10 min) : c'est l'anomalie A05 | Alerte ajoutée ; avec l'autorisation « Alarmes et rappels », notification reçue |
| R08a, R10c, R17 | Échecs ponctuels | Activité résiduelle d'un test précédent, feuille de partage en anglais, ANR système de l'émulateur | Réinitialisation fiabilisée, relance : réussis |

## 7. Non exécuté (limites de l'environnement)

| Id | Point | Raison |
|---|---|---|
| NE01 | Motorola Razr 50s physique | aucun téléphone connecté à l'environnement |
| NE02 | Charnière, mode flex, écran externe réel | pas de capteur de charnière ni de posture dans l'émulateur (seuls rotation, tailles et écran externe simulé) |
| NE03 | Prise de vue en direct d'un vrai tensiomètre | caméra de l'émulateur = scène virtuelle ; reconnaissance testée sur photos importées |
| NE04 | Photos réelles de plusieurs tensiomètres | une seule photo réelle disponible ; banques d'images bloquées par le réseau |
| NE05 | Fournisseurs d'IA réels | exclu volontairement (aucune donnée médicale transmise sans autorisation) |
| NE06 | Performances et autonomie réelles | émulateur sans accélération |

**Recette complémentaire recommandée sur le Razr 50s** : installer l'APK 1.0.2, faire une séance avec photo du tensiomètre
(écran externe et intérieur), plier/déplier pendant une séance, vérifier un rappel à l'heure exacte puis après redémarrage,
activer le verrouillage biométrique.

## 8. Preuves

- Captures et fichiers de l'appareil : `recette/preuves/appareil/` (les captures R19 montrant votre photo réelle ne sont pas publiées) ;
- redémarrage : `recette/preuves/RB_alarmes_apres_redemarrage.txt` ; biométrie : `recette/preuves/BIO_journal_biometrie.txt` ;
  « Commencer » : `recette/preuves/R16e_commencer_chronologie.png` ;
- tests : `app/src/androidTest/java/com/cardiosuivi/app/recette/`, scripts : `recette/*.py`, `recette/lancer_recette.sh`.

## 9. Détail des scénarios

Colonne « Preuve » : fichier dans `recette/preuves/appareil/` sauf mention contraire.

