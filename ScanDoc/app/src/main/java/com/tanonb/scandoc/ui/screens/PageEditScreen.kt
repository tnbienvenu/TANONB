package com.tanonb.scandoc.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tanonb.scandoc.app
import com.tanonb.scandoc.data.PageEntity
import com.tanonb.scandoc.imaging.Bitmaps
import com.tanonb.scandoc.imaging.NPoint
import com.tanonb.scandoc.imaging.PageFilter
import com.tanonb.scandoc.imaging.Quad
import com.tanonb.scandoc.ui.components.BusyOverlay
import com.tanonb.scandoc.ui.components.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Recadrage (4 coins avec loupe), rotation et filtre d'une page. */
@Composable
fun PageEditScreen(nav: NavHostController, pageId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by produceState<Pair<PageEntity, Bitmap>?>(null, pageId) {
        value = withContext(Dispatchers.IO) {
            val p = context.app.repository.page(pageId) ?: return@withContext null
            Bitmaps.load(File(p.originalPath), 1600)?.let { p to it }
        }
    }
    var quad by remember { mutableStateOf<Quad?>(null) }
    var rotation by remember { mutableIntStateOf(-1) }
    var filter by remember { mutableStateOf<PageFilter?>(null) }
    var saving by remember { mutableStateOf(false) }

    val loaded = data
    if (loaded != null && quad == null) {
        quad = Quad.decode(loaded.first.quad); rotation = loaded.first.rotation; filter = PageFilter.of(loaded.first.filter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.Close, "Annuler") } },
                title = { Text("Ajuster la page") },
                actions = {
                    IconButton(enabled = loaded != null && !saving, onClick = {
                        val q = quad ?: return@IconButton
                        saving = true
                        scope.launch {
                            runCatching { context.app.background { updatePage(pageId, q, rotation, filter ?: PageFilter.AUTO) } }
                                .onFailure { context.toast("Échec : ${it.message}") }
                            saving = false
                            nav.popBackStack()
                        }
                    }) { Icon(Icons.Filled.Check, "Valider", tint = MaterialTheme.colorScheme.primary) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF202124)).padding(20.dp), contentAlignment = Alignment.Center) {
                if (loaded != null && quad != null) CropEditor(loaded.second, quad!!) { quad = it }
            }
            Column(Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        val b = loaded?.second ?: return@TextButton
                        scope.launch {
                            val q = withContext(Dispatchers.Default) { Bitmaps.detect(b) }
                            if (q == null) context.toast("Aucun bord détecté : ajustez les coins à la main.") else quad = q
                        }
                    }) { Icon(Icons.Outlined.AutoFixHigh, null); Text(" Détection auto") }
                    TextButton(onClick = { quad = if (quad?.isFull == true) Quad.INSET else Quad.FULL }) {
                        Icon(Icons.Outlined.CropFree, null); Text(if (quad?.isFull == true) " Définir une zone" else " Page entière")
                    }
                    IconButton(onClick = { rotation = (rotation + 270) % 360 }) { Icon(Icons.AutoMirrored.Outlined.RotateLeft, "Pivoter à gauche") }
                    IconButton(onClick = { rotation = (rotation + 90) % 360 }) { Icon(Icons.AutoMirrored.Outlined.RotateRight, "Pivoter à droite") }
                    if (rotation > 0) Text("$rotation°", style = MaterialTheme.typography.labelLarge)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageFilter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
                }
            }
        }
    }
    if (saving) BusyOverlay("Traitement de la page…")
}

@Composable
private fun CropEditor(bitmap: Bitmap, quad: Quad, onChange: (Quad) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val current by rememberUpdatedState(quad)
    var dragging by remember { mutableIntStateOf(-1) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val ratio = bitmap.width.toFloat() / bitmap.height
        val boxRatio = constraints.maxWidth.toFloat() / constraints.maxHeight
        val (w, h) = if (ratio > boxRatio) constraints.maxWidth.toFloat() to constraints.maxWidth / ratio else constraints.maxHeight * ratio to constraints.maxHeight.toFloat()
        Box(Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    val grab = 48.dp.toPx()
                    detectDragGestures(
                        onDragStart = { o ->
                            val pts = current.points
                            val i = pts.indices.minBy { hypot(pts[it].x * size.width - o.x, pts[it].y * size.height - o.y) }
                            dragging = if (hypot(pts[i].x * size.width - o.x, pts[i].y * size.height - o.y) < grab) i else -1
                            touch = o
                        },
                        onDragEnd = { dragging = -1 },
                        onDragCancel = { dragging = -1 },
                        onDrag = { change, delta ->
                            if (dragging >= 0) {
                                change.consume()
                                val p = current.points[dragging]
                                val np = NPoint((p.x + delta.x / size.width).coerceIn(0f, 1f), (p.y + delta.y / size.height).coerceIn(0f, 1f))
                                touch = Offset(np.x * size.width, np.y * size.height)
                                onChange(current.with(dragging, np))
                            }
                        },
                    )
                },
            ) {
                val pts = quad.points.map { Offset(it.x * size.width, it.y * size.height) }
                val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                // Assombrit l'extérieur de la zone retenue.
                val outside = Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)); addPath(path); fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd }
                drawPath(outside, Color.Black.copy(alpha = 0.45f))
                drawPath(path, Color(0xFF4D84FF), style = Stroke(width = 3.dp.toPx()))
                pts.forEach { drawCircle(Color.White, 12.dp.toPx(), it); drawCircle(Color(0xFF1E5EFF), 8.dp.toPx(), it) }
                if (dragging >= 0) drawLoupe(image, touch, size.width, size.height, path)
            }
        }
    }
}

/** Loupe ×2,5 affichée au-dessus du doigt pour placer les coins précisément. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLoupe(image: ImageBitmap, at: Offset, w: Float, h: Float, path: Path) {
    val r = 56.dp.toPx()
    val zoom = 2.5f
    val center = Offset(at.x.coerceIn(r, w - r), if (at.y > 2.6f * r) at.y - 1.6f * r else at.y + 1.6f * r)
    val sx = image.width / w; val sy = image.height / h
    val srcHalfW = r / zoom * sx; val srcHalfH = r / zoom * sy
    val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
    clipPath(circle) {
        drawRect(Color.DarkGray, topLeft = Offset(center.x - r, center.y - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r))
        drawImage(
            image,
            srcOffset = IntOffset(
                (at.x * sx - srcHalfW).roundToInt().coerceIn(0, (image.width - (2 * srcHalfW).roundToInt()).coerceAtLeast(0)),
                (at.y * sy - srcHalfH).roundToInt().coerceIn(0, (image.height - (2 * srcHalfH).roundToInt()).coerceAtLeast(0)),
            ),
            srcSize = IntSize((2 * srcHalfW).roundToInt(), (2 * srcHalfH).roundToInt()),
            dstOffset = IntOffset((center.x - r).roundToInt(), (center.y - r).roundToInt()),
            dstSize = IntSize((2 * r).roundToInt(), (2 * r).roundToInt()),
        )
        drawLine(Color(0xFF4D84FF), Offset(center.x - 10, center.y), Offset(center.x + 10, center.y), 2f)
        drawLine(Color(0xFF4D84FF), Offset(center.x, center.y - 10), Offset(center.x, center.y + 10), 2f)
    }
    drawCircle(Color.White, r, center, style = Stroke(3.dp.toPx()))
}
