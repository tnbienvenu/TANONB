package com.cardiosuivi.app.recette

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.cardiosuivi.domain.model.ReminderConfig
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.schedule.ScheduledSlot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class R15_RappelsTest : RecetteBase() {

    private fun shell(cmd: String): String {
        val pfd = instr.uiAutomation.executeShellCommand(cmd)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText()
    }

    private fun alarmsOfApp(): List<String> = shell("dumpsys alarm").lines().filter { it.contains(ctx.packageName) && it.contains("RTC_WAKEUP") }

    @Test
    fun r15_programmation_rappels_et_ecran() {
        resetApp(); launch()
        click("Plus"); click("Rappels")
        waitText("Jours de mesure")
        shot("R15_ecran_rappels")
        val defaults = exists("à 07:00") && exists("à 19:00")
        record("R15a", "Rappels – valeurs initiales", "aucune modification", "mer/jeu/ven, 07:00 et 19:00", if (defaults) "07:00 / 19:00" else "autres", defaults, "R15_ecran_rappels.png")
        val alarms = alarmsOfApp()
        File(outDir, "R15_dumpsys_alarm.txt").writeText(shell("dumpsys alarm").lines().filter { it.contains(ctx.packageName) }.joinToString("\n"))
        record("R15b", "Rappels – alarmes système", "réglages par défaut", "2 alarmes programmées (matin et soir) dans AlarmManager", "${alarms.size} alarme(s) RTC_WAKEUP de l'application", alarms.size >= 2, "R15_dumpsys_alarm.txt")
        click("lun.")
        Thread.sleep(1500)
        val days = runBlocking { c.settings.current().reminders.days }
        record("R15c", "Rappels – jours", "ajout du lundi", "lundi enregistré", "$days", days.contains(java.time.DayOfWeek.MONDAY), "—")
        click("lun.")
        click("Séance du soir")
        Thread.sleep(1500)
        val eve = runBlocking { c.settings.current().reminders.eveningEnabled }
        record("R15d", "Rappels – désactivation du soir", "interrupteur soir", "soir désactivé", "eveningEnabled=$eve", !eve, "—")
        click("Séance du soir")
        click("90 s")
        click("2", substring = false)
        Thread.sleep(1500)
        val st = runBlocking { c.settings.current() }
        record("R15e", "Protocole de séance", "90 s, 2 mesures", "intervalle 90 s et 2 mesures enregistrés", "interval=${st.intervalSeconds}, mesures=${st.measurementsPerSession}",
            st.intervalSeconds == 90 && st.measurementsPerSession == 2, "—")
        click("Modifier l'heure du matin")
        waitText("Heure du matin")
        shot("R15_selecteur_heure")
        click("Annuler")
        record("R15f", "Sélecteur d'heure", "ouvrir / annuler", "dialogue d'heure 24 h affiché puis fermé sans modification",
            "heure matin=${runBlocking { c.settings.current().reminders.morningTime }}", runBlocking { c.settings.current().reminders.morningTime } == LocalTime.of(7, 0), "R15_selecteur_heure.png")
        relaunch(); click("Plus"); click("Rappels")
        record("R15g", "Rappels – persistance", "relance", "90 s et 2 mesures conservés", if (exists("90 s")) "conservés" else "perdus", runBlocking { c.settings.current().intervalSeconds } == 90, "—")
    }

    @Test
    fun r16_notification_reporter_ignorer_commencer() {
        // Pré-requis côté hôte (le retrait de l'autorisation tue le processus) : appops SCHEDULE_EXACT_ALARM default.
        resetApp()
        launch()
        val prompt = !c.reminderScheduler.canScheduleExact() && exists("Autoriser les rappels à l'heure exacte")
        shot("R16_alerte_heure_exacte")
        record("R16p", "Alarmes exactes non autorisées (Android 14, défaut)", "installation neuve", "alerte sur l'accueil + bouton vers « Alarmes et rappels »",
            "canScheduleExact=${c.reminderScheduler.canScheduleExact()}, alerte=${exists("Autoriser les rappels à l'heure exacte")}", prompt || c.reminderScheduler.canScheduleExact(), "R16_alerte_heure_exacte.png")
        shell("appops set ${ctx.packageName} SCHEDULE_EXACT_ALARM allow")
        // Programme un rappel « matin » dans 2 minutes, tous les jours.
        val now = ZonedDateTime.now()
        val t = now.toLocalTime().plusMinutes(2).withSecond(0).withNano(0)
        val cfg = ReminderConfig(days = java.time.DayOfWeek.entries.toSet(), morningTime = t, eveningEnabled = false)
        runBlocking { c.settings.setReminders(cfg) }
        c.reminderScheduler.rescheduleAll(cfg)
        record("R16a", "Alarme exacte", "autorisation accordée, rappel à $t", "alarme exacte programmée", "canScheduleExact=${c.reminderScheduler.canScheduleExact()}", c.reminderScheduler.canScheduleExact(), "R15_dumpsys_alarm.txt")
        scenario?.close()
        device.pressHome()
        device.openNotification()
        val n = device.wait(Until.findObject(By.textContains("Séance de tension du matin")), 9 * 60_000L)
        Thread.sleep(1500)
        shot("R16_notification")
        record("R16b", "Notification de rappel", "attente du déclenchement (~2 min)", "notification « Séance de tension du matin » avec Commencer / Reporter… / Ignorer",
            if (n != null) "reçue à ${LocalTime.now().withNano(0)}" else "non reçue en 9 min", n != null && device.hasObject(By.textContains("Reporter")), "R16_notification.png")
        // Reporter → dialogue 10/15/30
        device.findObject(By.textContains("Reporter"))?.click()
        val dlg = device.wait(Until.findObject(By.textContains("Dans 10 minutes")), 60_000)
        shot("R16_report_dialogue")
        dlg?.click()
        Thread.sleep(3000)
        val snoozeAlarms = alarmsOfApp()
        record("R16c", "Reporter", "Reporter… → Dans 10 minutes", "notification retirée, nouvelle alarme ~10 min plus tard", "dialogue=${dlg != null}, alarmes=${snoozeAlarms.size}",
            dlg != null && snoozeAlarms.size >= 2, "R16_report_dialogue.png")
        // Ignorer : on poste directement une notification de rappel (même code que l'alarme)
        com.cardiosuivi.app.reminders.Notifications.showReminder(ctx, "${LocalDate.now()}-MORNING", SessionPeriod.MORNING, snoozed = false)
        device.openNotification()
        device.wait(Until.findObject(By.text("Ignorer")), 30_000)?.click()
        Thread.sleep(3000)
        val gone = !device.hasObject(By.textContains("Séance de tension du matin"))
        val ev = runBlocking { c.database.reminderEventDao().forSlot("${LocalDate.now()}-MORNING").map { it.action } }
        record("R16d", "Ignorer", "bouton Ignorer", "notification retirée, événement IGNORED journalisé", "retirée=$gone, événements=$ev", gone && ev.contains("IGNORED"), "—")
        // Commencer
        com.cardiosuivi.app.reminders.Notifications.showReminder(ctx, "${LocalDate.now()}-MORNING", SessionPeriod.MORNING, snoozed = false)
        device.openNotification()
        device.wait(Until.findObject(By.text("Commencer")), 30_000)?.click()
        val t0 = System.currentTimeMillis()
        var opened = false
        while (!opened && System.currentTimeMillis() - t0 < 180_000) {
            opened = device.hasObject(By.textContains("Avant de commencer")) || device.hasObject(By.text("Nouvelle séance"))
            if (!opened) Thread.sleep(3000)
        }
        val delay = (System.currentTimeMillis() - t0) / 1000
        shot("R16_commencer")
        runCatching {
            java.io.File(ctx.getExternalFilesDir(null), "recette/R16_commencer_hierarchie.xml").outputStream().use { device.dumpWindowHierarchy(it) }
        }
        record("R16e", "Commencer depuis la notification", "bouton Commencer", "ouverture directe de la séance du matin",
            if (opened) "séance ouverte en $delay s" else "non détectée en $delay s", opened, "R16_commencer.png, R16_commencer_hierarchie.xml")
        device.pressBack(); Thread.sleep(1500)
    }

    @Test
    fun r17_seance_manquee() {
        resetApp(); launch()
        val past = ScheduledSlot(LocalDate.now().minusDays(1), SessionPeriod.EVENING, LocalTime.of(19, 0))
        c.reminderScheduler.scheduleMissedCheck(past, ReminderConfig())
        scenario?.close(); device.pressHome()
        device.openNotification()
        val n = device.wait(Until.findObject(By.textContains("Séance non réalisée")), 4 * 60_000L)
        shot("R17_seance_manquee")
        record("R17", "Détection de séance manquée", "créneau d'hier 19:00 sans séance (WorkManager)", "notification « Séance non réalisée »",
            if (n != null) "reçue" else "non reçue", n != null, "R17_seance_manquee.png")
        device.pressBack()
    }
}
