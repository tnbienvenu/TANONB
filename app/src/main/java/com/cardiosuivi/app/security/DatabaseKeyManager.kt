package com.cardiosuivi.app.security

import android.content.Context
import java.io.File
import java.security.SecureRandom

/**
 * Gère la phrase secrète de la base SQLCipher : 32 octets aléatoires générés au premier lancement,
 * conservés uniquement sous forme chiffrée par une clé de l'Android Keystore.
 */
class DatabaseKeyManager(private val context: Context) {

    private val cipher = KeystoreCipher(KEY_ALIAS)
    private val file: File get() = File(context.noBackupFilesDir, KEY_FILE)

    /** Retourne la phrase secrète (à effacer par l'appelant après usage si possible). */
    fun getOrCreatePassphrase(): ByteArray {
        val f = file
        if (f.exists()) {
            return cipher.decrypt(f.readBytes())
        }
        val pass = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val tmp = File(f.parentFile, "$KEY_FILE.tmp")
        tmp.writeBytes(cipher.encrypt(pass))
        if (!tmp.renameTo(f)) {
            tmp.delete()
            throw IllegalStateException("Impossible d'enregistrer la clé de la base de données")
        }
        return pass
    }

    fun hasKey(): Boolean = file.exists()

    /** Supprime la clé : la base existante devient définitivement illisible (suppression des données). */
    fun destroy() {
        file.delete()
        cipher.deleteKey()
    }

    companion object {
        private const val KEY_ALIAS = "cardiosuivi_db_key_v1"
        private const val KEY_FILE = "db.key"
    }
}
