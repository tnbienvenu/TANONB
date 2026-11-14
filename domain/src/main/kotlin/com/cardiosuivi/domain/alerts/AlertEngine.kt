package com.cardiosuivi.domain.alerts

import com.cardiosuivi.domain.model.BpTarget
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.Symptom
import com.cardiosuivi.domain.reference.MedicalReference
import com.cardiosuivi.domain.reference.MedicalThresholds
import com.cardiosuivi.domain.reference.PlausibilityBounds
import com.cardiosuivi.domain.stats.PeriodReport
import com.cardiosuivi.domain.stats.SessionStats
import com.cardiosuivi.domain.util.Fmt

/** Niveaux d'alerte, du moins au plus grave. */
enum class AlertLevel(val label: String) {
    INFO("Information"),
    CHECK_MEASUREMENT("Vérifier la mesure"),
    ATTENTION("À surveiller"),
    CONSULT("À discuter avec un professionnel de santé"),
    CONSULT_SOON("Contacter rapidement un professionnel de santé"),
    URGENT("Urgence : appeler les secours"),
}

data class Alert(
    val level: AlertLevel,
    val code: String,
    val title: String,
    val message: String,
    val references: List<MedicalReference> = emptyList(),
)

/**
 * Moteur d'alertes local, déterministe, indépendant de toute IA et de toute connexion Internet.
 *
 * Il ne pose pas de diagnostic : il signale des valeurs, demande des vérifications de mesure
 * et oriente vers un professionnel de santé ou les services d'urgence. En cas de doute,
 * l'utilisateur doit toujours contacter un professionnel de santé ; l'application ne doit jamais
 * retarder une prise en charge.
 */
class AlertEngine(
    /** Numéro des services d'urgence affiché (configurable ; 112 par défaut, numéro d'urgence européen). */
    private val emergencyNumber: String = "112",
) {

    private val urgentAdvice: String
        get() = "Appelez immédiatement les services d'urgence ($emergencyNumber). " +
            "Ne conduisez pas vous-même. Cette recommandation prime sur toute autre information de l'application."

    /** Évalue les symptômes déclarés, indépendamment des valeurs de tension. */
    fun evaluateSymptoms(symptoms: Set<Symptom>): List<Alert> {
        val urgent = symptoms.filter { it.urgent }
        val out = mutableListOf<Alert>()
        if (urgent.isNotEmpty()) {
            out += Alert(
                AlertLevel.URGENT, "urgent_symptoms",
                "Symptômes pouvant évoquer une urgence",
                "Vous avez signalé : ${urgent.joinToString { it.label.lowercase() }}. $urgentAdvice",
                listOf(MedicalReference.AHA_CRISIS),
            )
        } else if (symptoms.isNotEmpty()) {
            out += Alert(
                AlertLevel.CONSULT, "symptoms",
                "Symptômes signalés",
                "Vous avez signalé : ${symptoms.joinToString { it.label.lowercase() }}. " +
                    "Si ces symptômes persistent, s'aggravent ou vous inquiètent, contactez un professionnel de santé.",
            )
        }
        return out
    }

    /** Vérifications de cohérence d'une mesure saisie (erreur de lecture ou de frappe possible). */
    fun checkPlausibility(systolic: Int, diastolic: Int, pulse: Int?): List<Alert> {
        val out = mutableListOf<Alert>()
        if (systolic <= diastolic) {
            out += Alert(
                AlertLevel.CHECK_MEASUREMENT, "sys_le_dia", "Valeurs incohérentes",
                "La systolique ($systolic) doit être supérieure à la diastolique ($diastolic). Vérifiez que les champs ne sont pas inversés.",
            )
        }
        if (systolic !in PlausibilityBounds.SYSTOLIC) {
            out += Alert(AlertLevel.CHECK_MEASUREMENT, "sys_bounds", "Systolique inhabituelle",
                "La valeur systolique $systolic mmHg est hors de la plage de saisie attendue. Vérifiez la lecture de l'écran.")
        }
        if (diastolic !in PlausibilityBounds.DIASTOLIC) {
            out += Alert(AlertLevel.CHECK_MEASUREMENT, "dia_bounds", "Diastolique inhabituelle",
                "La valeur diastolique $diastolic mmHg est hors de la plage de saisie attendue. Vérifiez la lecture de l'écran.")
        }
        if (pulse != null && pulse !in PlausibilityBounds.PULSE) {
            out += Alert(AlertLevel.CHECK_MEASUREMENT, "pulse_bounds", "Pouls inhabituel",
                "La fréquence cardiaque $pulse/min est hors de la plage de saisie attendue. Vérifiez la lecture de l'écran.")
        }
        return out
    }

    /**
     * Évaluation immédiate d'une mesure individuelle, appelée dès sa validation pendant la séance
     * afin de ne jamais retarder une recommandation urgente.
     */
    fun evaluateMeasurement(systolic: Int, diastolic: Int, pulse: Int?, symptoms: Set<Symptom>): List<Alert> {
        val out = mutableListOf<Alert>()
        out += checkPlausibility(systolic, diastolic, pulse)
        val severe = isSevere(systolic.toDouble(), diastolic.toDouble())
        val hasUrgentSymptom = symptoms.any { it.urgent }
        if (severe && hasUrgentSymptom) {
            out += Alert(
                AlertLevel.URGENT, "severe_with_symptoms",
                "Tension très élevée avec symptômes",
                "Mesure à $systolic/$diastolic mmHg avec symptômes. $urgentAdvice",
                listOf(MedicalReference.AHA_CRISIS, MedicalReference.ESH_2023),
            )
        } else if (severe) {
            out += Alert(
                AlertLevel.CONSULT_SOON, "severe_single",
                "Tension très élevée",
                "Mesure à $systolic/$diastolic mmHg (≥ ${MedicalThresholds.SEVERE.systolic}/${MedicalThresholds.SEVERE.diastolic}). " +
                    "Restez assis au calme 5 minutes puis refaites une mesure. Si la valeur reste aussi élevée, contactez rapidement " +
                    "votre médecin ou un service de garde. En cas de douleur thoracique, d'essoufflement, de faiblesse d'un côté du corps, " +
                    "de trouble de la parole ou de la vision : appelez les secours ($emergencyNumber).",
                listOf(MedicalReference.ESH_2023, MedicalReference.AHA_CRISIS),
            )
        }
        out += evaluateSymptoms(symptoms)
        if (isLow(systolic.toDouble(), diastolic.toDouble()) && symptoms.any { it == Symptom.DIZZINESS || it == Symptom.FAINTING }) {
            out += Alert(
                AlertLevel.CONSULT_SOON, "low_with_symptoms", "Tension basse avec malaise",
                "Mesure à $systolic/$diastolic mmHg avec vertiges ou malaise. Allongez-vous ou asseyez-vous et contactez un professionnel de santé. " +
                    "En cas de perte de connaissance : appelez les secours ($emergencyNumber).",
                listOf(MedicalReference.AHA_LOW_BP),
            )
        }
        if (pulse != null) out += evaluatePulse(pulse.toDouble(), symptoms, single = true)
        return out.distinctBy { it.code }
    }

    /** Évaluation d'une séance validée. */
    fun evaluateSession(session: MeasurementSession, stats: SessionStats, target: BpTarget?): List<Alert> {
        val out = mutableListOf<Alert>()
        out += evaluateSymptoms(session.symptoms)
        val p = stats.primary ?: return out
        val sys = p.systolic
        val dia = p.diastolic
        val urgentSymptom = session.symptoms.any { it.urgent }

        // Écart important entre mesures successives → technique de mesure / mesure supplémentaire.
        val maxStep = (stats.systolicDeltas + stats.diastolicDeltas).maxOfOrNull { kotlin.math.abs(it) }
        if (maxStep != null && maxStep > MedicalThresholds.INTRA_SESSION_DIFF_MMHG) {
            out += Alert(
                AlertLevel.CHECK_MEASUREMENT, "intra_diff",
                "Mesures successives peu concordantes",
                "Deux mesures successives diffèrent de $maxStep mmHg (> ${MedicalThresholds.INTRA_SESSION_DIFF_MMHG} mmHg). " +
                    "Vérifiez la position (assis, dos appuyé, bras à hauteur du cœur), le brassard, et envisagez une mesure supplémentaire.",
                listOf(MedicalReference.ESH_HBPM_2021, MedicalReference.ESH_2023),
            )
        }
        when {
            isSevere(sys, dia) && urgentSymptom -> out += Alert(
                AlertLevel.URGENT, "session_severe_symptoms", "Tension très élevée avec symptômes",
                "Moyenne de séance ${Fmt.bp(sys, dia)} mmHg avec symptômes. $urgentAdvice",
                listOf(MedicalReference.AHA_CRISIS),
            )
            isSevere(sys, dia) -> out += Alert(
                AlertLevel.CONSULT_SOON, "session_severe", "Moyenne de séance très élevée",
                "Moyenne de séance ${Fmt.bp(sys, dia)} mmHg (≥ ${MedicalThresholds.SEVERE.systolic}/${MedicalThresholds.SEVERE.diastolic}). " +
                    "Contactez rapidement votre médecin ou un service de garde. En cas de symptômes, appelez les secours ($emergencyNumber).",
                listOf(MedicalReference.ESH_2023, MedicalReference.AHA_CRISIS),
            )
            sys >= MedicalThresholds.GRADE_2.systolic!! || dia >= MedicalThresholds.GRADE_2.diastolic!! -> out += Alert(
                AlertLevel.CONSULT, "session_grade2", "Moyenne de séance nettement élevée",
                "Moyenne de séance ${Fmt.bp(sys, dia)} mmHg. Si ces valeurs se répètent, parlez-en à votre médecin dans les prochains jours.",
                listOf(MedicalReference.ESH_2023),
            )
            target != null && (sys >= target.systolic || dia >= target.diastolic) -> out += Alert(
                AlertLevel.ATTENTION, "session_above_target", "Au-dessus de votre objectif",
                "Moyenne de séance ${Fmt.bp(sys, dia)} mmHg, objectif personnel < ${target.systolic}/${target.diastolic} mmHg. " +
                    "Une séance isolée ne suffit pas à conclure ; c'est la moyenne sur plusieurs jours qui compte.",
            )
            target == null && (sys >= MedicalThresholds.HOME_HYPERTENSION.systolic!! || dia >= MedicalThresholds.HOME_HYPERTENSION.diastolic!!) -> out += Alert(
                AlertLevel.ATTENTION, "session_above_home_threshold", "Au-dessus du seuil de référence à domicile",
                "Moyenne de séance ${Fmt.bp(sys, dia)} mmHg ; seuil de référence à domicile ${MedicalThresholds.HOME_HYPERTENSION.systolic}/${MedicalThresholds.HOME_HYPERTENSION.diastolic} mmHg " +
                    "(s'applique à une moyenne sur plusieurs jours). Renseignez votre objectif personnel défini avec votre médecin dans le profil.",
                MedicalThresholds.HOME_HYPERTENSION.references,
            )
        }
        if (isLow(sys, dia)) {
            val withSymptoms = session.symptoms.any { it == Symptom.DIZZINESS || it == Symptom.FAINTING }
            out += Alert(
                if (withSymptoms) AlertLevel.CONSULT_SOON else AlertLevel.INFO, "session_low", "Tension basse",
                "Moyenne de séance ${Fmt.bp(sys, dia)} mmHg (< ${MedicalThresholds.LOW_BP.systolic}/${MedicalThresholds.LOW_BP.diastolic}). " +
                    if (withSymptoms) "Avec vertiges ou malaise, contactez un professionnel de santé."
                    else "Sans symptôme, ce n'est pas forcément anormal ; signalez-le lors de votre prochaine consultation.",
                listOf(MedicalReference.AHA_LOW_BP),
            )
        }
        p.pulse?.let { out += evaluatePulse(it, session.symptoms, single = false) }
        return out.distinctBy { it.code }
    }

    /** Évaluation d'une période (ex. 7 jours) à partir de la moyenne des valeurs de séance. */
    fun evaluatePeriod(report: PeriodReport, target: BpTarget?): List<Alert> {
        val out = mutableListOf<Alert>()
        val sys = report.overall.systolic.mean
        val dia = report.overall.diastolic.mean
        if (sys == null || dia == null) {
            out += Alert(AlertLevel.INFO, "no_data", "Pas de données", "Aucune séance validée sur la période.")
            return out
        }
        if (report.daysWithData < MedicalThresholds.HBPM_MIN_DAYS) {
            out += Alert(
                AlertLevel.INFO, "insufficient_days", "Données insuffisantes pour conclure",
                "Seulement ${report.daysWithData} jour(s) de mesures. Les recommandations s'appuient sur au moins " +
                    "${MedicalThresholds.HBPM_MIN_DAYS} jours (idéalement ${MedicalThresholds.HBPM_RECOMMENDED_DAYS}).",
                listOf(MedicalReference.ESH_2023, MedicalReference.ESC_2024),
            )
            return out
        }
        val home = MedicalThresholds.HOME_HYPERTENSION
        if (sys >= home.systolic!! || dia >= home.diastolic!!) {
            out += Alert(
                AlertLevel.CONSULT, "period_above_home_threshold", "Moyenne au-dessus du seuil à domicile",
                "Moyenne ${Fmt.bp(sys, dia)} mmHg sur ${report.sessionCount} séances et ${report.daysWithData} jours " +
                    "(seuil de référence à domicile ${home.systolic}/${home.diastolic} mmHg). À discuter avec votre médecin.",
                home.references,
            )
        }
        if (target != null && (sys >= target.systolic || dia >= target.diastolic)) {
            out += Alert(
                AlertLevel.ATTENTION, "period_above_target", "Moyenne au-dessus de votre objectif",
                "Moyenne ${Fmt.bp(sys, dia)} mmHg ; objectif personnel < ${target.systolic}/${target.diastolic} mmHg.",
            )
        }
        report.completeness?.let { c ->
            val rate = c.completionRate
            if (rate != null && rate < 0.5 && c.missedSlots >= 2) {
                out += Alert(
                    AlertLevel.INFO, "low_completeness", "Suivi incomplet",
                    "${c.missedSlots} séance(s) programmée(s) non réalisée(s) sur la période ; l'interprétation est moins fiable.",
                )
            }
        }
        return out
    }

    private fun evaluatePulse(pulse: Double, symptoms: Set<Symptom>, single: Boolean): List<Alert> {
        val out = mutableListOf<Alert>()
        val what = if (single) "Fréquence cardiaque" else "Fréquence cardiaque moyenne"
        val concerning = symptoms.any { it == Symptom.FAINTING || it == Symptom.CHEST_PAIN || it == Symptom.SHORTNESS_OF_BREATH || it == Symptom.PALPITATIONS || it == Symptom.DIZZINESS }
        if (pulse < MedicalThresholds.BRADYCARDIA_BPM) {
            out += Alert(
                if (concerning) AlertLevel.CONSULT_SOON else AlertLevel.INFO, "pulse_low", "Fréquence cardiaque basse",
                "$what ${Fmt.d0(pulse)}/min (< ${MedicalThresholds.BRADYCARDIA_BPM}). " +
                    if (concerning) "Avec des symptômes, contactez rapidement un professionnel de santé."
                    else "Peut être normal chez certaines personnes ou sous certains traitements ; à signaler à votre médecin.",
                listOf(MedicalReference.ACC_AHA_BRADY_2018),
            )
        } else if (pulse > MedicalThresholds.TACHYCARDIA_BPM) {
            out += Alert(
                if (concerning) AlertLevel.CONSULT_SOON else AlertLevel.ATTENTION, "pulse_high", "Fréquence cardiaque élevée au repos",
                "$what ${Fmt.d0(pulse)}/min (> ${MedicalThresholds.TACHYCARDIA_BPM}). " +
                    if (concerning) "Avec des symptômes, contactez rapidement un professionnel de santé."
                    else "Vérifiez que vous étiez au repos depuis 5 minutes ; si cela se répète, parlez-en à votre médecin.",
                listOf(MedicalReference.ACC_AHA_BRADY_2018),
            )
        }
        return out
    }

    companion object {
        fun isSevere(sys: Double, dia: Double): Boolean =
            sys >= MedicalThresholds.SEVERE.systolic!! || dia >= MedicalThresholds.SEVERE.diastolic!!

        fun isLow(sys: Double, dia: Double): Boolean =
            sys < MedicalThresholds.LOW_BP.systolic!! || dia < MedicalThresholds.LOW_BP.diastolic!!

        /** Niveau le plus grave d'une liste d'alertes. */
        fun highest(alerts: List<Alert>): AlertLevel? = alerts.maxByOrNull { it.level.ordinal }?.level
    }
}
