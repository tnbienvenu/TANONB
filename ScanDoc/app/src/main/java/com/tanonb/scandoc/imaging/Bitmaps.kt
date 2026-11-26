package com.tanonb.scandoc.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

object Bitmaps {
    /** Taille maximale (plus grand côté) des photos d'origine conservées. */
    const val ORIGINAL_MAX = 2800
    /** Taille maximale des pages traitées (≈ A4 à 250 dpi). */
    const val PROCESSED_MAX = 2200

    fun load(context: Context, uri: Uri, maxDim: Int = ORIGINAL_MAX): Bitmap? {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return decode({ context.contentResolver.openInputStream(uri) }, orientation, maxDim)
    }

    fun load(file: File, maxDim: Int = ORIGINAL_MAX): Bitmap? {
        if (!file.exists()) return null
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
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return scaleDown(bmp, maxDim)
        }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (rotated !== bmp) bmp.recycle()
        return scaleDown(rotated, maxDim)
    }

    fun scaleDown(b: Bitmap, maxDim: Int): Bitmap {
        val m = max(b.width, b.height)
        if (m <= maxDim) return b
        val f = maxDim.toFloat() / m
        val out = Bitmap.createScaledBitmap(b, (b.width * f).roundToInt().coerceAtLeast(1), (b.height * f).roundToInt().coerceAtLeast(1), true)
        if (out !== b) b.recycle()
        return out
    }

    fun saveJpeg(b: Bitmap, file: File, quality: Int = 90) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
    }

    /** Détection des bords sur une copie réduite de l'image. */
    fun detect(b: Bitmap): Quad? {
        val f = 256f / max(b.width, b.height)
        val small = Bitmap.createScaledBitmap(b, (b.width * f).roundToInt().coerceAtLeast(16), (b.height * f).roundToInt().coerceAtLeast(16), true)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val q = DocumentDetector.detectArgb(px, small.width, small.height)
        if (small !== b) small.recycle()
        return q
    }

    /** Aimante un recadrage manuel sur les bords réels de la feuille. */
    fun snap(b: Bitmap, q: Quad): Quad {
        val f = 256f / max(b.width, b.height)
        val small = Bitmap.createScaledBitmap(b, (b.width * f).roundToInt().coerceAtLeast(16), (b.height * f).roundToInt().coerceAtLeast(16), true)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val out = DocumentDetector.snap(px, small.width, small.height, q)
        if (small !== b) small.recycle()
        return out
    }

    /** Recadrage avec correction de perspective : le quadrilatère est projeté sur un rectangle. */
    fun warp(src: Bitmap, q: Quad, maxDim: Int = PROCESSED_MAX): Bitmap {
        val w = src.width.toFloat(); val h = src.height.toFloat()
        val pts = floatArrayOf(q.tl.x * w, q.tl.y * h, q.tr.x * w, q.tr.y * h, q.br.x * w, q.br.y * h, q.bl.x * w, q.bl.y * h)
        var outW = max(hypot(pts[2] - pts[0], pts[3] - pts[1]), hypot(pts[4] - pts[6], pts[5] - pts[7]))
        var outH = max(hypot(pts[6] - pts[0], pts[7] - pts[1]), hypot(pts[4] - pts[2], pts[5] - pts[3]))
        val scale = minOf(1f, maxDim / max(outW, outH))
        outW = (outW * scale).coerceAtLeast(16f); outH = (outH * scale).coerceAtLeast(16f)
        val dst = floatArrayOf(0f, 0f, outW, 0f, outW, outH, 0f, outH)
        val m = Matrix()
        if (q.isFull) m.setScale(outW / w, outH / h)
        else if (!m.setPolyToPoly(pts, 0, dst, 0, 4)) return src.copy(Bitmap.Config.ARGB_8888, true)
        val out = Bitmap.createBitmap(outW.roundToInt(), outH.roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Rognage automatique des bandes de fond restées en haut, en bas et sur les côtés. */
    fun autoTrim(b: Bitmap): Bitmap {
        val f = minOf(1f, 400f / max(b.width, b.height))
        val small = if (f < 1f) Bitmap.createScaledBitmap(b, (b.width * f).roundToInt().coerceAtLeast(32), (b.height * f).roundToInt().coerceAtLeast(32), true) else b
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val t = Trim.find(px, small.width, small.height)
        val sx = b.width.toFloat() / small.width; val sy = b.height.toFloat() / small.height
        if (small !== b) small.recycle()
        val top = (t[0] * sy).roundToInt(); val bottom = (t[1] * sy).roundToInt(); val left = (t[2] * sx).roundToInt(); val right = (t[3] * sx).roundToInt()
        if (top + bottom + left + right == 0) return b
        val out = Bitmap.createBitmap(b, left, top, b.width - left - right, b.height - top - bottom)
        if (out !== b) b.recycle()
        return out
    }

    /** Réglage « Aplanir les pages courbées » (mis à jour depuis les préférences). */
    @Volatile var flattenEnabled: Boolean = true

    /**
     * Recadrage avec aplanissement : si les bords réels de la feuille sont courbes, la page est « dépliée »
     * (carreau de Coons) ; sinon redressement de perspective classique.
     */
    fun flatWarp(src: Bitmap, q: Quad, maxDim: Int = PROCESSED_MAX): Bitmap {
        if (q.isFull || !flattenEnabled) return warp(src, q, maxDim)
        val fa = minOf(1f, 900f / max(src.width, src.height))
        val small = if (fa < 1f) Bitmap.createScaledBitmap(src, (src.width * fa).roundToInt(), (src.height * fa).roundToInt(), true) else src
        val spx = IntArray(small.width * small.height)
        small.getPixels(spx, 0, small.width, 0, 0, small.width, small.height)
        val qs = q.points.map { floatArrayOf(it.x * small.width, it.y * small.height) }
        val curves = Dewarp.edgeCurves(spx, small.width, small.height, qs)
        if (small !== src) small.recycle()
        // Courbure négligeable partout : la perspective suffit (plus fidèle pour une feuille plane).
        val significant = (0 until 4).any { k ->
            val a = qs[if (k == 3) 0 else if (k == 2) 3 else k]; val b = qs[if (k == 0) 1 else if (k == 3) 3 else 2]
            val len = hypot(b[0] - a[0], b[1] - a[1])
            (0..10).any { i -> val t = i / 10f; kotlin.math.abs(t * (1 - t) * (curves[k][0] + curves[k][1] * t)) > 0.004f * len }
        }
        if (!significant) return warp(src, q, maxDim)
        val sc = 1f / fa
        val qf = q.points.map { floatArrayOf(it.x * src.width, it.y * src.height) }
        val cf = Array(4) { floatArrayOf(curves[it][0] * sc, curves[it][1] * sc) }
        var outW = max(hypot(qf[1][0] - qf[0][0], qf[1][1] - qf[0][1]), hypot(qf[2][0] - qf[3][0], qf[2][1] - qf[3][1]))
        var outH = max(hypot(qf[3][0] - qf[0][0], qf[3][1] - qf[0][1]), hypot(qf[2][0] - qf[1][0], qf[2][1] - qf[1][1]))
        val scale = minOf(1f, maxDim / max(outW, outH))
        outW = (outW * scale).coerceAtLeast(16f); outH = (outH * scale).coerceAtLeast(16f)
        val px = IntArray(src.width * src.height)
        src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
        val ow = outW.roundToInt(); val oh = outH.roundToInt()
        val out = Dewarp.render(px, src.width, src.height, qf, cf, ow, oh)
        return Bitmap.createBitmap(out, ow, oh, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
    }

    fun rotate(b: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return b
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        val out = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        if (out !== b) b.recycle()
        return out
    }

    fun applyFilter(b: Bitmap, filter: PageFilter): Bitmap {
        if (filter == PageFilter.ORIGINAL) return b
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        Filters.apply(px, b.width, b.height, filter)
        val out = if (b.isMutable) b else b.copy(Bitmap.Config.ARGB_8888, true).also { b.recycle() }
        out.setPixels(px, 0, out.width, 0, 0, out.width, out.height)
        return out
    }

    fun drawOverlays(b: Bitmap, marks: List<Mark>, stamps: List<Stamp>): Bitmap {
        if (stamps.isEmpty() && marks.isEmpty()) return b
        val out = if (b.isMutable) b else b.copy(Bitmap.Config.ARGB_8888, true).also { b.recycle() }
        val c = Canvas(out)
        Painters.drawMarks(c, out.width.toFloat(), out.height.toFloat(), marks)
        Painters.drawStamps(c, out.width.toFloat(), out.height.toFloat(), stamps)
        return out
    }

    /** Chaîne complète : recadrage → rotation → filtre → retouches → signatures et textes. */
    fun render(
        original: Bitmap, quad: Quad, rotation: Int, filter: PageFilter,
        marks: List<Mark> = emptyList(), stamps: List<Stamp> = emptyList(), maxDim: Int = PROCESSED_MAX,
    ): Bitmap {
        var b = flatWarp(original, quad, maxDim)
        if (!quad.isFull) b = autoTrim(b)
        b = rotate(b, rotation)
        b = applyFilter(b, filter)
        return drawOverlays(b, marks, stamps)
    }

    /** Vignette (lecture rapide d'un JPEG réduit). */
    fun thumbnail(file: File, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
