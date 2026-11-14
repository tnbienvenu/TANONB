package com.cardiosuivi.domain.analysis

import com.cardiosuivi.domain.reference.MedicalThresholds
import com.cardiosuivi.domain.stats.PeriodReport
import com.cardiosuivi.domain.util.Fmt
import kotlin.math.abs

/** Réponse structurée imposée (assistant local comme IA). */
data class StructuredAnswer(
    val observedData: String,
    val statistics: String,
    val interpretation: String,
    val uncertainties: String,
    val generalRecommendations: String,
    val whenToSeekCare: String,
    /** Origine de la réponse (moteur local ou fournisseur d'IA). */
    val source: String,
) {
    fun asText(): String = buildString {
        appendLine("1. Données observées"); appendLine(observedData); appendLine()
        appendLine("2. Résultats statistiques"); appendLine(statistics); appendLine()
        appendLine("3. Interprétation"); appendLine(interpretation); appendLine()
        appendLine("4. Incertitudes et limites"); appendLine(uncertainties); appendLine()
        appendLine("5. Recommandations générales"); appendLine(generalRecommendations); appendLine()
        appendLine("6. Situations nécessitant un avis médical"); append(whenToSeekCare)
    }
}

enum class PresetQuestion(val text: String) {
    EVOLUTION("Comment ma tension évolue-t-elle ?"),
    DIASTOLIC_TREND("Ma tension diastolique augmente-t-elle ?"),
    MEAN_7D("Quelle est ma moyenne sur les sept derniers jours ?"),
    MORNING_EVENING("Quelle différence observe-t-on entre le matin et le soir ?"),
    COHERENCE("Mes trois mesures successives sont-elles cohérentes ?"),
    DISCUSS("Quelles tendances méritent d'être discutées avec mon cardiologue ?"),
    SUFFICIENT("Mes mesures sont-elles suffisamment nombreuses pour conclure ?"),
    LIFESTYLE("Quelles mesures d'hygiène de vie sont généralement recommandées ?"),
}

/**
 * Assistant local : répond aux questions prédéfinies à partir du seul moteur statistique,
 * sans connexion ni transmission de données.
 */
object LocalAssistant {

    const val SOURCE = "Moteur d'analyse local (sans IA, sans transmission de données)"

    fun whenToSeekCare(emergencyNumber: String) =
        "Appelez immédiatement les secours ($emergencyNumber) en cas de douleur thoracique, d'essoufflement important, " +
            "de faiblesse ou d'engourdissement d'un côté du corps, de trouble de la parole ou de la vision, de confusion ou de malaise, " +
            "a fortiori si la tension est très élevée. Contactez rapidement votre médecin si une valeur ≥ 180/110 mmHg se confirme après " +
            "5 minutes de repos. Discutez avec votre médecin si votre moyenne à domicile dépasse durablement votre objectif ou 135/85 mmHg. " +
            "Ne modifiez jamais votre traitement sans avis médical."

    private const val LIFESTYLE =
        "Recommandations générales des sociétés savantes (ESC 2024, ESH 2023, OMS), à adapter avec votre médecin : " +
            "réduire le sel (l'OMS recommande moins de 5 g de sel par jour) ; alimentation riche en fruits, légumes, légumineuses, " +
            "de type méditerranéen ou DASH ; activité physique régulière (au moins 150 minutes par semaine d'intensité modérée selon l'OMS) ; " +
            "maintenir un poids adapté ; limiter l'alcool ; arrêter le tabac ; sommeil suffisant et gestion du stress ; " +
            "prendre son traitement comme prescrit. Ces conseils ne remplacent pas un avis personnalisé."

    fun answer(q: PresetQuestion, report: AnalysisReport, emergencyNumber: String = "112"): StructuredAnswer {
        val r7 = report.last7
        val r30 = report.last30
        val common = commonLimits(r30)
        return when (q) {
            PresetQuestion.EVOLUTION, PresetQuestion.DIASTOLIC_TREND -> {
                val t = r30.trend ?: report.last90.trend
                val window = if (r30.trend != null) "30" else "90"
                val focusDia = q == PresetQuestion.DIASTOLIC_TREND
                StructuredAnswer(
                    observedData = periodLine("30 derniers jours", r30) + "\n" + periodLine("90 derniers jours", report.last90),
                    statistics = if (t == null) (r30.trendUnavailableReason ?: report.last90.trendUnavailableReason ?: "Tendance non calculable.")
                    else "Régression linéaire sur $window jours (${t.n} séances) : systolique ${Fmt.signed1(t.systolicSlopePerWeek)} mmHg/semaine (R² ${Fmt.d1(t.systolicR2)}), " +
                        "diastolique ${Fmt.signed1(t.diastolicSlopePerWeek)} mmHg/semaine (R² ${Fmt.d1(t.diastolicR2)}).",
                    interpretation = if (t == null) "Pas assez de données pour décrire une évolution."
                    else {
                        val slope = if (focusDia) t.diastolicSlopePerWeek else t.systolicSlopePerWeek
                        val r2 = if (focusDia) t.diastolicR2 else t.systolicR2
                        val what = if (focusDia) "La diastolique" else "La systolique"
                        val dir = when {
                            abs(slope) < 1.0 -> "est globalement stable"
                            slope > 0 -> "a tendance à augmenter"
                            else -> "a tendance à diminuer"
                        }
                        "$what $dir sur la période" + if (r2 < 0.3) ", mais la tendance est faiblement marquée (R² < 0,3) : les variations d'une séance à l'autre dominent." else "."
                    },
                    uncertainties = common,
                    generalRecommendations = "Poursuivre des mesures régulières dans les mêmes conditions pour confirmer l'évolution.",
                    whenToSeekCare = whenToSeekCare(emergencyNumber),
                    source = SOURCE,
                )
            }
            PresetQuestion.MEAN_7D -> StructuredAnswer(
                observedData = periodLine("7 derniers jours", r7),
                statistics = "Moyenne des valeurs de séance : ${Fmt.bp(r7.overall.systolic.mean, r7.overall.diastolic.mean)} mmHg, " +
                    "pouls ${Fmt.d0(r7.overall.pulse.mean)}/min ; moyenne des mesures individuelles : " +
                    "${Fmt.bp(r7.individualSystolic.mean, r7.individualDiastolic.mean)} mmHg (${r7.measurementCount} mesures). " +
                    "Méthode de séance : ${report.method.label}.",
                interpretation = r7.overall.systolic.mean?.let { s ->
                    val d = r7.overall.diastolic.mean!!
                    if (r7.daysWithData < MedicalThresholds.HBPM_MIN_DAYS) "Moins de ${MedicalThresholds.HBPM_MIN_DAYS} jours de mesures : la moyenne n'est pas interprétable seule."
                    else "Repère à domicile : ${HbpmProtocol.classify(s, d).label}."
                } ?: "Aucune séance validée sur les 7 derniers jours.",
                uncertainties = commonLimits(r7),
                generalRecommendations = "Comparez cette moyenne à l'objectif fixé avec votre médecin.",
                whenToSeekCare = whenToSeekCare(emergencyNumber),
                source = SOURCE,
            )
            PresetQuestion.MORNING_EVENING -> {
                val c = r30.morningVsEvening
                StructuredAnswer(
                    observedData = "Matin : ${c.a.sessionCount} séance(s) ; soir : ${c.b.sessionCount} séance(s) sur 30 jours.",
                    statistics = "Matin ${Fmt.bp(c.a.systolic.mean, c.a.diastolic.mean)} mmHg ; soir ${Fmt.bp(c.b.systolic.mean, c.b.diastolic.mean)} mmHg ; " +
                        "différence soir − matin ${Fmt.signed1(c.systolicDiff)} / ${Fmt.signed1(c.diastolicDiff)} mmHg.",
                    interpretation = when {
                        c.systolicDiff == null -> "Comparaison impossible : il faut des séances le matin ET le soir."
                        !c.sufficient -> "Effectifs trop faibles (minimum ${c.minPerGroup} séances par moment) pour conclure."
                        abs(c.systolicDiff) < 5 && abs(c.diastolicDiff ?: 0.0) < 5 -> "Peu de différence entre le matin et le soir."
                        else -> "La tension est en moyenne plus ${if (c.systolicDiff > 0) "élevée le soir" else "élevée le matin"} ; c'est un élément utile à signaler à votre médecin, notamment au regard de l'heure de prise du traitement."
                    },
                    uncertainties = common,
                    generalRecommendations = "Mesurez à des heures régulières, avant la prise du traitement le matin si votre médecin le recommande.",
                    whenToSeekCare = whenToSeekCare(emergencyNumber),
                    source = SOURCE,
                )
            }
            PresetQuestion.COHERENCE -> {
                val s = report.lastSession
                val st = report.lastSessionStats
                if (s == null || st == null) StructuredAnswer(
                    "Aucune séance validée.", "—", "Impossible d'évaluer la cohérence sans séance.", "—",
                    "Réalisez une séance de trois mesures.", whenToSeekCare(emergencyNumber), SOURCE,
                ) else {
                    val values = s.measurements.sortedBy { it.index }.joinToString(" ; ") { "${it.index}) ${it.systolic}/${it.diastolic}" + (it.pulse?.let { p -> " pouls $p" } ?: "") }
                    val maxStep = (st.systolicDeltas + st.diastolicDeltas).maxOfOrNull { abs(it) }
                    StructuredAnswer(
                        observedData = "Séance du ${Fmt.date(s.localDate)} (${s.period.label}) : $values.",
                        statistics = "Écart-type systolique ${Fmt.d1(st.systolic.sd)} mmHg, diastolique ${Fmt.d1(st.diastolic.sd)} mmHg ; " +
                            "amplitude ${st.systolic.range ?: "—"} / ${st.diastolic.range ?: "—"} mmHg ; plus grand écart entre deux mesures successives : ${maxStep ?: "—"} mmHg.",
                        interpretation = when {
                            maxStep == null -> "Une seule mesure : cohérence non évaluable."
                            maxStep > MedicalThresholds.INTRA_SESSION_DIFF_MMHG -> "Mesures peu concordantes (écart > ${MedicalThresholds.INTRA_SESSION_DIFF_MMHG} mmHg). Une première mesure plus élevée est fréquente (effet de l'attention/stress) ; vérifiez les conditions de mesure et envisagez une mesure supplémentaire."
                            else -> "Mesures concordantes (écarts ≤ ${MedicalThresholds.INTRA_SESSION_DIFF_MMHG} mmHg)."
                        },
                        uncertainties = "Évaluation sur une seule séance.",
                        generalRecommendations = "Respectez 1 minute entre les mesures, sans parler ni bouger, bras à hauteur du cœur.",
                        whenToSeekCare = whenToSeekCare(emergencyNumber),
                        source = SOURCE,
                    )
                }
            }
            PresetQuestion.DISCUSS -> StructuredAnswer(
                observedData = periodLine("30 derniers jours", r30),
                statistics = report.findings.filter { it.severity >= FindingSeverity.WATCH }.joinToString("\n") { "• ${it.title} : ${it.text}" }.ifBlank { "Aucun constat particulier." },
                interpretation = if (report.pointsToDiscuss.isEmpty()) "Aucun point saillant n'a été détecté par le moteur local ; vos mesures restent utiles à présenter à votre médecin."
                else report.pointsToDiscuss.joinToString("\n") { "• $it" },
                uncertainties = report.limitations.joinToString("\n") { "• $it" },
                generalRecommendations = "Apportez le rapport PDF de l'application lors de votre consultation.",
                whenToSeekCare = whenToSeekCare(emergencyNumber),
                source = SOURCE,
            )
            PresetQuestion.SUFFICIENT -> {
                val enough = r7.daysWithData >= MedicalThresholds.HBPM_MIN_DAYS
                StructuredAnswer(
                    observedData = periodLine("7 derniers jours", r7) + "\n" + periodLine("30 derniers jours", r30),
                    statistics = r30.completeness?.let { "Taux de réalisation des séances programmées (30 j) : ${Fmt.pct(it.completionRate)} ; manquées : ${it.missedSlots}." } ?: "Pas de calendrier de séances configuré.",
                    interpretation = if (enough) "Le nombre de jours de mesure récents (${r7.daysWithData}) atteint le minimum de ${MedicalThresholds.HBPM_MIN_DAYS} jours retenu par les recommandations pour estimer une moyenne à domicile ; 7 jours sont idéaux."
                    else "Insuffisant : ${r7.daysWithData} jour(s) de mesure sur 7 jours. Les recommandations demandent au moins ${MedicalThresholds.HBPM_MIN_DAYS} jours (idéalement ${MedicalThresholds.HBPM_RECOMMENDED_DAYS}), matin et soir. Le protocole d'automesure standardisée de l'application peut vous guider.",
                    uncertainties = "Votre calendrier habituel (3 jours par semaine) diffère du protocole standardisé sur 7 jours consécutifs.",
                    generalRecommendations = "Avant une consultation, réalisez si possible le protocole standardisé sur 7 jours.",
                    whenToSeekCare = whenToSeekCare(emergencyNumber),
                    source = SOURCE,
                )
            }
            PresetQuestion.LIFESTYLE -> StructuredAnswer(
                observedData = "Question générale, indépendante de vos mesures.",
                statistics = "—",
                interpretation = "Les mesures hygiéno-diététiques font partie de la prise en charge de la tension artérielle, en complément d'un éventuel traitement.",
                uncertainties = "Recommandations générales, non personnalisées.",
                generalRecommendations = LIFESTYLE,
                whenToSeekCare = whenToSeekCare(emergencyNumber),
                source = SOURCE,
            )
        }
    }

    private fun periodLine(label: String, r: PeriodReport): String =
        "$label : ${r.sessionCount} séance(s), ${r.measurementCount} mesure(s), ${r.daysWithData} jour(s) avec mesures ; " +
            "moyenne ${Fmt.bp(r.overall.systolic.mean, r.overall.diastolic.mean)} mmHg, pouls ${Fmt.d0(r.overall.pulse.mean)}/min."

    private fun commonLimits(r: PeriodReport): String = buildString {
        append("Calculs fondés uniquement sur ${r.sessionCount} séance(s) validée(s). ")
        if (r.daysWithData < MedicalThresholds.HBPM_MIN_DAYS) append("Moins de ${MedicalThresholds.HBPM_MIN_DAYS} jours de mesures. ")
        r.completeness?.let { if (it.missedSlots > 0) append("${it.missedSlots} séance(s) programmée(s) manquée(s). ") }
        append("Aucune valeur manquante n'est estimée.")
    }
}
