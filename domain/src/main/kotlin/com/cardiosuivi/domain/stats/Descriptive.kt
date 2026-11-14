package com.cardiosuivi.domain.stats

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Statistiques descriptives élémentaires, déterministes et reproductibles.
 * Toutes les fonctions retournent null lorsque l'effectif est insuffisant : aucune valeur n'est extrapolée.
 */
object Descriptive {

    fun mean(values: List<Double>): Double? = if (values.isEmpty()) null else values.sum() / values.size

    fun meanInt(values: List<Int>): Double? = mean(values.map { it.toDouble() })

    /** Écart-type d'échantillon (dénominateur n − 1). Null si n < 2. */
    fun sampleSd(values: List<Double>): Double? {
        if (values.size < 2) return null
        val m = values.sum() / values.size
        val ss = values.sumOf { (it - m) * (it - m) }
        return sqrt(ss / (values.size - 1))
    }

    fun sampleSdInt(values: List<Int>): Double? = sampleSd(values.map { it.toDouble() })

    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
    }

    /** Écart absolu médian (MAD, non normalisé). */
    fun mad(values: List<Double>): Double? {
        val med = median(values) ?: return null
        return median(values.map { abs(it - med) })
    }

    /** Coefficient de variation en % (SD / moyenne × 100). */
    fun coefficientOfVariation(values: List<Double>): Double? {
        val m = mean(values) ?: return null
        val sd = sampleSd(values) ?: return null
        if (m == 0.0) return null
        return sd / m * 100.0
    }

    /**
     * Variabilité réelle moyenne (ARV, « average real variability ») :
     * moyenne des valeurs absolues des différences entre valeurs successives. Null si n < 2.
     */
    fun averageRealVariability(values: List<Double>): Double? {
        if (values.size < 2) return null
        return values.zipWithNext { a, b -> abs(b - a) }.average()
    }

    data class Regression(
        /** Pente (unité de y par unité de x). */
        val slope: Double,
        val intercept: Double,
        /** Coefficient de détermination. */
        val r2: Double,
        val n: Int,
    )

    /** Régression linéaire par moindres carrés ordinaires. Null si n < 3 ou si x est constant. */
    fun linearRegression(x: List<Double>, y: List<Double>): Regression? {
        require(x.size == y.size) { "x et y doivent avoir la même taille" }
        val n = x.size
        if (n < 3) return null
        val mx = x.average()
        val my = y.average()
        var sxx = 0.0
        var sxy = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            val dx = x[i] - mx
            val dy = y[i] - my
            sxx += dx * dx
            sxy += dx * dy
            syy += dy * dy
        }
        if (sxx == 0.0) return null
        val slope = sxy / sxx
        val intercept = my - slope * mx
        val r2 = if (syy == 0.0) 1.0 else (sxy * sxy) / (sxx * syy)
        return Regression(slope, intercept, r2, n)
    }
}

/** Résumé d'une série de valeurs. */
data class SeriesSummary(
    val n: Int,
    val mean: Double?,
    val min: Int?,
    val max: Int?,
    val sd: Double?,
) {
    val range: Int? get() = if (min != null && max != null) max - min else null

    companion object {
        fun of(values: List<Int>): SeriesSummary = SeriesSummary(
            n = values.size,
            mean = Descriptive.meanInt(values),
            min = values.minOrNull(),
            max = values.maxOrNull(),
            sd = Descriptive.sampleSdInt(values),
        )

        fun ofDoubles(values: List<Double>): SeriesSummary = SeriesSummary(
            n = values.size,
            mean = Descriptive.mean(values),
            min = values.minOrNull()?.let { kotlin.math.round(it).toInt() },
            max = values.maxOrNull()?.let { kotlin.math.round(it).toInt() },
            sd = Descriptive.sampleSd(values),
        )
    }
}
