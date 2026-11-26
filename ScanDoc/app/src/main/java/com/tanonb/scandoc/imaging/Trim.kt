package com.tanonb.scandoc.imaging

import kotlin.math.sqrt

/**
 * Rognage automatique : après redressement, retire les bandes de fond (table, ombre) restées
 * sur les bords. On compare chaque ligne/colonne, depuis le bord vers l'intérieur, à la couleur
 * du papier (estimée au centre) ; on s'arrête à la première ligne qui ressemble au papier.
 * Au plus [maxFraction] de la dimension est retiré de chaque côté.
 */
object Trim {
    /** Renvoie (haut, bas, gauche, droite) en pixels de l'image fournie. */
    fun find(px: IntArray, w: Int, h: Int, maxFraction: Float = 0.06f): IntArray {
        if (w < 32 || h < 32) return IntArray(4)
        val n = w * h
        val lum = FloatArray(n); val c1 = FloatArray(n); val c2 = FloatArray(n)
        for (i in 0 until n) {
            val c = px[i]; val r = ((c shr 16) and 0xff).toFloat(); val g = ((c shr 8) and 0xff).toFloat(); val b = (c and 0xff).toFloat()
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b; c1[i] = r - g; c2[i] = (r + g) / 2 - b
        }
        // Couleur du papier : moitié la plus claire de la zone centrale.
        val idx = ArrayList<Int>()
        for (y in h / 4 until 3 * h / 4 step 2) for (x in w / 4 until 3 * w / 4 step 2) idx += y * w + x
        val ml = idx.map { lum[it] }.sorted()[idx.size / 2]
        val paper = idx.filter { lum[it] >= ml }
        fun med(a: FloatArray) = paper.map { a[it] }.sorted()[paper.size / 2]
        val pl = med(lum); val p1 = med(c1); val p2 = med(c2)
        val bg = BooleanArray(n) {
            val a = 1.6f * (c1[it] - p1); val b = 1.6f * (c2[it] - p2); val l = 0.45f * (lum[it] - pl)
            sqrt(a * a + b * b + l * l) > 38f
        }
        fun rowFrac(y: Int): Float { var k = 0; for (x in 0 until w) if (bg[y * w + x]) k++; return k.toFloat() / w }
        fun colFrac(x: Int): Float { var k = 0; for (y in 0 until h) if (bg[y * w + x]) k++; return k.toFloat() / h }
        val limH = (h * maxFraction).toInt(); val limW = (w * maxFraction).toInt()
        val thr = 0.30f
        var top = 0; while (top < limH && rowFrac(top) > thr) top++
        var bottom = 0; while (bottom < limH && rowFrac(h - 1 - bottom) > thr) bottom++
        var left = 0; while (left < limW && colFrac(left) > thr) left++
        var right = 0; while (right < limW && colFrac(w - 1 - right) > thr) right++
        // Une ligne de plus pour ne pas garder de liseré flou.
        return intArrayOf(top, bottom, left, right).map { if (it > 0) it + 1 else 0 }.toIntArray()
    }
}
