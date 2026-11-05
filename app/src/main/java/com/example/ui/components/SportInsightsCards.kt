package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import com.example.viewmodel.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Read-only insights derived from sports_v1. No duplicated activity or nutrition storage. */
@Composable
fun SportInsightsCards(data: SportsData, today: LocalDate, report: SportsReportNumbers?) {
    val unique = ActivityRecoverySupport.unique(data.history)
    val dated = unique.mapNotNull { session ->
        runCatching {
            Triple(session, Instant.parse(session.start).atZone(ZoneId.systemDefault()).toLocalDate(),
                Duration.between(Instant.parse(session.start), Instant.parse(session.end)).toMinutes().coerceAtLeast(0))
        }.getOrNull()
    }.filter { it.second <= today }
    val recent = dated.filter { it.second >= today.minusDays(6) }
    val sets = data.sets.filter { it.date <= today.toString() }
    val latest = data.recovery.filter { it.date <= today.toString() }.maxByOrNull { it.date }
    val sleep = data.daily.find { it.date == today.toString() }?.sleepMinutes
    val readiness = when {
        latest == null && sleep == null -> "Brak danych — dodaj samopoczucie lub odczyt snu"
        latest?.energy?.let { it <= 2 } == true || latest?.fatigue?.let { it >= 4 } == true ||
            (sleep != null && sleep < 360) -> "Rozważ odpoczynek lub lżejszą aktywność"
        latest?.energy?.let { it >= 4 } == true && latest?.fatigue?.let { it <= 2 } == true -> "Samopoczucie sprzyja aktywności"
        else -> "Dostosuj intensywność do samopoczucia"
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_readiness")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Gotowość do treningu", style = MaterialTheme.typography.titleMedium, color = LeafGreen)
            Text(readiness)
            Text("Ostatnie samopoczucie: " + (latest?.let { "${it.date} • energia ${it.energy}/5, zmęczenie ${it.fatigue}/5" } ?: "brak") +
                " • sen dziś: " + (sleep?.let { "${it / 60} h ${it % 60} min" } ?: "brak"),
                style = MaterialTheme.typography.bodySmall)
            Text("Wskaźnik orientacyjny, nie ocena medyczna. Nie zmienia planu treningowego.",
                style = MaterialTheme.typography.labelSmall)
        }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_activity_calendar")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Kalendarz aktywności • 28 dni", style = MaterialTheme.typography.titleMedium, color = LeafGreen)
            val active = dated.map { it.second }.toSet()
            val rest = data.plans.filter { it.rest }.map { it.date }.toSet()
            for (week in 0..3) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (day in 0..6) {
                        val date = today.minusDays((27 - week * 7 - day).toLong())
                        val isActive = date in active
                        val isRest = date.toString() in rest
                        Box(Modifier.weight(1f).height(30.dp)
                            .background(if (isActive) LeafGreen else if (isRest) WaterAccent
                                else MutedSurface2, RoundedCornerShape(7.dp))) {
                            Text("${date.dayOfMonth}", modifier = Modifier.padding(5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) Cream else GoldAccent)
                        }
                    }
                }
            }
            Text("Zielony: aktywność • niebieski: planowany odpoczynek • pozostałe: brak wpisu",
                style = MaterialTheme.typography.labelSmall)
        }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_strength_progress")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Progres siłowy", style = MaterialTheme.typography.titleMedium, color = LeafGreen)
            val exercises = sets.groupBy { it.exercise.trim() }.filterKeys { it.isNotEmpty() }
                .entries.sortedByDescending { it.value.maxOfOrNull { set -> set.date } ?: "" }.take(5)
            if (exercises.isEmpty()) Text("Brak zapisanych serii — wykres pojawi się po dodaniu ćwiczeń.")
            exercises.forEach { (exercise, entries) ->
                val days = entries.groupBy { it.date }.toSortedMap().entries.toList().takeLast(6)
                val volumes = days.map { d -> d.value.sumOf { it.kg * it.reps } }
                val max = volumes.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                Text(exercise, style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    volumes.forEach { volume ->
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight((volume / max).toFloat().coerceIn(.04f, 1f))
                                .background(LeafGreen, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
                        }
                    }
                }
                Text("Objętość (kg × powtórzenia), ostatnie ${days.size} dni z wpisami • ostatnio: ${volumes.lastOrNull()?.roundToInt() ?: 0} kg",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_muscle_map")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mapa trenowanych mięśni", style = MaterialTheme.typography.titleMedium, color = LeafGreen)
            val labels = listOf(
                "Klatka piersiowa" to listOf("klat", "chest", "bench", "wyciskan"),
                "Plecy" to listOf("plec", "back", "row", "wiosł", "ściągan", "podciągan"),
                "Nogi" to listOf("nog", "leg", "squat", "przysiad", "martwy", "deadlift", "łyd"),
                "Barki" to listOf("bark", "shoulder", "overhead"),
                "Ramiona" to listOf("biceps", "triceps", "uginan", "prostowan"),
                "Brzuch" to listOf("brzuch", "abs", "plank")
            )
            val weekSets = sets.filter { it.date >= today.minusDays(6).toString() }
            val counts = labels.map { (label, keywords) ->
                label to weekSets.count { set -> keywords.any { it in set.exercise.lowercase() } }
            }
            val max = counts.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            counts.forEach { (label, count) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.width(126.dp), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { count.toFloat() / max },
                        modifier = Modifier.weight(1f), color = LeafGreen)
                    Text("  $count", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text("Orientacyjna mapa na podstawie nazw ćwiczeń z ostatnich 7 dni. Nieznane nazwy nie są przypisywane automatycznie.",
                style = MaterialTheme.typography.labelSmall)
        }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_cardio_stats")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Statystyki cardio • 7 dni", style = MaterialTheme.typography.titleMedium, color = WaterAccent)
            val types = listOf("Spacer" to listOf("spacer", "walk"), "Bieganie" to listOf("bieg", "run"),
                "Rower" to listOf("rower", "cycl", "bik"), "Pływanie" to listOf("pływ", "swim"))
            types.forEach { (name, words) ->
                val sessions = recent.filter { entry -> words.any { it in entry.first.title.lowercase() } }
                val minutes = sessions.sumOf { it.third }
                val km = sessions.mapNotNull { it.first.distanceKm }.sum()
                Text("$name: ${sessions.size} sesji • $minutes min" +
                    (if (sessions.any { it.first.distanceKm != null }) " • ${String.format(java.util.Locale.getDefault(), "%.1f", km)} km" else ""),
                    style = MaterialTheme.typography.bodyMedium)
            }
            Text("Dystans wyświetlany tylko, gdy jest zapisany w aktywności.",
                style = MaterialTheme.typography.labelSmall)
        }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_smart_weekly_summary")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Podsumowanie tygodnia", style = MaterialTheme.typography.titleMedium, color = LeafGreen)
            val week = report
            if (week == null) Text("Trwa przygotowanie raportu z zapisanych danych.")
            else {
                Text("${week.from} – ${week.until}: ${week.activity.sessions} treningów, ${week.activity.minutes} min ruchu.")
                Text("Potwierdzone dni diety: ${week.approved}/7 • średnie białko: " +
                    (week.meanProtein?.let { "${it.roundToInt()} g" } ?: "brak danych"),
                    style = MaterialTheme.typography.bodyMedium)
                val message = when {
                    week.activity.activeDays == 0 -> "Zacznij od krótkiej aktywności dopasowanej do możliwości."
                    week.approved < 3 -> "Uzupełnij potwierdzenia posiłków, aby lepiej porównywać dietę i aktywność."
                    else -> "Porównaj aktywność, sen i samopoczucie przed zwiększeniem obciążenia."
                }
                Text(message, color = GoldAccent)
                Text("Podsumowanie lokalne; szczegółowy raport i analiza AI pozostają w zakładce Raport.",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
