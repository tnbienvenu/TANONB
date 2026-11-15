package com.cardiosuivi.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    val shown = sessions.filter { it.status.isValidated }.filter {
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.MORNING -> it.period == SessionPeriod.MORNING
            HistoryFilter.EVENING -> it.period == SessionPeriod.EVENING
            HistoryFilter.INCOMPLETE -> it.status == SessionStatus.INCOMPLETE
        }
    }
    ScreenScaffold("Historique", onBack = null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryFilter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
        }
        Text("${shown.size} séance(s) validée(s) · valeur de séance : ${settings.aggregation.label}", style = MaterialTheme.typography.labelSmall)
        if (shown.isEmpty()) EmptyState("Aucune séance validée.")
        shown.groupBy { it.localDate }.forEach { (date, list) ->
            Text(Fmt.dateLong(date).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
            list.forEach { s ->
                val st = SessionStatistics.compute(s, settings.aggregation)
                SectionCard(modifier = Modifier.clickable { nav.navigate(Routes.sessionDetail(s.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusDot(if (s.status == SessionStatus.COMPLETED) CardioColors.Done else CardioColors.Incomplete)
                        Text("${s.period.label} · ${s.measurements.size} mesure(s) · ${s.status.label}", style = MaterialTheme.typography.labelMedium)
                    }
                    st.primary?.let { p ->
                        Text("${Fmt.d0(p.systolic)}/${Fmt.d0(p.diastolic)} mmHg" + (p.pulse?.let { " · ${Fmt.d0(it)} /min" } ?: ""), style = MaterialTheme.typography.titleLarge)
                    }
                    Text(s.measurements.joinToString("  ·  ") { "${it.systolic}/${it.diastolic}" }, style = MaterialTheme.typography.bodySmall)
                    if (s.symptoms.isNotEmpty()) Text("Symptômes : ${s.symptoms.joinToString { it.label.lowercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    ScreenScaffold("Détail de la séance", onBack = onBack, actions = {
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Supprimer la séance") }
    }) {
        if (s == null) { Text("Séance introuvable."); return@ScreenScaffold }
        val st = SessionStatistics.compute(s, settings.aggregation)
        Text("${Fmt.dateLong(s.localDate).replaceFirstChar { it.uppercase() }} · ${s.period.label} · ${s.protocol.label} · ${s.arm.label}", style = MaterialTheme.typography.bodySmall)
        SectionCard("Valeur de séance") {
            st.primary?.let { p -> BpValue(Fmt.d0(p.systolic), Fmt.d0(p.diastolic), p.pulse?.let { Fmt.d0(it) }) }
            Text("${settings.aggregation.label} · statut : ${s.status.label}", style = MaterialTheme.typography.labelSmall)
        }
        SectionCard("Mesures") {
            s.measurements.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${m.index}. ${m.systolic}/${m.diastolic} mmHg" + (m.pulse?.let { " · $it /min" } ?: ""), fontWeight = FontWeight.Medium)
                        Text("${timeFmt.format(m.measuredAt.atZone(vm.zone))} · ${m.source.label}" + (m.ocrConfidence?.let { " · confiance OCR ${Fmt.pct(it)}" } ?: ""), style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { editing = m }) { Icon(Icons.Filled.Edit, "Corriger") }
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
            if (notes != s.notes) OutlinedButton(onClick = { vm.updateSession(s.copy(notes = notes)) }) { Text("Enregistrer la note") }
        }
        HorizontalDivider()
    }

    editing?.let { m ->
        var sys by remember(m) { mutableStateOf(m.systolic.toString()) }
        var dia by remember(m) { mutableStateOf(m.diastolic.toString()) }
        var pul by remember(m) { mutableStateOf(m.pulse?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Corriger la mesure ${m.index}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Systolique", sys, { sys = it })
                    NumberField("Diastolique", dia, { dia = it })
                    NumberField("Pouls (facultatif)", pul, { pul = it })
                }
            },
            confirmButton = {
                TextButton(enabled = sys.toIntOrNull() != null && dia.toIntOrNull() != null, onClick = {
                    vm.updateMeasurement(m.copy(systolic = sys.toInt(), diastolic = dia.toInt(), pulse = pul.toIntOrNull(),
                        source = if (m.source == MeasurementSource.MANUAL) MeasurementSource.MANUAL else MeasurementSource.OCR_CORRECTED))
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
