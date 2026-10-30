package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.MealPlanEntity
import com.example.data.RecipeEntity
import com.example.viewmodel.*

@Composable
fun SavedMealSetsCard(viewModel: DietaViewModel, day: String) {
    val sets by viewModel.savedMealSets.collectAsState()
    val meals by viewModel.mealPlans.collectAsState()
    var targetDay by rememberSaveable(day) { mutableStateOf(day) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.refreshSavedMealSets() }
    CollapsibleMangoCard("Ulubione zestawy", "saved_meal_sets") {
        Text("Dodawanie do: $targetDay. Zestaw zachowuje porcje, rodzaj posiłku i zamiany składników. Korzysta z aktualnych przepisów w bazie.")
        PlanDateSelector(targetDay, { targetDay = it }, "meal_set_target", showDays = false)
        OutlinedButton(onClick = { selected = meals.filter { MealPlannerSupport.matchesDay(it.dayOfWeek, day) }.map { it.id }.toSet(); name = ""; saving = true; message = null }, enabled = !busy) { Text("Zapisz zestaw z tego dnia") }
        if (sets.isEmpty()) Text("Wybierz posiłki i nazwij pierwszy zestaw, np. Moje śniadanie.")
        sets.forEach { set ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(set.name, style = MaterialTheme.typography.titleMedium)
                Text(set.entries.joinToString(" • ") { it.meal.recipeTitle }, style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(enabled = !busy, modifier = Modifier.testTag("apply_meal_set_${set.id}"), onClick = {
                        busy = true; viewModel.applyMealSet(set.id, targetDay) { error -> busy = false; message = error ?: "Dodano zestaw do: $targetDay. Możesz zmienić gramaturę w posiłkach." }
                    }) { Text("+ Dodaj zestaw") }
                    TextButton(enabled = !busy, onClick = { busy = true; viewModel.deleteMealSet(set.id) { error -> busy = false; message = error ?: "Usunięto zestaw." } }) { Text("Usuń zestaw") }
                }
            }
        }
        message?.let { Text(it) }
    }
    if (saving) AlertDialog(onDismissRequest = { if (!busy) saving = false }, title = { Text("Zapisz ulubiony zestaw") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nazwa zestawu") }, singleLine = true)
            val available = meals.filter { MealPlannerSupport.matchesDay(it.dayOfWeek, day) }
            if (available.isEmpty()) Text("Najpierw dodaj posiłki do tego dnia.")
            available.forEach { meal -> Row {
                Checkbox(meal.id in selected, { checked -> selected = if (checked) selected + meal.id else selected - meal.id })
                Text("${meal.mealType}: ${meal.recipeTitle}", Modifier.weight(1f).padding(top = 12.dp))
            } }
            message?.let { Text(it) }
        }
    }, confirmButton = { TextButton(enabled = !busy && name.isNotBlank() && selected.isNotEmpty(), onClick = {
        busy = true; viewModel.saveMealSet(name, selected.toList()) { error -> busy = false; message = error ?: "Zapisano zestaw."; if (error == null) saving = false }
    }) { Text(if (busy) "Zapisywanie…" else "Zapisz") } }, dismissButton = { TextButton(enabled = !busy, onClick = { saving = false }) { Text("Anuluj") } })
}

@Composable
fun CookedBatchButton(viewModel: DietaViewModel, meal: MealPlanEntity, recipe: RecipeEntity, showButton: Boolean = true, onClosed: () -> Unit = {}) {
    var open by rememberSaveable { mutableStateOf(!showButton) }
    var byWeight by rememberSaveable { mutableStateOf(false) }
    var reference by rememberSaveable(meal.id) { mutableStateOf((MealPlannerSupport.dateForDay(meal.dayOfWeek) ?: java.time.LocalDate.now()).toString()) }
    var total by rememberSaveable { mutableStateOf("") }
    var portions by rememberSaveable { mutableStateOf("3") }
    val amounts = remember { mutableStateMapOf<String, String>() }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val weight = total.replace(',', '.').toFloatOrNull()
    val count = portions.replace(',', '.').toFloatOrNull()
    val values = amounts.filterValues { it.isNotBlank() }
    val gramRows = values.map { (day, amount) -> BatchMealPortion(day, amount.replace(',', '.').toFloatOrNull() ?: Float.NaN) }
    val portionRows = values.map { (date, amount) -> PortionMealAssignment(date, amount.replace(',', '.').toFloatOrNull() ?: Float.NaN) }
    val validation = if (byWeight) {
        if (weight != null && count != null) runCatching { MealConvenienceSupport.batchServings(weight, count, gramRows); Unit } else null
    } else if (count != null) runCatching { MealQueueSupport.validatePortions(count, portionRows) } else null
    if (showButton) OutlinedButton(onClick = {
        amounts.clear(); message = null; byWeight = false; portions = "3"; open = true
    }, modifier = Modifier.fillMaxWidth().testTag("cooked_batch_${meal.id}")) { Text("Gotowanie na kilka dni") }
    message?.takeIf { !open }?.let { Text(it) }
    if (open) MangoEditSheet(onDismissRequest = { open = false; onClosed() }, dismissEnabled = !busy,
        title = { Text("Zaplanuj porcje na kilka dni") }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    FilterChip(!byWeight, { if (byWeight) { byWeight = false; amounts.clear() } }, label = { Text("Bez ważenia") })
                    FilterChip(byWeight, { if (!byWeight) { byWeight = true; amounts.clear() } }, label = { Text("Dokładne gramy") })
                }
                if (!byWeight) Text("Podziel danie na równe pudełka. Nie trzeba ważyć całości. Jedna porcja w tym planie odpowiada obecnemu posiłkowi: ${MealPlannerSupport.formatServingsLabel(meal.servings)}.")
                else Text("Podaj wagę całego gotowego dania i liczbę bazowych porcji przepisu; na dni wpisz gramy.")
                Text("Dodajemy dodatkowe, niezjedzone posiłki. Posiłek źródłowy zostaje w swoim dniu. Jeśli był tylko wzorem, możesz go później usunąć.")
                if (byWeight) OutlinedTextField(total, { total = it }, label = { Text("Waga gotowej całości (g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(portions, { portions = it }, label = { Text(if (byWeight) "Bazowe porcje w całości" else "Liczba przygotowanych porcji / pudełek") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                PlanDateSelector(reference, { reference = it }, "portion_batch_week", showDays = false)
                MealPlannerSupport.weekDays(java.time.LocalDate.parse(reference)).forEach { day ->
                    val date = MealPlannerSupport.dateForDay(day.canonicalName, java.time.LocalDate.parse(reference))!!.toString()
                    OutlinedTextField(amounts[date].orEmpty(), { amounts[date] = it },
                        label = { Text("${day.displayWithDate} (${if (byWeight) "g" else "porcje"})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
                if (!byWeight) Text("Np. wtorek: 1, środa: 1, czwartek: 1. Możesz wpisać 0,5 lub 2 porcje. Puste dni pomijamy.")
                if (values.isNotEmpty()) {
                    Text("Wybrane terminy:")
                    values.toSortedMap().forEach { (date, amount) -> Row(Modifier.fillMaxWidth()) {
                        Text("$date • $amount ${if (byWeight) "g" else "porcji"}", Modifier.weight(1f))
                        TextButton(enabled = !busy, onClick = { amounts.remove(date) }) { Text("Usuń") }
                    } }
                }
                validation?.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                message?.let { Text(it) }
            }
        }, confirmButton = { Button(enabled = !busy && validation?.isSuccess == true, onClick = {
            busy = true
            val done: (String?) -> Unit = { error -> busy = false; message = error ?: "Dodano posiłki na wybrane daty."; if (error == null) { open = false; onClosed() } }
            if (byWeight) viewModel.planCookedBatch(meal.id, weight!!, count!!, gramRows, done)
            else viewModel.planPortionBatch(meal.id, count!!, portionRows, done)
        }) { Text(if (busy) "Zapisywanie…" else "Dodaj porcje do dni") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { open = false; onClosed() }) { Text("Anuluj") } })
}

@Composable
fun SaveSingleMealSetButton(viewModel: DietaViewModel, meal: MealPlanEntity) {
    var open by remember { mutableStateOf(false) }; var name by remember { mutableStateOf(meal.recipeTitle.take(80)) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    OutlinedButton(onClick = { open = true }) { Text("Zapisz jako mój zestaw") }
    if (open) AlertDialog(onDismissRequest = { if (!busy) open = false }, title = { Text("Mój zestaw produktów") },
        text = { Column { Text("Zapisz ten posiłek ze składnikami, porcjami i zamiennikami."); OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nazwa zestawu") }); error?.let { Text(it) } } },
        confirmButton = { TextButton(enabled = !busy && name.isNotBlank(), onClick = { busy = true; viewModel.saveMealSet(name, listOf(meal.id)) { message -> busy = false; error = message; if (message == null) open = false } }) { Text("Zapisz") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { open = false }) { Text("Anuluj") } })
}
