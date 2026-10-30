package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import com.example.viewmodel.*
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun NutritionWeekCard(viewModel: DietaViewModel) {
    val days by viewModel.habits.days.collectAsState()
    val approvals by viewModel.healthConnect.weeklyEnergy.confirmations.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val date by viewModel.currentDate.collectAsState()
    val today = LocalDate.parse(date)
    val review by produceState<NutritionWeekReview?>(null, days, approvals, profile, date) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            NutritionWeekSupport.review(profile, days, approvals, today.minusDays(7), today.minusDays(1), today)
        }
    }
    var details by rememberSaveable(date) { mutableStateOf(false) }
    CollapsibleMangoCard("Ocena jadłospisu tygodnia", "nutrition_week_review") {
        val r = review
        if (r == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
            Text("${today.minusDays(7)} – ${today.minusDays(1)}", style = MaterialTheme.typography.titleMedium)
            Text("Pełne dni potwierdzone przez Ciebie: ${r.approved.size}/${r.periodDays}", modifier = Modifier.testTag("nutrition_approved_complete"))
            LinearProgressIndicator(progress = { r.approved.size.toFloat() / r.periodDays.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(), color = LeafGreen, trackColor = WarmBrown)
            Text("Zatwierdzone dni wchodzą do średnich także przy szacunkowych wartościach składników. To pasek pokrycia okresu, nie ocena zdrowotna.", style = MaterialTheme.typography.bodySmall)
            if (r.approved.isNotEmpty()) {
                Text("Średnio ${r.meanCalories?.roundToInt()} kcal • B ${r.meanProtein?.roundToInt()} g • W ${r.approved.map { it.carbs }.average().roundToInt()} g • T ${r.approved.map { it.fat }.average().roundToInt()} g")
                r.proteinTarget?.let { target ->
                    Text("Aktualny cel białka: $target g • osiągnięty w ${r.proteinDaysAtTarget}/${r.approved.size} dni")
                    LinearProgressIndicator(progress = { ((r.meanProtein ?: 0.0) / target).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
                Text("Warzywa i owoce: ${r.produceMean?.let { "${it.roundToInt()} g/dzień" } ?: "brak gramatur do średniej"} • ${r.produceMeasuredDays} dni z gramaturą")
                Text("Zapisane napoje: ${r.drinksMean?.roundToInt()} ml/dzień • cel osiągnięty w ${r.drinksDaysAtGoal}/${r.approved.size} dni")
                Text("Mocne strony", style = MaterialTheme.typography.titleSmall)
                r.strengths.forEach { Text("• $it") }
            }
            Text("Na kolejny tydzień", style = MaterialTheme.typography.titleSmall)
            r.actions.forEachIndexed { index, action -> Text("${index + 1}. $action") }
            TextButton(onClick = { details = !details }, modifier = Modifier.testTag("nutrition_week_details")) {
                Text(if (details) "Zwiń szczegóły −" else "Porównanie i podstawy oceny +")
            }
            if (details) {
                Text("Poprzedni okres: ${today.minusDays(14)} – ${today.minusDays(8)} • ${r.previous.size} zatwierdzonych dni")
                if (r.previousMeanCalories != null && r.meanCalories != null) {
                    Text("Kalorie: ${r.previousMeanCalories.roundToInt()} → ${r.meanCalories.roundToInt()} kcal/dzień")
                    Text("Białko: ${r.previousMeanProtein?.roundToInt()} → ${r.meanProtein?.roundToInt()} g/dzień")
                    Text("Porównuj liczbę dni i cele obu okresów. Mniej kalorii nie oznacza automatycznie lepszej diety.", style = MaterialTheme.typography.bodySmall)
                } else Text("Za mało zatwierdzonych zapisów do porównania średnich.")
                r.observations.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                Text("Bez pomiarów nie oceniamy błonnika, soli, cukrów dodanych, tłuszczów nasyconych, witamin ani pór jedzenia. Nazwa dania nie dowodzi jego składu. Brak takich pomiarów nie podważa pełnego zapisu dnia.", style = MaterialTheme.typography.bodySmall)
            }
            Text("Obliczenia lokalne. Wyjaśnienie AI połączy je z aktywnością, samopoczuciem i pomiarami po wygenerowaniu raportu.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
        }
    }
}
