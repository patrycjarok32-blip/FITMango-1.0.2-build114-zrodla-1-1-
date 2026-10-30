package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import com.example.viewmodel.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun ReductionPlanCard(saved: ReductionPlan?, today: LocalDate, busy: Boolean, profileCalories: Int?,
    onProfile: () -> Unit, onSave: (ReductionPlan?) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(today.toString()) }
    var rows by remember { mutableStateOf(listOf("1800" to "3", "1700" to "3", "1600" to "3")) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(saved) { editing = false; error = null }
    fun edit() {
        start = saved?.start ?: today.toString()
        rows = saved?.stages?.map { it.calories.toString() to it.weeks.toString() }
            ?: listOf("1800" to "3", "1700" to "3", "1600" to "3")
        error = null; editing = true
    }
    MangoCard(Modifier.fillMaxWidth().testTag("reduction_plan")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Etapy redukcji", style = MaterialTheme.typography.titleMedium)
            Text("Twój harmonogram kalorii i terminów korekt.")
            if (saved == null) Text("Przykład: tygodnie 1–3: 1800 kcal → 4–6: 1700 kcal → 7–9: 1600 kcal. Ustaw i zapisz własny plan.")
            saved?.let { plan ->
                val periods = remember(plan) { ReductionPlanSupport.periods(plan) }
                val active = periods.indexOfFirst { it.contains(today) }
                ReductionStepChart(plan, today)
                val format = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                periods.forEachIndexed { i, p ->
                    Text("${if (i == active) "● Teraz: " else ""}Etap ${i + 1}: ${p.stage.calories} kcal • ${p.stage.weeks} tyg.\n" +
                        "Tygodnie ${p.firstWeek}–${p.firstWeek + p.stage.weeks - 1} • ${p.start.format(format)}–${p.endExclusive.minusDays(1).format(format)}",
                        color = if (i == active) LeafGreen else MaterialTheme.colorScheme.onSurface)
                }
                when {
                    today < periods.first().start -> Text("Start za ${ChronoUnit.DAYS.between(today, periods.first().start)} dni.")
                    active >= 0 -> {
                        val p = periods[active]
                        val next = periods.getOrNull(active + 1)
                        Text(if (next != null) "Kolejna korekta za ${p.daysRemaining(today)} dni (${p.endExclusive.format(format)}): ${next.stage.calories} kcal."
                            else "Do końca ostatniego etapu: ${p.daysRemaining(today)} dni. Potem oceń dalszy plan.")
                        profileCalories?.takeIf { it != p.stage.calories }?.let {
                            Text("Cel w Profilu: $it kcal. Plan na dziś: ${p.stage.calories} kcal.")
                        }
                    }
                    else -> Text("Plan zakończony ${periods.last().endExclusive.minusDays(1).format(format)}. Oceń efekty i ustal dalszy plan.")
                }
                TextButton(onClick = onProfile) { Text("Przejdź do celu kalorii w Profilu") }
            }
            Text("To zapisany przez Ciebie plan, nie automatyczna rekomendacja. Termin korekty służy ocenie efektów; cel kalorii zmieniasz w Profilu.", style = MaterialTheme.typography.bodySmall)
            if (!editing) TextButton(enabled = !busy, onClick = { edit() }) { Text(if (saved == null) "Ustaw etapy" else "Edytuj etapy") }
            if (editing) {
                OutlinedTextField(start, { start = it.take(10) }, label = { Text("Start: RRRR-MM-DD") },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("reduction_start"))
                rows.forEachIndexed { index, row ->
                    Text("Etap ${index + 1}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(row.first, { value -> rows = rows.mapIndexed { i, r -> if (i == index) value.take(5) to r.second else r } },
                            label = { Text("kcal/dzień") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, enabled = !busy, modifier = Modifier.weight(1f).testTag("reduction_calories_$index"))
                        OutlinedTextField(row.second, { value -> rows = rows.mapIndexed { i, r -> if (i == index) r.first to value.take(2) else r } },
                            label = { Text("Tygodnie") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, enabled = !busy, modifier = Modifier.weight(1f).testTag("reduction_weeks_$index"))
                    }
                    if (rows.size > 1) TextButton(enabled = !busy, onClick = { rows = rows.filterIndexed { i, _ -> i != index } }) { Text("Usuń etap ${index + 1}") }
                }
                if (rows.size < 12) TextButton(enabled = !busy, onClick = { rows = rows + ("" to "3") }) { Text("Dodaj etap") }
                error?.let { Text(it, color = ErrorInk) }
                Button(enabled = !busy, modifier = Modifier.testTag("reduction_save"), onClick = {
                    val plan = runCatching {
                        ReductionPlan(start, rows.map {
                            ReductionStage(requireNotNull(it.first.toIntOrNull()) { "Podaj całkowitą liczbę kalorii." },
                                requireNotNull(it.second.toIntOrNull()) { "Podaj całkowitą liczbę tygodni." })
                        }).also { ReductionPlanSupport.validate(it) }
                    }
                    plan.onSuccess { onSave(it) }.onFailure { error = it.message ?: "Sprawdź datę i wartości etapów." }
                }) { Text("Zapisz plan") }
                TextButton(enabled = !busy, onClick = { editing = false; error = null }) { Text("Zamknij edycję") }
            }
        }
    }
}

@Composable
private fun ReductionStepChart(plan: ReductionPlan, today: LocalDate) {
    val periods = remember(plan) { ReductionPlanSupport.periods(plan) }
    val totalDays = ChronoUnit.DAYS.between(periods.first().start, periods.last().endExclusive).toFloat()
    val low = plan.stages.minOf { it.calories } - 100f
    val high = plan.stages.maxOf { it.calories } + 100f
    val active = periods.indexOfFirst { it.contains(today) }
    val description = "Plan kalorii: " + periods.joinToString { "${it.stage.calories} kcal przez ${it.stage.weeks} tygodni" }
    Text("kcal/dzień • ${low.toInt()}–${high.toInt()}", style = MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(8.dp).testTag("reduction_chart")
        .semantics { contentDescription = description }) {
        fun x(date: LocalDate) = ChronoUnit.DAYS.between(periods.first().start, date) / totalDays * size.width
        fun y(calories: Int) = (high - calories) / (high - low) * size.height
        drawLine(WarmBrown, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx())
        drawLine(WarmBrown, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        periods.forEachIndexed { i, p ->
            val color = if (i == active) LeafGreen else GoldAccent
            drawLine(color, Offset(x(p.start), y(p.stage.calories)), Offset(x(p.endExclusive), y(p.stage.calories)), 4.dp.toPx())
            periods.getOrNull(i + 1)?.let { next ->
                drawLine(GoldAccent, Offset(x(p.endExclusive), y(p.stage.calories)), Offset(x(p.endExclusive), y(next.stage.calories)), 2.dp.toPx())
            }
        }
        if (active >= 0) {
            drawLine(LeafGreen.copy(alpha = .5f), Offset(x(today), 0f), Offset(x(today), size.height), 1.dp.toPx())
            drawCircle(LeafGreen, 5.dp.toPx(), Offset(x(today), y(periods[active].stage.calories)))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Start • tydz. 1", style = MaterialTheme.typography.bodySmall)
        Text("${plan.stages.sumOf { it.weeks }} tygodni łącznie", style = MaterialTheme.typography.bodySmall)
    }
    Text("Długość stopnia = czas etapu. Zielony punkt = dziś.", style = MaterialTheme.typography.bodySmall)
}
