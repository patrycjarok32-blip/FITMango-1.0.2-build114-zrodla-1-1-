package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.viewmodel.*

@Composable
fun UniversalNutritionRepairPanel(viewModel: DietaViewModel, targets: List<NutritionRepairTarget>) {
    val state by viewModel.nutritionRepair.collectAsState()
    val context = LocalContext.current
    val revision by viewModel.usability.revision.collectAsState()
    val prefs = remember(context) { context.getSharedPreferences("FITMangoUsability", 0) }
    var online by remember { mutableStateOf(prefs.getBoolean("nutritionRepairOnline", true)) }
    var issueLimit by remember(state.total) { mutableIntStateOf(5) }
    var historyOpen by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf<NutritionRepairIssue?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val history = remember(revision) { NutritionRepairSources.history(context) }
    Text("Wszystkie przepisy i jadłospis • ${targets.size} produktów z brakami kalorii lub makro", style = MaterialTheme.typography.titleSmall)
    Text("Sprawdzamy każdy produkt z brakami, także spoza lokalnej tabeli. Znane wartości i ilości pozostają bez zmian. Brak ilości nie blokuje zapisania danych na 100 g/ml. Lista obejmuje stan z chwili uruchomienia; nowe wpisy sprawdź ponownie.", style = MaterialTheme.typography.bodySmall)
    Row {
        Checkbox(online, onCheckedChange = { online = it; prefs.edit().putBoolean("nutritionRepairOnline", it).apply() }, enabled = !state.running)
        Column(Modifier.padding(top = 8.dp)) {
            Text("Szukaj także w Open Food Facts")
            Text("Do bazy wysyłamy tylko nazwy lub kody produktów. Bez Gemini. Wyniki zależą od dostępności danych; różne produkty wymagają wyboru.", style = MaterialTheme.typography.bodySmall)
        }
    }
    Button(onClick = { viewModel.repairAllMissingNutrition(online) }, enabled = !state.running && !saving && targets.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("repair_all_nutrition")) {
        Text(if (state.running) "Uzupełniam wszystkie braki…" else "Uzupełnij wszystkie braki")
    }
    if (state.running) {
        LinearProgressIndicator(progress = { state.processed.toFloat() / state.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
        Text("${state.processed}/${state.total} • zapisano ${state.saved} produktów\n${state.current}")
        Text("Większa lista może potrwać kilka minut. Możesz przerwać; zapisane wyniki pozostaną dostępne.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = viewModel::cancelNutritionRepair, modifier = Modifier.testTag("cancel_nutrition_repair")) { Text("Przerwij") }
    } else if (state.total > 0) Text("Sprawdzono ${state.processed}/${state.total} • uzupełniono ${state.saved} produktów • do sprawdzenia ${state.issues.size}")
    if (state.message.isNotBlank()) Text(state.message)
    if (!state.running && (state.total > 0 || state.message.isNotBlank())) {
        Text("Raport dotyczy tej operacji; po ręcznych zmianach możesz go zamknąć.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = viewModel::dismissNutritionRepairReport) { Text("Zamknij raport uzupełniania") }
    }
    state.issues.take(issueLimit).forEach { issue ->
        Text(issue.target.name, style = MaterialTheme.typography.labelLarge)
        Text(issue.reason, style = MaterialTheme.typography.bodySmall)
        if (issue.candidates.isNotEmpty()) TextButton(enabled = !state.running && !saving, onClick = { choice = issue; error = null }) { Text("Wybierz właściwy produkt (${issue.candidates.size})") }
    }
    if (state.issues.size > issueLimit) TextButton(onClick = { issueLimit += 10 }) { Text("Pokaż kolejne wyniki (${state.issues.size - issueLimit})") }
    if (history.isNotEmpty()) {
        TextButton(onClick = { historyOpen = !historyOpen }) { Text(if (historyOpen) "Zwiń źródła uzupełnień" else "Ostatnie uzupełnienia i źródła") }
        if (historyOpen) history.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    choice?.let { issue -> AlertDialog(onDismissRequest = { if (!saving) choice = null }, title = { Text(issue.target.name) },
        text = { Column {
            Text("Wybierz produkt zgodny z etykietą. Dane dotyczą 100 ${issue.target.basis}.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.heightIn(max = 350.dp)) { items(issue.candidates) { candidate ->
                Text("${candidate.name} • ${candidate.brand}", style = MaterialTheme.typography.labelLarge)
                Text("${candidate.caloriesPer100g} kcal • B ${candidate.proteinPer100g ?: "brak"} • W ${candidate.carbsPer100g ?: "brak"} • T ${candidate.fatPer100g ?: "brak"}", style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    viewModel.applyNutritionRepairChoice(issue.target, candidate) { message -> saving = false; error = message; if (message == null) choice = null }
                }) { Text(if (saving) "Zapisywanie…" else "Wybierz i zapisz") }
            } }
        } }, confirmButton = {}, dismissButton = { TextButton(enabled = !saving, onClick = { choice = null }) { Text("Zamknij") } }) }
}
