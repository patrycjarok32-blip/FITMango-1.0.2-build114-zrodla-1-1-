package com.example.ui.screens

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
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.*
import java.time.LocalDate
import kotlin.math.abs

/** Honors Android's animator scale and touch-exploration accessibility preference. */
@Composable
internal fun progressMotionEnabled(): Boolean {
    val context = LocalContext.current
    val scale = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    return scale > 0f
}

@Composable
fun WeightTrendChart(points: List<WeightPoint>) {
    var selected by remember(points) { mutableStateOf<WeightPoint?>(null) }
    if (points.size < 2) { Text("Dodaj pomiary z co najmniej dwóch dat, aby zobaczyć wykres."); return }
    val smooth = remember(points) { EffectsSupport.smoothed(points) }
    val motion = progressMotionEnabled() && !LocalReducedMangoMotion.current
    val lineReveal = remember(points) { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(points, motion) {
        if (!motion) lineReveal.snapTo(1f)
        else {
            lineReveal.snapTo(0f)
            lineReveal.animateTo(1f, tween(650))
        }
    }
    val drawFraction = lineReveal.value
    val lo = points.minOf { it.kg } - .5; val hi = points.maxOf { it.kg } + .5
    val from = LocalDate.parse(points.first().date).toEpochDay(); val to = LocalDate.parse(points.last().date).toEpochDay()
    val rawColor = MangoCoral; val trendColor = MaterialTheme.colorScheme.secondary; val axis = MaterialTheme.colorScheme.outline
    val values = points.map { it.kg }
    val averageWeight = values.average()
    val minWeight = values.minOrNull() ?: 0.0
    val maxWeight = values.maxOrNull() ?: 0.0
    val weightChange = points.last().kg - points.first().kg
    val changeLabel = String.format(
        java.util.Locale.getDefault(), "%+.1f kg", weightChange
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("kg • ${String.format(java.util.Locale.getDefault(), "%.1f–%.1f", lo, hi)}",
            style = MaterialTheme.typography.bodySmall)
        Text("Zmiana w okresie: $changeLabel", color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Średnia: ${String.format(java.util.Locale.getDefault(), "%.2f", averageWeight)} kg",
            color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        Text("Min–max: ${String.format(java.util.Locale.getDefault(), "%.1f–%.1f", minWeight, maxWeight)} kg",
            color = GoldAccent, style = MaterialTheme.typography.bodySmall)
    }
    Text("Liczba pomiarów: ${points.size} • zakres: ${points.first().date} – ${points.last().date}",
        style = MaterialTheme.typography.labelSmall)
    Canvas(Modifier.fillMaxWidth().height(170.dp).padding(8.dp)
        .semantics { contentDescription = "Wykres wagi: ${points.size} pomiarów. Średnia ${String.format(java.util.Locale.getDefault(), "%.1f", averageWeight)} kilogramów. Zmiana $changeLabel. Zakres ${points.first().date} do ${points.last().date}." }
        .testTag("effects_weight_chart").pointerInput(points) {
        detectTapGestures { tap ->
            selected = points.minByOrNull { abs((LocalDate.parse(it.date).toEpochDay() - from).toFloat() / (to - from).coerceAtLeast(1) * size.width - tap.x) }
        }
    }) {
        fun position(p: WeightPoint) = Offset(((LocalDate.parse(p.date).toEpochDay() - from).toDouble() / (to - from).coerceAtLeast(1) * size.width).toFloat(), ((hi - p.kg) / (hi - lo) * size.height).toFloat())
        drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        fun animatedSegment(a: WeightPoint, b: WeightPoint, color: androidx.compose.ui.graphics.Color, thickness: Float) {
            val aX = position(a).x
            val bX = position(b).x
            val revealX = size.width * drawFraction
            if (aX <= revealX) {
                val fraction = if (bX <= aX) 1f else ((revealX - aX) / (bX - aX)).coerceIn(0f, 1f)
                val start = position(a)
                val end = position(b)
                drawLine(color, start, Offset(start.x + (end.x - start.x) * fraction, start.y + (end.y - start.y) * fraction), thickness)
            }
        }
        points.zipWithNext().forEach { (a, b) -> animatedSegment(a, b, rawColor.copy(alpha = .4f), 1.dp.toPx()) }
        points.filter { position(it).x <= size.width * drawFraction }.forEach { drawCircle(rawColor, 3.dp.toPx(), position(it)) }
        smooth.zipWithNext().forEach { (a, b) -> animatedSegment(a, b, trendColor, 3.dp.toPx()) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(points.first().date, style = MaterialTheme.typography.bodySmall); Text(points.last().date, style = MaterialTheme.typography.bodySmall) }
    Text("● pomiar • zielona linia: średnia dostępnych pomiarów z ostatnich 7 dni (min. 2). Dotknij punktu.", style = MaterialTheme.typography.bodySmall)
    selected?.let { Text("${it.date}: ${it.kg} kg", color = LeafGreen, style = MaterialTheme.typography.titleSmall) }
}

@Composable
fun EffectsScreen(viewModel: DietaViewModel, onBack: () -> Unit, onProfile: () -> Unit) {
    val context = LocalContext.current
    val motion = progressMotionEnabled()
    val weights by viewModel.weightLogs.collectAsStateWithLifecycle()
    val measurements by viewModel.bodyMeasurements.collectAsStateWithLifecycle()
    val days by viewModel.habits.days.collectAsStateWithLifecycle()
    val confirmations by viewModel.healthConnect.weeklyEnergy.confirmations.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val revision by viewModel.usability.revision.collectAsStateWithLifecycle()
    val aiBusy by viewModel.effectsAiBusy.collectAsStateWithLifecycle()
    val aiText by viewModel.effectsAiResult.collectAsStateWithLifecycle()
    val health by viewModel.healthConnect.healthConnectSync.collectAsStateWithLifecycle()
    val date by viewModel.currentDate.collectAsStateWithLifecycle()
    val today = LocalDate.parse(date)
    var range by rememberSaveable { mutableIntStateOf(30) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableIntStateOf(0) }
    var adjustment by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showGoalPicker by rememberSaveable { mutableStateOf(false) }
    val settings by produceState(EffectsSettings(), revision) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { EffectsSupport.read(context) }.getOrDefault(EffectsSettings()) } }
    val sports by produceState(SportsData(), revision) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { SportsSupport.read(context) }.getOrDefault(SportsData()) } }
    val energySettings = remember(revision) { runCatching { EnergyCalculatorSupport.read(context) }.getOrNull() }
    val scaleMeasurements = remember(revision) { runCatching { ScaleMeasurementsSupport.read(context) }.getOrDefault(emptyList()) }
    val report by produceState<EffectsReport?>(null, weights, measurements, days, confirmations, profile, sports, settings, date, range, energySettings, scaleMeasurements) {
        value = null
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { EffectsSupport.analyze(ScaleMeasurementsSupport.withScaleWeights(weights, scaleMeasurements), measurements, days, confirmations, profile, sports, settings, today, range, energySettings) }
    }
    var low by rememberSaveable { mutableStateOf("") }; var high by rememberSaveable { mutableStateOf("") }
    var hunger by rememberSaveable(date) { mutableIntStateOf(3) }; var energy by rememberSaveable(date) { mutableIntStateOf(3) }
    var sleep by rememberSaveable(date) { mutableStateOf("") }
    LaunchedEffect(settings.low, settings.high) { low = settings.low?.toString().orEmpty(); high = settings.high?.toString().orEmpty() }
    LaunchedEffect(settings.wellbeing, date) { settings.wellbeing.find { it.date == date }?.let { hunger = it.hunger; energy = it.energy; sleep = it.sleepHours?.toString().orEmpty() } }
    fun completed(error: String?) { busy = false; message = error; if (error == null) feedback++ }
    fun n(value: Double?, unit: String) = value?.let { String.format(java.util.Locale.getDefault(), "%.2f %s", it, unit) } ?: "brak danych"
    LazyColumn(Modifier.fillMaxSize().background(DarkBackground).padding(horizontal = 16.dp), state = rememberLazyListState(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Postępy", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Wróć") }
            }
            SuccessFeedback(feedback, "Zapisano")
            message?.let { Text(it, color = ErrorInk) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(14, 30, 90).forEach { value -> FilterChip(range == value, { range = value }, label = { Text("$value dni") }) } }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Cel: ${profile?.goal ?: "nieustawiony"}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { showGoalPicker = !showGoalPicker }) { Text(if (showGoalPicker) "Zamknij" else "Zmień cel") }
            }
            if (showGoalPicker) {
                Column {
                    listOf("Redukcja", "Utrzymanie", "Budowanie masy").forEach { goal ->
                        FilterChip(profile?.goal == goal, {
                            busy = true
                            viewModel.setEffectsGoal(goal) { error ->
                                completed(error)
                                if (error == null) showGoalPicker = false
                            }
                        }, enabled = !busy, label = { Text(goal) })
                    }
                    Text("Zmiana celu kalorii wymaga osobnego zatwierdzenia.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        val r = report
        if (r != null) {
            item { MangoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Bilans wagi • $range dni", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Outlined.MonitorWeight, contentDescription = null, tint = LeafGreen, modifier = Modifier.size(18.dp)); Text("Zmiana wagi", style = MaterialTheme.typography.labelMedium) }
                            Text(if (r.points.size >= 2) String.format(java.util.Locale.getDefault(), "%+.1f kg", r.points.last().kg - r.points.first().kg) else "—",
                                style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Outlined.ShowChart, contentDescription = null, tint = LeafGreen, modifier = Modifier.size(18.dp)); Text("Trend tygodniowy", style = MaterialTheme.typography.labelMedium) }
                            Text(r.trendKgWeek?.let { String.format(java.util.Locale.getDefault(), "%+.2f kg", it) } ?: "—",
                                style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                Text(r.summary, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Outlined.Timeline, contentDescription = null, tint = LeafGreen); Text("Wykres wagi", style = MaterialTheme.typography.titleSmall) }
                WeightTrendChart(r.points)
                Text("Talia: ${n(r.waistChange, "cm")} • Pomiary: ${r.points.size}", style = MaterialTheme.typography.bodySmall)

            } } }
        item {
            var planExpanded by rememberSaveable { mutableStateOf(false) }
            val reducedMangoMotion = LocalReducedMangoMotion.current
            MangoCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).then(if (reducedMangoMotion) Modifier else Modifier.animateContentSize(animationSpec = tween(220)))) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Etapy redukcji", style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { planExpanded = !planExpanded }) {
                            Text(if (planExpanded) "Zwiń −" else "Szczegóły +")
                        }
                    }
                    if (planExpanded) {
                        ReductionPlanCard(settings.reductionPlan, today, busy, profile?.targetCalories, onProfile) { plan ->
                            busy = true
                            viewModel.saveEffectsSettings(settings.copy(reductionPlan = plan), ::completed)
                        }
                    }
                }
            }
        }
            item { CollapsibleMangoCard("Jakość danych", "effects_quality") {
                Text("Zakończone dni w okresie: ${r.periodDays}. Dni z jedzeniem: ${r.loggedDays}. Z aktualnym zatwierdzeniem: ${r.confirmedDays}. Pełny zapis potwierdzony przez Ciebie: ${r.completeDays}.")
                Text("Szacunkowe wartości składników: ${r.partialDays} dni. Pomiary wagi: ${r.points.size}. Dzisiejszy dzień jedzenia nie jest analizowany.")
                Text("Brak wpisu to brak danych. Każdy zatwierdzony dzień oznacza pełny zapis i wchodzi do średnich. Szacunki składników oznaczamy osobno; automatyczna korekta kalorii wymaga precyzyjnych danych.")
            } }
            item { CollapsibleMangoCard("Zapotrzebowanie i cel", "effects_energy") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Utrzymanie z profilu: ${r.maintenance?.toInt()?.let { "$it kcal/dzień" } ?: "uzupełnij Profil"}.")
                Text("Cel jedzenia: ${profile?.targetCalories ?: "brak danych"} kcal. Średnia ze wszystkich zatwierdzonych dni (także zaakceptowanych szacunków): ${r.meanCalories?.toInt() ?: "brak danych"} kcal; białko: ${r.meanProtein?.toInt() ?: "brak danych"} g.")
                Text(EnergyCalculatorSupport.base(profile, energySettings)?.explanation ?: "Uzupełnij poprawny Profil; indywidualne cele ustaw ręcznie.", style = MaterialTheme.typography.bodySmall)
                r.referenceIntake?.let { Text("Przy stabilnym trendzie średnia spożycia wyniosła ${it.toInt()} kcal. To punkt odniesienia z zapisów, nie pomiar Twojego metabolizmu.") }
                Text(r.reasons)
                r.proposedDelta?.let { delta ->
                    val target = profile?.targetCalories ?: 0
                    Button(enabled = !busy, onClick = { adjustment = target to delta }, modifier = Modifier.testTag("effects_review_adjustment")) { Text("Rozważ: $target → ${target + delta} kcal") }
                }
                }
            } }
            item { CollapsibleMangoCard("Raport tygodniowy", "effects_week") { Text(r.weeklyText) } }
            item { CollapsibleMangoCard("Trening i samopoczucie", "effects_wellbeing") {
                Text(r.strengthText)
                Text("Pokazujemy zapisane wyniki; porównaj także powtórzenia i technikę ćwiczenia.", style = MaterialTheme.typography.bodySmall)
                Text(r.wellbeingText)
                Text("Zapis na dziś ($date)")
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Głód $hunger/5", Modifier.weight(1f)); TextButton(onClick = { hunger = (hunger - 1).coerceAtLeast(1) }) { Text("−") }; TextButton(onClick = { hunger = (hunger + 1).coerceAtMost(5) }) { Text("+") } }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Energia $energy/5", Modifier.weight(1f)); TextButton(onClick = { energy = (energy - 1).coerceAtLeast(1) }) { Text("−") }; TextButton(onClick = { energy = (energy + 1).coerceAtMost(5) }) { Text("+") } }
                OutlinedTextField(sleep, { sleep = it.take(6) }, label = { Text("Sen w godzinach (opcjonalnie)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                health?.sleepDurationMinutes?.takeIf { it > 0 }?.let { minutes -> TextButton(onClick = { sleep = (minutes / 60.0).toString().take(5) }) { Text("Użyj ostatniego odczytu snu (${minutes / 60} h ${minutes % 60} min)") } }
                val sleepValue = sleep.replace(',', '.').toDoubleOrNull()
                Button(enabled = !busy && (sleep.isBlank() || sleepValue != null && sleepValue.isFinite() && sleepValue in 0.0..24.0), onClick = {
                    busy = true
                    viewModel.saveEffectsSettings(settings.copy(wellbeing = (settings.wellbeing.filterNot { it.date == date } + WellbeingDay(date, hunger, energy, sleepValue)).sortedByDescending { it.date }.take(730)), ::completed)
                }) { Text("Zapisz samopoczucie ✓") }
            } }
        }
        item { CollapsibleMangoCard("Przedział wagi na utrzymanie", "effects_band") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(low, { low = it.take(6) }, label = { Text("Od kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(high, { high = it.take(6) }, label = { Text("Do kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            val l = low.replace(',', '.').toDoubleOrNull(); val h = high.replace(',', '.').toDoubleOrNull()
            Button(enabled = !busy && l != null && h != null && l.isFinite() && h.isFinite() && l in 30.0..400.0 && h > l && h <= 400, onClick = {
                busy = true; viewModel.saveEffectsSettings(settings.copy(low = l, high = h), ::completed)
            }) { Text("Zapisz przedział") }
        } }
        item { CollapsibleMangoCard("Opcjonalne wyjaśnienie AI", "effects_ai") {
            Text("Wyślesz do Gemini podsumowanie trendu, jedzenia, treningów i samopoczucia. Raport lokalny działa bez AI. Wyjaśnienie opisuje dane z chwili uruchomienia; po nowych wpisach wygeneruj je ponownie.")
            TextButton(onClick = onProfile) { Text("Ustawienia AI w Profilu") }
            val aiPulse by animateFloatAsState(if (aiBusy && motion) 0.72f else 1f, animationSpec = tween(if (motion) 450 else 0), label = "ai_busy_pulse")
            Button(enabled = !aiBusy && report != null, onClick = { viewModel.runEffectsAiAnalysis(range) }) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp).graphicsLayer { alpha = aiPulse })
                Spacer(Modifier.width(8.dp))
                Text(if (aiBusy) "Przygotowywanie…" else "Wyjaśnij moje efekty")
            }
            if (aiBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            AnimatedVisibility(visible = aiText.isNotBlank(), enter = if (motion) fadeIn(tween(280)) else fadeIn(tween(0)), exit = fadeOut(tween(0))) { Text(aiText) }
        } }
    }
    adjustment?.let { (target, delta) -> AlertDialog(onDismissRequest = { if (!busy) adjustment = null }, title = { Text("Zatwierdzić korektę?") },
        text = { Text("Cel: $target → ${target + delta} kcal. Białko i tłuszcze zachowujemy; węglowodany dopasujemy do nowego celu. Kolejna propozycja pojawi się najwcześniej po 21 dniach nowych obserwacji.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { busy = true; viewModel.applyEffectsAdjustment(target, delta, range) { completed(it); if (it == null) adjustment = null } }) { Text("Zatwierdź") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { adjustment = null }) { Text("Odrzuć") } }) }
}
