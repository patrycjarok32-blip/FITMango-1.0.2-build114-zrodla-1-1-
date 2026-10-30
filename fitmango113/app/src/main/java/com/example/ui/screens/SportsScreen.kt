package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.compose.foundation.background
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
    val message by sports.message.collectAsStateWithLifecycle()
    val aiBusy by viewModel.effectsAiBusy.collectAsStateWithLifecycle()
    val aiReport by viewModel.sportsWeeklyAi.collectAsStateWithLifecycle()
    val foodDays by viewModel.habits.days.collectAsStateWithLifecycle()
    val confirmations by viewModel.healthConnect.weeklyEnergy.confirmations.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    var showReport by rememberSaveable { mutableStateOf(false) }
    var showExtras by rememberSaveable { mutableStateOf(false) }
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
        verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Wróć") }
                Text("Aktywność i regeneracja", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            SuccessFeedback(feedback, "Zapisano")
            message?.let { Text(it, color = GoldAccent) }
            permissionError?.let { Text(it, color = ErrorInk) }
            SportsHistoryControls(busy,
                onPermissions = { permissionError = null; runCatching { launcher.launch(setOf(
                    HealthPermission.getReadPermission(ExerciseSessionRecord::class), HealthPermission.getReadPermission(DistanceRecord::class),
                    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class), HealthPermission.getReadPermission(StepsRecord::class),
                    HealthPermission.getReadPermission(SleepSessionRecord::class))) }.onFailure { permissionError = "Otwórz uprawnienia Health Connect w Profilu." } },
                onRefresh = { permissionError = null; sports.refresh { viewModel.usability.changed() } })
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showReport && !showExtras, onClick = { showReport = false; showExtras = false }, label = { Text("Aktywność") }, modifier = Modifier.testTag("sport_activity_tab"))
            FilterChip(selected = showReport && !showExtras, onClick = { showReport = true; showExtras = false }, label = { Text("Raport") }, modifier = Modifier.testTag("sport_report_tab"))
        } }
        item { TextButton(onClick = { showExtras = !showExtras; showReport = false }, modifier = Modifier.testTag("sport_personal_progress")) { Text(if (showExtras) "Wróć do aktywności ←" else "Rekordy i porównanie tygodni →") } }
        if (showExtras) {
            item { PersonalProgressCards(viewModel, data, today) }
        } else if (showReport) {
            item { NutritionWeekCard(viewModel) }
            item { reportNumbers?.let { numbers -> SportsWeeklyReport(numbers, profile, aiReport, aiBusy) { viewModel.runWeeklyAiAnalysis() } }
                ?: LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else {
        item { ActivityTodayCard(data, today, current) }
        item { if (graphics.isNotEmpty()) SportActivityChart(graphics) }
        item { MangoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        item { Button(onClick = { add = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("sport_add_activity")) { Text("+ Dodaj wykonaną aktywność") } }
        item { RecoveryCard(data, today, busy,
            onSave = { d, energy, fatigue -> sports.recovery(d, energy, fatigue, ::saved) },
            onRemove = { sports.removeRecovery(it, ::saved) }) }
        item { Button(onClick = { showReport = true }, modifier = Modifier.fillMaxWidth().testTag("sport_open_report")) { Text("Zobacz raport tygodniowy →") } }
        item {
            TextButton(onClick = { showHistory = !showHistory }) { Text(if (showHistory) "Zwiń historię treningów −" else "Historia treningów (${visible.size}) +") }
            Text("Kalorie treningów mają charakter informacyjny. Nie zwiększają celu jedzenia i nie są ponownie dodawane do spalania ogółem.", style = MaterialTheme.typography.bodySmall)
            if (duplicates.isNotEmpty()) TextButton(onClick = { showDuplicates = !showDuplicates }) { Text("Pominięte powtórzenia: ${duplicates.size} • ${if (showDuplicates) "zwiń" else "pokaż"}") }
        }
        if (showDuplicates) items(duplicates, key = { "duplicate|" + it.id }) { session ->
            Text("Pominięto: ${session.title} • ${sportTime(Instant.parse(session.start).toEpochMilli())} • ${session.source}", style = MaterialTheme.typography.bodySmall)
            if (ActivityRecoverySupport.manual(session)) TextButton(enabled = !busy, onClick = { sports.removeActivity(session.id, ::saved) }) { Text("Usuń ręczne powtórzenie") }
        }
        if (showHistory) {
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
