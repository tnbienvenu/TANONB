package com.cardiosuivi.domain

import com.cardiosuivi.domain.ocr.AssignmentMethod
import com.cardiosuivi.domain.ocr.OcrElement
import com.cardiosuivi.domain.ocr.OcrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du moteur d'analyse OCR à partir d'éléments de texte simulant la sortie d'ML Kit
 * pour des dispositions d'écran courantes. (Les tests sur photos réelles se font sur appareil.)
 */
class OcrParserTest {

    private fun e(text: String, l: Float, t: Float, w: Float, h: Float) = OcrElement(text, l, t, l + w, t + h)

    @Test
    fun labelledVerticalLayout() {
        val els = listOf(
            e("SYS", 10f, 20f, 40f, 15f), e("mmHg", 10f, 40f, 40f, 10f), e("128", 80f, 10f, 120f, 60f),
            e("DIA", 10f, 100f, 40f, 15f), e("mmHg", 10f, 120f, 40f, 10f), e("82", 100f, 90f, 100f, 60f),
            e("PUL", 10f, 180f, 40f, 15f), e("/min", 10f, 200f, 40f, 10f), e("67", 140f, 180f, 60f, 35f),
        )
        val r = OcrParser.parse(els)
        assertEquals(AssignmentMethod.LABELS, r.method)
        assertEquals(128, r.systolic.value)
        assertEquals(82, r.diastolic.value)
        assertEquals(67, r.pulse.value)
        assertFalse(r.systolic.uncertain)
    }

    @Test
    fun unlabelledLayoutUsesPositionAndWarns() {
        val els = listOf(
            e("10:42", 0f, 0f, 50f, 12f), // heure, ignorée
            e("135", 50f, 30f, 120f, 70f),
            e("88", 80f, 120f, 90f, 70f),
            e("72", 120f, 210f, 50f, 35f),
        )
        val r = OcrParser.parse(els)
        assertEquals(AssignmentMethod.LAYOUT, r.method)
        assertEquals(135, r.systolic.value)
        assertEquals(88, r.diastolic.value)
        assertEquals(72, r.pulse.value)
        assertTrue(r.warnings.any { it.contains("position") })
        assertTrue(r.systolic.confidence < 0.9)
    }

    @Test
    fun sevenSegmentConfusionsAreCorrectedButFlagged() {
        val els = listOf(
            e("SYS", 0f, 10f, 30f, 12f), e("I3B", 60f, 0f, 100f, 60f),
            e("DIA", 0f, 90f, 30f, 12f), e("8O", 80f, 80f, 80f, 60f),
            e("PUL", 0f, 170f, 30f, 12f), e("7Z", 100f, 165f, 50f, 30f),
        )
        val r = OcrParser.parse(els)
        assertEquals(138, r.systolic.value)
        assertEquals(80, r.diastolic.value)
        assertEquals(72, r.pulse.value)
        assertTrue(r.systolic.reason!!.contains("ambigu"))
        assertTrue(r.systolic.confidence < 0.9)
    }

    @Test
    fun sideBySideRowIsReadLeftToRight() {
        val els = listOf(
            e("142", 0f, 0f, 100f, 60f),
            e("91", 150f, 2f, 80f, 60f),
            e("66", 60f, 100f, 50f, 30f),
        )
        val r = OcrParser.parse(els)
        assertEquals(142, r.systolic.value)
        assertEquals(91, r.diastolic.value)
        assertEquals(66, r.pulse.value)
    }

    @Test
    fun invertedValuesAreFlaggedNotSwapped() {
        val els = listOf(e("80", 0f, 0f, 100f, 60f), e("125", 0f, 80f, 100f, 60f))
        val r = OcrParser.parse(els)
        assertEquals(80, r.systolic.value)
        assertEquals(125, r.diastolic.value)
        assertTrue(r.systolic.uncertain)
        assertTrue(r.diastolic.uncertain)
        assertTrue(r.warnings.any { it.contains("intervertis") })
    }

    @Test
    fun nothingReadableNeverInventsValues() {
        val r = OcrParser.parse(listOf(e("OMRON", 0f, 0f, 100f, 20f), e("mmHg", 0f, 30f, 50f, 10f)))
        assertNull(r.systolic.value)
        assertNull(r.diastolic.value)
        assertNull(r.pulse.value)
        assertTrue(r.anyUncertain)
        assertEquals(AssignmentMethod.NONE, r.method)
    }

    @Test
    fun missingPulseStaysEmpty() {
        val r = OcrParser.parse(listOf(e("SYS", 0f, 0f, 30f, 10f), e("120", 50f, 0f, 90f, 50f), e("DIA", 0f, 70f, 30f, 10f), e("75", 60f, 65f, 70f, 50f)))
        assertEquals(120, r.systolic.value)
        assertEquals(75, r.diastolic.value)
        assertNull(r.pulse.value)
        assertTrue(r.warnings.any { it.contains("Fréquence cardiaque non lue") })
    }

    @Test
    fun implausibleValueIsNotProposed() {
        val r = OcrParser.parse(listOf(e("SYS", 0f, 0f, 30f, 10f), e("295", 50f, 0f, 90f, 50f), e("DIA", 0f, 70f, 30f, 10f), e("75", 60f, 65f, 70f, 50f)))
        assertNull(r.systolic.value)
        assertTrue(r.systolic.alternatives.contains(295))
    }

    @Test
    fun parseNumberRejectsWordsDatesAndTimes() {
        assertNull(OcrParser.parseNumber("SYS"))
        assertNull(OcrParser.parseNumber("12:30"))
        assertNull(OcrParser.parseNumber("11/14"))
        assertNull(OcrParser.parseNumber("2026"))
        assertNull(OcrParser.parseNumber("5"))
        assertEquals(118 to false, OcrParser.parseNumber("118"))
        assertEquals(108 to true, OcrParser.parseNumber("1O8"))
    }

    @Test
    fun consensusAgreementAndDisagreement() {
        fun reading(s: String, d: String, p: String) = OcrParser.parse(
            listOf(e("SYS", 0f, 0f, 30f, 10f), e(s, 50f, 0f, 90f, 50f), e("DIA", 0f, 70f, 30f, 10f), e(d, 60f, 65f, 70f, 50f), e("PUL", 0f, 140f, 30f, 10f), e(p, 60f, 135f, 40f, 30f)),
        )
        val agree = OcrParser.consensus(listOf(reading("131", "84", "70"), reading("131", "84", "70"), reading("131", "84", "70")))
        assertEquals(131, agree.systolic.value)
        assertFalse(agree.systolic.uncertain)

        val disagree = OcrParser.consensus(listOf(reading("131", "84", "70"), reading("137", "84", "70"), reading("131", "84", "70")))
        assertEquals(131, disagree.systolic.value)
        assertTrue(disagree.systolic.uncertain)
        assertTrue(disagree.systolic.alternatives.contains(137))
        assertFalse(disagree.diastolic.uncertain)
    }
}
