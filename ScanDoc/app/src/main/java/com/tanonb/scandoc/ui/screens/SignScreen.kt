package com.tanonb.scandoc.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tanonb.scandoc.app
import com.tanonb.scandoc.data.PageEntity
import com.tanonb.scandoc.imaging.Painters
import com.tanonb.scandoc.imaging.Stamp
import com.tanonb.scandoc.ui.components.BusyOverlay
import com.tanonb.scandoc.ui.components.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * « Remplir et signer » : signature manuscrite, texte libre, date, coches ✓ et ✗.
 * Touchez un élément pour le sélectionner, faites-le glisser, pincez pour l'agrandir.
 */
@Composable
fun SignScreen(nav: NavHostController, docId: Long, startPage: Int) {
    val context = LocalContext.current
    val repo = context.app.repository
    val scope = rememberCoroutineScope()
    var pages by remember { mutableStateOf<List<PageEntity>?>(null) }
    androidx.compose.runtime.LaunchedEffect(docId) { pages = repo.pages(docId) }
    var index by remember { mutableIntStateOf(startPage) }
    val page = pages?.getOrNull(index.coerceIn(0, ((pages?.size ?: 1) - 1).coerceAtLeast(0)))
    val base by produceState<Bitmap?>(null, page?.id) { value = page?.let { repo.baseImage(it.id, withMarks = true)?.second } }
    val stamps = remember { mutableStateListOf<Stamp>() }
    var loadedFor by remember { mutableStateOf(-1L) }
    var selected by remember { mutableIntStateOf(-1) }
    var drawSignature by remember { mutableStateOf(false) }
    var addText by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }

    if (page != null && loadedFor != page.id) {
        stamps.clear(); stamps.addAll(Stamp.decodeList(page.stamps)); loadedFor = page.id; selected = -1; dirty = false
    }

    fun add(s: Stamp) { stamps += s; selected = stamps.lastIndex; dirty = true }
    suspend fun save() {
        val p = page ?: return
        if (!dirty) return
        context.app.background { updatePage(p.id, stamps = stamps.toList()) }
        dirty = false
    }
    fun changePage(delta: Int) {
        scope.launch {
            saving = true; runCatching { save() }; saving = false
            pages = repo.pages(docId)
            index = (index + delta).coerceIn(0, (pages?.size ?: 1) - 1)
            loadedFor = -1
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.Close, "Annuler") } },
                title = { Text("Remplir et signer") },
                actions = {
                    if (selected in stamps.indices) IconButton(onClick = { stamps.removeAt(selected); selected = -1; dirty = true }) { Icon(Icons.Outlined.Delete, "Supprimer l'élément") }
                    IconButton(enabled = page != null && !saving, onClick = {
                        scope.launch {
                            saving = true
                            runCatching { save() }.onFailure { context.toast("Échec : ${it.message}") }
                            saving = false
                            nav.popBackStack()
                        }
                    }) { Icon(Icons.Filled.Check, "Terminer", tint = MaterialTheme.colorScheme.primary) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Color.Black).padding(12.dp), contentAlignment = Alignment.Center) {
                val bmp = base
                if (bmp != null) {
                    val ratio = bmp.width.toFloat() / bmp.height
                    val boxRatio = constraints.maxWidth.toFloat() / constraints.maxHeight
                    val (w, h) = if (ratio > boxRatio) constraints.maxWidth.toFloat() to constraints.maxWidth / ratio else constraints.maxHeight * ratio to constraints.maxHeight.toFloat()
                    val density = LocalDensity.current
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    Box(Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
                        Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        Canvas(
                            Modifier.fillMaxSize().pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val sw = size.width.toFloat(); val sh = size.height.toFloat()
                                    val pad = 16.dp.toPx()
                                    selected = stamps.indices.lastOrNull { i ->
                                        val r = Painters.bounds(stamps[i], sw, sh); r.inset(-pad, -pad); r.contains(down.position.x, down.position.y)
                                    } ?: -1
                                    if (selected < 0) return@awaitEachGesture
                                    do {
                                        val event = awaitPointerEvent()
                                        val pan = event.calculatePan(); val zoom = event.calculateZoom()
                                        val i = selected
                                        if (i in stamps.indices && (pan != Offset.Zero || zoom != 1f)) {
                                            val s = stamps[i]
                                            stamps[i] = s.copy(
                                                cx = (s.cx + pan.x / sw).coerceIn(0f, 1f), cy = (s.cy + pan.y / sh).coerceIn(0f, 1f),
                                                size = (s.size * zoom).coerceIn(0.01f, 0.9f),
                                            )
                                            dirty = true
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            },
                        ) {
                            drawIntoCanvas { c -> Painters.drawStamps(c.nativeCanvas, size.width, size.height, stamps) }
                            if (selected in stamps.indices) {
                                val r = Painters.bounds(stamps[selected], size.width, size.height)
                                val pad = 6.dp.toPx()
                                drawRect(
                                    Color(0xFF3B63F5), topLeft = Offset(r.left - pad, r.top - pad),
                                    size = androidx.compose.ui.geometry.Size(r.width() + 2 * pad, r.height() + 2 * pad),
                                    style = Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                                )
                            }
                        }
                    }
                }
            }
            Column(Modifier.navigationBarsPadding().padding(vertical = 6.dp)) {
                if ((pages?.size ?: 0) > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = index > 0, onClick = { changePage(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Page précédente") }
                    Text("Page ${index + 1} sur ${pages?.size}")
                    IconButton(enabled = index < (pages?.size ?: 1) - 1, onClick = { changePage(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Page suivante") }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { drawSignature = true }, label = { Text("Signature") }, leadingIcon = { Icon(Icons.Outlined.Draw, null) })
                    AssistChip(onClick = { addText = true }, label = { Text("Texte") }, leadingIcon = { Icon(Icons.Outlined.TextFields, null) })
                    AssistChip(onClick = { add(Stamp(0.5f, 0.5f, 0.035f, text = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date()))) },
                        label = { Text("Date") }, leadingIcon = { Icon(Icons.Outlined.CalendarToday, null) })
                    AssistChip(onClick = { add(Stamp(0.5f, 0.5f, 0.05f, text = "✓", color = 0xFF1E4FD8.toInt())) }, label = { Text("✓  Coche") })
                    AssistChip(onClick = { add(Stamp(0.5f, 0.5f, 0.05f, text = "✗", color = 0xFFD32F2F.toInt())) }, label = { Text("✗  Croix") })
                }
            }
        }
    }

    if (drawSignature) SignatureDialog(
        existing = repo.latestSignature()?.absolutePath,
        onDismiss = { drawSignature = false },
        onUse = { path -> drawSignature = false; add(Stamp(0.7f, 0.85f, 0.35f, path = path)) },
        onNew = { bmp ->
            drawSignature = false
            scope.launch {
                val f = repo.newSignatureFile()
                withContext(Dispatchers.IO) { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                add(Stamp(0.7f, 0.85f, 0.35f, path = f.absolutePath))
            }
        },
    )
    if (addText) TextStampDialog(onDismiss = { addText = false }) { text, color ->
        addText = false
        if (text.isNotBlank()) add(Stamp(0.5f, 0.5f, 0.035f, text = text, color = color))
    }
    if (saving) BusyOverlay("Enregistrement…")
}

@Composable
private fun TextStampDialog(onDismiss: () -> Unit, onDone: (String, Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(Color(0xFF111111)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter du texte") },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, label = { Text("Texte (nom, adresse…)") })
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(Color(0xFF111111), Color(0xFF1E4FD8), Color(0xFFD32F2F)).forEach { c ->
                        Box(Modifier.size(32.dp).background(c, RoundedCornerShape(50)).border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                            .clickable { color = c })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(text, color.toArgb()) }) { Text("Ajouter") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Signature : réutiliser la dernière ou en dessiner une nouvelle au doigt. */
@Composable
private fun SignatureDialog(existing: String?, onDismiss: () -> Unit, onUse: (String) -> Unit, onNew: (Bitmap) -> Unit) {
    var drawing by remember { mutableStateOf(existing == null) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (drawing) "Dessinez votre signature" else "Votre signature") },
        text = {
            if (!drawing && existing != null) {
                val bmp = remember(existing) { Painters.signature(existing) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(120.dp).background(Color.White)) }
                    TextButton(onClick = { drawing = true }) { Text("Créer une nouvelle signature") }
                }
            } else {
                Column {
                    Canvas(
                        Modifier.fillMaxWidth().height(200.dp).background(Color.White).border(1.dp, Color.LightGray)
                            .pointerInput(Unit) {
                                canvasSize = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
                                detectDragGestures(
                                    onDragStart = { current = listOf(it) },
                                    onDrag = { ch, _ -> ch.consume(); current = current + ch.position },
                                    onDragEnd = { strokes += current; current = emptyList() },
                                )
                            },
                    ) {
                        drawLine(Color.LightGray, Offset(16f, size.height * 0.75f), Offset(size.width - 16f, size.height * 0.75f), 2f)
                        (strokes + listOf(current)).filter { it.isNotEmpty() }.forEach { s ->
                            val path = Path().apply { moveTo(s[0].x, s[0].y); s.drop(1).forEach { lineTo(it.x, it.y) } }
                            drawPath(path, Color(0xFF0D1B4C), style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                        }
                    }
                    TextButton(onClick = { strokes.clear() }) { Text("Effacer") }
                }
            }
        },
        confirmButton = {
            if (!drawing && existing != null) TextButton(onClick = { onUse(existing) }) { Text("Utiliser") }
            else TextButton(enabled = strokes.isNotEmpty(), onClick = { onNew(renderSignature(strokes, canvasSize)) }) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Signature rognée au plus juste, fond transparent. */
private fun renderSignature(strokes: List<List<Offset>>, size: androidx.compose.ui.geometry.Size): Bitmap {
    val all = strokes.flatten()
    val pad = 12f
    val minX = (all.minOf { it.x } - pad).coerceAtLeast(0f); val minY = (all.minOf { it.y } - pad).coerceAtLeast(0f)
    val maxX = (all.maxOf { it.x } + pad).coerceAtMost(size.width.coerceAtLeast(1f)); val maxY = (all.maxOf { it.y } + pad).coerceAtMost(size.height.coerceAtLeast(1f))
    val scale = 2f
    val bmp = Bitmap.createBitmap(((maxX - minX) * scale).toInt().coerceAtLeast(1), ((maxY - minY) * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0D1B4C.toInt(); style = Paint.Style.STROKE; strokeWidth = 7f * scale / 2; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    strokes.filter { it.isNotEmpty() }.forEach { s ->
        val p = android.graphics.Path()
        p.moveTo((s[0].x - minX) * scale, (s[0].y - minY) * scale)
        s.drop(1).forEach { p.lineTo((it.x - minX) * scale, (it.y - minY) * scale) }
        if (s.size == 1) p.lineTo((s[0].x - minX) * scale + 1, (s[0].y - minY) * scale)
        c.drawPath(p, paint)
    }
    return bmp
}
