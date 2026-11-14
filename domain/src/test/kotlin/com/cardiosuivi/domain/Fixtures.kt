package com.cardiosuivi.domain

import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.MeasurementSource
import com.cardiosuivi.domain.model.ProtocolType
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import com.cardiosuivi.domain.model.Symptom
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Données de TEST uniquement (jamais utilisées par l'application). */
object Fixtures {
    val ZONE: ZoneId = ZoneId.of("Europe/Paris")
    private var nextId = 1L

    fun session(
        date: LocalDate,
        period: SessionPeriod = SessionPeriod.MORNING,
        readings: List<Triple<Int, Int, Int?>>,
        status: SessionStatus = SessionStatus.COMPLETED,
        protocol: ProtocolType = ProtocolType.USUAL,
        symptoms: Set<Symptom> = emptySet(),
        time: LocalTime = if (period == SessionPeriod.MORNING) LocalTime.of(7, 5) else LocalTime.of(19, 5),
    ): MeasurementSession {
        val id = nextId++
        val start = ZonedDateTime.of(date, time, ZONE).toInstant()
        return MeasurementSession(
            id = id,
            startedAt = start,
            completedAt = start.plusSeconds(300),
            period = period,
            status = status,
            protocol = protocol,
            localDate = date,
            symptoms = symptoms,
            measurements = readings.mapIndexed { i, (s, d, p) ->
                Measurement(
                    id = id * 10 + i, sessionId = id, index = i + 1, systolic = s, diastolic = d, pulse = p,
                    measuredAt = start.plusSeconds(60L * (i + 1)), source = MeasurementSource.MANUAL,
                )
            },
        )
    }

    fun r(s: Int, d: Int, p: Int? = 70) = Triple(s, d, p)
}
