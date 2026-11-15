package com.cardiosuivi.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.cardiosuivi.app.ai.AiException
import com.cardiosuivi.app.data.AiProvider
import com.cardiosuivi.app.ui.AppViewModel
import com.cardiosuivi.app.ui.Routes
import com.cardiosuivi.app.ui.components.Disclaimer
import com.cardiosuivi.app.ui.components.KeyValue
import com.cardiosuivi.app.ui.components.ScreenScaffold
import com.cardiosuivi.app.ui.components.SectionCard
import com.cardiosuivi.app.ui.components.SwitchRow
import com.cardiosuivi.domain.ai.AiPromptBuilder
import com.cardiosuivi.domain.analysis.LocalAssistant
import com.cardiosuivi.domain.analysis.PresetQuestion
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun AssistantScreen(vm: AppViewModel, nav: NavHostController) {
    val analysis by vm.analysis.collectAsState()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    val treatments by vm.treatments.collectAsState()
    val scope = rememberCoroutineScope()
    var question by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf<String?>(null) }
    var source by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var askConsent by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    val provider = settings.aiProvider
    val aiReady = provider != AiProvider.NONE && vm.c.ai.hasKey(provider)

    fun buildContext() = analysis?.let { AiPromptBuilder.buildContext(it, profile, treatments, settings.sharing, vm.now().toLocalDate()) }

    fun askAi(q: String) {
        val ctx = buildContext() ?: return
        if (settings.consentFor(provider) == 0L) { askConsent = true; return }
        loading = true; error = null
        scope.launch {
            try {
                val a = vm.c.ai.ask(q, ctx)
                answer = a.text; source = a.source
            } catch (e: AiException) {
                error = e.message
            } catch (e: Exception) {
                error = "Erreur inattendue : ${e.message}"
            } finally { loading = false }
        }
    }

    ScreenScaffold("Assistant IA", onBack = { nav.popBackStack() }) {
        SectionCard {
            Text(
                if (aiReady) "Fournisseur : ${provider.label}. Les questions libres sont envoyées avec un résumé statistique pseudonymisé, après votre consentement."
                else "Aucun fournisseur d'IA configuré : les questions prédéfinies sont traitées par le moteur local (sans connexion).",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { nav.navigate(Routes.AI_SETTINGS) }) { Text("Configurer l'IA") }
        }
        Text("Questions fréquentes (réponse locale immédiate)", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PresetQuestion.entries.forEach { q ->
                AssistChip(onClick = {
                    analysis?.let { a ->
                        val r = LocalAssistant.answer(q, a, settings.emergencyNumber)
                        question = q.text; answer = r.asText(); source = r.source; error = null
                    }
                }, label = { Text(q.text) })
            }
        }
        OutlinedTextField(question, { question = it }, label = { Text("Votre question") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { askAi(question) }, enabled = aiReady && question.isNotBlank() && !loading) { Text("Demander à l'IA") }
            OutlinedButton(onClick = { preview = buildContext()?.let { AiPromptBuilder.contextJson(it) } }) { Text("Données transmises") }
            if (loading) CircularProgressIndicator()
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        answer?.let { a ->
            SectionCard("Réponse") {
                source?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                SelectionContainer { Text(a) }
                Text("Réponse automatisée : à discuter avec votre médecin. Elle ne constitue ni un diagnostic ni une prescription.", style = MaterialTheme.typography.labelSmall)
            }
        }
        Disclaimer()
    }

    if (askConsent) ConsentDialog(provider, onAccept = {
        askConsent = false
        scope.launch { vm.c.settings.setConsent(provider, true) }
    }, onDismiss = { askConsent = false })

    preview?.let { p ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Données envoyées au fournisseur") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("Ni nom, ni date de naissance, ni clé de l'appareil. Les traitements et antécédents ne sont inclus que si vous l'avez autorisé.", style = MaterialTheme.typography.bodySmall)
                    SelectionContainer { Text(p, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall) }
                }
            },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("Fermer") } },
        )
    }
}

@Composable
fun ConsentDialog(provider: AiProvider, onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Consentement à la transmission") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pour répondre, CardioSuivi doit envoyer à ${provider.label} (service externe, hors de l'Union européenne possible) :")
                Text("• vos statistiques tensionnelles agrégées et vos dernières séances ;")
                Text("• selon vos réglages : âge, sexe, traitements, antécédents ;")
                Text("• votre question.")
                Text("Votre nom et votre date de naissance ne sont jamais envoyés. Le fournisseur traite ces données selon sa propre politique de confidentialité. Vous pouvez retirer ce consentement à tout moment dans « Configurer l'IA ».")
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("J'accepte") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Refuser") } },
    )
}

@Composable
fun AiSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var anthropicModel by rememberSaveable(settings.anthropicModel) { mutableStateOf(settings.anthropicModel) }
    var openAiModel by rememberSaveable(settings.openAiModel) { mutableStateOf(settings.openAiModel) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var keyVersion by remember { mutableStateOf(0) }

    ScreenScaffold("Configuration de l'IA", onBack = onBack) {
        SectionCard("Fournisseur") {
            AiProvider.entries.forEach { p ->
                // Toute la ligne est sélectionnable (cible tactile plus grande, accessibilité).
                Row(
                    Modifier.fillMaxWidth().selectable(selected = settings.aiProvider == p, role = Role.RadioButton) { scope.launch { vm.c.settings.setAiProvider(p) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.aiProvider == p, onClick = null)
                    Text(p.label)
                }
            }
            Text("L'IA n'est qu'un complément : l'analyse locale et les alertes fonctionnent sans elle et sans Internet.", style = MaterialTheme.typography.bodySmall)
        }
        val p = settings.aiProvider
        if (p != AiProvider.NONE) {
            SectionCard("Clé d'API ${p.label}") {
                val has = remember(p, keyVersion) { vm.c.ai.hasKey(p) }
                Text(if (has) "Une clé est enregistrée (chiffrée par le Keystore Android)." else "Aucune clé enregistrée.")
                OutlinedTextField(key, { key = it }, label = { Text("Nouvelle clé") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = key.isNotBlank(), onClick = {
                        status = if (vm.c.ai.saveKey(p, key)) { key = ""; keyVersion++; "Clé enregistrée." } else "Clé invalide : caractères non autorisés (espace, accent…)."
                    }) { Text("Enregistrer") }
                    if (has) OutlinedButton(onClick = { vm.c.ai.deleteKey(p); keyVersion++; status = "Clé supprimée." }) { Text("Supprimer") }
                }
                Text("La clé n'est jamais affichée, exportée ni incluse dans les sauvegardes. Préférez une clé dédiée à cette application, avec un plafond de dépenses.", style = MaterialTheme.typography.labelSmall)
            }
            SectionCard("Modèle") {
                if (p == AiProvider.ANTHROPIC) OutlinedTextField(anthropicModel, { anthropicModel = it }, label = { Text("Modèle Anthropic") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                else OutlinedTextField(openAiModel, { openAiModel = it }, label = { Text("Modèle OpenAI") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { scope.launch { vm.c.settings.setModels(anthropicModel, openAiModel); status = "Modèle enregistré." } }) { Text("Enregistrer") }
                    OutlinedButton(enabled = !testing, onClick = {
                        testing = true; status = null
                        scope.launch {
                            status = try {
                                vm.c.settings.setModels(anthropicModel, openAiModel)
                                val r = vm.c.ai.testConnection(p, if (p == AiProvider.ANTHROPIC) anthropicModel else openAiModel)
                                "Connexion réussie (réponse : ${r.take(40)})."
                            } catch (e: Exception) { "Échec : ${e.message}" } finally { testing = false }
                        }
                    }) { Text("Tester (sans données de santé)") }
                    if (testing) CircularProgressIndicator()
                }
            }
            SectionCard("Consentement et données partagées") {
                val at = settings.consentFor(p)
                KeyValue("Consentement", if (at == 0L) "Non donné" else "Donné le " + DateFormat.getDateTimeInstance().format(Date(at)))
                if (at != 0L) OutlinedButton(onClick = { scope.launch { vm.c.settings.setConsent(p, false) } }) { Text("Retirer le consentement") }
                val sh = settings.sharing
                SwitchRow("Inclure l'âge et le sexe", checked = sh.includeAgeAndSex) { scope.launch { vm.c.settings.setSharing(sh.copy(includeAgeAndSex = it)) } }
                SwitchRow("Inclure les dernières séances détaillées", checked = sh.includeRecentSessions) { scope.launch { vm.c.settings.setSharing(sh.copy(includeRecentSessions = it)) } }
                SwitchRow("Inclure les traitements", checked = sh.includeTreatments) { scope.launch { vm.c.settings.setSharing(sh.copy(includeTreatments = it)) } }
                SwitchRow("Inclure les antécédents", checked = sh.includeMedicalHistory) { scope.launch { vm.c.settings.setSharing(sh.copy(includeMedicalHistory = it)) } }
            }
        }
        status?.let { Text(it) }
    }
}
