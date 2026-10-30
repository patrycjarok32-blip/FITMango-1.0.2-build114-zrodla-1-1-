package com.example.ui.screens

import com.example.ui.components.FitMangoIcon as Icon

import com.example.ui.components.swipePlannerDays
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.*
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.MealPlanEntity
import com.example.data.RecipeEntity
import com.example.viewmodel.MealConvenienceSupport
import com.example.viewmodel.QuickUseSupport
import com.example.data.RecipeIngredientEntity
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.DietaViewModel
import com.example.viewmodel.IngredientSubstitutionSupport
import com.example.viewmodel.ResolvedMealIngredientItem
import com.example.viewmodel.MealCategorySpec
import com.example.viewmodel.MealPlannerSupport
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(viewModel: DietaViewModel) {
    val mealPlans by viewModel.mealPlans.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val recipeIngredients by viewModel.recipeIngredients.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingAi.collectAsStateWithLifecycle()
    val refreshing by viewModel.healthConnect.isSyncing.collectAsStateWithLifecycle()

    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()
    val today = java.time.LocalDate.parse(currentDate)
    var selectedDayName by remember(currentDate) { mutableStateOf(today.toString()) }
    val selectedDate = MealPlannerSupport.dateForDay(selectedDayName, today) ?: today
    val weekDays = MealPlannerSupport.weekDays(selectedDate)
    val mealCategories = MealPlannerSupport.MEAL_CATEGORIES

    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.refreshCurrentDate()
    }
    val selectedDaySpec = weekDays[selectedDate.dayOfWeek.value - 1]

    val dayMeals = remember(mealPlans, selectedDayName) {
        mealPlans.filter { MealPlannerSupport.matchesDay(it.dayOfWeek, selectedDayName) }
    }

    val eatenMeals = com.example.viewmodel.MealQueueSupport.eaten(dayMeals, selectedDayName)
    val remainingMeals = com.example.viewmodel.MealQueueSupport.remaining(dayMeals, selectedDayName)
    var showEatenHistory by remember(selectedDayName) { mutableStateOf(false) }
    var showEmptySlots by remember(selectedDayName) { mutableStateOf(false) }
    var actionFeedback by remember { mutableIntStateOf(0) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var cookingRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var cookingMeal by remember { mutableStateOf<MealPlanEntity?>(null) }
    val dragController = remember(viewModel, currentDate) {
        MealDragController { meal, date ->
            viewModel.moveMealPlan(meal, date, onResult = { saved, error ->
                actionError = error
                if (saved) selectedDayName = date
            })
        }
    }
    DisposableEffect(dragController) { onDispose { dragController.cancel() } }
    val plannerListState = rememberLazyListState()
    val reducedMotion = com.example.ui.components.LocalReducedMangoMotion.current
    val renderedMeals = rememberMealQueueMotion(remainingMeals, selectedDayName)
    var motionFeedback by remember(selectedDayName) { mutableIntStateOf(0) }
    var motionMessage by remember(selectedDayName) { mutableStateOf("Zapisano posiłek") }
    LaunchedEffect(viewModel, selectedDayName, reducedMotion) {
        viewModel.mealMotionEvents.collect { event ->
            if (event.undo || event.date == selectedDayName) {
                motionMessage = if (event.undo) "Cofnięto zmianę" else if (event.completed == true) "Zapisano jako zjedzone" else "Posiłek wrócił do zaplanowanych"
                motionFeedback++
            }
        }
    }
    var previousEatenCount by remember(selectedDayName) { mutableStateOf(eatenMeals.size) }
    LaunchedEffect(selectedDayName, eatenMeals.size) {
        if (eatenMeals.size > previousEatenCount) {
            if (!reducedMotion) androidx.compose.animation.core.Animatable(0f).animateTo(1f, androidx.compose.animation.core.tween(230))
            androidx.compose.runtime.withFrameNanos { }
            // Four utility cards + queue heading precede the actual meal categories.
            val nextCategory = remainingMeals.firstOrNull()?.mealType
            val categoryIndex = mealCategories.filter { category ->
                showEmptySlots || dayMeals.isEmpty() || renderedMeals.any { MealPlannerSupport.matchesMealCategory(it.mealType, category.canonicalName) }
            }.sortedBy { category -> if (renderedMeals.any { MealPlannerSupport.matchesMealCategory(it.mealType, category.canonicalName) }) 0 else 1 }
                .indexOfFirst { MealPlannerSupport.matchesMealCategory(nextCategory.orEmpty(), it.canonicalName) }.coerceAtLeast(0)
            val target = if (remainingMeals.isEmpty()) 4 else 5 + categoryIndex
            val index = target.coerceAtMost((plannerListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            if (reducedMotion) plannerListState.scrollToItem(index) else plannerListState.animateScrollToItem(index)
        }
        previousEatenCount = eatenMeals.size
    }
    val displayedCategories = mealCategories.filter { category ->
        showEmptySlots || dayMeals.isEmpty() || renderedMeals.any { MealPlannerSupport.matchesMealCategory(it.mealType, category.canonicalName) }
    }.sortedBy { category -> if (renderedMeals.any { MealPlannerSupport.matchesMealCategory(it.mealType, category.canonicalName) }) 0 else 1 }

    // Dialog states
    var mealToChangeRecipe by remember { mutableStateOf<MealPlanEntity?>(null) }
    var slotToAddRecipe by remember { mutableStateOf<MealCategorySpec?>(null) }
    var recipePickerSearch by remember { mutableStateOf("") }
    val recipeFilterRevision by viewModel.usability.revision.collectAsStateWithLifecycle()
    var slotToAddProduct by remember { mutableStateOf<MealCategorySpec?>(null) }

    var mealToMove by remember { mutableStateOf<MealPlanEntity?>(null) }
    var targetMoveDay by remember { mutableStateOf("Poniedziałek") }
    var targetMoveCategory by remember { mutableStateOf("Obiad") }

    var mealForInstructions by remember { mutableStateOf<MealPlanEntity?>(null) }

    var mealToEditServings by remember { mutableStateOf<MealPlanEntity?>(null) }
    var customServingsText by remember { mutableStateOf("1") }

    // Ingredient swap dialog state
    var showSwapDialog by remember { mutableStateOf(false) }
    var ingredientToSwap by remember { mutableStateOf("") }
    var mealForIngredientSwap by remember { mutableStateOf<MealPlanEntity?>(null) }
    var swapSaving by remember { mutableStateOf(false) }
    var swapError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MangoYellow,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = selectedDaySpec.displayWithDate,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = Cream
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.generateAiMealPlan(selectedDayName) }) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Generuj z AI",
                            tint = GoldAccent
                        )
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
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = refreshing,
            onRefresh = { viewModel.refreshCurrentDate(); viewModel.healthConnect.sync(force = true) }, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MealUndoCard(viewModel)
            SuccessFeedback(actionFeedback)
            SuccessFeedback(motionFeedback, motionMessage, tag = "meal_status_feedback")
            actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            com.example.ui.components.LegacyPlanRepairCard(mealPlans, viewModel) { selectedDayName = it }
            if (selectedDate == today) NutritionStatusBadge(NutritionStatus.IN_PROGRESS, "Dzień w toku")
            Column(Modifier.swipePlannerDays(selectedDayName) { selectedDayName = it }.testTag("planner_day_gesture")) {
                com.example.ui.components.PlanDateSelector(selectedDayName, { selectedDayName = it }, "planner_date", showDays = false)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                weekDays.forEach { daySpec ->
                    val dayDate = MealPlannerSupport.dateForDay(daySpec.canonicalName, selectedDate)!!.toString()
                    val isSelected = selectedDayName == dayDate
                    val mealsForDate = mealPlans.filter { MealPlannerSupport.matchesDay(it.dayOfWeek, dayDate) }
                    val plannedCount = com.example.viewmodel.MealQueueSupport.remaining(mealsForDate, dayDate).size
                    val eatenCount = com.example.viewmodel.MealQueueSupport.eaten(mealsForDate, dayDate).size
                    val isToday = dayDate == currentDate
                    val dropTarget = dragController.target() == dayDate
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .border(if (isToday || dropTarget) 1.5.dp else 0.dp,
                                if (dropTarget) LeafGreen else if (isToday) MangoYellow else Color.Transparent, RoundedCornerShape(18.dp))
                            .background(if (dropTarget) LeafGreen.copy(alpha = .2f) else if (isSelected) MutedSurface2 else Color.Transparent)
                            .onGloballyPositioned { dragController.register(dayDate, it.boundsInRoot(),
                                selectedDate.minusDays(selectedDate.dayOfWeek.value - 1L).toString()) }
                            .semantics { contentDescription = "$dayDate, zaplanowane: $plannedCount, zjedzone: $eatenCount" }
                            .clickable { selectedDayName = dayDate }
                            .testTag("planner_day_${daySpec.canonicalName}")
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = daySpec.shortLetter,
                            fontSize = 11.sp,
                            color = if (isSelected) MangoYellow else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) JuicyOrange else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = daySpec.dayNumber.toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isSelected) MangoButtonInk else GoldAccent
                            )
                        }
                        Row(Modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (plannedCount > 0) Box(Modifier.size(5.dp).clip(CircleShape).background(JuicyOrange))
                            if (eatenCount > 0) androidx.compose.material3.Icon(Icons.Default.CheckCircle, null, tint = LeafGreen, modifier = Modifier.size(11.dp))
                        }
                    }
                }
            }

            Text("Obwódka: dziś • ● zaplanowane • ✓ zjedzone", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
            HorizontalDivider(color = WarmBrown.copy(alpha = 0.3f))

            // Daily summary header + AI generate button
            val dailyNutrition = remember(dayMeals, recipes, recipeIngredients) {
                IngredientSubstitutionSupport.totalMealNutrition(dayMeals, recipes, recipeIngredients)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    MangoSectionTitle(text = "Posiłki • ${selectedDaySpec.canonicalName}")
                    key(selectedDayName) {
                        if (dayMeals.isEmpty()) Text("Zaplanowano: 0 posiłków", modifier = Modifier.testTag("daily_nutrition_summary"), style = MaterialTheme.typography.labelMedium)
                        else AnimatedMealNutrition(dailyNutrition, "daily_nutrition_summary", prefix = "Suma dnia: ")
                    }
                }

                Button(
                    onClick = { viewModel.generateAiMealPlan(selectedDayName) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JuicyOrange,
                        contentColor = MangoButtonInk
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.pressScaleEffect()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Generuj", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = OrangeInk,
                    trackColor = WarmBrown
                )
            }

            // All 5 meal categories (Śniadanie, II śniadanie, Obiad, Przekąska, Kolacja)
            LazyColumn(
                state = plannerListState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .mangoDayChange(selectedDate.toEpochDay())
                    .testTag("meal_plan_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { com.example.ui.components.ChangeHistoryCard(viewModel) }
                item { com.example.ui.components.NutritionAttentionCard(viewModel) }
                item { com.example.ui.components.SavedMealSetsCard(viewModel, selectedDayName) }
                item { com.example.ui.components.QuickMealsCard(viewModel, selectedDayName) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (remainingMeals.isEmpty() && eatenMeals.isNotEmpty()) "Wszystkie zaplanowane posiłki zjedzone ✓" else "Do zjedzenia: ${remainingMeals.size}", modifier = Modifier.weight(1f))
                        if (eatenMeals.isNotEmpty()) TextButton(onClick = { showEatenHistory = true }, modifier = Modifier.testTag("show_eaten_history")) { Text("Zjedzone (${eatenMeals.size})") }
                    }
                }
                items(displayedCategories, key = { it.canonicalName }) { categorySpec ->
                    val mealsInCategory = renderedMeals.filter {
                        MealPlannerSupport.matchesMealCategory(it.mealType, categorySpec.canonicalName)
                    }

                    MealCategorySection(
                        categorySpec = categorySpec,
                        meals = mealsInCategory,
                        activeMealIds = remainingMeals.map { it.id }.toSet(),
                        nextMealId = remainingMeals.firstOrNull()?.id,
                        recipes = recipes,
                        recipeIngredients = recipeIngredients,
                        onAddRecipeToSlot = {
                            recipePickerSearch = ""
                            slotToAddRecipe = categorySpec
                        },
                        onAddProductToSlot = { slotToAddProduct = categorySpec },
                        onDecreaseServings = { meal ->
                            val current = meal.servings
                            val next = if (abs(current - current.roundToInt()) < 0.01f && current >= 2f) {
                                current - 1f
                            } else {
                                (current - 0.5f).coerceAtLeast(0.5f)
                            }
                            viewModel.updateMealPlanServings(meal, next)
                        },
                        onIncreaseServings = { meal ->
                            val current = meal.servings
                            val next = if (abs(current - current.roundToInt()) < 0.01f) {
                                current + 1f
                            } else {
                                current + 0.5f
                            }
                            viewModel.updateMealPlanServings(meal, next)
                        },
                        onEditServingsClick = { meal ->
                            customServingsText = MealPlannerSupport.formatQuantityNumber(meal.servings)
                            mealToEditServings = meal
                        },
                        onSwapIngredientClick = { meal, formattedIng ->
                            mealForIngredientSwap = meal
                            ingredientToSwap = formattedIng
                            showSwapDialog = true
                            swapError = null
                        },
                        onViewInstructionsClick = { meal ->
                            mealForInstructions = meal
                        },
                        onChangeRecipeClick = { meal ->
                            recipePickerSearch = ""
                            mealToChangeRecipe = meal
                        },
                        onMoveMealClick = { meal ->
                            targetMoveDay = MealPlannerSupport.normalizeDayOfWeek(meal.dayOfWeek)
                            targetMoveCategory = MealPlannerSupport.normalizeMealType(meal.mealType)
                            mealToMove = meal
                        },
                        onRemoveMealClick = { meal ->
                            viewModel.deleteMealPlan(meal)
                        },
                        onToggleEatenClick = { meal ->
                            viewModel.toggleMealPlanCompleted(meal)
                        },
                        onIngredientQuantityChange = { meal, ingredient, quantity -> viewModel.editMealIngredientQuantity(meal, ingredient, quantity) },
                        viewModel = viewModel,
                        dragController = dragController
                    )
                }
                if (dayMeals.isNotEmpty()) item { TextButton(onClick = { showEmptySlots = !showEmptySlots }, modifier = Modifier.testTag("show_other_meal_slots")) { Text(if (showEmptySlots) "Ukryj puste pory posiłków" else "Zaplanuj dodatkowy posiłek") } }
            }
        }
        MealDragOverlay(dragController)
        }
    }
    cookingRecipe?.let { recipe ->
        val meal = cookingMeal
        val cookingIngredients = if (meal != null) com.example.viewmodel.CookingIngredientsSupport.fromMeal(
            IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(recipe, meal, recipeIngredients))
            else com.example.viewmodel.CookingIngredientsSupport.fromRecipe(recipe, recipeIngredients)
        CookingDialog(recipe, ingredients = cookingIngredients, initialServings = meal?.servings ?: 1f, sessionKey = "meal_${meal?.id ?: recipe.id}",
            portionLabel = if (meal != null) "Ilości łącznie: ${MealPlannerSupport.formatQuantityNumber(meal.servings)} porcji" else "Ilości dla całego przepisu") {
            cookingRecipe = null; cookingMeal = null
        }
    }

    if (showEatenHistory) com.example.ui.components.MangoEditSheet(onDismissRequest = { showEatenHistory = false },
        title = { Text("Zjedzone posiłki") }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                eatenMeals.forEach { meal ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text("${meal.mealType} • ${meal.recipeTitle}")
                        Text("${MealPlannerSupport.formatServingsLabel(meal.servings)} • ${meal.calories} kcal")
                        TextButton(onClick = { viewModel.toggleMealPlanCompleted(meal) }, modifier = Modifier.testTag("restore_eaten_${meal.id}")) { Text("Cofnij zjedzenie") }
                        com.example.ui.components.CopyMealButton(viewModel, meal)
                    } }
                }
                if (eatenMeals.isEmpty()) Text("Brak zjedzonych posiłków w tym dniu.")
            }
        }, confirmButton = { TextButton(onClick = { showEatenHistory = false }) { Text("Zamknij") } })

    // 1. Change Recipe or Add Recipe to Category Dialog
    if (mealToChangeRecipe != null || slotToAddRecipe != null) {
        val editingMeal = mealToChangeRecipe
        val targetSlot = slotToAddRecipe
        var pickerCategory by remember(editingMeal?.id, targetSlot?.canonicalName) { mutableStateOf("Wszystkie") }
        var pickerPreparation by remember(editingMeal?.id, targetSlot?.canonicalName) { mutableStateOf("Wszystkie czasy") }
        val filteredRecipes = remember(recipes, recipePickerSearch, pickerCategory, pickerPreparation, recipeFilterRevision) {
            recipes.filter { candidate -> com.example.viewmodel.RecipeFilterSupport.matches(candidate,
                recipePickerSearch, pickerCategory, pickerPreparation, viewModel.usability.minutes(candidate),
                viewModel.usability.tag(candidate.id, "nocook"), viewModel.usability.tag(candidate.id, "work")) }
        }
        val unknownTimes = recipes.count { viewModel.usability.minutes(it) == null }


        com.example.ui.components.MangoEditSheet(
            onDismissRequest = {
                mealToChangeRecipe = null
                slotToAddRecipe = null
            },
            containerColor = MutedSurface1,
            title = {
                Text(
                    text = if (editingMeal != null) "Zmień przepis (${editingMeal.mealType})"
                    else "Dodaj przepis do: ${targetSlot?.canonicalName.orEmpty()}",
                    color = Cream,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = recipePickerSearch,
                        onValueChange = { recipePickerSearch = it },
                        modifier = Modifier.fillMaxWidth().testTag("planner_recipe_search"),
                        singleLine = true,
                        placeholder = { Text("Szukaj w zapisanych przepisach...", color = Cream.copy(alpha = 0.5f), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MangoYellow) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JuicyOrange,
                            unfocusedBorderColor = WarmBrown,
                            focusedTextColor = Cream,
                            unfocusedTextColor = Cream
                        )
                    )

                    RecipeFilterChips(pickerCategory, { pickerCategory = it }, pickerPreparation, { pickerPreparation = it })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Wyniki: ${filteredRecipes.size} • Brak czasu: $unknownTimes", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                        if (recipePickerSearch.isNotBlank() || pickerCategory != "Wszystkie" || pickerPreparation != "Wszystkie czasy") {
                            TextButton(onClick = { recipePickerSearch = ""; pickerCategory = "Wszystkie"; pickerPreparation = "Wszystkie czasy" },
                                modifier = Modifier.testTag("clear_planner_recipe_filters")) { Text("Wyczyść") }
                        }
                    }
                    if (pickerPreparation != "Wszystkie czasy" && unknownTimes > 0) Text(
                        "Brakujący czas wpiszesz w przepisie: Czas i sposób przygotowania.", style = MaterialTheme.typography.bodySmall)
                    if (filteredRecipes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Brak pasujących przepisów.", color = Cream.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f).testTag("planner_recipe_results"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredRecipes, key = { it.id }) { candidate ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .mangoResultAppearance("$pickerCategory|$pickerPreparation|$recipePickerSearch")
                                        .clickable {
                                            if (editingMeal != null) {
                                                viewModel.changeMealPlanRecipe(editingMeal, candidate)
                                            } else if (targetSlot != null) {
                                                viewModel.addMealPlan(
                                                    dayOfWeek = selectedDayName,
                                                    mealType = targetSlot.canonicalName,
                                                    recipe = candidate,
                                                    servings = 1f,
                                                    onResult = { saved, error -> actionError = error; if (saved) actionFeedback++ }
                                                )
                                            }
                                            mealToChangeRecipe = null
                                            slotToAddRecipe = null
                                        }
                                        .testTag("pick_recipe_${candidate.id}")
                                        .border(1.dp, WarmBrown.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MutedSurface2)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = candidate.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Cream
                                        )
                                        val minutes = viewModel.usability.minutes(candidate)
                                        Text(com.example.viewmodel.RecipeCategories.categories(candidate.category).joinToString(" / ") {
                                            com.example.viewmodel.RecipeCategories.label(it)
                                        } + " • " + (minutes?.let { "$it min" } ?: "Brak czasu"),
                                            color = GoldAccent, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            text = "Źródło: ${candidate.source} • ${candidate.calories} kcal (B: ${candidate.protein}g W: ${candidate.carbs}g T: ${candidate.fat}g)",
                                            fontSize = 11.sp,
                                            color = MangoYellow
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        mealToChangeRecipe = null
                        slotToAddRecipe = null
                    }
                ) {
                    Text("Anuluj", color = Color.Gray)
                }
            }
        )
    }

    // 2. Move Meal to Another Day / Category Dialog
    mealToMove?.let { meal ->
        com.example.ui.components.MangoEditSheet(
            onDismissRequest = { mealToMove = null },
            containerColor = MutedSurface1,
            title = { Text("Przenieś posiłek", color = Cream, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = meal.recipeTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MangoYellow
                    )

                    com.example.ui.components.PlanDateSelector(targetMoveDay, { targetMoveDay = it }, "move_to_day")

                    Text("Wybierz kategorię posiłku:", fontSize = 12.sp, color = GoldAccent, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(mealCategories) { cat ->
                            FilterChip(
                                selected = targetMoveCategory == cat.canonicalName,
                                onClick = { targetMoveCategory = cat.canonicalName },
                                label = { Text(cat.canonicalName, fontSize = 11.sp) },
                                modifier = Modifier.testTag("move_to_category_${cat.canonicalName}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JuicyOrange,
                                    selectedLabelColor = DarkBackground
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                MangoPrimaryButton(
                    text = "Przenieś",
                    modifier = Modifier.testTag("confirm_move_meal_button"),
                    onClick = {
                        viewModel.moveMealPlan(meal, targetMoveDay, targetMoveCategory)
                        mealToMove = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { mealToMove = null }) {
                    Text("Anuluj", color = Color.Gray)
                }
            }
        )
    }

    // 3. Edit Servings Dialog
    mealToEditServings?.let { meal ->
        com.example.ui.components.MangoEditSheet(
            onDismissRequest = { mealToEditServings = null },
            containerColor = MutedSurface1,
            title = { Text("Zmień liczbę porcji", color = Cream, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(meal.recipeTitle, fontWeight = FontWeight.Bold, color = MangoYellow, fontSize = 14.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("0.5", "1", "1.5", "2", "3", "4")) { preset ->
                            FilterChip(
                                selected = customServingsText.trim() == preset,
                                onClick = { customServingsText = preset },
                                label = { Text("$preset por.", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JuicyOrange,
                                    selectedLabelColor = DarkBackground
                                )
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customServingsText,
                        onValueChange = { customServingsText = it },
                        label = { Text("Liczba porcji") },
                        modifier = Modifier.fillMaxWidth(),
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
                    text = "Zapisz",
                    onClick = {
                        val newServings = customServingsText.replace(',', '.').toFloatOrNull()?.coerceAtLeast(0.25f) ?: meal.servings
                        viewModel.updateMealPlanServings(meal, newServings)
                        mealToEditServings = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { mealToEditServings = null }) {
                    Text("Anuluj", color = Color.Gray)
                }
            }
        )
    }

    // 4. Complete Recipe Instructions Dialog
    mealForInstructions?.let { initialMeal ->
        val meal = mealPlans.find { it.id == initialMeal.id } ?: initialMeal
        val matchedRecipe = recipes.find { it.id == meal.recipeId }
            ?: recipes.find { it.title.equals(meal.recipeTitle, ignoreCase = true) }
        val resolvedIngredients = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(
            recipe = matchedRecipe,
            meal = meal,
            allIngredientEntities = recipeIngredients
        )
        val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(
            meal = meal,
            recipe = matchedRecipe,
            allIngredientEntities = recipeIngredients
        )

        AlertDialog(
            onDismissRequest = { mealForInstructions = null },
            containerColor = MutedSurface1,
            title = {
                Text(
                    text = MealPlannerSupport.formatMealHeader(meal.recipeTitle, meal.servings),
                    color = Cream,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (matchedRecipe != null && matchedRecipe.source.isNotBlank()) item {
                        Text("Źródło: ${matchedRecipe.source}", color = GoldAccent, fontSize = 11.sp)
                    }
                    item { Text(buildNutritionDisplayLine(nutrition), color = MangoYellow) }
                    item { Text("Składniki (${MealPlannerSupport.formatServingsLabel(meal.servings)}):", color = Cream, fontWeight = FontWeight.Bold) }
                    items(resolvedIngredients) { ingredient ->
                        Column {
                            Text("- ${ingredient.displayLine}", color = Cream, fontSize = 12.sp)
                            Text(IngredientSubstitutionSupport.ingredientEnergyLabel(ingredient.ingredientName, ingredient.quantityForMeal,
                                ingredient.unit, recipeIngredients.filter { it.recipeId == matchedRecipe?.id }, ingredient.substitution),
                                color = MangoYellow, fontSize = 11.sp)
                        }
                    }
                    if (matchedRecipe != null) item {
                        Button(onClick = { cookingRecipe = matchedRecipe; cookingMeal = meal; mealForInstructions = null }, modifier = Modifier.testTag("meal_start_cooking")) { Text("Gotuj krok po kroku") }
                    }
                    item { Text("Pełna instrukcja przygotowania:", color = Cream, fontWeight = FontWeight.Bold) }
                    item { Text(matchedRecipe?.instructions?.takeIf { it.isNotBlank() }
                        ?: "Brak zapisanej instrukcji przygotowania dla tego posiłku.", color = Cream, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                MangoPrimaryButton(
                    text = "Zamknij",
                    modifier = Modifier.testTag("close_meal_recipe_dialog"),
                    onClick = { mealForInstructions = null }
                )
            }
        )
    }

    slotToAddProduct?.let { slot ->
        MealProductAdditionDialog(viewModel, slot.canonicalName,
            onDismiss = { slotToAddProduct = null },
            onSave = { candidate, quantity, unit, done ->
                viewModel.addScannedProductToMeal(candidate, quantity, unit, selectedDayName, slot.canonicalName) { error ->
                    done(error)
                }
            })
    }

    // 5. Ingredient Swap/Replacement Dialog (Connected to Open Food Facts, Barcode Scanner, Local DB & AI)
    val targetMealForSwap = mealForIngredientSwap?.let { m -> mealPlans.find { it.id == m.id } ?: m }
    if (showSwapDialog && targetMealForSwap != null) {
        val swapRecipe = recipes.find { it.id == targetMealForSwap.recipeId }
        val missing = MealConvenienceSupport.missingIngredients(
            IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(swapRecipe, targetMealForSwap, recipeIngredients),
            recipeIngredients.filter { it.recipeId == swapRecipe?.id })
        val targetKey = IngredientSubstitutionSupport.normalizeIngredientKey(ingredientToSwap)
        IngredientReplacementDialog(
            originalIngredientLine = ingredientToSwap,
            recipeTitle = targetMealForSwap.recipeTitle,
            isForPlannedMeal = true,
            viewModel = viewModel,
            onDismiss = { if (!swapSaving) showSwapDialog = false },
            saving = swapSaving,
            saveError = swapError,
            mealPreview = { candidate, amount, unit ->
                val before = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(targetMealForSwap, swapRecipe, recipeIngredients)
                com.example.viewmodel.MealChangePreview.line(before, com.example.viewmodel.MealChangePreview.calculate(targetMealForSwap, swapRecipe, recipeIngredients, ingredientToSwap, candidate, amount, unit))
            },
            isNutritionRepair = missing.any { IngredientSubstitutionSupport.normalizeIngredientKey(it.originalIngredientName) == targetKey },
            onConfirmReplacement = { candidate, enteredQuantity, enteredUnit, alsoSaveToOriginalRecipe ->
                swapSaving = true; swapError = null
                viewModel.replaceIngredientInMealPlan(
                    meal = targetMealForSwap,
                    originalIngredientRaw = ingredientToSwap,
                    candidate = candidate,
                    enteredQuantity = enteredQuantity,
                    enteredUnit = enteredUnit,
                    alsoSaveToOriginalRecipe = alsoSaveToOriginalRecipe,
                    onResult = { saved, error -> swapSaving = false; swapError = error; if (saved) showSwapDialog = false }
                )
            }
        )
    }
}

@Composable
private fun MealCategorySection(
    categorySpec: MealCategorySpec,
    meals: List<MealPlanEntity>,
    activeMealIds: Set<Long>,
    nextMealId: Long?,
    recipes: List<RecipeEntity>,
    recipeIngredients: List<RecipeIngredientEntity>,
    onAddRecipeToSlot: () -> Unit,
    onAddProductToSlot: () -> Unit,
    onDecreaseServings: (MealPlanEntity) -> Unit,
    onIncreaseServings: (MealPlanEntity) -> Unit,
    onEditServingsClick: (MealPlanEntity) -> Unit,
    onSwapIngredientClick: (MealPlanEntity, String) -> Unit,
    onViewInstructionsClick: (MealPlanEntity) -> Unit,
    onChangeRecipeClick: (MealPlanEntity) -> Unit,
    onMoveMealClick: (MealPlanEntity) -> Unit,
    onRemoveMealClick: (MealPlanEntity) -> Unit,
    onToggleEatenClick: (MealPlanEntity) -> Unit,
    onIngredientQuantityChange: (MealPlanEntity, ResolvedMealIngredientItem, Float) -> Unit,
    viewModel: DietaViewModel,
    dragController: MealDragController
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("meal_category_section_${categorySpec.canonicalName}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Icon(mealCategoryIcon(categorySpec.canonicalName), null,
                    tint = mealCategoryColor(categorySpec.canonicalName), modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(categorySpec.canonicalName, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        color = mealCategoryColor(categorySpec.canonicalName))
                    Text(categorySpec.timeRange, fontSize = 11.sp, color = GoldAccent)
                }
            }

            TextButton(
                onClick = onAddRecipeToSlot,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.testTag("add_to_slot_${categorySpec.canonicalName}")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MangoYellow, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("Dodaj przepis", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MangoYellow)
            }
        }

        TextButton(onClick = onAddProductToSlot,
            modifier = Modifier.align(Alignment.End).testTag("add_product_to_slot_${categorySpec.canonicalName}"),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Dodaj produkt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        if (meals.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, WarmBrown.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = MutedSurface1.copy(alpha = 0.65f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Brak zaplanowanego posiłku (${categorySpec.canonicalName})",
                        fontSize = 12.sp,
                        color = Cream.copy(alpha = 0.55f)
                    )
                    Text(
                        text = "Wybierz →",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldAccent,
                        modifier = Modifier.clickable { onAddRecipeToSlot() }
                    )
                }
            }
        } else {
            meals.forEach { meal ->
                val matchedRecipe = recipes.find { it.id == meal.recipeId }
                    ?: recipes.find { it.title.equals(meal.recipeTitle, ignoreCase = true) }

                key(meal.id) {
                MealQueueMotionItem(visible = meal.id in activeMealIds) {
                PlannedMealDetailCard(
                    meal = meal,
                    interactionsEnabled = meal.id in activeMealIds,
                    isUpcoming = meal.id == nextMealId,
                    recipe = matchedRecipe,
                    recipeIngredients = recipeIngredients,
                    timeRange = categorySpec.timeRange,
                    onDecreaseServings = { onDecreaseServings(meal) },
                    onIncreaseServings = { onIncreaseServings(meal) },
                    onEditServingsClick = { onEditServingsClick(meal) },
                    onSwapIngredientClick = { ingLine -> onSwapIngredientClick(meal, ingLine) },
                    onViewInstructionsClick = { onViewInstructionsClick(meal) },
                    onChangeRecipeClick = { onChangeRecipeClick(meal) },
                    onMoveMealClick = { onMoveMealClick(meal) },
                    onRemoveMealClick = { onRemoveMealClick(meal) },
                    onToggleEatenClick = { onToggleEatenClick(meal) },
                    onIngredientQuantityChange = { ingredient, quantity -> onIngredientQuantityChange(meal, ingredient, quantity) },
                    viewModel = viewModel,
                    dragController = dragController
                )
                }
                }
            }
        }
    }
}

@Composable
internal fun PlannedMealDetailCard(
    meal: MealPlanEntity,
    recipe: RecipeEntity?,
    recipeIngredients: List<RecipeIngredientEntity>,
    timeRange: String,
    onDecreaseServings: () -> Unit,
    onIncreaseServings: () -> Unit,
    onEditServingsClick: () -> Unit,
    onSwapIngredientClick: (String) -> Unit,
    onViewInstructionsClick: () -> Unit,
    onChangeRecipeClick: () -> Unit,
    onMoveMealClick: () -> Unit,
    onRemoveMealClick: () -> Unit,
    onToggleEatenClick: () -> Unit,
    onIngredientQuantityChange: ((ResolvedMealIngredientItem, Float) -> Unit)? = null,
    viewModel: DietaViewModel? = null,
    dragController: MealDragController? = null,
    interactionsEnabled: Boolean = true,
    isUpcoming: Boolean = false
) {
    val statusBusy = if (viewModel != null) meal.id in viewModel.mealStatusBusy.collectAsStateWithLifecycle().value else false
    var expanded by remember(meal.id) { mutableStateOf(false) }
    var actionsOpen by remember(meal.id) { mutableStateOf(false) }
    var copying by remember(meal.id) { mutableStateOf(false) }
    var batching by remember(meal.id) { mutableStateOf(false) }
    val quantityGuard = remember(meal.id) { com.example.ui.components.QuantityReviewGuard() }
    val resolvedIngredients = remember(recipe, meal, recipeIngredients) {
        IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(
            recipe = recipe,
            meal = meal,
            allIngredientEntities = recipeIngredients
        )
    }
    val nutrition = remember(meal, recipe, recipeIngredients) {
        IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(
            meal = meal,
            recipe = recipe,
            allIngredientEntities = recipeIngredients
        )
    }
    val missingIngredients = remember(resolvedIngredients, recipeIngredients, recipe) {
        MealConvenienceSupport.missingIngredients(resolvedIngredients, recipeIngredients.filter { it.recipeId == recipe?.id })
    }
    var repairIngredientName by rememberSaveable(meal.id) { mutableStateOf<String?>(null) }
    var showMissing by rememberSaveable(meal.id) { mutableStateOf(false) }
    var repairQuantity by rememberSaveable(meal.id) { mutableStateOf("") }
    var repairSaving by remember { mutableStateOf(false) }
    var repairError by remember { mutableStateOf<String?>(null) }
    val repairIngredient = resolvedIngredients.find { it.originalIngredientName == repairIngredientName }
    if (repairIngredient != null && viewModel != null) {
        com.example.ui.components.MangoEditSheet(dismissEnabled = !repairSaving, onDismissRequest = { if (!repairSaving) repairIngredientName = null }, title = { Text("Uzupełnij: ${repairIngredient.ingredientName}") }, text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text("Podaj ilość tego składnika w całym widocznym posiłku, a następnie odczytaj lub wpisz wartości z etykiety.")
                OutlinedTextField(repairQuantity, { repairQuantity = it }, label = { Text("Ilość składnika (g lub ml)") }, singleLine = true)
                repairError?.let { Text(it) }
                val quantity = repairQuantity.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 10000 }
                Box(Modifier.fillMaxWidth().height(360.dp)) {
                NutritionLabelScreen(initialName = repairIngredient.ingredientName, onBack = { if (!repairSaving) repairIngredientName = null }, onProduct = { product ->
                    if (quantity != null && !repairSaving) {
                        repairSaving = true; repairError = null
                        viewModel.replaceIngredientInMealPlan(meal,
                            "${repairIngredient.originalIngredientName} — ${MealPlannerSupport.formatQuantityNumber((repairIngredient.originalBaseQuantity ?: 1f) * meal.servings)} ${repairIngredient.originalUnit}",
                            product, quantity, product.defaultUnit,
                            onResult = { saved, error -> repairSaving = false; repairError = error; if (saved) repairIngredientName = null })
                    }
                }, canSubmit = quantity != null && !repairSaving, saveMessage = if (repairSaving) "Zapisywanie…" else if (quantity == null) "Podaj poprawną ilość składnika u góry." else null)
                }
            }
        }, confirmButton = {})
    }
    var showAddProduct by remember { mutableStateOf(false) }
    if (showAddProduct && viewModel != null) {
        MealProductAdditionDialog(viewModel, meal.recipeTitle,
            onDismiss = { showAddProduct = false },
            onSave = { candidate, quantity, unit, done ->
                viewModel.addMealIngredient(meal, candidate, quantity, unit) { saved, error ->
                    done(if (saved) null else error ?: "Nie udało się zapisać produktu.")
                }
            })
    }
    var showGramsDialog by remember { mutableStateOf(false) }
    var showCookedWeight by remember { mutableStateOf(false) }
    var eatenGramsText by rememberSaveable(meal.id) { mutableStateOf("") }
    var portionGramsText by rememberSaveable(meal.id, recipe?.id) { mutableStateOf("") }
    var gramsError by remember { mutableStateOf<String?>(null) }
    var savingGrams by remember { mutableStateOf(false) }
    if (showCookedWeight && recipe != null && viewModel != null) {
        com.example.ui.components.CookedWeightDialog(recipe, viewModel, mealId = meal.id, onDismiss = { showCookedWeight = false }, onSaved = {
            portionGramsText = MealPlannerSupport.formatQuantityNumber(it)
            showCookedWeight = false
        })
    }
    if (showGramsDialog && viewModel != null) {
        com.example.ui.components.MangoEditSheet(dismissEnabled = !savingGrams, onDismissRequest = { if (!savingGrams) showGramsDialog = false },
            title = { Text("Ile zjedzono?") },
            text = {
                com.example.ui.components.MealGramsContent(
                    portionText = portionGramsText, eatenText = eatenGramsText,
                    onPortionChange = { portionGramsText = it; gramsError = null },
                    onEatenChange = { eatenGramsText = it; gramsError = null },
                    caloriesPerServing = (nutrition.calories ?: meal.calories) / MealPlannerSupport.safeServings(meal.servings),
                    saving = savingGrams, error = gramsError,
                    nutritionPreview = { servings ->
                        val next = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal.copy(servings = servings), recipe, recipeIngredients)
                        com.example.viewmodel.MealChangePreview.line(nutrition, next)
                    },
                    onSave = { eaten, portion ->
                        savingGrams = true
                        viewModel.recordMealGrams(meal, eaten, portion) { success, error ->
                            savingGrams = false
                            if (success) showGramsDialog = false else gramsError = error
                        }
                    })
            },
            confirmButton = {},
            dismissButton = { TextButton(enabled = !savingGrams, onClick = { showGramsDialog = false }) { Text("Anuluj") } })
    }
    var removingIngredient by remember { mutableStateOf<ResolvedMealIngredientItem?>(null) }
    var ingredientError by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf(false) }
    removingIngredient?.let { target ->
        AlertDialog(onDismissRequest = { if (!removing) removingIngredient = null },
            title = { Text("Usunąć składnik?") },
            text = { Text("${target.ingredientName} zostanie usunięty z tego posiłku. Przepis w bazie pozostanie bez zmian.") },
            confirmButton = { TextButton(enabled = !removing, onClick = {
                removing = true
                viewModel?.removeMealIngredient(meal, target) { success, error ->
                    removing = false
                    if (success) removingIngredient = null else ingredientError = error
                }
            }) { Text("Usuń") } },
            dismissButton = { TextButton(enabled = !removing, onClick = { removingIngredient = null }) { Text("Anuluj") } })
    }
    var quantityTarget by remember { mutableStateOf<ResolvedMealIngredientItem?>(null) }
    var quantityText by remember { mutableStateOf("") }
    quantityTarget?.let { ingredient -> com.example.ui.components.MangoEditSheet(onDismissRequest = { quantityTarget = null },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.fillMaxWidth(0.95f),
        title = { Text(ingredient.ingredientName) }, text = { Column {
            OutlinedTextField(quantityText, { quantityText = it }, label = { Text("Ilość w tym posiłku (${if (ingredient.unit in listOf("ml", "l")) "ml" else "g"})") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("meal_ingredient_quantity_input"))
            val amount = quantityText.replace(',', '.').toFloatOrNull()
            val profile = IngredientSubstitutionSupport.lookupKnownIngredientNutrition(ingredient.ingredientName, recipeIngredients.filter { it.recipeId == recipe?.id })
            val sub = ingredient.substitution
            val candidate = com.example.viewmodel.SubstituteCandidate(name = ingredient.ingredientName,
                caloriesPer100g = sub?.caloriesPer100g ?: profile?.caloriesPer100g,
                proteinPer100g = sub?.proteinPer100g ?: profile?.proteinPer100g,
                carbsPer100g = sub?.carbsPer100g ?: profile?.carbsPer100g,
                fatPer100g = sub?.fatPer100g ?: profile?.fatPer100g)
            if (amount != null && amount.isFinite() && amount > 0 && amount <= 100000) {
                Text(com.example.viewmodel.MealChangePreview.line(nutrition,
                    com.example.viewmodel.MealChangePreview.calculate(meal, recipe, recipeIngredients, ingredient.originalIngredientName, candidate, amount,
                        if (ingredient.unit in listOf("ml", "l")) "ml" else "g")))
            }
        } }, confirmButton = { TextButton(modifier = Modifier.testTag("save_meal_ingredient_quantity"), enabled = quantityText.replace(',', '.').toFloatOrNull()?.let { it.isFinite() && it > 0 && it <= 100000 } == true,
            onClick = {
                val amount = quantityText.replace(',', '.').toFloat()
                val unit = if (ingredient.unit in listOf("ml", "l")) "ml" else "g"
                val oldAmount = ingredient.quantityForMeal?.let { IngredientSubstitutionSupport.quantityInGramsOrMl(ingredient.ingredientName, it, ingredient.unit, recipeIngredients.filter { row -> row.recipeId == recipe?.id }) }
                quantityGuard.run(com.example.viewmodel.SubstituteCandidate(ingredient.ingredientName), amount, unit, oldAmount) {
                    onIngredientQuantityChange?.invoke(ingredient, amount); quantityTarget = null
                }
            }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = { quantityTarget = null }) { Text("Anuluj") } }) }
    val headerText = MealPlannerSupport.formatMealHeader(meal.recipeTitle, meal.servings)

    MangoCard(
        isSelected = MealPlannerSupport.completedInCurrentWeek(meal),
        modifier = Modifier
            .mangoExpand()
            .testTag("planned_meal_card_${meal.id}")
    ) {
        Column(
            modifier = Modifier
                .background(mealCategoryColor(meal.mealType).copy(alpha = .045f))
                .drawBehind { drawRect(mealCategoryColor(meal.mealType), size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isUpcoming) Surface(color = mealCategoryColor(meal.mealType).copy(alpha = .13f), shape = RoundedCornerShape(10.dp)) {
                Text("Najbliższy posiłek", Modifier.padding(horizontal = 10.dp, vertical = 6.dp).testTag("upcoming_meal_${meal.id}"),
                    color = mealCategoryColor(meal.mealType), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            // Top status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (MealPlannerSupport.completedInCurrentWeek(meal)) LeafGreen else mealCategoryColor(meal.mealType))
                    )
                    androidx.compose.material3.Icon(mealCategoryIcon(meal.mealType), null,
                        tint = mealCategoryColor(meal.mealType), modifier = Modifier.size(18.dp))
                    Text(
                        text = "${MealPlannerSupport.normalizeMealType(meal.mealType)} • $timeRange",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (MealPlannerSupport.completedInCurrentWeek(meal)) LeafGreen else mealCategoryColor(meal.mealType)
                    )
                }
                if (MealPlannerSupport.completedInCurrentWeek(meal)) {
                    Text(
                        text = "Zjedzone ✓",
                        color = LeafGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                dragController?.let { controller ->
                    IconButton(onClick = onMoveMealClick,
                        modifier = Modifier.mealDragHandle(controller, meal).testTag("drag_meal_${meal.id}")) {
                        androidx.compose.material3.Icon(Icons.Default.DragIndicator, "Przytrzymaj i przeciągnij posiłek na dzień kalendarza", tint = GoldAccent)
                    }
                }
            }

            // Title + Image + Macros + Serving Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RecipePhoto(showIllustrationLabel = false, 
                    imageUrl = recipe?.imageUrl.orEmpty(),
                    title = meal.recipeTitle,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = headerText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Cream,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    AnimatedMealNutrition(nutrition, "animated_meal_nutrition_${meal.id}")
                }
            }

            if (expanded) MealConvenienceSupport.batchPortionWeight(meal.id, recipeIngredients)?.let { weight ->
                Text("Gotowe danie: ${MealPlannerSupport.formatQuantityNumber(weight * MealPlannerSupport.safeServings(meal.servings))} g w tej porcji", color = MangoYellow, fontSize = 12.sp)
            }
            if (expanded) nutrition.note?.let { Text(it, color = GoldAccent, fontSize = 12.sp) }
            NutritionStatusBadge(if (missingIngredients.isEmpty() && !nutrition.isPartial) NutritionStatus.COMPLETE else NutritionStatus.MISSING,
                if (missingIngredients.isEmpty() && !nutrition.isPartial) "Pełne dane kalorii i makro" else "Niepełne dane (${missingIngredients.size})",
                Modifier.testTag("nutrition_completeness_${meal.id}"))
            if (expanded) Text("Rozpoznane produkty dostają kalorie i makro automatycznie, także po odmianie nazwy. Szacunki z tabeli oraz masy jajek i ząbków są oznaczone. Zapisane przez Ciebie wartości mają pierwszeństwo.", color = GoldAccent, fontSize = 12.sp)
            if (expanded && missingIngredients.isNotEmpty()) {
                TextButton(onClick = { showMissing = !showMissing; expanded = true }, modifier = Modifier.testTag("repair_nutrition_${meal.id}")) { Text(if (showMissing) "Zwiń brakujące dane" else "Uzupełnij brakujące dane (${missingIngredients.size})") }
                if (showMissing) missingIngredients.forEach { item ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(item.ingredientName, Modifier.weight(1f).padding(top = 12.dp), fontSize = 12.sp)
                        TextButton(onClick = { onSwapIngredientClick("${item.originalIngredientName} — ${MealPlannerSupport.formatQuantityNumber((item.originalBaseQuantity ?: 1f) * meal.servings)} ${item.originalUnit}") }) { Text("Baza") }
                        if (viewModel != null) TextButton(onClick = {
                            repairIngredientName = item.originalIngredientName
                            repairError = null
                            repairQuantity = item.quantityForMeal?.let { IngredientSubstitutionSupport.quantityInGramsOrMl(item.ingredientName, it, item.unit, recipeIngredients.filter { row -> row.recipeId == recipe?.id }) }?.let { MealPlannerSupport.formatQuantityNumber(it) }.orEmpty()
                        }) { Text("Etykieta") }
                    }
                }
            }
            TextButton(enabled = interactionsEnabled, onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("toggle_meal_details_${meal.id}")) {
                Text(if (expanded) "Zwiń szczegóły −" else "Składniki i szczegóły +", color = MangoYellow)
            }
            if (expanded) {
            if (viewModel != null) com.example.ui.components.SaveSingleMealSetButton(viewModel, meal)
            // Serving adjustment bar
            Surface(
                color = MutedSurface2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Porcje posiłku:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Cream.copy(alpha = 0.85f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedIconButton(
                            onClick = onDecreaseServings,
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("decrease_servings_${meal.id}"),
                            border = BorderStroke(1.dp, WarmBrown)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Zmniejsz porcje",
                                tint = MangoYellow,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = MealPlannerSupport.formatServingsLabel(meal.servings),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Cream,
                            modifier = Modifier
                                .clickable { onEditServingsClick() }
                                .testTag("servings_badge_${meal.id}")
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )

                        OutlinedIconButton(
                            onClick = onIncreaseServings,
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("increase_servings_${meal.id}"),
                            border = BorderStroke(1.dp, JuicyOrange)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Zwiększ porcje",
                                tint = OrangeInk,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            ingredientError?.let { Text(it, color = MangoYellow) }
            // Individual Ingredients with Scaled Quantities & Units + "Zamień" Button
            if (resolvedIngredients.isNotEmpty()) {
                Surface(
                    color = MutedSurface2.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Składniki (${MealPlannerSupport.formatServingsLabel(meal.servings)}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent
                        )
                        resolvedIngredients.forEach { ing ->
                            val formattedLine = ing.displayLine
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("- $formattedLine", fontSize = 12.sp,
                                            color = if (ing.substitution != null) MangoYellow else Cream,
                                            fontWeight = if (ing.substitution != null) FontWeight.Bold else FontWeight.Normal)
                                        Text(IngredientSubstitutionSupport.ingredientEnergyLabel(ing.ingredientName, ing.quantityForMeal, ing.unit,
                                            recipeIngredients.filter { it.recipeId == recipe?.id }, ing.substitution),
                                            fontSize = 11.sp, color = MangoYellow)
                                    }
                                    if (viewModel != null) IconButton(modifier = Modifier.testTag("remove_meal_ingredient_${meal.id}_${ing.originalIngredientName}"),
                                        onClick = { removingIngredient = ing; ingredientError = null }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Usuń składnik ${ing.ingredientName}", tint = MangoYellow)
                                    }
                                    if (onIngredientQuantityChange != null) IconButton(modifier = Modifier.testTag("edit_ingredient_quantity_${meal.id}_${ing.originalIngredientName}"), onClick = { quantityTarget = ing; quantityText = ing.quantityForMeal?.let { IngredientSubstitutionSupport.quantityInGramsOrMl(ing.ingredientName, it, ing.unit, recipeIngredients.filter { row -> row.recipeId == recipe?.id }) }?.toString().orEmpty() }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Zmień gramaturę", tint = MangoYellow)
                                    }
                                    TextButton(
                                        onClick = { onSwapIngredientClick("${ing.originalIngredientName} — ${MealPlannerSupport.formatQuantityNumber((ing.originalBaseQuantity ?: 1f) * meal.servings)} ${ing.originalUnit}") },
                                        modifier = Modifier
                                            .height(26.dp)
                                            .testTag("swap_ingredient_${meal.id}_${ing.originalIngredientName}"),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwapHoriz,
                                            contentDescription = "Zamień",
                                            modifier = Modifier.size(13.dp),
                                            tint = MangoYellow
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Zamień", fontSize = 11.sp, color = MangoYellow, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (ing.substitution != null && !IngredientSubstitutionSupport.isExtra(ing.substitution)) {
                                    Text(
                                        text = "Oryginał: ${ing.substitution.formatOriginalForServings(meal.servings)}",
                                        fontSize = 10.sp,
                                        color = Cream.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(start = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = WarmBrown.copy(alpha = 0.3f))

            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(enabled = interactionsEnabled, onClick = { actionsOpen = true }, modifier = Modifier.testTag("meal_options_${meal.id}")) {
                        androidx.compose.material3.Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp))
                        Text("Opcje")
                    }
                    MangoActionsMenu(expanded = actionsOpen, onDismiss = { actionsOpen = false }) {
                        DropdownMenuItem(text = { Text("Przepis / gotowanie") }, onClick = { actionsOpen = false; onViewInstructionsClick() }, modifier = Modifier.testTag("view_instructions_${meal.id}"))
                        if (viewModel != null) {
                            DropdownMenuItem(text = { Text("Dodaj produkt") }, onClick = { actionsOpen = false; showAddProduct = true }, modifier = Modifier.testTag("add_product_to_meal_${meal.id}"))
                            DropdownMenuItem(text = { Text("Zjedzone gramy") }, onClick = {
                                actionsOpen = false
                                if (portionGramsText.isBlank()) MealConvenienceSupport.batchPortionWeight(meal.id, recipeIngredients)?.let {
                                    portionGramsText = MealPlannerSupport.formatQuantityNumber(it)
                                }
                                if (portionGramsText.isBlank()) recipe?.let { QuickUseSupport.cookedWeight(it) }?.let {
                                    portionGramsText = MealPlannerSupport.formatQuantityNumber(QuickUseSupport.portionWeight(it.first, it.second))
                                }
                                showGramsDialog = true; gramsError = null
                            }, modifier = Modifier.testTag("enter_meal_grams_${meal.id}"))
                            if (recipe != null) DropdownMenuItem(text = { Text("Waga gotowego dania") }, onClick = { actionsOpen = false; showCookedWeight = true })
                            DropdownMenuItem(text = { Text("Kopiuj na inny dzień") }, onClick = { actionsOpen = false; copying = true }, modifier = Modifier.testTag("copy_meal_${meal.id}"))
                            if (recipe != null) DropdownMenuItem(text = { Text("Gotowanie na kilka dni") }, onClick = { actionsOpen = false; batching = true }, modifier = Modifier.testTag("cooked_batch_${meal.id}"))
                        }
                        DropdownMenuItem(text = { Text("Edytuj porcje") }, onClick = { actionsOpen = false; onEditServingsClick() })
                        DropdownMenuItem(text = { Text("Zmień przepis") }, onClick = { actionsOpen = false; onChangeRecipeClick() }, modifier = Modifier.testTag("change_recipe_${meal.id}"))
                        DropdownMenuItem(text = { Text("Przenieś") }, onClick = { actionsOpen = false; onMoveMealClick() }, modifier = Modifier.testTag("move_meal_${meal.id}"))
                        DropdownMenuItem(text = { Text("Usuń posiłek") }, onClick = { actionsOpen = false; onRemoveMealClick() }, modifier = Modifier.testTag("remove_meal_${meal.id}"))
                    }
                }
                Button(enabled = interactionsEnabled && !statusBusy, onClick = onToggleEatenClick, modifier = Modifier.weight(1f).testTag("toggle_eaten_${meal.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = if (MealPlannerSupport.completedInCurrentWeek(meal)) LeafGreen else JuicyOrange, contentColor = MangoButtonInk)) {
                    Text(if (statusBusy) "Zapisywanie…" else if (MealPlannerSupport.completedInCurrentWeek(meal)) "Cofnij zjedzenie" else "Zjedzone ✓")
                }
            }
        }
    }
    if (copying && viewModel != null) com.example.ui.components.CopyMealButton(viewModel, meal, showButton = false, onClosed = { copying = false })
    if (batching && viewModel != null && recipe != null) com.example.ui.components.CookedBatchButton(viewModel, meal, recipe, showButton = false, onClosed = { batching = false })
    com.example.ui.components.QuantityReviewPrompt(quantityGuard)
}

private fun buildNutritionDisplayLine(nutrition: com.example.viewmodel.ScaledMealNutrition): String {
    val parts = mutableListOf<String>()
    nutrition.calories?.let { parts.add("$it kcal") }
    if (nutrition.hasMacros) {
        nutrition.protein?.let { parts.add("B: ${it}g") }
        nutrition.carbs?.let { parts.add("W: ${it}g") }
        nutrition.fat?.let { parts.add("T: ${it}g") }
    }
    return if (parts.isEmpty()) {
        "Brak danych o wartościach odżywczych"
    } else {
        parts.joinToString(" • ") + if (nutrition.isPartial) " • niepełne dane" else ""
    }
}
