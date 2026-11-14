package com.cardiosuivi.app.ui.session

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cardiosuivi.app.AppContainer
import com.cardiosuivi.app.data.toCodes
import com.cardiosuivi.app.ocr.CropQuad
import com.cardiosuivi.app.ocr.ImageLoader
import com.cardiosuivi.domain.alerts.Alert
import com.cardiosuivi.domain.alerts.AlertEngine
import com.cardiosuivi.domain.alerts.AlertLevel
import com.cardiosuivi.domain.model.AggregationMethod
import com.cardiosuivi.domain.model.Arm
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.MeasurementSource
import com.cardiosuivi.domain.model.ProtocolType
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import com.cardiosuivi.domain.model.Symptom
import com.cardiosuivi.domain.ocr.OcrField
import com.cardiosuivi.domain.ocr.OcrReading
import com.cardiosuivi.domain.schedule.ScheduleCalculator
import com.cardiosuivi.domain.stats.SessionStatistics
import com.cardiosuivi.domain.stats.SessionStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

enum class FlowStep { LOADING, PREPARATION, REST, CAPTURE, VALIDATE, INTERVAL, SUMMARY, SAVED }

data class FieldInput(val text: String = "", val uncertain: Boolean = false, val hint: String? = null, val alternatives: List<Int> = emptyList())

data class FlowState(
    val step: FlowStep = FlowStep.LOADING,
    val period: SessionPeriod = SessionPeriod.MORNING,
    val protocol: ProtocolType = ProtocolType.USUAL,
    val arm: Arm = Arm.UNKNOWN,
    val planned: Int = 3,
    val intervalSeconds: Int = 60,
    val method: AggregationMethod = AggregationMethod.ALL_MEASUREMENTS,
    val sessionId: Long? = null,
    val startedAt: Instant = Instant.now(),
    val measurements: List<Measurement> = emptyList(),
    val restRemaining: Int = 0,
    val intervalRemaining: Int = 0,
    // Validation de la mesure en cours
    val photoTakenAt: Instant? = null,
    val hasPhoto: Boolean = false,
    val quad: CropQuad = CropQuad.FULL,
    val ocrRunning: Boolean = false,
    val ocrReading: OcrReading? = null,
    val ocrMessage: String? = null,
    val sys: FieldInput = FieldInput(),
    val dia: FieldInput = FieldInput(),
    val pul: FieldInput = FieldInput(),
    val pendingPlausibility: List<Alert> = emptyList(),
    val editingMeasurementId: Long? = null,
    // Alertes
    val lastMeasurementAlerts: List<Alert> = emptyList(),
    val sessionAlerts: List<Alert> = emptyList(),
    val symptomAlerts: List<Alert> = emptyList(),
    val symptoms: Set<Symptom> = emptySet(),
    val notes: String = "",
    val error: String? = null,
) {
    val nextIndex: Int get() = measurements.size + 1
    val stats: SessionStats? get() = if (measurements.isEmpty()) null else SessionStatistics.compute(asSession(), method)
    fun asSession(status: SessionStatus = SessionStatus.IN_PROGRESS) = MeasurementSession(
        id = sessionId ?: 0, startedAt = startedAt, period = period, status = status, protocol = protocol,
        plannedMeasurements = planned, arm = arm, symptoms = symptoms, notes = notes,
        localDate = startedAt.atZone(ZoneId.systemDefault()).toLocalDate(), measurements = measurements,
    )
}

/**
 * Déroulement d'une séance : préparation → repos (facultatif) → pour chaque mesure : photo/OCR ou saisie → validation
 * → intervalle → … → résumé → validation de la séance.
 * Chaque mesure validée est enregistrée immédiatement (séance « en cours », non comptée dans les statistiques)
 * afin qu'aucune donnée ne soit perdue ; la séance n'est « terminée » qu'après validation explicite.
 */
class SessionFlowViewModel(private val c: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(FlowState())
    val state: StateFlow<FlowState> = _state

    /** Image en cours de validation (hors état pour éviter des copies inutiles). */
    val bitmap = MutableStateFlow<Bitmap?>(null)
    private var photoFile: File? = null
    private var timerJob: Job? = null
    private var alertEngine = AlertEngine()
    private var initialized = false

    fun init(period: SessionPeriod?, protocol: ProtocolType, resumeId: Long?, slotKey: String?) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val s = c.settings.current()
            alertEngine = AlertEngine(s.emergencyNumber)
            val zone = ZoneId.systemDefault()
            val planned = if (protocol == ProtocolType.STANDARDIZED_HBPM) 2 else s.measurementsPerSession
            val resume = resumeId?.let { c.sessions.get(it) }?.takeIf { it.status == SessionStatus.IN_PROGRESS }
            if (resume != null) {
                _state.value = FlowState(
                    step = if (resume.measurements.size >= resume.plannedMeasurements) FlowStep.SUMMARY else FlowStep.CAPTURE,
                    period = resume.period, protocol = resume.protocol, arm = resume.arm, planned = resume.plannedMeasurements,
                    intervalSeconds = s.intervalSeconds, method = s.aggregation, sessionId = resume.id, startedAt = resume.startedAt,
                    measurements = resume.measurements, symptoms = resume.symptoms, notes = resume.notes,
                )
            } else {
                val p = period ?: ScheduleCalculator.periodForTime(ZonedDateTime.now(zone).toLocalTime(), s.reminders)
                _state.value = FlowState(step = FlowStep.PREPARATION, period = p, protocol = protocol, planned = planned, intervalSeconds = s.intervalSeconds, method = s.aggregation)
            }
            slotKey?.let { c.sessions.recordReminderEvent(it, "STARTED") }
        }
    }

    fun setPeriod(p: SessionPeriod) = _state.update { it.copy(period = p) }
    fun setArm(a: Arm) = _state.update { it.copy(arm = a) }

    fun startRest() {
        _state.update { it.copy(step = FlowStep.REST, restRemaining = 300) }
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_state.value.restRemaining > 0) {
                delay(1000)
                _state.update { it.copy(restRemaining = it.restRemaining - 1) }
            }
            Feedback.signal(c.appContext)
            goToCapture()
        }
    }

    fun goToCapture() {
        timerJob?.cancel()
        clearCurrent()
        _state.update { it.copy(step = FlowStep.CAPTURE, startedAt = if (it.measurements.isEmpty()) Instant.now() else it.startedAt) }
    }

    private fun clearCurrent() {
        photoFile?.delete()
        photoFile = null
        bitmap.value = null
        _state.update {
            it.copy(hasPhoto = false, photoTakenAt = null, quad = CropQuad.FULL, ocrReading = null, ocrMessage = null,
                sys = FieldInput(), dia = FieldInput(), pul = FieldInput(), pendingPlausibility = emptyList(), editingMeasurementId = null, error = null)
        }
    }

    fun manualEntry() {
        clearCurrent()
        _state.update { it.copy(step = FlowStep.VALIDATE, ocrMessage = "Saisie manuelle : recopiez les valeurs affichées par le tensiomètre.") }
    }

    /** Photo prise par l'appareil (heure réelle = heure de la prise). */
    fun onPhotoCaptured(file: File, takenAt: Instant) {
        photoFile = file
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { ImageLoader.load(file) }
            if (bmp == null) {
                _state.update { it.copy(error = "Impossible de lire la photo.") }
                return@launch
            }
            bitmap.value = bmp
            _state.update { it.copy(step = FlowStep.VALIDATE, hasPhoto = true, photoTakenAt = takenAt, quad = CropQuad.FULL) }
            runOcr()
        }
    }

    /** Photo importée : l'heure de prise EXIF est utilisée si elle existe, sinon l'heure actuelle (signalée). */
    fun onPhotoImported(uri: android.net.Uri) {
        viewModelScope.launch {
            val (bmp, taken) = withContext(Dispatchers.IO) {
                val b = ImageLoader.load(c.appContext, uri)
                val t = runCatching {
                    c.appContext.contentResolver.openInputStream(uri)?.use { s -> androidx.exifinterface.media.ExifInterface(s).dateTimeOriginal }
                }.getOrNull()
                b to t
            }
            if (bmp == null) {
                _state.update { it.copy(error = "Impossible de lire l'image importée.") }
                return@launch
            }
            bitmap.value = bmp
            _state.update {
                it.copy(step = FlowStep.VALIDATE, hasPhoto = true, photoTakenAt = taken?.let { ms -> Instant.ofEpochMilli(ms) } ?: Instant.now(), quad = CropQuad.FULL,
                    ocrMessage = if (taken == null) "Heure de prise inconnue : l'heure actuelle sera enregistrée." else null)
            }
            runOcr()
        }
    }

    fun setQuad(q: CropQuad) = _state.update { it.copy(quad = q) }

    fun runOcr() {
        val bmp = bitmap.value ?: return
        val quad = _state.value.quad
        _state.update { it.copy(ocrRunning = true, error = null) }
        viewModelScope.launch {
            try {
                val res = c.ocr.recognize(bmp, quad)
                val r = res.reading
                fun f(x: OcrField) = FieldInput(x.value?.toString() ?: "", x.uncertain || x.value == null, x.reason, x.alternatives)
                _state.update {
                    it.copy(
                        ocrRunning = false, ocrReading = r, sys = f(r.systolic), dia = f(r.diastolic), pul = f(r.pulse),
                        ocrMessage = (r.warnings.joinToString("\n")).ifBlank { "Valeurs détectées : vérifiez-les avant de valider." },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(ocrRunning = false, ocrMessage = "La reconnaissance a échoué (${e.message}). Recadrez l'image ou saisissez les valeurs.") }
            }
        }
    }

    fun setSys(t: String) = _state.update { it.copy(sys = it.sys.copy(text = t, uncertain = false), pendingPlausibility = emptyList()) }
    fun setDia(t: String) = _state.update { it.copy(dia = it.dia.copy(text = t, uncertain = false), pendingPlausibility = emptyList()) }
    fun setPul(t: String) = _state.update { it.copy(pul = it.pul.copy(text = t, uncertain = false), pendingPlausibility = emptyList()) }
    fun swapSysDia() = _state.update { it.copy(sys = it.dia, dia = it.sys, pendingPlausibility = emptyList()) }

    /**
     * Valide la mesure courante. Si les valeurs semblent incohérentes, une confirmation explicite est demandée
     * ([confirmed] = true au second appel).
     */
    fun validateMeasurement(confirmed: Boolean = false) {
        val st = _state.value
        val sys = st.sys.text.toIntOrNull()
        val dia = st.dia.text.toIntOrNull()
        val pul = st.pul.text.takeIf { it.isNotBlank() }?.toIntOrNull()
        if (sys == null || dia == null) {
            _state.update { it.copy(error = "Les valeurs systolique et diastolique sont obligatoires.") }
            return
        }
        if (st.pul.text.isNotBlank() && pul == null) {
            _state.update { it.copy(error = "Fréquence cardiaque invalide.") }
            return
        }
        val checks = alertEngine.checkPlausibility(sys, dia, pul)
        if (checks.isNotEmpty() && !confirmed) {
            _state.update { it.copy(pendingPlausibility = checks) }
            return
        }
        val r = st.ocrReading
        val source = when {
            !st.hasPhoto || r == null -> MeasurementSource.MANUAL
            r.systolic.value == sys && r.diastolic.value == dia && r.pulse.value == pul -> MeasurementSource.OCR
            else -> MeasurementSource.OCR_CORRECTED
        }
        viewModelScope.launch {
            try {
                if (st.editingMeasurementId != null) {
                    val old = st.measurements.first { it.id == st.editingMeasurementId }
                    val upd = old.copy(systolic = sys, diastolic = dia, pulse = pul, source = if (old.source == MeasurementSource.MANUAL) MeasurementSource.MANUAL else MeasurementSource.OCR_CORRECTED)
                    c.sessions.updateMeasurement(upd)
                    _state.update { s -> s.copy(measurements = s.measurements.map { if (it.id == upd.id) upd else it }, step = FlowStep.SUMMARY, editingMeasurementId = null) }
                    clearCurrent()
                    _state.update { it.copy(step = FlowStep.SUMMARY) }
                    return@launch
                }
                var sessionId = st.sessionId
                if (sessionId == null) {
                    sessionId = c.sessions.createSession(st.asSession())
                }
                val m = Measurement(
                    sessionId = sessionId, index = st.nextIndex, systolic = sys, diastolic = dia, pulse = pul,
                    measuredAt = st.photoTakenAt ?: Instant.now(), source = source, ocrConfidence = r?.overallConfidence?.takeIf { st.hasPhoto },
                )
                val id = c.sessions.addMeasurement(sessionId, m)
                val saved = m.copy(id = id)
                val alerts = alertEngine.evaluateMeasurement(sys, dia, pul, st.symptoms).filter { it.level != AlertLevel.CHECK_MEASUREMENT }
                clearCurrent()
                val count = st.measurements.size + 1
                _state.update {
                    it.copy(
                        sessionId = sessionId, measurements = it.measurements + saved, lastMeasurementAlerts = alerts,
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
        clearCurrent()
        _state.update { it.copy(step = FlowStep.SUMMARY) }
    }

    fun editMeasurement(m: Measurement) {
        clearCurrent()
        _state.update {
            it.copy(step = FlowStep.VALIDATE, editingMeasurementId = m.id, sys = FieldInput(m.systolic.toString()), dia = FieldInput(m.diastolic.toString()),
                pul = FieldInput(m.pulse?.toString() ?: ""), ocrMessage = "Correction de la mesure ${m.index}.")
        }
    }

    fun deleteMeasurement(m: Measurement) = viewModelScope.launch {
        c.sessions.deleteMeasurement(m.id)
        // Renumérotation pour conserver un ordre 1..n cohérent.
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
            c.sessions.validateSession(id, status, Instant.now(), st.symptoms.toCodes(), st.notes)
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
            clearCurrent()
            onDone()
        }
    }

    /** Quitter sans supprimer : la séance reste « en cours » et pourra être reprise. */
    fun leaveForLater() {
        timerJob?.cancel()
        clearCurrent()
    }

    override fun onCleared() {
        photoFile?.delete()
        super.onCleared()
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { SessionFlowViewModel(c) } }
    }
}
