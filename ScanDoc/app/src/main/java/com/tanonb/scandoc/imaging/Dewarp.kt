package com.tanonb.scandoc.imaging

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Aplanissement d'une feuille courbée ou gondolée (code pur).
 *
 * 1. Chaque bord réel de la feuille est suivi le long du côté du quadrilatère : à chaque position, on cherche
 *    (perpendiculairement, ±3 %) le saut de couleur entre « papier à l'intérieur » et « fond à l'extérieur ».
 * 2. Le décalage est lissé par un polynôme t(1−t)(c₀ + c₁t), nul aux coins : un bord droit reste droit.
 *    Mesures aberrantes écartées ; courbure plafonnée (sinon le bord est gardé droit).
 * 3. La surface délimitée par les 4 bords courbes est projetée sur un rectangle (carreau de Coons),
 *    ce qui redresse aussi le contenu (lignes de texte, tableaux).
 */
object Dewarp {

    /** Coefficients (c₀, c₁) du décalage de chaque côté (haut, droite, bas, gauche), en pixels de l'image analysée. */
    fun edgeCurves(px: IntArray, w: Int, h: Int, q: List<FloatArray>): Array<FloatArray> {
        val n = w * h
        val r = FloatArray(n); val g = FloatArray(n); val b = FloatArray(n)
        for (i in 0 until n) { val c = px[i]; r[i] = ((c shr 16) and 0xff).toFloat(); g[i] = ((c shr 8) and 0xff).toFloat(); b[i] = (c and 0xff).toFloat() }
        val br = DocumentDetector.blur(r, w, h, 2); val bg = DocumentDetector.blur(g, w, h, 2); val bb = DocumentDetector.blur(b, w, h, 2)
        fun at(x: Float, y: Float): Int = y.toInt().coerceIn(0, h - 1) * w + x.toInt().coerceIn(0, w - 1)

        // Couleur du papier au centre du quadrilatère.
        val cx = q.sumOf { it[0].toDouble() }.toFloat() / 4; val cy = q.sumOf { it[1].toDouble() }.toFloat() / 4
        val idx = ArrayList<Int>()
        for (y in (cy - h / 12).toInt()..(cy + h / 12).toInt() step 2) for (x in (cx - w / 12).toInt()..(cx + w / 12).toInt() step 2) idx += at(x.toFloat(), y.toFloat())
        fun lumOf(i: Int) = 0.299f * br[i] + 0.587f * bg[i] + 0.114f * bb[i]
        val ml = idx.map(::lumOf).sorted()[idx.size / 2]
        val paper = idx.filter { lumOf(it) >= ml }
        fun med(f: (Int) -> Float) = paper.map(f).sorted()[paper.size / 2]
        val pl = med(::lumOf); val p1 = med { br[it] - bg[it] }; val p2 = med { (br[it] + bg[it]) / 2 - bb[it] }
        fun paperLike(i: Int): Boolean {
            val a = 1.6f * (br[i] - bg[i] - p1); val c = 1.6f * ((br[i] + bg[i]) / 2 - bb[i] - p2); val l = 0.45f * (lumOf(i) - pl)
            return sqrt(a * a + c * c + l * l) < 40f
        }

        val sides = listOf(q[0] to q[1], q[1] to q[2], q[3] to q[2], q[0] to q[3])
        val range = (0.03f * max(w, h)).toInt().coerceAtLeast(4)
        return Array(4) { k ->
            val (a, bEnd) = sides[k]
            val dx = bEnd[0] - a[0]; val dy = bEnd[1] - a[1]
            val len = hypot(dx, dy)
            if (len < 20f) return@Array floatArrayOf(0f, 0f)
            var nx = -dy / len; var ny = dx / len
            val mx = (a[0] + bEnd[0]) / 2; val my = (a[1] + bEnd[1]) / 2
            if ((mx - cx) * nx + (my - cy) * ny < 0) { nx = -nx; ny = -ny } // normale vers l'extérieur
            val samples = 48
            val ts = ArrayList<Float>(); val offs = ArrayList<Float>()
            for (s in 0 until samples) {
                val t = s / (samples - 1f)
                val px0 = a[0] + dx * t; val py0 = a[1] + dy * t
                var best = 0f; var bestS = 0
                for (o in -range..range) {
                    val inner = at(px0 + nx * (o - 4), py0 + ny * (o - 4)); val outer = at(px0 + nx * (o + 4), py0 + ny * (o + 4))
                    if (!paperLike(inner) || paperLike(outer)) continue
                    val i1 = at(px0 + nx * (o + 2), py0 + ny * (o + 2)); val i2 = at(px0 + nx * (o - 2), py0 + ny * (o - 2))
                    val gr = abs(br[i1] - br[i2]) + abs(bg[i1] - bg[i2]) + abs(bb[i1] - bb[i2])
                    if (gr > best) { best = gr; bestS = o }
                }
                if (best > 25f) { ts += t; offs += bestS.toFloat() }
            }
            if (ts.size < samples * 0.6) return@Array floatArrayOf(0f, 0f)
            fit(ts, offs, len)
        }
    }

    /** Moindres carrés robustes pour off(t) = t(1−t)(c₀ + c₁t) ; renvoie (0,0) si peu fiable. */
    internal fun fit(ts: List<Float>, offs: List<Float>, len: Float): FloatArray {
        var keep = ts.indices.toList()
        var c0 = 0.0; var c1 = 0.0
        repeat(3) {
            var s11 = 0.0; var s12 = 0.0; var s22 = 0.0; var b1 = 0.0; var b2 = 0.0
            for (i in keep) {
                val t = ts[i].toDouble(); val x1 = t * (1 - t); val x2 = t * t * (1 - t); val y = offs[i].toDouble()
                s11 += x1 * x1; s12 += x1 * x2; s22 += x2 * x2; b1 += x1 * y; b2 += x2 * y
            }
            val det = s11 * s22 - s12 * s12
            if (abs(det) < 1e-9) return floatArrayOf(0f, 0f)
            c0 = (b1 * s22 - b2 * s12) / det; c1 = (s11 * b2 - s12 * b1) / det
            val res = keep.map { i -> val t = ts[i]; abs(offs[i] - t * (1 - t) * (c0 + c1 * t)).toFloat() }
            val medRes = res.sorted()[res.size / 2]
            keep = keep.filterIndexed { j, _ -> res[j] < max(3f, 2.5f * medRes) }
            if (keep.size < 8) return floatArrayOf(0f, 0f)
        }
        val res = keep.map { i -> val t = ts[i]; abs(offs[i] - t * (1 - t) * (c0 + c1 * t)).toFloat() }.sorted()[keep.size / 2]
        var bulge = 0.0
        for (k in 0..20) { val t = k / 20.0; bulge = max(bulge, abs(t * (1 - t) * (c0 + c1 * t))) }
        return if (bulge > 0.03 * len || res > 3f) floatArrayOf(0f, 0f) else floatArrayOf(c0.toFloat(), c1.toFloat())
    }

    /**
     * Projection de la zone délimitée par les bords courbes sur un rectangle outW×outH (échantillonnage bilinéaire).
     * [q] et [curves] sont exprimés en pixels de [src] (courbes déjà mises à l'échelle).
     */
    fun render(src: IntArray, sw: Int, sh: Int, q: List<FloatArray>, curves: Array<FloatArray>, outW: Int, outH: Int): IntArray {
        val cx = q.sumOf { it[0].toDouble() }.toFloat() / 4; val cy = q.sumOf { it[1].toDouble() }.toFloat() / 4
        val sides = listOf(q[0] to q[1], q[1] to q[2], q[3] to q[2], q[0] to q[3])
        fun curvePoint(k: Int, t: Float, out: FloatArray) {
            val (a, b) = sides[k]
            val dx = b[0] - a[0]; val dy = b[1] - a[1]; val len = max(hypot(dx, dy), 1e-3f)
            var nx = -dy / len; var ny = dx / len
            if (((a[0] + b[0]) / 2 - cx) * nx + ((a[1] + b[1]) / 2 - cy) * ny < 0) { nx = -nx; ny = -ny }
            val o = t * (1 - t) * (curves[k][0] + curves[k][1] * t)
            out[0] = a[0] + dx * t + nx * o; out[1] = a[1] + dy * t + ny * o
        }
        val top = Array(outW) { FloatArray(2) }; val bottom = Array(outW) { FloatArray(2) }
        for (x in 0 until outW) { val u = if (outW > 1) x / (outW - 1f) else 0f; curvePoint(0, u, top[x]); curvePoint(2, u, bottom[x]) }
        val left = FloatArray(2); val right = FloatArray(2)
        val out = IntArray(outW * outH)
        for (y in 0 until outH) {
            val v = if (outH > 1) y / (outH - 1f) else 0f
            curvePoint(3, v, left); curvePoint(1, v, right)
            for (x in 0 until outW) {
                val u = if (outW > 1) x / (outW - 1f) else 0f
                val px = (1 - v) * top[x][0] + v * bottom[x][0] + (1 - u) * left[0] + u * right[0] -
                    ((1 - u) * (1 - v) * q[0][0] + u * (1 - v) * q[1][0] + u * v * q[2][0] + (1 - u) * v * q[3][0])
                val py = (1 - v) * top[x][1] + v * bottom[x][1] + (1 - u) * left[1] + u * right[1] -
                    ((1 - u) * (1 - v) * q[0][1] + u * (1 - v) * q[1][1] + u * v * q[2][1] + (1 - u) * v * q[3][1])
                out[y * outW + x] = sample(src, sw, sh, px, py)
            }
        }
        return out
    }

    private fun sample(src: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val fx = x.coerceIn(0f, w - 1.001f); val fy = y.coerceIn(0f, h - 1.001f)
        val x0 = fx.toInt(); val y0 = fy.toInt(); val tx = fx - x0; val ty = fy - y0
        val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
        val c00 = src[y0 * w + x0]; val c10 = src[y0 * w + x1]; val c01 = src[y1 * w + x0]; val c11 = src[y1 * w + x1]
        val w00 = (1 - tx) * (1 - ty); val w10 = tx * (1 - ty); val w01 = (1 - tx) * ty; val w11 = tx * ty
        val r = (((c00 shr 16) and 0xff) * w00 + ((c10 shr 16) and 0xff) * w10 + ((c01 shr 16) and 0xff) * w01 + ((c11 shr 16) and 0xff) * w11).roundToInt().coerceIn(0, 255)
        val g = (((c00 shr 8) and 0xff) * w00 + ((c10 shr 8) and 0xff) * w10 + ((c01 shr 8) and 0xff) * w01 + ((c11 shr 8) and 0xff) * w11).roundToInt().coerceIn(0, 255)
        val b = ((c00 and 0xff) * w00 + (c10 and 0xff) * w10 + (c01 and 0xff) * w01 + (c11 and 0xff) * w11).roundToInt().coerceIn(0, 255)
        return (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }
}
