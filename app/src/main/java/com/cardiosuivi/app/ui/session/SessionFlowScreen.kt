package com.cardiosuivi.app.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardiosuivi.app.ocr.CropQuad
import com.cardiosuivi.app.ocr.NPoint
import com.cardiosuivi.app.report.Sharing
import com.cardiosuivi.app.ui.AppViewModel
import com.cardiosuivi.app.ui.components.AlertCard
import com.cardiosuivi.app.ui.components.BpValue
import com.cardiosuivi.app.ui.components.ChoiceChips
import com.cardiosuivi.app.ui.components.ConfirmDialog
import com.cardiosuivi.app.ui.components.KeyValue
import com.cardiosuivi.app.ui.components.NumberField
import com.cardiosuivi.app.ui.components.ScreenScaffold
import com.cardiosuivi.app.ui.components.SectionCard
import com.cardiosuivi.app.ui.theme.CardioColors
import com.cardiosuivi.domain.alerts.AlertLevel
import com.cardiosuivi.domain.model.Arm
import com.cardiosuivi.domain.model.ProtocolType
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.Symptom
import com.cardiosuivi.domain.util.Fmt
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.hypot

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
        else -> "Mesure ${minOf(st.nextIndex, st.planned)} sur ${st.planned}"
    }
    ScreenScaffold(title, onBack = { if (st.step == FlowStep.SAVED) onExit() else confirmExit = true }) {
        if (st.measurements.isNotEmpty() && st.step !in setOf(FlowStep.SUMMARY, FlowStep.SAVED)) {
            LinearProgressIndicator(progress = { st.measurements.size.toFloat() / st.planned }, modifier = Modifier.fillMaxWidth())
        }
        st.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (st.step) {
            FlowStep.LOADING -> CircularProgressIndicator()
            FlowStep.PREPARATION -> Preparation(st, vm)
            FlowStep.REST -> Countdown("Repos au calme", st.restRemaining, 300, "Restez assis, détendu, sans parler. La mesure pourra commencer à la fin du décompte.") {
                OutlinedButton(onClick = vm::goToCapture) { Text("Passer le repos") }
            }
            FlowStep.CAPTURE -> CameraCapture(st.nextIndex, vm::onPhotoCaptured, vm::onPhotoImported, vm::manualEntry)
            FlowStep.VALIDATE -> Validation(st, vm)
            FlowStep.INTERVAL -> {
                st.lastMeasurementAlerts.forEach { AlertCard(it, callEmergency) }
                val last = st.measurements.last()
                SectionCard("Mesure ${last.index} enregistrée") { BpValue("${last.systolic}", "${last.diastolic}", last.pulse?.toString() ?: "—") }
                Countdown("Avant la mesure ${st.nextIndex}", st.intervalRemaining, st.intervalSeconds, "Gardez la même position, bras au repos, sans parler.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::goToCapture) { Text(if (st.intervalRemaining > 0) "Mesurer maintenant" else "Mesure suivante") }
                        OutlinedButton(onClick = vm::skipToSummary) { Text("Arrêter ici") }
                    }
                }
            }
            FlowStep.SUMMARY -> Summary(st, vm, callEmergency)
            FlowStep.SAVED -> {
                val urgent = st.sessionAlerts.any { it.level == AlertLevel.URGENT }
                if (urgent) Text("Recommandation urgente", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                st.sessionAlerts.sortedByDescending { it.level.ordinal }.forEach { AlertCard(it, callEmergency) }
                if (st.sessionAlerts.isEmpty()) Text("Aucune alerte pour cette séance.")
                SectionCard("Valeur de séance") {
                    st.stats?.primary?.let { p -> BpValue(Fmt.d0(p.systolic), Fmt.d0(p.diastolic), p.pulse?.let { Fmt.d0(it) }) }
                    Text(st.method.label, style = MaterialTheme.typography.labelSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExit) { Text("Terminer") }
                    st.sessionId?.let { id -> OutlinedButton(onClick = { onOpenSession(id) }) { Text("Voir le détail") } }
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Quitter la séance ?") },
            text = { Text(if (st.measurements.isEmpty()) "Aucune mesure n'a été enregistrée." else "${st.measurements.size} mesure(s) enregistrée(s). Vous pouvez reprendre la séance plus tard depuis l'accueil, ou l'abandonner (suppression).") },
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
    SectionCard("Avant de commencer") {
        listOf(
            "Reposez-vous cinq minutes au calme.",
            "Asseyez-vous, dos soutenu, pieds à plat au sol, jambes non croisées.",
            "Placez le brassard sur le bras nu, à hauteur du cœur, bras posé.",
            "Utilisez un brassard de taille adaptée à votre bras.",
            "Ne parlez pas et ne bougez pas pendant la mesure.",
            "Évitez caféine, tabac et exercice physique dans les 30 minutes précédentes.",
            "Respectez les consignes du fabricant de votre tensiomètre.",
        ).forEach { Text("• $it") }
    }
    SectionCard("Séance") {
        Text("Moment", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(listOf(SessionPeriod.MORNING, SessionPeriod.EVENING, SessionPeriod.OTHER), st.period, { it.label }, vm::setPeriod)
        Text("Bras", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(Arm.entries.toList(), st.arm, { it.label }, vm::setArm)
        Text("${st.planned} mesures espacées d'environ ${st.intervalSeconds} secondes." +
            if (st.protocol == ProtocolType.STANDARDIZED_HBPM) " (Protocole d'automesure standardisée : 2 mesures par séance.)" else "",
            style = MaterialTheme.typography.bodySmall)
    }
    Button(onClick = vm::startRest, modifier = Modifier.fillMaxWidth()) { Text("Démarrer le repos de 5 minutes") }
    OutlinedButton(onClick = vm::goToCapture, modifier = Modifier.fillMaxWidth()) { Text("Je suis déjà reposé(e) : commencer") }
}

@Composable
private fun Countdown(title: String, remaining: Int, total: Int, hint: String, actions: @Composable () -> Unit) {
    SectionCard(title) {
        Text(String.format(Fmt.LOCALE, "%d:%02d", remaining / 60, remaining % 60), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        LinearProgressIndicator(progress = { 1f - remaining.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
        Text(hint, style = MaterialTheme.typography.bodySmall)
        actions()
    }
}

@Composable
private fun Validation(st: FlowState, vm: SessionFlowViewModel) {
    val bmp by vm.bitmap.collectAsState()
    bmp?.let { b ->
        SectionCard("Photo") {
            Text("Ajustez les quatre coins sur l'écran du tensiomètre (corrige la perspective), puis relancez l'analyse.", style = MaterialTheme.typography.bodySmall)
            CropEditor(b, st.quad, vm::setQuad)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::runOcr, enabled = !st.ocrRunning) { Text("Analyser la zone") }
                TextButton(onClick = { vm.setQuad(if (st.quad.isFull) CropQuad.DEFAULT else CropQuad.FULL) }) { Text(if (st.quad.isFull) "Définir une zone" else "Image entière") }
            }
            if (st.ocrRunning) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(4.dp)); Text("Reconnaissance en cours…") }
        }
    }
    SectionCard(if (st.editingMeasurementId != null) "Corriger la mesure" else "Valeurs de la mesure ${st.nextIndex}") {
        st.ocrMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        st.ocrReading?.let { r -> Text("Confiance globale de la lecture : ${Fmt.pct(r.overallConfidence)}", style = MaterialTheme.typography.labelSmall) }
        FieldRow("Systolique (SYS, mmHg)", st.sys, CardioColors.Systolic, vm::setSys)
        FieldRow("Diastolique (DIA, mmHg)", st.dia, CardioColors.Diastolic, vm::setDia)
        FieldRow("Fréquence cardiaque (PUL, /min) — facultatif si non affichée", st.pul, CardioColors.Pulse, vm::setPul)
        TextButton(onClick = vm::swapSysDia) { Icon(Icons.Filled.SwapVert, null); Text(" Intervertir SYS et DIA") }
        Text("Aucune valeur n'est enregistrée sans votre validation. Les champs signalés doivent être vérifiés sur l'écran du tensiomètre.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { vm.validateMeasurement() }, modifier = Modifier.fillMaxWidth(), enabled = !st.ocrRunning) { Text("Valider ces valeurs") }
        if (st.editingMeasurementId == null) OutlinedButton(onClick = vm::goToCapture, modifier = Modifier.fillMaxWidth()) { Text("Reprendre la photo") }
    }
    if (st.pendingPlausibility.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Vérifiez les valeurs") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { st.pendingPlausibility.forEach { Text("• ${it.message}") } } },
            confirmButton = { TextButton(onClick = { vm.validateMeasurement(confirmed = true) }) { Text("Je confirme ces valeurs") } },
            dismissButton = { TextButton(onClick = { vm.setSys(st.sys.text) }) { Text("Corriger") } },
        )
    }
}

@Composable
private fun FieldRow(label: String, f: FieldInput, color: Color, onChange: (String) -> Unit) {
    Column {
        NumberField(
            label, f.text, onChange, Modifier.fillMaxWidth(), isError = f.uncertain,
            supporting = when {
                f.uncertain && f.text.isBlank() -> "Non lu : ${f.hint ?: "à saisir"}"
                f.uncertain -> "À vérifier : ${f.hint ?: "lecture incertaine"}"
                else -> f.hint
            },
        )
        if (f.alternatives.isNotEmpty() && f.uncertain) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Autres valeurs lues :", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterVertically), color = color)
                f.alternatives.take(6).forEach { v -> AssistChip(onClick = { onChange(v.toString()) }, label = { Text("$v") }) }
            }
        }
    }
}

/** Éditeur de recadrage : quatre coins déplaçables sur l'image (coordonnées normalisées). */
@Composable
private fun CropEditor(bitmap: android.graphics.Bitmap, quad: CropQuad, onChange: (CropQuad) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var dragging by remember { mutableIntStateOf(-1) }
    val ratio = bitmap.width.toFloat() / bitmap.height
    Box(Modifier.fillMaxWidth().aspectRatio(ratio)) {
        Image(image, contentDescription = "Photo de l'écran du tensiomètre", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        if (!quad.isFull) {
            val current by androidx.compose.runtime.rememberUpdatedState(quad)
            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o ->
                            val pts = listOf(current.tl, current.tr, current.br, current.bl)
                            val i = pts.indices.minBy { hypot(pts[it].x * size.width - o.x, pts[it].y * size.height - o.y) }
                            dragging = if (hypot(pts[i].x * size.width - o.x, pts[i].y * size.height - o.y) < 120f) i else -1
                        },
                        onDragEnd = { dragging = -1 },
                        onDrag = { change, delta ->
                            if (dragging >= 0) {
                                change.consume()
                                fun mv(p: NPoint) = NPoint((p.x + delta.x / size.width).coerceIn(0f, 1f), (p.y + delta.y / size.height).coerceIn(0f, 1f))
                                val q = current
                                onChange(when (dragging) {
                                    0 -> q.copy(tl = mv(q.tl)); 1 -> q.copy(tr = mv(q.tr)); 2 -> q.copy(br = mv(q.br)); else -> q.copy(bl = mv(q.bl))
                                })
                            }
                        },
                    )
                },
            ) {
                val pts = listOf(quad.tl, quad.tr, quad.br, quad.bl).map { Offset(it.x * size.width, it.y * size.height) }
                val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                drawPath(path, Color(0xFF00E5FF), style = Stroke(width = 4f))
                pts.forEach { drawCircle(Color(0xFF00E5FF), 18f, it); drawCircle(Color.White, 8f, it) }
            }
        }
    }
}

@Composable
private fun Summary(st: FlowState, vm: SessionFlowViewModel, callEmergency: () -> Unit) {
    val stats = st.stats
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
    var confirmDelete by remember { mutableStateOf<com.cardiosuivi.domain.model.Measurement?>(null) }
    st.symptomAlerts.filter { it.level == AlertLevel.URGENT }.forEach { AlertCard(it, callEmergency) }
    SectionCard("Mesures") {
        st.measurements.sortedBy { it.index }.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${m.index}. ${m.systolic}/${m.diastolic} mmHg" + (m.pulse?.let { " · $it /min" } ?: " · pouls non saisi"), fontWeight = FontWeight.Medium)
                    Text("${timeFmt.format(m.measuredAt.atZone(ZoneId.systemDefault()))} · ${m.source.label}", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { vm.editMeasurement(m) }) { Icon(Icons.Filled.Edit, "Corriger") }
                IconButton(onClick = { confirmDelete = m }) { Icon(Icons.Filled.Delete, "Supprimer") }
            }
        }
        if (st.measurements.size < st.planned) {
            Text("${st.measurements.size} mesure(s) sur ${st.planned} prévues : la séance sera enregistrée comme incomplète.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = vm::goToCapture) { Text("Ajouter la mesure ${st.nextIndex}") }
        }
    }
    if (stats != null) SectionCard("Calculs de la séance") {
        stats.primary?.let { p ->
            BpValue(Fmt.d0(p.systolic), Fmt.d0(p.diastolic), p.pulse?.let { Fmt.d0(it) })
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
    Button(onClick = vm::saveSession, modifier = Modifier.fillMaxWidth(), enabled = st.measurements.isNotEmpty()) {
        Text(if (st.measurements.size >= st.planned) "Valider la séance" else "Enregistrer la séance incomplète")
    }
    confirmDelete?.let { m ->
        ConfirmDialog("Supprimer la mesure ${m.index} ?", "${m.systolic}/${m.diastolic} sera supprimée.", "Supprimer",
            onConfirm = { vm.deleteMeasurement(m); confirmDelete = null }, onDismiss = { confirmDelete = null }, destructive = true)
    }
}
