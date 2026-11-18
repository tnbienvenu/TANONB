package com.cardiosuivi.app.backup

import android.content.Context
import android.net.Uri
import com.cardiosuivi.app.AppContainer
import com.cardiosuivi.app.BuildConfig
import com.cardiosuivi.app.data.enumOr
import com.cardiosuivi.domain.backup.BackupCrypto
import com.cardiosuivi.domain.backup.BackupException
import com.cardiosuivi.domain.backup.BackupMeasurement
import com.cardiosuivi.domain.backup.BackupObservation
import com.cardiosuivi.domain.backup.BackupPayload
import com.cardiosuivi.domain.backup.BackupProfile
import com.cardiosuivi.domain.backup.BackupSession
import com.cardiosuivi.domain.backup.BackupTreatment
import com.cardiosuivi.domain.model.Arm
import com.cardiosuivi.domain.model.BpTarget
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.MeasurementSource
import com.cardiosuivi.domain.model.Observation
import com.cardiosuivi.domain.model.Profile
import com.cardiosuivi.domain.model.ProtocolType
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import com.cardiosuivi.domain.model.Sex
import com.cardiosuivi.domain.model.Symptom
import com.cardiosuivi.domain.model.Treatment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class RestoreSummary(val sessions: Int, val measurements: Int, val treatments: Int, val observations: Int, val createdAt: Instant)

/**
 * Sauvegarde chiffrée par mot de passe (BackupCrypto : PBKDF2 + AES-256-GCM) vers un fichier choisi par l'utilisateur
 * (Storage Access Framework). Les clés d'API et les consentements ne sont jamais inclus.
 */
class BackupManager(private val context: Context, private val c: AppContainer) {

    suspend fun buildPayload(): BackupPayload {
        val profile = c.profile.getProfile()
        return BackupPayload(
            appVersion = BuildConfig.VERSION_NAME,
            createdAtEpochMs = System.currentTimeMillis(),
            profile = BackupProfile(
                profile.displayName, profile.birthDate?.toString(), profile.sex.name, profile.heightCm, profile.weightKg,
                profile.target?.systolic, profile.target?.diastolic, profile.medicalHistory, profile.observations,
            ),
            treatments = c.profile.getTreatments().map { BackupTreatment(it.name, it.dosage, it.usualTime?.toString(), it.startDate?.toString(), it.endDate?.toString(), it.active, it.notes) },
            observations = c.profile.getObservations().map { BackupObservation(it.date.toString(), it.text) },
            sessions = c.sessions.getAll().filter { it.status.isValidated }.map { s ->
                BackupSession(
                    s.startedAt.toEpochMilli(), s.completedAt?.toEpochMilli(), s.localDate.toString(), s.period.name, s.status.name,
                    s.protocol.name, s.plannedMeasurements, s.arm.name, s.symptoms.map { it.name }, s.notes,
                    s.measurements.map { m -> BackupMeasurement(m.index, m.systolic, m.diastolic, m.pulse, m.measuredAt.toEpochMilli(), m.source.name, m.ocrConfidence, m.note,
                        m.modifiedAt?.toEpochMilli(), m.zoneId, m.utcOffsetSeconds) },
                )
            },
            settings = c.settings.exportable(),
            aiAnalyses = c.aiAnalyses.getAll().map { com.cardiosuivi.domain.backup.BackupAiAnalysis(it.createdAt.toEpochMilli(), it.provider, it.model, it.question, it.answer) },
        )
    }

    suspend fun export(uri: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        val payload = buildPayload()
        val bytes = BackupCrypto.encryptPayload(payload, password)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: throw BackupException("Impossible d'écrire le fichier.")
        payload.sessions.size
    }

    suspend fun read(uri: Uri, password: CharArray): BackupPayload = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw BackupException("Impossible de lire le fichier.")
        if (bytes.size > 50 * 1024 * 1024) throw BackupException("Fichier trop volumineux.")
        BackupCrypto.decryptPayload(bytes, password)
    }

    fun summarize(p: BackupPayload) = RestoreSummary(
        p.sessions.size, p.sessions.sumOf { it.measurements.size }, p.treatments.size, p.observations.size, Instant.ofEpochMilli(p.createdAtEpochMs),
    )

    /** Remplace TOUTES les données locales par le contenu de la sauvegarde. */
    suspend fun restore(p: BackupPayload) = withContext(Dispatchers.IO) {
        val profile = p.profile?.let {
            Profile(
                displayName = it.displayName,
                birthDate = it.birthDate?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() },
                sex = enumOr(it.sex, Sex.UNSPECIFIED),
                heightCm = it.heightCm, weightKg = it.weightKg,
                target = if (it.targetSystolic != null && it.targetDiastolic != null) BpTarget(it.targetSystolic!!, it.targetDiastolic!!) else null,
                medicalHistory = it.medicalHistory, observations = it.observations,
            )
        }
        val treatments = p.treatments.map {
            Treatment(
                name = it.name, dosage = it.dosage,
                usualTime = it.usualTime?.let { t -> runCatching { LocalTime.parse(t) }.getOrNull() },
                startDate = it.startDate?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() },
                endDate = it.endDate?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() },
                active = it.active, notes = it.notes,
            )
        }
        val observations = p.observations.mapNotNull { o -> runCatching { Observation(date = LocalDate.parse(o.date), text = o.text) }.getOrNull() }
        val sessions = p.sessions.map { s ->
            MeasurementSession(
                startedAt = Instant.ofEpochMilli(s.startedAtEpochMs),
                completedAt = s.completedAtEpochMs?.let { Instant.ofEpochMilli(it) },
                period = enumOr(s.period, SessionPeriod.OTHER),
                status = enumOr(s.status, SessionStatus.INCOMPLETE).let { if (it == SessionStatus.IN_PROGRESS) SessionStatus.INCOMPLETE else it },
                protocol = enumOr(s.protocol, ProtocolType.USUAL),
                plannedMeasurements = s.plannedMeasurements,
                arm = enumOr(s.arm, Arm.UNKNOWN),
                symptoms = s.symptoms.mapNotNull { n -> Symptom.entries.firstOrNull { it.name == n } }.toSet(),
                notes = s.notes,
                localDate = LocalDate.parse(s.localDate),
                measurements = s.measurements.map { m ->
                    Measurement(
                        index = m.index, systolic = m.systolic, diastolic = m.diastolic, pulse = m.pulse,
                        measuredAt = Instant.ofEpochMilli(m.measuredAtEpochMs), source = enumOr(m.source, MeasurementSource.MANUAL),
                        ocrConfidence = m.ocrConfidence, note = m.note,
                        modifiedAt = m.modifiedAtEpochMs?.let { Instant.ofEpochMilli(it) }, zoneId = m.zoneId, utcOffsetSeconds = m.utcOffsetSeconds,
                    )
                },
            )
        }
        val ai = p.aiAnalyses.map { com.cardiosuivi.domain.model.AiAnalysisRecord(createdAt = Instant.ofEpochMilli(it.createdAtEpochMs), provider = it.provider, model = it.model, question = it.question, answer = it.answer) }
        c.maintenance.replaceAll(profile, treatments, observations, sessions, ai)
        c.settings.importSettings(p.settings)
        c.reminderScheduler.rescheduleAll(c.settings.current().reminders)
    }
}
