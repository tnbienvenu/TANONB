# Références médicales et seuils

Recherche documentaire réalisée en novembre 2026. Les seuils sont codés dans `domain/src/main/kotlin/com/cardiosuivi/domain/reference/` ; **aucun seuil n'est utilisé sans référence**. Niveau de vérification indiqué : **[P]** source primaire ou communiqué officiel de la société savante ; **[S]** sources secondaires concordantes (résumés, commentaires de sociétés nationales). Les DOI et URL sont à revérifier par un professionnel de santé avant toute diffusion publique.

## Recommandations

| Réf. | Publication | Vérification |
|---|---|---|
| ESC 2024 | McEvoy JW et al. *2024 ESC Guidelines for the management of elevated blood pressure and hypertension*. Eur Heart J 2024;45:3912–4018. doi:10.1093/eurheartj/ehae178 | [P] communiqué ESC ; [S] tableau des seuils hors cabinet (commentaire DGK, Rev Esp Cardiol) |
| ESH 2023 | Mancia G et al. *2023 ESH Guidelines for the management of arterial hypertension*. J Hypertens 2023;41:1874–2071. doi:10.1097/HJH.0000000000003480 | [S] classification par grades, protocole d'automesure |
| ESH 2021 | Stergiou GS et al. *2021 ESH practice guidelines for office and out-of-office blood pressure measurement*. J Hypertens 2021;39:1293–1302. doi:10.1097/HJH.0000000000002843 | [S] 3–7 jours, matin et soir, seuil 135/85 |
| AHA/ACC 2025 | Jones DW et al. *2025 AHA/ACC/… Guideline for the Prevention, Detection, Evaluation and Management of High Blood Pressure in Adults*. Hypertension 2025;82:e212–e316. doi:10.1161/HYP.0000000000000249 | [P] communiqué ACC (catégories inchangées depuis 2017) |
| AHA – crise | American Heart Association, *Hypertensive Crisis: When You Should Call 911* (information patients) | [S] repris par Mayo Clinic et des sites hospitaliers ; page heart.org non consultable depuis l'environnement de développement |
| AHA – hypotension | American Heart Association, *Low Blood Pressure* (information patients) | repère usuel < 90/60 mmHg, à confirmer |
| ACC/AHA/HRS 2018 | Kusumoto FM et al. *Guideline on the Evaluation and Management of Patients With Bradycardia*. Circulation 2019;140:e382–e482 | définition de la bradycardie < 50/min |
| OMS 2021 | WHO. *Guideline for the pharmacological treatment of hypertension in adults*. Genève, 2021 (NCBI Bookshelf NBK573629) | [S] seuil de traitement ≥ 140/90 ; cible < 140/90, < 130 systolique si maladie cardiovasculaire |

## Seuils appliqués et contexte

### Mesure à domicile (appliqués aux moyennes de l'utilisateur)
| Repère | Valeur | Contexte | Réf. |
|---|---|---|---|
| Pression non élevée | < 120/70 mmHg | moyenne d'automesure | ESC 2024 |
| Pression élevée | 120/70 à < 135/85 mmHg | moyenne d'automesure | ESC 2024 |
| **Seuil d'hypertension à domicile** | **≥ 135 et/ou ≥ 85 mmHg** | **moyenne sur plusieurs jours**, jamais une mesure isolée | ESC 2024, ESH 2023, ESH 2021 |
| Données suffisantes | ≥ 3 jours (idéalement 7) | avant toute interprétation d'une moyenne | ESH 2023, ESC 2024 |

### Consultation (information uniquement, non appliqué aux mesures à domicile)
- Hypertension au cabinet ≥ 140/90 mmHg (ESC 2024, ESH 2023, OMS 2021) ; catégorie « élevée » 120–139/70–89 (ESC 2024).
- AHA/ACC 2025 : normale < 120/80 ; élevée 120–129/< 80 ; stade 1 130–139 ou 80–89 ; stade 2 ≥ 140 ou ≥ 90.

### Objectifs thérapeutiques (fixés par le médecin, saisis dans le profil)
- ESC 2024 : systolique au cabinet 120–129 mmHg chez la plupart des patients traités **si bien toléré** ; principe « aussi bas que raisonnablement possible » chez les personnes fragiles, ≥ 85 ans ou avec hypotension orthostatique symptomatique.
- OMS 2021 : < 140/90 mmHg sans comorbidité ; systolique < 130 mmHg en cas de maladie cardiovasculaire connue.
- L'application n'impose aucun objectif : sans objectif personnel, elle se réfère au seuil à domicile de 135/85 et invite à renseigner l'objectif défini avec le médecin.

### Valeurs très élevées et urgence
| Situation | Règle | Réf. |
|---|---|---|
| Grade 2 (repère) | 160–179 et/ou 100–109 → en parler au médecin si cela se répète | ESH 2023 |
| Très élevée, sans symptôme | ≥ 180 et/ou ≥ 110 → repos 5 min, nouvelle mesure, contact médical rapide si confirmé | ESH 2023 (grade 3), AHA (≥ 180/120) |
| Très élevée **avec** symptômes | → **appel immédiat des secours** | AHA – crise |
| Symptômes évocateurs (douleur thoracique, essoufflement, faiblesse/engourdissement unilatéral, trouble de la parole ou de la vision, confusion, céphalée brutale, malaise, douleur dorsale intense) | → **appel immédiat des secours, quelle que soit la tension** | AHA (crise hypertensive, AVC, infarctus) |

Le seuil ESH ≥ 180/110 est retenu (plus prudent que ≥ 180/120) pour déclencher la recommandation de nouvelle mesure et de contact médical.

### Autres
- Hypotension : < 90/60 mmHg ; sans symptôme, simple information ; avec vertiges ou malaise → contact médical (AHA).
- Fréquence cardiaque : < 50/min (bradycardie, ACC/AHA/HRS 2018), > 100/min au repos (tachycardie) ; avec symptômes → contact médical rapide.
- Écart > 10 mmHg entre deux mesures successives d'une séance → vérifier la technique et faire une mesure supplémentaire (ESH 2021/2023).

## Protocoles

- **Calendrier habituel** (utilisateur) : mercredi, jeudi, vendredi ; matin et soir ; 3 mesures à 1 min d'intervalle. Les horaires 07:00 / 19:00 sont des valeurs initiales, pas des recommandations médicales.
- **Automesure standardisée** (ESH 2023/2021, ESC 2024) : 7 jours consécutifs (minimum 3), 2 mesures le matin et 2 le soir à 1–2 min d'intervalle, mesures du premier jour écartées, moyenne de toutes les mesures restantes comparée à 135/85 mmHg.

## Bornes de plausibilité (techniques, non médicales)
SYS 60–260, DIA 30–160, pouls 30–220 : servent uniquement à détecter une erreur de lecture ou de frappe ; une valeur hors bornes reste enregistrable après confirmation explicite.

## Hygiène de vie (conseils généraux affichés)
Réduction du sel (OMS : < 5 g/jour), alimentation de type méditerranéen/DASH, activité physique (OMS : ≥ 150 min/semaine d'intensité modérée), poids adapté, limitation de l'alcool, arrêt du tabac, sommeil, observance du traitement prescrit.
