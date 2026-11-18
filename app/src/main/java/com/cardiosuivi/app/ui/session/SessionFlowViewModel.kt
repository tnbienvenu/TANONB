package com.cardiosuivi.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cardiosuivi.app.AppContainer
import com.cardiosuivi.app.data.toCodes
import com.cardiosuivi.domain.alerts.Alert
import com.cardiosuivi.domain.alerts.AlertEngine
import com.cardiosuivi.domain.alerts.AlertLevel
import com.cardiosuivi.domain.entry.EntryField
import com.cardiosuivi.domain.entry.MeasurementEntry
import com.cardiosuivi.domain.entry.Timestamp
import com.cardiosuivi.domain.model.AggregationMethod
import com.cardiosuivi.domain.model.Arm
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.MeasurementSource
import com.cardiosuivi.domain.model.PeriodWindows
import com.cardiosuivi.domain.model.ProtocolType
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import com.cardiosuivi.domain.model.Symptom
import com.cardiosuivi.domain.stats.SessionStatistics
import com.cardiosuivi.domain.stats.SessionStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Étapes de la séance guidée (1.1.0 : saisie manuelle uniquement). */
enum class FlowStep { LOADING, PREPARATION, REST, ENTRY, INTERVAL, SUMMARY, SAVED }

data class FlowState(
    val step: FlowStep = FlowStep.LOADING,
    /** Période déterminée automatiquement à partir de l'heure de la première mesure (OTHER avant la première mesure). */
    val period: SessionPeriod = SessionPeriod.OTHER,
    val protocol: ProtocolType = ProtocolType.USUAL,
    val arm: Arm = Arm.DEFAULT_FOR_NEW,
    val planned: Int = 3,
    val intervalSeconds: Int = 60,
    val method: AggregationMethod = AggregationMethod.ALL_MEASUREMENTS,
    val windows: PeriodWindows = PeriodWindows(),
    val sessionId: Long? = null,
    val startedAt: Instant = Instant.now(),
    val measurements: List<Measurement> = emptyList(),
    val restRemaining: Int = 0,
    val intervalRemaining: Int = 0,
    // Formulaire de la mesure en cours (les valeurs saisies sont conservées en cas d'erreur).
    val sys: String = "",
    val dia: String = "",
    val pul: String = "",
    val fieldErrors: Map<EntryField, String> = emptyMap(),
    val pendingWarnings: List<String> = emptyList(),
    val editingMeasurementId: Long? = null,
    /** Étape à laquelle revenir après une correction. */
    val returnTo: FlowStep? = null,
    // Alertes
    val lastMeasurementAlerts: List<Alert> = emptyList(),
    val sessionAlerts: List<Alert> = emptyList(),
    val symptomAlerts: List<Alert> = emptyList(),
    val symptoms: Set<Symptom> = emptySet(),
    val notes: String = "",
    val error: String? = null,
) {
    val nextIndex: Int get() = measurements.size + 1
    val hasUnsavedInput: Boolean get() = sys.isNotBlank() || dia.isNotBlank() || pul.isNotBlank()
    val editing: Measurement? get() = editingMeasurementId?.let { id -> measurements.firstOrNull { it.id == id } }
    val stats: SessionStats? get() = if (measurements.isEmpty()) null else SessionStatistics.compute(asSession(), method)
    fun asSession(status: SessionStatus = SessionStatus.IN_PROGRESS) = MeasurementSession(
        id = sessionId ?: 0, startedAt = startedAt, period = period, status = status, protocol = protocol,
        plannedMeasurements = planned, arm = arm, symptoms = symptoms, notes = notes,
        localDate = startedAt.atZone(ZoneId.systemDefault()).toLocalDate(), measurements = measurements,
    )
}

/**
 * Déroulement d'une séance : préparation → repos (facultatif) → pour chaque mesure : saisie SYS / DIA / PUL →
 * intervalle → … → récapitulatif → validation de la séance.
 * Chaque mesure validée est enregistrée immédiatement (séance « en cours », non comptée dans les statistiques) afin
 * qu'aucune donnée ne soit perdue ; la séance n'est « terminée » qu'après validation explicite.
 *
 * Horodatage : chaque mesure reçoit l'heure du téléphone au moment de sa validation (instant à la milliseconde, fuseau
 * et décalage UTC) ; une correction ne modifie jamais cet horodatage mais renseigne `modifiedAt`.
 * Période : déterminée à partir de l'heure locale de la première mesure et des plages configurées ; « Autre » hors plage.
 * Bras : gauche, enregistré automatiquement pour toute nouvelle séance.
 */
class SessionFlowViewModel(private val c: AppContainer, private val clock: Clock = Clock.systemDefaultZone()) : ViewModel() {

    private val _state = MutableStateFlow(FlowState())
    val state: StateFlow<FlowState> = _state

    private var timerJob: Job? = null
    private var alertEngine = AlertEngine()
    private var initialized = false

    /** [period] (rappel) n'est plus utilisé : la période est déterminée à partir de l'heure réelle de la mesure. */
    @Suppress("UNUSED_PARAMETER")
    fun init(period: SessionPeriod?, protocol: ProtocolType, resumeId: Long?, slotKey: String?) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val s = c.settings.current()
            alertEngine = AlertEngine(s.emergencyNumber)
            val planned = if (protocol == ProtocolType.STANDARDIZED_HBPM) 2 else s.measurementsPerSession
            val resume = resumeId?.let { c.sessions.get(it) }?.takeIf { it.status == SessionStatus.IN_PROGRESS }
            _state.value = if (resume != null) {
                FlowState(
                    step = if (resume.measurements.size >= resume.plannedMeasurements) FlowStep.SUMMARY else FlowStep.ENTRY,
                    period = resume.period, protocol = resume.protocol, arm = resume.arm, planned = resume.plannedMeasurements,
                    intervalSeconds = s.intervalSeconds, method = s.aggregation, windows = s.windows, sessionId = resume.id,
                    startedAt = resume.startedAt, measurements = resume.measurements, symptoms = resume.symptoms, notes = resume.notes,
                )
            } else {
                FlowState(step = FlowStep.PREPARATION, protocol = protocol, planned = planned, intervalSeconds = s.intervalSeconds,
                    method = s.aggregation, windows = s.windows)
            }
            slotKey?.let { c.sessions.recordReminderEvent(it, "STARTED") }
        }
    }

    fun startRest() {
        _state.update { it.copy(step = FlowStep.REST, restRemaining = 300) }
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_state.value.restRemaining > 0) {
                delay(1000)
                _state.update { it.copy(restRemaining = it.restRemaining - 1) }
            }
            Feedback.signal(c.appContext)
            goToEntry()
        }
    }

    /** Passe à la saisie de la mesure suivante. */
    fun goToEntry() {
        timerJob?.cancel()
        clearForm()
        _state.update { it.copy(step = FlowStep.ENTRY, returnTo = null, editingMeasurementId = null) }
    }

    private fun clearForm() = _state.update {
        it.copy(sys = "", dia = "", pul = "", fieldErrors = emptyMap(), pendingWarnings = emptyList(), error = null)
    }

    fun setSys(t: String) = _state.update { it.copy(sys = MeasurementEntry.sanitize(t), fieldErrors = it.fieldErrors - EntryField.SYS - EntryField.DIA, pendingWarnings = emptyList()) }
    fun setDia(t: String) = _state.update { it.copy(dia = MeasurementEntry.sanitize(t), fieldErrors = it.fieldErrors - EntryField.DIA, pendingWarnings = emptyList()) }
    fun setPul(t: String) = _state.update { it.copy(pul = MeasurementEntry.sanitize(t), fieldErrors = it.fieldErrors - EntryField.PUL, pendingWarnings = emptyList()) }
    fun dismissWarnings() = _state.update { it.copy(pendingWarnings = emptyList()) }

    /**
     * Valide la mesure en cours (« Valider et continuer »). Refus si SYS, DIA ou PUL est absent ou invalide (les valeurs
     * saisies sont conservées). Une valeur hors des bornes de plausibilité demande une confirmation ([confirmed]).
     */
    fun validateMeasurement(confirmed: Boolean = false) {
        val st = _state.value
        val v = MeasurementEntry.validate(st.sys, st.dia, st.pul)
        if (!v.isValid) {
            _state.update { it.copy(fieldErrors = v.errors, error = null) }
            return
        }
        if (v.warnings.isNotEmpty() && !confirmed) {
            _state.update { it.copy(pendingWarnings = v.warnings) }
            return
        }
        val sys = v.systolic!!; val dia = v.diastolic!!; val pul = v.pulse!!
        viewModelScope.launch {
            try {
                val editing = st.editing
                if (editing != null) {
                    val changed = editing.systolic != sys || editing.diastolic != dia || editing.pulse != pul
                    // L'horodatage initial n'est jamais modifié ; seule la date de correction est renseignée.
                    val upd = editing.copy(systolic = sys, diastolic = dia, pulse = pul, modifiedAt = if (changed) clock.instant() else editing.modifiedAt)
                    if (changed) c.sessions.updateMeasurement(upd)
                    val back = st.returnTo ?: FlowStep.SUMMARY
                    clearForm()
                    _state.update { s -> s.copy(measurements = s.measurements.map { if (it.id == upd.id) upd else it }, editingMeasurementId = null, returnTo = null, step = back) }
                    return@launch
                }
                val ts = Timestamp.now(clock)
                var sessionId = st.sessionId
                var current = st
                if (sessionId == null) {
                    val local = ts.instant.atZone(ZoneId.of(ts.zoneId))
                    current = st.copy(startedAt = ts.instant, period = st.windows.periodOf(local.toLocalTime()), arm = Arm.DEFAULT_FOR_NEW)
                    sessionId = c.sessions.createSession(current.asSession())
                }
                val m = Measurement(
                    sessionId = sessionId, index = current.nextIndex, systolic = sys, diastolic = dia, pulse = pul,
                    measuredAt = ts.instant, source = MeasurementSource.MANUAL, zoneId = ts.zoneId, utcOffsetSeconds = ts.utcOffsetSeconds,
                )
                val id = c.sessions.addMeasurement(sessionId, m)
                val saved = m.copy(id = id)
                val alerts = alertEngine.evaluateMeasurement(sys, dia, pul, st.symptoms).filter { it.level != AlertLevel.CHECK_MEASUREMENT }
                val count = current.measurements.size + 1
                clearForm()
                _state.update {
                    it.copy(
                        sessionId = sessionId, startedAt = current.startedAt, period = current.period, arm = current.arm,
                        measurements = it.measurements + saved, lastMeasurementAlerts = alerts,
                        step = if (count >= it.planned) FlowStep.SUMMARY else FlowStep.INTERVAL,
                        intervalRemaining = it.intervalSeconds,
                    )
                }
                if (count < st.planned) startInterval()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Enregistrement impossible : ${e.message}") }
            }
        }
    }

    private fun startInterval() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_state.value.intervalRemaining > 0) {
                delay(1000)
                _state.update { it.copy(intervalRemaining = it.intervalRemaining - 1) }
            }
            Feedback.signal(c.appContext)
        }
    }

    fun skipToSummary() {
        timerJob?.cancel()
        clearForm()
        _state.update { it.copy(step = FlowStep.SUMMARY, editingMeasurementId = null, returnTo = null) }
    }

    /** Ouvre la correction d'une mesure déjà validée ; retour à l'étape courante ensuite. */
    fun editMeasurement(m: Measurement) {
        val from = _state.value.step
        clearForm()
        _state.update {
            it.copy(step = FlowStep.ENTRY, editingMeasurementId = m.id, returnTo = from,
                sys = m.systolic.toString(), dia = m.diastolic.toString(), pul = m.pulse?.toString() ?: "")
        }
    }

    /** « Revenir à la mesure précédente » depuis la saisie ou l'intervalle. */
    fun editPrevious() {
        val last = _state.value.measurements.maxByOrNull { it.index } ?: return
        editMeasurement(last)
    }

    fun cancelEdit() {
        val back = _state.value.returnTo ?: FlowStep.SUMMARY
        clearForm()
        _state.update { it.copy(editingMeasurementId = null, returnTo = null, step = back) }
    }

    fun deleteMeasurement(m: Measurement) = viewModelScope.launch {
        c.sessions.deleteMeasurement(m.id)
        // Renumérotation pour conserver un ordre 1..n cohérent (les horodatages ne changent pas).
        val remaining = _state.value.measurements.filter { it.id != m.id }.sortedBy { it.index }.mapIndexed { i, x -> x.copy(index = i + 1) }
        remaining.forEach { c.sessions.updateMeasurement(it) }
        _state.update { it.copy(measurements = remaining) }
    }

    fun toggleSymptom(s: Symptom) {
        val set = _state.value.symptoms.let { if (s in it) it - s else it + s }
        // Évaluation immédiate : une recommandation urgente ne doit jamais attendre l'enregistrement.
        _state.update { it.copy(symptoms = set, symptomAlerts = alertEngine.evaluateSymptoms(set)) }
    }

    fun setNotes(n: String) = _state.update { it.copy(notes = n) }

    fun saveSession() {
        val st = _state.value
        val id = st.sessionId ?: return
        if (st.measurements.isEmpty()) return
        val status = if (st.measurements.size >= st.planned) SessionStatus.COMPLETED else SessionStatus.INCOMPLETE
        viewModelScope.launch {
            c.sessions.updateSession(st.asSession(SessionStatus.IN_PROGRESS).copy(id = id))
            c.sessions.validateSession(id, status, clock.instant(), st.symptoms.toCodes(), st.notes)
            val validated = st.asSession(status).copy(id = id)
            val alerts = alertEngine.evaluateSession(validated, SessionStatistics.compute(validated, st.method), c.profile.getProfile().target)
            _state.update { it.copy(step = FlowStep.SAVED, sessionAlerts = alerts) }
        }
    }

    /** Abandon : la séance en cours (non validée) est supprimée. */
    fun cancelSession(onDone: () -> Unit) {
        timerJob?.cancel()
        viewModelScope.launch {
            _state.value.sessionId?.let { c.sessions.deleteSession(it) }
            clearForm()
            onDone()
        }
    }

    /** Quitter sans supprimer : la séance reste « en cours » et pourra être reprise. */
    fun leaveForLater() {
        timerJob?.cancel()
        clearForm()
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { SessionFlowViewModel(c) } }
    }
}
