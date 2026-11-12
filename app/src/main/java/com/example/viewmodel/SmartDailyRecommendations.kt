package com.example.viewmodel

/**
 * Deterministic, offline-first recommendations. AI can explain these suggestions,
 * but must not invent nutrition values or mutate the user's diary.
 */
data class SmartDailyInput(
    val calorieTarget: Int,
    val consumedCalories: Int?,
    val proteinTargetGrams: Int?,
    val consumedProteinGrams: Int?,
    val waterConsumedMl: Int?,
    val waterTargetMl: Int?,
    val nutritionIsPartial: Boolean = false
)

data class SmartDailySuggestion(
    val id: String,
    val title: String,
    val explanation: String,
    val priority: Int
)

object SmartDailyRecommendations {
    fun evaluate(input: SmartDailyInput): List<SmartDailySuggestion> {
        val suggestions = mutableListOf<SmartDailySuggestion>()
        if (input.nutritionIsPartial || input.consumedCalories == null || input.consumedCalories < 0) {
            suggestions += SmartDailySuggestion(
                "check_nutrition", "Sprawdź dane posiłków",
                "Część wartości odżywczych jest niepełna. Uzupełnij je przed analizą bilansu.", 100
            )
        } else if (input.calorieTarget > 0) {
            val remaining = (input.calorieTarget.toLong() - input.consumedCalories.toLong()).coerceAtLeast(0)
            if (remaining > 0) suggestions += SmartDailySuggestion(
                "calorie_balance", "Zaplanuj kolejny posiłek",
                "Do dziennego celu pozostało $remaining kcal. Wybierz posiłek z jadłospisu.", 60
            )
        }
        if (!input.nutritionIsPartial && input.proteinTargetGrams != null &&
            input.proteinTargetGrams > 0 && input.consumedProteinGrams != null &&
            input.consumedProteinGrams >= 0) {
            val missing = (input.proteinTargetGrams - input.consumedProteinGrams).coerceAtLeast(0)
            if (missing > 0) suggestions += SmartDailySuggestion(
                "protein_balance", "Uzupełnij białko",
                "Do wybranego celu pozostało $missing g białka. Sprawdź skład następnego posiłku.", 50
            )
        }
        if (input.waterTargetMl != null && input.waterTargetMl > 0 &&
            input.waterConsumedMl != null && input.waterConsumedMl >= 0 &&
            input.waterConsumedMl < input.waterTargetMl) {
            suggestions += SmartDailySuggestion(
                "water", "Sprawdź nawodnienie",
                "Do ustawionego celu pozostało ${input.waterTargetMl - input.waterConsumedMl} ml.", 20
            )
        }
        return suggestions.sortedWith(compareByDescending<SmartDailySuggestion> { it.priority }.thenBy { it.id })
    }
}
