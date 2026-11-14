package com.cardiosuivi.domain.ai

import com.cardiosuivi.domain.analysis.AnalysisReport
import com.cardiosuivi.domain.model.Profile
import com.cardiosuivi.domain.model.Sex
import com.cardiosuivi.domain.model.Treatment
import com.cardiosuivi.domain.stats.GroupStats
import com.cardiosuivi.domain.stats.PeriodReport
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.Period
import kotlin.math.round

/** Options de partage choisies par l'utilisateur lors du consentement. */
data class AiSharingOptions(
    val includeAgeAndSex: Boolean = true,
    val includeTreatments: Boolean = false,
    val includeMedicalHistory: Boolean = false,
    val includeRecentSessions: Boolean = true,
)

@Serializable
data class AiGroup(val sessions: Int, val measurements: Int, val sysMean: Double?, val diaMean: Double?, val pulseMean: Double?, val sysSd: Double?, val diaSd: Double?)

@Serializable
data class AiPeriod(
    val label: String,
    val from: String,
    val to: String,
    val daysWithData: Int,
    val calendarDays: Long,
    val overall: AiGroup,
    val morning: AiGroup,
    val evening: AiGroup,
    val trendSysPerWeek: Double?,
    val trendDiaPerWeek: Double?,
    val trendR2Sys: Double?,
    val trendR2Dia: Double?,
    val trendUnavailableReason: String?,
    val expectedSlots: Int?,
    val missedSlots: Int?,
    val sessionsAboveTarget: Int?,
)

@Serializable
data class AiSession(val date: String, val period: String, val status: String, val readings: List<String>, val sessionValue: String?, val symptoms: List<String>)

@Serializable
data class AiContext(
    val generatedAt: String,
    val aggregationMethod: String,
    val ageYears: Int?,
    val sex: String?,
    val personalTarget: String?,
    val treatments: List<String>?,
    val medicalHistory: String?,
    val periods: List<AiPeriod>,
    val findings: List<String>,
    val pointsToDiscuss: List<String>,
    val limitations: List<String>,
    val localAlerts: List<String>,
    val recentSessions: List<AiSession>?,
)

/**
 * Construit le prompt système et le contexte structuré envoyés au fournisseur d'IA.
 * Le contexte ne contient NI nom, NI date de naissance exacte : uniquement des agrégats
 * et, si l'utilisateur l'accepte, l'âge, le sexe, les traitements et les antécédents.
 */
object AiPromptBuilder {

    private val json = Json { prettyPrint = false; explicitNulls = false; encodeDefaults = true }

    fun systemPrompt(emergencyNumber: String): String = """
Tu es l'assistant d'analyse de l'application CardioSuivi, outil personnel d'aide au suivi de la tension artérielle à domicile.
Tu t'adresses à l'utilisateur en français, avec clarté, prudence et sans jargon inutile.

Ce que tu reçois : des résultats statistiques déjà calculés par le moteur local déterministe de l'application (JSON), puis la question de l'utilisateur.
Appuie-toi exclusivement sur ces données ; n'invente aucune mesure, ne complète aucune donnée manquante et cite les effectifs.
Distingue toujours moyenne de séance, moyenne journalière et moyenne sur plusieurs jours.

Repères (à présenter comme tels, le médecin peut fixer un autre objectif) : en automesure, une moyenne ≥ 135/85 mmHg sur plusieurs jours correspond au seuil d'hypertension à domicile (ESC 2024, ESH 2023) ; < 120/70 mmHg correspond à une pression non élevée ; une valeur ≥ 180/110 mmHg qui se confirme après repos justifie un contact médical rapide ; avec douleur thoracique, essoufflement, signes neurologiques, troubles de la parole ou de la vision : appel immédiat des secours ($emergencyNumber).

Règles impératives :
- Tu ne poses aucun diagnostic définitif et tu ne te présentes jamais comme un médecin ou comme l'équivalent d'une consultation.
- Tu refuses de modifier, d'arrêter, de suspendre ou d'ajuster une prescription, et tu ne proposes aucune posologie ni aucun médicament. Dans ce cas, réponds que seul le médecin prescripteur peut le décider.
- Si la question évoque des symptômes d'urgence, commence par recommander d'appeler les secours ($emergencyNumber).
- Signale clairement lorsque les données sont insuffisantes pour conclure.

Structure OBLIGATOIRE de chaque réponse, avec exactement ces titres numérotés :
1. Données observées
2. Résultats statistiques
3. Interprétation
4. Incertitudes et limites
5. Recommandations générales
6. Situations nécessitant un avis médical
""".trimIndent()

    fun buildContext(report: AnalysisReport, profile: Profile, treatments: List<Treatment>, options: AiSharingOptions, today: LocalDate): AiContext {
        fun g(s: GroupStats) = AiGroup(s.sessionCount, s.measurementCount, r1(s.systolic.mean), r1(s.diastolic.mean), r1(s.pulse.mean), r1(s.systolic.sd), r1(s.diastolic.sd))
        fun p(label: String, r: PeriodReport) = AiPeriod(
            label, r.from.toString(), r.to.toString(), r.daysWithData, r.calendarDays,
            g(r.overall), g(r.morningVsEvening.a), g(r.morningVsEvening.b),
            r1(r.trend?.systolicSlopePerWeek), r1(r.trend?.diastolicSlopePerWeek), r2(r.trend?.systolicR2), r2(r.trend?.diastolicR2),
            r.trendUnavailableReason, r.completeness?.expectedSlots, r.completeness?.missedSlots, r.targetExceedance?.sessionsAbove,
        )
        val age = if (options.includeAgeAndSex) profile.birthDate?.let { Period.between(it, today).years } else null
        val sex = if (options.includeAgeAndSex && profile.sex != Sex.UNSPECIFIED) profile.sex.label else null
        val sessions = if (options.includeRecentSessions) {
            report.last30.sessionValues.takeLast(15).map { sv ->
                AiSession(
                    sv.session.localDate.toString(), sv.session.period.label, sv.session.status.label,
                    sv.session.measurements.sortedBy { it.index }.map { m -> "${m.systolic}/${m.diastolic}" + (m.pulse?.let { " P$it" } ?: "") },
                    sv.stats.primary?.let { "${r1(it.systolic)}/${r1(it.diastolic)}" },
                    sv.session.symptoms.map { it.label },
                )
            }
        } else null
        return AiContext(
            generatedAt = report.generatedAt.toLocalDate().toString(),
            aggregationMethod = report.method.label,
            ageYears = age,
            sex = sex,
            personalTarget = profile.target?.let { "< ${it.systolic}/${it.diastolic} mmHg" },
            treatments = if (options.includeTreatments) treatments.filter { it.active }.map { t ->
                listOfNotNull(t.name, t.dosage.takeIf { it.isNotBlank() }, t.usualTime?.let { "prise habituelle $it" }).joinToString(", ")
            } else null,
            medicalHistory = if (options.includeMedicalHistory) profile.medicalHistory.takeIf { it.isNotBlank() } else null,
            periods = listOfNotNull(p("7 jours", report.last7), p("30 jours", report.last30), p("90 jours", report.last90), report.custom?.let { p("Période personnalisée", it) }),
            findings = report.findings.map { "${it.title} : ${it.text}" },
            pointsToDiscuss = report.pointsToDiscuss,
            limitations = report.limitations,
            localAlerts = report.alerts.map { "${it.level.label} – ${it.title} : ${it.message}" },
            recentSessions = sessions,
        )
    }

    fun contextJson(context: AiContext): String = json.encodeToString(context)

    fun userMessage(context: AiContext, question: String): String =
        "Données structurées du moteur local (JSON) :\n${contextJson(context)}\n\nQuestion de l'utilisateur : $question"

    private fun r1(x: Double?) = x?.let { round(it * 10) / 10 }
    private fun r2(x: Double?) = x?.let { round(it * 100) / 100 }
}

/**
 * Garde-fou local, appliqué AVANT tout envoi : les demandes de modification de traitement ou de posologie
 * reçoivent une réponse locale de refus, sans transmission de données.
 */
object AiSafetyGuard {
    private val patterns = listOf(
        Regex("\\bposologie\\b"),
        Regex("\\bdos(e|age|es)\\b"),
        Regex("\\b\\d+\\s?mg\\b"),
        Regex("(augmenter|diminuer|baisser|r[ée]duire|doubler|arr[êe]ter|stopper|suspendre|changer|modifier|remplacer|sauter)\\b.{0,40}\\b(traitement|m[ée]dicament|comprim[ée]s?|cachets?|pilules?|prescription|ordonnance)"),
        Regex("(traitement|m[ée]dicament|comprim[ée]s?)\\b.{0,40}\\b(augmenter|diminuer|doubler|arr[êe]ter|changer|modifier)"),
        Regex("(quel|quels|quelle)\\s+(m[ée]dicament|traitement)\\s+(dois|devrais|faut)"),
        Regex("(dois|devrais|peux)[- ]je\\s+(prendre|arr[êe]ter|augmenter|diminuer)\\s+(un|une|mon|ma|mes|le|la|les|plus|moins)"),
    )

    fun isPrescriptionRequest(question: String): Boolean {
        val q = question.lowercase()
        return patterns.any { it.containsMatchIn(q) }
    }

    const val REFUSAL =
        "Je ne peux pas vous conseiller de modifier, d'arrêter ou d'ajuster un traitement, ni proposer une posologie : " +
            "seul votre médecin prescripteur peut prendre cette décision, en tenant compte de votre situation complète. " +
            "Vous pouvez en revanche lui présenter le rapport PDF de vos mesures. Ne modifiez pas votre traitement de vous-même. " +
            "En cas de symptômes inquiétants, contactez un professionnel de santé ou les services d'urgence."
}
