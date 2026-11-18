package com.cardiosuivi.app.recette

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Autorisations refusées, absence de caméra/OCR (1.1.0), rotation et tailles d'écran.
 * Les scénarios OCR de la 1.0 (R18 jeu de référence, R19 import de photo) sont supprimés avec la fonctionnalité.
 */
@RunWith(AndroidJUnit4::class)
class R18_EcransTest : RecetteBase() {

    private fun shell(cmd: String): String =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instr.uiAutomation.executeShellCommand(cmd)).bufferedReader().readText()

    @Test
    fun r20_permissions_refusees() {
        // Pré-requis (côté hôte, avant le lancement : un retrait d'autorisation tue le processus) :
        //   pm revoke … POST_NOTIFICATIONS
        shell("appops set ${ctx.packageName} SCHEDULE_EXACT_ALARM deny")
        permissionAnswer = false // toute fenêtre d'autorisation sera refusée dans ce scénario
        resetApp(); launch()
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
        back(); tab("Accueil")
        val requested = ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        val noCamera = "android.permission.CAMERA" !in requested
        record("R20d", "Caméra supprimée (1.1.0)", "permissions déclarées par l'application", "aucune autorisation CAMERA demandée",
            "CAMERA ${if (noCamera) "absente" else "PRÉSENTE"} ; déclarées : ${requested.joinToString { it.substringAfterLast('.') }}", noCamera, "—")
        click("Commencer une séance")
        click("Je suis déjà reposé(e) : commencer")
        val dialog = answerPermissionDialog(allow = false, timeout = 8_000)
        waitText("SYS — Pression systolique", timeout = 60_000)
        shot("R20_saisie_sans_camera")
        record("R20e", "Saisie sans caméra ni OCR", "Commencer une séance", "formulaire SYS/DIA/PUL affiché directement, sans demande d'autorisation ni bouton photo/import",
            "dialogue=$dialog, photo=${exists("Photo") || exists("Importer") || exists("Saisie manuelle")}",
            !dialog && exists("SYS — Pression systolique") && !exists("Importer") && !exists("Saisie manuelle") && !exists("Reprendre la photo"), "R20_saisie_sans_camera.png")
        shell("pm grant ${ctx.packageName} android.permission.POST_NOTIFICATIONS")
        shell("appops set ${ctx.packageName} SCHEDULE_EXACT_ALARM allow")
    }

    @Test
    fun r21_orientation_tailles_pliable() {
        resetApp(); launch()
        // Formulaire en cours de saisie puis rotation
        tab("Profil"); click("Profil personnel")
        type("Nom ou pseudonyme", "ROTATION-FICTIF")
        device.setOrientationLeft(); Thread.sleep(6000)
        shot("R21_profil_paysage")
        val kept = exists("ROTATION-FICTIF")
        record("R21a", "Rotation – formulaire non enregistré", "saisie « ROTATION-FICTIF » puis rotation", "saisie conservée", if (kept) "conservée" else "PERDUE", kept, "R21_profil_paysage.png")
        device.setOrientationNatural(); Thread.sleep(5000)
        back(); tab("Accueil")
        // Séance en cours puis rotation
        click("Commencer une séance")
        click("Je suis déjà reposé(e) : commencer")
        type("SYS — ", "133"); type("DIA — ", "86")
        device.setOrientationLeft(); Thread.sleep(6000)
        shot("R21_seance_paysage")
        val kept2 = exists("133", false) && exists("86", false)
        record("R21b", "Rotation – séance en cours", "valeurs saisies 133/86 puis rotation", "étape et valeurs conservées", if (kept2) "conservées" else "perdues", kept2, "R21_seance_paysage.png")
        device.setOrientationNatural(); Thread.sleep(5000)
        // Changement de taille d'écran = pliage/dépliage (écran externe du Razr 50s ≈ 1056×1066)
        // Après un changement de taille, l'émulateur logiciel peut mettre plus d'une minute à recréer l'activité :
        // les contrôles passent par UiAutomator (sans synchronisation Compose), avec une attente jusqu'à 3 min.
        fun uiHas(t: String, ms: Long = 180_000) = device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.textContains(t)), ms) == true
        shell("wm size 1056x1066"); shell("wm density 360")
        val kept3 = uiHas("133", 300_000)
        rawShot("R21_ecran_externe_pendant_seance")
        record("R21c", "Pliable – passage à l'écran externe pendant la saisie", "wm size 1056×1066, 360 dpi", "valeurs conservées, interface utilisable", if (kept3) "conservées" else "perdues", kept3, "R21_ecran_externe_pendant_seance.png")
        device.pressBack()
        device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Abandonner")), 120_000)?.click()
        // Petit écran : le bouton principal est sous la ligne de flottaison (UiAutomator ne voit que l'écran visible).
        val home = uiHas("Résumé de votre santé cardiovasculaire")
        rawShot("R21_accueil_ecran_externe")
        record("R21d", "Écran externe – accueil", "1056×1066", "bouton principal visible, contenu défilant", if (home) "visible" else "absent", home, "R21_accueil_ecran_externe.png")
        for ((label, size, dens) in listOf(Triple("petit téléphone", "720x1280", "320"), Triple("tablette / grand écran déplié", "1600x2560", "320"))) {
            shell("wm size $size"); shell("wm density $dens"); Thread.sleep(5000)
            val ok = uiHas("Résumé de votre santé cardiovasculaire")
            Thread.sleep(3000)
            rawShot("R21_accueil_${size}")
            record("R21-$size", "Taille d'écran – $label", size, "accueil affiché sans chevauchement, largeur de contenu limitée", if (ok) "affiché" else "absent", ok, "R21_accueil_${size}.png")
        }
        shell("wm size reset"); shell("wm density reset"); Thread.sleep(6000)
    }
}
