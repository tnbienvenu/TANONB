package com.tanonb.scandoc

import com.tanonb.scandoc.export.DocxWriter
import com.tanonb.scandoc.export.PdfWriter
import com.tanonb.scandoc.imaging.DocumentDetector
import com.tanonb.scandoc.imaging.Filters
import com.tanonb.scandoc.imaging.PageFilter
import com.tanonb.scandoc.imaging.PageSize
import com.tanonb.scandoc.ocr.OcrLine
import com.tanonb.scandoc.ocr.OcrPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.abs

class CoreTest {

    /** Feuille blanche inclinée (quadrilatère) sur fond sombre, avec quelques lignes de « texte ». */
    private fun synthetic(w: Int, h: Int, corners: List<Pair<Float, Float>>): IntArray {
        val px = IntArray(w * h) { 0xFF3A3530.toInt() }
        fun inside(x: Float, y: Float): Boolean {
            var sign = 0
            for (i in 0 until 4) {
                val (ax, ay) = corners[i]; val (bx, by) = corners[(i + 1) % 4]
                val c = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
                val s = if (c > 0) 1 else -1
                if (sign == 0) sign = s else if (s != sign) return false
            }
            return true
        }
        for (y in 0 until h) for (x in 0 until w) {
            if (inside(x + 0.5f, y + 0.5f)) px[y * w + x] = if (y % 12 < 2 && x % 40 < 30) 0xFF202020.toInt() else 0xFFF2F0EA.toInt()
        }
        return px
    }

    @Test fun detectsTiltedSheet() {
        val w = 240; val h = 320
        val corners = listOf(40f to 50f, 200f to 35f, 215f to 290f, 25f to 280f)
        val q = DocumentDetector.detectArgb(synthetic(w, h, corners), w, h)
        assertNotNull(q)
        q!!.points.zip(corners).forEach { (p, c) ->
            assertTrue("coin ${p.x * w},${p.y * h} attendu $c", abs(p.x * w - c.first) < 8 && abs(p.y * h - c.second) < 8)
        }
    }

    /** Photo réaliste : facture en perspective sur une table en bois, ombre sur la gauche (coins connus). */
    @Test fun detectsSheetOnRealisticPhoto() {
        val raw = javaClass.classLoader!!.getResourceAsStream("photo_192x256.rgb")!!.readBytes()
        val w = 192; val h = 256
        val px = IntArray(w * h) { i -> (0xff shl 24) or ((raw[3 * i].toInt() and 0xff) shl 16) or ((raw[3 * i + 1].toInt() and 0xff) shl 8) or (raw[3 * i + 2].toInt() and 0xff) }
        val q = DocumentDetector.detectArgb(px, w, h)
        assertNotNull(q)
        // Coins dans la photo 1500×2000 : (220,260) (1290,180) (1380,1820) (160,1760)
        val expected = listOf(220f / 1500 to 260f / 2000, 1290f / 1500 to 180f / 2000, 1380f / 1500 to 1820f / 2000, 160f / 1500 to 1760f / 2000)
        q!!.points.zip(expected).forEach { (p, e) ->
            assertTrue("coin ${p.x},${p.y} attendu $e", abs(p.x - e.first) < 0.04f && abs(p.y - e.second) < 0.04f)
        }
    }

    @Test fun noDocumentOnUniformImage() {
        assertNull(DocumentDetector.detectArgb(IntArray(200 * 200) { 0xFF808080.toInt() }, 200, 200))
    }

    @Test fun normalizationWhitensShadowedPaper() {
        val w = 200; val h = 200
        // Papier avec un dégradé d'ombre (gauche sombre) et du texte noir.
        val px = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val paper = 120 + x * 120 / w
            val v = if (y % 20 < 3) 30 else paper
            (0xff shl 24) or (v shl 16) or (v shl 8) or v
        }
        Filters.apply(px, w, h, PageFilter.AUTO)
        val paperLeft = Filters.lum(px[10 * w + 5]); val paperRight = Filters.lum(px[10 * w + 195])
        val text = Filters.lum(px[1 * w + 100])
        assertTrue("papier gauche $paperLeft", paperLeft > 235)
        assertTrue("papier droit $paperRight", paperRight > 235)
        assertTrue("texte $text", text < 90)
    }

    private fun jpeg(name: String): ByteArray = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes()

    private fun writePdf(name: String, size: PageSize, password: String?): File {
        val ocr = OcrPage(listOf(listOf(OcrLine("Facture n° 2026-12 – Montant : 12 437 000 F", 0.1f, 0.1f, 0.9f, 0.14f))))
        val out = File("build/test-pdfs").apply { mkdirs() }.let { File(it, name) }
        out.outputStream().use {
            PdfWriter(it).write(listOf(PdfWriter.PageInput(jpeg("p.jpg"), 400, 560, ocr), PdfWriter.PageInput(jpeg("l.jpg"), 600, 400, null)), "Essai é", size, password)
        }
        return out
    }

    @Test fun pdfStructureIsConsistent() {
        val f = writePdf("plain.pdf", PageSize.AUTO, null)
        val bytes = f.readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        // Chaque entrée de la table xref pointe bien sur « n 0 obj ».
        val xrefPos = text.substringAfterLast("startxref\n").substringBefore("\n").trim().toInt()
        val lines = text.substring(xrefPos).lines()
        val count = lines[1].split(" ")[1].toInt()
        for (n in 1 until count) {
            val off = lines[2 + n].substring(0, 10).toInt()
            assertTrue("objet $n", text.startsWith("$n 0 obj", off))
        }
        assertTrue(text.contains("/Count 2"))
        assertTrue(text.contains("3 Tr"))
    }

    @Test fun pdfWithPageSizeAndPassword() {
        writePdf("a4.pdf", PageSize.A4, null)
        val f = writePdf("protected.pdf", PageSize.A4, "secret")
        val text = String(f.readBytes(), Charsets.ISO_8859_1)
        assertTrue(text.contains("/Encrypt 5 0 R"))
        assertTrue(text.contains("/Filter /Standard /V 2 /R 3"))
        assertTrue(!text.contains("3 Tr"))  // le contenu est chiffré
    }

    @Test fun rc4KnownVector() {
        // Vecteur de test classique : clé "Key", texte "Plaintext" → BBF316E8D940AF0AD3
        val r = PdfWriter.Encryption.rc4("Key".toByteArray(), "Plaintext".toByteArray())
        assertEquals("BBF316E8D940AF0AD3", r.joinToString("") { String.format("%02X", it.toInt() and 0xff) })
    }

    @Test fun docxContainsText() {
        val out = ByteArrayOutputStream()
        DocxWriter.write(out, "Test", listOf(OcrPage(listOf(listOf(OcrLine("Bonjour & <monde>", 0f, 0f, 1f, 0.1f)))), OcrPage.EMPTY))
        val entries = mutableMapOf<String, String>()
        ZipInputStream(out.toByteArray().inputStream()).use { z ->
            while (true) { val e = z.nextEntry ?: break; entries[e.name] = z.readBytes().toString(Charsets.UTF_8) }
        }
        assertTrue(entries.containsKey("[Content_Types].xml"))
        val doc = entries.getValue("word/document.xml")
        assertTrue(doc.contains("Bonjour &amp; &lt;monde&gt;"))
        assertTrue(doc.contains("w:type=\"page\""))
        File("build/test-pdfs/test.docx").writeBytes(out.toByteArray())
    }
}
