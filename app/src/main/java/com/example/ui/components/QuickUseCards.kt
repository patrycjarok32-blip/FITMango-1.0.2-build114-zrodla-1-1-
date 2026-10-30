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
import com.example.data.RecipeEntity
import com.example.data.MealPlanEntity
import com.example.viewmodel.*
import com.example.ui.screens.NutritionLabelScreen
import com.example.ui.screens.ScannedProductMealDialog
import java.time.LocalDate

@Composable
fun CopyMealButton(viewModel: DietaViewModel, meal: MealPlanEntity, showButton: Boolean = true, onClosed: () -> Unit = {}) {
    var open by remember { mutableStateOf(!showButton) }
    var busy by remember { mutableStateOf(false) }
    var date by remember(meal.id) { mutableStateOf((MealPlannerSupport.dateForDay(meal.dayOfWeek) ?: LocalDate.now()).plusDays(1).toString()) }
    var category by remember(meal.id) { mutableStateOf(meal.mealType) }
    var message by remember { mutableStateOf<String?>(null) }
    if (showButton) TextButton(enabled = !busy, onClick = { message = null; open = true }, modifier = Modifier.testTag("copy_meal_${meal.id}")) { Text("Kopiuj na inny dzień") }
    message?.takeIf { !open }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (open) MangoEditSheet(onDismissRequest = { open = false; onClosed() }, dismissEnabled = !busy,
        title = { Text("Kopiuj posiłek") }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(meal.recipeTitle)
                PlanDateSelector(date, { date = it }, "copy_meal_date")
                Text("Rodzaj posiłku:")
                MealPlannerSupport.MEAL_CATEGORIES.forEach { slot -> FilterChip(category == slot.canonicalName,
                    { category = slot.canonicalName }, label = { Text(slot.canonicalName) }) }
                Text("Kopia zachowa porcje i zmienione składniki. Będzie niezjedzona; posiłek źródłowy pozostanie.")
                message?.let { Text(it) }
            }
        }, confirmButton = { Button(enabled = !busy, onClick = {
            busy = true
            viewModel.copyMeals(listOf(meal.id), date, category) { result ->
                busy = false; message = result ?: "Skopiowano: ${MealPlannerSupport.planDateLabel(date)}."
                if (result == null) { open = false; onClosed() }
            }
        }, modifier = Modifier.testTag("confirm_copy_meal")) { Text(if (busy) "Kopiowanie…" else "Kopiuj") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { open = false; onClosed() }) { Text("Anuluj") } })
}

@Composable
fun QuickMealsCard(viewModel: DietaViewModel, selectedDay: String) {
    val meals by viewModel.mealPlans.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val ingredients by viewModel.recipeIngredients.collectAsState()
    var copying by remember { mutableStateOf(false) }
    var sourceDay by remember(selectedDay) { mutableStateOf(MealPlannerSupport.WEEK_DAYS.first { it.canonicalName != selectedDay }.canonicalName) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var assign by remember { mutableStateOf<SubstituteCandidate?>(null) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var manualSaving by remember { mutableStateOf(false) }
    var manualError by remember { mutableStateOf<String?>(null) }
    CollapsibleMangoCard("Szybkie dodawanie i moje produkty", "quick_meals") {
        Text("Dodawanie do: $selectedDay. Kopie zachowują porcje i zmienione składniki; pozostają niezjedzone.")
        Text("Ostatnio jedzone", style = MaterialTheme.typography.titleMedium)
        val recent = meals.filter { it.isCompleted && it.completedDate != null }.sortedByDescending { it.completedDate }.take(10)
        if (recent.isEmpty()) Text("Oznacz posiłek jako zjedzony, aby pojawił się tutaj.")
        recent.forEach { meal ->
            TextButton(enabled = !busy, onClick = {
                busy = true
                viewModel.copyMeals(listOf(meal.id), selectedDay) { error -> busy = false; message = error ?: "Dodano ${meal.recipeTitle}." }
            }) { Text("+ ${meal.recipeTitle} • ${meal.completedDate} • ${MealPlannerSupport.formatServingsLabel(meal.servings)}") }
        }
        OutlinedButton(onClick = { copying = true; message = null }) { Text("Skopiuj jadłospis z innego dnia") }
        Text("Moje produkty", style = MaterialTheme.typography.titleMedium)
        Text("1 porcja produktu to 100 g lub 100 ml. Po wybraniu podasz ilość oraz posiłek.")
        val products = recipes.mapNotNull { QuickUseSupport.product(it, ingredients) }
        products.forEach { p -> TextButton(onClick = { assign = p }) { Text("+ ${p.name} • ${p.caloriesPer100g} kcal / 100 ${p.defaultUnit}") } }
        if (products.isEmpty()) Text("Zapisz produkt ze skanera lub dodaj własny.")
        OutlinedButton(onClick = { manualError = null; manual = true }) { Text("+ Własny produkt / etykieta") }
        message?.let { Text(it) }
    }
    if (copying) AlertDialog(onDismissRequest = { if (!busy) copying = false }, title = { Text("Kopiuj do: $selectedDay") }, text = {
        Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
            Text("Wybierz dzień źródłowy z bieżącego planu tygodnia. Kopia zostanie dopisana do istniejących posiłków.")
            MealPlannerSupport.WEEK_DAYS.filter { it.canonicalName != selectedDay }.forEach { day ->
                FilterChip(sourceDay == day.canonicalName, { sourceDay = day.canonicalName }, label = { Text(day.canonicalName) })
            }
            message?.let { Text(it) }
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        val ids = meals.filter { MealPlannerSupport.matchesDay(it.dayOfWeek, sourceDay) }.map { it.id }
        if (ids.isEmpty()) message = "Ten dzień nie ma posiłków."
        else {
            busy = true
            viewModel.copyMeals(ids, selectedDay) { error -> busy = false; message = error ?: "Skopiowano ${ids.size} posiłków."; if (error == null) copying = false }
        }
    }) { Text(if (busy) "Kopiowanie…" else "Kopiuj") } }, dismissButton = { TextButton(enabled = !busy, onClick = { copying = false }) { Text("Anuluj") } })
    if (manual) AlertDialog(onDismissRequest = { if (!manualSaving) manual = false }, title = { Text("Własny produkt") }, text = {
        Box(Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
            NutritionLabelScreen(ownProduct = true, savingProduct = manualSaving, saveMessage = manualError,
                onBack = { if (!manualSaving) manual = false }, onProduct = { product ->
                manualSaving = true; manualError = null
                viewModel.saveOwnProduct(product) { error -> manualSaving = false; manualError = error; message = error ?: "Zapisano produkt."; if (error == null) { manual = false; assign = product } }
            })
        }
    }, confirmButton = {})
    assign?.let { product -> ScannedProductMealDialog(product, viewModel, onDismiss = { assign = null }, onAdded = { day, type ->
        message = "Dodano do: $day — $type."; assign = null
    }) }
}

@Composable
fun CookedWeightDialog(recipe: RecipeEntity, viewModel: DietaViewModel, onDismiss: () -> Unit, onSaved: (Float) -> Unit, mealId: Long? = null) {
    val known = QuickUseSupport.cookedWeight(recipe)
    var weight by remember { mutableStateOf(known?.first?.toString() ?: "") }
    var portions by remember { mutableStateOf(known?.second?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val total = weight.replace(',', '.').toFloatOrNull()
    val count = portions.replace(',', '.').toFloatOrNull()
    val perPortion = if (total != null && count != null) runCatching { QuickUseSupport.portionWeight(total, count) }.getOrNull() else null
    MangoEditSheet(dismissEnabled = !busy, onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Waga gotowego dania") }, text = {
        Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Zważ całe danie po przygotowaniu, bez naczynia. Liczba porcji musi odpowiadać porcjom, dla których zapisano makro przepisu. Zapis wagi nie zmienia kalorii całego dania.")
            OutlinedTextField(weight, { weight = it }, label = { Text("Waga całości (g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(portions, { portions = it }, label = { Text("Liczba porcji całego przepisu") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            perPortion?.let { Text("1 porcja: ${MealPlannerSupport.formatQuantityNumber(it)} g") }
            Text("Zapisana waga dotyczy bazowego przepisu. Po zmianie składników sprawdź wagę porcji ponownie.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy && perPortion != null, onClick = {
        busy = true
        viewModel.saveCookedWeight(recipe.id, total!!, count!!, mealId) { result -> busy = false; error = result; if (result == null) onSaved(perPortion!!) }
    }) { Text(if (busy) "Zapisywanie…" else "Zapisz wagę") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Anuluj") } })
}

