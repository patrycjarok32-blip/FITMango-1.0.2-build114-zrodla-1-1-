package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.PersonalProgressCards
import com.example.ui.components.SportsHistoryControls
import com.example.ui.components.SportsWeeklyReport
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LeafGreen
import com.example.ui.theme.MangoYellow
import com.example.ui.theme.MutedSurface1
import com.example.ui.theme.MutedSurface2
import com.example.ui.theme.OrangeInk
import com.example.ui.theme.WarmBrown
import com.example.viewmodel.ActivityRecoverySupport
import com.example.viewmodel.CurrentActivitySupport
import com.example.viewmodel.DietaViewModel
import com.example.viewmodel.NutritionWeekCard
import com.example.viewmodel.SportsGraphicsSupport
import com.example.viewmodel.SportsReportNumbers
import com.example.viewmodel.SportsSupport
import com.example.viewmodel.SportsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun SportsScreen(
    viewModel: DietaViewModel,
    onBack: () -> Unit,
    sports: SportsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val data by sports.data.collectAsStateWithLifecycle()
    val date by viewModel.currentDate.collectAsStateWithLifecycle()
    val today = LocalDate.parse(date)
    val health by viewModel.healthConnect.healthConnectSync.collectAsStateWithLifecycle()
    val current = remember(health, data, today) { CurrentActivitySupport.resolve(health, data, today) }

    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        sports.refreshIfGranted { viewModel.usability.changed() }
    }

    val busy by sports.busy.collectAsStateWithLifecycle()
    val message by sports.message.collectAsStateWithLifecycle()
    val aiBusy by viewModel.effectsAiBusy.collectAsStateWithLifecycle()
    val aiReport by viewModel.sportsWeeklyAi.collectAsStateWithLifecycle()
    val foodDays by viewModel.habits.days.collectAsStateWithLifecycle()
    val confirmations by viewModel.healthConnect.weeklyEnergy.confirmations.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()

    var showReport by rememberSaveable { mutableStateOf(false) }
    var showExtras by rememberSaveable { mutableStateOf(false) }
    var feedback by remember { mutableIntStateOf(0) }
    var permissionError by remember { mutableStateOf<String?>(null) }

    fun saved() { feedback++; viewModel.usability.changed() }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        sports.refresh { viewModel.usability.changed() }
    }

    val now = remember(data, today) { Instant.now() }
    val week = remember(data, today) { ActivityRecoverySupport.week(data, today, now) }
    val progress = ActivityRecoverySupport.progress(data, week)
    val start = SportsSupport.week(today)
    val graphics by androidx.compose.runtime.produceState<List<com.example.viewmodel.SportGraphicDay>>(emptyList(), data, today) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            SportsGraphicsSupport.days(data, start, now)
        }
    }
    val reportNumbers by androidx.compose.runtime.produceState<SportsReportNumbers?>(null, data, foodDays, confirmations, today) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            SportsGraphicsSupport.report(data, foodDays, confirmations, today, now)
        }
    }
    val completed = remember(data, start, today) { ActivityRecoverySupport.summary(data, start, today.minusDays(1), now) }
    val previous = remember(data, start, today) { ActivityRecoverySupport.summary(data, start.minusWeeks(1), today.minusDays(8), now) }

    val summarySteps = current.steps ?: 0L
    val summaryCalories = current.calories?.roundToInt() ?: 0
    val summarySleep = current.sleepMinutes ?: 0L
    val activeGoalProgress = if (summarySteps > 0L) (summarySteps.toFloat() / 10000f).coerceIn(0f, 1f) else 0f
    val sleepProgress = if (summarySleep > 0L) (summarySleep.toFloat() / 480f).coerceIn(0f, 1f) else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DarkBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Wróć") }
                Text("Aktywność i regeneracja", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            SuccessFeedback(feedback, "Zapisano")
            message?.let { Text(it, color = GoldAccent) }
            permissionError?.let { Text(it, color = ErrorInk) }
            SportsHistoryControls(
                busy,
                onPermissions = {
                    permissionError = null
                    runCatching {
                        launcher.launch(
                            setOf(
                                HealthPermission.getReadPermission(ExerciseSessionRecord::class),
                                HealthPermission.getReadPermission(DistanceRecord::class),
                                HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
                                HealthPermission.getReadPermission(StepsRecord::class),
                                HealthPermission.getReadPermission(SleepSessionRecord::class)
                            )
                        )
                    }.onFailure { permissionError = "Otwórz uprawnienia Health Connect w Profilu." }
                },
                onRefresh = { permissionError = null; sports.refresh { viewModel.usability.changed() } }
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MutedSurface1)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Dziś", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Podsumowanie aktywności", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(MangoYellow, OrangeInk)))
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Timeline, contentDescription = null, tint = Color.White)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricCard(
                            title = "Kroki",
                            value = if (summarySteps > 0L) summarySteps.toString() else "Brak danych",
                            unit = "kroków",
                            tint = LeafGreen,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Kalorie",
                            value = if (summaryCalories > 0) summaryCalories.toString() else "Brak danych",
                            unit = "kcal",
                            tint = OrangeInk,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricCard(
                            title = "Sen",
                            value = if (summarySleep > 0L) "${summarySleep / 60}h ${summarySleep % 60}m" else "Brak danych",
                            unit = "regeneracja",
                            tint = Color(0xFFB39DDB),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Postęp",
                            value = "${(activeGoalProgress * 100).toInt()}%",
                            unit = "cel kroku",
                            tint = MangoYellow,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryMiniCard(
                    icon = Icons.Default.DirectionsRun,
                    label = "Aktywność",
                    value = if (summarySteps > 0L) String.format(java.util.Locale.getDefault(), "%d", summarySteps) else "Brak danych",
                    tint = LeafGreen,
                    modifier = Modifier.weight(1f)
                )
                SummaryMiniCard(
                    icon = Icons.Default.LocalFireDepartment,
                    label = "Spalanie",
                    value = if (summaryCalories > 0) "${summaryCalories} kcal" else "Brak danych",
                    tint = OrangeInk,
                    modifier = Modifier.weight(1f)
                )
                SummaryMiniCard(
                    icon = Icons.Default.Nightlight,
                    label = "Sen",
                    value = if (summarySleep > 0L) "${summarySleep / 60}h" else "Brak danych",
                    tint = Color(0xFFB39DDB),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showReport && !showExtras,
                    onClick = { showReport = false; showExtras = false },
                    label = { Text("Aktywność") },
                    modifier = Modifier.testTag("sport_activity_tab")
                )
                FilterChip(
                    selected = showReport && !showExtras,
                    onClick = { showReport = true; showExtras = false },
                    label = { Text("Raport") },
                    modifier = Modifier.testTag("sport_report_tab")
                )
            }
        }

        item {
            TextButton(
                onClick = { showExtras = !showExtras; showReport = false },
                modifier = Modifier.testTag("sport_personal_progress")
            ) {
                Text(if (showExtras) "Wróć do aktywności ←" else "Zobacz postęp osobisty →")
            }
        }

        if (showExtras) {
            item { PersonalProgressCards(viewModel, data, today) }
        } else if (showReport) {
            item { NutritionWeekCard(viewModel) }
            item {
                reportNumbers?.let { numbers ->
                    SportsWeeklyReport(numbers, profile, aiReport, aiBusy) { viewModel.runWeeklyAiAnalysis() }
                }
            }
        } else {
            item {
                val currentDayData = data.daily.find { it.date == today.toString() }
                val weeklyAvg = if (graphics.isNotEmpty()) graphics.averageOf { it.steps ?: 0L } else 0.0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MutedSurface1)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.BarChart, contentDescription = null, tint = MangoYellow)
                            Text("Trend tygodniowy", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            QuickStatPill("Kroki", if (summarySteps > 0L) String.format(java.util.Locale.getDefault(), "%d", summarySteps) else "—", Modifier.weight(1f))
                            QuickStatPill("Średnia", if (weeklyAvg > 0.0) String.format(java.util.Locale.getDefault(), "%.0f", weeklyAvg) else "—", Modifier.weight(1f))
                            QuickStatPill("Czas", if (summarySleep > 0L) "${summarySleep / 60}h" else "—", Modifier.weight(1f))
                        }
                        Text(
                            "Dzisiaj: ${if (currentDayData != null) "${currentDayData.steps ?: 0} kroków" else "brak danych"} • Energia: ${summaryCalories} kcal • Regeneracja: ${if (summarySleep > 0L) "${summarySleep / 60}h" else "brak danych"}",
                            color = GoldAccent,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessFeedback(feedback: Int, label: String) {
    if (feedback > 0) {
        Text("$label", color = LeafGreen, fontSize = 12.sp)
    }
}

@Composable
private fun SummaryMiniCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = tint)
                Text(label, color = GoldAccent, fontSize = 10.sp)
            }
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    unit: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MutedSurface2)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = GoldAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(unit, color = Color(0xFF9EAEC2), fontSize = 10.sp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(tint.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(tint)
                        .width(if (value == "Brak danych") 0.dp else 120.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickStatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MutedSurface2)
    ) {
        Column(Modifier.padding(vertical = 8.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = GoldAccent, fontSize = 9.sp)
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

private fun <T> List<T>.averageOf(selector: (T) -> Long): Double {
    if (isEmpty()) return 0.0
    return sumOf { selector(it).toDouble() } / size.toDouble()
}

private fun Int.dpToPx(): Int = this

private fun <T> List<T>.averageOfLong(selector: (T) -> Long): Double = averageOf(selector)

private fun <T> List<T>.sumOfLong(selector: (T) -> Long): Long = sumOf { selector(it) }

private fun String.orDash(): String = if (isBlank()) "—" else this

private fun Double.clamp01(): Double = coerceIn(0.0, 1.0)

private fun Int.safeProgress(): Float = this.coerceIn(0, 100).toFloat() / 100f

private fun Long.formatDisplay(): String = this.toString()

private fun Long.formatMinutes(): String = "${this / 60}h ${this % 60}m"
