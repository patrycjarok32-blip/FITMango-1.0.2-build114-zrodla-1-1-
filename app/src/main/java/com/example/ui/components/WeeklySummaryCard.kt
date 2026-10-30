package com.example.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.viewmodel.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class WeeklyMetric(val label: String, val unit: String) {
    ENERGY("Kalorie", "kcal"), PROTEIN("Białko", "g"), WATER("Napoje", "ml"), PRODUCE("Warzywa i owoce", "g")
}
private val WeeklyOrange = Color(0xFFFFA21B)
private val WeeklyGreen = Color(0xFF00C99A)

@Composable
fun WeeklySummaryCard(viewModel: DietaViewModel) {
    val days by viewModel.habits.days.collectAsState()
    val date by viewModel.currentDate.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val weights by viewModel.weightLogs.collectAsState()
    val coordinator = viewModel.healthConnect
    val energy by coordinator.weeklyEnergy.energy.collectAsState()
    val confirmations by coordinator.weeklyEnergy.confirmations.collectAsState()
    val loading by coordinator.weeklyEnergyLoading.collectAsState()
    val syncMessage by coordinator.weeklyEnergyMessage.collectAsState()
    val sync by coordinator.healthConnectSync.collectAsState()
    var offset by remember { mutableIntStateOf(0) }
    var metric by remember { mutableStateOf(WeeklyMetric.ENERGY) }
    var selected by remember { mutableStateOf<String?>(null) }
    var confirmOpen by remember { mutableStateOf(false) }
    var fullData by remember { mutableStateOf(false) }
    var confirmError by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("FITMangoHabits", android.content.Context.MODE_PRIVATE) }
    var profileEnergy by remember { mutableStateOf(preferences.getBoolean("weeklyUseMaintenance", true)) }
    val energyRevision by viewModel.usability.revision.collectAsState()
    val energySettings = remember(energyRevision) { runCatching { EnergyCalculatorSupport.read(context) }.getOrNull() }
    val maintenance = WeeklyEnergySupport.maintenanceCalories(profile, energySettings)
    val today = LocalDate.parse(date)
    val end = today.plusWeeks(offset.toLong())
    val start = end.minusDays(6)
    val dates = (0L..6L).map { start.plusDays(it) }
    val analysisEnergy = if (profileEnergy) dates.map { d -> DailyEnergy(d.toString(), maintenance, d < today, 0L) } else energy
    val analysis = WeeklyEnergySupport.analyze(days, analysisEnergy, confirmations, start, end, today, if (profileEnergy) "profile" else "health")
    val expenditureLabel = if (profileEnergy) "Zapotrzebowanie szacowane" else "Spalone ogółem (Health Connect)"
    CollapsibleMangoCard("Podsumowanie tygodnia", "weekly_summary") {
        LaunchedEffect(start, end, sync?.lastSyncTimeMillis) { coordinator.loadWeeklyEnergy(start, end) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { offset--; selected = null }) { Text("← Wcześniej") }
            TextButton(enabled = offset < 0, onClick = { offset++; selected = null }) { Text("Później →") }
        }
        val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        Text("${start.format(fmt)} – ${end.format(fmt)}", style = MaterialTheme.typography.titleMedium)
        listOf(listOf(WeeklyMetric.ENERGY, WeeklyMetric.PROTEIN), listOf(WeeklyMetric.WATER, WeeklyMetric.PRODUCE)).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m -> FilterChip(selected = metric == m, onClick = { metric = m },
                    label = { Text(m.label) }, modifier = Modifier.weight(1f)) }
            }
        }
        val foodValues = dates.map { d -> days.find { it.date == d.toString() }?.let { day ->
            when (metric) {
                WeeklyMetric.ENERGY -> day.calories.toFloat().takeIf { day.meals.isNotEmpty() }
                WeeklyMetric.PROTEIN -> day.protein.toFloat().takeIf { day.meals.isNotEmpty() }
                WeeklyMetric.WATER -> day.drinksMl.toFloat().takeIf { day.drinksMl > 0 }
                WeeklyMetric.PRODUCE -> day.produceGrams.toFloat().takeIf { day.produce.any { it.category in listOf("fruit", "vegetable") && it.grams != null } }
            }
        } }
        if (metric == WeeklyMetric.ENERGY) {
            Text("Podstawa bilansu", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = profileEnergy, onClick = { profileEnergy = true; preferences.edit().putBoolean("weeklyUseMaintenance", true).apply() }, label = { Text("Zapotrzebowanie") }, modifier = Modifier.weight(1f))
                FilterChip(selected = !profileEnergy, onClick = { profileEnergy = false; preferences.edit().putBoolean("weeklyUseMaintenance", false).apply() }, label = { Text("Health Connect") }, modifier = Modifier.weight(1f))
            }
        }
        val burned = if (metric == WeeklyMetric.ENERGY) dates.map { d -> analysisEnergy.find { it.date == d.toString() }?.totalKcal?.toFloat() } else List(7) { null }
        val goal = when (metric) {
            WeeklyMetric.ENERGY -> profile?.targetCalories?.toFloat()
            WeeklyMetric.PROTEIN -> profile?.targetProtein?.toFloat()?.takeIf { it > 0 }
            WeeklyMetric.WATER -> days.find { it.date == date }?.waterGoal?.toFloat()
            WeeklyMetric.PRODUCE -> days.find { it.date == date }?.produceGoal?.toFloat()
        }
        val chartMax = (foodValues.filterNotNull() + burned.filterNotNull() + listOfNotNull(goal)).maxOrNull()?.coerceAtLeast(1f)?.times(1.15f) ?: 1f
        Text("${metric.unit} • skala do ${chartMax.roundToInt()}", style = MaterialTheme.typography.bodySmall)
        WeeklyBars(dates, foodValues, burned, metric == WeeklyMetric.ENERGY, goal, chartMax, metric.unit, selected) { selected = it }
        if (metric == WeeklyMetric.ENERGY) Text("🟠 Zjedzone   🟢 $expenditureLabel", style = MaterialTheme.typography.bodySmall)
        Text("— brak wpisu; 0 = zapisane zero. Linia: aktualny cel ${goal?.roundToInt()?.toString() ?: "nieustawiony"} ${if (goal != null) metric.unit else ""}. Cele mogły wcześniej być inne.", style = MaterialTheme.typography.bodySmall)
        if (today >= start && today <= end) Text("Dziś w toku — nie uwzględniamy tego dnia w analizie.", style = MaterialTheme.typography.bodySmall)
        val present = foodValues.filterNotNull()
        if (present.isNotEmpty()) Text("Średnia z ${present.size} dni z wpisami: ${(present.sum() / present.size).roundToInt()} ${metric.unit}", style = MaterialTheme.typography.titleSmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        syncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        TextButton(enabled = !loading, onClick = { coordinator.loadWeeklyEnergy(start, end) }) { Text("Odśwież spalanie z Health Connect") }
        selected?.let { key ->
            val day = days.find { it.date == key }
            if (key == today.toString()) NutritionStatusBadge(NutritionStatus.IN_PROGRESS, "Dzień w toku")
            val e = analysisEnergy.find { it.date == key }
            val eligible = key in analysis.includedDates
            val confirmation = confirmations.find { it.date == key }
            val foodApproved = day != null && WeeklyEnergySupport.isFoodConfirmed(day, confirmation)
            HorizontalDivider()
            Text("${LocalDate.parse(key).format(fmt)}${if (key == date) " • w toku" else ""}", style = MaterialTheme.typography.titleMedium)
            Text("Zjedzone: ${day?.calories?.toString()?.takeIf { day.meals.isNotEmpty() } ?: "brak wpisów"} ${if (day?.meals?.isNotEmpty() == true) "kcal" else ""}")
            Text("$expenditureLabel: ${e?.totalKcal?.roundToInt()?.let { "$it kcal" } ?: "brak danych"}")
            if (!profileEnergy) e?.let { Text("Odczyt: ${java.time.Instant.ofEpochMilli(it.syncedAt).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))}", style = MaterialTheme.typography.bodySmall) }
            if (day != null) {
                Text("B: ${day.protein} g • W: ${day.carbs} g • T: ${day.fat} g\nNapoje: ${day.drinksMl} ml • Warzywa i owoce: ${day.produceGrams} g")
                day.meals.forEach { Text(if (foodApproved) it.removeSuffix(" • niepełne dane") else it, style = MaterialTheme.typography.bodySmall) }
            }
            if (foodApproved) TextButton(onClick = { coordinator.weeklyEnergy.reopen(key) }) { Text(if (day?.nutritionComplete == false) "Pełny dzień zatwierdzony ✓ • wartości szacunkowe • cofnij" else "Pełny dzień zatwierdzony ✓ • cofnij") }
            if (foodApproved && !eligible) Text("Posiłki pozostają zatwierdzone. Bilans wymaga dostępnego zapotrzebowania lub spalania dla zakończonego dnia.", style = MaterialTheme.typography.bodySmall)
            if (!foodApproved && LocalDate.parse(key) < today && day != null && day.meals.isNotEmpty() && e?.fullDayWindow == true && (e.totalKcal ?: 0.0) > 0) {
                OutlinedButton(onClick = { fullData = false; confirmError = null; confirmOpen = true }) { Text(if (day.nutritionComplete) "Potwierdź pełne dane dnia" else "Potwierdź pełny zapis • wartości szacunkowe") }
            } else if (!foodApproved && day?.nutritionComplete == false) Text("Niepełne kalorie składników. Dzień można zatwierdzić po jego zakończeniu; wynik będzie przybliżony.")
            else if (!foodApproved) Text(if (profileEnergy) "Analiza wymaga zakończonego dnia, zapisanych posiłków i poprawnie uzupełnionego profilu." else "Analiza wymaga zakończonego dnia, zapisanych posiłków i całkowitego spalania.", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Text("Bilans energii i trend wagi", style = MaterialTheme.typography.titleMedium)
        EnergyExplanationButton(viewModel, selected ?: analysis.includedDates.lastOrNull() ?: today.toString(), profileEnergy)
        Text("Zatwierdzenie dotyczy posiłków. Zmiana podstawy bilansu lub odświeżenie spalania nie cofa zatwierdzenia; wynik korzysta z aktualnych wartości.", style = MaterialTheme.typography.bodySmall)
        if (!profileEnergy && days.any { d -> d.date in analysis.includedDates && confirmations.find { it.date == d.date }?.let { c -> c.source != "health" || c.burnedKcal != analysisEnergy.find { it.date == d.date }?.totalKcal } == true })
            Text("Spalanie z Health Connect różni się od wartości przy zatwierdzeniu lub nie było wtedy sprawdzane. Porównaj pełne dni z aplikacją źródłową; bilans jest przybliżony.", style = MaterialTheme.typography.bodySmall)
        if (days.any { !it.nutritionComplete && it.date in analysis.includedDates }) Text("Analiza zawiera pełne, zatwierdzone dni z szacunkowymi wartościami składników. Kalorie i bilans są przybliżone; brakujące wartości mogą zmienić wynik deficytu lub nadwyżki.")
        Text(if (profileEnergy) "Zapotrzebowanie przed deficytem: ${maintenance?.roundToInt()?.toString() ?: "uzupełnij profil"} kcal/dzień. Cel jedzenia: ${profile?.targetCalories ?: 0} kcal."
            else "Bilans oparty na spalaniu ogółem z Health Connect, bez dodawania celu jedzenia ani BMR.", style = MaterialTheme.typography.bodySmall)
        Text(if (profileEnergy) (EnergyCalculatorSupport.base(profile, energySettings)?.explanation ?: "Uzupełnij poprawny Profil.") + " Szacunek z aktualnych ustawień, także dla wcześniejszych dni."
            else "Odczyt Health Connect może być niepełny. Potwierdź dane tylko po sprawdzeniu pełnego dnia w aplikacji źródłowej.", style = MaterialTheme.typography.bodySmall)
        Text("Potwierdzone dni w analizie: ${analysis.includedDates.size}/7", modifier = Modifier.testTag("weekly_energy_coverage"))
        if (analysis.includedDates.isEmpty()) Text(if (profileEnergy) "Dotknij zakończonego dnia na wykresie i potwierdź kompletność posiłków." else "Za mało danych. Dotknij dnia na wykresie i potwierdź kompletność posiłków oraz spalania.")
        else {
            val def = analysis.deficitKcal
            Text("${if (def >= 0) "Deficyt" else "Nadwyżka"}: ${abs(def).roundToInt()} kcal łącznie (${(abs(def) / analysis.includedDates.size).roundToInt()} kcal/dzień).", modifier = Modifier.testTag("weekly_energy_balance"))
            Text("Zjedzone: ${analysis.eatenKcal} kcal • $expenditureLabel: ${analysis.burnedKcal.roundToInt()} kcal", style = MaterialTheme.typography.bodySmall)
            val eq = analysis.energyEquivalentKg
            Text("Teoretyczny odpowiednik energii: ${String.format(Locale.getDefault(), "%+.2f", eq)} kg", style = MaterialTheme.typography.titleSmall)
            Text("To przybliżenie bilansu (7700 kcal/kg), nie prognoza zmiany wagi. Masa zależy także od wody, metabolizmu i dokładności pomiarów. Nie przeliczamy brakujących dni na cały tydzień.", style = MaterialTheme.typography.bodySmall)
        }
        val weightChange = WeeklyEnergySupport.weightChange(weights, start, end)
        Text(if (weightChange == null) "Rzeczywista zmiana wagi: potrzebne pomiary z co najmniej dwóch różnych dni tego tygodnia." else "Rzeczywista zmiana wagi: ${String.format(Locale.getDefault(), "%+.2f", weightChange)} kg (pierwszy → ostatni pomiar).", style = MaterialTheme.typography.bodySmall)
    }
    if (confirmOpen) AlertDialog(onDismissRequest = { confirmOpen = false }, title = { Text("Zatwierdzenie dnia") }, text = {
        Column {
            Text(if (profileEnergy) "Potwierdź wszystkie posiłki i kaloryczne napoje. Bilans użyje szacowanego zapotrzebowania z aktualnego profilu, nie celu redukcji i nie spalania z zegarka."
                else "Potwierdź, że zapisano wszystkie zjedzone posiłki i kaloryczne napoje, a całkowite spalanie w Health Connect obejmuje cały dzień. Sam koniec dnia nie potwierdza kompletności danych.")
            Row { Checkbox(fullData, { fullData = it }); Text(if (days.find { it.date == selected }?.nutritionComplete == false) "Potwierdzam pełny zapis posiłków i kalorycznych napojów. Akceptuję szacunkowe wartości odżywcze i przybliżony bilans." else "Potwierdzam kompletność danych", Modifier.padding(top = 12.dp)) }
            confirmError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = fullData, onClick = {
        val day = days.find { it.date == selected }
        val result = runCatching { requireNotNull(day); coordinator.weeklyEnergy.confirm(day, today, if (profileEnergy) requireNotNull(maintenance) else null, acceptPartial = !day.nutritionComplete) }
        if (result.isSuccess) confirmOpen = false else confirmError = "Dane zmieniły się lub są niepełne. Odśwież spalanie i spróbuj ponownie."
    }) { Text("Uwzględnij w analizie") } }, dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("Anuluj") } })
}

@Composable
private fun WeeklyBars(dates: List<LocalDate>, food: List<Float?>, burned: List<Float?>, paired: Boolean, goal: Float?, max: Float, unit: String, selected: String?, onSelect: (String) -> Unit) {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    // Finite transitions; follows the app's reduced-motion setting.
    val animatedFood = food.map { mangoFloat(it ?: 0f) }
    val animatedBurned = burned.map { mangoFloat(it ?: 0f) }
    Canvas(Modifier.fillMaxWidth().height(180.dp).pointerInput(dates) {
        detectTapGestures { point ->
            val index = (point.x / (size.width / 7f)).toInt().coerceIn(0, 6)
            onSelect(dates[index].toString())
        }
    }.testTag("weekly_summary_chart").semantics {
        contentDescription = dates.indices.joinToString("; ") { i -> "${dates[i]}: zapisane ${food[i]?.roundToInt() ?: "brak"} $unit, spalone ${burned[i]?.roundToInt() ?: "brak danych"}" }
    }) {
        val base = size.height - 8.dp.toPx()
        val top = 12.dp.toPx()
        val h = base - top
        drawLine(grid, Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
        goal?.let { drawLine(neutral.copy(alpha = 0.5f), Offset(0f, base - h * it / max), Offset(size.width, base - h * it / max), 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))) }
        val cell = size.width / 7
        dates.indices.forEach { i ->
            val x = cell * i + cell * 0.18f
            val bw = cell * if (paired) 0.25f else 0.48f
            fun mark(value: Float?, animated: Float, left: Float, color: Color) {
                if (value == null) drawLine(neutral, Offset(left, base - 5.dp.toPx()), Offset(left + bw, base - 5.dp.toPx()), 2.dp.toPx())
                else if (value == 0f) drawCircle(color, 3.dp.toPx(), Offset(left + bw / 2, base - 4.dp.toPx()))
                else {
                    val height = h * (animated / max).coerceIn(0f, 1f)
                    drawRoundRect(color, Offset(left, base - height), Size(bw, height), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                }
            }
            mark(food[i], animatedFood[i], x, WeeklyOrange)
            if (paired) mark(burned[i], animatedBurned[i], x + cell * 0.34f, WeeklyGreen)
        }
    }
    Row(Modifier.fillMaxWidth()) {
        val names = listOf("Pon", "Wt", "Śr", "Czw", "Pt", "Sob", "Ndz")
        dates.forEach { d ->
            TextButton(onClick = { onSelect(d.toString()) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                Text("${names[d.dayOfWeek.value - 1]}\n${d.dayOfMonth}", style = MaterialTheme.typography.labelSmall,
                    color = if (selected == d.toString()) WeeklyGreen else MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
