package com.cardiosuivi.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.cardiosuivi.app.ui.AppViewModel
import com.cardiosuivi.app.ui.Routes
import com.cardiosuivi.app.ui.charts.Bar
import com.cardiosuivi.app.ui.charts.BarGroup
import com.cardiosuivi.app.ui.charts.ChartPoint
import com.cardiosuivi.app.ui.charts.ChartSeries
import com.cardiosuivi.app.ui.charts.GroupedBarChart
import com.cardiosuivi.app.ui.charts.RefLine
import com.cardiosuivi.app.ui.charts.TimeSeriesChart
import com.cardiosuivi.app.ui.components.Appear
import com.cardiosuivi.app.ui.components.CardHeader
import com.cardiosuivi.app.ui.components.EmptyState
import com.cardiosuivi.app.ui.components.HealthCard
import com.cardiosuivi.app.ui.components.PeriodChoice
import com.cardiosuivi.app.ui.components.PeriodPreset
import com.cardiosuivi.app.ui.components.PeriodSelector
import com.cardiosuivi.app.ui.components.ScreenScaffold
import com.cardiosuivi.app.ui.components.SegmentedControl
import com.cardiosuivi.app.ui.components.StatTile
import com.cardiosuivi.app.ui.navigateTab
import com.cardiosuivi.app.ui.theme.Health
import com.cardiosuivi.app.ui.theme.HealthColors
import com.cardiosuivi.app.ui.theme.Spacing
import com.cardiosuivi.domain.reference.MedicalThresholds
import com.cardiosuivi.domain.stats.GroupStats
import com.cardiosuivi.domain.stats.SessionValue
import com.cardiosuivi.domain.util.Fmt
import java.time.format.DateTimeFormatter

/** Écran « Tendances » : tableau de bord cardiovasculaire (toutes les vues graphiques, interactives). */
@Composable
fun ChartsScreen(vm: AppViewModel, nav: NavHostController) {
    val sessions by vm.sessions.collectAsState()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    var choice by remember { mutableStateOf(PeriodChoice(PeriodPreset.D30)) }
    var individual by rememberSaveable { mutableStateOf(false) }
    val today = vm.now().toLocalDate()
    val earliest = sessions.filter { it.status.isValidated }.minOfOrNull { it.localDate }
    val (from, to) = choice.range(today, earliest)
    val report = remember(sessions, settings, profile, from, to) { vm.periodReport(from, to) }
    val dt = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    val c = Health.colors

    ScreenScaffold("Tendances", onBack = null, subtitle = "Votre tension artérielle dans le temps") {
        PeriodSelector(choice, { choice = it }, today, earliest)
        Text(
            "${report.sessionCount} séance(s), ${report.measurementCount} mesure(s), ${report.daysWithData} jour(s) avec mesures sur ${report.calendarDays}. Jours sans mesure : ${report.calendarDays - report.daysWithData}.",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val values = report.sessionValues
        fun ts(v: SessionValue) = v.session.startedAt.toEpochMilli()
        fun whenOf(v: SessionValue) = "${dt.format(v.session.startedAt.atZone(vm.zone))} (${v.session.period.label.lowercase()})"

        if (values.isEmpty()) {
            EmptyState("Aucune séance validée sur cette période. Les graphiques apparaîtront après vos premières mesures ; aucune valeur n'est jamais estimée.", Icons.Filled.ShowChart)
        }

        // Synthèse chiffrée de la période.
        Appear(0) {
            HealthCard {
                CardHeader("Moyennes de la période", Icons.Filled.BarChart, c.diastolic)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s), maxItemsInEachRow = 3) {
                    val m = Modifier.weight(1f).widthIn(min = 96.dp)
                    StatTile("Systolique", report.overall.systolic.mean?.let { "${Fmt.d0(it)} mmHg" } ?: "—", "moyenne des séances", c.systolic, m)
                    StatTile("Diastolique", report.overall.diastolic.mean?.let { "${Fmt.d0(it)} mmHg" } ?: "—", "moyenne des séances", c.diastolic, m)
                    StatTile("Pouls", report.overall.pulse.mean?.let { "${Fmt.d0(it)} bpm" } ?: "—", "moyenne des séances", c.pulse, m)
                }
            }
        }

        // 1. Tension artérielle.
        Appear(1) {
            HealthCard {
                CardHeader("Tension artérielle", Icons.Filled.Favorite, c.systolic, trailing = "mmHg")
                SegmentedControl(listOf(false, true), individual, { if (it) "Mesures individuelles" else "Valeurs de séance" }, { individual = it })
                val refs = buildList {
                    add(RefLine(MedicalThresholds.HOME_HYPERTENSION.systolic!!.toDouble(), "Repère domicile 135", c.threshold))
                    add(RefLine(MedicalThresholds.HOME_HYPERTENSION.diastolic!!.toDouble(), "Repère domicile 85", c.threshold))
                    profile.target?.let { add(RefLine(it.systolic.toDouble(), "Objectif ${it.systolic}", c.target)); add(RefLine(it.diastolic.toDouble(), "Objectif ${it.diastolic}", c.target)) }
                }
                if (individual) {
                    val ms = values.flatMap { v -> v.session.measurements.map { v to it } }
                    TimeSeriesChart(
                        listOf(
                            ChartSeries("Systolique", c.systolic, ms.map { (_, m) -> ChartPoint(m.measuredAt.toEpochMilli(), m.systolic.toDouble(), "${m.systolic} mmHg — ${dt.format(m.measuredAt.atZone(vm.zone))}, mesure ${m.index}") }),
                            ChartSeries("Diastolique", c.diastolic, ms.map { (_, m) -> ChartPoint(m.measuredAt.toEpochMilli(), m.diastolic.toDouble(), "${m.diastolic} mmHg — ${dt.format(m.measuredAt.atZone(vm.zone))}, mesure ${m.index}") }),
                        ),
                        refLines = refs,
                    )
                } else {
                    TimeSeriesChart(
                        listOf(
                            ChartSeries("Systolique", c.systolic, values.map { ChartPoint(ts(it), it.primary.systolic, "${Fmt.d1(it.primary.systolic)} mmHg — ${whenOf(it)}") }),
                            ChartSeries("Diastolique", c.diastolic, values.map { ChartPoint(ts(it), it.primary.diastolic, "${Fmt.d1(it.primary.diastolic)} mmHg — ${whenOf(it)}") }),
                        ),
                        refLines = refs,
                    )
                }
                Note("Chaque point est une séance (ou une mesure) réellement enregistrée ; la courbe est interrompue au-delà de 8 jours sans donnée. Valeur de séance : ${settings.aggregation.label}.")
            }
        }

        // 2. Fréquence cardiaque.
        Appear(2) {
            HealthCard {
                CardHeader("Fréquence cardiaque", Icons.Filled.MonitorHeart, c.pulse, trailing = "bpm")
                TimeSeriesChart(listOf(ChartSeries("Fréquence cardiaque", c.pulse,
                    values.filter { it.primary.pulse != null }.map { ChartPoint(ts(it), it.primary.pulse!!, "${Fmt.d0(it.primary.pulse)} /min — ${whenOf(it)}") })), height = 180)
            }
        }

        // 3. Matin / soir.
        Appear(3) {
            HealthCard {
                CardHeader("Matin et soir", Icons.Filled.WbSunny, c.morning)
                val me = report.morningVsEvening
                GroupedBarChart(listOf(bpGroup("Matin", me.a, c), bpGroup("Soir", me.b, c)), legendOf(c))
                Note("Écart soir − matin : ${Fmt.signed1(me.systolicDiff)} / ${Fmt.signed1(me.diastolicDiff)} mmHg." + if (!me.sufficient) " Effectifs insuffisants pour conclure." else "")
            }
        }

        // 4. Moyennes hebdomadaires et mensuelles.
        Appear(4) {
            HealthCard {
                CardHeader("Moyennes hebdomadaires", Icons.Filled.DateRange, c.diastolic)
                GroupedBarChart(report.weekly.map { bpGroup(Fmt.date(it.start).take(5), it.stats, c) }, legendOf(c))
            }
        }
        Appear(5) {
            HealthCard {
                CardHeader("Moyennes mensuelles", Icons.Filled.CalendarMonth, c.diastolic)
                GroupedBarChart(report.monthly.map { bpGroup(it.label, it.stats, c) }, legendOf(c))
            }
        }

        // 5. Variabilité.
        Appear(6) {
            HealthCard {
                CardHeader("Variabilité tensionnelle", Icons.Filled.Waves, c.attention, trailing = "mmHg")
                Note("Écart-type des mesures au sein de chaque séance (mmHg).")
                TimeSeriesChart(listOf(
                    ChartSeries("Écart-type SYS", c.systolic, values.filter { it.stats.systolic.sd != null }.map { ChartPoint(ts(it), it.stats.systolic.sd!!, "${Fmt.d1(it.stats.systolic.sd)} mmHg — ${whenOf(it)}") }),
                    ChartSeries("Écart-type DIA", c.diastolic, values.filter { it.stats.diastolic.sd != null }.map { ChartPoint(ts(it), it.stats.diastolic.sd!!, "${Fmt.d1(it.stats.diastolic.sd)} mmHg — ${whenOf(it)}") }),
                ), height = 170)
                val v = report.variability
                Note("Entre séances : écart-type ${Fmt.d1(v.systolicSdBetweenSessions)} / ${Fmt.d1(v.diastolicSdBetweenSessions)} mmHg, variabilité successive moyenne ${Fmt.d1(v.systolicArv)} / ${Fmt.d1(v.diastolicArv)} mmHg.")
            }
        }

        // 6. Pression pulsée et jours de la semaine.
        Appear(6) {
            HealthCard {
                CardHeader("Pression pulsée", Icons.Filled.ShowChart, c.pulsePressure, trailing = "mmHg")
                TimeSeriesChart(listOf(ChartSeries("Pression pulsée", c.pulsePressure,
                    values.map { ChartPoint(ts(it), it.primary.pulsePressure, "${Fmt.d0(it.primary.pulsePressure)} mmHg — ${whenOf(it)}") })), height = 170)
            }
        }
        Appear(6) {
            HealthCard {
                CardHeader("Jours de la semaine", Icons.Filled.CalendarMonth, c.evening)
                GroupedBarChart(report.byWeekday.map { (d, g) -> bpGroup(d.getDisplayName(java.time.format.TextStyle.SHORT, Fmt.LOCALE), g, c) }, legendOf(c))
            }
        }

        // 7. Historique.
        Appear(6) {
            HealthCard(onClick = { nav.navigateTab(Routes.HISTORY) }) {
                CardHeader("Historique des mesures", Icons.AutoMirrored.Filled.List, MaterialTheme.colorScheme.primary, chevron = true)
                values.sortedByDescending { it.session.startedAt }.take(5).forEach { v ->
                    com.cardiosuivi.app.ui.components.KeyValue(
                        "${Fmt.date(v.session.localDate)} · ${v.session.period.label}",
                        "${Fmt.d0(v.primary.systolic)}/${Fmt.d0(v.primary.diastolic)} mmHg",
                    )
                }
                if (values.isEmpty()) Note("Aucune séance sur la période.")
                TextButton(onClick = { nav.navigateTab(Routes.HISTORY) }) { Text("Tout l'historique") }
            }
        }
    }
}

@Composable
private fun Note(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

private fun legendOf(c: HealthColors) = listOf("Systolique" to c.systolic, "Diastolique" to c.diastolic, "Pouls" to c.pulse)

private fun bpGroup(label: String, g: GroupStats, c: HealthColors) = BarGroup(
    label,
    listOf(
        Bar(g.systolic.mean, c.systolic, "SYS ${Fmt.d1(g.systolic.mean)} mmHg (${g.sessionCount} séances)"),
        Bar(g.diastolic.mean, c.diastolic, "DIA ${Fmt.d1(g.diastolic.mean)} mmHg"),
        Bar(g.pulse.mean, c.pulse, "Pouls ${Fmt.d1(g.pulse.mean)} /min"),
    ),
    g.sessionCount,
)
