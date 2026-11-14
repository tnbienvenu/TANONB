package com.cardiosuivi.domain.reference

/**
 * Références bibliographiques utilisées par les règles de l'application.
 * Chaque seuil de [MedicalThresholds] cite au moins une de ces références.
 * Détails, contexte d'application et niveau de vérification : docs/REFERENCES_MEDICALES.md.
 */
enum class MedicalReference(
    val shortName: String,
    val citation: String,
    val year: Int,
    val doiOrUrl: String,
) {
    ESC_2024(
        "ESC 2024",
        "McEvoy JW et al. 2024 ESC Guidelines for the management of elevated blood pressure and hypertension. Eur Heart J. 2024;45(38):3912–4018.",
        2024,
        "doi:10.1093/eurheartj/ehae178",
    ),
    ESH_2023(
        "ESH 2023",
        "Mancia G et al. 2023 ESH Guidelines for the management of arterial hypertension. J Hypertens. 2023;41(12):1874–2071.",
        2023,
        "doi:10.1097/HJH.0000000000003480",
    ),
    ESH_HBPM_2021(
        "ESH 2021 (automesure)",
        "Stergiou GS et al. 2021 European Society of Hypertension practice guidelines for office and out-of-office blood pressure measurement. J Hypertens. 2021;39(7):1293–1302.",
        2021,
        "doi:10.1097/HJH.0000000000002843",
    ),
    AHA_2025(
        "AHA/ACC 2025",
        "Jones DW et al. 2025 AHA/ACC/AANP/AAPA/ABC/ACCP/ACPM/AGS/AMA/ASPC/NMA/PCNA/SGIM Guideline for the Prevention, Detection, Evaluation and Management of High Blood Pressure in Adults. Hypertension. 2025;82:e212–e316.",
        2025,
        "doi:10.1161/HYP.0000000000000249",
    ),
    AHA_CRISIS(
        "AHA – crise hypertensive",
        "American Heart Association. Hypertensive Crisis: When You Should Call 911 for High Blood Pressure (information patients, heart.org).",
        2024,
        "https://www.heart.org/en/health-topics/high-blood-pressure/understanding-blood-pressure-readings/hypertensive-crisis-when-you-should-call-911-for-high-blood-pressure",
    ),
    AHA_LOW_BP(
        "AHA – hypotension",
        "American Heart Association. Low Blood Pressure – When Blood Pressure Is Too Low (information patients, heart.org).",
        2024,
        "https://www.heart.org/en/health-topics/high-blood-pressure/the-facts-about-high-blood-pressure/low-blood-pressure-when-blood-pressure-is-too-low",
    ),
    ACC_AHA_BRADY_2018(
        "ACC/AHA/HRS 2018 (bradycardie)",
        "Kusumoto FM et al. 2018 ACC/AHA/HRS Guideline on the Evaluation and Management of Patients With Bradycardia and Cardiac Conduction Delay. Circulation. 2019;140:e382–e482.",
        2018,
        "doi:10.1161/CIR.0000000000000628",
    ),
    WHO_2021(
        "OMS 2021",
        "World Health Organization. Guideline for the pharmacological treatment of hypertension in adults. Geneva: WHO; 2021.",
        2021,
        "https://www.ncbi.nlm.nih.gov/books/NBK573629/",
    ),
}
