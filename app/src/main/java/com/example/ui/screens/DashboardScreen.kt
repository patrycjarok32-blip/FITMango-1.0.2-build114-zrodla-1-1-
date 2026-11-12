package com.example.ui.screens

import com.example.ui.components.FitMangoIcon as Icon

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.DietaViewModel
import com.example.viewmodel.HealthConnectSupport
import com.example.viewmodel.MealPlannerSupport
import com.example.viewmodel.IngredientSubstitutionSupport
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DietaViewModel,
    onNavigateToAiCoach: () -> Unit,
    onNavigateToRecipes: () -> Unit,
    onNavigateToRecipeImport: () -> Unit = onNavigateToRecipes,
    onNavigateToMealPlan: () -> Unit,
    onNavigateToBarcodeScanner: () -> Unit,
    onNavigateToMeasurements: () -> Unit,
    onNavigateToDocuments: () -> Unit,
    onNavigateToProductPhoto: () -> Unit = onNavigateToBarcodeScanner,
    onNavigateToSport: () -> Unit = {},
    onNavigateToEffects: () -> Unit = {}
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val nutritionSnapshot by viewModel.dashboardNutrition.collectAsStateWithLifecycle()
    val mealPlans by viewModel.mealPlans.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val recipeIngredients by viewModel.recipeIngredients.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingAi.collectAsStateWithLifecycle()
    val healthSync by viewModel.healthConnect.healthConnectSync.collectAsStateWithLifecycle()
    val mangoOnline = com.example.ui.components.rememberMangoOnline()
    val sportsModel: com.example.viewmodel.SportsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val sportsData by sportsModel.data.collectAsStateWithLifecycle()
    val refreshing by viewModel.healthConnect.isSyncing.collectAsStateWithLifecycle()
    val habitDays by viewModel.habits.days.collectAsStateWithLifecycle()
    val waterTotal by viewModel.waterDrankMl.collectAsStateWithLifecycle()
    val sleepReadMessage by viewModel.healthConnect.sleepReadMessage.collectAsStateWithLifecycle()
    val bodyMeasurements by viewModel.bodyMeasurements.collectAsStateWithLifecycle()

    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.refreshCurrentDate()
        viewModel.healthConnect.refreshDashboard()
    }

    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()
    val today = java.time.LocalDate.parse(currentDate)
    val targetCalories = (profile?.targetCalories ?: 2000).coerceAtLeast(1)
    val consumed = nutritionSnapshot?.takeIf { it.date == currentDate }?.nutrition
        ?: com.example.viewmodel.ScaledMealNutrition(0, 0, 0, 0)
    val consumedCalories = consumed.calories ?: 0
    val incompleteNutrition = consumed.isPartial || consumed.calories == null ||
        consumed.protein == null || consumed.carbs == null || consumed.fat == null
    val reducedMangoMotion = LocalReducedMangoMotion.current
    val calorieFraction by animateFloatAsState(
        targetValue = (consumedCalories.toFloat() / targetCalories).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = if (reducedMangoMotion) 0 else 320),
        label = "calorie_ring"
    )
    val animatedCalories by animateIntAsState(
        targetValue = consumedCalories,
        animationSpec = tween(durationMillis = if (reducedMangoMotion) 0 else 420),
        label = "calorie_counter"
    )
    val consumedProtein = consumed.protein ?: 0
    val consumedCarbs = consumed.carbs ?: 0
    val consumedFat = consumed.fat ?: 0
    val targetProtein = (profile?.targetProtein ?: 100).coerceAtLeast(1)
    val targetCarbs = (profile?.targetCarbs ?: 250).coerceAtLeast(1)
    val targetFat = (profile?.targetFat ?: 67).coerceAtLeast(1)

    val activity = remember(healthSync, sportsData, today) { com.example.viewmodel.CurrentActivitySupport.resolve(healthSync, sportsData, today) }
    val burnedCalories = activity.calories

    val latestWeightValue = healthSync?.latestWeightKg?.takeIf { it > 0 }?.toFloat()
        ?: bodyMeasurements.firstOrNull()?.weightKg
        ?: profile?.currentWeightKg


    val mangoPhase = rememberMangoDayPhase()
    val mangoBackground = when (mangoPhase) {
        MangoDayPhase.MORNING -> Color(0xFFFFB547)
        MangoDayPhase.DAY -> Color(0xFF83C890)
        MangoDayPhase.EVENING -> Color(0xFFFF805D)
        MangoDayPhase.NIGHT -> Color(0xFF547B6A)
    }
    val backgroundPulse = if (reducedMangoMotion) 0.12f else {
        val backgroundMotion = rememberInfiniteTransition(label = "premium_background")
        val pulse by backgroundMotion.animateFloat(
            initialValue = 0.12f, targetValue = 0.22f,
            animationSpec = infiniteRepeatable(tween(9000), RepeatMode.Reverse),
            label = "premium_background_pulse")
        pulse
    }
    val waterFillFraction by animateFloatAsState(
        targetValue = (waterTotal.toFloat() / 2000f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = if (reducedMangoMotion) 0 else 850),
        label = "premium_water_fill")
    val waterPulseScale by animateFloatAsState(
        targetValue = 1f + waterFillFraction * 0.025f,
        animationSpec = tween(durationMillis = if (reducedMangoMotion) 0 else 500),
        label = "premium_water_pulse")
    val usabilityRevision by viewModel.usability.revision.collectAsStateWithLifecycle()
    val compactDashboard = remember(usabilityRevision) { viewModel.usability.compactDashboard() }
    val showDashboardTips = remember(usabilityRevision) { viewModel.usability.showDashboardTips() }
    val appearanceContext = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MangoGuide(size = 34.dp)
                        Column {
                            Text(
                                text = "FITMango",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Dieta • aktywność • postępy", fontSize = 11.sp, color = GoldAccent)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val nextLight = !MangoAppearance.light
                        appearanceContext.getSharedPreferences(MangoAppearance.PREFS, 0)
                            .edit().putBoolean(MangoAppearance.KEY, nextLight).apply()
                        MangoAppearance.light = nextLight
                    }) {
                        androidx.compose.material3.Icon(
                            if (MangoAppearance.light) Icons.Default.DarkMode else Icons.Default.LightMode,
                            if (MangoAppearance.light) "Włącz ciemny motyw" else "Włącz jasny motyw",
                            tint = LeafGreen
                        )
                    }
                    IconButton(onClick = onNavigateToProductPhoto) {
                        androidx.compose.material3.Icon(Icons.Default.CameraAlt, "Zdjęcie produktu", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onNavigateToDocuments) {
                        androidx.compose.material3.Icon(Icons.Default.PictureAsPdf, "Dokumenty i import PDF", tint = GoldAccent)
                    }
                    IconButton(onClick = { viewModel.healthConnect.sync(force = true) }, enabled = !refreshing) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else androidx.compose.material3.Icon(Icons.Default.Sync, "Odśwież Health Connect", tint = LeafGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = refreshing,
            onRefresh = { viewModel.refreshCurrentDate(); viewModel.healthConnect.sync(force = true) }, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(MaterialTheme.colorScheme.background, mangoBackground.copy(alpha = if (reducedMangoMotion) .07f else backgroundPulse * .55f).compositeOver(MaterialTheme.colorScheme.background), MaterialTheme.colorScheme.background)
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!mangoOnline) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("📴 Offline • Twoje zapisane posiłki, treningi i pomiary pozostają dostępne. " +
                        "AI, wyszukiwanie online i synchronizacja wymagają internetu.",
                        modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            val smartSuggestions = remember(
                targetCalories, consumedCalories, incompleteNutrition,
                consumedProtein, targetProtein, waterTotal
            ) {
                com.example.viewmodel.SmartDailyRecommendations.evaluate(
                    com.example.viewmodel.SmartDailyInput(
                        calorieTarget = targetCalories,
                        consumedCalories = if (incompleteNutrition) null else consumedCalories,
                        proteinTargetGrams = targetProtein,
                        consumedProteinGrams = if (incompleteNutrition) null else consumedProtein,
                        waterConsumedMl = waterTotal,
                        waterTargetMl = 2000,
                        nutritionIsPartial = incompleteNutrition
                    )
                )
            }
            val selectedMango = remember(usabilityRevision) {
                com.example.viewmodel.MangoRewardShop.load(appearanceContext).equipped
            }
            val mangoIcon = com.example.viewmodel.MangoRewardShop.catalog
                .firstOrNull { it.id == selectedMango }?.icon ?: "🥭"
            var mangoGreeting by rememberSaveable { mutableStateOf(false) }
            Surface(
                onClick = { mangoGreeting = !mangoGreeting },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().testTag("interactive_mango_mascot")
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (mangoGreeting) "$mangoIcon✨" else mangoIcon,
                        style = MaterialTheme.typography.headlineMedium)
                    Column {
                        Text(if (mangoGreeting) "Brawo, że dbasz o swoje nawyki!"
                            else "Cześć! Dotknij Mango 👋",
                            style = MaterialTheme.typography.titleSmall)
                        Text("Twój pomocnik na dziś", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val mangoNextStep = remember(today, sportsData.plans, waterTotal, consumedCalories, targetCalories) {
                com.example.viewmodel.MangoDailyTools.nextAction(
                    today, sportsData.plans, waterTotal,
                    (targetCalories - consumedCalories).coerceAtLeast(0))
            }
            Card(modifier = Modifier.fillMaxWidth().testTag("mango_daily_coach"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🥭 Mango Coach • następny krok",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(mangoNextStep, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onNavigateToMealPlan) { Text("Jadłospis") }
                        TextButton(onClick = onNavigateToAiCoach) { Text("Zapytaj Mango") }
                    }
                }
            }
            if (smartSuggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("smart_daily_recommendations"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Inteligentne wskazówki • Mój dzień",
                            style = MaterialTheme.typography.titleMedium)
                        smartSuggestions.take(3).forEach { suggestion ->
                            Column {
                                Text(suggestion.title, fontWeight = FontWeight.SemiBold)
                                Text(suggestion.explanation, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TextButton(onClick = onNavigateToMealPlan) {
                            Text("Otwórz jadłospis")
                        }
                    }
                }
            }
            if (incompleteNutrition) {
                Text("Niepełne dane odżywcze: wartości bilansu mogą być zaniżone. " +
                    "Uzupełnij składniki, aby uzyskać dokładniejsze propozycje.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("dashboard_partial_nutrition"))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MangoAnimatedLogo()
                    Text("Mój dzień • Cześć! 👋", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text("Bilans, następny posiłek i postępy w jednym miejscu", color = GoldAccent, fontSize = 13.sp)
                    val datedWeights = remember(bodyMeasurements) {
                        bodyMeasurements.filter { it.weightKg > 0f }.sortedBy { it.date }
                    }
                    val recentWeights = remember(datedWeights, today) {
                        datedWeights.filter { entry ->
                            runCatching {
                                val day = java.time.LocalDate.parse(entry.date)
                                !day.isAfter(today) && !day.isBefore(today.minusDays(30))
                            }.getOrDefault(false)
                        }
                    }
                    if (recentWeights.size >= 2) {
                        val firstWeight = recentWeights.first().weightKg
                        val lastWeight = recentWeights.last().weightKg
                        val change = lastWeight - firstWeight
                        Text("Trend wagi (30 dni): " + String.format(java.util.Locale.getDefault(), "%+.1f kg", change) +
                            " • ${recentWeights.size} pomiarów", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Trend wagi: dodaj co najmniej 2 pomiary w ciągu 30 dni.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

            }
            if (!compactDashboard && showDashboardTips) Card(
                modifier = Modifier.fillMaxWidth().testTag("fresh_mango_3_companion"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MangoGuide(
                        message = when {
                            incompleteNutrition ->
                                "Niektóre wartości odżywcze są niepełne. Sprawdź składniki przed oceną bilansu."
                            consumedCalories >= targetCalories ->
                                "Cel kalorii został osiągnięty. Sprawdź bilans i zaplanuj następny posiłek."
                            waterTotal < 500 && consumedCalories > 0 ->
                                "W dzienniku zapisano niewiele napojów. Sprawdź, czy wpisy są aktualne."
                            sportsData.plans.any { it.date == currentDate && !it.rest } ->
                                "Trening na dziś jest w planie. Powodzenia!"
                            else -> "Małe kroki każdego dnia też się liczą."
                        },
                        event = if (consumedCalories >= targetCalories && !incompleteNutrition) 1 else 0,
                        size = 34.dp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onNavigateToMealPlan, modifier = Modifier.weight(1f)) {
                            Text("🥗 Posiłki")
                        }
                        OutlinedButton(onClick = onNavigateToSport, modifier = Modifier.weight(1f)) {
                            Text("🏋️ Trening")
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = compactDashboard,
                    onClick = { viewModel.usability.setCompactDashboard(!compactDashboard) },
                    label = { Text("Kompaktowy pulpit") },
                    modifier = Modifier.testTag("dashboard_compact_toggle")
                )
                FilterChip(
                    selected = showDashboardTips,
                    onClick = { viewModel.usability.setShowDashboardTips(!showDashboardTips) },
                    label = { Text("Wskazówki Mango") },
                    modifier = Modifier.testTag("dashboard_tips_toggle")
                )
            }
            var showDailyGreeting by rememberSaveable { mutableStateOf(false) }
            if (!compactDashboard) TextButton(onClick = { showDailyGreeting = !showDailyGreeting },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.testTag("dashboard_daily_greeting_toggle")) {
                Text(if (showDailyGreeting) "Ukryj motywację −" else "Motywacja dnia +")
            }
            if (!compactDashboard && showDailyGreeting) MangoDailyGreeting(
                today = today,
                activeDates = habitDays.map { it.date },
                waterMl = waterTotal,
                consumed = consumedCalories,
                calorieGoal = targetCalories
            )
            Card(
                modifier = Modifier.fillMaxWidth().testTag("fresh_mango_calorie_hero"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MangoHalfGauge(
                            label = "Spożyte kcal", value = animatedCalories, goal = targetCalories,
                            progress = calorieFraction, color = LeafGreen, modifier = Modifier.weight(1f),
                            subtitle = "z $targetCalories kcal"
                        )
                        val burned = com.example.viewmodel.CurrentActivitySupport.calories(burnedCalories)
                        MangoHalfGauge(
                            label = "Spalone kcal", value = burned, goal = null,
                            progress = if (burned == null) 0f else (burned.toFloat() / targetCalories).coerceIn(0f, 1f),
                            color = JuicyOrange, modifier = Modifier.weight(1f),
                            subtitle = when {
                                burned == null -> "Brak odczytu"
                                healthSync?.totalCaloriesBurned?.let { it.isFinite() && it > 0 } == true -> "Łącznie z Health Connect"
                                else -> "Aktywne z Health Connect"
                            }
                        )
                    }
                    Box(Modifier.fillMaxWidth()) {
                        PremiumCalorieRing(consumed.calories, targetCalories, incompleteNutrition, Modifier.fillMaxWidth())
                        MangoGoalConfetti(
                            goalReached = !incompleteNutrition &&
                                consumedCalories >= (targetCalories * 0.95f).toInt() &&
                                consumedCalories <= targetCalories,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                    if (consumedProtein >= targetProtein && consumedProtein > 0) {
                        MangoBadge("Cel białka", earned = true)
                    }
                    if (consumedCalories in 1..targetCalories && consumedCalories >= (targetCalories * 0.95f).toInt()) {
                        MangoBadge("Dzienny cel kalorii", earned = true)
                    }
                    Text("Pozostało: ${(targetCalories - consumedCalories).coerceAtLeast(0)} kcal  •  Cel: ${profile?.goal ?: "Twój cel"}",
                        fontSize = 12.sp, color = GoldAccent, maxLines = 2)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FreshMangoMacroTile("Białko", consumedProtein, targetProtein, LeafGreen, Modifier.weight(1f))
                        FreshMangoMacroTile("Tłuszcze", consumedFat, targetFat, JuicyOrange, Modifier.weight(1f))
                        FreshMangoMacroTile("Węglowodany", consumedCarbs, targetCarbs, MangoYellow, Modifier.weight(1f))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().testTag("fresh_mango_quick_navigation"),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FreshMangoDestinationCard("Jadłospis", "Posiłki", Icons.Outlined.RestaurantMenu,
                    Modifier.weight(1f), onNavigateToMealPlan)
                FreshMangoDestinationCard("Trening", "Aktywność", Icons.Outlined.FitnessCenter,
                    Modifier.weight(1f), onNavigateToSport)
                FreshMangoDestinationCard("Postępy", "Wyniki", Icons.Default.TrendingUp,
                    Modifier.weight(1f), onNavigateToEffects)
            }
            var showDashboardExtras by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { showDashboardExtras = !showDashboardExtras },
                modifier = Modifier.fillMaxWidth().testTag("dashboard_more_toggle")) {
                Text(if (showDashboardExtras) "▲ Ukryj dodatkowe skróty" else "▼ Więcej skrótów i import przepisów")
            }
            if (showDashboardExtras) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("dashboard_import_recipe")
                    .clickable(onClick = onNavigateToRecipeImport),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MutedSurface2)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.Icon(Icons.Default.AddCircleOutline, contentDescription = null,
                        tint = JuicyOrange, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Importuj przepis", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text("Instagram • Cookidoo • strony WWW • zdjęcia",
                            color = GoldAccent, fontSize = 12.sp)
                    }
                    androidx.compose.material3.Icon(Icons.Default.ChevronRight,
                        contentDescription = null, tint = JuicyOrange)
                }
            }
            }
            Card(
                modifier = Modifier.fillMaxWidth().testTag("fresh_mango_meal_preview")
                    .clickable(onClick = onNavigateToMealPlan),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(18.dp))
                            .background(MutedSurface2),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(Icons.Outlined.RestaurantMenu,
                            contentDescription = null, tint = JuicyOrange,
                            modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Dzisiejsze posiłki", fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("Otwórz jadłospis i zaplanuj swój dzień",
                            fontSize = 12.sp, color = GoldAccent)
                    }
                    androidx.compose.material3.Icon(Icons.Default.ChevronRight,
                        contentDescription = null, tint = JuicyOrange)
                }
            }
            if (showDashboardExtras) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("my_day_shortcuts"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Mój dzień • szybkie działania", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onNavigateToMealPlan, modifier = Modifier.weight(1f)) {
                            Text("Jadłospis", maxLines = 1)
                        }
                        OutlinedButton(onClick = onNavigateToEffects, modifier = Modifier.weight(1f)) {
                            Text("Postępy", maxLines = 1)
                        }
                    }
                    Text("Poniżej znajdziesz propozycje posiłków, produkty wymagające uzupełnienia, wodę i aktywność.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            }
            val weeklyActiveDays = habitDays.count { entry ->
                runCatching {
                    val day = java.time.LocalDate.parse(entry.date)
                    !day.isAfter(today) && !day.isBefore(today.minusDays(6))
                }.getOrDefault(false)
            }
            val mangoMood = when {
                consumedCalories >= targetCalories * 0.95f && consumedCalories <= targetCalories -> "🥭 Brawo! Twój dzisiejszy cel jest blisko."
                waterTotal > 0 && weeklyActiveDays >= 3 -> "🥭 Świetna regularność! Małe kroki się liczą."
                consumedCalories > 0 -> "🥭 Dobrze, że zapisujesz posiłki. Tak trzymaj!"
                else -> "🥭 Cześć! Zacznij od małego kroku dla siebie."
            }
            Card(
                modifier = Modifier.fillMaxWidth().testTag("mango_reactive_companion"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(26.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    MangoMoodIllustration(
                        mood = when {
                            consumedCalories >= targetCalories * 0.95f && consumedCalories <= targetCalories -> MangoMood.CELEBRATE
                            waterTotal > 0 && weeklyActiveDays >= 3 -> MangoMood.HAPPY
                            consumedCalories > 0 -> MangoMood.HAPPY
                            else -> MangoMood.WELCOME
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Twój Mango-przyjaciel", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(mangoMood, color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth().testTag("mango_weekly_summary"),
                shape = RoundedCornerShape(26.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Twój tydzień z Mango", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("Ostatnie 7 dni • na podstawie zapisanych aktywnych dni",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(
                        progress = { (weeklyActiveDays / 7f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                        color = LeafGreen
                    )
                    Text("Aktywne dni: $weeklyActiveDays / 7",
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("Dzisiaj: $consumedCalories kcal • woda $waterTotal ml",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onNavigateToEffects) { Text("Zobacz pełne postępy") }
                }
            }
            MangoDayPhaseSettings()
            MangoGrowthTree(
                activeDays = habitDays.count { entry ->
                    runCatching {
                        val d = java.time.LocalDate.parse(entry.date)
                        !d.isAfter(today) && !d.isBefore(today.minusDays(29))
                    }.getOrDefault(false)
                },
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
            com.example.ui.components.ReleaseNotesControl(autoShow = true)
            com.example.ui.components.MealUndoCard(viewModel)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(MealPlannerSupport.weekDays(today)[today.dayOfWeek.value - 1].displayWithDate,
                    modifier = Modifier.weight(1f).testTag("dashboard_current_date"),
                    color = GoldAccent, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onNavigateToMealPlan) { Text("Jadłospis →") }
                IconButton(onClick = onNavigateToSport, modifier = Modifier.testTag("dashboard_open_sport")) {
                    androidx.compose.material3.Icon(Icons.Outlined.FitnessCenter, "Sport i treningi", tint = LeafGreen)
                }
            }

            // Main daily summary — layout aligned with the FITMango visual reference.
            BackupReminderCard(viewModel)
            val layoutRevision by viewModel.usability.revision.collectAsState()
            val sectionOrder = remember(layoutRevision) { viewModel.usability.order() }
            val sections: Map<String, @Composable () -> Unit> = linkedMapOf(
                "checkin" to { QuickCheckinCard(viewModel) },
                "sport" to { SportsOverviewCard(onNavigateToSport, sports = sportsModel, today = today, current = activity) },
                "effects" to { MangoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MangoSectionArt(sectionArtwork("effects"), sectionArtworkAccent("effects"))
                        Text("Efekty i analiza", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Trend wagi, jakość zapisów i raport dla Twojego celu", color = GoldAccent)
                    TextButton(onClick = onNavigateToEffects) { Text("Otwórz analizę →") }
                } } },
                "calories" to {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.example.ui.components.EnergyExplanationButton(viewModel, currentDate)

                    }
                },
                "produce" to {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProduceDailyCard(viewModel)

                    }
                },
                "health" to {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Health Connect Live Metrics Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, WarmBrown.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .testTag("dashboard_health_connect_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = OrangeInk,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Aktywność i zdrowie",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            )
                        }
                        if (healthSync?.isConnected == true) {
                            Text(
                                text = "Połączono",
                                fontSize = 11.sp,
                                color = LeafGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Steps
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MutedSurface2
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Outlined.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Text("Kroki", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                                }
                                val stepsText = HealthConnectSupport.formatStepsDisplay(activity.steps)
                                Text(
                                    text = stepsText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("dashboard_steps_text")
                                )
                            }
                        }

                        // Calories Burned
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MutedSurface2
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = OrangeInk, modifier = Modifier.size(24.dp))
                                    Text("Spalone kcal", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                                }
                                val calBurnedText = com.example.viewmodel.CurrentActivitySupport.calories(activity.calories)?.let { "$it kcal" } ?: "Brak danych"
                                Text(
                                    text = calBurnedText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("dashboard_calories_burned_text")
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Weight
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MutedSurface2
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Outlined.MonitorWeight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Text("Aktualna waga", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                                }
                                val weightText = if (latestWeightValue != null && latestWeightValue > 0f) {
                                    HealthConnectSupport.formatWeightDisplay(latestWeightValue)
                                } else {
                                    "Brak danych"
                                }
                                Text(
                                    text = weightText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("dashboard_weight_text")
                                )
                            }
                        }

                        // Sleep
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MutedSurface2
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(24.dp))
                                    Text(activity.sleepLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                                }
                                val sleepText = HealthConnectSupport.formatSleepDurationDisplay(activity.sleepMinutes)
                                Text(
                                    text = sleepText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("dashboard_sleep_text")
                                )
                                if (sleepReadMessage.isNotBlank() && activity.sleepLabel == "Sen (ostatni odczyt)") Text(sleepReadMessage, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    HealthConnectInsights(
                        sync = healthSync,
                        eatenCalories = consumedCalories,
                        calorieGoal = targetCalories,
                        sleepMinutes = activity.sleepMinutes,
                        onSync = { viewModel.healthConnect.sync(force = true) },
                        syncing = refreshing
                    )
                }
            }

                    }
                },
                "water" to {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DailyIntakeCard(viewModel)
            // The detailed intake card already shows water progress and creatine.
            // Avoid a second progress bar and an overlaid decorative glass on small screens.

                    }
                },
                "suggestions" to {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WhatToEatCard(viewModel)
                    }
                },
                "badges" to {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HabitsChallengesCard(viewModel)
                    }
                },
                "history" to {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HabitsHistoryCard(viewModel)
                    }
                },
                "week" to {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WeeklySummaryCard(viewModel)
                    }
                },
                "ai" to {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // AI Dietitian Banner with Mango mascot
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAiCoach() }
                    .border(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(listOf(JuicyOrange, MangoYellow)),
                        shape = RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MutedSurface2)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Mango mascot/icon
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(RipeYellow, JuicyOrange))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "AI",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Zapytaj AI Dietetyka Mango",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Profesjonalne porady i zamienniki",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(JuicyOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Go",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

                    }
                },
                "tools" to {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Quick Tools
            MangoSectionTitle(text = "Szybkie Narzędzia", showSparkle = true)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    title = "Pełny jadłospis",
                    icon = Icons.Default.CalendarMonth,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToMealPlan
                )
                QuickActionCard(
                    title = "Baza przepisów",
                    icon = Icons.Outlined.RestaurantMenu,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToRecipes
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    title = "Zdjęcie produktu",
                    icon = Icons.Default.CameraAlt,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToProductPhoto
                )
                QuickActionCard(
                    title = "Skaner kodów",
                    icon = Icons.Default.QrCodeScanner,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToBarcodeScanner
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    title = "Pomiary ciała",
                    icon = Icons.Outlined.MonitorWeight,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToMeasurements
                )
                QuickActionCard("Dodaj PDF", Icons.Default.PictureAsPdf, Modifier.weight(1f), onNavigateToDocuments)
            }

                    }
                }
            )
            sectionOrder.filterNot { viewModel.usability.hidden(it) }.forEach { id ->
                key(id) {
                    val section = sections[id]
                    if (section != null) {
                        val summary = when (id) {
                            "water" -> "$waterTotal ml • woda i kreatyna"
                            "produce" -> habitDays.find { it.date == currentDate }?.let { "${it.produceGrams} / ${it.produceGoal} g" } ?: "Dzisiejsze porcje i cel"
                            "health" -> "${HealthConnectSupport.formatStepsDisplay(activity.steps)} kroków • sen: ${HealthConnectSupport.formatSleepDurationDisplay(activity.sleepMinutes)}"
                            "tools" -> "Skaner, zdjęcie produktu, dokumenty i pomiary"
                            "ai" -> "Dietetyk Mango"
                            else -> ""
                        }
                        if (id == "water") StudioHydrationCards(viewModel) { section() }
                        else if (id in setOf("produce", "health", "tools", "ai"))
                            CompactStartCard(viewModel.usability.cards[id].orEmpty(), summary, id,
                                quickAction = if (id == "water") ({ viewModel.addBeverage(250) }) else null) { section() }
                        else section()
                    }
                }
            }
            DashboardCustomization(viewModel)
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrangeInk)
                }
            }
        }
        }
    }
}

@Composable
fun QuickActionCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .height(96.dp)
            .pressScaleEffect()
            .clickable { onClick() }
            .border(1.dp, WarmBrown.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FreshMangoDestinationCard(
    title: String,
    subtitle: String,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val accent = if (imageVector == Icons.Outlined.FitnessCenter || imageVector == Icons.Default.TrendingUp) LeafGreen else JuicyOrange
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector, contentDescription = null,
                    tint = accent, modifier = Modifier.size(24.dp))
            }
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                maxLines = 1)
            Text(subtitle, color = GoldAccent, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun FreshMangoMacroTile(
    label: String,
    value: Int,
    target: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp), color = accent.copy(alpha = 0.10f), border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.24f))) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val fraction = if (target > 0) (value.toFloat() / target).coerceIn(0f, 1f) else 0f
            val animatedFraction = mangoFloat(fraction)
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawArc(accent.copy(alpha = 0.2f), -90f, 360f, false, style = stroke)
                    drawArc(accent, -90f, 360f * animatedFraction, false, style = stroke)
                }
                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            }
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1)
            Text("$value g", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Text("cel $target g", fontSize = 10.sp, color = GoldAccent)
        }
    }
}

@Composable
private fun MangoHalfGauge(
    label: String, value: Int?, goal: Int?, progress: Float,
    color: Color, modifier: Modifier = Modifier, subtitle: String
) {
    val animatedProgress = mangoFloat(progress.coerceIn(0f, 1f))
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.BottomCenter) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(76.dp)) {
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 9.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                val diameter = minOf(size.width - 12.dp.toPx(), size.height * 1.75f)
                val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
                val left = (size.width - diameter) / 2f
                val top = size.height - diameter / 2f - 4.dp.toPx()
                drawArc(color = Color.LightGray.copy(alpha = .32f), startAngle = 180f,
                    sweepAngle = 180f, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top), size = arcSize, style = stroke)
                drawArc(color = color, startAngle = 180f,
                    sweepAngle = 180f * animatedProgress, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top), size = arcSize, style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value?.toString() ?: "—", fontSize = 23.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                if (goal != null) Text("z $goal", fontSize = 11.sp, color = GoldAccent)
            }
        }
        Text(subtitle, fontSize = 10.sp, color = GoldAccent, maxLines = 2,
            textAlign = TextAlign.Center, lineHeight = 12.sp)
    }
}
