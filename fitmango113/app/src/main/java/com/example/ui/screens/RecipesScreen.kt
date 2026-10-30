package com.example.ui.screens

import com.example.ui.components.FitMangoIcon as Icon

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.viewmodel.FridgeRecipeSupport
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.RecipeEntity
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.DietaViewModel
import com.example.viewmodel.ImportedRecipe
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.viewmodel.RecipeCategories
import com.example.viewmodel.MealPlannerSupport
import com.example.viewmodel.RecipeImportMessages
import com.example.viewmodel.instagramPostUriOrNull
import com.example.viewmodel.usableInstagramCaption
import com.example.viewmodel.extractSharedRecipeUrlOrNull
import com.example.viewmodel.IngredientSubstitutionSupport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(viewModel: DietaViewModel, onNavigateHome: () -> Unit = {}) {
    val context = LocalContext.current
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val calorieIngredients by viewModel.recipeIngredients.collectAsStateWithLifecycle()
    val photoProgress by viewModel.photoFillProgress.collectAsStateWithLifecycle()
    val photoResult by viewModel.photoFillResult.collectAsStateWithLifecycle()
    val photoSettings = remember(context) { com.example.viewmodel.RecipePhotoSettings(context) }
    var queueStatus by remember { mutableStateOf(photoSettings.prefs.getString("status", "").orEmpty()) }
    DisposableEffect(photoSettings) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "status") queueStatus = prefs.getString("status", "").orEmpty()
        }
        photoSettings.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { photoSettings.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }


    val importedPreview by viewModel.importedRecipePreview.collectAsStateWithLifecycle()
    val fallbackPromptMode by viewModel.importFallbackPromptMode.collectAsStateWithLifecycle()
    val pendingSharedText by viewModel.pendingSharedRecipeText.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingAi.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Wszystkie") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var extraIngredientRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var photoRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var showPhotoRecipePicker by remember { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var instagramReaderUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var importInput by rememberSaveable { mutableStateOf("") }
    var pastedFallbackText by rememberSaveable { mutableStateOf("") }
    var importType by rememberSaveable { mutableStateOf("Website") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var cookingRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var planFeedback by remember { mutableIntStateOf(0) }
    var planSaving by remember { mutableStateOf(false) }
    var planError by remember { mutableStateOf<String?>(null) }

    var showFridgeDialog by remember { mutableStateOf(false) }
    var fridgeContents by rememberSaveable { mutableStateOf("") }
    var renameRecipeTarget by remember { mutableStateOf<RecipeEntity?>(null) }
    var recipeNameInput by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }
    var deleteRecipeTarget by remember { mutableStateOf<RecipeEntity?>(null) }
    var showDuplicateConfirmation by remember { mutableStateOf(false) }
    var duplicateResult by remember { mutableStateOf<String?>(null) }
    var ingredientEditTarget by remember { mutableStateOf<Pair<RecipeEntity, com.example.viewmodel.PlannedMealIngredient>?>(null) }
    var ingredientDeleteTarget by remember { mutableStateOf<Pair<RecipeEntity, com.example.viewmodel.PlannedMealIngredient>?>(null) }
    var ingredientQuantity by remember { mutableStateOf("") }
    // Preview inline edit state
    var isEditingPreview by remember { mutableStateOf(false) }
    var previewEditTitle by remember { mutableStateOf("") }
    var previewEditServings by remember { mutableStateOf("1") }
    var previewEditIngredients by remember { mutableStateOf("") }
    var previewEditInstructions by remember { mutableStateOf("") }
    var previewEditCalories by remember { mutableStateOf("0") }
    var previewEditProtein by remember { mutableStateOf("0") }
    var previewEditCarbs by remember { mutableStateOf("0") }
    var previewEditFat by remember { mutableStateOf("0") }

    // Handle incoming shared recipe text from Android Share sheet (ACTION_SEND)
    LaunchedEffect(pendingSharedText) {
        val shared = pendingSharedText
        if (!shared.isNullOrBlank()) {
            val lower = shared.lowercase()
            importType = when {
                lower.contains("instagram.com") || lower.contains("instagr.am") -> "Instagram"
                lower.contains("cookidoo") -> "Cookidoo"
                else -> "Website"
            }
            val sharedUrl = extractSharedRecipeUrlOrNull(shared)
            importInput = sharedUrl ?: shared
            pastedFallbackText = if (sharedUrl == null && instagramPostUriOrNull(shared) == null) shared else ""
            errorMessage = null
            viewModel.clearImportFallbackMode()
            showImportDialog = true
            if (importType == "Instagram" && instagramPostUriOrNull(importInput) != null && !isLoading) {
                viewModel.importRecipe("Instagram", importInput) { success, error ->
                    errorMessage = if (success) null else error
                }
            }
            viewModel.consumePendingSharedRecipeText()
        }
    }

    // Sync preview fields whenever a new preview recipe arrives
    LaunchedEffect(importedPreview) {
        val preview = importedPreview
        if (preview != null) {
            isEditingPreview = preview.requiresInstructionsCompletion && preview.instructions.isBlank()
            previewEditTitle = preview.title
            previewEditServings = preview.servings ?: "1"
            previewEditIngredients = preview.ingredients
            previewEditInstructions = preview.instructions
            previewEditCalories = preview.calories.toString()
            previewEditProtein = preview.protein.toString()
            previewEditCarbs = preview.carbs.toString()
            previewEditFat = preview.fat.toString()
        }
    }

    var showEditRecipeDialog by remember { mutableStateOf(false) }
    var recipeToEdit by remember { mutableStateOf<RecipeEntity?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("Inne") }
    var editDescription by remember { mutableStateOf("") }
    var editIngredients by remember { mutableStateOf("") }
    var editInstructions by remember { mutableStateOf("") }
    var editCalories by remember { mutableStateOf("400") }
    var editProtein by remember { mutableStateOf("30") }
    var editCarbs by remember { mutableStateOf("40") }
    var editFat by remember { mutableStateOf("15") }

    var showAddToPlanDialog by remember { mutableStateOf(false) }
    var recipeToAddToPlan by remember { mutableStateOf<RecipeEntity?>(null) }
    var selectedPlanDay by rememberSaveable { mutableStateOf(java.time.LocalDate.now().toString()) }
    var selectedMealSlot by remember { mutableStateOf("Obiad") }
    var servingsInput by remember { mutableStateOf("1.0") }

    // Ingredient replacement dialog state
    var showSwapDialog by remember { mutableStateOf(false) }
    var ingredientToSwap by remember { mutableStateOf("") }
    var recipeForIngredientSwap by remember { mutableStateOf<RecipeEntity?>(null) }

    val mealSlots = MealPlannerSupport.MEAL_CATEGORIES.map { it.canonicalName }

    var savedAnimationEvent by remember { mutableIntStateOf(0) }
    val savingRecipe by viewModel.recipeSaveInProgress.collectAsStateWithLifecycle()

    val usabilityRevision by viewModel.usability.revision.collectAsState()
    var preparationFilter by remember { mutableStateOf("Wszystkie czasy") }
    val recipeIndex by produceState<List<com.example.viewmodel.RecipeFilterSupport.Indexed>>(emptyList(), recipes, usabilityRevision) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            recipes.map { com.example.viewmodel.RecipeFilterSupport.index(it, viewModel.usability.minutes(it),
                viewModel.usability.tag(it.id, "nocook"), viewModel.usability.tag(it.id, "work")) }
        }
    }
    val searchResult by produceState("" to emptyList<RecipeEntity>(), recipeIndex, favoritesOnly, selectedCategory, searchQuery, preparationFilter) {
        if (searchQuery.isNotBlank()) kotlinx.coroutines.delay(150)
        value = searchQuery to kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.example.viewmodel.RecipeFilterSupport.filter(recipeIndex, searchQuery, selectedCategory, preparationFilter, favoritesOnly)
        }
    }
    val filteredRecipes = searchResult.second

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Twoje przepisy", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateHome, modifier = Modifier.testTag("recipes_go_home")) {
                        Icon(Icons.Default.Home, "Wróć do Startu", tint = MangoYellow)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            showImportDialog = true
                            errorMessage = null
                            viewModel.clearImportFallbackMode()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JuicyOrange, contentColor = MangoButtonInk),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .pressScaleEffect()
                            .testTag("open_import_dialog_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Import", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Importuj", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Cream
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).testTag("recipes_list"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SuccessFeedback(planFeedback)
                MangoCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Baza przepisów FITMango", style = MaterialTheme.typography.titleMedium)
                        Text(if (searchResult.first != searchQuery) "Wyszukiwanie…" else "${recipes.size} przepisów • ${filteredRecipes.size} w tym widoku", color = GoldAccent,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            OutlinedButton(onClick = { showFridgeDialog = true }, modifier = Modifier.fillMaxWidth().testTag("open_fridge_recipes")) {
                Text("Co masz w lodówce?")
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recipes_search_input"),
                placeholder = { Text("Szukaj przepisu...", color = Cream.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MangoYellow) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JuicyOrange,
                    unfocusedBorderColor = WarmBrown.copy(alpha = 0.5f),
                    focusedContainerColor = MutedSurface1,
                    unfocusedContainerColor = MutedSurface1,
                    focusedTextColor = Cream,
                    unfocusedTextColor = Cream
                )
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly },
                        label = { Text("♥ Ulubione") },
                        leadingIcon = { FitMangoAssetIcon(com.example.R.drawable.fit_icon_favorite, modifier = Modifier.size(18.dp)) }, modifier = Modifier.testTag("recipes_favorites_filter"))
                }
                items(listOf("Wszystkie") + RecipeCategories.options) { category ->
                    FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category },
                        label = { Text(RecipeCategories.label(category)) },
                        leadingIcon = {
                            fitMangoCategoryResource(category)?.let { resource ->
                                FitMangoAssetIcon(resource, modifier = Modifier.size(18.dp))
                            }
                        }, modifier = Modifier.testTag("recipe_category_$category"))
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(
                        enabled = recipes.isNotEmpty(),
                        onClick = { showPhotoRecipePicker = true },
                        label = { Text("Szukaj zdjęć") }
                    )
                }
                item {
                    AssistChip(
                        onClick = { showDuplicateConfirmation = true },
                        label = { Text("Usuń duplikaty") }
                    )
                }
                item {
                    AssistChip(
                        onClick = { if (photoProgress != null) viewModel.cancelRecipePhotoFill() else viewModel.fillMissingRecipePhotos(context) },
                        label = { Text(if (photoProgress != null) "Przerwij zdjęcia" else "Uzupełnij zdjęcia") }
                    )
                }
                item {
                    AssistChip(
                        enabled = photoProgress == null,
                        onClick = { viewModel.fillMissingRecipePhotos(context, improveExisting = true) },
                        label = { Text("Napraw i dobierz zdjęcia") }
                    )
                }
            }
            duplicateResult?.let { Text(it, color = GoldAccent, fontSize = 11.sp) }
            ImportSavedFeedback(savedAnimationEvent)
            photoProgress?.let { Text(it, color = GoldAccent, fontSize = 11.sp); ImportProgress() }
            photoResult?.let { Text(it, color = GoldAccent, fontSize = 11.sp) }
            if (queueStatus.isNotBlank()) Text(queueStatus, color = GoldAccent, fontSize = 11.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(com.example.viewmodel.RecipeFilterSupport.preparationOptions) { option ->
                    FilterChip(selected = preparationFilter == option, onClick = { preparationFilter = option }, label = { Text(option) })
                }
            }
            if (preparationFilter != "Wszystkie czasy") Text("Bez podanego czasu: ${recipes.count { viewModel.usability.minutes(it) == null }}. Uzupełnij go w szczegółach: Czas i sposób przygotowania.", style = MaterialTheme.typography.bodySmall)

            if (filteredRecipes.isEmpty()) {
                MangoEmptyState("Brak przepisów pasujących do wybranych filtrów.", "Wyczyść filtry",
                    onAction = { searchQuery = ""; selectedCategory = "Wszystkie"; preparationFilter = "Wszystkie czasy"; favoritesOnly = false })
            }
            }
            }
                gridItems(filteredRecipes, key = { it.id }) { recipe ->
                    MangoRecipeCard(
                        title = recipe.title,
                        category = RecipeCategories.categories(recipe.category).joinToString(" / ") { RecipeCategories.label(it) },
                        calories = recipe.calories,
                        imageUrl = recipe.imageUrl,
                        onClick = { selectedRecipe = recipe },
                        onPhotoClick = { photoRecipe = recipe },
                        onEditClick = { renameRecipeTarget = recipe; recipeNameInput = recipe.title; renameError = null },
                        onDeleteClick = { deleteRecipeTarget = recipe },
                        isFavorite = recipe.isFavorite,
                        onFavoriteClick = { viewModel.toggleRecipeFavorite(recipe.id) },
                        modifier = Modifier.testTag("recipe_card_${recipe.title}")
                            .mangoResultAppearance("$selectedCategory|$preparationFilter|${searchResult.first}|$favoritesOnly"),
                        preparationMinutes = viewModel.usability.minutes(recipe)
                    )
                }
        }
    }

    cookingRecipe?.let { CookingDialog(it, ingredients = com.example.viewmodel.CookingIngredientsSupport.fromRecipe(it, calorieIngredients)) { cookingRecipe = null } }
    if (showFridgeDialog) {
        FridgeRecipesDialog(recipes = recipes, contents = fridgeContents,
            onContentsChange = { fridgeContents = it }, onDismiss = { showFridgeDialog = false },
            onRecipeClick = { selectedRecipe = it; showFridgeDialog = false })
    }

    // 1. Intelligent Website / Cookidoo / Instagram Import Dialog
    val recipeImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) viewModel.importRecipeImages(context, uris) { success, error ->
            errorMessage = if (success) null else error
        }
    }
    instagramReaderUrl?.let { postUrl ->
        InstagramCaptionReader(postUrl, onDismiss = { instagramReaderUrl = null }, onCaption = { caption ->
            pastedFallbackText = caption
            errorMessage = null
            instagramReaderUrl = null
        })
    }
    if (showImportDialog && importedPreview == null && instagramReaderUrl == null) {
        val showFallbackPasteBox = fallbackPromptMode != null || importType == "Cookidoo" || importType == "Instagram"

        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .testTag("recipe_import_dialog"),
            title = { Text("Inteligentny Import Przepisu", color = Cream, fontWeight = FontWeight.Bold) },
            containerColor = MutedSurface1,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { recipeImagePicker.launch("image/*") }, enabled = !isLoading) {
                        Text("Importuj zdjęcia / zrzuty (1–12, jeden przepis)", color = MangoYellow)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = importType == "Website",
                            onClick = {
                                importType = "Website"
                                errorMessage = null
                                viewModel.clearImportFallbackMode()
                            },
                            label = { Text("Strona WWW", fontSize = 11.sp) },
                            modifier = Modifier.testTag("import_mode_Website"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JuicyOrange,
                                selectedLabelColor = DarkBackground
                            )
                        )
                        FilterChip(
                            selected = importType == "Cookidoo",
                            onClick = {
                                importType = "Cookidoo"
                                errorMessage = null
                            },
                            label = { Text("Cookidoo", fontSize = 11.sp) },
                            modifier = Modifier.testTag("import_mode_Cookidoo"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JuicyOrange,
                                selectedLabelColor = DarkBackground
                            )
                        )
                        FilterChip(
                            selected = importType == "Instagram",
                            onClick = {
                                importType = "Instagram"
                                errorMessage = null
                            },
                            label = { Text("Instagram", fontSize = 11.sp) },
                            modifier = Modifier.testTag("import_mode_Instagram"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JuicyOrange,
                                selectedLabelColor = DarkBackground
                            )
                        )
                    }

                    Text(
                        text = when (importType) {
                            "Instagram" -> "Wklej publiczny link do posta z Instagrama lub bezpośrednio opis posta z przepisem:"
                            "Cookidoo" -> "Wklej link do przepisu w Cookidoo lub skopiowane składniki i instrukcje:"
                            else -> "Wklej link do przepisu (np. aniagotuje.pl, strona WWW, Cookidoo) lub treść przepisu:"
                        },
                        fontSize = 12.sp,
                        color = Cream
                    )

                    OutlinedTextField(
                        value = importInput,
                        onValueChange = {
                            importInput = it
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_input_field"),
                        placeholder = {
                            Text(
                                text = when (importType) {
                                    "Instagram" -> "https://www.instagram.com/p/... lub opis posta"
                                    "Cookidoo" -> "https://cookidoo.pl/recipes/... lub tekst przepisu"
                                    else -> "https://... lub treść przepisu"
                                },
                                color = Cream.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JuicyOrange,
                            unfocusedBorderColor = WarmBrown,
                            focusedTextColor = Cream,
                            unfocusedTextColor = Cream
                        )
                    )

                    errorMessage?.let { err ->
                        Text(
                            text = err,
                            color = if (err == RecipeImportMessages.COOKIDOO_FALLBACK_MESSAGE || err == RecipeImportMessages.INSTAGRAM_FALLBACK_MESSAGE) {
                                MangoYellow
                            } else {
                                ErrorInk
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("import_error_message")
                        )
                    }

                    if (importType == "Instagram") {
                        Button(enabled = !isLoading && instagramPostUriOrNull(importInput) != null,
                            modifier = Modifier.fillMaxWidth().testTag("read_instagram_caption"), onClick = {
                                instagramReaderUrl = instagramPostUriOrNull(importInput)?.toString()?.replaceFirst("http://", "https://")
                            }) { Text("Pobierz opis z otwartej rolki") }
                        Text("Udostępnij post lub rolkę: Instagram → Udostępnij → FITMango. Najpierw próbujemy odczytać publiczny opis.",
                            fontSize = 11.sp, color = GoldAccent)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !isLoading && instagramPostUriOrNull(importInput) != null,
                                modifier = Modifier.weight(1f).testTag("open_instagram_post"), onClick = {
                                    instagramPostUriOrNull(importInput)?.let { uri ->
                                        try {
                                            context.startActivity(android.content.Intent.createChooser(
                                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri.toString())),
                                                "Otwórz post — wybierz przeglądarkę, aby skopiować opis"))
                                        } catch (_: Exception) { errorMessage = "Nie można otworzyć linku. Otwórz go w swojej przeglądarce." }
                                    }
                                }) { Text("Otwórz post", fontSize = 11.sp) }
                            OutlinedButton(enabled = !isLoading, modifier = Modifier.weight(1f).testTag("paste_instagram_caption"), onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val copied = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                val caption = usableInstagramCaption(copied)
                                if (caption != null) { pastedFallbackText = caption; errorMessage = null }
                                else errorMessage = "Skopiuj opis ze składnikami i przygotowaniem. W schowku nie ma opisu przepisu lub jest tylko link."
                            }) { Text("Wklej opis", fontSize = 11.sp) }
                        }
                        Text("Jeśli link jest blokowany, użyj Pobierz opis z otwartej rolki. Otwórz opis i naciśnij Pobierz opis — tekst pojawi się poniżej do sprawdzenia. Nie odczytujemy tekstu wyłącznie na filmie.",
                            fontSize = 11.sp, color = Cream)
                    }

                    if (showFallbackPasteBox) {
                        val pasteHelperLabel = when {
                            fallbackPromptMode == "Cookidoo" || importType == "Cookidoo" ->
                                "Wklej skopiowane składniki i instrukcje z Cookidoo poniżej:"
                            fallbackPromptMode == "Instagram" || importType == "Instagram" ->
                                "Wklej skopiowany opis posta z Instagrama poniżej:"
                            else ->
                                "Wklej treść przepisu (składniki i przygotowanie):"
                        }
                        Text(
                            text = pasteHelperLabel,
                            fontSize = 11.sp,
                            color = GoldAccent,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = pastedFallbackText,
                            onValueChange = {
                                pastedFallbackText = it
                                errorMessage = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 160.dp)
                                .testTag("pasted_recipe_text_input"),
                            placeholder = {
                                Text(
                                    text = "Składniki:\n- ...\nPrzygotowanie:\n1. ...",
                                    color = Cream.copy(alpha = 0.45f),
                                    fontSize = 11.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JuicyOrange,
                                unfocusedBorderColor = WarmBrown,
                                focusedTextColor = Cream,
                                unfocusedTextColor = Cream
                            )
                        )
                    }

                    if (isLoading) {
                        ImportProgress()
                    }
                }
            },
            confirmButton = {
                MangoPrimaryButton(
                    text = "Importuj przepis",
                    modifier = Modifier.testTag("import_submit_button"),
                    enabled = !isLoading,
                    onClick = {
                        val effectiveInput = if (pastedFallbackText.isNotBlank()) {
                            pastedFallbackText
                        } else {
                            importInput
                        }
                        if (effectiveInput.isNotBlank()) {
                            viewModel.importRecipe(importType, effectiveInput) { success, err ->
                                if (success) {
                                    errorMessage = null
                                } else {
                                    // Preserve importInput and pastedFallbackText on error (including Gemini quota failure)
                                    errorMessage = err ?: "Błąd importowania: nie udało się pobrać przepisu."
                                }
                            }
                        } else {
                            errorMessage = "Wprowadź adres URL przepisu lub wklej jego treść."
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        errorMessage = null
                        viewModel.clearImportFallbackMode()
                    },
                    modifier = Modifier.testTag("import_cancel_button")
                ) {
                    Text("Anuluj", color = MangoYellow)
                }
            }
        )
    }

    // 1B. Imported Recipe Preview Dialog (Shown BEFORE saving to Room)
    importedPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelRecipePreview() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .testTag("imported_recipe_preview_dialog"),
            containerColor = MutedSurface1,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    errorMessage?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
                    if (preview.requiresInstructionsCompletion) Text(
                        "Pobrano dostępne dane z Cookidoo. Możesz zapisać przepis bez instrukcji i uzupełnić je później w edycji. Kalorie i makro ze źródła dotyczą 1 porcji.",
                        fontSize = 12.sp, color = Cream, modifier = Modifier.testTag("cookidoo_partial_notice"))
                    if (savingRecipe) { ImportProgress(); Text("Sprawdzam kalorie i makro…") }
                    Text(
                        text = if (isEditingPreview) "Edytuj zaimportowany przepis" else "Podgląd zaimportowanego przepisu",
                        fontSize = 13.sp,
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isEditingPreview) OutlinedTextField(value = previewEditTitle, onValueChange = { previewEditTitle = it },
                        label = { Text("Nazwa potrawy") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("preview_edit_title_input"))
                }
            },
            text = {
                if (isEditingPreview) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = previewEditTitle,
                                onValueChange = { previewEditTitle = it },
                                label = { Text("Tytuł przepisu") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("preview_edit_title_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JuicyOrange,
                                    unfocusedBorderColor = WarmBrown,
                                    focusedTextColor = Cream,
                                    unfocusedTextColor = Cream
                                )
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = previewEditServings,
                                onValueChange = { previewEditServings = it },
                                label = { Text("Porcje") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("preview_edit_servings_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JuicyOrange,
                                    unfocusedBorderColor = WarmBrown,
                                    focusedTextColor = Cream,
                                    unfocusedTextColor = Cream
                                )
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = previewEditIngredients,
                                onValueChange = { previewEditIngredients = it },
                                label = { Text("Składniki (każdy w nowej linii)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("preview_edit_ingredients_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JuicyOrange,
                                    unfocusedBorderColor = WarmBrown,
                                    focusedTextColor = Cream,
                                    unfocusedTextColor = Cream
                                )
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = previewEditInstructions,
                                onValueChange = { previewEditInstructions = it },
                                label = { Text("Instrukcje przygotowania") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("preview_edit_instructions_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JuicyOrange,
                                    unfocusedBorderColor = WarmBrown,
                                    focusedTextColor = Cream,
                                    unfocusedTextColor = Cream
                                )
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = previewEditCalories,
                                    onValueChange = { previewEditCalories = it },
                                    label = { Text("Kcal") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = JuicyOrange,
                                        unfocusedBorderColor = WarmBrown,
                                        focusedTextColor = Cream,
                                        unfocusedTextColor = Cream
                                    )
                                )
                                OutlinedTextField(
                                    value = previewEditProtein,
                                    onValueChange = { previewEditProtein = it },
                                    label = { Text("Białko (g)") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = JuicyOrange,
                                        unfocusedBorderColor = WarmBrown,
                                        focusedTextColor = Cream,
                                        unfocusedTextColor = Cream
                                    )
                                )
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = previewEditCarbs,
                                    onValueChange = { previewEditCarbs = it },
                                    label = { Text("Węgle (g)") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = JuicyOrange,
                                        unfocusedBorderColor = WarmBrown,
                                        focusedTextColor = Cream,
                                        unfocusedTextColor = Cream
                                    )
                                )
                                OutlinedTextField(
                                    value = previewEditFat,
                                    onValueChange = { previewEditFat = it },
                                    label = { Text("Tłuszcz (g)") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = JuicyOrange,
                                        unfocusedBorderColor = WarmBrown,
                                        focusedTextColor = Cream,
                                        unfocusedTextColor = Cream
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Źródło: ${preview.source.ifBlank { importType }}",
                            fontSize = 11.sp,
                            color = GoldAccent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("preview_recipe_source")
                        )
                        Text(
                            text = "Porcje: ${preview.servings ?: "1"}",
                            fontSize = 12.sp,
                            color = MangoYellow,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("preview_recipe_servings")
                        )

                        Text(
                            text = "Składniki:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Cream
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp, max = 135.dp)
                                .testTag("preview_recipe_ingredients"),
                            color = MutedSurface2,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val ingLines = preview.ingredients.lines().filter { it.isNotBlank() }
                                items(ingLines) { line ->
                                    Text(
                                        text = "- $line",
                                        fontSize = 12.sp,
                                        color = Cream
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Instrukcje przygotowania:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Cream
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp, max = 135.dp)
                                .testTag("preview_recipe_instructions"),
                            color = MutedSurface2,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val stepLines = preview.instructions.lines().filter { it.isNotBlank() }
                                items(stepLines) { step ->
                                    Text(
                                        text = step,
                                        fontSize = 12.sp,
                                        color = Cream.copy(alpha = 0.92f)
                                    )
                                }
                            }
                        }

                        val hasNutrition = preview.nutritionAvailable ||
                            preview.calories > 0 ||
                            preview.protein > 0 ||
                            preview.carbs > 0 ||
                            preview.fat > 0
                        Text(
                            text = if (hasNutrition) {
                                "🔥 ${preview.calories} kcal | B: ${preview.protein}g | W: ${preview.carbs}g | T: ${preview.fat}g"
                            } else {
                                "Wartości odżywcze: Brak danych w źródle"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MangoYellow,
                            modifier = Modifier.testTag("preview_recipe_nutrition")
                        )
                    }
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        viewModel.estimateImportedNutrition(preview.copy(title = previewEditTitle.ifBlank { preview.title },
                            ingredients = previewEditIngredients.ifBlank { preview.ingredients }, instructions = previewEditInstructions.ifBlank { preview.instructions },
                            servings = previewEditServings.ifBlank { "1" })) { success, error -> if (!success) errorMessage = error }
                    }) { Text("Szacuj makro AI (niezweryfikowane)", color = MangoYellow) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MangoSecondaryButton(
                        text = "Edytuj",
                        modifier = Modifier.weight(1f).testTag("preview_edit_recipe_button"),
                        onClick = {
                            if (isEditingPreview) {
                                // Apply edits to preview and return to preview mode
                                val updated = preview.copy(
                                    title = previewEditTitle.trim().ifBlank { preview.title },
                                    servings = previewEditServings.trim().ifBlank { "1" },
                                    ingredients = previewEditIngredients.trim().ifBlank { preview.ingredients },
                                    instructions = previewEditInstructions.trim().ifBlank { preview.instructions },
                                    calories = previewEditCalories.toIntOrNull() ?: preview.calories,
                                    protein = previewEditProtein.toIntOrNull() ?: preview.protein,
                                    carbs = previewEditCarbs.toIntOrNull() ?: preview.carbs,
                                    fat = previewEditFat.toIntOrNull() ?: preview.fat
                                )
                                viewModel.updateRecipePreview(updated)
                                isEditingPreview = false
                            } else {
                                isEditingPreview = true
                            }
                        }
                    )
                    MangoPrimaryButton(
                        text = if (savingRecipe) "Sprawdzanie…" else if (preview.requiresInstructionsCompletion &&
                            (if (isEditingPreview) previewEditInstructions else preview.instructions).isBlank())
                            "Zapisz bez instrukcji" else "Zapisz przepis",
                        enabled = !savingRecipe,
                        modifier = Modifier.weight(1f).testTag("preview_save_recipe_button"),
                        onClick = {
                            val finalRecipeToSave = if (isEditingPreview) {
                                preview.copy(
                                    title = previewEditTitle.trim().ifBlank { preview.title },
                                    servings = previewEditServings.trim().ifBlank { "1" },
                                    ingredients = previewEditIngredients.trim().ifBlank { preview.ingredients },
                                    instructions = previewEditInstructions.trim().ifBlank { preview.instructions },
                                    calories = previewEditCalories.toIntOrNull() ?: preview.calories,
                                    protein = previewEditProtein.toIntOrNull() ?: preview.protein,
                                    carbs = previewEditCarbs.toIntOrNull() ?: preview.carbs,
                                    fat = previewEditFat.toIntOrNull() ?: preview.fat
                                )
                            } else {
                                preview.copy(title = previewEditTitle.trim().ifBlank { preview.title })
                            }
                            errorMessage = null
                            viewModel.confirmSavePreviewedRecipe(finalRecipeToSave,
                                onSaved = { savedAnimationEvent++; showImportDialog = false; importInput = ""; pastedFallbackText = ""; isEditingPreview = false },
                                onError = { errorMessage = it; showImportDialog = true })
                        }
                    )
                }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelRecipePreview()
                        isEditingPreview = false
                    },
                    modifier = Modifier.testTag("preview_cancel_recipe_button")
                ) {
                    Text("Anuluj", color = Color.Gray)
                }
            }
        )
    }

    // 2. Recipe Detail Screen/Dialog (styled like premium cooking card)
    selectedRecipe?.let { initialSelected ->
        val recipe = recipes.find { it.id == initialSelected.id } ?: initialSelected
        AlertDialog(
            onDismissRequest = { selectedRecipe = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .testTag("recipe_detail_dialog"),
            containerColor = MutedSurface1,
            title = { Text(recipe.title, fontWeight = FontWeight.Black, color = Cream, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(onClick = { cookingRecipe = recipe; selectedRecipe = null }, modifier = Modifier.testTag("recipe_start_cooking")) {
                        Text("Gotuj krok po kroku")
                    }
                    TextButton(onClick = { viewModel.toggleRecipeFavorite(recipe.id) }, modifier = Modifier.testTag("recipe_detail_favorite")) {
                        Text(if (recipe.isFavorite) "♥ W ulubionych — usuń" else "♡ Dodaj do ulubionych")
                    }
                    com.example.ui.components.RecipePreparationSettings(recipe, viewModel)
                    RecipePhoto(
                    imageUrl = recipe.imageUrl,
                    title = recipe.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                )
                    TextButton(onClick = { photoRecipe = recipe; selectedRecipe = null }) {
                        Text(if (recipe.imageUrl.isBlank()) "Dodaj zdjęcie potrawy" else "Zmień zdjęcie potrawy", color = MangoYellow)
                    }
                    Text("Źródło: ${recipe.source}", fontSize = 11.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                    if (recipe.description.isNotBlank()) {
                        Text(recipe.description, fontSize = 11.sp, color = MangoYellow, fontWeight = FontWeight.SemiBold)
                    }

                    Text("Składniki i gramatura:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Cream)

                    // Separation of ingredients line-by-line with replacement button
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp),
                        color = MutedSurface2,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val parsedIngredients = MealPlannerSupport.parseRecipeIngredients(recipe.ingredients)
                            items(parsedIngredients) { ingItem ->
                                val formattedIng = ingItem.formatForServings(1f)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("- $formattedIng", fontSize = 12.sp, color = Cream)
                                        Text(IngredientSubstitutionSupport.ingredientEnergyLabel(ingItem.name, ingItem.baseQuantity, ingItem.unit,
                                            calorieIngredients.filter { it.recipeId == recipe.id }), fontSize = 11.sp, color = MangoYellow)
                                    }
                                    IconButton(onClick = { ingredientEditTarget = recipe to ingItem; ingredientQuantity = ingItem.baseQuantity?.toString().orEmpty(); selectedRecipe = null }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Zmień ilość składnika", tint = MangoYellow)
                                    }
                                    IconButton(
                                        enabled = parsedIngredients.size > 1,
                                        onClick = { ingredientDeleteTarget = recipe to ingItem; selectedRecipe = null }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Usuń składnik", tint = if (parsedIngredients.size > 1) ErrorInk else Color.Gray)
                                    }
                                    TextButton(
                                        onClick = {
                                            recipeForIngredientSwap = recipe
                                            ingredientToSwap = formattedIng
                                            showSwapDialog = true
                                        },
                                        modifier = Modifier
                                            .height(28.dp)
                                            .testTag("recipe_swap_ingredient_${ingItem.name}"),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = "Zamień", modifier = Modifier.size(14.dp), tint = MangoYellow)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Zamień", fontSize = 11.sp, color = MangoYellow)
                                    }
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { extraIngredientRecipe = recipe; selectedRecipe = null },
                            modifier = Modifier.weight(1f).testTag("add_recipe_ingredient")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Dodaj produkt")
                        }
                        TextButton(onClick = { deleteRecipeTarget = recipe; selectedRecipe = null }) { Text("Usuń przepis", color = ErrorInk) }
                    }
                    Text("Przygotowanie:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Cream)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        color = MutedSurface2,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                            item { Text(recipe.instructions, fontSize = 12.sp, color = Cream.copy(alpha = 0.9f)) }
                        }
                    }

                    Divider(color = WarmBrown.copy(alpha = 0.3f))
                    Text("🔥 ${recipe.calories} kcal | B: ${recipe.protein}g | W: ${recipe.carbs}g | T: ${recipe.fat}g", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MangoYellow)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MangoSecondaryButton(
                        text = "Edytuj",
                        onClick = {
                            recipeToEdit = recipe
                            editTitle = recipe.title
                            editCategory = RecipeCategories.canonical(recipe.category)
                            editDescription = recipe.description
                            editIngredients = recipe.ingredients
                            editInstructions = recipe.instructions
                            editCalories = recipe.calories.toString()
                            editProtein = recipe.protein.toString()
                            editCarbs = recipe.carbs.toString()
                            editFat = recipe.fat.toString()
                            selectedRecipe = null
                            showEditRecipeDialog = true
                        }
                    )
                    MangoPrimaryButton(
                        text = "Dodaj do jadłospisu",
                        modifier = Modifier.testTag("add_to_meal_plan_button"),
                        onClick = {
                            recipeToAddToPlan = recipe
                            servingsInput = "1"
                            selectedPlanDay = java.time.LocalDate.now().toString()
                            selectedRecipe = null
                            showAddToPlanDialog = true
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedRecipe = null },
                    modifier = Modifier.testTag("close_recipe_detail_button")
                ) {
                    Text("Zamknij", color = Color.Gray)
                }
            }
        )
    }

    extraIngredientRecipe?.let { recipe ->
        AddRecipeIngredientDialog(recipe, viewModel) { extraIngredientRecipe = null; selectedRecipe = recipe }
    }

    if (showPhotoRecipePicker) AlertDialog(
        onDismissRequest = { showPhotoRecipePicker = false },
        title = { Text("Wybierz potrawę") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(recipes, key = { it.id }) { recipe ->
                    TextButton(onClick = {
                        showPhotoRecipePicker = false
                        photoRecipe = recipe
                    }, modifier = Modifier.fillMaxWidth()) { Text(recipe.title) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showPhotoRecipePicker = false }) { Text("Zamknij") } }
    )

    if (showDuplicateConfirmation) AlertDialog(
        onDismissRequest = { showDuplicateConfirmation = false }, title = { Text("Usunąć identyczne duplikaty?") },
        text = { Text("Połączę tylko przepisy z identyczną nazwą, składnikami, instrukcjami i wartościami odżywczymi. Posiłki pozostaną w jadłospisie.") },
        confirmButton = { TextButton(onClick = { showDuplicateConfirmation = false; viewModel.removeExactDuplicateRecipes { duplicateResult = "Usunięto duplikaty: $it" } }) { Text("Połącz duplikaty") } },
        dismissButton = { TextButton(onClick = { showDuplicateConfirmation = false }) { Text("Anuluj") } })
    renameRecipeTarget?.let { recipe ->
        AlertDialog(onDismissRequest = { renameRecipeTarget = null }, title = { Text("Zmień nazwę potrawy") },
            text = { Column { OutlinedTextField(value = recipeNameInput, onValueChange = { recipeNameInput = it },
                label = { Text("Nazwa potrawy") }, modifier = Modifier.testTag("recipe_rename_input"))
                renameError?.let { Text(it, color = ErrorInk) }
            } },
            confirmButton = { TextButton(enabled = recipeNameInput.isNotBlank(), onClick = {
                viewModel.renameRecipe(recipe.id, recipeNameInput) { error ->
                    if (error == null) renameRecipeTarget = null else renameError = error
                }
            }) { Text("Zapisz") } }, dismissButton = { TextButton(onClick = { renameRecipeTarget = null }) { Text("Anuluj") } })
    }
    deleteRecipeTarget?.let { recipe -> AlertDialog(onDismissRequest = { deleteRecipeTarget = null },
        title = { Text("Usunąć ${recipe.title}?") }, text = { Text("Usunięte zostaną również posiłki korzystające z tego przepisu. Tej operacji nie można cofnąć.") },
        confirmButton = { TextButton(onClick = { viewModel.deleteRecipe(recipe); deleteRecipeTarget = null }) { Text("Usuń") } },
        dismissButton = { TextButton(onClick = { deleteRecipeTarget = null }) { Text("Anuluj") } }) }
    ingredientDeleteTarget?.let { (recipe, ingredient) ->
        AlertDialog(
            onDismissRequest = { ingredientDeleteTarget = null },
            title = { Text("Usunąć składnik?") },
            text = { Text("${ingredient.name} zostanie usunięty z przepisu. Kalorie i makro zostaną ponownie przeliczone na podstawie dostępnych danych składników.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.editRecipeIngredient(recipe, ingredient.formatForServings(1f), null)
                    ingredientDeleteTarget = null
                }) { Text("Usuń", color = ErrorInk) }
            },
            dismissButton = { TextButton(onClick = { ingredientDeleteTarget = null }) { Text("Anuluj") } }
        )
    }

    ingredientEditTarget?.let { (recipe, ingredient) -> AlertDialog(onDismissRequest = { ingredientEditTarget = null },
        properties = DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.fillMaxWidth(0.95f),
        title = { Text(ingredient.name) }, text = { Column {
            OutlinedTextField(ingredientQuantity, { ingredientQuantity = it }, label = { Text("Ilość (${ingredient.unit})") })
            Text("Makro przeliczymy dla składników z dostępnymi danymi. Brakujących wartości nie zastępujemy zerami.")
        } }, confirmButton = { TextButton(enabled = ingredientQuantity.replace(',', '.').toFloatOrNull()?.let { it.isFinite() && it > 0 && it <= 100000 } == true,
            onClick = { viewModel.editRecipeIngredient(recipe, ingredient.formatForServings(1f), ingredientQuantity.replace(',', '.').toFloat()); ingredientEditTarget = null }) { Text("Zapisz ilość") } },
        dismissButton = { Row {
            TextButton(enabled = MealPlannerSupport.parseRecipeIngredients(recipe.ingredients).size > 1, onClick = { viewModel.editRecipeIngredient(recipe, ingredient.formatForServings(1f), null); ingredientEditTarget = null }) { Text("Usuń składnik") }
            TextButton(onClick = { ingredientEditTarget = null }) { Text("Anuluj") }
        } }) }

    photoRecipe?.let { recipe ->
        RecipePhotoDialog(recipe, viewModel) { photoRecipe = null; selectedRecipe = recipe }
    }

    // 3. Edit Recipe Dialog
    if (showEditRecipeDialog && recipeToEdit != null) {
        val recipe = recipeToEdit!!
        AlertDialog(
            onDismissRequest = { showEditRecipeDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.95f),
            containerColor = MutedSurface1,
            title = {
                Column {
                    Text("Edytuj przepis i składniki", color = Cream, fontWeight = FontWeight.Bold)
                    errorMessage?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(RecipeCategories.options) { category ->
                            FilterChip(selected = editCategory == category, onClick = { editCategory = category },
                                label = { Text(RecipeCategories.label(category)) })
                        }
                    }
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Nazwa") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                    Text("Zmiana ilości znanego składnika przelicza wartości. Nieznane produkty wymagają danych z etykiety; niepełna suma zachowuje poprzednie wartości. Możesz też podać własne kalorie i makro.", color = MangoYellow)
                    OutlinedTextField(value = editIngredients, onValueChange = { editIngredients = it }, label = { Text("Składniki i gramatura (np. 150g kurczak)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                    OutlinedTextField(value = editInstructions, onValueChange = { editInstructions = it }, label = { Text("Przygotowanie") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = editCalories, onValueChange = { editCalories = it }, label = { Text("Kcal") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                        OutlinedTextField(value = editProtein, onValueChange = { editProtein = it }, label = { Text("Białko (g)") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = editCarbs, onValueChange = { editCarbs = it }, label = { Text("Węgle (g)") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                        OutlinedTextField(value = editFat, onValueChange = { editFat = it }, label = { Text("Tłuszcz (g)") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JuicyOrange, unfocusedBorderColor = WarmBrown, focusedTextColor = Cream, unfocusedTextColor = Cream))
                    }
                }
            },
            confirmButton = {
                MangoPrimaryButton(
                    text = "Zapisz",
                    onClick = {
                        viewModel.updateRecipeDetails(
                            recipe = recipe,
                            newTitle = editTitle,
                            newCategory = editCategory,
                            newDescription = editDescription,
                            newIngredients = editIngredients,
                            newInstructions = editInstructions,
                            newCalories = editCalories.toIntOrNull() ?: recipe.calories,
                            newProtein = editProtein.toIntOrNull() ?: recipe.protein,
                            newCarbs = editCarbs.toIntOrNull() ?: recipe.carbs,
                            newFat = editFat.toIntOrNull() ?: recipe.fat,
                            onError = { errorMessage = it; recipeToEdit = recipe; showEditRecipeDialog = true }
                        )
                        showEditRecipeDialog = false
                        recipeToEdit = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showEditRecipeDialog = false }) {
                    Text("Anuluj", color = Color.Gray)
                }
            }
        )
    }

    // 4. Add To Plan Dialog
    if (showAddToPlanDialog && recipeToAddToPlan != null) {
        val recipe = recipeToAddToPlan!!
        val parsedServings = servingsInput.replace(',', '.').toFloatOrNull()?.coerceAtLeast(0.25f) ?: 1.0f

        MangoEditSheet(
            dismissEnabled = !planSaving,
            onDismissRequest = { if (!planSaving) showAddToPlanDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.95f),
            containerColor = MutedSurface1,
            title = { Text("Dodaj do jadłospisu", color = Cream, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    planError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(
                        text = "${recipe.title} — ${MealPlannerSupport.formatServingsLabel(parsedServings)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Cream
                    )

                    PlanDateSelector(selectedPlanDay, { selectedPlanDay = it }, "select_plan_day")

                    Text("Kategoria posiłku:", fontSize = 12.sp, color = GoldAccent, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(mealSlots) { slot ->
                            FilterChip(
                                selected = selectedMealSlot == slot,
                                onClick = { selectedMealSlot = slot },
                                label = { Text(slot, fontSize = 11.sp) },
                                modifier = Modifier.testTag("select_meal_slot_$slot"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JuicyOrange,
                                    selectedLabelColor = DarkBackground
                                )
                            )
                        }
                    }

                    Text("Liczba porcji:", fontSize = 12.sp, color = GoldAccent, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("1", "2", "3", "4")) { preset ->
                            FilterChip(
                                selected = servingsInput.trim() == preset || servingsInput.trim() == "$preset.0",
                                onClick = { servingsInput = preset },
                                label = { Text("$preset por.", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JuicyOrange,
                                    selectedLabelColor = DarkBackground
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = servingsInput,
                        onValueChange = { servingsInput = it },
                        label = { Text("Liczba porcji") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("servings_input_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JuicyOrange,
                            unfocusedBorderColor = WarmBrown,
                            focusedTextColor = Cream,
                            unfocusedTextColor = Cream
                        )
                    )
                }
            },
            confirmButton = {
                MangoPrimaryButton(
                    text = if (planSaving) "Zapisywanie…" else "Zapisz",
                    enabled = !planSaving,
                    modifier = Modifier.testTag("confirm_add_to_plan_button"),
                    onClick = {
                        planSaving = true; planError = null
                        val servings = servingsInput.replace(',', '.').toFloatOrNull()?.coerceAtLeast(0.25f) ?: 1.0f
                        viewModel.addMealPlan(selectedPlanDay, selectedMealSlot, recipe, servings) { saved, error ->
                            planSaving = false
                            if (saved) { planFeedback++; showAddToPlanDialog = false; recipeToAddToPlan = null }
                            else planError = error
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(enabled = !planSaving, onClick = { showAddToPlanDialog = false }) {
                    Text("Anuluj", color = Color.Gray)
                }
            }
        )
    }

    // 5. Ingredient Swap/Replacement Dialog (Connected to Open Food Facts, Barcode Scanner, Local DB & AI)
    val targetRecipeForSwap = recipeForIngredientSwap?.let { r -> recipes.find { it.id == r.id } ?: r }
    if (showSwapDialog && targetRecipeForSwap != null) {
        IngredientReplacementDialog(
            originalIngredientLine = ingredientToSwap,
            recipeTitle = targetRecipeForSwap.title,
            isForPlannedMeal = false,
            viewModel = viewModel,
            onDismiss = { showSwapDialog = false },
            onConfirmReplacement = { candidate, enteredQuantity, enteredUnit, _ ->
                viewModel.replaceIngredientInRecipe(
                    recipe = targetRecipeForSwap,
                    originalIngredientRaw = ingredientToSwap,
                    candidate = candidate,
                    enteredQuantity = enteredQuantity,
                    enteredUnit = enteredUnit
                )
                showSwapDialog = false
            }
        )
    }
}
