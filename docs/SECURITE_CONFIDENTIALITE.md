# Sécurité, confidentialité et réglementation

## Données et stockage
- **Offline-first** : toutes les données restent sur le téléphone ; aucun compte, aucune synchronisation, **aucune télémétrie**, aucun SDK d'analyse ou de publicité.
- **Base chiffrée** SQLCipher (AES-256). Phrase secrète aléatoire (32 octets) chiffrée par une clé AES-256-GCM **non exportable** de l'Android Keystore, stockée dans `noBackupFilesDir`.
- **Sauvegarde Android désactivée** (`allowBackup=false`, règles d'extraction excluant tout) : aucune donnée de santé dans le cloud Google ni lors d'un transfert d'appareil.
- **Photos** : aucune depuis la 1.1.0 (OCR et caméra supprimés). Les éventuelles photos temporaires laissées par la 1.0.x sont purgées au démarrage.
- **Interprétations IA** (1.1.0) : les réponses reçues d'un fournisseur d'IA sont conservées dans la base chiffrée (date, fournisseur, question, réponse), incluses dans la sauvegarde chiffrée et effacées avec « Supprimer toutes les données ».
- **Fichiers exportés** (PDF/CSV) : cache privé, partagés via FileProvider avec permission temporaire, purgés après 1 h. Un fichier partagé quitte la protection de l'application : l'utilisateur en est averti.

## Accès à l'application
- Verrouillage biométrique facultatif (biométrie forte ou code de l'appareil ; Android 10 : biométrie ou code), au démarrage et après 60 s en arrière-plan.
- **Écran protégé** activé par défaut (`FLAG_SECURE`) : pas de capture d'écran ni d'aperçu dans les applications récentes.

## Sauvegarde chiffrée
Format `.csbk` : en-tête `CSBK` + version + itérations + sel (16 o) + IV (12 o), puis AES-256-GCM avec l'en-tête en données authentifiées. Clé dérivée par **PBKDF2-HMAC-SHA256, 600 000 itérations** (recommandation OWASP), mot de passe ≥ 8 caractères. Toute altération ou mauvais mot de passe est détecté. Clés d'API et consentements **jamais** sauvegardés. La restauration remplace l'ensemble des données après confirmation.

## Intelligence artificielle
- Désactivée par défaut ; l'analyse locale et les alertes fonctionnent sans elle.
- **Consentement explicite** et horodaté par fournisseur avant toute transmission ; révocable.
- Données transmises : statistiques agrégées et dernières séances ; **jamais** le nom ni la date de naissance ; âge, sexe, traitements, antécédents uniquement si l'utilisateur l'autorise. Aperçu exact disponible (« Données transmises »).
- **Garde-fou local** : les demandes de modification de traitement ou de posologie reçoivent un refus local, sans aucun envoi. Le prompt système impose la structure en 6 sections, interdit tout diagnostic définitif et toute prescription.
- Clés d'API chiffrées par le Keystore, jamais affichées ni journalisées ; trafic HTTPS uniquement (`cleartextTrafficPermitted=false`).

## Autorisations Android
| Autorisation | Usage |
|---|---|
| `POST_NOTIFICATIONS` | rappels de séance |
| `SCHEDULE_EXACT_ALARM` | rappels à l'heure exacte (accordée par l'utilisateur ; repli sur une fenêtre de 10 min) |
| `RECEIVE_BOOT_COMPLETED` | rétablir les rappels après redémarrage |
| `INTERNET` | uniquement pour l'assistant IA, après consentement |
| `USE_BIOMETRIC`, `VIBRATE` | verrouillage, signal de fin de chronomètre |

Aucune autorisation de localisation, contacts, stockage partagé ou téléphone (l'appel des secours ouvre le composeur, sans `CALL_PHONE`).

## Réglementation — à examiner avant toute diffusion publique
L'usage personnel ne pose pas les mêmes questions qu'une diffusion. Avant publication (Play Store ou autre), faire examiner notamment :
- **Statut de dispositif médical** : un logiciel qui interprète des mesures physiologiques et émet des alertes peut relever du règlement (UE) 2017/745 (MDR, règle 11 pour les logiciels) ; aux États-Unis, du cadre FDA « Software as a Medical Device ». L'application est présentée comme outil personnel d'aide au suivi, mais la qualification dépend de la destination revendiquée et des fonctions ; un avis réglementaire est nécessaire.
- **RGPD** : données de santé = catégorie particulière (art. 9) ; base légale (consentement explicite), information, droits (export, suppression : disponibles), analyse d'impact (AIPD) si traitement à grande échelle, transferts hors UE vers les fournisseurs d'IA (clauses contractuelles, politique de conservation des fournisseurs).
- **Play Store** : politique « Health apps », formulaire « Sécurité des données », justification de `SCHEDULE_EXACT_ALARM`, politique de confidentialité publique.
- **Fournisseurs d'IA** : conditions d'utilisation relatives aux données de santé et aux usages médicaux.
