package com.example.viewmodel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WorkoutEntry(val exercise: String, val number: Int, val reps: Int, val kg: Double, val done: Boolean)
data class WorkoutSession(val id: String, val date: String, val title: String, val started: Long,
    val entries: List<WorkoutEntry>, val ended: Long = 0L, val effort: Int = 0)

object WorkoutSessionStore {
    private const val KEY = "strength_workouts_v1"
    private fun encode(s: WorkoutSession): JSONObject = JSONObject()
        .put("id", s.id).put("date", s.date).put("title", s.title).put("started", s.started)
        .put("ended", s.ended).put("effort", s.effort)
        .put("entries", JSONArray().also { a -> s.entries.forEach { e ->
            a.put(JSONObject().put("exercise", e.exercise).put("number", e.number)
                .put("reps", e.reps).put("kg", e.kg).put("done", e.done))
        } })
    private fun decode(o: JSONObject): WorkoutSession {
        val a = o.getJSONArray("entries")
        require(a.length() <= 360)
        val entries = (0 until a.length()).map { i -> a.getJSONObject(i).let { e ->
            WorkoutEntry(e.getString("exercise"), e.getInt("number"), e.getInt("reps"),
                e.getDouble("kg"), e.getBoolean("done"))
        } }
        val s = WorkoutSession(o.getString("id"), o.getString("date"), o.getString("title"),
            o.getLong("started"), entries, o.optLong("ended", 0), o.optInt("effort", 0))
        require(s.id.length in 1..200 && s.title.length in 1..120 && s.started > 0 &&
            (s.ended == 0L || s.ended >= s.started) && s.effort in 0..10)
        java.time.LocalDate.parse(s.date)
        entries.forEach { require(it.exercise.length in 1..120 && it.number in 1..12 &&
            it.reps in 1..1000 && it.kg.isFinite() && it.kg in 0.0..2000.0) }
        return s
    }
    fun validateRaw(raw: String) { parse(raw) }
    private fun parse(raw: String): List<WorkoutSession> {
        val a = JSONArray(raw)
        require(a.length() <= 200)
        return (0 until a.length()).map { decode(a.getJSONObject(it)) }
    }
    fun read(context: Context): List<WorkoutSession> = parse(context.getSharedPreferences("FITMangoUsability", 0).getString(KEY, "[]") ?: "[]")
    fun write(context: Context, sessions: List<WorkoutSession>) {
        require(sessions.size <= 200 && sessions.count { it.ended == 0L } <= 1)
        val raw = JSONArray().also { a -> sessions.forEach { a.put(encode(it)) } }.toString()
        check(context.getSharedPreferences("FITMangoUsability", 0).edit().putString(KEY, raw).commit())
    }
    fun create(plan: SportPlan, date: String, now: Long = System.currentTimeMillis()): WorkoutSession {
        require(!plan.rest && plan.exercises.isNotEmpty())
        return WorkoutSession(UUID.randomUUID().toString(), date, plan.title, now,
            plan.exercises.flatMap { e -> (1..e.sets).map { n ->
                WorkoutEntry(e.name, n, e.reps, 0.0, false)
            } })
    }
}
