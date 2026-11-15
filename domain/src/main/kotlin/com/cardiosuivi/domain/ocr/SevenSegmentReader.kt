package com.cardiosuivi.domain.ocr

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Image en niveaux de gris (0 = noir, 255 = blanc), indépendante d'Android. */
class GrayImage(val width: Int, val height: Int, val pixels: IntArray) {
    init { require(pixels.size == width * height) }
    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]

    /** Réduction par moyenne de blocs, pour que la plus grande dimension ne dépasse pas [maxDim]. */
    fun downscale(maxDim: Int): GrayImage {
        val m = max(width, height)
        if (m <= maxDim) return this
        val f = m.toDouble() / maxDim
        val nw = (width / f).roundToInt().coerceAtLeast(1)
        val nh = (height / f).roundToInt().coerceAtLeast(1)
        val out = IntArray(nw * nh)
        for (y in 0 until nh) {
            val y0 = (y * f).toInt(); val y1 = min(height, ((y + 1) * f).toInt().coerceAtLeast(y0 + 1))
            for (x in 0 until nw) {
                val x0 = (x * f).toInt(); val x1 = min(width, ((x + 1) * f).toInt().coerceAtLeast(x0 + 1))
                var s = 0; var n = 0
                for (yy in y0 until y1) for (xx in x0 until x1) { s += pixels[yy * width + xx]; n++ }
                out[y * nw + x] = s / n
            }
        }
        return GrayImage(nw, nh, out)
    }
}

/**
 * Lecteur dédié aux afficheurs LCD à 7 segments (écrans de tensiomètres), mal reconnus par les
 * moteurs OCR généralistes.
 *
 * 1. Binarisation par seuil local (moyenne sur une fenêtre, méthode de Bradley) : résiste aux dégradés et reflets.
 * 2. Suppression des grands traits (cadre de l'écran, bords de l'appareil).
 * 3. Regroupement des segments d'un même chiffre par dilatation (plusieurs rayons essayés).
 * 4. Décodage de chaque chiffre par présence/absence des 7 segments ; un motif inconnu est rejeté,
 *    un motif à un segment près est accepté mais marqué incertain.
 * 5. Reconstitution des nombres (chiffres alignés et rapprochés), restitués sous forme d'[OcrElement]
 *    pour l'analyse de disposition et de plausibilité d'[OcrParser].
 *
 * Aucune valeur n'est inventée : un chiffre indécodable fait échouer le nombre entier.
 */
object SevenSegmentReader {

    /** Journal de diagnostic (tests uniquement). */
    internal var debug: ((String) -> Unit)? = null

    private const val WORK_DIM = 640

    /** Motifs des segments dans l'ordre a, b, c, d, e, f, g. */
    private val PATTERNS: Map<Int, List<String>> = mapOf(
        0 to listOf("1111110"),
        1 to listOf("0110000"),
        2 to listOf("1101101"),
        3 to listOf("1111001"),
        4 to listOf("0110011"),
        5 to listOf("1011011"),
        6 to listOf("1011111", "0011111"),
        7 to listOf("1110000", "1110010"),
        8 to listOf("1111111"),
        9 to listOf("1111011", "1110011"),
    )
    private val CODE_TO_DIGIT: Map<Int, Int> = PATTERNS.flatMap { (d, ps) -> ps.map { it.toInt(2) to d } }.toMap()

    internal data class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val w get() = x1 - x0 + 1
        val h get() = y1 - y0 + 1
        val cx get() = (x0 + x1) / 2f
        val cy get() = (y0 + y1) / 2f
        fun vOverlap(o: Box): Float {
            val inter = min(y1, o.y1) - max(y0, o.y0) + 1
            return if (inter <= 0) 0f else inter.toFloat() / min(h, o.h)
        }
    }

    internal data class Digit(val box: Box, val value: Int, val uncertain: Boolean)

    data class Result(val elements: List<OcrElement>, val reading: OcrReading)

    /**
     * Lit l'image et retourne la meilleure lecture trouvée (ou une lecture vide).
     * Deuxième passage automatique : si des nombres sont trouvés, l'image est recadrée autour de l'écran
     * (zone des nombres élargie) et relue à meilleure résolution, ce qui récupère souvent le pouls (petits chiffres).
     */
    fun read(image: GrayImage): Result {
        val first = readOnce(image)
        if (first.elements.isEmpty()) return first
        val x0 = first.elements.minOf { it.left }; val x1 = first.elements.maxOf { it.right }
        val y0 = first.elements.minOf { it.top }; val y1 = first.elements.maxOf { it.bottom }
        val bw = x1 - x0; val bh = y1 - y0
        val cx0 = max(0, (x0 - 0.6f * bw).toInt()); val cx1 = min(image.width - 1, (x1 + 0.6f * bw).toInt())
        val cy0 = max(0, (y0 - 0.3f * bh).toInt()); val cy1 = min(image.height - 1, (y1 + 0.9f * bh).toInt())
        val cw = cx1 - cx0 + 1; val ch = cy1 - cy0 + 1
        if (cw < 20 || ch < 20 || (cw > 0.9 * image.width && ch > 0.9 * image.height)) return first
        val crop = IntArray(cw * ch) { i -> image[cx0 + i % cw, cy0 + i / cw] }
        val second = readOnce(GrayImage(cw, ch, crop))
        if (quality(second.reading) <= quality(first.reading)) return first
        val moved = second.elements.map { it.copy(left = it.left + cx0, right = it.right + cx0, top = it.top + cy0, bottom = it.bottom + cy0) }
        return Result(moved, OcrParser.parse(moved))
    }

    private fun readOnce(image: GrayImage): Result {
        val img = image.downscale(WORK_DIM)
        val scale = image.width.toFloat() / img.width
        // Chaque rangée de chiffres (grands SYS/DIA, petit pouls) peut exiger un réglage différent :
        // tous les réglages sont essayés et les nombres lus sont regroupés par emplacement puis départagés par vote.
        val pool = mutableListOf<OcrElement>()
        for (k in doubleArrayOf(0.10, 0.16, 0.22)) {
            val mask = adaptiveThreshold(img, k)
            removeLargeStrokes(mask, img.width, img.height)
            for (r in intArrayOf(1, 2, 3, 4, 6)) {
                pool += numbers(mask, img.width, img.height, r).map { e ->
                    e.copy(left = e.left * scale, top = e.top * scale, right = e.right * scale, bottom = e.bottom * scale)
                }
            }
        }
        val elements = vote(pool)
        debug?.invoke("pool ${pool.groupBy { it.text }.mapValues { it.value.size }} -> ${elements.map { it.text }}")
        return Result(elements, OcrParser.parse(elements))
    }

    /** Regroupe les lectures d'un même emplacement et retient la plus fiable. */
    internal fun vote(pool: List<OcrElement>): List<OcrElement> {
        val clusters = mutableListOf<MutableList<OcrElement>>()
        for (e in pool.sortedByDescending { it.height }) {
            val c = clusters.firstOrNull { cl -> cl.any { overlaps(it, e) } }
            if (c != null) c += e else clusters += mutableListOf(e)
        }
        val chosen = clusters.mapNotNull { cl ->
            val byText = cl.groupBy { it.text }
            // Les fragments perdent des chiffres mais n'en ajoutent pas : à emplacement égal, le nombre le plus long
            // qui contient les autres est préféré ; sinon, le plus fréquent.
            val best = byText.entries.maxWith(
                compareBy<Map.Entry<String, List<OcrElement>>> { e -> e.value.any { !it.uncertain } }
                    .thenBy { e -> byText.keys.count { other -> other != e.key && e.key.contains(other) } }
                    .thenBy { it.value.size }
                    .thenBy { it.key.length },
            )
            val rival = byText.keys.any { it != best.key && it.length == best.key.length }
            val rep = best.value.maxBy { it.height }
            rep.copy(uncertain = rep.uncertain || best.value.all { it.uncertain } || rival, support = if (rival) 1 else best.value.count { !it.uncertain })
        }
        // Un « nombre » qui englobe plusieurs autres nombres est un artefact (traits de rangées différentes fusionnés).
        return chosen.filter { e ->
            chosen.count { o -> o !== e && o.centerX in e.left..e.right && o.centerY in e.top..e.bottom } < 2
        }
    }

    private fun overlaps(a: OcrElement, b: OcrElement): Boolean {
        val iw = min(a.right, b.right) - max(a.left, b.left)
        val ih = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (iw <= 0 || ih <= 0) return false
        return iw * ih >= 0.4f * max(a.width * a.height, b.width * b.height)
    }

    /** Score de qualité d'une lecture : champs lus, cohérence SYS > DIA, plausibilité. */
    fun quality(r: OcrReading): Double {
        var s = 0.0
        val sys = r.systolic.value
        val dia = r.diastolic.value
        if (sys != null) s += 2
        if (dia != null) s += 2
        if (r.pulse.value != null) s += 2
        if (sys != null && dia != null && sys > dia) s += 2
        if (sys != null && dia != null && sys - dia in 10..150) s += 1
        s += r.fields.count { it.value != null && !it.uncertain } * 0.3
        return s
    }

    // ---------------------------------------------------------------------------------------------

    /** Masque « sombre » par seuil local de Bradley (moyenne sur une fenêtre via image intégrale). */
    internal fun adaptiveThreshold(img: GrayImage, k: Double): BooleanArray {
        val w = img.width; val h = img.height
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += img.pixels[y * w + x]
                integral[(y + 1) * (w + 1) + (x + 1)] = integral[y * (w + 1) + (x + 1)] + row
            }
        }
        val half = max(7, min(w, h) / 10)
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            val ya = max(0, y - half); val yb = min(h - 1, y + half)
            for (x in 0 until w) {
                val xa = max(0, x - half); val xb = min(w - 1, x + half)
                val count = (xb - xa + 1) * (yb - ya + 1)
                val sum = integral[(yb + 1) * (w + 1) + (xb + 1)] - integral[ya * (w + 1) + (xb + 1)] -
                    integral[(yb + 1) * (w + 1) + xa] + integral[ya * (w + 1) + xa]
                val mean = sum.toDouble() / count
                out[y * w + x] = img.pixels[y * w + x] < mean * (1 - k)
            }
        }
        return out
    }

    /** Composantes connexes (8-voisinage) d'un masque. */
    internal fun components(mask: BooleanArray, w: Int, h: Int): List<Pair<Box, IntArray>> {
        val label = IntArray(w * h)
        val out = mutableListOf<Pair<Box, IntArray>>()
        val stack = IntArray(w * h)
        var next = 1
        for (start in mask.indices) {
            if (!mask[start] || label[start] != 0) continue
            var sp = 0
            stack[sp++] = start
            label[start] = next
            val pix = ArrayList<Int>()
            var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
            while (sp > 0) {
                val p = stack[--sp]
                pix += p
                val px = p % w; val py = p / w
                if (px < x0) x0 = px; if (px > x1) x1 = px
                if (py < y0) y0 = py; if (py > y1) y1 = py
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = px + dx; val ny = py + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val q = ny * w + nx
                    if (mask[q] && label[q] == 0) { label[q] = next; stack[sp++] = q }
                }
            }
            out += Box(x0, y0, x1, y1) to pix.toIntArray()
            next++
        }
        return out
    }

    /** Efface les traits trop grands pour être un segment (cadre de l'écran, bords, ombres). */
    private fun removeLargeStrokes(mask: BooleanArray, w: Int, h: Int) {
        val maxDim = max(w, h)
        for ((b, pix) in components(mask, w, h)) {
            val big = max(b.w, b.h) > 0.45 * maxDim
            val touchesBorder = b.x0 == 0 || b.y0 == 0 || b.x1 == w - 1 || b.y1 == h - 1
            val dense = pix.size > 0.6 * b.w * b.h && b.w > 0.15 * w && b.h > 0.15 * h // grande zone sombre pleine
            if (big || (touchesBorder && max(b.w, b.h) > 0.12 * maxDim) || dense) pix.forEach { mask[it] = false }
        }
    }

    private fun dilate(mask: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        // Dilatation séparable (horizontale puis verticale) par un carré de rayon r.
        val tmp = BooleanArray(w * h)
        for (y in 0 until h) {
            var last = -10 * r - 10
            for (x in 0 until w) {
                if (mask[y * w + x]) last = x
                if (x - last <= r) tmp[y * w + x] = true
            }
            last = Int.MAX_VALUE / 2
            for (x in w - 1 downTo 0) {
                if (mask[y * w + x]) last = x
                if (last - x <= r) tmp[y * w + x] = true
            }
        }
        val out = BooleanArray(w * h)
        for (x in 0 until w) {
            var last = -10 * r - 10
            for (y in 0 until h) {
                if (tmp[y * w + x]) last = y
                if (y - last <= r) out[y * w + x] = true
            }
            last = Int.MAX_VALUE / 2
            for (y in h - 1 downTo 0) {
                if (tmp[y * w + x]) last = y
                if (last - y <= r) out[y * w + x] = true
            }
        }
        return out
    }

    /** Candidats chiffres (rayon de dilatation r) → nombres. */
    private fun numbers(mask: BooleanArray, w: Int, h: Int, r: Int): List<OcrElement> {
        val minH = max(10, h / 40)
        val cells = components(dilate(mask, w, h, r), w, h).mapNotNull { (b, _) ->
            // Boîte serrée sur les pixels sombres d'origine.
            var x0 = w; var y0 = h; var x1 = -1; var y1 = -1; var n = 0
            for (y in b.y0..b.y1) for (x in b.x0..b.x1) if (mask[y * w + x]) {
                n++
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
            }
            if (n == 0) return@mapNotNull null
            val t = Box(x0, y0, x1, y1)
            if (t.h < minH || t.h > 0.9 * h) return@mapNotNull null
            val aspect = t.w.toFloat() / t.h
            if (aspect > 1.0f) return@mapNotNull null // un chiffre est plus haut que large
            // Un chiffre complet mesure au moins ~6 épaisseurs de trait ; un segment isolé (fragment) ~3 à 5.
            val stroke = strokeWidth(mask, w, t) ?: return@mapNotNull null
            if (t.h < MIN_HEIGHT_IN_STROKES * stroke) return@mapNotNull null
            t
        }
        if (cells.isEmpty()) return emptyList()

        // Rangées de cellules de hauteur comparable, puis nombres = cellules proches.
        val sorted = cells.sortedBy { it.x0 }
        val used = BooleanArray(sorted.size)
        val out = mutableListOf<OcrElement>()
        for (i in sorted.indices) {
            if (used[i]) continue
            val group = mutableListOf(sorted[i]); used[i] = true
            var last = sorted[i]
            for (j in i + 1 until sorted.size) {
                if (used[j]) continue
                val c = sorted[j]
                val refH = maxOf(group.maxOf { it.h }, c.h)
                // Un « 1 » (case étroite) est plus court (segments b et c seuls) et aligné à droite de sa case.
                val narrow = c.w < 0.3f * refH || last.w < 0.3f * refH
                val minRatio = if (narrow) 0.6f else 0.75f
                val sameRow = c.vOverlap(last) > 0.6f && min(c.h, last.h) >= minRatio * max(c.h, last.h)
                val gap = c.x0 - last.x1
                val maxGap = if (c.w < 0.3f * refH) 1.2f * refH else 0.75f * refH
                if (sameRow && gap >= -2 && gap < maxGap) { group += c; used[j] = true; last = c }
            }
            if (group.size !in 2..3) continue
            for (gi in group.indices) group[gi] = extendVertically(mask, w, h, group[gi])
            // Cellule de référence de la rangée : hauteur maximale (les chiffres 4, 7 et 1 n'ont pas tous les segments).
            val top = group.minOf { it.y0 }; val bottom = group.maxOf { it.y1 }
            val cellH = bottom - top + 1
            val cellW = group.filter { it.w > 0.4f * cellH }.maxOfOrNull { it.w } ?: (cellH * 0.55f).toInt()
            debug?.invoke("group r=$r ${group.joinToString { "[${it.x0},${it.y0} ${it.w}x${it.h}]" }}")
            val digits = group.map { decode(mask, w, it, top, bottom, cellW) ?: return@map null }
            if (digits.any { it == null }) continue
            val ds = digits.filterNotNull()
            val text = ds.joinToString("") { it.value.toString() }
            if (text.startsWith("0")) continue
            val uncertain = ds.any { it.uncertain }
            out += OcrElement(
                text = text,
                left = group.minOf { it.x0 }.toFloat(), top = top.toFloat(),
                right = group.maxOf { it.x1 }.toFloat(), bottom = bottom.toFloat(),
                uncertain = uncertain,
            )
        }
        return out
    }

    private const val MIN_HEIGHT_IN_STROKES = 5.5f

    /**
     * Rattache à une case les segments horizontaux détachés situés juste au-dessus ou en dessous
     * (segments a ou d séparés par un petit espace sur certains afficheurs).
     */
    private fun extendVertically(mask: BooleanArray, w: Int, h: Int, b: Box): Box {
        if (b.w < 0.3f * b.h) return b // « 1 » : pas de segment horizontal
        val xa = b.x0 + b.w / 4; val xb = b.x1 - b.w / 4
        fun rowDark(y: Int) = (xa..xb).any { x -> mask[y * w + x] }
        val maxGap = max(2, (0.12f * b.h).toInt())
        val reach = (0.3f * b.h).toInt()
        var y1 = b.y1; var gap = 0
        for (y in b.y1 + 1..min(h - 1, b.y1 + reach)) {
            if (rowDark(y)) { y1 = y; gap = 0 } else if (++gap > maxGap) break
        }
        var y0 = b.y0; gap = 0
        for (y in b.y0 - 1 downTo max(0, b.y0 - reach)) {
            if (rowDark(y)) { y0 = y; gap = 0 } else if (++gap > maxGap) break
        }
        return Box(b.x0, y0, b.x1, y1)
    }

    /** Épaisseur de trait : médiane des longueurs des plages sombres horizontales dans la cellule. */
    private fun strokeWidth(mask: BooleanArray, w: Int, b: Box): Float? {
        val runs = ArrayList<Int>()
        for (y in b.y0..b.y1) {
            var run = 0
            for (x in b.x0..b.x1) {
                if (mask[y * w + x]) run++ else { if (run > 0) runs += run; run = 0 }
            }
            if (run > 0) runs += run
        }
        if (runs.isEmpty()) return null
        runs.sort()
        return runs[runs.size / 2].toFloat()
    }

    /** Décode un chiffre en mesurant la présence de chacun des 7 segments dans la cellule. */
    private fun decode(mask: BooleanArray, w: Int, b: Box, top: Int, bottom: Int, cellW: Int): Digit? {
        val h = bottom - top + 1
        // « 1 » : cellule étroite occupant presque toute la hauteur.
        if (b.w < 0.42f * cellW) {
            val rows = (top..bottom).count { y -> (b.x0..b.x1).any { x -> mask[y * w + x] } }
            return if (rows >= 0.65f * h) Digit(b, 1, uncertain = false) else null
        }
        val x0 = b.x1 - cellW + 1 // aligne la cellule à droite (les chiffres sont justifiés à droite)
        val left = max(b.x0, x0)
        val cw = b.x1 - left + 1
        val imgH = mask.size / w
        fun dark(x: Int, y: Int) = x in 0 until w && y in 0 until imgH && mask[y * w + x]
        // Segment horizontal : proportion des colonnes centrales comportant un pixel sombre dans la bande.
        fun hSeg(y0f: Float, y1f: Float): Boolean {
            val ya = top + (y0f * h).toInt(); val yb = top + (y1f * h).toInt()
            val xa = left + (0.3f * cw).toInt(); val xb = left + (0.7f * cw).toInt()
            if (xb <= xa) return false
            val n = (xa..xb).count { x -> (ya..yb).any { y -> dark(x, y) } }
            return n >= 0.55f * (xb - xa + 1)
        }
        // Segment vertical : proportion des lignes de la bande comportant un pixel sombre sur le bord.
        fun vSeg(x0f: Float, x1f: Float, y0f: Float, y1f: Float): Boolean {
            val xa = left + (x0f * cw).toInt(); val xb = left + (x1f * cw).toInt()
            val ya = top + (y0f * h).toInt(); val yb = top + (y1f * h).toInt()
            val n = (ya..yb).count { y -> (xa..xb).any { x -> dark(x, y) } }
            return n >= 0.55f * (yb - ya + 1)
        }
        val seg = booleanArrayOf(
            hSeg(0f, 0.2f),                       // a
            vSeg(0.62f, 1f, 0.15f, 0.38f),        // b
            vSeg(0.62f, 1f, 0.62f, 0.85f),        // c
            hSeg(0.8f, 1f),                       // d
            vSeg(0f, 0.38f, 0.62f, 0.85f),        // e
            vSeg(0f, 0.38f, 0.15f, 0.38f),        // f
            hSeg(0.4f, 0.6f),                     // g
        )
        val code = seg.fold(0) { acc, s -> (acc shl 1) or (if (s) 1 else 0) }
        debug?.invoke("cell $b row $top..$bottom cellW=$cellW code=${Integer.toBinaryString(code).padStart(7, '0')}")
        CODE_TO_DIGIT[code]?.let { return Digit(b, it, uncertain = false) }
        // Tolérance d'un segment, seulement si le chiffre le plus proche est unique.
        val near = CODE_TO_DIGIT.filter { Integer.bitCount(it.key xor code) == 1 }.values.toSet()
        return if (near.size == 1 && near.first() != 1) Digit(b, near.first(), uncertain = true) else null
    }
}
