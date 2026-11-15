package com.cardiosuivi.app

import android.content.Context
import android.util.Log
import com.cardiosuivi.app.ai.AiService
import com.cardiosuivi.app.backup.BackupManager
import com.cardiosuivi.app.data.DataMaintenance
import com.cardiosuivi.app.data.ProfileRepository
import com.cardiosuivi.app.data.SessionRepository
import com.cardiosuivi.app.data.SettingsRepository
import com.cardiosuivi.app.data.db.AppDatabase
import com.cardiosuivi.app.ocr.OcrEngine
import com.cardiosuivi.app.reminders.ReminderScheduler
import com.cardiosuivi.app.security.AppLock
import com.cardiosuivi.app.security.DatabaseKeyManager
import com.cardiosuivi.app.security.SecretStore
import java.io.File

/**
 * Conteneur de dépendances manuel (choix volontaire plutôt qu'un framework d'injection :
 * moins de génération de code, graphe d'objets explicite et facile à tester).
 */
class AppContainer(private val context: Context) {

    val appContext: Context get() = context

    val settings = SettingsRepository(context)
    val secrets = SecretStore(context)
    val keyManager = DatabaseKeyManager(context)
    val reminderScheduler = ReminderScheduler(context)
    val appLock = AppLock()

    /** Erreur d'ouverture de la base (ex. : clé Keystore invalidée). Affichée à l'utilisateur. */
    @Volatile var databaseError: Throwable? = null
        private set

    private val dbLock = Any()
    @Volatile private var _db: AppDatabase? = null

    fun databaseOrNull(): AppDatabase? {
        _db?.let { return it }
        synchronized(dbLock) {
            _db?.let { return it }
            return try {
                val pass = keyManager.getOrCreatePassphrase()
                AppDatabase.openEncrypted(context, pass).also {
                    // Force l'ouverture pour détecter immédiatement une phrase secrète invalide.
                    it.openHelper.writableDatabase
                    _db = it
                    databaseError = null
                }
            } catch (e: Throwable) {
                Log.e("CardioSuivi", "Ouverture de la base chiffrée impossible", e)
                databaseError = e
                null
            }
        }
    }

    val database: AppDatabase get() = databaseOrNull() ?: throw IllegalStateException("Base de données indisponible", databaseError)

    fun sessionsOrNull(): SessionRepository? = databaseOrNull()?.let { SessionRepository(it) }

    // Dépôts recréés à partir de la base COURANTE : après une suppression complète des données,
    // aucun composant ne doit conserver une référence vers l'ancienne base fermée.
    val sessions: SessionRepository get() = SessionRepository(database)
    val profile: ProfileRepository get() = ProfileRepository(database)
    val maintenance: DataMaintenance get() = DataMaintenance(database)
    val ocr: OcrEngine by lazy { OcrEngine() }

    private val http by lazy { com.cardiosuivi.app.ai.defaultHttpClientForApp() }

    /** Adresse de remplacement des API d'IA, réservée aux tests automatisés (serveur simulé local). */
    @Volatile var aiBaseUrlOverride: String? = null

    val ai: AiService
        get() = AiService(
            settings, secrets,
            com.cardiosuivi.app.ai.AnthropicClient(http, aiBaseUrlOverride ?: "https://api.anthropic.com"),
            com.cardiosuivi.app.ai.OpenAiClient(http, aiBaseUrlOverride ?: "https://api.openai.com"),
        )
    val backup: BackupManager by lazy { BackupManager(context, this) }

    /**
     * Suppression définitive de toutes les données : base, clé, secrets, paramètres, fichiers temporaires.
     * L'application redémarre ensuite sur une base vide.
     */
    suspend fun destroyAllData() {
        runCatching { databaseOrNull()?.let { DataMaintenance(it).wipeAll() } }
        synchronized(dbLock) {
            _db?.close()
            _db = null
        }
        context.deleteDatabase(AppDatabase.NAME)
        keyManager.destroy()
        secrets.clearAll()
        settings.resetAll()
        reminderScheduler.cancelAll()
        File(context.cacheDir, "exports").deleteRecursively()
        File(context.cacheDir, "photos").deleteRecursively()
        // Les paramètres reviennent aux valeurs initiales (rappels activés) : ils doivent être reprogrammés
        // immédiatement, sinon aucun rappel ne serait émis avant le prochain démarrage du processus (anomalie A09).
        reminderScheduler.rescheduleAll(settings.current().reminders)
        settings.markRescheduled()
    }
}
