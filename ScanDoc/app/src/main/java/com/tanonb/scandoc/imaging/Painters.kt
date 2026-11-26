package com.tanonb.scandoc.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache

/**
 * Dessin des signatures, textes et traits sur un Canvas Android de taille w×h.
 * Le même code sert au rendu final des pages et aux aperçus interactifs : ce qu'on voit est ce qu'on obtient.
 */
object Painters {
    private val signatures = LruCache<String, Bitmap>(8)

    fun signature(path: String): Bitmap? = signatures.get(path) ?: BitmapFactory.decodeFile(path)?.also { signatures.put(path, it) }

    private fun textPaint(s: Stamp, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = s.color
        textSize = s.size * w
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    /** Rectangle occupé par un élément (pour l'affichage, la sélection et le déplacement). */
    fun bounds(s: Stamp, w: Float, h: Float): RectF {
        val cx = s.cx * w; val cy = s.cy * h
        return if (s.isText) {
            val p = textPaint(s, w)
            val tw = p.measureText(s.text); val fm = p.fontMetrics
            val th = fm.descent - fm.ascent
            RectF(cx - tw / 2, cy - th / 2, cx + tw / 2, cy + th / 2)
        } else {
            val bmp = s.path?.let(::signature)
            val sw = s.size * w
            val sh = if (bmp != null) sw * bmp.height / bmp.width else sw / 3
            RectF(cx - sw / 2, cy - sh / 2, cx + sw / 2, cy + sh / 2)
        }
    }

    fun drawStamps(c: Canvas, w: Float, h: Float, stamps: List<Stamp>) {
        val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        for (s in stamps) {
            val r = bounds(s, w, h)
            if (s.isText) {
                val p = textPaint(s, w)
                c.drawText(s.text!!, r.left, r.top - p.fontMetrics.ascent, p)
            } else {
                s.path?.let(::signature)?.let { c.drawBitmap(it, null, r, bmpPaint) }
            }
        }
    }

    fun drawMarks(c: Canvas, w: Float, h: Float, marks: List<Mark>) {
        for (m in marks) {
            if (m.points.isEmpty()) continue
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = if (m.tool == MarkTool.HIGHLIGHT) Paint.Cap.SQUARE else Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = m.width * w
                color = m.color
                // Le surligneur assombrit sans masquer le texte (comme un vrai surligneur).
                if (m.tool == MarkTool.HIGHLIGHT) xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            }
            val path = Path()
            path.moveTo(m.points[0].x * w, m.points[0].y * h)
            if (m.points.size == 1) path.lineTo(m.points[0].x * w + 0.1f, m.points[0].y * h)
            for (i in 1 until m.points.size) path.lineTo(m.points[i].x * w, m.points[i].y * h)
            c.drawPath(path, paint)
        }
    }
}
