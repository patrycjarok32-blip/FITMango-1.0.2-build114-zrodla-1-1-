package com.example.viewmodel

import android.content.Context
import org.json.JSONObject

/** User consent policy for dietitian-style AI advice. Never changes nutrition targets. */
data class DietitianControlSettings(
    val adviceMode: String = "approve",
    val includeTraining: Boolean = true
) {
    init { require(adviceMode in setOf("approve", "manual", "off")) }
}

object DietitianControlStore {
    private const val PREF = "FITMangoUsability"
    private const val KEY = "dietitian_control_v1"
    fun read(context: Context): DietitianControlSettings {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return DietitianControlSettings()
        return runCatching {
            val j = JSONObject(raw)
            DietitianControlSettings(j.optString("mode", "approve"), j.optBoolean("training", true))
        }.getOrDefault(DietitianControlSettings())
    }
    fun save(context: Context, value: DietitianControlSettings) {
        val raw = JSONObject().put("mode", value.adviceMode)
            .put("training", value.includeTraining).toString()
        check(context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, raw).commit()) { "Nie udało się zapisać ustawień." }
    }
}
