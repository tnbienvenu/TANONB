package com.cardiosuivi.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cardiosuivi.app.AppContainer
import com.cardiosuivi.app.data.AppSettings
import com.cardiosuivi.domain.alerts.AlertEngine
import com.cardiosuivi.domain.analysis.AnalysisReport
import com.cardiosuivi.domain.analysis.CardioAnalysisEngine
import com.cardiosuivi.domain.model.AggregationMethod
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.Observation
import com.cardiosuivi.domain.model.Profile
import com.cardiosuivi.domain.model.ReminderConfig
import com.cardiosuivi.domain.model.Treatment
import com.cardiosuivi.domain.schedule.ScheduleCalculator
import com.cardiosuivi.domain.schedule.ScheduledSlot
import com.cardiosuivi.domain.stats.PeriodReport
import com.cardiosuivi.domain.stats.PeriodStatistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** ViewModel partagé par les écrans de consultation (données observées depuis la base chiffrée). */
class AppViewModel(val c: AppContainer) : ViewModel() {

    val zone: ZoneId get() = ZoneId.systemDefault()
    fun now(): ZonedDateTime = ZonedDateTime.now(zone)

    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings(onboardingAccepted = true))
    val sessions: StateFlow<List<MeasurementSession>> = c.sessions.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val inProgress: StateFlow<MeasurementSession?> = c.sessions.observeInProgress().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val profile: StateFlow<Profile> = c.profile.observeProfile().stateIn(viewModelScope, SharingStarted.Eagerly, Profile())
    val treatments: StateFlow<List<Treatment>> = c.profile.observeTreatments().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val observations: StateFlow<List<Observation>> = c.profile.observeObservations().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun alertEngine(): AlertEngine = AlertEngine(settings.value.emergencyNumber)

    /** Analyse déterministe recalculée à chaque changement de données ou de paramètres. */
    val analysis: StateFlow<AnalysisReport?> = combine(sessions, profile, settings) { s, p, st ->
        CardioAnalysisEngine(AlertEngine(st.emergencyNumber)).analyze(s, p, st.aggregation, st.reminders, now())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun periodReport(from: LocalDate, to: LocalDate): PeriodReport =
        PeriodStatistics.compute(sessions.value, from, to, settings.value.aggregation, profile.value.target, settings.value.reminders, now())

    fun nextSlots(count: Int): List<ScheduledSlot> = ScheduleCalculator.nextSlots(settings.value.reminders, now(), count)

    fun saveProfile(p: Profile) = viewModelScope.launch { c.profile.saveProfile(p) }
    fun saveTreatment(t: Treatment) = viewModelScope.launch { c.profile.saveTreatment(t) }
    fun deleteTreatment(id: Long) = viewModelScope.launch { c.profile.deleteTreatment(id) }
    fun saveObservation(o: Observation) = viewModelScope.launch { c.profile.saveObservation(o) }
    fun deleteObservation(id: Long) = viewModelScope.launch { c.profile.deleteObservation(id) }

    fun deleteSession(id: Long) = viewModelScope.launch { c.sessions.deleteSession(id) }
    fun updateSession(s: MeasurementSession) = viewModelScope.launch { c.sessions.updateSession(s) }
    fun updateMeasurement(m: Measurement) = viewModelScope.launch { c.sessions.updateMeasurement(m) }
    fun deleteMeasurement(id: Long) = viewModelScope.launch { c.sessions.deleteMeasurement(id) }

    fun setReminders(r: ReminderConfig) = viewModelScope.launch {
        c.settings.setReminders(r)
        c.reminderScheduler.rescheduleAll(r, now())
        c.settings.markRescheduled()
    }

    fun setProtocol(interval: Int, perSession: Int) = viewModelScope.launch { c.settings.setProtocol(interval, perSession) }
    fun setAggregation(m: AggregationMethod) = viewModelScope.launch { c.settings.setAggregation(m) }
    fun setBiometric(on: Boolean) = viewModelScope.launch { c.settings.setBiometric(on) }
    fun setSecureScreen(on: Boolean) = viewModelScope.launch { c.settings.setSecureScreen(on) }
    fun setEmergencyNumber(n: String) = viewModelScope.launch { c.settings.setEmergencyNumber(n) }
    fun setHbpmStart(d: LocalDate?) = viewModelScope.launch { c.settings.setHbpmStart(d) }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { AppViewModel(c) } }
    }
}
