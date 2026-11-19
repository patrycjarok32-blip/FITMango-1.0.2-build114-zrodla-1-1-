package com.example.viewmodel

import androidx.health.connect.client.records.ExerciseSessionRecord

/** Display the original Health Connect title when available; otherwise use its exercise type. */
object SportSessionPresentation {
    private val names = mapOf(
        "HIGH_INTENSITY_INTERVAL_TRAINING" to "Intensywny trening interwałowy (HIIT)",
        "STRENGTH_TRAINING" to "Trening siłowy",
        "WALKING" to "Spacer",
        "RUNNING" to "Bieganie",
        "BIKING" to "Rower",
        "YOGA" to "Joga",
        "PILATES" to "Pilates",
        "ELLIPTICAL" to "Orbitrek",
        "HIKING" to "Wędrówka",
        "SWIMMING_POOL" to "Pływanie w basenie",
        "SWIMMING_OPEN_WATER" to "Pływanie",
        "DANCING" to "Taniec",
        "ROWING_MACHINE" to "Wioślarz",
        "TREADMILL" to "Bieżnia",
        "CALISTHENICS" to "Kalistenika",
        "STAIR_CLIMBING" to "Schody"
    )
    private val typeNames: Map<Int, String> by lazy {
        ExerciseSessionRecord::class.java.fields.mapNotNull { field ->
            if (!field.name.startsWith("EXERCISE_TYPE_")) return@mapNotNull null
            val code = runCatching { field.getInt(null) }.getOrNull() ?: return@mapNotNull null
            names[field.name.removePrefix("EXERCISE_TYPE_")]?.let { code to it }
        }.toMap()
    }
    fun isGenericTitle(raw: String): Boolean =
        raw.isBlank() || raw.trim().lowercase() in setOf("trening", "workout", "exercise", "ćwiczenia", "other workout")
    fun typeLabel(activityType: Int?): String? = activityType?.let { typeNames[it] }
    fun label(session: SportSession): String {
        val original = session.title.trim()
        if (!isGenericTitle(original)) return original
        return typeLabel(session.activityType) ?: "Trening (typ HC: ${session.activityType ?: "brak"})"
    }
}
