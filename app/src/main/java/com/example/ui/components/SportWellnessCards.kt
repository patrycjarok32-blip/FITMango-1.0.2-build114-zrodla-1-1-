package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.runtime.*
import androidx.compose.ui.draw.scale
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
                if (latest.date == today.toString() && (latest.energy <= 2 || latest.fatigue >= 4)) {
                    Text("Zgłaszasz niską energię lub duże zmęczenie. Rozważ lżejszą aktywność i odpoczynek.",
                        color = WaterAccent, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("To wskazówki oparte na wpisach, nie diagnoza ani automatyczna zmiana planu.",
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
