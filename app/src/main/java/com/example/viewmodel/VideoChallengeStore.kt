package com.example.viewmodel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class VideoWorkout(val id: String, val day: Int, val title: String, val minutes: Int,
    val category: String, val url: String, val completedAt: Long = 0)
data class VideoChallenge(val id: String, val title: String, val days: Int,
    val workouts: List<VideoWorkout>)

object VideoChallengeStore {
    private const val KEY = "video_challenges_v1"
    private fun validate(challenges: List<VideoChallenge>) {
        require(challenges.size <= 100 && challenges.map { it.id }.distinct().size == challenges.size)
        challenges.forEach { c ->
            require(c.id.isNotBlank() && c.title.length in 1..120 && c.days in 1..365 &&
                c.workouts.size <= 1000)
            require(c.workouts.map { it.id }.distinct().size == c.workouts.size)
            c.workouts.forEach { w ->
                require(w.id.isNotBlank() && w.day in 1..c.days && w.title.length in 1..120 &&
                    w.minutes in 1..300 && w.category.length in 1..60 && w.completedAt >= 0)
                require(w.url.isBlank() || ((w.url.startsWith("https://www.youtube.com/watch?") ||
                    w.url.startsWith("https://youtube.com/watch?") ||
                    w.url.startsWith("https://youtu.be/") ||
                    w.url.startsWith("https://www.youtube.com/shorts/") ||
                    w.url.startsWith("https://youtube.com/shorts/")) && w.url.length <= 500))
            }
        }
    }
    fun validateRaw(raw: String) { parse(raw) }
    private fun parse(raw: String): List<VideoChallenge> {
        val a = JSONArray(raw)
        require(a.length() <= 100)
        return (0 until a.length()).map { i ->
            val c = a.getJSONObject(i)
            val entries = c.getJSONArray("workouts")
            require(entries.length() <= 1000)
            VideoChallenge(c.getString("id"), c.getString("title"), c.getInt("days"),
                (0 until entries.length()).map { j -> entries.getJSONObject(j).let { w ->
                    VideoWorkout(w.getString("id"), w.getInt("day"), w.getString("title"),
                        w.getInt("minutes"), w.optString("category", "Inne"),
                        w.optString("url", ""), w.optLong("completedAt", 0))
                } })
        }.also(::validate)
    }
    fun read(context: Context): List<VideoChallenge> = parse(context.getSharedPreferences("FITMangoUsability", 0).getString(KEY, "[]") ?: "[]")
    fun write(context: Context, challenges: List<VideoChallenge>) {
        validate(challenges)
        val a = JSONArray()
        challenges.forEach { c ->
            a.put(JSONObject().put("id", c.id).put("title", c.title).put("days", c.days)
                .put("workouts", JSONArray().also { list -> c.workouts.forEach { w ->
                    list.put(JSONObject().put("id", w.id).put("day", w.day).put("title", w.title)
                        .put("minutes", w.minutes).put("category", w.category)
                        .put("url", w.url).put("completedAt", w.completedAt))
                } }))
        }
        check(context.getSharedPreferences("FITMangoUsability", 0).edit()
            .putString(KEY, a.toString()).commit())
    }
    fun addChallenge(items: List<VideoChallenge>, title: String, weeks: Int): List<VideoChallenge> {
        require(title.trim().length in 1..120 && weeks in 1..52)
        return items + VideoChallenge(UUID.randomUUID().toString(), title.trim(), weeks * 7, emptyList())
    }
    fun duplicate(items: List<VideoChallenge>, id: String): List<VideoChallenge> {
        val original = items.first { it.id == id }
        return items + original.copy(id = UUID.randomUUID().toString(),
            title = (original.title.take(110) + " (kopia)"),
            workouts = original.workouts.map { it.copy(id = UUID.randomUUID().toString(), completedAt = 0) })
    }
    fun duplicateWeek(items: List<VideoChallenge>, id: String, fromWeek: Int, toWeek: Int): List<VideoChallenge> =
        items.map { c ->
            if (c.id != id) c else {
                require(fromWeek >= 1 && toWeek >= 1 && fromWeek != toWeek &&
                    toWeek * 7 - 6 <= c.days && fromWeek * 7 - 6 <= c.days)
                val copied = c.workouts.filter { (it.day - 1) / 7 + 1 == fromWeek }.map { w ->
                    w.copy(id = UUID.randomUUID().toString(),
                        day = (toWeek - 1) * 7 + (w.day - 1) % 7 + 1, completedAt = 0)
                }
                c.copy(workouts = c.workouts.filterNot { (it.day - 1) / 7 + 1 == toWeek } + copied)
            }
        }
}
