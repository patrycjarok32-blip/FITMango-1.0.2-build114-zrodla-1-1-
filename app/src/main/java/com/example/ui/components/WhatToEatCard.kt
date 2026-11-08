package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.viewmodel.*
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

@Composable
fun WhatToEatCard(viewModel: DietaViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val meals by viewModel.mealPlans.collectAsState()
    val ingredients by viewModel.recipeIngredients.collectAsState()
    val date by viewModel.currentDate.collectAsState()
    var selected by remember { mutableStateOf<MealSuggestion?>(null) }
    var type by remember { mutableStateOf("Przekąska") }
    var filter by remember { mutableStateOf("Wszystkie") }
    var pantryText by remember { mutableStateOf("") }
    var showPantry by remember { mutableStateOf(false) }
    var showWeekPreview by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    CollapsibleMangoCard("Co mogę jeszcze dziś zjeść?", "what_to_eat") {
        // This content is composed only when expanded. Never scan the recipe database on the UI thread.
        var calculationError by remember(recipes, meals, ingredients, profile, date, filter) { mutableStateOf<String?>(null) }
        val result by produceState<Pair<ScaledMealNutrition, List<MealSuggestion>>?>(null, recipes, meals, ingredients, profile, date, filter) {
            value = null
            try {
                value = withContext(Dispatchers.Default) {
                    val today = LocalDate.parse(date)
                    val consumed = IngredientSubstitutionSupport.totalMealNutrition(meals.filter {
                        it.isCompleted && it.completedDate == date && MealPlannerSupport.dateForDay(it.dayOfWeek, today) == today
                    }, recipes, ingredients)
                    consumed to profile?.let { WhatToEatSupport.suggest(recipes, consumed, it, ingredients, filter) }.orEmpty()
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { calculationError = "Nie udało się przygotować propozycji. Sprawdź dane przepisów i rozwiń sekcję ponownie." }
        }
        Text("Propozycje z Twoich przepisów według dzisiejszych zjedzonych posiłków. Uwzględniamy pozostałe kalorie i brakujące makro; wartości przepisów mogą być szacunkowe.")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Wszystkie", "Słodkie", "Wytrawne").forEach { option ->
                FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
            }
        }
        val goal = profile
        val consumed = result?.first
        val suggestions = result?.second.orEmpty()
        if (goal == null) Text("Uzupełnij profil i dzienny cel.")
        else if (consumed == null) Text(calculationError ?: "Przygotowuję propozycje…")
        else {
            if (consumed.isPartial || consumed.calories == null || consumed.protein == null || consumed.carbs == null || consumed.fat == null) {
                Text("Niepełne dane żywieniowe — pokazujemy propozycje z Twoich przepisów na podstawie dostępnych kalorii. Porcje i makro są orientacyjne; nieznane wartości nie są liczone jako zero.")
            } else {
                Text("Do celu: ${(goal.targetCalories - consumed.calories).coerceAtLeast(0)} kcal • B: ${(goal.targetProtein - consumed.protein).coerceAtLeast(0)} g • W: ${(goal.targetCarbs - consumed.carbs).coerceAtLeast(0)} g • T: ${(goal.targetFat - consumed.fat).coerceAtLeast(0)} g")
            }
            if (suggestions.isEmpty()) Text("Brak pasujących porcji w bazie lub dzienny cel kalorii został osiągnięty.")
            suggestions.forEach { s ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(s.recipe.title, style = MaterialTheme.typography.titleMedium)
                    Text(if (s.approximate) "${MealPlannerSupport.formatServingsLabel(s.servings)} • około ${s.calories} kcal (szacunek; makro może być niepełne)" else "${MealPlannerSupport.formatServingsLabel(s.servings)} • ${s.calories} kcal • B: ${s.protein} g • W: ${s.carbs} g • T: ${s.fat} g")
                    TextButton(enabled = !busy, onClick = { selected = s; message = null }, modifier = Modifier.testTag("suggestion_${s.recipe.id}")) { Text("Dodaj do dzisiejszego jadłospisu") }
                }
            }
        }
        HorizontalDivider()
        TextButton(onClick = { showPantry = !showPantry }) { Text(if (showPantry) "Ukryj moją lodówkę" else "Co ugotować z mojej lodówki?") }
        if (showPantry) {
            OutlinedTextField(value = pantryText, onValueChange = { pantryText = it },
                label = { Text("Produkty w lodówce (oddziel przecinkami)") }, modifier = Modifier.fillMaxWidth())
            val pantryMatches = remember(recipes, ingredients, pantryText) {
                if (pantryText.isBlank()) emptyList() else PantryRecipeMatcher.match(recipes, ingredients, pantryText.split(","))
            }
            pantryMatches.take(5).forEach { match ->
                Text("${match.recipe.title}: masz ${match.available}/${match.total} składników" +
                    if (match.missing.isEmpty()) " • masz wszystko" else " • brakuje: ${match.missing.take(3).joinToString(", ")}")
            }
        }
        TextButton(onClick = { showWeekPreview = !showWeekPreview }) { Text(if (showWeekPreview) "Ukryj plan" else "Zaproponuj jadłospis na 7 dni") }
        if (showWeekPreview && profile != null) {
            val week = remember(recipes, ingredients, profile) {
                SmartWeeklyPlanner.preview(recipes, ingredients, profile!!)
            }
            if (week.isEmpty()) Text("Brak przepisów z dostępną kalorycznością do ułożenia planu.")
            week.forEach { day ->
                Text("Dzień ${day.dayIndex + 1} • około ${day.estimatedCalories} kcal", style = MaterialTheme.typography.titleSmall)
                Text(day.suggestions.joinToString(" • ") { it.recipe.title })
            }
            Text("To podgląd propozycji, nie zapisuje ani nie zmienia obecnego jadłospisu.", style = MaterialTheme.typography.bodySmall)
        }
        message?.let { Text(it) }
    }
    selected?.let { suggestion ->
        AlertDialog(onDismissRequest = { if (!busy) selected = null }, title = { Text(suggestion.recipe.title) }, text = {
            Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${MealPlannerSupport.formatServingsLabel(suggestion.servings)} • ${suggestion.calories} kcal. Dodamy niezjedzony posiłek na dziś; gramaturę możesz potem zmienić.")
                MealPlannerSupport.MEAL_CATEGORIES.forEach { category -> FilterChip(type == category.canonicalName, { type = category.canonicalName }, label = { Text(category.canonicalName) }) }
                message?.let { Text(it) }
            }
        }, confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            viewModel.addSuggestedMeal(suggestion.recipe.id, suggestion.servings, type) { error ->
                busy = false; message = error ?: "Dodano do dzisiejszego Jadłospisu."; if (error == null) selected = null
            }
        }) { Text(if (busy) "Dodawanie…" else "Dodaj") } }, dismissButton = { TextButton(enabled = !busy, onClick = { selected = null }) { Text("Anuluj") } })
    }
}
