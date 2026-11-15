package com.cardiosuivi.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cardiosuivi.domain.model.ReminderConfig
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.schedule.ScheduleCalculator
import com.cardiosuivi.domain.schedule.ScheduledSlot
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Programme les alarmes de rappel avec AlarmManager.
 * - Alarme exacte (setExactAndAllowWhileIdle) si l'autorisation « Alarmes et rappels » est accordée ;
 * - sinon alarme approchée dans une fenêtre de 10 minutes (setWindow), limitation signalée dans l'écran de diagnostic.
 * Une seule alarme est programmée par moment (matin, soir) : la suivante est reprogrammée à chaque déclenchement,
 * au démarrage du téléphone et à chaque modification des réglages.
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Reprogramme toutes les alarmes à partir de la configuration. Retourne les prochains créneaux programmés. */
    fun rescheduleAll(config: ReminderConfig, now: ZonedDateTime = ZonedDateTime.now()): List<ScheduledSlot> {
        val scheduled = mutableListOf<ScheduledSlot>()
        for (period in listOf(SessionPeriod.MORNING, SessionPeriod.EVENING)) {
            cancel(requestCode(period))
            val next = ScheduleCalculator.nextOccurrence(config, period, now) ?: continue
            val slot = ScheduledSlot(next.toLocalDate(), period, next.toLocalTime())
            setAlarm(requestCode(period), next.toInstant().toEpochMilli(), slot, snoozed = false)
            scheduled += slot
        }
        ensureDailySafetyNet()
        return scheduled.sortedBy { it.at(now.zone) }
    }

    fun snooze(slotKey: String, period: SessionPeriod, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val parts = slotKey.split("-")
        val date = runCatching { java.time.LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()) }.getOrNull()
            ?: java.time.LocalDate.now()
        val slot = ScheduledSlot(date, period, java.time.LocalTime.now())
        setAlarm(snoozeRequestCode(period), at, slot, snoozed = true, slotKeyOverride = slotKey)
    }

    fun cancelAll() {
        for (p in listOf(SessionPeriod.MORNING, SessionPeriod.EVENING)) {
            cancel(requestCode(p)); cancel(snoozeRequestCode(p))
        }
    }

    /** Planifie la vérification « séance manquée » après le délai de grâce. */
    fun scheduleMissedCheck(slot: ScheduledSlot, config: ReminderConfig, zone: ZoneId = ZoneId.systemDefault()) {
        val deadline = slot.at(zone).plusMinutes(config.missedGraceMinutes.toLong())
        val delay = Duration.between(ZonedDateTime.now(zone), deadline).toMillis().coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<MissedSessionWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(MissedSessionWorker.KEY_DATE, slot.date.toString()).putString(MissedSessionWorker.KEY_PERIOD, slot.period.name).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("missed-${slot.key}", ExistingWorkPolicy.REPLACE, req)
    }

    /** Filet de sécurité : reprogrammation quotidienne au cas où une alarme aurait été perdue. */
    private fun ensureDailySafetyNet() {
        val req = PeriodicWorkRequestBuilder<RescheduleWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("reschedule", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    private fun setAlarm(code: Int, atMillis: Long, slot: ScheduledSlot, snoozed: Boolean, slotKeyOverride: String? = null) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_FIRE
            putExtra(Notifications.EXTRA_SLOT_KEY, slotKeyOverride ?: slot.key)
            putExtra(Notifications.EXTRA_PERIOD, slot.period.name)
            putExtra(ReminderAlarmReceiver.EXTRA_SNOOZED, snoozed)
        }
        val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, atMillis, 10 * 60_000L, pi)
        }
    }

    private fun cancel(code: Int) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply { action = ReminderAlarmReceiver.ACTION_FIRE }
        val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    private fun requestCode(p: SessionPeriod) = if (p == SessionPeriod.MORNING) 101 else 102
    private fun snoozeRequestCode(p: SessionPeriod) = if (p == SessionPeriod.MORNING) 201 else 202
}

/** Ouvre le réglage système « Alarmes et rappels » de l'application (Android 12+). */
object ExactAlarmSettings {
    fun open(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
