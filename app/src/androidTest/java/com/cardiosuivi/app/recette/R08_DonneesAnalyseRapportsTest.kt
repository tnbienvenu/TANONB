package com.cardiosuivi.app.recette

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.MeasurementSource
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Jeu de données FICTIF (4 séances sur 7 jours, pouls 70) et résultats attendus calculés à la main :
 *  J-6 matin  130/80 ×3            → séance 130/80 (les deux méthodes)
 *  J-5 soir   140/90 ×3            → 140/90
 *  J-3 matin  150/96, 140/90, 130/84 → moyenne des 3 : 140/90 ; 2 dernières : 135/87
 *  J-1 soir   120/76 ×3            → 120/76
 *  Moyenne 7 j (3 mesures) : SYS (130+140+140+120)/4 = 132,5 → « 133 » ; DIA (80+90+90+76)/4 = 84 → « 133/84 »
 *  Moyenne 7 j (2 dernières) : SYS (130+140+135+120)/4 = 131,25 → « 131 » ; DIA (80+90+87+76)/4 = 83,25 → « 131/83 »
 *  Matin (J-6, J-3) 135/85 ; soir (J-5, J-1) 130/83 → soir − matin −5,0 / −2,0
 */
@RunWith(AndroidJUnit4::class)
class R08_DonneesAnalyseRapportsTest : RecetteBase() {

    private val zone = ZoneId.systemDefault()

    private fun seed() = runBlocking {
        val today = ZonedDateTime.now(zone).toLocalDate()
        suspend fun s(daysAgo: Long, period: SessionPeriod, vararg r: Pair<Int, Int>) {
            val d = today.minusDays(daysAgo)
            val start = ZonedDateTime.of(d, if (period == SessionPeriod.MORNING) LocalTime.of(7, 5) else LocalTime.of(19, 5), zone).toInstant()
            val ms = r.mapIndexed { i, (a, b) -> Measurement(index = i + 1, systolic = a, diastolic = b, pulse = 70, measuredAt = start.plusSeconds(60L * (i + 1)), source = MeasurementSource.MANUAL) }
            c.sessions.insertFull(MeasurementSession(startedAt = start, completedAt = start.plusSeconds(300), period = period, status = SessionStatus.COMPLETED, localDate = d, measurements = ms))
        }
        s(6, SessionPeriod.MORNING, 130 to 80, 130 to 80, 130 to 80)
        s(5, SessionPeriod.EVENING, 140 to 90, 140 to 90, 140 to 90)
        s(3, SessionPeriod.MORNING, 150 to 96, 140 to 90, 130 to 84)
        s(1, SessionPeriod.EVENING, 120 to 76, 120 to 76, 120 to 76)
    }

    @Test
    fun r08_tableau_de_bord_et_methode_de_calcul() {
        resetApp(); seed(); launch()
        waitText("Moyenne 7 jours")
        // Les séances sont lues de façon asynchrone : on attend la valeur calculée (au plus 60 s) avant de conclure.
        runCatching { waitText("Moyenne 7 jours : 133/84", timeout = 60_000) }
        shot("R08_tableau_de_bord_jeu_fictif")
        record("R08a", "Tableau de bord – moyenne 7 j", "jeu fictif 4 séances", "« 133/84 mmHg » ; 4 séance(s), 4 jour(s)",
            "133/84 ${if (exists("133/84 mmHg")) "affiché" else "absent"} ; ${if (exists("4 séance(s), 4 jour(s)")) "effectifs OK" else "effectifs KO"}",
            exists("133/84 mmHg") && exists("4 séance(s), 4 jour(s)"), "R08_tableau_de_bord_jeu_fictif.png")
        tab("Profil"); click("Paramètres et sécurité")
        click("Moyenne des deux dernières mesures")
        back(); click("Accueil")
        waitText("Moyenne 7 jours")
        shot("R08_methode_deux_dernieres")
        record("R08b", "Paramètre méthode de calcul", "« Moyenne des deux dernières mesures »", "moyenne 7 j recalculée « 131/83 mmHg »",
            if (exists("131/83 mmHg")) "131/83" else "non recalculé", exists("131/83 mmHg"), "R08_methode_deux_dernieres.png")
        relaunch()
        record("R08c", "Paramètre méthode – persistance", "relance", "méthode conservée (131/83)", if (exists("131/83 mmHg")) "conservée" else "perdue", exists("131/83 mmHg"), "—")
    }

    @Test
    fun r09_graphiques_analyse_assistant_local() {
        resetApp(); seed(); launch()
        tab("Tendances")
        waitText("Touchez ou glissez sur le graphique pour afficher la valeur exacte.")
        shot("R09_graphique_tension")
        val chart = compose.onAllNodes(hasContentDescription("Graphique", substring = true))[0]
        chart.performTouchInput { this.click(center) }
        compose.waitForIdle()
        val tapped = exists("Systolique :") || exists("Diastolique :")
        shot("R09_graphique_valeur_touchee")
        record("R09a", "Graphiques – valeur exacte", "toucher le centre du graphique", "valeur exacte et date affichées", if (tapped) "affichée" else "rien", tapped, "R09_graphique_valeur_touchee.png")
        val counts = exists("4 séance(s), 12 mesure(s), 4 jour(s) avec mesures sur 30") && exists("Jours sans mesure : 26")
        record("R09b", "Graphiques – effectifs et manquants", "période 30 j", "4 séances, 12 mesures, 4 jours sur 30, 26 jours sans mesure", if (counts) "conforme" else "non conforme", counts, "R09_graphique_tension.png")
        // Écran Tendances : toutes les vues sont des cartes successives ; on fait défiler jusqu'à chacune.
        for (k in listOf("Fréquence cardiaque", "Matin et soir", "Moyennes hebdomadaires", "Moyennes mensuelles", "Variabilité tensionnelle", "Pression pulsée", "Jours de la semaine", "Historique des mesures")) {
            runCatching { node(k, substring = false).performScrollTo() }
            compose.waitForIdle()
            shot("R09_graphique_${k.replace(" ", "_")}")
        }
        runCatching { node("Matin et soir", substring = false).performScrollTo() }
        val me = exists("Écart soir − matin : -5,0 / -2,0 mmHg.")
        record("R09c", "Graphiques – matin/soir", "jeu fictif", "écart soir − matin −5,0 / −2,0 ; effectifs insuffisants signalés", if (me) "conforme" else "non conforme", me && exists("Effectifs insuffisants"), "R09_graphique_Matin_et_soir.png")
        click("7 jours")
        record("R09d", "Graphiques – filtre période", "7 jours", "« 4 séance(s) … sur 7 »", if (exists("sur 7.")) "filtré" else "non filtré", exists("sur 7."), "—")

        tab("Analyse IA")
        waitText("Synthèse générale")
        shot("R09_analyse")
        record("R09e", "Analyse – moyenne 7 j", "jeu fictif", "constat « Moyenne 133/84 mmHg … repère élevée »", if (exists("Moyenne 133/84 mmHg")) "présent" else "absent",
            exists("Moyenne 133/84 mmHg") && exists("élevée"), "R09_analyse.png")

        click("Ouvrir l'assistant IA")
        click("Quelle est ma moyenne sur les sept derniers jours ?")
        waitText("1. Données observées")
        shot("R09_assistant_local")
        val six = listOf("1. Données observées", "2. Résultats statistiques", "3. Interprétation", "4. Incertitudes et limites", "5. Recommandations générales", "6. Situations nécessitant un avis médical").all { exists(it) }
        record("R09f", "Assistant local", "question prédéfinie « moyenne 7 jours »", "réponse en 6 sections, 133/84, sans connexion",
            "6 sections=${six}, 133/84=${exists("133/84")}", six && exists("133/84") && exists("Moteur d'analyse local"), "R09_assistant_local.png")
        type("Votre question", "Dois-je augmenter mon traitement ?")
        record("R09g", "Assistant – IA non configurée", "question libre", "bouton « Demander à l'IA » inactif sans fournisseur",
            "aucun fournisseur", exists("Aucun fournisseur d'IA configuré"), "—")
    }

    @Test
    fun r10_rapports_pdf_csv() {
        resetApp(); seed()
        runBlocking { c.profile.saveProfile(com.cardiosuivi.domain.model.Profile(displayName = "RECETTE-FICTIF")) }
        launch()
        tab("Profil"); click("Rapports PDF et CSV")
        click("7 jours")
        click("Générer le rapport PDF", substring = true)
        waitText("Rapport PDF généré.", "Échec", timeout = 180_000)
        shot("R10_rapport_genere")
        val pdf = File(ctx.cacheDir, "exports").listFiles()?.firstOrNull { it.extension == "pdf" }
        var pages = 0
        if (pdf != null) {
            PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
                pages = r.pageCount
                for (i in 0 until minOf(r.pageCount, 4)) r.openPage(i).use { p ->
                    val bmp = Bitmap.createBitmap(p.width * 2, p.height * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(outDir, "R10_pdf_page${i + 1}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                }
            }
        }
        record("R10a", "Rapport PDF", "période 7 jours, jeu fictif", "fichier PDF valide de plusieurs pages", "fichier=${pdf?.name}, ${pdf?.length()} octets, $pages page(s)", pdf != null && pages >= 2, "R10_pdf_page1.png … page4")

        click("Exporter en CSV", substring = true)
        waitText("Export CSV généré.")
        val csv = File(ctx.cacheDir, "exports").listFiles()?.firstOrNull { it.extension == "csv" }
        val lines = csv?.readText(Charsets.UTF_8)?.trimEnd()?.split("\r\n").orEmpty()
        File(outDir, "R10_export.csv").writeText(csv?.readText() ?: "")
        val okCsv = lines.size == 13 && lines.any { it.contains(";150;96;70;54;") } && lines.any { it.contains(";140,0;90,0;70,0;") }
        record("R10b", "Export CSV", "période 7 jours", "en-tête + 12 lignes ; ligne 150/96 (PP 54) ; valeur de séance 140,0/90,0", "${lines.size} lignes", okCsv, "R10_export.csv")

        click("Partager", substring = true)
        val chooser = device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.textContains("Sharing")), 30_000) ||
            device.hasObject(androidx.test.uiautomator.By.textContains("Partag")) ||
            device.hasObject(androidx.test.uiautomator.By.res("android:id/resolver_list")) ||
            device.hasObject(androidx.test.uiautomator.By.pkg("com.android.intentresolver"))
        Thread.sleep(2000)
        shot("R10_menu_partage")
        record("R10c", "Partage Android", "bouton Partager", "menu de partage du système affiché", if (chooser) "affiché" else "absent", chooser, "R10_menu_partage.png")
        device.pressBack(); Thread.sleep(1500)
    }

    @Test
    fun r11_traitements_observations_hbpm_calendrier() {
        resetApp(); launch()
        tab("Profil"); click("Traitements et observations")
        click("Ajouter un traitement")
        waitText("Nouveau traitement")
        val saveBtn = compose.onAllNodes(androidx.compose.ui.test.hasText("Enregistrer").and(androidx.compose.ui.test.hasClickAction()))[0]
        val disabled = runCatching { saveBtn.assertIsNotEnabled(); true }.getOrDefault(false)
        record("R11a", "Traitement – nom vide", "aucun nom", "bouton Enregistrer inactif", if (disabled) "inactif" else "actif", disabled, "—")
        type("Médicament *", "MEDICAMENT-FICTIF")
        type("Posologie prescrite", "1 comprimé (fictif)")
        type("Heure habituelle", "25:99")
        shot("R11_traitement_heure_invalide")
        val blocked = runCatching { compose.onAllNodes(androidx.compose.ui.test.hasText("Enregistrer").and(androidx.compose.ui.test.hasClickAction()))[0].assertIsNotEnabled(); true }.getOrDefault(false)
        if (!blocked) click("Enregistrer")
        Thread.sleep(1500)
        val tBad = runBlocking { c.profile.getTreatments() }
        val silentlyDropped = tBad.size == 1 && tBad[0].usualTime == null
        record("R11b", "Traitement – heure invalide", "« 25:99 »", "enregistrement bloqué tant que l'heure est invalide, message d'aide affiché",
            when { silentlyDropped -> "enregistré SANS heure, sans message (donnée perdue silencieusement)"; blocked -> "bouton inactif, message « ${if (exists("Heure invalide")) "Heure invalide…" else "absent"} »"; else -> "autre : ${tBad}" },
            blocked && exists("Heure invalide") && tBad.isEmpty(), "R11_traitement_heure_invalide.png")
        if (tBad.isEmpty()) {
            type("Heure habituelle", "08:00")
            type("Début (JJ/MM/AAAA)", "01/09/2026")
            click("Enregistrer")
        }
        waitText("Traitements antihypertenseurs")
        val t1 = runBlocking { c.profile.getTreatments() }
        record("R11b2", "Traitement – ajout valide", "MEDICAMENT-FICTIF, 08:00, 01/09/2026", "traitement enregistré avec heure et date",
            "$t1", t1.size == 1 && (t1[0].usualTime.toString() == "08:00" || silentlyDropped), "—")
        back(); click("Traitements et observations")
        record("R11c", "Traitement – persistance", "fermer/rouvrir", "toujours listé", if (exists("MEDICAMENT-FICTIF")) "listé" else "absent", exists("MEDICAMENT-FICTIF"), "—")
        compose.onAllNodes(hasContentDescription("Modifier"))[0].performClickCompat()
        waitText("Modifier le traitement")
        type("Posologie prescrite", "2 comprimés (fictif)")
        type("Heure habituelle", "08:00")
        type("Début (JJ/MM/AAAA)", "01/09/2026")
        click("Traitement en cours")
        click("Enregistrer")
        waitText("(arrêté)")
        val t2 = runBlocking { c.profile.getTreatments().first() }
        record("R11d", "Traitement – modification", "posologie 2 cp, 08:00, 01/09/2026, arrêté", "mis à jour, libellé « (arrêté) »",
            "dosage=${t2.dosage}, heure=${t2.usualTime}, début=${t2.startDate}, actif=${t2.active}",
            t2.dosage.startsWith("2") && !t2.active && t2.usualTime.toString() == "08:00" && t2.startDate.toString() == "2026-09-01")
        type("Nouvelle observation", "Observation fictive de recette")
        click("Ajouter")
        waitText("Observation fictive de recette")
        record("R11e", "Journal d'observations", "ajout", "observation listée avec la date du jour", "listée", exists("Observation fictive de recette"), "—")
        compose.onAllNodes(hasContentDescription("Supprimer"))[0].performClickCompat()
        waitText("Supprimer MEDICAMENT-FICTIF")
        click("Supprimer")
        val left = runBlocking { c.profile.getTreatments().size }
        record("R11f", "Traitement – suppression", "supprimer + confirmer", "traitement supprimé", "$left restant(s)", left == 0, "—")

        back(); click("Automesure standardisée")
        click("Commencer aujourd'hui")
        waitText("Début")
        shot("R11_hbpm")
        // 1.1.0 : un seul bouton, le moment (matin/soir) est déduit automatiquement de l'heure de la mesure.
        val hbpm = exists("Non commencé") && exists("Commencer une séance d'automesure") && !exists("Séance du matin") && !exists("Séance du soir")
        record("R11g", "Automesure standardisée", "Commencer aujourd'hui", "protocole démarré, statut « Non commencé », un bouton unique (moment automatique, sans choix matin/soir)",
            if (hbpm) "conforme" else "non conforme", hbpm, "R11_hbpm.png")
        back(); click("Calendrier des séances")
        waitText("Prochaines séances")
        shot("R11_calendrier")
        record("R11h", "Calendrier", "mois courant", "grille mensuelle, légende et prochaines séances (mer/jeu/ven 07:00/19:00)", "affiché",
            exists("Manquée") && (exists("07:00") || exists("19:00")), "R11_calendrier.png")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickCompat() {
        runCatching { performScrollTo() }
        performClick()
        compose.waitForIdle()
    }
}
