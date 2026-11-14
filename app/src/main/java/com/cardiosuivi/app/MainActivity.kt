package com.cardiosuivi.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import com.cardiosuivi.app.data.enumOr
import com.cardiosuivi.app.reminders.Notifications
import com.cardiosuivi.app.ui.AppRoot
import com.cardiosuivi.app.ui.theme.CardioTheme
import com.cardiosuivi.domain.model.SessionPeriod
import kotlinx.coroutines.flow.MutableStateFlow

/** Demande de démarrage d'une séance (depuis une notification de rappel). */
data class StartRequest(val period: SessionPeriod, val slotKey: String?, val id: Long = System.nanoTime())

class MainActivity : FragmentActivity() {

    val startRequests = MutableStateFlow<StartRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val container = (application as CardioSuiviApp).container
        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            LaunchedEffect(settings?.secureScreen) {
                // Empêche captures d'écran et aperçu dans les applications récentes (activé par défaut).
                if (settings?.secureScreen != false) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            CardioTheme {
                AppRoot(activity = this, container = container, settings = settings, startRequests = startRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(Notifications.EXTRA_START_SESSION, false) == true) {
            val period = enumOr(intent.getStringExtra(Notifications.EXTRA_PERIOD), SessionPeriod.MORNING)
            val slot = intent.getStringExtra(Notifications.EXTRA_SLOT_KEY)
            slot?.let { Notifications.cancel(this, it) }
            startRequests.value = StartRequest(period, slot)
            intent.removeExtra(Notifications.EXTRA_START_SESSION)
        }
    }
}
