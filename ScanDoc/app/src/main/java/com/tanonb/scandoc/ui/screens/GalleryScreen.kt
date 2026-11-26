package com.tanonb.scandoc.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.tanonb.scandoc.app
import com.tanonb.scandoc.data.PageSource
import com.tanonb.scandoc.ui.components.BusyOverlay
import com.tanonb.scandoc.ui.components.toast
import com.tanonb.scandoc.ui.openNewDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun galleryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Context.hasGalleryAccess() = galleryPermissions().any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

private fun Context.hasFullGalleryAccess() = ContextCompat.checkSelfPermission(
    this, if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE,
) == PackageManager.PERMISSION_GRANTED

private fun queryImages(context: Context): List<Uri> {
    val out = ArrayList<Uri>()
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Images.Media._ID),
        null, null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (c.moveToNext()) out += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol))
    }
    return out
}

private val galleryThumbs = LruCache<Uri, Bitmap>(120)

/** « Ajouter à partir des photos » : sélection ordonnée de photos de la galerie, recadrage automatique. */
@Composable
fun GalleryScreen(nav: NavHostController, docId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf(context.hasGalleryAccess()) }
    var refresh by remember { mutableStateOf(0) }
    var autoCrop by rememberSaveable { mutableStateOf(context.app.settings.current.autoCrop) }
    val selected = remember { mutableStateListOf<Uri>() }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = context.hasGalleryAccess(); refresh++
    }
    LaunchedEffect(Unit) { if (!access) permLauncher.launch(galleryPermissions()) }

    val images by produceState<List<Uri>?>(null, access, refresh) {
        value = if (access) withContext(Dispatchers.IO) { runCatching { queryImages(context) }.getOrDefault(emptyList()) } else emptyList()
    }

    fun finish(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            progress = 0 to uris.size
            val onProgress: (Int, Int) -> Unit = { i, n -> progress = i to n }
            val sources = uris.map { PageSource.FromUri(it) }
            val result = runCatching {
                if (docId < 0) context.app.background { createWithPages(sources, autoCrop, onProgress = onProgress) }
                else context.app.background { addPages(docId, sources, autoCrop, onProgress = onProgress); docId }
            }.getOrNull()
            progress = null
            when {
                result == null -> context.toast("Impossible de lire ces images.")
                docId < 0 -> nav.openNewDocument(result)
                else -> nav.popBackStack()
            }
        }
    }

    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { finish(it) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.Close, "Fermer") }
            Text(
                if (selected.isEmpty()) "Ajouter à partir des photos" else "${selected.size} photo(s) sélectionnée(s)",
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { finish(selected.toList()) }, enabled = selected.isNotEmpty()) {
                Icon(Icons.Filled.Check, "Valider", tint = if (selected.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(32.dp))
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(autoCrop, { autoCrop = it })
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Recadrage automatique", style = MaterialTheme.typography.titleMedium)
                Text("Détecte les bords de la feuille et redresse la perspective", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (access && !context.hasFullGalleryAccess()) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Accès limité à certaines photos", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { permLauncher.launch(galleryPermissions()) }) { Text("Gérer") }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
            val list = images
            when {
                !access -> NoAccess(
                    onGrant = { permLauncher.launch(galleryPermissions()) },
                    onPicker = { systemPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
                list == null -> Unit
                list.isEmpty() -> Text("Aucune photo trouvée.", Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.toString() }) { uri ->
                        val index = selected.indexOf(uri)
                        GalleryTile(uri, if (index >= 0) index + 1 else null) {
                            if (index >= 0) selected.remove(uri) else selected.add(uri)
                        }
                    }
                }
            }
        }
        if (access) Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { systemPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Ouvrir le sélecteur de photos Android")
            }
        }
    }
    progress?.let { (i, n) -> BusyOverlay("Traitement de la page ${minOf(i + 1, n)} sur $n…", if (n > 0) i.toFloat() / n else null) }
}

@Composable
private fun NoAccess(onGrant: () -> Unit, onPicker: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("ScanDoc a besoin d'accéder à vos photos pour les afficher ici.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) { Text("Autoriser l'accès") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPicker) { Text("Choisir avec le sélecteur Android") }
    }
}

@Composable
private fun GalleryTile(uri: Uri, number: Int?, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState(galleryThumbs.get(uri), uri) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(360, 360), null) }.getOrNull()?.also { galleryThumbs.put(uri, it) }
        }
    }
    Box(
        Modifier.aspectRatio(1f).background(Color(0xFFDADDE3)).clickable(onClick = onClick)
            .then(if (number != null) Modifier.border(3.dp, MaterialTheme.colorScheme.primary) else Modifier),
    ) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (number != null) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.25f)))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("$number", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
