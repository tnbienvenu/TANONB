package com.tanonb.scandoc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tanonb.scandoc.MainActivity
import com.tanonb.scandoc.app
import com.tanonb.scandoc.data.PageSource
import com.tanonb.scandoc.ui.components.BusyOverlay
import com.tanonb.scandoc.ui.components.toast
import com.tanonb.scandoc.ui.screens.CameraScreen
import com.tanonb.scandoc.ui.screens.CombineScreen
import com.tanonb.scandoc.ui.screens.DocumentScreen
import com.tanonb.scandoc.ui.screens.GalleryScreen
import com.tanonb.scandoc.ui.screens.MainShell
import com.tanonb.scandoc.ui.screens.MarkupScreen
import com.tanonb.scandoc.ui.screens.PageEditScreen
import com.tanonb.scandoc.ui.screens.ReorderScreen
import com.tanonb.scandoc.ui.screens.SettingsScreen
import com.tanonb.scandoc.ui.screens.SignScreen
import com.tanonb.scandoc.ui.screens.TextScreen

object Routes {
    const val MAIN = "main"
    fun gallery(docId: Long = -1) = "gallery?docId=$docId"
    fun camera(docId: Long = -1) = "camera?docId=$docId"
    fun doc(id: Long) = "doc/$id"
    fun edit(pageId: Long) = "edit/$pageId"
    fun reorder(docId: Long) = "reorder/$docId"
    fun sign(docId: Long, pageIndex: Int = 0) = "sign/$docId?page=$pageIndex"
    fun text(docId: Long) = "text/$docId"
    fun marks(pageId: Long) = "marks/$pageId"
    fun combine(docId: Long = -1) = "combine?docId=$docId"
    const val SETTINGS = "settings"
}

/** Ouvre un document nouvellement créé en remplaçant l'écran de création dans l'historique. */
fun NavHostController.openNewDocument(id: Long) {
    navigate(Routes.doc(id)) { popUpTo(Routes.MAIN) }
}

@Composable
fun ScanDocNav(activity: MainActivity) {
    val nav = rememberNavController()
    val incoming by activity.incoming.collectAsState()
    var importing by remember { mutableStateOf(false) }

    // Fichiers partagés depuis une autre application → nouveau document.
    LaunchedEffect(incoming) {
        if (incoming.isEmpty()) return@LaunchedEffect
        val uris = incoming
        activity.incoming.value = emptyList()
        importing = true
        val id = runCatching {
            activity.app.background { createWithPages(uris.map { PageSource.FromUri(it) }, autoCrop = activity.app.settings.current.autoCrop, name = defaultName("Import")) }
        }.getOrNull()
        importing = false
        if (id != null) nav.openNewDocument(id) else activity.toast("Impossible d'importer ces fichiers.")
    }

    Box {
        NavHost(nav, startDestination = Routes.MAIN) {
            composable(Routes.MAIN) { MainShell(nav) }
            composable(
                "gallery?docId={docId}",
                arguments = listOf(navArgument("docId") { type = NavType.LongType; defaultValue = -1L }),
            ) { GalleryScreen(nav, it.arguments?.getLong("docId") ?: -1L) }
            composable(
                "camera?docId={docId}",
                arguments = listOf(navArgument("docId") { type = NavType.LongType; defaultValue = -1L }),
            ) { CameraScreen(nav, it.arguments?.getLong("docId") ?: -1L) }
            composable("doc/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                DocumentScreen(nav, it.arguments!!.getLong("id"))
            }
            composable("edit/{pageId}", arguments = listOf(navArgument("pageId") { type = NavType.LongType })) {
                PageEditScreen(nav, it.arguments!!.getLong("pageId"))
            }
            composable("reorder/{docId}", arguments = listOf(navArgument("docId") { type = NavType.LongType })) {
                ReorderScreen(nav, it.arguments!!.getLong("docId"))
            }
            composable(
                "sign/{docId}?page={page}",
                arguments = listOf(navArgument("docId") { type = NavType.LongType }, navArgument("page") { type = NavType.IntType; defaultValue = 0 }),
            ) { SignScreen(nav, it.arguments!!.getLong("docId"), it.arguments!!.getInt("page")) }
            composable("text/{docId}", arguments = listOf(navArgument("docId") { type = NavType.LongType })) {
                TextScreen(nav, it.arguments!!.getLong("docId"))
            }
            composable(
                "combine?docId={docId}",
                arguments = listOf(navArgument("docId") { type = NavType.LongType; defaultValue = -1L }),
            ) { CombineScreen(nav, it.arguments?.getLong("docId") ?: -1L) }
            composable("marks/{pageId}", arguments = listOf(navArgument("pageId") { type = NavType.LongType })) {
                MarkupScreen(nav, it.arguments!!.getLong("pageId"))
            }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
        }
        if (importing) BusyOverlay("Importation…")
    }
}
