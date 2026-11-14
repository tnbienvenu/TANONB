package com.cardiosuivi.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.cardiosuivi.app.reminders.Notifications
import com.cardiosuivi.app.ui.AppViewModel
import com.cardiosuivi.app.ui.Routes
import com.cardiosuivi.app.ui.components.ChoiceChips
import com.cardiosuivi.app.ui.components.KeyValue
import com.cardiosuivi.app.ui.components.ScreenScaffold
import com.cardiosuivi.app.ui.components.SectionCard
import com.cardiosuivi.app.ui.components.StatusDot
import com.cardiosuivi.app.ui.components.SwitchRow
import com.cardiosuivi.app.ui.theme.CardioColors
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.schedule.ScheduleCalculator
import com.cardiosuivi.domain.schedule.SlotStatus
import com.cardiosuivi.domain.util.Fmt
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Date

private fun slotColor(s: SlotStatus) = when (s) {
    SlotStatus.DONE -> CardioColors.Done
    SlotStatus.INCOMPLETE -> CardioColors.Incomplete
    SlotStatus.MISSED -> CardioColors.Missed
    SlotStatus.PENDING -> CardioColors.Pending
}

@Composable
fun CalendarScreen(vm: AppViewModel, nav: NavHostController) {
    val sessions by vm.sessions.collectAsState()
    val settings by vm.settings.collectAsState()
    val now = vm.now()
    var month by remember { mutableStateOf(YearMonth.from(now)) }
    var selected by remember { mutableStateOf(now.toLocalDate()) }
    val states = remember(sessions, settings, month) {
        ScheduleCalculator.slotStates(settings.reminders, sessions, month.atDay(1), month.atEndOfMonth(), now).groupBy { it.slot.date }
    }
    val validatedByDay = remember(sessions) { sessions.filter { it.status.isValidated }.groupBy { it.localDate } }

    ScreenScaffold("Calendrier des séances", onBack = { nav.popBackStack() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mois précédent") }
            Text("${month.month.getDisplayName(TextStyle.FULL, Fmt.LOCALE).replaceFirstChar { it.uppercase() }} ${month.year}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mois suivant") }
        }
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { d -> Text(d.getDisplayName(TextStyle.SHORT, Fmt.LOCALE), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) }
        }
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1
        val cells = offset + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val idx = r * 7 + c - offset
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            if (idx in 0 until month.lengthOfMonth()) {
                                val d = month.atDay(idx + 1)
                                val isSel = d == selected
                                Column(
                                    Modifier.fillMaxWidth().aspectRatio(1f)
                                        .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                                        .then(if (d == now.toLocalDate()) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
                                        .clickable { selected = d }.padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        states[d]?.forEach { StatusDot(slotColor(it.status), 7) }
                                        val extra = (validatedByDay[d]?.size ?: 0) - (states[d]?.count { it.sessionId != null } ?: 0)
                                        repeat(extra.coerceIn(0, 3)) { StatusDot(MaterialTheme.colorScheme.primary, 7) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SlotStatus.entries.forEach { s -> Row(verticalAlignment = Alignment.CenterVertically) { StatusDot(slotColor(s)); Text(" ${s.label}", style = MaterialTheme.typography.labelSmall) } }
            Row(verticalAlignment = Alignment.CenterVertically) { StatusDot(MaterialTheme.colorScheme.primary); Text(" Séance hors calendrier", style = MaterialTheme.typography.labelSmall) }
        }
        SectionCard(Fmt.dateLong(selected).replaceFirstChar { it.uppercase() }) {
            val slots = states[selected].orEmpty()
            if (slots.isEmpty()) Text("Aucune séance programmée ce jour.")
            slots.forEach { s -> KeyValue("${s.slot.period.label} (${s.slot.time})", s.status.label) }
            validatedByDay[selected].orEmpty().forEach { s ->
                TextButton(onClick = { nav.navigate(Routes.sessionDetail(s.id)) }) { Text("Voir la séance du ${s.period.label.lowercase()} (${s.status.label.lowercase()})") }
            }
        }
        SectionCard("Prochaines séances") {
            vm.nextSlots(6).forEach { s -> KeyValue(Fmt.dateLong(s.date).replaceFirstChar { it.uppercase() }, "${s.period.label} · ${s.time}") }
        }
    }
}

@Composable
fun RemindersScreen(vm: AppViewModel, nav: NavHostController) {
    val settings by vm.settings.collectAsState()
    val r = settings.reminders
    var editing by remember { mutableStateOf<SessionPeriod?>(null) }
    var interval by remember(settings.intervalSeconds) { mutableIntStateOf(settings.intervalSeconds) }
    var perSession by remember(settings.measurementsPerSession) { mutableIntStateOf(settings.measurementsPerSession) }
    val context = LocalContext.current

    ScreenScaffold("Programmation des rappels", onBack = { nav.popBackStack() }) {
        SectionCard {
            SwitchRow("Rappels activés", "Notifications émises par l'application Android aux horaires choisis", r.enabled) { vm.setReminders(r.copy(enabled = it)) }
        }
        SectionCard("Jours de mesure") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { d ->
                    FilterChip(selected = d in r.days, onClick = { vm.setReminders(r.copy(days = if (d in r.days) r.days - d else r.days + d)) },
                        label = { Text(d.getDisplayName(TextStyle.SHORT, Fmt.LOCALE)) })
                }
            }
            Text("Valeurs initiales : mercredi, jeudi et vendredi.", style = MaterialTheme.typography.labelSmall)
        }
        SectionCard("Horaires") {
            SwitchRow("Séance du matin", "à ${r.morningTime}", r.morningEnabled) { vm.setReminders(r.copy(morningEnabled = it)) }
            TextButton(onClick = { editing = SessionPeriod.MORNING }) { Text("Modifier l'heure du matin") }
            SwitchRow("Séance du soir", "à ${r.eveningTime}", r.eveningEnabled) { vm.setReminders(r.copy(eveningEnabled = it)) }
            TextButton(onClick = { editing = SessionPeriod.EVENING }) { Text("Modifier l'heure du soir") }
            Text("Horaires initiaux proposés : 07:00 et 19:00. Ce sont des réglages personnels, pas des horaires médicaux.", style = MaterialTheme.typography.labelSmall)
            Text("Délai avant de considérer une séance comme manquée", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(listOf(60, 120, 180, 240), r.missedGraceMinutes, { "${it / 60} h" }) { vm.setReminders(r.copy(missedGraceMinutes = it)) }
        }
        SectionCard("Protocole de séance") {
            Text("Intervalle entre deux mesures", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(listOf(60, 90, 120), interval, { "${it} s" }) { interval = it; vm.setProtocol(it, perSession) }
            Text("Nombre de mesures par séance", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(listOf(2, 3), perSession, { "$it" }) { perSession = it; vm.setProtocol(interval, it) }
        }
        SectionCard("Vérification") {
            Button(onClick = { Notifications.showReminder(context, "test-${System.currentTimeMillis()}", SessionPeriod.MORNING, snoozed = false) }) { Text("Envoyer un rappel de test") }
            OutlinedButton(onClick = { nav.navigate(Routes.PERMISSIONS) }) { Text("Diagnostic des autorisations") }
        }
    }

    editing?.let { p ->
        val init = if (p == SessionPeriod.MORNING) r.morningTime else r.eveningTime
        val state = rememberTimePickerState(init.hour, init.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (p == SessionPeriod.MORNING) "Heure du matin" else "Heure du soir") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime.of(state.hour, state.minute)
                    vm.setReminders(if (p == SessionPeriod.MORNING) r.copy(morningTime = t) else r.copy(eveningTime = t))
                    editing = null
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun StatusLine(ok: Boolean, title: String, detail: String, action: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error, null, tint = if (ok) Color(0xFF2E7D32) else Color(0xFFC62828))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
            action?.invoke()
        }
    }
}

@Composable
fun PermissionsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    @Suppress("UNUSED_EXPRESSION") tick
    val notifOk = Notifications.canPost(context)
    val exactOk = vm.c.reminderScheduler.canScheduleExact()
    val pm = context.getSystemService(PowerManager::class.java)
    val batteryOk = pm.isIgnoringBatteryOptimizations(context.packageName)
    val cameraOk = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED

    ScreenScaffold("Diagnostic des autorisations", onBack = onBack) {
        SectionCard("Rappels") {
            StatusLine(notifOk, "Notifications", if (notifOk) "Autorisées." else "Bloquées : aucun rappel ne pourra s'afficher.") {
                if (!notifOk) TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("Autoriser") }
            }
            StatusLine(exactOk, "Alarmes et rappels (heure exacte)",
                if (exactOk) "Les rappels se déclenchent à l'heure exacte." else "Non accordé : les rappels peuvent être décalés de quelques minutes.") {
                if (!exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }) { Text("Ouvrir le réglage") }
            }
            StatusLine(batteryOk, "Optimisation de la batterie",
                if (batteryOk) "L'application n'est pas restreinte." else "Optimisée par le système : sur certains téléphones, les rappels peuvent être retardés. Vous pouvez exclure CardioSuivi de l'optimisation.") {
                if (!batteryOk) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("Réglages de batterie") }
            }
            KeyValue("Dernière reprogrammation", if (settings.lastRescheduleAt == 0L) "—" else DateFormat.getDateTimeInstance().format(Date(settings.lastRescheduleAt)))
            KeyValue("Prochains rappels", vm.nextSlots(2).joinToString(" ; ") { "${Fmt.date(it.date)} ${it.time}" }.ifBlank { "Aucun" })
            Text("Les rappels sont reprogrammés automatiquement après redémarrage du téléphone, mise à jour de l'application ou changement d'heure.", style = MaterialTheme.typography.labelSmall)
            OutlinedButton(onClick = { vm.setReminders(settings.reminders) }) { Text("Reprogrammer maintenant") }
        }
        SectionCard("Appareil photo") {
            StatusLine(cameraOk, "Caméra", if (cameraOk) "Autorisée." else "Non autorisée : l'import de photo et la saisie manuelle restent possibles.") {
                if (!cameraOk) TextButton(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) { Text("Autoriser") }
            }
        }
        SectionCard("Système") {
            KeyValue("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            KeyValue("Appareil", "${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }
}
