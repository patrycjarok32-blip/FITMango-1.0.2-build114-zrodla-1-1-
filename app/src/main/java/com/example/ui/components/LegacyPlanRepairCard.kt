package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.MealPlanEntity
import com.example.viewmodel.DietaViewModel
import com.example.viewmodel.LegacyPlanDates
import com.example.viewmodel.MealPlannerSupport
import java.time.LocalDate

@Composable
fun LegacyPlanRepairCard(meals: List<MealPlanEntity>, viewModel: DietaViewModel, onWeekSelected: (String) -> Unit) {
    val undated = remember(meals) { meals.filter { LegacyPlanDates.needsWeek(it) } }
    if (undated.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    var reference by remember { mutableStateOf(LocalDate.now().minusWeeks(1).toString()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag("legacy_plan_repair")) {
        Text("Starsze wpisy bez daty (${undated.size}) — przypisz tydzień")
    }
    if (open) MangoEditSheet(onDismissRequest = { open = false }, dismissEnabled = !busy,
        title = { Text("Przywróć właściwy tydzień") }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Starsze wersje zapisały nazwę dnia bez daty. Te wpisy nie są już przypisywane do bieżącego tygodnia. Wybierz tydzień, do którego należą.")
                PlanDateSelector(reference, { reference = it }, "legacy_week", showDays = false)
                val monday = MealPlannerSupport.dateForDay("Poniedziałek", LocalDate.parse(reference))!!
                Text("Wybrany tydzień: $monday — ${monday.plusDays(6)}")
                Text("Przypiszesz ${undated.size} wpisów. Składniki, gramy i zapis zjedzenia pozostają zachowane.")
                undated.forEach { Text("${it.dayOfWeek} • ${it.recipeTitle}", style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it) }
            }
        }, confirmButton = {
            Button(enabled = !busy, onClick = {
                busy = true; error = null
                viewModel.assignLegacyPlanWeek(undated.map { it.id }, reference) { message ->
                    busy = false; error = message
                    if (message == null) { open = false; onWeekSelected(reference) }
                }
            }, modifier = Modifier.testTag("confirm_legacy_week")) { Text(if (busy) "Zapisywanie…" else "Przypisz do tego tygodnia") }
        }, dismissButton = { TextButton(enabled = !busy, onClick = { open = false }) { Text("Anuluj") } })
}
