package com.cardiosuivi.domain.export

import com.cardiosuivi.domain.model.AggregationMethod
import com.cardiosuivi.domain.model.MeasurementSession
import com.cardiosuivi.domain.stats.SessionStatistics
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Export CSV : une ligne par mesure individuelle, avec la valeur de séance répétée.
 * Séparateur « ; » et virgule décimale (compatibles avec les tableurs configurés en français),
 * encodage UTF-8 avec BOM ajouté par l'appelant si nécessaire.
 */
object CsvExporter {

    private val TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    val HEADER = listOf(
        "seance_id", "date", "moment", "protocole", "statut", "bras", "mesure_n", "heure_mesure",
        "systolique_mmHg", "diastolique_mmHg", "pouls_bpm", "pression_pulsee_mmHg", "source",
        "valeur_seance_sys", "valeur_seance_dia", "valeur_seance_pouls", "methode_agregation", "symptomes", "notes_seance",
    )

    fun export(sessions: List<MeasurementSession>, method: AggregationMethod, zone: ZoneId): String {
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(";")).append("\r\n")
        for (s in sessions.filter { it.status.isValidated }.sortedBy { it.startedAt }) {
            val st = SessionStatistics.compute(s, method)
            for (m in s.measurements.sortedBy { it.index }) {
                val row = listOf(
                    s.id.toString(),
                    s.localDate.toString(),
                    s.period.label,
                    s.protocol.label,
                    s.status.label,
                    s.arm.label,
                    m.index.toString(),
                    TS.format(m.measuredAt.atZone(zone)),
                    m.systolic.toString(),
                    m.diastolic.toString(),
                    m.pulse?.toString() ?: "",
                    m.pulsePressure.toString(),
                    m.source.label,
                    dec(st.primary?.systolic),
                    dec(st.primary?.diastolic),
                    dec(st.primary?.pulse),
                    method.label,
                    s.symptoms.joinToString(", ") { it.label },
                    s.notes,
                )
                sb.append(row.joinToString(";") { escape(it) }).append("\r\n")
            }
        }
        return sb.toString()
    }

    private fun dec(x: Double?): String = x?.let { String.format(Locale.FRANCE, "%.1f", it) } ?: ""

    /** Échappement RFC 4180 (adapté au séparateur « ; »). */
    fun escape(v: String): String {
        val needs = v.contains(';') || v.contains('"') || v.contains('\n') || v.contains('\r')
        val safe = if (v.startsWith("=") || v.startsWith("+") || v.startsWith("-") || v.startsWith("@")) "'$v" else v // injection de formules
        return if (needs) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
