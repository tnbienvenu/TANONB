package com.cardiosuivi.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.cardiosuivi.app.ui.components.CardHeader
import com.cardiosuivi.app.ui.components.HealthCard
import com.cardiosuivi.app.ui.components.PrimaryButton
import com.cardiosuivi.app.ui.components.SecondaryButton
import com.cardiosuivi.app.ui.components.SegmentedControl
import com.cardiosuivi.app.ui.components.StatusBadge
import com.cardiosuivi.app.ui.theme.Health
import com.cardiosuivi.app.ui.theme.NumberStyles
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.cardiosuivi.app.report.Sharing
import com.cardiosuivi.app.ui.AppViewModel
import com.cardiosuivi.app.ui.Routes
import com.cardiosuivi.app.ui.components.AlertCard
import com.cardiosuivi.app.ui.components.BpValue
import com.cardiosuivi.app.ui.components.ConfirmDialog
import com.cardiosuivi.app.ui.components.EmptyState
import com.cardiosuivi.app.ui.components.KeyValue
import com.cardiosuivi.app.ui.components.NumberField
import com.cardiosuivi.app.ui.components.ScreenScaffold
import com.cardiosuivi.app.ui.components.SectionCard
import com.cardiosuivi.app.ui.components.StatusDot
import com.cardiosuivi.app.ui.theme.CardioColors
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSource
import com.cardiosuivi.domain.entry.EntryField
import com.cardiosuivi.domain.entry.MeasurementEntry
import com.cardiosuivi.domain.entry.localDateTime
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import com.cardiosuivi.domain.stats.SessionStatistics
import com.cardiosuivi.domain.util.Fmt
import java.time.format.DateTimeFormatter

private enum class HistoryFilter(val label: String) { ALL("Toutes"), MORNING("Matin"), EVENING("Soir"), INCOMPLETE("Incomplètes") }

@Composable
fun HistoryScreen(vm: AppViewModel, nav: NavHostController) {
    val sessions by vm.sessions.collectAsState()
    val settings by vm.settings.collectAsState()
    val inProgress by vm.inProgress.collectAsState()
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    val c = Health.colors
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val shown = sessions.filter { it.status.isValidated }.filter {
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.MORNING -> it.period == SessionPeriod.MORNING
            HistoryFilter.EVENING -> it.period == SessionPeriod.EVENING
            HistoryFilter.INCOMPLETE -> it.status == SessionStatus.INCOMPLETE
        }
    }
    ScreenScaffold("Mesures", onBack = null, subtitle = "Séances de trois mesures et historique") {
        PrimaryButton("Nouvelle séance", { nav.navigate(Routes.session()) }, icon = Icons.Filled.Add)
        inProgress?.let { s ->
            HealthCard(onClick = { nav.navigate(Routes.session(resume = s.id)) }) {
                CardHeader("Séance en cours", Icons.Filled.MonitorHeart, MaterialTheme.colorScheme.primary, chevron = true)
                Text("${s.measurements.size}/${s.plannedMeasurements} mesure(s) enregistrée(s), non validée. Touchez pour reprendre.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        SegmentedControl(HistoryFilter.entries.toList(), filter, { it.label }, { filter = it })
        Text("${shown.size} séance(s) validée(s) · valeur de séance : ${settings.aggregation.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (shown.isEmpty()) EmptyState("Aucune séance validée.", Icons.Filled.MonitorHeart)
        shown.groupBy { it.localDate }.forEach { (date, list) ->
            Text(Fmt.dateLong(date).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).semantics { heading() })
            HealthCard(contentPadding = 6.dp) {
                list.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 10.dp))
                    val st = SessionStatistics.compute(s, settings.aggregation)
                    val complete = s.status == SessionStatus.COMPLETED
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { nav.navigate(Routes.sessionDetail(s.id)) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${timeFmt.format(s.startedAt.atZone(vm.zone))} · ${com.cardiosuivi.app.ui.session.periodText(s.period).replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            st.primary?.let { p ->
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("${Fmt.d0(p.systolic)}/${Fmt.d0(p.diastolic)}", style = NumberStyles.medium)
                                    Text(" mmHg", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                                    p.pulse?.let { Text("   ${Fmt.d0(it)} bpm", style = MaterialTheme.typography.titleSmall, color = c.pulse, modifier = Modifier.padding(bottom = 2.dp)) }
                                }
                            }
                            Text("${s.measurements.size} mesure(s) : " + s.measurements.joinToString("  ·  ") { "${it.systolic}/${it.diastolic}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (s.symptoms.isNotEmpty()) Text("Symptômes : ${s.symptoms.joinToString { it.label.lowercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StatusBadge(if (complete) "Complète" else "Incomplète", if (complete) c.favorable else c.attention, if (complete) Icons.Filled.CheckCircle else Icons.Filled.Warning)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit) {
    val sessions by vm.sessions.collectAsState()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    val context = LocalContext.current
    val s = sessions.firstOrNull { it.id == id }
    var editing by remember { mutableStateOf<Measurement?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var notes by rememberSaveable(s?.notes) { mutableStateOf(s?.notes ?: "") }
    ScreenScaffold("Détail de la séance", onBack = onBack, actions = {
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Supprimer la séance") }
    }) {
        if (s == null) { Text("Séance introuvable."); return@ScreenScaffold }
        val st = SessionStatistics.compute(s, settings.aggregation)
        Text("${Fmt.dateLong(s.localDate).replaceFirstChar { it.uppercase() }} · ${com.cardiosuivi.app.ui.session.periodText(s.period)} · ${s.protocol.label} · ${s.arm.label}", style = MaterialTheme.typography.bodySmall)
        HealthCard {
            CardHeader("Valeur de séance", Icons.Filled.MonitorHeart, Health.colors.systolic)
            st.primary?.let { p -> BpValue(Fmt.d0(p.systolic), Fmt.d0(p.diastolic), p.pulse?.let { Fmt.d0(it) }, style = NumberStyles.hero) }
            val complete = s.status == SessionStatus.COMPLETED
            StatusBadge(s.status.label, if (complete) Health.colors.favorable else Health.colors.attention, if (complete) Icons.Filled.CheckCircle else Icons.Filled.Warning)
            Text(settings.aggregation.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard("Mesures") {
            s.measurements.forEachIndexed { i, m ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mesure ${m.index}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${m.systolic}/${m.diastolic} mmHg" + (m.pulse?.let { " · $it bpm" } ?: " · pouls non renseigné"), style = NumberStyles.small)
                        val at = m.localDateTime(vm.zone)
                        Text(
                            "Mesurée à ${Fmt.timeSeconds(at)}" +
                                (if (m.source != MeasurementSource.MANUAL) " · ${m.source.label}" else "") +
                                (m.modifiedAt?.let { " · corrigée le ${Fmt.dateTimeLong(it.atZone(vm.zone))}" } ?: ""),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editing = m }) { Icon(Icons.Filled.Edit, "Corriger la mesure ${m.index}") }
                }
            }
        }
        SectionCard("Statistiques") {
            KeyValue("Moyenne de toutes les mesures", st.meanAll?.let { "${Fmt.d1(it.systolic)}/${Fmt.d1(it.diastolic)}" } ?: "—")
            KeyValue("Moyenne des 2 dernières", st.meanLastTwo?.let { "${Fmt.d1(it.systolic)}/${Fmt.d1(it.diastolic)}" } ?: "—")
            KeyValue("Pouls moyen", Fmt.d1(st.meanAll?.pulse))
            KeyValue("SYS min/max/amplitude", "${st.systolic.min ?: "—"} / ${st.systolic.max ?: "—"} / ${st.systolic.range ?: "—"}")
            KeyValue("DIA min/max/amplitude", "${st.diastolic.min ?: "—"} / ${st.diastolic.max ?: "—"} / ${st.diastolic.range ?: "—"}")
            KeyValue("Écart-type SYS / DIA", "${Fmt.d1(st.systolic.sd)} / ${Fmt.d1(st.diastolic.sd)}")
            KeyValue("Évolution SYS", st.systolicDeltas.joinToString(", ") { if (it >= 0) "+$it" else "$it" }.ifBlank { "—" })
            KeyValue("Évolution DIA", st.diastolicDeltas.joinToString(", ") { if (it >= 0) "+$it" else "$it" }.ifBlank { "—" })
            KeyValue("Pression pulsée moyenne", Fmt.d0(st.pulsePressure.mean))
        }
        vm.alertEngine().evaluateSession(s, st, profile.target).forEach { AlertCard(it) { Sharing.dial(context, settings.emergencyNumber) } }
        SectionCard("Symptômes et notes") {
            Text(if (s.symptoms.isEmpty()) "Aucun symptôme signalé." else s.symptoms.joinToString { it.label })
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            if (notes != s.notes) SecondaryButton("Enregistrer la note", { vm.updateSession(s.copy(notes = notes)) })
        }
        HorizontalDivider()
    }

    editing?.let { m ->
        var sys by remember(m) { mutableStateOf(m.systolic.toString()) }
        var dia by remember(m) { mutableStateOf(m.diastolic.toString()) }
        var pul by remember(m) { mutableStateOf(m.pulse?.toString() ?: "") }
        var errors by remember(m) { mutableStateOf<Map<EntryField, String>>(emptyMap()) }
        // Mesure 1.0.x enregistrée sans pouls : on ne peut pas l'inventer, le pouls reste facultatif pour cette correction.
        val legacyNoPulse = m.pulse == null
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Corriger la mesure ${m.index}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mesurée le ${Fmt.dateTimeLong(m.localDateTime(vm.zone))} : l'horodatage d'origine est conservé ; la date de correction est enregistrée à part.",
                        style = MaterialTheme.typography.bodySmall)
                    NumberField("SYS — Systolique *", sys, { sys = it; errors = emptyMap() }, unit = "mmHg", isError = errors[EntryField.SYS] != null, supporting = errors[EntryField.SYS])
                    NumberField("DIA — Diastolique *", dia, { dia = it; errors = emptyMap() }, unit = "mmHg", isError = errors[EntryField.DIA] != null, supporting = errors[EntryField.DIA])
                    NumberField(if (legacyNoPulse) "PUL — Fréquence cardiaque" else "PUL — Fréquence cardiaque *", pul, { pul = it; errors = emptyMap() }, unit = "bpm",
                        isError = errors[EntryField.PUL] != null, supporting = errors[EntryField.PUL])
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = MeasurementEntry.validate(sys, dia, pul)
                    val errs = if (legacyNoPulse && pul.isBlank()) v.errors - EntryField.PUL else v.errors
                    if (errs.isNotEmpty() || v.systolic == null || v.diastolic == null) { errors = errs; return@TextButton }
                    val newPulse = if (legacyNoPulse && pul.isBlank()) null else v.pulse
                    val changed = v.systolic != m.systolic || v.diastolic != m.diastolic || newPulse != m.pulse
                    if (changed) {
                        vm.updateMeasurement(m.copy(systolic = v.systolic!!, diastolic = v.diastolic!!, pulse = newPulse, modifiedAt = java.time.Instant.now(),
                            source = if (m.source == MeasurementSource.MANUAL) MeasurementSource.MANUAL else MeasurementSource.OCR_CORRECTED))
                    }
                    editing = null
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Annuler") } },
        )
    }
    if (confirmDelete && s != null) ConfirmDialog(
        "Supprimer la séance ?", "La séance et ses ${s.measurements.size} mesure(s) seront définitivement supprimées.", "Supprimer",
        onConfirm = { confirmDelete = false; vm.deleteSession(s.id); onBack() }, onDismiss = { confirmDelete = false }, destructive = true,
    )
}
