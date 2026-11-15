package com.cardiosuivi.app.recette

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardiosuivi.domain.model.Measurement
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.model.MeasurementSource
import com.cardiosuivi.domain.model.Profile
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.SessionStatus
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Intégration IA testée contre un SERVEUR SIMULÉ local (MockWebServer sur l'appareil).
 * Aucune requête n'est envoyée à un fournisseur réel ; données fictives uniquement.
 */
@RunWith(AndroidJUnit4::class)
class R12_IntelligenceArtificielleTest : RecetteBase() {

    private val server = MockWebServer()

    @After
    fun stop() {
        c.aiBaseUrlOverride = null
        runCatching { server.shutdown() }
    }

    private fun anthropicOk(text: String) = MockResponse().setResponseCode(200).setBody(
        """{"id":"msg_test","type":"message","role":"assistant","stop_reason":"end_turn","content":[{"type":"text","text":${org.json.JSONObject.quote(text)}}]}""",
    )

    private fun seed() = runBlocking {
        c.profile.saveProfile(Profile(displayName = "RECETTE-FICTIF", birthDate = LocalDate.of(1958, 6, 15)))
        val zone = ZoneId.systemDefault()
        val d = LocalDate.now(zone).minusDays(1)
        val st = ZonedDateTime.of(d, java.time.LocalTime.of(7, 5), zone).toInstant()
        c.sessions.insertFull(MeasurementSession(startedAt = st, period = SessionPeriod.MORNING, status = SessionStatus.COMPLETED, localDate = d,
            measurements = (1..3).map { Measurement(index = it, systolic = 131, diastolic = 83, pulse = 66, measuredAt = st.plusSeconds(60L * it), source = MeasurementSource.MANUAL) }))
    }

    @Test
    fun r12_ia_serveur_simule() {
        server.start()
        c.aiBaseUrlOverride = server.url("/").toString().trimEnd('/')
        resetApp(); seed(); launch()
        click("Plus"); click("Assistant IA"); click("Configurer l'IA")
        click("Anthropic Claude")
        waitText("Aucune clé enregistrée.")
        type("Nouvelle clé", "clé avec accent")
        click("Enregistrer")
        val rejected = exists("Clé invalide")
        record("R12a", "IA – clé invalide", "« clé avec accent »", "clé refusée (caractères non autorisés)", if (rejected) "refusée" else "acceptée", rejected, "—")
        type("Nouvelle clé", "sk-ant-TEST-FICTIVE-0000")
        click("Enregistrer")
        waitText("Une clé est enregistrée")
        shot("R12_cle_enregistree")
        val stored = c.secrets.get("api_key_anthropic") == "sk-ant-TEST-FICTIVE-0000"
        val rawPrefs = File(ctx.dataDir, "shared_prefs/cardiosuivi_secrets.xml").readText()
        record("R12b", "IA – stockage de la clé", "sk-ant-TEST-FICTIVE-0000", "clé chiffrée (absente en clair du fichier de préférences), relisible par l'application",
            "relue=${stored}, en clair dans le fichier=${rawPrefs.contains("TEST-FICTIVE")}", stored && !rawPrefs.contains("TEST-FICTIVE"), "R12_cle_enregistree.png")

        server.enqueue(anthropicOk("OK"))
        click("Tester (sans données de santé)")
        waitText("Connexion réussie", "Échec", timeout = 90_000)
        val testReq = server.takeRequest(60, java.util.concurrent.TimeUnit.SECONDS)!!
        val testBody = testReq.body.readUtf8()
        shot("R12_test_connexion")
        record("R12c", "IA – test de connexion", "serveur simulé répond « OK »", "« Connexion réussie » ; requête sans aucune donnée de santé ; en-têtes x-api-key et anthropic-version",
            "statut=${if (exists("Connexion réussie")) "réussie" else "échec"} ; corps=${testBody.take(160)} ; version=${testReq.getHeader("anthropic-version")}",
            exists("Connexion réussie") && !testBody.contains("131") && !testBody.contains("RECETTE") && testReq.getHeader("x-api-key") == "sk-ant-TEST-FICTIVE-0000",
            "R12_test_connexion.png")

        back()
        type("Votre question", "Comment ma tension évolue-t-elle ?")
        click("Demander à l'IA")
        waitText("Consentement à la transmission")
        shot("R12_consentement")
        val before = server.requestCount
        record("R12d", "IA – consentement", "première question", "dialogue de consentement AVANT tout envoi ; aucune requête", "dialogue affiché, requêtes=${server.requestCount - before}",
            exists("Consentement à la transmission"), "R12_consentement.png")
        click("Refuser")
        record("R12e", "IA – refus du consentement", "Refuser", "aucune requête envoyée", "requêtes envoyées=${server.requestCount - 1}", server.requestCount == 1, "—")

        click("Demander à l'IA"); click("J'accepte")
        server.enqueue(anthropicOk("1. Données observées\nRÉPONSE SIMULÉE DE TEST\n6. Situations nécessitant un avis médical\n..."))
        Thread.sleep(1500)
        click("Demander à l'IA")
        waitText("RÉPONSE SIMULÉE DE TEST", "Erreur", timeout = 120_000)
        val req = server.takeRequest(60, java.util.concurrent.TimeUnit.SECONDS)!!
        val body = req.body.readUtf8()
        shot("R12_reponse_ia")
        val noIdentity = !body.contains("RECETTE-FICTIF") && !body.contains("1958-06-15") && !body.contains("15/06/1958")
        record("R12f", "IA – question avec consentement", "« Comment ma tension évolue-t-elle ? »",
            "réponse affichée ; requête contenant statistiques et question, SANS nom ni date de naissance",
            "réponse=${exists("RÉPONSE SIMULÉE DE TEST")} ; nom absent=${!body.contains("RECETTE-FICTIF")} ; naissance absente=${!body.contains("1958-06-15")} ; âge=${body.contains("ageYears")} ; question=${body.contains("évolue")}",
            exists("RÉPONSE SIMULÉE DE TEST") && noIdentity && body.contains("évolue") && body.contains("131"), "R12_reponse_ia.png")
        File(outDir, "R12_requete_envoyee.json").writeText(body)

        val n = server.requestCount
        type("Votre question", "Dois-je augmenter mon traitement ?")
        click("Demander à l'IA")
        waitText("Je ne peux pas vous conseiller de modifier")
        shot("R12_garde_fou")
        record("R12g", "IA – garde-fou prescription", "« Dois-je augmenter mon traitement ? »", "refus local, aucune requête envoyée",
            "refus affiché, requêtes supplémentaires=${server.requestCount - n}", server.requestCount == n && exists("Garde-fou local"), "R12_garde_fou.png")

        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""))
        type("Votre question", "Ma tension diastolique augmente-t-elle ?")
        click("Demander à l'IA")
        waitText("Clé d'API invalide", timeout = 90_000)
        shot("R12_erreur_401")
        record("R12h", "IA – erreur d'authentification", "réponse HTTP 401 simulée", "message « Clé d'API invalide » lisible, pas de plantage", "affiché", exists("Clé d'API invalide"), "R12_erreur_401.png")

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"stop_reason":"refusal","content":[]}"""))
        click("Demander à l'IA")
        waitText("décliné", timeout = 90_000)
        record("R12i", "IA – refus du modèle", "stop_reason=refusal simulé", "message explicite, pas de contenu vide", "affiché", exists("décliné"), "—")

        // Absence de connexion : port fermé (aucun serveur)
        c.aiBaseUrlOverride = "http://127.0.0.1:9"
        click("Demander à l'IA")
        waitText("Connexion au service impossible", timeout = 120_000)
        shot("R12_hors_connexion")
        record("R12j", "IA – pas de connexion", "service injoignable", "« Connexion au service impossible », l'analyse locale reste disponible",
            "affiché", exists("Connexion au service impossible"), "R12_hors_connexion.png")
        click("Quelle est ma moyenne sur les sept derniers jours ?")
        waitText("Moteur d'analyse local")
        record("R12k", "Hors connexion – analyse locale", "question prédéfinie", "réponse locale disponible sans réseau", "affichée", exists("1. Données observées"), "—")

        // Retrait du consentement
        click("Configurer l'IA")
        click("Retirer le consentement")
        waitText("Non donné")
        back()
        c.aiBaseUrlOverride = server.url("/").toString().trimEnd('/')
        val n2 = server.requestCount
        type("Votre question", "Question après retrait")
        click("Demander à l'IA")
        waitText("Consentement à la transmission")
        record("R12l", "IA – retrait du consentement", "consentement retiré puis question", "consentement redemandé, aucune requête", "dialogue réaffiché, requêtes=${server.requestCount - n2}",
            server.requestCount == n2, "—")
        click("Refuser")
    }
}
