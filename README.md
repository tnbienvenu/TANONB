# CardioSuivi

Application Android native (Kotlin, Jetpack Compose, Material 3) de **suivi personnel de la tension artérielle à domicile** : saisie manuelle guidée des valeurs affichées par un tensiomètre électronique (SYS, DIA, pouls), date et heure enregistrées automatiquement.

> Version **1.1.0** : nouvelle interface, OCR supprimé (saisie manuelle uniquement), bras gauche et moment de la journée enregistrés automatiquement. Voir [docs/CARDIOSUIVI_1.1.0.md](docs/CARDIOSUIVI_1.1.0.md).

> CardioSuivi est un outil personnel d'aide au suivi. Il **ne remplace pas un médecin**, ne pose **aucun diagnostic**, ne modifie **jamais** un traitement et n'est pas un dispositif médical certifié. En cas de douleur thoracique, d'essoufflement, de faiblesse d'un côté du corps, de trouble de la parole ou de la vision : appelez immédiatement les secours (112 ; 15 en France).

## Fonctions

| Domaine | Contenu |
|---|---|
| Séances | Protocole de 3 mesures (2 à 3 configurables), recommandations de mesure, repos chronométré de 5 min, intervalle configurable (1 min par défaut), **saisie SYS / DIA / PUL obligatoire** (entiers, contrôles de validité), horodatage automatique (seconde, fuseau), bras gauche et matin/soir automatiques, retour à la mesure précédente, correction, séance incomplète, reprise d'une séance interrompue |
| Rappels | Mercredi/jeudi/vendredi à 07:00 et 19:00 (valeurs initiales modifiables), Commencer / Reporter 10-15-30 min / Ignorer, alarmes exactes si autorisées, reprise après redémarrage, séances manquées, diagnostic des autorisations |
| Statistiques | Séance (moyenne des 3, des 2 dernières, min, max, amplitude, écart-type, évolution), jour, semaine, mois, 7/30/90 jours, période personnalisée ; matin/soir, jours de la semaine, semaines et mois successifs ; effectifs et données manquantes ; tendance, variabilité (SD, CV, ARV) |
| Analyse | Moteur local déterministe, seuils référencés (ESC 2024, ESH 2023, AHA/ACC 2025, OMS 2021), constats, points à discuter, limites ; protocole d'automesure standardisée sur 7 jours |
| Alertes | Locales, hors ligne, immédiates : vérification de mesure, objectif dépassé, valeur très élevée, symptômes d'urgence → appel des secours |
| IA | Assistant local (6 sections imposées) ; Anthropic Claude ou OpenAI en option, après consentement explicite, avec garde-fou anti-prescription et clé chiffrée |
| Rapports | PDF (14 sections, graphiques), CSV ; partage Android (WhatsApp, e-mail…) |
| Sécurité | Base SQLCipher (AES-256), clé dans l'Android Keystore, verrouillage biométrique, écran protégé, sauvegarde chiffrée par mot de passe, suppression des données, aucune télémétrie |

## Démarrage rapide

- **Installer l'APK** : voir [docs/INSTALLATION.md](docs/INSTALLATION.md).
- **Compiler** : JDK 17+ et Android SDK 36, puis `./gradlew assembleRelease` (ou `assembleDebug`).
- **Tester** : `./gradlew :domain:test :app:testDebugUnitTest` (JVM) ; `./gradlew connectedDebugAndroidTest` (appareil).

## Documentation

- [Installation](docs/INSTALLATION.md)
- [Architecture technique](docs/ARCHITECTURE.md)
- [Références médicales et seuils](docs/REFERENCES_MEDICALES.md)
- [Sécurité, confidentialité, réglementation](docs/SECURITE_CONFIDENTIALITE.md)
- [Tests et état de validation](docs/TESTS.md)
