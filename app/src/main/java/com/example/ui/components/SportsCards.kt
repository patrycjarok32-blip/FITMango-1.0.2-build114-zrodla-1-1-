package com.example.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.viewmodel.*

@Composable
fun ActivityRing(days: Int, goal: Int, modifier: Modifier = Modifier, unit: String = "dni") {
    val progress = mangoFloat((days / goal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f))
    val accent = LeafGreen; val track = MutedSurface2
    Box(modifier.size(84.dp).testTag("sport_activity_ring"), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            val style = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, -90f, 360f, false, style = style)
            if (progress > 0) drawArc(accent, -90f, 360f * progress, false, style = style)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$days/$goal", style = if (maxOf(days, goal) >= 100) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge)
            Text(unit, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SportsOverviewCard(onOpen: () -> Unit, sports: SportsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(), today: java.time.LocalDate = java.time.LocalDate.now(), current: CurrentActivity? = null) {
    val data by sports.data.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { sports.refreshIfGranted() }
    val summary = remember(data, today) { ActivityRecoverySupport.week(data, today, java.time.Instant.now()) }
    val day = data.daily.find { it.date == today.toString() }
    val daily = remember(data, today) { ActivityRecoverySupport.summary(data, today, today, java.time.Instant.now()) }
    MangoCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SportHeading(androidx.compose.material.icons.Icons.Outlined.DirectionsWalk, "Aktywność i regeneracja")
            SportMetricRow(if (current != null) current.steps else day?.steps, daily.minutes,
                if (current != null) current.sleepMinutes else day?.sleepMinutes, sleepLabel = current?.sleepLabel ?: "Sen po pobudce dziś")
            val progress = ActivityRecoverySupport.progress(data, summary)
            val ratio = mangoFloat((progress.first.toFloat() / progress.second.coerceAtLeast(1)).coerceIn(0f, 1f))
            Text("Cel tygodnia: ${progress.first} / ${progress.second} ${ActivityRecoverySupport.unit(data)}", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { ratio }, color = LeafGreen, trackColor = WarmBrown, modifier = Modifier.fillMaxWidth())
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("open_sport")) { Text("Szczegóły i raport →") }
        }
    }
}

@Composable
fun RestTimerCard() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("FITMangoRestTimer", 0) }
    var timer by remember { mutableStateOf(RestTimer(prefs.getLong("deadline", 0), prefs.getLong("paused", 0))) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val haptic = LocalHapticFeedback.current
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    fun update(value: RestTimer) {
        prefs.edit().putLong("deadline", value.deadline).putLong("paused", value.paused).putBoolean("notified", false).apply()
        timer = value; now = System.currentTimeMillis()
    }
    DisposableEffect(timer) {
        val handler = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                now = System.currentTimeMillis()
                if (timer.seconds(now) > 0) handler.postDelayed(this, 1000)
                else if (timer.deadline > 0 && !prefs.getBoolean("notified", false)) {
                    prefs.edit().putBoolean("notified", true).apply()
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
        if (timer.deadline > 0) tick.run()
        onDispose { handler.removeCallbacks(tick) }
    }
    MangoCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val seconds = timer.seconds(now)
            Text("Przerwa między seriami • ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("rest_timer"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 90, 120).forEach { value ->
                    OutlinedButton(onClick = { update(RestTimer.start(value, System.currentTimeMillis())) }, modifier = Modifier.weight(1f)) { Text("${value}s") }
                }
            }
            Row {
                TextButton(enabled = seconds > 0, onClick = { update(if (timer.deadline > 0) timer.pause(System.currentTimeMillis()) else timer.resume(System.currentTimeMillis())) }) { Text(if (timer.deadline > 0) "Pauza" else "Wznów") }
                TextButton(onClick = { update(RestTimer()) }) { Text("Wyzeruj") }
            }
            Text("Odliczanie jest zachowane po wyjściu. Sygnał działa w otwartym ekranie Sport.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
        }
    }
}
