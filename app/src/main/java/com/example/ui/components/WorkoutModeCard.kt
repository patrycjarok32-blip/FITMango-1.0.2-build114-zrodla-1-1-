package com.example.ui.components

import androidx.compose.foundation.layout.*
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
    var effort by rememberSaveable { mutableIntStateOf(6) }
    var rest by rememberSaveable { mutableIntStateOf(0) }
    var restRunning by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(restRunning, rest) {
        if (restRunning && rest > 0) { delay(1000); rest-- }
        else if (rest == 0) restRunning = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("🏋️ Tryb treningowy FITMango", style = MaterialTheme.typography.titleLarge)
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
            val done = active.entries.count { it.done }
            Text("🟢 Trening trwa: ${active.title}")
            Text("Wykonano ${done}/${active.entries.size} serii")
            LinearProgressIndicator(progress = { if (active.entries.isEmpty()) 0f else done.toFloat() / active.entries.size },
                modifier = Modifier.fillMaxWidth())
            Text("${SportExerciseIcons.forExercise(selected.orEmpty())} Ćwiczenie ${index + 1}/${exercises.size}: ${selected.orEmpty()}",
                style = MaterialTheme.typography.titleMedium)
            val previous = selected?.let { WorkoutInsights.previousKg(sports.sets, it) }
            val advice = selected?.let { WorkoutInsights.coachAdvice(sports, it,
                active.entries.firstOrNull { e -> e.exercise == it }?.reps ?: 10) }
            if (previous != null) Text("📈 Ostatni ciężar: " + previous + " kg")
            if (advice != null) {
                Text("🧠 Trener: " + advice.recommendation)
                if (advice.weightKg != null) Text("Orientacyjny ciężar: " + advice.weightKg + " kg")
                if (advice.reasons.isNotEmpty()) Text(advice.reasons.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall)
                if (!advice.dataAvailable) Text("Brakuje aktualnych danych regeneracji — dodaj sen i samopoczucie.",
                    style = MaterialTheme.typography.bodySmall)
            }
            active.entries.forEachIndexed { i, entry ->
                if (entry.exercise == selected) {
                    var kg by rememberSaveable(active.id, i) { mutableStateOf(entry.kg.toString()) }
                    var reps by rememberSaveable(active.id, i) { mutableStateOf(entry.reps.toString()) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${entry.number}.", modifier = Modifier.width(24.dp))
                        OutlinedTextField(kg, { kg = it }, label = { Text("kg") },
                            modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(reps, { reps = it }, label = { Text("Powt.") },
                            modifier = Modifier.weight(1f), singleLine = true)
                        val weight = kg.replace(',', '.').toDoubleOrNull()
                        val count = reps.toIntOrNull()
                        Checkbox(checked = entry.done, enabled = !busy && weight != null &&
                            weight.isFinite() && weight in 0.0..2000.0 && count != null && count in 1..1000,
                            onCheckedChange = { checked ->
                                if (weight != null && count != null) {
                                    onEntry(active.id, i, count, weight, checked)
                                    if (checked) { rest = 60; restRunning = true }
                                }
                            })
                    }
                    TextButton(onClick = {
                        val weight = kg.replace(',', '.').toDoubleOrNull()
                        val count = reps.toIntOrNull()
                        if (weight != null && count != null) onEntry(active.id, i, count, weight, entry.done)
                    }, enabled = !busy && kg.replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it in 0.0..2000.0 } == true &&
                        reps.toIntOrNull()?.let { it in 1..1000 } == true) { Text("Zapisz ciężar i powtórzenia") }
                }
            }
            Text("⏱️ Przerwa: ${rest / 60}:${(rest % 60).toString().padStart(2, '0')}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { rest = 60; restRunning = true }) { Text("60s") }
                TextButton(onClick = { restRunning = !restRunning }, enabled = rest > 0) {
                    Text(if (restRunning) "Pauza" else "Wznów")
                }
                TextButton(onClick = { rest = 0; restRunning = false }) { Text("Pomiń") }
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
            Button(onClick = { onFinish(active.id, effort) }, enabled = !busy && done > 0,
                modifier = Modifier.fillMaxWidth()) { Text("🏁 Zakończ i zapisz podsumowanie") }
            Text("Postęp zapisuje się po każdej zatwierdzonej serii. Po powrocie do aplikacji można wznowić sesję.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
