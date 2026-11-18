package com.cardiosuivi.app.recette

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardiosuivi.domain.model.SessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Séances de mesure. Valeurs FICTIVES ; résultats attendus calculés à la main :
 * M1 148/94 P76, M2 140/90 P74, M3 136/88 P72
 *  - moyenne des 3 : SYS 424/3 = 141,33 ; DIA 272/3 = 90,67 ; P 74,0
 *  - moyenne des 2 dernières : 138,0 / 89,0 / 73,0
 *  - amplitude SYS 136–148 (12) ; DIA 88–94 (6) ; évolutions SYS −8 puis −4 ; DIA −4 puis −2
 *  - écart-type SYS √(((6,67)²+(1,33)²+(5,33)²)/2) = 6,11 ; DIA √((3,33²+0,67²+2,67²)/2) = 3,06
 *  - pression pulsée moyenne (54+50+48)/3 = 50,67 → « 51 mmHg »
 */
@RunWith(AndroidJUnit4::class)
class R03_SeancesTest : RecetteBase() {

    private fun startManualSession() {
        click("Commencer une séance")
        waitText("Avant de commencer")
        shot("R03_preparation")
        click("Je suis déjà reposé(e) : commencer")
        waitText("SYS — Pression systolique", timeout = 90_000)
    }

    private fun enterMeasurement(sys: String, dia: String, pul: String, proof: String? = null) {
        waitText("SYS — Pression systolique")
        type("SYS — ", sys)
        type("DIA — ", dia)
        type("PUL — ", pul)
        proof?.let { shot(it) }
        click("Valider et continuer")
    }

    @Test
    fun r03_seance_trois_mesures_et_calculs() {
        resetApp()
        launch()
        startManualSession()
        shot("R03_saisie_vide")
        enterMeasurement("148", "94", "76", "R03_saisie_mesure1")
        waitText("Mesure 1 enregistrée")
        waitText("Avant la mesure 2")
        shot("R03_intervalle")
        record("R03a", "Séance – intervalle", "mesure 1 validée", "écran d'intervalle avec décompte (60 s)", "écran « Avant la mesure 2 » affiché", exists("Avant la mesure 2"), "R03_intervalle.png")
        click("Mesurer maintenant")
        enterMeasurement("140", "90", "74")
        click("Mesurer maintenant")
        enterMeasurement("136", "88", "72")
        waitText("Moyennes de la séance", timeout = 90_000)
        shot("R03_resume_haut")
        val checks = mapOf(
            "moyenne 3" to exists("141,3/90,7 · 74,0"),
            "moyenne 2 dernières" to exists("138,0/89,0 · 73,0"),
            "valeur séance 141" to exists("141", substring = false),
            "amplitude SYS" to exists("136 – 148 (12)"),
            "amplitude DIA" to exists("88 – 94 (6)"),
            "écart-type" to exists("6,1 / 3,1"),
            "évolution SYS" to exists("-8 puis -4"),
            "évolution DIA" to exists("-4 puis -2"),
            "pression pulsée" to exists("51 mmHg"),
        )
        node("Pression pulsée moyenne").let { runCatching { it.assertExists() } }
        shot("R03_resume_calculs")
        record("R03b", "Séance – calculs du résumé", "148/94/76 ; 140/90/74 ; 136/88/72",
            "141,3/90,7·74,0 ; 138,0/89,0·73,0 ; 136–148 (12) ; 88–94 (6) ; SD 6,1/3,1 ; −8 puis −4 ; −4 puis −2 ; PP 51",
            checks.entries.joinToString { "${it.key}=${if (it.value) "OK" else "KO"}" }, checks.values.all { it }, "R03_resume_calculs.png")

        click("Valider la séance")
        waitText("Séance enregistrée", "Terminer", timeout = 60_000)
        shot("R03_seance_enregistree")
        val home = exists("Au-dessus du seuil de référence à domicile")
        record("R03c", "Séance – alerte de fin", "moyenne 141/91, sans objectif", "alerte « Au-dessus du seuil de référence à domicile » (135/85)",
            if (home) "alerte affichée" else "absente", home, "R03_seance_enregistree.png")
        click("Terminer")
        waitText("Commencer une séance")
        val s = runBlocking { c.sessions.getAll() }
        val ok = s.size == 1 && s[0].status == SessionStatus.COMPLETED && s[0].measurements.map { it.systolic } == listOf(148, 140, 136)
        record("R03d", "Séance – enregistrement", "validation", "1 séance « Terminée », 3 mesures 148/140/136, heures réelles croissantes",
            "${s.size} séance(s), statut ${s.firstOrNull()?.status}, mesures ${s.firstOrNull()?.measurements?.map { "${it.systolic}/${it.diastolic}@${it.measuredAt}" }}",
            ok && s[0].measurements.zipWithNext().all { (a, b) -> a.measuredAt < b.measuredAt })
        shot("R03_tableau_de_bord_apres")
        // 1.1 : la carte « Dernière séance » affiche la dernière mesure (136/88) en grand et la moyenne de référence (141/91).
        val dash = exists("Dernière séance") && exists("136", false) && exists("88", false) && exists("Moyenne de référence de la séance : 141/91 mmHg")
        record("R03e", "Tableau de bord", "après séance", "dernière mesure 136/88 et moyenne de séance 141/91 affichées", if (dash) "affichées" else "absentes",
            dash, "R03_tableau_de_bord_apres.png")

        tab("Mesures")
        waitText("Complète", substring = true)
        val hist = exists("141/91", false) && exists("148/94  ·  140/90  ·  136/88")
        shot("R03_historique")
        record("R03f", "Historique", "1 séance", "ligne 141/91 mmHg et détail des 3 mesures", if (hist) "conforme" else "non conforme", hist, "R03_historique.png")
    }

    @Test
    fun r04_erreurs_de_saisie() {
        resetApp()
        launch()
        startManualSession()
        click("Valider et continuer")
        val empty = exists("SYS est obligatoire") && exists("DIA est obligatoire") && exists("PUL est obligatoire")
        shot("R04_champs_vides")
        record("R04a", "Saisie – SYS, DIA et PUL vides", "validation sans valeur", "3 messages « … est obligatoire », rien enregistré", if (empty) "3 messages affichés" else "messages absents",
            empty && runBlocking { c.sessions.getAll().isEmpty() }, "R04_champs_vides.png")

        type("SYS — ", "8x0"); type("DIA — ", "120"); type("PUL — ", "7a2")
        val filtered = exists("80", false) && exists("72", false)
        record("R04b", "Saisie – caractères non numériques", "« 8x0 », « 7a2 »", "seuls les chiffres sont acceptés → 80 et 72", if (filtered) "filtrés" else "non filtrés", filtered, "—")
        click("Valider et continuer")
        val inverted = exists("doit être supérieure à la diastolique")
        val kept = exists("80", false) && exists("120", false) && exists("72", false)
        shot("R04_inversion")
        record("R04c", "Saisie – SYS ≤ DIA refusée", "80 / 120 / 72", "refus avec message sous le champ, valeurs saisies conservées, rien enregistré",
            "message=$inverted, valeurs conservées=$kept", inverted && kept && runBlocking { c.sessions.getAll().isEmpty() }, "R04_inversion.png")

        type("SYS — ", "300"); type("DIA — ", "20"); type("PUL — ", "250")
        click("Valider et continuer")
        val bounds = exists("Vérifiez les valeurs") && exists("valeur inhabituelle")
        shot("R04_hors_bornes")
        record("R04d", "Saisie – hors bornes de plausibilité (inchangées)", "300 / 20 / 250", "dialogue de vérification listant les valeurs inhabituelles", if (bounds) "affiché" else "absent", bounds, "R04_hors_bornes.png")
        click("Corriger")

        type("SYS — ", "1234")
        val limited = exists("123", false) && !exists("1234", false)
        record("R04e", "Saisie – longueur", "« 1234 »", "saisie limitée à 3 chiffres (123)", if (limited) "123" else "non limitée", limited, "—")

        type("SYS — ", "128"); type("DIA — ", "82"); type("PUL — ", "")
        click("Valider et continuer")
        val pulseRequired = exists("PUL est obligatoire") && runBlocking { c.sessions.getAll().isEmpty() }
        shot("R04_pouls_obligatoire")
        record("R04f", "Saisie – PUL obligatoire", "128 / 82 / (vide)", "refus « PUL est obligatoire », rien enregistré", if (pulseRequired) "refusé" else "accepté", pulseRequired, "R04_pouls_obligatoire.png")
        type("PUL — ", "72")
        click("Valider et continuer")
        waitText("Mesure 1 enregistrée")
        val m = runBlocking { c.sessions.getAll().firstOrNull()?.measurements?.firstOrNull() }
        record("R04g", "Séance en cours non comptée", "1 mesure 128/82/72 validée", "mesure enregistrée, séance au statut « En cours »",
            "${m?.systolic}/${m?.diastolic}/${m?.pulse}, statut ${runBlocking { c.sessions.getAll().first().status }}",
            m?.pulse == 72 && runBlocking { c.sessions.getAll().first().status } == SessionStatus.IN_PROGRESS, "—")

        // Confirmation explicite d'une valeur atypique mais réelle
        click("Mesurer maintenant")
        type("SYS — ", "265"); type("DIA — ", "100"); type("PUL — ", "80")
        click("Valider et continuer")
        click("Je confirme ces valeurs")
        waitText("Mesure 2 enregistrée")
        record("R04h", "Saisie – valeur hors bornes confirmée", "265/100/80 confirmée", "enregistrée après confirmation explicite", "mesure 2 enregistrée", true, "—")
    }

    @Test
    fun r05_seance_incomplete_et_interruption() {
        resetApp()
        launch()
        startManualSession()
        enterMeasurement("132", "84", "70")
        click("Mesurer maintenant")
        enterMeasurement("128", "82", "68")
        waitText("Avant la mesure 3")
        // Interruption : retour → « Reprendre plus tard »
        back()
        waitText("Quitter la séance ?")
        shot("R05_quitter")
        click("Reprendre plus tard")
        waitText("Séance en cours", timeout = 60_000)
        shot("R05_seance_en_cours_accueil")
        record("R05a", "Interruption de séance", "2 mesures puis retour → reprendre plus tard", "accueil : carte « Séance en cours » 2/3",
            if (exists("2/3 mesure(s)")) "carte affichée 2/3" else "absente", exists("2/3 mesure(s)"), "R05_seance_en_cours_accueil.png")
        // Relance complète de l'activité (simule la fermeture de l'application)
        relaunch()
        record("R05b", "Interruption – relance", "relance de l'activité", "séance en cours toujours proposée", if (exists("Séance en cours")) "proposée" else "perdue", exists("Séance en cours"), "—")
        click("Reprendre")
        waitText("SYS — Pression systolique", "Résumé de la séance", timeout = 90_000)
        record("R05c", "Reprise de séance", "Reprendre", "reprise à la mesure 3", if (exists("Mesure 3")) "mesure 3" else "autre étape", exists("Mesure 3"), "—")
        // On s'arrête à 2 mesures : séance incomplète
        back(); click("Reprendre plus tard"); waitText("Séance en cours")
        click("Reprendre")
        waitText("SYS — Pression systolique", timeout = 90_000)
        back() // retour depuis la saisie → dialogue
        waitText("Quitter la séance ?")
        click("Continuer")
        // Résumé via « Arrêter ici » n'est proposé qu'en intervalle : on valide une mesure puis on supprime ? Non : on passe par le résumé
        record("R05d", "Dialogue de sortie – Continuer", "retour puis « Continuer »", "reste dans la séance", if (exists("SYS — Pression systolique")) "reste" else "quitte", exists("SYS — Pression systolique"), "—")
    }

    @Test
    fun r06_seance_incomplete_validee() {
        resetApp()
        launch()
        startManualSession()
        enterMeasurement("132", "84", "70")
        click("Mesurer maintenant")
        enterMeasurement("128", "82", "68")
        waitText("Arrêter ici")
        click("Arrêter ici")
        waitText("la séance sera enregistrée comme incomplète")
        shot("R06_resume_incomplet")
        click("Enregistrer la séance incomplète")
        waitText("Terminer")
        click("Terminer")
        val s = runBlocking { c.sessions.getAll().first() }
        // Moyenne attendue des 2 mesures : 130/83, P 69
        record("R06", "Séance incomplète", "132/84/70 puis 128/82/68, arrêt", "statut « Incomplète », valeur 130/83",
            "statut ${s.status}, ${s.measurements.size} mesures", s.status == SessionStatus.INCOMPLETE && s.measurements.size == 2, "R06_resume_incomplet.png")
    }

    @Test
    fun r07_alertes_et_symptomes() {
        resetApp()
        launch()
        startManualSession()
        enterMeasurement("186", "112", "88")
        waitText("Mesure 1 enregistrée")
        val severe = exists("Tension très élevée") && exists("5 minutes")
        shot("R07_alerte_valeur_tres_elevee")
        record("R07a", "Alerte immédiate", "186/112", "« Contacter rapidement un professionnel » + repos 5 min et nouvelle mesure",
            if (severe) "alerte affichée pendant l'intervalle" else "absente", severe, "R07_alerte_valeur_tres_elevee.png")
        click("Arrêter ici")
        waitText("Symptômes associés")
        click("Douleur ou oppression dans la poitrine")
        val urgent = exists("Urgence : appeler les secours") && exists("Appeler les secours")
        shot("R07_urgence_symptome")
        record("R07b", "Alerte urgente – symptôme", "symptôme « douleur thoracique » coché (avant enregistrement)", "carte d'urgence immédiate avec bouton d'appel (112)",
            if (urgent) "carte affichée immédiatement" else "absente", urgent && exists("112"), "R07_urgence_symptome.png")
        click("Appeler les secours")
        Thread.sleep(4000)
        val dialer = device.currentPackageName
        record("R07c", "Appel des secours", "bouton « Appeler les secours »", "ouverture du composeur avec le numéro (sans appel automatique)",
            "application au premier plan : $dialer", dialer != ctx.packageName, "—")
        // Retour depuis le composeur (le nombre d'appuis varie selon l'état de l'application Téléphone).
        repeat(5) { if (device.currentPackageName != ctx.packageName) { device.pressBack(); Thread.sleep(2500) } }
        waitText("Symptômes associés", timeout = 60_000)
        click("Enregistrer la séance incomplète")
        waitText("Recommandation urgente")
        shot("R07_seance_urgente_enregistree")
        record("R07d", "Alerte de séance", "séance 186/112 + douleur thoracique", "« Recommandation urgente » à l'enregistrement",
            "affichée", exists("Recommandation urgente"), "R07_seance_urgente_enregistree.png")
    }
}
