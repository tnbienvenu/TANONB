package com.cardiosuivi.domain.analysis

import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.ProtocolType
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.reference.MedicalReference
import com.cardiosuivi.domain.reference.MedicalThresholds
import com.cardiosuivi.domain.stats.Descriptive
import java.time.LocalDate

/**
 * Protocole complémentaire d'automesure standardisée (distinct du calendrier habituel mercredi–vendredi).
 *
 * D'après ESH 2023 / ESH 2021 / ESC 2024 : 7 jours consécutifs (au minimum 3), deux mesures le matin
 * et deux le soir à 1–2 minutes d'intervalle ; les mesures du premier jour sont écartées ; la moyenne de
 * toutes les mesures restantes est comparée au seuil d'automesure de 135/85 mmHg.
 * L'application propose 2 ou 3 mesures par séance ; toutes les mesures validées sont moyennées.
 */
object HbpmProtocol {

    enum class Status(val label: String) {
        NOT_STARTED("Non commencé"),
        IN_PROGRESS("En cours"),
        SUFFICIENT_MINIMUM("Minimum atteint (3 jours exploitables)"),
        COMPLETE("Protocole complet (7 jours)"),
    }

    enum class Classification(val label: String) {
        NON_ELEVATED("Moyenne < 120/70 mmHg (non élevée)"),
        ELEVATED("Moyenne entre 120/70 et 135/85 mmHg (élevée)"),
        HYPERTENSION_RANGE("Moyenne ≥ 135/85 mmHg (seuil d'hypertension à domicile atteint)"),
    }

    data class DayDetail(val date: LocalDate, val morningSessions: Int, val eveningSessions: Int, val readings: Int, val excluded: Boolean)

    data class Result(
        val startDate: LocalDate?,
        val status: Status,
        val days: List<DayDetail>,
        val usableDays: Int,
        val readingsUsed: Int,
        val meanSystolic: Double?,
        val meanDiastolic: Double?,
        val meanPulse: Double?,
        val classification: Classification?,
        val notes: List<String>,
        val references: List<MedicalReference> = listOf(MedicalReference.ESH_2023, MedicalReference.ESH_HBPM_2021, MedicalReference.ESC_2024),
    )

    fun evaluate(sessions: List<MeasurementSession>, startDate: LocalDate?): Result {
        val protocolSessions = sessions
            .filter { it.protocol == ProtocolType.STANDARDIZED_HBPM && it.status.isValidated && it.measurements.isNotEmpty() }
            .filter { startDate == null || !it.localDate.isBefore(startDate) }
            .filter { startDate == null || it.localDate.isBefore(startDate.plusDays(MedicalThresholds.HBPM_RECOMMENDED_DAYS.toLong())) }
        if (protocolSessions.isEmpty()) {
            return Result(startDate, Status.NOT_STARTED, emptyList(), 0, 0, null, null, null, null,
                listOf("Aucune séance du protocole standardisé n'a encore été validée."))
        }
        val firstDay = startDate ?: protocolSessions.minOf { it.localDate }
        val byDay = protocolSessions.groupBy { it.localDate }.toSortedMap()
        val days = byDay.map { (d, s) ->
            DayDetail(
                d,
                s.count { it.period == SessionPeriod.MORNING },
                s.count { it.period == SessionPeriod.EVENING },
                s.sumOf { it.measurements.size },
                excluded = d == firstDay,
            )
        }
        val used = protocolSessions.filter { it.localDate != firstDay }.flatMap { it.measurements }
        val usableDays = days.count { !it.excluded }
        val notes = mutableListOf("Les mesures du premier jour (${firstDay}) sont écartées, conformément au protocole.")
        val incompleteDays = days.filter { !it.excluded && (it.morningSessions == 0 || it.eveningSessions == 0) }
        if (incompleteDays.isNotEmpty()) notes += "${incompleteDays.size} jour(s) sans séance du matin ou du soir."
        val status = when {
            usableDays >= MedicalThresholds.HBPM_RECOMMENDED_DAYS - 1 -> Status.COMPLETE
            usableDays >= MedicalThresholds.HBPM_MIN_DAYS -> Status.SUFFICIENT_MINIMUM
            else -> Status.IN_PROGRESS
        }
        val sys = Descriptive.meanInt(used.map { it.systolic })
        val dia = Descriptive.meanInt(used.map { it.diastolic })
        val pul = Descriptive.meanInt(used.mapNotNull { it.pulse })
        val classification = if (status == Status.IN_PROGRESS || sys == null || dia == null) null else classify(sys, dia)
        if (status == Status.IN_PROGRESS) notes += "Au moins ${MedicalThresholds.HBPM_MIN_DAYS} jours exploitables sont nécessaires avant toute interprétation."
        return Result(firstDay, status, days, usableDays, used.size, sys, dia, pul, classification, notes)
    }

    fun classify(sys: Double, dia: Double): Classification {
        val h = MedicalThresholds.HOME_HYPERTENSION
        val n = MedicalThresholds.HOME_NON_ELEVATED_UPPER
        return when {
            sys >= h.systolic!! || dia >= h.diastolic!! -> Classification.HYPERTENSION_RANGE
            sys >= n.systolic!! || dia >= n.diastolic!! -> Classification.ELEVATED
            else -> Classification.NON_ELEVATED
        }
    }
}
