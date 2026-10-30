package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.viewmodel.MealPlannerSupport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDateSelector(value: String, onDate: (String) -> Unit, tag: String, showDays: Boolean = true) {
    val date = MealPlannerSupport.dateForDay(value) ?: LocalDate.now()
    var calendar by remember { mutableStateOf(false) }
    if (showDays) Text("Data: ${MealPlannerSupport.planDateLabel(date.toString())}", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { onDate(date.minusWeeks(1).toString()) }, modifier = Modifier.testTag("${tag}_previous_week")) { Text(if (showDays) "← Tydzień" else "←") }
        TextButton(onClick = { onDate(LocalDate.now().toString()) }) { Text("Dziś") }
        if (!showDays) TextButton(onClick = { calendar = true }, modifier = Modifier.testTag("${tag}_calendar")) { Text("Kalendarz") }
        TextButton(onClick = { onDate(date.plusWeeks(1).toString()) }, modifier = Modifier.testTag("${tag}_next_week")) { Text(if (showDays) "Tydzień →" else "→") }
    }
    if (showDays) OutlinedButton(onClick = { calendar = true }, modifier = Modifier.testTag("${tag}_calendar")) { Text("Wybierz datę z kalendarza") }
    if (showDays) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(MealPlannerSupport.weekDays(date)) { day ->
            val dayDate = MealPlannerSupport.dateForDay(day.canonicalName, date)!!
            FilterChip(selected = date == dayDate, onClick = { onDate(dayDate.toString()) },
                label = { Text(day.chipLabelWithDate) }, modifier = Modifier.testTag("${tag}_${day.canonicalName}"))
        }
    }
    if (calendar) {
        // Material DatePicker uses UTC midnight, independent of the phone's timezone.
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { calendar = false }, confirmButton = {
            TextButton(enabled = state.selectedDateMillis != null, onClick = {
                state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                calendar = false
            }) { Text("Wybierz") }
        }, dismissButton = { TextButton(onClick = { calendar = false }) { Text("Anuluj") } }) { DatePicker(state) }
    }
}
