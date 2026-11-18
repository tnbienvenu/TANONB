package com.cardiosuivi.app.recette

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardiosuivi.domain.entry.localDateTime
import com.cardiosuivi.domain.model.Arm
import com.cardiosuivi.domain.model.MeasurementSource
import com.cardiosuivi.domain.model.PeriodWindows
import com.cardiosuivi.domain.model.SessionStatus
import com.cardiosuivi.domain.util.Fmt
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

/**
 * Exigences 1.1.0 : saisie manuelle seule (SYS, DIA, PUL), bras gauche et période automatiques, horodatage automatique
 * (secondes, fuseau), retour à la mesure précédente, correction sans changer l'horodatage, confirmation d'abandon,
 * persistance après fermeture, fuseau horaire. Valeurs FICTIVES.
 */
@RunWith(AndroidJUnit4::class)
class R23_SaisieSimplifieeTest : RecetteBase() {

    private fun shell(cmd: String): String =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instr.uiAutomation.executeShellCommand(cmd)).bufferedReader().readText()

    private fun enter(sys: String, dia: String, pul: String) {
        waitText("SYS — Pression systolique")
        type("SYS — ", sys); type("DIA — ", dia); type("PUL — ", pul)
        click("Valider et continuer")
    }

    private fun sessions() = runBlocking { c.sessions.getAll() }

    @Test
    fun r23_saisie_horodatage_correction() {
        resetApp(); launch()
        click("Commencer une séance")
        waitText("Avant de commencer")
        shot("R23_preparation")
        val noChoice = !exists("Bras droit") && !exists("Non précisé") && !exists("Moment", false) && !exists("Soir", false) && !exists("Autre", false)
        record("R23a", "Préparation – aucun choix de bras ni de période", "écran de préparation", "pas de sélection Bras gauche/droit ni Matin/Soir/Autre",
            if (noChoice) "aucun sélecteur" else "sélecteur présent", noChoice, "R23_preparation.png")
        click("Je suis déjà reposé(e) : commencer")
        waitText("MESURE 1 SUR 3")
        val noOcr = !exists("Photo") && !exists("Importer") && !exists("Saisie manuelle") && !exists("Analyser la zone") && !exists("photo")
        val fields = exists("SYS — Pression systolique") && exists("DIA — Pression diastolique") && exists("PUL — Fréquence cardiaque") &&
            exists("mmHg") && exists("bpm") && exists("Date et heure enregistrées automatiquement")
        shot("R23_formulaire")
        record("R23b", "Formulaire – trois champs, aucun OCR", "mesure 1", "SYS, DIA, PUL avec unités ; pas de photo/import ; pas de champ date/heure",
            "champs=$fields, OCR absent=$noOcr", fields && noOcr && !exists("Date de la mesure") && !exists("Heure de la mesure"), "R23_formulaire.png")

        val t0 = Instant.now()
        enter("131", "84", "74")
        waitText("Mesure 1 enregistrée")
        val t1 = Instant.now()
        val s1 = sessions().single()
        val m1 = s1.measurements.single()
        val zone = ZoneId.systemDefault()
        val expectedPeriod = runBlocking { c.settings.current().windows }.periodOf(m1.measuredAt.atZone(zone).toLocalTime())
        val stampOk = !m1.measuredAt.isBefore(t0.minusSeconds(1)) && !m1.measuredAt.isAfter(t1.plusSeconds(1)) && m1.zoneId == zone.id &&
            m1.utcOffsetSeconds == zone.rules.getOffset(m1.measuredAt).totalSeconds && m1.modifiedAt == null && m1.source == MeasurementSource.MANUAL
        record("R23c", "Horodatage automatique", "validation de la mesure 1 entre $t0 et $t1",
            "heure du téléphone à la validation (ms), fuseau ${zone.id}, décalage UTC, sans date de correction",
            "measuredAt=${m1.measuredAt}, zoneId=${m1.zoneId}, offset=${m1.utcOffsetSeconds}", stampOk, "—")
        record("R23d", "Bras gauche et période automatiques", "nouvelle séance", "bras = gauche ; période = plage contenant ${Fmt.timeSeconds(m1.measuredAt.atZone(zone))} ($expectedPeriod)",
            "bras=${s1.arm}, période=${s1.period}", s1.arm == Arm.LEFT && s1.period == expectedPeriod, "—")

        // Retour à la mesure précédente depuis la saisie de la mesure 2.
        click("Mesurer maintenant")
        waitText("MESURE 2 SUR 3")
        click("Revenir à la mesure 1")
        waitText("Corriger la mesure 1")
        shot("R23_retour_mesure_1")
        type("SYS — ", "130")
        click("Enregistrer la correction")
        waitText("Valeurs de la mesure 2")
        val m1b = sessions().single().measurements.single()
        val fixOk = m1b.systolic == 130 && m1b.measuredAt == m1.measuredAt && m1b.modifiedAt != null && m1b.modifiedAt!! > m1.measuredAt
        record("R23e", "Retour à la mesure précédente et correction", "mesure 2 → « Revenir à la mesure 1 », SYS 131 → 130",
            "valeur corrigée, horodatage d'origine inchangé, date de correction distincte, retour à la saisie de la mesure 2",
            "sys=${m1b.systolic}, measuredAt=${m1b.measuredAt} (orig. ${m1.measuredAt}), modifiedAt=${m1b.modifiedAt}", fixOk && exists("MESURE 2 SUR 3"), "R23_retour_mesure_1.png")

        enter("129", "83", "72")
        waitText("Mesure 2 enregistrée")
        click("Mesurer maintenant")
        enter("128", "82", "72")
        waitText("Récapitulatif des mesures", timeout = 90_000)
        val ms = sessions().single().measurements.sortedBy { it.index }
        val distinct = ms.map { it.measuredAt }.zipWithNext().all { (a, b) -> a < b }
        val times = ms.all { exists("à ${Fmt.timeSeconds(it.localDateTime())}") }
        shot("R23_recapitulatif")
        record("R23f", "Récapitulatif – heures de chaque mesure", "3 mesures", "3 horodatages distincts et croissants, affichés à la seconde (HH:mm:ss)",
            "distincts=$distinct, affichés=$times : ${ms.joinToString { Fmt.timeSeconds(it.localDateTime()) }}", distinct && times, "R23_recapitulatif.png")
        // Moyennes : (130+129+128)/3 = 129 ; (84+83+82)/3 = 83 ; (74+72+72)/3 = 72,7
        val avg = exists("129,0/83,0 · 72,7")
        record("R23g", "Récapitulatif – moyennes", "130/84/74, 129/83/72, 128/82/72", "moyenne des 3 : 129,0/83,0 · 72,7", if (avg) "conforme" else "non conforme", avg, "R23_recapitulatif.png")

        // Correction de la mesure 3 avant clôture.
        val m3 = ms.last()
        compose.onAllNodes(hasContentDescription("Corriger"))[2].let { n -> runCatching { n.performScrollTo() }; n.performClick() }; compose.waitForIdle()
        waitText("Corriger la mesure 3")
        type("PUL — ", "71")
        click("Enregistrer la correction")
        waitText("Récapitulatif des mesures")
        val m3b = sessions().single().measurements.maxBy { it.index }
        record("R23h", "Correction avant clôture", "mesure 3 : PUL 72 → 71", "valeur corrigée, horodatage inchangé, retour au récapitulatif",
            "pul=${m3b.pulse}, measuredAt inchangé=${m3b.measuredAt == m3.measuredAt}", m3b.pulse == 71 && m3b.measuredAt == m3.measuredAt && m3b.modifiedAt != null, "—")

        click("Valider la séance")
        waitText("Séance enregistrée", "Terminer", timeout = 60_000)
        click("Terminer")
        val before = sessions().single()
        // Fermeture / relance de l'application puis consultation du détail.
        relaunch()
        tab("Mesures"); waitText("Complète")
        click("129/83", substring = true); waitText("Valeur de séance")
        shot("R23_detail")
        val after = sessions().single()
        val persisted = before.measurements.map { it.measuredAt to it.modifiedAt } == after.measurements.map { it.measuredAt to it.modifiedAt } &&
            after.status == SessionStatus.COMPLETED
        val shown = after.measurements.all { exists("Mesurée à ${Fmt.timeSeconds(it.localDateTime())}") } && exists("Bras gauche")
        record("R23i", "Horodatage – fermeture puis consultation", "relance de l'application, ouverture du détail", "horodatages et dates de correction identiques ; bras gauche mentionné discrètement",
            "identiques=$persisted, affichés=$shown", persisted && shown, "R23_detail.png")
        back()

        // Confirmation avant abandon d'une séance contenant des valeurs saisies.
        tab("Accueil"); click("Commencer une séance"); click("Je suis déjà reposé(e) : commencer")
        waitText("SYS — Pression systolique")
        type("SYS — ", "140")
        back()
        waitText("Quitter la séance ?")
        val warn = exists("Les valeurs saisies mais non validées seront perdues.")
        shot("R23_abandon")
        click("Abandonner")
        waitText("Commencer une séance")
        record("R23j", "Confirmation avant abandon", "valeur saisie puis retour", "dialogue de confirmation signalant la perte des valeurs ; après « Abandonner », rien n'est enregistré",
            "avertissement=$warn, séances=${sessions().size}", warn && sessions().size == 1, "R23_abandon.png")
    }

    @Test
    fun r23_fuseau_horaire() {
        resetApp(); launch()
        val original = ZoneId.systemDefault().id
        click("Commencer une séance"); click("Je suis déjà reposé(e) : commencer")
        enter("126", "80", "70")
        waitText("Mesure 1 enregistrée")
        click("Arrêter ici"); click("Enregistrer la séance incomplète"); waitText("Terminer"); click("Terminer")
        val m = sessions().single().measurements.single()
        val localOrig = Fmt.timeSeconds(m.measuredAt.atZone(ZoneId.of(original)))
        val target = if (original == "Asia/Tokyo") "America/New_York" else "Asia/Tokyo"
        shell("cmd alarm set-timezone $target")
        Thread.sleep(5000)
        val changed = ZoneId.systemDefault().id
        try {
            if (changed != target) {
                record("R23k", "Changement de fuseau horaire", "cmd alarm set-timezone $target", "heure d'origine conservée", "fuseau non modifiable sur cet appareil ($changed) : NON EXÉCUTÉ", false, "—")
                return
            }
            relaunch()
            tab("Mesures"); waitText("Incomplète")
            click("126/80", substring = true); waitText("Valeur de séance")
            shot("R23_fuseau_detail")
            val kept = exists("Mesurée à $localOrig")
            record("R23k", "Changement de fuseau horaire", "mesure à $localOrig ($original) puis téléphone en $target",
                "heure affichée dans le fuseau d'enregistrement ($localOrig), donnée inchangée",
                "affichée=$kept, zoneId=${m.zoneId}, measuredAt inchangé=${sessions().single().measurements.single().measuredAt == m.measuredAt}", kept, "R23_fuseau_detail.png")
        } finally {
            shell("cmd alarm set-timezone $original")
            Thread.sleep(3000)
        }
    }
}
