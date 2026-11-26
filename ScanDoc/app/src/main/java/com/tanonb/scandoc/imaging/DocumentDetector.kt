package com.tanonb.scandoc.imaging

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Détection automatique des bords d'un document (sans OpenCV), sur une image réduite (~256 px).
 *
 * Principe : une feuille est en général plus claire et moins saturée que le fond. On calcule un score
 * « clair et peu coloré », on le lisse, on le seuille (Otsu), on garde la plus grande zone connexe,
 * puis on cherche le quadrilatère d'aire maximale inscrit dans son enveloppe convexe.
 */
object DocumentDetector {

    /** Détection sur des pixels ARGB. Renvoie null si aucun document n'est trouvé de façon fiable. */
    fun detectArgb(px: IntArray, w: Int, h: Int): Quad? {
        val score = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xff; val g = (c shr 8) and 0xff; val b = c and 0xff
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            val sat = max(r, max(g, b)) - min(r, min(g, b))
            score[i] = (lum - sat / 2).coerceIn(0, 255)
        }
        return detect(score, w, h)
    }

    /** Détection sur une carte de luminance (0..255), par exemple le plan Y d'une image caméra. */
    fun detect(lum: IntArray, w: Int, h: Int): Quad? {
        if (w < 16 || h < 16) return null
        val n = w * h
        val s = boxBlur(lum, w, h, 2)

        val hist = IntArray(256)
        for (v in s) hist[v.coerceIn(0, 255)]++
        val t = otsu(hist, n)
        // Contraste insuffisant entre la feuille et le fond.
        var sumLo = 0L; var cntLo = 0; var sumHi = 0L; var cntHi = 0
        for (v in s) if (v > t) { sumHi += v; cntHi++ } else { sumLo += v; cntLo++ }
        if (cntLo == 0 || cntHi == 0) return null
        if (sumHi / cntHi - sumLo / cntLo < 30) return null

        // Plus grande composante connexe claire.
        val label = IntArray(n)
        val queue = IntArray(n)
        var bestLabel = 0; var bestSize = 0; var next = 1
        for (start in 0 until n) {
            if (s[start] <= t || label[start] != 0) continue
            var head = 0; var tail = 0
            queue[tail++] = start; label[start] = next
            while (head < tail) {
                val p = queue[head++]
                val x = p % w; val y = p / w
                if (x > 0) { val q = p - 1; if (label[q] == 0 && s[q] > t) { label[q] = next; queue[tail++] = q } }
                if (x < w - 1) { val q = p + 1; if (label[q] == 0 && s[q] > t) { label[q] = next; queue[tail++] = q } }
                if (y > 0) { val q = p - w; if (label[q] == 0 && s[q] > t) { label[q] = next; queue[tail++] = q } }
                if (y < h - 1) { val q = p + w; if (label[q] == 0 && s[q] > t) { label[q] = next; queue[tail++] = q } }
            }
            if (tail > bestSize) { bestSize = tail; bestLabel = next }
            next++
        }
        if (bestSize < n * 0.10) return null

        // Étendue de la composante ligne par ligne (les trous dus au texte sont ignorés).
        val pts = ArrayList<FloatArray>()
        var filled = 0L
        for (y in 0 until h) {
            var lo = -1; var hi = -1
            val row = y * w
            for (x in 0 until w) if (label[row + x] == bestLabel) { if (lo < 0) lo = x; hi = x }
            if (lo >= 0) {
                filled += hi - lo + 1
                pts += floatArrayOf(lo.toFloat(), y.toFloat()); pts += floatArrayOf(hi + 1f, y.toFloat())
                pts += floatArrayOf(lo.toFloat(), y + 1f); pts += floatArrayOf(hi + 1f, y + 1f)
            }
        }
        val hull = convexHull(pts)
        if (hull.size < 4) return null
        val quad = maxAreaQuad(hull) ?: return null
        val qArea = polygonArea(quad)
        if (qArea < n * 0.10) return null
        // La zone doit ressembler à un quadrilatère (et non à une forme quelconque).
        if (filled / qArea < 0.80) return null

        // Léger retrait vers le centre : évite de garder un liseré du fond sur les bords.
        val cx = quad.sumOf { it[0].toDouble() }.toFloat() / 4; val cy = quad.sumOf { it[1].toDouble() }.toFloat() / 4
        val norm = quad.map {
            val x = it[0] + (cx - it[0]) * 0.012f; val y = it[1] + (cy - it[1]) * 0.012f
            NPoint((x / w).coerceIn(0f, 1f), (y / h).coerceIn(0f, 1f))
        }
        val q = Quad.ordered(norm)
        val nearFull = q.points.zip(Quad.FULL.points).all { (a, b) -> abs(a.x - b.x) < 0.025f && abs(a.y - b.y) < 0.025f }
        return if (nearFull) Quad.FULL else q
    }

    internal fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(src.size)
        val out = IntArray(src.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var sum = 0; var cnt = 0
                for (k in max(0, x - r)..min(w - 1, x + r)) { sum += src[row + k]; cnt++ }
                tmp[row + x] = sum / cnt
            }
        }
        for (x in 0 until w) for (y in 0 until h) {
            var sum = 0; var cnt = 0
            for (k in max(0, y - r)..min(h - 1, y + r)) { sum += tmp[k * w + x]; cnt++ }
            out[y * w + x] = sum / cnt
        }
        return out
    }

    fun otsu(hist: IntArray, total: Int): Int {
        var sum = 0.0
        for (i in 0 until 256) sum += i * hist[i].toDouble()
        var sumB = 0.0; var wB = 0; var best = -1.0; var threshold = 127
        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t * hist[t].toDouble()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; threshold = t }
        }
        return threshold
    }

    /** Enveloppe convexe (chaîne monotone d'Andrew), sens trigonométrique. */
    internal fun convexHull(points: List<FloatArray>): List<FloatArray> {
        val p = points.distinctBy { it[0] to it[1] }.sortedWith(compareBy({ it[0] }, { it[1] }))
        if (p.size < 3) return p
        fun cross(o: FloatArray, a: FloatArray, b: FloatArray) = (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
        val lower = ArrayList<FloatArray>()
        for (pt in p) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), pt) <= 0) lower.removeAt(lower.size - 1); lower += pt }
        val upper = ArrayList<FloatArray>()
        for (pt in p.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), pt) <= 0) upper.removeAt(upper.size - 1); upper += pt }
        lower.removeAt(lower.size - 1); upper.removeAt(upper.size - 1)
        return lower + upper
    }

    /** Quadrilatère d'aire maximale dont les sommets sont des points de l'enveloppe (O(n³), n ≤ 64). */
    internal fun maxAreaQuad(hullIn: List<FloatArray>): List<FloatArray>? {
        val hull = if (hullIn.size > 64) List(64) { hullIn[it * hullIn.size / 64] } else hullIn
        val n = hull.size
        if (n < 4) return null
        fun tri(a: FloatArray, b: FloatArray, c: FloatArray) = abs((b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])) / 2f
        var best = -1f
        var result: List<FloatArray>? = null
        for (i in 0 until n) for (k in i + 2 until n) {
            var bj = -1; var aj = -1f
            for (j in i + 1 until k) { val a = tri(hull[i], hull[j], hull[k]); if (a > aj) { aj = a; bj = j } }
            var bl = -1; var al = -1f
            for (l in k + 1 until n + i) { val ll = l % n; val a = tri(hull[i], hull[k], hull[ll]); if (a > al) { al = a; bl = ll } }
            if (bj < 0 || bl < 0) continue
            if (aj + al > best) { best = aj + al; result = listOf(hull[i], hull[bj], hull[k], hull[bl]) }
        }
        return result
    }

    internal fun polygonArea(p: List<FloatArray>): Double {
        var s = 0.0
        for (i in p.indices) { val a = p[i]; val b = p[(i + 1) % p.size]; s += a[0] * b[1] - b[0] * a[1] }
        return abs(s) / 2.0
    }
}
