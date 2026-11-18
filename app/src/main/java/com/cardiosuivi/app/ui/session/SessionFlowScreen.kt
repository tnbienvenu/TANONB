package com.cardiosuivi.app.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardiosuivi.app.report.Sharing
import com.cardiosuivi.app.ui.AppViewModel
import com.cardiosuivi.app.ui.components.AlertCard
import com.cardiosuivi.app.ui.components.Appear
import com.cardiosuivi.app.ui.components.BpValue
import com.cardiosuivi.app.ui.components.CardHeader
import com.cardiosuivi.app.ui.components.CircularCountdown
import com.cardiosuivi.app.ui.components.ConfirmDialog
import com.cardiosuivi.app.ui.components.HealthCard
import com.cardiosuivi.app.ui.components.KeyValue
import com.cardiosuivi.app.ui.components.PrimaryButton
import com.cardiosuivi.app.ui.components.ScreenScaffold
import com.cardiosuivi.app.ui.components.SecondaryButton
import com.cardiosuivi.app.ui.components.SectionCard
import com.cardiosuivi.app.ui.components.StepProgress
import com.cardiosuivi.app.ui.theme.Health
import com.cardiosuivi.app.ui.theme.NumberStyles
import com.cardiosuivi.app.ui.theme.Radius
import com.cardiosuivi.domain.alerts.AlertLevel
import com.cardiosuivi.domain.entry.EntryField
import com.cardiosuivi.domain.entry.localDateTime
import com.cardiosuivi.domain.model.ProtocolType
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.Symptom
import com.cardiosuivi.domain.util.Fmt

@Composable
fun SessionFlowScreen(
    app: AppViewModel,
    period: SessionPeriod?,
    protocol: ProtocolType,
    resumeId: Long?,
    slotKey: String?,
    onExit: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    val vm: SessionFlowViewModel = viewModel(factory = SessionFlowViewModel.factory(app.c))
    LaunchedEffect(Unit) { vm.init(period, protocol, resumeId, slotKey) }
    val st by vm.state.collectAsState()
    val settings by app.settings.collectAsState()
    val context = LocalContext.current
    var confirmExit by remember { mutableStateOf(false) }
    val callEmergency = { Sharing.dial(context, settings.emergencyNumber) }

    BackHandler(enabled = st.step != FlowStep.SAVED) { confirmExit = true }

    val title = when (st.step) {
        FlowStep.SAVED -> "Séance enregistrée"
        FlowStep.SUMMARY -> "Résumé de la séance"
        FlowStep.PREPARATION, FlowStep.REST -> if (st.protocol == ProtocolType.STANDARDIZED_HBPM) "Automesure standardisée" else "Nouvelle séance"
        else -> if (st.editingMeasurementId != null) "Correction" else "Mesure ${minOf(st.nextIndex, st.planned)} sur ${st.planned}"
    }
    ScreenScaffold(title, onBack = { if (st.step == FlowStep.SAVED) onExit() else confirmExit = true }) {
        if (st.step in setOf(FlowStep.ENTRY, FlowStep.INTERVAL) && st.editingMeasurementId == null) {
            StepProgress(done = st.measurements.size, total = st.planned, current = minOf(st.nextIndex, st.planned))
        }
        st.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (st.step) {
            FlowStep.LOADING -> CircularProgressIndicator()
            FlowStep.PREPARATION -> Preparation(st, vm)
            FlowStep.REST -> Countdown("Repos au calme", st.restRemaining, 300, "Restez assis, détendu, sans parler. La mesure pourra commencer à la fin du décompte.") {
                SecondaryButton("Passer le repos", vm::goToEntry)
            }
            FlowStep.ENTRY -> Entry(st, vm)
            FlowStep.INTERVAL -> {
                st.lastMeasurementAlerts.forEach { AlertCard(it, callEmergency) }
                val last = st.measurements.last()
                Appear {
                    HealthCard {
                        CardHeader("Mesure ${last.index} enregistrée", Icons.Filled.CheckCircle, Health.colors.favorable)
                        BpValue("${last.systolic}", "${last.diastolic}", last.pulse?.toString() ?: "—")
                        Text("Enregistrée le ${Fmt.dateTimeLong(last.localDateTime())}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = vm::editPrevious) { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Text(" Corriger la mesure ${last.index}") }
                    }
                }
                Countdown("Avant la mesure ${st.nextIndex}", st.intervalRemaining, st.intervalSeconds, "Gardez la même position, bras gauche au repos, sans parler.") {
                    PrimaryButton(if (st.intervalRemaining > 0) "Mesurer maintenant" else "Mesure suivante", vm::goToEntry, icon = Icons.AutoMirrored.Filled.ArrowForward)
                    SecondaryButton("Arrêter ici", vm::skipToSummary)
                }
            }
            FlowStep.SUMMARY -> Summary(st, vm, callEmergency)
            FlowStep.SAVED -> {
                val urgent = st.sessionAlerts.any { it.level == AlertLevel.URGENT }
                SavedConfirmation()
                if (urgent) Text("Recommandation urgente", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                st.sessionAlerts.sortedByDescending { it.level.ordinal }.forEach { AlertCard(it, callEmergency) }
                if (st.sessionAlerts.isEmpty()) Text("Aucune alerte pour cette séance.")
                HealthCard {
                    CardHeader("Valeur de séance", Icons.Filled.Favorite, Health.colors.systolic)
                    st.stats?.primary?.let { p -> BpValue(Fmt.d0(p.systolic), Fmt.d0(p.diastolic), p.pulse?.let { Fmt.d0(it) }, style = NumberStyles.hero) }
                    Text(st.method.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PrimaryButton("Terminer", onExit)
                st.sessionId?.let { id -> SecondaryButton("Voir le détail", { onOpenSession(id) }) }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Quitter la séance ?") },
            text = {
                Text(
                    buildString {
                        append(if (st.measurements.isEmpty()) "Aucune mesure n'a été enregistrée." else "${st.measurements.size} mesure(s) enregistrée(s). Vous pouvez reprendre la séance plus tard depuis l'accueil, ou l'abandonner (suppression).")
                        if (st.hasUnsavedInput) append("\nLes valeurs saisies mais non validées seront perdues.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; vm.cancelSession(onExit) }) { Text("Abandonner", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Row {
                    if (st.measurements.isNotEmpty()) TextButton(onClick = { confirmExit = false; vm.leaveForLater(); onExit() }) { Text("Reprendre plus tard") }
                    TextButton(onClick = { confirmExit = false }) { Text("Continuer") }
                }
            },
        )
    }
}

@Composable
private fun Preparation(st: FlowState, vm: SessionFlowViewModel) {
    HealthCard {
        CardHeader("Avant de commencer", Icons.Filled.Info, MaterialTheme.colorScheme.primary)
        listOf(
            "Reposez-vous cinq minutes au calme.",
            "Asseyez-vous, dos soutenu, pieds à plat au sol, jambes non croisées.",
            "Placez le brassard sur le bras nu, à hauteur du cœur, bras posé.",
            "Utilisez un brassard de taille adaptée à votre bras.",
            "Ne parlez pas et ne bougez pas pendant la mesure.",
            "Évitez caféine, tabac et exercice physique dans les 30 minutes précédentes.",
            "Respectez les consignes du fabricant de votre tensiomètre.",
        ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
    SectionCard("Séance") {
        Text("${st.planned} mesures successives espacées d'environ ${st.intervalSeconds} secondes, au bras gauche." +
            if (st.protocol == ProtocolType.STANDARDIZED_HBPM) " (Protocole d'automesure standardisée : 2 mesures par séance.)" else "",
            style = MaterialTheme.typography.bodyMedium)
        Text("La date et l'heure de chaque mesure sont enregistrées automatiquement ; le moment (matin ou soir) est déduit de l'heure.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    PrimaryButton("Démarrer le repos de 5 minutes", vm::startRest, icon = Icons.Filled.Timer)
    SecondaryButton("Je suis déjà reposé(e) : commencer", vm::goToEntry)
}

@Composable
private fun Countdown(title: String, remaining: Int, total: Int, hint: String, actions: @Composable () -> Unit) {
    HealthCard {
        CardHeader(title, Icons.Filled.Timer, MaterialTheme.colorScheme.primary)
        CircularCountdown(remaining, total, Modifier.align(Alignment.CenterHorizontally))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
        actions()
    }
}

@Composable
private fun Entry(st: FlowState, vm: SessionFlowViewModel) {
    val focus = LocalFocusManager.current
    val editing = st.editing
    HealthCard {
        CardHeader(
            if (editing != null) "Corriger la mesure ${editing.index}" else "Valeurs de la mesure ${st.nextIndex}",
            Icons.Filled.Edit, MaterialTheme.colorScheme.primary,
        )
        Text("Recopiez les trois valeurs affichées par le tensiomètre.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EntryInput(EntryField.SYS, st.sys, st.fieldErrors[EntryField.SYS], Health.colors.systolic, vm::setSys, ImeAction.Next) { focus.moveFocus(FocusDirection.Down) }
        EntryInput(EntryField.DIA, st.dia, st.fieldErrors[EntryField.DIA], Health.colors.diastolic, vm::setDia, ImeAction.Next) { focus.moveFocus(FocusDirection.Down) }
        EntryInput(EntryField.PUL, st.pul, st.fieldErrors[EntryField.PUL], Health.colors.pulse, vm::setPul, ImeAction.Done) { focus.clearFocus(); vm.validateMeasurement() }
        Text("* champs obligatoires · nombres entiers uniquement", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(
                if (editing != null) "Mesurée le ${Fmt.dateTimeLong(editing.localDateTime())} — l'horodatage d'origine est conservé."
                else "Date et heure enregistrées automatiquement à la validation.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (editing != null) {
            PrimaryButton("Enregistrer la correction", { focus.clearFocus(); vm.validateMeasurement() }, icon = Icons.Filled.Check)
            SecondaryButton("Annuler la correction", vm::cancelEdit)
        } else {
            PrimaryButton("Valider et continuer", { focus.clearFocus(); vm.validateMeasurement() }, icon = Icons.Filled.Check)
            if (st.measurements.isNotEmpty()) SecondaryButton("Revenir à la mesure ${st.measurements.maxOf { it.index }}", vm::editPrevious, icon = Icons.AutoMirrored.Filled.ArrowBack)
        }
    }
    if (st.pendingWarnings.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = vm::dismissWarnings,
            title = { Text("Vérifiez les valeurs") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { st.pendingWarnings.forEach { Text("• $it") } } },
            confirmButton = { TextButton(onClick = { vm.validateMeasurement(confirmed = true) }) { Text("Je confirme ces valeurs") } },
            dismissButton = { TextButton(onClick = vm::dismissWarnings) { Text("Corriger") } },
        )
    }
}

/** Champ de saisie d'une valeur : libellé abrégé et complet, unité, clavier numérique, message d'erreur sous le champ. */
@Composable
private fun EntryInput(field: EntryField, value: String, error: String?, color: Color, onChange: (String) -> Unit, ime: ImeAction, onIme: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text("${field.short} — ${field.label} (${field.unit}) *") },
            singleLine = true,
            isError = error != null,
            suffix = { Text(field.unit, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ime),
            keyboardActions = KeyboardActions(onNext = { onIme() }, onDone = { onIme() }),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.field),
            textStyle = NumberStyles.medium.copy(color = color),
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "${field.label}, ${field.unit}, obligatoire"
                if (error != null) error(error)
            },
        )
    }
}

@Composable
private fun Summary(st: FlowState, vm: SessionFlowViewModel, callEmergency: () -> Unit) {
    val stats = st.stats
    var confirmDelete by remember { mutableStateOf<com.cardiosuivi.domain.model.Measurement?>(null) }
    st.symptomAlerts.filter { it.level == AlertLevel.URGENT }.forEach { AlertCard(it, callEmergency) }
    HealthCard {
        CardHeader("Récapitulatif des mesures", Icons.Filled.MonitorHeart, Health.colors.systolic, trailing = "${st.measurements.size}/${st.planned}")
        st.measurements.sortedBy { it.index }.forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mesure ${m.index}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${m.systolic}/${m.diastolic} mmHg" + (m.pulse?.let { " · $it bpm" } ?: " · pouls non saisi"), style = NumberStyles.small)
                    Text("à ${Fmt.timeSeconds(m.localDateTime())}" + (m.modifiedAt?.let { " · corrigée" } ?: ""), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { vm.editMeasurement(m) }) { Icon(Icons.Filled.Edit, "Corriger") }
                IconButton(onClick = { confirmDelete = m }) { Icon(Icons.Filled.Delete, "Supprimer") }
            }
        }
        if (st.measurements.size < st.planned) {
            Text("${st.measurements.size} mesure(s) sur ${st.planned} prévues : la séance sera enregistrée comme incomplète.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            SecondaryButton("Ajouter la mesure ${st.nextIndex}", vm::goToEntry, icon = Icons.Filled.Add)
        }
    }
    if (stats != null) HealthCard {
        CardHeader("Moyennes de la séance", Icons.Filled.Calculate, Health.colors.diastolic)
        Text("${Fmt.dateLong(st.asSession().localDate).replaceFirstChar { it.uppercase() }} · ${periodText(st.period)} · ${st.arm.label}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        stats.primary?.let { p ->
            BpValue(Fmt.d0(p.systolic), Fmt.d0(p.diastolic), p.pulse?.let { Fmt.d0(it) }, style = NumberStyles.hero)
            Text("Valeur de séance : ${st.method.label}${if (stats.methodFallback) " — non applicable, moyenne des mesures disponibles" else ""}", style = MaterialTheme.typography.labelSmall)
        }
        HorizontalDivider()
        KeyValue("Moyenne des ${stats.measurementCount} mesures", stats.meanAll?.let { "${Fmt.d1(it.systolic)}/${Fmt.d1(it.diastolic)}" + (it.pulse?.let { p -> " · ${Fmt.d1(p)}" } ?: "") } ?: "—")
        KeyValue("Moyenne des 2 dernières", stats.meanLastTwo?.let { "${Fmt.d1(it.systolic)}/${Fmt.d1(it.diastolic)}" + (it.pulse?.let { p -> " · ${Fmt.d1(p)}" } ?: "") } ?: "—")
        KeyValue("Systolique min – max (amplitude)", "${stats.systolic.min ?: "—"} – ${stats.systolic.max ?: "—"} (${stats.systolic.range ?: "—"})")
        KeyValue("Diastolique min – max (amplitude)", "${stats.diastolic.min ?: "—"} – ${stats.diastolic.max ?: "—"} (${stats.diastolic.range ?: "—"})")
        KeyValue("Pouls min – max", "${stats.pulse.min ?: "—"} – ${stats.pulse.max ?: "—"}")
        KeyValue("Écart-type SYS / DIA", "${Fmt.d1(stats.systolic.sd)} / ${Fmt.d1(stats.diastolic.sd)}")
        KeyValue("Évolution entre prises (SYS)", stats.systolicDeltas.joinToString(" puis ") { if (it >= 0) "+$it" else "$it" }.ifBlank { "—" })
        KeyValue("Évolution entre prises (DIA)", stats.diastolicDeltas.joinToString(" puis ") { if (it >= 0) "+$it" else "$it" }.ifBlank { "—" })
        KeyValue("Pression pulsée moyenne", stats.pulsePressure.mean?.let { "${Fmt.d0(it)} mmHg" } ?: "—")
    }
    SectionCard("Symptômes associés (facultatif)") {
        Text("Ressentez-vous l'un de ces symptômes ?", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Symptom.entries.forEach { s ->
                FilterChip(selected = s in st.symptoms, onClick = { vm.toggleSymptom(s) }, label = { Text(s.label) })
            }
        }
        st.symptomAlerts.filter { it.level != AlertLevel.URGENT }.forEach { AlertCard(it) }
        OutlinedTextField(st.notes, vm::setNotes, label = { Text("Notes (contexte, oubli de traitement…)") }, modifier = Modifier.fillMaxWidth())
    }
    PrimaryButton(if (st.measurements.size >= st.planned) "Valider la séance" else "Enregistrer la séance incomplète", vm::saveSession, enabled = st.measurements.isNotEmpty(), icon = Icons.Filled.Check)
    confirmDelete?.let { m ->
        ConfirmDialog("Supprimer la mesure ${m.index} ?", "${m.systolic}/${m.diastolic} sera supprimée.", "Supprimer",
            onConfirm = { vm.deleteMeasurement(m); confirmDelete = null }, onDismiss = { confirmDelete = null }, destructive = true)
    }
}

/** Confirmation d'enregistrement : coche animée (sobre, désactivée si les animations sont réduites). */
@Composable
private fun SavedConfirmation() {
    val reduce = com.cardiosuivi.app.ui.theme.LocalReduceMotion.current
    var shown by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { shown = true }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (shown) 1f else 0.6f, androidx.compose.animation.core.spring(dampingRatio = 0.55f), label = "confirmation",
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Health.colors.favorable,
            modifier = Modifier.size(72.dp).graphicsLayer { scaleX = scale; scaleY = scale })
        Text("Séance enregistrée", style = MaterialTheme.typography.headlineSmall)
    }
}

/** Libellé du moment déterminé automatiquement. */
fun periodText(p: SessionPeriod): String = when (p) {
    SessionPeriod.MORNING -> "matin"
    SessionPeriod.EVENING -> "soir"
    SessionPeriod.OTHER -> "hors plages matin/soir"
}
