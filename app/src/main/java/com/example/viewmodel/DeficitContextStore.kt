package com.example.viewmodel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Optional self-reported context; no health diagnoses, no automatic conclusions. */
object DeficitContextStore {
    private const val KEY = "deficit_context_v1"
    private val labels = mapOf(
        "cycle" to "Cykl menstruacyjny",
        "strength" to "Intensywny trening",
        "sleep" to "Gorszy sen",
        "salt" to "Więcej soli / nietypowe jedzenie",
        "other" to "Inne wahania / samopoczucie"
    )
    fun options(): Map<String, String> = labels
    fun read(context: Context): Map<String, Set<String>> = runCatching {
        val raw = context.getSharedPreferences("FITMangoUsability", Context.MODE_PRIVATE).getString(KEY, "{}").orEmpty()
        val root = JSONObject(raw)
        root.keys().asSequence().mapNotNull { date ->
            if (runCatching { LocalDate.parse(date) }.isFailure) return@mapNotNull null
            val values = root.optJSONArray(date) ?: return@mapNotNull null
            date to (0 until values.length()).mapNotNull { i ->
                values.optString(i).takeIf { it in labels }
            }.toSet()
        }.toMap()
    }.getOrDefault(emptyMap())
    fun toggle(context: Context, date: LocalDate, code: String): Boolean {
        if (code !in labels) return false
        val existing = read(context).toMutableMap()
        val values = existing[date.toString()].orEmpty().toMutableSet()
        if (!values.add(code)) values.remove(code)
        if (values.isEmpty()) existing.remove(date.toString()) else existing[date.toString()] = values
        val root = JSONObject()
        existing.filterKeys { runCatching { LocalDate.parse(it) >= date.minusDays(365) }.getOrDefault(false) }
            .forEach { (day, items) -> root.put(day, JSONArray(items.sorted())) }
        return context.getSharedPreferences("FITMangoUsability", Context.MODE_PRIVATE)
            .edit().putString(KEY, root.toString()).commit()
    }
}
