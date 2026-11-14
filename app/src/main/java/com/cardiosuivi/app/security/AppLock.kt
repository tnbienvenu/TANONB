package com.cardiosuivi.app.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * État de verrouillage de l'interface (protection biométrique facultative).
 * Verrouillée au démarrage, puis à nouveau après [LOCK_DELAY_MS] passées en arrière-plan : les courtes
 * sorties (sélecteur de photos, menu de partage, réglages système) ne verrouillent pas et n'interrompent pas une séance.
 */
class AppLock(private val clock: () -> Long = System::currentTimeMillis) {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked
    private var backgroundAt = 0L

    fun unlock() { _locked.value = false }
    fun onBackground() { backgroundAt = clock() }
    fun onForeground() {
        if (backgroundAt > 0 && clock() - backgroundAt >= LOCK_DELAY_MS) _locked.value = true
        backgroundAt = 0
    }

    companion object { const val LOCK_DELAY_MS = 60_000L }
}

object Biometrics {

    /** Combinaison d'authentificateurs : biométrie forte ou code de l'appareil (Android 11+) ; biométrie ou code sur Android 10. */
    val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun availability(activity: FragmentActivity): Int = BiometricManager.from(activity).canAuthenticate(authenticators)

    fun isAvailable(activity: FragmentActivity): Boolean = availability(activity) == BiometricManager.BIOMETRIC_SUCCESS

    fun describe(code: Int): String = when (code) {
        BiometricManager.BIOMETRIC_SUCCESS -> "Disponible"
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Aucune empreinte, aucun visage ni code de verrouillage configuré sur l'appareil"
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Pas de capteur biométrique"
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Capteur temporairement indisponible"
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Mise à jour de sécurité requise"
        else -> "Indisponible (code $code)"
    }

    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Déverrouiller CardioSuivi")
            .setSubtitle("Vos données de santé sont protégées")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }
}
