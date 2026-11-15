package com.cardiosuivi.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardiosuivi.app.ui.components.StatusDot
import com.cardiosuivi.app.ui.theme.Health
import com.cardiosuivi.app.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

data class ChartPoint(val x: Long, val y: Double, val label: String)
data class ChartSeries(val name: String, val color: Color, val points: List<ChartPoint>)
data class RefLine(val y: Double, val label: String, val color: Color)

private const val DAY_MS = 24L * 3600 * 1000

/**
 * Courbe temporelle : l'axe horizontal est proportionnel au temps réel, chaque point correspond à une donnée
 * réellement enregistrée. Les segments ne relient que des points séparés de moins de [maxGapMillis] ;
 * au-delà, la courbe est interrompue pour ne pas suggérer des mesures inexistantes. Aucune valeur n'est interpolée.
 * Toucher ou glisser sur le graphique affiche la date, l'heure et la valeur exacte du point le plus proche.
 */
@Composable
fun TimeSeriesChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    refLines: List<RefLine> = emptyList(),
    height: Int = 220,
    maxGapMillis: Long = 8L * DAY_MS,
    unit: String = "",
    showLegend: Boolean = true,
    xLabels: Boolean = true,
) {
    val all = series.flatMap { it.points }
    if (all.isEmpty()) {
        ChartEmpty("Aucune mesure sur la période. Les jours sans mesure ne sont jamais estimés.", height)
        return
    }
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val cardColor = Health.colors.card
    var selected by remember(series) { mutableStateOf<Pair<ChartSeries, ChartPoint>?>(null) }

    val ys = all.map { it.y } + refLines.map { it.y }
    val yMin = floor((ys.min() - 5) / 10) * 10
    val yMax = ceil((ys.max() + 5) / 10) * 10
    val xMin = all.minOf { it.x }
    val xMax = all.maxOf { it.x }.let { if (it == xMin) xMin + 1 else it }
    val dayFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM")
    val zone = java.time.ZoneId.systemDefault()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        // Bulle de valeur : date, heure et valeur exacte du point sélectionné.
        val sel = selected
        Box(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp))
                .background(if (sel != null) sel.first.color.copy(alpha = 0.10f) else Color.Transparent)
                .padding(horizontal = Spacing.m, vertical = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                if (sel != null) "${sel.first.name} : ${sel.second.label}" else "Touchez ou glissez sur le graphique pour afficher la valeur exacte.",
                style = if (sel != null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                color = if (sel != null) MaterialTheme.colorScheme.onSurface else labelColor,
            )
        }
        Canvas(
            Modifier.fillMaxWidth().height(height.dp)
                .semantics { contentDescription = "Graphique ${series.joinToString { it.name }}, ${all.size} points. Touchez un point pour afficher sa valeur." }
                .pointerInput(series) {
                    fun pick(o: Offset) {
                        val left = 34.dp.toPx(); val right = size.width - 8.dp.toPx(); val top = 8.dp.toPx(); val bottom = size.height - 22.dp.toPx()
                        fun px(x: Long) = left + (x - xMin).toFloat() / (xMax - xMin) * (right - left)
                        fun py(y: Double) = bottom - ((y - yMin) / (yMax - yMin)).toFloat() * (bottom - top)
                        selected = series.flatMap { s -> s.points.map { s to it } }
                            .minByOrNull { (_, p) -> abs(px(p.x) - o.x) + abs(py(p.y) - o.y) * 0.35f }
                    }
                    detectTapGestures { pick(it) }
                }
                .pointerInput(series) {
                    detectDragGestures(onDrag = { change, _ ->
                        val left = 34.dp.toPx(); val right = size.width - 8.dp.toPx(); val top = 8.dp.toPx(); val bottom = size.height - 22.dp.toPx()
                        fun px(x: Long) = left + (x - xMin).toFloat() / (xMax - xMin) * (right - left)
                        fun py(y: Double) = bottom - ((y - yMin) / (yMax - yMin)).toFloat() * (bottom - top)
                        val o = change.position
                        selected = series.flatMap { s -> s.points.map { s to it } }
                            .minByOrNull { (_, p) -> abs(px(p.x) - o.x) * 1f + abs(py(p.y) - o.y) * 0.15f }
                    })
                },
        ) {
            val left = 34.dp.toPx(); val right = size.width - 8.dp.toPx(); val top = 8.dp.toPx(); val bottom = size.height - 22.dp.toPx()
            fun px(x: Long) = left + (x - xMin).toFloat() / (xMax - xMin) * (right - left)
            fun py(y: Double) = bottom - ((y - yMin) / (yMax - yMin)).toFloat() * (bottom - top)
            val step = when { yMax - yMin > 120 -> 30.0; yMax - yMin > 60 -> 20.0; else -> 10.0 }
            var g = yMin
            val axisStyle = TextStyle(fontSize = 11.sp, color = labelColor)
            while (g <= yMax) {
                drawLine(gridColor, Offset(left, py(g)), Offset(right, py(g)), strokeWidth = 1f)
                drawText(measurer, g.toInt().toString(), Offset(0f, py(g) - 8.dp.toPx()), axisStyle)
                g += step
            }
            if (xLabels) {
                val first = java.time.Instant.ofEpochMilli(xMin).atZone(zone).toLocalDate()
                val last = java.time.Instant.ofEpochMilli(xMax).atZone(zone).toLocalDate()
                drawText(measurer, dayFmt.format(first), Offset(left, bottom + 4.dp.toPx()), axisStyle)
                if (last != first) {
                    val w = measurer.measure(dayFmt.format(last), axisStyle).size.width
                    drawText(measurer, dayFmt.format(last), Offset(right - w, bottom + 4.dp.toPx()), axisStyle)
                }
            }
            refLines.forEach { r ->
                drawLine(r.color.copy(alpha = 0.8f), Offset(left, py(r.y)), Offset(right, py(r.y)), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
            series.forEach { s ->
                val pts = s.points.sortedBy { it.x }
                val path = Path()
                pts.forEachIndexed { i, p ->
                    val o = Offset(px(p.x), py(p.y))
                    if (i == 0 || p.x - pts[i - 1].x > maxGapMillis) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(path, s.color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                pts.forEach { p ->
                    val c = Offset(px(p.x), py(p.y))
                    drawCircle(cardColor, radius = 4.5.dp.toPx(), center = c)
                    drawCircle(s.color, radius = 3.dp.toPx(), center = c)
                }
            }
            selected?.let { (s, p) ->
                val c = Offset(px(p.x), py(p.y))
                drawLine(labelColor.copy(alpha = 0.6f), Offset(c.x, top), Offset(c.x, bottom), strokeWidth = 1.dp.toPx())
                drawCircle(cardColor, 8.dp.toPx(), c)
                drawCircle(s.color, 6.dp.toPx(), c)
            }
        }
        if (showLegend) Legend(series.map { it.name to it.color } + refLines.map { it.label to it.color })
    }
}

@Composable
private fun ChartEmpty(text: String, height: Int) {
    Box(
        Modifier.fillMaxWidth().height((height * 0.6f).dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.l))
    }
}

data class Bar(val value: Double?, val color: Color, val label: String)
data class BarGroup(val label: String, val bars: List<Bar>, val n: Int)

/** Barres groupées (comparaisons matin/soir, semaines, mois) avec effectifs. Les groupes sans donnée sont signalés et non dessinés. */
@Composable
fun GroupedBarChart(groups: List<BarGroup>, legend: List<Pair<String, Color>>, modifier: Modifier = Modifier, height: Int = 200, baseline: Double? = null) {
    if (groups.isEmpty() || groups.all { g -> g.bars.all { it.value == null } }) {
        ChartEmpty("Données insuffisantes sur la période.", height)
        return
    }
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    var selected by remember(groups) { mutableStateOf<Pair<BarGroup, Bar>?>(null) }
    val values = groups.flatMap { g -> g.bars.mapNotNull { it.value } }
    val yMin = baseline ?: (floor(((values.minOrNull() ?: 0.0) - 10) / 10) * 10).coerceAtLeast(0.0)
    val yMax = ceil(((values.maxOrNull() ?: 100.0) + 5) / 10) * 10

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        val sel = selected
        Box(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp))
                .background(if (sel != null) sel.second.color.copy(alpha = 0.10f) else Color.Transparent)
                .padding(horizontal = Spacing.m, vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                if (sel != null) "${sel.first.label} — ${sel.second.label}" else "Touchez une barre pour afficher la valeur exacte.",
                style = if (sel != null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                color = if (sel != null) MaterialTheme.colorScheme.onSurface else labelColor,
            )
        }
        Canvas(
            Modifier.fillMaxWidth().height(height.dp)
                .semantics { contentDescription = "Diagramme en barres, ${groups.size} groupe(s). Touchez une barre pour afficher sa valeur." }
                .pointerInput(groups) {
                    detectTapGestures { tap ->
                        val left = 34.dp.toPx(); val right = size.width - 8.dp.toPx()
                        val gw = (right - left) / groups.size
                        val gi = ((tap.x - left) / gw).toInt().coerceIn(0, groups.size - 1)
                        val g = groups[gi]
                        val bw = gw * 0.8f / g.bars.size
                        val bi = ((tap.x - left - gi * gw - gw * 0.1f) / bw).toInt().coerceIn(0, g.bars.size - 1)
                        selected = g to g.bars[bi]
                    }
                },
        ) {
            val left = 34.dp.toPx(); val right = size.width - 8.dp.toPx(); val top = 8.dp.toPx(); val bottom = size.height - 30.dp.toPx()
            fun py(y: Double) = bottom - ((y - yMin) / (yMax - yMin)).toFloat() * (bottom - top)
            val axisStyle = TextStyle(fontSize = 11.sp, color = labelColor)
            var g = yMin
            while (g <= yMax) {
                drawLine(gridColor, Offset(left, py(g)), Offset(right, py(g)), 1f)
                drawText(measurer, g.toInt().toString(), Offset(0f, py(g) - 8.dp.toPx()), axisStyle)
                g += if (yMax - yMin > 100) 20.0 else 10.0
            }
            val gw = (right - left) / groups.size
            groups.forEachIndexed { i, grp ->
                val bw = gw * 0.8f / grp.bars.size
                grp.bars.forEachIndexed { j, b ->
                    val x = left + i * gw + gw * 0.1f + j * bw
                    if (b.value != null) {
                        val isSel = sel?.first == grp && sel.second == b
                        drawRoundRect(
                            if (sel == null || isSel) b.color else b.color.copy(alpha = 0.45f),
                            Offset(x + 2, py(b.value)), androidx.compose.ui.geometry.Size(bw - 4, bottom - py(b.value)),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                    }
                }
                val lbl = if (groups.size > 6) grp.label.takeLast(5) else grp.label
                drawText(measurer, "$lbl (n=${grp.n})", Offset(left + i * gw + 2, bottom + 6.dp.toPx()), TextStyle(fontSize = 10.sp, color = labelColor))
            }
        }
        Legend(legend)
    }
}

@Composable
fun Legend(items: List<Pair<String, Color>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items.forEach { (n, c) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusDot(c, 8)
                Text(n, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
