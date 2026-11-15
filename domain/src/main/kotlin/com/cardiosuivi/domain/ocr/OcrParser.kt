package com.cardiosuivi.domain.ocr

import com.cardiosuivi.domain.reference.PlausibilityBounds
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Élément de texte reconnu (mot) avec sa boîte englobante en pixels. */
data class OcrElement(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Vrai si le moteur de lecture signale lui-même une ambiguïté (ex. chiffre 7 segments décodé à un segment près). */
    val uncertain: Boolean = false,
    /** Nombre de lectures indépendantes concordantes (réglages du lecteur 7 segments) ; 1 par défaut. */
    val support: Int = 1,
) {
    val height: Float get() = bottom - top
    val width: Float get() = right - left
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2

    /** Recouvrement vertical relatif avec un autre élément (0..1). */
    fun verticalOverlap(o: OcrElement): Float {
        val inter = min(bottom, o.bottom) - max(top, o.top)
        if (inter <= 0) return 0f
        return inter / min(height, o.height).coerceAtLeast(1f)
    }
}

enum class OcrFieldType(val label: String) { SYSTOLIC("SYS"), DIASTOLIC("DIA"), PULSE("PUL") }

data class OcrField(
    val type: OcrFieldType,
    /** Valeur proposée ; null si illisible ou non trouvée (jamais inventée). */
    val value: Int?,
    /** Confiance 0..1. */
    val confidence: Double,
    /** Vrai si l'utilisateur doit particulièrement vérifier ce champ. */
    val uncertain: Boolean,
    val reason: String? = null,
    /** Autres valeurs candidates observées. */
    val alternatives: List<Int> = emptyList(),
)

enum class AssignmentMethod { LABELS, LAYOUT, NONE }

data class OcrReading(
    val systolic: OcrField,
    val diastolic: OcrField,
    val pulse: OcrField,
    val method: AssignmentMethod,
    val warnings: List<String>,
    /** Tous les nombres plausibles détectés, pour sélection manuelle. */
    val candidates: List<Int>,
    val rawText: String,
) {
    val fields: List<OcrField> get() = listOf(systolic, diastolic, pulse)
    val overallConfidence: Double get() = fields.filter { it.value != null }.map { it.confidence }.average().takeIf { !it.isNaN() } ?: 0.0
    val anyUncertain: Boolean get() = fields.any { it.uncertain || it.value == null }
}

/**
 * Analyse le texte reconnu sur la photo d'un écran de tensiomètre pour en extraire SYS, DIA et PUL.
 *
 * Principes :
 * 1. Ne jamais inventer une valeur : un champ non trouvé ou implausible reste vide (null).
 * 2. Les libellés (SYS, DIA, PUL, /min…) sont prioritaires sur la disposition.
 * 3. À défaut de libellés, disposition usuelle des écrans : systolique en haut (grands chiffres),
 *    diastolique en dessous, pouls en bas (chiffres plus petits). Toute déduction par disposition est
 *    signalée comme moins fiable.
 * 4. Toute incohérence (SYS ≤ DIA, valeurs hors plage) rend les champs incertains, sans permutation silencieuse.
 */
object OcrParser {

    private val SYS_LABELS = setOf("SYS", "SYST", "SYS.", "SYSTOLIC", "SYSTOLE", "SYSTOLIQUE", "SIS")
    private val DIA_LABELS = setOf("DIA", "DIAS", "DIA.", "DIASTOLIC", "DIASTOLE", "DIASTOLIQUE")
    private val PUL_LABELS = setOf("PUL", "PULSE", "PULS", "PUL.", "PUL/MIN", "PULSE/MIN", "/MIN", "MIN", "P/MIN", "BPM", "POULS", "PR", "HR")

    /** Confusions fréquentes de l'OCR sur les afficheurs à 7 segments. */
    private val CONFUSIONS = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0', 'U' to '0',
        'I' to '1', 'l' to '1', '|' to '1', '!' to '1', 'i' to '1', 'L' to '1', 'J' to '1',
        'Z' to '2', 'z' to '2',
        'E' to '3',
        'A' to '4',
        'S' to '5', 's' to '5',
        'G' to '6', 'b' to '6',
        'T' to '7', '?' to '7',
        'B' to '8',
        'g' to '9', 'q' to '9',
    )

    internal data class Candidate(val element: OcrElement, val value: Int, val corrected: Boolean)

    /** Normalise un jeton : renvoie (valeur, corrigé ?) ou null si ce n'est pas un nombre de tension/pouls. */
    internal fun parseNumber(raw: String): Pair<Int, Boolean>? {
        val t = raw.trim().trim('.', ',', ';', '-', '_', '\'', '"')
        if (t.isEmpty() || t.length > 4) return null
        if (t.contains(':') || t.contains('/')) return null // heures, dates
        val digits = t.count { it.isDigit() }
        if (digits == 0) return null
        var corrected = false
        val sb = StringBuilder()
        for (c in t) {
            when {
                c.isDigit() -> sb.append(c)
                CONFUSIONS.containsKey(c) -> { sb.append(CONFUSIONS.getValue(c)); corrected = true }
                else -> return null
            }
        }
        // Au moins un vrai chiffre est exigé (les mots sans chiffre ont été écartés plus haut) ;
        // les autres caractères doivent tous être des confusions connues des afficheurs 7 segments.
        val s = sb.toString()
        if (s.length !in 2..3) return null
        val v = s.toIntOrNull() ?: return null
        if (v < 20 || v > 300) return null
        return v to corrected
    }

    private fun normalizeLabel(t: String): String = t.uppercase().replace(" ", "").trim(',', ';', ':')

    fun parse(elements: List<OcrElement>): OcrReading {
        val rawText = elements.joinToString(" ") { it.text }
        val warnings = mutableListOf<String>()

        // Certains moteurs regroupent « SYS 128 » dans un même élément : on découpe.
        val tokens = elements.flatMap { splitElement(it) }

        val candidates = tokens.mapNotNull { e -> parseNumber(e.text)?.let { Candidate(e, it.first, it.second || e.uncertain) } }
        val sysLabel = tokens.firstOrNull { normalizeLabel(it.text) in SYS_LABELS }
        val diaLabel = tokens.firstOrNull { normalizeLabel(it.text) in DIA_LABELS }
        val pulLabel = tokens.firstOrNull { normalizeLabel(it.text) in PUL_LABELS }

        if (candidates.isEmpty()) {
            return OcrReading(
                emptyField(OcrFieldType.SYSTOLIC, "Aucun nombre lisible"),
                emptyField(OcrFieldType.DIASTOLIC, "Aucun nombre lisible"),
                emptyField(OcrFieldType.PULSE, "Aucun nombre lisible"),
                AssignmentMethod.NONE,
                listOf("Aucune valeur lisible détectée. Recadrez l'écran du tensiomètre ou saisissez les valeurs manuellement."),
                emptyList(), rawText,
            )
        }

        // 1) Attribution par libellés.
        val used = mutableSetOf<Candidate>()
        fun nearestTo(label: OcrElement?): Candidate? {
            if (label == null) return null
            return candidates
                .filter { it !in used }
                .filter { c ->
                    val e = c.element
                    // À droite sur la même ligne, ou juste en dessous / au-dessus à proximité.
                    val sameRow = e.verticalOverlap(label) > 0.3f && e.left >= label.left - label.width
                    val near = abs(e.centerY - label.centerY) < 4 * max(label.height, e.height)
                    sameRow || near
                }
                .minByOrNull { c -> distance(label, c.element) }
                ?.also { used += it }
        }

        val labelCount = listOfNotNull(sysLabel, diaLabel, pulLabel).size
        var method = AssignmentMethod.NONE
        var sysC: Candidate? = null
        var diaC: Candidate? = null
        var pulC: Candidate? = null

        if (labelCount >= 2) {
            sysC = nearestTo(sysLabel)
            diaC = nearestTo(diaLabel)
            pulC = nearestTo(pulLabel)
            method = AssignmentMethod.LABELS
        }

        // 2) Attribution par disposition (si libellés absents ou incomplets).
        if (method == AssignmentMethod.NONE || listOfNotNull(sysC, diaC).size < 2) {
            val layout = assignByLayout(candidates)
            if (sysC == null) sysC = layout.first
            if (diaC == null) diaC = layout.second
            if (pulC == null) pulC = layout.third
            if (method == AssignmentMethod.NONE) method = AssignmentMethod.LAYOUT
            warnings += "Champs identifiés d'après leur position sur l'écran : vérifiez que SYS, DIA et PUL ne sont pas intervertis."
        }

        var sysF = toField(OcrFieldType.SYSTOLIC, sysC, fieldBase(method, sysC), PlausibilityBounds.SYSTOLIC)
        var diaF = toField(OcrFieldType.DIASTOLIC, diaC, fieldBase(method, diaC), PlausibilityBounds.DIASTOLIC)
        var pulF = toField(OcrFieldType.PULSE, pulC, fieldBase(method, pulC), PlausibilityBounds.PULSE)

        // 2 bis) Une attribution d'après la disposition n'est fiable que si la paire SYS/DIA est complète :
        // un nombre isolé (ex. le pouls seul lu) pourrait être pris pour la systolique.
        // SYS et DIA s'affichent dans la même taille : une grande différence de hauteur trahit une confusion (ex. pouls pris pour DIA).
        val sizeMismatch = sysC != null && diaC != null &&
            minOf(sysC!!.element.height, diaC!!.element.height) < 0.8f * maxOf(sysC!!.element.height, diaC!!.element.height)
        if (method == AssignmentMethod.LAYOUT && (sysF.value == null || diaF.value == null || sizeMismatch)) {
            fun weaken(f: OcrField) = if (f.value != null && !f.uncertain) f.copy(uncertain = true, confidence = minOf(f.confidence, 0.6), reason = f.reason ?: "Lecture partielle de l'écran : champ à vérifier") else f
            sysF = weaken(sysF); diaF = weaken(diaF); pulF = weaken(pulF)
        }
        // 3) Cohérence systolique > diastolique, sans permutation silencieuse.
        if (sysF.value != null && diaF.value != null && sysF.value!! <= diaF.value!!) {
            warnings += "La systolique lue (${sysF.value}) n'est pas supérieure à la diastolique (${diaF.value}) : champs probablement intervertis ou mal lus."
            sysF = sysF.copy(uncertain = true, confidence = sysF.confidence * 0.5, reason = "Incohérent avec la diastolique")
            diaF = diaF.copy(uncertain = true, confidence = diaF.confidence * 0.5, reason = "Incohérent avec la systolique")
        }
        // Une même valeur ne doit pas être attribuée à deux champs à partir du même élément.
        if (pulC != null && (pulC == sysC || pulC == diaC)) {
            pulF = emptyField(OcrFieldType.PULSE, "Pouls non distingué des autres valeurs")
        }
        if (pulF.value == null) warnings += "Fréquence cardiaque non lue : saisissez-la si elle est affichée."

        val all = candidates.map { it.value }.distinct()
        fun withAlternatives(f: OcrField) = f.copy(alternatives = all.filter { it != f.value })

        return OcrReading(
            withAlternatives(sysF), withAlternatives(diaF), withAlternatives(pulF),
            method, warnings.distinct(), all, rawText,
        )
    }

    /**
     * Consensus entre plusieurs lectures de la même photo (images prétraitées différemment).
     * Une valeur confirmée par plusieurs variantes gagne en confiance ; un désaccord rend le champ incertain.
     */
    fun consensus(readings: List<OcrReading>): OcrReading {
        require(readings.isNotEmpty())
        if (readings.size == 1) return readings.first()
        fun merge(type: OcrFieldType, select: (OcrReading) -> OcrField): OcrField {
            val fields = readings.map(select)
            val withValue = fields.filter { it.value != null }
            if (withValue.isEmpty()) return emptyField(type, fields.firstNotNullOfOrNull { it.reason } ?: "Non lu")
            val votes = withValue.groupBy { it.value!! }
            val best = votes.maxWith(compareBy<Map.Entry<Int, List<OcrField>>> { it.value.size }.thenBy { e -> e.value.maxOf { it.confidence } })
            val agreement = best.value.size.toDouble() / readings.size
            val disagreement = votes.size > 1
            val conf = (best.value.maxOf { it.confidence } * (0.6 + 0.4 * agreement)).coerceAtMost(0.99)
            val uncertain = disagreement || best.value.all { it.uncertain } || conf < 0.6
            return OcrField(
                type, best.key, conf, uncertain,
                when {
                    disagreement -> "Lectures divergentes : ${votes.keys.joinToString(" / ")}"
                    else -> best.value.first().reason
                },
                (votes.keys - best.key).toList() + best.value.flatMap { it.alternatives }.distinct().filter { it != best.key && it !in votes.keys },
            )
        }
        var sys = merge(OcrFieldType.SYSTOLIC) { it.systolic }
        var dia = merge(OcrFieldType.DIASTOLIC) { it.diastolic }
        val pul = merge(OcrFieldType.PULSE) { it.pulse }
        val warnings = readings.flatMap { it.warnings }.toMutableList()
        if (sys.value != null && dia.value != null && sys.value!! <= dia.value!!) {
            sys = sys.copy(uncertain = true)
            dia = dia.copy(uncertain = true)
        }
        val method = if (readings.any { it.method == AssignmentMethod.LABELS }) AssignmentMethod.LABELS
        else readings.firstOrNull { it.method != AssignmentMethod.NONE }?.method ?: AssignmentMethod.NONE
        return OcrReading(
            sys, dia, pul, method, warnings.distinct(),
            readings.flatMap { it.candidates }.distinct(),
            readings.joinToString("\n---\n") { it.rawText },
        )
    }

    /**
     * Choisit entre deux lectures issues de moteurs différents (lecteur 7 segments et OCR généraliste).
     * La plus complète et cohérente est retenue ; si les deux lisent des valeurs différentes pour un même champ,
     * ce champ est marqué incertain et l'autre valeur est proposée en alternative.
     */
    fun combine(primary: OcrReading, secondary: OcrReading, quality: (OcrReading) -> Double): OcrReading {
        val (best, other) = if (quality(secondary) > quality(primary)) secondary to primary else primary to secondary
        fun merge(b: OcrField, o: OcrField): OcrField = when {
            b.value == null || o.value == null -> b
            b.value == o.value -> b.copy(confidence = minOf(0.99, b.confidence + 0.1))
            else -> b.copy(uncertain = true, reason = "Lectures divergentes : ${b.value} / ${o.value}", alternatives = (listOf(o.value!!) + b.alternatives).distinct())
        }
        return best.copy(
            systolic = merge(best.systolic, other.systolic),
            diastolic = merge(best.diastolic, other.diastolic),
            pulse = merge(best.pulse, other.pulse),
            candidates = (best.candidates + other.candidates).distinct(),
            rawText = best.rawText + "\n---\n" + other.rawText,
        )
    }

    private fun assignByLayout(candidates: List<Candidate>): Triple<Candidate?, Candidate?, Candidate?> {
        if (candidates.isEmpty()) return Triple(null, null, null)
        val maxH = candidates.maxOf { it.element.height }
        // Garde les nombres affichés en assez grand (les petits chiffres sont souvent l'heure, la mémoire…).
        val big = candidates.filter { it.element.height >= 0.35f * maxH }
            .sortedByDescending { it.element.height }
            .take(3)
        // Ordre de lecture : de haut en bas ; à hauteur égale (même rangée), de gauche à droite.
        val ordered = big.sortedWith { a, b ->
            if (a.element.verticalOverlap(b.element) > 0.5f) a.element.left.compareTo(b.element.left)
            else a.element.centerY.compareTo(b.element.centerY)
        }
        return when (ordered.size) {
            1 -> Triple(ordered[0], null, null)
            2 -> Triple(ordered[0], ordered[1], null)
            else -> Triple(ordered[0], ordered[1], ordered[2])
        }
    }

    /** Nombre minimal de lectures concordantes pour qu'une valeur placée d'après la disposition soit jugée fiable. */
    const val MIN_SUPPORT_FOR_CONFIDENCE = 4

    /**
     * Confiance de départ d'un champ : libellés lus → 0,9 ; disposition seule → 0,7 (à vérifier),
     * sauf si la valeur est confirmée par plusieurs lectures indépendantes et décodée sans approximation → 0,85.
     */
    private fun fieldBase(method: AssignmentMethod, c: Candidate?): Double = when {
        method == AssignmentMethod.LABELS -> 0.9
        c != null && !c.corrected && c.element.support >= MIN_SUPPORT_FOR_CONFIDENCE -> 0.85
        else -> 0.7
    }

    private fun toField(type: OcrFieldType, c: Candidate?, base: Double, bounds: IntRange): OcrField {
        if (c == null) return emptyField(type, "Valeur non trouvée")
        if (c.value !in bounds) {
            return OcrField(type, null, 0.0, true, "Valeur lue (${c.value}) hors plage plausible : à saisir manuellement", listOf(c.value))
        }
        var conf = base
        var reason: String? = null
        if (c.corrected) {
            conf -= 0.2
            reason = "Caractère(s) ambigu(s) interprété(s) (« ${c.element.text} » → ${c.value})"
        }
        return OcrField(type, c.value, conf, conf < 0.75, reason)
    }

    private fun emptyField(type: OcrFieldType, reason: String) = OcrField(type, null, 0.0, true, reason)

    private fun distance(a: OcrElement, b: OcrElement): Float {
        val dx = b.centerX - a.centerX
        val dy = b.centerY - a.centerY
        // Pénalise les éléments à gauche du libellé et privilégie la même rangée.
        val leftPenalty = if (b.right < a.left) 2f else 1f
        return (abs(dx) + 2 * abs(dy)) * leftPenalty
    }

    /** Découpe un élément contenant plusieurs jetons séparés par des espaces (largeur répartie proportionnellement). */
    private fun splitElement(e: OcrElement): List<OcrElement> {
        val parts = e.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size <= 1) return listOf(e)
        val totalChars = parts.sumOf { it.length }.toFloat()
        var x = e.left
        return parts.map { p ->
            val w = e.width * p.length / totalChars
            val out = OcrElement(p, x, e.top, x + w, e.bottom, e.uncertain, e.support)
            x += w
            out
        }
    }
}
