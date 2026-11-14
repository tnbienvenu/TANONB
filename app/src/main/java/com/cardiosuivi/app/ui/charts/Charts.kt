package com.cardiosuivi.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardiosuivi.app.ui.components.StatusDot
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

data class ChartPoint(val x: Long, val y: Double, val label: String)
data class ChartSeries(val name: String, val color: Color, val points: List<ChartPoint>)
data class RefLine(val y: Double, val label: String, val color: Color)

/**
 * Courbe temporelle : l'axe horizontal est proportionnel au temps réel, chaque point correspond à une donnée
 * réellement enregistrée. Les segments ne relient que des points séparés de moins de [maxGapMillis] ;
 * au-delà, la courbe est interrompue pour ne pas suggérer des mesures inexistantes.
 */
@Composable
fun TimeSeriesChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    refLines: List<RefLine> = emptyList(),
    height: Int = 220,
    maxGapMillis: Long = 8L * 24 * 3600 * 1000,
    unit: String = "",
) {
    val all = series.flatMap { it.points }
    if (all.isEmpty()) {
        Text("Aucune donnée sur la période.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    var selected by remember(series) { mutableStateOf<Pair<ChartSeries, ChartPoint>?>(null) }

    val ys = all.map { it.y } + refLines.map { it.y }
    val yMin = floor((ys.min() - 5) / 10) * 10
    val yMax = ceil((ys.max() + 5) / 10) * 10
    val xMin = all.minOf { it.x }
    val xMax = all.maxOf { it.x }.let { if (it == xMin) xMin + 1 else it }

    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(height.dp)
                .semantics { contentDescription = "Graphique ${series.joinToString { it.name }}, ${all.size} points. Touchez un point pour afficher sa valeur." }
                .pointerInput(series) {
                    detectTapGestures { tap ->
                        val left = 40f; val right = size.width - 8f; val top = 8f; val bottom = size.height - 22f
                        fun px(x: Long) = left + (x - xMin).toFloat() / (xMax - xMin) * (right - left)
                        fun py(y: Double) = bottom - ((y - yMin) / (yMax - yMin)).toFloat() * (bottom - top)
                        selected = series.flatMap { s -> s.points.map { s to it } }
                            .minByOrNull { (_, p) -> abs(px(p.x) - tap.x) + abs(py(p.y) - tap.y) * 0.5f }
                    }
                },
        ) {
            val left = 40f; val right = size.width - 8f; val top = 8f; val bottom = size.height - 22f
            fun px(x: Long) = left + (x - xMin).toFloat() / (xMax - xMin) * (right - left)
            fun py(y: Double) = bottom - ((y - yMin) / (yMax - yMin)).toFloat() * (bottom - top)
            val step = if (yMax - yMin > 100) 20.0 else 10.0
            var g = yMin
            while (g <= yMax) {
                drawLine(gridColor, Offset(left, py(g)), Offset(right, py(g)), strokeWidth = 1f)
                drawText(measurer, g.toInt().toString(), Offset(0f, py(g) - 8f), TextStyle(fontSize = 10.sp, color = labelColor))
                g += step
            }
            refLines.forEach { r ->
                drawLine(r.color, Offset(left, py(r.y)), Offset(right, py(r.y)), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
            series.forEach { s ->
                val pts = s.points.sortedBy { it.x }
                val path = Path()
                pts.forEachIndexed { i, p ->
                    val o = Offset(px(p.x), py(p.y))
                    if (i == 0 || p.x - pts[i - 1].x > maxGapMillis) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(path, s.color, style = Stroke(width = 3f))
                pts.forEach { p -> drawCircle(s.color, radius = 5f, center = Offset(px(p.x), py(p.y))) }
            }
            selected?.let { (s, p) ->
                val c = Offset(px(p.x), py(p.y))
                drawCircle(Color.White, 9f, c)
                drawCircle(s.color, 7f, c)
                drawLine(labelColor, Offset(c.x, top), Offset(c.x, bottom), strokeWidth = 1f)
            }
        }
        val sel = selected
        Text(
            if (sel != null) "${sel.first.name} : ${sel.second.label}" else "Touchez un point pour afficher la valeur exacte.",
            style = MaterialTheme.typography.bodySmall,
        )
        Legend(series.map { it.name to it.color } + refLines.map { it.label to it.color })
    }
}

data class Bar(val value: Double?, val color: Color, val label: String)
data class BarGroup(val label: String, val bars: List<Bar>, val n: Int)

/** Barres groupées (comparaisons matin/soir, semaines, mois) avec effectifs. Les groupes sans donnée sont signalés et non dessinés. */
@Composable
fun GroupedBarChart(groups: List<BarGroup>, legend: List<Pair<String, Color>>, modifier: Modifier = Modifier, height: Int = 200, baseline: Double? = null) {
    if (groups.isEmpty()) {
        Text("Aucune donnée sur la période.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    var selected by remember(groups) { mutableStateOf<Pair<BarGroup, Bar>?>(null) }
    val values = groups.flatMap { g -> g.bars.mapNotNull { it.value } }
    val yMin = baseline ?: (floor(((values.minOrNull() ?: 0.0) - 10) / 10) * 10).coerceAtLeast(0.0)
    val yMax = ceil(((values.maxOrNull() ?: 100.0) + 5) / 10) * 10

    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(height.dp).pointerInput(groups) {
                detectTapGestures { tap ->
                    val left = 40f; val right = size.width - 8f
                    val gw = (right - left) / groups.size
                    val gi = ((tap.x - left) / gw).toInt().coerceIn(0, groups.size - 1)
                    val g = groups[gi]
                    val bw = gw * 0.8f / g.bars.size
                    val bi = ((tap.x - left - gi * gw - gw * 0.1f) / bw).toInt().coerceIn(0, g.bars.size - 1)
                    selected = g to g.bars[bi]
                }
            },
        ) {
            val left = 40f; val right = size.width - 8f; val top = 8f; val bottom = size.height - 30f
            fun py(y: Double) = bottom - ((y - yMin) / (yMax - yMin)).toFloat() * (bottom - top)
            var g = yMin
            while (g <= yMax) {
                drawLine(gridColor, Offset(left, py(g)), Offset(right, py(g)), 1f)
                drawText(measurer, g.toInt().toString(), Offset(0f, py(g) - 8f), TextStyle(fontSize = 10.sp, color = labelColor))
                g += if (yMax - yMin > 100) 20.0 else 10.0
            }
            val gw = (right - left) / groups.size
            groups.forEachIndexed { i, grp ->
                val bw = gw * 0.8f / grp.bars.size
                grp.bars.forEachIndexed { j, b ->
                    val x = left + i * gw + gw * 0.1f + j * bw
                    if (b.value != null) {
                        drawRect(b.color, Offset(x + 2, py(b.value)), androidx.compose.ui.geometry.Size(bw - 4, bottom - py(b.value)))
                    }
                }
                val lbl = if (groups.size > 6) grp.label.takeLast(5) else grp.label
                drawText(measurer, "$lbl (n=${grp.n})", Offset(left + i * gw + 2, bottom + 4), TextStyle(fontSize = 9.sp, color = labelColor))
            }
        }
        val sel = selected
        Text(
            if (sel != null) "${sel.first.label} — ${sel.second.label}" else "Touchez une barre pour afficher la valeur exacte.",
            style = MaterialTheme.typography.bodySmall,
        )
        Legend(legend)
    }
}

@Composable
fun Legend(items: List<Pair<String, Color>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (n, c) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusDot(c)
                Text(n, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
