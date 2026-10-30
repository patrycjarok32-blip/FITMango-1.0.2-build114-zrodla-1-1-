package com.example.viewmodel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.Instant

data class SportSet(val id: String, val date: String, val exercise: String, val reps: Int, val kg: Double, val time: Long)
data class SportPlan(val date: String, val title: String, val rest: Boolean)
data class SportSession(val id: String, val title: String, val start: String, val end: String, val source: String,
    val activeKcal: Double? = null, val distanceKm: Double? = null, val activityType: Int? = null)
data class SportsData(val sets: List<SportSet> = emptyList(), val plans: List<SportPlan> = emptyList(),
    val history: List<SportSession> = emptyList(), val updated: Long = 0, val goal: Int = 3,
    val daily: List<ActivityDay> = emptyList(), val recovery: List<RecoveryCheck> = emptyList(),
    val goalMode: String = "days", val minuteGoal: Int = 150, val walkGoal: Int = 3,
    val healthUpdated: Long = 0, val readNote: String = "")

object SportsSupport {
    const val KEY = "sports_v1"
    fun week(date: LocalDate) = date.minusDays(date.dayOfWeek.value - 1L)
    fun activeDays(data: SportsData, date: LocalDate): Int {
        val from = week(date); val until = from.plusDays(6)
        val days = ActivityRecoverySupport.unique(data.history).map { Instant.parse(it.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
        return days.filter { it >= from && it <= until && it <= LocalDate.now() }.distinct().size
    }

    fun previous(data: SportsData, exercise: String, date: String): List<SportSet> {
        val candidates = data.sets.filter { it.exercise.trim().equals(exercise.trim(), true) && it.date < date }
        val last = candidates.maxOfOrNull { it.date } ?: return emptyList()
        return candidates.filter { it.date == last }.sortedBy { it.time }
    }
    fun encode(data: SportsData): String = JSONObject().put("goal", data.goal).put("updated", data.updated)
        .put("goalMode", data.goalMode).put("minuteGoal", data.minuteGoal).put("walkGoal", data.walkGoal)
        .put("healthUpdated", data.healthUpdated).put("readNote", data.readNote)
        .put("daily", JSONArray().also { a -> data.daily.forEach { d -> a.put(JSONObject().put("date", d.date)
            .put("steps", d.steps ?: JSONObject.NULL).put("sleep", d.sleepMinutes ?: JSONObject.NULL)
            .put("sleepEstimated", d.sleepEstimated).put("sources", d.sources).put("readAt", d.readAt)) } })
        .put("recovery", JSONArray().also { a -> data.recovery.forEach { d -> a.put(JSONObject().put("date", d.date)
            .put("energy", d.energy).put("fatigue", d.fatigue)) } })
        .put("sets", JSONArray().also { array -> data.sets.forEach { s -> array.put(JSONObject().put("id", s.id).put("date", s.date)
            .put("exercise", s.exercise).put("reps", s.reps).put("kg", s.kg).put("time", s.time)) } })
        .put("plans", JSONArray().also { array -> data.plans.forEach { p -> array.put(JSONObject().put("date", p.date).put("title", p.title).put("rest", p.rest)) } })
        .put("history", JSONArray().also { array -> data.history.forEach { h -> array.put(JSONObject().put("id", h.id).put("title", h.title)
            .put("start", h.start).put("end", h.end).put("source", h.source).put("kcal", h.activeKcal ?: JSONObject.NULL)
            .put("km", h.distanceKm ?: JSONObject.NULL).put("type", h.activityType ?: JSONObject.NULL)) } }).toString()
    fun decode(raw: String): SportsData {
        if (raw.isBlank()) return SportsData()
        val json = JSONObject(raw)
        fun rows(key: String): List<JSONObject> {
            val a = json.optJSONArray(key) ?: JSONArray(); require(a.length() <= 50_000)
            return (0 until a.length()).map { a.getJSONObject(it) }
        }
        val data = SportsData(rows("sets").map { SportSet(it.getString("id"), it.getString("date"), it.getString("exercise"),
            it.getInt("reps"), it.getDouble("kg"), it.getLong("time")) },
            rows("plans").map { SportPlan(it.getString("date"), it.getString("title"), it.getBoolean("rest")) },
            rows("history").map { SportSession(it.getString("id"), it.getString("title"), it.getString("start"), it.getString("end"),
                it.getString("source"), if (it.isNull("kcal")) null else it.getDouble("kcal"), if (it.isNull("km")) null else it.getDouble("km"), if (it.isNull("type")) null else it.getInt("type")) },
            json.optLong("updated", 0), json.optInt("goal", 3),
            rows("daily").map { ActivityDay(it.getString("date"), if (it.isNull("steps")) null else it.getLong("steps"),
                if (it.isNull("sleep")) null else it.getLong("sleep"), it.optBoolean("sleepEstimated", false),
                it.optString("sources", "Health Connect"), it.optLong("readAt", 0)) },
            rows("recovery").map { RecoveryCheck(it.getString("date"), it.getInt("energy"), it.getInt("fatigue")) },
            json.optString("goalMode", "days"), json.optInt("minuteGoal", 150), json.optInt("walkGoal", 3),
            json.optLong("healthUpdated", 0), json.optString("readNote", ""))
        require(data.goal in 1..7 && data.updated >= 0)
        require(data.goalMode in setOf("days", "minutes", "walks") && data.minuteGoal in 10..1500 && data.walkGoal in 1..21)
        require(data.healthUpdated >= 0 && data.readNote.length <= 2000)
        require(data.daily.size <= 400 && data.recovery.size <= 730)
        require(data.daily.map { it.date }.distinct().size == data.daily.size && data.recovery.map { it.date }.distinct().size == data.recovery.size)
        data.daily.forEach { LocalDate.parse(it.date); require((it.steps == null || it.steps in 0..1000000L) &&
            (it.sleepMinutes == null || it.sleepMinutes in 1..1440L) && it.readAt >= 0 && it.sources.length <= 2000) }
        data.recovery.forEach { LocalDate.parse(it.date); require(it.energy in 1..5 && it.fatigue in 1..5) }
        require(data.sets.map { it.id }.distinct().size == data.sets.size && data.plans.map { it.date }.distinct().size == data.plans.size)
        require(data.history.map { it.id }.distinct().size == data.history.size)
        data.sets.forEach { require(it.id.length in 1..200 && it.exercise.length in 1..120 && it.reps in 1..1000 && it.kg.isFinite() && it.kg in 0.0..2000.0 && it.time >= 0); LocalDate.parse(it.date) }
        data.plans.forEach { require(it.title.length in 1..120); LocalDate.parse(it.date) }
        data.history.forEach { require(it.id.length in 1..500 && it.title.length in 1..200 && it.source.length <= 200)
            require(Instant.parse(it.end) > Instant.parse(it.start))
            require(it.activeKcal == null || it.activeKcal.isFinite() && it.activeKcal >= 0)
            require(it.distanceKm == null || it.distanceKm.isFinite() && it.distanceKm >= 0) }
        return data
    }
    fun read(context: Context) = decode(context.getSharedPreferences("FITMangoUsability", 0).getString(KEY, "").orEmpty())
    fun write(context: Context, data: SportsData) {
        val raw = encode(data); decode(raw)
        check(context.getSharedPreferences("FITMangoUsability", 0).edit().putString(KEY, raw).commit()) { "Nie udało się zapisać treningu." }
    }
}

data class RestTimer(val deadline: Long = 0, val paused: Long = 0) {
    fun seconds(now: Long) = ((if (deadline > 0) (deadline - now).coerceAtLeast(0) else paused.coerceAtLeast(0)) + 999) / 1000
    fun pause(now: Long) = RestTimer(paused = (deadline - now).coerceAtLeast(0))
    fun resume(now: Long) = if (paused > 0) RestTimer(now + paused) else this
    companion object { fun start(seconds: Int, now: Long): RestTimer { require(seconds in 10..3600); return RestTimer(now + seconds * 1000L) } }
}
