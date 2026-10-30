package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.RecipeEntity
import com.example.data.MealPlanEntity
import com.example.ui.theme.*
import com.example.viewmodel.DietaViewModel
import com.example.viewmodel.SubstituteCandidate
import com.example.viewmodel.IngredientSubstitutionSupport
import com.example.viewmodel.MealPlannerSupport

@Composable
fun AddRecipeIngredientDialog(recipe: RecipeEntity?, viewModel: DietaViewModel, meal: MealPlanEntity? = null, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("g") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pickingProduct by remember { mutableStateOf(false) }
    var pickedCandidate by remember { mutableStateOf<SubstituteCandidate?>(null) }
    val known = remember(name) { IngredientSubstitutionSupport.lookupKnownIngredientNutrition(name) }
    LaunchedEffect(name) {
        known?.let {
            if (kcal.isBlank()) kcal = it.caloriesPer100g?.let(MealPlannerSupport::formatQuantityNumber).orEmpty()
            if (protein.isBlank()) protein = it.proteinPer100g?.let(MealPlannerSupport::formatQuantityNumber).orEmpty()
            if (carbs.isBlank()) carbs = it.carbsPer100g?.let(MealPlannerSupport::formatQuantityNumber).orEmpty()
            if (fat.isBlank()) fat = it.fatPer100g?.let(MealPlannerSupport::formatQuantityNumber).orEmpty()
        }
    }
    if (pickingProduct) {
        IngredientReplacementDialog(originalIngredientLine = "Produkt — 100 g", recipeTitle = recipe?.title ?: meal?.recipeTitle.orEmpty(),
            isForPlannedMeal = false, viewModel = viewModel, onDismiss = { pickingProduct = false }, isAddition = true,
            onConfirmReplacement = { candidate, quantity, selectedUnit, _ ->
                pickedCandidate = candidate
                name = candidate.name
                amount = MealPlannerSupport.formatQuantityNumber(quantity)
                unit = selectedUnit
                kcal = candidate.caloriesPer100g?.let(MealPlannerSupport::formatQuantityNumber).orEmpty()
                protein = candidate.proteinPer100g?.let(MealPlannerSupport::formatQuantityNumber).orEmpty()
                carbs = candidate.carbsPer100g?.let(MealPlannerSupport::formatQuantityNumber).orEmpty()
                fat = candidate.fatPer100g?.let(MealPlannerSupport::formatQuantityNumber).orEmpty()
                pickingProduct = false
            })
        return
    }
    fun number(text: String) = text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Surface(Modifier.fillMaxWidth(0.94f), shape = RoundedCornerShape(20.dp), color = MutedSurface1) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dodaj produkt", color = Cream, style = MaterialTheme.typography.titleLarge)
            Text("Wpisz własny produkt lub wybierz go z bazy albo skanera.", color = GoldAccent, style = MaterialTheme.typography.bodySmall)
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickingProduct = true }, modifier = Modifier.fillMaxWidth().testTag("choose_extra_product")) { Text("Wybierz z bazy lub skanera") }
                OutlinedTextField(name, { name = it.take(120); pickedCandidate = null; kcal = ""; protein = ""; carbs = ""; fat = "" }, singleLine = true, label = { Text("Nazwa składnika (dowolna)") }, modifier = Modifier.fillMaxWidth().testTag("extra_ingredient_name"))
                OutlinedTextField(amount, { amount = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = { Text(if (meal != null) "Ilość w tym posiłku" else "Ilość na 1 porcję przepisu") }, modifier = Modifier.fillMaxWidth().testTag("extra_ingredient_amount"))
                Row { listOf("g", "ml").forEach { option -> FilterChip(unit == option, { unit = option }, label = { Text(option) }) } }
                Text("Kalorie są wymagane. Pobierz je z bazy lub wpisz z etykiety na 100 g lub 100 ml. Makro możesz uzupełnić poniżej.", color = GoldAccent)
                OutlinedTextField(kcal, { kcal = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = { Text("Kalorie / 100 $unit") }, modifier = Modifier.fillMaxWidth().testTag("extra_ingredient_kcal"))
                OutlinedTextField(protein, { protein = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = { Text("Białko / 100 $unit") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(carbs, { carbs = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = { Text("Węglowodany / 100 $unit") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fat, { fat = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = { Text("Tłuszcz / 100 $unit") }, modifier = Modifier.fillMaxWidth())
                if (known != null && pickedCandidate == null) Text("Wartości z lokalnej tabeli są szacunkowe — porównaj je z etykietą produktu.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                val portionKcal = number(kcal)?.let { calories -> number(amount)?.let { calories * it / 100f } }
                portionKcal?.let { Text("Dodana ilość: ${MealPlannerSupport.formatQuantityNumber(it)} kcal", color = LeafGreen) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !saving, onClick = {
            val quantity = number(amount)
            if (name.isBlank() || number(kcal) == null || quantity == null || quantity <= 0 || quantity > 10000 || listOf(kcal, protein, carbs, fat).any { it.isNotBlank() && number(it) == null }) {
                error = "Wpisz nazwę, kalorie na 100 g/ml i ilość większą od 0 (do 10000). Wartości muszą być nieujemne."
            } else {
                saving = true
                val candidate = (pickedCandidate ?: SubstituteCandidate(name = name)).copy(name = name, caloriesPer100g = number(kcal), proteinPer100g = number(protein), carbsPer100g = number(carbs), fatPer100g = number(fat))
                val result: (Boolean, String?) -> Unit = { success, message ->
                    saving = false
                    if (success) onDismiss() else error = message
                }
                if (meal != null) viewModel.addMealIngredient(meal, candidate, quantity, unit, result)
                else if (recipe != null) viewModel.addRecipeIngredient(recipe, candidate, quantity, unit, result)
                else { saving = false; error = "Nie znaleziono przepisu." }
            }
        }, modifier = Modifier.testTag("save_extra_ingredient")) { Text(if (meal != null) "Dodaj do posiłku" else "Dodaj do przepisu", color = MangoYellow) }
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Anuluj", color = Cream) }
            }
        }
      }
    }
}
