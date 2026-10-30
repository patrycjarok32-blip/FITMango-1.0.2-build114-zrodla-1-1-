package com.example.viewmodel

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Dated snapshots independent of the reusable weekly planner. No credentials. */
class HabitsDiary(context: Context, repository: DietaRepository, date: StateFlow<String>, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("FITMangoHabits", Context.MODE_PRIVATE)
    private val revision = MutableStateFlow(0)
    private val _days = MutableStateFlow(readDays())
    val days: StateFlow<List<HabitDay>> = _days.asStateFlow()
    private data class Source(val meals: List<MealPlanEntity>, val recipes: List<RecipeEntity>, val ingredients: List<RecipeIngredientEntity>, val drinks: List<BeverageLogEntity>, val profile: UserProfileEntity?)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == "days") scope.launch(Dispatchers.Default) { _days.value = readDays() }
        if (key == null || key != "days") revision.value += 1
    }
    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        scope.coroutineContext[Job]?.invokeOnCompletion { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        scope.launch(Dispatchers.Default) {
            val source = combine(repository.allMealPlans, repository.allRecipes, repository.allRecipeIngredients,
                repository.beverageLogs, repository.userProfile) { m, r, i, b, p -> Source(m, r, i, b, p) }
            combine(source, date, revision) { s, today, _ -> s to today }.collect { (s, today) ->
                val completed = s.meals.filter { it.isCompleted && it.completedDate == today }
                val adjusted = ProduceSupport.entries(completed, s.recipes, s.ingredients).map { entry ->
                    val override = overrides().optJSONObject(today)?.optJSONObject(entry.key)
                    if (override == null) entry else entry.copy(reviewed = true, category = override.optString("category", entry.category),
                        grams = if (override.has("grams")) override.getDouble("grams").toFloat() * entry.portionScale / override.optDouble("portionScale", 1.0).toFloat() else entry.grams)
                } + manual(today)
                val nutrition = completed.map { meal -> IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, s.recipes.find { it.id == meal.recipeId }, s.ingredients) }
                val day = HabitDay(today, nutrition.sumOf { it.calories ?: 0 }, nutrition.sumOf { (it.protein ?: 0).toInt() },
                    nutrition.sumOf { (it.carbs ?: 0).toInt() }, nutrition.sumOf { (it.fat ?: 0).toInt() },
                    s.drinks.filter { it.date == today }.sumOf { it.amountMl }, s.profile?.targetCalories ?: 2000,
                    prefs.getInt("produceGoal", 400), prefs.getInt("waterGoal", 2500),
                    completed.mapIndexed { i, meal -> "${meal.recipeTitle} • ${nutrition[i].calories ?: 0} kcal • ${MealPlannerSupport.formatServingsLabel(meal.servings)}${if (nutrition[i].isPartial) " • niepełne dane" else ""}" }, adjusted, nutritionComplete = nutrition.none { it.isPartial || it.calories == null })
                val updated = (readDays().filter { it.date != today } + day).sortedByDescending { it.date }.take(730)
                val json = encodeDays(updated)
                if (prefs.getString("days", "") != json) prefs.edit().putString("days", json).apply()
                _days.value = updated
            }
        }
    }
    fun setGoals(produce: Int, water: Int) {
        require(produce in 100..2000 && water in 250..10000)
        prefs.edit().putInt("produceGoal", produce).putInt("waterGoal", water).apply(); refresh()
    }
    fun add(date: String, name: String, grams: Float, category: String) {
        LocalDate.parse(date); require(name.isNotBlank() && name.length <= 80 && grams.isFinite() && grams in 1f..10000f)
        require(category in listOf("fruit", "vegetable"))
        saveManual(date, manual(date) + ProduceEntry(UUID.randomUUID().toString(), name.trim(), grams, category, false))
    }
    fun remove(date: String, key: String) = saveManual(date, manual(date).filter { it.key != key })
    fun correct(date: String, entry: ProduceEntry, grams: Float?, category: String) {
        LocalDate.parse(date); require(category in listOf("fruit", "vegetable", "other"))
        require(grams == null || grams.isFinite() && grams in 0f..10000f)
        val all = overrides(); val day = all.optJSONObject(date) ?: JSONObject()
        val value = JSONObject().put("category", category).put("portionScale", entry.portionScale); grams?.let { value.put("grams", it) }
        day.put(entry.key, value); all.put(date, day)
        prefs.edit().putString("overrides", trimDates(all).toString()).apply(); refresh()
    }
    fun refresh() { _days.value = readDays(); revision.value += 1 }
    private fun trimDates(root: JSONObject): JSONObject {
        root.keys().asSequence().toList().sortedDescending().drop(730).forEach { root.remove(it) }
        return root
    }
    private fun overrides() = JSONObject(prefs.getString("overrides", "{}").orEmpty())
    private fun manual(date: String): List<ProduceEntry> = decodeEntries(JSONObject(prefs.getString("manual", "{}").orEmpty()).optJSONArray(date) ?: JSONArray())
    private fun saveManual(date: String, entries: List<ProduceEntry>) {
        val all = JSONObject(prefs.getString("manual", "{}").orEmpty()); all.put(date, encodeEntries(entries))
        prefs.edit().putString("manual", trimDates(all).toString()).apply(); refresh()
    }
    private fun readDays(): List<HabitDay> = runCatching { decodeDays(prefs.getString("days", "[]").orEmpty()) }.getOrDefault(emptyList())
    companion object {
        private fun encodeEntries(entries: List<ProduceEntry>) = JSONArray().apply { entries.forEach { e ->
            put(JSONObject().put("key", e.key).put("name", e.name).put("grams", e.grams ?: JSONObject.NULL).put("category", e.category).put("automatic", e.automatic).put("portionScale", e.portionScale).put("reviewed", e.reviewed))
        } }
        private fun decodeEntries(array: JSONArray): List<ProduceEntry> = (0 until array.length()).map { idx ->
            val e = array.getJSONObject(idx); ProduceEntry(e.getString("key"), e.getString("name"),
                if (e.isNull("grams")) null else e.getDouble("grams").toFloat(), e.getString("category"), e.getBoolean("automatic"), e.optDouble("portionScale", 1.0).toFloat(), e.optBoolean("reviewed", false))
        }
        private fun encodeDays(days: List<HabitDay>): String = JSONArray().apply { days.forEach { d ->
            put(JSONObject().put("date", d.date).put("calories", d.calories).put("protein", d.protein).put("carbs", d.carbs).put("fat", d.fat)
                .put("drinksMl", d.drinksMl).put("targetCalories", d.targetCalories).put("produceGoal", d.produceGoal).put("waterGoal", d.waterGoal)
                .put("nutritionComplete", d.nutritionComplete).put("meals", JSONArray(d.meals)).put("produce", encodeEntries(d.produce)))
        } }.toString()
        private fun decodeDays(json: String): List<HabitDay> { val a = JSONArray(json); return (0 until a.length()).map { i ->
            val d = a.getJSONObject(i); val meals = d.getJSONArray("meals")
            HabitDay(d.getString("date"), d.getInt("calories"), d.getInt("protein"), d.getInt("carbs"), d.getInt("fat"), d.getInt("drinksMl"),
                d.getInt("targetCalories"), d.getInt("produceGoal"), d.getInt("waterGoal"), (0 until meals.length()).map { meals.getString(it) }, decodeEntries(d.getJSONArray("produce")), d.optBoolean("nutritionComplete", true))
        } }
        fun export(context: Context): String {
            val p = context.getSharedPreferences("FITMangoHabits", Context.MODE_PRIVATE)
            return JSONObject().put("days", JSONArray(p.getString("days", "[]"))).put("manual", JSONObject(p.getString("manual", "{}")))
                .put("overrides", JSONObject(p.getString("overrides", "{}"))).put("weeklyEnergy", JSONObject(p.getString("weeklyEnergy", "{}"))).put("produceGoal", p.getInt("produceGoal", 400)).put("waterGoal", p.getInt("waterGoal", 2500)).toString()
        }
        fun validate(json: String) {
            if (json.isBlank()) return
            require(json.length <= 2_000_000)
            val root = JSONObject(json); root.optJSONObject("weeklyEnergy")?.let { WeeklyEnergyDiary.validate(it) }; val days = decodeDays(root.getJSONArray("days").toString()); require(days.size <= 730)
            require(days.map { it.date }.distinct().size == days.size)
            days.forEach { d -> LocalDate.parse(d.date); require(listOf(d.calories, d.protein, d.carbs, d.fat, d.drinksMl).all { it >= 0 })
                require(d.produceGoal in 100..2000 && d.waterGoal in 250..10000 && d.meals.size <= 1000 && d.produce.size <= 10000)
                d.produce.forEach { e -> require(e.category in listOf("fruit", "vegetable", "other") && e.name.length <= 500 && e.portionScale.isFinite() && e.portionScale > 0 && (e.grams == null || e.grams.isFinite() && e.grams in 0f..100000f)) }
            }
            require(root.getInt("produceGoal") in 100..2000 && root.getInt("waterGoal") in 250..10000)
            val manual = root.getJSONObject("manual"); require(manual.length() <= 730)
            manual.keys().forEach { date -> LocalDate.parse(date); val entries = decodeEntries(manual.getJSONArray(date)); require(entries.size <= 10000)
                entries.forEach { e -> require(!e.automatic && e.category in listOf("fruit", "vegetable") && e.grams != null && e.grams.isFinite() && e.grams in 1f..10000f && e.name.length <= 80) }
            }
            val overrides = root.getJSONObject("overrides"); require(overrides.length() <= 730)
            overrides.keys().forEach { date -> LocalDate.parse(date); val day = overrides.getJSONObject(date); require(day.length() <= 10000)
                day.keys().forEach { key -> val v = day.getJSONObject(key); require(v.getString("category") in listOf("fruit", "vegetable", "other"))
                    require(v.optDouble("portionScale", 1.0).let { it.isFinite() && it > 0 })
                    if (v.has("grams")) { val g = v.getDouble("grams"); require(g.isFinite() && g in 0.0..10000.0) }
                }
            }
        }
        fun restore(context: Context, json: String) {
            validate(json)
            val p = context.getSharedPreferences("FITMangoHabits", Context.MODE_PRIVATE)
            if (json.isBlank()) { p.edit().clear().apply(); return }
            val r = JSONObject(json); p.edit().putString("days", r.getJSONArray("days").toString()).putString("manual", r.getJSONObject("manual").toString())
                .putString("overrides", r.getJSONObject("overrides").toString()).putString("weeklyEnergy", (r.optJSONObject("weeklyEnergy") ?: JSONObject()).toString()).putInt("produceGoal", r.getInt("produceGoal")).putInt("waterGoal", r.getInt("waterGoal")).apply()
        }
    }
}
