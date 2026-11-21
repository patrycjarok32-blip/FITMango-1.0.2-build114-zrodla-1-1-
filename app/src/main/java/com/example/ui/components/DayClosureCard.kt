package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.*
import java.time.LocalDate

/** On-open reminder: explicit confirmation only, never automatic approval. */
@Composable
fun DayClosureCard(viewModel: DietaViewModel) {
    val days by viewModel.habits.days.collectAsStateWithLifecycle()
    val confirmations by viewModel.healthConnect.weeklyEnergy.confirmations.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val pending = DayClosureReminder.pendingYesterday(today, days, confirmations)
    var dismissedDate by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    if (pending != null && dismissedDate != pending.date) {
        Card(Modifier.fillMaxWidth().testTag("yesterday_closure_card")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zatwierdź wczorajszy dzień", style = MaterialTheme.typography.titleMedium)
                Text("${pending.date} • ${pending.calories} / ${pending.targetCalories} kcal")
                Text("Potwierdź, że zapisałaś wszystkie posiłki. Szacunkowe makro nie blokuje zatwierdzenia.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val day = days.firstOrNull { it.date == pending.date }
                        error = runCatching {
                            requireNotNull(day)
                            viewModel.healthConnect.weeklyEnergy.confirmFood(day, today)
                        }.exceptionOrNull()?.message
                    }, modifier = Modifier.testTag("confirm_yesterday_food")) { Text("Zatwierdź dzień") }
                    TextButton(onClick = { dismissedDate = pending.date }) { Text("Później") }
                }
            }
        }
    }
}
