package com.cardiosuivi.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.cardiosuivi.app.report.Sharing
import com.cardiosuivi.app.ui.AppViewModel
import com.cardiosuivi.app.ui.Routes
import com.cardiosuivi.app.ui.components.AlertCard
import com.cardiosuivi.app.ui.components.ConfirmDialog
import com.cardiosuivi.app.ui.components.Disclaimer
import com.cardiosuivi.app.ui.components.KeyValue
import com.cardiosuivi.app.ui.components.PeriodChoice
import com.cardiosuivi.app.ui.components.PeriodPreset
import com.cardiosuivi.app.ui.components.PeriodSelector
import com.cardiosuivi.app.ui.components.ScreenScaffold
import com.cardiosuivi.app.ui.components.SectionCard
import com.cardiosuivi.app.ui.components.StatusDot
import com.cardiosuivi.domain.analysis.FindingSeverity
import com.cardiosuivi.domain.analysis.HbpmProtocol
import com.cardiosuivi.domain.model.ProtocolType
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.reference.MedicalReference
import com.cardiosuivi.domain.reference.MedicalThresholds
import com.cardiosuivi.domain.reference.PlausibilityBounds
import com.cardiosuivi.domain.stats.PeriodReport
import com.cardiosuivi.domain.util.Fmt
import androidx.compose.ui.graphics.Color

private fun severityColor(s: FindingSeverity) = when (s) {
    FindingSeverity.FAVORABLE -> Color(0xFF2E7D32)
    FindingSeverity.NEUTRAL -> Color(0xFF78909C)
    FindingSeverity.WATCH -> Color(0xFFF9A825)
    FindingSeverity.DISCUSS -> Color(0xFFEF6C00)
    FindingSeverity.IMPORTANT -> Color(0xFFC62828)
}

@Composable
fun AnalysisScreen(vm: AppViewModel, nav: NavHostController) {
    val analysis by vm.analysis.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    val context = LocalContext.current
    var choice by remember { mutableStateOf(PeriodChoice(PeriodPreset.D30)) }
    val today = vm.now().toLocalDate()
    val earliest = sessions.filter { it.status.isValidated }.minOfOrNull { it.localDate }
    val (from, to) = choice.range(today, earliest)
    val period = remember(sessions, settings, profile, from, to) { vm.periodReport(from, to) }

    ScreenScaffold("Analyse cardiovasculaire", onBack = null) {
        Text("Moteur d'analyse local et déterministe : calculs vérifiables, sans IA et sans connexion.", style = MaterialTheme.typography.bodySmall)
        val a = analysis
        if (a == null) { Text("Calcul en cours…"); return@ScreenScaffold }

        a.alerts.sortedByDescending { it.level.ordinal }.forEach { AlertCard(it) { Sharing.dial(context, settings.emergencyNumber) } }

        SectionCard("Constats") {
            a.findings.forEach { f ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusDot(severityColor(f.severity))
                    androidx.compose.foundation.layout.Column {
                        Text(f.title, fontWeight = FontWeight.SemiBold)
                        Text(f.text, style = MaterialTheme.typography.bodySmall)
                        if (f.references.isNotEmpty()) Text("Réf. : " + f.references.joinToString { it.shortName }, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        SectionCard("Points à discuter avec votre cardiologue") {
            if (a.pointsToDiscuss.isEmpty()) Text("Aucun point saillant détecté automatiquement.")
            a.pointsToDiscuss.forEach { Text("• $it") }
        }

        Text("Statistiques détaillées", style = MaterialTheme.typography.titleMedium)
        PeriodSelector(choice, { choice = it }, today, earliest)
        PeriodDetails(period)

        SectionCard("Limites de l'analyse") { a.limitations.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { nav.navigate(Routes.HBPM) }) { Text("Automesure standardisée") }
            TextButton(onClick = { nav.navigate(Routes.REFERENCES) }) { Text("Références") }
        }
        Disclaimer()
    }
}

@Composable
fun PeriodDetails(r: PeriodReport) {
    SectionCard("Période du ${Fmt.date(r.from)} au ${Fmt.date(r.to)}") {
        KeyValue("Séances / mesures / jours", "${r.sessionCount} / ${r.measurementCount} / ${r.daysWithData} sur ${r.calendarDays}")
        r.completeness?.let { KeyValue("Séances programmées (faites / incomplètes / manquées / à venir)", "${it.doneSlots} / ${it.incompleteSlots} / ${it.missedSlots} / ${it.pendingSlots}") }
        HorizontalDivider()
        KeyValue("Moyenne des séances", "${Fmt.bp(r.overall.systolic.mean, r.overall.diastolic.mean)} mmHg · ${Fmt.d0(r.overall.pulse.mean)} /min")
        KeyValue("Moyenne des moyennes journalières", "${Fmt.bp(r.meanOfDailyMeansSystolic, r.meanOfDailyMeansDiastolic)} mmHg")
        KeyValue("Moyenne des mesures individuelles", "${Fmt.bp(r.individualSystolic.mean, r.individualDiastolic.mean)} mmHg")
        KeyValue("SYS min – max (séances)", "${r.overall.systolic.min ?: "—"} – ${r.overall.systolic.max ?: "—"}")
        KeyValue("DIA min – max (séances)", "${r.overall.diastolic.min ?: "—"} – ${r.overall.diastolic.max ?: "—"}")
        KeyValue("Pression pulsée moyenne", "${Fmt.d0(r.overall.pulsePressure.mean)} mmHg")
        r.targetExceedance?.let { KeyValue("Séances au-dessus de l'objectif", "${it.sessionsAbove} / ${it.sessionCount} (${Fmt.pct(it.rate)})") }
        HorizontalDivider()
        val t = r.trend
        KeyValue("Tendance SYS / DIA", if (t == null) "—" else "${Fmt.signed1(t.systolicSlopePerWeek)} / ${Fmt.signed1(t.diastolicSlopePerWeek)} mmHg/sem.")
        if (t == null) Text(r.trendUnavailableReason ?: "", style = MaterialTheme.typography.labelSmall)
        val v = r.variability
        KeyValue("Écart-type entre séances", "${Fmt.d1(v.systolicSdBetweenSessions)} / ${Fmt.d1(v.diastolicSdBetweenSessions)}")
        KeyValue("Variabilité successive (ARV)", "${Fmt.d1(v.systolicArv)} / ${Fmt.d1(v.diastolicArv)}")
        KeyValue("Écart-type intra-séance moyen", "${Fmt.d1(v.meanWithinSessionSystolicSd)} / ${Fmt.d1(v.meanWithinSessionDiastolicSd)}")
        HorizontalDivider()
        val me = r.morningVsEvening
        KeyValue("Matin (${me.a.sessionCount})", "${Fmt.bp(me.a.systolic.mean, me.a.diastolic.mean)} · ${Fmt.d0(me.a.pulse.mean)}")
        KeyValue("Soir (${me.b.sessionCount})", "${Fmt.bp(me.b.systolic.mean, me.b.diastolic.mean)} · ${Fmt.d0(me.b.pulse.mean)}")
        KeyValue("Écart soir − matin", "${Fmt.signed1(me.systolicDiff)} / ${Fmt.signed1(me.diastolicDiff)}" + if (!me.sufficient) " (effectifs insuffisants)" else "")
        r.byWeekday.forEach { (d, g) -> KeyValue("${d.getDisplayName(java.time.format.TextStyle.FULL, Fmt.LOCALE)} (${g.sessionCount})", Fmt.bp(g.systolic.mean, g.diastolic.mean)) }
        if (r.weekly.size > 1) {
            HorizontalDivider()
            Text("Semaines successives", fontWeight = FontWeight.SemiBold)
            r.weekly.zipWithNext().forEach { (a, b) ->
                KeyValue("${Fmt.date(b.start)} vs ${Fmt.date(a.start)}", "${Fmt.signed1(diff(a.stats.systolic.mean, b.stats.systolic.mean))} / ${Fmt.signed1(diff(a.stats.diastolic.mean, b.stats.diastolic.mean))}")
            }
        }
        if (r.monthly.size > 1) {
            Text("Mois successifs", fontWeight = FontWeight.SemiBold)
            r.monthly.zipWithNext().forEach { (a, b) ->
                KeyValue("${b.label} vs ${a.label}", "${Fmt.signed1(diff(a.stats.systolic.mean, b.stats.systolic.mean))} / ${Fmt.signed1(diff(a.stats.diastolic.mean, b.stats.diastolic.mean))}")
            }
        }
        if (r.outliers.isNotEmpty()) {
            HorizontalDivider()
            Text("Valeurs inhabituelles (critère statistique)", fontWeight = FontWeight.SemiBold)
            r.outliers.forEach { Text("• ${Fmt.date(it.session.localDate)} : ${it.reason}", style = MaterialTheme.typography.bodySmall) }
        }
        Text("Méthode : ${r.method.label}. Aucune valeur manquante n'est estimée.", style = MaterialTheme.typography.labelSmall)
    }
}

private fun diff(a: Double?, b: Double?) = if (a != null && b != null) b - a else null

@Composable
fun HbpmScreen(vm: AppViewModel, nav: NavHostController) {
    val sessions by vm.sessions.collectAsState()
    val settings by vm.settings.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    val result = remember(sessions, settings.hbpmStartDate) { HbpmProtocol.evaluate(sessions, settings.hbpmStartDate) }
    ScreenScaffold("Automesure standardisée", onBack = { nav.popBackStack() }) {
        SectionCard("Protocole") {
            Text("Protocole complémentaire, distinct de votre calendrier habituel, utile avant une consultation :")
            Text("• ${MedicalThresholds.HBPM_RECOMMENDED_DAYS} jours consécutifs (au minimum ${MedicalThresholds.HBPM_MIN_DAYS}) ;")
            Text("• chaque jour, une séance le matin et une le soir, de 2 mesures à 1–2 minutes d'intervalle ;")
            Text("• les mesures du premier jour sont écartées ; la moyenne des autres est comparée au repère de 135/85 mmHg.")
            Text("Réf. : " + result.references.joinToString { it.shortName }, style = MaterialTheme.typography.labelSmall)
        }
        SectionCard("Mon protocole") {
            if (settings.hbpmStartDate == null) {
                Text("Aucun protocole en cours.")
                Button(onClick = { vm.setHbpmStart(vm.now().toLocalDate()) }) { Text("Commencer aujourd'hui") }
            } else {
                KeyValue("Début", Fmt.date(settings.hbpmStartDate!!))
                KeyValue("Statut", result.status.label)
                KeyValue("Jours exploitables", "${result.usableDays}")
                KeyValue("Mesures utilisées", "${result.readingsUsed}")
                KeyValue("Moyenne", "${Fmt.bp(result.meanSystolic, result.meanDiastolic)} mmHg · ${Fmt.d0(result.meanPulse)} /min")
                result.classification?.let { Text(it.label, fontWeight = FontWeight.SemiBold) }
                result.days.forEach { d -> KeyValue(Fmt.date(d.date) + if (d.excluded) " (écarté)" else "", "matin ${d.morningSessions} · soir ${d.eveningSessions} · ${d.readings} mesures") }
                result.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { nav.navigate(Routes.session(SessionPeriod.MORNING, ProtocolType.STANDARDIZED_HBPM)) }) { Text("Séance du matin") }
                    Button(onClick = { nav.navigate(Routes.session(SessionPeriod.EVENING, ProtocolType.STANDARDIZED_HBPM)) }) { Text("Séance du soir") }
                }
                TextButton(onClick = { confirmReset = true }) { Text("Terminer / réinitialiser le protocole") }
            }
        }
        Disclaimer()
    }
    if (confirmReset) ConfirmDialog("Réinitialiser le protocole ?", "Les séances déjà réalisées sont conservées dans l'historique.", "Réinitialiser",
        onConfirm = { vm.setHbpmStart(null); confirmReset = false }, onDismiss = { confirmReset = false })
}

@Composable
fun ReferencesScreen(onBack: () -> Unit) {
    ScreenScaffold("Références médicales", onBack = onBack) {
        Text("Seuils utilisés par l'application et leur contexte d'application. Votre médecin peut fixer des objectifs différents.", style = MaterialTheme.typography.bodySmall)
        MedicalThresholds.all.forEach { t ->
            SectionCard(t.label) {
                KeyValue("Contexte", t.context.label)
                KeyValue("Valeurs", "${t.systolic ?: "—"} / ${t.diastolic ?: "—"} mmHg")
                Text(t.note, style = MaterialTheme.typography.bodySmall)
                Text("Réf. : " + t.references.joinToString { it.shortName }, style = MaterialTheme.typography.labelSmall)
            }
        }
        SectionCard("Fréquence cardiaque et protocole") {
            Text("Bradycardie : < ${MedicalThresholds.BRADYCARDIA_BPM}/min ; tachycardie au repos : > ${MedicalThresholds.TACHYCARDIA_BPM}/min (ACC/AHA/HRS 2018).")
            Text("Écart > ${MedicalThresholds.INTRA_SESSION_DIFF_MMHG} mmHg entre deux mesures successives : mesure supplémentaire conseillée (ESH 2021/2023).")
            Text("Automesure : ${MedicalThresholds.HBPM_RECOMMENDED_DAYS} jours (minimum ${MedicalThresholds.HBPM_MIN_DAYS}), matin et soir, 1er jour écarté (ESH 2023, ESC 2024).")
        }
        SectionCard("Objectifs thérapeutiques (information)") {
            Text("ESC 2024 : chez la plupart des patients traités, objectif de pression systolique au cabinet de 120 à 129 mmHg si le traitement est bien toléré ; objectifs plus souples (« aussi bas que raisonnablement possible ») chez les personnes fragiles ou très âgées.")
            Text("OMS 2021 : objectif < 140/90 mmHg chez les patients sans comorbidité ; systolique < 130 mmHg en cas de maladie cardiovasculaire connue.")
            Text("Ces objectifs concernent les mesures au cabinet et sont fixés par le médecin : renseignez votre objectif personnel dans le profil.", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard("Bornes de plausibilité (techniques)") {
            Text("Utilisées uniquement pour détecter une erreur de lecture : SYS ${PlausibilityBounds.SYSTOLIC.first}–${PlausibilityBounds.SYSTOLIC.last}, DIA ${PlausibilityBounds.DIASTOLIC.first}–${PlausibilityBounds.DIASTOLIC.last}, pouls ${PlausibilityBounds.PULSE.first}–${PlausibilityBounds.PULSE.last}. Ce ne sont pas des seuils médicaux.")
        }
        MedicalReference.entries.forEach { r ->
            SectionCard(r.shortName) {
                Text(r.citation, style = MaterialTheme.typography.bodySmall)
                Text(r.doiOrUrl, style = MaterialTheme.typography.labelSmall)
            }
        }
        Disclaimer()
    }
}
