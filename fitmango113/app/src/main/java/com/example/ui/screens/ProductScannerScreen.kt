package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.viewmodel.DietaViewModel
import com.example.viewmodel.MealPlannerSupport
import com.example.viewmodel.SubstituteCandidate
import com.example.ui.theme.*
import com.example.ui.components.RecipePhoto

/** The dashboard scanner displays OFF data directly, without requiring a Gemini key. */
@Composable
fun ProductScannerScreen(viewModel: DietaViewModel, onBack: () -> Unit, initialPhotoMode: Boolean = false) {
    var photoMode by rememberSaveable { mutableStateOf(initialPhotoMode) }
    var labelMode by rememberSaveable { mutableStateOf(false) }
    var labelProduct by remember { mutableStateOf<SubstituteCandidate?>(null) }
    var assignProduct by remember { mutableStateOf<SubstituteCandidate?>(null) }
    var addedMessage by remember { mutableStateOf<String?>(null) }
    var scannedCode by remember { mutableStateOf<String?>(null) }
    val product by viewModel.scannedProductResult.collectAsState()
    val loading by viewModel.isScanningBarcodeOff.collectAsState()
    val error by viewModel.barcodeLookupError.collectAsState()
    LaunchedEffect(Unit) { viewModel.resetSubstitutionTransientState() }
    if (photoMode) {
        ProductPhotoScreen(viewModel, onBack = { if (initialPhotoMode) onBack() else photoMode = false }, onProduct = { item ->
            labelProduct = item; photoMode = false; scannedCode = "zdjęcie"; addedMessage = null
        })
    } else if (labelMode) {
        NutritionLabelScreen(onBack = { labelMode = false }, onProduct = { item ->
            labelProduct = item; labelMode = false; scannedCode = "etykieta"; assignProduct = item
            viewModel.saveOwnProduct(item) { result -> addedMessage = result ?: "Produkt zapisany w Moich produktach." }
        })
    } else if (scannedCode == null) {
        BarcodeScannerScreen(onBarcodeScanned = { code ->
            labelProduct = null
            scannedCode = code
            viewModel.lookupProductByBarcode(code)
        }, onBack = onBack, onReadLabel = { labelMode = true }, onRecognizePhoto = { photoMode = true })
    } else {
        Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp).testTag("dashboard_scanned_product"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) { Text("Wróć do Start") }
                Text("Zeskanowany produkt", style = MaterialTheme.typography.headlineSmall)
                Text(if (scannedCode == "zdjęcie") "Źródło: wybrany wynik wyszukiwania" else if (labelProduct != null) "Źródło: etykieta produktu" else "Kod: $scannedCode")
                if (labelProduct == null && loading) { CircularProgressIndicator(); Text("Pobieranie produktu z Open Food Facts…") }
                error?.takeIf { labelProduct == null }?.let { Text(it); Button(onClick = { viewModel.lookupProductByBarcode(scannedCode!!) }) { Text("Ponów pobieranie") } }
                (labelProduct ?: product)?.let { item ->
                    RecipePhoto(item.imageUrl, item.name, Modifier.fillMaxWidth().height(180.dp))
                    Text(item.name, style = MaterialTheme.typography.titleLarge)
                    if (item.brand.isNotBlank()) Text(item.brand)
                    Text(if (scannedCode == "zdjęcie") "Wartości z bazy na 100 ${item.defaultUnit}:" else if (labelProduct != null) "Wartości na 100 ${item.defaultUnit} (sprawdzona etykieta):" else "Wartości na 100 g / 100 ml (Open Food Facts):")
                    fun value(number: Float?, unit: String) = number?.let { "$it $unit" } ?: "Brak danych"
                    Text("Kalorie: ${value(item.caloriesPer100g, "kcal")}")
                    Text("Białko: ${value(item.proteinPer100g, "g")}")
                    Text("Węglowodany: ${value(item.carbsPer100g, "g")}")
                    Text("Tłuszcz: ${value(item.fatPer100g, "g")}")
                    Button(onClick = { assignProduct = item }, modifier = Modifier.testTag("scanned_product_add_to_meal")) { Text("Dodaj do posiłku") }
                    var savingProduct by remember(item) { mutableStateOf(false) }
                    OutlinedButton(enabled = !savingProduct, onClick = {
                        savingProduct = true
                        viewModel.saveOwnProduct(item) { error ->
                            savingProduct = false
                            addedMessage = error ?: "Zapisano w Moich produktach w Jadłospisie."
                        }
                    }) { Text(if (savingProduct) "Zapisywanie…" else "Zapisz w moich produktach") }
                    addedMessage?.let { Text(it, color = LeafGreen) }
                }
                OutlinedButton(onClick = { labelMode = true }) { Text("Odczytaj inną etykietę") }
                OutlinedButton(onClick = { viewModel.resetSubstitutionTransientState(); scannedCode = null; labelProduct = null; addedMessage = null }) { Text("Skanuj kolejny produkt") }
            }
        }
    }
    assignProduct?.let { item ->
        ScannedProductMealDialog(item, viewModel, onDismiss = { assignProduct = null }, onAdded = { day, type ->
            assignProduct = null
            addedMessage = "Dodano do: $day — $type. Produkt znajdziesz w Jadłospisie."
        })
    }
}

@Composable
fun ScannedProductMealDialog(product: SubstituteCandidate, viewModel: DietaViewModel,
    onDismiss: () -> Unit, onAdded: (String, String) -> Unit) {
    val days = MealPlannerSupport.WEEK_DAYS
    val types = MealPlannerSupport.MEAL_CATEGORIES.map { it.canonicalName }
    var day by remember { mutableStateOf(days[java.time.LocalDate.now().dayOfWeek.value - 1].canonicalName) }
    var type by remember { mutableStateOf("Przekąska") }
    val lastPortion = remember(product) { viewModel.usability.lastPortion(product) }
    val quantityGuard = remember { com.example.ui.components.QuantityReviewGuard() }
    var quantity by remember { mutableStateOf(lastPortion?.first?.let { MealPlannerSupport.formatQuantityNumber(it) } ?: "100") }
    var unit by remember { mutableStateOf(lastPortion?.second ?: product.defaultUnit) }
    var daysOpen by remember { mutableStateOf(false) }
    var typesOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val amount = quantity.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 10000 }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Dodaj do posiłku") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(product.name)
            Box {
                OutlinedButton(onClick = { daysOpen = true }) { Text("Dzień: $day") }
                DropdownMenu(expanded = daysOpen, onDismissRequest = { daysOpen = false }) {
                    days.forEach { item -> DropdownMenuItem(text = { Text(item.displayWithDate) }, onClick = { day = item.canonicalName; daysOpen = false }) }
                }
            }
            Box {
                OutlinedButton(onClick = { typesOpen = true }) { Text("Posiłek: $type") }
                DropdownMenu(expanded = typesOpen, onDismissRequest = { typesOpen = false }) {
                    types.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { type = item; typesOpen = false }) }
                }
            }
            OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Ilość ($unit)") },
                modifier = Modifier.testTag("scanned_product_quantity"), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("g", "ml").forEach { value -> FilterChip(selected = unit == value, onClick = { unit = value }, label = { Text(value) }) }
            }
            amount?.let { qty ->
                val nutrition = product.nutritionForQuantity(qty)
                Text("Dla tej ilości: ${nutrition.calories?.let { "$it kcal" } ?: "brak kalorii"}")
            }
            if (listOf(product.proteinPer100g, product.carbsPer100g, product.fatPer100g).any { it == null }) Text("Baza produktu nie zawiera pełnego makro. Brakujące dane pozostaną oznaczone.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(enabled = !saving && amount != null, onClick = {
            val qty = amount ?: return@TextButton
            val selectedDay = day; val selectedType = type; val selectedUnit = unit
            quantityGuard.run(product, qty, selectedUnit, lastPortion?.takeIf { it.second == selectedUnit }?.first) {
                saving = true; error = null
                viewModel.addScannedProductToMeal(product, qty, selectedUnit, selectedDay, selectedType) { result ->
                    saving = false
                    if (result == null) onAdded(selectedDay, selectedType) else error = result
                }
            }
        }) { Text(if (saving) "Dodawanie…" else "Dodaj") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Anuluj") } })
    com.example.ui.components.QuantityReviewPrompt(quantityGuard)
}
