package com.tanonb.scandoc.imaging

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Détection automatique des bords d'un document (sans OpenCV), sur une image réduite (~256 px).
 *
 * 1. Candidats : zones de couleur proche de celle du papier au centre de l'image (plusieurs tolérances),
 *    et zones « claires et peu colorées » (plusieurs seuils). Une ouverture morphologique coupe les ponts
 *    fins (reflets, ombres) entre la feuille et le fond.
 * 2. Pour chaque candidat : enveloppe convexe → quadrilatère d'aire maximale, puis score =
 *    netteté du bord tout autour (saut de couleur de part et d'autre du contour) × remplissage × taille.
 * 3. Le meilleur quadrilatère est « aimanté » : chaque côté est déplacé sur le bord réel le plus net,
 *    et les coins sont recalculés par intersection des côtés.
 *
 * Ce choix vient de photos réelles : sous un éclairage chaud, la feuille peut être plus sombre que la table
 * (reflets) ; c'est la couleur et le contour, pas la luminosité, qui la distinguent.
 */
object DocumentDetector {

    /** Détection sur des pixels ARGB. Renvoie null si aucun document n'est trouvé de façon fiable. */
    fun detectArgb(px: IntArray, w: Int, h: Int, quick: Boolean = false): Quad? {
        if (w < 24 || h < 24) return null
        val n = w * h
        val r = FloatArray(n); val g = FloatArray(n); val b = FloatArray(n)
        for (i in 0 until n) {
            val c = px[i]
            r[i] = ((c shr 16) and 0xff).toFloat(); g[i] = ((c shr 8) and 0xff).toFloat(); b[i] = (c and 0xff).toFloat()
        }
        val img = Rgb(w, h, blur(r, w, h, 1), blur(g, w, h, 1), blur(b, w, h, 1))
        val lum = FloatArray(n) { 0.299f * img.r[it] + 0.587f * img.g[it] + 0.114f * img.b[it] }
        val c1 = FloatArray(n) { img.r[it] - img.g[it] }
        val c2 = FloatArray(n) { (img.r[it] + img.g[it]) / 2 - img.b[it] }

        // Couleur du papier : fenêtre centrale, pixels les plus clairs (le fond, pas le texte).
        val cx = w / 2; val cy = h / 2; val rx = max(2, w / 10); val ry = max(2, h / 10)
        val win = ArrayList<Int>()
        for (y in cy - ry until cy + ry) for (x in cx - rx until cx + rx) win += y * w + x
        val medLum = median(win.map { lum[it] })
        val bright = win.filter { lum[it] >= medLum }
        val pc1 = median(bright.map { c1[it] }); val pc2 = median(bright.map { c2[it] }); val pl = median(bright.map { lum[it] })
        val dist = FloatArray(n) {
            val a = 1.6f * (c1[it] - pc1); val bb = 1.6f * (c2[it] - pc2); val l = 0.45f * (lum[it] - pl)
            sqrt(a * a + bb * bb + l * l)
        }
        val distB = blur(dist, w, h, 2)
        val seeds = ArrayList<Int>()
        var y = cy - ry
        while (y < cy + ry) { var x = cx - rx; while (x < cx + rx) { seeds += y * w + x; x += 2 }; y += 2 }

        val masks = ArrayList<BooleanArray>()
        val tolerances = if (quick) floatArrayOf(14f, 24f, 42f) else floatArrayOf(10f, 14f, 18f, 24f, 32f, 42f, 55f)
        for (t in tolerances) masks += BooleanArray(n) { distB[it] < t }
        // Candidats « clairs et peu colorés » (fonds sombres, cas classiques).
        val sc = FloatArray(n) {
            val mn = min(img.r[it], min(img.g[it], img.b[it])); val mx = max(img.r[it], max(img.g[it], img.b[it]))
            mn - (mx - mn) / 2
        }
        val scB = blur(sc, w, h, 2)
        val sorted = scB.copyOf().also { it.sort() }
        for (pct in if (quick) intArrayOf(55, 75) else intArrayOf(50, 60, 70, 80)) {
            val t = sorted[(n - 1) * pct / 100]
            masks += BooleanArray(n) { scB[it] > t }
        }

        var best: Pair<Float, List<FloatArray>>? = null
        for (raw in masks) {
            val m = dilate(erode(raw, w, h, 2), w, h, 2)
            val comp = component(m, w, h, seeds)
            val size = comp.count { it }
            if (size < n * 0.08) continue
            val quad = quadOf(comp, w, h) ?: continue
            val qa = polygonArea(quad)
            if (qa < n * 0.08) continue
            // Surface pleine (trous du texte comblés) = tout ce qui n'est pas atteignable depuis le bord.
            val outside = component(BooleanArray(n) { !comp[it] }, w, h, borderSeeds(w, h))
            val solid = n - outside.count { it }
            val fill = min(solid / qa, qa / max(solid.toDouble(), 1.0)).toFloat()
            val es = edgeSupport(img, quad)
            val score = es * fill * fill * (0.5f + 0.5f * min(1f, (qa / (0.25 * n)).toFloat()))
            // Sans vrai bord visible (moins de la moitié du contour), ce n'est pas une feuille.
            if (es < 0.55f) continue
            if (best == null || score > best.first) best = score to quad
        }
        val (score, quad0) = best ?: return null
        if (score < 0.35f) return null
        val quad = refine(img, quad0)
        val q = Quad.ordered(quad.map { NPoint((it[0] / w).coerceIn(0f, 1f), (it[1] / h).coerceIn(0f, 1f)) })
        val nearFull = q.points.zip(Quad.FULL.points).all { (a, bb) -> abs(a.x - bb.x) < 0.03f && abs(a.y - bb.y) < 0.03f }
        return if (nearFull) Quad.FULL else q
    }

    /**
     * Aimantation d'un recadrage manuel : chaque côté est recalé sur le bord net le plus proche (quelques pixels),
     * sans jamais déplacer un coin de plus de ~3 % de l'image.
     */
    fun snap(px: IntArray, w: Int, h: Int, quad: Quad): Quad {
        if (quad.isFull) return quad
        val n = w * h
        val r = FloatArray(n); val g = FloatArray(n); val b = FloatArray(n)
        for (i in 0 until n) { val c = px[i]; r[i] = ((c shr 16) and 0xff).toFloat(); g[i] = ((c shr 8) and 0xff).toFloat(); b[i] = (c and 0xff).toFloat() }
        val img = Rgb(w, h, blur(r, w, h, 1), blur(g, w, h, 1), blur(b, w, h, 1))
        val pts = quad.points.map { floatArrayOf(it.x * w, it.y * h) }
        val out = refine(img, pts)
        return Quad(
            NPoint((out[0][0] / w).coerceIn(0f, 1f), (out[0][1] / h).coerceIn(0f, 1f)),
            NPoint((out[1][0] / w).coerceIn(0f, 1f), (out[1][1] / h).coerceIn(0f, 1f)),
            NPoint((out[2][0] / w).coerceIn(0f, 1f), (out[2][1] / h).coerceIn(0f, 1f)),
            NPoint((out[3][0] / w).coerceIn(0f, 1f), (out[3][1] / h).coerceIn(0f, 1f)),
        )
    }

    /** Image RVB flottante (lissée). */
    internal class Rgb(val w: Int, val h: Int, val r: FloatArray, val g: FloatArray, val b: FloatArray) {
        fun diff(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val i = y1.toInt() * w + x1.toInt(); val j = y2.toInt() * w + x2.toInt()
            return abs(r[i] - r[j]) + abs(g[i] - g[j]) + abs(b[i] - b[j])
        }
        fun inside(x: Float, y: Float) = x >= 0 && y >= 0 && x < w - 1 && y < h - 1
    }

    /** Part du contour qui longe un vrai bord (saut de couleur net de part et d'autre). */
    private fun edgeSupport(img: Rgb, q: List<FloatArray>): Float {
        var tot = 0f; var good = 0f
        for (i in 0 until 4) {
            val a = q[i]; val b = q[(i + 1) % 4]
            val len = hypot(b[0] - a[0], b[1] - a[1]); if (len < 1) continue
            val nx = -(b[1] - a[1]) / len; val ny = (b[0] - a[0]) / len
            for (k in 0 until 24) {
                val s = 0.05f + 0.9f * k / 23
                val px = a[0] + (b[0] - a[0]) * s; val py = a[1] + (b[1] - a[1]) * s
                val x1 = px + nx * 2.5f; val y1 = py + ny * 2.5f; val x2 = px - nx * 2.5f; val y2 = py - ny * 2.5f
                tot += 1
                // Côté collé au bord de l'image : ni preuve ni contre-preuve.
                if (!img.inside(x1, y1) || !img.inside(x2, y2)) { good += 0.5f; continue }
                val d = img.diff(x1, y1, x2, y2)
                good += if (d > 24) 1f else d / 24 * 0.5f
            }
        }
        return if (tot == 0f) 0f else good / tot
    }

    private fun lineStrength(img: Rgb, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val len = hypot(bx - ax, by - ay); if (len < 1) return 0f
        val nx = -(by - ay) / len; val ny = (bx - ax) / len
        var tot = 0f; var cnt = 0
        for (k in 0 until 20) {
            val s = 0.08f + 0.84f * k / 19
            val px = ax + (bx - ax) * s; val py = ay + (by - ay) * s
            val x1 = px + nx * 1.5f; val y1 = py + ny * 1.5f; val x2 = px - nx * 1.5f; val y2 = py - ny * 1.5f
            if (!img.inside(x1, y1) || !img.inside(x2, y2)) continue
            tot += min(img.diff(x1, y1, x2, y2), 80f); cnt++
        }
        return if (cnt == 0) 0f else tot / cnt
    }

    /** Aimante chaque côté sur le bord réel le plus net (±4 px), puis recalcule les coins. */
    private fun refine(img: Rgb, q: List<FloatArray>): List<FloatArray> {
        val lines = Array(4) { FloatArray(4) }
        for (i in 0 until 4) {
            val a = q[i]; val b = q[(i + 1) % 4]
            val len = hypot(b[0] - a[0], b[1] - a[1])
            val nx = -(b[1] - a[1]) / len; val ny = (b[0] - a[0]) / len
            var bestV = lineStrength(img, a[0], a[1], b[0], b[1])
            var line = floatArrayOf(a[0], a[1], b[0], b[1])
            for (da in -4..4) for (db in -4..4) {
                val ax = a[0] + nx * da; val ay = a[1] + ny * da; val bx = b[0] + nx * db; val by = b[1] + ny * db
                val v = lineStrength(img, ax, ay, bx, by)
                if (v > bestV + 0.5f) { bestV = v; line = floatArrayOf(ax, ay, bx, by) }
            }
            lines[i] = line
        }
        return List(4) { i ->
            val l1 = lines[(i + 3) % 4]; val l2 = lines[i]
            val d1x = l1[2] - l1[0]; val d1y = l1[3] - l1[1]; val d2x = l2[2] - l2[0]; val d2y = l2[3] - l2[1]
            val den = d1x * d2y - d1y * d2x
            if (abs(den) < 1e-6f) return@List q[i]
            val t = ((l2[0] - l1[0]) * d2y - (l2[1] - l1[1]) * d2x) / den
            val p = floatArrayOf(l1[0] + d1x * t, l1[1] + d1y * t)
            if (hypot(p[0] - q[i][0], p[1] - q[i][1]) > 8) q[i] else p
        }
    }

    // ------------------------------------------------------------------ outils

    private fun median(v: List<Float>): Float = if (v.isEmpty()) 0f else v.sorted()[v.size / 2]

    /** Flou boîte par image intégrale (rayon r). */
    internal fun blur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val sat = DoubleArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0.0
            for (x in 0 until w) { row += src[y * w + x]; sat[(y + 1) * (w + 1) + x + 1] = sat[y * (w + 1) + x + 1] + row }
        }
        return FloatArray(w * h) { i ->
            val x = i % w; val y = i / w
            val x0 = max(0, x - r); val x1 = min(w, x + r + 1); val y0 = max(0, y - r); val y1 = min(h, y + r + 1)
            val s = sat[y1 * (w + 1) + x1] - sat[y0 * (w + 1) + x1] - sat[y1 * (w + 1) + x0] + sat[y0 * (w + 1) + x0]
            (s / ((x1 - x0) * (y1 - y0))).toFloat()
        }
    }

    private fun erode(m: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        val f = blur(FloatArray(m.size) { if (m[it]) 1f else 0f }, w, h, r)
        return BooleanArray(m.size) { f[it] > 0.999f }
    }

    private fun dilate(m: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        val f = blur(FloatArray(m.size) { if (m[it]) 1f else 0f }, w, h, r)
        return BooleanArray(m.size) { f[it] > 0.001f }
    }

    private fun borderSeeds(w: Int, h: Int): List<Int> =
        (0 until w).flatMap { listOf(it, (h - 1) * w + it) } + (0 until h).flatMap { listOf(it * w, it * w + w - 1) }

    /** Pixels de [mask] reliés (4-voisinage) à l'une des graines. */
    private fun component(mask: BooleanArray, w: Int, h: Int, seeds: List<Int>): BooleanArray {
        val lab = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        var head = 0; var tail = 0
        for (s in seeds) if (mask[s] && !lab[s]) { lab[s] = true; queue[tail++] = s }
        while (head < tail) {
            val p = queue[head++]; val x = p % w; val y = p / w
            if (x > 0 && mask[p - 1] && !lab[p - 1]) { lab[p - 1] = true; queue[tail++] = p - 1 }
            if (x < w - 1 && mask[p + 1] && !lab[p + 1]) { lab[p + 1] = true; queue[tail++] = p + 1 }
            if (y > 0 && mask[p - w] && !lab[p - w]) { lab[p - w] = true; queue[tail++] = p - w }
            if (y < h - 1 && mask[p + w] && !lab[p + w]) { lab[p + w] = true; queue[tail++] = p + w }
        }
        return lab
    }

    private fun quadOf(comp: BooleanArray, w: Int, h: Int): List<FloatArray>? {
        val pts = ArrayList<FloatArray>()
        for (y in 0 until h) {
            var lo = -1; var hi = -1
            for (x in 0 until w) if (comp[y * w + x]) { if (lo < 0) lo = x; hi = x }
            if (lo >= 0) {
                pts += floatArrayOf(lo.toFloat(), y.toFloat()); pts += floatArrayOf(hi + 1f, y.toFloat())
                pts += floatArrayOf(lo.toFloat(), y + 1f); pts += floatArrayOf(hi + 1f, y + 1f)
            }
        }
        val hull = convexHull(pts)
        return if (hull.size < 4) null else maxAreaQuad(hull)
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

    /** Quadrilatère d'aire maximale dont les sommets sont des points de l'enveloppe (O(n³), n ≤ 48). */
    internal fun maxAreaQuad(hullIn: List<FloatArray>): List<FloatArray>? {
        val hull = if (hullIn.size > 48) List(48) { hullIn[it * hullIn.size / 48] } else hullIn
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
