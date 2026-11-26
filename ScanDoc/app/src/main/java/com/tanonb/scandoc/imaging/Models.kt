package com.tanonb.scandoc.imaging

import org.json.JSONArray
import org.json.JSONObject

/** Point normalisé (0..1) dans l'image. */
data class NPoint(val x: Float, val y: Float)

/** Quadrilatère de recadrage (haut-gauche, haut-droit, bas-droit, bas-gauche), coordonnées normalisées. */
data class Quad(val tl: NPoint, val tr: NPoint, val br: NPoint, val bl: NPoint) {
    val points: List<NPoint> get() = listOf(tl, tr, br, bl)
    val isFull: Boolean get() = this == FULL

    fun encode(): String = points.joinToString(",") { "${it.x},${it.y}" }

    fun with(index: Int, p: NPoint): Quad = when (index) {
        0 -> copy(tl = p); 1 -> copy(tr = p); 2 -> copy(br = p); else -> copy(bl = p)
    }

    /** Aire normalisée (formule du lacet). */
    fun area(): Float {
        val p = points
        var s = 0f
        for (i in p.indices) { val a = p[i]; val b = p[(i + 1) % 4]; s += a.x * b.y - b.x * a.y }
        return kotlin.math.abs(s) / 2f
    }

    companion object {
        val FULL = Quad(NPoint(0f, 0f), NPoint(1f, 0f), NPoint(1f, 1f), NPoint(0f, 1f))
        val INSET = Quad(NPoint(0.08f, 0.08f), NPoint(0.92f, 0.08f), NPoint(0.92f, 0.92f), NPoint(0.08f, 0.92f))

        fun decode(s: String?): Quad {
            val v = s?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: return FULL
            if (v.size != 8) return FULL
            return Quad(NPoint(v[0], v[1]), NPoint(v[2], v[3]), NPoint(v[4], v[5]), NPoint(v[6], v[7]))
        }

        /** Remet 4 points quelconques dans l'ordre TL, TR, BR, BL. */
        fun ordered(pts: List<NPoint>): Quad {
            val tl = pts.minBy { it.x + it.y }
            val br = pts.maxBy { it.x + it.y }
            val rest = pts.filter { it !== tl && it !== br }.ifEmpty { pts }
            val tr = rest.maxBy { it.x - it.y }
            val bl = rest.minBy { it.x - it.y }
            return Quad(tl, tr, br, bl)
        }
    }
}

enum class PageFilter(val label: String) {
    ORIGINAL("Original"),
    AUTO("Couleur auto"),
    GRAY("Niveaux de gris"),
    BW("Noir et blanc"),
    WHITEBOARD("Lumineux");

    companion object {
        fun of(s: String?): PageFilter = entries.firstOrNull { it.name == s } ?: AUTO
    }
}

/**
 * Élément « Remplir et signer » posé sur une page, coordonnées normalisées à l'image traitée.
 * Signature : [path] d'une image PNG, [size] = largeur relative à la largeur de la page.
 * Texte (texte libre, date, ✓, ✗) : [text], [size] = hauteur de police relative à la largeur de la page.
 */
data class Stamp(
    val cx: Float,
    val cy: Float,
    val size: Float,
    val path: String? = null,
    val text: String? = null,
    val color: Int = 0xFF101010.toInt(),
) {
    val isText: Boolean get() = text != null

    companion object {
        fun decodeList(s: String?): List<Stamp> = runCatching {
            val a = JSONArray(s ?: "[]")
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Stamp(
                    cx = o.getDouble("cx").toFloat(), cy = o.getDouble("cy").toFloat(), size = o.getDouble("s").toFloat(),
                    path = o.optString("path").ifEmpty { null }, text = if (o.has("text")) o.getString("text") else null,
                    color = o.optInt("color", 0xFF101010.toInt()),
                )
            }
        }.getOrDefault(emptyList())

        fun encodeList(list: List<Stamp>): String = JSONArray().apply {
            list.forEach { st ->
                put(JSONObject().put("cx", st.cx.toDouble()).put("cy", st.cy.toDouble()).put("s", st.size.toDouble()).put("color", st.color).apply {
                    st.path?.let { put("path", it) }
                    st.text?.let { put("text", it) }
                })
            }
        }.toString()
    }
}

/** Outils de retouche : gomme de nettoyage, stylo, surligneur. */
enum class MarkTool(val label: String) { ERASE("Nettoyer"), PEN("Stylo"), HIGHLIGHT("Surligneur") }

/** Trait dessiné sur une page (coordonnées normalisées, épaisseur relative à la largeur). */
data class Mark(val tool: MarkTool, val color: Int, val width: Float, val points: List<NPoint>) {
    companion object {
        fun decodeList(s: String?): List<Mark> = runCatching {
            val a = JSONArray(s ?: "[]")
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val p = o.getJSONArray("p")
                Mark(
                    MarkTool.valueOf(o.getString("tool")), o.getInt("color"), o.getDouble("w").toFloat(),
                    (0 until p.length() / 2).map { k -> NPoint(p.getDouble(2 * k).toFloat(), p.getDouble(2 * k + 1).toFloat()) },
                )
            }
        }.getOrDefault(emptyList())

        fun encodeList(list: List<Mark>): String = JSONArray().apply {
            list.forEach { m ->
                put(JSONObject().put("tool", m.tool.name).put("color", m.color).put("w", m.width.toDouble())
                    .put("p", JSONArray().apply { m.points.forEach { put(it.x.toDouble()); put(it.y.toDouble()) } }))
            }
        }.toString()
    }
}

/** Taille de page du PDF (« Redimensionner »), en points PostScript (portrait). */
enum class PageSize(val label: String, val w: Float, val h: Float) {
    AUTO("Taille d'origine", 0f, 0f),
    A4("A4", 595f, 842f),
    A3("A3", 842f, 1191f),
    A5("A5", 420f, 595f),
    LETTER("Lettre US", 612f, 792f),
    LEGAL("Légal US", 612f, 1008f),
    CARD("Carte de visite", 252f, 144f);

    companion object { fun of(s: String?) = entries.firstOrNull { it.name == s } ?: AUTO }
}

/** Niveaux de compression du PDF. */
enum class Compression(val label: String, val detail: String, val quality: Int, val maxDim: Int) {
    HIGH("Qualité élevée", "Meilleure netteté, fichier plus lourd", 85, 2200),
    MEDIUM("Qualité moyenne", "Bon compromis (recommandé)", 70, 1700),
    LOW("Taille réduite", "Idéal pour l'envoi par e-mail ou WhatsApp", 50, 1200);

    companion object { fun of(s: String?) = entries.firstOrNull { it.name == s } ?: MEDIUM }
}
