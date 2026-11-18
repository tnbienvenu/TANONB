# CardioSuivi 1.1.0 — Refonte graphique et ergonomique

Branche : `claude/cardiosuivi-refonte-ui-1.1.0` (à partir de la 1.0.2 validée). Version proposée : **1.1.0 (versionCode 4)**,
cohérente avec le schéma du projet (1.0.x = corrections, 1.1 = évolution de l'interface sans changement fonctionnel).

> **Références iOS 27** : les captures et guides d'interface de l'application Santé d'iOS 27 n'étaient pas consultables
> depuis l'environnement de développement (pas d'accès réseau à ces ressources). La refonte s'inspire donc uniquement des
> **principes ergonomiques publics et stables** des applications de santé premium (fond grisé, cartes blanches arrondies,
> en-tête de catégorie coloré, grandes valeurs, hiérarchie marquée). **Aucune ressource, icône ou image d'Apple n'est
> utilisée** : icônes Material (Google, licence Apache 2.0), couleurs et composants originaux.

## 1. Audit UX/UI de la version 1.0.x

| Écran | Constat | Gravité UX |
|---|---|---|
| Thème | Palette bleu-gris générique ; cartes grises peu contrastées sur fond gris ; valeurs en `headlineMedium` (peu visibles) ; pas de système d'espacements ni de rayons centralisé | Majeure |
| Navigation | 5 onglets « Accueil / Historique / Graphiques / Analyse / Plus » : profil et réglages cachés dans « Plus » ; assistant IA difficile à trouver | Modérée |
| Accueil | Empilement de cartes textuelles de même poids ; la dernière tension n'était pas mise en avant ; pas de graphique ; « Accès rapides » en puces redondantes avec la navigation | Majeure |
| Séance | Progression par barre linéaire uniquement ; décompte en texte + barre ; pas d'indicateur de confiance OCR par champ (seulement un texte d'aide) ; récapitulatif peu lisible | Majeure |
| Graphiques | Un seul graphique visible à la fois via 8 puces ; valeur touchée affichée sous le graphique en petit ; pas de dates en abscisse ; couleurs fixes (mauvais contraste en sombre) | Majeure |
| Analyse | Liste de « constats » à points colorés (information portée par la couleur seule) ; pas de structure de rapport ; analyse locale et IA sur deux écrans sans distinction visuelle | Majeure |
| Historique | Une carte par séance, sans regroupement visuel ; statut par pastille colorée seule | Modérée |
| Formulaires | Unités dans les libellés, champs à angles vifs, boutons de 40 dp, champs obligatoires non signalés | Modérée |
| Mode sombre | Partiel : couleurs des courbes et alertes identiques en clair et en sombre | Modérée |
| Accessibilité | Valeurs lues chiffre par chiffre par TalkBack sans libellé ; couleur seule pour certains états | Modérée |

## 2. Système de design (`ui/theme/Theme.kt`, `ui/components/Components.kt`)

- **Couleurs sémantiques** (`HealthColors`, clair et sombre) : systolique rouge doux, diastolique bleu, pouls violet,
  pression pulsée magenta, favorable vert (uniquement pour un constat que le moteur médical qualifie de favorable),
  attention orange, alerte rouge, neutre gris ; fond grisé `#F2F3F7` / noir en sombre ; cartes blanches / `#1C1D21`.
- **Typographie** : police système, titres en gras, grand titre 32 sp ; **valeurs à chiffres tabulaires** (`NumberStyles` :
  52 / 34 / 24 / 17 sp).
- **Espacements** (grille de 4 dp), **rayons** (cartes 22 dp, champs 14 dp, boutons 16 dp), **tailles minimales**
  (cibles ≥ 48 dp, boutons 54 dp, largeur de contenu ≤ 680 dp sur écran déplié).
- **Composants** : `HealthCard` (carte à ombre légère), `CardHeader` (icône + catégorie colorée + chevron),
  `MetricBlock` / `BpValue` (libellé en toutes lettres + valeur + unité, lu comme une phrase par TalkBack),
  `StatTile`, `StatusBadge` (icône + texte + couleur), `SegmentedControl`, `SettingsRow`, `PrimaryButton`,
  `SecondaryButton`, `StepProgress` (MESURE n / 3), `CircularCountdown`, `EmptyState`, `Appear` (apparition des cartes).
- **Animations** : apparition des cartes, fondu entre écrans, chronomètre, sélection graphique, coche d'enregistrement ;
  **toutes désactivées si l'utilisateur a désactivé les animations** dans Android (`ANIMATOR_DURATION_SCALE = 0`).

## 3. Écrans

| Écran | Refonte |
|---|---|
| Navigation | **Accueil · Mesures · Tendances · Analyse IA · Profil** ; icône pleine + libellé + couleur pour l'onglet actif ; état des onglets conservé ; gestes système respectés (insets) |
| Accueil | En-tête CARDIOSUIVI / Bonjour / résumé / date / icône de profil ; carte **Dernière tension** (dernière mesure en 52 sp, heure, moyenne de séance, évolution vs séance précédente) ; **Évolution tensionnelle** (graphique interactif, 7 j / 30 j / 90 j / Année) ; **Prochaine séance** (jour, heure, matin/soir, nombre de mesures, « Commencer une séance ») ; **Tendances** (moyenne récente, évolution SYS/DIA, variabilité, complétude) ; **Objectif** ; **Analyse** (synthèse locale + « Consulter mon analyse ») ; alertes et avertissements de rappels conservés |
| Mesures | Bouton « Nouvelle séance », séance en cours, filtres segmentés, **séances regroupées par date** (heure, moyenne SYS/DIA, pouls, nombre de mesures, badge Complète/Incomplète), détail au toucher |
| Séance | Préparation, **MESURE n / 3 avec segments**, photo, **badge de confiance OCR par champ** (Lu avec confiance / À vérifier / Non lu) + jauge de confiance globale, correction, **chronomètre circulaire**, **récapitulatif** des mesures et moyennes, coche animée d'enregistrement |
| Tendances | Toutes les vues sur un seul écran : moyennes de la période, tension (séance ou mesures individuelles, repères), fréquence cardiaque, matin/soir, moyennes hebdomadaires et mensuelles, variabilité, pression pulsée, jours de la semaine, historique ; bulle de valeur (date, heure, valeur exacte) au toucher ou au glissement ; états vides |
| Analyse IA | Rapport en **8 cartes** (synthèse, évolution, systolique, diastolique, matin/soir, vigilance, recommandations générales, questions pour le cardiologue) **marquées « Calcul local · sans IA »** ; carte **« Interprétation par l'IA »** distincte (badge « Générée par une IA · facultative »), aucune interprétation affichée tant qu'aucune question n'est posée ; statistiques détaillées, limites, références, avertissements conservés |
| Profil | Carte d'identité ; sections Santé, Suivi, Intelligence artificielle, Sécurité et confidentialité, Informations |
| Formulaires | Unités en suffixe (cm, kg, mmHg, bpm), champs arrondis, clavier numérique, « * » sur les champs obligatoires, messages d'erreur sous le champ, boutons ≥ 54 dp, confirmations des actions sensibles conservées |

## 4. Ce qui n'a pas changé (contrainte de non-régression)

Modèles de données, base chiffrée SQLCipher, calculs (`domain`), seuils et règles médicales, alertes, rappels et alarmes,
OCR, sauvegarde chiffrée, biométrie, IA (consentement, données transmises, garde-fous), rapports PDF/CSV : **aucune
modification**. Seuls les fichiers `ui/` (et le numéro de version) ont été modifiés. Le module `domain` est inchangé.

Le recensement des fonctions accessibles avant/après est conservé : chaque entrée de l'ancien menu « Plus » se retrouve
dans « Profil » avec le même libellé ; « Historique », « Graphiques » et « Analyse » deviennent « Mesures »,
« Tendances » et « Analyse IA ».

## 5. Limitations restantes

- **Pas de test sur un Motorola Razr 50s réel** (émulateur Android 14 aux dimensions du Razr déplié, sans charnière).
- **Écran externe du Razr** : seule une taille d'écran équivalente est simulée ; l'usage réel sur l'écran externe
  (applications autorisées, gestes) n'est pas vérifié.
- **Recherche dans l'historique** : la fonction n'existait pas en 1.0 ; non ajoutée (filtres conservés).
- Les graphiques sont dessinés par l'application (pas de bibliothèque tierce) : pas de zoom par pincement.
- Captures d'écran réalisées avec des **données fictives** (profil « Démo (fictif) »), jamais enregistrées en production.

## 6. Résultats des tests (version 1.1.0)

Environnement : émulateur Android 14 (API 34, x86_64, **sans accélération matérielle**), écran 720 × 1760 à 280 dpi
(proportions du Razr 50s déplié). Données **fictives** créées par les tests (profils « Démo (fictif) », « … FICTIF »).
Assistant IA testé uniquement contre un **serveur simulé local** : aucune donnée envoyée à un fournisseur d'IA.
Journal brut : `recette/journal_refonte_1.1.tsv` (dernière exécution de chaque scénario).

| Domaine | Classes / scénarios | Résultat |
|---|---|---|
| Démarrage, profil | R01, R02a–j | réussis |
| Séances (3 mesures, erreurs de saisie, incomplètes, interruption, alertes, symptômes) | R03a–f, R04a–h, R05a–d, R06, R07a–d | réussis |
| Accueil, analyses, graphiques, rapports PDF/CSV, traitements, calendrier | R08a–c, R09a–g, R10a–c, R11a–h | réussis |
| Assistant IA (serveur simulé, consentement, données transmises) | R12a–l | réussis |
| Sécurité, sauvegardes chiffrées et corrompues, restauration | R13a–f, R14a–f | réussis |
| Rappels, alarmes exactes, Reporter / Ignorer, séance manquée | R15a–g, R16p, R16a–d, R17 | réussis |
| Ouverture de la séance depuis le bouton « Commencer » de la notification | R16e | **échec de l'automate** : la séance du matin s'est bien ouverte (capture `captures-1.1.0/preuves_campagne_ui/R16e_commencer_apres_180s.png`), mais après la fenêtre de 180 s du test (37 à 57 s en 1.0.2 sur le même émulateur). Délai à vérifier sur appareil réel |
| OCR (66 images de référence) | R18 synthétique / réel | 93,3 % / 84,3 % de valeurs correctes, **0 erreur non signalée** |
| Import d'une photo, validation, enregistrement | R19, R19b | réussis |
| Autorisations refusées (notifications, alarmes exactes, caméra) | R20a–e | réussis |
| Rotation, écran externe 1056 × 1066 | R21a–d | réussis |
| Tailles 720 × 1280 et 1600 × 2560 | R21 (boucle de tailles) | **automate interrompu** (`ComposeNotIdleException` : l'émulateur logiciel met plus de 60 s à recréer l'écran) ; **vérifié manuellement** par captures après 2 min (`captures-1.1.0/preuves_campagne_ui/R21_hote_*.png`) ; aucun rendu continu au repos mesuré (1 image en 10 s) |
| Refonte : accueil, moyenne de séance, TalkBack, cible ≥ 48 dp, historique groupé, filtres, 8 cartes d'analyse + IA distincte, MESURE n / 3, mode sombre, police 130 % | R22a–j | réussis |

Synthèse : **123 scénarios réussis sur 124 enregistrés** par l'automate, 1 échec d'automate (R16e) dont le résultat
fonctionnel est constaté a posteriori, et 2 contrôles de taille d'écran vérifiés manuellement.

**Non exécuté** : aucun test sur un Motorola Razr 50s physique (écran externe réel, charnière, capteur d'empreinte,
appareil photo réel en conditions de mesure, autonomie, performances réelles).

### APK signé de production

- `dist/CardioSuivi-1.1.0-arm64-v8a.apk` (Razr 50s : arm64-v8a), versionCode 4, versionName 1.1.0, minifié R8.
  SHA-256 : `994f36100bf9b916a0aa24f6e13f15aa8f485bc8cd674920efabafbc157f46dc`.
- Signé avec **le même certificat que la 1.0.x** (SHA-256 `c526072e…a500b7`, vérifié par `apksigner`) : s'installe en
  mise à jour par-dessus la 1.0.2 sans perte de données. La base de données et ses modèles n'ont pas changé ; la mise à
  jour 1.0.2 → 1.1.0 elle-même n'a toutefois pas été rejouée sur l'émulateur.
- Recette de l'APK release (variante x86_64, même code) : `recette/journal_release_1.1.0.tsv` — RL00 à RL10 réussis
  (installation, écran protégé contre les captures, séance, calculs, enregistrement, JSON de l'IA après R8, rapport PDF,
  absence de plantage, redémarrage). RL09 avait d'abord été signalé à tort : le filtre comptait un plantage antérieur
  de la variante **debug** ; filtre corrigé (nom de processus exact, tampon vidé au départ).
- Aucune donnée fictive dans l'APK de production : les jeux de démonstration n'existent que dans les tests instrumentés.

### Anomalie observée, non reproduite

- 15/11 20:37, variante debug, pendant la fermeture de l'activité à la fin d'un test instrumenté R03 (au moment où ce
  test échouait sur une attente de libellé, corrigée ensuite) : `ArrayIndexOutOfBoundsException` interne à Compose
  (`SlotTable.moveSlotGapTo`, destruction de la composition). Non reproduite dans les ~5 h de tests suivantes ni sur
  l'APK release. Origine probable : bibliothèque Compose lors de l'arrêt forcé par le cadre de test ; à surveiller.
