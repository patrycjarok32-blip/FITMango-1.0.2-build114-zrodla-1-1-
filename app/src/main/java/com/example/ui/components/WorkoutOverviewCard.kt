package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.viewmodel.*
import java.time.LocalDate

@Composable
fun WorkoutOverviewCard(
    today: LocalDate, sports: SportsData, sessions: List<WorkoutSession>,
    challenges: List<VideoChallenge>, busy: Boolean,
    onRestDay: (String) -> Unit
) {
    val summary = remember(sports, sessions, challenges) { WorkoutInsights.summary(sports, sessions, challenges) }
    val start = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val days = remember(start, sports, sessions, challenges) {
        WorkoutInsights.calendar(start, sports, sessions, challenges)
    }
    var confirmRest by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("📅 Kalendarz treningów", style = MaterialTheme.typography.titleLarge)
        days.forEach { day ->
            val labels = buildList {
                if (day.strengthPlanned > 0) add("siłowy w planie")
                if (day.strengthDone > 0) add("siłowy ✓")
                if (day.videosPlanned > 0) add("wideo w planie ${day.videosPlanned}")
                if (day.videosDone > 0) add("wideo ✓ ${day.videosDone}")
                if (day.recovery) add("regeneracja")
            }
            Text("${day.date.dayOfWeek.name.take(3)} ${day.date.dayOfMonth} • ${labels.joinToString(" · ").ifBlank { "bez wpisów" }}")
        }
        HorizontalDivider()
        Text("📊 Statystyki i rekordy", style = MaterialTheme.typography.titleLarge)
        Text("Ukończone treningi: ${summary.sessions} • wideo: ${summary.videoMinutes} min")
        Text("Siłowe: ${summary.minutes} min • ${summary.sets} serii • ${"%.1f".format(summary.volumeKg)} kg objętości")
        summary.bestKg.entries.sortedByDescending { it.value }.take(5).forEach { (name, kg) ->
            Text("🏋️ $name: ${"%.1f".format(kg)} kg")
        }
        Text("🏆 Osiągnięcia", style = MaterialTheme.typography.titleMedium)
        if (summary.badges.isEmpty()) Text("Pierwsza odznaka czeka na ukończony trening.")
        summary.badges.forEach { Text(it) }
        HorizontalDivider()
        Text("🌿 Regeneracja i zamiana treningu", style = MaterialTheme.typography.titleMedium)
        Text("Możesz zastąpić dzisiejszą sesję dniem odpoczynku. Nie usuwa to historii ani wykonanych serii.",
            style = MaterialTheme.typography.bodySmall)
        val plan = sports.plans.firstOrNull { it.date == today.toString() && !it.rest }
        if (plan != null) {
            OutlinedButton(onClick = { confirmRest = true }, enabled = !busy) {
                Text("Zamień ${plan.title} na regenerację")
            }
        }
        if (confirmRest) {
            Text("Potwierdź zmianę planu na dzisiaj. Możesz później zaplanować nową sesję.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { confirmRest = false }) { Text("Anuluj") }
                Button(onClick = { onRestDay(today.toString()); confirmRest = false },
                    enabled = !busy) { Text("Zatwierdź") }
            }
        }
    }
}
