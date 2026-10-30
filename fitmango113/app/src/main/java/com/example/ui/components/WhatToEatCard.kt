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
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    CollapsibleMangoCard("Co mogę jeszcze dziś zjeść?", "what_to_eat") {
        // This content is composed only when expanded. Never scan the recipe database on the UI thread.
        var calculationError by remember(recipes, meals, ingredients, profile, date) { mutableStateOf<String?>(null) }
        val result by produceState<Pair<ScaledMealNutrition, List<MealSuggestion>>?>(null, recipes, meals, ingredients, profile, date) {
            value = null
            try {
                value = withContext(Dispatchers.Default) {
                    val today = LocalDate.parse(date)
                    val consumed = IngredientSubstitutionSupport.totalMealNutrition(meals.filter {
                        it.isCompleted && it.completedDate == date && MealPlannerSupport.dateForDay(it.dayOfWeek, today) == today
                    }, recipes, ingredients)
                    consumed to profile?.let { WhatToEatSupport.suggest(recipes, consumed, it, ingredients) }.orEmpty()
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { calculationError = "Nie udało się przygotować propozycji. Sprawdź dane przepisów i rozwiń sekcję ponownie." }
        }
        Text("Propozycje z Twoich przepisów według dzisiejszych zjedzonych posiłków. Uwzględniamy pozostałe kalorie i brakujące makro; wartości przepisów mogą być szacunkowe.")
        val goal = profile
        val consumed = result?.first
        val suggestions = result?.second.orEmpty()
        if (goal == null) Text("Uzupełnij profil i dzienny cel.")
        else if (consumed == null) Text(calculationError ?: "Przygotowuję propozycje…")
        else if (consumed.isPartial) Text("Najpierw uzupełnij brakujące dane składników. Przy niepełnym bilansie nie dopasowujemy porcji.")
        else {
            Text("Do celu: ${(goal.targetCalories - (consumed.calories ?: 0)).coerceAtLeast(0)} kcal • B: ${(goal.targetProtein - (consumed.protein ?: 0)).coerceAtLeast(0)} g • W: ${(goal.targetCarbs - (consumed.carbs ?: 0)).coerceAtLeast(0)} g • T: ${(goal.targetFat - (consumed.fat ?: 0)).coerceAtLeast(0)} g")
            if (suggestions.isEmpty()) Text("Brak pasujących porcji w bazie lub dzienny cel kalorii został osiągnięty.")
            suggestions.forEach { s ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(s.recipe.title, style = MaterialTheme.typography.titleMedium)
                    Text("${MealPlannerSupport.formatServingsLabel(s.servings)} • ${s.calories} kcal • B: ${s.protein} g • W: ${s.carbs} g • T: ${s.fat} g")
                    TextButton(enabled = !busy, onClick = { selected = s; message = null }, modifier = Modifier.testTag("suggestion_${s.recipe.id}")) { Text("Dodaj do dzisiejszego jadłospisu") }
                }
            }
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
