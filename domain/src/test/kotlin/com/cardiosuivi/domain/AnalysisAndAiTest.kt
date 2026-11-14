package com.cardiosuivi.domain

import com.cardiosuivi.domain.Fixtures.ZONE
import com.cardiosuivi.domain.Fixtures.r
import com.cardiosuivi.domain.Fixtures.session
import com.cardiosuivi.domain.ai.AiPromptBuilder
import com.cardiosuivi.domain.ai.AiSafetyGuard
import com.cardiosuivi.domain.ai.AiSharingOptions
import com.cardiosuivi.domain.analysis.CardioAnalysisEngine
import com.cardiosuivi.domain.analysis.HbpmProtocol
import com.cardiosuivi.domain.analysis.LocalAssistant
import com.cardiosuivi.domain.analysis.PresetQuestion
import com.cardiosuivi.domain.model.AggregationMethod
import com.cardiosuivi.domain.model.BpTarget
import com.cardiosuivi.domain.model.Profile
import com.cardiosuivi.domain.model.ProtocolType
import com.cardiosuivi.domain.model.ReminderConfig
import com.cardiosuivi.domain.model.SessionPeriod
import com.cardiosuivi.domain.model.Sex
import com.cardiosuivi.domain.model.Treatment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class AnalysisAndAiTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val now = ZonedDateTime.of(today, LocalTime.of(21, 0), ZONE)

    private fun sessions() = (0 until 12).flatMap { i ->
        val d = today.minusDays(i.toLong() * 2)
        listOf(
            session(d, SessionPeriod.MORNING, readings = listOf(r(146, 90), r(144, 89), r(143, 88))),
            session(d, SessionPeriod.EVENING, readings = listOf(r(132, 82), r(130, 81), r(129, 80))),
        )
    }

    @Test
    fun analysisProducesFindingsAndPointsToDiscuss() {
        val profile = Profile(displayName = "Test", target = BpTarget(135, 85))
        val report = CardioAnalysisEngine().analyze(sessions(), profile, AggregationMethod.ALL_MEASUREMENTS, ReminderConfig(), now)
        assertTrue(report.findings.any { it.title.startsWith("Moyenne sur 7 jours") })
        assertTrue(report.findings.any { it.title.startsWith("Comparaison matin / soir") })
        assertTrue(report.limitations.any { it.contains("Moyenne de toutes les mesures") })
        // Matin ≈ 144/89 et soir ≈ 130/81 : écart matin/soir ≥ 10 mmHg → point à discuter
        assertTrue(report.pointsToDiscuss.any { it.contains("matin/soir") })
    }

    @Test
    fun localAssistantAlwaysReturnsSixSections() {
        val report = CardioAnalysisEngine().analyze(sessions(), Profile(), AggregationMethod.ALL_MEASUREMENTS, ReminderConfig(), now)
        for (q in PresetQuestion.entries) {
            val a = LocalAssistant.answer(q, report, "112")
            val text = a.asText()
            listOf("1. Données observées", "2. Résultats statistiques", "3. Interprétation", "4. Incertitudes et limites",
                "5. Recommandations générales", "6. Situations nécessitant un avis médical").forEach {
                assertTrue("Section manquante « $it » pour $q", text.contains(it))
            }
            assertTrue(a.whenToSeekCare.contains("112"))
        }
        val me = LocalAssistant.answer(PresetQuestion.MORNING_EVENING, report)
        assertTrue(me.statistics, me.statistics.contains("-14,0"))
    }

    @Test
    fun emptyDataIsHandled() {
        val report = CardioAnalysisEngine().analyze(emptyList(), Profile(), AggregationMethod.LAST_TWO, null, now)
        assertNull(report.lastSession)
        val a = LocalAssistant.answer(PresetQuestion.MEAN_7D, report)
        assertTrue(a.interpretation.contains("Aucune séance"))
    }

    @Test
    fun aiContextExcludesIdentityAndOptionalData() {
        val profile = Profile(displayName = "Jean Exemple", birthDate = LocalDate.of(1960, 5, 1), sex = Sex.MALE, medicalHistory = "antécédent X")
        val report = CardioAnalysisEngine().analyze(sessions(), profile, AggregationMethod.ALL_MEASUREMENTS, ReminderConfig(), now)
        val treatments = listOf(Treatment(name = "MédicamentTest", dosage = "5 mg"))
        val ctx = AiPromptBuilder.buildContext(report, profile, treatments, AiSharingOptions(), today)
        val json = AiPromptBuilder.contextJson(ctx)
        assertFalse(json.contains("Jean"))
        assertFalse(json.contains("1960"))
        assertFalse(json.contains("MédicamentTest"))
        assertFalse(json.contains("antécédent X"))
        assertEquals(66, ctx.ageYears)

        val ctx2 = AiPromptBuilder.buildContext(report, profile, treatments, AiSharingOptions(includeAgeAndSex = false, includeTreatments = true, includeMedicalHistory = true), today)
        val json2 = AiPromptBuilder.contextJson(ctx2)
        assertTrue(json2.contains("MédicamentTest"))
        assertNull(ctx2.ageYears)
        assertTrue(AiPromptBuilder.systemPrompt("112").contains("6. Situations nécessitant un avis médical"))
    }

    @Test
    fun safetyGuardDetectsPrescriptionRequests() {
        listOf(
            "Dois-je augmenter mon traitement ?",
            "Je peux arrêter mes comprimés ?",
            "Quelle posologie pour mon amlodipine ?",
            "Faut-il passer à 10 mg ?",
            "Est-ce que je peux doubler la dose ce soir",
            "quel médicament dois-je prendre",
        ).forEach { assertTrue(it, AiSafetyGuard.isPrescriptionRequest(it)) }
        listOf(
            "Comment ma tension évolue-t-elle ?",
            "Ma tension diastolique augmente-t-elle ?",
            "Quelle est ma moyenne sur les sept derniers jours ?",
            "Quelles mesures d'hygiène de vie sont recommandées ?",
        ).forEach { assertFalse(it, AiSafetyGuard.isPrescriptionRequest(it)) }
    }

    @Test
    fun hbpmProtocolDiscardsFirstDay() {
        val start = LocalDate.of(2026, 11, 2)
        val s = (0 until 7).flatMap { i ->
            val d = start.plusDays(i.toLong())
            val sys = if (i == 0) 170 else 130
            listOf(
                session(d, SessionPeriod.MORNING, readings = listOf(r(sys, 80), r(sys, 80)), protocol = ProtocolType.STANDARDIZED_HBPM),
                session(d, SessionPeriod.EVENING, readings = listOf(r(sys, 80), r(sys, 80)), protocol = ProtocolType.STANDARDIZED_HBPM),
            )
        }
        val res = HbpmProtocol.evaluate(s, start)
        assertEquals(HbpmProtocol.Status.COMPLETE, res.status)
        assertEquals(6, res.usableDays)
        assertEquals(24, res.readingsUsed)
        assertEquals(130.0, res.meanSystolic!!, 1e-9)
        assertEquals(HbpmProtocol.Classification.ELEVATED, res.classification)

        val partial = HbpmProtocol.evaluate(s.take(4), start)
        assertEquals(HbpmProtocol.Status.IN_PROGRESS, partial.status)
        assertNull(partial.classification)
        // Séances habituelles non comptées
        assertEquals(HbpmProtocol.Status.NOT_STARTED, HbpmProtocol.evaluate(sessions(), null).status)
    }
}
