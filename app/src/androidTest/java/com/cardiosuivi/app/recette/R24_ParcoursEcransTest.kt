package com.cardiosuivi.app.recette

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardiosuivi.domain.model.BpTarget
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.MeasurementSource
import com.cardiosuivi.domain.model.PeriodWindows
import com.cardiosuivi.domain.model.Profile
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import com.cardiosuivi.domain.model.Treatment
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Parcours de TOUS les écrans (5 onglets + 12 entrées du Profil) en clair puis en sombre, avec données FICTIVES ;
 * contrôle du titre, de l'absence de plantage et de la luminance du fond. Plages matin/soir configurables.
 */
@RunWith(AndroidJUnit4::class)
class R24_ParcoursEcransTest : RecetteBase() {

    private val zone = ZoneId.systemDefault()

    private fun shell(cmd: String) { instr.uiAutomation.executeShellCommand(cmd).close(); Thread.sleep(1500) }

    private fun luma(): Int {
        val b: Bitmap = instr.uiAutomation.takeScreenshot() ?: return -1
        var sum = 0L; var n = 0
        for (y in b.height / 10 until b.height / 10 + 20) for (x in 4 until 24) {
            val p = b.getPixel(x, y); sum += ((p shr 16 and 0xff) * 299 + (p shr 8 and 0xff) * 587 + (p and 0xff) * 114) / 1000; n++
        }
        return (sum / n).toInt()
    }

    private fun seed() = runBlocking {
        c.profile.saveProfile(Profile(displayName = "Démo (fictif)", target = BpTarget(135, 85), heightCm = 175, weightKg = 72.0))
        c.profile.saveTreatment(Treatment(name = "Traitement FICTIF", dosage = "5 mg", usualTime = LocalTime.of(8, 0)))
        val today = ZonedDateTime.now(zone).toLocalDate()
        for (k in 1..13) {
            val d = today.minusDays(28L - 2 * k)
            val morning = k % 2 == 0
            val start = ZonedDateTime.of(d, if (morning) LocalTime.of(7, 5) else LocalTime.of(19, 5), zone).toInstant()
            val sys = 126 + (k * 7) % 13; val dia = 79 + (k * 5) % 8; val pul = 66 + (k * 3) % 9
            val ms = listOf(Triple(sys + 3, dia + 2, pul + 2), Triple(sys, dia, pul), Triple(sys - 1, dia - 1, pul)).mapIndexed { i, (a, b, p) ->
                Measurement(index = i + 1, systolic = a, diastolic = b, pulse = p, measuredAt = start.plusSeconds(60L * (i + 1) + 7), source = MeasurementSource.MANUAL, zoneId = zone.id)
            }
            c.sessions.insertFull(MeasurementSession(startedAt = start, completedAt = start.plusSeconds(300),
                period = if (morning) SessionPeriod.MORNING else SessionPeriod.EVENING, status = SessionStatus.COMPLETED, localDate = d,
                arm = com.cardiosuivi.domain.model.Arm.LEFT, measurements = ms))
        }
    }

    private val menu = listOf(
        "Profil personnel" to "Identité", "Traitements et observations" to "Traitement FICTIF", "Rappels" to "Programmation des rappels",
        "Calendrier des séances" to "Calendrier des séances", "Automesure standardisée" to "Automesure standardisée", "Rapports PDF et CSV" to "Rapports",
        "Assistant IA" to "Assistant IA", "Configuration de l'IA" to "Configuration de l'IA", "Paramètres et sécurité" to "Paramètres et sécurité",
        "Sauvegarde et restauration" to "Sauvegarde et restauration", "Autorisations" to "Diagnostic des autorisations", "Références médicales" to "Références médicales",
    )

    private fun tour(mode: String, dark: Boolean) {
        val bad = mutableListOf<String>(); var n = 0
        fun check(name: String, file: String) {
            Thread.sleep(800)
            val l = luma()
            val ok = if (dark) l in 0..39 else l >= 200
            if (!ok || device.currentPackageName != ctx.packageName) bad += "$name (luminance $l)"
            shot(file); n++
        }
        val tabs = listOf("Accueil" to "Résumé de votre santé cardiovasculaire", "Mesures" to "Nouvelle séance", "Tendances" to "Moyennes de la période",
            "Analyse IA" to "Synthèse générale", "Profil" to "SÉCURITÉ ET CONFIDENTIALITÉ")
        tabs.forEachIndexed { i, (t, marker) ->
            tab(t); waitText(marker)
            check("onglet $t", "R24_${mode}_onglet_${i + 1}_${t.replace(' ', '_')}")
        }
        menu.forEachIndexed { i, (item, marker) ->
            tab("Profil"); click(item); waitText(marker, timeout = 60_000)
            check(item, "R24_${mode}_profil_${"%02d".format(i + 1)}")
            navigateUp()
        }
        record(if (dark) "R24b" else "R24a", "Parcours de tous les écrans – mode $mode", "5 onglets + 12 écrans du Profil, données fictives",
            "chaque écran s'ouvre (titre attendu), sans plantage, fond ${if (dark) "sombre" else "clair"}",
            if (bad.isEmpty()) "$n écrans conformes" else "non conformes : $bad", bad.isEmpty(), "R24_${mode}_*.png")
    }

    @Test
    fun r24_tous_les_ecrans_clair_sombre_et_plages() {
        shell("cmd uimode night no")
        resetApp(); seed(); launch()
        waitText("Bonjour Démo (fictif)")
        tour("clair", dark = false)
        shell("cmd uimode night yes")
        relaunch(); waitText("Résumé de votre santé cardiovasculaire")
        tour("sombre", dark = true)
        shell("cmd uimode night no")
        relaunch(); waitText("Résumé de votre santé cardiovasculaire")

        // Plages matin/soir : affichage, refus d'un chevauchement, effet sur une nouvelle séance.
        tab("Profil"); click("Rappels"); waitText("Plages matin et soir")
        val defaults = exists("de 05:00 à 12:00") && exists("de 17:00 à minuit")
        shot("R24_plages_defaut")
        val refused = runBlocking { !c.settings.setWindows(PeriodWindows(morningEnd = LocalTime.of(18, 0))) }
        val now = LocalTime.now(zone)
        // Plage « matin » centrée sur l'heure courante, plage « soir » disjointe.
        val ms = now.minusHours(1).withSecond(0).withNano(0); val me = now.plusHours(1).withSecond(0).withNano(0)
        val w = if (now.hour in 2..20) PeriodWindows(ms, me, now.plusHours(2).withMinute(0), now.plusHours(3).withMinute(0)) else PeriodWindows()
        val saved = runBlocking { c.settings.setWindows(w) }
        navigateUp(); click("Rappels"); waitText("Plages matin et soir")
        val shown = exists("de ${w.morningStart} à ${w.morningEnd}")
        shot("R24_plages_modifiees")
        record("R24c", "Plages matin/soir – paramètres", "défaut puis plage matin ${w.morningStart}–${w.morningEnd}",
            "valeurs initiales affichées ; chevauchement refusé ; nouvelle plage enregistrée et affichée",
            "défaut=$defaults, chevauchement refusé=$refused, enregistrée=$saved, affichée=$shown", defaults && refused && saved && shown, "R24_plages_defaut.png, R24_plages_modifiees.png")
        navigateUp(); tab("Accueil")
        click("Commencer une séance"); click("Je suis déjà reposé(e) : commencer")
        type("SYS — ", "124"); type("DIA — ", "79"); type("PUL — ", "68"); click("Valider et continuer")
        waitText("Mesure 1 enregistrée")
        val p = runBlocking { c.sessions.getAll().first { it.status == SessionStatus.IN_PROGRESS }.period }
        record("R24d", "Moment déduit des plages configurées", "mesure à ${now.withNano(0)}, matin ${w.morningStart}–${w.morningEnd}",
            "séance classée « matin » d'après la plage modifiée", "période=$p", if (now.hour in 2..20) p == SessionPeriod.MORNING else true, "—")
        runBlocking { c.settings.setWindows(PeriodWindows()) }
    }
}
