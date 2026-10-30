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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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
                                color = MangoYellow
                            )
                            Text("Twój codzienny bilans • build ${com.example.BuildConfig.VERSION_CODE}", fontSize = 10.sp, color = GoldAccent)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToProductPhoto) {
                        androidx.compose.material3.Icon(Icons.Default.CameraAlt, "Zdjęcie produktu", tint = MangoYellow)
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
                .consumeWindowInsets(padding)
                .imePadding()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkBackground, DarkBackground)
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            com.example.ui.components.ReleaseNotesControl(autoShow = true)
            com.example.ui.components.MealUndoCard(viewModel)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(MealPlannerSupport.weekDays(today)[today.dayOfWeek.value - 1].displayWithDate,
                    modifier = Modifier.weight(1f).testTag("dashboard_current_date"),
                    color = GoldAccent, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onNavigateToMealPlan) { Text("Jadłospis →") }
                IconButton(onClick = onNavigateToSport, modifier = Modifier.testTag("dashboard_open_sport")) {
                    androidx.compose.material3.Icon(Icons.Default.FitnessCenter, "Sport i treningi", tint = LeafGreen)
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
            val plannedCalories by produceState<Int?>(null, mealPlans, recipes, recipeIngredients, currentDate) {
                value = null
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    IngredientSubstitutionSupport.totalMealNutrition(
                        mealPlans.filter { MealPlannerSupport.matchesDay(it.dayOfWeek, currentDate) },
                        recipes, recipeIngredients).calories ?: 0
                }
            }
            StudioBalanceCard(consumed, targetCalories, targetProtein, targetCarbs, targetFat,
                plannedCalories, com.example.viewmodel.CurrentActivitySupport.calories(burnedCalories))
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
                colors = CardDefaults.cardColors(containerColor = MutedSurface1)
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
                                color = Cream,
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
                                    Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = MangoYellow, modifier = Modifier.size(24.dp))
                                    Text("Kroki", fontSize = 11.sp, color = Cream.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                                }
                                val stepsText = HealthConnectSupport.formatStepsDisplay(activity.steps)
                                Text(
                                    text = stepsText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Cream,
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
                                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = OrangeInk, modifier = Modifier.size(24.dp))
                                    Text("Spalone kcal", fontSize = 11.sp, color = Cream.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                                }
                                val calBurnedText = com.example.viewmodel.CurrentActivitySupport.calories(activity.calories)?.let { "$it kcal" } ?: "Brak danych"
                                Text(
                                    text = calBurnedText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Cream,
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
                                    Icon(Icons.Default.MonitorWeight, contentDescription = null, tint = MangoYellow, modifier = Modifier.size(24.dp))
                                    Text("Aktualna waga", fontSize = 11.sp, color = Cream.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
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
                                    color = Cream,
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
                                    Icon(Icons.Default.Bedtime, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(24.dp))
                                    Text(activity.sleepLabel, fontSize = 11.sp, color = Cream.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                                }
                                val sleepText = HealthConnectSupport.formatSleepDurationDisplay(activity.sleepMinutes)
                                Text(
                                    text = sleepText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Cream,
                                    modifier = Modifier.testTag("dashboard_sleep_text")
                                )
                                if (sleepReadMessage.isNotBlank() && activity.sleepLabel == "Sen (ostatni odczyt)") Text(sleepReadMessage, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

                    }
                },
                "water" to {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DailyIntakeCard(viewModel)
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
                            tint = Cream,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Zapytaj AI Dietetyka Mango",
                            fontWeight = FontWeight.ExtraBold,
                            color = Cream,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Profesjonalne porady i zamienniki",
                            fontSize = 12.sp,
                            color = Cream.copy(alpha = 0.8f)
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
                            tint = Cream,
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
                    icon = Icons.Default.RestaurantMenu,
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
                    icon = Icons.Default.MonitorWeight,
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
        colors = CardDefaults.cardColors(containerColor = MutedSurface1),
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
                tint = MangoYellow,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Cream,
                maxLines = 1
            )
        }
    }
}
