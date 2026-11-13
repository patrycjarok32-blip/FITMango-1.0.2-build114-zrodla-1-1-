package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.viewmodel.*
import kotlinx.coroutines.delay
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

@Composable
fun WorkoutModeCard(
    date: String, plan: SportPlan?, workouts: List<WorkoutSession>, sports: SportsData, busy: Boolean,
    onStart: (SportPlan) -> Unit,
    onEntry: (String, Int, Int, Double, Boolean) -> Unit,
    onFinish: (String, Int) -> Unit,
    onPause: (String, Boolean) -> Unit,
    onDiscard: (String) -> Unit,
    onReplaceExercise: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val active = workouts.firstOrNull { it.ended == 0L }
    var recoveryNote by rememberSaveable { mutableStateOf(false) }
    val last = workouts.lastOrNull { it.ended > 0L }
    var exerciseIndex by rememberSaveable(active?.id) { mutableIntStateOf(0) }
    var showReplacement by rememberSaveable { mutableStateOf(false) }
    var equipmentFilter by rememberSaveable { mutableStateOf("Wszystko") }
    var showTechnique by rememberSaveable { mutableStateOf(false) }
    var showExerciseList by rememberSaveable { mutableStateOf(false) }
    var effort by rememberSaveable { mutableIntStateOf(6) }
    var rest by rememberSaveable { mutableIntStateOf(0) }
    var restRunning by rememberSaveable { mutableStateOf(false) }
    var showFinishConfirmation by rememberSaveable { mutableStateOf(false) }
    var showExitChoice by rememberSaveable { mutableStateOf(false) }
    var showVideo by rememberSaveable { mutableStateOf(false) }
    var clockNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active?.id, active?.pausedAt) {
        while (active != null && active.ended == 0L) {
            clockNow = System.currentTimeMillis()
            delay(1000)
        }
    }
    LaunchedEffect(restRunning, rest) {
        if (restRunning && rest > 0) { delay(1000); rest-- }
        else if (rest == 0) restRunning = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (active != null) {
            Text("💾 Sesja zapisana automatycznie. Po ponownym uruchomieniu aplikacji możesz ją wznowić.",
                style = MaterialTheme.typography.bodySmall)
        }
        var showWeeklyReport by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { showWeeklyReport = !showWeeklyReport }) {
            Text(if (showWeeklyReport) "Ukryj raport tygodniowy" else "📊 Raport tygodniowy Premium")
        }
        if (showWeeklyReport) {
            val comparison = remember(workouts) {
                MangoWorkoutWeeklyReport.compare(workouts, java.time.LocalDate.now())
            }
            val now = comparison.current
            val before = comparison.previous
            Text("Ostatnie 7 dni vs poprzednie 7 dni", style = MaterialTheme.typography.titleSmall)
            listOf(
                Triple("Treningi", now.sessions.toDouble(), before.sessions.toDouble()),
                Triple("Serie", now.sets.toDouble(), before.sets.toDouble()),
                Triple("Objętość (kg)", now.volumeKg, before.volumeKg),
                Triple("Rekordy", now.records.toDouble(), before.records.toDouble())
            ).forEach { (label, current, previous) ->
                Text("$label: ${"%.0f".format(current)} (poprzednio ${"%.0f".format(previous)})",
                    style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = {
                    if (current <= 0.0) 0f
                    else (current / maxOf(current, previous, 1.0)).toFloat().coerceIn(0f, 1f)
                }, modifier = Modifier.fillMaxWidth())
            }
            Text("Średni wysiłek: " +
                (now.averageEffort?.let { "%.1f/10".format(it) } ?: "brak danych") +
                " • poprzednio: " +
                (before.averageEffort?.let { "%.1f/10".format(it) } ?: "brak danych"),
                style = MaterialTheme.typography.bodySmall)
            Text("Ocena regeneracji zależy od dostępnych wpisów snu i samopoczucia.",
                style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = {
            runCatching {
                val json = WorkoutSessionStore.exportJson(context)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_SUBJECT, "FITMango — historia treningów")
                    putExtra(Intent.EXTRA_TEXT, json)
                }
                context.startActivity(Intent.createChooser(send, "Eksportuj historię treningów"))
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("💾 Eksportuj historię treningów (JSON)") }
        Text("Eksport obejmuje wyłącznie historię treningów. Nie zawiera kluczy API ani danych konta.",
            style = MaterialTheme.typography.bodySmall)
        var confirmRestore by rememberSaveable { mutableStateOf(false) }
        var restoreMessage by rememberSaveable { mutableStateOf<String?>(null) }
        val restoreAvailable = remember(workouts) {
            runCatching { WorkoutSessionStore.restorePoint(context) != null }.getOrDefault(false)
        }
        if (restoreAvailable) {
            OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) {
                Text("↩ Przywróć poprzednią historię treningów")
            }
        }
        restoreMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (confirmRestore) {
            AlertDialog(
                onDismissRequest = { confirmRestore = false },
                title = { Text("Przywrócić historię?") },
                text = { Text("Zastąpi to bieżącą historię poprzednim punktem przywracania. " +
                    "Obecna historia zostanie zachowana jako punkt cofnięcia. " +
                    "Po przywróceniu otwórz ponownie ekran treningu.") },
                confirmButton = {
                    Button(onClick = {
                        restoreMessage = runCatching {
                            val count = WorkoutSessionStore.restorePrevious(context)
                            "Przywrócono $count sesji. Odśwież ekran treningu."
                        }.getOrElse { "Nie udało się przywrócić historii: ${it.message ?: "błąd danych"}" }
                        confirmRestore = false
                    }) { Text("Przywróć") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRestore = false }) { Text("Anuluj") }
                }
            )
        }
        val weekly = remember(workouts) {
            MangoWorkoutInsights.weeklyReport(java.time.LocalDate.now(), workouts)
        }
        var showWeekly by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { showWeekly = !showWeekly }) {
            Text(if (showWeekly) "Ukryj raport tygodniowy" else "📊 Raport tygodniowy Premium")
        }
        if (showWeekly) {
            Card {
                Column(Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ten tydzień / poprzedni", style = MaterialTheme.typography.titleMedium)
                    Text("Treningi: ${weekly.sessions} / ${weekly.previousSessions} • serie: ${weekly.sets}")
                    Text("Czas aktywny: ${weekly.minutes} / ${weekly.previousMinutes} min")
                    Text("Rekordy ciężaru: ${weekly.records}")
                    weekly.averageEffort?.let { Text("Średni wysiłek: ${"%.1f".format(it)}/10") }
                    Text("Objętość: ${"%.0f".format(weekly.volumeKg)} / ${"%.0f".format(weekly.previousVolumeKg)} kg")
                    val maxVolume = maxOf(weekly.volumeKg, weekly.previousVolumeKg, 1.0)
                    Text("Bieżący tydzień", style = MaterialTheme.typography.labelSmall)
                    LinearProgressIndicator(progress = {
                        (weekly.volumeKg / maxVolume).toFloat().coerceIn(0f, 1f)
                    }, modifier = Modifier.fillMaxWidth())
                    Text("Poprzedni tydzień", style = MaterialTheme.typography.labelSmall)
                    LinearProgressIndicator(progress = {
                        (weekly.previousVolumeKg / maxVolume).toFloat().coerceIn(0f, 1f)
                    }, modifier = Modifier.fillMaxWidth())
                    Text("Zmiana objętości: ${"%+.0f".format(weekly.volumeKg - weekly.previousVolumeKg)} kg")
                }
            }
        }
        val lastCompleted = workouts.filter { it.ended > 0L }.maxByOrNull { it.ended }
        if (lastCompleted != null) {
            TextButton(onClick = { recoveryNote = !recoveryNote }) {
                Text("😴 Mango Trener regeneracji")
            }
            if (recoveryNote) {
                val elapsedHours = ((System.currentTimeMillis() - lastCompleted.ended)
                    .coerceAtLeast(0L) / 3600000L)
                val today = java.time.LocalDate.now().toString()
                val sleep = sports.daily.filter { it.date <= today }.maxByOrNull { it.date }
                    ?.takeIf { it.date >= java.time.LocalDate.now().minusDays(1).toString() }
                    ?.sleepMinutes
                val check = sports.recovery.filter { it.date <= today }.maxByOrNull { it.date }
                    ?.takeIf { it.date >= java.time.LocalDate.now().minusDays(2).toString() }
                if (sleep != null) Text("💤 Ostatni sen: ${sleep / 60} h ${sleep % 60} min")
                if (check != null) Text("Energia: ${check.energy}/5 • zmęczenie: ${check.fatigue}/5")
                val lowRecovery = (sleep != null && sleep < 360) ||
                    (check != null && (check.fatigue >= 4 || check.energy <= 2))
                Text(when {
                    lowRecovery -> "Regeneracja może być niewystarczająca. Rozważ lżejszy trening."
                    lastCompleted.effort >= 8 && elapsedHours < 24 ->
                        "Ostatni trening był intensywny. Rozważ odpoczynek lub mniejsze obciążenie."
                    elapsedHours < 12 ->
                        "Od ostatniej sesji minęło mniej niż 12 godzin. Oceń zmęczenie."
                    else -> "Dostosuj obciążenie do samopoczucia i techniki."
                }, style = MaterialTheme.typography.bodySmall)
                if (sleep == null && check == null)
                    Text("Brak aktualnych danych snu lub samopoczucia. Sprawdź synchronizację Health Connect.",
                        style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(
            onClick = {
                val spotify = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/"))
                    .setPackage("com.spotify.music")
                val opened = runCatching { context.startActivity(spotify) }.isSuccess
                if (!opened) runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://open.spotify.com/")))
                }
            }, modifier = Modifier.fillMaxWidth()
        ) { Text("🎵 Otwórz Spotify — muzyka podczas treningu") }
        Text("Uruchom muzykę w Spotify i wróć do FITMango. Licznik i serie działają bez odtwarzania dźwięku. " +
            "Film YouTube może przejąć dźwięk — zamknij film, by kontynuować Spotify.",
            style = MaterialTheme.typography.bodySmall)
        val finishedSets = active?.entries?.count { it.done } ?: 0
        MangoGuide(
            message = when {
                active == null -> "Gotowa na trening?"
                active.entries.isNotEmpty() && finishedSets == active.entries.size ->
                    "Brawo! Wszystkie serie ukończone. 🏆"
                finishedSets > 0 -> "Świetnie! ${finishedSets} serii za Tobą. 🥭"
                else -> "Trening trwa — skup się na następnej serii."
            },
            event = finishedSets, size = 32.dp)
        Text(if (active == null) "🏋️ Wybierz trening" else "🏋️ Trening w toku", style = MaterialTheme.typography.titleLarge)
        if (active == null) {
            if (plan != null && !plan.rest && plan.exercises.isNotEmpty()) {
                Text("${plan.title} • ${plan.exercises.size} ćwiczeń • ${plan.exercises.sumOf { it.sets }} serii")
                Button(onClick = { onStart(plan) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("▶ Rozpocznij trening")
                }
            } else Text("Wybierz dzień z zaplanowanymi ćwiczeniami, aby rozpocząć trening.")
            if (last != null) {
                val done = last.entries.filter { it.done }
                val volume = done.sumOf { it.kg * it.reps }
                Text("🏆 Ostatni trening: ${last.title}", style = MaterialTheme.typography.titleMedium)
                Text("Wykonano ${done.size}/${last.entries.size} serii • objętość ${"%.1f".format(volume)} kg")
                val elapsed = WorkoutSessionStore.activeDurationMillis(last)
                Text("Czas aktywny: ${elapsed / 60000} min • wysiłek ${last.effort}/10")
                val report = remember(last, workouts) { MangoWorkoutInsights.summary(last, workouts) }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🏆 Podsumowanie Premium", style = MaterialTheme.typography.titleMedium)
                        Text("Serie: ${report.completedSets}/${report.totalSets} • objętość: ${"%.1f".format(report.volumeKg)} kg")
                        Text("Rekordy ciężaru: ${report.personalRecords} • czas aktywny: ${report.activeMinutes} min")
                        report.previousVolumeKg?.let { old ->
                            Text("Poprzednia sesja: ${"%.1f".format(old)} kg • zmiana: ${"%+.1f".format(report.volumeKg - old)} kg")
                        }
                    }
                }

                if (elapsed > 4L * 60L * 60L * 1000L && last.pausedMillis == 0L)
                    Text("Starszy zapis: czas mógł obejmować przerwy i pozostawienie otwartej aplikacji.",
                        style = MaterialTheme.typography.bodySmall)
            }
        } else {
            if (active.pausedAt > 0L) Text("⏸ Trening wstrzymany — wznów licznik przed kolejną serią.",
                style = MaterialTheme.typography.bodyMedium)
            val exercises = active.entries.map { it.exercise }.distinct()
            val pendingExercises = exercises.filter { name -> active.entries.any { it.exercise == name && !it.done } }
            val allCompleted = pendingExercises.isEmpty()
            val index = exerciseIndex.coerceIn(0, (exercises.size - 1).coerceAtLeast(0))
            val selected = if (allCompleted) null else exercises.getOrNull(index)?.takeIf { it in pendingExercises } ?: pendingExercises.firstOrNull()
            val nextPending = active.entries.indexOfFirst { !it.done }
            LaunchedEffect(active.id, nextPending) {
                if (nextPending >= 0 && !showExerciseList) {
                    val pendingExercise = active.entries[nextPending].exercise
                    val pendingIndex = exercises.indexOf(pendingExercise)
                    if (pendingIndex >= 0 && exercises.getOrNull(exerciseIndex) != pendingExercise &&
                        active.entries.filter { it.exercise == exercises.getOrNull(exerciseIndex) }.all { it.done }) {
                        exerciseIndex = pendingIndex
                    }
                }
            }
            val elapsedMs = WorkoutSessionStore.activeDurationMillis(active, clockNow)
            Text("⏱️ Czas aktywny: ${elapsedMs / 60000}:${((elapsedMs / 1000) % 60).toString().padStart(2, '0')}" +
                if (active.pausedAt > 0L) " • PAUZA" else "",
                style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onPause(active.id, active.pausedAt == 0L) },
                enabled = !busy) {
                Text(if (active.pausedAt > 0L) "▶ Wznów licznik" else "⏸ Wstrzymaj licznik")
            }
            val done = active.entries.count { it.done }
            Text(if (allCompleted) "🎉 Wszystkie ćwiczenia ukończone" else "🟢 ${active.title} • Pozostało ${pendingExercises.size} z ${exercises.size} ćwiczeń", style = MaterialTheme.typography.titleMedium)
            val completion = if (active.entries.isEmpty()) 0f else done.toFloat() / active.entries.size
            val animatedCompletion by animateFloatAsState(targetValue = completion, label = "workoutProgress")
            Text("Wykonano ${done}/${active.entries.size} serii • ${(completion * 100).toInt()}%")
            AnimatedVisibility(visible = allCompleted) {
                Text("🎉 Brawo! Wszystkie serie ukończone — zapisz trening, aby utrwalić wynik.",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { animatedCompletion },
                modifier = Modifier.fillMaxWidth())
            if (!allCompleted) Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TERAZ ĆWICZYSZ", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(selected.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val videoUrl = ImportedExerciseVideos.forExercise(selected.orEmpty())
                    if (videoUrl != null) {
                        OutlinedButton(onClick = { showVideo = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("▶ Film instruktażowy w FITMango")
                        }
                        if (showVideo) AlertDialog(
                            onDismissRequest = { showVideo = false },
                            title = { Text("Technika: " + selected.orEmpty()) },
                            text = {
                                EmbeddedYoutubeWorkout(videoUrl, Modifier.fillMaxWidth())
                            },
                            confirmButton = {
                                TextButton(onClick = { showVideo = false }) { Text("Wróć do serii") }
                            }
                        )
                    } else {
                        Text("Film instruktażowy nie został jeszcze przypisany do tego ćwiczenia.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Ćwiczenie ${index + 1} z ${exercises.size}", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { showTechnique = true }) { Text("ⓘ Technika i wskazówki") }
                }
            }
            if (!allCompleted && showTechnique) {
                AlertDialog(onDismissRequest = { showTechnique = false },
                    title = { Text(selected.orEmpty()) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🎯 Jak wykonać ćwiczenie?", style = MaterialTheme.typography.titleMedium)
                            Text(StrengthExerciseLibrary.exercises.firstOrNull {
                                it.name == selected
                            }?.tip ?: "Wykonuj ruch powoli i kontroluj technikę.")
                            Text("Etapy ruchu", style = MaterialTheme.typography.titleSmall)
                            ExerciseMovementGuide.steps(selected.orEmpty()).forEach { step ->
                                Text(step.title, style = MaterialTheme.typography.labelLarge)
                                Text(step.instruction, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Zdjęcie dodasz z galerii na karcie ćwiczenia.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = { TextButton(onClick = { showTechnique = false }) { Text("Zamknij") } })
            }
            if (selected != null) {
                val exerciseHistory = remember(selected, workouts) {
                    MangoWorkoutInsights.exerciseHistory(selected, workouts).takeLast(6)
                }
                if (exerciseHistory.isNotEmpty()) {
                    var showHistory by rememberSaveable(selected) { mutableStateOf(false) }
                    TextButton(onClick = { showHistory = !showHistory }) {
                        Text(if (showHistory) "Ukryj historię" else "📈 Historia ćwiczenia")
                    }
                    if (showHistory) {
                        val peak = exerciseHistory.maxOfOrNull { it.maxKg }?.coerceAtLeast(1.0) ?: 1.0
                        Text("Ciężar maksymalny • ostatnie sesje",
                            style = MaterialTheme.typography.labelMedium)
                        exerciseHistory.forEach { point ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(point.date.takeLast(5), modifier = Modifier.width(46.dp),
                                    style = MaterialTheme.typography.labelSmall)
                                LinearProgressIndicator(
                                    progress = { (point.maxKg / peak).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier.weight(1f))
                                Text("${point.maxKg} kg",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        val peakReps = exerciseHistory.maxOfOrNull { it.maxReps }?.coerceAtLeast(1) ?: 1
                        val peakVolume = exerciseHistory.maxOfOrNull { it.volumeKg }?.coerceAtLeast(1.0) ?: 1.0
                        Text("Powtórzenia • ostatnie sesje",
                            style = MaterialTheme.typography.labelMedium)
                        exerciseHistory.forEach { point ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(point.date.takeLast(5), modifier = Modifier.width(46.dp),
                                    style = MaterialTheme.typography.labelSmall)
                                LinearProgressIndicator(progress = {
                                    (point.maxReps.toFloat() / peakReps).coerceIn(0f, 1f)
                                }, modifier = Modifier.weight(1f))
                                Text("${point.maxReps}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text("Objętość • ostatnie sesje",
                            style = MaterialTheme.typography.labelMedium)
                        exerciseHistory.forEach { point ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(point.date.takeLast(5), modifier = Modifier.width(46.dp),
                                    style = MaterialTheme.typography.labelSmall)
                                LinearProgressIndicator(progress = {
                                    (point.volumeKg / peakVolume).toFloat().coerceIn(0f, 1f)
                                }, modifier = Modifier.weight(1f))
                                Text("${"%.0f".format(point.volumeKg)} kg",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        exerciseHistory.forEach { point ->
                            Text("${point.date}: ${point.maxKg} kg × maks. ${point.maxReps} • ${"%.1f".format(point.volumeKg)} kg objętości",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            val priorSets = selected?.let { SportsSupport.previous(sports, it, active.date) }.orEmpty()
            val previous = priorSets.lastOrNull()?.kg
            val completedForExercise = active.entries.count { it.exercise == selected && it.done }
            if (!allCompleted) OutlinedButton(onClick = { showReplacement = true },
                enabled = !busy && completedForExercise == 0,
                modifier = Modifier.fillMaxWidth()) { Text("⇄ Zamień ćwiczenie") }
            if (!allCompleted && showReplacement) {
                val choices = MangoExerciseAlternatives.candidates(
                    selected.orEmpty(), StrengthExerciseLibrary.exercises.map { it.name },
                    home = equipmentFilter == "Dom"
                ).filter { candidate -> active.entries.none { it.exercise == candidate } }
                AlertDialog(onDismissRequest = { showReplacement = false },
                    title = { Text("Zamień ćwiczenie") },
                    text = {
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Wszystko", "Dom", "Siłownia").forEach { option ->
                                    FilterChip(selected = equipmentFilter == option,
                                        onClick = { equipmentFilter = option },
                                        label = { Text(option) })
                                }
                            }
                            Text("Ta sama orientacyjna grupa mięśniowa. Sprawdź technikę i dostępny sprzęt.",
                                style = MaterialTheme.typography.bodySmall)
                        if (choices.isEmpty()) Text("Brak potwierdzonych zamienników w bibliotece. " +
                            "Nie proponujemy losowych ćwiczeń.")
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(choices.size) { n ->
                                TextButton(onClick = {
                                    onReplaceExercise(active.id, selected.orEmpty(), choices[n])
                                    showReplacement = false
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text(choices[n])
                                }
                            }
                        }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showReplacement = false }) { Text("Anuluj") } })
            }
            val advice = selected?.let { WorkoutInsights.coachAdvice(sports, it,
                active.entries.firstOrNull { e -> e.exercise == it }?.reps ?: 10) }
            if (!allCompleted && previous != null) Text("📈 Poprzedni wynik: " + previous + " kg • sprawdź powtórzenia w dzienniku")
            if (!allCompleted && advice != null) {
                Text("🧠 Trener: " + advice.recommendation)
                if (advice.weightKg != null) Text("Orientacyjny ciężar: " + advice.weightKg + " kg")
                if (advice.reasons.isNotEmpty()) Text(advice.reasons.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall)
                if (!advice.dataAvailable) Text("Brakuje aktualnych danych regeneracji — dodaj sen i samopoczucie.",
                    style = MaterialTheme.typography.bodySmall)
            }
            val nextEntry = active.entries.indexOfFirst { it.exercise == selected && !it.done }
            val visibleEntry = if (nextEntry >= 0) nextEntry else active.entries.indexOfLast { it.exercise == selected }
            if (!allCompleted) active.entries.forEachIndexed { i, entry ->
                if (entry.exercise == selected && i == visibleEntry) {
                    val prior = priorSets.getOrNull((entry.number - 1).coerceAtLeast(0)) ?: priorSets.lastOrNull()
                    var kg by rememberSaveable(active.id, i) { mutableStateOf(if (entry.kg > 0.0) entry.kg.toString() else prior?.kg?.toString() ?: "0") }
                    var reps by rememberSaveable(active.id, i) { mutableStateOf(prior?.reps?.toString() ?: entry.reps.toString()) }
                    if (prior != null) Text("Poprzednio: ${prior.kg} kg × ${prior.reps} powtórzeń", style = MaterialTheme.typography.bodyMedium)
                    Text("Seria ${entry.number} • ${active.entries.count { it.exercise == selected && it.done }} z ${active.entries.count { it.exercise == selected }} ukończonych", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${entry.number}.", modifier = Modifier.width(24.dp))
                        OutlinedTextField(kg, { kg = it }, label = { Text("kg") },
                            modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(reps, { reps = it }, label = { Text("Powt.") },
                            modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Button(onClick = {
                        val weight = kg.replace(',', '.').toDoubleOrNull()
                        val count = reps.toIntOrNull()
                        if (weight != null && count != null) {
                            onEntry(active.id, i, count, weight, !entry.done)
                            if (!entry.done) { rest = 60; restRunning = true }
                            else { rest = 0; restRunning = false }
                        }
                    }, enabled = !busy && kg.replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it in 0.0..2000.0 } == true &&
                        reps.toIntOrNull()?.let { it in 1..1000 } == true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                        Text(if (entry.done) "↩ Cofnij ukończenie serii" else "✓ Zakończ serię")
                    }
                }
            }
            if (!allCompleted) Text("⏱️ Przerwa: ${rest / 60}:${(rest % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.titleLarge)
            if (!allCompleted) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { rest = 45; restRunning = true }) { Text("45s") }
                TextButton(onClick = { rest = 60; restRunning = true }) { Text("60s") }
                TextButton(onClick = { rest = 90; restRunning = true }) { Text("90s") }
                TextButton(onClick = { restRunning = !restRunning }, enabled = rest > 0) {
                    Text(if (restRunning) "Pauza" else "Wznów")
                }
                TextButton(onClick = { rest = 0; restRunning = false }) { Text("Pomiń") }
            }
            if (!allCompleted) OutlinedButton(onClick = { showExerciseList = !showExerciseList }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showExerciseList) "Ukryj listę ćwiczeń" else "☰ Lista ćwiczeń")
            }
            if (!allCompleted && showExerciseList) pendingExercises.forEach { exercise ->
                val position = exercises.indexOf(exercise)
                TextButton(onClick = { exerciseIndex = position; showExerciseList = false },
                    modifier = Modifier.fillMaxWidth()) {
                    Text(SportExerciseIcons.forExercise(exercise) + " " + exercise)
                }
            }
            if (!allCompleted) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exerciseIndex = (index - 1).coerceAtLeast(0) },
                    enabled = index > 0) { Text("← Wstecz") }
                OutlinedButton(onClick = { exerciseIndex = (index + 1).coerceAtMost(exercises.lastIndex) },
                    enabled = index < exercises.lastIndex) { Text("Dalej →") }
            }
            if (allCompleted) Text("Wszystkie serie zapisane. Możesz zakończyć trening i zobaczyć podsumowanie.")
            Text("Odczuwalny wysiłek: ${effort}/10")
            Slider(value = effort.toFloat(), onValueChange = { effort = it.toInt().coerceIn(1, 10) },
                valueRange = 1f..10f, steps = 8)
            Button(onClick = { if (allCompleted) showFinishConfirmation = true else showExitChoice = true }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) { Text("🏁 Zakończ trening") }
            if (showExitChoice) AlertDialog(
                onDismissRequest = { showExitChoice = false },
                title = { Text("Co zrobić z treningiem?") },
                text = { Text("Zapisz wykonane serie w dzienniku lub odrzuć sesję próbną bez zapisywania. Twój plan pozostanie bez zmian.") },
                confirmButton = { Button(onClick = { showExitChoice = false; showFinishConfirmation = true }, enabled = done > 0) { Text("Zapisz trening") } },
                dismissButton = {
                    Column {
                        TextButton(onClick = { showExitChoice = false; onDiscard(active.id) }) { Text("Nie zapisuj — odrzuć trening") }
                        TextButton(onClick = { showExitChoice = false }) { Text("Kontynuuj trening") }
                    }
                }
            )
            if (showFinishConfirmation) AlertDialog(
                onDismissRequest = { showFinishConfirmation = false },
                title = { Text("Zakończyć trening?") },
                text = { Text("Zapisano $done z ${active.entries.size} serii. Niewykonane serie pozostaną pominięte w podsumowaniu.") },
                confirmButton = { Button(onClick = {
                    showFinishConfirmation = false
                    onFinish(active.id, effort)
                }) { Text("Zakończ i zapisz") } },
                dismissButton = { TextButton(onClick = { showFinishConfirmation = false }) { Text("Wróć do treningu") } }
            )
            Text("Postęp zapisuje się po każdej zatwierdzonej serii. Po powrocie do aplikacji można wznowić sesję.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
