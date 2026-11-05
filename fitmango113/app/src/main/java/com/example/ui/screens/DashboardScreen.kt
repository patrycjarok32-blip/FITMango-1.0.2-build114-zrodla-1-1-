
package com.example.ui.screens

import com.example.ui.components.FitMangoIcon as Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.DietaViewModel
import com.example.viewmodel.HealthConnectSupport
import com.example.viewmodel.MealPlannerSupport
import com.example.viewmodel.IngredientSubstitutionSupport

private val MangoPage = Color(0xFFFFFAEF)
private val MangoInk = Color(0xFF25362C)
private val MangoSubtle = Color(0xFF657568)
private val MangoOrange = Color(0xFFFF8A29)
private val MangoLime = Color(0xFFB9E56B)
private val MangoGreen = Color(0xFF288A61)
private val MangoCardWhite = Color.White

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
    val today = remember(currentDate) { java.time.LocalDate.parse(currentDate) }
    val targetCalories = (profile?.targetCalories ?: 2000).coerceAtLeast(1)
    val consumed = nutritionSnapshot?.takeIf { it.date == currentDate }?.nutrition
        ?: com.example.viewmodel.ScaledMealNutrition(0, 0, 0, 0)
    val targetProtein = (profile?.targetProtein ?: 100).coerceAtLeast(1)
    val targetCarbs = (profile?.targetCarbs ?: 250).coerceAtLeast(1)
    val targetFat = (profile?.targetFat ?: 67).coerceAtLeast(1)
    val activity = remember(healthSync, sportsData, today) {
        com.example.viewmodel.CurrentActivitySupport.resolve(healthSync, sportsData, today)
    }
    val latestWeightValue = healthSync?.latestWeightKg?.takeIf { it > 0 }?.toFloat()
        ?: bodyMeasurements.firstOrNull()?.weightKg
        ?: profile?.currentWeightKg
    val layoutRevision by viewModel.usability.revision.collectAsState()
    val sectionOrder = remember(layoutRevision) { viewModel.usability.order() }

    Scaffold(
        containerColor = MangoPage,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MangoGuide(size = 38.dp)
                        Column {
                            Text("FITMango", color = MangoInk, fontSize = 23.sp, fontWeight = FontWeight.Black)
                            Text("Twój zdrowy dzień • build ${com.example.BuildConfig.VERSION_CODE}",
                                color = MangoSubtle, fontSize = 10.sp)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToProductPhoto) {
                        androidx.compose.material3.Icon(Icons.Default.CameraAlt, "Zdjęcie produktu", tint = MangoOrange)
                    }
                    IconButton(onClick = onNavigateToDocuments) {
                        androidx.compose.material3.Icon(Icons.Default.PictureAsPdf, "Dokumenty i import PDF", tint = MangoGreen)
                    }
                    IconButton(onClick = { viewModel.healthConnect.sync(force = true) }, enabled = !refreshing) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else androidx.compose.material3.Icon(Icons.Default.Sync, "Odśwież Health Connect", tint = MangoGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MangoPage)
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refreshCurrentDate(); viewModel.healthConnect.sync(force = true) },
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(MangoPage)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ReleaseNotesControl(autoShow = true)
                MealUndoCard(viewModel)

                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MangoOrange),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("DZISIAJ JEST DOBRY DZIEŃ! 🥭", fontWeight = FontWeight.Black,
                            fontSize = 18.sp, color = Color.White)
                        Text(MealPlannerSupport.weekDays(today)[today.dayOfWeek.value - 1].displayWithDate,
                            color = Color.White, modifier = Modifier.testTag("dashboard_current_date"))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onNavigateToMealPlan,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MangoInk)) {
                                Text("Jadłospis")
                            }
                            Button(onClick = onNavigateToSport,
                                modifier = Modifier.testTag("dashboard_open_sport"),
                                colors = ButtonDefaults.buttonColors(containerColor = MangoInk, contentColor = Color.White)) {
                                androidx.compose.material3.Icon(Icons.Default.FitnessCenter, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Trening")
                            }
                        }
                    }
                }

                BackupReminderCard(viewModel)

                val sections: Map<String, @Composable () -> Unit> = linkedMapOf(
                    "checkin" to { QuickCheckinCard(viewModel) },
                    "sport" to { SportsOverviewCard(onNavigateToSport, sports = sportsModel, today = today, current = activity) },
                    "effects" to {
                        DashboardFeatureCard(
                            title = "Twoje postępy", subtitle = "Waga, trendy i analiza celu",
                            icon = Icons.Default.TrendingUp, background = Color(0xFFE8F6DF),
                            iconColor = MangoGreen, onClick = onNavigateToEffects
                        )
                    },
                    "calories" to {
                        val plannedCalories by produceState<Int?>(null, mealPlans, recipes, recipeIngredients, currentDate) {
                            value = null
                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                IngredientSubstitutionSupport.totalMealNutrition(
                                    mealPlans.filter { MealPlannerSupport.matchesDay(it.dayOfWeek, currentDate) },
                                    recipes, recipeIngredients
                                ).calories ?: 0
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StudioBalanceCard(consumed, targetCalories, targetProtein, targetCarbs,
                                targetFat, plannedCalories,
                                com.example.viewmodel.CurrentActivitySupport.calories(activity.calories))
                            EnergyExplanationButton(viewModel, currentDate)
                        }
                    },
                    "produce" to { ProduceDailyCard(viewModel) },
                    "health" to {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("dashboard_health_connect_card"),
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(containerColor = MangoCardWhite)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Zdrowie i aktywność", modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = MangoInk)
                                    if (healthSync?.isConnected == true) {
                                        Text("● Połączono", color = MangoGreen, fontSize = 11.sp)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DashboardMetric(
                                        "Kroki", HealthConnectSupport.formatStepsDisplay(activity.steps),
                                        Icons.Default.DirectionsWalk, Color(0xFFE5F5D5), MangoGreen,
                                        Modifier.weight(1f).testTag("dashboard_steps_text")
                                    )
                                    DashboardMetric(
                                        "Spalone kcal",
                                        com.example.viewmodel.CurrentActivitySupport.calories(activity.calories)?.let { "$it kcal" } ?: "Brak danych",
                                        Icons.Default.LocalFireDepartment, Color(0xFFFFE9D7), MangoOrange,
                                        Modifier.weight(1f).testTag("dashboard_calories_burned_text")
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DashboardMetric(
                                        "Waga", latestWeightValue?.takeIf { it > 0f }
                                            ?.let { HealthConnectSupport.formatWeightDisplay(it) } ?: "Brak danych",
                                        Icons.Default.MonitorWeight, Color(0xFFFFF2C9), Color(0xFFBA871C),
                                        Modifier.weight(1f).testTag("dashboard_weight_text")
                                    )
                                    DashboardMetric(
                                        activity.sleepLabel,
                                        HealthConnectSupport.formatSleepDurationDisplay(activity.sleepMinutes),
                                        Icons.Default.Bedtime, Color(0xFFECE7FF), Color(0xFF8270C7),
                                        Modifier.weight(1f).testTag("dashboard_sleep_text")
                                    )
                                }
                                if (sleepReadMessage.isNotBlank() && activity.sleepLabel == "Sen (ostatni odczyt)") {
                                    Text(sleepReadMessage, color = MangoSubtle, fontSize = 11.sp)
                                }
                            }
                        }
                    },
                    "water" to { DailyIntakeCard(viewModel) },
                    "suggestions" to { WhatToEatCard(viewModel) },
                    "badges" to { HabitsChallengesCard(viewModel) },
                    "history" to { HabitsHistoryCard(viewModel) },
                    "week" to { WeeklySummaryCard(viewModel) },
                    "ai" to {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onNavigateToAiCoach),
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDAF3B8))
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp))
                                    .background(MangoGreen), contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.Icon(Icons.Default.SmartToy, "AI", tint = Color.White,
                                        modifier = Modifier.size(29.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Dietetyk Mango AI", fontWeight = FontWeight.ExtraBold,
                                        fontSize = 17.sp, color = MangoInk)
                                    Text("Zapytaj o posiłki, przepisy i zamienniki",
                                        color = MangoSubtle, fontSize = 12.sp)
                                }
                                androidx.compose.material3.Icon(Icons.Default.ArrowForwardIos, "Otwórz AI",
                                    tint = MangoGreen, modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    "tools" to {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Szybkie narzędzia", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                                color = MangoInk)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                QuickActionCard("Jadłospis", Icons.Default.CalendarMonth, Modifier.weight(1f), onNavigateToMealPlan)
                                QuickActionCard("Przepisy", Icons.Default.RestaurantMenu, Modifier.weight(1f), onNavigateToRecipes)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                QuickActionCard("Zdjęcie", Icons.Default.CameraAlt, Modifier.weight(1f), onNavigateToProductPhoto)
                                QuickActionCard("Skaner", Icons.Default.QrCodeScanner, Modifier.weight(1f), onNavigateToBarcodeScanner)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                QuickActionCard("Pomiary", Icons.Default.MonitorWeight, Modifier.weight(1f), onNavigateToMeasurements)
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
                                "produce" -> habitDays.find { it.date == currentDate }
                                    ?.let { "${it.produceGrams} / ${it.produceGoal} g" } ?: "Dzisiejsze porcje i cel"
                                "health" -> "${HealthConnectSupport.formatStepsDisplay(activity.steps)} kroków • sen: ${HealthConnectSupport.formatSleepDurationDisplay(activity.sleepMinutes)}"
                                "tools" -> "Skaner, zdjęcie produktu, dokumenty i pomiary"
                                "ai" -> "Dietetyk Mango"
                                else -> ""
                            }
                            if (id == "water") StudioHydrationCards(viewModel) { section() }
                            else if (id in setOf("produce", "health", "tools", "ai")) {
                                CompactStartCard(viewModel.usability.cards[id].orEmpty(), summary, id,
                                    quickAction = null) { section() }
                            } else section()
                        }
                    }
                }

                DashboardCustomization(viewModel)
                if (isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MangoOrange)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun DashboardMetric(
    title: String,
    value: String,
    icon: ImageVector,
    background: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(background)
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = accent,
            modifier = Modifier.size(25.dp))
        Text(title, fontSize = 11.sp, color = MangoSubtle, maxLines = 2)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MangoInk,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DashboardFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    background: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.Icon(icon, null, tint = iconColor, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MangoInk)
                Text(subtitle, color = MangoSubtle, fontSize = 12.sp)
            }
            androidx.compose.material3.Icon(Icons.Default.ChevronRight, "Otwórz", tint = iconColor)
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = when (title) {
        "Jadłospis", "Pełny jadłospis" -> Color(0xFFFFE3B8)
        "Przepisy", "Baza przepisów" -> Color(0xFFDFF4CA)
        "Zdjęcie", "Zdjęcie produktu" -> Color(0xFFFFD9CB)
        "Skaner", "Skaner kodów" -> Color(0xFFD9EAFD)
        "Pomiary", "Pomiary ciała" -> Color(0xFFECE0FF)
        else -> Color(0xFFFFEDBD)
    }
    Card(
        modifier = modifier.height(110.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = accent)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(13.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Icon(icon, title, tint = MangoInk,
                modifier = Modifier.size(29.dp))
            Spacer(Modifier.height(9.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = MangoInk, maxLines = 2)
        }
    }
}
