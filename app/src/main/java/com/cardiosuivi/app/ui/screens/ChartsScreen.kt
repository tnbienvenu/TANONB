package com.cardiosuivi.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardiosuivi.app.ui.AppViewModel
import com.cardiosuivi.app.ui.charts.Bar
import com.cardiosuivi.app.ui.charts.BarGroup
import com.cardiosuivi.app.ui.charts.ChartPoint
import com.cardiosuivi.app.ui.charts.ChartSeries
import com.cardiosuivi.app.ui.charts.GroupedBarChart
import com.cardiosuivi.app.ui.charts.RefLine
import com.cardiosuivi.app.ui.charts.TimeSeriesChart
import com.cardiosuivi.app.ui.components.PeriodChoice
import com.cardiosuivi.app.ui.components.PeriodPreset
import com.cardiosuivi.app.ui.components.PeriodSelector
import com.cardiosuivi.app.ui.components.ScreenScaffold
import com.cardiosuivi.app.ui.components.SectionCard
import com.cardiosuivi.app.ui.theme.CardioColors
import com.cardiosuivi.domain.reference.MedicalThresholds
import com.cardiosuivi.domain.stats.GroupStats
import com.cardiosuivi.domain.util.Fmt
import java.time.format.DateTimeFormatter

private enum class ChartKind(val label: String) {
    BP("Tension"), PULSE("Pouls"), PP("Pression pulsée"), VAR("Variabilité"), ME("Matin / soir"), WEEK("Hebdomadaire"), MONTH("Mensuel"), WEEKDAY("Jours")
}

@Composable
fun ChartsScreen(vm: AppViewModel) {
    val sessions by vm.sessions.collectAsState()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    var choice by remember { mutableStateOf(PeriodChoice(PeriodPreset.D30)) }
    var kind by rememberSaveable { mutableStateOf(ChartKind.BP) }
    var individual by rememberSaveable { mutableStateOf(false) }
    val today = vm.now().toLocalDate()
    val earliest = sessions.filter { it.status.isValidated }.minOfOrNull { it.localDate }
    val (from, to) = choice.range(today, earliest)
    val report = remember(sessions, settings, profile, from, to) { vm.periodReport(from, to) }
    val dt = DateTimeFormatter.ofPattern("dd/MM HH:mm")

    ScreenScaffold("Graphiques", onBack = null) {
        PeriodSelector(choice, { choice = it }, today, earliest)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }) }
        }
        Text("${report.sessionCount} séance(s), ${report.measurementCount} mesure(s), ${report.daysWithData} jour(s) avec mesures sur ${report.calendarDays}. Jours sans mesure : ${report.calendarDays - report.daysWithData}.", style = MaterialTheme.typography.labelSmall)

        val values = report.sessionValues
        fun ts(v: com.cardiosuivi.domain.stats.SessionValue) = v.session.startedAt.toEpochMilli()
        fun when_(v: com.cardiosuivi.domain.stats.SessionValue) = "${dt.format(v.session.startedAt.atZone(vm.zone))} (${v.session.period.label.lowercase()})"

        SectionCard(kind.label) {
            when (kind) {
                ChartKind.BP -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !individual, onClick = { individual = false }, label = { Text("Valeurs de séance") })
                        FilterChip(selected = individual, onClick = { individual = true }, label = { Text("Mesures individuelles") })
                    }
                    val refs = buildList {
                        add(RefLine(MedicalThresholds.HOME_HYPERTENSION.systolic!!.toDouble(), "Repère domicile 135", CardioColors.Threshold))
                        add(RefLine(MedicalThresholds.HOME_HYPERTENSION.diastolic!!.toDouble(), "Repère domicile 85", CardioColors.Threshold))
                        profile.target?.let { add(RefLine(it.systolic.toDouble(), "Objectif ${it.systolic}", CardioColors.Target)); add(RefLine(it.diastolic.toDouble(), "Objectif ${it.diastolic}", CardioColors.Target)) }
                    }
                    if (individual) {
                        val ms = values.flatMap { v -> v.session.measurements.map { v to it } }
                        TimeSeriesChart(
                            listOf(
                                ChartSeries("Systolique", CardioColors.Systolic, ms.map { (v, m) -> ChartPoint(m.measuredAt.toEpochMilli(), m.systolic.toDouble(), "${m.systolic} mmHg — ${dt.format(m.measuredAt.atZone(vm.zone))}, mesure ${m.index}") }),
                                ChartSeries("Diastolique", CardioColors.Diastolic, ms.map { (v, m) -> ChartPoint(m.measuredAt.toEpochMilli(), m.diastolic.toDouble(), "${m.diastolic} mmHg — ${dt.format(m.measuredAt.atZone(vm.zone))}, mesure ${m.index}") }),
                            ),
                            refLines = refs,
                        )
                    } else {
                        TimeSeriesChart(
                            listOf(
                                ChartSeries("Systolique", CardioColors.Systolic, values.map { ChartPoint(ts(it), it.primary.systolic, "${Fmt.d1(it.primary.systolic)} mmHg — ${when_(it)}") }),
                                ChartSeries("Diastolique", CardioColors.Diastolic, values.map { ChartPoint(ts(it), it.primary.diastolic, "${Fmt.d1(it.primary.diastolic)} mmHg — ${when_(it)}") }),
                            ),
                            refLines = refs,
                        )
                    }
                    Text("Chaque point est une séance (ou une mesure) réellement enregistrée ; la courbe est interrompue au-delà de 8 jours sans donnée. Valeur de séance : ${settings.aggregation.label}.", style = MaterialTheme.typography.labelSmall)
                }
                ChartKind.PULSE -> TimeSeriesChart(listOf(ChartSeries("Fréquence cardiaque", CardioColors.Pulse,
                    values.filter { it.primary.pulse != null }.map { ChartPoint(ts(it), it.primary.pulse!!, "${Fmt.d0(it.primary.pulse)} /min — ${when_(it)}") })))
                ChartKind.PP -> TimeSeriesChart(listOf(ChartSeries("Pression pulsée", CardioColors.PulsePressure,
                    values.map { ChartPoint(ts(it), it.primary.pulsePressure, "${Fmt.d0(it.primary.pulsePressure)} mmHg — ${when_(it)}") })))
                ChartKind.VAR -> {
                    Text("Écart-type des mesures au sein de chaque séance (mmHg).", style = MaterialTheme.typography.bodySmall)
                    TimeSeriesChart(listOf(
                        ChartSeries("Écart-type SYS", CardioColors.Systolic, values.filter { it.stats.systolic.sd != null }.map { ChartPoint(ts(it), it.stats.systolic.sd!!, "${Fmt.d1(it.stats.systolic.sd)} mmHg — ${when_(it)}") }),
                        ChartSeries("Écart-type DIA", CardioColors.Diastolic, values.filter { it.stats.diastolic.sd != null }.map { ChartPoint(ts(it), it.stats.diastolic.sd!!, "${Fmt.d1(it.stats.diastolic.sd)} mmHg — ${when_(it)}") }),
                    ))
                    val v = report.variability
                    Text("Entre séances : écart-type ${Fmt.d1(v.systolicSdBetweenSessions)} / ${Fmt.d1(v.diastolicSdBetweenSessions)} mmHg, variabilité successive moyenne ${Fmt.d1(v.systolicArv)} / ${Fmt.d1(v.diastolicArv)} mmHg.", style = MaterialTheme.typography.bodySmall)
                }
                ChartKind.ME -> {
                    val me = report.morningVsEvening
                    GroupedBarChart(listOf(bpGroup("Matin", me.a), bpGroup("Soir", me.b)), bpLegend)
                    Text("Écart soir − matin : ${Fmt.signed1(me.systolicDiff)} / ${Fmt.signed1(me.diastolicDiff)} mmHg." + if (!me.sufficient) " Effectifs insuffisants pour conclure." else "", style = MaterialTheme.typography.bodySmall)
                }
                ChartKind.WEEK -> GroupedBarChart(report.weekly.map { bpGroup(Fmt.date(it.start).take(5), it.stats) }, bpLegend)
                ChartKind.MONTH -> GroupedBarChart(report.monthly.map { bpGroup(it.label, it.stats) }, bpLegend)
                ChartKind.WEEKDAY -> GroupedBarChart(report.byWeekday.map { (d, g) -> bpGroup(d.getDisplayName(java.time.format.TextStyle.SHORT, Fmt.LOCALE), g) }, bpLegend)
            }
        }
    }
}

private val bpLegend = listOf("Systolique" to CardioColors.Systolic, "Diastolique" to CardioColors.Diastolic, "Pouls" to CardioColors.Pulse)

private fun bpGroup(label: String, g: GroupStats) = BarGroup(
    label,
    listOf(
        Bar(g.systolic.mean, CardioColors.Systolic, "SYS ${Fmt.d1(g.systolic.mean)} mmHg (${g.sessionCount} séances)"),
        Bar(g.diastolic.mean, CardioColors.Diastolic, "DIA ${Fmt.d1(g.diastolic.mean)} mmHg"),
        Bar(g.pulse.mean, CardioColors.Pulse, "Pouls ${Fmt.d1(g.pulse.mean)} /min"),
    ),
    g.sessionCount,
)
