package com.cardiosuivi.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cardiosuivi.app.CardioSuiviApp
import com.cardiosuivi.app.data.enumOr
import com.cardiosuivi.app.ui.theme.CardioTheme
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.schedule.ScheduledSlot
import com.cardiosuivi.domain.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private const val TAG = "CardioReminders"

/** Exécute un travail asynchrone court dans un BroadcastReceiver (goAsync). */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur de traitement du rappel", e)
        } finally {
            pending.finish()
        }
    }
}

/** Déclenchement d'une alarme : affiche le rappel, programme l'alarme suivante et la vérification « manquée ». */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val slotKey = intent.getStringExtra(Notifications.EXTRA_SLOT_KEY) ?: return
        val period = enumOr(intent.getStringExtra(Notifications.EXTRA_PERIOD), SessionPeriod.MORNING)
        val snoozed = intent.getBooleanExtra(EXTRA_SNOOZED, false)
        runAsync {
            val c = (context.applicationContext as CardioSuiviApp).container
            val settings = c.settings.current()
            val date = runCatching { LocalDate.parse(slotKey.substringBeforeLast("-")) }.getOrDefault(LocalDate.now())
            // Pas de rappel si la séance a déjà été validée.
            val alreadyDone = c.sessionsOrNull()?.hasValidatedSession(date, period) ?: false
            if (!alreadyDone) Notifications.showReminder(context, slotKey, period, snoozed)
            if (!snoozed) {
                val time = if (period == SessionPeriod.MORNING) settings.reminders.morningTime else settings.reminders.eveningTime
                c.reminderScheduler.scheduleMissedCheck(ScheduledSlot(date, period, time), settings.reminders)
                c.reminderScheduler.rescheduleAll(settings.reminders)
                c.settings.markRescheduled()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.cardiosuivi.REMINDER_FIRE"
        const val EXTRA_SNOOZED = "snoozed"
    }
}

/** Actions de la notification : report et « ignorer ». */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slotKey = intent.getStringExtra(Notifications.EXTRA_SLOT_KEY) ?: return
        val period = enumOr(intent.getStringExtra(Notifications.EXTRA_PERIOD), SessionPeriod.MORNING)
        val c = (context.applicationContext as CardioSuiviApp).container
        when (intent.action) {
            ACTION_SNOOZE -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 10).coerceIn(1, 120)
                Notifications.cancel(context, slotKey)
                c.reminderScheduler.snooze(slotKey, period, minutes)
                runAsync { c.sessionsOrNull()?.recordReminderEvent(slotKey, "SNOOZED_$minutes") }
            }
            ACTION_DISMISS -> {
                Notifications.cancel(context, slotKey)
                runAsync { c.sessionsOrNull()?.recordReminderEvent(slotKey, "IGNORED") }
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.cardiosuivi.REMINDER_SNOOZE"
        const val ACTION_DISMISS = "com.cardiosuivi.REMINDER_DISMISS"
        const val EXTRA_MINUTES = "minutes"
    }
}

/** Rétablit les alarmes après redémarrage, mise à jour, changement d'heure ou d'autorisation. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync {
            val c = (context.applicationContext as CardioSuiviApp).container
            val s = c.settings.current()
            c.reminderScheduler.rescheduleAll(s.reminders)
            c.settings.markRescheduled()
        }
    }
}

/** Petit dialogue de choix du report (10, 15 ou 30 minutes), ouvert depuis la notification. */
class SnoozeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val slotKey = intent.getStringExtra(Notifications.EXTRA_SLOT_KEY)
        val period = intent.getStringExtra(Notifications.EXTRA_PERIOD)
        if (slotKey == null || period == null) { finish(); return }
        setContent {
            CardioTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Reporter le rappel") },
                    text = {
                        Column(Modifier.fillMaxWidth()) {
                            listOf(10, 15, 30).forEach { m ->
                                TextButton(onClick = { snooze(slotKey, period, m) }, modifier = Modifier.fillMaxWidth()) { Text("Dans $m minutes") }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { finish() }) { Text("Annuler") } },
                )
            }
        }
    }

    private fun snooze(slotKey: String, period: String, minutes: Int) {
        sendBroadcast(Intent(this, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
            putExtra(Notifications.EXTRA_SLOT_KEY, slotKey)
            putExtra(Notifications.EXTRA_PERIOD, period)
            putExtra(ReminderActionReceiver.EXTRA_MINUTES, minutes)
        })
        finish()
    }
}

/** Vérifie, après le délai de grâce, si la séance programmée a été validée ; sinon prévient l'utilisateur. */
class MissedSessionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val date = inputData.getString(KEY_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return Result.success()
        val period = enumOr(inputData.getString(KEY_PERIOD), SessionPeriod.MORNING)
        val c = (applicationContext as CardioSuiviApp).container
        val repo = c.sessionsOrNull() ?: return Result.retry()
        if (!repo.hasValidatedSession(date, period)) {
            val key = ScheduledSlot(date, period, LocalTime.MIDNIGHT).key
            Notifications.cancel(applicationContext, key)
            Notifications.showMissed(applicationContext, key, period, Fmt.date(date))
            repo.recordReminderEvent(key, "MISSED")
        }
        return Result.success()
    }

    companion object {
        const val KEY_DATE = "date"
        const val KEY_PERIOD = "period"
    }
}

class RescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as CardioSuiviApp).container
        c.reminderScheduler.rescheduleAll(c.settings.current().reminders)
        c.settings.markRescheduled()
        return Result.success()
    }
}
