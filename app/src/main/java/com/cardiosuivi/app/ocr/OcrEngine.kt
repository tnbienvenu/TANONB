package com.cardiosuivi.app.ocr

import android.graphics.Bitmap
import com.cardiosuivi.domain.ocr.GrayImage
import com.cardiosuivi.domain.ocr.OcrElement
import com.cardiosuivi.domain.ocr.SevenSegmentReader
import com.cardiosuivi.domain.ocr.OcrParser
import com.cardiosuivi.domain.ocr.OcrReading
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reconnaissance des valeurs affichées par le tensiomètre, entièrement hors ligne :
 * - lecteur dédié aux afficheurs 7 segments (SevenSegmentReader, module domaine) ;
 * - ML Kit (modèle latin embarqué) sur plusieurs variantes prétraitées, fusionnées par consensus.
 * Les deux lectures sont comparées (OcrParser.combine) ; tout désaccord rend le champ incertain.
 * Aucune image ni aucun texte n'est transmis hors de l'appareil.
 */
class OcrEngine {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    data class Result(val reading: OcrReading, val variantsAnalysed: Int)

    suspend fun recognize(source: Bitmap, quad: CropQuad): Result = withContext(Dispatchers.Default) {
        val cropped = ImagePreprocessor.warp(source, quad)
        // 1) Lecteur dédié aux afficheurs LCD 7 segments (méthode principale pour les tensiomètres).
        val seven = runCatching { SevenSegmentReader.read(toGray(cropped, 1600)).reading }.getOrNull()
        // 2) OCR généraliste ML Kit (utile pour les écrans à chiffres « imprimés » et les libellés SYS/DIA/PUL).
        val mlkit = runCatching { recognizeMlKit(cropped) }.getOrNull()
        val reading = when {
            seven != null && mlkit != null -> OcrParser.combine(seven, mlkit, SevenSegmentReader::quality)
            seven != null -> seven
            mlkit != null -> mlkit
            else -> throw IllegalStateException("La reconnaissance a échoué")
        }
        Result(reading, 5)
    }

    /** Conversion en niveaux de gris (luminance), réduite à [maxDim] pixels au plus. */
    private fun toGray(src: Bitmap, maxDim: Int): GrayImage {
        val m = maxOf(src.width, src.height)
        val b = if (m > maxDim) Bitmap.createScaledBitmap(src, src.width * maxDim / m, src.height * maxDim / m, true) else src
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        for (i in px.indices) {
            val c = px[i]
            px[i] = (((c shr 16) and 0xff) * 299 + ((c shr 8) and 0xff) * 587 + (c and 0xff) * 114) / 1000
        }
        return GrayImage(b.width, b.height, px)
    }

    private suspend fun recognizeMlKit(cropped: Bitmap): OcrReading {
        val variants = buildList {
            add(cropped)
            add(ImagePreprocessor.enhanceContrast(cropped))
            add(ImagePreprocessor.binarize(cropped))
            add(ImagePreprocessor.binarize(cropped, invert = true))
        }
        val readings = variants.mapNotNull { bmp ->
            runCatching { OcrParser.parse(toElements(process(bmp))) }.getOrNull()
        }
        if (readings.isEmpty()) throw IllegalStateException("La reconnaissance de texte a échoué")
        // On privilégie les lectures ayant trouvé des valeurs ; le consensus gère les désaccords.
        val useful = readings.filter { it.systolic.value != null || it.diastolic.value != null }.ifEmpty { readings }
        return OcrParser.consensus(useful)
    }

    private suspend fun process(bitmap: Bitmap): Text = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun toElements(text: Text): List<OcrElement> = text.textBlocks.flatMap { block ->
        block.lines.flatMap { line ->
            line.elements.mapNotNull { el ->
                val b = el.boundingBox ?: return@mapNotNull null
                OcrElement(el.text, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
            }
        }
    }
}
