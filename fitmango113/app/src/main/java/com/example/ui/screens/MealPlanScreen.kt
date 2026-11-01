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

    var showSwapDialog by remember { mutableStateOf(false) }
    var ingredientToSwap by remember { mutableStateOf("") }
    var mealForIngredientSwap by remember { mutableStateOf<MealPlanEntity?>(null) }
    var swapSaving by remember { mutableStateOf(false) }
    var swapError by remember { mutableStateOf<String?>(null) }

    val dailyNutrition = remember(dayMeals, recipes, recipeIngredients) {
        IngredientSubstitutionSupport.totalMealNutrition(dayMeals, recipes, recipeIngredients)
    }
    val dailyCompletion = remember(dayMeals, eatenMeals.size) {
        if (dayMeals.isEmpty()) 0f else (eatenMeals.size.toFloat() / dayMeals.size.toFloat()).coerceIn(0f, 1f)
    }
    val dayRegularity = remember(dayMeals, mealPlans) {
        if (dayMeals.isEmpty()) 0f else (dayMeals.count { it.completed == true || it.completed == null }.toFloat() / dayMeals.size.toFloat()).coerceIn(0f, 1f)
    }

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

            PremiumNutritionSummaryCard(
                calories = dailyNutrition.calories?.toInt() ?: 0,
                protein = dailyNutrition.protein?.toInt() ?: 0,
                carbs = dailyNutrition.carbs?.toInt() ?: 0,
                fat = dailyNutrition.fat?.toInt() ?: 0,
                mealsPlanned = dayMeals.size,
                mealsEaten = eatenMeals.size,
                mealsRemaining = remainingMeals.size,
                completion = dailyCompletion,
                regularity = dayRegularity
            )

            item { NutritionWeekCard(viewModel) }

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
                        Text(if (remainingMeals.isEmpty() && eatenMeals.isNotEmpty()) "Wszystkie zaplanowane posiłki zjedzone ✓" else "Do zjedzenia: ${remainingMeals.size}", modifier = Modifier.testTag("meal_list_counter"), style = MaterialTheme.typography.labelLarge)
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
                if (dayMeals.isNotEmpty()) item { TextButton(onClick = { showEmptySlots = !showEmptySlots }, modifier = Modifier.testTag("show_other_meal_slots")) { Text(if (showEmptySlots) "Ukryj inne sloty" else "Pokaż pozostałe sloty") } }
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

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = pickerCategory == "Wszystkie", onClick = { pickerCategory = "Wszystkie" }, label = { Text("Wszystkie") }) }
                        item { FilterChip(selected = pickerCategory == "Śniadanie", onClick = { pickerCategory = "Śniadanie" }, label = { Text("Śniadanie") }) }
                        item { FilterChip(selected = pickerCategory == "Obiad", onClick = { pickerCategory = "Obiad" }, label = { Text("Obiad") }) }
                        item { FilterChip(selected = pickerCategory == "Kolacja", onClick = { pickerCategory = "Kolacja" }, label = { Text("Kolacja") }) }
                    }

                    if (filteredRecipes.isEmpty()) {
                        Text("Brak przepisów spełniających filtrowanie.", color = GoldAccent)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredRecipes, key = { it.id }) { recipe ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        if (editingMeal != null) {
                                            viewModel.changeMealRecipe(editingMeal, recipe)
                                            mealToChangeRecipe = null
                                        } else {
                                            viewModel.addMealToDay(selectedDayName, targetSlot?.canonicalName ?: "Obiad", recipe)
                                            slotToAddRecipe = null
                                        }
                                    },
                                    colors = CardDefaults.cardColors(containerColor = MutedSurface2)
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = recipe.imageUrl ?: "",
                                            contentDescription = null,
                                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(recipe.title, fontWeight = FontWeight.Bold, color = Cream)
                                            Text("${recipe.calories} kcal • ${recipe.category}", color = GoldAccent, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { mealToChangeRecipe = null; slotToAddRecipe = null }) { Text("Zamknij") } }
        )
    }
}

@Composable
private fun PremiumNutritionSummaryCard(
    calories: Int,
    protein: Int,
    carbs: Int,
    fat: Int,
    mealsPlanned: Int,
    mealsEaten: Int,
    mealsRemaining: Int,
    completion: Float,
    regularity: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MutedSurface1)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Bilans żywieniowy", fontWeight = FontWeight.Bold, color = Cream, fontSize = 16.sp)
                    Text("Dzień • podsumowanie zgodne z aktualnym stanem aplikacji", color = GoldAccent, fontSize = 11.sp)
                }
                Surface(
                    color = JuicyOrange.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (mealsRemaining == 0) "Dzień zakończony" else "Do zjedzenia: $mealsRemaining",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = MangoYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MacroMetricCell(label = "Kalorie", value = "${calories} kcal", tint = JuicyOrange, modifier = Modifier.weight(1f))
                MacroMetricCell(label = "Białko", value = "${protein} g", tint = LeafGreen, modifier = Modifier.weight(1f))
                MacroMetricCell(label = "Węglowodany", value = "${carbs} g", tint = MangoYellow, modifier = Modifier.weight(1f))
                MacroMetricCell(label = "Tłuszcze", value = "${fat} g", tint = OrangeInk, modifier = Modifier.weight(1f))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Realizacja dnia", color = GoldAccent, fontSize = 11.sp)
                MangoProgressBar(completion.coerceIn(0f, 1f), color = LeafGreen, trackColor = MutedSurface2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${(completion * 100).toInt()}% realizacji", color = Cream, fontWeight = FontWeight.Bold)
                    Text("${mealsEaten}/${mealsPlanned} posiłków", color = GoldAccent)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SmallNutritionStat(label = "Regularność", value = "${(regularity * 100).toInt()}%", tint = GoldAccent, modifier = Modifier.weight(1f))
                SmallNutritionStat(label = "Zjedzone", value = "$mealsEaten", tint = LeafGreen, modifier = Modifier.weight(1f))
                SmallNutritionStat(label = "Pozostałe", value = "$mealsRemaining", tint = OrangeInk, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MacroMetricCell(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MutedSurface2)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = GoldAccent, fontSize = 10.sp)
            Text(value, color = tint, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SmallNutritionStat(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MutedSurface2)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = GoldAccent, fontSize = 9.sp)
            Text(value, color = tint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SuccessFeedback(feedback: Int, tag: String = "meal_status_feedback") {
    if (feedback > 0) {
        Text(
            text = "Zapisano",
            color = LeafGreen,
            modifier = Modifier.testTag(tag),
            fontSize = 12.sp
        )
    }
}
