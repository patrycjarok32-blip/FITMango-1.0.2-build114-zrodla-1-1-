package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.*

@Composable
fun ChangeHistoryCard(viewModel: DietaViewModel) {
    val meals by viewModel.mealChangeHistory.collectAsStateWithLifecycle()
    val measurements by MeasurementChangeHistory.entries.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val time = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    CollapsibleMangoCard("Historia zmian (${meals.size + measurements.size})", "change_history") {
        Text("Ostatnie 10 zmian posiłków i 10 zmian pomiarów z bieżącego uruchomienia aplikacji. Najpierw cofaj nowsze zmiany tego samego wpisu. Nowsze dane są chronione przed nadpisaniem.", style = MaterialTheme.typography.bodySmall)
        meals.forEach { entry -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("${time.format(java.util.Date(entry.time))} • ${entry.label}", Modifier.weight(1f))
            TextButton(enabled = !busy, onClick = { viewModel.undoLastMealChange(entry.id) }) { Text("Cofnij") }
        } }
        measurements.forEach { entry -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("${time.format(java.util.Date(entry.time))} • ${entry.label}", Modifier.weight(1f))
            TextButton(enabled = !busy, onClick = { busy = true; viewModel.undoMeasurementChange(entry.id) { message -> busy = false; error = message } }) { Text("Przywróć") }
        } }
        val mealError by viewModel.mealUndoMessage.collectAsStateWithLifecycle()
        (error ?: mealError)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (meals.isEmpty() && measurements.isEmpty()) Text("Brak zmian do cofnięcia w tej sesji.")
    }
}
