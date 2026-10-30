package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.viewmodel.*

@Composable
fun ChatRecipeDialog(text: String, viewModel: DietaViewModel, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val draft = remember(text) { ChatRecipeSupport.draft(text) }
    var title by remember { mutableStateOf(draft?.title.orEmpty()) }
    var ingredients by remember { mutableStateOf(draft?.ingredients.orEmpty()) }
    var instructions by remember { mutableStateOf(draft?.instructions.orEmpty()) }
    var servings by remember { mutableStateOf(draft?.servings ?: "1") }
    var kcal by remember { mutableStateOf(draft?.calories?.toString() ?: "0") }
    var protein by remember { mutableStateOf(draft?.protein?.toString() ?: "0") }
    var carbs by remember { mutableStateOf(draft?.carbs?.toString() ?: "0") }
    var fat by remember { mutableStateOf(draft?.fat?.toString() ?: "0") }
    var date by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var slot by remember { mutableStateOf("Obiad") }
    var portions by remember { mutableStateOf("1") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val batch = servings.replace(',', '.').toFloatOrNull(); val amount = portions.replace(',', '.').toFloatOrNull()
    val valid = title.isNotBlank() && ingredients.isNotBlank() && instructions.isNotBlank() && batch != null && batch.isFinite() && batch in .25f..1000f &&
        amount != null && amount.isFinite() && amount in .25f..100f && kcal.toIntOrNull()?.let { it in 0..15000 } == true && protein.toIntOrNull()?.let { it in 0..1000 } == true && carbs.toIntOrNull()?.let { it in 0..2000 } == true && fat.toIntOrNull()?.let { it in 0..1000 } == true
    fun save(add: Boolean) {
        busy = true; error = null
        viewModel.saveChatRecipe(ImportedRecipe(title, servings, ingredients, instructions, kcal.toInt(), protein.toInt(), carbs.toInt(), fat.toInt(), "AI Dietetyk", nutritionIsEstimated = true),
            if (add) date else null, slot, amount!!) { saved, message ->
            busy = false; error = message
            if (saved != null) onSaved()
        }
    }
    MangoEditSheet(onDismissRequest = onDismiss, dismissEnabled = !busy, title = { Text("Przepis od AI → jadłospis") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sprawdź jeden przepis, porcje i gramatury. Kalorie i makro z AI są szacunkowe; nieznane składniki mogą wymagać uzupełnienia.")
            if (draft == null) {
                Text("Odpowiedź nie zawiera jednego pełnego przepisu. Uzupełnij pola albo poproś AI o jeden przepis.")
                androidx.compose.foundation.text.selection.SelectionContainer { Text(text.take(8000)) }
            }
            OutlinedTextField(title, { title = it.take(200) }, label = { Text("Nazwa") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(servings, { servings = it.take(6) }, label = { Text("Ile porcji daje cały przepis") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ingredients, { ingredients = it.take(30000) }, label = { Text("Składniki całego przepisu i ilości") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(instructions, { instructions = it.take(50000) }, label = { Text("Kroki przygotowania") }, modifier = Modifier.fillMaxWidth())
            Text("Wartości NA JEDNĄ PORCJĘ (szacunkowe)")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(kcal, { kcal = it.take(5) }, label = { Text("kcal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(protein, { protein = it.take(4) }, label = { Text("B g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(carbs, { carbs = it.take(4) }, label = { Text("W g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(fat, { fat = it.take(4) }, label = { Text("T g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            PlanDateSelector(date, { date = it }, "chat_recipe_date", showDays = false)
            Column { MealPlannerSupport.MEAL_CATEGORIES.forEach { category -> FilterChip(slot == category.canonicalName, { slot = category.canonicalName }, label = { Text(category.canonicalName) }) } }
            OutlinedTextField(portions, { portions = it.take(6) }, label = { Text("Porcje do jadłospisu") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { Button(enabled = !busy && valid, onClick = { save(true) }, modifier = Modifier.testTag("chat_recipe_add_plan")) { Text(if (busy) "Zapisywanie…" else "Zapisz i dodaj do jadłospisu") } },
        dismissButton = { Row { TextButton(enabled = !busy && valid, onClick = { save(false) }) { Text("Tylko baza przepisów") }; TextButton(enabled = !busy, onClick = onDismiss) { Text("Anuluj") } } })
}
