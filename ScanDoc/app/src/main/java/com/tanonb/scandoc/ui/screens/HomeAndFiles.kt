package com.tanonb.scandoc.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tanonb.scandoc.app
import com.tanonb.scandoc.data.DocumentSummary
import com.tanonb.scandoc.ui.Routes
import com.tanonb.scandoc.ui.components.BusyOverlay
import com.tanonb.scandoc.ui.components.DocActions
import com.tanonb.scandoc.ui.components.DocMenuSheet
import com.tanonb.scandoc.ui.components.FileThumbnail
import com.tanonb.scandoc.ui.components.formatDate
import com.tanonb.scandoc.ui.components.formatDateTime
import com.tanonb.scandoc.ui.components.rememberDocActions

private val BOTTOM_SPACE = PaddingValues(bottom = 120.dp)

@Composable
fun HomeTab(nav: NavHostController, onSearch: () -> Unit) {
    val context = LocalContext.current
    val docs by remember { context.app.repository.observeDocuments() }.collectAsState(initial = null)
    val ocr by context.app.repository.ocrRunning.collectAsState()
    val actions = rememberDocActions()
    var menuFor by remember { mutableStateOf<DocumentSummary?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text("ScanDoc", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Rechercher", Modifier.size(28.dp)) }
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Outlined.Settings, "Réglages", Modifier.size(28.dp)) }
        }
        val settings by context.app.settings.state.collectAsState()
        var features by remember { mutableStateOf(false) }
        if (settings.showBanner) Banner(onDiscover = { features = true })
        if (features) FeaturesDialog(onDismiss = { features = false }, onHide = { features = false; context.app.settings.update { it.copy(showBanner = false) } })
        if (ocr > 0) Text(
            "Reconnaissance du texte en cours ($ocr page(s))…",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        val list = docs
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState()
            else -> LazyColumn(contentPadding = BOTTOM_SPACE, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { FeaturedDocument(list.first(), actions, onOpen = { nav.navigate(Routes.doc(list.first().id)) }, onMore = { menuFor = list.first() }) }
                if (list.size > 1) item {
                    Text("Récents", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
                items(list.drop(1), key = { it.id }) { d ->
                    DocumentCard(d, actions,
                        onOpen = { nav.navigate(Routes.doc(d.id)) },
                        onSign = { nav.navigate(Routes.sign(d.id)) },
                        onMore = { menuFor = d })
                }
            }
        }
    }
    menuFor?.let { d ->
        DocMenuSheet(d.id, d.name, actions, onDismiss = { menuFor = null },
            onOpen = { nav.navigate(Routes.doc(d.id)) }, onText = { nav.navigate(Routes.text(d.id)) }, onSign = { nav.navigate(Routes.sign(d.id)) }, onCombine = { nav.navigate(Routes.combine(d.id)) })
    }
    actions.busy.value?.let { BusyOverlay(it) }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(20.dp))
        Text("Aucun document", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Appuyez sur le bouton bleu pour numériser un document, créer un PDF à partir de vos photos ou combiner des fichiers.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(120.dp))
    }
}

/** Dernier document : grande vignette et actions rapides (comme sur l'écran d'accueil d'Adobe Scan). */
@Composable
private fun FeaturedDocument(d: DocumentSummary, actions: DocActions, onOpen: () -> Unit, onMore: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(d.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = onOpen))
            Text("${formatDate(d.updatedAt)} · ${d.pageCount} page(s)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileThumbnail(
                    d.coverPath, maxDim = 700,
                    modifier = Modifier.width(150.dp).height(200.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpen),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    QuickAction(Icons.Outlined.IosShare, "Partager") { actions.sharePdf(d.id) }
                    QuickAction(Icons.Outlined.Description, "Enregistrer au format Word") { actions.saveWord(d.id) }
                    QuickAction(Icons.Outlined.Image, "Enregistrer au format JPEG") { actions.saveJpeg(d.id) }
                    QuickAction(Icons.Outlined.MoreHoriz, "Autres", onMore)
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DocumentCard(d: DocumentSummary, actions: DocActions, onOpen: () -> Unit, onSign: () -> Unit, onMore: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(12.dp)) {
            FileThumbnail(d.coverPath, maxDim = 400, modifier = Modifier.width(110.dp).height(120.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).height(120.dp)) {
                Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${formatDate(d.updatedAt)} · ${d.pageCount} p.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { actions.sharePdf(d.id) }) { Icon(Icons.Outlined.IosShare, "Partager") }
                    IconButton(onClick = onOpen) { Icon(Icons.Outlined.Edit, "Modifier") }
                    IconButton(onClick = onSign) { Icon(Icons.Outlined.Draw, "Signer") }
                    IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreHoriz, "Autres") }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Fichiers

private enum class Sort(val label: String) { DATE("Date de modification"), NAME("Nom"), CREATED("Date de création"), PAGES("Nombre de pages") }

@Composable
fun FilesTab(nav: NavHostController, focusSearch: Boolean, onSearchFocused: () -> Unit) {
    val context = LocalContext.current
    val docs by remember { context.app.repository.observeDocuments() }.collectAsState(initial = emptyList())
    val actions = rememberDocActions()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(Sort.DATE) }
    var sortMenu by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<DocumentSummary?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) { focus.requestFocus(); onSearchFocused() } }

    val shown = docs.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }.let { l ->
        when (sort) {
            Sort.DATE -> l.sortedByDescending { it.updatedAt }
            Sort.NAME -> l.sortedBy { it.name.lowercase() }
            Sort.CREATED -> l.sortedByDescending { it.createdAt }
            Sort.PAGES -> l.sortedByDescending { it.pageCount }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Fichiers", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, "Trier") }
                DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                    Sort.entries.forEach { s ->
                        DropdownMenuItem(text = { Text((if (s == sort) "✓ " else "") + s.label) }, onClick = { sort = s; sortMenu = false })
                    }
                }
            }
        }
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("Rechercher un document") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Effacer") } },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
        )
        Text("${shown.size} document(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(contentPadding = BOTTOM_SPACE) {
            items(shown, key = { it.id }) { d ->
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate(Routes.doc(d.id)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FileThumbnail(d.coverPath, maxDim = 200, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp, 72.dp).clip(RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${formatDateTime(d.updatedAt)} · ${d.pageCount} page(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { menuFor = d }) { Icon(Icons.Outlined.MoreHoriz, "Autres") }
                }
            }
        }
    }
    menuFor?.let { d ->
        DocMenuSheet(d.id, d.name, actions, onDismiss = { menuFor = null },
            onOpen = { nav.navigate(Routes.doc(d.id)) }, onText = { nav.navigate(Routes.text(d.id)) }, onSign = { nav.navigate(Routes.sign(d.id)) }, onCombine = { nav.navigate(Routes.combine(d.id)) })
    }
    actions.busy.value?.let { BusyOverlay(it) }
}

/** Bandeau d'accueil en dégradé. */
@Composable
private fun Banner(onDiscover: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(com.tanonb.scandoc.ui.theme.BannerGradient).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Toutes les fonctions\nsont incluses", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        androidx.compose.material3.OutlinedButton(
            onClick = onDiscover,
            border = BorderStroke(2.dp, Color.White),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) { Text("Découvrir", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun FeaturesDialog(onDismiss: () -> Unit, onHide: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fonctions incluses") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                listOf(
                    "Numérisation avec l'appareil photo, détection des bords",
                    "Création de PDF à partir de vos photos",
                    "Combinaison de documents et de fichiers PDF",
                    "Recadrage, rotation, filtres, nettoyage",
                    "Reconnaissance de texte (PDF consultable)",
                    "Enregistrement au format Word et JPEG",
                    "Remplir et signer",
                    "Compression et protection par mot de passe",
                    "Taille de page (A4, Lettre…) et impression",
                ).forEach { Text("•  $it", modifier = Modifier.padding(vertical = 3.dp)) }
                Spacer(Modifier.height(8.dp))
                Text("Aucun abonnement, aucun compte : tout fonctionne hors ligne.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onHide) { Text("Masquer le bandeau") } },
    )
}
