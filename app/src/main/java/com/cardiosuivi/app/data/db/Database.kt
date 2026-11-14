package com.cardiosuivi.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Entity(tableName = "sessions", indices = [Index("localDate"), Index("status")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val completedAt: Long?,
    /** Date locale ISO (yyyy-MM-dd). */
    val localDate: String,
    val period: String,
    val status: String,
    val protocol: String,
    val plannedMeasurements: Int,
    val arm: String,
    /** Codes de symptômes séparés par des virgules. */
    val symptoms: String,
    val notes: String,
)

@Entity(
    tableName = "measurements",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val indexInSession: Int,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val measuredAt: Long,
    val source: String,
    val ocrConfidence: Double?,
    val note: String,
)

data class SessionWithMeasurements(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId") val measurements: List<MeasurementEntity>,
)

/** Profil unique (id = 1). */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String,
    val birthDate: String?,
    val sex: String,
    val heightCm: Int?,
    val weightKg: Double?,
    val targetSystolic: Int?,
    val targetDiastolic: Int?,
    val medicalHistory: String,
    val observations: String,
)

@Entity(tableName = "treatments")
data class TreatmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String,
    val usualTime: String?,
    val startDate: String?,
    val endDate: String?,
    val active: Boolean,
    val notes: String,
)

@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val text: String,
)

/** Journal des actions sur les rappels (report, ignoré, commencé) — utile au suivi des séances non réalisées. */
@Entity(tableName = "reminder_events", indices = [Index("slotKey")])
data class ReminderEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotKey: String,
    val action: String,
    val at: Long,
)

@Dao
interface SessionDao {
    @Transaction
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionWithMeasurements>>

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    suspend fun getAll(): List<SessionWithMeasurements>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionWithMeasurements?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionWithMeasurements?

    @Transaction
    @Query("SELECT * FROM sessions WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgress(): Flow<SessionWithMeasurements?>

    @Query("SELECT COUNT(*) FROM sessions WHERE localDate = :date AND period = :period AND status IN ('COMPLETED','INCOMPLETE')")
    suspend fun countValidated(date: String, period: String): Int

    @Insert
    suspend fun insertSession(s: SessionEntity): Long

    @Update
    suspend fun updateSession(s: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Insert
    suspend fun insertMeasurement(m: MeasurementEntity): Long

    @Update
    suspend fun updateMeasurement(m: MeasurementEntity)

    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun deleteMeasurement(id: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun deleteAll()

    @Query("SELECT * FROM treatments ORDER BY active DESC, name")
    fun observeTreatments(): Flow<List<TreatmentEntity>>

    @Query("SELECT * FROM treatments ORDER BY active DESC, name")
    suspend fun getTreatments(): List<TreatmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTreatment(t: TreatmentEntity): Long

    @Query("DELETE FROM treatments WHERE id = :id")
    suspend fun deleteTreatment(id: Long)

    @Query("DELETE FROM treatments")
    suspend fun deleteAllTreatments()

    @Query("SELECT * FROM observations ORDER BY date DESC, id DESC")
    fun observeObservations(): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations ORDER BY date DESC, id DESC")
    suspend fun getObservations(): List<ObservationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertObservation(o: ObservationEntity): Long

    @Query("DELETE FROM observations WHERE id = :id")
    suspend fun deleteObservation(id: Long)

    @Query("DELETE FROM observations")
    suspend fun deleteAllObservations()
}

@Dao
interface ReminderEventDao {
    @Insert
    suspend fun insert(e: ReminderEventEntity)

    @Query("SELECT * FROM reminder_events WHERE slotKey = :slotKey ORDER BY at DESC")
    suspend fun forSlot(slotKey: String): List<ReminderEventEntity>

    @Query("DELETE FROM reminder_events WHERE at < :before")
    suspend fun purgeBefore(before: Long)

    @Query("DELETE FROM reminder_events")
    suspend fun deleteAll()
}

@Database(
    entities = [SessionEntity::class, MeasurementEntity::class, ProfileEntity::class, TreatmentEntity::class, ObservationEntity::class, ReminderEventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun profileDao(): ProfileDao
    abstract fun reminderEventDao(): ReminderEventDao

    companion object {
        const val NAME = "cardiosuivi.db"

        /** Ouvre la base chiffrée par SQLCipher (AES-256) avec la phrase secrète protégée par le Keystore. */
        fun openEncrypted(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
