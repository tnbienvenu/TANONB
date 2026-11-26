package com.tanonb.scandoc

import com.tanonb.scandoc.export.DocxWriter
import com.tanonb.scandoc.export.PdfWriter
import com.tanonb.scandoc.imaging.DocumentDetector
import com.tanonb.scandoc.imaging.Filters
import com.tanonb.scandoc.imaging.NPoint
import com.tanonb.scandoc.imaging.Quad
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

    /** Vraie photo (facture sur table claire, reflet à gauche, ordinateur en haut) : la feuille est plus sombre que le reflet. */
    @Test fun detectsSheetOnRealPhonePhotoWithGlare() {
        val raw = javaClass.classLoader!!.getResourceAsStream("reel_210x256.rgb")!!.readBytes()
        val w = 210; val h = 256
        val px = IntArray(w * h) { i -> (0xff shl 24) or ((raw[3 * i].toInt() and 0xff) shl 16) or ((raw[3 * i + 1].toInt() and 0xff) shl 8) or (raw[3 * i + 2].toInt() and 0xff) }
        val q = DocumentDetector.detectArgb(px, w, h)
        assertNotNull(q)
        val expected = listOf(205f / 1032 to 163f / 1260, 795f / 1032 to 178f / 1260, 980f / 1032 to 1012f / 1260, 255f / 1032 to 1115f / 1260)
        q!!.points.zip(expected).forEach { (p, e) ->
            assertTrue("coin ${p.x},${p.y} attendu $e", abs(p.x - e.first) < 0.03f && abs(p.y - e.second) < 0.03f)
        }
        // Mode rapide (appareil photo en direct) : même résultat à peu près.
        val quick = DocumentDetector.detectArgb(px, w, h, quick = true)
        assertNotNull(quick)
        quick!!.points.zip(expected).forEach { (p, e) -> assertTrue("rapide ${p.x},${p.y} attendu $e", abs(p.x - e.first) < 0.04f && abs(p.y - e.second) < 0.04f) }
    }

    /** Recadrage manuel approximatif (côtés haut et bas décalés) : l'aimantation le recale sur les vrais bords. */
    @Test fun manualCropSnapsToSheetEdges() {
        val raw = javaClass.classLoader!!.getResourceAsStream("reel_210x256.rgb")!!.readBytes()
        val w = 210; val h = 256
        val px = IntArray(w * h) { i -> (0xff shl 24) or ((raw[3 * i].toInt() and 0xff) shl 16) or ((raw[3 * i + 1].toInt() and 0xff) shl 8) or (raw[3 * i + 2].toInt() and 0xff) }
        val truth = listOf(205f / 1032 to 163f / 1260, 795f / 1032 to 178f / 1260, 980f / 1032 to 1012f / 1260, 255f / 1032 to 1115f / 1260)
        val off = listOf(0f to -0.025f, 0f to -0.025f, 0f to 0.02f, 0f to 0.02f)  // haut trop haut, bas trop bas
        val start = Quad(
            NPoint(truth[0].first + off[0].first, truth[0].second + off[0].second), NPoint(truth[1].first + off[1].first, truth[1].second + off[1].second),
            NPoint(truth[2].first + off[2].first, truth[2].second + off[2].second), NPoint(truth[3].first + off[3].first, truth[3].second + off[3].second),
        )
        val snapped = DocumentDetector.snap(px, w, h, start)
        fun err(q: Quad) = q.points.zip(truth).sumOf { (p, t) -> (abs(p.x - t.first) + abs(p.y - t.second)).toDouble() }
        assertTrue("erreur avant ${err(start)}, après ${err(snapped)}", err(snapped) < err(start) * 0.5)
    }

    /** Recadrage trop large (table visible sur les 4 bords) : le rognage automatique retire les bandes de table. */
    @Test fun autoTrimRemovesTableStrips() {
        val raw = javaClass.classLoader!!.getResourceAsStream("loose_280x380.rgb")!!.readBytes()
        val w = 280; val h = 380
        val px = IntArray(w * h) { i -> (0xff shl 24) or ((raw[3 * i].toInt() and 0xff) shl 16) or ((raw[3 * i + 1].toInt() and 0xff) shl 8) or (raw[3 * i + 2].toInt() and 0xff) }
        val t = com.tanonb.scandoc.imaging.Trim.find(px, w, h)
        // Contour agrandi de 5 % : la feuille commence à ~2,4 % de chaque bord.
        val (top, bottom, left, right) = listOf(t[0].toFloat() / h, t[1].toFloat() / h, t[2].toFloat() / w, t[3].toFloat() / w)
        listOf(top, bottom, left, right).forEach { assertTrue("rognage ${t.toList()}", it in 0.015f..0.04f) }
    }

    @Test fun autoTrimKeepsCleanPage() {
        val w = 200; val h = 280
        val px = IntArray(w * h) { i -> val y = i / w; if (y % 14 in 6..7) 0xFF222222.toInt() else 0xFFF4F4F0.toInt() }
        assertEquals(listOf(0, 0, 0, 0), com.tanonb.scandoc.imaging.Trim.find(px, w, h).toList())
    }

    /** Aplanissement : un bord droit reste droit, un bord courbé est mesuré, une mesure aberrante est rejetée. */
    @Test fun dewarpEdgeFit() {
        val ts = List(40) { it / 39f }
        val straight = com.tanonb.scandoc.imaging.Dewarp.fit(ts, ts.map { 0f }, 800f)
        assertEquals(0f, straight[0], 1e-3f)
        val bent = ts.map { t -> t * (1 - t) * 40f }               // flèche de 10 px au milieu
        val noisy = bent.mapIndexed { i, v -> if (i == 7) v + 25f else v }  // un point aberrant
        val c = com.tanonb.scandoc.imaging.Dewarp.fit(ts, noisy, 800f)
        assertEquals(40f, c[0], 2f)
        // Courbure invraisemblable (> 3 % du côté) : on garde le bord droit.
        val wild = com.tanonb.scandoc.imaging.Dewarp.fit(ts, ts.map { t -> t * (1 - t) * 400f }, 800f)
        assertEquals(0f, wild[0], 1e-3f)
    }

    /** Carreau de Coons sur un rectangle sans courbure = simple recadrage. */
    @Test fun dewarpRenderIdentity() {
        val w = 40; val h = 30
        val src = IntArray(w * h) { i -> (0xff shl 24) or ((i % w) * 6 shl 16) or ((i / w) * 8 shl 8) }
        val q = listOf(floatArrayOf(0f, 0f), floatArrayOf(39f, 0f), floatArrayOf(39f, 29f), floatArrayOf(0f, 29f))
        val out = com.tanonb.scandoc.imaging.Dewarp.render(src, w, h, q, Array(4) { floatArrayOf(0f, 0f) }, w, h)
        assertEquals(src[5 * w + 7], out[5 * w + 7])
        assertEquals(src[29 * w + 39], out[29 * w + 39])
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
