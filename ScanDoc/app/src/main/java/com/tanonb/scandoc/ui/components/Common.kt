package com.tanonb.scandoc.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tanonb.scandoc.imaging.Bitmaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------ vignettes

private val thumbCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
}

/** Vignette d'un fichier image, mise en cache (la clé inclut la date de modification). */
@Composable
fun FileThumbnail(
    path: String?, modifier: Modifier = Modifier, maxDim: Int = 400, contentScale: ContentScale = ContentScale.Crop,
    /** Valeur associée à la version affichée, renvoyée par [onShown] quand cette version apparaît à l'écran. */
    tag: Any? = null, onShown: ((Any?) -> Unit)? = null,
    /** Fond affiché derrière l'image (transparent dans la visionneuse : la page repose sur le fond noir). */
    background: Color = Color(0xFFE9EBF0),
) {
    val file = path?.let { File(it) }
    val key = file?.let { "${it.absolutePath}:${it.lastModified()}:$maxDim" }
    // Quand la page change (filtre, recadrage…), la clé change : on recharge l'image au lieu de garder l'ancienne.
    val bmp by produceState<Bitmap?>(key?.let { thumbCache.get(it) }, key) {
        if (key == null) { value = null; return@produceState }
        val cached = thumbCache.get(key)
        val loaded = cached ?: withContext(Dispatchers.IO) { Bitmaps.thumbnail(file, maxDim)?.also { thumbCache.put(key, it) } }
        if (loaded != null) { value = loaded; onShown?.invoke(tag) }
    }
    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = contentScale) }
    }
}

// ------------------------------------------------------------------ dates

private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
private val dateTimeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
fun formatDate(t: Long): String = dateFmt.format(Date(t))
fun formatDateTime(t: Long): String = dateTimeFmt.format(Date(t))

// ------------------------------------------------------------------ partage

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

fun Context.shareFiles(files: List<File>, mime: String, title: String = "Partager") {
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }.setType(mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(intent, title))
}

fun Context.shareText(text: String) {
    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Partager le texte"))
}

fun Context.openFile(file: File, mime: String) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }.onFailure { toast("Aucune application ne peut ouvrir ce fichier.") }
}

// ------------------------------------------------------------------ impression

/** Envoie un PDF au service d'impression Android (imprimantes Wi-Fi, « Enregistrer au format PDF »…). */
fun Context.printPdf(file: File, jobName: String) {
    val pm = getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
    pm.print(jobName, object : android.print.PrintDocumentAdapter() {
        override fun onLayout(
            old: android.print.PrintAttributes?, new: android.print.PrintAttributes?, cancel: android.os.CancellationSignal?,
            callback: LayoutResultCallback, extras: android.os.Bundle?,
        ) {
            if (cancel?.isCanceled == true) { callback.onLayoutCancelled(); return }
            callback.onLayoutFinished(
                android.print.PrintDocumentInfo.Builder(file.name).setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),
                true,
            )
        }

        override fun onWrite(
            pages: Array<out android.print.PageRange>?, destination: android.os.ParcelFileDescriptor,
            cancel: android.os.CancellationSignal?, callback: WriteResultCallback,
        ) {
            runCatching {
                file.inputStream().use { input -> java.io.FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
            }.onSuccess { callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES)) }
                .onFailure { callback.onWriteFailed(it.message) }
        }
    }, null)
}

// ------------------------------------------------------------------ dialogues

@Composable
fun BusyOverlay(message: String, progress: Float? = null) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (progress == null) CircularProgressIndicator() else LinearProgressIndicator(progress = { progress })
                Spacer(Modifier.height(16.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun RenameDialog(initial: String, title: String = "Renommer", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("Nom du document") }) },
        confirmButton = { TextButton(onClick = { onConfirm(value.text) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
