package com.tanonb.scandoc.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tanonb.scandoc.app
import com.tanonb.scandoc.data.CombinePart
import com.tanonb.scandoc.data.PageEntity
import com.tanonb.scandoc.imaging.Compression
import com.tanonb.scandoc.imaging.PageFilter
import com.tanonb.scandoc.imaging.PageSize
import com.tanonb.scandoc.ocr.OcrPage
import com.tanonb.scandoc.ui.components.BusyOverlay
import com.tanonb.scandoc.ui.components.FileThumbnail
import com.tanonb.scandoc.ui.components.formatDate
import com.tanonb.scandoc.ui.components.shareText
import com.tanonb.scandoc.ui.components.toast
import com.tanonb.scandoc.ui.openNewDocument
import kotlinx.coroutines.launch

@Composable
private fun SimpleTopBar(title: String, nav: NavHostController, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
        title = { Text(title) },
        actions = { actions() },
    )
}

// ------------------------------------------------------------------ Réorganiser

@Composable
fun ReorderScreen(nav: NavHostController, docId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val order = remember { mutableStateListOf<PageEntity>() }
    LaunchedEffect(docId) { order.clear(); order.addAll(context.app.repository.pages(docId)) }
    Scaffold(topBar = {
        SimpleTopBar("Réorganiser les pages", nav) {
            IconButton(onClick = {
                scope.launch { context.app.repository.reorder(docId, order.map { it.id }); nav.popBackStack() }
            }) { Icon(Icons.Filled.Check, "Enregistrer", tint = MaterialTheme.colorScheme.primary) }
        }
    }) { padding ->
        LazyVerticalGrid(
            GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(padding).background(MaterialTheme.colorScheme.background),
        ) {
            itemsIndexed(order, key = { _, p -> p.id }) { i, p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FileThumbnail(p.processedPath, maxDim = 400, modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(4.dp)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(enabled = i > 0, onClick = { order.add(i - 1, order.removeAt(i)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Avant") }
                        Text("${i + 1}", fontWeight = FontWeight.Bold)
                        IconButton(enabled = i < order.lastIndex, onClick = { order.add(i + 1, order.removeAt(i)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Après") }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Texte reconnu

@Composable
fun TextScreen(nav: NavHostController, docId: Long) {
    val context = LocalContext.current
    val pages by produceState<List<OcrPage>?>(null, docId) {
        value = runCatching { context.app.background { ensureOcr(docId) } }.getOrDefault(emptyList())
    }
    val all = pages?.mapIndexed { i, p -> "— Page ${i + 1} —\n${p.text}" }?.joinToString("\n\n").orEmpty()
    Scaffold(topBar = {
        SimpleTopBar("Copier le texte", nav) {
            IconButton(enabled = pages != null, onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Texte", pages?.joinToString("\n\n") { it.text }.orEmpty()))
                context.toast("Texte copié")
            }) { Icon(Icons.Outlined.ContentCopy, "Copier tout") }
            IconButton(enabled = pages != null, onClick = { context.shareText(all) }) { Icon(Icons.Outlined.Share, "Partager") }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (pages == null) BusyOverlay("Reconnaissance du texte…")
            else SelectionContainer {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    Text(
                        "Texte reconnu automatiquement : relisez-le avant de l'utiliser. Appui long pour sélectionner une partie.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    pages!!.forEachIndexed { i, p ->
                        Text("Page ${i + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(p.text.ifBlank { "(aucun texte reconnu)" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Combiner des fichiers

private sealed interface CombineItem {
    val key: String
    data class Doc(val id: Long, val name: String, val cover: String?, val pages: Int) : CombineItem { override val key = "d$id" }
    data class External(val uri: Uri, val name: String) : CombineItem { override val key = "u$uri" }
}

private fun Context.displayName(uri: Uri): String = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull() ?: uri.lastPathSegment ?: "Fichier"

/** Combine plusieurs documents ScanDoc et/ou fichiers (PDF, images) de l'appareil en un seul PDF. */
@Composable
fun CombineScreen(nav: NavHostController, preselected: Long) {
    val context = LocalContext.current
    val repo = context.app.repository
    val scope = rememberCoroutineScope()
    val docs by remember { repo.observeDocuments() }.collectAsState(initial = emptyList())
    val chosen = remember { mutableStateListOf<CombineItem>() }
    var name by remember { mutableStateOf(repo.defaultName("Combiné")) }
    var busy by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    if (!initialized && docs.isNotEmpty()) {
        docs.firstOrNull { it.id == preselected }?.let { chosen += CombineItem.Doc(it.id, it.name, it.coverPath, it.pageCount) }
        initialized = true
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u -> if (chosen.none { it.key == "u$u" }) chosen += CombineItem.External(u, context.displayName(u)) }
    }
    fun toggle(item: CombineItem.Doc) { chosen.firstOrNull { it.key == item.key }?.let { chosen.remove(it) } ?: chosen.add(item) }

    Scaffold(
        topBar = { SimpleTopBar("Combiner des fichiers", nav) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nom du nouveau document") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Button(
                        enabled = chosen.size >= 1 && !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val id = runCatching {
                                    // L'ordre choisi est respecté : documents et fichiers sont ajoutés tels qu'ils sont listés.
                                    val items = chosen.map { if (it is CombineItem.Doc) CombinePart.Document(it.id) else CombinePart.File((it as CombineItem.External).uri) }
                                    context.app.background { combine(items, name) }
                                }.getOrNull()
                                busy = false
                                if (id != null) nav.openNewDocument(id) else context.toast("Impossible de combiner ces fichiers.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (chosen.isEmpty()) "Sélectionnez des fichiers" else "Combiner ${chosen.size} fichier(s)") }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (chosen.isNotEmpty()) {
                item { SectionTitle("Ordre de combinaison") }
                itemsIndexed(chosen, key = { _, it -> "sel" + it.key }) { i, it ->
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelLarge) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            when (it) { is CombineItem.Doc -> it.name; is CombineItem.External -> it.name },
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        IconButton(enabled = i > 0, onClick = { chosen.add(i - 1, chosen.removeAt(i)) }) { Icon(Icons.Outlined.KeyboardArrowUp, "Monter") }
                        IconButton(enabled = i < chosen.lastIndex, onClick = { chosen.add(i + 1, chosen.removeAt(i)) }) { Icon(Icons.Outlined.KeyboardArrowDown, "Descendre") }
                        IconButton(onClick = { chosen.removeAt(i) }) { Icon(Icons.Outlined.Delete, "Retirer") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Icon(Icons.Outlined.NoteAdd, null); Spacer(Modifier.width(8.dp)); Text("Ajouter des PDF ou images de l'appareil")
                }
            }
            item { SectionTitle("Mes documents") }
            items(docs, key = { it.id }) { d ->
                val item = CombineItem.Doc(d.id, d.name, d.coverPath, d.pageCount)
                val checked = chosen.any { it.key == item.key }
                Row(
                    Modifier.fillMaxWidth().clickable { toggle(item) }.background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked, { toggle(item) })
                    FileThumbnail(d.coverPath, maxDim = 200, modifier = Modifier.size(44.dp, 58.dp).clip(RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatDate(d.updatedAt)} · ${d.pageCount} page(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (docs.isEmpty()) item {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.InsertDriveFile, null); Spacer(Modifier.width(8.dp))
                    Text("Aucun document : ajoutez des fichiers de l'appareil ci-dessus.")
                }
            }
        }
    }
    if (busy) BusyOverlay("Combinaison en cours…")
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp))
}

// ------------------------------------------------------------------ Préférences

@Composable
fun SettingsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val store = context.app.settings
    val s by store.state.collectAsState()
    Scaffold(topBar = { SimpleTopBar("Préférences", nav) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
            SectionTitle("Numérisation")
            SettingCard {
                SwitchRow("Recadrage automatique", "Détecte les bords de la feuille sur les photos importées", s.autoCrop) { v -> store.update { it.copy(autoCrop = v) } }
                HorizontalDivider()
                SwitchRow("Aplanir les pages courbées", "Redresse une feuille gondolée ou pliée, comme sur un scanner à plat", s.flatten) { v -> store.update { it.copy(flatten = v) } }
                HorizontalDivider()
                Text("Filtre par défaut", Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp), style = MaterialTheme.typography.bodyLarge)
                PageFilter.entries.forEach { f -> RadioRow(f.label, null, s.defaultFilter == f) { store.update { it.copy(defaultFilter = f) } } }
            }
            SectionTitle("PDF")
            SettingCard {
                SwitchRow("Reconnaissance de texte (OCR)", "PDF consultable : recherche et copie du texte, hors ligne", s.searchablePdf) { v -> store.update { it.copy(searchablePdf = v) } }
                HorizontalDivider()
                Text("Qualité / taille du fichier", Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp), style = MaterialTheme.typography.bodyLarge)
                Compression.entries.forEach { c -> RadioRow(c.label, c.detail, s.compression == c) { store.update { it.copy(compression = c) } } }
                HorizontalDivider()
                Text("Taille de page des nouveaux documents", Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp), style = MaterialTheme.typography.bodyLarge)
                PageSize.entries.forEach { p -> RadioRow(p.label, null, s.pageSize == p) { store.update { it.copy(pageSize = p) } } }
            }
            SectionTitle("Accueil")
            SettingCard {
                SwitchRow("Afficher le bandeau d'accueil", null, s.showBanner) { v -> store.update { it.copy(showBanner = v) } }
            }
            SectionTitle("À propos")
            SettingCard {
                Text(
                    "ScanDoc 1.0 — numérisation, PDF, OCR, Word, JPEG, signature. Tout fonctionne hors ligne : vos documents restent sur ce téléphone.\n\n" +
                        "Enregistrements : Téléchargements/ScanDoc (PDF, Word) et Images/ScanDoc (JPEG).",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) { Column { content() } }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Column {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
