package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import com.example.viewmodel.*
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
private fun Modifier.sportAppear(): Modifier {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ready = true }
    val opacity = mangoFloat(if (ready || LocalReducedMangoMotion.current) 1f else 0f)
    return this.graphicsLayer { alpha = opacity }
}

@Composable
fun SportHeading(icon: ImageVector, title: String, color: Color = LeafGreen) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(22.dp), tint = color)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SportMetric(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    MangoCard(modifier) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(24.dp), tint = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = GoldAccent)
        Text(value, style = MaterialTheme.typography.titleLarge)
    } }
}

@Composable
fun SportMetricRow(steps: Long?, minutes: Long, sleep: Long?, modifier: Modifier = Modifier,
    sleepLabel: String = "Sen po pobudce dziś") {
    val large = LocalDensity.current.fontScale > 1.25f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (large) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SportMetric("Kroki dziś", HealthConnectSupport.formatStepsDisplay(steps), Icons.Outlined.DirectionsWalk, LeafGreen, Modifier.fillMaxWidth().testTag("activity_steps_metric"))
            SportMetric("Ruch dziś", "$minutes min", Icons.Outlined.Schedule, LeafGreen, Modifier.fillMaxWidth().testTag("activity_movement_metric"))
            SportMetric(sleepLabel, HealthConnectSupport.formatSleepDurationDisplay(sleep), Icons.Outlined.Bedtime, SleepAccent, Modifier.fillMaxWidth().testTag("activity_sleep_metric"))
        } else if (maxWidth < 480.dp) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SportMetric("Kroki dziś", HealthConnectSupport.formatStepsDisplay(steps), Icons.Outlined.DirectionsWalk, LeafGreen, Modifier.weight(1f).fillMaxHeight().testTag("activity_steps_metric"))
                SportMetric("Ruch dziś", "$minutes min", Icons.Outlined.Schedule, LeafGreen, Modifier.weight(1f).fillMaxHeight().testTag("activity_movement_metric"))
            }
            SportMetric(sleepLabel, HealthConnectSupport.formatSleepDurationDisplay(sleep), Icons.Outlined.Bedtime, SleepAccent, Modifier.fillMaxWidth().testTag("activity_sleep_metric"))
        } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SportMetric("Kroki dziś", HealthConnectSupport.formatStepsDisplay(steps), Icons.Outlined.DirectionsWalk, LeafGreen, Modifier.weight(1f).fillMaxHeight().testTag("activity_steps_metric"))
            SportMetric("Ruch dziś", "$minutes min", Icons.Outlined.Schedule, LeafGreen, Modifier.weight(1f).fillMaxHeight().testTag("activity_movement_metric"))
            SportMetric(sleepLabel, HealthConnectSupport.formatSleepDurationDisplay(sleep), Icons.Outlined.Bedtime, SleepAccent, Modifier.weight(1f).fillMaxHeight().testTag("activity_sleep_metric"))
        }
    }
}

@Composable
fun SportBars(days: List<SportGraphicDay>, selected: String?, onSelect: (String) -> Unit,
    values: List<Long?> = days.map { it.minutes }, color: Color = LeafGreen, unit: String = "min", tag: String = "sport_bars") {
    require(values.size == days.size)
    var ready by remember(days.firstOrNull()?.date) { mutableStateOf(false) }
    LaunchedEffect(days.firstOrNull()?.date) { ready = true }
    val maximum = (values.filterNotNull().maxOrNull() ?: 0L).coerceAtLeast(1)
    val grid = WarmBrown
    Text(if (values.all { it == null }) "$unit • brak zapisów" else "$unit • skala 0–$maximum", style = MaterialTheme.typography.labelSmall, color = GoldAccent)
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(143.dp).padding(top = 26.dp)) {
            (0..3).forEach { row -> val y = size.height * row / 3
                drawLine(grid.copy(alpha = .5f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            days.forEachIndexed { index, day ->
                val value = values[index]
                val height = mangoFloat(if (value == null || !ready && !LocalReducedMangoMotion.current) 0f else value.toFloat() / maximum)
                val chosen = selected == day.date.toString()
                Column(Modifier.weight(1f).clickable(enabled = !day.future) { onSelect(day.date.toString()) }
                    .semantics { contentDescription = "${day.date}: ${value?.let { "$it $unit" } ?: if (day.future) "dzień przyszły" else "brak zapisu"}" }
                    .testTag("${tag}_${day.date}"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value?.toString() ?: "—", style = MaterialTheme.typography.labelSmall, color = if (chosen) OrangeInk else GoldAccent)
                    Box(Modifier.fillMaxWidth().height(120.dp).padding(top = 6.dp), contentAlignment = Alignment.BottomCenter) {
                        if (value != null && value > 0) Box(Modifier.width(22.dp).height((height * 112).dp)
                            .background(if (chosen) JuicyOrange else color, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                        else Box(Modifier.width(16.dp).height(3.dp).background(if (day.future) WarmBrown else GoldAccent, RoundedCornerShape(2.dp)))
                    }
                    Text(SportsGraphicsSupport.weekday(day.date), style = MaterialTheme.typography.labelMedium,
                        color = if (chosen) OrangeInk else GoldAccent, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
fun SportActivityChart(days: List<SportGraphicDay>) {
    var selected by rememberSaveable(days.firstOrNull()?.date.toString()) { mutableStateOf<String?>(null) }
    MangoCard(Modifier.fillMaxWidth().sportAppear().mangoExpand()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SportHeading(Icons.Outlined.BarChart, "Aktywność w tygodniu")
        Text("${days.firstOrNull()?.date} – ${days.lastOrNull()?.date}", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
        SportBars(days, selected, { selected = it })
        Text("Dotknij dnia, aby zobaczyć trening. — oznacza brak zapisanej sesji, nie brak ruchu.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
        days.find { it.date.toString() == selected }?.let { day ->
            HorizontalDivider(color = WarmBrown)
            Text("${day.date} • ${day.minutes?.let { "$it min" } ?: "brak zapisanych sesji"}", style = MaterialTheme.typography.titleSmall)
            day.sessions.forEach { session ->
                Text("${session.title} • ${java.time.Duration.between(java.time.Instant.parse(session.start), java.time.Instant.parse(session.end)).toMinutes()} min • ${sportTime(java.time.Instant.parse(session.start).toEpochMilli())}", style = MaterialTheme.typography.bodyMedium)
                Text("Źródło: ${session.source}", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
            }
        }
    } }
}

@Composable
fun SportSleepChart(days: List<SportGraphicDay>) {
    var selected by rememberSaveable(days.firstOrNull()?.date.toString()) { mutableStateOf<String?>(null) }
    val values = days.map { it.sleepMinutes }
    val maxHours = maxOf(8, kotlin.math.ceil((values.filterNotNull().maxOrNull() ?: 0L) / 60.0).toInt())
    val blue = SleepAccent; val grid = WarmBrown
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ready = true }
    val progress = mangoFloat(if (ready || LocalReducedMangoMotion.current) 1f else 0f)
    val mean = values.filterNotNull().takeIf { it.isNotEmpty() }?.average()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Średni sen: ${SportsGraphicsSupport.sleepLabel(mean?.toLong())} • ${values.count { it != null }} zapisów", style = MaterialTheme.typography.bodyMedium)
        Text("godziny • skala 0–$maxHours", style = MaterialTheme.typography.labelSmall, color = GoldAccent)
        Canvas(Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = "Wykres snu z ${values.count { it != null }} dat; brakujące noce nie są łączone linią." }) {
            fun point(index: Int, minutes: Long) = Offset(index.toFloat() / (days.size - 1).coerceAtLeast(1) * size.width,
                size.height * (1 - minutes.toFloat() / (maxHours * 60) * progress))
            (0..2).forEach { row -> val y = size.height * row / 2
                drawLine(grid.copy(alpha = .5f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
            values.forEachIndexed { i, value -> if (value != null) {
                if (i > 0) values[i - 1]?.let { prior -> drawLine(blue, point(i - 1, prior), point(i, value), 2.dp.toPx(), StrokeCap.Round) }
                drawCircle(blue, if (selected == days[i].date.toString()) 6.dp.toPx() else 4.dp.toPx(), point(i, value))
            } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { day -> TextButton(onClick = { selected = day.date.toString() }, enabled = !day.future,
                contentPadding = PaddingValues(0.dp), modifier = Modifier.weight(1f).testTag("sleep_day_${day.date}")) {
                Text(SportsGraphicsSupport.weekday(day.date), style = MaterialTheme.typography.labelMedium,
                    color = if (selected == day.date.toString()) OrangeInk else GoldAccent)
            } }
        }
        days.find { it.date.toString() == selected }?.let { Text("${it.date}: ${SportsGraphicsSupport.sleepLabel(it.sleepMinutes)}", modifier = Modifier.testTag("sleep_selected")) }
        Text("— brak odczytu. Sen przypisujemy do daty pobudki; przerwy w danych pozostają przerwami na wykresie.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
    }
}

@Composable
fun RatingDots(label: String, rating: Double?, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label: ${rating?.let { String.format(java.util.Locale.getDefault(), "%.1f / 5", it) } ?: "brak wpisów"}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { dot -> Box(Modifier.size(10.dp).background(if (rating != null && dot <= rating.roundToInt()) color else WarmBrown, RoundedCornerShape(5.dp))) }
        }
    }
}

@Composable
fun SportGoalProgress(data: SportsData, summary: ActivitySummary, completed: ActivitySummary, previous: ActivitySummary, monday: Boolean) {
    val progress = ActivityRecoverySupport.progress(data, summary)
    val ratio = mangoFloat((progress.first.toFloat() / progress.second.coerceAtLeast(1)).coerceIn(0f, 1f))
    SportHeading(Icons.Outlined.Flag, "Cel tygodniowy")
    Text("${progress.first} / ${progress.second} ${ActivityRecoverySupport.unit(data)}", style = MaterialTheme.typography.headlineSmall)
    LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(10.dp), color = LeafGreen, trackColor = WarmBrown)
    if (monday) Text("Porównanie pojawi się po pierwszym zakończonym dniu.", style = MaterialTheme.typography.bodySmall)
    else Text("Te same zakończone dni: ${completed.minutes} min teraz • ${previous.minutes} min tydzień wcześniej.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
    Text("Minuty zapisanych sesji obejmują przerwy; nakładający się czas liczymy raz.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
}
