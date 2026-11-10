package com.example.ui.components

/** Consistent visual hints for exercise names, without changing persisted exercise identifiers. */
internal object SportExerciseIcons {
    fun forGroup(group: String): String = when (group) {
        "Nogi" -> "🦵"
        "Pośladki" -> "🍑"
        "Plecy" -> "🏋️"
        "Klatka" -> "💪"
        "Barki" -> "🏋️"
        "Ramiona", "Biceps", "Triceps" -> "💪"
        "Core" -> "🎯"
        "Łydki" -> "🦵"
        "Full Body" -> "⚡"
        else -> "🏃"
    }

    fun forExercise(name: String): String {
        val known = StrengthExerciseLibrary.exercises.firstOrNull {
            it.name.equals(name, ignoreCase = true)
        }
        return if (known != null) forGroup(known.group) else when {
            name.contains("cardio", ignoreCase = true) -> "❤️"
            name.contains("joga", ignoreCase = true) -> "🧘"
            name.contains("brzuch", ignoreCase = true) -> "🎯"
            name.contains("walk", ignoreCase = true) -> "🚶"
            name.contains("taniec", ignoreCase = true) -> "💃"
            else -> "🏋️"
        }
    }
}
