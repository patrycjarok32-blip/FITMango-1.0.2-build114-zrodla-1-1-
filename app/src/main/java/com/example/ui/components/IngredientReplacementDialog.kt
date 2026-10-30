package com.example.ui.components

import com.example.ui.components.FitMangoIcon as Icon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.RecipeIngredientEntity
import com.example.ui.screens.BarcodeScannerScreen
import com.example.ui.theme.*
import com.example.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientReplacementDialog(
    originalIngredientLine: String,
    recipeTitle: String,
    isForPlannedMeal: Boolean,
    viewModel: DietaViewModel,
    onDismiss: () -> Unit,
    isAddition: Boolean = false,
    additionActionLabel: String = "Wybierz produkt",
    isNutritionRepair: Boolean = false,
    allowPartialNutritionRepair: Boolean = false,
    saving: Boolean = false,
    saveError: String? = null,
    mealPreview: ((SubstituteCandidate, Float, String) -> String)? = null,
    onConfirmReplacement: (
        candidate: SubstituteCandidate,
        enteredQuantity: Float,
        enteredUnit: String,
        alsoSaveToOriginalRecipe: Boolean
    ) -> Unit
) {
    val parsedOriginal = remember(originalIngredientLine) {
        MealPlannerSupport.parseSingleIngredientLine(originalIngredientLine)
    }
    val originalName = parsedOriginal.name.ifBlank { originalIngredientLine.trim() }
    val initialQty = parsedOriginal.baseQuantity?.takeIf { it > 0f } ?: 100f
    val initialUnit = IngredientSubstitutionSupport.normalizeMassOrVolumeUnit(parsedOriginal.unit)

    var selectedMode by remember { mutableStateOf(SubstituteOptionMode.OPEN_FOOD_FACTS) }
    var offQuery by remember(originalName) { mutableStateOf(originalName) }
    var barcodeInput by remember { mutableStateOf("") }
    var localSearchQuery by remember { mutableStateOf("") }
    var showFullScreenScanner by remember { mutableStateOf(false) }

    val quantityGuard = remember { QuantityReviewGuard() }
    var selectedCandidate by remember { mutableStateOf<SubstituteCandidate?>(null) }
    fun initialRepairValue(value: Float?) = if (isNutritionRepair && selectedCandidate?.defaultUnit != initialUnit) "" else value?.toString().orEmpty()
    var repairCalories by remember(selectedCandidate) { mutableStateOf(initialRepairValue(selectedCandidate?.caloriesPer100g)) }
    var repairProtein by remember(selectedCandidate) { mutableStateOf(initialRepairValue(selectedCandidate?.proteinPer100g)) }
    var repairCarbs by remember(selectedCandidate) { mutableStateOf(initialRepairValue(selectedCandidate?.carbsPer100g)) }
    var repairFat by remember(selectedCandidate) { mutableStateOf(initialRepairValue(selectedCandidate?.fatPer100g)) }
    fun nutrient(text: String) = text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it >= 0 }
    val repairedCandidate = selectedCandidate?.let { if (!isNutritionRepair) it else it.copy(
        caloriesPer100g = nutrient(repairCalories), proteinPer100g = nutrient(repairProtein),
        carbsPer100g = nutrient(repairCarbs), fatPer100g = nutrient(repairFat)) }
    val repairComplete = !isNutritionRepair || repairedCandidate?.let {
        if (allowPartialNutritionRepair) it.caloriesPer100g != null &&
            listOf(repairProtein, repairCarbs, repairFat).all { value -> value.isBlank() || nutrient(value) != null }
        else listOf(it.caloriesPer100g, it.proteinPer100g, it.carbsPer100g, it.fatPer100g).all { n -> n != null && n.isFinite() && n >= 0 }
    } == true
    var quantityInput by remember(initialQty) {
        mutableStateOf(MealPlannerSupport.formatQuantityNumber(initialQty))
    }
    var selectedUnit by remember(initialUnit) { mutableStateOf(initialUnit) }
    LaunchedEffect(selectedUnit) {
        if (isNutritionRepair && selectedCandidate != null && selectedCandidate?.defaultUnit != selectedUnit) {
            repairCalories = ""; repairProtein = ""; repairCarbs = ""; repairFat = ""
        }
    }
    var saveToOriginalRecipe by remember { mutableStateOf(false) }

    val preferencesRevision by viewModel.usability.revision.collectAsState()
    val recentProducts = remember(preferencesRevision) { viewModel.usability.recent() }
    val favoriteProducts = remember(preferencesRevision) { viewModel.usability.favorites() }
    val offResults by viewModel.offSearchResults.collectAsState()
    val isSearchingOff by viewModel.isSearchingOff.collectAsState()
    val offError by viewModel.offSearchError.collectAsState()

    val scannedProduct by viewModel.scannedProductResult.collectAsState()
    val isScanningBarcode by viewModel.isScanningBarcodeOff.collectAsState()
    val barcodeError by viewModel.barcodeLookupError.collectAsState()

    val aiSuggestions by viewModel.aiSubstituteSuggestions.collectAsState()
    val isLoadingAi by viewModel.isLoadingAiSubstitutes.collectAsState()
    val aiError by viewModel.aiSubstitutesError.collectAsState()

    val roomIngredients by viewModel.recipeIngredients.collectAsState()
    val localIndex by produceState<List<SubstituteCandidate>>(emptyList(), roomIngredients, preferencesRevision) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            IngredientSubstitutionSupport.buildLocalDatabaseCandidates(roomIngredients)
        }
    }
    val localCandidates by produceState<List<SubstituteCandidate>>(emptyList(), localIndex, localSearchQuery) {
        if (localSearchQuery.isNotBlank()) kotlinx.coroutines.delay(150)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val query = localSearchQuery.trim().lowercase(java.util.Locale.forLanguageTag("pl-PL"))
            if (query.isBlank()) localIndex else localIndex.filter {
                it.name.lowercase(java.util.Locale.forLanguageTag("pl-PL")).contains(query) ||
                    it.brand.lowercase(java.util.Locale.forLanguageTag("pl-PL")).contains(query)
            }
        }
    }

    LaunchedEffect(originalIngredientLine) {
        viewModel.resetSubstitutionTransientState()
    }

    LaunchedEffect(scannedProduct) {
        scannedProduct?.let { scanned ->
            selectedCandidate = scanned
        }
    }

    if (showFullScreenScanner) {
        Dialog(
            onDismissRequest = { showFullScreenScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BarcodeScannerScreen(
                onBarcodeScanned = { scannedCode ->
                    showFullScreenScanner = false
                    barcodeInput = scannedCode
                    viewModel.lookupProductByBarcode(scannedCode) { found ->
                        selectedCandidate = found
                    }
                },
                onBack = { showFullScreenScanner = false }
            )
        }
    }

    MangoEditSheet(
        dismissEnabled = !saving,
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .testTag("ingredient_replacement_dialog"),
        containerColor = MutedSurface1,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isAddition) "Wybierz produkt" else "Zamień składnik",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = Cream
                )
                Text(
                    text = if (isAddition) "Wyszukaj produkt lub zeskanuj kod kreskowy." else "Wymieniany składnik: ${originalName} (${MealPlannerSupport.formatQuantityNumber(initialQty)} $initialUnit)",
                    fontSize = 12.sp,
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp).verticalScroll(rememberScrollState()).testTag("ingredient_picker_scroll"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProductShortcuts(recentProducts, favoriteProducts,
                    onSelect = { product ->
                        selectedCandidate = product
                        if (isAddition) viewModel.usability.lastPortion(product)?.let { (amount, unit) ->
                            quantityInput = MealPlannerSupport.formatQuantityNumber(amount); selectedUnit = unit
                        }
                    },
                    onFavorite = { viewModel.usability.toggleFavorite(it) },
                    portion = { viewModel.usability.lastPortion(it) }, onRemoveRecent = { viewModel.usability.removeRecent(it) }, enabled = !saving)
                selectedCandidate?.let { product ->
                    TextButton(onClick = { viewModel.usability.toggleFavorite(product) }) {
                        Text(if (viewModel.usability.isFavorite(product)) "★ Usuń z ulubionych produktów" else "☆ Ulubiony produkt")
                    }
                    val amount = quantityInput.replace(',', '.').toFloatOrNull()
                    if (amount != null && amount.isFinite() && amount > 0 && amount <= 100000) {
                        mealPreview?.let { preview -> Text(preview(repairedCandidate ?: product, amount, selectedUnit), style = MaterialTheme.typography.bodySmall) }
                    }
                }
                // 1. Four Replacement Options (2x2 Grid so full Polish labels are always visible)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ModeOptionButton(
                            mode = SubstituteOptionMode.OPEN_FOOD_FACTS,
                            isSelected = selectedMode == SubstituteOptionMode.OPEN_FOOD_FACTS,
                            onClick = { selectedMode = SubstituteOptionMode.OPEN_FOOD_FACTS },
                            modifier = Modifier.weight(1f)
                        )
                        ModeOptionButton(
                            mode = SubstituteOptionMode.BARCODE_SCAN,
                            isSelected = selectedMode == SubstituteOptionMode.BARCODE_SCAN,
                            onClick = { selectedMode = SubstituteOptionMode.BARCODE_SCAN },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ModeOptionButton(
                            mode = SubstituteOptionMode.LOCAL_DATABASE,
                            isSelected = selectedMode == SubstituteOptionMode.LOCAL_DATABASE,
                            onClick = { selectedMode = SubstituteOptionMode.LOCAL_DATABASE },
                            modifier = Modifier.weight(1f)
                        )
                        ModeOptionButton(
                            mode = SubstituteOptionMode.AI_SUGGESTION,
                            isSelected = selectedMode == SubstituteOptionMode.AI_SUGGESTION,
                            onClick = {
                                selectedMode = SubstituteOptionMode.AI_SUGGESTION
                                if (aiSuggestions.isEmpty() && !isLoadingAi && aiError == null) {
                                    viewModel.requestAiSubstitutes(originalIngredientLine, recipeTitle)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 2. Mode-Specific Search / Action Controls
                when (selectedMode) {
                    SubstituteOptionMode.OPEN_FOOD_FACTS -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = offQuery,
                                onValueChange = { offQuery = it },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("off_search_input"),
                                placeholder = {
                                    Text("Nazwa produktu w Open Food Facts...", fontSize = 12.sp, color = Cream.copy(alpha = 0.45f))
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JuicyOrange,
                                    unfocusedBorderColor = WarmBrown,
                                    focusedTextColor = Cream,
                                    unfocusedTextColor = Cream
                                )
                            )
                            Button(
                                onClick = { viewModel.searchOpenFoodFacts(offQuery) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = JuicyOrange,
                                    contentColor = MangoButtonInk
                                ),
                                modifier = Modifier.testTag("off_search_button")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Szukaj", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Szukaj", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    SubstituteOptionMode.BARCODE_SCAN -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showFullScreenScanner = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_barcode_scanner_button"),
                                border = BorderStroke(1.dp, JuicyOrange),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MangoYellow)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Uruchom skaner aparatu (EAN-13 / EAN-8 / UPC)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = barcodeInput,
                                    onValueChange = { barcodeInput = it },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("barcode_input_field"),
                                    placeholder = {
                                        Text("Wpisz lub zeskanuj kod EAN-13 / EAN-8 / UPC...", fontSize = 11.sp, color = Cream.copy(alpha = 0.45f))
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = JuicyOrange,
                                        unfocusedBorderColor = WarmBrown,
                                        focusedTextColor = Cream,
                                        unfocusedTextColor = Cream
                                    )
                                )
                                Button(
                                    onClick = {
                                        viewModel.lookupProductByBarcode(barcodeInput) { found ->
                                            selectedCandidate = found
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = JuicyOrange,
                                        contentColor = MangoButtonInk
                                    ),
                                    modifier = Modifier.testTag("barcode_lookup_button")
                                ) {
                                    Text("Sprawdź", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    SubstituteOptionMode.LOCAL_DATABASE -> {
                        OutlinedTextField(
                            value = localSearchQuery,
                            onValueChange = { localSearchQuery = it },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("local_search_input"),
                            placeholder = {
                                Text("Filtruj lokalną bazę składników...", fontSize = 12.sp, color = Cream.copy(alpha = 0.45f))
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JuicyOrange,
                                unfocusedBorderColor = WarmBrown,
                                focusedTextColor = Cream,
                                unfocusedTextColor = Cream
                            )
                        )
                    }

                    SubstituteOptionMode.AI_SUGGESTION -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Propozycje AI (3–5 zamienników do wyboru):",
                                fontSize = 12.sp,
                                color = GoldAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { viewModel.requestAiSubstitutes(originalIngredientLine, recipeTitle) },
                                border = BorderStroke(1.dp, MangoYellow),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MangoYellow),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("request_ai_substitutes_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Zapytaj AI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 3. Results List for the Active Mode
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(185.dp),
                    color = MutedSurface2.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    when (selectedMode) {
                        SubstituteOptionMode.OPEN_FOOD_FACTS -> {
                            when {
                                isSearchingOff -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = OrangeInk, modifier = Modifier.size(28.dp))
                                    }
                                }
                                offError != null -> {
                                    Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = offError ?: "",
                                            color = ErrorInk,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.testTag("off_error_text")
                                        )
                                    }
                                }
                                offResults.isEmpty() -> {
                                    Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Wpisz nazwę produktu i kliknij „Szukaj”, aby pobrać prawdziwe produkty z Open Food Facts.",
                                            color = Cream.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                else -> {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                            .testTag("off_results_list"),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        itemsIndexed(offResults) { idx, product ->
                                            SubstituteProductCard(
                                                candidate = product,
                                                isSelected = selectedCandidate == product,
                                                testTag = "off_product_card_$idx",
                                                onClick = { selectedCandidate = product }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SubstituteOptionMode.BARCODE_SCAN -> {
                            when {
                                isScanningBarcode -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = OrangeInk, modifier = Modifier.size(28.dp))
                                    }
                                }
                                barcodeError != null -> {
                                    Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = barcodeError ?: "",
                                            color = ErrorInk,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.testTag("barcode_error_text")
                                        )
                                    }
                                }
                                scannedProduct != null -> {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                    ) {
                                        item {
                                            SubstituteProductCard(
                                                candidate = scannedProduct!!,
                                                isSelected = selectedCandidate == scannedProduct,
                                                testTag = "scanned_product_card",
                                                onClick = { selectedCandidate = scannedProduct }
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Zeskanuj kod kreskowy aparatem (EAN-13, EAN-8, UPC) lub wpisz kod powyżej, aby pobrać produkt z Open Food Facts.",
                                            color = Cream.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        SubstituteOptionMode.LOCAL_DATABASE -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .testTag("local_results_list"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(localCandidates) { idx, item ->
                                    SubstituteProductCard(
                                        candidate = item,
                                        isSelected = selectedCandidate?.name == item.name && selectedCandidate?.source == SubstituteOptionMode.LOCAL_DATABASE,
                                        testTag = "local_product_card_$idx",
                                        onClick = { selectedCandidate = item }
                                    )
                                }
                            }
                        }

                        SubstituteOptionMode.AI_SUGGESTION -> {
                            when {
                                isLoadingAi -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = OrangeInk, modifier = Modifier.size(28.dp))
                                    }
                                }
                                aiError != null -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp)
                                            .testTag("ai_substitutes_error_banner"),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Błąd Gemini AI: $aiError",
                                            color = ErrorInk,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Wyszukiwarka Open Food Facts oraz baza lokalna są nadal dostępne — wybierz jedną z zakładek powyżej.",
                                            color = Cream.copy(alpha = 0.8f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                aiSuggestions.isEmpty() -> {
                                    Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Kliknij „Zapytaj AI”, aby otrzymać 3–5 propozycji zamienników.",
                                            color = Cream.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                else -> {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                            .testTag("ai_results_list"),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        itemsIndexed(aiSuggestions) { idx, item ->
                                            SubstituteProductCard(
                                                candidate = item,
                                                isSelected = selectedCandidate?.name == item.name && selectedCandidate?.source == SubstituteOptionMode.AI_SUGGESTION,
                                                testTag = "ai_product_card_$idx",
                                                onClick = {
                                                    selectedCandidate = item
                                                    if (item.defaultQuantity > 0f) {
                                                        quantityInput = MealPlannerSupport.formatQuantityNumber(item.defaultQuantity)
                                                    }
                                                    selectedUnit = item.defaultUnit
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Quantity Editor (grams or millilitres) & Original vs Replacement Preview
                val enteredQtyFloat = quantityInput.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: initialQty
                Surface(
                    color = MutedSurface2,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = quantityInput,
                                onValueChange = { quantityInput = it },
                                singleLine = true,
                                label = { Text("Ilość ($selectedUnit)", fontSize = 11.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("replacement_quantity_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JuicyOrange,
                                    unfocusedBorderColor = WarmBrown,
                                    focusedTextColor = Cream,
                                    unfocusedTextColor = Cream
                                )
                            )

                            listOf("g", "ml").forEach { unitOpt ->
                                FilterChip(
                                    selected = selectedUnit == unitOpt,
                                    onClick = { selectedUnit = unitOpt },
                                    label = { Text(unitOpt, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.testTag("unit_chip_$unitOpt"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = JuicyOrange,
                                        selectedLabelColor = DarkBackground
                                    )
                                )
                            }
                        }

                        selectedCandidate?.let { candidate ->
                            if (isNutritionRepair) {
                                Text("Uzupełnij wartości na 100 $selectedUnit. Wpisz 0 tylko wtedy, gdy tak podaje etykieta. Dane na gramy i mililitry nie są automatycznie zamieniane.")
                                OutlinedTextField(repairCalories, { repairCalories = it }, label = { Text("kcal / 100 $selectedUnit") }, singleLine = true)
                                OutlinedTextField(repairProtein, { repairProtein = it }, label = { Text("Białko (g)") }, singleLine = true)
                                OutlinedTextField(repairCarbs, { repairCarbs = it }, label = { Text("Węglowodany (g)") }, singleLine = true)
                                OutlinedTextField(repairFat, { repairFat = it }, label = { Text("Tłuszcze (g)") }, singleLine = true)
                                if (allowPartialNutritionRepair) Text("Możesz zapisać same kalorie. Puste pola makro pozostaną brakujące; nie zastępuj ich zerami.")
                                else if (!repairComplete) Text("Brakuje części makro. Uzupełnij pola przed zapisaniem.")
                            }
                            val (origFormatted, repFormatted) = IngredientSubstitutionSupport.formatOriginalVsReplacementPreview(
                                originalName = originalName,
                                originalQty = initialQty,
                                originalUnit = initialUnit,
                                replacementName = candidate.name,
                                replacementQty = enteredQtyFloat,
                                replacementUnit = selectedUnit
                            )
                            val scaledMacros = candidate.nutritionForQuantity(enteredQtyFloat)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("replacement_preview_box"),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (!isAddition) Text(
                                    text = "Oryginał: $origFormatted",
                                    fontSize = 11.sp,
                                    color = Cream.copy(alpha = 0.75f)
                                )
                                Text(
                                    text = if (isAddition) "Produkt: $repFormatted" else "Zamiennik: $repFormatted",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MangoYellow
                                )
                                Text(
                                    text = formatCandidateScaledNutritionLine(scaledMacros, enteredQtyFloat, selectedUnit),
                                    fontSize = 11.sp,
                                    color = LeafGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (isForPlannedMeal && !isAddition) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { saveToOriginalRecipe = !saveToOriginalRecipe }
                                    .testTag("save_to_original_recipe_checkbox")
                            ) {
                                Checkbox(
                                    checked = saveToOriginalRecipe,
                                    onCheckedChange = { saveToOriginalRecipe = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = JuicyOrange,
                                        uncheckedColor = GoldAccent
                                    )
                                )
                                Text(
                                    text = "Zapisz zamiennik na stałe również w oryginalnym przepisie",
                                    fontSize = 11.sp,
                                    color = Cream.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            saveError?.let { Text(it) }
            MangoPrimaryButton(
                text = if (saving) "Zapisywanie…" else if (isNutritionRepair) "Zapisz dane składnika" else if (isAddition) additionActionLabel else "Zatwierdź zamianę",
                enabled = !saving && repairComplete && selectedCandidate != null && (quantityInput.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it <= 100000 } ?: 0f) > 0f,
                modifier = Modifier.testTag("confirm_swap_button"),
                onClick = {
                    val candidate = repairedCandidate ?: return@MangoPrimaryButton
                    val qty = quantityInput.replace(',', '.').toFloatOrNull() ?: return@MangoPrimaryButton
                    val last = viewModel.usability.lastPortion(candidate)?.takeIf { it.second == selectedUnit }?.first
                    quantityGuard.run(candidate, qty, selectedUnit, last) {
                        onConfirmReplacement(candidate, qty, selectedUnit, saveToOriginalRecipe)
                    }
                }
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_swap_button")
            ) {
                Text("Anuluj", color = Color.Gray)
            }
        }
    )
    QuantityReviewPrompt(quantityGuard)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeOptionButton(
    mode: SubstituteOptionMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = mode.label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier.testTag("swap_mode_${mode.key}"),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = JuicyOrange,
            selectedLabelColor = DarkBackground,
            containerColor = MutedSurface2,
            labelColor = Cream
        )
    )
}

@Composable
fun SubstituteProductCard(
    candidate: SubstituteCandidate,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) JuicyOrange else WarmBrown.copy(alpha = 0.35f)
    val bgColor = if (isSelected) JuicyOrange.copy(alpha = 0.14f) else MutedSurface1

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (candidate.imageUrl.isNotBlank()) {
                val imageContext = androidx.compose.ui.platform.LocalContext.current
                val imageRequest = remember(imageContext, candidate.imageUrl) {
                    coil.request.ImageRequest.Builder(imageContext).data(candidate.imageUrl).size(192).crossfade(false).build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = candidate.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MutedSurface2),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = candidate.name,
                        tint = MangoYellow,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = candidate.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Cream,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val brandText = candidate.brand.ifBlank { "Brak marki" }
                val barcodeText = if (candidate.barcode.isNotBlank()) " • Kod: ${candidate.barcode}" else ""
                Text(
                    text = "Marka: $brandText$barcodeText",
                    fontSize = 10.sp,
                    color = GoldAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val kcalStr = candidate.caloriesPer100g?.let { "${MealPlannerSupport.formatQuantityNumber(it)} kcal" } ?: "Brak danych"
                val pStr = candidate.proteinPer100g?.let { "${MealPlannerSupport.formatQuantityNumber(it)} g" } ?: "Brak danych"
                val cStr = candidate.carbsPer100g?.let { "${MealPlannerSupport.formatQuantityNumber(it)} g" } ?: "Brak danych"
                val fStr = candidate.fatPer100g?.let { "${MealPlannerSupport.formatQuantityNumber(it)} g" } ?: "Brak danych"

                Text(
                    text = "Na 100 g: Kalorie: $kcalStr",
                    fontSize = 11.sp,
                    color = MangoYellow,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "B: $pStr | W: $cStr | T: $fStr",
                    fontSize = 10.sp,
                    color = Cream.copy(alpha = 0.82f)
                )

                if (candidate.aiReason.isNotBlank()) {
                    Text(
                        text = candidate.aiReason,
                        fontSize = 10.sp,
                        color = LeafGreen,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Wybrano",
                    tint = OrangeInk,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatCandidateScaledNutritionLine(
    nutrition: ScaledMealNutrition,
    quantity: Float,
    unit: String
): String {
    val qtyStr = MealPlannerSupport.formatQuantityNumber(quantity)
    val kcalPart = nutrition.calories?.let { "$it kcal" } ?: "Brak danych kcal"
    val pPart = nutrition.protein?.let { "${it}g" } ?: "Brak danych"
    val cPart = nutrition.carbs?.let { "${it}g" } ?: "Brak danych"
    val fPart = nutrition.fat?.let { "${it}g" } ?: "Brak danych"
    return "Wartość dla $qtyStr $unit: $kcalPart | B: $pPart | W: $cPart | T: $fPart"
}
