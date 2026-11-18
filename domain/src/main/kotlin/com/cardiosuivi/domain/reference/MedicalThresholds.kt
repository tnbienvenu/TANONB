package com.cardiosuivi.domain.reference

/**
 * Seuils utilisés par le moteur d'alertes et d'analyse. AUCUN seuil n'est utilisé sans référence.
 *
 * Distinction importante :
 * - seuils de mesure À DOMICILE (automesure) : utilisés pour interpréter les moyennes de l'utilisateur ;
 * - seuils de CONSULTATION (cabinet) : donnés à titre d'information, non appliqués aux mesures à domicile ;
 * - OBJECTIFS THÉRAPEUTIQUES : fixés par le médecin, saisis par l'utilisateur dans son profil ;
 * - situations d'URGENCE : valeurs très élevées, a fortiori avec symptômes.
 */
data class Threshold(
    val id: String,
    val label: String,
    val systolic: Int?,
    val diastolic: Int?,
    val context: ThresholdContext,
    val references: List<MedicalReference>,
    val note: String,
)

enum class ThresholdContext(val label: String) {
    HOME("Mesure à domicile"),
    OFFICE("Mesure en consultation"),
    SEVERE("Valeur très élevée"),
    LOW("Valeur basse"),
    HEART_RATE("Fréquence cardiaque"),
    MEASUREMENT_TECHNIQUE("Technique de mesure"),
}

object MedicalThresholds {

    /** Automesure : pression « non élevée » < 120/70 mmHg (ESC 2024, tableau des seuils hors cabinet). */
    val HOME_NON_ELEVATED_UPPER = Threshold(
        "home_non_elevated", "Automesure non élevée", 120, 70, ThresholdContext.HOME,
        listOf(MedicalReference.ESC_2024),
        "Moyenne d'automesure < 120/70 mmHg : pression non élevée.",
    )

    /** Automesure : hypertension si moyenne ≥ 135 et/ou ≥ 85 mmHg (ESC 2024, ESH 2023, ESH 2021). */
    val HOME_HYPERTENSION = Threshold(
        "home_hypertension", "Seuil d'hypertension à domicile", 135, 85, ThresholdContext.HOME,
        listOf(MedicalReference.ESC_2024, MedicalReference.ESH_2023, MedicalReference.ESH_HBPM_2021),
        "S'applique à la MOYENNE d'une série d'automesures sur plusieurs jours, pas à une mesure isolée.",
    )

    /** Consultation : hypertension ≥ 140/90 mmHg (ESC 2024, ESH 2023, OMS 2021) — information uniquement. */
    val OFFICE_HYPERTENSION = Threshold(
        "office_hypertension", "Seuil d'hypertension en consultation", 140, 90, ThresholdContext.OFFICE,
        listOf(MedicalReference.ESC_2024, MedicalReference.ESH_2023, MedicalReference.WHO_2021),
        "Seuil de mesure au cabinet, non applicable directement aux mesures à domicile.",
    )

    /** Grade 2 (ESH 2023) : 160–179 et/ou 100–109 mmHg. */
    val GRADE_2 = Threshold(
        "grade2", "Valeur nettement élevée", 160, 100, ThresholdContext.SEVERE,
        listOf(MedicalReference.ESH_2023),
        "Classification ESH (mesures de consultation) ; utilisée ici comme repère pour recommander un avis médical si la valeur se confirme.",
    )

    /**
     * Grade 3 (ESH 2023) : ≥ 180 et/ou ≥ 110 mmHg. Retenu (plus prudent que le repère AHA ≥ 180/120)
     * pour recommander une nouvelle mesure après repos puis un contact médical rapide.
     */
    val SEVERE = Threshold(
        "severe", "Valeur très élevée", 180, 110, ThresholdContext.SEVERE,
        listOf(MedicalReference.ESH_2023, MedicalReference.AHA_CRISIS),
        "AHA : ≥ 180/120 mmHg = crise hypertensive ; avec douleur thoracique, essoufflement ou signes d'AVC : appeler les urgences.",
    )

    /** Hypotension : < 90/60 mmHg (AHA, information patients). Préoccupante surtout en présence de symptômes. */
    val LOW_BP = Threshold(
        "low_bp", "Pression basse", 90, 60, ThresholdContext.LOW,
        listOf(MedicalReference.AHA_LOW_BP),
        "Une pression basse sans symptôme n'est pas forcément anormale ; avec malaise ou vertiges, demander un avis.",
    )

    /** Bradycardie : fréquence < 50/min (ACC/AHA/HRS 2018). */
    const val BRADYCARDIA_BPM = 50

    /** Tachycardie : fréquence au repos > 100/min (définition usuelle, ACC/AHA/HRS 2018). */
    const val TACHYCARDIA_BPM = 100

    /**
     * Écart entre deux mesures successives d'une même séance au-delà duquel une mesure supplémentaire est conseillée
     * (> 10 mmHg ; ESH 2021 / ESH 2023, protocole de mesure).
     */
    const val INTRA_SESSION_DIFF_MMHG = 10

    /** Automesure standardisée : nombre de jours recommandé (7, minimum 3), mesures en double matin et soir, 1er jour écarté. */
    const val HBPM_RECOMMENDED_DAYS = 7
    const val HBPM_MIN_DAYS = 3

    val all: List<Threshold> = listOf(
        HOME_NON_ELEVATED_UPPER, HOME_HYPERTENSION, OFFICE_HYPERTENSION, GRADE_2, SEVERE, LOW_BP,
    )
}

/**
 * Bornes de PLAUSIBILITÉ TECHNIQUE (et non médicales) pour la validation de la saisie :
 * elles servent uniquement à détecter une erreur de frappe.
 * Une valeur hors bornes reste enregistrable après confirmation explicite.
 */
object PlausibilityBounds {
    val SYSTOLIC = 60..260
    val DIASTOLIC = 30..160
    val PULSE = 30..220
}
