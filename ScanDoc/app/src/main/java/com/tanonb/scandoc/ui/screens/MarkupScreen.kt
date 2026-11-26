package com.tanonb.scandoc.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.tanonb.scandoc.imaging.Mark
import com.tanonb.scandoc.imaging.MarkTool
import com.tanonb.scandoc.imaging.NPoint
import com.tanonb.scandoc.imaging.Painters
import com.tanonb.scandoc.ui.components.BusyOverlay
import com.tanonb.scandoc.ui.components.toast
import kotlinx.coroutines.launch

private val PEN_COLORS = listOf(Color(0xFF111111), Color(0xFF1E4FD8), Color(0xFFD32F2F), Color(0xFF2E7D32))
private val HIGHLIGHT_COLORS = listOf(Color(0xFFFFEB3B), Color(0xFF8BF07A), Color(0xFFFF9EC7), Color(0xFF7FD8FF))

/**
 * « Nettoyer » : gomme blanche pour effacer taches, ombres, traces de doigts ou marques parasites,
 * plus stylo et surligneur pour annoter. Le résultat est intégré à la page (PDF, JPEG, Word).
 */
@Composable
fun MarkupScreen(nav: NavHostController, pageId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by produceState<Pair<PageEntity, Bitmap>?>(null, pageId) { value = context.app.repository.baseImage(pageId, withMarks = false) }
    val marks = remember { mutableStateListOf<Mark>() }
    var loaded by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf(MarkTool.ERASE) }
    var penColor by remember { mutableStateOf(PEN_COLORS[0]) }
    var hlColor by remember { mutableStateOf(HIGHLIGHT_COLORS[0]) }
    var width by remember { mutableFloatStateOf(0.5f) }
    var current by remember { mutableStateOf<Mark?>(null) }
    var saving by remember { mutableStateOf(false) }

    data?.let { (p, _) -> if (!loaded) { marks.addAll(Mark.decodeList(p.marks)); loaded = true } }

    fun strokeWidth() = when (tool) {
        MarkTool.ERASE -> 0.01f + width * 0.07f
        MarkTool.PEN -> 0.002f + width * 0.008f
        MarkTool.HIGHLIGHT -> 0.01f + width * 0.04f
    }
    fun strokeColor() = when (tool) {
        MarkTool.ERASE -> Color.White
        MarkTool.PEN -> penColor
        MarkTool.HIGHLIGHT -> hlColor
    }.toArgb()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.Close, "Annuler") } },
                title = { Text("Nettoyer et annoter") },
                actions = {
                    IconButton(enabled = marks.isNotEmpty(), onClick = { marks.removeAt(marks.lastIndex) }) { Icon(Icons.AutoMirrored.Outlined.Undo, "Annuler le dernier trait") }
                    IconButton(enabled = marks.isNotEmpty(), onClick = { marks.clear() }) { Icon(Icons.Outlined.DeleteSweep, "Tout effacer") }
                    IconButton(enabled = data != null && !saving, onClick = {
                        saving = true
                        scope.launch {
                            runCatching { context.app.background { updatePage(pageId, marks = marks.toList()) } }.onFailure { context.toast("Échec : ${it.message}") }
                            saving = false
                            nav.popBackStack()
                        }
                    }) { Icon(Icons.Filled.Check, "Valider", tint = MaterialTheme.colorScheme.primary) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Color.Black).padding(12.dp), contentAlignment = Alignment.Center) {
                val bmp = data?.second
                if (bmp != null) {
                    val ratio = bmp.width.toFloat() / bmp.height
                    val boxRatio = constraints.maxWidth.toFloat() / constraints.maxHeight
                    val (w, h) = if (ratio > boxRatio) constraints.maxWidth.toFloat() to constraints.maxWidth / ratio else constraints.maxHeight * ratio to constraints.maxHeight.toFloat()
                    val density = LocalDensity.current
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    Box(Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
                        Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        Canvas(
                            Modifier.fillMaxSize().pointerInput(tool, penColor, hlColor, width) {
                                detectDragGestures(
                                    onDragStart = { o -> current = Mark(tool, strokeColor(), strokeWidth(), listOf(NPoint(o.x / size.width, o.y / size.height))) },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        current = current?.let { it.copy(points = it.points + NPoint(change.position.x / size.width, change.position.y / size.height)) }
                                    },
                                    onDragEnd = { current?.let { marks += it }; current = null },
                                    onDragCancel = { current?.let { marks += it }; current = null },
                                )
                            },
                        ) {
                            drawIntoCanvas { c -> Painters.drawMarks(c.nativeCanvas, size.width, size.height, marks + listOfNotNull(current)) }
                        }
                    }
                }
            }
            Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(tool == MarkTool.ERASE, { tool = MarkTool.ERASE }, label = { Text("Nettoyer") }, leadingIcon = { Icon(Icons.Outlined.AutoFixNormal, null) })
                    FilterChip(tool == MarkTool.PEN, { tool = MarkTool.PEN }, label = { Text("Stylo") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
                    FilterChip(tool == MarkTool.HIGHLIGHT, { tool = MarkTool.HIGHLIGHT }, label = { Text("Surligneur") }, leadingIcon = { Icon(Icons.Outlined.BorderColor, null) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Épaisseur", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(12.dp))
                    Slider(width, { width = it }, modifier = Modifier.weight(1f))
                }
                if (tool != MarkTool.ERASE) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    (if (tool == MarkTool.PEN) PEN_COLORS else HIGHLIGHT_COLORS).forEach { c ->
                        val sel = c == (if (tool == MarkTool.PEN) penColor else hlColor)
                        Box(
                            Modifier.size(34.dp).background(c, CircleShape)
                                .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.LightGray, CircleShape)
                                .clickable { if (tool == MarkTool.PEN) penColor = c else hlColor = c },
                        )
                    }
                } else Text(
                    "Passez le doigt sur les taches, ombres ou annotations à effacer.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (saving) BusyOverlay("Enregistrement…")
}
