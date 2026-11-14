package com.cardiosuivi.domain.stats

import com.cardiosuivi.domain.model.AggregationMethod
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSession

/** Triplet de valeurs moyennes (systolique, diastolique, pouls). */
data class BpMean(
    val systolic: Double,
    val diastolic: Double,
    /** Null si aucune mesure de la sélection ne comporte de pouls. */
    val pulse: Double?,
    /** Nombre de mesures utilisées pour calculer la moyenne. */
    val n: Int,
) {
    val pulsePressure: Double get() = systolic - diastolic
}

/**
 * Statistiques d'UNE séance. Ne jamais confondre avec une moyenne journalière
 * (moyenne des séances d'un jour) ou une moyenne sur plusieurs jours (voir PeriodStatistics).
 */
data class SessionStats(
    val sessionId: Long,
    val measurementCount: Int,
    /** Moyenne de toutes les mesures de la séance. */
    val meanAll: BpMean?,
    /** Moyenne des deux dernières mesures (null si moins de 2 mesures). */
    val meanLastTwo: BpMean?,
    /** Valeur de séance retenue selon la méthode configurée. */
    val primary: BpMean?,
    val method: AggregationMethod,
    /** Vrai si la méthode configurée n'était pas applicable et que la moyenne des mesures disponibles a été utilisée. */
    val methodFallback: Boolean,
    val systolic: SeriesSummary,
    val diastolic: SeriesSummary,
    val pulse: SeriesSummary,
    val pulsePressure: SeriesSummary,
    /** Différences successives (mesure k+1 − mesure k) en mmHg. */
    val systolicDeltas: List<Int>,
    val diastolicDeltas: List<Int>,
    val pulseDeltas: List<Int?>,
    /** Plus grand écart absolu entre deux mesures de la séance (systolique ou diastolique). */
    val maxIntraSessionDifference: Int?,
)

object SessionStatistics {

    fun meanOf(measurements: List<Measurement>): BpMean? {
        if (measurements.isEmpty()) return null
        val pulses = measurements.mapNotNull { it.pulse }
        return BpMean(
            systolic = measurements.map { it.systolic }.average(),
            diastolic = measurements.map { it.diastolic }.average(),
            pulse = if (pulses.isEmpty()) null else pulses.average(),
            n = measurements.size,
        )
    }

    fun compute(session: MeasurementSession, method: AggregationMethod): SessionStats {
        val ms = session.measurements.sortedWith(compareBy({ it.index }, { it.measuredAt }))
        val meanAll = meanOf(ms)
        val meanLastTwo = if (ms.size >= 2) meanOf(ms.takeLast(2)) else null
        val (primary, fallback) = when (method) {
            AggregationMethod.ALL_MEASUREMENTS -> meanAll to false
            AggregationMethod.LAST_TWO -> if (meanLastTwo != null) meanLastTwo to false else meanAll to (meanAll != null)
        }
        val sys = ms.map { it.systolic }
        val dia = ms.map { it.diastolic }
        val maxDiff = if (ms.size < 2) null else {
            val sysRange = sys.max() - sys.min()
            val diaRange = dia.max() - dia.min()
            maxOf(sysRange, diaRange)
        }
        return SessionStats(
            sessionId = session.id,
            measurementCount = ms.size,
            meanAll = meanAll,
            meanLastTwo = meanLastTwo,
            primary = primary,
            method = method,
            methodFallback = fallback,
            systolic = SeriesSummary.of(sys),
            diastolic = SeriesSummary.of(dia),
            pulse = SeriesSummary.of(ms.mapNotNull { it.pulse }),
            pulsePressure = SeriesSummary.of(ms.map { it.pulsePressure }),
            systolicDeltas = sys.zipWithNext { a, b -> b - a },
            diastolicDeltas = dia.zipWithNext { a, b -> b - a },
            pulseDeltas = ms.zipWithNext { a, b -> if (a.pulse != null && b.pulse != null) b.pulse - a.pulse else null },
            maxIntraSessionDifference = maxDiff,
        )
    }
}
