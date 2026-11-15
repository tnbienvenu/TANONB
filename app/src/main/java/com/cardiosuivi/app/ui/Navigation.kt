package com.cardiosuivi.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cardiosuivi.app.AppContainer
import com.cardiosuivi.app.MainActivity
import com.cardiosuivi.app.StartRequest
import com.cardiosuivi.app.data.AppSettings
import com.cardiosuivi.app.data.enumOr
import com.cardiosuivi.app.ui.screens.AiSettingsScreen
import com.cardiosuivi.app.ui.screens.AnalysisScreen
import com.cardiosuivi.app.ui.screens.AssistantScreen
import com.cardiosuivi.app.ui.screens.BackupScreen
import com.cardiosuivi.app.ui.screens.CalendarScreen
import com.cardiosuivi.app.ui.screens.ChartsScreen
import com.cardiosuivi.app.ui.screens.DashboardScreen
import com.cardiosuivi.app.ui.screens.DatabaseErrorScreen
import com.cardiosuivi.app.ui.screens.HbpmScreen
import com.cardiosuivi.app.ui.screens.HistoryScreen
import com.cardiosuivi.app.ui.screens.LockScreen
import com.cardiosuivi.app.ui.screens.MoreScreen
import com.cardiosuivi.app.ui.screens.OnboardingScreen
import com.cardiosuivi.app.ui.screens.PermissionsScreen
import com.cardiosuivi.app.ui.screens.ProfileScreen
import com.cardiosuivi.app.ui.screens.ReferencesScreen
import com.cardiosuivi.app.ui.screens.RemindersScreen
import com.cardiosuivi.app.ui.screens.ReportsScreen
import com.cardiosuivi.app.ui.screens.SessionDetailScreen
import com.cardiosuivi.app.ui.screens.SettingsScreen
import com.cardiosuivi.app.ui.screens.TreatmentsScreen
import com.cardiosuivi.app.ui.session.SessionFlowScreen
import com.cardiosuivi.domain.model.ProtocolType
import com.cardiosuivi.domain.model.SessionPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope

private object Loading
private object Failed

/** Redémarre l'interface sur une tâche neuve (après suppression ou réinitialisation des données). */
fun restartApp(activity: android.app.Activity) {
    activity.startActivity(
        android.content.Intent(activity, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
    activity.finish()
}

object Routes {
    const val DASHBOARD = "dashboard"
    const val HISTORY = "history"
    const val CHARTS = "charts"
    const val ANALYSIS = "analysis"
    const val MORE = "more"
    const val SESSION = "session?period={period}&protocol={protocol}&resume={resume}&slot={slot}"
    const val SESSION_DETAIL = "session_detail/{id}"
    const val ASSISTANT = "assistant"
    const val REPORTS = "reports"
    const val CALENDAR = "calendar"
    const val REMINDERS = "reminders"
    const val PERMISSIONS = "permissions"
    const val PROFILE = "profile"
    const val TREATMENTS = "treatments"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
    const val AI_SETTINGS = "ai_settings"
    const val REFERENCES = "references"
    const val HBPM = "hbpm"

    fun session(period: SessionPeriod? = null, protocol: ProtocolType = ProtocolType.USUAL, resume: Long = -1, slot: String? = null) =
        "session?period=${period?.name ?: ""}&protocol=${protocol.name}&resume=$resume&slot=${slot ?: ""}"

    fun sessionDetail(id: Long) = "session_detail/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.DASHBOARD, "Accueil", Icons.Filled.Home),
    Tab(Routes.HISTORY, "Historique", Icons.AutoMirrored.Filled.List),
    Tab(Routes.CHARTS, "Graphiques", Icons.AutoMirrored.Filled.ShowChart),
    Tab(Routes.ANALYSIS, "Analyse", Icons.Filled.Analytics),
    Tab(Routes.MORE, "Plus", Icons.Filled.Menu),
)

@Composable
fun AppRoot(activity: MainActivity, container: AppContainer, settings: AppSettings?, startRequests: MutableStateFlow<StartRequest?>) {
    val scope = rememberCoroutineScope()
    if (settings == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // Ouverture de la base chiffrée hors du fil principal (dérivation de clé SQLCipher potentiellement longue).
    val db by produceState<Any?>(initialValue = Loading) {
        value = withContext(Dispatchers.IO) { container.databaseOrNull() } ?: Failed
    }
    if (db === Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (db === Failed) {
        DatabaseErrorScreen(container.databaseError) {
            scope.launch {
                container.destroyAllData()
                restartApp(activity)
            }
        }
        return
    }
    if (!settings.onboardingAccepted) {
        OnboardingScreen { scope.launch { container.settings.setOnboardingAccepted() } }
        return
    }
    val locked by container.appLock.locked.collectAsState()
    val vm: AppViewModel = viewModel(factory = AppViewModel.factory(container))
    // L'écran de verrouillage est superposé : la navigation (et une éventuelle séance en cours) est conservée.
    Box(Modifier.fillMaxSize()) {
        MainNavigation(vm, startRequests)
        if (settings.biometricLock && locked) {
            Surface(Modifier.fillMaxSize()) { LockScreen(activity) { container.appLock.unlock() } }
        }
    }
}

@Composable
private fun MainNavigation(vm: AppViewModel, startRequests: MutableStateFlow<StartRequest?>) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        // Android 13+ : les rappels nécessitent l'autorisation de notifications.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && vm.settings.value.reminders.enabled &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val request by startRequests.collectAsState()
    LaunchedEffect(request) {
        request?.let {
            nav.navigate(Routes.session(it.period, slot = it.slotKey))
            startRequests.value = null
        }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == current }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = { nav.navigateTab(t.route) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(bottom = padding.calculateBottomPadding()), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            NavHost(nav, startDestination = Routes.DASHBOARD) {
                composable(Routes.DASHBOARD) { DashboardScreen(vm, nav) }
                composable(Routes.HISTORY) { HistoryScreen(vm, nav) }
                composable(Routes.CHARTS) { ChartsScreen(vm) }
                composable(Routes.ANALYSIS) { AnalysisScreen(vm, nav) }
                composable(Routes.MORE) { MoreScreen(nav) }
                composable(
                    Routes.SESSION,
                    arguments = listOf(
                        navArgument("period") { type = NavType.StringType; defaultValue = "" },
                        navArgument("protocol") { type = NavType.StringType; defaultValue = ProtocolType.USUAL.name },
                        navArgument("resume") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("slot") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val a = entry.arguments
                    SessionFlowScreen(
                        app = vm,
                        period = a?.getString("period")?.takeIf { it.isNotBlank() }?.let { enumOr(it, SessionPeriod.MORNING) },
                        protocol = enumOr(a?.getString("protocol"), ProtocolType.USUAL),
                        resumeId = a?.getLong("resume")?.takeIf { it > 0 },
                        slotKey = a?.getString("slot")?.takeIf { it.isNotBlank() },
                        onExit = { nav.popBackStack(Routes.DASHBOARD, inclusive = false) },
                        onOpenSession = { id -> nav.navigate(Routes.sessionDetail(id)) { popUpTo(Routes.DASHBOARD) } },
                    )
                }
                composable(Routes.SESSION_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    SessionDetailScreen(vm, e.arguments?.getLong("id") ?: 0L) { nav.popBackStack() }
                }
                composable(Routes.ASSISTANT) { AssistantScreen(vm, nav) }
                composable(Routes.REPORTS) { ReportsScreen(vm) { nav.popBackStack() } }
                composable(Routes.CALENDAR) { CalendarScreen(vm, nav) }
                composable(Routes.REMINDERS) { RemindersScreen(vm, nav) }
                composable(Routes.PERMISSIONS) { PermissionsScreen(vm) { nav.popBackStack() } }
                composable(Routes.PROFILE) { ProfileScreen(vm) { nav.popBackStack() } }
                composable(Routes.TREATMENTS) { TreatmentsScreen(vm) { nav.popBackStack() } }
                composable(Routes.BACKUP) { BackupScreen(vm) { nav.popBackStack() } }
                composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
                composable(Routes.AI_SETTINGS) { AiSettingsScreen(vm) { nav.popBackStack() } }
                composable(Routes.REFERENCES) { ReferencesScreen { nav.popBackStack() } }
                composable(Routes.HBPM) { HbpmScreen(vm, nav) }
            }
        }
    }
}

fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
