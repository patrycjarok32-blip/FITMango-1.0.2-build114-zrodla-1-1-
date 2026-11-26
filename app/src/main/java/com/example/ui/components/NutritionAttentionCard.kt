package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NutritionAttentionCard(viewModel: DietaViewModel, openFirstMissing: Boolean = false) {
    val repairState by viewModel.nutritionRepair.collectAsStateWithLifecycle()
    val meals by viewModel.mealPlans.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val rows by viewModel.recipeIngredients.collectAsStateWithLifecycle()
    val revision by viewModel.usability.revision.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(emptyList<NutritionRepairTarget>()) }
    var query by remember { mutableStateOf("") }
    var visibleLimit by remember(query) { mutableIntStateOf(10) }
    var target by remember { mutableStateOf<NutritionRepairTarget?>(null) }
    var initialTargetOpened by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(meals, recipes, rows, revision) {
        loading = true
        loadError = false
        try { queue = withContext(Dispatchers.Default) { UniversalNutritionRepair.collect(recipes, meals, rows) } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { loadError = true }
        finally { loading = false }
    }
    LaunchedEffect(queue, openFirstMissing) {
        if (openFirstMissing && !initialTargetOpened && queue.isNotEmpty()) {
            target = queue.first()
            initialTargetOpened = true
        }
    }
    CollapsibleMangoCard(if (loading) "Sprawdzanie składników…" else "Co wymaga uwagi? (${queue.size})", "nutrition_attention") {
        if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Sprawdzanie przepisów i jadłospisu…"); return@CollapsibleMangoCard }
        if (loadError) { Text("Nie udało się sprawdzić składników. Otwórz ekran ponownie."); return@CollapsibleMangoCard }
        UniversalNutritionRepairPanel(viewModel, queue)
        HorizontalDivider()
        Text("Pozostałe produkty możesz też uzupełnić pojedynczo z etykiety lub wybranej bazy. Brak kalorii nie jest zerem; przyprawy pomijamy zgodnie z Twoim ustawieniem.", style = MaterialTheme.typography.bodySmall)
        if (queue.isEmpty()) Text("Nie znaleziono braków kalorii ani makro. Brakujące ilości uzupełnij w szczegółach konkretnego posiłku.")
        else {
            val quantityGaps = queue.count { it.quantityMissing }
            val withBarcode = queue.count { it.barcode.isNotBlank() }
            Text("Do uzupełnienia: ${queue.size} produktów • brak ilości: $quantityGaps • z kodem kreskowym: $withBarcode",
                style = MaterialTheme.typography.bodySmall)
            Text("Automatyczne wyszukiwanie korzysta najpierw z zapisanych produktów, a po włączeniu opcji online z Open Food Facts. Niejednoznaczne dopasowania wymagają zatwierdzenia.",
                style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Szukaj brakującego produktu") }, modifier = Modifier.fillMaxWidth())
        val visible = queue.filter { ProduceSupport.normalize(it.name).contains(ProduceSupport.normalize(query.trim())) }
        visible.take(visibleLimit).forEach { item -> Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(item.name + if (item.barcode.isBlank()) "" else " • ${item.barcode}")
                Text("Wystąpienia: ${item.occurrences} • dane na 100 ${item.basis}" + if (item.quantityMissing) " • sprawdź także ilość w posiłku" else "", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(enabled = !busy && !repairState.running, onClick = { target = item; error = null }) { Text("Uzupełnij") }
        } }
        if (visible.size > visibleLimit) TextButton(onClick = { visibleLimit += 50 }) { Text("Pokaż kolejne (${visible.size - visibleLimit})") }
        if (visibleLimit > 10) TextButton(onClick = { visibleLimit = 10 }) { Text("Pokaż mniej") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    target?.let { item -> IngredientReplacementDialog(originalIngredientLine = "${item.name} — 100 ${item.basis}", recipeTitle = "Dane produktu na 100 g/ml",
        isForPlannedMeal = false, viewModel = viewModel, isNutritionRepair = true, allowPartialNutritionRepair = true, saving = busy, saveError = error,
        onDismiss = { if (!busy) target = null }, onConfirmReplacement = { candidate, _, unit, _ ->
            busy = true
            viewModel.applyNutritionRepairChoice(item, candidate.copy(defaultUnit = unit), manuallyConfirmed = true) { message -> busy = false; error = message; if (message == null) target = null }
        }) }
}
