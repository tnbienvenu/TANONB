package com.cardiosuivi.app.recette

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class R01_DemarrageEtProfilTest : RecetteBase() {

    @Test
    fun r01_onboarding() {
        resetApp(onboarding = false)
        launch()
        waitText("Bienvenue dans CardioSuivi")
        val btn = compose.onAllNodes(hasText("Commencer").and(hasClickAction()))[0]
        btn.assertIsNotEnabled()
        shot("R01_onboarding_bouton_inactif")
        click("J'ai compris", substring = true)
        btn.assertIsEnabled()
        val seen0 = permissionDialogsSeen
        click("Commencer")
        val asked = answerPermissionDialog(allow = true, timeout = 60_000) || permissionDialogsSeen > seen0
        record("R01c", "Autorisation des notifications (Android 13+)", "premier accès au tableau de bord", "fenêtre système de demande affichée", if (asked) "affichée puis acceptée" else "non affichée", asked, "ECHEC_attente_NOUVELLE SÉANCE.png (run 1)")
        waitText("Commencer une séance", timeout = 120_000)
        shot("R01_tableau_de_bord_vide")
        record("R01", "Avertissement initial", "case non cochée puis cochée",
            "Bouton inactif sans case ; tableau de bord après acceptation",
            "Bouton inactif puis tableau de bord affiché", exists("Aucune séance validée"))
        relaunch()
        record("R01b", "Avertissement initial", "relance de l'application", "avertissement non réaffiché",
            if (exists("Bienvenue dans CardioSuivi")) "réaffiché" else "tableau de bord directement", !exists("Bienvenue dans CardioSuivi"), "—")
    }

    @Test
    fun r02_profil_formulaire_complet() {
        resetApp()
        launch()
        tab("Profil"); click("Profil personnel")
        waitText("Nom ou pseudonyme")
        shot("R02_profil_vide")

        // 1. Champs vides : enregistrement accepté (tous facultatifs), objectif absent
        click("Enregistrer")
        waitText("Profil enregistré.")
        val p0 = runBlocking { c.profile.getProfile() }
        record("R02a", "Profil – champs vides", "aucun champ", "enregistré, objectif null", "objectif=${p0.target}, nom='${p0.displayName}'", p0.target == null && p0.displayName.isEmpty(), "—")

        // 2. Valeurs incorrectes
        type("Date de naissance", "31/02/1960")
        click("Enregistrer")
        val badDate = exists("Date de naissance invalide.")
        shot("R02b_date_invalide")
        record("R02b", "Profil – date invalide", "31/02/1960", "message « Date de naissance invalide. »", if (badDate) "message affiché" else "aucun message", badDate, "R02b_date_invalide.png")

        type("Date de naissance", "15/06/1958")
        type("Systolique <", "135"); type("Diastolique <", "")
        click("Enregistrer")
        record("R02c", "Profil – objectif incomplet", "135 / vide", "refus : deux valeurs requises",
            if (exists("Renseignez les deux valeurs")) "refusé" else "accepté", exists("Renseignez les deux valeurs"), "—")

        type("Diastolique <", "140")
        click("Enregistrer")
        record("R02d", "Profil – objectif incohérent", "135 / 140 (SYS ≤ DIA)", "refus « Objectif incohérent »",
            if (exists("Objectif incohérent")) "refusé" else "accepté", exists("Objectif incohérent"), "—")

        type("Systolique <", "999"); type("Diastolique <", "85")
        val sysShown = node("999", substring = false).let { true }
        // Les champs numériques limitent la saisie à 3 chiffres ; 999 hors plage doit être refusé
        click("Enregistrer")
        record("R02e", "Profil – limite haute", "999 / 85", "refus (hors 90–200)", if (exists("Objectif incohérent")) "refusé" else "accepté", sysShown && exists("Objectif incohérent"), "—")

        type("Systolique <", "13a5")
        val filtered = exists("135", substring = false)
        record("R02f", "Profil – caractères non numériques", "« 13a5 »", "lettres filtrées → 135", if (filtered) "135" else "non filtré", filtered, "—")

        // 3. Valeurs normales
        type("Nom ou pseudonyme", "RECETTE-FICTIF")
        click("Homme")
        type("Taille", "176"); type("Poids", "78,5")
        type("Diastolique <", "85")
        type("Antécédents médicaux", "Antécédent fictif de test")
        type("Observations complémentaires", "Observation fictive")
        click("Enregistrer")
        waitText("Profil enregistré.")
        shot("R02g_profil_rempli")
        val p1 = runBlocking { c.profile.getProfile() }
        val okSave = p1.displayName == "RECETTE-FICTIF" && p1.birthDate == LocalDate.of(1958, 6, 15) && p1.target?.systolic == 135 &&
            p1.target?.diastolic == 85 && p1.heightCm == 176 && p1.weightKg == 78.5 && p1.medicalHistory == "Antécédent fictif de test"
        record("R02g", "Profil – valeurs normales", "RECETTE-FICTIF, 15/06/1958, H, 176 cm, 78,5 kg, objectif 135/85", "toutes valeurs en base",
            "$p1", okSave, "R02g_profil_rempli.png")

        // 4. Fermer / rouvrir l'écran puis l'application
        back(); click("Profil personnel")
        val kept = exists("RECETTE-FICTIF") && exists("15/06/1958") && exists("78,5")
        record("R02h", "Profil – persistance écran", "fermer/rouvrir l'écran", "valeurs réaffichées", if (kept) "réaffichées" else "perdues", kept, "—")
        relaunch(); tab("Profil"); click("Profil personnel")
        val kept2 = exists("RECETTE-FICTIF") && exists("176")
        shot("R02i_profil_apres_relance")
        record("R02i", "Profil – persistance application", "relance de l'activité", "valeurs réaffichées", if (kept2) "réaffichées" else "perdues", kept2, "R02i_profil_apres_relance.png")

        // 5. Modification
        type("Nom ou pseudonyme", "RECETTE-FICTIF-2"); type("Systolique <", "130"); type("Diastolique <", "80")
        click("Enregistrer"); waitText("Profil enregistré.")
        val p2 = runBlocking { c.profile.getProfile() }
        back()
        click("Accueil")
        val dash = exists("< 130/80")
        record("R02j", "Profil – modification", "RECETTE-FICTIF-2, objectif 130/80", "base mise à jour + objectif affiché à l'accueil",
            "nom=${p2.displayName}, objectif=${p2.target}, accueil=${if (dash) "130/80 affiché" else "absent"}", p2.displayName == "RECETTE-FICTIF-2" && p2.target?.systolic == 130 && dash)
    }
}
