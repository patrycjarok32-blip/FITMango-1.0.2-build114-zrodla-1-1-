package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalAccessibilityManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val motion = com.example.ui.screens.progressMotionEnabled()
    var editing by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
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
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Flag, contentDescription = null, tint = LeafGreen)
                Text("Etapy redukcji", style = MaterialTheme.typography.titleMedium)
            }
            Text("Twój harmonogram kalorii i terminów korekt.")
            if (saved == null) Text("Przykład: tygodnie 1–3: 1800 kcal → 4–6: 1700 kcal → 7–9: 1600 kcal. Ustaw i zapisz własny plan.")
            saved?.let { plan ->
                val periods = remember(plan) { ReductionPlanSupport.periods(plan) }
                val active = periods.indexOfFirst { it.contains(today) }
                val format = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                val current = periods.getOrNull(active)
                Text("Plan: ${periods.size} etapów", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    periods.forEachIndexed { index, period ->
                        Card(Modifier.weight(1f), colors = CardDefaults.cardColors(
                            containerColor = if (index == active) LeafGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
                        )) {
                            Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Etap ${index + 1}", style = MaterialTheme.typography.labelSmall)
                                Text("${period.stage.calories} kcal", style = MaterialTheme.typography.labelMedium)
                                Text("${period.stage.weeks} tyg.", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Text(
                    when {
                        current != null -> "Teraz: ${current.stage.calories} kcal • pozostało ${current.daysRemaining(today)} dni"
                        today < periods.first().start -> "Plan rozpocznie się ${periods.first().start.format(format)}"
                        else -> "Plan zakończony"
                    }, color = LeafGreen, style = MaterialTheme.typography.titleSmall
                )
                current?.let { period ->
                    val elapsed = ChronoUnit.DAYS.between(period.start, today).toFloat().coerceAtLeast(0f)
                    val total = ChronoUnit.DAYS.between(period.start, period.endExclusive).toFloat().coerceAtLeast(1f)
                    val progress by animateFloatAsState((elapsed / total).coerceIn(0f, 1f), animationSpec = tween(if (motion) 550 else 0), label = "stage_progress")
                    Text("Postęp aktualnego etapu", style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp))
                }
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Ukryj harmonogram" else "Zobacz pełny harmonogram")
                }
                AnimatedVisibility(visible = showDetails, enter = expandVertically(tween(if (motion) 280 else 0)) + fadeIn(tween(if (motion) 220 else 0)), exit = shrinkVertically(tween(if (motion) 240 else 0)) + fadeOut(tween(if (motion) 180 else 0))) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReductionStepChart(plan, today)
                    periods.forEachIndexed { index, period ->
                        Text("Etap ${index + 1}: ${period.stage.calories} kcal • ${period.start.format(format)}–${period.endExclusive.minusDays(1).format(format)}")
                    }
                    }
                }
                when {
                    today < periods.first().start -> Text("Start za ${ChronoUnit.DAYS.between(today, periods.first().start)} dni.")
                    active >= 0 -> {
                        val p = periods[active]
                        val next = periods.getOrNull(active + 1)
                        Text(if (next != null) "Przegląd celu za ${p.daysRemaining(today)} dni (${p.endExclusive.format(format)}). Kolejny zapisany etap: ${next.stage.calories} kcal; zmiana nie jest automatyczna."
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
