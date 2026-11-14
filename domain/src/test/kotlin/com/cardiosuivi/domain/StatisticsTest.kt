package com.cardiosuivi.domain

import com.cardiosuivi.domain.Fixtures.r
import com.cardiosuivi.domain.Fixtures.session
import com.cardiosuivi.domain.model.AggregationMethod
import com.cardiosuivi.domain.model.BpTarget
import com.cardiosuivi.domain.model.ReminderConfig
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import com.cardiosuivi.domain.stats.Descriptive
import com.cardiosuivi.domain.stats.PeriodStatistics
import com.cardiosuivi.domain.stats.SessionStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class StatisticsTest {

    private val eps = 1e-9

    @Test
    fun descriptiveBasics() {
        assertEquals(2.0, Descriptive.mean(listOf(1.0, 2.0, 3.0))!!, eps)
        assertEquals(1.0, Descriptive.sampleSd(listOf(1.0, 2.0, 3.0))!!, eps)
        assertNull(Descriptive.sampleSd(listOf(5.0)))
        assertNull(Descriptive.mean(emptyList()))
        assertEquals(2.5, Descriptive.median(listOf(4.0, 1.0, 2.0, 3.0))!!, eps)
        assertEquals(1.0, Descriptive.mad(listOf(1.0, 2.0, 3.0, 4.0, 5.0))!!, eps)
        // ARV : |140-130| + |135-140| = 10 + 5 → 7,5
        assertEquals(7.5, Descriptive.averageRealVariability(listOf(130.0, 140.0, 135.0))!!, eps)
    }

    @Test
    fun regressionExactLine() {
        val x = listOf(0.0, 1.0, 2.0, 3.0)
        val y = x.map { 2 * it + 1 }
        val reg = Descriptive.linearRegression(x, y)!!
        assertEquals(2.0, reg.slope, eps)
        assertEquals(1.0, reg.intercept, eps)
        assertEquals(1.0, reg.r2, eps)
        assertNull(Descriptive.linearRegression(listOf(1.0, 1.0, 1.0), listOf(1.0, 2.0, 3.0)))
        assertNull(Descriptive.linearRegression(listOf(1.0, 2.0), listOf(1.0, 2.0)))
    }

    @Test
    fun sessionStatsThreeReadings() {
        val s = session(LocalDate.of(2026, 11, 4), readings = listOf(r(140, 90, 72), r(132, 84, 70), r(128, 82, 68)))
        val st = SessionStatistics.compute(s, AggregationMethod.ALL_MEASUREMENTS)
        assertEquals(3, st.measurementCount)
        assertEquals(400.0 / 3, st.meanAll!!.systolic, eps)
        assertEquals(256.0 / 3, st.meanAll!!.diastolic, eps)
        assertEquals(70.0, st.meanAll!!.pulse!!, eps)
        assertEquals(130.0, st.meanLastTwo!!.systolic, eps)
        assertEquals(83.0, st.meanLastTwo!!.diastolic, eps)
        assertEquals(st.meanAll, st.primary)
        assertEquals(128, st.systolic.min)
        assertEquals(140, st.systolic.max)
        assertEquals(12, st.systolic.range)
        assertEquals(listOf(-8, -4), st.systolicDeltas)
        assertEquals(listOf(-6, -2), st.diastolicDeltas)
        assertEquals(12, st.maxIntraSessionDifference)
        // Écart-type : valeurs 140,132,128 → moyenne 133,33 ; SD = sqrt(((6,67)²+(1,33)²+(5,33)²)/2) ≈ 6,11
        assertEquals(6.110100926607787, st.systolic.sd!!, 1e-9)
        assertEquals(46, st.pulsePressure.min) // 128 − 82
        assertEquals(50, st.pulsePressure.max) // 140 − 90
    }

    @Test
    fun lastTwoMethodAndFallback() {
        val s3 = session(LocalDate.of(2026, 11, 4), readings = listOf(r(150, 95), r(130, 80), r(126, 78)))
        val st = SessionStatistics.compute(s3, AggregationMethod.LAST_TWO)
        assertEquals(128.0, st.primary!!.systolic, eps)
        assertFalse(st.methodFallback)

        val s1 = session(LocalDate.of(2026, 11, 4), readings = listOf(r(150, 95)), status = SessionStatus.INCOMPLETE)
        val st1 = SessionStatistics.compute(s1, AggregationMethod.LAST_TWO)
        assertEquals(150.0, st1.primary!!.systolic, eps)
        assertTrue("Le repli doit être signalé", st1.methodFallback)
        assertNull(st1.meanLastTwo)
    }

    @Test
    fun pulseMissingIsNotInvented() {
        val s = session(LocalDate.of(2026, 11, 4), readings = listOf(r(130, 80, null), r(128, 79, null)))
        val st = SessionStatistics.compute(s, AggregationMethod.ALL_MEASUREMENTS)
        assertNull(st.primary!!.pulse)
        assertEquals(0, st.pulse.n)
    }

    @Test
    fun periodExcludesInProgressAndOutOfRange() {
        val d = LocalDate.of(2026, 11, 4)
        val sessions = listOf(
            session(d, readings = listOf(r(130, 80))),
            session(d, SessionPeriod.EVENING, readings = listOf(r(200, 120)), status = SessionStatus.IN_PROGRESS),
            session(d.minusDays(30), readings = listOf(r(180, 100))),
        )
        val rep = PeriodStatistics.compute(sessions, d.minusDays(6), d, AggregationMethod.ALL_MEASUREMENTS)
        assertEquals(1, rep.sessionCount)
        assertEquals(130.0, rep.overall.systolic.mean!!, eps)
        assertEquals(7L, rep.calendarDays)
        assertEquals(1, rep.daysWithData)
    }

    @Test
    fun sessionMeanVsDailyMeanVsIndividualMeanAreDistinct() {
        val d1 = LocalDate.of(2026, 11, 4)
        val d2 = d1.plusDays(1)
        val sessions = listOf(
            session(d1, SessionPeriod.MORNING, readings = listOf(r(120, 80), r(120, 80), r(120, 80))),
            session(d1, SessionPeriod.EVENING, readings = listOf(r(140, 90))),
            session(d2, SessionPeriod.MORNING, readings = listOf(r(150, 90), r(150, 90), r(150, 90))),
        )
        val rep = PeriodStatistics.compute(sessions, d1, d2, AggregationMethod.ALL_MEASUREMENTS)
        // Moyenne des valeurs de séance : (120 + 140 + 150)/3
        assertEquals(410.0 / 3, rep.overall.systolic.mean!!, eps)
        // Moyenne des moyennes journalières : jour 1 = 130 ; jour 2 = 150 → 140
        assertEquals(140.0, rep.meanOfDailyMeansSystolic!!, eps)
        // Moyenne des mesures individuelles : (3×120 + 140 + 3×150)/7
        assertEquals(950.0 / 7, rep.individualSystolic.mean!!, eps)
        assertEquals(2, rep.daily.size)
        assertEquals(130.0, rep.daily[0].stats.systolic.mean!!, eps)
    }

    @Test
    fun morningEveningComparisonAndSufficiency() {
        val base = LocalDate.of(2026, 11, 2)
        val sessions = (0 until 3).flatMap { i ->
            listOf(
                session(base.plusDays(i.toLong()), SessionPeriod.MORNING, readings = listOf(r(130, 80))),
                session(base.plusDays(i.toLong()), SessionPeriod.EVENING, readings = listOf(r(140, 84))),
            )
        }
        val rep = PeriodStatistics.compute(sessions, base, base.plusDays(6), AggregationMethod.ALL_MEASUREMENTS)
        assertEquals(10.0, rep.morningVsEvening.systolicDiff!!, eps)
        assertEquals(4.0, rep.morningVsEvening.diastolicDiff!!, eps)
        assertTrue(rep.morningVsEvening.sufficient)
        val rep2 = PeriodStatistics.compute(sessions.take(2), base, base.plusDays(6), AggregationMethod.ALL_MEASUREMENTS)
        assertFalse(rep2.morningVsEvening.sufficient)
    }

    @Test
    fun trendRequiresEnoughDataAndDetectsIncrease() {
        val base = LocalDate.of(2026, 10, 1)
        val few = (0 until 4).map { session(base.plusDays(it * 3L), readings = listOf(r(130 + it, 80))) }
        val repFew = PeriodStatistics.compute(few, base, base.plusDays(30), AggregationMethod.ALL_MEASUREMENTS)
        assertNull(repFew.trend)
        assertNotNull(repFew.trendUnavailableReason)

        // +1 mmHg tous les 2 jours = +3,5 mmHg/semaine (heures identiques)
        val many = (0 until 10).map { session(base.plusDays(it * 2L), readings = listOf(r(130 + it, 80 + it))) }
        val rep = PeriodStatistics.compute(many, base, base.plusDays(30), AggregationMethod.ALL_MEASUREMENTS)
        val t = rep.trend!!
        assertEquals(3.5, t.systolicSlopePerWeek, 1e-6)
        assertEquals(3.5, t.diastolicSlopePerWeek, 1e-6)
        assertEquals(1.0, t.systolicR2, 1e-9)
        assertEquals(18L, t.spanDays)
    }

    @Test
    fun weeklyAndMonthlyBuckets() {
        val sessions = listOf(
            session(LocalDate.of(2026, 10, 28), readings = listOf(r(130, 80))), // mercredi
            session(LocalDate.of(2026, 10, 30), readings = listOf(r(134, 82))), // vendredi même semaine
            session(LocalDate.of(2026, 11, 4), readings = listOf(r(140, 88))),
        )
        val rep = PeriodStatistics.compute(sessions, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30), AggregationMethod.ALL_MEASUREMENTS)
        assertEquals(2, rep.weekly.size)
        assertEquals(LocalDate.of(2026, 10, 26), rep.weekly[0].start)
        assertEquals(132.0, rep.weekly[0].stats.systolic.mean!!, eps)
        assertEquals(2, rep.monthly.size)
        assertEquals("2026-10", rep.monthly[0].label)
    }

    @Test
    fun completenessCountsMissedAndPending() {
        // Mercredi 4, jeudi 5, vendredi 6 novembre 2026 ; maintenant = jeudi 5 à 12h.
        val now = ZonedDateTime.of(LocalDate.of(2026, 11, 5), LocalTime.NOON, Fixtures.ZONE)
        val sessions = listOf(
            session(LocalDate.of(2026, 11, 4), SessionPeriod.MORNING, readings = listOf(r(130, 80), r(128, 80), r(127, 79))),
            session(LocalDate.of(2026, 11, 5), SessionPeriod.MORNING, readings = listOf(r(130, 80)), status = SessionStatus.INCOMPLETE),
        )
        val rep = PeriodStatistics.compute(
            sessions, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8), AggregationMethod.ALL_MEASUREMENTS,
            reminderConfig = ReminderConfig(), now = now,
        )
        val c = rep.completeness!!
        assertEquals(6, c.expectedSlots)
        assertEquals(1, c.doneSlots)
        assertEquals(1, c.incompleteSlots)
        assertEquals(1, c.missedSlots) // mercredi soir
        assertEquals(3, c.pendingSlots) // jeudi soir, vendredi matin et soir
        assertEquals(2.0 / 3, c.completionRate!!, eps)
    }

    @Test
    fun targetExceedanceAndOutliers() {
        val base = LocalDate.of(2026, 10, 1)
        val sessions = (0 until 12).map { i ->
            val sys = if (i == 5) 190 else 128 + (i % 3)
            session(base.plusDays(i.toLong()), readings = listOf(r(sys, 80 + (i % 2))))
        }
        val rep = PeriodStatistics.compute(sessions, base, base.plusDays(20), AggregationMethod.ALL_MEASUREMENTS, BpTarget(130, 80))
        assertEquals(12, rep.targetExceedance!!.sessionCount)
        // sessions ≥130 sys ou ≥80 dia : toutes ont dia ≥ 80
        assertEquals(12, rep.targetExceedance!!.sessionsAbove)
        assertTrue(rep.outliers.any { it.measurement.systolic == 190 })
        assertFalse(rep.outliers.any { it.measurement.systolic in 128..130 })
    }
}
