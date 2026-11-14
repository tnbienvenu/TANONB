package com.cardiosuivi.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.cardiosuivi.app.ui.components.Disclaimer
import com.cardiosuivi.app.ui.components.KeyValue
import com.cardiosuivi.app.ui.components.ScreenScaffold
import com.cardiosuivi.app.ui.components.SectionCard
import com.cardiosuivi.domain.alerts.AlertLevel
import com.cardiosuivi.domain.util.Fmt
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun DashboardScreen(vm: AppViewModel, nav: NavHostController) {
    val analysis by vm.analysis.collectAsState()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    val inProgress by vm.inProgress.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val context = LocalContext.current
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    ScreenScaffold("CardioSuivi", onBack = null) {
        Text(
            if (profile.displayName.isNotBlank()) "Bonjour ${profile.displayName}" else "Suivi de la tension artérielle",
            style = MaterialTheme.typography.titleMedium,
        )

        Button(
            onClick = { nav.navigate(Routes.session()) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(28.dp))
            Text("  NOUVELLE SÉANCE", style = MaterialTheme.typography.titleMedium)
        }

        inProgress?.let { s ->
            SectionCard("Séance en cours") {
                Text("Commencée le ${Fmt.date(s.localDate)} à ${timeFmt.format(s.startedAt.atZone(vm.zone))} — ${s.measurements.size}/${s.plannedMeasurements} mesure(s) enregistrée(s), non validée.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { nav.navigate(Routes.session(resume = s.id)) }) { Text("Reprendre") }
                }
            }
        }

        // Alertes urgentes ou importantes de la dernière séance.
        analysis?.alerts?.filter { it.level >= AlertLevel.CONSULT_SOON }?.forEach { a ->
            AlertCard(a) { Sharing.dial(context, settings.emergencyNumber) }
        }

        val report = analysis
        val last = report?.lastSession
        val lastStats = report?.lastSessionStats
        SectionCard("Dernière séance") {
            if (last == null || lastStats?.primary == null) {
                Text("Aucune séance validée pour l'instant.")
            } else {
                val p = lastStats.primary!!
                Text("${Fmt.dateLong(last.localDate)} · ${last.period.label} · ${last.measurements.size} mesure(s)", style = MaterialTheme.typography.bodySmall)
                BpValue(Fmt.d0(p.systolic), Fmt.d0(p.diastolic), p.pulse?.let { Fmt.d0(it) })
                Text("${settings.aggregation.label}${if (lastStats.methodFallback) " (non applicable : moyenne des mesures disponibles)" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { nav.navigate(Routes.sessionDetail(last.id)) }) { Text("Voir le détail") }
            }
        }

        if (report != null) {
            SectionCard("Tendance récente") {
                val r7 = report.last7
                KeyValue("Moyenne 7 jours", if (r7.sessionCount == 0) "—" else "${Fmt.bp(r7.overall.systolic.mean, r7.overall.diastolic.mean)} mmHg")
                KeyValue("Fréquence cardiaque moyenne (7 j)", r7.overall.pulse.mean?.let { "${Fmt.d0(it)} /min" } ?: "—")
                KeyValue("Séances (7 j)", "${r7.sessionCount} séance(s), ${r7.daysWithData} jour(s)")
                val r30 = report.last30
                KeyValue("Moyenne 30 jours", if (r30.sessionCount == 0) "—" else "${Fmt.bp(r30.overall.systolic.mean, r30.overall.diastolic.mean)} mmHg (${r30.sessionCount} séances)")
                val t = r30.trend
                Text(
                    when {
                        t == null -> r30.trendUnavailableReason ?: "Tendance non disponible."
                        abs(t.systolicSlopePerWeek) < 1 && abs(t.diastolicSlopePerWeek) < 1 -> "Tendance sur 30 jours : stable."
                        else -> "Tendance sur 30 jours : ${Fmt.signed1(t.systolicSlopePerWeek)} / ${Fmt.signed1(t.diastolicSlopePerWeek)} mmHg par semaine."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                r7.completeness?.let { c -> if (c.missedSlots > 0) Text("${c.missedSlots} séance(s) programmée(s) non réalisée(s) cette semaine.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        SectionCard("Objectif personnel") {
            val t = profile.target
            if (t == null) {
                Text("Non renseigné. Indiquez l'objectif fixé avec votre médecin.")
                TextButton(onClick = { nav.navigate(Routes.PROFILE) }) { Text("Renseigner le profil") }
            } else {
                Text("< ${t.systolic}/${t.diastolic} mmHg (mesures à domicile)", fontWeight = FontWeight.Medium)
                report?.last30?.targetExceedance?.let { e -> if (e.sessionCount > 0) Text("30 jours : ${e.sessionsAbove} séance(s) sur ${e.sessionCount} au-dessus de l'objectif.", style = MaterialTheme.typography.bodySmall) }
            }
        }

        SectionCard("Prochaines séances programmées") {
            if (settings.reminders.enabled && !com.cardiosuivi.app.reminders.Notifications.canPost(context)) {
                Text("Les notifications sont bloquées : vous ne recevrez pas les rappels.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { nav.navigate(Routes.PERMISSIONS) }) { Text("Vérifier les autorisations") }
            }
            val next = vm.nextSlots(4)
            if (!settings.reminders.enabled || next.isEmpty()) {
                Text("Aucun rappel programmé.")
            } else next.forEach { s ->
                KeyValue(Fmt.dateLong(s.date).replaceFirstChar { it.uppercase() }, "${s.period.label} · ${s.time}")
            }
            TextButton(onClick = { nav.navigate(Routes.REMINDERS) }) { Text("Modifier les rappels") }
        }

        SectionCard("Accès rapides") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(onClick = { nav.navigate(Routes.HISTORY) }, label = { Text("Historique") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) })
                AssistChip(onClick = { nav.navigate(Routes.CHARTS) }, label = { Text("Graphiques") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ShowChart, null) })
                AssistChip(onClick = { nav.navigate(Routes.ANALYSIS) }, label = { Text("Analyse") }, leadingIcon = { Icon(Icons.Filled.Analytics, null) })
                AssistChip(onClick = { nav.navigate(Routes.ASSISTANT) }, label = { Text("Assistant IA") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, null) })
                AssistChip(onClick = { nav.navigate(Routes.REPORTS) }, label = { Text("Rapports") }, leadingIcon = { Icon(Icons.Filled.PictureAsPdf, null) })
                AssistChip(onClick = { nav.navigate(Routes.CALENDAR) }, label = { Text("Calendrier") }, leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) })
            }
        }
        if (sessions.isEmpty()) {
            OutlinedButton(onClick = { nav.navigate(Routes.PROFILE) }, modifier = Modifier.fillMaxWidth()) { Text("Compléter mon profil") }
        }
        Disclaimer()
    }
}
