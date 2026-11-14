package com.cardiosuivi.app.data

import androidx.room.withTransaction
import com.cardiosuivi.app.data.db.AppDatabase
import com.cardiosuivi.app.data.db.ReminderEventEntity
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.Observation
import com.cardiosuivi.domain.model.Profile
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import com.cardiosuivi.domain.model.Treatment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** Accès aux séances et mesures (base chiffrée). */
class SessionRepository(private val db: AppDatabase) {
    private val dao = db.sessionDao()

    fun observeAll(): Flow<List<MeasurementSession>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    suspend fun getAll(): List<MeasurementSession> = dao.getAll().map { it.toDomain() }
    fun observe(id: Long): Flow<MeasurementSession?> = dao.observe(id).map { it?.toDomain() }
    suspend fun get(id: Long): MeasurementSession? = dao.get(id)?.toDomain()
    fun observeInProgress(): Flow<MeasurementSession?> = dao.observeInProgress().map { it?.toDomain() }

    suspend fun hasValidatedSession(date: LocalDate, period: SessionPeriod): Boolean =
        dao.countValidated(date.toString(), period.name) > 0

    /** Crée une séance « en cours » (non comptabilisée tant qu'elle n'est pas validée). */
    suspend fun createSession(session: MeasurementSession): Long =
        dao.insertSession(session.copy(id = 0, status = SessionStatus.IN_PROGRESS).toEntity())

    suspend fun updateSession(session: MeasurementSession) = dao.updateSession(session.toEntity())

    suspend fun addMeasurement(sessionId: Long, m: Measurement): Long = dao.insertMeasurement(m.toEntity(sessionId).copy(id = 0))
    suspend fun updateMeasurement(m: Measurement) = dao.updateMeasurement(m.toEntity(m.sessionId))
    suspend fun deleteMeasurement(id: Long) = dao.deleteMeasurement(id)

    /** Valide la séance : elle devient « terminée » ou « incomplète » et entre dans les statistiques. */
    suspend fun validateSession(id: Long, status: SessionStatus, completedAt: Instant, symptoms: String, notes: String) {
        require(status.isValidated)
        val s = dao.get(id) ?: return
        dao.updateSession(s.session.copy(status = status.name, completedAt = completedAt.toEpochMilli(), symptoms = symptoms, notes = notes))
    }

    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    /** Insère une séance complète (restauration). */
    suspend fun insertFull(session: MeasurementSession) {
        val id = dao.insertSession(session.toEntity().copy(id = 0))
        session.measurements.forEach { dao.insertMeasurement(it.toEntity(id).copy(id = 0)) }
    }

    suspend fun recordReminderEvent(slotKey: String, action: String) =
        db.reminderEventDao().insert(ReminderEventEntity(slotKey = slotKey, action = action, at = System.currentTimeMillis()))
}

/** Profil, traitements et observations. */
class ProfileRepository(private val db: AppDatabase) {
    private val dao = db.profileDao()

    fun observeProfile(): Flow<Profile> = dao.observe().map { it?.toDomain() ?: Profile() }
    suspend fun getProfile(): Profile = dao.get()?.toDomain() ?: Profile()
    suspend fun saveProfile(p: Profile) = dao.upsert(p.toEntity())

    fun observeTreatments(): Flow<List<Treatment>> = dao.observeTreatments().map { l -> l.map { it.toDomain() } }
    suspend fun getTreatments(): List<Treatment> = dao.getTreatments().map { it.toDomain() }
    suspend fun saveTreatment(t: Treatment) = dao.upsertTreatment(t.toEntity())
    suspend fun deleteTreatment(id: Long) = dao.deleteTreatment(id)

    fun observeObservations(): Flow<List<Observation>> = dao.observeObservations().map { l -> l.map { it.toDomain() } }
    suspend fun getObservations(): List<Observation> = dao.getObservations().map { it.toDomain() }
    suspend fun saveObservation(o: Observation) = dao.upsertObservation(o.toEntity())
    suspend fun deleteObservation(id: Long) = dao.deleteObservation(id)
}

/** Opérations globales (restauration, suppression des données). */
class DataMaintenance(private val db: AppDatabase) {
    suspend fun replaceAll(
        profile: Profile?,
        treatments: List<Treatment>,
        observations: List<Observation>,
        sessions: List<MeasurementSession>,
    ) {
        db.withTransaction {
            wipeTables()
            val p = db.profileDao()
            profile?.let { p.upsert(it.toEntity()) }
            treatments.forEach { p.upsertTreatment(it.toEntity().copy(id = 0)) }
            observations.forEach { p.upsertObservation(it.toEntity().copy(id = 0)) }
            val repo = SessionRepository(db)
            sessions.forEach { repo.insertFull(it) }
        }
    }

    suspend fun wipeAll() = db.withTransaction { wipeTables() }

    private suspend fun wipeTables() {
        db.sessionDao().deleteAllSessions()
        db.profileDao().deleteAll()
        db.profileDao().deleteAllTreatments()
        db.profileDao().deleteAllObservations()
        db.reminderEventDao().deleteAll()
    }
}
