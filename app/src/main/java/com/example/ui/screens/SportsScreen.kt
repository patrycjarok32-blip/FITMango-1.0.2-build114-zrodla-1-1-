package com.example.ui.screens

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
    var showTrainingProfile by rememberSaveable { mutableStateOf(false) }
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
                        Text("Centrum sportowe", style = MaterialTheme.typography.headlineSmall,
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
        item {
            FreshMangoSportHeader()
        }
        if (activitySection == "today") item {
            FreshMangoSportRings(progress.first, progress.second, visible.size)
        }
        item {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)) {
                listOf("today" to "Dzisiaj", "training" to "Trening",
                    "progress" to "Postępy", "recovery" to "Regeneracja").forEach { (key, label) ->
                    item {
                        FilterChip(
                            selected = activitySection == key,
                            onClick = { activitySection = key; showExtras = false; showReport = false },
                            label = { Text(label) },
                            modifier = Modifier.testTag("sport_${key}_tab")
                        )
                    }
                }
            }
        }
        if (activitySection == "training") item {
            OutlinedButton(onClick = { showTrainingProfile = !showTrainingProfile },
                modifier = Modifier.fillMaxWidth().testTag("sport_profile_tab")) {
                Text(if (showTrainingProfile) "Ukryj plan siłowy" else "Plan treningów i zapis serii")
            }
        }
        if (activitySection == "progress") item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showExtras = true; showReport = false },
                    modifier = Modifier.weight(1f).testTag("sport_personal_progress")) {
                    Text("Rekordy i siła")
                }
                OutlinedButton(onClick = { showReport = true; showExtras = false },
                    modifier = Modifier.weight(1f).testTag("sport_report_shortcut")) {
                    Text("Raport tygodnia")
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
        if (activitySection == "progress") item { PersonalProgressCards(viewModel, data, today) }
        if (activitySection == "progress") item { if (graphics.isNotEmpty()) SportActivityChart(graphics) }
        if (activitySection == "training" && showTrainingProfile) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Twój profil sportowy", style = MaterialTheme.typography.titleLarge,
                    color = LeafGreen, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Plan treningów, zapisane serie i rekordy. Dane pozostają w FITMango.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (activitySection == "training" && showTrainingProfile) item {
            SportProfileCards(data, today, busy,
                onPlan = { day, title, rest -> sports.plan(day, title, rest) { saved() } },
                onRemovePlan = { day -> sports.removePlan(day) { saved() } },
                onAddSet = { day, exercise, reps, kg -> sports.addSet(day, exercise, reps, kg) { saved() } })
        }
        if (activitySection == "today") item { ActivityTodayCard(data, today, current) }
        if (activitySection == "today" || activitySection == "training") item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SportsHistoryControls(busy,
                onPermissions = { permissionError = null; runCatching { launcher.launch(setOf(
                    HealthPermission.getReadPermission(ExerciseSessionRecord::class), HealthPermission.getReadPermission(DistanceRecord::class),
                    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class), HealthPermission.getReadPermission(StepsRecord::class),
                    HealthPermission.getReadPermission(SleepSessionRecord::class))) }.onFailure { permissionError = "Otwórz uprawnienia Health Connect w Profilu." } },
                onRefresh = { permissionError = null; sports.refresh { viewModel.usability.changed() } })
        } }
        if (activitySection == "today" || activitySection == "training") item { if (graphics.isNotEmpty()) SportActivityChart(graphics) }
        if (activitySection == "training") item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Twój dziennik treningów", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LeafGreen)
                MangoCard(Modifier.fillMaxWidth().testTag("health_connect_training_diary")) {
                Column(Modifier.padding(14.dp).then(if (reducedMangoMotion) Modifier else Modifier.animateContentSize(animationSpec = androidx.compose.animation.core.tween(220))), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Historia treningów • 30 dni",
                        style = MaterialTheme.typography.titleMedium, color = MangoMint)
                    Text("Zapisane aktywności: ${visible.size}",
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { showHistory = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Otwórz historię treningów")
                    }
                    if (duplicates.isNotEmpty()) Text("Wykryte powtórzone wpisy: ${duplicates.size}",
                        style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                }
                }
            }
        }

        if (activitySection == "today" || activitySection == "training") item { MangoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        if (activitySection == "training") item { Button(onClick = { add = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("sport_add_activity")) { Text("+ Dodaj wykonaną aktywność") } }
        if (activitySection == "recovery") item {
            Text("Regeneracja i samopoczucie", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = LeafGreen)
        }
        if (activitySection == "recovery") item { RecoveryCard(data, today, busy,
            onSave = { d, energy, fatigue -> sports.recovery(d, energy, fatigue, ::saved) },
            onRemove = { sports.removeRecovery(it, ::saved) }) }
        if (activitySection == "recovery") item { SportWellnessCards(data, today) }
        if (activitySection == "recovery") item { SportInsightsCards(data, today, reportNumbers) }
        if (activitySection == "training") item {
            MangoCard(Modifier.fillMaxWidth().testTag("sport_nutrition_connection")) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sport + dieta", style = MaterialTheme.typography.titleMedium, color = LeafGreen)
                    Text("Sprawdź odżywianie obok treningów. Korzystamy z istniejącego raportu diety — bez kopiowania posiłków i bez zwiększania limitu kalorii o trening.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { showSportNutrition = !showSportNutrition }) {
                        Text(if (showSportNutrition) "Ukryj raport odżywiania −" else "Pokaż raport odżywiania +")
                    }
                }
            }
        }
        if (showSportNutrition && activitySection == "training") item { NutritionWeekCard(viewModel) }

        if (activitySection == "training") item {
            TextButton(onClick = { showHistory = !showHistory }) { Text(if (showHistory) "Zwiń historię treningów −" else "Historia treningów (${visible.size}) +") }
            Text("Kalorie treningów mają charakter informacyjny. Nie zwiększają celu jedzenia i nie są ponownie dodawane do spalania ogółem.", style = MaterialTheme.typography.bodySmall)
            if (duplicates.isNotEmpty()) TextButton(onClick = { showDuplicates = !showDuplicates }) { Text("Pominięte powtórzenia: ${duplicates.size} • ${if (showDuplicates) "zwiń" else "pokaż"}") }
        }
        if (activitySection == "training" && showDuplicates) items(duplicates, key = { "duplicate|" + it.id }) { session ->
            Text("Pominięto: ${session.title} • ${sportTime(Instant.parse(session.start).toEpochMilli())} • ${session.source}", style = MaterialTheme.typography.bodySmall)
            if (ActivityRecoverySupport.manual(session)) TextButton(enabled = !busy, onClick = { sports.removeActivity(session.id, ::saved) }) { Text("Usuń ręczne powtórzenie") }
        }
        if (activitySection == "training" && showHistory) {
            if (visible.isEmpty()) item { Text("Brak zapisanych treningów z ostatnich 30 dni. Możesz dodać aktywność ręcznie lub odświeżyć Health Connect.") }
            items(visible, key = { it.id }) { session ->
                MangoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    val duration = java.time.Duration.between(Instant.parse(session.start), Instant.parse(session.end)).toMinutes()
                    Text(session.title, style = MaterialTheme.typography.titleMedium)
                    Text("${sportTime(Instant.parse(session.start).toEpochMilli())} • $duration min")
                    Text("Aktywne: ${session.activeKcal?.let { "${it.toInt()} kcal" } ?: "brak danych"} • Dystans: ${session.distanceKm?.let { String.format(java.util.Locale.getDefault(), "%.2f km", it) } ?: "brak danych"}")
                    Text("Źródło: ${session.source}", style = MaterialTheme.typography.bodySmall)
                    if (ActivityRecoverySupport.manual(session)) TextButton(enabled = !busy, onClick = { sports.removeActivity(session.id, ::saved) }) { Text("Usuń wpis ręczny") }
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
    val types = listOf("Spacer" to ExerciseSessionRecord.EXERCISE_TYPE_WALKING, "Bieganie" to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        "Rower" to ExerciseSessionRecord.EXERCISE_TYPE_BIKING, "Pływanie" to ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        "Joga" to ExerciseSessionRecord.EXERCISE_TYPE_YOGA, "Inna aktywność" to ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT)
    val valid = minutes.toIntOrNull()?.let { it in 1..1440 } == true && runCatching { LocalDate.parse(date) <= today && java.time.LocalTime.parse(time).second == 0 }.getOrDefault(false)
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        LazyColumn(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            item { Text("Dodaj aktywność", style = MaterialTheme.typography.titleLarge); Text("Zapis lokalny, bez szacowania kalorii i bez wysyłania do Health Connect.") }
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
            Text("🥭  Fresh Mango Sport 3.0",
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
private fun FreshMangoSportRings(progress: Int, goal: Int, sessions: Int) {
    Surface(modifier = Modifier.fillMaxWidth().testTag("fresh_mango_sport_rings"),
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            SportRing("Cel tygodnia", progress, goal.coerceAtLeast(1), LeafGreen)
            SportRing("Aktywności 30 dni", sessions, 12, OrangeInk)
        }
    }
}

@Composable
private fun SportRing(label: String, value: Int, target: Int, accent: Color) {
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
        Text(if (label == "Cel tygodnia") "Cel: $target" else "Ostatnie 30 dni",
            style = MaterialTheme.typography.labelSmall)
    }
}
