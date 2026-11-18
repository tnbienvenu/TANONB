package com.cardiosuivi.domain.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// ---------- Format des données sauvegardées (indépendant de la base de données) ----------

@Serializable
data class BackupMeasurement(
    val index: Int, val systolic: Int, val diastolic: Int, val pulse: Int?,
    val measuredAtEpochMs: Long, val source: String, val ocrConfidence: Double?, val note: String,
    // Format 2 (1.1.0) : champs facultatifs, absents des sauvegardes 1.0.x.
    val modifiedAtEpochMs: Long? = null, val zoneId: String? = null, val utcOffsetSeconds: Int? = null,
)

@Serializable
data class BackupSession(
    val startedAtEpochMs: Long, val completedAtEpochMs: Long?, val localDate: String, val period: String,
    val status: String, val protocol: String, val plannedMeasurements: Int, val arm: String,
    val symptoms: List<String>, val notes: String, val measurements: List<BackupMeasurement>,
)

@Serializable
data class BackupProfile(
    val displayName: String, val birthDate: String?, val sex: String, val heightCm: Int?, val weightKg: Double?,
    val targetSystolic: Int?, val targetDiastolic: Int?, val medicalHistory: String, val observations: String,
)

@Serializable
data class BackupTreatment(
    val name: String, val dosage: String, val usualTime: String?, val startDate: String?, val endDate: String?,
    val active: Boolean, val notes: String,
)

@Serializable
data class BackupObservation(val date: String, val text: String)

/** Interprétation générée par une IA et conservée (format 2). */
@Serializable
data class BackupAiAnalysis(val createdAtEpochMs: Long, val provider: String, val model: String, val question: String, val answer: String)

@Serializable
data class BackupPayload(
    val formatVersion: Int = CURRENT_FORMAT,
    val appVersion: String,
    val createdAtEpochMs: Long,
    val profile: BackupProfile?,
    val treatments: List<BackupTreatment>,
    val observations: List<BackupObservation>,
    val sessions: List<BackupSession>,
    /** Paramètres non sensibles (rappels, méthode d'agrégation…). Les clés d'API ne sont JAMAIS sauvegardées. */
    val settings: Map<String, String>,
    /** Format 2 : interprétations IA conservées (liste vide pour une sauvegarde 1.0.x). */
    val aiAnalyses: List<BackupAiAnalysis> = emptyList(),
) {
    /** 1 = CardioSuivi 1.0.x ; 2 = 1.1.0 (champs ajoutés, tous facultatifs : une sauvegarde 1.0.x reste restaurable). */
    companion object { const val CURRENT_FORMAT = 2 }
}

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Chiffrement des sauvegardes par mot de passe.
 * Format binaire : "CSBK" (4 octets) | version (1) | itérations (4, big-endian) | sel (16) | IV (12) | données AES-256-GCM.
 * Dérivation : PBKDF2-HMAC-SHA256, 600 000 itérations par défaut (recommandation OWASP pour PBKDF2-SHA256).
 * L'en-tête est authentifié (AAD) : toute modification est détectée.
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('C'.code.toByte(), 'S'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 1
    const val DEFAULT_ITERATIONS = 600_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    const val MIN_PASSWORD_LENGTH = 8

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = DEFAULT_ITERATIONS, random: SecureRandom = SecureRandom()): ByteArray {
        if (password.size < MIN_PASSWORD_LENGTH) throw BackupException("Le mot de passe doit comporter au moins $MIN_PASSWORD_LENGTH caractères.")
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val header = ByteBuffer.allocate(4 + 1 + 4 + SALT_LEN + IV_LEN)
            .put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(iv).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        val headerLen = 4 + 1 + 4 + SALT_LEN + IV_LEN
        if (data.size < headerLen + TAG_BITS / 8) throw BackupException("Fichier de sauvegarde invalide ou tronqué.")
        val bb = ByteBuffer.wrap(data)
        val magic = ByteArray(4).also { bb.get(it) }
        if (!magic.contentEquals(MAGIC)) throw BackupException("Ce fichier n'est pas une sauvegarde CardioSuivi.")
        val version = bb.get()
        if (version != VERSION) throw BackupException("Version de sauvegarde non prise en charge ($version).")
        val iterations = bb.int
        if (iterations !in 10_000..10_000_000) throw BackupException("En-tête de sauvegarde invalide.")
        val salt = ByteArray(SALT_LEN).also { bb.get(it) }
        val iv = ByteArray(IV_LEN).also { bb.get(it) }
        val header = data.copyOfRange(0, headerLen)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return try {
            cipher.doFinal(data, headerLen, data.size - headerLen)
        } catch (e: AEADBadTagException) {
            throw BackupException("Mot de passe incorrect ou fichier altéré.", e)
        }
    }

    fun encryptPayload(payload: BackupPayload, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray =
        encrypt(json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8), password, iterations)

    fun decryptPayload(data: ByteArray, password: CharArray): BackupPayload {
        val plain = decrypt(data, password)
        val payload = try {
            json.decodeFromString(BackupPayload.serializer(), plain.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw BackupException("Contenu de sauvegarde illisible.", e)
        }
        if (payload.formatVersion > BackupPayload.CURRENT_FORMAT) {
            throw BackupException("Sauvegarde créée par une version plus récente de l'application.")
        }
        return payload
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
