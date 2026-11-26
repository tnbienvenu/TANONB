package com.tanonb.scandoc

import android.app.Application
import android.content.Context
import com.tanonb.scandoc.data.Repository
import com.tanonb.scandoc.data.ScanDatabase
import com.tanonb.scandoc.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class ScanDocApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings by lazy { SettingsStore(this) }
    val repository by lazy { Repository(this, ScanDatabase.create(this).dao(), settings, appScope) }

    /** Exécute un traitement long hors de l'écran courant : il se termine même si l'utilisateur quitte l'écran. */
    suspend fun <T> background(block: suspend Repository.() -> T): T = appScope.async { repository.block() }.await()
}

val Context.app: ScanDocApp get() = applicationContext as ScanDocApp
