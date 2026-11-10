package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.viewmodel.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Navigation-only dashboard; all existing sports and challenge stores remain untouched. */
@Composable
fun SportTodayDashboard(today: LocalDate, data: SportsData,
    workouts: List<WorkoutSession>, challenges: List<VideoChallenge>,
    onStart: () -> Unit, onPlan: () -> Unit, onChallenges: () -> Unit, onRecovery: () -> Unit) {
    val plan = data.plans.firstOrNull { it.date == today.toString() }
    val active = workouts.firstOrNull { it.finishedAt == 0L }
    val completed = workouts.count { it.finishedAt > 0L }
    val pendingVideos = challenges.sumOf { challenge ->
        val start = runCatching { LocalDate.parse(challenge.startedOn) }.getOrNull()
        if (start == null) 0 else challenge.workouts.count {
            start.plusDays((it.day - 1).toLong()) == today && it.completedAt == 0L
        }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport5_today_dashboard")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Twój dzisiejszy plan", style = MaterialTheme.typography.titleLarge)
            Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("pl"))))
            Text(when {
                active != null -> "▶ Trening w toku: ${active.title}"
                plan?.rest == true -> "🌿 Dzień regeneracji"
                plan != null -> "🏋️ ${plan.title}"
                else -> "Brak zaplanowanej sesji"
            }, style = MaterialTheme.typography.titleMedium)
            if (plan != null && !plan.rest && plan.exercises.isNotEmpty()) {
                Text("${plan.exercises.size} ćwiczeń • ${plan.exercises.sumOf { it.sets }} serii")
            }
            if (pendingVideos > 0) Text("🎬 Wideo na dziś: $pendingVideos")
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().testTag("sport5_start")) {
                Text(if (active != null) "Wróć do treningu" else "▶ Rozpocznij trening")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPlan, modifier = Modifier.weight(1f)) { Text("Edytuj plan") }
                OutlinedButton(onClick = onRecovery, modifier = Modifier.weight(1f)) { Text("Regeneracja") }
            }
            OutlinedButton(onClick = onChallenges, modifier = Modifier.fillMaxWidth()) {
                Text("🎬 Moje wyzwania wideo")
            }
            Text("Wykonane sesje siłowe: $completed • Zapisane serie: ${data.sets.size}",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
