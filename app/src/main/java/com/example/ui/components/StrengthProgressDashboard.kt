package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.viewmodel.*
import java.time.LocalDate

/** Read-only strength analytics; computed from sports_v1 without changing backup formats. */
@Composable
fun StrengthProgressDashboard(data: SportsData, today: LocalDate) {
    val exercises = remember(data.sets) {
        data.sets.map { it.exercise.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    var selected by remember { mutableStateOf("") }
    var rangeDays by remember { mutableIntStateOf(90) }
    var selectedPoint by remember { mutableIntStateOf(-1) }
    val chosen = selected.takeIf { it in exercises } ?: exercises.firstOrNull()
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📈 Siła i rekordy", style = MaterialTheme.typography.titleLarge)
            if (chosen == null) {
                Text("Zapisz pierwszą serię, aby zobaczyć wykresy i rekordy.")
            } else {
                var menu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ćwiczenie: " + chosen + " ▾")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        exercises.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                selected = name
                                menu = false
                            })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30 to "30 dni", 90 to "90 dni", 3650 to "Całość").forEach { (days, label) ->
                        FilterChip(selected = rangeDays == days, onClick = { rangeDays = days },
                            label = { Text(label) })
                    }
                }
                val history = remember(data.sets, chosen, rangeDays, today) {
                    val from = today.minusDays(rangeDays.toLong()).toString()
                    data.sets.filter { it.exercise.trim() == chosen && it.date >= from && it.date <= today.toString() }
                        .groupBy { it.date }.toSortedMap()
                        .map { (date, sets) ->
                            Triple(date, sets.maxOf { it.kg }, sets.sumOf { it.kg * it.reps })
                        }
                }
                val maxKg = history.maxOfOrNull { it.second } ?: 0.0
                val maxVolume = history.maxOfOrNull { it.third } ?: 0.0
                if (history.isEmpty()) {
                    Text("Brak zapisanych serii w wybranym okresie. Wybierz dłuższy zakres dat.")
                } else {
                    Text("🏆 Rekord ciężaru: " + "%.1f".format(maxKg) + " kg")
                    Text("🏅 Największa objętość sesji: " + "%.1f".format(maxVolume) + " kg")
                }
                Text("Wykres maksymalnego ciężaru • ostatnie 12 dni treningowych",
                    style = MaterialTheme.typography.labelMedium)
                val visibleHistory = history.takeLast(12)
                val points = visibleHistory.map { it.second }
                val tint = MaterialTheme.colorScheme.primary
                if (points.isNotEmpty()) {
                    val pointIndex = selectedPoint.takeIf { it in visibleHistory.indices }
                    if (pointIndex != null) {
                        val point = visibleHistory[pointIndex]
                        Text("📍 " + point.first + " • " + "%.1f".format(point.second) +
                            " kg • objętość " + "%.1f".format(point.third) + " kg")
                    }
                    Canvas(Modifier.fillMaxWidth().height(110.dp)
                        .pointerInput(visibleHistory) {
                            detectTapGestures { offset ->
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                selectedPoint = ((visibleHistory.size - 1) * fraction).roundToInt()
                            }
                        }) {
                        val top = (points.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
                        val coords = points.mapIndexed { index, value ->
                            Offset(
                                if (points.size == 1) size.width / 2 else index * size.width / (points.size - 1),
                                (size.height - (value / top).toFloat() * (size.height - 12.dp.toPx()) - 6.dp.toPx())
                            )
                        }
                        if (coords.size > 1) {
                            val path = Path().apply {
                                moveTo(coords.first().x, coords.first().y)
                                coords.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(path, tint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                        }
                        coords.forEach { drawCircle(tint, 4.dp.toPx(), it) }
                    }
                    Text("Dotknij wykresu, aby zobaczyć szczegóły. Od " + visibleHistory.first().first + " do " + visibleHistory.last().first,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Weekly review and optional achievements, based on completed sets rather than calendar guesses. */
@Composable
fun StrengthWeeklyReview(data: SportsData, today: LocalDate) {
    val start = SportsSupport.week(today)
    val sets = remember(data.sets, start) {
        data.sets.filter { it.date >= start.toString() && it.date <= start.plusDays(6).toString() }
    }
    val days = sets.map { it.date }.distinct().size
    val volume = sets.sumOf { it.kg * it.reps }
    val planned = data.plans.count { !it.rest && it.date >= start.toString() &&
        it.date <= start.plusDays(6).toString() }
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📅 Podsumowanie tygodnia", style = MaterialTheme.typography.titleLarge)
            Text("Treningi z zapisanymi seriami: " + days + " / " + planned + " zaplanowanych")
            Text("Wykonane serie: " + sets.size)
            Text("Łączna objętość: " + "%.1f".format(volume) + " kg")
            LinearProgressIndicator(progress = {
                if (planned == 0) 0f else (days.toFloat() / planned).coerceIn(0f, 1f)
            }, modifier = Modifier.fillMaxWidth())
            val achievement = when {
                days >= 4 -> "🏆 Cztery dni aktywności siłowej w tym tygodniu!"
                days >= 3 -> "🥇 Trzy dni treningowe w tym tygodniu!"
                days >= 1 -> "✨ Pierwszy trening tygodnia zapisany!"
                else -> "Zapisz trening, aby zdobyć pierwsze osiągnięcie."
            }
            Text(achievement)
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Ukryj szczegóły" else "Pokaż szczegóły")
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    sets.groupBy { it.exercise }.entries.sortedByDescending { it.value.size }
                        .take(8).forEach { (name, items) ->
                            Text("• " + name + ": " + items.size + " serii")
                        }
                    if (sets.isEmpty()) Text("Brak zapisanych serii w tym tygodniu.")
                }
            }
        }
    }
}

/** Conservative suggestions only; never changes planned sessions without explicit approval. */
@Composable
fun StrengthRecoverySuggestion(data: SportsData, today: LocalDate, onShift: () -> Unit, busy: Boolean) {
    val recent = data.recovery.filter { it.date <= today.toString() }.maxByOrNull { it.date }
    val future = data.plans.any { it.date >= today.toString() && !it.rest }
    if (!future) return
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🧠 Elastyczny plan", style = MaterialTheme.typography.titleMedium)
            Text("Jeśli potrzebujesz więcej regeneracji, możesz przesunąć przyszłe sesje o jeden dzień.")
            if (recent != null) Text("Ostatnia ocena regeneracji: " + recent.date +
                " • energia " + recent.energy + "/10 • zmęczenie " + recent.fatigue + "/10")
            var confirm by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { confirm = true }, enabled = !busy) {
                Text("Zaproponuj dzień odpoczynku")
            }
            if (confirm) AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text("Przesunąć przyszły plan?") },
                text = { Text("Przesunięcie o jeden dzień obejmie zaplanowane sesje od dziś. Wykonane serie i historia pozostaną bez zmian.") },
                confirmButton = {
                    Button(onClick = { onShift(); confirm = false }, enabled = !busy) {
                        Text("Potwierdź przesunięcie")
                    }
                },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("Anuluj") } }
            )
        }
    }
}
