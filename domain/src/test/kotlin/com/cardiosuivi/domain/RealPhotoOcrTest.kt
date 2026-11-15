package com.cardiosuivi.domain

import com.cardiosuivi.domain.ocr.GrayImage
import com.cardiosuivi.domain.ocr.SevenSegmentReader
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Évaluation sur photos réelles d'écrans de tensiomètres, NON versionnées (données personnelles).
 * Usage : CARDIO_OCR_SAMPLES=/dossier ./gradlew :domain:test --tests '*RealPhotoOcrTest*' -i
 * Nom de fichier attendu : <sys>_<dia>_<pouls>.jpg (ex. 125_85_78.jpg) ; le résultat est comparé au nom.
 */
class RealPhotoOcrTest {
    @Test
    fun evaluateSamples() {
        val dir = System.getenv("CARDIO_OCR_SAMPLES")?.let(::File)
        assumeTrue("CARDIO_OCR_SAMPLES non défini : test ignoré", dir != null && dir.isDirectory)
        var ok = 0
        val stats = sortedMapOf<String, Int>()
        val files = dir!!.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }!!.sorted()
        for (f in files) {
            val img = ImageIO.read(f) ?: continue
            val px = IntArray(img.width * img.height) { i ->
                val c = img.getRGB(i % img.width, i / img.width)
                (((c shr 16) and 0xff) * 299 + ((c shr 8) and 0xff) * 587 + (c and 0xff) * 114) / 1000
            }
            val t0 = System.currentTimeMillis()
            val r = SevenSegmentReader.read(GrayImage(img.width, img.height, px)).reading
            val got = "${r.systolic.value}_${r.diastolic.value}_${r.pulse.value}"
            val expected = f.nameWithoutExtension.substringBefore("__").substringBefore('-')
            if (got == expected) ok++
            val truth = expected.split("_")
            r.fields.forEachIndexed { i, fl ->
                val v = fl.value?.toString()
                val k = when { v == null -> "rejet"; v == truth.getOrNull(i) && !fl.uncertain -> "correct_sur"; v == truth.getOrNull(i) -> "correct_a_verifier"; !fl.uncertain -> "ERREUR_SURE"; else -> "erreur_a_verifier" }
                stats[k] = (stats[k] ?: 0) + 1
            }
            println("${f.name}: lu $got (attendu $expected) incertains ${r.fields.map { it.uncertain }} en ${System.currentTimeMillis() - t0} ms")
        }
        println("Résultat : $ok/${files.size} lectures exactes ; champs : $stats")
    }
}
