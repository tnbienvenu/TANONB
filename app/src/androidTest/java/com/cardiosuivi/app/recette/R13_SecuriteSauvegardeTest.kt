package com.cardiosuivi.app.recette

import android.net.Uri
import androidx.compose.ui.test.assertIsNotEnabled
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardiosuivi.domain.backup.BackupException
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.MeasurementSource
import com.cardiosuivi.domain.model.Profile
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class R13_SecuriteSauvegardeTest : RecetteBase() {

    private fun seedOne() = runBlocking {
        c.profile.saveProfile(Profile(displayName = "RECETTE-FICTIF"))
        val st = Instant.parse("2026-11-10T06:05:00Z")
        c.sessions.insertFull(MeasurementSession(startedAt = st, period = SessionPeriod.MORNING, status = SessionStatus.COMPLETED, localDate = LocalDate.of(2026, 11, 10),
            measurements = (1..3).map { Measurement(index = it, systolic = 124 + it, diastolic = 80, pulse = 64, measuredAt = st.plusSeconds(60L * it), source = MeasurementSource.MANUAL) }))
    }

    private fun secureFlag(): Boolean {
        var on = false
        scenario!!.onActivity { on = (it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0 }
        return on
    }

    @Test
    fun r13_parametres_securite() {
        resetApp(secure = true); launch()
        record("R13a", "Écran protégé (défaut)", "réglage par défaut", "FLAG_SECURE actif (captures bloquées)", "FLAG_SECURE=${secureFlag()}", secureFlag(), "capture noire R000 (image ATD) / fenêtre")
        tab("Profil"); click("Paramètres et sécurité")
        click("Écran protégé")
        Thread.sleep(1500)
        record("R13b", "Écran protégé – désactivation", "interrupteur", "FLAG_SECURE retiré", "FLAG_SECURE=${secureFlag()}", !secureFlag(), "—")
        shot("R13_parametres")
        type("Numéro des services d'urgence", "15a#")
        val filtered = exists("15", false)
        click("Enregistrer")
        Thread.sleep(1000)
        val saved = runBlocking { c.settings.current().emergencyNumber }
        record("R13c", "Numéro d'urgence", "« 15a# »", "caractères invalides filtrés, « 15 » enregistré", "affiché=${filtered}, enregistré=$saved", saved == "15", "R13_parametres.png")
        type("Numéro des services d'urgence", "")
        click("Enregistrer")
        Thread.sleep(1000)
        val empty = runBlocking { c.settings.current().emergencyNumber }
        record("R13d", "Numéro d'urgence vide", "champ vidé", "retour à 112 (jamais de numéro vide)", "enregistré=$empty", empty == "112", "—")
        relaunch()
        tab("Profil"); click("Paramètres et sécurité")
        record("R13e", "Paramètres – persistance", "relance", "écran protégé désactivé et 112 conservés", "FLAG_SECURE=${secureFlag()}", !secureFlag() && exists("112"), "—")

        // Biométrie : aucun moyen d'authentification configuré sur l'émulateur au départ
        click("Verrouillage biométrique")
        Thread.sleep(2000)
        shot("R13_biometrie_indisponible")
        val msg = exists("Aucune empreinte") || exists("Indisponible") || exists("Pas de capteur")
        val stillOff = !runBlocking { c.settings.current().biometricLock }
        record("R13f", "Biométrie – non configurée sur l'appareil", "activer le verrouillage sans empreinte ni code", "message explicatif, verrouillage NON activé",
            "message=${msg}, activé=${!stillOff}", msg && stillOff, "R13_biometrie_indisponible.png")
    }

    @Test
    fun r14_sauvegardes_corrompues_et_restauration() {
        resetApp(); seedOne(); launch()
        val dir = File(ctx.getExternalFilesDir(null), "sauvegardes").apply { mkdirs() }
        val good = File(dir, "bonne.csbk")
        runBlocking { c.backup.export(Uri.fromFile(good), "MotDePasseFictif1".toCharArray()) }
        record("R14a", "Sauvegarde chiffrée", "mot de passe « MotDePasseFictif1 »", "fichier créé, contenu illisible en clair",
            "${good.length()} octets, contient « RECETTE » en clair=${String(good.readBytes(), Charsets.ISO_8859_1).contains("RECETTE")}",
            good.length() > 100 && !String(good.readBytes(), Charsets.ISO_8859_1).contains("RECETTE"), "—")

        fun tryRead(f: File, pwd: String): String = try {
            runBlocking { c.backup.read(Uri.fromFile(f), pwd.toCharArray()) }; "lue"
        } catch (e: BackupException) { "refusée : ${e.message}" } catch (e: Exception) { "PLANTAGE ${e.javaClass.simpleName}" }

        val bytes = good.readBytes()
        val cases = listOf(
            Triple("mauvais mot de passe", good to "AutreMotDePasse", "Mot de passe incorrect"),
            Triple("fichier tronqué (moitié)", File(dir, "tronque.csbk").apply { writeBytes(bytes.copyOf(bytes.size / 2)) } to "MotDePasseFictif1", "incorrect ou fichier altéré"),
            Triple("un octet modifié", File(dir, "modifie.csbk").apply { writeBytes(bytes.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 0x55).toByte() }) } to "MotDePasseFictif1", "altéré"),
            Triple("fichier vide", File(dir, "vide.csbk").apply { writeBytes(ByteArray(0)) } to "MotDePasseFictif1", "invalide ou tronqué"),
            Triple("autre fichier (texte)", File(dir, "texte.csbk").apply { writeText("ceci n'est pas une sauvegarde de CardioSuivi, contenu quelconque assez long") } to "MotDePasseFictif1", "pas une sauvegarde"),
            Triple("en-tête modifié", File(dir, "entete.csbk").apply { writeBytes(bytes.copyOf().also { it[12] = (it[12].toInt() xor 1).toByte() }) } to "MotDePasseFictif1", "altéré"),
        )
        cases.forEachIndexed { i, (label, fp, expectedMsg) ->
            val obs = tryRead(fp.first, fp.second)
            record("R14b${i + 1}", "Sauvegarde corrompue", label, "refus avec message « …$expectedMsg… », aucune donnée modifiée", obs,
                obs.startsWith("refusée") && obs.contains(expectedMsg) && runBlocking { c.sessions.getAll().size } == 1, "—")
        }
        // Restauration réelle après suppression
        runBlocking { c.maintenance.wipeAll() }
        val p = runBlocking { c.backup.read(Uri.fromFile(good), "MotDePasseFictif1".toCharArray()) }
        runBlocking { c.backup.restore(p) }
        val back = runBlocking { c.sessions.getAll() }
        val name = runBlocking { c.profile.getProfile().displayName }
        record("R14c", "Restauration", "bonne sauvegarde après suppression des données", "1 séance (125/126/127) et profil restaurés",
            "${back.size} séance(s), ${back.firstOrNull()?.measurements?.map { it.systolic }}, profil=$name",
            back.size == 1 && back[0].measurements.map { it.systolic } == listOf(125, 126, 127) && name == "RECETTE-FICTIF", "—")

        // Interface : écran de sauvegarde, mots de passe non concordants
        tab("Profil"); click("Sauvegarde et restauration")
        type("Mot de passe (", "abcdefgh1")
        type("Confirmer le mot de passe", "abcdefgh2")
        shot("R14_mots_de_passe_differents")
        val disabled = runCatching {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Choisir l'emplacement et sauvegarder").and(androidx.compose.ui.test.hasClickAction()))[0]
                .assertIsNotEnabledCompat(); true
        }.getOrDefault(false)
        record("R14d", "Sauvegarde – confirmation", "mots de passe différents", "bouton de sauvegarde inactif", if (disabled) "inactif" else "actif", disabled, "R14_mots_de_passe_differents.png")
        type("Mot de passe (", "court"); type("Confirmer le mot de passe", "court")
        val disabled2 = runCatching {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Choisir l'emplacement et sauvegarder").and(androidx.compose.ui.test.hasClickAction()))[0]
                .assertIsNotEnabledCompat(); true
        }.getOrDefault(false)
        record("R14e", "Sauvegarde – mot de passe court", "« court » (5 caractères)", "bouton inactif (minimum 8)", if (disabled2) "inactif" else "actif", disabled2, "—")

        // Suppression totale via l'interface (double confirmation) puis redémarrage propre (anomalie A02)
        click("Supprimer toutes les données")
        click("Continuer")
        click("Tout supprimer")
        Thread.sleep(8000)
        scenario?.close()
        scenario = androidx.test.core.app.ActivityScenario.launch(com.cardiosuivi.app.MainActivity::class.java)
        waitText("Bienvenue dans CardioSuivi", "Commencer une séance", timeout = 120_000)
        shot("R14_apres_suppression")
        val wiped = runBlocking { c.sessions.getAll().isEmpty() && c.profile.getProfile().displayName.isEmpty() }
        record("R14f", "Suppression de toutes les données", "double confirmation", "données effacées, application relancée sans plantage, avertissement initial réaffiché",
            "effacées=$wiped, avertissement=${exists("Bienvenue dans CardioSuivi")}", wiped && exists("Bienvenue dans CardioSuivi"), "R14_apres_suppression.png")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertIsNotEnabledCompat() {
        assertIsNotEnabled()
    }
}
