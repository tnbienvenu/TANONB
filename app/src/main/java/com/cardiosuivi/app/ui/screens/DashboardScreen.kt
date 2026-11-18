package com.cardiosuivi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.cardiosuivi.app.report.Sharing
import com.cardiosuivi.app.ui.AppViewModel
import com.cardiosuivi.app.ui.Routes
import com.cardiosuivi.app.ui.charts.ChartPoint
import com.cardiosuivi.app.ui.charts.ChartSeries
import com.cardiosuivi.app.ui.charts.TimeSeriesChart
import com.cardiosuivi.app.ui.components.AlertCard
import com.cardiosuivi.app.ui.components.Appear
import com.cardiosuivi.app.ui.components.BpValue
import com.cardiosuivi.app.ui.components.CardHeader
import com.cardiosuivi.app.ui.components.Disclaimer
import com.cardiosuivi.app.ui.components.HealthCard
import com.cardiosuivi.app.ui.components.MaxContentWidth
import com.cardiosuivi.app.ui.components.PeriodChoice
import com.cardiosuivi.app.ui.components.PeriodPreset
import com.cardiosuivi.app.ui.components.PrimaryButton
import com.cardiosuivi.app.ui.components.SecondaryButton
import com.cardiosuivi.app.ui.components.SegmentedControl
import com.cardiosuivi.app.ui.components.StatTile
import com.cardiosuivi.app.ui.components.StatusBadge
import com.cardiosuivi.app.ui.navigateTab
import com.cardiosuivi.app.ui.theme.Health
import com.cardiosuivi.app.ui.theme.NumberStyles
import com.cardiosuivi.app.ui.theme.Spacing
import com.cardiosuivi.domain.alerts.AlertLevel
import com.cardiosuivi.domain.analysis.FindingSeverity
import com.cardiosuivi.domain.stats.SessionStatistics
import com.cardiosuivi.domain.util.Fmt
import com.cardiosuivi.domain.entry.localDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val dashboardPeriods = listOf(PeriodPreset.D7, PeriodPreset.D30, PeriodPreset.D90, PeriodPreset.YEAR)

@Composable
fun DashboardScreen(vm: AppViewModel, nav: NavHostController) {
    val analysis by vm.analysis.collectAsState()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    val inProgress by vm.inProgress.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val context = LocalContext.current
    val c = Health.colors
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val today = vm.now().toLocalDate()

    com.cardiosuivi.app.ui.components.ScreenScaffold(title = "", onBack = null) {
        // ---- En-tête -------------------------------------------------------------------------------------------
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("CARDIOSUIVI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    if (profile.displayName.isNotBlank()) "Bonjour ${profile.displayName}" else "Bonjour",
                    style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() },
                )
                Text("Résumé de votre santé cardiovasculaire", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.dateLong(today).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { nav.navigateTab(Routes.MORE) }) {
                Icon(Icons.Filled.AccountCircle, contentDescription = "Profil", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            }
        }

        // ---- Séance en cours -----------------------------------------------------------------------------------
        inProgress?.let { s ->
            Appear(0) {
                HealthCard(onClick = { nav.navigate(Routes.session(resume = s.id)) }) {
                    CardHeader("Séance en cours", Icons.Filled.MonitorHeart, MaterialTheme.colorScheme.primary, chevron = true)
                    Text("Commencée le ${Fmt.date(s.localDate)} à ${timeFmt.format(s.startedAt.atZone(vm.zone))} — ${s.measurements.size}/${s.plannedMeasurements} mesure(s) enregistrée(s), non validée.")
                    PrimaryButton("Reprendre", { nav.navigate(Routes.session(resume = s.id)) })
                }
            }
        }

        // ---- Alertes importantes -------------------------------------------------------------------------------
        analysis?.alerts?.filter { it.level >= AlertLevel.CONSULT_SOON }?.forEach { a ->
            AlertCard(a) { Sharing.dial(context, settings.emergencyNumber) }
        }

        // ---- Dernière tension ----------------------------------------------------------------------------------
        val report = analysis
        val last = report?.lastSession
        val lastStats = report?.lastSessionStats
        Appear(1) {
            HealthCard(onClick = last?.let { { nav.navigate(Routes.sessionDetail(it.id)) } }) {
                CardHeader("Dernière séance", Icons.Filled.Favorite, c.systolic,
                    trailing = last?.let { l -> l.measurements.maxByOrNull { it.index }?.let { timeFmt.format(it.localDateTime(vm.zone)) } }, chevron = last != null)
                if (last == null || lastStats?.primary == null) {
                    Text("Aucune séance validée pour l'instant.", style = MaterialTheme.typography.bodyLarge)
                    Text("Vos dernières valeurs apparaîtront ici après votre première séance de mesures.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val m = last.measurements.maxBy { it.index }
                    Text("Dernière mesure · ${Fmt.dateTimeLong(m.localDateTime(vm.zone))} · ${com.cardiosuivi.app.ui.session.periodText(last.period)}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BpValue(m.systolic.toString(), m.diastolic.toString(), m.pulse?.toString() ?: "—", style = NumberStyles.hero)
                    val p = lastStats.primary!!
                    Column(
                        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Moyenne de référence de la séance : ${Fmt.d0(p.systolic)}/${Fmt.d0(p.diastolic)} mmHg" + (p.pulse?.let { " · ${Fmt.d0(it)} bpm" } ?: ""),
                            style = MaterialTheme.typography.titleSmall)
                        Text("${last.measurements.size} mesure(s) · ${settings.aggregation.label}${if (lastStats.methodFallback) " (moyenne des mesures disponibles)" else ""}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Évolution par rapport à la séance précédente (valeurs de séance réellement enregistrées).
                        val prev = sessions.filter { it.status.isValidated && it.measurements.isNotEmpty() && it.id != last.id && it.startedAt < last.startedAt }.maxByOrNull { it.startedAt }
                        val pp = prev?.let { SessionStatistics.compute(it, settings.aggregation).primary }
                        if (pp != null) {
                            val ds = p.systolic - pp.systolic; val dd = p.diastolic - pp.diastolic
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(trendIcon(ds), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Text("Par rapport à la séance précédente (${Fmt.date(prev.localDate)}) : ${Fmt.signed1(ds)} / ${Fmt.signed1(dd)} mmHg",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        } else Text("Pas de séance précédente pour comparer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ---- Évolution tensionnelle ----------------------------------------------------------------------------
        var preset by rememberSaveable { mutableStateOf(PeriodPreset.D7) }
        val (from, to) = PeriodChoice(preset).range(today, null)
        val period = remember(sessions, settings, profile, from, to) { vm.periodReport(from, to) }
        Appear(2) {
            HealthCard {
                CardHeader("Évolution tensionnelle", Icons.Filled.Insights, c.diastolic, trailing = "${period.sessionCount} séance(s)")
                SegmentedControl(dashboardPeriods, preset, { it.label }, { preset = it })
                val dt = DateTimeFormatter.ofPattern("dd/MM HH:mm")
                val values = period.sessionValues
                TimeSeriesChart(
                    listOf(
                        ChartSeries("Systolique", c.systolic, values.map { ChartPoint(it.session.startedAt.toEpochMilli(), it.primary.systolic, "${Fmt.d0(it.primary.systolic)} mmHg — ${dt.format(it.session.startedAt.atZone(vm.zone))}") }),
                        ChartSeries("Diastolique", c.diastolic, values.map { ChartPoint(it.session.startedAt.toEpochMilli(), it.primary.diastolic, "${Fmt.d0(it.primary.diastolic)} mmHg — ${dt.format(it.session.startedAt.atZone(vm.zone))}") }),
                    ),
                    height = 180,
                )
                TextButton(onClick = { nav.navigateTab(Routes.CHARTS) }) { Text("Voir toutes les tendances") }
            }
        }

        // ---- Prochaine séance ----------------------------------------------------------------------------------
        Appear(3) {
            HealthCard {
                CardHeader("Prochaine séance", Icons.Filled.Alarm, c.attention)
                val next = vm.nextSlots(1).firstOrNull()
                if (settings.reminders.enabled && next != null) {
                    Text(Fmt.dateLong(next.date).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text("${next.time}", style = NumberStyles.large)
                        Text("· séance du ${next.period.label.lowercase()}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text("${settings.measurementsPerSession} mesures prévues, espacées d'environ ${settings.intervalSeconds} secondes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Aucun rappel programmé.", style = MaterialTheme.typography.bodyLarge)
                }
                if (settings.reminders.enabled && !com.cardiosuivi.app.reminders.Notifications.canPost(context)) {
                    StatusBadge("Notifications bloquées", c.alert, Icons.Filled.Warning)
                    Text("Les notifications sont bloquées : vous ne recevrez pas les rappels.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { nav.navigate(Routes.PERMISSIONS) }) { Text("Vérifier les autorisations") }
                }
                // Android 12+ : sans l'autorisation « Alarmes et rappels », Android peut retarder le rappel jusqu'à 10 minutes.
                if (settings.reminders.enabled && !vm.c.reminderScheduler.canScheduleExact()) {
                    StatusBadge("Rappels approximatifs", c.attention, Icons.Filled.Warning)
                    Text("Les rappels peuvent être décalés de quelques minutes : autorisez « Alarmes et rappels » pour qu'ils arrivent à l'heure exacte.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { com.cardiosuivi.app.reminders.ExactAlarmSettings.open(context) }) { Text("Autoriser les rappels à l'heure exacte") }
                }
                PrimaryButton("Commencer une séance", { nav.navigate(Routes.session()) }, icon = Icons.Filled.PlayArrow)
                TextButton(onClick = { nav.navigate(Routes.REMINDERS) }) { Text("Modifier les rappels") }
            }
        }

        // ---- Tendances -----------------------------------------------------------------------------------------
        if (report != null) Appear(4) {
            HealthCard(onClick = { nav.navigateTab(Routes.CHARTS) }) {
                CardHeader("Tendances", Icons.AutoMirrored.Filled.TrendingUp, c.pulse, chevron = true)
                val r7 = report.last7
                val r30 = report.last30
                Text(
                    "Moyenne 7 jours : " + if (r7.sessionCount == 0) "—" else "${Fmt.bp(r7.overall.systolic.mean, r7.overall.diastolic.mean)} mmHg",
                    style = MaterialTheme.typography.titleMedium,
                )
                val t = r30.trend
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s), maxItemsInEachRow = 2) {
                    val tileMod = Modifier.weight(1f).widthIn(min = 140.dp)
                    StatTile("Évolution systolique", t?.let { "${Fmt.signed1(it.systolicSlopePerWeek)} mmHg/sem." } ?: "—", if (t == null) "30 j : données insuffisantes" else "sur 30 jours", c.systolic, tileMod)
                    StatTile("Évolution diastolique", t?.let { "${Fmt.signed1(it.diastolicSlopePerWeek)} mmHg/sem." } ?: "—", if (t == null) "30 j : données insuffisantes" else "sur 30 jours", c.diastolic, tileMod)
                    StatTile("Variabilité", "${Fmt.d1(r30.variability.systolicArv)} / ${Fmt.d1(r30.variability.diastolicArv)}", "variabilité successive, mmHg (30 j)", modifier = tileMod)
                    val comp = r7.completeness?.completionRate
                    StatTile("Complétude du suivi", comp?.let { Fmt.pct(it) } ?: "—", r7.completeness?.let { "${it.doneSlots + it.incompleteSlots}/${it.expectedSlots - it.pendingSlots} séances prévues (7 j)" } ?: "aucune séance programmée", modifier = tileMod)
                }
                Text("Fréquence cardiaque moyenne (7 j) : " + (r7.overall.pulse.mean?.let { "${Fmt.d0(it)} bpm" } ?: "—") + " · Séances (7 j) : ${r7.sessionCount} séance(s), ${r7.daysWithData} jour(s)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (t != null) Text(
                    if (abs(t.systolicSlopePerWeek) < 1 && abs(t.diastolicSlopePerWeek) < 1) "Tendance sur 30 jours : stable." else "Tendance sur 30 jours : ${Fmt.signed1(t.systolicSlopePerWeek)} / ${Fmt.signed1(t.diastolicSlopePerWeek)} mmHg par semaine.",
                    style = MaterialTheme.typography.bodySmall,
                )
                r7.completeness?.let { cp -> if (cp.missedSlots > 0) Text("${cp.missedSlots} séance(s) programmée(s) non réalisée(s) cette semaine.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        // ---- Objectif personnel --------------------------------------------------------------------------------
        Appear(5) {
            HealthCard {
                CardHeader("Objectif personnel", Icons.Filled.Flag, c.target)
                val t = profile.target
                if (t == null) {
                    Text("Non renseigné. Indiquez l'objectif fixé avec votre médecin.")
                    TextButton(onClick = { nav.navigate(Routes.PROFILE) }) { Text("Renseigner le profil") }
                } else {
                    Text("< ${t.systolic}/${t.diastolic} mmHg (mesures à domicile)", style = MaterialTheme.typography.titleMedium)
                    report?.last30?.targetExceedance?.let { e -> if (e.sessionCount > 0) Text("30 jours : ${e.sessionsAbove} séance(s) sur ${e.sessionCount} au-dessus de l'objectif.", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        // ---- Analyse ------------------------------------------------------------------------------------------
        if (report != null) Appear(6) {
            HealthCard {
                CardHeader("Analyse", Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.tertiary)
                StatusBadge("Calculée localement · sans IA", c.neutral)
                val key = report.findings.firstOrNull { it.title.startsWith("Moyenne sur 7") } ?: report.findings.firstOrNull { it.severity >= FindingSeverity.WATCH } ?: report.findings.firstOrNull()
                if (key == null) Text("Pas encore assez de données pour une synthèse.", style = MaterialTheme.typography.bodyMedium)
                else {
                    Text(key.title, style = MaterialTheme.typography.titleSmall)
                    Text(key.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                }
                Text("Calculée le ${Fmt.dateTimeLong(vm.now().withNano(0))} à partir des séances enregistrées.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (report.pointsToDiscuss.isNotEmpty()) Text("${report.pointsToDiscuss.size} point(s) à discuter avec votre médecin.", style = MaterialTheme.typography.bodySmall, color = c.attention)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val ai by vm.latestAi.collectAsState()
                val la = ai
                if (la == null) Text("Interprétation IA : aucune enregistrée.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    StatusBadge("Générée par une IA", MaterialTheme.colorScheme.tertiary, Icons.Filled.AutoAwesome)
                    Text("Dernière interprétation IA : ${Fmt.dateTimeLong(la.createdAt.atZone(vm.zone))} (${la.provider})", style = MaterialTheme.typography.labelMedium)
                    Text(la.answer, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                SecondaryButton("Consulter mon analyse", { nav.navigateTab(Routes.ANALYSIS) })
            }
        }

        if (sessions.isEmpty()) SecondaryButton("Compléter mon profil", { nav.navigate(Routes.PROFILE) })
        Disclaimer()
    }
}

private fun trendIcon(delta: Double) = when {
    delta >= 1 -> Icons.AutoMirrored.Filled.TrendingUp
    delta <= -1 -> Icons.AutoMirrored.Filled.TrendingDown
    else -> Icons.AutoMirrored.Filled.TrendingFlat
}

