package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.viewmodel.*
import java.time.LocalDate
import java.time.Instant
import kotlin.math.roundToInt

@Composable
fun QuickCheckinCard(viewModel: DietaViewModel, sports: SportsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val revision by viewModel.usability.revision.collectAsStateWithLifecycle()
    val date by viewModel.currentDate.collectAsStateWithLifecycle()
    val data by sports.data.collectAsStateWithLifecycle()
    val loaded by produceState<Result<EffectsSettings>?>(null, revision) {
        value = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { EffectsSupport.read(context) } }
    }
    val note = data.recovery.find { it.date == date }
    val old = loaded?.getOrNull()?.wellbeing?.find { it.date == date }
    var energy by rememberSaveable(date, note?.energy, old?.energy) { mutableIntStateOf(note?.energy ?: old?.energy ?: 3) }
    var fatigue by rememberSaveable(date, note?.fatigue) { mutableIntStateOf(note?.fatigue ?: 3) }
    var hunger by rememberSaveable(date, old?.hunger) { mutableIntStateOf(old?.hunger ?: 3) }
    var busy by remember { mutableStateOf(false) }
    var event by remember { mutableIntStateOf(0) }
    var savedPulse by remember { mutableStateOf(false) }
    LaunchedEffect(event) { if (event > 0) { savedPulse = true; kotlinx.coroutines.delay(1500); savedPulse = false } }
    var error by remember { mutableStateOf<String?>(null) }
    MangoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SportHeading(Icons.Outlined.SentimentSatisfiedAlt, "Jak się dziś czujesz?")
        Text("Dzisiaj • 1 = mało, 5 = dużo", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
        @Composable fun rating(label: String, value: Int, changed: (Int) -> Unit) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { (1..5).forEach { n ->
                FilterChip(selected = value == n, onClick = { changed(n) }, enabled = !busy && loaded?.isSuccess == true,
                    label = { Text(n.toString()) }, modifier = Modifier.semantics { contentDescription = "$label: $n z 5" })
            } }
        }
        rating("Energia", energy) { energy = it }; rating("Zmęczenie", fatigue) { fatigue = it }; rating("Głód", hunger) { hunger = it }
        Button(enabled = !busy && !savedPulse && loaded?.isSuccess == true, onClick = {
            busy = true; error = null
            viewModel.saveQuickWellbeing(energy, fatigue, hunger) { message -> busy = false; error = message; if (message == null) event++ }
        }, modifier = Modifier.fillMaxWidth().testTag("quick_checkin_save")) { Text(if (busy) "Zapisywanie…" else if (savedPulse) "Zapisano ✓" else if (note != null || old != null) "Zaktualizuj dzisiejszy wpis" else "Zapisz samopoczucie") }
        SuccessFeedback(event, "Zapisano samopoczucie", "quick_checkin_saved")
        if (loaded?.isFailure == true) Text("Nie udało się odczytać samopoczucia. Sprawdź zapisane dane w Profilu.", color = ErrorInk)
        error?.let { Text(it, color = ErrorInk) }
    } }
}

@Composable
fun PersonalProgressCards(viewModel: DietaViewModel, data: SportsData, today: LocalDate) {
    val weights by viewModel.weightLogs.collectAsStateWithLifecycle()
    val food by viewModel.habits.days.collectAsStateWithLifecycle()
    val approvals by viewModel.healthConnect.weeklyEnergy.confirmations.collectAsStateWithLifecycle()
    val revision by viewModel.usability.revision.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val details by produceState<Result<Pair<List<PersonalRecord>, WeekComparison>>?>(null, data, food, approvals, weights, today, revision) {
        value = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val merged = ScaleMeasurementsSupport.withScaleWeights(weights, ScaleMeasurementsSupport.read(context))
            PersonalProgressSupport.records(data, Instant.now()) to PersonalProgressSupport.compare(data, food, approvals, merged, today, Instant.now())
        } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Twoje postępy", style = MaterialTheme.typography.headlineSmall)
        if (details == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (details?.isFailure == true) Text("Nie udało się odczytać danych do porównania. Zapisane wpisy pozostają zachowane.", color = ErrorInk)
        details?.getOrNull()?.let { (records, comparison) ->
            MangoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SportHeading(Icons.Outlined.EmojiEvents, "Osobiste rekordy", OrangeInk)
                Text("Z dostępnych zapisów. Historia zegarka może obejmować tylko ostatnie 30 dni.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                if (records.isEmpty()) Text("Rekordy pojawią się po zapisaniu aktywności.")
                records.forEach { record ->
                    Text(record.title, style = MaterialTheme.typography.titleSmall)
                    Text("${String.format(java.util.Locale.getDefault(), if (record.unit == "dni") "%.0f" else "%.1f", record.value)} ${record.unit}", style = MaterialTheme.typography.headlineSmall, color = LeafGreen)
                    Text("${record.date} • ${record.source}", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                }
            } }
            WeekComparisonCard(comparison)
        }
    }
}

@Composable
fun WeekComparisonCard(comparison: WeekComparison) {
    val a = comparison.older; val b = comparison.newer
    fun date(d: LocalDate) = d.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"))
    fun avg(value: Double?, suffix: String, days: Int) = value?.let { "${it.roundToInt()} $suffix\n$days dni" } ?: "—\nbrak zapisów"
    MangoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SportHeading(Icons.Outlined.CompareArrows, "Porównanie tygodni")
        Text("Dwa ostatnie zakończone tygodnie pn.–nd.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
        @Composable fun row(label: String, left: String, right: String) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(left, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(right, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = WarmBrown)
        }
        row("Okres", "${date(a.from)}–${date(a.until)}\n${a.from.year}", "${date(b.from)}–${date(b.until)}\n${b.from.year}")
        row("Zapisany ruch", if (a.activity.sessions == 0) "—\nbrak sesji" else "${a.activity.minutes} min\n${a.activity.activeDays} dni", if (b.activity.sessions == 0) "—\nbrak sesji" else "${b.activity.minutes} min\n${b.activity.activeDays} dni")
        row("Średnie jedzenie", avg(a.calories, "kcal", a.approvedDays), avg(b.calories, "kcal", b.approvedDays))
        row("Średnie białko", avg(a.protein, "g", a.approvedDays), avg(b.protein, "g", b.approvedDays))
        row("Średni sen", "${SportsGraphicsSupport.sleepLabel(a.sleepMinutes?.toLong())}\n${a.sleepDays} zapisów", "${SportsGraphicsSupport.sleepLabel(b.sleepMinutes?.toLong())}\n${b.sleepDays} zapisów")
        fun weight(value: Double?, count: Int) = value?.let { String.format(java.util.Locale.getDefault(), "%.2f kg\n%d dni", it, count) } ?: "—\nbrak pomiarów"
        row("Średnia waga", weight(a.weight, a.weightDays), weight(b.weight, b.weightDays))
        Text("Jedzenie: tylko zatwierdzone dni, również zaakceptowane szacunki. Sprawdź liczbę dni w obu tygodniach; brak zapisu nie jest zerem. Średnia wagi nie określa zmiany tłuszczu.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
    } }
}
