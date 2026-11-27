package com.example.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.viewmodel.ConsultationVisuals
import org.json.JSONObject
import java.util.Locale

@Composable
internal fun ConsultationExtraCharts(data: JSONObject) {
    fun fmt(v: Double) = String.format(Locale.getDefault(), "%.1f", v)
    fun rows(key: String, source: JSONObject = data): List<JSONObject> {
        val a = source.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
    val sleep = remember(data) { rows("sleep").filter { ConsultationVisuals.number(it,"sen_minuty")?.let { n -> n > 0 && n <= 1440 } == true } }
    val recovery = remember(data) { rows("recovery") }
    OutlinedCard { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sen i regeneracja", style = MaterialTheme.typography.titleMedium)
        if (sleep.isEmpty()) Text("Brak odczytów snu.")
        sleep.forEach { row ->
            val hours = row.getDouble("sen_minuty") / 60
            Text(row.optString("data") + " • " + fmt(hours) + " h" + if (row.optBoolean("sen_szacowany_z_sesji")) " (szacunek)" else "")
            LinearProgressIndicator(progress = { (hours / 12).toFloat().coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth())
        }
        if (sleep.isNotEmpty()) Text("Skala 0–12 h; dłuższe odczyty opisano liczbowo.", style = MaterialTheme.typography.bodySmall)
        recovery.forEach { row ->
            Text(row.optString("data"), style = MaterialTheme.typography.labelMedium)
            listOf("Energia" to "energia_1_do_5", "Zmęczenie" to "zmeczenie_1_do_5").forEach { (label,key) ->
                ConsultationVisuals.number(row,key)?.takeIf { it in 1.0..5.0 }?.let { n ->
                    Text("$label: ${n.toInt()}/5")
                    LinearProgressIndicator(progress = { n.toFloat()/5 }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (recovery.isEmpty()) Text("Brak ocen samopoczucia.")
    } }
    val energy = data.optJSONObject("energy") ?: JSONObject()
    val values = listOf("TDEE 14 dni" to "tdee14","TDEE 28 dni" to "tdee28","TDEE 90 dni" to "tdee90","Zapotrzebowanie modelowe" to "model").mapNotNull { (label,key) -> ConsultationVisuals.number(energy,key)?.takeIf { it > 0 }?.let { label to it } }
    val intake = ConsultationVisuals.number(data,"srednie_kcal")
    OutlinedCard { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("TDEE i bilans energetyczny", style = MaterialTheme.typography.titleMedium)
        val max = (values.map { it.second } + listOfNotNull(intake)).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        (values + listOfNotNull(intake?.let { "Spożycie w okresie raportu" to it })).forEach { (label,n) ->
            Text("$label: ${n.toInt()} kcal/d")
            LinearProgressIndicator(progress = { (n/max).toFloat() }, modifier = Modifier.fillMaxWidth())
        }
        val tdee = ConsultationVisuals.number(energy,"tdee14")
        if (tdee != null && intake != null) Text("Orientacyjna różnica TDEE 14 dni − spożycie: ${(tdee-intake).toInt()} kcal/d. Okna mogą się różnić; to nie potwierdzony deficyt.")
        else Text("Brak danych do oszacowania deficytu z kalibracji 14 dni.")
        Text("Nie doliczamy kalorii treningu do TDEE.", style = MaterialTheme.typography.bodySmall)
    } }
    val cycle = remember(data) { rows("cycle",energy) }
    OutlinedCard { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Porównanie cykli", style = MaterialTheme.typography.titleMedium)
        if (cycle.isEmpty()) Text("Potrzebne daty rozpoczęcia dwóch cykli i pomiary wagi.")
        val all = cycle.flatMap { listOfNotNull(ConsultationVisuals.number(it,"current"),ConsultationVisuals.number(it,"previous")) }
        val low = (all.minOrNull() ?: 0.0) - 0.1
        val high = (all.maxOrNull() ?: 1.0) + 0.1
        cycle.forEach { row ->
            Text("Dzień cyklu ${row.optInt("day")}")
            listOf("Obecny" to "current","Poprzedni" to "previous").forEach { (label,key) ->
                val n = ConsultationVisuals.number(row,key)
                Text("$label: " + (n?.let { fmt(it)+" kg" } ?: "brak pomiaru"))
                if (n != null) LinearProgressIndicator(progress = { ((n-low)/(high-low)).toFloat().coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth(), color = if (key=="current") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
            }
        }
        if (all.isNotEmpty()) Text("Wspólna skala ${fmt(low)}–${fmt(high)} kg.")
        Text("Porównanie odpowiadających dni od początku miesiączki, nie potwierdzonych faz hormonalnych.", style = MaterialTheme.typography.bodySmall)
    } }
}
