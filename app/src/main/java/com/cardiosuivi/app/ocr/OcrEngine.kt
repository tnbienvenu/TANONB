package com.cardiosuivi.app.ocr

import android.graphics.Bitmap
import com.cardiosuivi.domain.ocr.OcrElement
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
 * Reconnaissance des valeurs affichées par le tensiomètre.
 * ML Kit (modèle latin embarqué, fonctionnement hors ligne) est appliqué à plusieurs variantes
 * prétraitées de la même image ; les lectures sont ensuite fusionnées par consensus (OcrParser.consensus).
 * Aucune image ni aucun texte n'est transmis hors de l'appareil.
 */
class OcrEngine {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    data class Result(val reading: OcrReading, val variantsAnalysed: Int)

    suspend fun recognize(source: Bitmap, quad: CropQuad): Result = withContext(Dispatchers.Default) {
        val cropped = ImagePreprocessor.warp(source, quad)
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
        Result(OcrParser.consensus(useful), variants.size)
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
