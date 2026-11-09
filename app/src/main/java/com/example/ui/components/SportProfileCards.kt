package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import com.example.viewmodel.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Uses the existing sports_v1 storage; no Room migration or extra Health Connect permissions. */
@Composable
fun SportProfileCards(data: SportsData, today: LocalDate, busy: Boolean,
    onPlan: (String, String, Boolean) -> Unit, onRemovePlan: (String) -> Unit,
    onAddSet: (String, String, Int, Double) -> Unit) {
    var planDay by rememberSaveable { mutableStateOf(today.toString()) }
    var planTitle by rememberSaveable { mutableStateOf("Push") }
    var rest by rememberSaveable { mutableStateOf(false) }
    var exercise by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableStateOf("10") }
    var weight by rememberSaveable { mutableStateOf("0") }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showSetEditor by rememberSaveable { mutableStateOf(false) }
    val weekStart = SportsSupport.week(today)
    val plans = remember(data.plans, weekStart) {
        (0L..6L).map { weekStart.plusDays(it) to data.plans.firstOrNull { p -> p.date == weekStart.plusDays(it).toString() } }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_profile")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SportHeading(Icons.Outlined.FitnessCenter, "Mój profil sportowy")
            Text("🥭 Twój plan • małe kroki, duży progres", style = MaterialTheme.typography.titleSmall, color = LeafGreen)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SportProfileSummary("Treningi", "${data.plans.count { !it.rest }}", Modifier.weight(1f))
                SportProfileSummary("Serie", "${data.sets.size}", Modifier.weight(1f))
                SportProfileSummary("Odpoczynek", "${data.plans.count { it.rest }}", Modifier.weight(1f))
            }
            Text("Plan tygodnia • zapis lokalny", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            plans.forEach { (date, plan) ->
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("sport_plan_day_${date}"),
                    shape = RoundedCornerShape(18.dp),
                    color = if (date == today) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, if (date == today) LeafGreen.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEE dd.MM", Locale("pl"))),
                            modifier = Modifier.width(94.dp), style = MaterialTheme.typography.bodySmall)
                        Column(Modifier.weight(1f)) {
                            Text(plan?.title ?: "Bez planu",
                                fontWeight = if (date == today) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium)
                            if (plan?.rest == true) Text("Regeneracja", style = MaterialTheme.typography.labelSmall)
                        }
                        if (plan != null) TextButton(onClick = { onRemovePlan(date.toString()) },
                            enabled = !busy) {
                            Icon(Icons.Outlined.Close, contentDescription = "Usuń plan z ${date}")
                        }
                    }
                }
            }
            TextButton(onClick = { showEditor = !showEditor }) { Text(if (showEditor) "Zamknij edycję" else "Zaplanuj trening +") }
            if (showEditor) {
                OutlinedTextField(planDay, { planDay = it }, label = { Text("Data RRRR-MM-DD") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(planTitle, { planTitle = it.take(120) }, label = { Text("Nazwa treningu") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !rest)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rest, { rest = it }); Text("Dzień odpoczynku")
                }
                val valid = runCatching { LocalDate.parse(planDay) }.getOrNull() != null &&
                    (rest || planTitle.isNotBlank())
                Button(onClick = { onPlan(planDay, planTitle, rest) },
                    enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) { Text("Zapisz plan") }
            }
            HorizontalDivider()
            TextButton(onClick = { showSetEditor = !showSetEditor }) {
                Text(if (showSetEditor) "Zwiń zapis serii −" else "Zapisz serię siłową +")
            }
            if (showSetEditor) {
            OutlinedTextField(exercise, { exercise = it.take(120) }, label = { Text("Ćwiczenie") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(reps, { reps = it }, label = { Text("Powtórzenia") },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(weight, { weight = it }, label = { Text("Ciężar kg") },
                    singleLine = true, modifier = Modifier.weight(1f))
            }
            val count = reps.toIntOrNull()
            val kg = weight.replace(',', '.').toDoubleOrNull()
            Button(onClick = { if (count != null && kg != null) onAddSet(today.toString(), exercise.trim(), count, kg) },
                enabled = !busy && exercise.isNotBlank() && count != null && count in 1..1000 &&
                    kg != null && kg.isFinite() && kg in 0.0..2000.0,
                modifier = Modifier.fillMaxWidth().testTag("sport_profile_add_set")) { Text("Zapisz serię") }
            }
        }
    }
    val recent = remember(data.sets, today) {
        data.sets.filter { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d ->
            !d.isAfter(today) && !d.isBefore(today.minusDays(83)) } == true }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_strength_progress")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SportHeading(Icons.Outlined.TrendingUp, "Siła i rekordy osobiste")
            if (recent.none { it.kg > 0.0 }) Text("Zapisz serię z ciężarem, aby zobaczyć postępy z 12 tygodni.")
            else {
                val exercises = recent.filter { it.kg > 0.0 }.groupBy { it.exercise.trim().lowercase(Locale.ROOT) }
                exercises.entries.sortedByDescending { it.value.size }.take(5).forEach { (_, sets) ->
                    val best = sets.maxByOrNull { it.kg }!!
                    val volume = sets.sumOf { it.kg * it.reps }
                    Text("${best.exercise} • rekord ${formatSport(best.kg)} kg • objętość ${formatSport(volume)} kg",
                        style = MaterialTheme.typography.bodySmall)
                    val points = sets.sortedWith(compareBy<SportSet> { it.date }.thenBy { it.time }).takeLast(16)
                    if (points.size >= 2) {
                        val min = points.minOf { it.kg }
                        val max = points.maxOf { it.kg }
                        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                            val range = (max - min).coerceAtLeast(1.0)
                            val coords = points.mapIndexed { index, set ->
                                Offset(size.width * index / (points.size - 1),
                                    size.height * (1f - ((set.kg - min) / range).toFloat()))
                            }
                            coords.zipWithNext().forEach { (a, b) ->
                                drawLine(LeafGreen, a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                            }
                            coords.forEach { drawCircle(LeafGreen, radius = 3.dp.toPx(), center = it) }
                        }
                    }
                }
                Text("Rekord oznacza największy zapisany ciężar, a objętość sumę ciężar × powtórzenia. Nie są to szacunki maksymalnej siły.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_recovery_guidance")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SportHeading(Icons.Outlined.AutoAwesome, "Wskazówki treningowe")
            val check = data.recovery.firstOrNull { it.date == today.toString() }
            val sleep = data.daily.firstOrNull { it.date == today.toString() }?.sleepMinutes
            val tip = when {
                check == null && sleep == null -> "Dodaj ocenę energii i zmęczenia lub zsynchronizuj sen, aby otrzymać wskazówkę."
                check != null && (check.fatigue >= 4 || check.energy <= 2) ->
                    "Zgłaszasz duże zmęczenie lub niską energię. Rozważ lżejszą sesję albo odpoczynek."
                sleep != null && sleep < 360 -> "Odczyt snu jest krótki. Rozważ spokojniejszy trening i zadbaj o regenerację."
                else -> "Jeśli czujesz się dobrze, realizuj plan i obserwuj technikę oraz samopoczucie."
            }
            Text(tip)
            Text("Wskazówki lokalne oparte na zapisanych danych, nie diagnoza medyczna ani zewnętrzny model AI.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Sen: ${sleep?.let { "${it / 60} h ${it % 60} min" } ?: "brak odczytu"} • energia: ${check?.energy ?: "–"}/5 • zmęczenie: ${check?.fatigue ?: "–"}/5",
                style = MaterialTheme.typography.bodySmall)
            Text("Kalorie i białko sprawdzisz w zakładce Dieta; aktywność nie podnosi automatycznie limitu kalorii.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatSport(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

@Composable
private fun SportProfileSummary(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
        }
    }
}
