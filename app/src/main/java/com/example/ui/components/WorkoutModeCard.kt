package com.example.ui.components

import androidx.compose.foundation.layout.*
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
    onFinish: (String, Int) -> Unit
) {
    val active = workouts.firstOrNull { it.ended == 0L }
    val last = workouts.lastOrNull { it.ended > 0L }
    var exerciseIndex by rememberSaveable(active?.id) { mutableIntStateOf(0) }
    var showTechnique by rememberSaveable { mutableStateOf(false) }
    var showExerciseList by rememberSaveable { mutableStateOf(false) }
    var effort by rememberSaveable { mutableIntStateOf(6) }
    var rest by rememberSaveable { mutableIntStateOf(0) }
    var restRunning by rememberSaveable { mutableStateOf(false) }
    var showFinishConfirmation by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(restRunning, rest) {
        if (restRunning && rest > 0) { delay(1000); rest-- }
        else if (rest == 0) restRunning = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("🏋️ Trening w toku", style = MaterialTheme.typography.titleLarge)
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
                Text("Czas: ${((last.ended - last.started) / 60000).coerceAtLeast(0)} min • wysiłek ${last.effort}/10")
            }
        } else {
            val exercises = active.entries.map { it.exercise }.distinct()
            val index = exerciseIndex.coerceIn(0, (exercises.size - 1).coerceAtLeast(0))
            val selected = exercises.getOrNull(index)
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
            val done = active.entries.count { it.done }
            Text("🟢 ${active.title} • Ćwiczenie ${index + 1} z ${exercises.size}", style = MaterialTheme.typography.titleMedium)
            Text("Wykonano ${done}/${active.entries.size} serii")
            LinearProgressIndicator(progress = { if (active.entries.isEmpty()) 0f else done.toFloat() / active.entries.size },
                modifier = Modifier.fillMaxWidth())
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(selected.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    // Full-width photograph instead of the former narrow gallery column.
                    ExercisePhotoCard(selected.orEmpty(), modifier = Modifier.fillMaxWidth())
                    Text("Ćwiczenie ${index + 1} z ${exercises.size}", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { showTechnique = true }) { Text("ⓘ Technika i wskazówki") }
                }
            }
            if (showTechnique) {
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
            val previous = selected?.let { WorkoutInsights.previousKg(sports.sets, it) }
            val advice = selected?.let { WorkoutInsights.coachAdvice(sports, it,
                active.entries.firstOrNull { e -> e.exercise == it }?.reps ?: 10) }
            if (previous != null) Text("📈 Poprzedni wynik: " + previous + " kg • sprawdź powtórzenia w dzienniku")
            if (advice != null) {
                Text("🧠 Trener: " + advice.recommendation)
                if (advice.weightKg != null) Text("Orientacyjny ciężar: " + advice.weightKg + " kg")
                if (advice.reasons.isNotEmpty()) Text(advice.reasons.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall)
                if (!advice.dataAvailable) Text("Brakuje aktualnych danych regeneracji — dodaj sen i samopoczucie.",
                    style = MaterialTheme.typography.bodySmall)
            }
            val nextEntry = active.entries.indexOfFirst { it.exercise == selected && !it.done }
            val visibleEntry = if (nextEntry >= 0) nextEntry else active.entries.indexOfLast { it.exercise == selected }
            active.entries.forEachIndexed { i, entry ->
                if (entry.exercise == selected && i == visibleEntry) {
                    var kg by rememberSaveable(active.id, i) { mutableStateOf(entry.kg.toString()) }
                    var reps by rememberSaveable(active.id, i) { mutableStateOf(entry.reps.toString()) }
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
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (entry.done) "↩ Cofnij ukończenie serii" else "✓ Zakończ serię")
                    }
                }
            }
            Text("⏱️ Przerwa: ${rest / 60}:${(rest % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { rest = 60; restRunning = true }) { Text("60s") }
                TextButton(onClick = { restRunning = !restRunning }, enabled = rest > 0) {
                    Text(if (restRunning) "Pauza" else "Wznów")
                }
                TextButton(onClick = { rest = 0; restRunning = false }) { Text("Pomiń") }
            }
            OutlinedButton(onClick = { showExerciseList = !showExerciseList }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showExerciseList) "Ukryj listę ćwiczeń" else "☰ Lista ćwiczeń")
            }
            if (showExerciseList) exercises.forEachIndexed { position, exercise ->
                TextButton(onClick = { exerciseIndex = position; showExerciseList = false },
                    modifier = Modifier.fillMaxWidth()) {
                    Text(SportExerciseIcons.forExercise(exercise) + " " + exercise)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exerciseIndex = (index - 1).coerceAtLeast(0) },
                    enabled = index > 0) { Text("← Wstecz") }
                OutlinedButton(onClick = { exerciseIndex = (index + 1).coerceAtMost(exercises.lastIndex) },
                    enabled = index < exercises.lastIndex) { Text("Dalej →") }
            }
            Text("Odczuwalny wysiłek: ${effort}/10")
            Slider(value = effort.toFloat(), onValueChange = { effort = it.toInt().coerceIn(1, 10) },
                valueRange = 1f..10f, steps = 8)
            Button(onClick = { showFinishConfirmation = true }, enabled = !busy && done > 0,
                modifier = Modifier.fillMaxWidth()) { Text("🏁 Zakończ i zapisz podsumowanie") }
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
