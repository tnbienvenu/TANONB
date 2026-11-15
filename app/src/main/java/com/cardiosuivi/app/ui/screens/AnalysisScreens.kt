package com.cardiosuivi.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.Alignment
import com.cardiosuivi.app.ui.charts.ChartPoint
import com.cardiosuivi.app.ui.charts.ChartSeries
import com.cardiosuivi.app.ui.charts.TimeSeriesChart
import com.cardiosuivi.app.ui.components.Appear
import com.cardiosuivi.app.ui.components.CardHeader
import com.cardiosuivi.app.ui.components.HealthCard
import com.cardiosuivi.app.ui.components.PrimaryButton
import com.cardiosuivi.app.ui.components.SecondaryButton
import com.cardiosuivi.app.ui.components.SectionHeader
import com.cardiosuivi.app.ui.components.StatusBadge
import com.cardiosuivi.app.ui.theme.Health
import com.cardiosuivi.app.ui.theme.NumberStyles
import com.cardiosuivi.domain.alerts.AlertLevel
import com.cardiosuivi.domain.analysis.Finding
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

@Composable
private fun severityColor(s: FindingSeverity) = when (s) {
    FindingSeverity.FAVORABLE -> Health.colors.favorable
    FindingSeverity.NEUTRAL -> Health.colors.neutral
    FindingSeverity.WATCH -> Health.colors.attention
    FindingSeverity.DISCUSS -> Health.colors.attention
    FindingSeverity.IMPORTANT -> Health.colors.alert
}

private fun severityLabel(s: FindingSeverity) = when (s) {
    FindingSeverity.FAVORABLE -> "Favorable"
    FindingSeverity.NEUTRAL -> "Information"
    FindingSeverity.WATCH -> "À surveiller"
    FindingSeverity.DISCUSS -> "À discuter"
    FindingSeverity.IMPORTANT -> "Important"
}

private fun severityIcon(s: FindingSeverity) = when (s) {
    FindingSeverity.FAVORABLE -> Icons.Filled.CheckCircle
    FindingSeverity.NEUTRAL -> Icons.Filled.Info
    else -> Icons.Filled.Warning
}

/** Constat du moteur local : badge (icône + texte + couleur), titre, texte, références. */
@Composable
private fun FindingItem(f: Finding) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusBadge(severityLabel(f.severity), severityColor(f.severity), severityIcon(f.severity))
        Text(f.title, style = MaterialTheme.typography.titleSmall)
        Text(f.text, style = MaterialTheme.typography.bodyMedium)
        if (f.references.isNotEmpty()) Text("Réf. : " + f.references.joinToString { it.shortName }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Carte de rapport numérotée. */
@Composable
private fun ReportCard(number: Int, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, index: Int, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Appear(index) {
        HealthCard {
            CardHeader("$number. $title", icon, color)
            content()
        }
    }
}

@Composable
private fun LocalBadge() = StatusBadge("Calcul local · sans IA", Health.colors.neutral, Icons.Filled.Calculate)

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
    val c = Health.colors

    ScreenScaffold("Analyse IA", onBack = null, subtitle = "Rapport cardiovasculaire") {
        Text("Les cartes 1 à 8 sont calculées sur ce téléphone par un moteur déterministe (calculs vérifiables, sans IA et sans connexion). L'interprétation par une IA est séparée et facultative.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val a = analysis
        if (a == null) { Text("Calcul en cours…"); return@ScreenScaffold }
        val r30 = a.last30
        fun byTitle(prefix: String) = a.findings.filter { it.title.startsWith(prefix) }

        a.alerts.filter { it.level >= AlertLevel.CONSULT_SOON }.sortedByDescending { it.level.ordinal }.forEach { AlertCard(it) { Sharing.dial(context, settings.emergencyNumber) } }

        ReportCard(1, "Synthèse générale", Icons.Filled.Summarize, MaterialTheme.colorScheme.primary, 0) {
            LocalBadge()
            val items = byTitle("Données disponibles") + byTitle("Moyenne sur") + byTitle("Objectif personnel")
            if (items.isEmpty()) Text("Pas encore de séance validée : la synthèse apparaîtra après vos premières mesures.")
            items.forEachIndexed { i, f -> if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); FindingItem(f) }
        }
        ReportCard(2, "Évolution de la tension", Icons.AutoMirrored.Filled.TrendingUp, c.diastolic, 1) {
            LocalBadge()
            val dt = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")
            TimeSeriesChart(
                listOf(
                    ChartSeries("Systolique", c.systolic, r30.sessionValues.map { ChartPoint(it.session.startedAt.toEpochMilli(), it.primary.systolic, "${Fmt.d0(it.primary.systolic)} mmHg — ${dt.format(it.session.startedAt.atZone(vm.zone))}") }),
                    ChartSeries("Diastolique", c.diastolic, r30.sessionValues.map { ChartPoint(it.session.startedAt.toEpochMilli(), it.primary.diastolic, "${Fmt.d0(it.primary.diastolic)} mmHg — ${dt.format(it.session.startedAt.atZone(vm.zone))}") }),
                ), height = 160,
            )
            val tr = byTitle("Tendance")
            if (tr.isEmpty()) Text(r30.trendUnavailableReason ?: "Tendance non disponible : données insuffisantes.", style = MaterialTheme.typography.bodyMedium)
            tr.forEach { FindingItem(it) }
        }
        ReportCard(3, "Analyse systolique", Icons.Filled.Favorite, c.systolic, 2) {
            LocalBadge()
            ComponentStats("Systolique", r30.overall.systolic.mean, r30.overall.systolic.min, r30.overall.systolic.max, r30.trend?.systolicSlopePerWeek, r30.variability.systolicSdBetweenSessions, MedicalThresholds.HOME_HYPERTENSION.systolic, profile.target?.systolic, r30.sessionCount)
        }
        ReportCard(4, "Analyse diastolique", Icons.Filled.FavoriteBorder, c.diastolic, 3) {
            LocalBadge()
            ComponentStats("Diastolique", r30.overall.diastolic.mean, r30.overall.diastolic.min, r30.overall.diastolic.max, r30.trend?.diastolicSlopePerWeek, r30.variability.diastolicSdBetweenSessions, MedicalThresholds.HOME_HYPERTENSION.diastolic, profile.target?.diastolic, r30.sessionCount)
        }
        ReportCard(5, "Comparaison matin / soir", Icons.Filled.WbSunny, c.morning, 4) {
            LocalBadge()
            val me = r30.morningVsEvening
            KeyValue("Matin (${me.a.sessionCount} séance(s))", "${Fmt.bp(me.a.systolic.mean, me.a.diastolic.mean)} mmHg")
            KeyValue("Soir (${me.b.sessionCount} séance(s))", "${Fmt.bp(me.b.systolic.mean, me.b.diastolic.mean)} mmHg")
            byTitle("Comparaison matin").forEach { FindingItem(it) }
        }
        ReportCard(6, "Points de vigilance", Icons.Filled.Warning, c.attention, 5) {
            LocalBadge()
            val watch = a.findings.filter { it.severity >= FindingSeverity.WATCH } +
                (byTitle("Variabilité") + byTitle("Pression pulsée") + byTitle("Valeurs inhabituelles")).filter { it.severity < FindingSeverity.WATCH }
            val other = a.alerts.filter { it.level < AlertLevel.CONSULT_SOON }
            if (watch.isEmpty() && other.isEmpty()) Text("Aucun point de vigilance détecté automatiquement.", style = MaterialTheme.typography.bodyMedium)
            other.sortedByDescending { it.level.ordinal }.forEach { AlertCard(it) }
            watch.distinct().forEachIndexed { i, f -> if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); FindingItem(f) }
        }
        ReportCard(7, "Recommandations générales", Icons.Filled.Lightbulb, c.favorable, 6) {
            Text("Conseils généraux de mesure (non personnalisés, sans modification de traitement) :", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(
                "Mesurez dans les mêmes conditions, matin et soir, selon votre calendrier.",
                "Reposez-vous cinq minutes, assis, dos soutenu, bras posé à hauteur du cœur.",
                "Évitez caféine, tabac et exercice dans les 30 minutes précédant la mesure.",
                "Effectuez les trois mesures de la séance, espacées d'environ une minute.",
                "Apportez le rapport PDF de CardioSuivi à votre prochaine consultation.",
                "Ne modifiez jamais votre traitement sans avis médical.",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            a.last7.completeness?.let { cp -> if (cp.missedSlots > 0) Text("Cette semaine : ${cp.missedSlots} séance(s) programmée(s) non réalisée(s).", style = MaterialTheme.typography.bodySmall, color = c.attention) }
            Text("Réf. : recommandations de mesure à domicile (ESH 2023, ESC 2024).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ReportCard(8, "Questions pour le cardiologue", Icons.Filled.QuestionAnswer, MaterialTheme.colorScheme.tertiary, 6) {
            LocalBadge()
            if (a.pointsToDiscuss.isEmpty()) Text("Aucun point saillant détecté automatiquement.", style = MaterialTheme.typography.bodyMedium)
            a.pointsToDiscuss.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }

        // Interprétation par une IA : distincte des calculs locaux, facultative, avec consentement.
        Appear(6) {
            val aiReady = settings.aiProvider != com.cardiosuivi.app.data.AiProvider.NONE && vm.c.ai.hasKey(settings.aiProvider)
            HealthCard(border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))) {
                CardHeader("Interprétation par l'IA", Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.tertiary)
                StatusBadge("Générée par une IA · facultative", MaterialTheme.colorScheme.tertiary, Icons.Filled.AutoAwesome)
                Text(
                    if (aiReady) "Fournisseur configuré : ${settings.aiProvider.label}. Les réponses de l'IA s'affichent dans l'assistant, séparées des calculs ci-dessus, après votre consentement explicite."
                    else "Aucune IA n'est configurée : seules les analyses locales ci-dessus sont disponibles. L'assistant répond aussi aux questions fréquentes sans connexion.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Aucune interprétation n'est affichée tant que vous n'avez pas posé de question.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrimaryButton("Ouvrir l'assistant IA", { nav.navigate(Routes.ASSISTANT) }, icon = Icons.Filled.AutoAwesome, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        SectionHeader("Statistiques détaillées")
        PeriodSelector(choice, { choice = it }, today, earliest)
        PeriodDetails(period)

        SectionCard("Limites de l'analyse") { a.limitations.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } }
        SecondaryButton("Automesure standardisée", { nav.navigate(Routes.HBPM) }, icon = Icons.Filled.Science)
        SecondaryButton("Références médicales", { nav.navigate(Routes.REFERENCES) }, icon = Icons.AutoMirrored.Filled.MenuBook)
        Disclaimer()
    }
}

@Composable
private fun ComponentStats(name: String, mean: Double?, min: Int?, max: Int?, slope: Double?, sd: Double?, homeRef: Int?, target: Int?, n: Int) {
    if (n == 0 || mean == null) { Text("Aucune séance sur 30 jours.", style = MaterialTheme.typography.bodyMedium); return }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(Fmt.d0(mean), style = NumberStyles.large)
        Text(" mmHg · moyenne 30 jours ($n séances)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
    }
    KeyValue("Minimum – maximum (séances)", "${min ?: "—"} – ${max ?: "—"} mmHg")
    KeyValue("Évolution", slope?.let { "${Fmt.signed1(it)} mmHg/semaine" } ?: "données insuffisantes")
    KeyValue("Écart-type entre séances", "${Fmt.d1(sd)} mmHg")
    if (homeRef != null) KeyValue("Repère à domicile (ESH/ESC)", "$homeRef mmHg")
    if (target != null) KeyValue("Objectif personnel", "< $target mmHg")
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
