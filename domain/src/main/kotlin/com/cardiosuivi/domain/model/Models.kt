package com.cardiosuivi.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Moment de la journée d'une séance. */
enum class SessionPeriod(val label: String) {
    MORNING("Matin"),
    EVENING("Soir"),
    OTHER("Autre");
}

/**
 * Statut d'une séance.
 * - [IN_PROGRESS] : séance commencée, mesures en cours de saisie (non comptée dans les statistiques).
 * - [COMPLETED] : séance validée par l'utilisateur avec le nombre de mesures prévu par le protocole.
 * - [INCOMPLETE] : séance validée volontairement avec moins de mesures que prévu.
 */
enum class SessionStatus(val label: String) {
    IN_PROGRESS("En cours"),
    COMPLETED("Terminée"),
    INCOMPLETE("Incomplète");

    /** Une séance est exploitable dans les statistiques uniquement après validation. */
    val isValidated: Boolean get() = this != IN_PROGRESS
}

/**
 * Origine d'une valeur enregistrée. Depuis la 1.1.0, seule la saisie manuelle existe ; les valeurs OCR sont conservées
 * pour afficher correctement l'historique des mesures enregistrées avec la 1.0.x (jamais réécrites).
 */
enum class MeasurementSource(val label: String) {
    OCR("Reconnaissance automatique, validée"),
    OCR_CORRECTED("Reconnaissance automatique, corrigée"),
    MANUAL("Saisie manuelle");
}

/** Protocole de mesure suivi pour une séance. */
enum class ProtocolType(val label: String) {
    /** Calendrier habituel de l'utilisateur (par défaut mercredi, jeudi, vendredi ; matin et soir). */
    USUAL("Suivi habituel"),
    /** Automesure standardisée sur plusieurs jours consécutifs (voir HbpmProtocol). */
    STANDARDIZED_HBPM("Automesure standardisée");
}

/**
 * Bras de mesure. Depuis la 1.1.0, toute nouvelle séance est enregistrée au bras gauche, sans choix (mesures toujours
 * réalisées au bras gauche) ; les valeurs des séances antérieures sont conservées telles quelles.
 */
enum class Arm(val label: String) {
    LEFT("Bras gauche"), RIGHT("Bras droit"), UNKNOWN("Non précisé");

    companion object {
        /** Bras enregistré automatiquement pour les nouvelles séances. */
        val DEFAULT_FOR_NEW = LEFT
    }
}

/**
 * Symptômes déclarés facultativement à la fin d'une séance.
 * [urgent] = symptôme pouvant évoquer une urgence (douleur thoracique, signes neurologiques…),
 * pour lequel l'application recommande d'appeler immédiatement les services d'urgence.
 * Voir docs/REFERENCES_MEDICALES.md (AHA – signes d'alerte de l'AVC, de l'infarctus et de la crise hypertensive).
 */
enum class Symptom(val label: String, val urgent: Boolean) {
    CHEST_PAIN("Douleur ou oppression dans la poitrine", true),
    SHORTNESS_OF_BREATH("Essoufflement important, difficulté à respirer", true),
    WEAKNESS_NUMBNESS("Faiblesse ou engourdissement du visage, d'un bras ou d'une jambe", true),
    SPEECH_DIFFICULTY("Difficulté à parler ou à comprendre", true),
    VISION_CHANGE("Trouble brutal de la vision", true),
    CONFUSION("Confusion, désorientation", true),
    SEVERE_HEADACHE("Mal de tête brutal et intense", true),
    FAINTING("Malaise avec perte de connaissance", true),
    BACK_PAIN("Douleur intense dans le dos", true),
    DIZZINESS("Vertiges, sensation de tête légère", false),
    PALPITATIONS("Palpitations", false),
    HEADACHE("Mal de tête modéré", false),
    FATIGUE("Fatigue inhabituelle", false),
    NAUSEA("Nausées", false);
}

/** Une mesure individuelle (une prise) de tension artérielle. */
data class Measurement(
    val id: Long = 0,
    val sessionId: Long = 0,
    /** Rang dans la séance : 1, 2 ou 3. */
    val index: Int,
    val systolic: Int,
    val diastolic: Int,
    /** Fréquence cardiaque ; peut être absente si non affichée/illisible et non saisie. */
    val pulse: Int?,
    /**
     * Horodatage de la mesure, généré par l'application au moment de la validation (horloge du téléphone, à la
     * milliseconde). Jamais modifié par une correction de valeur.
     */
    val measuredAt: Instant,
    val source: MeasurementSource,
    /** Confiance globale de l'OCR (0..1) — uniquement pour les mesures historiques enregistrées par photo en 1.0.x. */
    val ocrConfidence: Double? = null,
    val note: String = "",
    /** Date de la dernière correction des valeurs, distincte de [measuredAt] ; null si jamais corrigée. */
    val modifiedAt: Instant? = null,
    /** Fuseau horaire du téléphone au moment de la mesure (ex. « Europe/Paris ») ; null pour les mesures antérieures à la 1.1.0. */
    val zoneId: String? = null,
    /** Décalage UTC en secondes au moment de la mesure (lève l'ambiguïté lors des changements d'heure). */
    val utcOffsetSeconds: Int? = null,
) {
    /** Pression pulsée = systolique − diastolique. */
    val pulsePressure: Int get() = systolic - diastolic
}

data class MeasurementSession(
    val id: Long = 0,
    val startedAt: Instant,
    val completedAt: Instant? = null,
    val period: SessionPeriod,
    val status: SessionStatus,
    val protocol: ProtocolType = ProtocolType.USUAL,
    /** Nombre de mesures prévues par le protocole (3 par défaut). */
    val plannedMeasurements: Int = 3,
    val arm: Arm = Arm.UNKNOWN,
    val symptoms: Set<Symptom> = emptySet(),
    val notes: String = "",
    /** Date locale de la séance (jour civil dans le fuseau de l'utilisateur au moment de la séance). */
    val localDate: LocalDate,
    val measurements: List<Measurement> = emptyList(),
)

/** Objectif tensionnel personnalisé, défini avec le médecin. */
data class BpTarget(val systolic: Int, val diastolic: Int)

enum class Sex(val label: String) { FEMALE("Femme"), MALE("Homme"), OTHER("Autre"), UNSPECIFIED("Non précisé") }

data class Profile(
    val displayName: String = "",
    val birthDate: LocalDate? = null,
    val sex: Sex = Sex.UNSPECIFIED,
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    /** Objectif personnel (mesures à domicile). Null = non renseigné. */
    val target: BpTarget? = null,
    val medicalHistory: String = "",
    val observations: String = "",
)

data class Treatment(
    val id: Long = 0,
    val name: String,
    val dosage: String = "",
    val usualTime: LocalTime? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val active: Boolean = true,
    val notes: String = "",
)

data class Observation(
    val id: Long = 0,
    val date: LocalDate,
    val text: String,
)

/**
 * Méthode d'agrégation des mesures d'une séance. Configurable et rappelée dans chaque rapport.
 */
enum class AggregationMethod(val label: String, val description: String) {
    ALL_MEASUREMENTS(
        "Moyenne de toutes les mesures",
        "La valeur de séance est la moyenne de toutes les mesures validées de la séance (habituellement 3).",
    ),
    LAST_TWO(
        "Moyenne des deux dernières mesures",
        "La valeur de séance est la moyenne des deux dernières mesures ; la première prise est écartée. " +
            "Si la séance comporte moins de deux mesures, la moyenne des mesures disponibles est utilisée et signalée.",
    );
}

/**
 * Plages horaires servant à classer automatiquement une séance en « matin » ou « soir » à partir de l'heure de sa
 * première mesure (bornes de début incluses, de fin exclues ; `eveningEnd` = 00:00 signifie « jusqu'à minuit »).
 * Une séance hors de ces plages n'est classée ni matin ni soir (« Autre ») : aucune période n'est attribuée
 * artificiellement. Valeurs initiales configurables, non médicales, choisies pour contenir les heures de rappel par défaut
 * (07:00 et 19:00) et le délai de tolérance des séances manquées (3 h).
 */
data class PeriodWindows(
    val morningStart: LocalTime = LocalTime.of(5, 0),
    val morningEnd: LocalTime = LocalTime.of(12, 0),
    val eveningStart: LocalTime = LocalTime.of(17, 0),
    val eveningEnd: LocalTime = LocalTime.MIDNIGHT,
) {
    private fun contains(start: LocalTime, end: LocalTime, t: LocalTime): Boolean = when {
        end == LocalTime.MIDNIGHT -> !t.isBefore(start)
        start.isBefore(end) -> !t.isBefore(start) && t.isBefore(end)
        else -> !t.isBefore(start) || t.isBefore(end) // plage traversant minuit
    }

    /** Période correspondant à l'heure locale [t], ou [SessionPeriod.OTHER] si aucune plage ne la contient. */
    fun periodOf(t: LocalTime): SessionPeriod = when {
        contains(morningStart, morningEnd, t) -> SessionPeriod.MORNING
        contains(eveningStart, eveningEnd, t) -> SessionPeriod.EVENING
        else -> SessionPeriod.OTHER
    }

    /** Vrai si les deux plages ne se chevauchent pas et ne sont pas vides. */
    val isValid: Boolean
        get() = morningStart != morningEnd && eveningStart != eveningEnd &&
            periodOf(morningStart) == SessionPeriod.MORNING &&
            !contains(eveningStart, eveningEnd, morningStart) && !contains(morningStart, morningEnd, eveningStart)
}

/** Configuration des rappels : jours, horaires (valeurs initiales configurables, non médicales). */
data class ReminderConfig(
    val enabled: Boolean = true,
    val days: Set<DayOfWeek> = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
    val morningEnabled: Boolean = true,
    val morningTime: LocalTime = LocalTime.of(7, 0),
    val eveningEnabled: Boolean = true,
    val eveningTime: LocalTime = LocalTime.of(19, 0),
    /** Délai après l'heure prévue au-delà duquel une séance non réalisée est considérée comme manquée. */
    val missedGraceMinutes: Int = 180,
)

/**
 * Interprétation produite par un fournisseur d'IA externe et conservée localement (base chiffrée), avec sa date,
 * afin de pouvoir la consulter ensuite. Les réponses locales (sans IA) ne sont pas enregistrées : elles sont recalculées.
 */
data class AiAnalysisRecord(
    val id: Long = 0,
    val createdAt: Instant,
    val provider: String,
    val model: String,
    val question: String,
    val answer: String,
)
