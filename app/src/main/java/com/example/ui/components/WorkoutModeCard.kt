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
    val active = workouts.firstOrNull { it.ended == 0L }
    var recoveryNote by rememberSaveable { mutableStateOf(false) }
    val last = workouts.lastOrNull { it.ended > 0L }
    var exerciseIndex by rememberSaveable(active?.id) { mutableIntStateOf(0) }
    var showReplacement by rememberSaveable { mutableStateOf(false) }
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
        val lastCompleted = workouts.filter { it.ended > 0L }.maxByOrNull { it.ended }
        if (lastCompleted != null) {
            TextButton(onClick = { recoveryNote = !recoveryNote }) {
                Text("😴 Mango Trener regeneracji")
            }
            if (recoveryNote) {
                val elapsedHours = ((System.currentTimeMillis() - lastCompleted.ended)
                    .coerceAtLeast(0L) / 3600000L)
                Text(if (lastCompleted.effort >= 8 && elapsedHours < 24)
                    "Ostatni trening był intensywny. Rozważ lżejszą sesję lub odpoczynek."
                    else if (elapsedHours < 12)
                    "Od ostatniej sesji minęło mniej niż 12 godzin. Sprawdź sen i samopoczucie."
                    else "Sprawdź regenerację, sen i zmęczenie przed doborem obciążenia.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        MangoGuide(message = if (active == null) "Gotowa na trening?" else "Trening trwa — skup się na następnej serii.",
            event = if (active?.entries?.all { it.done } == true) 1 else 0, size = 32.dp)
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
                val choices = StrengthExerciseLibrary.exercises.map { it.name }
                    .filter { it != selected && active.entries.none { e -> e.exercise == it } }
                AlertDialog(onDismissRequest = { showReplacement = false },
                    title = { Text("Zamień ćwiczenie") },
                    text = {
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                            items(choices.size) { n ->
                                TextButton(onClick = {
                                    onReplaceExercise(active.id, selected.orEmpty(), choices[n])
                                    showReplacement = false
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text(choices[n])
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
