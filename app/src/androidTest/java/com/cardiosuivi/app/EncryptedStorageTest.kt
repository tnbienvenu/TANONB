package com.cardiosuivi.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardiosuivi.app.data.db.AppDatabase
import com.cardiosuivi.app.security.KeystoreCipher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Tests instrumentés (à exécuter sur appareil ou émulateur) : chiffrement réel par l'Android Keystore
 * et base SQLCipher (le fichier ne doit pas être lisible comme une base SQLite en clair).
 */
@RunWith(AndroidJUnit4::class)
class EncryptedStorageTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun keystoreRoundTrip() {
        val c = KeystoreCipher("test_alias_cardiosuivi")
        val plain = "secret".toByteArray()
        val enc = c.encrypt(plain)
        assertFalse(enc.contentEquals(plain))
        assertArrayEquals(plain, c.decrypt(enc))
        c.deleteKey()
    }

    @Test
    fun databaseFileIsEncrypted() {
        ctx.deleteDatabase("cardiosuivi.db")
        val db = AppDatabase.openEncrypted(ctx, ByteArray(32) { it.toByte() })
        db.openHelper.writableDatabase.execSQL("INSERT INTO observations(date, text) VALUES('2026-01-01', 'TEXTE_EN_CLAIR')")
        db.close()
        val bytes = File(ctx.getDatabasePath("cardiosuivi.db").path).readBytes()
        val header = String(bytes.copyOfRange(0, 16), Charsets.ISO_8859_1)
        assertFalse("En-tête SQLite en clair détecté", header.startsWith("SQLite format 3"))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("TEXTE_EN_CLAIR"))
        // Mauvaise clé : ouverture impossible.
        val wrong = AppDatabase.openEncrypted(ctx, ByteArray(32) { 7 })
        val failed = runCatching { wrong.openHelper.writableDatabase }.isFailure
        wrong.close()
        assertTrue(failed)
        ctx.deleteDatabase("cardiosuivi.db")
    }
}
