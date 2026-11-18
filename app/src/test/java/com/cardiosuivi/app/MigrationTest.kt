package com.cardiosuivi.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cardiosuivi.app.data.SessionRepository
import com.cardiosuivi.app.data.ProfileRepository
import com.cardiosuivi.app.data.db.AppDatabase
import com.cardiosuivi.domain.model.Arm
import com.cardiosuivi.domain.model.MeasurementSource
import com.cardiosuivi.domain.model.SessionPeriod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant

/**
 * Migration 1.0.x (schéma 1) → 1.1.0 (schéma 2) : la base v1 est créée à partir du schéma exporté par Room en 1.0
 * (`app/schemas/.../1.json`), remplie avec des données 1.0 (mesure OCR, pouls absent, bras droit, période « Autre »),
 * puis ouverte par Room en version 2. Room vérifie lui-même la conformité du schéma obtenu ; le test vérifie que toutes
 * les données sont conservées à l'identique et que les nouvelles colonnes restent vides.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class)
class MigrationTest {

    private fun createV1(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/com.cardiosuivi.app.data.db.AppDatabase/1.json").readText()).jsonObject["database"]!!.jsonObject
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        schema["entities"]!!.jsonArray.forEach { e ->
            val o = e.jsonObject
            val table = o["tableName"]!!.jsonPrimitive.content
            db.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            o["indices"]?.jsonArray?.forEach { i -> db.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        // Données 1.0.x
        db.execSQL("INSERT INTO sessions (id, startedAt, completedAt, localDate, period, status, protocol, plannedMeasurements, arm, symptoms, notes) VALUES " +
            "(1, 1762236300000, 1762236600000, '2025-11-04', 'MORNING', 'COMPLETED', 'USUAL', 3, 'RIGHT', 'DIZZINESS', 'note 1.0'), " +
            "(2, 1762282800000, 1762283000000, '2025-11-04', 'OTHER', 'INCOMPLETE', 'USUAL', 3, 'UNKNOWN', '', '')")
        db.execSQL("INSERT INTO measurements (id, sessionId, indexInSession, systolic, diastolic, pulse, measuredAt, source, ocrConfidence, note) VALUES " +
            "(1, 1, 1, 141, 91, 74, 1762236312345, 'OCR', 0.93, ''), (2, 1, 2, 138, 88, NULL, 1762236377000, 'OCR_CORRECTED', 0.71, ''), " +
            "(3, 1, 3, 135, 86, 70, 1762236441000, 'MANUAL', NULL, ''), (4, 2, 1, 129, 83, 72, 1762282812000, 'MANUAL', NULL, '')")
        db.execSQL("INSERT INTO treatments (id, name, dosage, usualTime, startDate, endDate, active, notes) VALUES (1, 'Traitement FICTIF', '5 mg', '08:00', '2025-01-01', NULL, 1, '')")
        db.execSQL("INSERT INTO profile (id, displayName, birthDate, sex, heightCm, weightKg, targetSystolic, targetDiastolic, medicalHistory, observations) VALUES (1, 'Profil FICTIF', NULL, 'UNSPECIFIED', 175, 72.5, 135, 85, '', '')")
        db.execSQL("INSERT INTO reminder_events (id, slotKey, action, at) VALUES (1, '2025-11-04-MORNING', 'STARTED', 1762236000000)")
        db.version = 1
        db.close()
    }

    @Test
    fun migrate1To2KeepsAllData() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val file = ctx.getDatabasePath("migration-test.db").also { it.parentFile?.mkdirs(); it.delete() }
        createV1(file)
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, file.absolutePath).addMigrations(AppDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        val sessions = SessionRepository(db).getAll().sortedBy { it.id }
        assertEquals(2, sessions.size)
        val s1 = sessions[0]
        assertEquals(SessionPeriod.MORNING, s1.period)
        assertEquals(Arm.RIGHT, s1.arm) // jamais réécrit en « bras gauche »
        assertEquals("note 1.0", s1.notes)
        assertEquals(listOf(141, 138, 135), s1.measurements.map { it.systolic })
        assertEquals(listOf(74, null, 70), s1.measurements.map { it.pulse }) // pouls absent conservé tel quel
        assertEquals(Instant.ofEpochMilli(1762236312345), s1.measurements[0].measuredAt) // horodatage à la milliseconde
        assertEquals(MeasurementSource.OCR, s1.measurements[0].source)
        assertEquals(0.93, s1.measurements[0].ocrConfidence!!, 1e-9)
        s1.measurements.forEach { assertNull(it.modifiedAt); assertNull(it.zoneId); assertNull(it.utcOffsetSeconds) }
        assertEquals(SessionPeriod.OTHER, sessions[1].period)
        assertEquals(Arm.UNKNOWN, sessions[1].arm)
        val profiles = ProfileRepository(db)
        assertEquals("Profil FICTIF", profiles.getProfile().displayName)
        assertEquals(135, profiles.getProfile().target?.systolic)
        assertEquals(listOf("Traitement FICTIF"), profiles.getTreatments().map { it.name })
        assertEquals(1, db.reminderEventDao().forSlot("2025-11-04-MORNING").size)
        assertEquals(0, db.aiAnalysisDao().getAll().size)
        assertEquals(2, db.openHelper.readableDatabase.version)
        db.close()
    }
}
