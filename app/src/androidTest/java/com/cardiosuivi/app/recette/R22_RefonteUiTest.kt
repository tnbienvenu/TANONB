package com.cardiosuivi.app.recette

import android.graphics.Bitmap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardiosuivi.domain.model.BpTarget
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.MeasurementSource
import com.cardiosuivi.domain.model.Profile
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Refonte graphique 1.1 : captures des écrans principaux (clair, sombre, grande police) et contrôles
 * d'affichage et d'accessibilité.
 *
 * Données FICTIVES de démonstration (profil « Démo – données fictives ») : 14 séances sur 28 jours générées par une règle
 * déterministe, dernière séance du jour 131/84/74, 129/83/72, 128/82/72 (dernière mesure 128/82/72).
 */
@RunWith(AndroidJUnit4::class)
class R22_RefonteUiTest : RecetteBase() {

    private val zone = ZoneId.systemDefault()

    private fun seedDemo() = runBlocking {
        c.profile.saveProfile(Profile(displayName = "Démo (fictif)", target = BpTarget(135, 85)))
        val today = ZonedDateTime.now(zone).toLocalDate()
        suspend fun s(daysAgo: Long, period: SessionPeriod, vararg r: Triple<Int, Int, Int>) {
            val d = today.minusDays(daysAgo)
            val start = ZonedDateTime.of(d, if (period == SessionPeriod.MORNING) LocalTime.of(7, 5) else LocalTime.of(19, 5), zone).toInstant()
            val ms = r.mapIndexed { i, (a, b, p) -> Measurement(index = i + 1, systolic = a, diastolic = b, pulse = p, measuredAt = start.plusSeconds(60L * (i + 1)), source = MeasurementSource.MANUAL) }
            c.sessions.insertFull(MeasurementSession(startedAt = start, completedAt = start.plusSeconds(300), period = period, status = SessionStatus.COMPLETED, localDate = d, measurements = ms))
        }
        for (k in 1..13) {
            val days = 28L - 2 * k
            val sys = 126 + (k * 7) % 13
            val dia = 79 + (k * 5) % 8
            val pul = 66 + (k * 3) % 9
            s(days, if (k % 2 == 0) SessionPeriod.MORNING else SessionPeriod.EVENING, Triple(sys + 3, dia + 2, pul + 2), Triple(sys, dia, pul), Triple(sys - 1, dia - 1, pul))
        }
        s(0, SessionPeriod.MORNING, Triple(131, 84, 74), Triple(129, 83, 72), Triple(128, 82, 72))
    }

    private fun shell(cmd: String) {
        instr.uiAutomation.executeShellCommand(cmd).close()
        Thread.sleep(1500)
    }

    /** Luminance moyenne d'une bande en haut de l'écran (fond), pour vérifier le mode sombre. */
    private fun backgroundLuma(): Int {
        val b: Bitmap = instr.uiAutomation.takeScreenshot() ?: return -1
        var sum = 0L; var n = 0
        for (y in b.height / 10 until b.height / 10 + 20) for (x in 4 until 24) {
            val p = b.getPixel(x, y); sum += ((p shr 16 and 0xff) * 299 + (p shr 8 and 0xff) * 587 + (p and 0xff) * 114) / 1000; n++
        }
        return (sum / n).toInt()
    }

    private fun scrollTo(text: String) { runCatching { node(text, substring = true).performScrollTo() }; compose.waitForIdle() }

    @Test
    fun r22_ecrans_principaux_clair_sombre_police() {
        shell("cmd uimode night no")
        resetApp(); seedDemo(); launch()

        // --- Tableau de bord ---
        waitText("Bonjour Démo (fictif)"); waitText("Moyenne de référence de la séance"); Thread.sleep(1500)
        shot("UI_01_accueil_haut")
        val lastOk = exists("Dernière séance") && exists("Bonjour Démo (fictif)") && exists("128", false) && exists("82", false) && exists("72", false)
        record("R22a", "Accueil – dernière tension", "séance fictive 131/84/74, 129/83/72, 128/82/72", "en-tête « Bonjour », dernière mesure 128 / 82 / 72 en grand",
            if (lastOk) "affichée" else "absente", lastOk, "UI_01_accueil_haut.png")
        val mean = exists("Moyenne de référence de la séance : 129/83 mmHg")
        record("R22b", "Accueil – moyenne de séance", "(131+129+128)/3 = 129,3 ; (84+83+82)/3 = 83", "« Moyenne de référence de la séance : 129/83 mmHg »", if (mean) "conforme" else "non conforme", mean, "UI_01_accueil_haut.png")
        val cd = runCatching { compose.onAllNodes(hasContentDescription("Systolique : 128 mmHg")).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        record("R22c", "Accessibilité – valeurs lues par TalkBack", "bloc systolique", "description « Systolique : 128 mmHg » (libellé + valeur + unité)", if (cd) "présente" else "absente", cd, "—")
        scrollTo("Évolution tensionnelle"); shot("UI_02_accueil_evolution")
        scrollTo("Prochaine séance"); shot("UI_03_accueil_prochaine_seance")
        val btn = runCatching { compose.onAllNodes(hasText("Commencer une séance"))[0].assertHeightIsAtLeast(48.dp); true }.getOrDefault(false)
        record("R22d", "Accessibilité – cible tactile", "bouton « Commencer une séance »", "hauteur ≥ 48 dp", if (btn) "≥ 48 dp" else "< 48 dp", btn, "UI_03_accueil_prochaine_seance.png")
        scrollTo("Analyse"); shot("UI_04_accueil_tendances_analyse")

        // --- Mesures ---
        tab("Mesures"); waitText("Nouvelle séance"); shot("UI_05_mesures")
        val hist = exists("129/83", false) && exists("Complète")
        record("R22e", "Mesures – historique groupé par date", "14 séances fictives", "séances groupées par jour, valeur 129/83 et badge « Complète »", if (hist) "conforme" else "non conforme", hist, "UI_05_mesures.png")
        click("129/83", substring = true); waitText("Valeur de séance"); shot("UI_06_detail_seance"); back()

        // --- Tendances ---
        tab("Tendances"); waitText("Moyennes de la période"); shot("UI_07_tendances")
        scrollTo("Fréquence cardiaque"); shot("UI_08_tendances_pouls")
        scrollTo("Matin et soir"); shot("UI_09_tendances_matin_soir")
        val seg = exists("1 an", false) && exists("90 jours")
        record("R22f", "Tendances – filtres de période", "contrôle segmenté", "7 jours | 30 jours | 90 jours | 1 an | Tout", if (seg) "présents" else "absents", seg, "UI_07_tendances.png")

        // --- Analyse IA ---
        tab("Analyse IA"); waitText("Synthèse générale"); shot("UI_10_analyse")
        val cards = listOf("1. Synthèse générale", "2. Évolution de la tension", "3. Analyse systolique", "4. Analyse diastolique", "5. Fréquence cardiaque",
            "6. Comparaison matin / soir", "7. Points de vigilance", "8. Recommandations générales", "9. Questions pour le médecin", "Interprétation par l'IA").all { exists(it) }
        val local = exists("Calcul local · sans IA") && exists("Générée par une IA · facultative")
        record("R22g", "Analyse IA – 9 cartes + IA séparée", "jeu fictif", "9 cartes calculées localement (badge « Calcul local ») et carte IA distincte",
            "cartes=$cards, badges=$local", cards && local, "UI_10_analyse.png")
        scrollTo("3. Analyse systolique"); shot("UI_11_analyse_systolique")
        scrollTo("Interprétation par l'IA"); shot("UI_12_analyse_ia")

        // --- Profil ---
        tab("Profil"); waitText("Sécurité et confidentialité".uppercase()); shot("UI_13_profil")
        click("Profil personnel"); waitText("Identité"); shot("UI_14_profil_formulaire"); navigateUp()

        // --- Séance guidée : étape, saisie, chronomètre, récapitulatif ---
        tab("Accueil"); click("Commencer une séance")
        waitText("Avant de commencer"); shot("UI_15_seance_preparation")
        click("Je suis déjà reposé(e) : commencer")
        waitText("MESURE 1 SUR 3"); shot("UI_16_seance_saisie")
        type("SYS — ", "128"); type("DIA — ", "82"); type("PUL — ", "72"); hideKeyboard()
        shot("UI_16b_seance_saisie_remplie")
        click("Valider et continuer")
        waitText("Mesure 1 enregistrée"); Thread.sleep(3000); shot("UI_17_seance_chronometre")
        val step = exists("MESURE 2 SUR 3")
        record("R22h", "Séance guidée – progression", "mesure 1 validée", "indicateur « MESURE 2 SUR 3 » et chronomètre circulaire", if (step) "affiché" else "absent", step, "UI_17_seance_chronometre.png")
        click("Arrêter ici")
        waitText("Récapitulatif des mesures"); shot("UI_18_seance_recapitulatif")
        // Abandon de la séance de démonstration (non enregistrée).
        back(); runCatching { click("Abandonner") }

        // --- Mode sombre ---
        shell("cmd uimode night yes")
        relaunch(); waitText("Résumé de votre santé cardiovasculaire"); Thread.sleep(1500)
        val luma = backgroundLuma()
        shot("UI_19_accueil_sombre")
        record("R22i", "Mode sombre", "cmd uimode night yes", "fond sombre (luminance < 40) et textes lisibles", "luminance du fond $luma", luma in 0..39, "UI_19_accueil_sombre.png")
        tab("Tendances"); waitText("Moyennes de la période"); scrollTo("Tension artérielle"); shot("UI_20_tendances_sombre")
        tab("Analyse IA"); waitText("Synthèse générale"); shot("UI_21_analyse_sombre")
        tab("Mesures"); waitText("Nouvelle séance"); shot("UI_22_mesures_sombre")
        shell("cmd uimode night no")

        // --- Grande police (accessibilité) ---
        shell("settings put system font_scale 1.3")
        relaunch(); waitText("Résumé de votre santé cardiovasculaire"); Thread.sleep(1500)
        shot("UI_23_accueil_police_130")
        scrollTo("Commencer une séance")
        val big = exists("Commencer une séance") && exists("128", false)
        shot("UI_24_police_130_bouton")
        record("R22j", "Taille de police 130 %", "font_scale 1.3", "valeurs et bouton principal toujours affichés", if (big) "affichés" else "manquants", big, "UI_23_accueil_police_130.png")
        shell("settings put system font_scale 1.0")
    }
}
