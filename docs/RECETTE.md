# Rapport de recette fonctionnelle — CardioSuivi 1.0.1 → 1.0.2

*Recette exécutée le 15/11/2026 sur émulateur Android. Rapport généré à partir des journaux d'exécution
(`recette/preuves/appareil/journal.tsv`, `recette/journal_hote.tsv`, `recette/journal_release.tsv`,
`recette/journal_biometrie.tsv`) par `recette/generer_rapport.py`.*

## 1. Verdict

| | |
|---|---|
| Scénarios élémentaires | **142** : **136 réussis**, **0 échoué**, **6 non exécutés** (impossibles dans cet environnement, voir § 7) |
| Anomalies de l'application trouvées | **9** (A01–A09) : 2 majeures, 6 modérées, 1 mineure — **toutes corrigées et vérifiées par un nouveau passage** |
| APK release signé | **1.0.2** (versionCode 3), même certificat que 1.0.1 (SHA-256 c526072e…a500b7) ; parcours principaux rejoués sur l'APK 1.0.2 x86_64 construit à partir du même code que l'APK arm64 livré (RL00–RL10 réussis ; déjà réussis sur la construction précédente : `recette/journal_release_1.0.1.tsv`) |
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
| APK release signé 1.0.2 | RL00–RL10 (écran protégé, séance, JSON, PDF, persistance) | réussis |

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
| A01 | majeure | Ouverture de la base chiffrée sur le fil principal — Au premier lancement sur l'émulateur, fenêtre « ne répond pas » ; analyse : la base SQLCipher (dérivation de clé) était ouverte pendant la composition de l'interface, sur le fil principal. Sur un téléphone lent, risque de démarrage figé ou d'ANR. | Ouverture en arrière-plan (produceState + Dispatchers.IO) avec écran de chargement | corrigée et vérifiée (R01, R01b, RL01) |
| A02 | majeure | Références vers une base fermée après « Supprimer toutes les données » — Revue de code puis scénario R14f : les dépôts étaient mémorisés (lazy) sur l'ancienne base ; l'activité était seulement recréée (ViewModel conservé). | Dépôts recréés sur la base courante ; redémarrage de l'interface sur une tâche neuve après suppression | corrigée et vérifiée (R14f) |
| A03 | modérée | Heure de prise d'un traitement invalide perdue silencieusement — R11b : « 25:99 » enregistré sans heure et sans message. | Enregistrement bloqué tant que l'heure ou la date est invalide, message d'aide | corrigée et vérifiée (R11b, R11b2) |
| A04 | modérée | Saisie non enregistrée perdue à la rotation ou au pliage — R21a : nom saisi dans le profil perdu après rotation (même mécanisme lors du pliage/dépliage du Razr). | Formulaires en rememberSaveable (profil, traitement en édition, observation, notes, numéro d'urgence, modèles IA) | corrigée et vérifiée (R21a, R21b, R21c) |
| A05 | modérée | Rappels potentiellement retardés sans information (Android 14) — R16 : « Alarmes et rappels » non accordé par défaut ; repli sur une alarme approchée (jusqu'à 10 min de retard) sans que l'utilisateur en soit informé hors de l'écran de diagnostic. | Alerte sur l'accueil et l'écran Rappels avec bouton vers le réglage système | corrigée et vérifiée (R16p, R16a, R16b) |
| A06 | modérée | Indicateur « à vérifier » de l'OCR non discriminant — R18 : les 66 lectures, y compris les 48 exactes, avaient leurs 3 champs marqués « à vérifier ». | Confiance fondée sur le nombre de lectures concordantes, le décodage exact, la complétude et la cohérence de taille SYS/DIA | corrigée et vérifiée (R18-réel, R18-synthétique) |
| A07 | modérée | Lecture hors limites dans le lecteur 7 segments — Évaluation du jeu de référence : exception quand des chiffres touchent le bord inférieur ; rattrapée, le lecteur abandonnait silencieusement. | Contrôle des bornes ; test de régression | corrigée et vérifiée (R18-réel) |
| A08 | mineure | Choix du fournisseur d'IA limité au petit bouton radio — Préparation de R12 : seule la pastille était cliquable (cible tactile réduite, accessibilité). | Ligne entière sélectionnable (rôle RadioButton) | corrigée et vérifiée (R12c) |
| A09 | modérée | Rappels non reprogrammés après « Supprimer toutes les données » — R15b : après une suppression des données, aucune alarme en attente (dumpsys alarm : uniquement des annulations) jusqu'au prochain démarrage du processus ; l'utilisateur pouvait rester sans rappel. | Reprogrammation des rappels (valeurs initiales) à la fin de la suppression des données | corrigée et vérifiée (R15b) |

### Incidents d'outillage (pas des anomalies de l'application)

| Scénario | Constat | Analyse | Traitement |
|---|---|---|---|
| R16e | « Commencer » depuis la notification : écran non détecté par le test | Écran bien affiché (capture R16_commencer.png, hiérarchie complète) ; UiAutomator interrogeait la fenêtre active du volet de notifications | Vérifié par pilotage adb sur une vraie notification : séance du matin ouverte en 37–57 s (chronologie en preuve) |
| BIO04–06 | Verrouillage « absent », empreinte inconnue « déverrouillée » | L'invite système « Déverrouiller CardioSuivi » recouvre l'écran de verrouillage ; le script cherchait le texte masqué | Rejoué avec horodatage et journaux du service biométrique : verrouillage, refus de l'empreinte 2, acceptation de l'empreinte 1 |
| R01c | Fenêtre d'autorisation non affichée | Autorisation accordée d'avance par le script de lancement | Révocation avant le test, réussi |
| R16b (1er passage) | Notification non reçue en 6 min | Alarme approchée (fenêtre de 10 min) : c'est l'anomalie A05 | Alerte ajoutée ; avec l'autorisation « Alarmes et rappels », notification reçue |
| RL (1.0.2, 1er passage) | Application arrêtée au démarrage | Surcharge de l'émulateur juste après son redémarrage (charge 21, ANR simultanés du lanceur, du stockage et d'autres processus système) | Relancé une fois la charge retombée : réussi |
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

Scénarios élémentaires : **142** — réussis **136**, échoués **0**, non exécutés **6** (dont **11** échoués lors d'une exécution antérieure puis réussis après correction ou reprise ; 203 exécutions au total).

| Id | Fonctionnalité | Données saisies | Résultat attendu | Résultat observé (dernière exécution) | Statut | Preuve | Gravité / correction |
|---|---|---|---|---|---|---|---|
| R01c | Autorisation des notifications (Android 13+) | premier accès au tableau de bord | fenêtre système de demande affichée | affichée puis acceptée — *auparavant échoué (2×) : non affichée* | ✅ réussi | ECHEC_attente_NOUVELLE SÉANCE.png (run 1) | — |
| R02a | Profil – champs vides | aucun champ | enregistré, objectif null | objectif=null, nom='' | ✅ réussi | — | — |
| R02b | Profil – date invalide | 31/02/1960 | message « Date de naissance invalide. » | message affiché | ✅ réussi | R02b_date_invalide.png | — |
| R02c | Profil – objectif incomplet | 135 / vide | refus : deux valeurs requises | refusé | ✅ réussi | — | — |
| R02d | Profil – objectif incohérent | 135 / 140 (SYS ≤ DIA) | refus « Objectif incohérent » | refusé | ✅ réussi | — | — |
| R02e | Profil – limite haute | 999 / 85 | refus (hors 90–200) | refusé | ✅ réussi | — | — |
| R02f | Profil – caractères non numériques | « 13a5 » | lettres filtrées → 135 | 135 | ✅ réussi | — | — |
| R02g | Profil – valeurs normales | RECETTE-FICTIF, 15/06/1958, H, 176 cm, 78,5 kg, objectif 135/85 | toutes valeurs en base | Profile(displayName=RECETTE-FICTIF, birthDate=1958-06-15, sex=MALE, heightCm=176, weightKg=78.5, target=BpTarget(systolic=135, diastolic=85), medicalHistory=Antécédent fictif de test, observations=Observation fictive) | ✅ réussi | R02g_profil_rempli.png | — |
| R02h | Profil – persistance écran | fermer/rouvrir l'écran | valeurs réaffichées | réaffichées | ✅ réussi | — | — |
| R02i | Profil – persistance application | relance de l'activité | valeurs réaffichées | réaffichées | ✅ réussi | R02i_profil_apres_relance.png | — |
| R11a | Traitement – nom vide | aucun nom | bouton Enregistrer inactif | inactif | ✅ réussi | — | — |
| R11b | Traitement – heure invalide | « 25:99 » | enregistrement bloqué tant que l'heure est invalide, message d'aide affiché | bouton inactif, message « Heure invalide… » — *auparavant échoué (1×) : enregistré SANS heure, sans message (donnée perdue silencieusement)* | ✅ réussi | R11_traitement_heure_invalide.png | A03 (modérée) : Enregistrement bloqué tant que l'heure ou la date est invalide, message d'aide |
| R09a | Graphiques – valeur exacte | toucher le centre du graphique | valeur exacte et date affichées | affichée | ✅ réussi | R09_graphique_valeur_touchee.png | — |
| R09b | Graphiques – effectifs et manquants | période 30 j | 4 séances, 12 mesures, 4 jours sur 30, 26 jours sans mesure | conforme | ✅ réussi | R09_graphique_tension.png | — |
| R09c | Graphiques – matin/soir | jeu fictif | écart soir − matin −5,0 / −2,0 ; effectifs insuffisants signalés | conforme | ✅ réussi | R09_graphique_Matin__soir.png | — |
| R09d | Graphiques – filtre période | 7 jours | « 4 séance(s) … sur 7 » | filtré | ✅ réussi | — | — |
| R09e | Analyse – moyenne 7 j | jeu fictif | constat « Moyenne 133/84 mmHg … repère élevée » | présent | ✅ réussi | R09_analyse.png | — |
| R09f | Assistant local | question prédéfinie « moyenne 7 jours » | réponse en 6 sections, 133/84, sans connexion | 6 sections=true, 133/84=true | ✅ réussi | R09_assistant_local.png | — |
| R09g | Assistant – IA non configurée | question libre | bouton « Demander à l'IA » inactif sans fournisseur | aucun fournisseur | ✅ réussi | — | — |
| R10a | Rapport PDF | période 7 jours, jeu fictif | fichier PDF valide de plusieurs pages | fichier=CardioSuivi_rapport_2026-11-09_2026-11-15.pdf, 113691 octets, 3 page(s) | ✅ réussi | R10_pdf_page1.png … page4 | — |
| R10b | Export CSV | période 7 jours | en-tête + 12 lignes ; ligne 150/96 (PP 54) ; valeur de séance 140,0/90,0 | 13 lignes | ✅ réussi | R10_export.csv | — |
| R10c | Partage Android | bouton Partager | menu de partage du système affiché | affiché — *auparavant échoué (1×) : absent* | ✅ réussi | R10_menu_partage.png | — |
| R08a | Tableau de bord – moyenne 7 j | jeu fictif 4 séances | « 133/84 mmHg » ; 4 séance(s), 4 jour(s) | 133/84 affiché ; effectifs OK — *auparavant échoué (1×) : 133/84 absent ; effectifs KO* | ✅ réussi | R08_tableau_de_bord_jeu_fictif.png | — |
| R02j | Profil – modification | RECETTE-FICTIF-2, objectif 130/80 | base mise à jour + objectif affiché à l'accueil | nom=RECETTE-FICTIF-2, objectif=BpTarget(systolic=130, diastolic=80), accueil=130/80 affiché | ✅ réussi | R02j.png | — |
| R07a | Alerte immédiate | 186/112 | « Contacter rapidement un professionnel » + repos 5 min et nouvelle mesure | alerte affichée pendant l'intervalle | ✅ réussi | R07_alerte_valeur_tres_elevee.png | — |
| R07b | Alerte urgente – symptôme | symptôme « douleur thoracique » coché (avant enregistrement) | carte d'urgence immédiate avec bouton d'appel (112) | carte affichée immédiatement | ✅ réussi | R07_urgence_symptome.png | — |
| R07c | Appel des secours | bouton « Appeler les secours » | ouverture du composeur avec le numéro (sans appel automatique) | application au premier plan : null | ✅ réussi | — | — |
| R07d | Alerte de séance | séance 186/112 + douleur thoracique | « Recommandation urgente » à l'enregistrement | affichée | ✅ réussi | R07_seance_urgente_enregistree.png | — |
| R05a | Interruption de séance | 2 mesures puis retour → reprendre plus tard | accueil : carte « Séance en cours » 2/3 | carte affichée 2/3 | ✅ réussi | R05_seance_en_cours_accueil.png | — |
| R05b | Interruption – relance | relance de l'activité | séance en cours toujours proposée | proposée | ✅ réussi | — | — |
| R05c | Reprise de séance | Reprendre | reprise à la mesure 3 | mesure 3 | ✅ réussi | — | — |
| R05d | Dialogue de sortie – Continuer | retour puis « Continuer » | reste dans la séance | reste | ✅ réussi | — | — |
| R03a | Séance – intervalle | mesure 1 validée | écran d'intervalle avec décompte (60 s) | écran « Avant la mesure 2 » affiché | ✅ réussi | R03_intervalle.png | — |
| R03b | Séance – calculs du résumé | 148/94/76 ; 140/90/74 ; 136/88/72 | 141,3/90,7·74,0 ; 138,0/89,0·73,0 ; 136–148 (12) ; 88–94 (6) ; SD 6,1/3,1 ; −8 puis −4 ; −4 puis −2 ; PP 51 | moyenne 3=OK, moyenne 2 dernières=OK, valeur séance 141=OK, amplitude SYS=OK, amplitude DIA=OK, écart-type=OK, évolution SYS=OK, évolution DIA=OK, pression pulsée=OK | ✅ réussi | R03_resume_calculs.png | — |
| R03c | Séance – alerte de fin | moyenne 141/91, sans objectif | alerte « Au-dessus du seuil de référence à domicile » (135/85) | alerte affichée | ✅ réussi | R03_seance_enregistree.png | — |
| R03d | Séance – enregistrement | validation | 1 séance « Terminée », 3 mesures 148/140/136, heures réelles croissantes | 1 séance(s), statut COMPLETED, mesures [148/94@2026-11-15T04:30:45.421Z, 140/90@2026-11-15T04:33:53.249Z, 136/88@2026-11-15T04:36:42.708Z] | ✅ réussi | R03d.png | — |
| R03e | Tableau de bord | après séance | Dernière séance 141/91/74 affichée | affichée | ✅ réussi | R03_tableau_de_bord_apres.png | — |
| R03f | Historique | 1 séance | ligne 141/91 mmHg et détail des 3 mesures | conforme | ✅ réussi | R03_historique.png | — |
| R06 | Séance incomplète | 132/84/70 puis 128/82/68, arrêt | statut « Incomplète », valeur 130/83 | statut INCOMPLETE, 2 mesures | ✅ réussi | R06_resume_incomplet.png | — |
| R04a | Validation de mesure – champs vides | SYS et DIA vides | message d'erreur, rien enregistré | message affiché | ✅ réussi | R04_champs_vides.png | — |
| R04b | Validation – caractères non numériques | « 8x0 », « 7a2 » | lettres filtrées → 80 et 72 | filtrées | ✅ réussi | — | — |
| R04c | Validation – SYS ≤ DIA | 80 / 120 | dialogue de vérification « SYS doit être supérieure à DIA » | dialogue affiché | ✅ réussi | R04_inversion.png | — |
| R04d | Validation – hors bornes | 300 / 20 / 250 | dialogue listant les 3 valeurs hors plage | affiché | ✅ réussi | R04_hors_bornes.png | — |
| R04e | Validation – longueur | « 1234 » | saisie limitée à 3 chiffres (123) | 123 | ✅ réussi | — | — |
| R04f | Validation – pouls facultatif | 128 / 82 / (vide) | mesure enregistrée avec pouls absent (non inventé) | pouls=null, 128/82 | ✅ réussi | R04f.png | — |
| R04g | Séance en cours non comptée | 1 mesure non validée | séance au statut « En cours » | IN_PROGRESS | ✅ réussi | — | — |
| R04h | Validation – valeur hors bornes confirmée | 265/100 confirmée | enregistrée après confirmation explicite | mesure 2 enregistrée | ✅ réussi | — | — |
| R08b | Paramètre méthode de calcul | « Moyenne des deux dernières mesures » | moyenne 7 j recalculée « 131/83 mmHg » | 131/83 | ✅ réussi | R08_methode_deux_dernieres.png | — |
| R08c | Paramètre méthode – persistance | relance | méthode conservée (131/83) | conservée | ✅ réussi | — | — |
| R12a | IA – clé invalide | « clé avec accent » | clé refusée (caractères non autorisés) | refusée | ✅ réussi | — | — |
| R12b | IA – stockage de la clé | sk-ant-TEST-FICTIVE-0000 | clé chiffrée (absente en clair du fichier de préférences), relisible par l'application | relue=true, en clair dans le fichier=false | ✅ réussi | R12_cle_enregistree.png | — |
| R12c | IA – test de connexion | serveur simulé répond « OK » | « Connexion réussie » ; requête sans aucune donnée de santé ; en-têtes x-api-key et anthropic-version | statut=réussie ; corps={"model":"claude-opus-5-5","max_tokens":16000,"system":"Réponds uniquement par le mot OK.","messages":[{"role":"user","content":"Test de connexion."}],"output_c ; version=2026-10-21 | ✅ réussi | R12_test_connexion.png | A08 (mineure) : Ligne entière sélectionnable (rôle RadioButton) |
| R13a | Écran protégé (défaut) | réglage par défaut | FLAG_SECURE actif (captures bloquées) | FLAG_SECURE=true | ✅ réussi | capture noire R000 (image ATD) / fenêtre | — |
| R13b | Écran protégé – désactivation | interrupteur | FLAG_SECURE retiré | FLAG_SECURE=false | ✅ réussi | — | — |
| R13c | Numéro d'urgence | « 15a# » | caractères invalides filtrés, « 15 » enregistré | affiché=true, enregistré=15 | ✅ réussi | R13_parametres.png | — |
| R13d | Numéro d'urgence vide | champ vidé | retour à 112 (jamais de numéro vide) | enregistré=112 | ✅ réussi | — | — |
| R13e | Paramètres – persistance | relance | écran protégé désactivé et 112 conservés | FLAG_SECURE=false | ✅ réussi | — | — |
| R13f | Biométrie – non configurée sur l'appareil | activer le verrouillage sans empreinte ni code | message explicatif, verrouillage NON activé | message=true, activé=false | ✅ réussi | R13_biometrie_indisponible.png | — |
| R14a | Sauvegarde chiffrée | mot de passe « MotDePasseFictif1 » | fichier créé, contenu illisible en clair | 1358 octets, contient « RECETTE » en clair=false | ✅ réussi | — | — |
| R14b1 | Sauvegarde corrompue | mauvais mot de passe | refus avec message « …Mot de passe incorrect… », aucune donnée modifiée | refusée : Mot de passe incorrect ou fichier altéré. | ✅ réussi | — | — |
| R14b2 | Sauvegarde corrompue | fichier tronqué (moitié) | refus avec message « …incorrect ou fichier altéré… », aucune donnée modifiée | refusée : Mot de passe incorrect ou fichier altéré. | ✅ réussi | — | — |
| R14b3 | Sauvegarde corrompue | un octet modifié | refus avec message « …altéré… », aucune donnée modifiée | refusée : Mot de passe incorrect ou fichier altéré. | ✅ réussi | — | — |
| R14b4 | Sauvegarde corrompue | fichier vide | refus avec message « …invalide ou tronqué… », aucune donnée modifiée | refusée : Fichier de sauvegarde invalide ou tronqué. | ✅ réussi | — | — |
| R14b5 | Sauvegarde corrompue | autre fichier (texte) | refus avec message « …pas une sauvegarde… », aucune donnée modifiée | refusée : Ce fichier n'est pas une sauvegarde CardioSuivi. | ✅ réussi | — | — |
| R14b6 | Sauvegarde corrompue | en-tête modifié | refus avec message « …altéré… », aucune donnée modifiée | refusée : Mot de passe incorrect ou fichier altéré. | ✅ réussi | — | — |
| R14c | Restauration | bonne sauvegarde après suppression des données | 1 séance (125/126/127) et profil restaurés | 1 séance(s), [125, 126, 127], profil=RECETTE-FICTIF | ✅ réussi | — | — |
| R14d | Sauvegarde – confirmation | mots de passe différents | bouton de sauvegarde inactif | inactif | ✅ réussi | R14_mots_de_passe_differents.png | — |
| R14e | Sauvegarde – mot de passe court | « court » (5 caractères) | bouton inactif (minimum 8) | inactif | ✅ réussi | — | — |
| R01 | Avertissement initial | case non cochée puis cochée | Bouton inactif sans case ; tableau de bord après acceptation | Bouton inactif puis tableau de bord affiché | ✅ réussi | R01.png | A01 (majeure) : Ouverture en arrière-plan (produceState + Dispatchers.IO) avec écran de chargement |
| R01b | Avertissement initial | relance de l'application | avertissement non réaffiché | tableau de bord directement | ✅ réussi | — | A01 (majeure) : Ouverture en arrière-plan (produceState + Dispatchers.IO) avec écran de chargement |
| R11b2 | Traitement – ajout valide | MEDICAMENT-FICTIF, 08:00, 01/09/2026 | traitement enregistré avec heure et date | [Treatment(id=1, name=MEDICAMENT-FICTIF, dosage=1 comprimé (fictif), usualTime=08:00, startDate=2026-09-01, endDate=null, active=true, notes=)] | ✅ réussi | — | A03 (modérée) : Enregistrement bloqué tant que l'heure ou la date est invalide, message d'aide |
| R11c | Traitement – persistance | fermer/rouvrir | toujours listé | listé | ✅ réussi | — | — |
| R11d | Traitement – modification | posologie 2 cp, 08:00, 01/09/2026, arrêté | mis à jour, libellé « (arrêté) » | dosage=2 comprimés (fictif), heure=08:00, début=2026-09-01, actif=false | ✅ réussi | R11d.png | — |
| R11e | Journal d'observations | ajout | observation listée avec la date du jour | listée | ✅ réussi | — | — |
| R11f | Traitement – suppression | supprimer + confirmer | traitement supprimé | 0 restant(s) | ✅ réussi | — | — |
| R11g | Automesure standardisée | Commencer aujourd'hui | protocole démarré, statut « Non commencé » et boutons matin/soir | affiché | ✅ réussi | R11_hbpm.png | — |
| R11h | Calendrier | mois courant | grille mensuelle, légende et prochaines séances (mer/jeu/ven 07:00/19:00) | affiché | ✅ réussi | R11_calendrier.png | — |
| R12d | IA – consentement | première question | dialogue de consentement AVANT tout envoi ; aucune requête | dialogue affiché, requêtes=0 | ✅ réussi | R12_consentement.png | — |
| R12e | IA – refus du consentement | Refuser | aucune requête envoyée | requêtes envoyées=0 | ✅ réussi | — | — |
| R12f | IA – question avec consentement | « Comment ma tension évolue-t-elle ? » | réponse affichée ; requête contenant statistiques et question, SANS nom ni date de naissance | réponse=true ; nom absent=true ; naissance absente=true ; âge=true ; question=true | ✅ réussi | R12_reponse_ia.png | — |
| R12g | IA – garde-fou prescription | « Dois-je augmenter mon traitement ? » | refus local, aucune requête envoyée | refus affiché, requêtes supplémentaires=0 | ✅ réussi | R12_garde_fou.png | — |
| R12h | IA – erreur d'authentification | réponse HTTP 401 simulée | message « Clé d'API invalide » lisible, pas de plantage | affiché | ✅ réussi | R12_erreur_401.png | — |
| R12i | IA – refus du modèle | stop_reason=refusal simulé | message explicite, pas de contenu vide | affiché | ✅ réussi | — | — |
| R12j | IA – pas de connexion | service injoignable | « Connexion au service impossible », l'analyse locale reste disponible | affiché | ✅ réussi | R12_hors_connexion.png | — |
| R12k | Hors connexion – analyse locale | question prédéfinie | réponse locale disponible sans réseau | affichée | ✅ réussi | — | — |
| R12l | IA – retrait du consentement | consentement retiré puis question | consentement redemandé, aucune requête | dialogue réaffiché, requêtes=0 | ✅ réussi | — | — |
| R14f | Suppression de toutes les données | double confirmation | données effacées, application relancée sans plantage, avertissement initial réaffiché | effacées=true, avertissement=true | ✅ réussi | R14_apres_suppression.png | A02 (majeure) : Dépôts recréés sur la base courante ; redémarrage de l'interface sur une tâche neuve après suppression |
| R21a | Rotation – formulaire non enregistré | saisie « ROTATION-FICTIF » puis rotation | saisie conservée | conservée — *auparavant échoué (1×) : PERDUE* | ✅ réussi | R21_profil_paysage.png | A04 (modérée) : Formulaires en rememberSaveable (profil, traitement en édition, observation, notes, numéro d'urgence, modèles IA) |
| R16a | Alarme exacte | autorisation accordée, rappel à 11:05 | alarme exacte programmée | canScheduleExact=true | ✅ réussi | R15_dumpsys_alarm.txt | A05 (modérée) : Alerte sur l'accueil et l'écran Rappels avec bouton vers le réglage système |
| R16b | Notification de rappel | attente du déclenchement (~2 min) | notification « Séance de tension du matin » avec Commencer / Reporter… / Ignorer | reçue à 11:05:16 — *auparavant échoué (1×) : non reçue en 6 min* | ✅ réussi | R16_notification.png | A05 (modérée) : Alerte sur l'accueil et l'écran Rappels avec bouton vers le réglage système |
| R18-synthétique | OCR – jeu de référence (synthétique) | 30 images × 3 champs | aucune erreur non signalée ; taux de lecture correcte élevé | correct 93.3 % · erreur 4.4 % (dont non signalée 0) · rejet 2.2 % · images entièrement exactes 27/30 | ✅ réussi | R18_ocr_resultats.tsv | A06 (modérée) : Confiance fondée sur le nombre de lectures concordantes, le décodage exact, la complétude et la cohérence de taille SYS/DIA |
| R18-réel | OCR – jeu de référence (réel) | 36 images × 3 champs | aucune erreur non signalée ; taux de lecture correcte élevé | correct 84.3 % · erreur 5.6 % (dont non signalée 0) · rejet 10.2 % · images entièrement exactes 22/36 | ✅ réussi | R18_ocr_resultats.tsv | A06 (modérée) : Confiance fondée sur le nombre de lectures concordantes, le décodage exact, la complétude et la cohérence de taille SYS/DIA; A07 (modérée) : Contrôle des bornes ; test de régression |
| R21b | Rotation – séance en cours | valeurs saisies 133/86 puis rotation | étape et valeurs conservées | conservées | ✅ réussi | R21_seance_paysage.png | A04 (modérée) : Formulaires en rememberSaveable (profil, traitement en édition, observation, notes, numéro d'urgence, modèles IA) |
| R21c | Pliable – passage à l'écran externe pendant la saisie | wm size 1056×1066, 360 dpi | valeurs conservées, interface utilisable | conservées | ✅ réussi | R21_ecran_externe_pendant_seance.png | A04 (modérée) : Formulaires en rememberSaveable (profil, traitement en édition, observation, notes, numéro d'urgence, modèles IA) |
| R21d | Écran externe – accueil | 1056×1066 | bouton principal visible, contenu défilant | visible | ✅ réussi | R21_accueil_ecran_externe.png | — |
| R21-720x1280 | Taille d'écran – petit téléphone | 720x1280 | accueil affiché sans chevauchement, largeur de contenu limitée | affiché | ✅ réussi | R21_accueil_720x1280.png | — |
| R21-1600x2560 | Taille d'écran – tablette / grand écran déplié | 1600x2560 | accueil affiché sans chevauchement, largeur de contenu limitée | affiché | ✅ réussi | R21_accueil_1600x2560.png | — |
| R19 | OCR – parcours complet (photo réelle importée) | photo réelle de l'écran 125/85/78 | champs pré-remplis 125 / 85 / 78, validation manuelle requise | sélecteur=true ; 125=true 85=true 78=true | ✅ réussi | R19_validation_ocr.png | — |
| R19b | OCR – enregistrement | validation | mesure enregistrée, source « Reconnaissance automatique » | 125/85/78 source=OCR | ✅ réussi | — | — |
| R20a | Notifications refusées | autorisation refusée | avertissement sur l'accueil « Les notifications sont bloquées » | affiché | ✅ réussi | R20_notifications_refusees.png | — |
| R20b | Diagnostic des autorisations | notifications et alarmes exactes refusées | deux lignes en erreur avec boutons de réglage | conforme | ✅ réussi | R20_diagnostic.png | — |
| R20c | Alarmes exactes refusées | appops SCHEDULE_EXACT_ALARM deny | repli sur alarme approchée (pas de plantage) | canScheduleExact=false | ✅ réussi | — | — |
| R20d | Caméra refusée | refus de l'autorisation caméra | message explicatif ; import et saisie manuelle disponibles | affiché | ✅ réussi | R20_camera_refusee.png | — |
| R20e | Saisie manuelle sans caméra | Saisie manuelle | formulaire de saisie accessible | accessible | ✅ réussi | — | — |
| R15a | Rappels – valeurs initiales | aucune modification | mer/jeu/ven, 07:00 et 19:00 | 07:00 / 19:00 | ✅ réussi | R15_ecran_rappels.png | — |
| R15b | Rappels – alarmes système | réglages par défaut | 2 alarmes programmées (matin et soir) dans AlarmManager | 2 alarme(s) RTC_WAKEUP de l'application — *auparavant échoué (1×) : 0 alarme(s) RTC_WAKEUP de l'application* | ✅ réussi | R15_dumpsys_alarm.txt | A09 (modérée) : Reprogrammation des rappels (valeurs initiales) à la fin de la suppression des données |
| R17 | Détection de séance manquée | créneau d'hier 19:00 sans séance (WorkManager) | notification « Séance non réalisée » | reçue | ✅ réussi | R17_seance_manquee.png | — |
| R15c | Rappels – jours | ajout du lundi | lundi enregistré | [MONDAY, WEDNESDAY, THURSDAY, FRIDAY] | ✅ réussi | — | — |
| R15d | Rappels – désactivation du soir | interrupteur soir | soir désactivé | eveningEnabled=false | ✅ réussi | — | — |
| R15e | Protocole de séance | 90 s, 2 mesures | intervalle 90 s et 2 mesures enregistrés | interval=90, mesures=2 | ✅ réussi | — | — |
| R15f | Sélecteur d'heure | ouvrir / annuler | dialogue d'heure 24 h affiché puis fermé sans modification | heure matin=07:00 | ✅ réussi | R15_selecteur_heure.png | — |
| R15g | Rappels – persistance | relance | 90 s et 2 mesures conservés | conservés | ✅ réussi | — | — |
| R16p | Alarmes exactes non autorisées (Android 14, défaut) | installation neuve | alerte sur l'accueil + bouton vers « Alarmes et rappels » | canScheduleExact=false, alerte=true | ✅ réussi | R16_alerte_heure_exacte.png | A05 (modérée) : Alerte sur l'accueil et l'écran Rappels avec bouton vers le réglage système |
| R16c | Reporter | Reporter… → Dans 10 minutes | notification retirée, nouvelle alarme ~10 min plus tard | dialogue=true, alarmes=2 | ✅ réussi | R16_report_dialogue.png | — |
| R16d | Ignorer | bouton Ignorer | notification retirée, événement IGNORED journalisé | retirée=true, événements=[IGNORED, SNOOZED_10] | ✅ réussi | — | — |
| R16e | Commencer depuis la notification (pilotage hôte, notification réelle « Rappel reporté : séance du matin ») | appui sur « Commencer » (adb input tap) | ouverture directe de la séance du matin | chargement (base chiffrée) puis écran « Nouvelle séance », moment « Matin » présélectionné, affiché entre 37 et 57 s après l appui (émulateur sans accélération) ; dans le test instrumenté, UiAutomator ne détectait pas l écran (limite de l outil, écran pourtant visible sur la capture R16_commencer.png) — *auparavant échoué (3×) : non détectée en 182 s* | ✅ réussi | preuves/R16e_commencer_chronologie.png (captures à 37, 57, 70, 106, 132, 169 s) | — |
| RB01 | Redémarrage du téléphone | arrêt brutal de l'émulateur (perte des alarmes système) puis démarrage à froid | alarmes reprogrammées sans ouvrir l'application | 2 alarmes (18/11 07:00 et 19:00) recréées 243 s après la fin du démarrage ; processus lancé « for broadcast BootReceiver » | ✅ réussi | preuves/RB_alarmes_apres_redemarrage.txt | — |
| NE01 | Motorola Razr 50s physique | parcours complet sur le téléphone | comportement identique à l'émulateur | aucun téléphone connecté à l'environnement | ⏸ non exécuté | — | — |
| NE02 | Pliable : charnière, mode « flex » (posture à demi plié), écran externe réel | pliage pendant une séance | mise en page adaptée, séance conservée | pas de capteur de charnière ni de posture dans l'émulateur ; seuls rotation, changement de taille et écran externe simulé testés (R21) | ⏸ non exécuté | — | — |
| NE03 | OCR : prise de vue en direct d'un vrai tensiomètre (CameraX) | photo d'un écran réel par l'appareil photo | valeurs pré-remplies, validation manuelle | l'émulateur ne fournit qu'une scène virtuelle (R03) ; la reconnaissance est testée sur photos importées (R18, R19) | ⏸ non exécuté | — | — |
| NE04 | OCR : plusieurs tensiomètres et photos réelles indépendantes | jeu de photos réelles variées | taux mesurés sur appareils variés | une seule photo réelle disponible (36 variantes dérivées) ; sources d'images en ligne bloquées par le réseau | ⏸ non exécuté | — | — |
| NE05 | IA : fournisseurs réels (Anthropic, OpenAI) | clé API réelle | réponse réelle | exclu volontairement (aucune donnée médicale transmise sans autorisation) ; serveur simulé utilisé (R12) | ⏸ non exécuté | — | — |
| NE06 | Performances et autonomie sur téléphone réel | usage prolongé | fluidité, pas d'ANR | émulateur sans accélération matérielle : temps non représentatifs | ⏸ non exécuté | — | — |
| RL00 | APK release – installation | app-x86_64-release.apk 1.0.2 signé | installation réussie | Performing Streamed Install / Success | ✅ réussi | journal adb | — |
| RL01 | Release – démarrage | premier lancement | avertissement initial affiché | affiché | ✅ réussi | journal adb | A01 (majeure) : Ouverture en arrière-plan (produceState + Dispatchers.IO) avec écran de chargement |
| RL02 | Release – écran protégé | capture d'écran système | capture noire (FLAG_SECURE actif par défaut) | niveaux de gris (0, 0) | ✅ réussi | /tmp/rl_secure.png | — |
| RL03 | Release – tableau de bord | acceptation | tableau de bord | affiché | ✅ réussi | journal adb | — |
| RL04-1 | Release – saisie de mesure | 142/91/71 | « Mesure 1 enregistrée » | OK | ✅ réussi | journal adb | — |
| RL04-2 | Release – saisie de mesure | 138/88/69 | « Mesure 2 enregistrée » | OK | ✅ réussi | journal adb | — |
| RL05 | Release – calculs | 142/91/71 et 138/88/69 | moyenne 140,0/89,5 · 70,0 | conforme | ✅ réussi | journal adb | — |
| RL06 | Release – enregistrement | séance incomplète | écran « Séance enregistrée » | OK | ✅ réussi | journal adb | — |
| RL07 | Release – sérialisation JSON (R8) | aperçu « Données transmises » | JSON du contexte affiché sans plantage | affiché | ✅ réussi | journal adb | — |
| RL08 | Release – rapport PDF | période 30 jours | « Rapport PDF généré. » | OK | ✅ réussi | journal adb | — |
| RL09 | Release – plantages | journal crash du système | aucun plantage de com.cardiosuivi.app | aucun | ✅ réussi | journal adb | — |
| RL10 | Release – persistance (processus tué) | force-stop puis relance | séance 140/90 toujours dans l'historique (base chiffrée) | présente | ✅ réussi | journal adb | — |
| BIO01 | Appareil – code PIN | locksettings set-pin 1234 | code de verrouillage défini | Lock credential verified successfully | ✅ réussi | journal adb | — |
| BIO02 | Appareil – empreinte | assistant Réglages + emu finger touch 1 | empreinte virtuelle enregistrée (dumpsys fingerprint) | ajoutée | ✅ réussi | journal adb | — |
| BIO03 | Activation du verrouillage | interrupteur + empreinte 1 | fenêtre biométrique Android puis activation | fenêtre affichée | ✅ réussi | journal adb | — |
| BIO04 | Verrouillage après 60 s en arrière-plan | retour après 80 s (12:28:03 → 12:29:24) | application verrouillée, authentification demandée | invite système « Déverrouiller CardioSuivi » affichée dès le retour, contenu inaccessible (l'invite masque l'écran « CardioSuivi est verrouillé », d'où l'échec de la détection du script) — *auparavant échoué (1×) : absent* | ✅ réussi | preuves/BIO_journal_biometrie.txt | — |
| BIO05 | Empreinte inconnue | emu finger touch 2 (non enregistrée) | accès refusé, application toujours verrouillée | « onAuthenticationFailed / handleAuthenticationRejected » à 12:32:23 ; invite toujours affichée 18 s après — *auparavant échoué (1×) : DÉVERROUILLÉE* | ✅ réussi | preuves/BIO_journal_biometrie.txt | — |
| BIO06 | Empreinte connue | emu finger touch 1 | déverrouillage | « handleAuthenticationSucceeded » à 12:32:59 ; écran Paramètres affiché à 12:33:20 — *auparavant échoué (1×) : toujours verrouillé* | ✅ réussi | preuves/BIO_journal_biometrie.txt | — |
| BIO07 | Courte sortie (< 60 s) | retour après 15 s (12:34:01 → 12:34:17) | pas de verrouillage (sélecteur de photo, partage…) | écran Paramètres affiché sans invite | ✅ réussi | pilotage adb | — |
