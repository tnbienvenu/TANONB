package com.cardiosuivi.app.security

import android.content.Context
import android.util.Base64

/**
 * Coffre des secrets (clés d'API des fournisseurs d'IA).
 * Les valeurs sont chiffrées par une clé de l'Android Keystore avant d'être écrites ;
 * aucune clé d'API n'est stockée en clair, journalisée, exportée ou sauvegardée.
 */
class SecretStore(context: Context) {

    private val prefs = context.getSharedPreferences("cardiosuivi_secrets", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher("cardiosuivi_secrets_v1")

    fun put(name: String, value: String) {
        val enc = cipher.encrypt(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(enc, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): String? {
        val b64 = prefs.getString(name, null) ?: return null
        return try {
            cipher.decrypt(Base64.decode(b64, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            // Clé du Keystore invalidée (ex. : réinitialisation de la sécurité de l'appareil) : le secret est perdu.
            prefs.edit().remove(name).apply()
            null
        }
    }

    fun has(name: String): Boolean = prefs.contains(name)

    fun remove(name: String) {
        prefs.edit().remove(name).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        cipher.deleteKey()
    }
}
