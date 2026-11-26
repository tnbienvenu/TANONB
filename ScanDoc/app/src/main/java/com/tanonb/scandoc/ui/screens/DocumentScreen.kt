package com.tanonb.scandoc.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Filter
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tanonb.scandoc.app
import com.tanonb.scandoc.data.PageEntity
import com.tanonb.scandoc.data.PageSource
import com.tanonb.scandoc.imaging.Bitmaps
import com.tanonb.scandoc.imaging.PageFilter
import com.tanonb.scandoc.imaging.PageSize
import com.tanonb.scandoc.imaging.Quad
import com.tanonb.scandoc.ui.Routes
import com.tanonb.scandoc.ui.components.BusyOverlay
import com.tanonb.scandoc.ui.components.ConfirmDialog
import com.tanonb.scandoc.ui.components.DocMenuSheet
import com.tanonb.scandoc.ui.components.FileThumbnail
import com.tanonb.scandoc.ui.components.MenuRow
import com.tanonb.scandoc.ui.components.RenameDialog
import com.tanonb.scandoc.ui.components.rememberDocActions
import com.tanonb.scandoc.ui.components.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DocumentScreen(nav: NavHostController, docId: Long) {
    val context = LocalContext.current
    val repo = context.app.repository
    val scope = rememberCoroutineScope()
    val doc by remember { repo.observeDocument(docId) }.collectAsState(initial = null)
    val pagesOrNull by remember { repo.observePages(docId) }.collectAsState(initial = null)
    val pages = pagesOrNull.orEmpty()
    val pager = rememberPagerState { pages.size }
    val actions = rememberDocActions()
    var busy by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var deletePage by remember { mutableStateOf(false) }
    var showSize by remember { mutableStateOf(false) }
    val pendingTurns = remember { androidx.compose.runtime.mutableStateMapOf<Long, Int>() }

    val current = pages.getOrNull(pager.currentPage.coerceAtMost((pages.size - 1).coerceAtLeast(0)))
    // Document supprimé (dernière page retirée, ou suppression depuis le menu).
    if (pagesOrNull != null && pages.isEmpty() && doc == null) { androidx.compose.runtime.LaunchedEffect(Unit) { nav.popBackStack() } }

    fun work(message: String, block: suspend () -> Unit) {
        scope.launch {
            busy = message
            runCatching { block() }.onFailure { context.toast("Échec : ${it.message}") }
            busy = null
        }
    }
    fun addUris(uris: List<Uri>, autoCrop: Boolean, filter: PageFilter? = null) {
        if (uris.isEmpty()) return
        work("Ajout des pages…") {
            context.app.background {
                addPages(docId, uris.map { PageSource.FromUri(it) }, autoCrop, filter ?: context.app.settings.current.defaultFilter)
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        addUris(uris, autoCrop = false, filter = PageFilter.ORIGINAL)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                title = {
                    Text(doc?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { rename = true })
                },
                actions = {
                    IconButton(onClick = { actions.sharePdf(docId) }) { Icon(Icons.Outlined.IosShare, "Partager") }
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreVert, "Autres") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ToolButton(Icons.Outlined.AddPhotoAlternate, "Ajouter") { showAdd = true }
                    ToolButton(Icons.Outlined.Crop, "Recadrer") { current?.let { nav.navigate(Routes.edit(it.id)) } }
                    ToolButton(Icons.AutoMirrored.Outlined.RotateRight, "Pivoter") {
                        // La page tourne tout de suite à l'écran ; l'image est mise à jour en arrière-plan.
                        current?.let { p ->
                            pendingTurns[p.id] = (pendingTurns[p.id] ?: 0) + 1
                            context.app.appScope.launch { runCatching { repo.rotatePage(p.id, 90) } }
                        }
                    }
                    ToolButton(Icons.Outlined.Filter, "Filtres") { showFilters = true }
                    ToolButton(Icons.Outlined.CleaningServices, "Nettoyer") { current?.let { nav.navigate(Routes.marks(it.id)) } }
                    ToolButton(Icons.Outlined.AspectRatio, "Redimens.") { showSize = true }
                    ToolButton(Icons.Outlined.Reorder, "Réorganiser") { nav.navigate(Routes.reorder(docId)) }
                    ToolButton(Icons.Outlined.Draw, "Signer") { nav.navigate(Routes.sign(docId, pager.currentPage)) }
                    ToolButton(Icons.AutoMirrored.Outlined.TextSnippet, "Texte") { nav.navigate(Routes.text(docId)) }
                    ToolButton(Icons.Outlined.Delete, "Supprimer") { deletePage = true }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceVariant)) {
            HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth(), key = { pages.getOrNull(it)?.id ?: it }) { i ->
                pages.getOrNull(i)?.let { pg ->
                    ZoomablePage(pg, pendingTurns[pg.id] ?: 0) { done -> pendingTurns[pg.id] = ((pendingTurns[pg.id] ?: 0) - done).coerceAtLeast(0) }
                }
            }
            Text(
                if (pages.isEmpty()) "" else "Page ${pager.currentPage + 1} sur ${pages.size}",
                textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            // Bande de vignettes
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pages.forEachIndexed { i, p ->
                    val sel = i == pager.currentPage
                    FileThumbnail(
                        p.processedPath, maxDim = 160,
                        modifier = Modifier.size(48.dp, 64.dp).clip(RoundedCornerShape(4.dp))
                            .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.LightGray, RoundedCornerShape(4.dp))
                            .clickable { scope.launch { pager.animateScrollToPage(i) } },
                    )
                }
            }
        }
    }

    if (showMenu) DocMenuSheet(docId, doc?.name ?: "", actions, onDismiss = { showMenu = false }, onOpen = null,
        onText = { nav.navigate(Routes.text(docId)) }, onSign = { nav.navigate(Routes.sign(docId, pager.currentPage)) }, onCombine = { nav.navigate(Routes.combine(docId)) })
    if (rename) RenameDialog(doc?.name ?: "", onDismiss = { rename = false }) { rename = false; actions.rename(docId, it) }
    if (deletePage && current != null) ConfirmDialog(
        "Supprimer la page ${pager.currentPage + 1} ?",
        if (pages.size == 1) "C'est la seule page : le document sera supprimé." else "Cette page sera retirée du document.",
        "Supprimer", onDismiss = { deletePage = false },
    ) { deletePage = false; work("Suppression…") { repo.deletePage(current.id) } }

    if (showAdd) ModalBottomSheet(onDismissRequest = { showAdd = false }) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Ajouter des pages", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            MenuRow(Icons.Outlined.CameraAlt, "Appareil photo") { showAdd = false; nav.navigate(Routes.camera(docId)) }
            MenuRow(Icons.Outlined.Collections, "À partir des photos") { showAdd = false; nav.navigate(Routes.gallery(docId)) }
            MenuRow(Icons.Outlined.FolderOpen, "Fichiers (PDF ou images)") { showAdd = false; filePicker.launch(arrayOf("application/pdf", "image/*")) }
        }
    }

    if (showSize) ModalBottomSheet(onDismissRequest = { showSize = false }) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Taille de page du PDF", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            val currentSize = PageSize.of(doc?.pageSize)
            PageSize.entries.forEach { size ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        showSize = false
                        scope.launch { repo.setPageSize(docId, size); context.toast("Taille de page : ${size.label}") }
                    }.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(size == currentSize, null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(size.label, style = MaterialTheme.typography.bodyLarge)
                        if (size != PageSize.AUTO) Text(
                            "${(size.w / 72 * 25.4).toInt()} × ${(size.h / 72 * 25.4).toInt()} mm",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showFilters && current != null) FilterSheet(
        page = current,
        onDismiss = { showFilters = false },
        onApply = { filter, all ->
            showFilters = false
            work(if (all) "Application du filtre à toutes les pages…" else "Application du filtre…") {
                context.app.background {
                    if (all) setFilterForAll(docId, filter)
                    else updatePage(current.id, Quad.decode(current.quad), current.rotation, filter)
                }
            }
        },
    )

    (busy ?: actions.busy.value)?.let { BusyOverlay(it) }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp).width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Page affichée avec zoom (pincer, double-tap) et rotation animée immédiate. */
@Composable
private fun ZoomablePage(page: PageEntity, pendingTurns: Int, onTurnsApplied: (Int) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Version de la page réellement affichée (l'image se recharge après le traitement).
    var shown by remember { mutableStateOf<PageEntity?>(null) }
    val angle = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(pendingTurns) {
        val target = pendingTurns * 90f
        if (target > angle.value) angle.animateTo(target, androidx.compose.animation.core.tween(220)) else angle.snapTo(target)
    }
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().padding(16.dp)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) }
            .pointerInput(Unit) {
                // Un doigt sans zoom : le glissement reste au défilement des pages. Deux doigts ou zoom actif : zoom/déplacement.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale <= 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val sp = shown ?: page
        val bw = constraints.maxWidth.toFloat().coerceAtLeast(1f); val bh = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val iw = sp.width.coerceAtLeast(1).toFloat(); val ih = sp.height.coerceAtLeast(1).toFloat()
        // Mise à l'échelle pendant le quart de tour : l'image tournée doit tenir dans le cadre.
        val ratio = minOf(bw / ih, bh / iw) / minOf(bw / iw, bh / ih)
        val m = ((angle.value % 180f) + 180f) % 180f
        val f = if (m <= 90f) m / 90f else (180f - m) / 90f
        val rotScale = 1f + (ratio - 1f) * f
        FileThumbnail(
            page.processedPath, maxDim = 1600, contentScale = ContentScale.Fit,
            tag = page,
            onShown = { t ->
                val now = t as? PageEntity ?: return@FileThumbnail
                val before = shown
                shown = now
                if (before != null && before.id == now.id && before.rotation != now.rotation) {
                    onTurnsApplied(((now.rotation - before.rotation + 360) % 360) / 90)
                }
            },
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale * rotScale; scaleY = scale * rotScale; translationX = offset.x; translationY = offset.y
                rotationZ = angle.value
            },
        )
    }
}

/** Choix du filtre avec aperçus calculés sur la page courante. */
@Composable
private fun FilterSheet(page: PageEntity, onDismiss: () -> Unit, onApply: (PageFilter, Boolean) -> Unit) {
    var chosen by remember { mutableStateOf(PageFilter.of(page.filter)) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Filtres", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PageFilter.entries.forEach { f ->
                    val preview by produceState<Bitmap?>(null, page.id, f) {
                        value = withContext(Dispatchers.Default) {
                            Bitmaps.load(File(page.originalPath), 360)?.let { o ->
                                Bitmaps.render(o, Quad.decode(page.quad), page.rotation, f, maxDim = 300).also { o.recycle() }
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { chosen = f }) {
                        Box(
                            Modifier.size(84.dp, 110.dp).clip(RoundedCornerShape(6.dp)).background(Color.LightGray)
                                .border(if (chosen == f) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
                        ) { preview?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                        Spacer(Modifier.height(4.dp))
                        Text(f.label, style = MaterialTheme.typography.labelMedium, color = if (chosen == f) MaterialTheme.colorScheme.primary else Color.Unspecified)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onApply(chosen, true) }, modifier = Modifier.weight(1f)) { Text("Toutes les pages") }
                Button(onClick = { onApply(chosen, false) }, modifier = Modifier.weight(1f)) { Text("Cette page") }
            }
        }
    }
}
