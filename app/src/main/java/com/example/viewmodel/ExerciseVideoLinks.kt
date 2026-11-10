package com.example.viewmodel

import android.content.Context
import org.json.JSONObject

/** User-owned instruction links; stored in the same preferences included in FITMango backups. */
object ExerciseVideoLinks {
    const val KEY = "exercise_video_links_v1"
    fun validate(raw: String) {
        val o = JSONObject(raw)
        require(o.length() <= 300)
        o.keys().forEach { key ->
            val url = o.getString(key)
            require(key.length in 1..120 && url.length <= 500 &&
                (url.isBlank() || url.startsWith("https://youtu.be/") ||
                    url.startsWith("https://www.youtube.com/watch?") ||
                    url.startsWith("https://youtube.com/watch?")))
        }
    }
    fun read(context: Context, name: String): String {
        val raw = context.getSharedPreferences("FITMangoUsability", 0).getString(KEY, "{}") ?: "{}"
        return runCatching { validate(raw); JSONObject(raw).optString(name, "") }.getOrDefault("")
    }
    fun save(context: Context, name: String, url: String) {
        require(name.length in 1..120)
        val prefs = context.getSharedPreferences("FITMangoUsability", 0)
        val o = JSONObject(prefs.getString(KEY, "{}") ?: "{}")
        if (url.isBlank()) o.remove(name) else o.put(name, url.trim())
        validate(o.toString())
        check(prefs.edit().putString(KEY, o.toString()).commit())
    }
}
