package com.cardiosuivi.domain.schedule

import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.ReminderConfig
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Créneau de séance programmé (jour + moment). */
data class ScheduledSlot(
    val date: LocalDate,
    val period: SessionPeriod,
    val time: LocalTime,
) {
    fun at(zone: ZoneId): ZonedDateTime = ZonedDateTime.of(date, time, zone)

    /** Identifiant stable du créneau, utilisé pour les notifications et le suivi des reports. */
    val key: String get() = "$date-${period.name}"
}

enum class SlotStatus(val label: String) {
    /** Séance validée complète pour ce créneau. */
    DONE("Réalisée"),
    /** Séance validée mais incomplète. */
    INCOMPLETE("Incomplète"),
    /** Délai de grâce dépassé sans séance validée. */
    MISSED("Manquée"),
    /** Créneau futur ou encore dans le délai de grâce. */
    PENDING("À venir"),
}

data class SlotState(val slot: ScheduledSlot, val status: SlotStatus, val sessionId: Long?)

/**
 * Calculs purs de planification. Les horaires sont des valeurs configurables par l'utilisateur,
 * pas des recommandations médicales.
 */
object ScheduleCalculator {

    /** Tous les créneaux programmés entre [from] et [to] inclus. */
    fun slotsBetween(config: ReminderConfig, from: LocalDate, to: LocalDate): List<ScheduledSlot> {
        if (to.isBefore(from)) return emptyList()
        val out = mutableListOf<ScheduledSlot>()
        var d = from
        while (!d.isAfter(to)) {
            if (d.dayOfWeek in config.days) {
                if (config.morningEnabled) out += ScheduledSlot(d, SessionPeriod.MORNING, config.morningTime)
                if (config.eveningEnabled) out += ScheduledSlot(d, SessionPeriod.EVENING, config.eveningTime)
            }
            d = d.plusDays(1)
        }
        return out
    }

    /** Prochaines occurrences strictement postérieures à [now]. */
    fun nextSlots(config: ReminderConfig, now: ZonedDateTime, count: Int): List<ScheduledSlot> {
        if (!config.enabled || config.days.isEmpty() || (!config.morningEnabled && !config.eveningEnabled)) return emptyList()
        val out = mutableListOf<ScheduledSlot>()
        var d = now.toLocalDate()
        var guard = 0
        while (out.size < count && guard < 400) {
            for (s in slotsBetween(config, d, d)) {
                if (s.at(now.zone).isAfter(now)) out += s
                if (out.size == count) break
            }
            d = d.plusDays(1)
            guard++
        }
        return out
    }

    /** Prochaine occurrence d'un moment donné (matin ou soir), strictement après [now]. */
    fun nextOccurrence(config: ReminderConfig, period: SessionPeriod, now: ZonedDateTime): ZonedDateTime? {
        if (!config.enabled || config.days.isEmpty()) return null
        val time = when (period) {
            SessionPeriod.MORNING -> if (config.morningEnabled) config.morningTime else return null
            SessionPeriod.EVENING -> if (config.eveningEnabled) config.eveningTime else return null
            SessionPeriod.OTHER -> return null
        }
        var d = now.toLocalDate()
        repeat(8) {
            if (d.dayOfWeek in config.days) {
                val candidate = ZonedDateTime.of(d, time, now.zone)
                if (candidate.isAfter(now)) return candidate
            }
            d = d.plusDays(1)
        }
        return null
    }

    /**
     * Cohérence entre rappels et plages matin/soir : une séance commencée à l'heure du rappel, ou jusqu'à la fin du délai
     * de tolérance, doit être classée dans la période du rappel ; sinon elle ne satisferait pas le créneau.
     * Retourne la liste des incohérences à signaler (vide si tout est cohérent).
     */
    fun windowWarnings(config: ReminderConfig, windows: com.cardiosuivi.domain.model.PeriodWindows): List<String> {
        val out = mutableListOf<String>()
        fun check(on: Boolean, t: LocalTime, p: SessionPeriod, name: String) {
            if (!on) return
            if (windows.periodOf(t) != p) out += "Le rappel du $name ($t) est en dehors de la plage du $name."
            else if (windows.periodOf(t.plusMinutes(config.missedGraceMinutes.toLong())) != p)
                out += "Une séance du $name commencée jusqu'à ${config.missedGraceMinutes / 60} h après le rappel ($t) pourrait sortir de la plage du $name."
        }
        check(config.morningEnabled, config.morningTime, SessionPeriod.MORNING, "matin")
        check(config.eveningEnabled, config.eveningTime, SessionPeriod.EVENING, "soir")
        return out
    }

    /**
     * État de chaque créneau programmé entre [from] et [to] : réalisé, incomplet, manqué ou à venir.
     * Seules les séances VALIDÉES (terminées ou incomplètes) satisfont un créneau.
     */
    fun slotStates(
        config: ReminderConfig,
        sessions: List<MeasurementSession>,
        from: LocalDate,
        to: LocalDate,
        now: ZonedDateTime,
    ): List<SlotState> {
        val validated = sessions.filter { it.status.isValidated }
        return slotsBetween(config, from, to).map { slot ->
            val match = validated
                .filter { it.localDate == slot.date && it.period == slot.period }
                .sortedBy { if (it.status == SessionStatus.COMPLETED) 0 else 1 }
                .firstOrNull()
            val status = when {
                match?.status == SessionStatus.COMPLETED -> SlotStatus.DONE
                match?.status == SessionStatus.INCOMPLETE -> SlotStatus.INCOMPLETE
                isPastGrace(slot, config, now) -> SlotStatus.MISSED
                else -> SlotStatus.PENDING
            }
            SlotState(slot, status, match?.id)
        }
    }

    fun isPastGrace(slot: ScheduledSlot, config: ReminderConfig, now: ZonedDateTime): Boolean {
        val deadline = slot.at(now.zone).plus(Duration.ofMinutes(config.missedGraceMinutes.toLong()))
        return now.isAfter(deadline)
    }
}
