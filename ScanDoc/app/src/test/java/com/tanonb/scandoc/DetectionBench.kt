package com.tanonb.scandoc

import com.tanonb.scandoc.imaging.DocumentDetector
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Banc d'essai de la détection des bords (lancé seulement si SCANDOC_SCENES désigne un dossier de scènes) :
 * chaque ligne de manifest.txt = nom, largeur, hauteur, puis 8 coordonnées attendues (ou « none »).
 */
class DetectionBench {
    @Test fun bench() {
        val dir = File(System.getenv("SCANDOC_SCENES") ?: return)
        val quick = System.getenv("SCANDOC_QUICK") == "1"
        System.getenv("SCANDOC_CFG")?.split(",")?.let { c -> DocumentDetector.USE_VOTE = c[0] == "1"; DocumentDetector.VOTE_TOL = c[1].toFloat(); DocumentDetector.STUCK_PENALTY = c[2].toFloat() }
        var ok = 0; var total = 0; var sumErr = 0.0; var docs = 0
        val lines = File(dir, "manifest.txt").readLines().filter { it.isNotBlank() }
        val t0 = System.currentTimeMillis()
        for (line in lines) {
            val p = line.trim().split(Regex("\\s+"))
            val name = p[0]; val w = p[1].toInt(); val h = p[2].toInt()
            val raw = File(dir, "$name.rgb").readBytes()
            val px = IntArray(w * h) { i -> (0xff shl 24) or ((raw[3 * i].toInt() and 0xff) shl 16) or ((raw[3 * i + 1].toInt() and 0xff) shl 8) or (raw[3 * i + 2].toInt() and 0xff) }
            val only = System.getenv("SCANDOC_ONLY")
            if (only != null && only != name) continue
            DocumentDetector.debugLog = if (only != null) { m -> println("   $m") } else null
            val q = DocumentDetector.detectArgb(px, w, h, quick)
            total++
            if (p[3] == "none") {
                val good = q == null || q.isFull
                if (good) ok++
                println(String.format("%-16s %s", name, if (good) "OK (aucun document)" else "ÉCHEC faux positif $q"))
            } else {
                docs++
                val exp = (0 until 4).map { p[3 + 2 * it].toFloat() to p[4 + 2 * it].toFloat() }
                if (q == null || q.isFull) { println(String.format("%-16s ÉCHEC non détecté", name)); continue }
                val err = q.points.zip(exp).maxOf { (a, e) -> max(abs(a.x - e.first), abs(a.y - e.second)).toDouble() }
                sumErr += err
                val good = err < 0.02
                if (good) ok++
                println(String.format("%-16s %s erreur max %.1f %%", name, if (good) "OK  " else "ÉCHEC", err * 100) + if (good) "" else "  obtenu " + q.points.joinToString(" ") { String.format("%.3f,%.3f", it.x, it.y) })
            }
        }
        println(String.format("RÉSULTAT %d/%d réussis, erreur moyenne %.2f %%, %d ms par image", ok, total, 100 * sumErr / max(docs, 1), (System.currentTimeMillis() - t0) / lines.size))
    }
}
