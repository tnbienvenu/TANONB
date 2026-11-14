package com.cardiosuivi.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cardiosuivi.app.MainActivity
import com.cardiosuivi.app.R
import com.cardiosuivi.domain.model.SessionPeriod

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_FOLLOWUP = "followup"

    const val EXTRA_START_SESSION = "com.cardiosuivi.START_SESSION"
    const val EXTRA_PERIOD = "com.cardiosuivi.PERIOD"
    const val EXTRA_SLOT_KEY = "com.cardiosuivi.SLOT_KEY"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Rappels de séance", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rappels aux horaires de mesure programmés"
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FOLLOWUP, "Suivi des séances", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Séances non réalisées"
            },
        )
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notificationId(slotKey: String): Int = 1000 + (slotKey.hashCode() and 0x7fff)

    fun startSessionIntent(context: Context, period: SessionPeriod, slotKey: String?): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_START_SESSION, true)
            putExtra(EXTRA_PERIOD, period.name)
            slotKey?.let { putExtra(EXTRA_SLOT_KEY, it) }
        }
        return PendingIntent.getActivity(context, (slotKey ?: period.name).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionIntent(context: Context, action: String, slotKey: String, period: SessionPeriod, minutes: Int = 0): PendingIntent {
        val i = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_SLOT_KEY, slotKey)
            putExtra(EXTRA_PERIOD, period.name)
            putExtra(ReminderActionReceiver.EXTRA_MINUTES, minutes)
        }
        return PendingIntent.getBroadcast(context, "$action-$slotKey-$minutes".hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Ouvre un petit dialogue proposant un report de 10, 15 ou 30 minutes. */
    private fun snoozeChooserIntent(context: Context, slotKey: String, period: SessionPeriod): PendingIntent {
        val i = Intent(context, SnoozeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
            putExtra(EXTRA_SLOT_KEY, slotKey)
            putExtra(EXTRA_PERIOD, period.name)
        }
        return PendingIntent.getActivity(context, "snooze-$slotKey".hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun showReminder(context: Context, slotKey: String, period: SessionPeriod, snoozed: Boolean) {
        if (!canPost(context)) return
        val title = if (snoozed) "Rappel reporté : séance du ${period.label.lowercase()}" else "Séance de tension du ${period.label.lowercase()}"
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Reposez-vous 5 minutes au calme, puis réalisez vos trois mesures.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Reposez-vous 5 minutes au calme, assis, dos appuyé, pieds au sol, bras à hauteur du cœur. Puis réalisez vos trois mesures.",
            ))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(startSessionIntent(context, period, slotKey))
            .setAutoCancel(true)
            .addAction(0, "Commencer", startSessionIntent(context, period, slotKey))
            .addAction(0, "Reporter…", snoozeChooserIntent(context, slotKey, period))
            .addAction(0, "Ignorer", actionIntent(context, ReminderActionReceiver.ACTION_DISMISS, slotKey, period))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(notificationId(slotKey), n)
    }

    fun showMissed(context: Context, slotKey: String, period: SessionPeriod, dateLabel: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_FOLLOWUP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Séance non réalisée")
            .setContentText("La séance du ${period.label.lowercase()} ($dateLabel) n'a pas été validée.")
            .setContentIntent(startSessionIntent(context, period, slotKey))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(notificationId("missed-$slotKey"), n)
    }

    fun cancel(context: Context, slotKey: String) = NotificationManagerCompat.from(context).cancel(notificationId(slotKey))
}
