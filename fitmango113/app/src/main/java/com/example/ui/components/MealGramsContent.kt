package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.viewmodel.MealPlannerSupport
import kotlin.math.roundToInt

/** Shared form: production hosts it in AlertDialog, tests use a bounded single window. */
@Composable
fun MealGramsContent(
    portionText: String, eatenText: String,
    onPortionChange: (String) -> Unit, onEatenChange: (String) -> Unit,
    caloriesPerServing: Float, saving: Boolean, error: String?,
    nutritionPreview: ((Float) -> String)? = null,
    onSave: (eaten: Float, portion: Float) -> Unit
) {
    val eaten = eatenText.replace(',', '.').toFloatOrNull()
    val portion = portionText.replace(',', '.').toFloatOrNull()
    val factor = if (eaten != null && portion != null) runCatching {
        MealPlannerSupport.servingsFromGrams(eaten, portion)
    }.getOrNull() else null
    Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Podaj wagę jednej gotowej porcji oraz zjedzoną ilość. Waga po gotowaniu może różnić się od sumy składników.")
        OutlinedTextField(value = portionText, onValueChange = onPortionChange,
            label = { Text("Waga 1 porcji (g)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("meal_portion_grams_input"))
        OutlinedTextField(value = eatenText, onValueChange = onEatenChange,
            label = { Text("Zjedzono (g)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("meal_eaten_grams_input"))
        if (factor != null) Text(nutritionPreview?.invoke(factor) ?: "Po przeliczeniu: ${(caloriesPerServing * factor).roundToInt()} kcal. Makro zmieni się proporcjonalnie.")
        Text("Zapis zastąpi ilość w tym posiłku i oznaczy go jako zjedzony. Możesz cofnąć zmianę.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(enabled = factor != null && !saving, onClick = {
            if (eaten != null && portion != null && factor != null) onSave(eaten, portion)
        }, modifier = Modifier.testTag("save_meal_grams")) {
            Text(if (saving) "Zapisywanie…" else "Zapisz i oznacz zjedzone")
        }
    }
}
