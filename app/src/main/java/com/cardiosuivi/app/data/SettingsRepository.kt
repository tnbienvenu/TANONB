package com.cardiosuivi.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cardiosuivi.domain.ai.AiSharingOptions
import com.cardiosuivi.domain.model.AggregationMethod
import com.cardiosuivi.domain.model.ReminderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class AiProvider(val label: String) { NONE("Aucun (analyse locale uniquement)"), ANTHROPIC("Anthropic Claude"), OPENAI("OpenAI") }

/** Paramètres de l'application (non sensibles ; les clés d'API sont dans SecretStore). */
data class AppSettings(
    val onboardingAccepted: Boolean = false,
    val reminders: ReminderConfig = ReminderConfig(),
    val intervalSeconds: Int = 60,
    val measurementsPerSession: Int = 3,
    val aggregation: AggregationMethod = AggregationMethod.ALL_MEASUREMENTS,
    val biometricLock: Boolean = false,
    val secureScreen: Boolean = true,
    val emergencyNumber: String = "112",
    val aiProvider: AiProvider = AiProvider.NONE,
    val anthropicModel: String = DEFAULT_ANTHROPIC_MODEL,
    val openAiModel: String = DEFAULT_OPENAI_MODEL,
    /** Horodatage du consentement à la transmission des données au fournisseur, 0 = pas de consentement. */
    val anthropicConsentAt: Long = 0,
    val openAiConsentAt: Long = 0,
    val sharing: AiSharingOptions = AiSharingOptions(),
    val hbpmStartDate: LocalDate? = null,
    val lastRescheduleAt: Long = 0,
) {
    fun consentFor(p: AiProvider): Long = when (p) {
        AiProvider.ANTHROPIC -> anthropicConsentAt
        AiProvider.OPENAI -> openAiConsentAt
        AiProvider.NONE -> 0
    }

    companion object {
        const val DEFAULT_ANTHROPIC_MODEL = "claude-opus-5-5"
        const val DEFAULT_OPENAI_MODEL = "gpt-5"
    }
}

private val Context.dataStore by preferencesDataStore(name = "cardiosuivi_settings")

class SettingsRepository(private val context: Context) {

    private object K {
        val onboarding = booleanPreferencesKey("onboarding_accepted")
        val remEnabled = booleanPreferencesKey("rem_enabled")
        val remDays = stringPreferencesKey("rem_days")
        val remMorningOn = booleanPreferencesKey("rem_morning_on")
        val remMorning = stringPreferencesKey("rem_morning")
        val remEveningOn = booleanPreferencesKey("rem_evening_on")
        val remEvening = stringPreferencesKey("rem_evening")
        val remGrace = intPreferencesKey("rem_grace")
        val interval = intPreferencesKey("interval_seconds")
        val perSession = intPreferencesKey("per_session")
        val aggregation = stringPreferencesKey("aggregation")
        val biometric = booleanPreferencesKey("biometric")
        val secure = booleanPreferencesKey("secure_screen")
        val emergency = stringPreferencesKey("emergency_number")
        val aiProvider = stringPreferencesKey("ai_provider")
        val anthropicModel = stringPreferencesKey("anthropic_model")
        val openAiModel = stringPreferencesKey("openai_model")
        val anthropicConsent = longPreferencesKey("anthropic_consent")
        val openAiConsent = longPreferencesKey("openai_consent")
        val shareAge = booleanPreferencesKey("share_age")
        val shareTreat = booleanPreferencesKey("share_treatments")
        val shareHistory = booleanPreferencesKey("share_history")
        val shareSessions = booleanPreferencesKey("share_sessions")
        val hbpmStart = stringPreferencesKey("hbpm_start")
        val lastReschedule = longPreferencesKey("last_reschedule")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        val days = this[K.remDays]?.let { s ->
            s.split(',').mapNotNull { n -> DayOfWeek.entries.firstOrNull { it.name == n } }.toSet()
        } ?: d.reminders.days
        return AppSettings(
            onboardingAccepted = this[K.onboarding] ?: false,
            reminders = ReminderConfig(
                enabled = this[K.remEnabled] ?: d.reminders.enabled,
                days = days,
                morningEnabled = this[K.remMorningOn] ?: d.reminders.morningEnabled,
                morningTime = this[K.remMorning]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: d.reminders.morningTime,
                eveningEnabled = this[K.remEveningOn] ?: d.reminders.eveningEnabled,
                eveningTime = this[K.remEvening]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: d.reminders.eveningTime,
                missedGraceMinutes = this[K.remGrace] ?: d.reminders.missedGraceMinutes,
            ),
            intervalSeconds = this[K.interval] ?: d.intervalSeconds,
            measurementsPerSession = this[K.perSession] ?: d.measurementsPerSession,
            aggregation = enumOr(this[K.aggregation], d.aggregation),
            biometricLock = this[K.biometric] ?: d.biometricLock,
            secureScreen = this[K.secure] ?: d.secureScreen,
            emergencyNumber = this[K.emergency] ?: d.emergencyNumber,
            aiProvider = enumOr(this[K.aiProvider], d.aiProvider),
            anthropicModel = this[K.anthropicModel] ?: d.anthropicModel,
            openAiModel = this[K.openAiModel] ?: d.openAiModel,
            anthropicConsentAt = this[K.anthropicConsent] ?: 0,
            openAiConsentAt = this[K.openAiConsent] ?: 0,
            sharing = AiSharingOptions(
                includeAgeAndSex = this[K.shareAge] ?: true,
                includeTreatments = this[K.shareTreat] ?: false,
                includeMedicalHistory = this[K.shareHistory] ?: false,
                includeRecentSessions = this[K.shareSessions] ?: true,
            ),
            hbpmStartDate = this[K.hbpmStart]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            lastRescheduleAt = this[K.lastReschedule] ?: 0,
        )
    }

    suspend fun setOnboardingAccepted() = context.dataStore.edit { it[K.onboarding] = true }

    suspend fun setReminders(c: ReminderConfig) = context.dataStore.edit {
        it[K.remEnabled] = c.enabled
        it[K.remDays] = c.days.sortedBy { d -> d.value }.joinToString(",") { d -> d.name }
        it[K.remMorningOn] = c.morningEnabled
        it[K.remMorning] = c.morningTime.toString()
        it[K.remEveningOn] = c.eveningEnabled
        it[K.remEvening] = c.eveningTime.toString()
        it[K.remGrace] = c.missedGraceMinutes
    }

    suspend fun setProtocol(intervalSeconds: Int, perSession: Int) = context.dataStore.edit {
        it[K.interval] = intervalSeconds.coerceIn(30, 600)
        it[K.perSession] = perSession.coerceIn(1, 5)
    }

    suspend fun setAggregation(m: AggregationMethod) = context.dataStore.edit { it[K.aggregation] = m.name }
    suspend fun setBiometric(on: Boolean) = context.dataStore.edit { it[K.biometric] = on }
    suspend fun setSecureScreen(on: Boolean) = context.dataStore.edit { it[K.secure] = on }
    suspend fun setEmergencyNumber(n: String) = context.dataStore.edit { it[K.emergency] = n.filter { c -> c.isDigit() || c == '+' }.ifBlank { "112" } }
    suspend fun setAiProvider(p: AiProvider) = context.dataStore.edit { it[K.aiProvider] = p.name }
    suspend fun setModels(anthropic: String, openAi: String) = context.dataStore.edit {
        it[K.anthropicModel] = anthropic.trim().ifBlank { AppSettings.DEFAULT_ANTHROPIC_MODEL }
        it[K.openAiModel] = openAi.trim().ifBlank { AppSettings.DEFAULT_OPENAI_MODEL }
    }

    suspend fun setConsent(p: AiProvider, granted: Boolean) = context.dataStore.edit {
        val v = if (granted) System.currentTimeMillis() else 0L
        when (p) {
            AiProvider.ANTHROPIC -> it[K.anthropicConsent] = v
            AiProvider.OPENAI -> it[K.openAiConsent] = v
            AiProvider.NONE -> Unit
        }
    }

    suspend fun setSharing(o: AiSharingOptions) = context.dataStore.edit {
        it[K.shareAge] = o.includeAgeAndSex
        it[K.shareTreat] = o.includeTreatments
        it[K.shareHistory] = o.includeMedicalHistory
        it[K.shareSessions] = o.includeRecentSessions
    }

    suspend fun setHbpmStart(d: LocalDate?) = context.dataStore.edit {
        if (d == null) it.remove(K.hbpmStart) else it[K.hbpmStart] = d.toString()
    }

    suspend fun markRescheduled() = context.dataStore.edit { it[K.lastReschedule] = System.currentTimeMillis() }

    /** Paramètres exportables dans une sauvegarde (aucun secret, aucun consentement). */
    suspend fun exportable(): Map<String, String> {
        val s = current()
        return mapOf(
            "reminders.enabled" to s.reminders.enabled.toString(),
            "reminders.days" to s.reminders.days.joinToString(",") { it.name },
            "reminders.morningEnabled" to s.reminders.morningEnabled.toString(),
            "reminders.morningTime" to s.reminders.morningTime.toString(),
            "reminders.eveningEnabled" to s.reminders.eveningEnabled.toString(),
            "reminders.eveningTime" to s.reminders.eveningTime.toString(),
            "reminders.grace" to s.reminders.missedGraceMinutes.toString(),
            "protocol.interval" to s.intervalSeconds.toString(),
            "protocol.perSession" to s.measurementsPerSession.toString(),
            "aggregation" to s.aggregation.name,
            "emergencyNumber" to s.emergencyNumber,
        )
    }

    suspend fun importSettings(m: Map<String, String>) {
        val cur = current()
        val r = cur.reminders
        setReminders(
            ReminderConfig(
                enabled = m["reminders.enabled"]?.toBooleanStrictOrNull() ?: r.enabled,
                days = m["reminders.days"]?.split(',')?.mapNotNull { n -> DayOfWeek.entries.firstOrNull { it.name == n } }?.toSet() ?: r.days,
                morningEnabled = m["reminders.morningEnabled"]?.toBooleanStrictOrNull() ?: r.morningEnabled,
                morningTime = m["reminders.morningTime"]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: r.morningTime,
                eveningEnabled = m["reminders.eveningEnabled"]?.toBooleanStrictOrNull() ?: r.eveningEnabled,
                eveningTime = m["reminders.eveningTime"]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: r.eveningTime,
                missedGraceMinutes = m["reminders.grace"]?.toIntOrNull() ?: r.missedGraceMinutes,
            ),
        )
        setProtocol(m["protocol.interval"]?.toIntOrNull() ?: cur.intervalSeconds, m["protocol.perSession"]?.toIntOrNull() ?: cur.measurementsPerSession)
        m["aggregation"]?.let { setAggregation(enumOr(it, cur.aggregation)) }
        m["emergencyNumber"]?.let { setEmergencyNumber(it) }
    }

    suspend fun resetAll() = context.dataStore.edit { it.clear() }
}
