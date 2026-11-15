package com.cardiosuivi.app.recette

import android.graphics.Bitmap
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.cardiosuivi.app.CardioSuiviApp
import com.cardiosuivi.app.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import java.io.File

/**
 * Base des scénarios de recette exécutés sur Android (émulateur ou appareil).
 * Toutes les données sont FICTIVES (patient de test « RECETTE-FICTIF »).
 * Chaque scénario consigne le résultat observé dans recette/journal.tsv et enregistre des captures.
 */
abstract class RecetteBase {

    @get:Rule
    val compose: ComposeTestRule = createEmptyComposeRule()

    protected val instr = InstrumentationRegistry.getInstrumentation()
    protected val ctx = instr.targetContext
    protected val app get() = ctx.applicationContext as CardioSuiviApp
    protected val c get() = app.container
    protected var scenario: ActivityScenario<MainActivity>? = null

    protected val device: UiDevice = UiDevice.getInstance(instr)

    /** Réponse à la fenêtre système d'autorisation si elle apparaît (true = autoriser). */
    var permissionAnswer = true

    private val allowIds = listOf(
        "com.android.permissioncontroller:id/permission_allow_button",
        "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
        "com.android.permissioncontroller:id/permission_allow_one_time_button",
    )
    private val denyIds = listOf(
        "com.android.permissioncontroller:id/permission_deny_button",
        "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button",
    )

    /** Répond à une fenêtre système d'autorisation si elle est affichée. */
    /** Nombre de fenêtres d'autorisation système traitées (preuve qu'une demande a bien été affichée). */
    var permissionDialogsSeen = 0

    fun tryAnswerPermission(allow: Boolean = permissionAnswer): Boolean {
        for (id in if (allow) allowIds else denyIds) {
            device.findObject(By.res(id))?.let { it.click(); permissionDialogsSeen++; Thread.sleep(1500); return true }
        }
        return false
    }

    fun answerPermissionDialog(allow: Boolean = permissionAnswer, timeout: Long = 15_000): Boolean {
        val seenBefore = permissionDialogsSeen
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < timeout) {
            if (tryAnswerPermission(allow) || permissionDialogsSeen > seenBefore) return true
            Thread.sleep(1000)
        }
        return false
    }

    protected val outDir: File by lazy { File(ctx.getExternalFilesDir(null), "recette").apply { mkdirs() } }

    /** Réinitialise toutes les données et prépare les paramètres (écran non protégé pour permettre les captures). */
    protected fun resetApp(onboarding: Boolean = true, secure: Boolean = false) = runBlocking {
        runCatching { scenario?.close() }
        scenario = null
        // Ferme toute activité encore ouverte (fenêtres système, singleTask) pour repartir d'un état neuf.
        repeat(3) { device.pressBack(); Thread.sleep(300) }
        device.pressHome()
        instr.runOnMainSync {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .plus(androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.PAUSED))
                .plus(androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.STOPPED))
                .forEach { it.finish() }
        }
        Thread.sleep(2000)
        c.destroyAllData()
        if (onboarding) c.settings.setOnboardingAccepted()
        c.settings.setSecureScreen(secure)
    }

    protected fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitText("NOUVELLE SÉANCE", "Bienvenue dans CardioSuivi", timeout = 120_000)
        answerPermissionDialog(timeout = 8_000)
    }

    protected fun relaunch() {
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitText("NOUVELLE SÉANCE", "Bienvenue dans CardioSuivi", "CardioSuivi est verrouillé", timeout = 120_000)
        answerPermissionDialog(timeout = 5_000)
    }

    @After
    fun closeScenario() { scenario?.close() }

    // ---------- Interactions ----------

    private var lastSysCheck = 0L

    /** Ferme les fenêtres « … ne répond pas » du système (lenteur de l'émulateur logiciel), sans toucher à l'application. */
    fun dismissSystemAnr() {
        val now = System.currentTimeMillis()
        if (now - lastSysCheck < 4000) return
        lastSysCheck = now
        if (device.hasObject(By.pkg("com.google.android.permissioncontroller")) || device.hasObject(By.pkg("com.android.permissioncontroller"))) {
            tryAnswerPermission()
        }
        if (device.hasObject(By.textContains("isn't responding")) || device.hasObject(By.textContains("ne répond pas"))) {
            (device.findObject(By.text("Wait")) ?: device.findObject(By.text("Attendre")))?.click()
            Thread.sleep(800)
        }
    }

    fun waitText(vararg texts: String, timeout: Long = 60_000, substring: Boolean = true) {
        try {
            compose.waitUntil(timeout) {
                runCatching { dismissSystemAnr() }
                runCatching { texts.any { t -> compose.onAllNodesWithText(t, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } }
                    .getOrDefault(false)
            }
        } catch (e: ComposeTimeoutException) {
            shot("ECHEC_attente_${texts.first().take(20)}")
            throw AssertionError("Texte attendu non affiché : ${texts.joinToString(" | ")}", e)
        }
    }

    fun exists(text: String, substring: Boolean = true): Boolean = runCatching {
        compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

    fun node(text: String, substring: Boolean = true): SemanticsNodeInteraction {
        waitText(text, substring = substring)
        return compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true)[0]
    }

    fun click(text: String, substring: Boolean = false) {
        waitText(text, substring = substring)
        val n = compose.onAllNodes(hasText(text, substring = substring).and(hasClickAction()))
        val target = if (n.fetchSemanticsNodes().isNotEmpty()) n[0] else compose.onAllNodesWithText(text, substring = substring)[0]
        runCatching { target.performScrollTo() }
        target.performClick()
        compose.waitForIdle()
    }

    /** Saisie dans le champ dont le libellé contient [label]. */
    fun type(label: String, value: String, clear: Boolean = true) {
        waitText(label)
        val f = compose.onAllNodes(hasSetTextAction().and(hasText(label, substring = true)))[0]
        runCatching { f.performScrollTo() }
        if (clear) f.performTextClearance()
        if (value.isNotEmpty()) f.performTextInput(value)
        compose.waitForIdle()
    }

    /** Ferme le clavier virtuel s'il est affiché (sinon « retour » ne ferait que le masquer). */
    fun hideKeyboard() {
        val st = device.executeShellCommand("dumpsys input_method")
        if (st.contains("mInputShown=true")) {
            device.pressBack(); Thread.sleep(800)
        }
    }

    fun back() {
        hideKeyboard()
        instr.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitForIdle()
        Thread.sleep(800)
    }

    // ---------- Preuves ----------

    fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val bmp: Bitmap = instr.uiAutomation.takeScreenshot() ?: return
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    /** Consigne un résultat : identifiant, fonctionnalité, données, attendu, observé, statut. */
    fun record(id: String, feature: String, data: String, expected: String, observed: String, ok: Boolean, proof: String = "$id.png") {
        val line = listOf(id, feature, data, expected, observed, if (ok) "RÉUSSI" else "ÉCHOUÉ", proof)
            .joinToString("\t") { it.replace("\t", " ").replace("\n", " / ") }
        synchronized(RecetteBase::class.java) { File(outDir, "journal.tsv").appendText(line + "\n") }
        if (!ok) throw AssertionError("$id : attendu « $expected », observé « $observed »")
    }
}
