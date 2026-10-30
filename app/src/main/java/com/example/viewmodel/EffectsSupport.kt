package com.example.viewmodel

import android.content.Context
import com.example.data.*
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import kotlin.math.abs

data class WellbeingDay(val date: String, val hunger: Int, val energy: Int, val sleepHours: Double?)
data class EffectsSettings(val low: Double? = null, val high: Double? = null, val wellbeing: List<WellbeingDay> = emptyList(),
    val lastAdjustment: String? = null, val goalChanged: String? = null, val reductionPlan: ReductionPlan? = null)
data class WeightPoint(val date: String, val kg: Double)
data class EffectsReport(val points: List<WeightPoint>, val trendKgWeek: Double?, val completeDays: Int, val confirmedDays: Int,
    val partialDays: Int, val loggedDays: Int, val periodDays: Int, val meanCalories: Double?, val meanProtein: Double?,
    val maintenance: Double?, val referenceIntake: Double?, val waistChange: Double?, val proposedDelta: Int?, val summary: String,
    val reasons: String, val weeklyText: String, val strengthText: String, val wellbeingText: String)

object EffectsSupport {
    const val KEY = "effects_v1"
    fun encode(s: EffectsSettings): String = JSONObject().put("low", s.low ?: JSONObject.NULL).put("high", s.high ?: JSONObject.NULL)
        .put("reductionPlan", s.reductionPlan?.let { plan ->
            JSONObject().put("start", plan.start).put("stages", JSONArray().also { a -> plan.stages.forEach {
                a.put(JSONObject().put("calories", it.calories).put("weeks", it.weeks))
            } })
        } ?: JSONObject.NULL)
        .put("lastAdjustment", s.lastAdjustment ?: JSONObject.NULL).put("goalChanged", s.goalChanged ?: JSONObject.NULL).put("wellbeing", JSONArray().also { array -> s.wellbeing.forEach {
            array.put(JSONObject().put("date", it.date).put("hunger", it.hunger).put("energy", it.energy).put("sleep", it.sleepHours ?: JSONObject.NULL))
        } }).toString()
    fun decode(raw: String): EffectsSettings {
        if (raw.isBlank()) return EffectsSettings()
        val j = JSONObject(raw); val a = j.optJSONArray("wellbeing") ?: JSONArray(); require(a.length() <= 730)
        val result = EffectsSettings(if (j.isNull("low")) null else j.getDouble("low"), if (j.isNull("high")) null else j.getDouble("high"),
            (0 until a.length()).map { val d = a.getJSONObject(it); WellbeingDay(d.getString("date"), d.getInt("hunger"), d.getInt("energy"), if (d.isNull("sleep")) null else d.getDouble("sleep")) },
            if (j.isNull("lastAdjustment")) null else j.getString("lastAdjustment"), if (j.isNull("goalChanged")) null else j.getString("goalChanged"),
            if (j.isNull("reductionPlan")) null else j.getJSONObject("reductionPlan").let { p ->
                val stages = p.getJSONArray("stages"); require(stages.length() in 1..12)
                ReductionPlan(p.getString("start"), (0 until stages.length()).map { i ->
                    val stage = stages.getJSONObject(i); ReductionStage(stage.getInt("calories"), stage.getInt("weeks"))
                })
            })
        require((result.low == null && result.high == null) || (result.low != null && result.high != null && result.low.isFinite() && result.high.isFinite() && result.low in 30.0..400.0 && result.high > result.low && result.high <= 400))
        result.wellbeing.forEach { LocalDate.parse(it.date); require(it.hunger in 1..5 && it.energy in 1..5 && (it.sleepHours == null || it.sleepHours.isFinite() && it.sleepHours in 0.0..24.0)) }
        require(result.wellbeing.map { it.date }.distinct().size == result.wellbeing.size)
        result.reductionPlan?.let { ReductionPlanSupport.validate(it) }
        result.lastAdjustment?.let { LocalDate.parse(it) }; result.goalChanged?.let { LocalDate.parse(it) }; return result
    }
    fun read(context: Context) = decode(context.getSharedPreferences("FITMangoUsability", 0).getString(KEY, "").orEmpty())
    fun save(context: Context, settings: EffectsSettings) { val raw = encode(settings); decode(raw)
        check(context.getSharedPreferences("FITMangoUsability", 0).edit().putString(KEY, raw).commit()) }
    fun trend(points: List<WeightPoint>): Double? {
        if (points.size < 2) return null
        val x = points.map { LocalDate.parse(it.date).toEpochDay().toDouble() }
        if (x.max() - x.min() < 7) return null
        val xm = x.average(); val ym = points.map { it.kg }.average()
        val denominator = x.sumOf { (it - xm) * (it - xm) }
        return if (denominator <= 0) null else x.indices.sumOf { (x[it] - xm) * (points[it].kg - ym) } / denominator * 7
    }
    fun smoothed(points: List<WeightPoint>): List<WeightPoint> = points.mapNotNull { p ->
        val lower = LocalDate.parse(p.date).minusDays(6).toString()
        val values = points.filter { it.date in lower..p.date }
        if (values.size < 2) null else WeightPoint(p.date, values.map { it.kg }.average())
    }
    fun analyze(weights: List<WeightLogEntity>, measurements: List<BodyMeasurementEntity>, days: List<HabitDay>,
        confirmations: List<EnergyConfirmation>, profile: UserProfileEntity?, sports: SportsData, settings: EffectsSettings,
        today: LocalDate, range: Int, energySettings: EnergyProfileSettings? = null): EffectsReport {
        require(range in listOf(7, 14, 30, 90))
        val weekly = range == 7
        val start = today.minusDays(if (weekly) 7L else range - 1L).toString()
        val end = (if (weekly) today.minusDays(1) else today).toString()
        val periodDays = if (weekly) 7 else range - 1
        fun normalizedDate(raw: String): String? = runCatching { LocalDate.parse(raw).toString() }.getOrNull()
            ?: runCatching { LocalDate.parse(raw, java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)).toString() }.getOrNull()
        val normalizedMeasurements = measurements.mapNotNull { row -> normalizedDate(row.date)?.let { row.copy(date = it) } }
        val normalizedWeights = weights.mapNotNull { row -> normalizedDate(row.date)?.let { row.copy(date = it) } }
        val merged = normalizedMeasurements.filter { it.weightKg in 30f..400f }.groupBy { it.date }.map { (d, rows) -> WeightPoint(d, rows.maxBy { it.timestamp }.weightKg.toDouble()) }.associateBy { it.date }.toMutableMap()
        normalizedWeights.filter { it.weightKg.isFinite() && it.weightKg in 30f..400f }.groupBy { it.date }.forEach { (d, rows) -> merged[d] = WeightPoint(d, rows.maxBy { it.id }.weightKg.toDouble()) }
        val points = merged.values.filter { it.date in start..end }.sortedBy { it.date }
        val trend = trend(points)
        val logged = days.distinctBy { it.date }.filter { it.date >= start && it.date <= end && it.date < today.toString() && it.meals.isNotEmpty() }
        val confirmed = logged.filter { day -> WeeklyEnergySupport.isFoodConfirmed(day, confirmations.find { it.date == day.date }) }
        val complete = confirmed
        // Confirmed estimates count in reports; automatic numerical corrections still need precise nutrients.
        val precise = confirmed.filter { it.nutritionComplete }
        val mean = complete.map { it.calories.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val protein = complete.map { it.protein.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val maintenance = WeeklyEnergySupport.maintenanceCalories(profile, energySettings)
        val span = if (points.isEmpty()) 0 else LocalDate.parse(points.last().date).toEpochDay() - LocalDate.parse(points.first().date).toEpochDay()
        val mass = points.lastOrNull()?.kg ?: profile?.currentWeightKg?.toDouble() ?: 0.0
        val samePhase = listOfNotNull(settings.lastAdjustment, settings.goalChanged).all { it <= start }
        val strong = samePhase && points.size >= 6 && span >= 21 && precise.size >= 21 && precise.size >= periodDays * .7 && trend != null
        val stable = trend != null && mass > 0 && abs(trend) / mass < .0015
        val reference = precise.takeIf { strong && stable }?.map { it.calories.toDouble() }?.average()
        val waist = normalizedMeasurements.filter { it.date in start..end && it.waistCm.isFinite() && it.waistCm > 0 }
            .groupBy { it.date }.map { (_, rows) -> rows.maxBy { it.timestamp } }.sortedBy { it.date }
        val waistChange = if (waist.size >= 2) (waist.last().waistCm - waist.first().waistCm).toDouble() else null
        val goal = profile?.goal ?: "Utrzymanie"
        val state = when {
            trend == null -> "Za mało pomiarów do oceny trendu."
            goal == "Redukcja" -> if (trend < 0) "Trend wagi jest spadkowy." else "Nie widać spadkowego trendu wagi w tym okresie."
            goal == "Budowanie masy" -> if (trend > 0) "Trend wagi jest wzrostowy. To nie określa ilości przyrostu mięśni." else "Nie widać wzrostowego trendu wagi w tym okresie."
            settings.low != null && settings.high != null && points.isNotEmpty() -> if (points.takeLast(3).map { it.kg }.average() in settings.low..settings.high) "Ostatnie pomiary są średnio w Twoim przedziale." else "Średnia ostatnich pomiarów jest poza Twoim przedziałem."
            stable -> "Waga jest względnie stabilna w obserwowanym okresie."
            trend > 0 -> "Widać trend wzrostowy wagi." else -> "Widać trend spadkowy wagi."
        }
        val recentChange = listOfNotNull(settings.lastAdjustment, settings.goalChanged).any { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(it), today) < 21 }
        val bmi = if (profile != null && profile.heightCm > 0) mass / (profile.heightCm / 100.0).let { it * it } else 0.0
        val rawDelta = if (!strong || recentChange || profile == null || maintenance == null || profile.age < 18 || bmi < 18.5) null else when {
            goal == "Redukcja" && trend!! >= -mass * .001 && profile.targetCalories - 100 >= maxOf(1500.0, maintenance * .8) -> -100
            goal == "Redukcja" && trend!! < -mass * .01 -> 100
            goal == "Budowanie masy" && trend!! <= mass * .001 && profile.targetCalories + 100 <= maintenance * 1.15 -> 100
            goal != "Redukcja" && goal != "Budowanie masy" && trend!! > mass * .0015 && profile.targetCalories - 100 >= maxOf(1500.0, maintenance * .8) -> -100
            goal != "Redukcja" && goal != "Budowanie masy" && trend!! < -mass * .0015 -> 100
            else -> null
        }
        val recentNotes = settings.wellbeing.filter { it.date >= today.minusDays(6).toString() && it.date <= end }
        val caution = recentNotes.size >= 3 && (recentNotes.map { it.hunger }.average() >= 4 || recentNotes.map { it.energy }.average() <= 2)
        val delta = rawDelta?.takeIf { profile != null && profile.targetCalories + it >= profile.targetProtein * 4 + profile.targetFat * 9 && !(it < 0 && caution) }
        val reason = when {
            energySettings?.individualPlan == true -> "Tryb indywidualny: nie proponujemy automatycznych celów ani korekt kalorii. Trend i zapisane pomiary pozostają dostępne."
            recentChange -> "Po zmianie celu lub zatwierdzonej korekcie czekamy co najmniej 21 dni na dalszą ocenę."
            !samePhase -> "Wybrany okres obejmuje także dane sprzed zmiany celu lub kalorii. Do korekty wybierz okres mieszczący się w jednej fazie obserwacji."
            rawDelta != null && rawDelta < 0 && caution -> "W ostatnich wpisach zgłaszasz duży głód lub mało energii. Nie proponujemy teraz obniżenia kalorii."
            !strong -> "Do liczbowej korekty wymagamy co najmniej 6 pomiarów rozłożonych na 21 dni oraz 21 zatwierdzonych dni jedzenia z precyzyjnymi wartościami składników (min. 70% okresu)."
            delta != null -> "To mały krok do rozważenia na podstawie trendu i zapisów z precyzyjnymi wartościami, nie pewny pomiar zapotrzebowania. Możesz go odrzucić."
            else -> "Nie proponujemy teraz zmiany kalorii. Uwzględnij obwody, wyniki treningowe i samopoczucie."
        }
        val lastWeek = complete.filter { it.date >= today.minusDays(7).toString() }
        val prevWeek = complete.filter { it.date >= today.minusDays(14).toString() && it.date < today.minusDays(7).toString() }
        fun avg(rows: List<HabitDay>) = if (rows.isEmpty()) "brak danych" else "${rows.map { it.calories }.average().toInt()} kcal (${rows.size} dni)"
        val weeklyText = "Ostatnie 7 zakończonych dni: ${avg(lastWeek)}." + (if (weekly) "" else " Poprzednie 7: ${avg(prevWeek)}.") + "\nCo działa: ${if (complete.isNotEmpty()) "masz ${complete.size} kompletnych, zatwierdzonych zapisów." else "zacznij od regularnych zapisów."}\nCo sprawdzić: ${confirmed.count { !it.nutritionComplete }} zatwierdzonych dni z szacunkowymi wartościami odżywczymi; ${logged.size - confirmed.size} zapisanych dni bez aktualnego zatwierdzenia."
        val comparisons = sports.sets.filter { it.date in start..end }.groupBy { it.exercise.trim().lowercase() }.mapNotNull { (_, sets) ->
            val dates = sets.map { it.date }.distinct().sorted(); if (dates.size < 2) null else {
                val old = sets.filter { it.date == dates[dates.lastIndex - 1] }.maxWith(compareBy<SportSet> { it.kg }.thenBy { it.reps })
                val new = sets.filter { it.date == dates.last() }.maxWith(compareBy<SportSet> { it.kg }.thenBy { it.reps })
                "${new.exercise}: ${old.reps} × ${old.kg} kg → ${new.reps} × ${new.kg} kg"
            }
        }.take(5)
        val notes = settings.wellbeing.filter { it.date in start..end }
        fun rating(value: Double) = String.format(java.util.Locale.getDefault(), "%.1f", value)
        val wellbeing = if (notes.isEmpty()) "Brak zapisów snu, głodu i energii w tym okresie." else {
            val sleep = notes.mapNotNull { it.sleepHours }
            "Wpisy samopoczucia: ${notes.size}. Głód ${rating(notes.map { it.hunger }.average())}/5, energia ${rating(notes.map { it.energy }.average())}/5. Sen: ${if (sleep.isEmpty()) "brak danych" else "${rating(sleep.average())} h (${sleep.size} wpisów)"}. To zestawienie, nie dowód przyczyny zmian wagi."
        }
        return EffectsReport(points, trend, complete.size, confirmed.size, logged.count { !it.nutritionComplete }, logged.size, periodDays, mean, protein,
            maintenance, reference, waistChange, delta, state, reason, weeklyText, if (comparisons.isEmpty()) "Potrzebne wyniki tego samego ćwiczenia z co najmniej dwóch dat." else comparisons.joinToString("\n"), wellbeing)
    }
}

object ChatRecipeSupport {
    fun draft(text: String): ImportedRecipe? {
        val cleaned = text.replace("**", "").replace(Regex("(?m)^#{1,6}\\s*"), "").trim()
        if (Regex("(?im)^\\s*(składniki|skladniki|ingredients)\\s*[:—-]?\\s*$").findAll(cleaned).count() > 1) return null
        return parseStructuredRecipeText(cleaned, "AI Dietetyk")?.copy(nutritionIsEstimated = true, source = "AI Dietetyk")
    }
}
