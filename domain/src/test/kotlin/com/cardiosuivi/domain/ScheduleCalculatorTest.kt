package com.cardiosuivi.domain

import com.cardiosuivi.domain.Fixtures.ZONE
import com.cardiosuivi.domain.Fixtures.r
import com.cardiosuivi.domain.Fixtures.session
import com.cardiosuivi.domain.model.ReminderConfig
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import com.cardiosuivi.domain.schedule.ScheduleCalculator
import com.cardiosuivi.domain.schedule.SlotStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class ScheduleCalculatorTest {

    private val config = ReminderConfig() // mercredi, jeudi, vendredi ; 07:00 et 19:00

    @Test
    fun defaultConfigMatchesUserCalendar() {
        assertEquals(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), config.days)
        assertEquals(LocalTime.of(7, 0), config.morningTime)
        assertEquals(LocalTime.of(19, 0), config.eveningTime)
    }

    @Test
    fun nextSlotsFromSaturday() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 11, 14), LocalTime.of(10, 0), ZONE) // samedi
        val next = ScheduleCalculator.nextSlots(config, now, 4)
        assertEquals(LocalDate.of(2026, 11, 18), next[0].date) // mercredi suivant
        assertEquals(SessionPeriod.MORNING, next[0].period)
        assertEquals(SessionPeriod.EVENING, next[1].period)
        assertEquals(LocalDate.of(2026, 11, 19), next[2].date)
    }

    @Test
    fun nextOccurrenceSameDayLater() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 11, 18), LocalTime.of(8, 0), ZONE) // mercredi 8h
        val evening = ScheduleCalculator.nextOccurrence(config, SessionPeriod.EVENING, now)!!
        assertEquals(LocalDate.of(2026, 11, 18), evening.toLocalDate())
        val morning = ScheduleCalculator.nextOccurrence(config, SessionPeriod.MORNING, now)!!
        assertEquals(LocalDate.of(2026, 11, 19), morning.toLocalDate())
        assertNull(ScheduleCalculator.nextOccurrence(config.copy(enabled = false), SessionPeriod.MORNING, now))
    }

    @Test
    fun dstTransitionKeepsLocalTime() {
        // Passage à l'heure d'été en France : dimanche 28 mars 2027 ; configuration tous les jours.
        val cfg = config.copy(days = DayOfWeek.entries.toSet())
        val now = ZonedDateTime.of(LocalDate.of(2027, 3, 27), LocalTime.of(20, 0), ZONE)
        val next = ScheduleCalculator.nextOccurrence(cfg, SessionPeriod.MORNING, now)!!
        assertEquals(LocalTime.of(7, 0), next.toLocalTime())
        assertEquals(LocalDate.of(2027, 3, 28), next.toLocalDate())
    }

    @Test
    fun defaultWindowsCoverDefaultRemindersAndGrace() {
        assertEquals(emptyList<String>(), ScheduleCalculator.windowWarnings(config, com.cardiosuivi.domain.model.PeriodWindows()))
        val late = config.copy(eveningTime = LocalTime.of(22, 30))
        assertEquals(1, ScheduleCalculator.windowWarnings(late, com.cardiosuivi.domain.model.PeriodWindows()).size)
    }

    @Test
    fun slotStatesOnlyValidatedSessionsCount() {
        val wed = LocalDate.of(2026, 11, 4)
        val now = ZonedDateTime.of(wed, LocalTime.of(23, 0), ZONE)
        val sessions = listOf(
            session(wed, SessionPeriod.MORNING, readings = listOf(r(130, 80), r(130, 80), r(130, 80))),
            session(wed, SessionPeriod.EVENING, readings = listOf(r(130, 80)), status = SessionStatus.IN_PROGRESS),
        )
        val states = ScheduleCalculator.slotStates(config, sessions, wed, wed, now)
        assertEquals(SlotStatus.DONE, states.first { it.slot.period == SessionPeriod.MORNING }.status)
        // 19:00 + 180 min = 22:00 < 23:00 et séance non validée → manquée
        assertEquals(SlotStatus.MISSED, states.first { it.slot.period == SessionPeriod.EVENING }.status)
    }

    @Test
    fun graceWindow() {
        val wed = LocalDate.of(2026, 11, 4)
        val states = ScheduleCalculator.slotStates(config, emptyList(), wed, wed, ZonedDateTime.of(wed, LocalTime.of(9, 59), ZONE))
        assertEquals(SlotStatus.PENDING, states.first { it.slot.period == SessionPeriod.MORNING }.status)
        val later = ScheduleCalculator.slotStates(config, emptyList(), wed, wed, ZonedDateTime.of(wed, LocalTime.of(10, 1), ZONE))
        assertEquals(SlotStatus.MISSED, later.first { it.slot.period == SessionPeriod.MORNING }.status)
    }

    @Test
    fun noSlotsWhenNothingEnabled() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 11, 14), LocalTime.NOON, ZONE)
        assertTrue(ScheduleCalculator.nextSlots(config.copy(morningEnabled = false, eveningEnabled = false), now, 3).isEmpty())
        assertTrue(ScheduleCalculator.nextSlots(config.copy(days = emptySet()), now, 3).isEmpty())
    }
}
