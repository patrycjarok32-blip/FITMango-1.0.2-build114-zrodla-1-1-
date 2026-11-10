package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import com.example.viewmodel.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Uses the existing sports_v1 storage; no Room migration or extra Health Connect permissions. */
@Composable
fun SportProfileCards(data: SportsData, today: LocalDate, busy: Boolean,
    onPlan: (String, String, Boolean) -> Unit, onRemovePlan: (String) -> Unit,
    onAddSet: (String, String, Int, Double) -> Unit,
    onSaveWeek: (List<SportPlan>) -> Unit = {},
    onRemoveSet: (String) -> Unit = {},
    onUpdateExercises: (String, List<PlannedExercise>) -> Unit = { _, _ -> }) {
    var planDay by rememberSaveable { mutableStateOf(today.toString()) }
    var planTitle by rememberSaveable { mutableStateOf("Push") }
    var rest by rememberSaveable { mutableStateOf(false) }
    var exercise by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableStateOf("10") }
    var weight by rememberSaveable { mutableStateOf("0") }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showSetEditor by rememberSaveable { mutableStateOf(false) }
    var planMode by rememberSaveable { mutableStateOf("auto") }
    var strengthGoal by rememberSaveable { mutableStateOf("muscle") }
    var trainingDays by rememberSaveable { mutableStateOf("3") }
    var experience by rememberSaveable { mutableStateOf("beginner") }
    var trainingPlace by rememberSaveable { mutableStateOf("gym") }
    var generatedTitles by remember { mutableStateOf<List<String>>(emptyList()) }
    val weekStart = SportsSupport.week(today)
    val plans = remember(data.plans, weekStart) {
        (0L..6L).map { weekStart.plusDays(it) to data.plans.firstOrNull { p -> p.date == weekStart.plusDays(it).toString() } }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_profile")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SportHeading(Icons.Outlined.FitnessCenter, "Mój profil sportowy")
            Text("🥭 Twój plan • małe kroki, duży progres", style = MaterialTheme.typography.titleSmall, color = LeafGreen)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SportProfileSummary("Treningi", "${data.plans.count { !it.rest }}", Modifier.weight(1f))
                SportProfileSummary("Serie", "${data.sets.size}", Modifier.weight(1f))
                SportProfileSummary("Odpoczynek", "${data.plans.count { it.rest }}", Modifier.weight(1f))
            }
            Text("✨ Inteligentny Plan Siłowy 3.0", style = MaterialTheme.typography.titleMedium, color = LeafGreen)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = planMode == "auto", onClick = { planMode = "auto" },
                    label = { Text("✨ Generator") })
                FilterChip(selected = planMode == "manual", onClick = { planMode = "manual" },
                    label = { Text("✏️ Własny plan") })
            }
            if (planMode == "auto") {
                Text("Wybierz cel, a następnie edytuj propozycję przed zapisaniem.",
                    style = MaterialTheme.typography.bodySmall)
                Text("Cel treningowy", style = MaterialTheme.typography.labelLarge)
                listOf("muscle" to "Budowa mięśni", "fat" to "Redukcja", "glutes" to "Nogi i pośladki",
                    "strength" to "Siła").chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { (key, label) ->
                            FilterChip(selected = strengthGoal == key, onClick = { strengthGoal = key },
                                label = { Text(label) })
                        }
                    }
                }
                Text("Dni treningowe", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (2..5).forEach { count ->
                        FilterChip(selected = trainingDays == count.toString(),
                            onClick = { trainingDays = count.toString() }, label = { Text("$count") })
                    }
                }
                Text("Doświadczenie", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("beginner" to "Start", "intermediate" to "Średni", "advanced" to "Zaaw.").forEach { (key, label) ->
                        FilterChip(selected = experience == key, onClick = { experience = key },
                            label = { Text(label) })
                    }
                }
                Text("Miejsce treningu", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("gym" to "Siłownia", "home" to "Dom", "mixed" to "Oba").forEach { (key, label) ->
                        FilterChip(selected = trainingPlace == key, onClick = { trainingPlace = key },
                            label = { Text(label) })
                    }
                }
                Button(onClick = {
                    generatedTitles = StrengthPlanSuggestions.generate(strengthGoal,
                        trainingDays.toInt(), experience, trainingPlace)
                }, modifier = Modifier.fillMaxWidth().testTag("sport_generate_plan")) {
                    Text("✨ Przygotuj propozycję tygodnia")
                }
                if (generatedTitles.isNotEmpty()) {
                    Text("Podgląd — możesz zmienić nazwy dni przed zapisaniem.",
                        style = MaterialTheme.typography.bodySmall)
                    generatedTitles.forEachIndexed { index, title ->
                        val date = weekStart.plusDays(index.toLong())
                        OutlinedTextField(value = title,
                            onValueChange = { value ->
                                generatedTitles = generatedTitles.toMutableList().also { it[index] = value.take(120) }
                            },
                            label = { Text(date.format(DateTimeFormatter.ofPattern("EEE dd.MM", Locale("pl")))) },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Button(onClick = {
                        onSaveWeek(generatedTitles.mapIndexed { index, title ->
                            SportPlan(weekStart.plusDays(index.toLong()).toString(),
                                if (title.isBlank()) "Odpoczynek" else title, title.isBlank() || title == "Odpoczynek",
                                if (title.isBlank() || title == "Odpoczynek") emptyList() else StrengthSessionGenerator.exercises(title, experience, trainingPlace))
                        })
                    }, enabled = !busy && generatedTitles.any { it.isNotBlank() },
                        modifier = Modifier.fillMaxWidth().testTag("sport_save_generated_plan")) {
                        Text("Zapisz cały tydzień")
                    }
                    Text("Zapis zastępuje wyłącznie plan na te dni. Historia serii i Health Connect pozostają bez zmian.",
                        style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text("Ręcznie zaplanuj wybrany dzień w edytorze poniżej.",
                    style = MaterialTheme.typography.bodySmall)
                LaunchedEffect(planMode) { showEditor = true }
            }
            Text("Plan tygodnia • zapis lokalny", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            plans.forEach { (date, plan) ->
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("sport_plan_day_${date}"),
                    shape = RoundedCornerShape(18.dp),
                    color = if (date == today) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, if (date == today) LeafGreen.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEE dd.MM", Locale("pl"))),
                            modifier = Modifier.width(94.dp), style = MaterialTheme.typography.bodySmall)
                        Column(Modifier.weight(1f)) {
                            Text(plan?.title ?: "Bez planu",
                                fontWeight = if (date == today) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium)
                            if (plan?.rest == true) Text("Regeneracja", style = MaterialTheme.typography.labelSmall)
                        }
                        if (plan != null) TextButton(onClick = { onRemovePlan(date.toString()) },
                            enabled = !busy) {
                            Icon(Icons.Outlined.Close, contentDescription = "Usuń plan z ${date}")
                        }
                    }
                }
            }
            TextButton(onClick = { showEditor = !showEditor }) { Text(if (showEditor) "Zamknij edycję" else "Zaplanuj trening +") }
            if (showEditor) {
                OutlinedTextField(planDay, { planDay = it }, label = { Text("Data RRRR-MM-DD") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(planTitle, { planTitle = it.take(120) }, label = { Text("Nazwa treningu") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !rest)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rest, { rest = it }); Text("Dzień odpoczynku")
                }
                val valid = runCatching { LocalDate.parse(planDay) }.getOrNull() != null &&
                    (rest || planTitle.isNotBlank())
                Button(onClick = { onPlan(planDay, planTitle, rest) },
                    enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) { Text("Zapisz plan") }
            }
            HorizontalDivider()
            var editorDate by rememberSaveable { mutableStateOf(today.toString()) }
            Text("🏋️ Trening i serie na wybrany dzień", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(7) { day ->
                    val date = weekStart.plusDays(day.toLong())
                    FilterChip(selected = editorDate == date.toString(),
                        onClick = { editorDate = date.toString() },
                        label = { Text(date.format(DateTimeFormatter.ofPattern("EE", Locale("pl")))) })
                }
            }
            val selectedPlan = data.plans.firstOrNull { it.date == editorDate }
            if (selectedPlan != null && !selectedPlan.rest) {
                Text("📋 Sesja: ${selectedPlan.title}", style = MaterialTheme.typography.titleMedium)
                if (selectedPlan.exercises.isEmpty()) {
                    Text("Ta sesja nie ma jeszcze dobranych ćwiczeń.")
                    TextButton(onClick = {
                        onUpdateExercises(editorDate, StrengthSessionGenerator.exercises(selectedPlan.title, "beginner", "gym"))
                    }, enabled = !busy) { Text("✨ Dobierz ćwiczenia do tej sesji") }
                }
                selectedPlan.exercises.forEachIndexed { index, planned ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${index + 1}. ${planned.name} • ${planned.sets} × ${planned.reps} • przerwa ${planned.restSeconds}s")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                onUpdateExercises(editorDate, selectedPlan.exercises.toMutableList().also {
                                    it[index] = planned.copy(sets = (planned.sets - 1).coerceAtLeast(1))
                                })
                            }, enabled = !busy && planned.sets > 1) { Text("− seria") }
                            TextButton(onClick = {
                                onUpdateExercises(editorDate, selectedPlan.exercises.toMutableList().also {
                                    it[index] = planned.copy(sets = (planned.sets + 1).coerceAtMost(12))
                                })
                            }, enabled = !busy && planned.sets < 12) { Text("+ seria") }
                            TextButton(onClick = {
                                onUpdateExercises(editorDate, selectedPlan.exercises.filterIndexed { i, _ -> i != index })
                            }, enabled = !busy) { Text("Usuń") }
                        }
                    }
                }
                var addExercise by rememberSaveable { mutableStateOf("Hip thrust") }
                OutlinedTextField(addExercise, { addExercise = it.take(120) },
                    label = { Text("Dodaj ćwiczenie do sesji") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    onUpdateExercises(editorDate, selectedPlan.exercises + PlannedExercise(addExercise.trim(), 3, 10))
                }, enabled = !busy && addExercise.isNotBlank() && selectedPlan.exercises.size < 30) {
                    Text("+ Dodaj do planu")
                }
            }
            StrengthDayEditor(LocalDate.parse(editorDate), data, busy, onAddSet, onRemoveSet)
            HorizontalDivider()
            TextButton(onClick = { showSetEditor = !showSetEditor }) {
                Text(if (showSetEditor) "Zwiń zapis serii −" else "Zapisz serię siłową +")
            }
            if (showSetEditor) {
            OutlinedTextField(exercise, { exercise = it.take(120) }, label = { Text("Ćwiczenie") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(reps, { reps = it }, label = { Text("Powtórzenia") },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(weight, { weight = it }, label = { Text("Ciężar kg") },
                    singleLine = true, modifier = Modifier.weight(1f))
            }
            val count = reps.toIntOrNull()
            val kg = weight.replace(',', '.').toDoubleOrNull()
            Button(onClick = { if (count != null && kg != null) onAddSet(today.toString(), exercise.trim(), count, kg) },
                enabled = !busy && exercise.isNotBlank() && count != null && count in 1..1000 &&
                    kg != null && kg.isFinite() && kg in 0.0..2000.0,
                modifier = Modifier.fillMaxWidth().testTag("sport_profile_add_set")) { Text("Zapisz serię") }
            }
        }
    }
    val recent = remember(data.sets, today) {
        data.sets.filter { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d ->
            !d.isAfter(today) && !d.isBefore(today.minusDays(83)) } == true }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_strength_progress")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SportHeading(Icons.Outlined.TrendingUp, "Siła i rekordy osobiste")
            if (recent.none { it.kg > 0.0 }) Text("Zapisz serię z ciężarem, aby zobaczyć postępy z 12 tygodni.")
            else {
                val exercises = recent.filter { it.kg > 0.0 }.groupBy { it.exercise.trim().lowercase(Locale.ROOT) }
                exercises.entries.sortedByDescending { it.value.size }.take(5).forEach { (_, sets) ->
                    val best = sets.maxByOrNull { it.kg }!!
                    val volume = sets.sumOf { it.kg * it.reps }
                    Text("${best.exercise} • rekord ${formatSport(best.kg)} kg • objętość ${formatSport(volume)} kg",
                        style = MaterialTheme.typography.bodySmall)
                    val points = sets.sortedWith(compareBy<SportSet> { it.date }.thenBy { it.time }).takeLast(16)
                    if (points.size >= 2) {
                        val min = points.minOf { it.kg }
                        val max = points.maxOf { it.kg }
                        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                            val range = (max - min).coerceAtLeast(1.0)
                            val coords = points.mapIndexed { index, set ->
                                Offset(size.width * index / (points.size - 1),
                                    size.height * (1f - ((set.kg - min) / range).toFloat()))
                            }
                            coords.zipWithNext().forEach { (a, b) ->
                                drawLine(LeafGreen, a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                            }
                            coords.forEach { drawCircle(LeafGreen, radius = 3.dp.toPx(), center = it) }
                        }
                    }
                }
                Text("Rekord oznacza największy zapisany ciężar, a objętość sumę ciężar × powtórzenia. Nie są to szacunki maksymalnej siły.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_recovery_guidance")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SportHeading(Icons.Outlined.AutoAwesome, "Wskazówki treningowe")
            val check = data.recovery.firstOrNull { it.date == today.toString() }
            val sleep = data.daily.firstOrNull { it.date == today.toString() }?.sleepMinutes
            val tip = when {
                check == null && sleep == null -> "Dodaj ocenę energii i zmęczenia lub zsynchronizuj sen, aby otrzymać wskazówkę."
                check != null && (check.fatigue >= 4 || check.energy <= 2) ->
                    "Zgłaszasz duże zmęczenie lub niską energię. Rozważ lżejszą sesję albo odpoczynek."
                sleep != null && sleep < 360 -> "Odczyt snu jest krótki. Rozważ spokojniejszy trening i zadbaj o regenerację."
                else -> "Jeśli czujesz się dobrze, realizuj plan i obserwuj technikę oraz samopoczucie."
            }
            Text(tip)
            Text("Wskazówki lokalne oparte na zapisanych danych, nie diagnoza medyczna ani zewnętrzny model AI.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Sen: ${sleep?.let { "${it / 60} h ${it % 60} min" } ?: "brak odczytu"} • energia: ${check?.energy ?: "–"}/5 • zmęczenie: ${check?.fatigue ?: "–"}/5",
                style = MaterialTheme.typography.bodySmall)
            Text("Kalorie i białko sprawdzisz w zakładce Dieta; aktywność nie podnosi automatycznie limitu kalorii.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatSport(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

@Composable
private fun SportProfileSummary(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
        }
    }
}

/** Local, deterministic suggestions; no external AI or additional permissions. */
internal object StrengthPlanSuggestions {
    fun generate(goal: String, days: Int, level: String, place: String): List<String> {
        require(days in 2..5)
        val split = when {
            goal == "glutes" -> listOf("Nogi i pośladki", "Góra ciała", "Pośladki i tył uda",
                "Góra ciała + core", "Nogi i pośladki")
            goal == "strength" -> listOf("Siła • dół", "Siła • góra", "Full Body • siła",
                "Siła • dół", "Siła • góra")
            days <= 3 -> listOf("Full Body A", "Full Body B", "Full Body C")
            else -> listOf("Góra ciała A", "Dół ciała A", "Góra ciała B", "Dół ciała B", "Full Body")
        }
        val result = MutableList(7) { "Odpoczynek" }
        val slots = when (days) {
            2 -> listOf(0, 3)
            3 -> listOf(0, 2, 4)
            4 -> listOf(0, 1, 3, 4)
            else -> listOf(0, 1, 2, 4, 5)
        }
        slots.forEachIndexed { index, day ->
            result[day] = split[index % split.size]
        }
        return result
    }
}

internal object StrengthSessionGenerator {
    fun exercises(title: String, level: String, place: String): List<PlannedExercise> {
        val name = title.lowercase(Locale.ROOT)
        val names = when {
            "odpoczynek" in name -> emptyList()
            "poślad" in name || "nogi" in name || "dół" in name -> listOf(
                "Przysiad", "Hip thrust", "Martwy ciąg rumuński", "Wykroki", "Glute bridge")
            "plecy" in name || "pull" in name -> listOf(
                "Wiosłowanie hantlami", "Ściąganie drążka", "Uginanie na biceps", "Dead bug")
            "push" in name || "klatka" in name -> listOf(
                "Wyciskanie na ławce", "Wyciskanie hantli", "Unoszenie bokiem", "Prostowanie na triceps")
            "góra" in name -> listOf(
                "Wiosłowanie hantlami", "Wyciskanie na ławce", "Wyciskanie hantli", "Uginanie na biceps")
            else -> listOf("Przysiad", "Wiosłowanie hantlami", "Pompki", "Hip thrust", "Plank")
        }
        val home = mapOf("Ściąganie drążka" to "Wiosłowanie hantlami",
            "Wyciskanie na ławce" to "Pompki", "Prostowanie na triceps" to "Pompki")
        return names.map { exercise ->
            val selected = if (place == "home") home[exercise] ?: exercise else exercise
            PlannedExercise(selected, if (level == "beginner") 2 else if (level == "advanced") 4 else 3,
                if (level == "advanced") 8 else 10, if (level == "advanced") 120 else 90)
        }.distinctBy { it.name }
    }
}
