package com.tanonb.scandoc.export

import com.tanonb.scandoc.imaging.PageSize
import com.tanonb.scandoc.ocr.OcrPage
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.max
import kotlin.math.min

/**
 * Écriture d'un PDF (code pur, sans bibliothèque) :
 *  - une image JPEG par page (DCTDecode : fichier compact) ;
 *  - une couche de texte invisible issue de l'OCR → PDF consultable (recherche, copier-coller) ;
 *  - taille de page au choix (taille d'origine, A4, Lettre…), l'image étant centrée avec marges ;
 *  - protection facultative par mot de passe (gestionnaire de sécurité standard, révision 3, RC4 128 bits).
 */
class PdfWriter(private val out: OutputStream) {

    class PageInput(val jpeg: ByteArray, val pixelWidth: Int, val pixelHeight: Int, val ocr: OcrPage?)

    private var offset = 0L
    private val offsets = ArrayList<Long>()
    private var crypt: Encryption? = null

    private fun write(b: ByteArray) { out.write(b); offset += b.size }
    private fun write(s: String) = write(s.toByteArray(Charsets.ISO_8859_1))

    private fun beginObj(num: Int) {
        while (offsets.size < num) offsets += 0L
        offsets[num - 1] = offset
        write("$num 0 obj\n")
    }

    private fun stream(num: Int, dict: String, data: ByteArray) {
        val bytes = crypt?.encrypt(num, data) ?: data
        beginObj(num)
        write("<< $dict /Length ${bytes.size} >>\nstream\n"); write(bytes); write("\nendstream\nendobj\n")
    }

    private fun hex(num: Int, bytes: ByteArray) = (crypt?.encrypt(num, bytes) ?: bytes).joinToString("", "<", ">") { String.format("%02X", it.toInt() and 0xff) }

    fun write(pages: List<PageInput>, title: String, pageSize: PageSize = PageSize.AUTO, password: String? = null) {
        require(pages.isNotEmpty())
        val id = ByteArray(16).also { SecureRandom().nextBytes(it) }
        if (!password.isNullOrEmpty()) crypt = Encryption(password, id)

        write("%PDF-1.4\n%âãÏÓ\n")
        // 1 catalogue, 2 arbre des pages, 3 police, 4 infos, 5 chiffrement, puis 3 objets par page.
        val pageObj = { i: Int -> 6 + i * 3 }
        beginObj(1); write("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        beginObj(2)
        write("<< /Type /Pages /Count ${pages.size} /Kids [${pages.indices.joinToString(" ") { "${pageObj(it)} 0 R" }}] >>\nendobj\n")
        beginObj(3); write("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n")
        beginObj(4); write("<< /Title ${hex(4, utf16(title))} /Producer ${hex(4, utf16("ScanDoc"))} >>\nendobj\n")
        beginObj(5); write((crypt?.dictionary() ?: "<< >>") + "\nendobj\n")

        pages.forEachIndexed { i, p ->
            val portrait = p.pixelWidth <= p.pixelHeight
            // Dimensions de la page et position de l'image (centrée, proportions conservées).
            val pw: Float; val ph: Float; val iw: Float; val ih: Float
            if (pageSize == PageSize.AUTO) {
                pw = if (portrait) 595f else 842f; ph = pw * p.pixelHeight / p.pixelWidth; iw = pw; ih = ph
            } else {
                val short = min(pageSize.w, pageSize.h); val long = max(pageSize.w, pageSize.h)
                pw = if (portrait) short else long; ph = if (portrait) long else short
                val margin = min(pw, ph) * 0.04f
                val s = min((pw - 2 * margin) / p.pixelWidth, (ph - 2 * margin) / p.pixelHeight)
                iw = p.pixelWidth * s; ih = p.pixelHeight * s
            }
            val ix = (pw - iw) / 2; val iy = (ph - ih) / 2

            val content = buildString {
                append("q ${f(iw)} 0 0 ${f(ih)} ${f(ix)} ${f(iy)} cm /Im0 Do Q\n")
                p.ocr?.lines?.filter { it.text.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let { lines ->
                    append("BT 3 Tr\n")
                    for (l in lines) {
                        val boxW = (l.right - l.left) * iw
                        val boxH = (l.bottom - l.top) * ih
                        if (boxW <= 0f || boxH <= 0f) continue
                        val size = max(1f, boxH * 0.85f)
                        val natural = textWidth(l.text) * size / 1000f
                        val scale = if (natural > 0f) (100f * boxW / natural).coerceIn(10f, 500f) else 100f
                        val x = ix + l.left * iw
                        val y = iy + ih - l.bottom * ih + boxH * 0.18f
                        append("/F1 ${f(size)} Tf ${f(scale)} Tz 1 0 0 1 ${f(x)} ${f(y)} Tm ")
                        append(winAnsi(l.text).joinToString("", "<", ">") { String.format("%02X", it.toInt() and 0xff) })
                        append(" Tj\n")
                    }
                    append("ET\n")
                }
            }.toByteArray(Charsets.ISO_8859_1)

            beginObj(pageObj(i))
            write("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${f(pw)} ${f(ph)}] " +
                "/Resources << /XObject << /Im0 ${pageObj(i) + 2} 0 R >> /Font << /F1 3 0 R >> >> /Contents ${pageObj(i) + 1} 0 R >>\nendobj\n")
            stream(pageObj(i) + 1, "", content)
            stream(pageObj(i) + 2, "/Type /XObject /Subtype /Image /Width ${p.pixelWidth} /Height ${p.pixelHeight} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode", p.jpeg)
        }

        val xref = offset
        write("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { write(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", it)) }
        val idHex = id.joinToString("") { String.format("%02X", it.toInt() and 0xff) }
        val enc = if (crypt != null) " /Encrypt 5 0 R" else ""
        write("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R /Info 4 0 R /ID [<$idHex> <$idHex>]$enc >>\nstartxref\n$xref\n%%EOF\n")
        out.flush()
    }

    /** Gestionnaire de sécurité standard PDF, révision 3 (RC4 128 bits), toutes permissions accordées une fois ouvert. */
    internal class Encryption(userPassword: String, private val id: ByteArray) {
        private val permissions = -4
        private val ownerPassword = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val o: ByteArray
        val u: ByteArray
        val key: ByteArray

        init {
            // Clé « propriétaire » (O) : algorithme 3.
            var h = md5(pad(ownerPassword))
            repeat(50) { h = md5(h) }
            var data = rc4(h, pad(userPassword))
            for (i in 1..19) data = rc4(ByteArray(16) { (h[it].toInt() xor i).toByte() }, data)
            o = data
            // Clé de chiffrement : algorithme 2.
            var k = md5(pad(userPassword) + o + byteArrayOf(permissions.toByte(), (permissions shr 8).toByte(), (permissions shr 16).toByte(), (permissions shr 24).toByte()) + id)
            repeat(50) { k = md5(k.copyOf(16)) }
            key = k.copyOf(16)
            // Valeur utilisateur (U) : algorithme 5.
            var x = rc4(key, md5(PAD + id))
            for (i in 1..19) x = rc4(ByteArray(16) { (key[it].toInt() xor i).toByte() }, x)
            u = x + ByteArray(16)
        }

        fun dictionary(): String {
            fun h(b: ByteArray) = b.joinToString("", "<", ">") { String.format("%02X", it.toInt() and 0xff) }
            return "<< /Filter /Standard /V 2 /R 3 /Length 128 /P $permissions /O ${h(o)} /U ${h(u)} >>"
        }

        fun encrypt(objNum: Int, data: ByteArray): ByteArray {
            val objKey = md5(key + byteArrayOf(objNum.toByte(), (objNum shr 8).toByte(), (objNum shr 16).toByte(), 0, 0)).copyOf(16)
            return rc4(objKey, data)
        }

        companion object {
            val PAD = byteArrayOf(
                0x28, 0xBF.toByte(), 0x4E, 0x5E, 0x4E, 0x75, 0x8A.toByte(), 0x41, 0x64, 0x00, 0x4E, 0x56, 0xFF.toByte(), 0xFA.toByte(), 0x01, 0x08,
                0x2E, 0x2E, 0x00, 0xB6.toByte(), 0xD0.toByte(), 0x68, 0x3E, 0x80.toByte(), 0x2F, 0x0C, 0xA9.toByte(), 0xFE.toByte(), 0x64, 0x53, 0x69, 0x7A,
            )

            fun pad(pw: String): ByteArray {
                val b = winAnsi(pw).take(32).toByteArray()
                return b + PAD.copyOf(32 - b.size)
            }

            fun md5(b: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(b)

            fun rc4(key: ByteArray, data: ByteArray): ByteArray {
                val s = IntArray(256) { it }
                var j = 0
                for (i in 0 until 256) {
                    j = (j + s[i] + (key[i % key.size].toInt() and 0xff)) and 0xff
                    val t = s[i]; s[i] = s[j]; s[j] = t
                }
                val out = ByteArray(data.size)
                var a = 0; var b = 0
                for (n in data.indices) {
                    a = (a + 1) and 0xff
                    b = (b + s[a]) and 0xff
                    val t = s[a]; s[a] = s[b]; s[b] = t
                    out[n] = (data[n].toInt() xor s[(s[a] + s[b]) and 0xff]).toByte()
                }
                return out
            }
        }
    }

    companion object {
        private fun f(v: Float) = String.format(java.util.Locale.ROOT, "%.2f", v)

        fun utf16(s: String) = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + s.toByteArray(Charsets.UTF_16BE)

        private val SPECIAL = mapOf(
            '€' to 0x80, '‚' to 0x82, 'ƒ' to 0x83, '„' to 0x84, '…' to 0x85, '†' to 0x86, '‡' to 0x87, 'ˆ' to 0x88, '‰' to 0x89,
            'Š' to 0x8A, '‹' to 0x8B, 'Œ' to 0x8C, 'Ž' to 0x8E, '‘' to 0x91, '’' to 0x92, '“' to 0x93, '”' to 0x94, '•' to 0x95,
            '–' to 0x96, '—' to 0x97, '˜' to 0x98, '™' to 0x99, 'š' to 0x9A, '›' to 0x9B, 'œ' to 0x9C, 'ž' to 0x9E, 'Ÿ' to 0x9F,
        )

        fun winAnsi(s: String): ByteArray = ByteArray(s.length) { i ->
            val c = s[i]
            when {
                c.code in 0x20..0x7E || c.code in 0xA0..0xFF -> c.code.toByte()
                SPECIAL.containsKey(c) -> SPECIAL.getValue(c).toByte()
                else -> '?'.code.toByte()
            }
        }

        // Chasses de Helvetica (1/1000 em) pour les caractères 32..126.
        private val WIDTHS = intArrayOf(
            278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
            1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
            333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
            556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584,
        )

        fun textWidth(s: String): Float = s.sumOf { c -> (if (c.code in 32..126) WIDTHS[c.code - 32] else 556).toDouble() }.toFloat()
    }
}
