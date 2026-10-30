package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.example.viewmodel.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun sportTime(millis: Long) = if (millis > 0) Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd.MM HH:mm")) else "brak odczytu"

@Composable
fun ActivityTodayCard(data: SportsData, today: LocalDate, current: CurrentActivity? = null) {
    val day = data.daily.find { it.date == today.toString() }
    val summary = remember(data, today) { ActivityRecoverySupport.summary(data, today, today, Instant.now()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dziś • $today", style = MaterialTheme.typography.titleMedium)
        SportMetricRow(if (current != null) current.steps else day?.steps, summary.minutes,
            if (current != null) current.sleepMinutes else day?.sleepMinutes, sleepLabel = current?.sleepLabel ?: "Sen po pobudce dziś")
        Text("Ruch: czas zapisanych sesji, nie całego ruchu w ciągu dnia.", style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.GoldAccent)
        var details by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { details = !details }) { Text(if (details) "Zwiń źródła i odczyty −" else "Źródła i ostatnia synchronizacja +") }
        if (details) MangoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Kroki: ${(if (current != null) current.steps else day?.steps)?.toString() ?: "brak odczytu"}", modifier = Modifier.testTag("sport_today_steps"))
            Text("${summary.minutes} min zapisanych sesji • ${summary.sessions} treningów")
            if (day?.sleepEstimated == true) Text("Sen oszacowany z czasu sesji bez pełnych faz.", style = MaterialTheme.typography.bodySmall)
            Text("Treningi odczytano: ${sportTime(data.updated)}\nKroki i sen: ${sportTime(current?.readAt ?: day?.readAt ?: 0)}", style = MaterialTheme.typography.bodySmall)
            day?.let { Text(it.sources, style = MaterialTheme.typography.bodySmall) }
            if (data.readNote.isNotBlank()) Text(data.readNote, style = MaterialTheme.typography.bodySmall)
        } }
    }
}

@Composable
fun RecoveryCard(data: SportsData, today: LocalDate, busy: Boolean,
    onSave: (String, Int, Int) -> Unit, onRemove: (String) -> Unit) {
    var edit by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(today.toString()) }
    val saved = data.recovery.find { it.date == date }
    var energy by rememberSaveable(date, saved) { mutableIntStateOf(saved?.energy ?: 3) }
    var fatigue by rememberSaveable(date, saved) { mutableIntStateOf(saved?.fatigue ?: 3) }
    MangoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SportHeading(androidx.compose.material.icons.Icons.Outlined.Bedtime, "Sen i samopoczucie", com.example.ui.theme.WaterAccent)
        val chart by produceState<List<SportGraphicDay>>(emptyList(), data, today) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { SportsGraphicsSupport.days(data, today.minusDays(6), Instant.now()) }
        }
        if (chart.isNotEmpty()) SportSleepChart(chart)
        val notes = data.recovery.filter { it.date in today.minusDays(6).toString()..today.toString() }
        RatingDots("Energia", notes.takeIf { it.isNotEmpty() }?.map { it.energy }?.average(), com.example.ui.theme.LeafGreen)
        RatingDots("Zmęczenie", notes.takeIf { it.isNotEmpty() }?.map { it.fatigue }?.average(), com.example.ui.theme.WaterAccent)
        Text(ActivityRecoverySupport.recovery(data, today))
        TextButton(onClick = { edit = !edit }) { Text(if (edit) "Zwiń samopoczucie −" else "Dodaj / popraw samopoczucie +") }
        if (edit) {
            PlanDateSelector(date, { if (!busy) date = it }, "recovery_date", showDays = false)
            Text("Data: $date")
            Text("Energia: $energy/5 • 1 = mało, 5 = dużo")
            Slider(value = energy.toFloat(), onValueChange = { energy = it.toInt() }, valueRange = 1f..5f, steps = 3, enabled = !busy)
            Text("Zmęczenie: $fatigue/5 • 1 = małe, 5 = duże")
            Slider(value = fatigue.toFloat(), onValueChange = { fatigue = it.toInt() }, valueRange = 1f..5f, steps = 3, enabled = !busy)
            Button(enabled = !busy && LocalDate.parse(date) <= today, onClick = { onSave(date, energy, fatigue) }, modifier = Modifier.testTag("save_recovery")) { Text("Zapisz samopoczucie") }
            if (saved != null) TextButton(enabled = !busy, onClick = { onRemove(date) }) { Text("Usuń samopoczucie z tego dnia") }
        }
        Text("Brak snu lub samopoczucia pozostaje brakiem danych. Wskazówki nie zmieniają celu kalorii.", style = MaterialTheme.typography.bodySmall)
    } }
}
