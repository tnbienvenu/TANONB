package com.tanonb.scandoc.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanonb.scandoc.app
import com.tanonb.scandoc.imaging.Compression
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

/** Actions d'export communes à tous les écrans (avec indicateur d'attente). */
class DocActions(private val context: Context, private val scope: CoroutineScope, val busy: MutableState<String?>) {

    private fun run(message: String, block: suspend () -> Unit) {
        scope.launch {
            busy.value = message
            runCatching { block() }.onFailure { context.toast("Échec : ${it.message ?: it.javaClass.simpleName}") }
            busy.value = null
        }
    }

    fun sharePdf(id: Long) = run("Création du PDF…") { context.shareFiles(listOf(context.app.background { exportPdf(id) }), "application/pdf") }
    fun shareJpeg(id: Long) = run("Préparation des images…") { context.shareFiles(context.app.background { exportJpegFiles(id) }, "image/jpeg") }
    fun shareWord(id: Long) = run("Reconnaissance du texte…") { context.shareFiles(listOf(context.app.background { exportDocx(id) }), MIME_DOCX) }

    fun savePdf(id: Long) = run("Création du PDF…") {
        val where = context.app.background { saveToDownloads(exportPdf(id), "application/pdf") }
        context.toast("PDF enregistré dans $where")
    }

    fun saveWord(id: Long) = run("Conversion au format Word…") {
        val file = context.app.background { exportDocx(id) }
        val where = context.app.background { saveToDownloads(file, MIME_DOCX) }
        context.toast("Document Word enregistré dans $where")
        context.openFile(file, MIME_DOCX)
    }

    fun saveJpeg(id: Long) = run("Enregistrement des images…") {
        val n = context.app.background { saveJpegsToGallery(id) }
        context.toast("$n image(s) JPEG enregistrée(s) dans Images/ScanDoc")
    }

    fun print(id: Long, name: String) = run("Préparation de l'impression…") { context.printPdf(context.app.background { exportPdf(id) }, name) }

    /** PDF compressé : partage, ou enregistrement dans Téléchargements, avec la taille obtenue. */
    fun compressed(id: Long, level: Compression, share: Boolean) = run("Compression du PDF…") {
        val file = context.app.background { exportPdf(id, compression = level) }
        val size = android.text.format.Formatter.formatShortFileSize(context, file.length())
        if (share) context.shareFiles(listOf(file), "application/pdf")
        else context.toast("PDF compressé ($size) enregistré dans " + context.app.background { saveToDownloads(file, "application/pdf") })
        if (share) context.toast("Taille du PDF : $size")
    }

    fun protect(id: Long, password: String, share: Boolean) = run("Chiffrement du PDF…") {
        val file = context.app.background { exportPdf(id, password = password) }
        if (share) context.shareFiles(listOf(file), "application/pdf")
        else context.toast("PDF protégé enregistré dans " + context.app.background { saveToDownloads(file, "application/pdf") })
    }

    fun openPdf(id: Long) = run("Création du PDF…") { context.openFile(context.app.background { exportPdf(id) }, "application/pdf") }

    fun rename(id: Long, name: String) = run("Enregistrement…") { context.app.repository.rename(id, name) }
    fun delete(id: Long) = run("Suppression…") { context.app.repository.deleteDocument(id) }
}

@Composable
fun rememberDocActions(): DocActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateOf<String?>(null) }
    return remember { DocActions(context, scope, busy) }
}

/** Menu « Autres » d'un document. */
@Composable
fun DocMenuSheet(
    docId: Long,
    name: String,
    actions: DocActions,
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)?,
    onText: () -> Unit,
    onSign: () -> Unit,
    onCombine: () -> Unit,
) {
    var rename by remember { mutableStateOf(false) }
    var compress by remember { mutableStateOf(false) }
    var protect by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            HorizontalDivider()
            fun act(f: () -> Unit): () -> Unit = { onDismiss(); f() }
            onOpen?.let { MenuRow(Icons.Outlined.OpenInNew, "Ouvrir", onClick = act(it)) }
            MenuRow(Icons.Outlined.PictureAsPdf, "Afficher le PDF", onClick = act { actions.openPdf(docId) })
            MenuRow(Icons.Outlined.Share, "Partager le PDF", onClick = act { actions.sharePdf(docId) })
            MenuRow(Icons.Outlined.Image, "Partager en JPEG", onClick = act { actions.shareJpeg(docId) })
            MenuRow(Icons.Outlined.Description, "Partager au format Word", onClick = act { actions.shareWord(docId) })
            HorizontalDivider()
            MenuRow(Icons.Outlined.FileDownload, "Enregistrer le PDF (Téléchargements)", onClick = act { actions.savePdf(docId) })
            MenuRow(Icons.Outlined.Description, "Enregistrer au format Word", onClick = act { actions.saveWord(docId) })
            MenuRow(Icons.Outlined.Image, "Enregistrer au format JPEG (galerie)", onClick = act { actions.saveJpeg(docId) })
            HorizontalDivider()
            MenuRow(Icons.Outlined.Draw, "Remplir et signer", onClick = act(onSign))
            MenuRow(Icons.AutoMirrored.Outlined.TextSnippet, "Copier le texte", onClick = act(onText))
            MenuRow(Icons.Outlined.Print, "Imprimer", onClick = act { actions.print(docId, name) })
            MenuRow(Icons.Outlined.Compress, "Compresser le PDF") { compress = true }
            MenuRow(Icons.Outlined.Lock, "Protéger par un mot de passe") { protect = true }
            MenuRow(Icons.AutoMirrored.Outlined.MergeType, "Combiner avec d'autres fichiers", onClick = act(onCombine))
            MenuRow(Icons.Outlined.DriveFileRenameOutline, "Renommer") { rename = true }
            MenuRow(Icons.Outlined.Delete, "Supprimer", tint = MaterialTheme.colorScheme.error) { delete = true }
        }
    }
    if (rename) RenameDialog(name, onDismiss = { rename = false }) { rename = false; onDismiss(); actions.rename(docId, it) }
    if (compress) CompressDialog(onDismiss = { compress = false }) { level, share -> compress = false; onDismiss(); actions.compressed(docId, level, share) }
    if (protect) PasswordDialog(onDismiss = { protect = false }) { pw, share -> protect = false; onDismiss(); actions.protect(docId, pw, share) }
    if (delete) ConfirmDialog("Supprimer « $name » ?", "Le document et toutes ses pages seront définitivement supprimés.", "Supprimer",
        onDismiss = { delete = false }) { delete = false; onDismiss(); actions.delete(docId) }
}

@Composable
fun MenuRow(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(20.dp))
        Text(label, color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun CompressDialog(onDismiss: () -> Unit, onDone: (Compression, Boolean) -> Unit) {
    var level by remember { mutableStateOf(Compression.LOW) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compresser le PDF") },
        text = {
            Column {
                Compression.entries.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { level = c }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(level == c, { level = c })
                        Column {
                            Text(c.label, style = MaterialTheme.typography.bodyLarge)
                            Text(c.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onDone(level, true) }) { Text("Partager") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { onDone(level, false) }) { Text("Enregistrer") } },
    )
}

@Composable
fun PasswordDialog(onDismiss: () -> Unit, onDone: (String, Boolean) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val ok = pw.length >= 4 && pw == pw2
    val transform = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Protéger par un mot de passe") },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Text("Le mot de passe sera demandé à l'ouverture du PDF. Il n'est pas conservé : notez-le.", style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.OutlinedTextField(pw, { pw = it }, label = { Text("Mot de passe (4 caractères min.)") }, singleLine = true, visualTransformation = transform)
                androidx.compose.material3.OutlinedTextField(pw2, { pw2 = it }, label = { Text("Confirmer") }, singleLine = true, visualTransformation = transform,
                    isError = pw2.isNotEmpty() && pw != pw2)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { visible = !visible }) {
                    androidx.compose.material3.Checkbox(visible, { visible = it }); Text("Afficher")
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(enabled = ok, onClick = { onDone(pw, true) }) { Text("Partager") } },
        dismissButton = { androidx.compose.material3.TextButton(enabled = ok, onClick = { onDone(pw, false) }) { Text("Enregistrer") } },
    )
}
