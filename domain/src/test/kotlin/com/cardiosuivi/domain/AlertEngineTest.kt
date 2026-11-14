package com.cardiosuivi.domain

import com.cardiosuivi.domain.Fixtures.r
import com.cardiosuivi.domain.Fixtures.session
import com.cardiosuivi.domain.alerts.AlertEngine
import com.cardiosuivi.domain.alerts.AlertLevel
import com.cardiosuivi.domain.model.AggregationMethod
import com.cardiosuivi.domain.model.BpTarget
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.Symptom
import com.cardiosuivi.domain.stats.PeriodStatistics
import com.cardiosuivi.domain.stats.SessionStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AlertEngineTest {

    private val engine = AlertEngine(emergencyNumber = "112")
    private val day = LocalDate.of(2026, 11, 4)

    @Test
    fun urgentSymptomsAlwaysTriggerUrgentEvenWithNormalPressure() {
        val alerts = engine.evaluateMeasurement(122, 78, 70, setOf(Symptom.CHEST_PAIN))
        assertEquals(AlertLevel.URGENT, AlertEngine.highest(alerts))
        assertTrue(alerts.first { it.level == AlertLevel.URGENT }.message.contains("112"))
    }

    @Test
    fun severeReadingWithoutSymptomsAsksRecheckAndContact() {
        val alerts = engine.evaluateMeasurement(186, 104, 80, emptySet())
        assertEquals(AlertLevel.CONSULT_SOON, AlertEngine.highest(alerts))
        assertTrue(alerts.any { it.code == "severe_single" && it.message.contains("5 minutes") })
    }

    @Test
    fun severeDiastolicAloneIsSevere() {
        assertTrue(AlertEngine.isSevere(150.0, 110.0))
        assertFalse(AlertEngine.isSevere(179.0, 109.0))
        assertTrue(AlertEngine.isSevere(180.0, 70.0))
    }

    @Test
    fun severeWithSymptomsIsUrgent() {
        val alerts = engine.evaluateMeasurement(195, 115, 90, setOf(Symptom.SEVERE_HEADACHE))
        assertEquals(AlertLevel.URGENT, AlertEngine.highest(alerts))
        assertTrue(alerts.any { it.code == "severe_with_symptoms" })
    }

    @Test
    fun nonUrgentSymptomsRecommendConsultation() {
        val alerts = engine.evaluateSymptoms(setOf(Symptom.FATIGUE))
        assertEquals(AlertLevel.CONSULT, AlertEngine.highest(alerts))
    }

    @Test
    fun plausibilityChecks() {
        val inverted = engine.checkPlausibility(80, 120, 70)
        assertTrue(inverted.any { it.code == "sys_le_dia" })
        val outOfBounds = engine.checkPlausibility(300, 20, 10)
        assertTrue(outOfBounds.any { it.code == "sys_bounds" })
        assertTrue(outOfBounds.any { it.code == "dia_bounds" })
        assertTrue(outOfBounds.any { it.code == "pulse_bounds" })
        assertTrue(engine.checkPlausibility(128, 82, 66).isEmpty())
    }

    @Test
    fun sessionAboveTargetAndIntraSessionDifference() {
        val s = session(day, readings = listOf(r(150, 92), r(136, 86), r(134, 85)))
        val st = SessionStatistics.compute(s, AggregationMethod.ALL_MEASUREMENTS)
        val alerts = engine.evaluateSession(s, st, BpTarget(135, 85))
        assertTrue(alerts.any { it.code == "intra_diff" && it.level == AlertLevel.CHECK_MEASUREMENT })
        assertTrue(alerts.any { it.code == "session_above_target" })
    }

    @Test
    fun sessionWithoutTargetUsesHomeReferenceThreshold() {
        val s = session(day, readings = listOf(r(138, 84), r(137, 84), r(136, 83)))
        val st = SessionStatistics.compute(s, AggregationMethod.ALL_MEASUREMENTS)
        val alerts = engine.evaluateSession(s, st, null)
        assertTrue(alerts.any { it.code == "session_above_home_threshold" })
    }

    @Test
    fun grade2AndLowPressureAndPulse() {
        val s = session(day, readings = listOf(r(165, 95, 110)))
        val alerts = engine.evaluateSession(s, SessionStatistics.compute(s, AggregationMethod.ALL_MEASUREMENTS), null)
        assertTrue(alerts.any { it.code == "session_grade2" })
        assertTrue(alerts.any { it.code == "pulse_high" && it.level == AlertLevel.ATTENTION })

        val low = session(day, readings = listOf(r(85, 55, 45)), symptoms = setOf(Symptom.DIZZINESS))
        val lowAlerts = engine.evaluateSession(low, SessionStatistics.compute(low, AggregationMethod.ALL_MEASUREMENTS), null)
        assertTrue(lowAlerts.any { it.code == "session_low" && it.level == AlertLevel.CONSULT_SOON })
        assertTrue(lowAlerts.any { it.code == "pulse_low" && it.level == AlertLevel.CONSULT_SOON })
    }

    @Test
    fun normalSessionProducesNoWarning() {
        val s = session(day, readings = listOf(r(118, 74, 64), r(116, 72, 63), r(117, 73, 62)))
        val alerts = engine.evaluateSession(s, SessionStatistics.compute(s, AggregationMethod.ALL_MEASUREMENTS), BpTarget(135, 85))
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun periodAlertsRequireThreeDaysAndUseHomeThreshold() {
        val two = listOf(session(day, readings = listOf(r(150, 95))), session(day.plusDays(1), readings = listOf(r(150, 95))))
        val rep2 = PeriodStatistics.compute(two, day, day.plusDays(6), AggregationMethod.ALL_MEASUREMENTS)
        assertTrue(engine.evaluatePeriod(rep2, null).any { it.code == "insufficient_days" })
        assertFalse(engine.evaluatePeriod(rep2, null).any { it.code == "period_above_home_threshold" })

        val three = two + session(day.plusDays(2), SessionPeriod.EVENING, readings = listOf(r(140, 88)))
        val rep3 = PeriodStatistics.compute(three, day, day.plusDays(6), AggregationMethod.ALL_MEASUREMENTS)
        assertTrue(engine.evaluatePeriod(rep3, null).any { it.code == "period_above_home_threshold" })
    }
}
