package com.tanonb.scandoc.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Ligne reconnue, boîte englobante normalisée (0..1) dans la page traitée. */
data class OcrLine(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Texte d'une page : blocs (paragraphes) composés de lignes. */
data class OcrPage(val blocks: List<List<OcrLine>>) {
    val text: String get() = blocks.joinToString("\n\n") { b -> b.joinToString("\n") { it.text } }
    val lines: List<OcrLine> get() = blocks.flatten()

    fun encode(): String = JSONArray().apply {
        blocks.forEach { b ->
            put(JSONArray().apply {
                b.forEach { l ->
                    put(JSONObject().put("t", l.text).put("l", l.left.toDouble()).put("u", l.top.toDouble()).put("r", l.right.toDouble()).put("b", l.bottom.toDouble()))
                }
            })
        }
    }.toString()

    companion object {
        val EMPTY = OcrPage(emptyList())

        fun decode(s: String?): OcrPage? {
            if (s == null) return null
            return runCatching {
                val a = JSONArray(s)
                OcrPage((0 until a.length()).map { i ->
                    val b = a.getJSONArray(i)
                    (0 until b.length()).map { j ->
                        val o = b.getJSONObject(j)
                        OcrLine(o.getString("t"), o.getDouble("l").toFloat(), o.getDouble("u").toFloat(), o.getDouble("r").toFloat(), o.getDouble("b").toFloat())
                    }
                })
            }.getOrNull()
        }
    }
}

/** Reconnaissance de texte hors ligne (ML Kit, alphabet latin : français, anglais…). */
object OcrEngine {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bitmap: Bitmap): OcrPage = suspendCancellableCoroutine { cont ->
        val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val blocks = result.textBlocks.map { block ->
                    block.lines.mapNotNull { line ->
                        val r = line.boundingBox ?: return@mapNotNull null
                        OcrLine(line.text, r.left / w, r.top / h, r.right / w, r.bottom / h)
                    }
                }.filter { it.isNotEmpty() }
                cont.resume(OcrPage(blocks))
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
