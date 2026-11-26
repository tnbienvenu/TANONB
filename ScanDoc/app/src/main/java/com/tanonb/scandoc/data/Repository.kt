package com.tanonb.scandoc.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.MediaStore
import com.tanonb.scandoc.export.DocxWriter
import com.tanonb.scandoc.export.PdfWriter
import com.tanonb.scandoc.imaging.Bitmaps
import com.tanonb.scandoc.imaging.Compression
import com.tanonb.scandoc.imaging.Mark
import com.tanonb.scandoc.imaging.PageSize
import com.tanonb.scandoc.imaging.PageFilter
import com.tanonb.scandoc.imaging.Quad
import com.tanonb.scandoc.imaging.Stamp
import com.tanonb.scandoc.ocr.OcrEngine
import com.tanonb.scandoc.ocr.OcrPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Élément à combiner. */
sealed interface CombinePart {
    data class Document(val id: Long) : CombinePart
    data class File(val uri: Uri) : CombinePart
}

/** Source d'une nouvelle page. */
sealed interface PageSource {
    data class FromUri(val uri: Uri) : PageSource
    data class FromFile(val file: File) : PageSource
}

class Repository(
    private val context: Context,
    private val dao: ScanDao,
    private val settings: SettingsStore,
    private val appScope: CoroutineScope,
) {
    private val root = File(context.filesDir, "docs").apply { mkdirs() }
    private val exportDir get() = File(context.cacheDir, "exports").apply { mkdirs() }
    private val ocrMutex = Mutex()
    /** Une seule modification de page à la fois (évite les conflits si l'on touche plusieurs fois Pivoter). */
    private val editMutex = Mutex()

    /** Nombre de pages en cours de reconnaissance de texte (affiché discrètement dans l'interface). */
    private val _ocrRunning = MutableStateFlow(0)
    val ocrRunning: StateFlow<Int> = _ocrRunning

    fun observeDocuments(): Flow<List<DocumentSummary>> = dao.observeDocuments()
    fun observeDocument(id: Long): Flow<DocumentEntity?> = dao.observeDocument(id)
    fun observePages(id: Long): Flow<List<PageEntity>> = dao.observePages(id)
    suspend fun pages(id: Long) = dao.pages(id)
    suspend fun page(id: Long) = dao.page(id)
    suspend fun document(id: Long) = dao.document(id)

    fun defaultName(prefix: String = "Scan"): String =
        "$prefix " + SimpleDateFormat("d MMM yyyy HH'h'mm", Locale.FRANCE).format(Date())

    // ---------------------------------------------------------------- création

    suspend fun createDocument(name: String? = null): Long {
        val now = System.currentTimeMillis()
        return dao.insert(DocumentEntity(name = name ?: defaultName(), createdAt = now, updatedAt = now, pageSize = settings.current.pageSize.name))
    }

    /**
     * Ajoute des pages à un document. [autoCrop] détecte les bords, [filter] (sinon celui des réglages)
     * est appliqué. Les fichiers PDF sont découpés en pages.
     */
    suspend fun addPages(
        docId: Long,
        sources: List<PageSource>,
        autoCrop: Boolean = settings.current.autoCrop,
        filter: PageFilter = settings.current.defaultFilter,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Int = withContext(Dispatchers.Default) {
        var position = dao.pages(docId).size
        var added = 0
        sources.forEachIndexed { i, src ->
            onProgress(i, sources.size)
            val isPdf = src is PageSource.FromUri && context.contentResolver.getType(src.uri) == "application/pdf"
            if (isPdf) {
                renderPdf((src as PageSource.FromUri).uri) { bmp ->
                    insertPage(docId, position++, bmp, Quad.FULL, PageFilter.ORIGINAL); added++
                }
            } else {
                val bmp = when (src) {
                    is PageSource.FromUri -> Bitmaps.load(context, src.uri)
                    is PageSource.FromFile -> Bitmaps.load(src.file)
                } ?: return@forEachIndexed
                val quad = if (autoCrop) Bitmaps.detect(bmp) ?: Quad.FULL else Quad.FULL
                insertPage(docId, position++, bmp, quad, filter); added++
                bmp.recycle()
            }
        }
        onProgress(sources.size, sources.size)
        dao.touch(docId)
        launchOcr()
        added
    }

    suspend fun createWithPages(sources: List<PageSource>, autoCrop: Boolean, filter: PageFilter = settings.current.defaultFilter, name: String? = null, onProgress: (Int, Int) -> Unit = { _, _ -> }): Long? {
        val id = createDocument(name)
        val n = addPages(id, sources, autoCrop, filter, onProgress)
        if (n == 0) { deleteDocument(id); return null }
        return id
    }

    private suspend fun insertPage(docId: Long, position: Int, original: Bitmap, quad: Quad, filter: PageFilter) {
        val dir = File(root, docId.toString()).apply { mkdirs() }
        val key = UUID.randomUUID().toString().take(12)
        val origFile = File(dir, "${key}_orig.jpg")
        val procFile = File(dir, "$key.jpg")
        Bitmaps.saveJpeg(original, origFile, 92)
        val processed = Bitmaps.render(original, quad, 0, filter)
        Bitmaps.saveJpeg(processed, procFile, 90)
        dao.insert(PageEntity(
            documentId = docId, position = position, originalPath = origFile.absolutePath, processedPath = procFile.absolutePath,
            quad = quad.encode(), rotation = 0, filter = filter.name, width = processed.width, height = processed.height,
        ))
        processed.recycle()
    }

    private suspend fun renderPdf(uri: Uri, onPage: suspend (Bitmap) -> Unit) {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            PdfRenderer(fd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val scale = 1700f / maxOf(page.width, page.height)
                        val bmp = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        onPage(bmp)
                        bmp.recycle()
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- modification

    /** Recalcule l'image traitée d'une page avec de nouveaux réglages (null = inchangé). */
    suspend fun updatePage(
        pageId: Long, quad: Quad? = null, rotation: Int? = null, filter: PageFilter? = null,
        stamps: List<Stamp>? = null, marks: List<Mark>? = null,
    ) = editMutex.withLock { withContext(Dispatchers.Default) {
        val p = dao.page(pageId) ?: return@withContext
        val q = quad ?: Quad.decode(p.quad)
        val rot = (((rotation ?: p.rotation) % 360) + 360) % 360
        val f = filter ?: PageFilter.of(p.filter)
        val st = stamps ?: Stamp.decodeList(p.stamps)
        // Les retouches sont liées à la géométrie : on les efface si le recadrage ou la rotation change.
        val geometryChanged = q.encode() != p.quad || rot != p.rotation
        val mk = marks ?: if (geometryChanged) emptyList() else Mark.decodeList(p.marks)
        val original = Bitmaps.load(File(p.originalPath)) ?: return@withContext
        val processed = Bitmaps.render(original, q, rot, f, mk, st)
        original.recycle()
        Bitmaps.saveJpeg(processed, File(p.processedPath), 90)
        dao.update(p.copy(
            quad = q.encode(), rotation = rot, filter = f.name, stamps = Stamp.encodeList(st), marks = Mark.encodeList(mk),
            width = processed.width, height = processed.height,
            ocr = if (geometryChanged || f.name != p.filter || marks != null) null else p.ocr,
        ))
        processed.recycle()
        dao.touch(p.documentId)
        launchOcr()
    } }

    /** Image de base d'une page (recadrée, pivotée, filtrée) sans retouches ni signatures, pour les éditeurs. */
    suspend fun baseImage(pageId: Long, withMarks: Boolean, maxDim: Int = 1600): Pair<PageEntity, Bitmap>? = withContext(Dispatchers.Default) {
        val p = dao.page(pageId) ?: return@withContext null
        val original = Bitmaps.load(File(p.originalPath)) ?: return@withContext null
        val b = Bitmaps.render(original, Quad.decode(p.quad), p.rotation, PageFilter.of(p.filter), if (withMarks) Mark.decodeList(p.marks) else emptyList(), emptyList(), maxDim)
        original.recycle()
        p to b
    }

    suspend fun setPageSize(docId: Long, size: PageSize) = dao.setPageSize(docId, size.name)

    /**
     * Rotation rapide : on fait tourner l'image déjà traitée (sans refaire recadrage ni filtre) et on
     * transforme les coordonnées des retouches et signatures pour qu'elles suivent la page.
     */
    suspend fun rotatePage(pageId: Long, delta: Int) = editMutex.withLock {
        withContext(Dispatchers.Default) {
            val p = dao.page(pageId) ?: return@withContext
            val turns = ((delta / 90) % 4 + 4) % 4
            if (turns == 0) return@withContext
            val file = File(p.processedPath)
            val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext
            val rotated = Bitmaps.rotate(bmp, turns * 90)
            Bitmaps.saveJpeg(rotated, file, 90)
            var w = p.width.toFloat(); var h = p.height.toFloat()
            var marks = Mark.decodeList(p.marks); var stamps = Stamp.decodeList(p.stamps)
            repeat(turns) {
                // Quart de tour horaire : (x, y) → (1 − y, x) ; les tailles sont relatives à la largeur.
                marks = marks.map { m -> m.copy(points = m.points.map { pt -> com.tanonb.scandoc.imaging.NPoint(1 - pt.y, pt.x) }, width = m.width * w / h) }
                stamps = stamps.map { st -> st.copy(cx = 1 - st.cy, cy = st.cx, size = st.size * w / h) }
                val t = w; w = h; h = t
            }
            dao.update(p.copy(
                rotation = (p.rotation + turns * 90) % 360, width = rotated.width, height = rotated.height,
                marks = Mark.encodeList(marks), stamps = Stamp.encodeList(stamps), ocr = null,
            ))
            rotated.recycle()
            dao.touch(p.documentId)
            launchOcr()
        }
    }

    suspend fun setFilterForAll(docId: Long, filter: PageFilter) {
        dao.pages(docId).forEach { updatePage(it.id, filter = filter) }
    }

    suspend fun deletePage(pageId: Long) {
        val p = dao.page(pageId) ?: return
        dao.deletePage(pageId)
        File(p.originalPath).delete(); File(p.processedPath).delete()
        val rest = dao.pages(p.documentId)
        if (rest.isEmpty()) deleteDocument(p.documentId) else { dao.reorder(rest); dao.touch(p.documentId) }
    }

    suspend fun reorder(docId: Long, orderedIds: List<Long>) {
        val pages = dao.pages(docId).associateBy { it.id }
        dao.reorder(orderedIds.mapNotNull { pages[it] })
        dao.touch(docId)
    }

    suspend fun rename(docId: Long, name: String) = dao.rename(docId, name.trim().ifBlank { defaultName() })

    suspend fun deleteDocument(docId: Long) {
        dao.deleteDocument(docId)
        File(root, docId.toString()).deleteRecursively()
    }

    /** Combine, dans l'ordre donné, des documents existants et des fichiers (PDF, images) en un nouveau document. */
    suspend fun combine(parts: List<CombinePart>, name: String?): Long? = withContext(Dispatchers.IO) {
        val newId = createDocument(name?.trim()?.ifBlank { null } ?: defaultName("Combiné"))
        val dir = File(root, newId.toString()).apply { mkdirs() }
        for (part in parts) when (part) {
            is CombinePart.Document -> {
                var position = dao.pages(newId).size
                for (p in dao.pages(part.id)) {
                    val key = UUID.randomUUID().toString().take(12)
                    val o = File(dir, "${key}_orig.jpg"); val pr = File(dir, "$key.jpg")
                    File(p.originalPath).copyTo(o, overwrite = true); File(p.processedPath).copyTo(pr, overwrite = true)
                    // Les signatures restent valables : leurs fichiers sont partagés et jamais modifiés.
                    dao.insert(p.copy(id = 0, documentId = newId, position = position++, originalPath = o.absolutePath, processedPath = pr.absolutePath))
                }
            }
            is CombinePart.File -> addPages(newId, listOf(PageSource.FromUri(part.uri)), autoCrop = false, filter = PageFilter.ORIGINAL)
        }
        if (dao.pages(newId).isEmpty()) { deleteDocument(newId); null } else newId
    }

    // ---------------------------------------------------------------- OCR

    private fun launchOcr() {
        appScope.launch { runCatching { ocrPending() } }
    }

    private suspend fun ocrPending() = ocrMutex.withLock {
        val todo = dao.pagesWithoutOcr()
        _ocrRunning.value = todo.size
        for (p in todo) {
            runCatching { recognize(p) }
            _ocrRunning.value = (_ocrRunning.value - 1).coerceAtLeast(0)
        }
        _ocrRunning.value = 0
    }

    private suspend fun recognize(p: PageEntity): OcrPage {
        val bmp = withContext(Dispatchers.IO) { Bitmaps.load(File(p.processedPath)) } ?: return OcrPage.EMPTY
        val res = try { OcrEngine.recognize(bmp) } finally { bmp.recycle() }
        dao.setOcr(p.id, res.encode())
        return res
    }

    /** Texte de toutes les pages (reconnaissance lancée si nécessaire). */
    suspend fun ensureOcr(docId: Long): List<OcrPage> = ocrMutex.withLock {
        dao.pages(docId).map { p -> OcrPage.decode(p.ocr) ?: runCatching { recognize(p) }.getOrDefault(OcrPage.EMPTY) }
    }

    // ---------------------------------------------------------------- exports

    private fun safeName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().ifBlank { "document" }.take(80)

    /**
     * Crée le PDF du document. [compression] et [password] sont facultatifs ; le nom de fichier reflète l'option
     * pour ne pas écraser l'export standard.
     */
    suspend fun exportPdf(docId: Long, compression: Compression = settings.current.compression, password: String? = null): File = withContext(Dispatchers.IO) {
        val doc = dao.document(docId) ?: error("Document introuvable")
        val ocr = if (settings.current.searchablePdf) ensureOcr(docId) else null
        val pages = dao.pages(docId)
        val suffix = when {
            !password.isNullOrEmpty() -> " (protégé)"
            compression != settings.current.compression -> " (${compression.label.lowercase()})"
            else -> ""
        }
        val file = File(exportDir, safeName(doc.name) + suffix + ".pdf")
        file.outputStream().buffered().use { out ->
            PdfWriter(out).write(pages.mapIndexed { i, p ->
                val bmp = Bitmaps.load(File(p.processedPath), compression.maxDim) ?: error("Page ${i + 1} illisible")
                val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, compression.quality, it) }.toByteArray()
                PdfWriter.PageInput(bytes, bmp.width, bmp.height, ocr?.getOrNull(i)).also { bmp.recycle() }
            }, doc.name, PageSize.of(doc.pageSize), password)
        }
        file
    }

    suspend fun exportDocx(docId: Long): File = withContext(Dispatchers.IO) {
        val doc = dao.document(docId) ?: error("Document introuvable")
        val ocr = ensureOcr(docId)
        File(exportDir, safeName(doc.name) + ".docx").also { f -> f.outputStream().buffered().use { DocxWriter.write(it, doc.name, ocr) } }
    }

    suspend fun exportText(docId: Long): String = ensureOcr(docId).joinToString("\n\n— — —\n\n") { it.text }

    suspend fun exportJpegFiles(docId: Long): List<File> = withContext(Dispatchers.IO) {
        val doc = dao.document(docId) ?: error("Document introuvable")
        val pages = dao.pages(docId)
        pages.mapIndexed { i, p ->
            val suffix = if (pages.size > 1) "_${i + 1}" else ""
            File(p.processedPath).copyTo(File(exportDir, safeName(doc.name) + "$suffix.jpg"), overwrite = true)
        }
    }

    /** Enregistre les pages en JPEG dans la galerie (Images/ScanDoc). */
    suspend fun saveJpegsToGallery(docId: Long): Int = withContext(Dispatchers.IO) {
        val doc = dao.document(docId) ?: return@withContext 0
        val pages = dao.pages(docId)
        val resolver = context.contentResolver
        pages.forEachIndexed { i, p ->
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, safeName(doc.name) + (if (pages.size > 1) "_${i + 1}" else "") + ".jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScanDoc")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Galerie indisponible")
            resolver.openOutputStream(uri)?.use { out -> File(p.processedPath).inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
        pages.size
    }

    /** Copie un fichier exporté dans Téléchargements/ScanDoc. */
    suspend fun saveToDownloads(file: File, mime: String): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/ScanDoc")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Téléchargements indisponible")
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        "Téléchargements/ScanDoc/${file.name}"
    }

    /** Fichier temporaire pour une photo prise par l'appareil photo intégré. */
    fun newCaptureFile(): File = File(File(context.cacheDir, "captures").apply { mkdirs() }, "cap_${System.currentTimeMillis()}.jpg")

    private val signatureDir get() = File(context.filesDir, "signatures").apply { mkdirs() }

    /** Chaque signature a son propre fichier : en créer une nouvelle ne modifie pas les pages déjà signées. */
    fun newSignatureFile(): File = File(signatureDir, "sig_${System.currentTimeMillis()}.png")

    fun latestSignature(): File? = signatureDir.listFiles { f -> f.name.endsWith(".png") }?.maxByOrNull { it.lastModified() }
}
