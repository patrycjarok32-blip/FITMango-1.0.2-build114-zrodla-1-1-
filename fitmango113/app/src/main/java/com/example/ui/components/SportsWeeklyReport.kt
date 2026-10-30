package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.UserProfileEntity
import com.example.ui.theme.*
import com.example.viewmodel.*
import kotlin.math.roundToInt

@Composable
private fun ReportPanel(icon: ImageVector, title: String, color: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    MangoCard(modifier) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SportHeading(icon, title, color)
        content()
    } }
}

@Composable
private fun ReportMiniBars(values: List<Long?>, color: Color, description: String) {
    val maximum = (values.filterNotNull().maxOrNull() ?: 0L).coerceAtLeast(1)
    val track = WarmBrown
    Canvas(Modifier.fillMaxWidth().height(45.dp).semantics { contentDescription = description }) {
        val slot = size.width / values.size.coerceAtLeast(1)
        values.forEachIndexed { i, value ->
            val h = if (value == null) 3.dp.toPx() else maxOf(3.dp.toPx(), size.height * value.toFloat() / maximum)
            drawRoundRect(if (value == null) track else color, Offset(slot * i + 2.dp.toPx(), size.height - h),
                Size((slot - 4.dp.toPx()).coerceAtLeast(1f), h), CornerRadius(3.dp.toPx()))
        }
    }
}

@Composable
private fun AiExcerpt(text: String) {
    if (text.isNotBlank()) Text("AI: $text", style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
}

@Composable
fun SportsWeeklyReport(numbers: SportsReportNumbers, profile: UserProfileEntity?, explanation: WeeklyAiExplanation?, busy: Boolean,
    onGenerate: () -> Unit) {
    val matched = SportsGraphicsSupport.matching(explanation, numbers.from, numbers.until)
    val cards = remember(matched) { if (matched?.available == true) SportsGraphicsSupport.aiCards(matched.answer) else AiReportCards() }
    var expanded by rememberSaveable(numbers.from.toString(), matched?.generatedAt) { mutableStateOf(false) }
    var evidenceOpen by rememberSaveable(numbers.from.toString(), matched?.generatedAt) { mutableStateOf(false) }
    var details by rememberSaveable(numbers.from.toString()) { mutableStateOf(false) }
    val twoColumns = LocalDensity.current.fontScale <= 1.15f
    val proteinTarget = profile?.targetProtein ?: 0
    val sleep = numbers.days.mapNotNull { it.sleepMinutes }
    fun average(value: Double?, suffix: String) = value?.let { "${it.roundToInt()} $suffix" } ?: "brak danych"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Raport tygodniowy", style = MaterialTheme.typography.headlineSmall)
        Text("${numbers.from} – ${numbers.until}\nOstatnie 7 zakończonych dni", style = MaterialTheme.typography.bodyMedium, color = GoldAccent)
        if (matched?.available == true) MangoGuide("Raport gotowy", event = matched.generatedAt.hashCode())
        ReportPanel(Icons.Outlined.TrackChanges, "Kierunek względem celu", LeafGreen, Modifier.fillMaxWidth()) {
            Text(profile?.goal ?: "Uzupełnij cel w Profilu", style = MaterialTheme.typography.titleLarge)
            Text(if (matched?.available == true) "Wyjaśnienie AI" else "Ocena wymaga analizy", style = MaterialTheme.typography.labelLarge,
                color = OrangeInk, modifier = Modifier.background(OrangeInk.copy(alpha = .1f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
            Text(cards.direction.ifBlank { if (matched?.available == true) "Ocena AI jest dostępna w pełnym uzasadnieniu poniżej." else "Wygeneruj raport, aby połączyć zapisy jedzenia, aktywności i pomiarów z Twoim celem." },
                maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (matched != null) Text("Wygenerowano: ${sportTime(matched.generatedAt)}. AI odnosi się do danych z chwili generowania; po zmianach odśwież raport.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SportMetric("Zatwierdzone dni", "${numbers.approved} / 7", Icons.Outlined.EventAvailable, LeafGreen, Modifier.weight(1f))
            SportMetric("Dni z aktywnością", "${numbers.activity.activeDays} / 7", Icons.Outlined.DirectionsWalk, LeafGreen, Modifier.weight(1f))
        }
        val food: @Composable (Modifier) -> Unit = { modifier ->
            ReportPanel(Icons.Outlined.Restaurant, "Posiłki i białko", LeafGreen, modifier) {
                Text(average(numbers.meanCalories, "kcal / dzień"), style = MaterialTheme.typography.titleMedium)
                Text("Białko: ${average(numbers.meanProtein, "g / dzień")}")
                if (numbers.meanProtein != null && proteinTarget > 0) {
                    val ratio = mangoFloat((numbers.meanProtein.toFloat() / proteinTarget).coerceIn(0f, 1f))
                    LinearProgressIndicator(progress = { ratio }, color = LeafGreen, trackColor = WarmBrown, modifier = Modifier.fillMaxWidth())
                    Text("Aktualny cel białka: ${proteinTarget} g", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                }
                ReportMiniBars(numbers.days.map { d -> numbers.food.find { it.date == d.date.toString() }?.calories?.toLong() }, LeafGreen,
                    "Kalorie w zatwierdzonych dniach. Średnia z ${numbers.approved} dni, brakujące daty nie są zerami.")
                Text("Średnie z ${numbers.approved} pełnych dni potwierdzonych przez Ciebie.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                if (numbers.estimatedFood) Text("Wartości odżywcze są szacunkowe.", style = MaterialTheme.typography.bodySmall)
                AiExcerpt(cards.food)
            }
        }
        val activity: @Composable (Modifier) -> Unit = { modifier ->
            ReportPanel(Icons.Outlined.DirectionsRun, "Aktywność", LeafGreen, modifier) {
                Text("${numbers.activity.minutes} min", style = MaterialTheme.typography.headlineSmall)
                Text("${numbers.activity.sessions} sesji • ${numbers.activity.walks} spacerów")
                ReportMiniBars(numbers.days.map { it.minutes }, LeafGreen, "Minuty zapisanych sesji za 7 zakończonych dni, bez podwajania nakładającego się czasu.")
                Text("Minuty zapisanych sesji, z przerwami. Kalorii treningów nie dodajemy ponownie do bilansu.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
            }
        }
        val recovery: @Composable (Modifier) -> Unit = { modifier ->
            ReportPanel(Icons.Outlined.Bedtime, "Regeneracja", SleepAccent, modifier) {
                Text(SportsGraphicsSupport.sleepLabel(sleep.takeIf { it.isNotEmpty() }?.average()?.toLong()), style = MaterialTheme.typography.headlineSmall)
                Text("Średni sen • ${sleep.size} zapisów", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                ReportMiniBars(numbers.days.map { it.sleepMinutes }, SleepAccent, "Długość snu według dat pobudki. Brakujące noce pozostają nieznane.")
                RatingDots("Energia", numbers.days.mapNotNull { it.energy }.takeIf { it.isNotEmpty() }?.average(), LeafGreen)
                RatingDots("Zmęczenie", numbers.days.mapNotNull { it.fatigue }.takeIf { it.isNotEmpty() }?.average(), SleepAccent)
                AiExcerpt(cards.recovery)
            }
        }
        val actions: @Composable (Modifier) -> Unit = { modifier ->
            ReportPanel(Icons.Outlined.Assignment, "Na kolejny tydzień", LeafGreen, modifier) {
                if (cards.actions.isEmpty()) Text(if (matched?.available == true) "Wskazówki przeczytasz w pełnym uzasadnieniu poniżej." else "Wskazówki pojawią się po wygenerowaniu raportu.", style = MaterialTheme.typography.bodyMedium)
                cards.actions.forEachIndexed { index, action ->
                    Text("${index + 1}. $action", style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        BoxWithConstraints {
            if (maxWidth >= 350.dp && twoColumns) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { food(Modifier.weight(1f)); activity(Modifier.weight(1f)) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { recovery(Modifier.weight(1f)); actions(Modifier.weight(1f)) }
            } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                food(Modifier.fillMaxWidth()); activity(Modifier.fillMaxWidth()); recovery(Modifier.fillMaxWidth()); actions(Modifier.fillMaxWidth())
            }
        }
        Button(onClick = onGenerate, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("sport_weekly_ai")) {
            Text(if (busy) "Przygotowuję raport…" else if (matched?.available == true) "Odśwież raport AI" else "Wygeneruj raport AI")
        }
        if (matched != null) {
            if (!matched.available) Text("AI jest teraz niedostępne. Dane i wykresy lokalne pozostają dostępne.", color = OrangeInk)
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("sport_report_expand")) {
                Text(if (expanded) "Zwiń pełne uzasadnienie −" else "Rozwiń pełne uzasadnienie →")
            }
            if (matched.evidence.isNotBlank()) {
                TextButton(onClick = { evidenceOpen = !evidenceOpen }, modifier = Modifier.fillMaxWidth().testTag("ai_evidence_toggle")) {
                    Text(if (evidenceOpen) "Zwiń podstawy oceny −" else "Dlaczego widzę taką ocenę? +")
                }
                if (evidenceOpen) MangoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dane z chwili generowania raportu", style = MaterialTheme.typography.titleMedium)
                    Text(matched.evidence, modifier = Modifier.testTag("ai_evidence_snapshot"))
                } }
            }
            if (expanded) MangoCard(Modifier.fillMaxWidth()) { Text(matched.fullText, Modifier.padding(14.dp).testTag("sport_report_full")) }
        }
        TextButton(onClick = { details = !details }) { Text(if (details) "Zwiń daty i źródła −" else "Daty, źródła i zakres raportu +") }
        if (details) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Zatwierdzone dni: " + numbers.food.joinToString { it.date }.ifBlank { "brak" })
            numbers.days.forEach { day -> Text("${day.date}: ${day.minutes?.let { "$it min sesji" } ?: "brak sesji"} • sen ${SportsGraphicsSupport.sleepLabel(day.sleepMinutes)}", style = MaterialTheme.typography.bodySmall) }
            Text("Nie uzupełniamy brakujących dni zerami. Zatwierdzone dni z szacunkowymi wartościami składników są uwzględnione.", style = MaterialTheme.typography.bodySmall)
            AiExcerpt(cards.confidence)
        }
        Text("Źródła: Health Connect i Twoje wpisy. AI korzysta z ustawień w Profilu; wykresy działają bez AI.", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
    }
}
