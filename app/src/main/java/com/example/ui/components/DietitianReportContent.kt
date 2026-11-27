package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.viewmodel.ConsultationVisuals
import org.json.JSONObject
import java.util.Locale

@Composable
fun DietitianReportContent(report: String) {
    if (report.isBlank()) {
        Text("Wygeneruj analizę, aby zobaczyć podsumowanie tygodnia.")
        return
    }
    val marker = "\n" + ConsultationVisuals.MARKER
    val visual = remember(report) { runCatching { JSONObject(report.substringAfter(marker, "{}")) }.getOrDefault(JSONObject()) }
    val blocks = remember(report) {
        val result = mutableListOf<Pair<String?, List<String>>>()
        var heading: String? = null
        var lines = mutableListOf<String>()
        fun flush() { if (lines.isNotEmpty() || heading != null) result.add(heading to lines.toList()); lines = mutableListOf() }
        report.substringBefore(marker).lines().forEach { raw ->
            val line = raw.trim()
            if (line.matches(Regex("^#{1,6}\\s+.*"))) {
                flush(); heading = line.replace(Regex("^#{1,6}\\s+"), "").trim()
            } else if (line == "---") { flush(); heading = null }
            else if (line.isNotBlank()) lines.add(line)
        }
        flush(); result
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (visual.length() > 0) ConsultationDashboard(visual)
        blocks.forEach { (title, paragraphs) ->
            var expanded by remember(report, title) { mutableStateOf(title?.startsWith("Dane szczegółowe") != true && title?.startsWith("Wiarygodność") != true) }
            val collapsible = title?.startsWith("Dane szczegółowe") == true || title?.startsWith("Wiarygodność") == true
            OutlinedCard(shape = RoundedCornerShape(18.dp), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (collapsible) TextButton(onClick = { expanded = !expanded }) { Text((if (expanded) "− " else "+ ") + title.orEmpty()) }
                    else if (!title.isNullOrBlank()) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (expanded) paragraphs.forEach { line ->
                        Text(line.replace("**", "").replace(Regex("^[-*]\\s+"), "• "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsultationDashboard(data: JSONObject) {
    fun n(key: String) = ConsultationVisuals.number(data, key)
    fun fmt(value: Double) = String.format(Locale.getDefault(), "%.1f", value)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mango • Twoje postępy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Dane z raportu • zatwierdzone dni", style = MaterialTheme.typography.labelMedium)
            n("zatwierdzone_dni")?.let { count -> Text("Dziennik: ${count.toInt()}/${n("dni_okresu")?.toInt() ?: "?"} dni") }
            n("zatwierdzone_dni_z_wartosciami_szacunkowymi")?.takeIf { it > 0 }?.let { Text("Wartości przybliżone w ${it.toInt()} dniach", style = MaterialTheme.typography.bodySmall) }
            listOf(Triple("Kalorie", "srednie_kcal", "cel_jedzenia_kcal"), Triple("Białko", "srednie_bialko_g", "cel_bialka_g"), Triple("Węglowodany", "srednie_weglowodany_g", "cel_weglowodanow_g"), Triple("Tłuszcze", "srednie_tluszcze_g", "cel_tluszczu_g")).forEach { (label, key, goalKey) ->
                val actual = n(key)
                val goal = n(goalKey)?.takeIf { it > 0 }
                val unit = if (label == "Kalorie") "kcal" else "g"
                Text("$label: " + (actual?.let { "${fmt(it)} $unit" } ?: "brak danych") + (goal?.let { " / ${fmt(it)} $unit" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                if (actual != null && goal != null) LinearProgressIndicator(progress = { (actual / goal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp), color = primary, trackColor = track)
            }
            Text("Cele z aktualnego profilu, nie TDEE ani cele historyczne.", style = MaterialTheme.typography.bodySmall)
        }
    }
    val points = remember(data) {
        val array = data.optJSONArray("weights")
        (0 until (array?.length() ?: 0)).mapNotNull { i ->
            val row = array?.optJSONObject(i) ?: return@mapNotNull null
            val kg = row.optDouble("kg")
            val date = runCatching { java.time.LocalDate.parse(row.optString("data")) }.getOrNull()
            if (date != null && kg.isFinite() && kg > 0) date to kg else null
        }.sortedBy { it.first }.distinctBy { it.first }
    }
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Masa ciała", style = MaterialTheme.typography.titleMedium)
            if (points.isEmpty()) Text("Brak pomiarów w okresie.")
            else {
                Text("${fmt(points.last().second)} kg • ${points.last().first}", style = MaterialTheme.typography.headlineSmall)
                if (points.size >= 2) {
                    val min = points.minOf { it.second } - 0.1
                    val max = points.maxOf { it.second } + 0.1
                    val span = (points.last().first.toEpochDay() - points.first().first.toEpochDay()).coerceAtLeast(1)
                    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                        val coords = points.map { (date, kg) -> Offset(8.dp.toPx() + (size.width - 16.dp.toPx()) * ((date.toEpochDay() - points.first().first.toEpochDay()).toFloat() / span.toFloat()), 8.dp.toPx() + (size.height - 16.dp.toPx()) * ((max - kg)/(max-min)).toFloat()) }
                        coords.zipWithNext().forEach { (a,b) -> drawLine(primary,a,b,strokeWidth=2.dp.toPx()) }
                        coords.forEach { drawCircle(primary,4.dp.toPx(),it) }
                    }
                    Text("${points.first().first} – ${points.last().first} • ${points.size} pomiarów")
                    Text("Zakres ${fmt(min+0.1)}–${fmt(max-0.1)} kg. Zmiana masy nie oznacza zmiany tłuszczu.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    data.optJSONObject("activity")?.let { activity ->
        OutlinedCard { Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Trening i regeneracja", style = MaterialTheme.typography.titleMedium)
            ConsultationVisuals.number(activity,"minuty_sesji_bez_nakladania")?.let { Text("${it.toInt()} min sesji", style = MaterialTheme.typography.headlineSmall) }
            ConsultationVisuals.number(activity,"dni_z_sesjami")?.let { Text("${it.toInt()} dni z sesjami") }
            Text("Czas sesji obejmuje przerwy.", style = MaterialTheme.typography.bodySmall)
        } }
    }
}
