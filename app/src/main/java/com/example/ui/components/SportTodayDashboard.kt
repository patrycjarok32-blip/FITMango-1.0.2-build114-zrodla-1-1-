package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    workouts: List<WorkoutSession>, resolvedSteps: Long?,
    onStart: () -> Unit, onPlan: () -> Unit, onRecovery: () -> Unit, onHistory: () -> Unit,
    onAdd: () -> Unit = onPlan, onDay: (LocalDate) -> Unit = { onPlan() }) {
    val plans = data.plans.filter { it.date == today.toString() }
    val plan = plans.firstOrNull { !it.rest } ?: plans.firstOrNull()
    val finishedPlans = plans.count { !it.rest && MangoWorkoutInsights.plannedStatus(it, data, workouts) == "✓ Wykonany" }
    val recordedToday = ActivityRecoverySupport.daySessions(data, today)
    val recordedMinutes = ActivityRecoverySupport.dayMinutes(data, today)
    val active = workouts.firstOrNull { it.ended == 0L }
    var showRecorded by rememberSaveable { mutableStateOf(false) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    MangoCard(Modifier.fillMaxWidth().testTag("sport5_today_dashboard")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Trening • Dzisiaj", style = MaterialTheme.typography.titleLarge)
            Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("pl"))))
            Text("Kroki: ${resolvedSteps?.toString() ?: "brak odczytu"} • Sesje: $recordedMinutes min",
                style = MaterialTheme.typography.titleMedium)
            Text("Sesje z planu: $finishedPlans / ${plans.count { !it.rest }}",
                style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                (0..6).forEach { offset ->
                    val day = monday.plusDays(offset.toLong())
                    val count = data.plans.count { it.date == day.toString() && !it.rest }
                    OutlinedCard(onClick = { onDay(day) }, modifier = Modifier.weight(1f).testTag("sport7_day_$day")) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(listOf("Pn","Wt","Śr","Cz","Pt","So","Nd")[offset], style = MaterialTheme.typography.labelSmall)
                            Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall)
                            Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text("Liczby w kalendarzu oznaczają zaplanowane jednostki.", style = MaterialTheme.typography.labelSmall)
            if (recordedToday.isNotEmpty()) {
                TextButton(onClick = { showRecorded = !showRecorded }) { Text(if (showRecorded) "Ukryj aktywności" else "Aktywności z zegarka / dziennika (${recordedToday.size})") }
            }
            if (recordedToday.isNotEmpty() && showRecorded) {
                Card(modifier = Modifier.fillMaxWidth().testTag("sport_recorded_today"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("✓ Wykonane aktywności z zegarka / dziennika",
                            style = MaterialTheme.typography.titleSmall)
                        Text("${recordedToday.size} aktywności • $recordedMinutes min",
                            style = MaterialTheme.typography.bodyMedium)
                        recordedToday.forEach { session ->
                            val minutes = java.time.Duration.between(
                                java.time.Instant.parse(session.start),
                                java.time.Instant.parse(session.end)).toMinutes()
                            val kcal = session.activeKcal?.takeIf { it.isFinite() && it >= 0 }
                            val activityLabel = SportSessionPresentation.label(session)
                            val sourceLabel = if (session.id.startsWith("manual|")) "wpis ręczny" else session.source.ifBlank { "źródło nieznane" }
                            Text("• $activityLabel — $minutes min • " +
                                (kcal?.let { String.format(Locale.getDefault(), "%.0f kcal aktywnych", it) }
                                    ?: "kcal: brak odczytu"),
                                style = MaterialTheme.typography.bodySmall)
                            Text("Źródło: $sourceLabel", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("Aktywność zaliczona do ruchu, ale nie oznacza automatycznie wykonania innego treningu z planu.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            plans.filterNot { it.rest }.forEach { session ->
                val status = MangoWorkoutInsights.plannedStatus(session, data, workouts)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Text(session.title, style = MaterialTheme.typography.titleSmall)
                        Text("${session.category} • $status", style = MaterialTheme.typography.bodySmall)
                        if (session.targetMinutes != null || session.targetDistanceKm != null) {
                            Text(listOfNotNull(session.targetMinutes?.let { "$it min" },
                                session.targetDistanceKm?.let { "$it km" }).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (active != null || plan == null || plan.rest) Text(when {
                active != null -> "▶ Trening w toku: ${active.title}"
                plan?.rest == true && recordedToday.isNotEmpty() -> "🌿 Dzień regeneracji w planie • aktywność wykonana"
                plan?.rest == true -> "🌿 Dzień regeneracji"
                plan != null -> ""
                else -> "Brak zaplanowanej sesji"
            }, style = MaterialTheme.typography.titleMedium)
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().testTag("sport5_start")) {
                Text(if (active != null) "Wróć do treningu" else if (plans.any { !it.rest }) "Otwórz trening" else "Wybierz trening")
            }
            TextButton(onClick = { showMore = !showMore }) { Text(if (showMore) "Mniej opcji" else "Więcej opcji") }
            if (showMore) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPlan, modifier = Modifier.weight(1f)) { Text("Edytuj plan") }
                OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) { Text("Historia") }
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().testTag("sport7_add")) { Icon(Icons.Outlined.Add, contentDescription = null); Text("Dodaj jednostkę") }
            TextButton(onClick = onRecovery) { Text("🌿 Samopoczucie i regeneracja") }
            }
        }
    }
}
