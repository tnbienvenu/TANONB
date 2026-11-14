package com.cardiosuivi.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Point normalisé (0..1) dans l'image. */
data class NPoint(val x: Float, val y: Float)

/** Quadrilatère de recadrage : haut-gauche, haut-droit, bas-droit, bas-gauche (coordonnées normalisées). */
data class CropQuad(val tl: NPoint, val tr: NPoint, val br: NPoint, val bl: NPoint) {
    companion object {
        val FULL = CropQuad(NPoint(0f, 0f), NPoint(1f, 0f), NPoint(1f, 1f), NPoint(0f, 1f))
        val DEFAULT = CropQuad(NPoint(0.1f, 0.15f), NPoint(0.9f, 0.15f), NPoint(0.9f, 0.85f), NPoint(0.1f, 0.85f))
    }
    val isFull: Boolean get() = this == FULL
}

object ImageLoader {

    /** Charge une image en respectant l'orientation EXIF, réduite à [maxDim] pixels au plus. */
    fun load(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? =
        context.contentResolver.openInputStream(uri)?.use { first ->
            val orientation = runCatching { ExifInterface(first).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            decode({ context.contentResolver.openInputStream(uri) }, orientation, maxDim)
        }

    fun load(file: File, maxDim: Int = 2048): Bitmap? {
        val orientation = runCatching { ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return decode({ file.inputStream() }, orientation, maxDim)
    }

    private fun decode(open: () -> InputStream?, orientation: Int, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return scaleDown(bmp, maxDim)
        }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (rotated != bmp) bmp.recycle()
        return scaleDown(rotated, maxDim)
    }

    private fun scaleDown(b: Bitmap, maxDim: Int): Bitmap {
        val m = max(b.width, b.height)
        if (m <= maxDim) return b
        val f = maxDim.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * f).roundToInt(), (b.height * f).roundToInt(), true)
    }
}

object ImagePreprocessor {

    /** Recadrage avec correction de perspective : le quadrilatère est projeté sur un rectangle. */
    fun warp(src: Bitmap, q: CropQuad): Bitmap {
        if (q.isFull) return src
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val pts = floatArrayOf(q.tl.x * w, q.tl.y * h, q.tr.x * w, q.tr.y * h, q.br.x * w, q.br.y * h, q.bl.x * w, q.bl.y * h)
        val outW = max(hypot(pts[2] - pts[0], pts[3] - pts[1]), hypot(pts[4] - pts[6], pts[5] - pts[7])).roundToInt().coerceIn(32, 2048)
        val outH = max(hypot(pts[6] - pts[0], pts[7] - pts[1]), hypot(pts[4] - pts[2], pts[5] - pts[3])).roundToInt().coerceIn(32, 2048)
        val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val m = Matrix()
        if (!m.setPolyToPoly(pts, 0, dst, 0, 4)) return src
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Niveaux de gris + contraste renforcé. */
    fun enhanceContrast(src: Bitmap, contrast: Float = 1.8f): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val t = (-0.5f * contrast + 0.5f) * 255f
        cm.postConcat(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, t,
            0f, contrast, 0f, 0f, t,
            0f, 0f, contrast, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )))
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    /** Binarisation par seuil d'Otsu (optionnellement inversée pour les écrans à chiffres clairs sur fond sombre). */
    fun binarize(src: Bitmap, invert: Boolean = false): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val gray = IntArray(px.size)
        val hist = IntArray(256)
        for (i in px.indices) {
            val c = px[i]
            val g = (((c shr 16) and 0xff) * 299 + ((c shr 8) and 0xff) * 587 + (c and 0xff) * 114) / 1000
            gray[i] = g
            hist[g]++
        }
        val threshold = otsu(hist, px.size)
        for (i in px.indices) {
            val dark = gray[i] < threshold
            val black = if (invert) !dark else dark
            px[i] = if (black) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun otsu(hist: IntArray, total: Int): Int {
        var sum = 0.0
        for (i in 0 until 256) sum += i * hist[i].toDouble()
        var sumB = 0.0
        var wB = 0
        var best = 0.0
        var threshold = 127
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
}
