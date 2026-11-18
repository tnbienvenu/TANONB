package com.cardiosuivi.domain

import com.cardiosuivi.domain.entry.EntryField
import com.cardiosuivi.domain.entry.MeasurementEntry
import com.cardiosuivi.domain.entry.Timestamp
import com.cardiosuivi.domain.entry.localDateTime
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSource
import com.cardiosuivi.domain.model.PeriodWindows
import com.cardiosuivi.domain.model.SessionPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Saisie 1.1.0 (SYS, DIA, PUL obligatoires), plages matin/soir et horodatage automatique. */
class EntryAndTimestampTest {

    @Test fun validEntryAccepted() {
        val v = MeasurementEntry.validate("128", "82", "72")
        assertTrue(v.isValid); assertEquals(128, v.systolic); assertEquals(82, v.diastolic); assertEquals(72, v.pulse)
        assertTrue(v.warnings.isEmpty())
    }

    @Test fun emptySysRefused() = assertRefused(MeasurementEntry.validate("", "82", "72"), EntryField.SYS)
    @Test fun emptyDiaRefused() = assertRefused(MeasurementEntry.validate("128", " ", "72"), EntryField.DIA)
    @Test fun emptyPulRefused() = assertRefused(MeasurementEntry.validate("128", "82", ""), EntryField.PUL)

    @Test fun nonNumericRefused() {
        assertRefused(MeasurementEntry.validate("12a", "82", "72"), EntryField.SYS)
        assertRefused(MeasurementEntry.validate("128", "82,5", "72"), EntryField.DIA)
        assertRefused(MeasurementEntry.validate("128", "82", "-7"), EntryField.PUL)
        assertRefused(MeasurementEntry.validate("1280", "82", "72"), EntryField.SYS)
        assertRefused(MeasurementEntry.validate("0", "82", "72"), EntryField.SYS)
    }

    @Test fun sysLowerOrEqualDiaRefused() {
        assertRefused(MeasurementEntry.validate("80", "120", "70"), EntryField.DIA)
        assertRefused(MeasurementEntry.validate("90", "90", "70"), EntryField.DIA)
    }

    @Test fun outOfPlausibilityBoundsNeedsConfirmationButIsNotAnError() {
        val v = MeasurementEntry.validate("270", "100", "72")
        assertTrue(v.isValid)
        assertEquals(1, v.warnings.size)
    }

    @Test fun sanitizeKeepsDigitsOnly() {
        assertEquals("128", MeasurementEntry.sanitize("1 2,8"))
        assertEquals("123", MeasurementEntry.sanitize("12345"))
        assertEquals("", MeasurementEntry.sanitize("abc"))
    }

    private fun assertRefused(v: com.cardiosuivi.domain.entry.EntryValidation, f: EntryField) {
        assertFalse(v.isValid)
        assertTrue("erreur attendue sur $f : ${v.errors}", v.errors.containsKey(f))
    }

    // --- Périodes ---

    @Test fun defaultWindows() {
        val w = PeriodWindows()
        assertEquals(SessionPeriod.OTHER, w.periodOf(LocalTime.of(4, 59)))
        assertEquals(SessionPeriod.MORNING, w.periodOf(LocalTime.of(5, 0)))
        assertEquals(SessionPeriod.MORNING, w.periodOf(LocalTime.of(11, 59, 59)))
        assertEquals(SessionPeriod.OTHER, w.periodOf(LocalTime.of(12, 0)))
        assertEquals(SessionPeriod.OTHER, w.periodOf(LocalTime.of(16, 59)))
        assertEquals(SessionPeriod.EVENING, w.periodOf(LocalTime.of(17, 0)))
        assertEquals(SessionPeriod.EVENING, w.periodOf(LocalTime.of(23, 59, 59)))
        assertEquals(SessionPeriod.OTHER, w.periodOf(LocalTime.MIDNIGHT))
        assertTrue(w.isValid)
    }

    @Test fun windowCrossingMidnight() {
        val w = PeriodWindows(eveningStart = LocalTime.of(18, 0), eveningEnd = LocalTime.of(1, 0))
        assertEquals(SessionPeriod.EVENING, w.periodOf(LocalTime.of(0, 30)))
        assertEquals(SessionPeriod.OTHER, w.periodOf(LocalTime.of(1, 0)))
        assertTrue(w.isValid)
    }

    @Test fun overlappingWindowsInvalid() {
        assertFalse(PeriodWindows(morningEnd = LocalTime.of(18, 0)).isValid)
    }

    // --- Horodatage ---

    @Test fun timestampUsesPhoneClockZoneAndKeepsSeconds() {
        val clock = Clock.fixed(Instant.parse("2026-07-01T05:05:12.345Z"), ZoneId.of("Europe/Paris"))
        val t = Timestamp.now(clock)
        assertEquals(Instant.parse("2026-07-01T05:05:12.345Z"), t.instant)
        assertEquals("Europe/Paris", t.zoneId)
        assertEquals(7200, t.utcOffsetSeconds) // heure d'été
        val m = Measurement(index = 1, systolic = 128, diastolic = 82, pulse = 72, measuredAt = t.instant, source = MeasurementSource.MANUAL,
            zoneId = t.zoneId, utcOffsetSeconds = t.utcOffsetSeconds)
        // Restitution dans le fuseau d'origine même si le téléphone a changé de fuseau.
        val local = m.localDateTime(fallback = ZoneId.of("America/New_York"))
        assertEquals(LocalTime.of(7, 5, 12, 345_000_000), local.toLocalTime())
        assertEquals(SessionPeriod.MORNING, PeriodWindows().periodOf(local.toLocalTime()))
    }

    @Test fun winterOffset() {
        val t = Timestamp.now(Clock.fixed(Instant.parse("2026-01-15T18:00:00Z"), ZoneId.of("Europe/Paris")))
        assertEquals(3600, t.utcOffsetSeconds)
    }

    @Test fun legacyMeasurementWithoutZoneUsesCurrentZone() {
        val m = Measurement(index = 1, systolic = 128, diastolic = 82, pulse = null, measuredAt = Instant.parse("2026-01-15T06:00:00Z"), source = MeasurementSource.OCR)
        assertNull(m.zoneId)
        assertEquals(LocalTime.of(7, 0), m.localDateTime(ZoneId.of("Europe/Paris")).toLocalTime())
    }

    @Test fun dateChangeAroundMidnight() {
        val zone = ZoneId.of("Europe/Paris")
        val before = Timestamp.now(Clock.fixed(Instant.parse("2026-11-18T22:59:30Z"), zone)) // 23:59:30 locale
        val after = Timestamp.now(Clock.fixed(Instant.parse("2026-11-18T23:00:45Z"), zone))  // 00:00:45 le 19
        fun m(t: Timestamp) = Measurement(index = 1, systolic = 120, diastolic = 80, pulse = 70, measuredAt = t.instant, source = MeasurementSource.MANUAL, zoneId = t.zoneId)
        assertEquals(java.time.LocalDate.of(2026, 11, 18), m(before).localDateTime().toLocalDate())
        assertEquals(java.time.LocalDate.of(2026, 11, 19), m(after).localDateTime().toLocalDate())
    }
}
