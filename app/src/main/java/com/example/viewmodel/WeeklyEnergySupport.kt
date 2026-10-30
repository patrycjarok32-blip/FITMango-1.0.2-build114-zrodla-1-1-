package com.example.viewmodel

import android.content.Context
import android.content.SharedPreferences
import com.example.data.WeightLogEntity
import com.example.data.UserProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.security.MessageDigest

data class DailyEnergy(val date: String, val totalKcal: Double?, val fullDayWindow: Boolean, val syncedAt: Long)
data class EnergyConfirmation(val date: String, val foodFingerprint: String, val burnedKcal: Double, val source: String = "health", val acceptsPartial: Boolean = false)
data class WeeklyEnergyAnalysis(val includedDates: List<String>, val eatenKcal: Int, val burnedKcal: Double) {
    val deficitKcal get() = burnedKcal - eatenKcal
    // A static energy equivalent, explicitly not a dynamic prediction of scale weight.
    val energyEquivalentKg get() = -deficitKcal / 7700.0
}
object WeeklyEnergySupport {
    /** Same maintenance equation and activity factors as Profile, before applying its goal adjustment. */
    fun maintenanceCalories(profile: UserProfileEntity?, settings: EnergyProfileSettings? = null): Double? =
        EnergyCalculatorSupport.base(profile, settings)?.maintenance
    fun fingerprint(day: HabitDay): String = MessageDigest.getInstance("SHA-256")
        .digest("${day.date}|${day.calories}|${day.protein}|${day.carbs}|${day.fat}|${day.meals.joinToString(";")}".toByteArray())
        .joinToString("") { "%02x".format(it) }
    /** Food approval survives energy refreshes and switching the expenditure basis.
     * The expenditure snapshot is retained for diagnostics, never used as a food approval key. */
    fun isFoodConfirmed(day: HabitDay, confirmation: EnergyConfirmation?): Boolean =
        confirmation != null && day.meals.isNotEmpty() &&
            (day.nutritionComplete || confirmation.acceptsPartial) && confirmation.foodFingerprint == fingerprint(day)
    fun analyze(days: List<HabitDay>, energy: List<DailyEnergy>, confirmations: List<EnergyConfirmation>, start: LocalDate, end: LocalDate, today: LocalDate, source: String = "health"): WeeklyEnergyAnalysis {
        require(source in listOf("health", "profile"))
        val included = days.distinctBy { it.date }.filter { d ->
            val date = LocalDate.parse(d.date)
            val e = energy.find { it.date == d.date }
            val c = confirmations.find { it.date == d.date }
            date >= start && date <= end && date < today && isFoodConfirmed(d, c) &&
                e != null && e.fullDayWindow && e.totalKcal != null && e.totalKcal.isFinite() && e.totalKcal > 0
        }
        return WeeklyEnergyAnalysis(included.map { it.date }.sorted(), included.sumOf { it.calories },
            included.sumOf { d -> energy.first { it.date == d.date }.totalKcal!! })
    }
    fun weightChange(logs: List<WeightLogEntity>, start: LocalDate, end: LocalDate): Float? {
        val points = logs.filter { it.date >= start.toString() && it.date <= end.toString() && it.weightKg.isFinite() && it.weightKg > 0 }
            .groupBy { it.date }.map { (_, rows) -> rows.maxBy { it.id } }.sortedBy { it.date }
        return if (points.size < 2) null else points.last().weightKg - points.first().weightKg
    }
}
/** Kept with the existing backed-up habits preferences; no Room schema migration. */
class WeeklyEnergyDiary(context: Context, scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("FITMangoHabits", Context.MODE_PRIVATE)
    private val _energy = MutableStateFlow(readEnergy())
    val energy = _energy.asStateFlow()
    private val _confirmations = MutableStateFlow(readConfirmations())
    val confirmations = _confirmations.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == "weeklyEnergy") { _energy.value = readEnergy(); _confirmations.value = readConfirmations() }
    }
    init { prefs.registerOnSharedPreferenceChangeListener(listener); scope.coroutineContext[Job]?.invokeOnCompletion { prefs.unregisterOnSharedPreferenceChangeListener(listener) } }
    private fun root() = runCatching { JSONObject(prefs.getString("weeklyEnergy", "{}").orEmpty()) }.getOrDefault(JSONObject())
    private fun readEnergy(): List<DailyEnergy> = runCatching {
        val a = root().optJSONArray("energy") ?: JSONArray()
        (0 until a.length()).map { i -> val d = a.getJSONObject(i); DailyEnergy(d.getString("date"), if (d.isNull("totalKcal")) null else d.getDouble("totalKcal"), d.getBoolean("fullDayWindow"), d.getLong("syncedAt")) }
    }.getOrDefault(emptyList())
    private fun readConfirmations(): List<EnergyConfirmation> = runCatching {
        val a = root().optJSONArray("confirmations") ?: JSONArray()
        (0 until a.length()).map { i -> val d = a.getJSONObject(i); EnergyConfirmation(d.getString("date"), d.getString("foodFingerprint"), d.getDouble("burnedKcal"), d.optString("source", "health"), d.optBoolean("acceptsPartial", false)) }
    }.getOrDefault(emptyList())
    @Synchronized
    fun saveEnergy(rows: List<DailyEnergy>) {
        rows.forEach { LocalDate.parse(it.date); require(it.totalKcal == null || it.totalKcal.isFinite() && it.totalKcal >= 0) }
        val merged = (readEnergy().filter { old -> rows.none { it.date == old.date } } + rows).sortedByDescending { it.date }.take(730)
        val r = root(); r.put("energy", JSONArray().apply { merged.forEach { put(JSONObject().put("date", it.date).put("totalKcal", it.totalKcal ?: JSONObject.NULL).put("fullDayWindow", it.fullDayWindow).put("syncedAt", it.syncedAt)) } })
        prefs.edit().putString("weeklyEnergy", r.toString()).apply(); _energy.value = merged
    }
    @Synchronized
    fun confirm(day: HabitDay, today: LocalDate, estimatedTdee: Double? = null, acceptPartial: Boolean = false) {
        require(LocalDate.parse(day.date) < today && day.meals.isNotEmpty() && (day.nutritionComplete || acceptPartial))
        val e = if (estimatedTdee != null) DailyEnergy(day.date, estimatedTdee, true, 0L)
            else requireNotNull(readEnergy().firstOrNull { it.date == day.date })
        val kcal = requireNotNull(e.totalKcal)
        require(e.fullDayWindow && kcal.isFinite() && kcal > 0)
        val rows = (readConfirmations().filter { it.date != day.date } + EnergyConfirmation(day.date, WeeklyEnergySupport.fingerprint(day), kcal, if (estimatedTdee != null) "profile" else "health", acceptPartial && !day.nutritionComplete)).sortedByDescending { it.date }.take(730)
        val r = root(); r.put("confirmations", JSONArray().apply { rows.forEach { put(JSONObject().put("date", it.date).put("foodFingerprint", it.foodFingerprint).put("burnedKcal", it.burnedKcal).put("source", it.source).put("acceptsPartial", it.acceptsPartial)) } })
        prefs.edit().putString("weeklyEnergy", r.toString()).apply(); _confirmations.value = rows
    }
    @Synchronized
    fun reopen(date: String) {
        val r = root(); r.put("confirmations", JSONArray().apply { readConfirmations().filter { it.date != date }.forEach { put(JSONObject().put("date", it.date).put("foodFingerprint", it.foodFingerprint).put("burnedKcal", it.burnedKcal).put("source", it.source).put("acceptsPartial", it.acceptsPartial)) } })
        prefs.edit().putString("weeklyEnergy", r.toString()).apply(); _confirmations.value = readConfirmations()
    }
    companion object {
        fun validate(root: JSONObject) {
            for (key in listOf("energy", "confirmations")) {
                val a = root.optJSONArray(key) ?: continue; require(a.length() <= 730)
                val dates = mutableSetOf<String>()
                for (i in 0 until a.length()) {
                    val row = a.getJSONObject(i); val date = row.getString("date"); LocalDate.parse(date); require(dates.add(date))
                    if (key == "energy") { if (!row.isNull("totalKcal")) require(row.getDouble("totalKcal").let { it.isFinite() && it >= 0 }); row.getBoolean("fullDayWindow"); require(row.getLong("syncedAt") >= 0) }
                    else { require(row.optString("source", "health") in listOf("health", "profile")); require(row.getDouble("burnedKcal").let { it.isFinite() && it > 0 }); require(row.getString("foodFingerprint").matches(Regex("[0-9a-f]{64}"))) }
                }
            }
        }
    }
}
