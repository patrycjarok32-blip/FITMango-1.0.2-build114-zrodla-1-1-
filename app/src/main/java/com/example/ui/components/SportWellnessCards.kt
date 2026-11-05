package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import com.example.viewmodel.ActivityRecoverySupport
import com.example.viewmodel.SportsData
import com.example.viewmodel.SportsSupport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Read-only sport achievements and rest-day context based on the existing sports_v1 data.
 * No parallel tracking, new persistence format, or calorie adjustments.
 */
@Composable
fun SportWellnessCards(data: SportsData, today: LocalDate) {
    val activityDates = ActivityRecoverySupport.unique(data.history)
        .mapNotNull { runCatching { Instant.parse(it.start).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() }
        .filter { !it.isAfter(today) }.toSet()
    val weekStart = SportsSupport.week(today)
    val weekDays = activityDates.count { !it.isBefore(weekStart) && !it.isAfter(today) }
    val recent14 = activityDates.count { !it.isBefore(today.minusDays(13)) }
    val recent30 = activityDates.count { !it.isBefore(today.minusDays(29)) }
    val streakStart = if (today in activityDates) today else today.minusDays(1)
    var streak = 0
    while (streak < 365 && streakStart.minusDays(streak.toLong()) in activityDates) streak++

    MangoCard(Modifier.fillMaxWidth().testTag("sport_challenges")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wyzwania i medale", style = MaterialTheme.typography.titleMedium, color = LeafGreen)
            Text("Odznaki przyznawane za zapisane dni aktywności. Każdy dzień liczymy tylko raz.",
                style = MaterialTheme.typography.bodySmall)
            Text("Ten tydzień: $weekDays / 3 dni • seria: $streak dni",
                style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { (weekDays / 3f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(), color = LeafGreen)
            MangoBadge("Sport • 3 dni w tygodniu", weekDays >= 3)
            Text("Ostatnie 14 dni: $recent14 / 6 • ostatnie 30 dni: $recent30 / 12",
                style = MaterialTheme.typography.bodySmall)
            MangoBadge("Sport • 6 dni w 14 dniach", recent14 >= 6)
            MangoBadge("Sport • 12 dni w 30 dniach", recent30 >= 12)
        }
    }

    val plannedRest = data.plans.filter { it.rest && it.date >= today.toString() &&
        it.date <= today.plusDays(7).toString() }.sortedBy { it.date }
    val latest = data.recovery.filter { it.date <= today.toString() }
        .maxByOrNull { it.date }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_rest_summary")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Odpoczynek i regeneracja", style = MaterialTheme.typography.titleMedium, color = WaterAccent)
            Text(if (plannedRest.isEmpty()) "Nie zaplanowano dni odpoczynku na najbliższy tydzień."
                else "Zaplanowany odpoczynek: " + plannedRest.joinToString { it.date },
                style = MaterialTheme.typography.bodyMedium)
            if (latest == null) {
                Text("Dodaj ocenę energii i zmęczenia w sekcji „Sen i samopoczucie”.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Ostatni wpis: ${latest.date} • energia ${latest.energy}/5 • zmęczenie ${latest.fatigue}/5",
                    style = MaterialTheme.typography.bodySmall)
                if (latest.energy <= 2 || latest.fatigue >= 4) {
                    Text("Zgłaszasz niską energię lub duże zmęczenie. Rozważ lżejszą aktywność i odpoczynek.",
                        color = WaterAccent, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("To wskazówki oparte na wpisach, nie diagnoza ani automatyczna zmiana planu.",
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
