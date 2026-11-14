package com.cardiosuivi.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.cardiosuivi.app.reminders.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class CardioSuiviApp : Application() {

    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)

        // Verrouillage biométrique : l'application se verrouille lorsqu'elle passe en arrière-plan.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                container.appLock.onBackground()
            }
        })

        appScope.launch(Dispatchers.IO) {
            // Les photos temporaires de séances anciennes ne sont pas conservées.
            File(cacheDir, "photos").listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
            File(cacheDir, "exports").listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3600_000L }?.forEach { it.delete() }
            val s = container.settings.current()
            container.reminderScheduler.rescheduleAll(s.reminders)
            container.settings.markRescheduled()
        }
    }
}
