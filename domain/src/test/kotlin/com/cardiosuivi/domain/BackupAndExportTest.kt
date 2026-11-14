package com.cardiosuivi.domain

import com.cardiosuivi.domain.Fixtures.r
import com.cardiosuivi.domain.Fixtures.session
import com.cardiosuivi.domain.backup.BackupCrypto
import com.cardiosuivi.domain.backup.BackupException
import com.cardiosuivi.domain.backup.BackupMeasurement
import com.cardiosuivi.domain.backup.BackupPayload
import com.cardiosuivi.domain.backup.BackupSession
import com.cardiosuivi.domain.export.CsvExporter
import com.cardiosuivi.domain.model.AggregationMethod
import com.cardiosuivi.domain.model.SessionStatus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupAndExportTest {

    private val payload = BackupPayload(
        appVersion = "test",
        createdAtEpochMs = 1_700_000_000_000,
        profile = null,
        treatments = emptyList(),
        observations = emptyList(),
        sessions = listOf(
            BackupSession(
                1_700_000_000_000, 1_700_000_300_000, "2026-11-04", "MORNING", "COMPLETED", "USUAL", 3, "LEFT",
                listOf("DIZZINESS"), "note ; avec \"guillemets\"",
                listOf(BackupMeasurement(1, 130, 82, 70, 1_700_000_060_000, "MANUAL", null, "")),
            ),
        ),
        settings = mapOf("aggregation" to "ALL_MEASUREMENTS"),
    )

    @Test
    fun encryptDecryptRoundTrip() {
        val pwd = "mot-de-passe-solide".toCharArray()
        val enc = BackupCrypto.encryptPayload(payload, pwd, iterations = 20_000)
        // Le contenu chiffré ne doit pas contenir de données lisibles.
        assertFalse(String(enc, Charsets.ISO_8859_1).contains("2026-11-04"))
        val dec = BackupCrypto.decryptPayload(enc, "mot-de-passe-solide".toCharArray())
        assertEquals(payload, dec)
    }

    @Test
    fun wrongPasswordAndTamperingAreDetected() {
        val enc = BackupCrypto.encrypt("données".toByteArray(), "abcdefgh1".toCharArray(), iterations = 20_000)
        val e1 = assertThrows(BackupException::class.java) { BackupCrypto.decrypt(enc, "mauvais-mdp".toCharArray()) }
        assertTrue(e1.message!!.contains("incorrect"))
        val tampered = enc.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertThrows(BackupException::class.java) { BackupCrypto.decrypt(tampered, "abcdefgh1".toCharArray()) }
        val headerTampered = enc.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() } // sel
        assertThrows(BackupException::class.java) { BackupCrypto.decrypt(headerTampered, "abcdefgh1".toCharArray()) }
        assertThrows(BackupException::class.java) { BackupCrypto.decrypt("pas une sauvegarde".toByteArray(), "abcdefgh1".toCharArray()) }
    }

    @Test
    fun shortPasswordRejectedAndRandomizedOutput() {
        assertThrows(BackupException::class.java) { BackupCrypto.encrypt(byteArrayOf(1), "court".toCharArray(), 20_000) }
        val a = BackupCrypto.encrypt(byteArrayOf(1, 2, 3), "abcdefgh1".toCharArray(), 20_000)
        val b = BackupCrypto.encrypt(byteArrayOf(1, 2, 3), "abcdefgh1".toCharArray(), 20_000)
        assertFalse(a.contentEquals(b))
        assertArrayEquals(byteArrayOf(1, 2, 3), BackupCrypto.decrypt(a, "abcdefgh1".toCharArray()))
    }

    @Test
    fun defaultIterationsRoundTrip() {
        val enc = BackupCrypto.encrypt(byteArrayOf(42), "abcdefgh1".toCharArray())
        assertArrayEquals(byteArrayOf(42), BackupCrypto.decrypt(enc, "abcdefgh1".toCharArray()))
    }

    @Test
    fun csvExport() {
        val s = session(LocalDate.of(2026, 11, 4), readings = listOf(r(140, 90, 72), r(130, 84, null)))
            .copy(notes = "=SOMME(A1); test")
        val inProgress = session(LocalDate.of(2026, 11, 5), readings = listOf(r(200, 120)), status = SessionStatus.IN_PROGRESS)
        val csv = CsvExporter.export(listOf(s, inProgress), AggregationMethod.ALL_MEASUREMENTS, Fixtures.ZONE)
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(3, lines.size) // en-tête + 2 mesures ; la séance en cours est exclue
        assertTrue(lines[0].startsWith("seance_id;date;moment"))
        assertTrue(lines[1].contains(";140;90;72;50;"))
        assertTrue(lines[2].contains(";130;84;;46;")) // pouls absent laissé vide
        assertTrue(lines[1].contains(";135,0;87,0;72,0;"))
        assertTrue(lines[1].contains("\"'=SOMME(A1); test\""))
    }
}
