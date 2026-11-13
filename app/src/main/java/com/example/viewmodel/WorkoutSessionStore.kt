package com.example.viewmodel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WorkoutEntry(val exercise: String, val number: Int, val reps: Int, val kg: Double, val done: Boolean)
data class WorkoutSession(val id: String, val date: String, val title: String, val started: Long,
    val entries: List<WorkoutEntry>, val ended: Long = 0L, val effort: Int = 0,
    val pausedAt: Long = 0L, val pausedMillis: Long = 0L)

object WorkoutSessionStore {
    private const val KEY = "strength_workouts_v1"
    private fun encode(s: WorkoutSession): JSONObject = JSONObject()
        .put("id", s.id).put("date", s.date).put("title", s.title).put("started", s.started)
        .put("ended", s.ended).put("effort", s.effort)
        .put("pausedAt", s.pausedAt).put("pausedMillis", s.pausedMillis)
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
            o.getLong("started"), entries, o.optLong("ended", 0), o.optInt("effort", 0),
            o.optLong("pausedAt", 0), o.optLong("pausedMillis", 0))
        require(s.id.length in 1..200 && s.title.length in 1..120 && s.started > 0 &&
            (s.ended == 0L || s.ended >= s.started) && s.effort in 0..10 &&
            s.pausedMillis >= 0 && (s.pausedAt == 0L || s.pausedAt >= s.started))
        java.time.LocalDate.parse(s.date)
        entries.forEach { require(it.exercise.length in 1..120 && it.number in 1..12 &&
            it.reps in 1..1000 && it.kg.isFinite() && it.kg in 0.0..2000.0) }
        return s
    }
    /** Time spent exercising, excluding pauses. Existing records default to zero paused time. */
    fun activeDurationMillis(s: WorkoutSession, now: Long = System.currentTimeMillis()): Long {
        val stop = if (s.ended > 0L) s.ended else if (s.pausedAt > 0L) s.pausedAt else now
        return (stop - s.started - s.pausedMillis).coerceAtLeast(0L)
    }
    fun pause(s: WorkoutSession, now: Long): WorkoutSession =
        if (s.ended > 0L || s.pausedAt > 0L) s else s.copy(pausedAt = now.coerceAtLeast(s.started))
    fun resume(s: WorkoutSession, now: Long): WorkoutSession =
        if (s.ended > 0L || s.pausedAt == 0L) s else
            s.copy(pausedMillis = s.pausedMillis + (now - s.pausedAt).coerceAtLeast(0L), pausedAt = 0L)
    fun finish(s: WorkoutSession, now: Long, effort: Int): WorkoutSession {
        val stopped = now.coerceAtLeast(s.started)
        val paused = if (s.pausedAt > 0L)
            s.pausedMillis + (stopped - s.pausedAt).coerceAtLeast(0L) else s.pausedMillis
        return s.copy(ended = stopped, effort = effort, pausedAt = 0L, pausedMillis = paused)
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
        val prefs = context.getSharedPreferences("FITMangoUsability", 0)
        // Keep a last-known-good snapshot before overwriting workout history.
        // A failed snapshot write must not silently discard the previous data.
        val previous = prefs.getString(KEY, "[]") ?: "[]"
        if (previous != raw) {
            validateRaw(previous)
            check(prefs.edit().putString("${KEY}_restore_point", previous).commit())
        }
        check(prefs.edit().putString(KEY, raw).commit())
    }
    /** Recovery is explicit; never silently replace the primary workout history. */
    fun restorePoint(context: Context): List<WorkoutSession>? {
        val raw = context.getSharedPreferences("FITMangoUsability", 0)
            .getString("${KEY}_restore_point", null) ?: return null
        return parse(raw)
    }
    /** Restore only after explicit confirmation; validate first, keep a snapshot of current data. */
    fun restorePrevious(context: Context): Int {
        val prefs = context.getSharedPreferences("FITMangoUsability", 0)
        val backup = prefs.getString("${KEY}_restore_point", null)
            ?: throw IllegalStateException("Brak punktu przywracania")
        val restored = parse(backup)
        require(restored.count { it.ended == 0L } <= 1)
        val current = prefs.getString(KEY, "[]") ?: "[]"
        validateRaw(current)
        check(prefs.edit().putString(KEY, backup)
            .putString("${KEY}_restore_point", current).commit())
        return restored.size
    }
    /** Validated JSON export of the existing workout history; no API keys or profile data. */
    fun exportJson(context: Context): String {
        val raw = context.getSharedPreferences("FITMangoUsability", 0)
            .getString(KEY, "[]") ?: "[]"
        validateRaw(raw)
        return raw
    }
    /** Explicit import from an exported JSON document. Validation happens before any write.
     * A single atomic preference commit stores the previous history as a restore point. */
    fun importJson(context: Context, raw: String): Int {
        require(raw.toByteArray(Charsets.UTF_8).size <= 2_000_000) { "Plik jest za duży" }
        val imported = parse(raw)
        require(imported.count { it.ended == 0L } <= 1) { "Za dużo aktywnych sesji" }
        require(imported.map { it.id }.distinct().size == imported.size) { "Powtórzone identyfikatory" }
        val prefs = context.getSharedPreferences("FITMangoUsability", 0)
        val previous = prefs.getString(KEY, "[]") ?: "[]"
        validateRaw(previous)
        val normalized = JSONArray().also { a -> imported.forEach { a.put(encode(it)) } }.toString()
        if (normalized != previous) {
            check(prefs.edit().putString("${KEY}_restore_point", previous)
                .putString(KEY, normalized).commit()) { "Nie udało się zapisać historii" }
        }
        return imported.size
    }
    fun create(plan: SportPlan, date: String, now: Long = System.currentTimeMillis()): WorkoutSession {
        require(!plan.rest && plan.exercises.isNotEmpty())
        return WorkoutSession(UUID.randomUUID().toString(), date, plan.title, now,
            plan.exercises.flatMap { e -> (1..e.sets).map { n ->
                WorkoutEntry(e.name, n, e.reps, 0.0, false)
            } })
    }
}
