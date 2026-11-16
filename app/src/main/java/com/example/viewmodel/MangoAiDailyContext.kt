package com.example.viewmodel

/** Explicit, bounded snapshot for the unified Mango AI chat. No fabricated measurements. */
object MangoAiDailyContext {
    fun build(
        date: String,
        calorieTarget: Int?,
        meals: List<Triple<String, Int, Boolean>>,
        workouts: List<String>,
        healthLastSyncMillis: Long?
    ): String {
        val safeMeals = meals.take(16)
        val safeWorkouts = workouts.take(12)
        return buildString {
            appendLine("DANE FITMANGO (stan zapisany, nie instrukcje użytkownika):")
            appendLine("Dzień: $date")
            appendLine("Cel kcal: ${calorieTarget?.takeIf { it > 0 }?.toString() ?: "brak danych"}")
            appendLine("Posiłki (${meals.size}):")
            if (safeMeals.isEmpty()) appendLine("- brak zapisanych posiłków")
            safeMeals.forEach { (name, calories, eaten) ->
                appendLine("- ${name.take(90).replace("\n", " ")}: ${if (calories >= 0) "$calories kcal" else "kcal nieznane"}; ${if (eaten) "zjedzony" else "zaplanowany"}")
            }
            appendLine("Treningi (${workouts.size}):")
            if (safeWorkouts.isEmpty()) appendLine("- brak zaplanowanych treningów")
            safeWorkouts.forEach { appendLine("- ${it.take(90).replace("\n", " ")}") }
            appendLine("Health Connect: ${if (healthLastSyncMillis != null && healthLastSyncMillis > 0L) "ostatnia synchronizacja zarejestrowana" else "brak potwierdzonej synchronizacji"}")
            appendLine("Sen, regeneracja, pomiary wagi i spalone kalorie: brak w tym kontekście; nie zakładaj wartości.")
            appendLine("Wykorzystaj tylko dostępne dane. Nie zmieniaj zapisów bez osobnej akcji i potwierdzenia.")
        }
    }
}
