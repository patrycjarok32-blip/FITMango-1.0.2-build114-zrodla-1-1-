package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.*
import com.example.viewmodel.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ProduceDailyCard(viewModel: DietaViewModel) {
    val date by viewModel.currentDate.collectAsStateWithLifecycle()
    val days by viewModel.habits.days.collectAsStateWithLifecycle()
    val day = days.find { it.date == date }
    var details by rememberSaveable { mutableStateOf(false) }
    var showReviewed by rememberSaveable(date) { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<ProduceEntry?>(null) }
    var goals by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.mangoExpand().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MangoIncreaseMotion(day?.produceGrams ?: 0) { FitMangoAssetIcon(R.drawable.fit_icon_vegetable, modifier = Modifier.size(32.dp)) }
                Text("Warzywa i owoce", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("${day?.produceGrams ?: 0} / ${day?.produceGoal ?: 400} g", modifier = Modifier.testTag("produce_total"), style = MaterialTheme.typography.headlineSmall)
            MangoProgressBar(((day?.produceGrams ?: 0) / (day?.produceGoal ?: 400).toFloat()).coerceIn(0f, 1f), color = LeafGreen)
            Text("Warzywa: ${day?.vegetableGrams ?: 0} g • Owoce: ${day?.fruitGrams ?: 0} g")
            Text("Liczymy składniki z posiłków oznaczonych jako zjedzone, proporcjonalnie do porcji.", style = MaterialTheme.typography.bodySmall)
            if (day?.produce?.any { it.category != "other" && it.grams == null } == true) {
                Text("Część produktów nie ma wagi w gramach. Uzupełnij ją w szczegółach.", color = OrangeInk)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { add = true }, Modifier.weight(1f).testTag("add_produce")) { Text("+ Dodaj wpis") }
                TextButton(onClick = { goals = true }) { Text("Mój cel") }
            }
            TextButton(onClick = { details = !details }, Modifier.fillMaxWidth()) {
                Text(if (details) "Zwiń składniki −" else "Sprawdź składniki i popraw +")
            }
            if (details) {
                if (day?.produce.isNullOrEmpty()) Text("Oznacz posiłek jako zjedzony lub dodaj ręczny wpis.")
                val reviewed = day?.produce.orEmpty().filter { it.automatic && it.reviewed }
                val pending = day?.produce.orEmpty().filter { it.automatic && !it.reviewed }
                if (pending.isEmpty() && day?.produce?.any { it.automatic } == true) Text("Wszystkie składniki sprawdzone ✓")
                if (reviewed.isNotEmpty()) TextButton(onClick = { showReviewed = !showReviewed }) {
                    Text(if (showReviewed) "Zwiń sprawdzone składniki −" else "Sprawdzone (${reviewed.size}) +")
                }
                day?.produce?.filter { !it.automatic || !it.reviewed || showReviewed }?.forEach { entry ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name)
                            val category = when (entry.category) { "fruit" -> "Owoc"; "vegetable" -> "Warzywo"; else -> "Nie wliczono" }
                            Text("$category • ${entry.grams?.let { MealPlannerSupport.formatQuantityNumber(it) + " g" } ?: "brak wagi"}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (entry.automatic) TextButton(onClick = { edit = entry }) { Text("Popraw") }
                        else TextButton(onClick = { viewModel.habits.remove(date, entry.key) }) { Text("Usuń") }
                    }
                }
            }
        }
    }
    if (add || edit != null) {
        val selected = edit
        var name by remember(selected) { mutableStateOf(selected?.name.orEmpty()) }
        var amount by remember(selected) { mutableStateOf(selected?.grams?.let { MealPlannerSupport.formatQuantityNumber(it) }.orEmpty()) }
        var category by remember(selected) { mutableStateOf(selected?.category ?: "vegetable") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { add = false; edit = null }, title = { Text(if (selected == null) "Dodaj warzywo lub owoc" else "Popraw składnik licznika") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nazwa") }, enabled = selected == null, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(amount, { amount = it }, label = { Text("Zjedzona ilość w gramach") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    listOf("vegetable" to "Warzywo", "fruit" to "Owoc", "other" to "Nie wliczaj").filter { selected != null || it.first != "other" }.forEach { (key, label) ->
                        FilterChip(category == key, { category = key }, label = { Text(label) })
                    }
                    Text("To wpis licznika. Nie dodaje ponownie kalorii ani nie zmienia przepisu.", style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }, confirmButton = { TextButton(onClick = {
                val grams = amount.trim().replace(',', '.').toFloatOrNull()
                if (category != "other" && (grams == null || !grams.isFinite() || grams !in 1f..10000f) || name.isBlank()) {
                    error = "Podaj nazwę i wagę od 1 do 10000 g."
                } else {
                    if (selected == null) viewModel.habits.add(date, name, grams!!, category)
                    else viewModel.habits.correct(date, selected, if (category == "other") null else grams, category)
                    add = false; edit = null
                }
            }) { Text("Zapisz") } }, dismissButton = { TextButton(onClick = { add = false; edit = null }) { Text("Anuluj") } })
    }
    if (goals) {
        var produce by remember { mutableStateOf((day?.produceGoal ?: 400).toString()) }
        var water by remember { mutableStateOf((day?.waterGoal ?: 2500).toString()) }
        var error by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { goals = false }, title = { Text("Twoje cele dzienne") }, text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(produce, { produce = it }, label = { Text("Warzywa i owoce (g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(water, { water = it }, label = { Text("Napoje (ml)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Text("Cel służy Twojemu planowaniu. Wyzwania nie zachęcają do przekraczania go.", style = MaterialTheme.typography.bodySmall)
                if (error) Text("Podaj 100–2000 g oraz 250–10000 ml.", color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TextButton(onClick = {
            val p = produce.toIntOrNull(); val w = water.toIntOrNull()
            if (p == null || p !in 100..2000 || w == null || w !in 250..10000) error = true
            else { viewModel.habits.setGoals(p, w); goals = false }
        }) { Text("Zapisz") } }, dismissButton = { TextButton(onClick = { goals = false }) { Text("Anuluj") } })
    }
}

@Composable
fun HabitsHistoryCard(viewModel: DietaViewModel) {
    val date by viewModel.currentDate.collectAsStateWithLifecycle()
    val days by viewModel.habits.days.collectAsStateWithLifecycle()
    val today = LocalDate.parse(date)
    var selectedText by rememberSaveable(date) { mutableStateOf(date) }
    val selected = LocalDate.parse(selectedText)
    val day = days.find { it.date == selectedText }
    CollapsibleMangoCard("Historia dni", "day_history") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { selectedText = selected.minusDays(1).toString() }, enabled = selected > today.minusDays(729)) { Text("‹") }
            Text(selected.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            TextButton(onClick = { selectedText = selected.plusDays(1).toString() }, enabled = selected < today) { Text("›") }
        }
        TextButton(onClick = { selectedText = date }) { Text("Wróć do dzisiaj") }
        if (day == null) Text("Brak zapisanych danych dla tego dnia. Historia zbiera dane od tej aktualizacji.")
        else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Zjedzone: ${day.calories} / ${day.targetCalories} kcal")
            Text("B: ${day.protein} g • W: ${day.carbs} g • T: ${day.fat} g")
            Text("Napoje: ${day.drinksMl} / ${day.waterGoal} ml")
            Text("Warzywa: ${day.vegetableGrams} g • Owoce: ${day.fruitGrams} g")
            if (day.meals.isEmpty()) Text("Nie zapisano zjedzonych posiłków.")
            day.meals.forEach { Text("• $it") }
        }
        Text("Podgląd nie zmienia dzisiejszego jadłospisu. Zachowujemy ostatnie 730 zapisanych dni.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun HabitsChallengesCard(viewModel: DietaViewModel) {
    val date by viewModel.currentDate.collectAsStateWithLifecycle()
    val days by viewModel.habits.days.collectAsStateWithLifecycle()
    val today = LocalDate.parse(date)
    val week = days.filter { val d = LocalDate.parse(it.date); !d.isBefore(today.minusDays(6)) && !d.isAfter(today) }
    val waterDays = week.count { it.drinksMl >= it.waterGoal }
    val produceDays = week.count { it.produceGrams >= it.produceGoal }
    val activeDays = days.count { it.calories > 0 || it.drinksMl > 0 || it.produceGrams > 0 }
    CollapsibleMangoCard("Wyzwania i odznaki", "challenges") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Wyzwania z ostatnich 7 dni", fontWeight = FontWeight.Bold)
            Text("Regularne nawodnienie: ${waterDays.coerceAtMost(5)} / 5 dni")
            MangoProgressBar((waterDays / 5f).coerceIn(0f, 1f), color = LeafGreen)
            Text("Kolorowy talerz: ${produceDays.coerceAtMost(5)} / 5 dni")
            MangoProgressBar((produceDays / 5f).coerceIn(0f, 1f), color = JuicyOrange)
            Text("Twoje odznaki", fontWeight = FontWeight.Bold)
            listOf("Pierwszy krok" to (activeDays >= 1), "Pierwszy tydzień wpisów" to (activeDays >= 7),
                "Regularne nawodnienie" to (days.count { it.drinksMl >= it.waterGoal } >= 5),
                "Kolorowy talerz" to (days.count { it.produceGrams >= it.produceGoal } >= 5)).forEach { (name, earned) ->
                MangoBadge(name, earned)
            }
            Text("Nagradzamy regularność, bez kar za przerwę. Wpisy i ich poprawki są liczone tylko raz.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
