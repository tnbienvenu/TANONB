package com.cardiosuivi.domain.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatage déterministe (locale française) des nombres et dates affichés. */
object Fmt {
    val LOCALE: Locale = Locale.FRANCE

    fun d0(x: Double?): String = x?.let { String.format(LOCALE, "%.0f", it) } ?: "—"
    fun d1(x: Double?): String = x?.let { String.format(LOCALE, "%.1f", it) } ?: "—"
    fun signed1(x: Double?): String = x?.let { String.format(LOCALE, "%+.1f", it) } ?: "—"
    fun pct(x: Double?): String = x?.let { String.format(LOCALE, "%.0f %%", it * 100) } ?: "—"
    fun bp(sys: Double?, dia: Double?): String = "${d0(sys)}/${d0(dia)}"

    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE)
    private val DATE_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", LOCALE)
    fun date(d: LocalDate): String = d.format(DATE)
    fun dateLong(d: LocalDate): String = d.format(DATE_LONG)
}
