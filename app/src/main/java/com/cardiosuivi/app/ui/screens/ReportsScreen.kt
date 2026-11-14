package com.cardiosuivi.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cardiosuivi.app.data.AiProvider
import com.cardiosuivi.app.report.PdfReportGenerator
import com.cardiosuivi.app.report.ReportData
import com.cardiosuivi.app.report.Sharing
import com.cardiosuivi.app.ui.AppViewModel
import com.cardiosuivi.app.ui.components.KeyValue
import com.cardiosuivi.app.ui.components.PeriodChoice
import com.cardiosuivi.app.ui.components.PeriodPreset
import com.cardiosuivi.app.ui.components.PeriodSelector
import com.cardiosuivi.app.ui.components.ScreenScaffold
import com.cardiosuivi.app.ui.components.SectionCard
import com.cardiosuivi.app.ui.components.SwitchRow
import com.cardiosuivi.domain.ai.AiPromptBuilder
import com.cardiosuivi.domain.analysis.CardioAnalysisEngine
import com.cardiosuivi.domain.analysis.PresetQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ReportsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val sessions by vm.sessions.collectAsState()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    val treatments by vm.treatments.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf(PeriodChoice(PeriodPreset.D30)) }
    var includeAi by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pdf by remember { mutableStateOf<File?>(null) }
    var csv by remember { mutableStateOf<File?>(null) }
    val today = vm.now().toLocalDate()
    val earliest = sessions.filter { it.status.isValidated }.minOfOrNull { it.localDate }
    val (from, to) = choice.range(today, earliest)
    val report = remember(sessions, settings, profile, from, to) { vm.periodReport(from, to) }
    val aiAvailable = settings.aiProvider != AiProvider.NONE && vm.c.ai.hasKey(settings.aiProvider) && settings.consentFor(settings.aiProvider) != 0L

    ScreenScaffold("Rapports", onBack = onBack) {
        PeriodSelector(choice, { choice = it; pdf = null; csv = null }, today, earliest)
        SectionCard("Contenu") {
            KeyValue("Séances validées", "${report.sessionCount}")
            KeyValue("Mesures", "${report.measurementCount}")
            KeyValue("Méthode de calcul", settings.aggregation.label)
            SwitchRow(
                "Inclure une analyse IA", if (aiAvailable) "Envoie le résumé statistique à ${settings.aiProvider.label} (consentement donné)." else "Nécessite un fournisseur configuré et votre consentement.",
                checked = includeAi && aiAvailable, enabled = aiAvailable,
            ) { includeAi = it }
        }
        Button(
            enabled = !busy && report.sessionCount > 0,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true; message = null
                scope.launch {
                    try {
                        val analysis = withContext(Dispatchers.Default) {
                            CardioAnalysisEngine(vm.alertEngine()).analyze(sessions, profile, settings.aggregation, settings.reminders, vm.now(), from to to)
                        }
                        var aiText: String? = null
                        var aiSource: String? = null
                        if (includeAi && aiAvailable) {
                            runCatching {
                                val ctx = AiPromptBuilder.buildContext(analysis, profile, treatments, settings.sharing, today)
                                vm.c.ai.ask(PresetQuestion.DISCUSS.text + " Rédige une synthèse prudente de la période pour un rapport destiné au cardiologue.", ctx)
                            }.onSuccess { aiText = it.text; aiSource = it.source }
                                .onFailure { message = "Analyse IA non incluse : ${it.message}" }
                        }
                        val file = withContext(Dispatchers.IO) {
                            PdfReportGenerator(context).generate(
                                ReportData(profile, treatments, analysis.custom ?: report, analysis, aiText, aiSource, settings.intervalSeconds, settings.measurementsPerSession, vm.now(), vm.zone),
                            )
                        }
                        pdf = file
                        if (message == null) message = "Rapport PDF généré."
                    } catch (e: Exception) {
                        message = "Échec de la génération : ${e.message}"
                    } finally { busy = false }
                }
            },
        ) { Icon(Icons.Filled.PictureAsPdf, null); Text("  Générer le rapport PDF") }
        OutlinedButton(
            enabled = !busy && report.sessionCount > 0,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    csv = withContext(Dispatchers.IO) { Sharing.writeCsv(context, sessions, settings.aggregation, from, to, vm.zone) }
                    message = "Export CSV généré."
                }
            },
        ) { Icon(Icons.Filled.TableChart, null); Text("  Exporter en CSV") }
        if (busy) CircularProgressIndicator()
        message?.let { Text(it) }
        pdf?.let { f ->
            SectionCard("Rapport PDF") {
                Text(f.name, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Sharing.share(context, f, "application/pdf", "Rapport CardioSuivi") }) { Icon(Icons.Filled.Share, null); Text(" Partager") }
                    OutlinedButton(onClick = { if (!Sharing.open(context, f, "application/pdf")) message = "Aucune application ne peut ouvrir les PDF." }) { Text("Ouvrir") }
                }
            }
        }
        csv?.let { f ->
            SectionCard("Fichier CSV") {
                Text(f.name, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { Sharing.share(context, f, "text/csv", "Mesures CardioSuivi") }) { Icon(Icons.Filled.Share, null); Text(" Partager") }
            }
        }
        Text("Le partage passe par le menu Android (WhatsApp, e-mail, Drive…). Les fichiers temporaires sont supprimés automatiquement. Attention : un fichier partagé quitte la protection de l'application.", style = MaterialTheme.typography.labelSmall)
    }
}
