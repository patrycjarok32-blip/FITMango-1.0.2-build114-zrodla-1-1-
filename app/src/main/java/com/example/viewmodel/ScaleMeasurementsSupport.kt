package com.example.viewmodel

import android.content.Context
import com.example.data.WeightLogEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ScaleMetric(val label: String, val unit: String, val min: Double, val max: Double)
data class ScaleMeasurement(val id: String, val date: String, val time: Long, val source: String,
    val values: Map<String, Double>, val origin: String = "manual")
object ScaleMeasurementsSupport {
    const val KEY = "scale_measurements_v1"
    val metrics = linkedMapOf(
        "weight" to ScaleMetric("Waga", "kg", 1.0, 500.0), "bmi" to ScaleMetric("BMI", "", 1.0, 150.0),
        "fat" to ScaleMetric("Tkanka tłuszczowa", "%", 0.0, 100.0), "subcutaneous" to ScaleMetric("Tłuszcz podskórny", "%", 0.0, 100.0),
        "visceral" to ScaleMetric("Tłuszcz trzewny — wskaźnik", "", 0.0, 100.0), "muscle" to ScaleMetric("Masa mięśniowa", "kg", 0.0, 400.0),
        "skeletal" to ScaleMetric("Mięśnie szkieletowe", "%", 0.0, 100.0), "bone" to ScaleMetric("Masa kostna", "kg", 0.0, 30.0),
        "lean" to ScaleMetric("Beztłuszczowa masa ciała", "kg", 0.0, 500.0), "age" to ScaleMetric("Wiek metaboliczny", "lat", 1.0, 150.0),
        "bmr" to ScaleMetric("BMR według wagi", "kcal/dzień", 100.0, 15000.0), "water" to ScaleMetric("Woda w ciele", "%", 0.0, 100.0),
        "protein" to ScaleMetric("Białko w ciele — nie w diecie", "%", 0.0, 100.0),
        "waterKg" to ScaleMetric("Masa wody z Health Connect", "kg", 0.0, 500.0))
    fun validate(row: ScaleMeasurement) {
        require(row.id.length in 1..500 && row.source.length in 1..200 && row.origin in listOf("manual", "health"))
        LocalDate.parse(row.date); require(row.time >= 0)
        require(row.values.isNotEmpty() && row.values.size <= metrics.size) { "Wpisz przynajmniej jedną znaną wartość." }
        row.values.forEach { (key, value) -> val m = requireNotNull(metrics[key]); require(value.isFinite() && value in m.min..m.max) { "Sprawdź: ${m.label}" } }
        row.values["weight"]?.let { weight -> listOf("muscle", "lean", "bone", "waterKg").forEach { key ->
            require((row.values[key] ?: 0.0) <= weight) { "${metrics.getValue(key).label} nie może przekraczać wagi z tego samego wpisu." }
        } }
    }
    fun encode(rows: List<ScaleMeasurement>): String {
        require(rows.size <= 5000 && rows.map { it.id }.distinct().size == rows.size)
        return JSONArray().also { a -> rows.forEach { r -> validate(r); a.put(JSONObject().put("id", r.id).put("date", r.date)
            .put("time", r.time).put("source", r.source).put("origin", r.origin).put("values", JSONObject(r.values))) } }.toString()
    }
    fun decode(raw: String): List<ScaleMeasurement> {
        if (raw.isBlank()) return emptyList()
        val a = JSONArray(raw); require(a.length() <= 5000)
        return (0 until a.length()).map { i -> val j = a.getJSONObject(i); val v = j.getJSONObject("values")
            ScaleMeasurement(j.getString("id"), j.getString("date"), j.getLong("time"), j.getString("source"),
                v.keys().asSequence().associateWith { v.getDouble(it) }, j.getString("origin")).also(::validate)
        }.also { require(it.map { r -> r.id }.distinct().size == it.size) }
    }
    fun read(c: Context): List<ScaleMeasurement> {
        val hidden = MeasurementRemovalSupport.hidden(c)
        return decode(c.getSharedPreferences("FITMangoUsability", 0).getString(KEY, "").orEmpty()).filterNot { MeasurementRemovalSupport.hiddenScale(it, hidden) }
    }
    fun write(c: Context, rows: List<ScaleMeasurement>) {
        check(c.getSharedPreferences("FITMangoUsability", 0).edit().putString(KEY, encode(rows)).commit()) { "Nie udało się zapisać pomiarów." }
    }
    fun merge(old: List<ScaleMeasurement>, incoming: List<ScaleMeasurement>): List<ScaleMeasurement> {
        incoming.forEach(::validate)
        return (old.associateBy { it.id } + incoming.associateBy { it.id }).values.sortedByDescending { it.time }.take(5000)
    }
    fun manual(date: String, values: Map<String, String>, today: LocalDate = LocalDate.now()): ScaleMeasurement {
        val d = LocalDate.parse(date); require(d <= today) { "Pomiar nie może mieć przyszłej daty." }
        val parsed = values.filterValues { it.isNotBlank() }.mapValues { (k, v) ->
            requireNotNull(v.trim().replace(',', '.').toDoubleOrNull()) { "Nieprawidłowa liczba: ${metrics[k]?.label}" }
        }
        return ScaleMeasurement(java.util.UUID.randomUUID().toString(), date, System.currentTimeMillis(), "Renpho — wpis ręczny", parsed).also(::validate)
    }
    /** Extra scale weights fill missing dates; existing dated weight history remains authoritative. */
    fun withScaleWeights(existing: List<WeightLogEntity>, rows: List<ScaleMeasurement>): List<WeightLogEntity> {
        fun date(raw: String) = runCatching { LocalDate.parse(raw).toString() }.getOrElse {
            runCatching { LocalDate.parse(raw, java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu")).toString() }.getOrDefault(raw)
        }
        val recorded = existing.map { date(it.date) }.toSet()
        return existing + rows.filter { it.values["weight"] != null && it.date !in recorded }.groupBy { it.date }.map { (_, entries) ->
            val latest = entries.maxBy { it.time }
            WeightLogEntity(id = latest.time, date = latest.date, weightKg = latest.values.getValue("weight").toFloat())
        }
    }
    fun aiRows(rows: List<ScaleMeasurement>, from: LocalDate, until: LocalDate): JSONArray = JSONArray().also { a ->
        rows.filter { runCatching { LocalDate.parse(it.date) in from..until }.getOrDefault(false) }.sortedBy { it.time }.takeLast(300).forEach { r ->
            a.put(JSONObject().put("data", r.date).put(if (r.origin == "manual") "czas_zapisu" else "czas_pomiaru", Instant.ofEpochMilli(r.time).toString()).put("zrodlo", r.source)
                .put("tryb", r.origin).put("pomiary", JSONArray().also { v -> r.values.forEach { (key, value) ->
                    val m = metrics.getValue(key); v.put(JSONObject().put("nazwa", m.label).put("wartosc", value).put("jednostka", m.unit))
                } }))
        }
    }
    const val AI_NOTE = "Skład ciała Renpho/BIA to szacunki algorytmu, zależne m.in. od nawodnienia i warunków ważenia. " +
        "Porównuj ten sam wskaźnik z tego samego źródła w podobnych warunkach. Nie sumuj mięśni, kości i beztłuszczowej masy: te kategorie się nakładają. " +
        "Mała zmiana nie dowodzi utraty tłuszczu ani przyrostu mięśni. Wiek metaboliczny i tłuszcz trzewny są wskaźnikami producenta, nie diagnozą. " +
        "Białko w ciele (%) nie jest spożytym białkiem. BMR z wagi to szacunek, nie całkowite zapotrzebowanie; nie dodawaj go do spalania i nie zastępuj nim celu. " +
        "Nie obliczaj nowych celów kalorii z samych tych odczytów. Nie łącz odrębnych odczytów w jeden rzekomy pomiar."
}
