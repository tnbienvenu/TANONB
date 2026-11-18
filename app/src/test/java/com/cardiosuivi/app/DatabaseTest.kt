package com.cardiosuivi.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cardiosuivi.app.data.DataMaintenance
import com.cardiosuivi.app.data.ProfileRepository
import com.cardiosuivi.app.data.SessionRepository
import com.cardiosuivi.app.data.db.AppDatabase
import com.cardiosuivi.domain.model.BpTarget
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.MeasurementSource
import com.cardiosuivi.domain.model.Observation
import com.cardiosuivi.domain.model.Profile
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import com.cardiosuivi.domain.model.Symptom
import com.cardiosuivi.domain.model.Treatment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Tests de la couche de persistance (Room) sur base en mémoire.
 * Le chiffrement SQLCipher, qui nécessite la bibliothèque native Android, est testé dans androidTest.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class)
class DatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var sessions: SessionRepository
    private lateinit var profiles: ProfileRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        sessions = SessionRepository(db)
        profiles = ProfileRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun newSession(date: LocalDate = LocalDate.of(2026, 11, 4)) = MeasurementSession(
        startedAt = Instant.parse("2026-11-04T06:05:00Z"), period = SessionPeriod.MORNING, status = SessionStatus.IN_PROGRESS, localDate = date,
    )

    @Test
    fun sessionLifecycleOnlyValidatedCounts() = runTest {
        val id = sessions.createSession(newSession())
        sessions.addMeasurement(id, Measurement(index = 1, systolic = 132, diastolic = 84, pulse = 70, measuredAt = Instant.parse("2026-11-04T06:06:00Z"), source = MeasurementSource.OCR, ocrConfidence = 0.9))
        sessions.addMeasurement(id, Measurement(index = 2, systolic = 128, diastolic = 82, pulse = null, measuredAt = Instant.parse("2026-11-04T06:07:00Z"), source = MeasurementSource.MANUAL))
        assertFalse(sessions.hasValidatedSession(LocalDate.of(2026, 11, 4), SessionPeriod.MORNING))
        assertEquals(id, sessions.observeInProgress().first()!!.id)

        sessions.validateSession(id, SessionStatus.INCOMPLETE, Instant.parse("2026-11-04T06:10:00Z"), "DIZZINESS", "note")
        assertTrue(sessions.hasValidatedSession(LocalDate.of(2026, 11, 4), SessionPeriod.MORNING))
        assertNull(sessions.observeInProgress().first())

        val s = sessions.get(id)!!
        assertEquals(SessionStatus.INCOMPLETE, s.status)
        assertEquals(setOf(Symptom.DIZZINESS), s.symptoms)
        assertEquals(listOf(1, 2), s.measurements.map { it.index })
        assertNull(s.measurements[1].pulse)
        assertEquals(MeasurementSource.OCR, s.measurements[0].source)
    }

    @Test
    fun deletingSessionCascadesMeasurements() = runTest {
        val id = sessions.createSession(newSession())
        sessions.addMeasurement(id, Measurement(index = 1, systolic = 130, diastolic = 80, pulse = 60, measuredAt = Instant.now(), source = MeasurementSource.MANUAL))
        sessions.deleteSession(id)
        assertTrue(sessions.getAll().isEmpty())
        assertEquals(0, db.query("SELECT * FROM measurements", null).use { it.count })
    }

    @Test
    fun profileTreatmentsAndRestoreReplaceAll() = runTest {
        profiles.saveProfile(Profile(displayName = "Ancien", target = BpTarget(135, 85)))
        profiles.saveTreatment(Treatment(name = "Ancien traitement"))
        sessions.createSession(newSession())

        val restored = MeasurementSession(
            startedAt = Instant.parse("2026-10-01T17:00:00Z"), period = SessionPeriod.EVENING, status = SessionStatus.COMPLETED,
            localDate = LocalDate.of(2026, 10, 1),
            measurements = (1..3).map { Measurement(index = it, systolic = 120 + it, diastolic = 80, pulse = 65, measuredAt = Instant.parse("2026-10-01T17:0${it}:00Z"), source = MeasurementSource.MANUAL) },
        )
        DataMaintenance(db).replaceAll(
            Profile(displayName = "Nouveau", birthDate = LocalDate.of(1970, 1, 2), target = BpTarget(130, 80)),
            listOf(Treatment(name = "Traitement A", dosage = "1 cp", usualTime = LocalTime.of(8, 0))),
            listOf(Observation(date = LocalDate.of(2026, 10, 2), text = "obs")),
            listOf(restored),
        )
        val p = profiles.getProfile()
        assertEquals("Nouveau", p.displayName)
        assertEquals(BpTarget(130, 80), p.target)
        assertEquals(listOf("Traitement A"), profiles.getTreatments().map { it.name })
        assertEquals(LocalTime.of(8, 0), profiles.getTreatments().first().usualTime)
        assertEquals(1, profiles.getObservations().size)
        val all = sessions.getAll()
        assertEquals(1, all.size)
        assertEquals(3, all.first().measurements.size)
        assertEquals(SessionStatus.COMPLETED, all.first().status)

        DataMaintenance(db).wipeAll()
        assertTrue(sessions.getAll().isEmpty())
        assertEquals("", profiles.getProfile().displayName)
    }
}

/** Horodatage 1.1.0 : fuseau, décalage et date de correction persistés ; horodatage initial inchangé par une correction. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class)
class TimestampPersistenceTest {
    @Test
    fun correctionKeepsMeasuredAtAndStoresModifiedAt() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = SessionRepository(db)
        val at = Instant.parse("2026-11-18T06:05:12.345Z")
        val id = repo.createSession(MeasurementSession(startedAt = at, period = SessionPeriod.MORNING, status = SessionStatus.IN_PROGRESS,
            localDate = LocalDate.of(2026, 11, 18), arm = com.cardiosuivi.domain.model.Arm.LEFT))
        val mid = repo.addMeasurement(id, Measurement(index = 1, systolic = 128, diastolic = 82, pulse = 72, measuredAt = at,
            source = MeasurementSource.MANUAL, zoneId = "Europe/Paris", utcOffsetSeconds = 3600))
        // Simple consultation : rien ne change.
        val read1 = repo.get(id)!!.measurements.single()
        assertEquals(at, read1.measuredAt); assertNull(read1.modifiedAt)
        assertEquals("Europe/Paris", read1.zoneId); assertEquals(3600, read1.utcOffsetSeconds)
        // Correction.
        val fix = Instant.parse("2026-11-18T06:20:00Z")
        repo.updateMeasurement(read1.copy(systolic = 126, modifiedAt = fix))
        val read2 = repo.get(id)!!.measurements.single()
        assertEquals(mid, read2.id)
        assertEquals(126, read2.systolic)
        assertEquals(at, read2.measuredAt)
        assertEquals(fix, read2.modifiedAt)
        assertEquals(com.cardiosuivi.domain.model.Arm.LEFT, repo.get(id)!!.arm)
        db.close()
    }
}
