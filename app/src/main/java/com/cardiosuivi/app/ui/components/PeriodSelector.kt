package com.cardiosuivi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardiosuivi.domain.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class PeriodPreset(val label: String, val days: Long?) {
    D7("7 jours", 7), D30("30 jours", 30), D90("90 jours", 90), ALL("Tout", null), CUSTOM("Personnalisée", null)
}

data class PeriodChoice(val preset: PeriodPreset, val customFrom: LocalDate? = null, val customTo: LocalDate? = null) {
    fun range(today: LocalDate, earliest: LocalDate?): Pair<LocalDate, LocalDate> = when (preset) {
        PeriodPreset.ALL -> (earliest ?: today) to today
        PeriodPreset.CUSTOM -> (customFrom ?: today.minusDays(29)) to (customTo ?: today)
        else -> today.minusDays(preset.days!! - 1) to today
    }
}

@Composable
fun PeriodSelector(choice: PeriodChoice, onChange: (PeriodChoice) -> Unit, today: LocalDate, earliest: LocalDate?) {
    var picking by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PeriodPreset.entries.forEach { p ->
            FilterChip(
                selected = choice.preset == p,
                onClick = { if (p == PeriodPreset.CUSTOM) picking = true else onChange(PeriodChoice(p)) },
                label = { Text(p.label) },
            )
        }
    }
    val (from, to) = choice.range(today, earliest)
    Text("Du ${Fmt.date(from)} au ${Fmt.date(to)}", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
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
