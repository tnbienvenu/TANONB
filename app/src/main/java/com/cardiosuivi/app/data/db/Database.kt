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
    // Version 2 (1.1.0) : colonnes ajoutées, NULL pour les mesures antérieures (jamais complétées rétroactivement).
    val modifiedAt: Long? = null,
    val zoneId: String? = null,
    val utcOffsetSeconds: Int? = null,
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

/** Interprétations IA conservées (version 2). */
@Entity(tableName = "ai_analyses", indices = [Index("createdAt")])
data class AiAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val provider: String,
    val model: String,
    val question: String,
    val answer: String,
)

@Dao
interface AiAnalysisDao {
    @Insert
    suspend fun insert(a: AiAnalysisEntity): Long

    @Query("SELECT * FROM ai_analyses ORDER BY createdAt DESC LIMIT 1")
    fun observeLatest(): Flow<AiAnalysisEntity?>

    @Query("SELECT * FROM ai_analyses ORDER BY createdAt DESC")
    suspend fun getAll(): List<AiAnalysisEntity>

    @Query("DELETE FROM ai_analyses")
    suspend fun deleteAll()
}

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
    entities = [SessionEntity::class, MeasurementEntity::class, ProfileEntity::class, TreatmentEntity::class, ObservationEntity::class, ReminderEventEntity::class, AiAnalysisEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun profileDao(): ProfileDao
    abstract fun reminderEventDao(): ReminderEventDao
    abstract fun aiAnalysisDao(): AiAnalysisDao

    companion object {
        const val NAME = "cardiosuivi.db"

        /**
         * 1.0.x → 1.1.0 : ajout de colonnes facultatives et d'une table. Aucune donnée existante n'est modifiée ni
         * supprimée (pas de migration destructive).
         */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE measurements ADD COLUMN modifiedAt INTEGER")
                db.execSQL("ALTER TABLE measurements ADD COLUMN zoneId TEXT")
                db.execSQL("ALTER TABLE measurements ADD COLUMN utcOffsetSeconds INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_analyses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`provider` TEXT NOT NULL, `model` TEXT NOT NULL, `question` TEXT NOT NULL, `answer` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_analyses_createdAt` ON `ai_analyses` (`createdAt`)")
            }
        }

        /** Ouvre la base chiffrée par SQLCipher (AES-256) avec la phrase secrète protégée par le Keystore. */
        fun openEncrypted(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
