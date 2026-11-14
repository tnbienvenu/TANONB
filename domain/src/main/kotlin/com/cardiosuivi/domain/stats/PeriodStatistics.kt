package com.cardiosuivi.domain.stats

import com.cardiosuivi.domain.model.AggregationMethod
import com.cardiosuivi.domain.model.BpTarget
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.ReminderConfig
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import com.cardiosuivi.domain.schedule.ScheduleCalculator
import com.cardiosuivi.domain.schedule.SlotStatus
import com.cardiosuivi.domain.util.Fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/** Valeur de séance (selon la méthode d'agrégation) rattachée à sa séance. */
data class SessionValue(
    val session: MeasurementSession,
    val stats: SessionStats,
) {
    val primary: BpMean get() = requireNotNull(stats.primary)
}

/**
 * Statistiques d'un groupe de séances (ex. : toutes les séances du matin).
 * Les moyennes sont des MOYENNES DE VALEURS DE SÉANCE (et non des moyennes de mesures individuelles).
 */
data class GroupStats(
    val sessionCount: Int,
    val measurementCount: Int,
    val systolic: SeriesSummary,
    val diastolic: SeriesSummary,
    val pulse: SeriesSummary,
    val pulsePressure: SeriesSummary,
) {
    val isEmpty: Boolean get() = sessionCount == 0

    companion object {
        fun of(values: List<SessionValue>): GroupStats = GroupStats(
            sessionCount = values.size,
            measurementCount = values.sumOf { it.stats.measurementCount },
            systolic = SeriesSummary.ofDoubles(values.map { it.primary.systolic }),
            diastolic = SeriesSummary.ofDoubles(values.map { it.primary.diastolic }),
            pulse = SeriesSummary.ofDoubles(values.mapNotNull { it.primary.pulse }),
            pulsePressure = SeriesSummary.ofDoubles(values.map { it.primary.pulsePressure }),
        )
    }
}

/** Moyenne journalière : moyenne des valeurs de séance d'un même jour civil. */
data class DailyAggregate(val date: LocalDate, val stats: GroupStats)

data class BucketAggregate(val label: String, val start: LocalDate, val end: LocalDate, val stats: GroupStats)

data class Comparison(
    val a: GroupStats,
    val b: GroupStats,
    /** Différence b − a des moyennes systoliques (null si un groupe est vide). */
    val systolicDiff: Double?,
    val diastolicDiff: Double?,
    val pulseDiff: Double?,
    /** Vrai si chaque groupe comporte au moins [minPerGroup] séances. */
    val sufficient: Boolean,
    val minPerGroup: Int,
)

data class Trend(
    /** Pente en mmHg par semaine. */
    val systolicSlopePerWeek: Double,
    val diastolicSlopePerWeek: Double,
    val systolicR2: Double,
    val diastolicR2: Double,
    val n: Int,
    val spanDays: Long,
)

data class Variability(
    /** Écart-type des valeurs de séance. */
    val systolicSdBetweenSessions: Double?,
    val diastolicSdBetweenSessions: Double?,
    val systolicCv: Double?,
    val diastolicCv: Double?,
    /** Variabilité réelle moyenne entre séances successives. */
    val systolicArv: Double?,
    val diastolicArv: Double?,
    /** Moyenne des écarts-types intra-séance. */
    val meanWithinSessionSystolicSd: Double?,
    val meanWithinSessionDiastolicSd: Double?,
)

data class Completeness(
    val expectedSlots: Int,
    val doneSlots: Int,
    val incompleteSlots: Int,
    val missedSlots: Int,
    val pendingSlots: Int,
) {
    /** Taux de réalisation des créneaux échus (réalisés + incomplets) / (échus). Null si aucun créneau échu. */
    val completionRate: Double?
        get() {
            val due = doneSlots + incompleteSlots + missedSlots
            return if (due == 0) null else (doneSlots + incompleteSlots).toDouble() / due
        }
}

data class StatisticalOutlier(val measurement: Measurement, val session: MeasurementSession, val reason: String)

data class TargetExceedance(val target: BpTarget, val sessionsAbove: Int, val sessionCount: Int) {
    val rate: Double? get() = if (sessionCount == 0) null else sessionsAbove.toDouble() / sessionCount
}

data class PeriodReport(
    val from: LocalDate,
    val to: LocalDate,
    val method: AggregationMethod,
    val sessionCount: Int,
    val incompleteSessionCount: Int,
    val measurementCount: Int,
    val daysWithData: Int,
    val calendarDays: Long,
    /** Moyenne sur la période = moyenne des valeurs de séance. */
    val overall: GroupStats,
    /** Statistiques des mesures individuelles (pour comparaison avec les moyennes de séance). */
    val individualSystolic: SeriesSummary,
    val individualDiastolic: SeriesSummary,
    val individualPulse: SeriesSummary,
    val daily: List<DailyAggregate>,
    /** Moyenne des moyennes journalières (chaque jour pèse autant, quel que soit son nombre de séances). */
    val meanOfDailyMeansSystolic: Double?,
    val meanOfDailyMeansDiastolic: Double?,
    val weekly: List<BucketAggregate>,
    val monthly: List<BucketAggregate>,
    val morningVsEvening: Comparison,
    val byWeekday: Map<DayOfWeek, GroupStats>,
    val trend: Trend?,
    val trendUnavailableReason: String?,
    val variability: Variability,
    val completeness: Completeness?,
    val targetExceedance: TargetExceedance?,
    val outliers: List<StatisticalOutlier>,
    val sessionValues: List<SessionValue>,
)

object PeriodStatistics {

    /** Nombre minimal de séances pour calculer une tendance (choix méthodologique de l'application). */
    const val MIN_SESSIONS_FOR_TREND = 6
    /** Durée minimale couverte pour calculer une tendance. */
    const val MIN_DAYS_FOR_TREND = 7L
    /** Nombre minimal de séances par groupe pour considérer une comparaison comme exploitable. */
    const val MIN_SESSIONS_PER_GROUP = 3
    /** Effectif minimal de mesures pour la détection statistique de valeurs atypiques. */
    const val MIN_MEASUREMENTS_FOR_OUTLIERS = 10

    /**
     * Calcule le rapport d'une période [from, to] (dates incluses).
     * Seules les séances validées (terminées ou incomplètes) et comportant au moins une mesure sont prises en compte.
     */
    fun compute(
        allSessions: List<MeasurementSession>,
        from: LocalDate,
        to: LocalDate,
        method: AggregationMethod,
        target: BpTarget? = null,
        reminderConfig: ReminderConfig? = null,
        now: ZonedDateTime? = null,
    ): PeriodReport {
        val inRange = allSessions.filter { !it.localDate.isBefore(from) && !it.localDate.isAfter(to) }
        val validated = inRange
            .filter { it.status.isValidated && it.measurements.isNotEmpty() }
            .sortedBy { it.startedAt }
        val values = validated.map { SessionValue(it, SessionStatistics.compute(it, method)) }
        val allMeasurements = validated.flatMap { it.measurements }

        val daily = values.groupBy { it.session.localDate }.toSortedMap()
            .map { (d, v) -> DailyAggregate(d, GroupStats.of(v)) }

        val weekly = values.groupBy { it.session.localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .toSortedMap()
            .map { (start, v) -> BucketAggregate("Semaine du $start", start, start.plusDays(6), GroupStats.of(v)) }

        val monthly = values.groupBy { YearMonth.from(it.session.localDate) }.toSortedMap()
            .map { (ym, v) -> BucketAggregate(ym.toString(), ym.atDay(1), ym.atEndOfMonth(), GroupStats.of(v)) }

        val morning = GroupStats.of(values.filter { it.session.period == SessionPeriod.MORNING })
        val evening = GroupStats.of(values.filter { it.session.period == SessionPeriod.EVENING })

        val byWeekday = values.groupBy { it.session.localDate.dayOfWeek }.toSortedMap()
            .mapValues { GroupStats.of(it.value) }

        val (trend, trendReason) = computeTrend(values)

        val completeness = if (reminderConfig != null && now != null) {
            val states = ScheduleCalculator.slotStates(reminderConfig, inRange, from, to, now)
            Completeness(
                expectedSlots = states.size,
                doneSlots = states.count { it.status == SlotStatus.DONE },
                incompleteSlots = states.count { it.status == SlotStatus.INCOMPLETE },
                missedSlots = states.count { it.status == SlotStatus.MISSED },
                pendingSlots = states.count { it.status == SlotStatus.PENDING },
            )
        } else null

        val exceedance = target?.let { t ->
            TargetExceedance(
                target = t,
                sessionsAbove = values.count { it.primary.systolic >= t.systolic || it.primary.diastolic >= t.diastolic },
                sessionCount = values.size,
            )
        }

        return PeriodReport(
            from = from,
            to = to,
            method = method,
            sessionCount = values.size,
            incompleteSessionCount = values.count { it.session.status == SessionStatus.INCOMPLETE },
            measurementCount = allMeasurements.size,
            daysWithData = daily.size,
            calendarDays = ChronoUnit.DAYS.between(from, to) + 1,
            overall = GroupStats.of(values),
            individualSystolic = SeriesSummary.of(allMeasurements.map { it.systolic }),
            individualDiastolic = SeriesSummary.of(allMeasurements.map { it.diastolic }),
            individualPulse = SeriesSummary.of(allMeasurements.mapNotNull { it.pulse }),
            daily = daily,
            meanOfDailyMeansSystolic = Descriptive.mean(daily.mapNotNull { it.stats.systolic.mean }),
            meanOfDailyMeansDiastolic = Descriptive.mean(daily.mapNotNull { it.stats.diastolic.mean }),
            weekly = weekly,
            monthly = monthly,
            morningVsEvening = compare(morning, evening),
            byWeekday = byWeekday,
            trend = trend,
            trendUnavailableReason = trendReason,
            variability = computeVariability(values),
            completeness = completeness,
            targetExceedance = exceedance,
            outliers = detectOutliers(validated),
            sessionValues = values,
        )
    }

    fun compare(a: GroupStats, b: GroupStats, minPerGroup: Int = MIN_SESSIONS_PER_GROUP): Comparison {
        fun diff(x: Double?, y: Double?) = if (x != null && y != null) y - x else null
        return Comparison(
            a = a,
            b = b,
            systolicDiff = diff(a.systolic.mean, b.systolic.mean),
            diastolicDiff = diff(a.diastolic.mean, b.diastolic.mean),
            pulseDiff = diff(a.pulse.mean, b.pulse.mean),
            sufficient = a.sessionCount >= minPerGroup && b.sessionCount >= minPerGroup,
            minPerGroup = minPerGroup,
        )
    }

    private fun computeTrend(values: List<SessionValue>): Pair<Trend?, String?> {
        if (values.size < MIN_SESSIONS_FOR_TREND) {
            return null to "Tendance non calculée : ${values.size} séance(s), minimum $MIN_SESSIONS_FOR_TREND requis."
        }
        val t0 = values.first().session.startedAt
        val span = ChronoUnit.DAYS.between(values.first().session.localDate, values.last().session.localDate)
        if (span < MIN_DAYS_FOR_TREND) {
            return null to "Tendance non calculée : les séances couvrent $span jour(s), minimum $MIN_DAYS_FOR_TREND requis."
        }
        val x = values.map { ChronoUnit.SECONDS.between(t0, it.session.startedAt) / 86400.0 }
        val sys = Descriptive.linearRegression(x, values.map { it.primary.systolic })
        val dia = Descriptive.linearRegression(x, values.map { it.primary.diastolic })
        if (sys == null || dia == null) return null to "Tendance non calculable (dates identiques)."
        return Trend(
            systolicSlopePerWeek = sys.slope * 7,
            diastolicSlopePerWeek = dia.slope * 7,
            systolicR2 = sys.r2,
            diastolicR2 = dia.r2,
            n = values.size,
            spanDays = span,
        ) to null
    }

    private fun computeVariability(values: List<SessionValue>): Variability {
        val sys = values.map { it.primary.systolic }
        val dia = values.map { it.primary.diastolic }
        return Variability(
            systolicSdBetweenSessions = Descriptive.sampleSd(sys),
            diastolicSdBetweenSessions = Descriptive.sampleSd(dia),
            systolicCv = Descriptive.coefficientOfVariation(sys),
            diastolicCv = Descriptive.coefficientOfVariation(dia),
            systolicArv = Descriptive.averageRealVariability(sys),
            diastolicArv = Descriptive.averageRealVariability(dia),
            meanWithinSessionSystolicSd = Descriptive.mean(values.mapNotNull { it.stats.systolic.sd }),
            meanWithinSessionDiastolicSd = Descriptive.mean(values.mapNotNull { it.stats.diastolic.sd }),
        )
    }

    /**
     * Valeurs atypiques au sens statistique (et non médical) : écart à la médiane personnelle
     * supérieur à 3 écarts absolus médians normalisés (×1,4826). Nécessite au moins
     * [MIN_MEASUREMENTS_FOR_OUTLIERS] mesures.
     */
    fun detectOutliers(sessions: List<MeasurementSession>): List<StatisticalOutlier> {
        val pairs = sessions.flatMap { s -> s.measurements.map { it to s } }
        if (pairs.size < MIN_MEASUREMENTS_FOR_OUTLIERS) return emptyList()
        val out = mutableListOf<StatisticalOutlier>()
        fun check(name: String, selector: (Measurement) -> Int) {
            val vals = pairs.map { selector(it.first).toDouble() }
            val med = Descriptive.median(vals) ?: return
            val mad = Descriptive.mad(vals) ?: return
            val scale = mad * 1.4826
            if (scale < 1.0) return // dispersion trop faible pour un critère robuste
            for ((m, s) in pairs) {
                val v = selector(m).toDouble()
                if (abs(v - med) > 3 * scale) {
                    out += StatisticalOutlier(
                        m, s,
                        "$name ${selector(m)} mmHg éloignée de votre médiane (${Fmt.d0(med)} mmHg)",
                    )
                }
            }
        }
        check("Systolique") { it.systolic }
        check("Diastolique") { it.diastolic }
        return out.distinctBy { it.measurement.id to it.reason }
    }
}
