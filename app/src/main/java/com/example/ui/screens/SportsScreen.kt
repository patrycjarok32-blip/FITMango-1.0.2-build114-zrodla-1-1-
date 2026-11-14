package com.example.ui.screens

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

@Composable
fun SportsScreen(viewModel: DietaViewModel, onBack: () -> Unit, sports: SportsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val data by sports.data.collectAsStateWithLifecycle()
    val workouts by sports.workouts.collectAsStateWithLifecycle()
    val videoChallenges by sports.videoChallenges.collectAsStateWithLifecycle()
    val date by viewModel.currentDate.collectAsStateWithLifecycle()
    val today = LocalDate.parse(date)
    val health by viewModel.healthConnect.healthConnectSync.collectAsStateWithLifecycle()
    val current = remember(health, data, today) { CurrentActivitySupport.resolve(health, data, today) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        sports.refreshIfGranted { viewModel.usability.changed() }
    }
    val busy by sports.busy.collectAsStateWithLifecycle()
    val reducedMangoMotion = LocalReducedMangoMotion.current
    val message by sports.message.collectAsStateWithLifecycle()
    val aiBusy by viewModel.effectsAiBusy.collectAsStateWithLifecycle()
    val aiReport by viewModel.sportsWeeklyAi.collectAsStateWithLifecycle()
    val foodDays by viewModel.habits.days.collectAsStateWithLifecycle()
    val confirmations by viewModel.healthConnect.weeklyEnergy.confirmations.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    var activitySection by rememberSaveable { mutableStateOf("today") }
    var trainingPage by rememberSaveable { mutableStateOf("session") }
    var showWeeklyTrainingCalendar by rememberSaveable { mutableStateOf(false) }
    var showTrainingProfile by rememberSaveable { mutableStateOf(false) }
    var showProgramLibrary by rememberSaveable { mutableStateOf(false) }
    var showExtendedProgress by rememberSaveable { mutableStateOf(false) }
    var selectedImportedProgram by rememberSaveable { mutableStateOf("home") }
    var confirmImportedProgram by rememberSaveable { mutableStateOf(false) }
    val importContext = LocalContext.current
    val importScope = rememberCoroutineScope()
    var importedDays by remember { mutableStateOf<List<ImportedWorkoutTemplate>>(emptyList()) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var confirmCustomImport by remember { mutableStateOf(false) }
    var editImportedPlan by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importBusy = true
            importError = null
            importScope.launch {
                try {
                    val text = UniversalWorkoutImporter.read(importContext, uri,
                        importContext.contentResolver.getType(uri))
                    importedDays = UniversalWorkoutImporter.parse(text)
                    if (importedDays.isEmpty()) importError =
                        "Nie rozpoznano ćwiczeń w formacie nazwa + serie × powtórzenia. Nie zapisano żadnych zmian."
                } catch (e: Exception) {
                    importedDays = emptyList()
                    importError = e.message ?: "Nie udało się odczytać pliku."
                } finally { importBusy = false }
            }
        }
    }
    var showReport by rememberSaveable { mutableStateOf(false) }
    var showExtras by rememberSaveable { mutableStateOf(false) }
    var showSportNutrition by rememberSaveable { mutableStateOf(false) }
    var editGoal by rememberSaveable { mutableStateOf(false) }
    var feedback by remember { mutableIntStateOf(0) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    var add by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showDuplicates by rememberSaveable { mutableStateOf(false) }
    fun saved() { feedback++; viewModel.usability.changed() }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        sports.refresh { viewModel.usability.changed() }
    }
    val now = remember(data, today) { Instant.now() }
    val week = remember(data, today) { ActivityRecoverySupport.week(data, today, now) }
    val progress = ActivityRecoverySupport.progress(data, week)
    val start = SportsSupport.week(today)
    val graphics by produceState<List<SportGraphicDay>>(emptyList(), data, today) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { SportsGraphicsSupport.days(data, start, now) }
    }
    val reportNumbers by produceState<SportsReportNumbers?>(null, data, foodDays, confirmations, today) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { SportsGraphicsSupport.report(data, foodDays, confirmations, today, now) }
    }
    // Compare completed weekdays only. Today's partial data is displayed separately.
    val completed = remember(data, start, today) { ActivityRecoverySupport.summary(data, start, today.minusDays(1), now) }
    val previous = remember(data, start, today) { ActivityRecoverySupport.summary(data, start.minusWeeks(1), today.minusDays(8), now) }
    val visible = remember(data, today) { ActivityRecoverySupport.unique(data.history).filter {
        Instant.parse(it.start).atZone(ZoneId.systemDefault()).toLocalDate() in today.minusDays(29)..today
    }.sortedByDescending { it.start } }
    val duplicates = remember(data) { val ids = ActivityRecoverySupport.unique(data.history).map { it.id }.toSet(); data.history.filterNot { it.id in ids } }
    LazyColumn(Modifier.fillMaxSize().background(DarkBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("💪  Centrum sportowe", style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold, color = LeafGreen)
                        Text("Aktywność • siła • regeneracja", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onBack) { Text("Wróć") }
                }
            }
            SuccessFeedback(feedback, "Zapisano")
            message?.let { Text(it, color = GoldAccent) }
            permissionError?.let { Text(it, color = ErrorInk) }

        }
        if (activitySection == "progress") item {
            val monthStart = today.withDayOfMonth(1)
            val monthSessions = workouts.filter { it.ended > 0L &&
                runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d ->
                    !d.isBefore(monthStart) && !d.isAfter(today)
                } == true }
            val completedVideo = videoChallenges.flatMap { it.workouts }.count { workout ->
                workout.completedAt > 0L && java.time.Instant.ofEpochMilli(workout.completedAt)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().let { d ->
                        !d.isBefore(monthStart) && !d.isAfter(today)
                    }
            }
            Card(shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EAFB))) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📊 Mój miesiąc", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = Color(0xFF43235E))
                    Text("Podsumowanie do " + today.toString(), color = Color(0xFF625573))
                    Text("🏋️ Sesje siłowe: " + monthSessions.size, color = Color(0xFF43235E))
                    Text("🎬 Ukończone treningi wideo: " + completedVideo,
                        color = Color(0xFF43235E))
                    Text("📅 Dni z treningiem siłowym: " +
                        monthSessions.map { it.date }.distinct().size, color = Color(0xFF43235E))
                    Text("Raport pokazuje zapisane ukończone sesje; nie szacuje brakujących danych.",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFF625573))
                }
            }
        }
        if (activitySection == "training" && trainingPage == "plan") item {
            val availableVideos = videoChallenges.flatMap { challenge ->
                challenge.workouts.map { challenge.title to it }
            }.filter { it.second.url.isNotBlank() }
            var selectedVideoId by rememberSaveable { mutableStateOf("") }
            if (availableVideos.isNotEmpty()) {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🎬 Trening wideo w FITMango",
                            style = MaterialTheme.typography.titleMedium)
                        availableVideos.take(30).forEach { (program, video) ->
                            TextButton(onClick = {
                                selectedVideoId = if (selectedVideoId == video.id) "" else video.id
                            }) { Text(program + " • " + video.title) }
                            if (selectedVideoId == video.id) {
                                MangoInAppVideoPlayer(video.url)
                                Text("Odtworzenie filmu nie oznacza ukończenia treningu. Zapisz wykonanie w planie.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        if (activitySection == "training" && trainingPage == "session") item {
            val activeWorkout = workouts.firstOrNull { it.ended == 0L }
            if (activeWorkout != null) {
                Card(shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EAFB))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("▶ Mango Workout Mode", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = Color(0xFF43235E))
                        Text(activeWorkout.title, color = Color(0xFF43235E))
                        val done = activeWorkout.entries.count { it.done }
                        Text("Ukończone serie: $done / ${activeWorkout.entries.size}",
                            color = Color(0xFF43235E))
                        LinearProgressIndicator(
                            progress = { done.toFloat() / activeWorkout.entries.size.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth(), color = Color(0xFF9B54C8))
                        if (done == activeWorkout.entries.size && done > 0) {
                            var finishEffort by rememberSaveable(activeWorkout.id) { mutableIntStateOf(5) }
                            Text("✓ Wszystkie serie wykonane. Zakończ sesję, aby zatrzymać licznik i usunąć status „W trakcie”.",
                                color = Color(0xFF43235E), fontWeight = FontWeight.SemiBold)
                            Text("Odczuwany wysiłek: $finishEffort/10", color = Color(0xFF43235E))
                            Slider(value = finishEffort.toFloat(),
                                onValueChange = { finishEffort = it.toInt().coerceIn(1, 10) },
                                valueRange = 1f..10f, steps = 8)
                            Button(onClick = { sports.finishWorkout(activeWorkout.id, finishEffort) { saved() } },
                                enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text("✓ Zakończ i zapisz trening")
                            }
                        }
                        val elapsed = (System.currentTimeMillis() - activeWorkout.started).coerceAtLeast(0L)
                        if (elapsed > 4L * 60L * 60L * 1000L) {
                            Text("Uwaga: sesja jest otwarta ponad 4 godziny. Czas od rozpoczęcia nie oznacza czasu aktywnych ćwiczeń. Zakończ trening po sprawdzeniu serii.",
                                color = Color(0xFF8A4A22), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Zaznaczaj serie i zapisuj ciężary w odtwarzaczu sesji poniżej.",
                            color = Color(0xFF625573))
                    }
                }
            }
        }
        if (activitySection == "training" && trainingPage == "session") item {
            val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
            Card(shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFAF4))) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showWeeklyTrainingCalendar = !showWeeklyTrainingCalendar }) {
                        Text(if (showWeeklyTrainingCalendar) "🗓️ Zwiń kalendarz tygodnia" else "🗓️ Rozwiń kalendarz tygodnia (7 dni)",
                            style = MaterialTheme.typography.titleMedium)
                    }
                    if (showWeeklyTrainingCalendar) (0..6).forEach { offset ->
                        val day = weekStart.plusDays(offset.toLong())
                        val plans = data.plans.filter { it.date == day.toString() }
                        val done = workouts.count { it.date == day.toString() && it.ended > 0L }
                        val completedVideo = videoChallenges.flatMap { it.workouts }.count { video ->
                            video.completedAt > 0L &&
                                java.time.Instant.ofEpochMilli(video.completedAt)
                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate() == day
                        }
                        val active = workouts.any { it.date == day.toString() && it.ended == 0L }
                        val recordedActivities = ActivityRecoverySupport.daySessions(data, day)
                        val status = MangoWorkoutInsights.calendarStatus(day, today, plans, workouts)
                        val confirmed = plans.count { plan ->
                            PlannedActivityMatching.match(plan, data) != null
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(day.toString().substring(5), modifier = Modifier.width(58.dp),
                                color = Color(0xFF43235E))
                            Column(Modifier.weight(1f)) {
                                if (plans.isEmpty()) {
                                    Text("Bez zaplanowanego treningu", color = Color(0xFF43235E))
                                } else {
                                    plans.forEach { plan ->
                                        Text("• " + (if (plan.rest) "Regeneracja" else plan.title) +
                                            (if (plan.rest) "" else " · " + plan.category) +
                                            (plan.targetDistanceKm?.let { " · ${it} km" } ?: "") +
                                            (plan.targetMinutes?.let { " · ${it} min" } ?: "") +
                                            "  " + MangoWorkoutInsights.plannedStatus(plan, data, workouts),
                                            color = Color(0xFF43235E))
                                    }
                                }
                                Text((if (recordedActivities.isNotEmpty()) {
                                    "✓ Zarejestrowano: ${recordedActivities.size} • Wykonano z planu: $confirmed"
                                } else status) + if (completedVideo > 0) " • 🎬 Wideo: $completedVideo" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF625573))
                            }
                        }
                        HorizontalDivider(color = Color(0xFFE8DDEA))
                    }
                    if (showWeeklyTrainingCalendar) Text("Kalendarz pokazuje plany oraz aktywności z Health Connect osobno.",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFF625573))
                }
            }
        }
        if (activitySection == "training") item {
            val suggestions = remember(workouts) { MangoProgressiveOverload.suggestions(workouts) }
            if (suggestions.isNotEmpty()) {
                Card(shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EAFB))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🥭 Mango Trener — sugestie na kolejny trening",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = Color(0xFF43235E))
                        Text("Propozycje wymagają powtarzalnych lekkich sesji. Ciężar zatwierdzasz samodzielnie.",
                            color = Color(0xFF625573))
                        suggestions.take(5).forEach { suggestion ->
                            Text(suggestion.exercise + ": " + suggestion.previousKg +
                                " → " + suggestion.suggestedKg + " kg",
                                fontWeight = FontWeight.SemiBold, color = Color(0xFF43235E))
                            Text(suggestion.explanation, color = Color(0xFF625573),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (activitySection == "achievements") item {
            val finished = workouts.filter { it.ended > 0L }
            val finishedCount = finished.size
            val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val weeklyCount = finished.count { session ->
                runCatching { LocalDate.parse(session.date) }.getOrNull()
                    ?.let { !it.isBefore(weekStart) && !it.isAfter(today) } == true
            }
            val planMedals = PlanAchievementSupport.awards(data, workouts, videoChallenges)
            val strengthProgress = finished.flatMap { it.entries }
                .filter { it.done && it.kg > 0.0 }
                .groupBy { it.exercise.trim().lowercase() }
                .any { (_, sets) -> sets.size > 1 &&
                    sets.maxOf { it.kg } > sets.minOf { it.kg } }
            val badges = listOf(
                Triple("🌱", "Pierwszy trening", 1),
                Triple("💪", "10 treningów", 10),
                Triple("🏆", "20 treningów", 20),
                Triple("⭐", "50 treningów", 50),
                Triple("👑", "100 treningów", 100)
            )
            val earned = badges.count { finishedCount >= it.third } +
                planMedals.size + (if (weeklyCount >= 3) 1 else 0) +
                (if (strengthProgress) 1 else 0)
            val context = LocalContext.current
            val medalColor = Color(0xFF43235E)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EAFB)),
                    shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val points = MangoDailyTools.rewardPoints(finishedCount, planMedals.size)
                        var rewardState by remember { mutableStateOf(MangoRewardShop.load(context)) }
                        var rewardFeedback by remember { mutableStateOf("") }
                        val rewardBalance = MangoRewardShop.balance(points, rewardState)
                        val level = points / 100 + 1
                        Text("🥭 Mango Rewards", style = MaterialTheme.typography.titleLarge,
                            color = medalColor, fontWeight = FontWeight.Bold)
                        Text("$points punktów • Poziom $level", color = medalColor)
                        LinearProgressIndicator(
                            progress = { (points % 100) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF9B54C8))
                        Text("10 punktów za ukończoną sesję siłową • 50 za ukończony plan.",
                            color = Color(0xFF625573), style = MaterialTheme.typography.bodySmall)
                        Text("Dostępne: $rewardBalance pkt • Wydane: ${MangoRewardShop.spent(rewardState)} pkt",
                            color = medalColor)
                        Text("Sklep z wyglądami Mango", style = MaterialTheme.typography.titleMedium,
                            color = medalColor)
                        MangoRewardShop.catalog.forEach { reward ->
                            val owned = reward.id == "classic" || reward.id in rewardState.purchased
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(reward.icon + " " + reward.title +
                                    if (reward.price > 0) " • ${reward.price} pkt" else "",
                                    modifier = Modifier.weight(1f), color = medalColor,
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(
                                    enabled = rewardState.equipped != reward.id &&
                                        (owned || rewardBalance >= reward.price),
                                    onClick = {
                                        val updated = MangoRewardShop.purchase(rewardState, reward.id, points)
                                        if (updated != null) {
                                            rewardState = updated
                                            MangoRewardShop.save(context, updated)
                                            rewardFeedback = "Wybrano: ${reward.title}"
                                            viewModel.usability.changed()
                                        }
                                    }) {
                                    Text(when {
                                        rewardState.equipped == reward.id -> "Wybrane"
                                        owned -> "Załóż"
                                        else -> "Kup"
                                    })
                                }
                            }
                        }
                        if (rewardFeedback.isNotBlank()) Text(rewardFeedback,
                            style = MaterialTheme.typography.bodySmall, color = medalColor)
                        Text("Zakupy kosmetyczne zapisują się w kopii zapasowej. Punkty wynikają z historii treningów.",
                            style = MaterialTheme.typography.bodySmall, color = medalColor)
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE7C7)),
                    shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("🥭 Moje osiągnięcia", style = MaterialTheme.typography.headlineSmall,
                            color = medalColor, fontWeight = FontWeight.Bold)
                        Text(when {
                            earned >= 6 -> "👑 Mistrz Mango"
                            earned >= 3 -> "🥭 Sportowe Mango"
                            else -> "🌱 Młode Mango"
                        }, style = MaterialTheme.typography.titleMedium, color = medalColor)
                        Text("🏆 $earned zdobytych • $finishedCount ukończonych treningów",
                            color = medalColor)
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFAF4)),
                    shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("🎯 Cel tygodniowy", style = MaterialTheme.typography.titleLarge,
                            color = medalColor, fontWeight = FontWeight.Bold)
                        Text("$weeklyCount / 3 ukończone treningi", color = medalColor)
                        LinearProgressIndicator(
                            progress = { (weeklyCount / 3f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = Color(0xFF9B54C8))
                        androidx.compose.animation.AnimatedVisibility(
                            visible = weeklyCount >= 3,
                            enter = if (reducedMangoMotion) androidx.compose.animation.EnterTransition.None
                                else androidx.compose.animation.fadeIn(
                                    animationSpec = androidx.compose.animation.core.tween(500)) +
                                    androidx.compose.animation.scaleIn(
                                        initialScale = 0.65f,
                                        animationSpec = androidx.compose.animation.core.spring(
                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy)),
                            exit = androidx.compose.animation.ExitTransition.None
                        ) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🏅", style = MaterialTheme.typography.displayMedium)
                                Text("✨ Brawo! Cel tygodniowy osiągnięty!",
                                    color = Color(0xFF9B54C8), fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(if (weeklyCount >= 3) "🏅 Cel osiągnięty!" else
                            "Jeszcze ${3 - weeklyCount} treningów do odznaki",
                            color = Color(0xFF625573))
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFAF4)),
                    shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("🥇 Album ukończonych planów",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = medalColor)
                        if (planMedals.isEmpty()) Text(
                            "Medal pojawi się po ukończeniu wszystkich treningów w programie wideo lub cyklu siłowym.",
                            color = Color(0xFF625573))
                        planMedals.forEach { plan ->
                            val completedAt = plan.completedAt
                            val dateText = java.time.Instant.ofEpochMilli(completedAt)
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = true,
                                    enter = if (reducedMangoMotion) androidx.compose.animation.EnterTransition.None
                                        else androidx.compose.animation.fadeIn(
                                            animationSpec = androidx.compose.animation.core.tween(550)) +
                                            androidx.compose.animation.scaleIn(initialScale = 0.6f)
                                ) {
                                    Text("🏅", style = MaterialTheme.typography.headlineMedium)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(plan.title, fontWeight = FontWeight.Bold, color = medalColor)
                                    Text("100% • ${plan.total} treningów • $dateText",
                                        color = Color(0xFF625573))
                                }
                                TextButton(onClick = {
                                    runCatching {
                                        val image = createMangoDiploma(context, plan.title, plan.completed, plan.total, dateText)
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                                            .setType("image/png")
                                            .putExtra(android.content.Intent.EXTRA_STREAM, image)
                                            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        context.startActivity(android.content.Intent.createChooser(intent, "Zapisz lub udostępnij dyplom"))
                                    }.onFailure {
                                        android.widget.Toast.makeText(context, "Nie udało się zapisać dyplomu",
                                            android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }) { Text("Dyplom") }
                            }
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFAF4)),
                    shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("🏆 Kolekcja odznak", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = medalColor)
                        badges.forEach { (emoji, name, threshold) ->
                            val unlocked = finishedCount >= threshold
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(if (unlocked) emoji else "🔒",
                                    style = MaterialTheme.typography.headlineMedium)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.SemiBold, color = medalColor)
                                    LinearProgressIndicator(
                                        progress = { (finishedCount.toFloat() / threshold).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                                    Text(if (unlocked) "Zdobyta ✓" else "$finishedCount / $threshold",
                                        color = Color(0xFF625573))
                                }
                            }
                        }
                        Text(if (strengthProgress) "🏋️ Progres siłowy — zdobyta ✓" else
                            "🔒 Progres siłowy — zwiększ ciężar w zapisanych seriach",
                            color = medalColor)
                    }
                }
            }
        }
        if (activitySection == "today") item { FreshMangoSportHeader() }
        if (activitySection == "today") item {
            FreshMangoSportRings(progress.first, progress.second, week.activeDays, ActivityRecoverySupport.unit(data))
        }
        item {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)) {
                listOf(
                    Triple("today", "🏠", "Dzisiaj"),
                    Triple("training", "🏋️", "Trening"),
                    Triple("progress", "📈", "Postępy"),
                    Triple("recovery", "🌿", "Regeneracja"),
                    Triple("achievements", "🏆", "Odznaki")
                ).forEach { (key, emoji, label) ->
                    item {
                        FilterChip(
                            selected = activitySection == key,
                            onClick = { activitySection = key; showExtras = false; showReport = false },
                            label = { Text("$emoji  $label") },
                            modifier = Modifier.testTag("sport_${key}_tab")
                        )
                    }
                }
            }
        }
        if (activitySection == "today") item {
            SportTodayDashboard(today, data, workouts, videoChallenges,
                onStart = { activitySection = "training"; trainingPage = "session" },
                onPlan = { activitySection = "training"; trainingPage = "plan" },
                onChallenges = { activitySection = "training"; trainingPage = "plan" },
                onRecovery = { activitySection = "recovery" })
        }
        if (activitySection == "training") item {
            Text("Trening • plan, wykonanie i historia", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("session", "▶", "Trenuj"), Triple("plan", "🗓", "Edytuj plan"),
                    Triple("diary", "📒", "Dziennik")).forEach { (key, icon, label) ->
                    item { FilterChip(selected = trainingPage == key,
                        onClick = { trainingPage = key }, label = { Text("$icon $label") }) }
                }
            }
        }
        if (activitySection == "training" && trainingPage != "session") item {
            FreshMangoSportSection("🏋️", "Twój trening",
                "Wybierz plan, rozpocznij trening lub przejrzyj dziennik")
        }
        if (activitySection == "progress") item {
            FreshMangoSportSection("📈", "Twoje postępy",
                "Rekordy, wykresy i porównania na podstawie zapisanych treningów")
        }
        if (activitySection == "recovery") item {
            FreshMangoSportSection("🌿", "Czas na regenerację",
                "Sen i samopoczucie • informacje z dostępnych pomiarów")
        }
        if (activitySection == "training" && trainingPage == "plan") item {
            OutlinedButton(onClick = { showProgramLibrary = !showProgramLibrary },
                modifier = Modifier.fillMaxWidth()) {
                Text(if (showProgramLibrary) "📚 Zwiń bibliotekę planów" else "📚 Biblioteka planów • import i gotowe programy")
            }
        }
        if (activitySection == "training" && trainingPage == "plan" && showProgramLibrary) item {
            val program = ImportedTrainingPrograms.all.first { it.id == selectedImportedProgram }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📚 Biblioteka planów treningowych", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "image/*")) },
                        enabled = !busy && !importBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (importBusy) "Rozpoznawanie planu…" else "📄 Importuj plan z PDF, DOCX lub zdjęcia") }
                    importError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (importedDays.isNotEmpty()) {
                        Text("Podgląd rozpoznanego planu — nic nie zostało zapisane",
                            style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { editImportedPlan = !editImportedPlan }) {
                            Text(if (editImportedPlan) "Zakończ edycję" else "✎ Popraw rozpoznany plan")
                        }
                        importedDays.forEachIndexed { dayIndex, day ->
                            if (editImportedPlan) {
                                OutlinedTextField(
                                    value = day.title,
                                    onValueChange = { name ->
                                        importedDays = importedDays.toMutableList().also {
                                            it[dayIndex] = day.copy(title = name.take(120))
                                        }
                                    },
                                    label = { Text("Nazwa treningu") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else Text(day.title + " • " + day.exercises.size + " ćwiczeń")
                            day.exercises.forEachIndexed { exerciseIndex, exercise ->
                                if (editImportedPlan) {
                                    OutlinedTextField(value = exercise.name, onValueChange = { name ->
                                        importedDays = importedDays.toMutableList().also { days ->
                                            val exercises = days[dayIndex].exercises.toMutableList()
                                            exercises[exerciseIndex] = exercise.copy(name = name.take(120))
                                            days[dayIndex] = days[dayIndex].copy(exercises = exercises)
                                        }
                                    }, label = { Text("Ćwiczenie") }, modifier = Modifier.fillMaxWidth())
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value = exercise.sets.toString(), onValueChange = { raw ->
                                            raw.toIntOrNull()?.takeIf { it in 1..12 }?.let { count ->
                                                importedDays = importedDays.toMutableList().also { days ->
                                                    val exercises = days[dayIndex].exercises.toMutableList()
                                                    exercises[exerciseIndex] = exercise.copy(sets = count)
                                                    days[dayIndex] = days[dayIndex].copy(exercises = exercises)
                                                }
                                            }
                                        }, label = { Text("Serie") }, modifier = Modifier.weight(1f))
                                        OutlinedTextField(value = exercise.reps.toString(), onValueChange = { raw ->
                                            raw.toIntOrNull()?.takeIf { it in 1..100 }?.let { count ->
                                                importedDays = importedDays.toMutableList().also { days ->
                                                    val exercises = days[dayIndex].exercises.toMutableList()
                                                    exercises[exerciseIndex] = exercise.copy(reps = count)
                                                    days[dayIndex] = days[dayIndex].copy(exercises = exercises)
                                                }
                                            }
                                        }, label = { Text("Powtórzenia") }, modifier = Modifier.weight(1f))
                                    }
                                    TextButton(onClick = {
                                        importedDays = importedDays.toMutableList().also { days ->
                                            val exercises = days[dayIndex].exercises.toMutableList()
                                            exercises.removeAt(exerciseIndex)
                                            days[dayIndex] = days[dayIndex].copy(exercises = exercises)
                                        }
                                    }) { Text("Usuń ćwiczenie") }
                                } else Text("• " + exercise.name + " — " + exercise.sets + " × " + exercise.reps)
                            }
                        }
                        Text("Sprawdź nazwy, serie i powtórzenia. Nierozpoznane pozycje nie są dodawane.")
                        Button(onClick = { confirmCustomImport = true }, enabled = !busy && importedDays.all { day -> day.title.isNotBlank() && day.exercises.isNotEmpty() && day.exercises.all { it.name.isNotBlank() } }) {
                            Text("Zatwierdź i zastosuj rozpoznany plan")
                        }
                        TextButton(onClick = { importedDays = emptyList(); importError = null }) {
                            Text("Odrzuć import")
                        }
                    }
                    Text("Dwa gotowe plany z przesłanych dokumentów. Wybór i podgląd nie zmieniają zapisanych treningów.")
                    ImportedTrainingPrograms.all.forEach { option ->
                        OutlinedButton(
                            onClick = { selectedImportedProgram = option.id },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text((if (selectedImportedProgram == option.id) "✓ " else "") + option.name) }
                    }
                    Text("Wybrany: " + program.name, style = MaterialTheme.typography.titleMedium)
                    Text(program.source + " • " + program.days.size + " treningi")
                    program.days.forEach { day ->
                        Text(day.title + " — " + day.exercises.size + " ćwiczeń", style = MaterialTheme.typography.titleMedium)
                        day.exercises.forEach { exercise ->
                            Text("• " + exercise.name + " · " + exercise.sets + " × " + exercise.reps)
                        }
                    }
                    Text("Przed zapisaniem sprawdź parametry. Zakresy 8–10 zapisano jako 8, a Plank 60 oznacza sekundy. Plan PDF ma charakter osobistego szablonu; jego ilustracje nie są kopiowane.")
                    Button(onClick = { confirmImportedProgram = true }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) { Text("Zastosuj ten plan w kalendarzu") }
                }
            }
            if (confirmCustomImport) AlertDialog(
                onDismissRequest = { confirmCustomImport = false },
                title = { Text("Zastosować zaimportowany plan?") },
                text = { Text("Rozpoznane dni zastąpią plany w kalendarzu co drugi dzień od dzisiaj. Historia treningów i zapisane serie pozostaną bez zmian.") },
                confirmButton = {
                    Button(onClick = {
                        val plans = importedDays.mapIndexed { index, day ->
                            SportPlan(today.plusDays(index.toLong() * 2).toString(),
                                day.title, false, day.exercises)
                        }
                        sports.saveTrainingWeek(plans) { saved() }
                        confirmCustomImport = false
                        importedDays = emptyList()
                    }, enabled = !busy && importedDays.isNotEmpty()) { Text("Zapisz plan") }
                },
                dismissButton = { TextButton(onClick = { confirmCustomImport = false }) { Text("Anuluj") } }
            )
            if (confirmImportedProgram) AlertDialog(
                onDismissRequest = { confirmImportedProgram = false },
                title = { Text("Zastosować: " + program.name + "?") },
                text = { Text("Zapisz " + program.days.size +
                    " dni treningowych w kalendarzu (co drugi dzień) od dzisiaj? Plany na tych datach zostaną zastąpione, ale historia i wyniki pozostaną bez zmian. Drugi szablon nadal będzie dostępny w bibliotece.") },
                confirmButton = {
                    Button(onClick = {
                        val days = program.days.mapIndexed { index, template ->
                            SportPlan(today.plusDays(index.toLong() * 2).toString(),
                                template.title, false, template.exercises)
                        }
                        sports.saveTrainingWeek(days) { saved() }
                        confirmImportedProgram = false
                    }, enabled = !busy) { Text("Tak, zastosuj") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmImportedProgram = false }) { Text("Anuluj") }
                }
            )
        }
        if (activitySection == "training" && trainingPage == "plan") item {
            OutlinedButton(onClick = { showTrainingProfile = !showTrainingProfile },
                modifier = Modifier.fillMaxWidth().testTag("sport_profile_tab")) {
                Text(if (showTrainingProfile) "Ukryj plan siłowy" else "Plan treningów i zapis serii")
            }
        }
        if (activitySection == "progress") item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showExtras = true; showReport = false },
                    modifier = Modifier.weight(1f).testTag("sport_personal_progress")) {
                    Text("🏆  Rekordy i siła")
                }
                OutlinedButton(onClick = { showReport = true; showExtras = false },
                    modifier = Modifier.weight(1f).testTag("sport_report_shortcut")) {
                    Text("📊  Raport tygodnia")
                }
            }
        }
        if (showExtras) {
            item { TextButton(onClick = { showExtras = false }) { Text("← Postępy") } }
            item { PersonalProgressCards(viewModel, data, today) }
        } else if (showReport) {
            item { TextButton(onClick = { showReport = false }) { Text("← Postępy") } }
            item { NutritionWeekCard(viewModel) }
            item { reportNumbers?.let { numbers -> SportsWeeklyReport(numbers, profile, aiReport, aiBusy) { viewModel.runWeeklyAiAnalysis() } }
                ?: LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else {
        if (activitySection == "progress") item { StrengthProgressDashboard(data, today) }
        if (activitySection == "progress") item {
            OutlinedButton(onClick = { showExtendedProgress = !showExtendedProgress },
                modifier = Modifier.fillMaxWidth()) {
                Text(if (showExtendedProgress) "▲ Zwiń dodatkowe statystyki" else
                    "▼ Więcej: statystyki osobiste, aktywność i wskazówki")
            }
        }
        if (activitySection == "progress" && showExtendedProgress) item {
            PersonalProgressCards(viewModel, data, today)
        }
        if (activitySection == "progress" && showExtendedProgress) item {
            if (graphics.isNotEmpty()) SportActivityChart(graphics)
        }
        if (activitySection == "progress" && showExtendedProgress) item {
            SportInsightsCards(data, today, reportNumbers)
        }
        if (activitySection == "training" && trainingPage == "plan") item {
            val todayPlan = data.plans.firstOrNull { it.date == today.toString() && !it.rest &&
                it.exercises.isNotEmpty() }
            if (todayPlan != null) {
                val shortPlan = remember(todayPlan) {
                    com.example.viewmodel.MangoDailyTools.quickWorkout(todayPlan.exercises)
                }
                var showShortPlan by rememberSaveable { mutableStateOf(false) }
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("⏱️ Mam tylko 20 minut",
                            style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showShortPlan = !showShortPlan }) {
                            Text(if (showShortPlan) "Ukryj propozycję" else "Pokaż skrócony trening")
                        }
                        if (showShortPlan) {
                            shortPlan.forEach { exercise ->
                                Text("${exercise.name}: ${exercise.sets} × ${exercise.reps}, przerwa ${exercise.restSeconds} s")
                            }
                            Text("To orientacyjny plan: 45 sekund na serię plus przerwy. Oryginalny trening nie jest zmieniany.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (activitySection == "training" && trainingPage == "plan" && showTrainingProfile) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🏋️  Twój profil sportowy", style = MaterialTheme.typography.titleLarge,
                    color = LeafGreen, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Plan treningów, zapisane serie i rekordy. Dane pozostają w FITMango.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (activitySection == "training" && trainingPage == "session") item {
            var workoutDate by rememberSaveable { mutableStateOf(today.toString()) }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (workouts.any { it.ended == 0L }) "▶ Trening w toku" else "▶ Wybierz trening i rozpocznij", style = MaterialTheme.typography.titleMedium)
                    if (workouts.none { it.ended == 0L }) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(7) { offset ->
                            val d = SportsSupport.week(today).plusDays(offset.toLong()).toString()
                            FilterChip(selected = workoutDate == d, onClick = { workoutDate = d },
                                label = { Text(d.substring(8)) })
                        }
                    }
                    val dayPlans = data.plans.filter { it.date == workoutDate && !it.rest }
                    var showAddActivity by rememberSaveable(workoutDate) { mutableStateOf(false) }
                    Text("Plan dnia: ${dayPlans.size} sesji. Wybierz sesję poniżej, aby ją edytować, rozpocząć lub przenieść.",
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { showAddActivity = !showAddActivity }) {
                        Text(if (showAddActivity) "Zamknij" else "＋ Dodaj kolejną aktywność")
                    }
                    if (showAddActivity) {
                        var extraCategory by rememberSaveable(workoutDate) { mutableStateOf("Cardio") }
                        var extraTitle by rememberSaveable(workoutDate) { mutableStateOf("Long Run") }
                        var extraMinutes by rememberSaveable(workoutDate) { mutableStateOf("60") }
                        var extraDistance by rememberSaveable(workoutDate) { mutableStateOf("9") }
                        var extraDescription by rememberSaveable(workoutDate) { mutableStateOf("") }
                        Text("Rodzaj aktywności", style = MaterialTheme.typography.titleSmall)
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf("Siłowy", "Cardio", "Wzmacnianie", "Mobilność i stretching", "Inna aktywność")) { kind ->
                                FilterChip(selected = extraCategory == kind, onClick = { extraCategory = kind },
                                    label = { Text(kind) })
                            }
                        }
                        Text("Gotowe propozycje", style = MaterialTheme.typography.titleSmall)
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf("Long Run", "Easy Run", "Interwały", "HIIT", "Full Body",
                                "Core", "Stretching", "Mobilność", "PULL", "PUSH", "LEGS")) { name ->
                                androidx.compose.material3.AssistChip(onClick = {
                                    extraTitle = name
                                    extraCategory = when (name) {
                                        "Long Run", "Easy Run", "Interwały", "HIIT" -> "Cardio"
                                        "Stretching", "Mobilność" -> "Mobilność i stretching"
                                        "Core", "Full Body" -> "Wzmacnianie"
                                        else -> "Siłowy"
                                    }
                                    extraMinutes = when (name) {
                                        "Long Run" -> "60"
                                        "Stretching", "Mobilność" -> "20"
                                        else -> "30"
                                    }
                                    extraDistance = when (name) {
                                        "Long Run" -> "9"
                                        "Easy Run" -> "4"
                                        else -> ""
                                    }
                                }, label = { Text(name) })
                            }
                        }
                        OutlinedTextField(value = extraTitle, onValueChange = { extraTitle = it.take(120) },
                            label = { Text("Nazwa sesji") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = extraMinutes, onValueChange = { extraMinutes = it },
                            label = { Text("Czas (minuty)") }, modifier = Modifier.fillMaxWidth())
                        if (extraCategory == "Cardio") {
                            OutlinedTextField(value = extraDistance, onValueChange = { extraDistance = it },
                                label = { Text("Dystans (km, opcjonalnie)") }, modifier = Modifier.fillMaxWidth())
                        }
                        OutlinedTextField(value = extraDescription, onValueChange = { extraDescription = it.take(1000) },
                            label = { Text("Opis (opcjonalnie)") }, modifier = Modifier.fillMaxWidth())
                        val mins = extraMinutes.toIntOrNull()
                        val km = if (extraCategory == "Cardio") extraDistance.replace(',', '.').toDoubleOrNull() else null
                        val validExtra = extraTitle.isNotBlank() && (mins == null && extraMinutes.isBlank() || mins != null && mins in 1..1440) &&
                            (extraCategory != "Cardio" || extraDistance.isBlank() || km != null && km.isFinite() && km > 0 && km <= 1000)
                        Button(enabled = validExtra && !busy, onClick = {
                            sports.addPlannedActivity(workoutDate, extraTitle, extraCategory,
                                mins, km, extraDescription) { saved(); showAddActivity = false }
                        }) { Text("Zapisz dodatkową sesję") }
                    }
                    var selectedSessionKey by rememberSaveable(workoutDate) { mutableStateOf("") }
                    val healthDaySessions = ActivityRecoverySupport.daySessions(
                        data, LocalDate.parse(workoutDate))
                    Text("Zarejestrowane aktywności (${healthDaySessions.size})",
                        style = MaterialTheme.typography.bodySmall)
                    healthDaySessions.forEach { recorded ->
                        Text("✓ ${recorded.title} · " +
                            (if (recorded.source.contains("huawei", ignoreCase = true)) "Zegarek Huawei / Health Connect"
                             else if (recorded.source.contains("manual", ignoreCase = true) ||
                                 recorded.id.startsWith("manual|")) "Wpis ręczny"
                             else "Health Connect"),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Jednoznacznie dopasowane biegi są oznaczane jako wykonane. " +
                        "Pozostałe aktywności można powiązać ręcznie.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Twoje zaplanowane sesje (${dayPlans.size})",
                        style = MaterialTheme.typography.titleMedium)
                    var transferKey by rememberSaveable(workoutDate) { mutableStateOf("") }
                    var transferDate by rememberSaveable(workoutDate) { mutableStateOf(workoutDate) }
                    var transferCopy by rememberSaveable(workoutDate) { mutableStateOf(true) }
                    dayPlans.forEachIndexed { index, plan ->
                        val key = plan.sessionId.ifBlank { "legacy:$index" }
                        val chosen = selectedSessionKey == key || (selectedSessionKey.isBlank() && index == 0)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = chosen, onClick = { selectedSessionKey = key },
                                label = { Text("${plan.title} • ${plan.category}") })
                            TextButton(enabled = !busy, onClick = {
                                transferKey = key; transferDate = workoutDate
                            }) { Text("Kopiuj / przenieś") }
                            if (plan.sessionId.isNotBlank()) {
                                TextButton(enabled = !busy, onClick = {
                                    sports.removePlannedActivity(plan.sessionId) { saved(); selectedSessionKey = "" }
                                }) { Text("Usuń") }
                            }
                        }
                        val linkedId = data.linkedActivities[plan.sessionId]
                        val linkedRecord = PlannedActivityMatching.match(plan, data)
                        Text(MangoWorkoutInsights.plannedStatus(plan, data, workouts),
                            color = LeafGreen, style = MaterialTheme.typography.labelMedium)
                        if (linkedRecord != null) {
                            Text("✓ Wykonano: ${linkedRecord.title} · zapis z dziennika / Health Connect",
                                color = LeafGreen, style = MaterialTheme.typography.bodyMedium)
                            if (linkedId != null) TextButton(enabled = !busy, onClick = {
                                sports.unlinkRecordedActivity(plan.sessionId) { saved() }
                            }) { Text("Cofnij powiązanie") }
                        } else if (plan.sessionId.isNotBlank() && !plan.rest && chosen) {
                            val availableRecords = healthDaySessions.filter { recorded ->
                                data.linkedActivities.none { it.key != plan.sessionId && it.value == recorded.id }
                            }
                            val isRun = plan.category == "Cardio" &&
                                listOf("run", "bieg").any { plan.title.contains(it, ignoreCase = true) }
                            val suggestions = availableRecords.filter { recorded ->
                                if (isRun) listOf("run", "bieg").any { recorded.title.contains(it, ignoreCase = true) }
                                else recorded.title.equals(plan.title, ignoreCase = true)
                            }
                            if (suggestions.isNotEmpty()) Text(
                                "Znaleziono ${suggestions.size} możliwych dopasowań. Potwierdź właściwą aktywność:",
                                style = MaterialTheme.typography.bodySmall)
                            else if (availableRecords.isNotEmpty()) Text(
                                "Brak pewnego dopasowania. Możesz ręcznie wskazać aktywność z tego dnia.",
                                style = MaterialTheme.typography.bodySmall)
                            (if (suggestions.isNotEmpty()) suggestions else availableRecords).forEach { recorded ->
                                TextButton(enabled = !busy, onClick = {
                                    sports.linkRecordedActivity(plan.sessionId, recorded.id) { saved() }
                                }) {
                                    Text("✓ Powiąż: ${recorded.title} · " +
                                        "${java.time.Duration.between(Instant.parse(recorded.start), Instant.parse(recorded.end)).toMinutes()} min" +
                                        (recorded.distanceKm?.let { " · ${it} km" } ?: ""))
                                }
                            }
                        }
                        if (chosen) {
                            Text(buildString {
                                plan.targetDistanceKm?.let { append("$it km  ") }
                                plan.targetMinutes?.let { append("$it min  ") }
                                if (plan.description.isNotBlank()) append(plan.description)
                            }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (transferKey.isNotBlank()) {
                        Text("Przenieś lub skopiuj wybraną sesję", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = transferCopy, onClick = { transferCopy = true }, label = { Text("Kopiuj") })
                            FilterChip(selected = !transferCopy, onClick = { transferCopy = false }, label = { Text("Przenieś") })
                        }
                        OutlinedTextField(value = transferDate, onValueChange = { transferDate = it.take(10) },
                            label = { Text("Data docelowa (RRRR-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                        val targetValid = runCatching { LocalDate.parse(transferDate) }.isSuccess && transferDate != workoutDate
                        Button(enabled = targetValid && !busy, onClick = {
                            val original = dayPlans.firstOrNull { plan ->
                                plan.sessionId.ifBlank { "legacy:${dayPlans.indexOf(plan)}" } == transferKey
                            }
                            if (original != null) {
                                sports.transferPlannedActivity(original.sessionId, workoutDate, transferDate, transferCopy) {
                                    saved(); transferKey = ""
                                }
                            }
                        }) { Text(if (transferCopy) "Skopiuj sesję" else "Przenieś sesję") }
                    }
                    val selectedPlan = dayPlans.firstOrNull { plan ->
                        val index = dayPlans.indexOf(plan)
                        selectedSessionKey == plan.sessionId.ifBlank { "legacy:$index" }
                    } ?: dayPlans.firstOrNull() ?: data.plans.firstOrNull { it.date == workoutDate }

                    var editSessionDetails by rememberSaveable(workoutDate) { mutableStateOf(false) }
                    if (selectedPlan != null && !selectedPlan.rest) {
                        TextButton(onClick = { editSessionDetails = !editSessionDetails }) {
                            Text(if (editSessionDetails) "Zamknij edytor sesji" else "✏️ Edytuj rodzaj, opis i cel sesji")
                        }
                        if (selectedPlan.description.isNotBlank() || selectedPlan.targetMinutes != null ||
                            selectedPlan.targetDistanceKm != null || selectedPlan.distanceRange.isNotBlank()) {
                            Text(buildString {
                                append(selectedPlan.category)
                                if (selectedPlan.distanceRange.isNotBlank()) append(" • cel: ${selectedPlan.distanceRange}")
                                selectedPlan.targetDistanceKm?.let { append(" • ${it} km") }
                                selectedPlan.targetMinutes?.let { append(" • ${it} min") }
                                if (selectedPlan.description.isNotBlank()) append("\n${selectedPlan.description}")
                            }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (editSessionDetails) {
                        val templates = listOf(
                            Triple("Cardio", "Long Run", "8–10 km"),
                            Triple("Cardio", "Easy Run", "3–5 km"),
                            Triple("Cardio", "Interwały", ""),
                            Triple("Cardio", "Rower", ""),
                            Triple("Mobilność i stretching", "Full Body Stretch", ""),
                            Triple("Mobilność i stretching", "Lower Body Stretch", ""),
                            Triple("Mobilność i stretching", "Mobility Flow", ""),
                            Triple("Siłowy", "Push", ""),
                            Triple("Siłowy", "Pull", ""),
                            Triple("Siłowy", "Legs", ""),
                            Triple("Wzmacnianie", "Wzmacnianie", ""),
                            Triple("Trening z filmem", "Trening z filmem", ""),
                            Triple("Inna aktywność", "Własna sesja", ""))
                        var sessionCategory by rememberSaveable(workoutDate, selectedPlan?.sessionId, selectedPlan?.title) { mutableStateOf(selectedPlan?.category ?: "Cardio") }
                        var sessionTitle by rememberSaveable(workoutDate, selectedPlan?.sessionId, selectedPlan?.title) { mutableStateOf(selectedPlan?.title ?: "") }
                        var sessionDescription by rememberSaveable(workoutDate, selectedPlan?.sessionId, selectedPlan?.title) { mutableStateOf(selectedPlan?.description ?: "") }
                        var sessionDistance by rememberSaveable(workoutDate, selectedPlan?.sessionId, selectedPlan?.title) { mutableStateOf(selectedPlan?.targetDistanceKm?.toString() ?: "") }
                        var sessionMinutes by rememberSaveable(workoutDate, selectedPlan?.sessionId, selectedPlan?.title) { mutableStateOf(selectedPlan?.targetMinutes?.toString() ?: "") }
                        var sessionRange by rememberSaveable(workoutDate, selectedPlan?.sessionId, selectedPlan?.title) { mutableStateOf(selectedPlan?.distanceRange ?: "") }
                        Text("Rodzaj treningu", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Siłowy", "Cardio", "Wzmacnianie", "Mobilność i stretching").forEach { kind ->
                                FilterChip(selected = sessionCategory == kind, onClick = { sessionCategory = kind },
                                    label = { Text(kind) })
                            }
                        }
                        Text("Szablony sesji", style = MaterialTheme.typography.titleMedium)
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(templates.size) { index ->
                                val template = templates[index]
                                androidx.compose.material3.AssistChip(onClick = {
                                    sessionCategory = template.first
                                    sessionTitle = template.second
                                    sessionRange = template.third
                                    if (template.second == "Long Run") {
                                        sessionDistance = "9"
                                        sessionMinutes = "60"
                                    }
                                }, label = { Text(template.second) })
                            }
                        }
                        OutlinedTextField(value = sessionCategory, onValueChange = { sessionCategory = it },
                            label = { Text("Rodzaj aktywności") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = sessionTitle, onValueChange = { sessionTitle = it.take(120) },
                            label = { Text("Nazwa sesji") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = sessionRange, onValueChange = { sessionRange = it.take(80) },
                            label = { Text("Cel, np. 8–10 km") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = sessionDistance, onValueChange = { sessionDistance = it },
                            label = { Text("Docelowy dystans (km)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = sessionMinutes, onValueChange = { sessionMinutes = it },
                            label = { Text("Docelowy czas (min)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = sessionDescription, onValueChange = { sessionDescription = it.take(1000) },
                            label = { Text("Opis i wskazówki") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        val parsedDistance = sessionDistance.replace(',', '.').toDoubleOrNull()
                        val parsedMinutes = sessionMinutes.toIntOrNull()
                        val valid = sessionTitle.isNotBlank() && sessionCategory in setOf(
                            "Siłowy", "Cardio", "Wzmacnianie", "Mobilność i stretching", "Trening z filmem", "Inna aktywność") &&
                            (sessionDistance.isBlank() || (parsedDistance != null && parsedDistance.isFinite() && parsedDistance > 0.0 && parsedDistance <= 1000.0)) &&
                            (sessionMinutes.isBlank() || (parsedMinutes != null && parsedMinutes in 1..1440))
                        Button(enabled = valid && !busy, onClick = {
                            if (selectedPlan?.sessionId?.isNotBlank() == true) {
                                sports.updatePlannedActivity(selectedPlan.sessionId, workoutDate, sessionTitle,
                                    sessionCategory, parsedMinutes, parsedDistance, sessionDescription) {
                                    saved(); editSessionDetails = false
                                }
                            } else {
                                sports.saveSessionDetails(workoutDate, sessionTitle, sessionCategory,
                                    sessionDescription, parsedDistance, parsedMinutes, sessionRange) {
                                    saved(); editSessionDetails = false
                                }
                            }
                        }) { Text("Zapisz szczegóły sesji") }
                    }
                    var quickMinutes by rememberSaveable(workoutDate) { mutableIntStateOf(0) }
                    if (workouts.none { it.ended == 0L } && selectedPlan != null &&
                        !selectedPlan.rest && selectedPlan.exercises.isNotEmpty()) {
                        Text("⏱️ Trening ekspresowy", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 15, 20, 30).forEach { duration ->
                                FilterChip(selected = quickMinutes == duration,
                                    onClick = { quickMinutes = duration },
                                    label = { Text(if (duration == 0) "Pełny" else "$duration min") })
                            }
                        }
                        if (quickMinutes > 0) Text("Wersja skrócona nie zmienia zapisanego planu.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    val displayedPlan = if (quickMinutes > 0 && selectedPlan != null)
                        MangoWorkoutInsights.quickPlan(selectedPlan, quickMinutes) else selectedPlan
                    WorkoutModeCard(workoutDate, displayedPlan,
                        workouts, data, busy,
                        onStart = { plan -> sports.startWorkout(plan) { saved() } },
                        onEntry = { id, index, reps, kg, done ->
                            sports.updateWorkoutEntry(id, index, reps, kg, done) { saved() }
                        },
                        onFinish = { id, effort -> sports.finishWorkout(id, effort) { saved() } },
                        onPause = { id, paused -> sports.setWorkoutPaused(id, paused) { saved() } },
                        onDiscard = { id -> sports.discardWorkout(id) { saved() } },
                        onReplaceExercise = { id, old, new -> sports.replaceWorkoutExercise(id, old, new) { saved() } })
                }
            }
        }
        if (activitySection == "training" && trainingPage == "plan" && showTrainingProfile) item {
            var plannerDays by rememberSaveable { mutableIntStateOf(3) }
            var plannerMinutes by rememberSaveable { mutableIntStateOf(45) }
            var plannerPreview by rememberSaveable { mutableStateOf(false) }
            val suggestions = MangoTrainingPlanner.suggest(today, plannerDays, plannerMinutes, data.plans)
            MangoCard(Modifier.fillMaxWidth().testTag("training4_week_planner")) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✨ Inteligentny planer tygodnia", style = MaterialTheme.typography.titleMedium)
                    Text("Wybierz liczbę dni i czas. Twoje zapisane treningi pozostaną bez zmian.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Dni treningowe w tygodniu")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(2, 3, 4, 5).forEach { days ->
                            FilterChip(selected = plannerDays == days, onClick = { plannerDays = days; plannerPreview = false },
                                label = { Text("$days") })
                        }
                    }
                    Text("Czas jednej sesji")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(20, 30, 45, 60).forEach { minutes ->
                            FilterChip(selected = plannerMinutes == minutes, onClick = { plannerMinutes = minutes; plannerPreview = false },
                                label = { Text("$minutes min") })
                        }
                    }
                    OutlinedButton(onClick = { plannerPreview = !plannerPreview }) {
                        Text(if (plannerPreview) "Ukryj propozycję" else "Pokaż propozycję")
                    }
                    if (plannerPreview) {
                        if (suggestions.isEmpty()) Text("Wszystkie proponowane dni są już zajęte.")
                        suggestions.forEach { plan ->
                            Text("${plan.date} • ${plan.title} • ${plan.targetMinutes} min",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Button(enabled = !busy && suggestions.isNotEmpty(), onClick = {
                            sports.saveTrainingWeek(suggestions) { saved(); plannerPreview = false }
                        }) { Text("Zapisz wolne dni") }
                    }
                }
            }
        }
        if (activitySection == "training" && trainingPage == "plan" && showTrainingProfile) item {
            MangoCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    WorkoutOverviewCard(today, data, workouts, videoChallenges, busy,
                        onRestDay = { day -> sports.plan(day, "Regeneracja", true) { saved() } })
                }
            }
        }
        if (activitySection == "training" && trainingPage == "plan" && showTrainingProfile) item {
            SportProfileCards(data, today, busy,
                onPlan = { day, title, rest -> sports.plan(day, title, rest) { saved() } },
                onRemovePlan = { day -> sports.removePlan(day) { saved() } },
                onAddSet = { day, exercise, reps, kg -> sports.addSet(day, exercise, reps, kg) { saved() } },
                onSaveWeek = { plans -> sports.saveTrainingWeek(plans) { saved() } },
                onMovePlan = { from, to, swap -> sports.movePlannedWorkout(from, to, swap) { saved() } },
                onCopyWeeks = { start, count -> sports.copyWorkoutWeeks(start, count) { saved() } },
                onShiftUpcoming = { from, days -> sports.shiftUpcomingWorkouts(from, days) { saved() } },
                onRemoveSet = { id -> sports.removeSet(id) { saved() } },
                onUpdateExercises = { date, exercises -> sports.updatePlannedExercises(date, exercises) { saved() } })
        }
        if (activitySection == "today") item {
            var showWeeklyReview by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { showWeeklyReview = !showWeeklyReview }) {
                Text(if (showWeeklyReview) "Zwiń podsumowanie tygodnia" else "📊 Pokaż podsumowanie tygodnia")
            }
            if (showWeeklyReview) StrengthWeeklyReview(data, today)
        }
        if (activitySection == "today") item {
            val check = data.recovery.firstOrNull { it.date == today.toString() }
            var quickEnergy by rememberSaveable { mutableIntStateOf(check?.energy ?: 3) }
            var quickFatigue by rememberSaveable { mutableIntStateOf(check?.fatigue ?: 3) }
            var quickExpanded by rememberSaveable { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { quickExpanded = !quickExpanded }) {
                        Text(if (quickExpanded) "🌿 Zwiń ocenę samopoczucia" else
                            "🌿 Samopoczucie • " + (if (check == null) "brak pomiaru, dodaj" else "zapisano dziś"))
                    }
                    if (quickExpanded) {
                        Text("Energia: " + quickEnergy + "/5")
                        Slider(value = quickEnergy.toFloat(), onValueChange = { quickEnergy = it.toInt().coerceIn(1,5) },
                            valueRange = 1f..5f, steps = 3)
                        Text("Zmęczenie: " + quickFatigue + "/5")
                        Slider(value = quickFatigue.toFloat(), onValueChange = { quickFatigue = it.toInt().coerceIn(1,5) },
                            valueRange = 1f..5f, steps = 3)
                        Button(onClick = {
                            sports.recovery(today.toString(), quickEnergy, quickFatigue) { saved(); quickExpanded = false }
                        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Zapisz samopoczucie") }
                    }
                }
            }
        }
        if (activitySection == "today") item { StrengthRecoverySuggestion(data, today, onShift = { sports.shiftUpcomingWorkouts(today.toString(), 1) { saved() } }, busy = busy) }
        if (activitySection == "today") item { ActivityTodayCard(data, today, current) }
        if (activitySection == "today" || (activitySection == "training" && trainingPage == "diary")) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SportsHistoryControls(busy,
                onPermissions = { permissionError = null; runCatching { launcher.launch(setOf(
                    HealthPermission.getReadPermission(ExerciseSessionRecord::class), HealthPermission.getReadPermission(DistanceRecord::class),
                    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class), HealthPermission.getReadPermission(StepsRecord::class),
                    HealthPermission.getReadPermission(SleepSessionRecord::class))) }.onFailure { permissionError = "Otwórz uprawnienia Health Connect w Profilu." } },
                onRefresh = { permissionError = null; sports.refresh { viewModel.usability.changed() } })
        } }
        if (activitySection == "today" || (activitySection == "training" && trainingPage == "diary")) item { if (graphics.isNotEmpty()) SportActivityChart(graphics) }
        if (activitySection == "training" && trainingPage == "diary") item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📒  Twój dziennik treningów", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LeafGreen)
                MangoCard(Modifier.fillMaxWidth().testTag("health_connect_training_diary")) {
                Column(Modifier.padding(14.dp).then(if (reducedMangoMotion) Modifier else Modifier.animateContentSize(animationSpec = androidx.compose.animation.core.tween(220))), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Historia treningów • 30 dni",
                        style = MaterialTheme.typography.titleMedium, color = MangoMint)
                    Text("Zapisane aktywności: ${visible.size}",
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { showHistory = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("📋  Otwórz historię treningów")
                    }
                    if (duplicates.isNotEmpty()) Text("Wykryte powtórzone wpisy: ${duplicates.size}",
                        style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                }
                }
            }
        }

        if (activitySection == "today" || (activitySection == "training" && trainingPage == "diary")) item { MangoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Twój tydzień • $start – ${start.plusDays(6)}", style = MaterialTheme.typography.labelLarge, color = GoldAccent)
            SportGoalProgress(data, week, completed, previous, today == start)
            TextButton(onClick = { editGoal = !editGoal }) { Text(if (editGoal) "Zwiń ustawienia celu −" else "Zmień cel tygodniowy +") }
            if (editGoal) {
            Text("Wybierz cel tygodniowy")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("days" to "Dni", "minutes" to "Minuty", "walks" to "Spacery").forEach { (mode, label) ->
                    FilterChip(selected = data.goalMode == mode, enabled = !busy, onClick = {
                        sports.setGoal(mode, when (mode) { "minutes" -> data.minuteGoal; "walks" -> data.walkGoal; else -> data.goal }, ::saved)
                    }, label = { Text(label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val target = progress.second
                val step = if (data.goalMode == "minutes") 10 else 1
                val max = when (data.goalMode) { "minutes" -> 1500; "walks" -> 21; else -> 7 }
                TextButton(enabled = !busy && target > step, onClick = { sports.setGoal(data.goalMode, (target - step).coerceAtLeast(step), ::saved) }) { Text("−") }
                Text("Cel: $target ${ActivityRecoverySupport.unit(data)}")
                TextButton(enabled = !busy && target < max, onClick = { sports.setGoal(data.goalMode, (target + step).coerceAtMost(max), ::saved) }) { Text("+") }
            }
            }
        } } }
        if (activitySection == "training" && trainingPage == "diary") item { Button(onClick = { add = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("sport_add_activity")) { Text("➕  Dodaj wykonaną aktywność") } }
        if (activitySection == "recovery") item {
            Text("🌙  Regeneracja i samopoczucie", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = LeafGreen)
        }
        if (activitySection == "recovery") item { RecoveryCard(data, today, busy,
            onSave = { d, energy, fatigue -> sports.recovery(d, energy, fatigue, ::saved) },
            onRemove = { sports.removeRecovery(it, ::saved) }) }
        if (activitySection == "recovery") item { SportWellnessCards(data, today) }
        if (activitySection == "recovery") item { SportInsightsCards(data, today, reportNumbers) }
        if (activitySection == "training" && trainingPage == "diary") item {
            MangoCard(Modifier.fillMaxWidth().testTag("sport_nutrition_connection")) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🥗  Sport + dieta", style = MaterialTheme.typography.titleMedium, color = LeafGreen)
                    Text("Sprawdź odżywianie obok treningów. Korzystamy z istniejącego raportu diety — bez kopiowania posiłków i bez zwiększania limitu kalorii o trening.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { showSportNutrition = !showSportNutrition }) {
                        Text(if (showSportNutrition) "Ukryj raport odżywiania −" else "Pokaż raport odżywiania +")
                    }
                }
            }
        }
        if (showSportNutrition && activitySection == "training" && trainingPage == "diary") item { NutritionWeekCard(viewModel) }

        if (activitySection == "training" && trainingPage == "diary") item {
            val sessionsToday = ActivityRecoverySupport.daySessions(data, today)
            val totalMinutes = ActivityRecoverySupport.dayMinutes(data, today)
            MangoCard(Modifier.fillMaxWidth().testTag("journal3_day_summary")) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📅 Dziennik 3.0 • ${today}", style = MaterialTheme.typography.titleMedium)
                    Text("${sessionsToday.size} aktywności • ${totalMinutes} minut", style = MaterialTheme.typography.bodyMedium)
                    if (sessionsToday.isEmpty()) Text("Brak zapisanych aktywności na ten dzień.")
                    sessionsToday.forEach { session ->
                        val mins = java.time.Duration.between(Instant.parse(session.start), Instant.parse(session.end)).toMinutes()
                        Text("${sportActivityIcon(session.title)} ${session.title} • ${mins} min • Health Connect / dziennik",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Health Connect i wpisy ręczne są pokazywane razem, bez powtórzeń.",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (activitySection == "training" && trainingPage == "diary") item {
            TextButton(onClick = { showHistory = !showHistory }) { Text(if (showHistory) "Zwiń historię treningów −" else "Historia treningów (${visible.size}) +") }
            Text("Kalorie treningów mają charakter informacyjny. Nie zwiększają celu jedzenia i nie są ponownie dodawane do spalania ogółem.", style = MaterialTheme.typography.bodySmall)
            if (duplicates.isNotEmpty()) TextButton(onClick = { showDuplicates = !showDuplicates }) { Text("Pominięte powtórzenia: ${duplicates.size} • ${if (showDuplicates) "zwiń" else "pokaż"}") }
        }
        if (activitySection == "training" && trainingPage == "diary" && showDuplicates) items(duplicates, key = { "duplicate|" + it.id }) { session ->
            Text("Pominięto: ${session.title} • ${sportTime(Instant.parse(session.start).toEpochMilli())} • ${session.source}", style = MaterialTheme.typography.bodySmall)
            if (ActivityRecoverySupport.manual(session)) TextButton(enabled = !busy, onClick = { sports.removeActivity(session.id, ::saved) }) { Text("Usuń ręczne powtórzenie") }
        }
        if (activitySection == "training" && trainingPage == "diary" && showHistory) {
            if (visible.isEmpty()) item { Text("Brak zapisanych treningów z ostatnich 30 dni. Możesz dodać aktywność ręcznie lub odświeżyć Health Connect.") }
            items(visible, key = { it.id }) { session ->
                MangoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    val duration = java.time.Duration.between(Instant.parse(session.start), Instant.parse(session.end)).toMinutes()
                    Text("${sportActivityIcon(session.title)}  ${session.title}", style = MaterialTheme.typography.titleMedium)
                    Text("${sportTime(Instant.parse(session.start).toEpochMilli())} • $duration min")
                    Text("Aktywne: ${session.activeKcal?.let { "${it.toInt()} kcal" } ?: "brak danych"} • Dystans: ${session.distanceKm?.let { String.format(java.util.Locale.getDefault(), "%.2f km", it) } ?: "brak danych"}")
                    Text("Źródło: " + (if (session.source.contains("huawei", ignoreCase = true)) "Zegarek Huawei" else if (ActivityRecoverySupport.manual(session)) "Wpis ręczny" else "Health Connect"), style = MaterialTheme.typography.bodySmall)
                    if (ActivityRecoverySupport.manual(session)) {
                        Row {
                            TextButton(enabled = !busy, onClick = { sports.copyActivity(session.id, today.toString(), ::saved) }) { Text("Kopiuj na dziś") }
                            TextButton(enabled = !busy, onClick = { sports.removeActivity(session.id, ::saved) }) { Text("Usuń wpis ręczny") }
                        }
                    }
                } }
            }
        }
        }
    }
    if (add) AddActivitySheet(today, busy, message, onDismiss = { add = false }, onSave = { d, t, m, title, type ->
        sports.addActivity(d, t, m, title, type) { saved(); add = false }
    })
}

@Composable
fun SportsHistoryControls(busy: Boolean, onPermissions: () -> Unit, onRefresh: () -> Unit) {
    Row {
        TextButton(enabled = !busy, onClick = onPermissions, modifier = Modifier.testTag("sport_permissions")) { Text("Połącz / uprawnienia") }
        TextButton(enabled = !busy, onClick = onRefresh, modifier = Modifier.testTag("sport_refresh")) { Text(if (busy) "Odczyt…" else "Odśwież") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddActivitySheet(today: LocalDate, busy: Boolean, message: String?, onDismiss: () -> Unit,
    onSave: (String, String, Int, String, Int) -> Unit) {
    val initial = remember { java.time.ZonedDateTime.now().minusMinutes(30).withSecond(0).withNano(0) }
    var date by rememberSaveable { mutableStateOf(initial.toLocalDate().toString()) }
    var time by rememberSaveable { mutableStateOf(initial.toLocalTime().toString()) }
    var minutes by rememberSaveable { mutableStateOf("30") }
    var kind by rememberSaveable { mutableIntStateOf(0) }
    val types = listOf("Siłowy" to ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, "Cardio" to ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, "Wzmacnianie" to ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, "Mobilność i stretching" to ExerciseSessionRecord.EXERCISE_TYPE_YOGA, "Trening z filmem" to ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, "Spacer" to ExerciseSessionRecord.EXERCISE_TYPE_WALKING, "Bieganie" to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        "Rower" to ExerciseSessionRecord.EXERCISE_TYPE_BIKING, "Pływanie" to ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        "Joga" to ExerciseSessionRecord.EXERCISE_TYPE_YOGA, "Inna aktywność" to ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT)
    val valid = minutes.toIntOrNull()?.let { it in 1..1440 } == true && runCatching { LocalDate.parse(date) <= today && java.time.LocalTime.parse(time).second == 0 }.getOrDefault(false)
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        LazyColumn(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            item { Text("Dziennik Treningowy 3.0 • dodaj aktywność", style = MaterialTheme.typography.titleLarge); Text("Możesz zapisać kilka różnych aktywności tego samego dnia. Każda ma osobny czas i typ. Zapis lokalny, bez szacowania kalorii i bez wysyłania do Health Connect.") }
            item { PlanDateSelector(date, { if (!busy) date = it }, "manual_activity", showDays = false); Text("Data: $date") }
            item { androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(types.size) { index -> FilterChip(selected = kind == index, enabled = !busy, onClick = { kind = index }, label = { Text(types[index].first) }) }
            } }
            item { OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Początek HH:mm") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = minutes, onValueChange = { minutes = it }, label = { Text("Czas trwania w minutach") }, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
            message?.let { item { Text(it, color = GoldAccent) } }
            item { Button(enabled = valid && !busy, onClick = { onSave(date, time, minutes.toInt(), types[kind].first, types[kind].second) },
                modifier = Modifier.fillMaxWidth().testTag("save_manual_activity")) { Text(if (busy) "Zapisywanie…" else "Zapisz aktywność") } }
            item { TextButton(enabled = !busy, onClick = onDismiss) { Text("Anuluj") } }
        }
    }
}

@Composable
private fun FreshMangoSportHeader() {
    Surface(shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("fresh_mango_sport_hero")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🥭  Fresh Mango Sport 5.0",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text("Twój ruch, siła i regeneracja w jednym miejscu",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text("Dane z dziennika i Health Connect • bez podwójnego naliczania",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun FreshMangoSportRings(progress: Int, goal: Int, activeDays: Int, goalUnit: String) {
    Surface(modifier = Modifier.fillMaxWidth().testTag("fresh_mango_sport_rings"),
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            SportRing("Cel tygodnia", progress, goal.coerceAtLeast(1), LeafGreen, goalUnit)
            SportRing("Aktywne dni", activeDays, 7, OrangeInk, "dni")
        }
    }
}

@Composable
private fun SportRing(label: String, value: Int, target: Int, accent: Color, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            val fraction = (value.toFloat() / target).coerceIn(0f, 1f)
            val reduced = LocalReducedMangoMotion.current
            val animated by androidx.compose.animation.core.animateFloatAsState(
                targetValue = fraction,
                animationSpec = androidx.compose.animation.core.tween(if (reduced) 0 else 550),
                label = "sport ring")
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 9.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawArc(accent.copy(alpha = 0.20f), -90f, 360f, false, style = stroke)
                drawArc(accent, -90f, 360f * animated, false, style = stroke)
            }
            Text("$value", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(if (label == "Cel tygodnia") "Cel: $target $unit" else "W tym tygodniu",
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FreshMangoSportSection(emoji: String, title: String, description: String) {
    Surface(modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** Decorative activity icons only; source workout records and deduplication remain unchanged. */
private fun sportActivityIcon(title: String): String {
    val name = title.lowercase(java.util.Locale.ROOT)
    return when {
        "siłow" in name || "strength" in name || "weight" in name -> "🏋️"
        "bieżni" in name || "treadmill" in name -> "🏃"
        "spacer" in name || "walk" in name -> "🚶"
        "bieg" in name || "running" in name -> "🏃"
        "rower" in name || "cycling" in name || "biking" in name -> "🚴"
        "joga" in name || "yoga" in name -> "🧘"
        "pływ" in name || "swim" in name -> "🏊"
        "pilates" in name -> "🤸"
        "taniec" in name || "dance" in name -> "💃"
        else -> "💪"
    }
}

private fun createMangoDiploma(context: android.content.Context, title: String,
    completed: Int, total: Int, date: String): android.net.Uri {
    val bitmap = android.graphics.Bitmap.createBitmap(1200, 850, android.graphics.Bitmap.Config.ARGB_8888)
    try {
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(255, 249, 238))
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.rgb(88, 47, 111)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 12f
        canvas.drawRoundRect(36f, 36f, 1164f, 814f, 42f, 42f, paint)
        paint.style = android.graphics.Paint.Style.FILL
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = 74f
        canvas.drawText("FITMango", 600f, 190f, paint)
        paint.color = android.graphics.Color.rgb(184, 121, 31)
        paint.textSize = 55f
        canvas.drawText("DYPLOM UKOŃCZENIA", 600f, 300f, paint)
        paint.color = android.graphics.Color.rgb(88, 47, 111)
        paint.textSize = 45f
        canvas.drawText(title.take(42), 600f, 430f, paint)
        paint.textSize = 35f
        canvas.drawText("Ukończono $completed z $total treningów", 600f, 530f, paint)
        canvas.drawText("Data: $date", 600f, 610f, paint)
        paint.textSize = 28f
        canvas.drawText("Gratulacje! Każdy trening to Twój sukces.", 600f, 725f, paint)
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                "FITMango_dyplom_${System.currentTimeMillis()}.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FITMango")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Nie można utworzyć pliku")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            } ?: error("Nie można zapisać obrazu")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                resolver.update(uri, android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    } finally {
        bitmap.recycle()
    }
}
