package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FoodBank
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LeafGreen
import com.example.ui.theme.MangoYellow
import com.example.ui.theme.MutedSurface1
import com.example.ui.theme.MutedSurface2
import com.example.ui.theme.OrangeInk
import com.example.ui.theme.WarmBrown
import com.example.viewmodel.CurrentActivitySupport
import com.example.viewmodel.DietaViewModel
import com.example.viewmodel.IngredientSubstitutionSupport
import java.time.LocalDate
import kotlin.math.roundToInt

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
    val healthSync by viewModel.healthConnect.healthConnectSync.collectAsStateWithLifecycle()
    val sportsModel: com.example.viewmodel.SportsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val sportsData by sportsModel.data.collectAsStateWithLifecycle()
    val bodyMeasurements by viewModel.bodyMeasurements.collectAsStateWithLifecycle()
    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()
    val today = remember(currentDate) { LocalDate.parse(currentDate) }
    val refreshing by viewModel.healthConnect.isSyncing.collectAsStateWithLifecycle()
    val habitDays by viewModel.habits.days.collectAsStateWithLifecycle()
    val waterTotal by viewModel.waterDrankMl.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingAi.collectAsStateWithLifecycle()
    val mealPlans by viewModel.mealPlans.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val recipeIngredients by viewModel.recipeIngredients.collectAsStateWithLifecycle()

    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.refreshCurrentDate()
        viewModel.healthConnect.refreshDashboard()
    }

    val consumed = nutritionSnapshot?.takeIf { it.date == currentDate }?.nutrition
        ?: com.example.viewmodel.ScaledMealNutrition(0, 0, 0, 0)
    val targetCalories = (profile?.targetCalories ?: 2000).coerceAtLeast(1)
    val consumedCalories = consumed.calories ?: 0
    val consumedProtein = consumed.protein ?: 0
    val consumedCarbs = consumed.carbs ?: 0
    val consumedFat = consumed.fat ?: 0
    val targetProtein = (profile?.targetProtein ?: 100).coerceAtLeast(1)
    val targetCarbs = (profile?.targetCarbs ?: 250).coerceAtLeast(1)
    val targetFat = (profile?.targetFat ?: 67).coerceAtLeast(1)

    val activity = remember(healthSync, sportsData, today) {
        com.example.viewmodel.CurrentActivitySupport.resolve(healthSync, sportsData, today)
    }
    val burnedCalories = activity.calories ?: 0.0
    val remainingCalories = (targetCalories - consumedCalories + burnedCalories.roundToInt()).coerceAtLeast(0)
    val proteinProgress = ((consumedProtein.toFloat() / targetProtein.toFloat()).coerceIn(0f, 1.2f))
    val carbsProgress = ((consumedCarbs.toFloat() / targetCarbs.toFloat()).coerceIn(0f, 1.2f))
    val fatProgress = ((consumedFat.toFloat() / targetFat.toFloat()).coerceIn(0f, 1.2f))
    val latestWeightValue = healthSync?.latestWeightKg?.takeIf { it > 0 }?.toFloat()
        ?: bodyMeasurements.firstOrNull()?.weightKg
        ?: profile?.currentWeightKg

    val todaySteps = activity.steps ?: 0L
    val todaySleep = activity.sleepMinutes ?: 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(MangoYellow, OrangeInk)))
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color.White)
                        }
                        Column {
                            Text("FITMango", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MangoYellow)
                            Text("Premium dashboard • ${today.dayOfMonth}/${today.monthValue}", fontSize = 10.sp, color = GoldAccent)
                        }
                    }
                },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { viewModel.healthConnect.sync(force = true) }, enabled = !refreshing) {
                            if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Sync, contentDescription = "Odśwież Health Connect", tint = LeafGreen)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground, titleContentColor = MangoYellow)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(MutedSurface1, DarkBackground)))
                    .border(1.dp, WarmBrown.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Dziś", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(today.toString(), color = Color(0xFFBBC7D6), fontSize = 12.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${consumedCalories} kcal", fontWeight = FontWeight.Black, fontSize = 28.sp, color = Color.White)
                            Text("spożycie", color = GoldAccent, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Cel ${targetCalories} kcal", color = Color(0xFFBFC9D6), fontSize = 12.sp)
                            Text("Pozostało ${remainingCalories} kcal", color = LeafGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    LinearProgressIndicator(
                        progress = { (consumedCalories.toFloat() / targetCalories.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MangoYellow,
                        trackColor = Color(0xFF1E2630)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricPill("Białko", "${consumedProtein}g", targetProtein, proteinProgress, Color(0xFF80D8FF))
                        MetricPill("Węgl.", "${consumedCarbs}g", targetCarbs, carbsProgress, Color(0xFF8DE9A5))
                        MetricPill("Tłuszcz", "${consumedFat}g", targetFat, fatProgress, Color(0xFFFFB980))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryTile(
                    title = "Waga",
                    value = if (latestWeightValue != null && latestWeightValue > 0f) "${String.format(java.util.Locale.getDefault(), "%.1f", latestWeightValue)} kg" else "Brak danych",
                    icon = Icons.Default.MonitorWeight,
                    tint = MangoYellow,
                    modifier = Modifier.weight(1f)
                )
                SummaryTile(
                    title = "Kroki",
                    value = if (todaySteps > 0) String.format(java.util.Locale.getDefault(), "%d", todaySteps) else "Brak danych",
                    icon = Icons.Default.DirectionsWalk,
                    tint = LeafGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryTile(
                    title = "Aktywność",
                    value = if (burnedCalories > 0.0) "${burnedCalories.roundToInt()} kcal" else "Brak danych",
                    icon = Icons.Default.LocalFireDepartment,
                    tint = OrangeInk,
                    modifier = Modifier.weight(1f)
                )
                SummaryTile(
                    title = "Sen",
                    value = if (todaySleep > 0L) "${todaySleep / 60}h ${todaySleep % 60}m" else "Brak danych",
                    icon = Icons.Default.Nightlight,
                    tint = Color(0xFFB39DDB),
                    modifier = Modifier.weight(1f)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MutedSurface1)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.BarChart, contentDescription = null, tint = MangoYellow)
                        Text("Szybkie podsumowanie", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickStatCard("kalorie", "${consumedCalories}/${targetCalories}", "kcal", modifier = Modifier.weight(1f))
                        QuickStatCard("makra", "${consumedProtein}/${targetProtein}", "białko", modifier = Modifier.weight(1f))
                        QuickStatCard("aktywn.", "${(activity.steps ?: 0L) / 1000}k", "kroki", modifier = Modifier.weight(1f))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionCard("Jadłospis", Icons.Default.FoodBank, modifier = Modifier.weight(1f), onClick = onNavigateToMealPlan)
                ActionCard("Sport", Icons.Default.FitnessCenter, modifier = Modifier.weight(1f), onClick = onNavigateToSport)
                ActionCard("Pomiary", Icons.Default.MonitorWeight, modifier = Modifier.weight(1f), onClick = onNavigateToMeasurements)
            }

            ActionCard("AI Dietetyk", Icons.Default.AccessibilityNew, modifier = Modifier.fillMaxWidth(), onClick = onNavigateToAiCoach)

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrangeInk)
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MutedSurface2)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = tint)
                Text(title, color = Color(0xFFBDC7D6), fontSize = 11.sp)
            }
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun MetricPill(title: String, value: String, target: Int, progress: Float, tint: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161D27)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Color(0xFFBAC6D3), fontSize = 10.sp)
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.width(72.dp),
                color = tint,
                trackColor = Color(0xFF2A3441)
            )
            Text("cel ${target}g", color = Color(0xFF8EA0B5), fontSize = 9.sp)
        }
    }
}

@Composable
private fun QuickStatCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MutedSurface2)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = GoldAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(unit, color = Color(0xFF9AA8B8), fontSize = 9.sp)
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .height(82.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MutedSurface2)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MangoYellow)
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

