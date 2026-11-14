package com.cardiosuivi.domain.analysis

import com.cardiosuivi.domain.alerts.Alert
import com.cardiosuivi.domain.alerts.AlertEngine
import com.cardiosuivi.domain.model.AggregationMethod
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.Profile
import com.cardiosuivi.domain.model.ReminderConfig
import com.cardiosuivi.domain.reference.MedicalReference
import com.cardiosuivi.domain.reference.MedicalThresholds
import com.cardiosuivi.domain.stats.PeriodReport
import com.cardiosuivi.domain.stats.PeriodStatistics
import com.cardiosuivi.domain.stats.SessionStatistics
import com.cardiosuivi.domain.stats.SessionStats
import com.cardiosuivi.domain.util.Fmt
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.abs

enum class FindingSeverity { NEUTRAL, FAVORABLE, WATCH, DISCUSS, IMPORTANT }

data class Finding(
    val title: String,
    val text: String,
    val severity: FindingSeverity,
    val references: List<MedicalReference> = emptyList(),
)

data class AnalysisReport(
    val generatedAt: ZonedDateTime,
    val method: AggregationMethod,
    val last7: PeriodReport,
    val last30: PeriodReport,
    val last90: PeriodReport,
    val custom: PeriodReport?,
    val lastSession: MeasurementSession?,
    val lastSessionStats: SessionStats?,
    val findings: List<Finding>,
    val pointsToDiscuss: List<String>,
    val limitations: List<String>,
    val alerts: List<Alert>,
)

/**
 * Moteur d'analyse local, déterministe et reproductible. Il produit des constats chiffrés et prudents ;
 * il ne remplace pas l'interprétation d'un médecin et ne pose aucun diagnostic.
 */
class CardioAnalysisEngine(private val alertEngine: AlertEngine = AlertEngine()) {

    /** Seuil (mmHg) en deçà duquel une différence moyenne matin/soir est considérée comme faible (choix d'affichage de l'application). */
    private val smallDiffMmHg = 5.0
    /** Pente (mmHg/semaine) en deçà de laquelle la tendance est qualifiée de stable (choix d'affichage de l'application). */
    private val stableSlope = 1.0

    fun analyze(
        sessions: List<MeasurementSession>,
        profile: Profile,
        method: AggregationMethod,
        reminderConfig: ReminderConfig?,
        now: ZonedDateTime,
        customRange: Pair<LocalDate, LocalDate>? = null,
    ): AnalysisReport {
        val today = now.toLocalDate()
        fun period(days: Long) = PeriodStatistics.compute(sessions, today.minusDays(days - 1), today, method, profile.target, reminderConfig, now)
        val r7 = period(7)
        val r30 = period(30)
        val r90 = period(90)
        val custom = customRange?.let { PeriodStatistics.compute(sessions, it.first, it.second, method, profile.target, reminderConfig, now) }

        val last = sessions.filter { it.status.isValidated && it.measurements.isNotEmpty() }.maxByOrNull { it.startedAt }
        val lastStats = last?.let { SessionStatistics.compute(it, method) }

        val findings = mutableListOf<Finding>()
        val discuss = mutableListOf<String>()
        val limits = mutableListOf<String>()

        limits += "Analyse automatisée fondée uniquement sur les mesures enregistrées dans l'application ; elle ne remplace pas l'avis d'un médecin."
        limits += "Méthode de calcul des séances : ${method.label}. Les moyennes de période sont des moyennes de valeurs de séance."
        limits += "La fiabilité dépend du tensiomètre (appareil validé, brassard adapté) et du respect des conditions de mesure."

        // Données et complétude.
        for (r in listOf(r7, r30)) {
            val label = if (r === r7) "7 derniers jours" else "30 derniers jours"
            val c = r.completeness
            val compText = if (c != null && c.expectedSlots > 0) {
                " Séances programmées : ${c.expectedSlots} (réalisées ${c.doneSlots}, incomplètes ${c.incompleteSlots}, manquées ${c.missedSlots}, à venir ${c.pendingSlots})."
            } else ""
            findings += Finding(
                "Données disponibles – $label",
                "${r.sessionCount} séance(s) validée(s), ${r.measurementCount} mesure(s), ${r.daysWithData} jour(s) avec mesures sur ${r.calendarDays}.$compText",
                FindingSeverity.NEUTRAL,
            )
        }

        // Moyennes et position par rapport aux seuils à domicile.
        for (r in listOf(r7, r30)) {
            val sys = r.overall.systolic.mean ?: continue
            val dia = r.overall.diastolic.mean ?: continue
            val label = if (r === r7) "7 jours" else "30 jours"
            if (r.daysWithData < MedicalThresholds.HBPM_MIN_DAYS) {
                findings += Finding(
                    "Moyenne sur $label", "Moyenne ${Fmt.bp(sys, dia)} mmHg, mais seulement ${r.daysWithData} jour(s) de mesures : insuffisant pour interpréter.",
                    FindingSeverity.NEUTRAL,
                )
                continue
            }
            val cls = HbpmProtocol.classify(sys, dia)
            val sev = when (cls) {
                HbpmProtocol.Classification.NON_ELEVATED -> FindingSeverity.FAVORABLE
                HbpmProtocol.Classification.ELEVATED -> FindingSeverity.WATCH
                HbpmProtocol.Classification.HYPERTENSION_RANGE -> FindingSeverity.DISCUSS
            }
            val pulse = r.overall.pulse.mean?.let { ", fréquence cardiaque moyenne ${Fmt.d0(it)}/min" } ?: ""
            findings += Finding(
                "Moyenne sur $label",
                "Moyenne ${Fmt.bp(sys, dia)} mmHg$pulse (${r.sessionCount} séances). Repère à domicile : ${cls.label}.",
                sev, MedicalThresholds.HOME_HYPERTENSION.references,
            )
            if (cls == HbpmProtocol.Classification.HYPERTENSION_RANGE) {
                discuss += "Moyenne à domicile sur $label de ${Fmt.bp(sys, dia)} mmHg, au-dessus du repère de 135/85 mmHg."
            }
        }

        // Objectif personnel.
        profile.target?.let { t ->
            val e = r30.targetExceedance
            if (e != null && e.sessionCount > 0) {
                val sev = when {
                    (e.rate ?: 0.0) >= 0.5 -> FindingSeverity.DISCUSS
                    e.sessionsAbove > 0 -> FindingSeverity.WATCH
                    else -> FindingSeverity.FAVORABLE
                }
                findings += Finding(
                    "Objectif personnel (< ${t.systolic}/${t.diastolic} mmHg)",
                    "Sur 30 jours, ${e.sessionsAbove} séance(s) sur ${e.sessionCount} (${Fmt.pct(e.rate)}) au-dessus de l'objectif.",
                    sev,
                )
                if (sev == FindingSeverity.DISCUSS) discuss += "Objectif personnel dépassé lors de ${e.sessionsAbove} séances sur ${e.sessionCount} en 30 jours."
            }
        } ?: run {
            limits += "Aucun objectif tensionnel personnel n'est renseigné : les comparaisons utilisent les repères généraux à domicile."
        }

        // Tendance.
        val trendReport = if (r30.trend != null) r30 else r90
        val trend = trendReport.trend
        if (trend != null) {
            fun qualify(s: Double) = when {
                abs(s) < stableSlope -> "stable"
                s > 0 -> "en hausse"
                else -> "en baisse"
            }
            val window = if (trendReport === r30) "30" else "90"
            findings += Finding(
                "Tendance ($window jours)",
                "Systolique ${qualify(trend.systolicSlopePerWeek)} (${Fmt.signed1(trend.systolicSlopePerWeek)} mmHg/semaine, R² ${Fmt.d1(trend.systolicR2)}), " +
                    "diastolique ${qualify(trend.diastolicSlopePerWeek)} (${Fmt.signed1(trend.diastolicSlopePerWeek)} mmHg/semaine, R² ${Fmt.d1(trend.diastolicR2)}), " +
                    "sur ${trend.n} séances couvrant ${trend.spanDays} jours. Un R² faible signifie que la tendance explique peu les variations.",
                if (trend.systolicSlopePerWeek >= stableSlope || trend.diastolicSlopePerWeek >= stableSlope) FindingSeverity.WATCH else FindingSeverity.NEUTRAL,
            )
            if ((trend.systolicSlopePerWeek >= 2 || trend.diastolicSlopePerWeek >= 2) && maxOf(trend.systolicR2, trend.diastolicR2) >= 0.3) {
                discuss += "Tendance à la hausse sur $window jours (${Fmt.signed1(trend.systolicSlopePerWeek)} / ${Fmt.signed1(trend.diastolicSlopePerWeek)} mmHg par semaine)."
            }
        } else {
            limits += r90.trendUnavailableReason ?: "Tendance non calculable."
        }

        // Matin / soir.
        val me = r30.morningVsEvening
        if (me.systolicDiff != null && me.diastolicDiff != null) {
            val txt = "Soir − matin : ${Fmt.signed1(me.systolicDiff)} mmHg (systolique), ${Fmt.signed1(me.diastolicDiff)} mmHg (diastolique) " +
                "— ${me.a.sessionCount} séance(s) du matin, ${me.b.sessionCount} du soir."
            findings += Finding(
                "Comparaison matin / soir (30 jours)",
                if (me.sufficient) txt else "$txt Effectifs insuffisants (minimum ${me.minPerGroup} séances par moment) pour conclure.",
                if (me.sufficient && (abs(me.systolicDiff) >= 10 || abs(me.diastolicDiff) >= 10)) FindingSeverity.WATCH else FindingSeverity.NEUTRAL,
            )
            if (me.sufficient && (abs(me.systolicDiff) >= 10 || abs(me.diastolicDiff) >= 10)) {
                discuss += "Écart matin/soir notable (${Fmt.signed1(me.systolicDiff)} / ${Fmt.signed1(me.diastolicDiff)} mmHg)."
            }
        }

        // Variabilité.
        val v = r30.variability
        if (v.systolicSdBetweenSessions != null) {
            findings += Finding(
                "Variabilité (30 jours)",
                "Écart-type entre séances : ${Fmt.d1(v.systolicSdBetweenSessions)} mmHg (systolique), ${Fmt.d1(v.diastolicSdBetweenSessions)} mmHg (diastolique) ; " +
                    "variabilité moyenne entre séances successives : ${Fmt.d1(v.systolicArv)} / ${Fmt.d1(v.diastolicArv)} mmHg ; " +
                    "écart-type moyen au sein d'une séance : ${Fmt.d1(v.meanWithinSessionSystolicSd)} / ${Fmt.d1(v.meanWithinSessionDiastolicSd)} mmHg.",
                FindingSeverity.NEUTRAL,
            )
        }

        // Pression pulsée.
        r30.overall.pulsePressure.mean?.let { pp ->
            findings += Finding(
                "Pression pulsée (30 jours)",
                "Pression pulsée moyenne (systolique − diastolique) : ${Fmt.d0(pp)} mmHg. Paramètre descriptif, à interpréter par le médecin selon l'âge et le contexte.",
                FindingSeverity.NEUTRAL,
            )
        }

        // Valeurs inhabituelles.
        val outliers = r90.outliers
        if (outliers.isNotEmpty()) {
            findings += Finding(
                "Valeurs inhabituelles (90 jours)",
                outliers.take(5).joinToString("\n") { "• ${Fmt.date(it.session.localDate)} : ${it.reason}" },
                FindingSeverity.WATCH,
            )
            discuss += "${outliers.size} valeur(s) statistiquement inhabituelle(s) sur 90 jours."
        }

        // Alertes.
        val alerts = mutableListOf<Alert>()
        alerts += alertEngine.evaluatePeriod(r7, profile.target)
        if (last != null && lastStats != null) alerts += alertEngine.evaluateSession(last, lastStats, profile.target)
        val severeSessions = r30.sessionValues.count { AlertEngine.isSevere(it.primary.systolic, it.primary.diastolic) }
        if (severeSessions > 0) discuss += "$severeSessions séance(s) avec une moyenne ≥ 180/110 mmHg au cours des 30 derniers jours."
        val symptomatic = r30.sessionValues.filter { it.session.symptoms.isNotEmpty() }
        if (symptomatic.isNotEmpty()) discuss += "Symptômes signalés lors de ${symptomatic.size} séance(s) sur 30 jours."

        if (r30.sessionCount < 6) limits += "Moins de 6 séances sur 30 jours : les conclusions sont fragiles."
        if (r30.incompleteSessionCount > 0) limits += "${r30.incompleteSessionCount} séance(s) incomplète(s) incluse(s) dans les calculs."
        limits += "Les seuils de référence sont issus des recommandations ESC 2024, ESH 2023 et AHA/ACC 2025 ; votre médecin peut fixer un objectif différent."

        return AnalysisReport(
            generatedAt = now, method = method,
            last7 = r7, last30 = r30, last90 = r90, custom = custom,
            lastSession = last, lastSessionStats = lastStats,
            findings = findings, pointsToDiscuss = discuss.distinct(), limitations = limits.distinct(),
            alerts = alerts.distinctBy { it.code },
        )
    }
}
