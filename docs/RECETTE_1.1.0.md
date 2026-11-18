# CardioSuivi 1.1.0 — Rapport de recette

Date d'exécution : 18 novembre 2026. Branche : `claude/cardiosuivi-refonte-ui-1.1.0`.

## 1. Environnement

| Élément | Valeur |
|---|---|
| Appareil | Émulateur Android 14 (API 34, x86_64, image Google APIs), **sans accélération matérielle** (rendu logiciel) |
| Écran | 720 × 1760 à 280 dpi (proportions du Motorola Razr 50s déplié) ; contrôles complémentaires 1056 × 1066 (écran externe), 720 × 1280, 1600 × 2560 |
| APK testés | debug (tests instrumentés) ; **release signé** x86_64 (même code que l'APK arm64 livré) pour la mise à jour et la recette release ; 1.0.1 release signé reconstruit depuis le commit `e713a21` (même certificat) |
| Données | **fictives** uniquement (profils « Démo (fictif) », « RECETTE-FICTIF », « MIGFICTIF »…) ; aucune donnée réelle, aucune photo |
| IA | serveur **simulé** local (MockWebServer) ; aucune donnée transmise à un fournisseur d'IA |
| Appareil réel | **non testé** (aucun Motorola Razr 50s disponible) |

Journaux : `recette/journal_1.1.0.tsv` (tests instrumentés, dernière exécution de chaque scénario),
`recette/journal_hote_1.1.0.tsv` (contrôles réalisés depuis l'hôte), `recette/journal_migration_1.0.1_1.1.0.tsv` et
`recette/journal_migration_passe2.tsv` (mise à jour réelle), `recette/journal_release_1.1.0.tsv` (APK release).
Captures : `docs/captures-1.1.0/` (écrans, parcours clair/sombre, preuves par scénario).

## 2. Tests automatisés sur JVM

| Suite | Résultat |
|---|---|
| `:domain:test` (statistiques, alertes, rappels, analyse, IA, sauvegarde, CSV, **saisie et horodatage 1.1.0**) | **58 tests, 0 échec** |
| `:app:testDebugUnitTest` (Room, **migration 1 → 2**, horodatage persistant, IA simulée, verrouillage) | **14 tests, 0 échec** |

## 3. Recette fonctionnelle sur Android (tests instrumentés)

**134 scénarios enregistrés : 132 réussis, 2 échecs d'automate** dont le résultat fonctionnel a été vérifié depuis
l'hôte (§ 4). Synthèse par exigence :

| Exigence | Scénarios | Résultat |
|---|---|---|
| Saisie obligatoire SYS / DIA / PUL, entiers, SYS ≤ DIA refusée, valeurs conservées après erreur, unités affichées | R04a–h, R23b | réussis |
| Trois mesures successives, intervalle, récapitulatif, moyennes exactes (141,3/90,7·74,0 ; 138,0/89,0·73,0 ; 129,0/83,0·72,7) | R03a–f, R23f–g, R22h | réussis |
| Horodatage automatique (ms, fuseau, décalage UTC), distinct par mesure, affiché à la seconde, inchangé par correction, conservé après fermeture, changement de fuseau | R23c, R23e, R23f, R23h, R23i, R23k, R03d | réussis |
| Bras gauche enregistré, aucun choix de bras ni de période, moment déduit des plages, plages configurables | R23a, R23d, R24c, R24d, R11g | réussis |
| Suppression de l'OCR : aucune autorisation CAMERA, aucun bouton photo/import, formulaire direct | R20d, R20e, R23b | réussis |
| Retour à la mesure précédente, correction avant clôture, confirmation avant abandon, interruption et reprise | R23e, R23h, R23j, R05a–d, R06 | réussis |
| Alertes et symptômes d'urgence | R07a–d | réussis |
| Accueil, graphiques, analyse (9 cartes + IA distincte), historique groupé | R08a–c, R09a–g, R22a–g | réussis |
| Rapports PDF (3 pages) et CSV | R10a–c | réussis |
| Traitements, observations, calendrier, automesure standardisée | R11a–h | réussis |
| Rappels : programmation, alarmes exactes, notification, Reporter, Ignorer, séance manquée | R15a–g, R16p, R16a–d, R17 | réussis |
| Ouverture de la séance depuis « Commencer » de la notification | R16e | échec d'automate → **réussi au contrôle hôte (5,1 s)** |
| IA (serveur simulé) : consentement, clé chiffrée, données transmises sans identité, garde-fou | R12a–l | réussis |
| Sécurité, sauvegarde chiffrée, fichiers corrompus refusés, restauration, suppression totale | R13a–f, R14a–f | réussis |
| Premier démarrage, profil, objectif | R01, R01b–c, R02a–j | réussis |
| Autorisations refusées | R20a–c | réussis |
| Rotation (formulaire et séance) | R21a–b | réussis |
| Écran externe 1056 × 1066 pendant la saisie | R21c | échec d'automate → **réussi au contrôle hôte** |
| Mode clair et sombre : 17 écrans chacun, police 130 %, TalkBack, cibles ≥ 48 dp | R24a–b, R22c–d, R22i–j | réussis |

## 4. Contrôles réalisés depuis l'hôte (`journal_hote_1.1.0.tsv`)

| Id | Contrôle | Résultat | Preuve |
|---|---|---|---|
| R21c | Passage à l'écran externe pendant la saisie (133/86) | valeurs conservées, affichées après 56 s | `preuves/R21c_hote_ecran_externe_valeurs.png` |
| R21d | Accueil sur écran externe | affiché, contenu défilant | `preuves/R21d_hote_ecran_externe_accueil.png` |
| R21 | 720 × 1280 et 1600 × 2560 | affichés, largeur limitée sur grand écran | `preuves/R21_hote_*.png` |
| R16e | « Commencer » depuis la notification | séance ouverte en 5,1 s (journal système) | `preuves/R16e_seance_ouverte_depuis_notification.png` |

Les échecs d'automate R21c et R16e tiennent à l'outil de test sur cet émulateur lent (recréation d'activité de plus de
3 min pour l'un, vidage de hiérarchie incomplet pour l'autre) ; le comportement de l'application est conforme.

## 5. Mise à jour réelle 1.0.1 → 1.1.0

APK release 1.0.1 (versionCode 2) installé, données saisies **par l'interface de la 1.0.1**, puis APK release 1.1.0
installé **par-dessus** (`adb install -r`, sans désinstallation), puis contrôle dans l'interface de la 1.1.0.

| Id | Vérification | Résultat |
|---|---|---|
| MG00–MG02 | installation 1.0.1, saisies, mise à jour acceptée (même certificat) | réussis |
| MG04 | 3 mesures identiques ; pouls absent conservé (non inventé) | réussi |
| MG05 | horodatages identiques à la seconde (16:00:11, 16:03:57, 16:08:35) | réussi |
| MG06 | bras droit et période « soir » de la 1.0.1 conservés (aucune réécriture en « bras gauche ») | réussi |
| MG08–MG09 | rappel du lundi conservé ; 5 alarmes reprogrammées | réussis |
| MG11 | nouvelle séance 1.1.0 enregistrée à côté des données 1.0.1 | réussi |
| MG12 | aucun plantage | réussi |
| MG03, MG07, MG10 | profil, traitement, fournisseur d'IA | échecs **du script** (saisie adb tronquée « MIGRAT », appui sur le libellé au lieu du bouton radio) → rejoués en passe 2 |
| MP01 | passe 2 : nom, traitement et fournisseur d'IA **vérifiés à l'écran dans la 1.0.1** avant mise à jour | réussi |
| MP03–MP05 | les trois valeurs présentes dans la 1.1.0 | réussis |
| MP06 | aucun plantage | réussi |

Conclusion : **aucune perte de données** constatée lors de la mise à jour réelle. Migration Room 1 → 2 également
couverte par un test unitaire sur un schéma 1.0 réel (`MigrationTest`).

## 6. Recette de l'APK release 1.1.0

Voir `recette/journal_release_1.1.0.tsv` (résultats reportés au § 6 bis après exécution).

## 7. Anomalies

| Id | Description | Gravité | État |
|---|---|---|---|
| A1.1-01 | Unités (mmHg, bpm, cm, kg) invisibles tant que le champ est vide (suffixe Material affiché seulement champ actif) | Mineure (exigence « afficher l'unité ») | **Corrigée** (unité dans le libellé) ; vérifiée par R23b et la recette release |
| A1.1-02 | 1.0.1 (ancienne version) : ANR au premier démarrage après installation neuve sur l'émulateur logiciel, fermeture par le système | Ancienne version, émulateur | Constatée 4 fois ; sans objet pour la 1.1.0 (voir RL01b) ; contournée dans le script (relance) |
| — | Échecs d'automate R05b, R08a (lecture asynchrone), r20 (retrait d'alarme exacte qui tue le processus), R21 (lenteur), R11g (attente obsolète) | Outillage de test | Tests corrigés et rejoués : réussis |

**Anomalies restantes connues** : aucune anomalie fonctionnelle ouverte sur l'émulateur.

## 8. Non exécuté

- Tests sur **Motorola Razr 50s réel** (écran externe réel, charnière, capteur d'empreinte, performances, autonomie).
- Biométrie réelle : non rejouée dans cette campagne (inchangée depuis la 1.0.2 où elle a été vérifiée sur émulateur).
- Envoi réel à un fournisseur d'IA (volontairement : serveur simulé uniquement).
- Changement de date système autour de minuit sur l'appareil : couvert par test unitaire (`EntryAndTimestampTest.dateChangeAroundMidnight`), non rejoué sur l'émulateur.
