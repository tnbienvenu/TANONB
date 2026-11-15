package com.cardiosuivi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardiosuivi.app.ui.theme.Spacing
import com.cardiosuivi.domain.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class PeriodPreset(val label: String, val days: Long?) {
    D7("7 jours", 7), D30("30 jours", 30), D90("90 jours", 90), YEAR("Année", 365), ALL("Tout", null), CUSTOM("Personnalisée", null)
}

data class PeriodChoice(val preset: PeriodPreset, val customFrom: LocalDate? = null, val customTo: LocalDate? = null) {
    fun range(today: LocalDate, earliest: LocalDate?): Pair<LocalDate, LocalDate> = when (preset) {
        PeriodPreset.ALL -> (earliest ?: today) to today
        PeriodPreset.CUSTOM -> (customFrom ?: today.minusDays(29)) to (customTo ?: today)
        else -> today.minusDays(preset.days!! - 1) to today
    }
}

private val segments = listOf(PeriodPreset.D7, PeriodPreset.D30, PeriodPreset.D90, PeriodPreset.YEAR, PeriodPreset.ALL)

/** Sélecteur de période : contrôle segmenté (7 j | 30 j | 90 j | Année | Tout) et période personnalisée. */
@Composable
fun PeriodSelector(choice: PeriodChoice, onChange: (PeriodChoice) -> Unit, today: LocalDate, earliest: LocalDate?, showCustom: Boolean = true) {
    var picking by remember { mutableStateOf(false) }
    val (from, to) = choice.range(today, earliest)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SegmentedControl(segments, choice.preset, { it.label }, { onChange(PeriodChoice(it)) })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (choice.preset == PeriodPreset.CUSTOM) "Période personnalisée : " else "") + "du ${Fmt.date(from)} au ${Fmt.date(to)}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            if (showCustom) TextButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp)); Text(" Personnalisée")
            }
        }
    }
    if (picking) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(enabled = state.selectedStartDateMillis != null, onClick = {
                    fun d(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    val s = d(state.selectedStartDateMillis!!)
                    val e = state.selectedEndDateMillis?.let { d(it) } ?: s
                    onChange(PeriodChoice(PeriodPreset.CUSTOM, s, e))
                    picking = false
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Annuler") } },
        ) {
            DateRangePicker(state, modifier = Modifier.height(500.dp), title = { Text("Période personnalisée", modifier = Modifier) })
        }
    }
}
