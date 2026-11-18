package com.cardiosuivi.domain.entry

import com.cardiosuivi.domain.reference.PlausibilityBounds

/** Champs du formulaire de saisie d'une mesure. */
enum class EntryField(val short: String, val label: String, val unit: String) {
    SYS("SYS", "Pression systolique", "mmHg"),
    DIA("DIA", "Pression diastolique", "mmHg"),
    PUL("PUL", "Fréquence cardiaque", "bpm"),
}

/**
 * Résultat de la validation du formulaire.
 * - [errors] : erreurs bloquantes, par champ (la mesure ne peut pas être enregistrée).
 * - [warnings] : valeurs hors des bornes de plausibilité technique ([PlausibilityBounds], inchangées depuis la 1.0) ;
 *   comme en 1.0, elles restent enregistrables après confirmation explicite.
 */
data class EntryValidation(
    val systolic: Int?,
    val diastolic: Int?,
    val pulse: Int?,
    val errors: Map<EntryField, String>,
    val warnings: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty() && systolic != null && diastolic != null && pulse != null
}

/**
 * Règles de saisie d'une mesure (1.1.0) : SYS, DIA et PUL obligatoires, nombres entiers positifs uniquement,
 * systolique strictement supérieure à la diastolique.
 */
object MeasurementEntry {
    /** Nombre maximal de chiffres saisis par champ (aucune valeur physiologique n'a plus de 3 chiffres). */
    const val MAX_DIGITS = 3

    /** Filtre appliqué à la frappe : ne conserve que les chiffres, dans la limite de [MAX_DIGITS]. */
    fun sanitize(input: String): String = input.filter { it in '0'..'9' }.take(MAX_DIGITS)

    private fun parse(field: EntryField, text: String, errors: MutableMap<EntryField, String>): Int? {
        val t = text.trim()
        if (t.isEmpty()) {
            errors[field] = "${field.short} est obligatoire : saisissez la ${field.label.lowercase()} (${field.unit})."
            return null
        }
        if (!t.all { it in '0'..'9' } || t.length > MAX_DIGITS) {
            errors[field] = "${field.short} doit être un nombre entier, sans virgule ni lettre (${field.unit})."
            return null
        }
        val v = t.toInt()
        if (v == 0) {
            errors[field] = "${field.short} ne peut pas être égal à 0."
            return null
        }
        return v
    }

    fun validate(sys: String, dia: String, pul: String): EntryValidation {
        val errors = linkedMapOf<EntryField, String>()
        val s = parse(EntryField.SYS, sys, errors)
        val d = parse(EntryField.DIA, dia, errors)
        val p = parse(EntryField.PUL, pul, errors)
        if (s != null && d != null && s <= d) {
            errors[EntryField.DIA] = "La systolique ($s) doit être supérieure à la diastolique ($d). Vérifiez que les valeurs ne sont pas inversées."
        }
        val warnings = mutableListOf<String>()
        if (errors.isEmpty()) {
            if (s != null && s !in PlausibilityBounds.SYSTOLIC) warnings += "Systolique $s mmHg : valeur inhabituelle (plage attendue ${PlausibilityBounds.SYSTOLIC.first}–${PlausibilityBounds.SYSTOLIC.last})."
            if (d != null && d !in PlausibilityBounds.DIASTOLIC) warnings += "Diastolique $d mmHg : valeur inhabituelle (plage attendue ${PlausibilityBounds.DIASTOLIC.first}–${PlausibilityBounds.DIASTOLIC.last})."
            if (p != null && p !in PlausibilityBounds.PULSE) warnings += "Fréquence cardiaque $p bpm : valeur inhabituelle (plage attendue ${PlausibilityBounds.PULSE.first}–${PlausibilityBounds.PULSE.last})."
        }
        return EntryValidation(s, d, p, errors, warnings)
    }
}

/** Horodatage automatique d'une mesure : instant du téléphone, fuseau et décalage UTC du moment. */
data class Timestamp(val instant: java.time.Instant, val zoneId: String, val utcOffsetSeconds: Int) {
    companion object {
        fun now(clock: java.time.Clock = java.time.Clock.systemDefaultZone()): Timestamp {
            val instant = clock.instant()
            val zone = clock.zone
            return Timestamp(instant, zone.id, zone.rules.getOffset(instant).totalSeconds)
        }
    }
}

/**
 * Restitution de l'heure d'une mesure : dans le fuseau enregistré avec la mesure lorsqu'il existe (1.1.0+), sinon dans
 * le fuseau courant du téléphone (mesures 1.0.x, comme auparavant).
 */
fun com.cardiosuivi.domain.model.Measurement.localDateTime(fallback: java.time.ZoneId = java.time.ZoneId.systemDefault()): java.time.ZonedDateTime {
    val zone = zoneId?.let { runCatching { java.time.ZoneId.of(it) }.getOrNull() } ?: fallback
    return measuredAt.atZone(zone)
}
