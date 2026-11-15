package com.cardiosuivi.app.recette

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardiosuivi.app.ocr.CropQuad
import com.cardiosuivi.app.ocr.ImageLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class R18_OcrEcransTest : RecetteBase() {

    private fun shell(cmd: String): String =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instr.uiAutomation.executeShellCommand(cmd)).bufferedReader().readText()

    /**
     * Jeu de référence déposé sur l'appareil (non versionné) : <files>/ocr_ref/<sys>_<dia>_<pouls>__<origine>.png
     * Moteur complet de l'application (lecteur 7 segments + ML Kit embarqué), exécuté sur Android.
     * Par champ : correct (valeur exacte), erreur (valeur fausse), rejet (aucune valeur) ;
     * erreur non signalée = valeur fausse présentée sans marque « à vérifier ».
     */
    @Test
    fun r18_ocr_jeu_de_reference() {
        val dir = File(ctx.getExternalFilesDir(null), "ocr_ref")
        val files = dir.listFiles { f -> f.extension == "png" || f.extension == "jpg" }?.sortedBy { it.name }.orEmpty()
        assumeTrue("jeu de référence absent", files.isNotEmpty())
        val rows = mutableListOf("fichier\torigine\tattendu\tlu\tincertains\tdurée_ms")
        data class Acc(var correct: Int = 0, var error: Int = 0, var reject: Int = 0, var silentError: Int = 0, var fullCorrect: Int = 0, var n: Int = 0)
        val acc = mutableMapOf<String, Acc>()
        for (f in files) {
            val truth = f.name.substringBefore("__").split("_").map { it.toInt() }
            val origin = if (f.name.contains("__reel")) "réel" else "synthétique"
            val bmp = ImageLoader.load(f) ?: BitmapFactory.decodeFile(f.path)
            val t0 = System.currentTimeMillis()
            val r = runBlocking { c.ocr.recognize(bmp, CropQuad.FULL).reading }
            val ms = System.currentTimeMillis() - t0
            val a = acc.getOrPut(origin) { Acc() }
            a.n++
            val fields = listOf(r.systolic, r.diastolic, r.pulse)
            fields.forEachIndexed { i, fld ->
                when {
                    fld.value == null -> a.reject++
                    fld.value == truth[i] -> a.correct++
                    else -> { a.error++; if (!fld.uncertain) a.silentError++ }
                }
            }
            if (fields.map { it.value } == truth) a.fullCorrect++
            rows += listOf(f.name, origin, truth.joinToString("/"), fields.joinToString("/") { (it.value ?: "∅").toString() },
                fields.count { it.uncertain }.toString(), ms.toString()).joinToString("\t")
        }
        File(outDir, "R18_ocr_resultats.tsv").writeText(rows.joinToString("\n"))
        for ((origin, a) in acc) {
            val tot = 3 * a.n
            fun pct(x: Int) = String.format("%.1f %%", 100.0 * x / tot)
            record("R18-$origin", "OCR – jeu de référence ($origin)", "${a.n} images × 3 champs",
                "aucune erreur non signalée ; taux de lecture correcte élevé",
                "correct ${pct(a.correct)} · erreur ${pct(a.error)} (dont non signalée ${a.silentError}) · rejet ${pct(a.reject)} · images entièrement exactes ${a.fullCorrect}/${a.n}",
                a.silentError == 0, "R18_ocr_resultats.tsv")
        }
    }

    /** Parcours utilisateur complet avec la vraie photo : import via le sélecteur de photos Android puis validation. */
    @Test
    fun r19_parcours_import_photo() {
        val photo = File(ctx.getExternalFilesDir(null), "ocr_ref/125_85_78__reel_photo_entiere.png")
        assumeTrue(photo.exists())
        // Dépôt dans la galerie (MediaStore) pour le sélecteur de photos
        shell("cp ${photo.path} /sdcard/Pictures/recette_tensiometre.png")
        shell("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/recette_tensiometre.png")
        shell("content call --uri content://media/external/file --method scan_volume --arg external_primary")
        Thread.sleep(5000)
        resetApp(); launch()
        click("NOUVELLE SÉANCE", substring = true)
        click("Je suis déjà reposé(e) : commencer")
        answerPermissionDialog(true, 20_000)
        waitText("Importer", timeout = 90_000)
        click("Importer", substring = true)
        Thread.sleep(6000)
        shot("R19_selecteur_photos")
        val thumb = device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.descContains("Photo taken")), 30_000)
            ?: device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.descContains("Photo")), 10_000)
        thumb?.click()
        waitText("Valeurs de la mesure 1", timeout = 120_000)
        waitText("Confiance globale", "La reconnaissance a échoué", timeout = 600_000)
        shot("R19_validation_ocr")
        val ok = exists("125", false) && exists("85", false) && exists("78", false)
        record("R19", "OCR – parcours complet (photo réelle importée)", "photo réelle de l'écran 125/85/78", "champs pré-remplis 125 / 85 / 78, validation manuelle requise",
            "sélecteur=${thumb != null} ; 125=${exists("125", false)} 85=${exists("85", false)} 78=${exists("78", false)}", ok, "R19_validation_ocr.png")
        click("Valider ces valeurs")
        waitText("Mesure 1 enregistrée")
        val m = runBlocking { c.sessions.getAll().first().measurements.first() }
        record("R19b", "OCR – enregistrement", "validation", "mesure enregistrée, source « Reconnaissance automatique »", "${m.systolic}/${m.diastolic}/${m.pulse} source=${m.source}",
            m.systolic == 125 && m.diastolic == 85 && m.pulse == 78 && m.source.name.startsWith("OCR"), "—")
    }

    @Test
    fun r20_permissions_refusees() {
        resetApp(); launch()
        shell("pm revoke ${ctx.packageName} android.permission.CAMERA")
        shell("pm revoke ${ctx.packageName} android.permission.POST_NOTIFICATIONS")
        shell("appops set ${ctx.packageName} SCHEDULE_EXACT_ALARM deny")
        permissionAnswer = false // toute fenêtre d'autorisation sera refusée dans ce scénario
        relaunch()
        Thread.sleep(2000)
        answerPermissionDialog(allow = false, timeout = 10_000)
        waitText("Les notifications sont bloquées", timeout = 30_000)
        shot("R20_notifications_refusees")
        record("R20a", "Notifications refusées", "autorisation refusée", "avertissement sur l'accueil « Les notifications sont bloquées »", "affiché", true, "R20_notifications_refusees.png")
        click("Vérifier les autorisations")
        waitText("Diagnostic des autorisations", "Rappels")
        shot("R20_diagnostic")
        val diag = exists("Bloquées : aucun rappel") && exists("Non accordé")
        record("R20b", "Diagnostic des autorisations", "notifications et alarmes exactes refusées", "deux lignes en erreur avec boutons de réglage", if (diag) "conforme" else "non conforme", diag, "R20_diagnostic.png")
        record("R20c", "Alarmes exactes refusées", "appops SCHEDULE_EXACT_ALARM deny", "repli sur alarme approchée (pas de plantage)", "canScheduleExact=${c.reminderScheduler.canScheduleExact()}",
            !c.reminderScheduler.canScheduleExact() && c.reminderScheduler.rescheduleAll(runBlocking { c.settings.current().reminders }).isNotEmpty(), "—")
        back(); click("Accueil")
        click("NOUVELLE SÉANCE", substring = true)
        click("Je suis déjà reposé(e) : commencer")
        answerPermissionDialog(allow = false, timeout = 20_000)
        waitText("n'est pas accordée", timeout = 60_000)
        shot("R20_camera_refusee")
        record("R20d", "Caméra refusée", "refus de l'autorisation caméra", "message explicatif ; import et saisie manuelle disponibles", "affiché",
            exists("Importer") && exists("Saisie manuelle"), "R20_camera_refusee.png")
        click("Saisie manuelle", substring = true)
        record("R20e", "Saisie manuelle sans caméra", "Saisie manuelle", "formulaire de saisie accessible", "accessible", exists("Systolique (SYS"), "—")
        shell("pm grant ${ctx.packageName} android.permission.CAMERA")
        shell("pm grant ${ctx.packageName} android.permission.POST_NOTIFICATIONS")
        shell("appops set ${ctx.packageName} SCHEDULE_EXACT_ALARM allow")
    }

    @Test
    fun r21_orientation_tailles_pliable() {
        resetApp(); launch()
        // Formulaire en cours de saisie puis rotation
        click("Plus"); click("Profil personnel")
        type("Nom ou pseudonyme", "ROTATION-FICTIF")
        device.setOrientationLeft(); Thread.sleep(6000)
        shot("R21_profil_paysage")
        val kept = exists("ROTATION-FICTIF")
        record("R21a", "Rotation – formulaire non enregistré", "saisie « ROTATION-FICTIF » puis rotation", "saisie conservée", if (kept) "conservée" else "PERDUE", kept, "R21_profil_paysage.png")
        device.setOrientationNatural(); Thread.sleep(5000)
        back(); click("Accueil")
        // Séance en cours puis rotation
        click("NOUVELLE SÉANCE", substring = true)
        click("Je suis déjà reposé(e) : commencer")
        answerPermissionDialog(true, 20_000)
        click("Saisie manuelle", substring = true)
        type("Systolique (SYS", "133"); type("Diastolique (DIA", "86")
        device.setOrientationLeft(); Thread.sleep(6000)
        shot("R21_seance_paysage")
        val kept2 = exists("133", false) && exists("86", false)
        record("R21b", "Rotation – séance en cours", "valeurs saisies 133/86 puis rotation", "étape et valeurs conservées", if (kept2) "conservées" else "perdues", kept2, "R21_seance_paysage.png")
        device.setOrientationNatural(); Thread.sleep(5000)
        // Changement de taille d'écran = pliage/dépliage (écran externe du Razr 50s ≈ 1056×1066)
        shell("wm size 1056x1066"); shell("wm density 360"); Thread.sleep(8000)
        shot("R21_ecran_externe_pendant_seance")
        val kept3 = exists("133", false)
        record("R21c", "Pliable – passage à l'écran externe pendant la saisie", "wm size 1056×1066, 360 dpi", "valeurs conservées, interface utilisable", if (kept3) "conservées" else "perdues", kept3, "R21_ecran_externe_pendant_seance.png")
        back(); runCatching { click("Abandonner") }
        waitText("NOUVELLE SÉANCE", timeout = 60_000)
        shot("R21_accueil_ecran_externe")
        record("R21d", "Écran externe – accueil", "1056×1066", "bouton principal visible, contenu défilant", if (exists("NOUVELLE SÉANCE")) "visible" else "absent", exists("NOUVELLE SÉANCE"), "R21_accueil_ecran_externe.png")
        for ((label, size, dens) in listOf(Triple("petit téléphone", "720x1280", "320"), Triple("tablette / grand écran déplié", "1600x2560", "320"))) {
            shell("wm size $size"); shell("wm density $dens"); Thread.sleep(8000)
            shot("R21_accueil_${size}")
            record("R21-$size", "Taille d'écran – $label", size, "accueil affiché sans chevauchement, largeur de contenu limitée", if (exists("NOUVELLE SÉANCE")) "affiché" else "absent", exists("NOUVELLE SÉANCE"), "R21_accueil_${size}.png")
        }
        shell("wm size reset"); shell("wm density reset"); Thread.sleep(6000)
    }
}
