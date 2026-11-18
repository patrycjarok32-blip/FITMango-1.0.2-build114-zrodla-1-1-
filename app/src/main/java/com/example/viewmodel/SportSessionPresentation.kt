package com.example.viewmodel

import androidx.health.connect.client.records.ExerciseSessionRecord

/** Uses the recorded workout title when meaningful; falls back to Health Connect type. */
object SportSessionPresentation {
    fun label(session: SportSession): String {
        val raw = session.title.trim()
        val generic = raw.isBlank() || raw.equals("Trening", true) || raw.equals("Workout", true) ||
            raw.equals("Exercise", true) || raw.equals("Ćwiczenia", true)
        if (!generic) return raw
        return when (session.activityType) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Spacer"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Bieganie"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Rower"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Trening siłowy"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Joga"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Pływanie"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Wędrówka"
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Orbitrek"
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
            else -> "Trening (typ nieznany)"
        }
    }
}