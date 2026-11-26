package com.tanonb.scandoc.imaging

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Filtres de numérisation sur des pixels ARGB (code pur, testable sur la JVM).
 *
 * Le cœur est la normalisation de l'éclairage : on estime la couleur du papier (fond) par blocs,
 * puis on divise chaque pixel par ce fond. Les ombres et les dominantes de couleur disparaissent
 * et le papier devient blanc, comme sur un scanner à plat.
 */
object Filters {

    fun apply(px: IntArray, w: Int, h: Int, filter: PageFilter) {
        when (filter) {
            PageFilter.ORIGINAL -> Unit
            // Rendu « scanner à plat » : ombres et plis effacés (grille fine), papier blanc pur, texte net.
            PageFilter.AUTO -> { normalize(px, w, h, 40); sharpen(px, w, h, 0.7f); levels(px, black = 45, white = 222, saturation = 1.3f) }
            PageFilter.GRAY -> { normalize(px, w, h, 40); sharpen(px, w, h, 0.7f); levels(px, black = 45, white = 222, saturation = 0f) }
            PageFilter.BW -> { normalize(px, w, h, 40); blackAndWhite(px) }
            PageFilter.WHITEBOARD -> { normalize(px, w, h, 20); levels(px, black = 60, white = 215, saturation = 1.5f) }
        }
    }

    private fun sharpCh(v: Int, sum9: Int, amount: Float) = (v + amount * (v - sum9 / 9f)).roundToInt().coerceIn(0, 255)

    /** Accentuation (masque flou) : rend les caractères plus nets, comme un scanner. */
    fun sharpen(px: IntArray, w: Int, h: Int, amount: Float) {
        val src = px.copyOf()
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            var r = 0; var g = 0; var b = 0
            for (dy in -1..1) for (dx in -1..1) { val c = src[i + dy * w + dx]; r += (c shr 16) and 0xff; g += (c shr 8) and 0xff; b += c and 0xff }
            val c = src[i]
            px[i] = (0xff shl 24) or (sharpCh((c shr 16) and 0xff, r, amount) shl 16) or (sharpCh((c shr 8) and 0xff, g, amount) shl 8) or sharpCh(c and 0xff, b, amount)
        }
    }

    /** Divise chaque pixel par la couleur estimée du papier à cet endroit. */
    fun normalize(px: IntArray, w: Int, h: Int, divisions: Int = 20) {
        val block = max(6, min(w, h) / divisions)
        val gw = (w + block - 1) / block
        val gh = (h + block - 1) / block
        val bgR = FloatArray(gw * gh); val bgG = FloatArray(gw * gh); val bgB = FloatArray(gw * gh)
        val hist = IntArray(256)
        for (gy in 0 until gh) for (gx in 0 until gw) {
            java.util.Arrays.fill(hist, 0)
            val x0 = gx * block; val y0 = gy * block
            val x1 = min(w, x0 + block); val y1 = min(h, y0 + block)
            var count = 0
            var y = y0
            while (y < y1) {
                var x = x0
                while (x < x1) { hist[lum(px[y * w + x])]++; count++; x += 2 }
                y += 2
            }
            // Seuil des 10 % de pixels les plus clairs du bloc : ce sont ceux du papier.
            var acc = 0; var thr = 255
            for (v in 255 downTo 0) { acc += hist[v]; if (acc >= max(1, count / 10)) { thr = v; break } }
            var r = 0L; var g = 0L; var b = 0L; var c = 0
            y = y0
            while (y < y1) {
                var x = x0
                while (x < x1) {
                    val p = px[y * w + x]
                    if (lum(p) >= thr) { r += (p shr 16) and 0xff; g += (p shr 8) and 0xff; b += p and 0xff; c++ }
                    x += 2
                }
                y += 2
            }
            val i = gy * gw + gx
            if (c > 0) { bgR[i] = r.toFloat() / c; bgG[i] = g.toFloat() / c; bgB[i] = b.toFloat() / c } else { bgR[i] = 255f; bgG[i] = 255f; bgB[i] = 255f }
        }
        // Dilatation (max 3×3) pour ignorer les blocs couverts de texte, puis lissage.
        for (arr in arrayOf(bgR, bgG, bgB)) {
            val dil = dilate(arr, gw, gh)
            val sm = smooth(dil, gw, gh)
            System.arraycopy(sm, 0, arr, 0, arr.size)
        }
        // Interpolation bilinéaire du fond et division.
        for (y in 0 until h) {
            val fy = ((y + 0.5f) / block - 0.5f).coerceIn(0f, (gh - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(gh - 1, y0 + 1); val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) / block - 0.5f).coerceIn(0f, (gw - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(gw - 1, x0 + 1); val tx = fx - x0
                val i00 = y0 * gw + x0; val i01 = y0 * gw + x1; val i10 = y1 * gw + x0; val i11 = y1 * gw + x1
                fun bil(a: FloatArray) = (a[i00] * (1 - tx) + a[i01] * tx) * (1 - ty) + (a[i10] * (1 - tx) + a[i11] * tx) * ty
                val p = px[y * w + x]
                val r = (((p shr 16) and 0xff) * 255f / max(bil(bgR), 40f)).roundToInt().coerceIn(0, 255)
                val g = (((p shr 8) and 0xff) * 255f / max(bil(bgG), 40f)).roundToInt().coerceIn(0, 255)
                val b = ((p and 0xff) * 255f / max(bil(bgB), 40f)).roundToInt().coerceIn(0, 255)
                px[y * w + x] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    /** Étirement des niveaux et réglage de la saturation (0 = niveaux de gris). */
    fun levels(px: IntArray, black: Int, white: Int, saturation: Float) {
        val lut = IntArray(256) { v -> ((v - black) * 255f / (white - black)).roundToInt().coerceIn(0, 255) }
        for (i in px.indices) {
            val p = px[i]
            var r = (p shr 16) and 0xff; var g = (p shr 8) and 0xff; var b = p and 0xff
            val l = (r * 299 + g * 587 + b * 114) / 1000
            r = (l + (r - l) * saturation).roundToInt().coerceIn(0, 255)
            g = (l + (g - l) * saturation).roundToInt().coerceIn(0, 255)
            b = (l + (b - l) * saturation).roundToInt().coerceIn(0, 255)
            px[i] = (0xff shl 24) or (lut[r] shl 16) or (lut[g] shl 8) or lut[b]
        }
    }

    /** Noir et blanc avec une courte rampe pour garder des contours lisses. */
    fun blackAndWhite(px: IntArray) {
        val lo = 150; val hi = 195
        for (i in px.indices) {
            val l = lum(px[i])
            val v = when { l <= lo -> 0; l >= hi -> 255; else -> (l - lo) * 255 / (hi - lo) }
            px[i] = (0xff shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    fun lum(p: Int): Int = ((((p shr 16) and 0xff) * 299 + ((p shr 8) and 0xff) * 587 + (p and 0xff) * 114) / 1000)

    private fun dilate(a: FloatArray, w: Int, h: Int) = FloatArray(a.size) { i ->
        val x = i % w; val y = i / w
        var m = 0f
        for (yy in max(0, y - 1)..min(h - 1, y + 1)) for (xx in max(0, x - 1)..min(w - 1, x + 1)) m = max(m, a[yy * w + xx])
        m
    }

    private fun smooth(a: FloatArray, w: Int, h: Int) = FloatArray(a.size) { i ->
        val x = i % w; val y = i / w
        var s = 0f; var c = 0
        for (yy in max(0, y - 1)..min(h - 1, y + 1)) for (xx in max(0, x - 1)..min(w - 1, x + 1)) { s += a[yy * w + xx]; c++ }
        s / c
    }
}
