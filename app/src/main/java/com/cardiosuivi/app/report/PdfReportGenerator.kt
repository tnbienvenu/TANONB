package com.cardiosuivi.app.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.cardiosuivi.domain.alerts.AlertLevel
import com.cardiosuivi.domain.analysis.AnalysisReport
import com.cardiosuivi.domain.model.Profile
import com.cardiosuivi.domain.model.Treatment
import com.cardiosuivi.domain.reference.MedicalReference
import com.cardiosuivi.domain.reference.MedicalThresholds
import com.cardiosuivi.domain.stats.GroupStats
import com.cardiosuivi.domain.stats.PeriodReport
import com.cardiosuivi.domain.stats.SessionValue
import com.cardiosuivi.domain.util.Fmt
import java.io.File
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class ReportData(
    val profile: Profile,
    val treatments: List<Treatment>,
    val period: PeriodReport,
    val analysis: AnalysisReport,
    val aiText: String?,
    val aiSource: String?,
    val intervalSeconds: Int,
    val measurementsPerSession: Int,
    val generatedAt: ZonedDateTime,
    val zone: ZoneId,
)

/** Génère le rapport médical PDF (android.graphics.pdf.PdfDocument, sans dépendance externe). */
class PdfReportGenerator(private val context: Context) {

    fun generate(data: ReportData): File {
        val doc = PdfDocument()
        val w = Writer(doc, data)
        try {
            w.render()
            w.finish()
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val f = File(dir, "CardioSuivi_rapport_${data.period.from}_${data.period.to}.pdf")
            f.outputStream().use { doc.writeTo(it) }
            return f
        } finally {
            doc.close()
        }
    }

    private class Writer(private val doc: PdfDocument, private val d: ReportData) {
        private val pageW = 595
        private val pageH = 842
        private val margin = 40f
        private val contentW = pageW - 2 * margin
        private var pageNo = 0
        private lateinit var page: PdfDocument.Page
        private lateinit var c: Canvas
        private var y = 0f

        private val brand = Color.rgb(15, 92, 120)
        private val sysColor = Color.rgb(198, 40, 40)
        private val diaColor = Color.rgb(21, 101, 192)
        private val pulseColor = Color.rgb(46, 125, 50)

        private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = brand }
        private val h1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = brand }
        private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.rgb(30, 30, 30) }
        private val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7.5f; color = Color.DKGRAY }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; strokeWidth = 0.6f }

        fun render() {
            newPage()
            c.drawText("CardioSuivi — Rapport de suivi tensionnel à domicile", margin, y + 18, title); y += 30
            para("Document d'aide au suivi produit par une application personnelle. Il ne constitue ni un diagnostic, ni une prescription, et ne remplace pas l'avis d'un médecin.", small)
            y += 6

            section("1. Identification")
            val p = d.profile
            val age = p.birthDate?.let { Period.between(it, d.generatedAt.toLocalDate()).years }
            kv("Nom ou pseudonyme", p.displayName.ifBlank { "Non renseigné" })
            kv("Date de naissance", p.birthDate?.let { "${Fmt.date(it)} (${age} ans)" } ?: "Non renseignée")
            kv("Sexe", p.sex.label)
            kv("Taille / poids", "${p.heightCm?.let { "$it cm" } ?: "—"} / ${p.weightKg?.let { "${Fmt.d1(it)} kg" } ?: "—"}")
            kv("Objectif personnel", p.target?.let { "< ${it.systolic}/${it.diastolic} mmHg" } ?: "Non renseigné")
            val active = d.treatments.filter { it.active }
            kv("Traitement(s)", if (active.isEmpty()) "Non renseigné" else active.joinToString(" ; ") { t ->
                listOfNotNull(t.name, t.dosage.ifBlank { null }, t.usualTime?.let { "prise à $it" }).joinToString(", ")
            })
            if (p.medicalHistory.isNotBlank()) kv("Antécédents", p.medicalHistory)
            if (p.observations.isNotBlank()) kv("Observations", p.observations)

            section("2. Période analysée")
            val r = d.period
            kv("Période", "du ${Fmt.date(r.from)} au ${Fmt.date(r.to)} (${r.calendarDays} jours)")
            kv("Données", "${r.sessionCount} séance(s) validée(s) dont ${r.incompleteSessionCount} incomplète(s), ${r.measurementCount} mesure(s), ${r.daysWithData} jour(s) avec mesures")
            r.completeness?.let { kv("Séances programmées", "${it.expectedSlots} : réalisées ${it.doneSlots}, incomplètes ${it.incompleteSlots}, manquées ${it.missedSlots}, à venir ${it.pendingSlots}") }
            kv("Généré le", d.generatedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))

            section("3. Protocole de mesure")
            para("Séances de ${d.measurementsPerSession} mesures successives espacées d'environ ${d.intervalSeconds} s, après 5 minutes de repos, assis, dos soutenu, pieds au sol, bras à hauteur du cœur, sans parler. " +
                "Valeurs recopiées manuellement depuis l'écran du tensiomètre, au bras gauche ; date et heure enregistrées automatiquement par l'application. " +
                "Les séances antérieures à la version 1.1 peuvent comporter des valeurs lues par photographie (validées par l'utilisateur).")
            para("Méthode de calcul de la valeur de séance : ${r.method.label}. ${r.method.description} Les moyennes de période sont des moyennes de valeurs de séance ; les moyennes journalières sont des moyennes des séances d'un même jour.", bold)

            section("4–5. Mesures et moyennes de chaque séance")
            sessionTable(r.sessionValues)

            section("6. Moyennes générales")
            table(
                listOf("Indicateur", "Systolique", "Diastolique", "Pouls", "Pression pulsée", "n"),
                listOf(
                    groupRow("Moyenne des séances", r.overall),
                    listOf("Moyenne des moyennes journalières", Fmt.d1(r.meanOfDailyMeansSystolic), Fmt.d1(r.meanOfDailyMeansDiastolic), "—", "—", "${r.daily.size} j"),
                    listOf("Mesures individuelles", Fmt.d1(r.individualSystolic.mean), Fmt.d1(r.individualDiastolic.mean), Fmt.d1(r.individualPulse.mean), "—", "${r.measurementCount}"),
                    listOf("Minimum – maximum (séances)", "${r.overall.systolic.min ?: "—"} – ${r.overall.systolic.max ?: "—"}", "${r.overall.diastolic.min ?: "—"} – ${r.overall.diastolic.max ?: "—"}", "${r.overall.pulse.min ?: "—"} – ${r.overall.pulse.max ?: "—"}", "—", ""),
                ),
                floatArrayOf(0.34f, 0.14f, 0.14f, 0.12f, 0.16f, 0.10f),
            )
            r.trend?.let { para("Tendance : systolique ${Fmt.signed1(it.systolicSlopePerWeek)} mmHg/semaine (R² ${Fmt.d1(it.systolicR2)}), diastolique ${Fmt.signed1(it.diastolicSlopePerWeek)} mmHg/semaine (R² ${Fmt.d1(it.diastolicR2)}), ${it.n} séances sur ${it.spanDays} jours.") }
                ?: para(r.trendUnavailableReason ?: "Tendance non calculée.")

            section("7. Graphiques")
            lineChart("Pression artérielle (valeurs de séance, mmHg)", r.sessionValues)
            pulseChart(r.sessionValues)

            section("8. Variabilité")
            val v = r.variability
            table(
                listOf("Indicateur", "Systolique", "Diastolique"),
                listOf(
                    listOf("Écart-type entre séances (mmHg)", Fmt.d1(v.systolicSdBetweenSessions), Fmt.d1(v.diastolicSdBetweenSessions)),
                    listOf("Coefficient de variation (%)", Fmt.d1(v.systolicCv), Fmt.d1(v.diastolicCv)),
                    listOf("Variabilité moyenne entre séances successives (mmHg)", Fmt.d1(v.systolicArv), Fmt.d1(v.diastolicArv)),
                    listOf("Écart-type moyen au sein d'une séance (mmHg)", Fmt.d1(v.meanWithinSessionSystolicSd), Fmt.d1(v.meanWithinSessionDiastolicSd)),
                ),
                floatArrayOf(0.6f, 0.2f, 0.2f),
            )

            section("9. Comparaison matin / soir")
            val me = r.morningVsEvening
            table(
                listOf("Moment", "Systolique", "Diastolique", "Pouls", "Pression pulsée", "Séances"),
                listOf(groupRow("Matin", me.a), groupRow("Soir", me.b),
                    listOf("Écart soir − matin", Fmt.signed1(me.systolicDiff), Fmt.signed1(me.diastolicDiff), Fmt.signed1(me.pulseDiff), "—", "")),
                floatArrayOf(0.30f, 0.15f, 0.15f, 0.12f, 0.16f, 0.12f),
            )
            if (!me.sufficient) para("Effectifs insuffisants (minimum ${me.minPerGroup} séances par moment) pour conclure.", small)
            if (r.byWeekday.isNotEmpty()) {
                y += 4
                table(
                    listOf("Jour", "Systolique", "Diastolique", "Pouls", "Séances"),
                    r.byWeekday.map { (day, g) -> listOf(day.getDisplayName(java.time.format.TextStyle.FULL, Fmt.LOCALE), Fmt.d1(g.systolic.mean), Fmt.d1(g.diastolic.mean), Fmt.d1(g.pulse.mean), "${g.sessionCount}") },
                    floatArrayOf(0.3f, 0.18f, 0.18f, 0.16f, 0.18f),
                )
            }

            section("10. Valeurs inhabituelles et alertes")
            val severe = r.sessionValues.filter { com.cardiosuivi.domain.alerts.AlertEngine.isSevere(it.primary.systolic, it.primary.diastolic) }
            if (severe.isEmpty() && r.outliers.isEmpty() && d.analysis.alerts.none { it.level >= AlertLevel.ATTENTION }) para("Aucune valeur inhabituelle détectée sur la période.")
            severe.forEach { para("• ${Fmt.date(it.session.localDate)} (${it.session.period.label}) : séance à ${Fmt.bp(it.primary.systolic, it.primary.diastolic)} mmHg (≥ ${MedicalThresholds.SEVERE.systolic}/${MedicalThresholds.SEVERE.diastolic}).") }
            r.outliers.forEach { para("• ${Fmt.date(it.session.localDate)} : ${it.reason} (critère statistique).") }
            d.analysis.alerts.filter { it.level >= AlertLevel.ATTENTION }.forEach { para("• ${it.level.label} — ${it.title} : ${it.message}") }
            val symptomatic = r.sessionValues.filter { it.session.symptoms.isNotEmpty() }
            symptomatic.forEach { s -> para("• ${Fmt.date(s.session.localDate)} : symptômes signalés — ${s.session.symptoms.joinToString { it.label.lowercase() }}.") }

            section("11. Analyse par intelligence artificielle")
            if (d.aiText.isNullOrBlank()) para("Non incluse (aucune analyse IA demandée, ou transmission non autorisée par l'utilisateur).")
            else {
                para("Source : ${d.aiSource ?: "fournisseur d'IA"}. Texte généré automatiquement à partir des statistiques ci-dessus, non validé par un professionnel de santé.", small)
                para(d.aiText)
            }

            section("12. Limites de l'analyse")
            d.analysis.limitations.forEach { para("• $it") }

            section("13. Recommandations générales")
            para("Poursuivre les mesures dans des conditions standardisées. Mesures d'hygiène de vie généralement recommandées (ESC 2024, ESH 2023, OMS) : réduction du sel, alimentation riche en fruits et légumes, activité physique régulière, poids adapté, limitation de l'alcool, arrêt du tabac. " +
                "Ne pas modifier un traitement sans avis médical. En cas de douleur thoracique, d'essoufflement, de signes neurologiques ou de malaise : appeler les secours.")

            section("14. Points à discuter avec le cardiologue")
            if (d.analysis.pointsToDiscuss.isEmpty()) para("Aucun point saillant détecté automatiquement ; les mesures restent à présenter au médecin.")
            d.analysis.pointsToDiscuss.forEach { para("• $it") }
            d.analysis.findings.filter { it.severity >= com.cardiosuivi.domain.analysis.FindingSeverity.WATCH }.forEach { para("• ${it.title} : ${it.text}", small) }

            section("Références")
            listOf(MedicalReference.ESC_2024, MedicalReference.ESH_2023, MedicalReference.ESH_HBPM_2021, MedicalReference.AHA_2025, MedicalReference.WHO_2021)
                .forEach { para("${it.citation} ${it.doiOrUrl}", small) }
        }

        fun finish() { doc.finishPage(page) }

        private fun newPage() {
            if (pageNo > 0) doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            c = page.canvas
            y = margin
            c.drawText("CardioSuivi · ${Fmt.date(d.period.from)} – ${Fmt.date(d.period.to)} · page $pageNo", margin, pageH - 20f, small)
            c.drawText("Outil personnel d'aide au suivi — ne remplace pas un avis médical", pageW - margin - small.measureText("Outil personnel d'aide au suivi — ne remplace pas un avis médical"), pageH - 20f, small)
        }

        private fun ensure(h: Float) { if (y + h > pageH - margin - 10) newPage() }

        private fun section(t: String) {
            ensure(40f)
            y += 8
            c.drawText(t, margin, y + 13, h1)
            y += 18
            c.drawLine(margin, y, margin + contentW, y, line)
            y += 6
        }

        private fun wrap(text: String, paint: Paint, width: Float): List<String> {
            val out = mutableListOf<String>()
            for (raw in text.split("\n")) {
                var rest = raw
                if (rest.isEmpty()) { out += ""; continue }
                while (rest.isNotEmpty()) {
                    var n = paint.breakText(rest, true, width, null)
                    if (n < rest.length) {
                        val sp = rest.lastIndexOf(' ', n)
                        if (sp > 0) n = sp
                    }
                    out += rest.substring(0, n).trimEnd()
                    rest = rest.substring(n).trimStart()
                }
            }
            return out
        }

        private fun para(text: String, paint: Paint = body) {
            val lh = paint.textSize * 1.35f
            for (l in wrap(text, paint, contentW)) {
                ensure(lh)
                c.drawText(l, margin, y + paint.textSize, paint)
                y += lh
            }
            y += 2
        }

        private fun kv(k: String, v: String) {
            val kw = 150f
            val lines = wrap(v, body, contentW - kw)
            val lh = body.textSize * 1.35f
            ensure(lh * lines.size)
            c.drawText(k, margin, y + body.textSize, bold)
            lines.forEach { l -> c.drawText(l, margin + kw, y + body.textSize, body); y += lh }
        }

        private fun groupRow(label: String, g: GroupStats) = listOf(
            label, Fmt.d1(g.systolic.mean), Fmt.d1(g.diastolic.mean), Fmt.d1(g.pulse.mean), Fmt.d1(g.pulsePressure.mean), "${g.sessionCount}",
        )

        private fun table(headers: List<String>, rows: List<List<String>>, ratios: FloatArray) {
            val lh = 12f
            val widths = ratios.map { it * contentW }
            fun drawRow(cells: List<String>, paint: Paint, shade: Boolean) {
                val wrapped = cells.mapIndexed { i, s -> wrap(s, paint, widths[i] - 4) }
                val h = (wrapped.maxOf { it.size }) * lh + 2
                ensure(h)
                if (shade) c.drawRect(margin, y, margin + contentW, y + h, Paint().apply { color = Color.rgb(236, 242, 246) })
                var x = margin
                wrapped.forEachIndexed { i, ls ->
                    ls.forEachIndexed { j, l -> c.drawText(l, x + 2, y + paint.textSize + 1 + j * lh, paint) }
                    x += widths[i]
                }
                y += h
                c.drawLine(margin, y, margin + contentW, y, line)
            }
            drawRow(headers, bold, true)
            rows.forEach { drawRow(it, body, false) }
            y += 4
        }

        private fun sessionTable(values: List<SessionValue>) {
            if (values.isEmpty()) { para("Aucune séance validée sur la période."); return }
            val maxN = values.maxOf { it.session.measurements.size }.coerceAtMost(5)
            val headers = listOf("Date", "Moment") + (1..maxN).map { "Mesure $it" } + listOf("Valeur de séance", "Statut")
            val first = 0.13f
            val second = 0.09f
            val last = 0.15f
            val status = 0.1f
            val each = (1f - first - second - last - status) / maxN
            val ratios = floatArrayOf(first, second, *FloatArray(maxN) { each }, last, status)
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            val rows = values.map { sv ->
                val ms = sv.session.measurements.sortedBy { it.index }
                listOf(Fmt.date(sv.session.localDate), sv.session.period.label) +
                    (0 until maxN).map { i ->
                        ms.getOrNull(i)?.let { m -> "${m.systolic}/${m.diastolic}" + (m.pulse?.let { " · $it" } ?: "") + "\n" + timeFmt.format(m.measuredAt.atZone(d.zone)) } ?: "—"
                    } +
                    listOf(
                        "${Fmt.bp(sv.primary.systolic, sv.primary.diastolic)}" + (sv.primary.pulse?.let { " · ${Fmt.d0(it)}" } ?: "") + if (sv.stats.methodFallback) " *" else "",
                        sv.session.status.label,
                    )
            }
            table(headers, rows, ratios)
            para("Format : systolique/diastolique · pouls (mmHg, battements/min), heure de la mesure. * méthode non applicable, moyenne des mesures disponibles.", small)
        }

        private fun chartFrame(titleText: String, height: Float, yMin: Float, yMax: Float, draw: (left: Float, top: Float, w: Float, h: Float, ty: (Float) -> Float) -> Unit) {
            ensure(height + 30)
            c.drawText(titleText, margin, y + 10, bold)
            y += 16
            val left = margin + 28
            val top = y
            val w = contentW - 32
            val h = height
            val frame = Paint().apply { style = Paint.Style.STROKE; color = Color.GRAY; strokeWidth = 0.6f }
            c.drawRect(left, top, left + w, top + h, frame)
            fun ty(v: Float) = top + h - (v - yMin) / (yMax - yMin) * h
            var g = (yMin / 20).toInt() * 20f
            while (g <= yMax) {
                if (g >= yMin) {
                    c.drawLine(left, ty(g), left + w, ty(g), line)
                    c.drawText(g.toInt().toString(), margin, ty(g) + 3, small)
                }
                g += 20f
            }
            draw(left, top, w, h, ::ty)
            y += h + 18
        }

        private fun lineChart(t: String, values: List<SessionValue>) {
            if (values.size < 1) { para("Pas de données à représenter."); return }
            val all = values.flatMap { listOf(it.primary.systolic, it.primary.diastolic) }
            val yMin = ((all.min() - 10) / 10).toInt() * 10f
            val yMax = ((all.max() + 10) / 10).toInt() * 10f + 10
            val t0 = values.first().session.startedAt
            val span = ChronoUnit.MINUTES.between(t0, values.last().session.startedAt).coerceAtLeast(1)
            chartFrame(t, 150f, yMin, yMax) { left, _, w, _, ty ->
                fun tx(sv: SessionValue) = left + 6 + (ChronoUnit.MINUTES.between(t0, sv.session.startedAt).toFloat() / span) * (w - 12)
                val thr = Paint().apply { color = Color.rgb(239, 108, 0); strokeWidth = 0.8f; pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f) }
                val home = MedicalThresholds.HOME_HYPERTENSION
                listOf(home.systolic!!, home.diastolic!!).forEach { v -> if (v in yMin.toInt()..yMax.toInt()) c.drawLine(left, ty(v.toFloat()), left + w, ty(v.toFloat()), thr) }
                d.profile.target?.let { tg ->
                    val tp = Paint().apply { color = Color.rgb(46, 125, 50); strokeWidth = 0.8f; pathEffect = DashPathEffect(floatArrayOf(2f, 2f), 0f) }
                    listOf(tg.systolic, tg.diastolic).forEach { v -> if (v in yMin.toInt()..yMax.toInt()) c.drawLine(left, ty(v.toFloat()), left + w, ty(v.toFloat()), tp) }
                }
                series(values.map { tx(it) to ty(it.primary.systolic.toFloat()) }, sysColor)
                series(values.map { tx(it) to ty(it.primary.diastolic.toFloat()) }, diaColor)
                c.drawText("${Fmt.date(values.first().session.localDate)}", left, y + 150f + 10, small)
                val endLabel = Fmt.date(values.last().session.localDate)
                c.drawText(endLabel, left + w - small.measureText(endLabel), y + 150f + 10, small)
            }
            para("Rouge : systolique · bleu : diastolique · orange pointillé : repère à domicile 135/85 mmHg" + (if (d.profile.target != null) " · vert pointillé : objectif personnel" else "") +
                ". Chaque point est une séance réelle ; aucun point n'est interpolé.", small)
        }

        private fun pulseChart(values: List<SessionValue>) {
            val pv = values.filter { it.primary.pulse != null }
            if (pv.isEmpty()) return
            val vals = pv.map { it.primary.pulse!! }
            val yMin = ((vals.min() - 10) / 10).toInt() * 10f
            val yMax = ((vals.max() + 10) / 10).toInt() * 10f + 10
            val t0 = pv.first().session.startedAt
            val span = ChronoUnit.MINUTES.between(t0, pv.last().session.startedAt).coerceAtLeast(1)
            chartFrame("Fréquence cardiaque (valeurs de séance, /min)", 90f, yMin, yMax) { left, _, w, _, ty ->
                series(pv.map { sv -> (left + 6 + ChronoUnit.MINUTES.between(t0, sv.session.startedAt).toFloat() / span * (w - 12)) to ty(sv.primary.pulse!!.toFloat()) }, pulseColor)
            }
        }

        private fun series(points: List<Pair<Float, Float>>, color: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 1f; style = Paint.Style.STROKE }
            if (points.size > 1) {
                val path = Path().apply { moveTo(points[0].first, points[0].second); points.drop(1).forEach { lineTo(it.first, it.second) } }
                c.drawPath(path, p)
            }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            points.forEach { c.drawCircle(it.first, it.second, 2f, dot) }
        }
    }
}
