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
            val expected = f.nameWithoutExtension.substringBefore('-')
            if (got == expected) ok++
            println("${f.name}: lu $got (attendu $expected) en ${System.currentTimeMillis() - t0} ms ; candidats ${r.candidates}")
        }
        println("Résultat : $ok/${files.size} lectures exactes")
    }
}
