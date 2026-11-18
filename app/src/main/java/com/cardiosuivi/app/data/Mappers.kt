package com.cardiosuivi.app.data

import com.cardiosuivi.app.data.db.MeasurementEntity
import com.cardiosuivi.app.data.db.ObservationEntity
import com.cardiosuivi.app.data.db.ProfileEntity
import com.cardiosuivi.app.data.db.SessionEntity
import com.cardiosuivi.app.data.db.SessionWithMeasurements
import com.cardiosuivi.app.data.db.TreatmentEntity
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

fun SessionWithMeasurements.toDomain(): MeasurementSession = MeasurementSession(
    id = session.id,
    startedAt = Instant.ofEpochMilli(session.startedAt),
    completedAt = session.completedAt?.let { Instant.ofEpochMilli(it) },
    period = enumOr(session.period, SessionPeriod.OTHER),
    status = enumOr(session.status, SessionStatus.IN_PROGRESS),
    protocol = enumOr(session.protocol, ProtocolType.USUAL),
    plannedMeasurements = session.plannedMeasurements,
    arm = enumOr(session.arm, Arm.UNKNOWN),
    symptoms = parseSymptoms(session.symptoms),
    notes = session.notes,
    localDate = LocalDate.parse(session.localDate),
    measurements = measurements.map { it.toDomain() }.sortedBy { it.index },
)

fun parseSymptoms(s: String): Set<Symptom> =
    s.split(',').mapNotNull { code -> Symptom.entries.firstOrNull { it.name == code.trim() } }.toSet()

fun Set<Symptom>.toCodes(): String = joinToString(",") { it.name }

fun MeasurementEntity.toDomain() = Measurement(
    id = id, sessionId = sessionId, index = indexInSession, systolic = systolic, diastolic = diastolic, pulse = pulse,
    measuredAt = Instant.ofEpochMilli(measuredAt), source = enumOr(source, MeasurementSource.MANUAL),
    ocrConfidence = ocrConfidence, note = note,
    modifiedAt = modifiedAt?.let { Instant.ofEpochMilli(it) }, zoneId = zoneId, utcOffsetSeconds = utcOffsetSeconds,
)

fun Measurement.toEntity(sessionId: Long) = MeasurementEntity(
    id = id, sessionId = sessionId, indexInSession = index, systolic = systolic, diastolic = diastolic, pulse = pulse,
    measuredAt = measuredAt.toEpochMilli(), source = source.name, ocrConfidence = ocrConfidence, note = note,
    modifiedAt = modifiedAt?.toEpochMilli(), zoneId = zoneId, utcOffsetSeconds = utcOffsetSeconds,
)

fun com.cardiosuivi.app.data.db.AiAnalysisEntity.toDomain() =
    com.cardiosuivi.domain.model.AiAnalysisRecord(id, Instant.ofEpochMilli(createdAt), provider, model, question, answer)

fun com.cardiosuivi.domain.model.AiAnalysisRecord.toEntity() =
    com.cardiosuivi.app.data.db.AiAnalysisEntity(id, createdAt.toEpochMilli(), provider, model, question, answer)

fun MeasurementSession.toEntity() = SessionEntity(
    id = id, startedAt = startedAt.toEpochMilli(), completedAt = completedAt?.toEpochMilli(), localDate = localDate.toString(),
    period = period.name, status = status.name, protocol = protocol.name, plannedMeasurements = plannedMeasurements,
    arm = arm.name, symptoms = symptoms.toCodes(), notes = notes,
)

fun ProfileEntity.toDomain() = Profile(
    displayName = displayName,
    birthDate = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    sex = enumOr(sex, Sex.UNSPECIFIED),
    heightCm = heightCm,
    weightKg = weightKg,
    target = if (targetSystolic != null && targetDiastolic != null) BpTarget(targetSystolic, targetDiastolic) else null,
    medicalHistory = medicalHistory,
    observations = observations,
)

fun Profile.toEntity() = ProfileEntity(
    displayName = displayName, birthDate = birthDate?.toString(), sex = sex.name, heightCm = heightCm, weightKg = weightKg,
    targetSystolic = target?.systolic, targetDiastolic = target?.diastolic, medicalHistory = medicalHistory, observations = observations,
)

fun TreatmentEntity.toDomain() = Treatment(
    id = id, name = name, dosage = dosage,
    usualTime = usualTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
    startDate = startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    endDate = endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    active = active, notes = notes,
)

fun Treatment.toEntity() = TreatmentEntity(
    id = id, name = name, dosage = dosage, usualTime = usualTime?.toString(), startDate = startDate?.toString(),
    endDate = endDate?.toString(), active = active, notes = notes,
)

fun ObservationEntity.toDomain() = Observation(id, LocalDate.parse(date), text)
fun Observation.toEntity() = ObservationEntity(id, date.toString(), text)
