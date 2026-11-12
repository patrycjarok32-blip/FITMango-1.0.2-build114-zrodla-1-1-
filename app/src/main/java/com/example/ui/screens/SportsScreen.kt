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
        if (activitySection == "achievements") item {
            val finished = workouts.filter { it.ended > 0L }
            val finishedCount = finished.size
            val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val weeklyCount = finished.count { session ->
                runCatching { LocalDate.parse(session.date) }.getOrNull()
                    ?.let { !it.isBefore(weekStart) && !it.isAfter(today) } == true
            }
            val planMedals = videoChallenges.filter { challenge ->
                challenge.workouts.isNotEmpty() &&
                    challenge.workouts.all { it.completedAt > 0L }
            }.sortedByDescending { it.workouts.maxOf { w -> w.completedAt } }
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
                        androidx.compose.animation.AnimatedVisibility(visible = weeklyCount >= 3 && !reducedMangoMotion) {
                            Text("✨ 🏅 Brawo! Nowa odznaka tygodniowa!",
                                color = Color(0xFF9B54C8), fontWeight = FontWeight.Bold)
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
                            "Medal pojawi się po ukończeniu wszystkich treningów w zapisanym planie wideo.",
                            color = Color(0xFF625573))
                        planMedals.forEach { plan ->
                            val completedAt = plan.workouts.maxOf { it.completedAt }
                            val dateText = java.time.Instant.ofEpochMilli(completedAt)
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("🏅", style = MaterialTheme.typography.headlineMedium)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(plan.title, fontWeight = FontWeight.Bold, color = medalColor)
                                    Text("100% • ${plan.workouts.size} treningów • $dateText",
                                        color = Color(0xFF625573))
                                }
                                TextButton(onClick = {
                                    val certificate = "🏅 Dyplom FITMango\\nUkończony plan: ${plan.title}\\n" +
                                        "Wykonane treningi: ${plan.workouts.size}/${plan.workouts.size}\\nData: $dateText"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(android.content.Intent.EXTRA_TEXT, certificate)
                                    context.startActivity(android.content.Intent.createChooser(intent, "Udostępnij dyplom"))
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
            Text("Wybierz dział treningowy", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    WorkoutModeCard(workoutDate, data.plans.firstOrNull { it.date == workoutDate },
                        workouts, data, busy,
                        onStart = { plan -> sports.startWorkout(plan) { saved() } },
                        onEntry = { id, index, reps, kg, done ->
                            sports.updateWorkoutEntry(id, index, reps, kg, done) { saved() }
                        },
                        onFinish = { id, effort -> sports.finishWorkout(id, effort) { saved() } },
                        onDiscard = { id -> sports.discardWorkout(id) { saved() } },
                        onReplaceExercise = { id, old, new -> sports.replaceWorkoutExercise(id, old, new) { saved() } })
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
            val todayPlan = data.plans.firstOrNull { it.date == today.toString() }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🏋️ Twój trening na dziś", style = MaterialTheme.typography.titleLarge)
                    Text(when {
                        todayPlan == null -> "Nie masz zaplanowanej sesji na dziś."
                        todayPlan.rest -> "Dzisiaj regeneracja • odpoczynek jest częścią planu."
                        else -> todayPlan.title + " • " + todayPlan.exercises.size + " ćwiczeń"
                    })
                    Button(onClick = {
                        activitySection = "training"
                        trainingPage = if (todayPlan != null && !todayPlan.rest) "session" else "plan"
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (todayPlan != null && !todayPlan.rest) "▶ Przejdź do treningu" else "🗓 Otwórz plan")
                    }
                }
            }
        }
        if (activitySection == "today") item { StrengthWeeklyReview(data, today) }
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
