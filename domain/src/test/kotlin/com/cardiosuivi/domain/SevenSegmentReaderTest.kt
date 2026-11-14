package com.cardiosuivi.domain

import com.cardiosuivi.domain.ocr.GrayImage
import com.cardiosuivi.domain.ocr.SevenSegmentReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests du lecteur 7 segments sur des écrans de tensiomètre SYNTHÉTIQUES : chiffres à segments séparés,
 * fond en dégradé, bruit, flou, cadre de l'écran et libellés parasites.
 */
class SevenSegmentReaderTest {

    private val segs = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abdeg", '3' to "abcdg", '4' to "bcfg",
        '5' to "acdfg", '6' to "acdefg", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg",
    )

    private class Canvas(val w: Int, val h: Int, bg: (Int, Int) -> Int) {
        val px = IntArray(w * h) { i -> bg(i % w, i / w) }
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, v: Int) {
            for (y in maxOf(0, y0) until minOf(h, y1)) for (x in maxOf(0, x0) until minOf(w, x1)) px[y * w + x] = v
        }
        fun blur() {
            val c = px.copyOf()
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                var s = 0
                for (dy in -1..1) for (dx in -1..1) s += c[(y + dy) * w + x + dx]
                px[y * w + x] = s / 9
            }
        }
        fun noise(r: Random, amp: Int) { for (i in px.indices) px[i] = (px[i] + r.nextInt(-amp, amp + 1)).coerceIn(0, 255) }
        fun image() = GrayImage(w, h, px)
    }

    /** Dessine un chiffre 7 segments (segments séparés par un petit espace, comme sur un LCD). */
    private fun Canvas.digit(ch: Char, x: Int, y: Int, dw: Int, dh: Int, ink: Int) {
        val t = maxOf(2, dh / 11) // épaisseur
        val g = maxOf(1, t / 2) // espace entre segments
        val mid = y + dh / 2
        for (s in segs.getValue(ch)) when (s) {
            'a' -> rect(x + t + g, y, x + dw - t - g, y + t, ink)
            'g' -> rect(x + t + g, mid - t / 2, x + dw - t - g, mid + t - t / 2, ink)
            'd' -> rect(x + t + g, y + dh - t, x + dw - t - g, y + dh, ink)
            'f' -> rect(x, y + t + g, x + t, mid - g, ink)
            'e' -> rect(x, mid + g, x + t, y + dh - t - g, ink)
            'b' -> rect(x + dw - t, y + t + g, x + dw, mid - g, ink)
            'c' -> rect(x + dw - t, mid + g, x + dw, y + dh - t - g, ink)
        }
    }

    /** Nombre justifié à droite dans un champ de 3 chiffres. */
    private fun Canvas.number(n: Int, rightX: Int, y: Int, dw: Int, dh: Int, ink: Int) {
        val s = n.toString()
        val step = (dw * 1.35).toInt()
        s.reversed().forEachIndexed { i, c -> digit(c, rightX - (i + 1) * step + (step - dw), y, dw, dh, ink) }
    }

    private fun screen(sys: Int, dia: Int, pul: Int, seed: Int, scale: Double = 1.0, withClutter: Boolean = true): GrayImage {
        val r = Random(seed)
        val w = (420 * scale).toInt(); val h = (560 * scale).toInt()
        val c = Canvas(w, h) { x, y -> (200 - y * 40 / h + x * 20 / w).coerceIn(0, 255) } // dégradé (reflet)
        val ink = 40 + r.nextInt(30)
        val dw = (70 * scale).toInt(); val dh = (120 * scale).toInt()
        val right = (380 * scale).toInt()
        c.number(sys, right, (60 * scale).toInt(), dw, dh, ink)
        c.number(dia, right, (220 * scale).toInt(), dw, dh, ink)
        c.number(pul, right, (390 * scale).toInt(), (dw * 0.55).toInt(), (dh * 0.55).toInt(), ink)
        if (withClutter) {
            // cadre de l'écran LCD
            c.rect((20 * scale).toInt(), (30 * scale).toInt(), (400 * scale).toInt(), (34 * scale).toInt(), ink)
            c.rect((20 * scale).toInt(), (500 * scale).toInt(), (400 * scale).toInt(), (504 * scale).toInt(), ink)
            // libellés « SYS », « DIA », icône cœur (petits blocs)
            for (yy in listOf(80, 240, 400)) c.rect((40 * scale).toInt(), (yy * scale).toInt(), (75 * scale).toInt(), ((yy + 14) * scale).toInt(), ink + 20)
            c.rect((150 * scale).toInt(), (410 * scale).toInt(), (170 * scale).toInt(), (428 * scale).toInt(), ink)
        }
        c.blur()
        c.noise(r, 12)
        return c.image()
    }

    @Test
    fun readsTypicalScreen() {
        val res = SevenSegmentReader.read(screen(125, 85, 78, seed = 1)).reading
        assertEquals(125, res.systolic.value)
        assertEquals(85, res.diastolic.value)
        assertEquals(78, res.pulse.value)
    }

    @Test
    fun readsAllDigitsAcrossManyScreensAndNeverIsConfidentlyWrong() {
        val r = Random(42)
        var exact = 0
        var confidentlyWrong = 0
        val n = 40
        repeat(n) { i ->
            val sys = r.nextInt(95, 200)
            val dia = r.nextInt(50, minOf(sys - 15, 125))
            val pul = r.nextInt(45, 130)
            val scale = listOf(0.8, 1.0, 1.6, 2.5)[i % 4]
            val res = SevenSegmentReader.read(screen(sys, dia, pul, seed = i, scale = scale)).reading
            if (res.systolic.value == sys && res.diastolic.value == dia && res.pulse.value == pul) exact++
            for ((f, truth) in listOf(res.systolic to sys, res.diastolic to dia, res.pulse to pul)) {
                if (f.value != null && f.value != truth && !f.uncertain) confidentlyWrong++
            }
        }
        assertTrue("Lectures exactes : $exact/$n", exact >= (n * 0.9).toInt())
        assertEquals("Aucune valeur fausse ne doit être présentée comme sûre", 0, confidentlyWrong)
    }

    @Test
    fun everyDigitIsDecoded() {
        // Couvre les 10 chiffres, y compris 1, 4 et 7 qui n'utilisent pas tous les segments.
        listOf(Triple(110, 74, 69), Triple(147, 98, 52), Triple(203, 136, 80)).forEachIndexed { i, (s, d, p) ->
            val res = SevenSegmentReader.read(screen(s, d, p, seed = 100 + i)).reading
            assertEquals(s, res.systolic.value); assertEquals(d, res.diastolic.value); assertEquals(p, res.pulse.value)
        }
    }

    @Test
    fun blankOrNoisyImageGivesNoValue() {
        val c = Canvas(400, 500) { _, _ -> 190 }
        c.noise(Random(3), 25)
        val res = SevenSegmentReader.read(c.image()).reading
        assertNull(res.systolic.value)
        assertNull(res.diastolic.value)
    }
}
