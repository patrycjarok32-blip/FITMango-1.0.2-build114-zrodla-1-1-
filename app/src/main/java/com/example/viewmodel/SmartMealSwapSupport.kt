package com.example.viewmodel

/** Safe ranking of existing recipes. The user must approve any replacement. */
data class SmartMealOption(
    val recipeId: Long,
    val title: String,
    val calories: Int?,
    val proteinGrams: Int?,
    val preparationMinutes: Int? = null,
    val availableIngredients: Int = 0,
    val totalIngredients: Int = 0
)
data class SmartMealSwap(val recipe: SmartMealOption, val reason: String, val score: Int)

object SmartMealSwapSupport {
    fun suggest(
        current: SmartMealOption,
        options: List<SmartMealOption>,
        maximumResults: Int = 5
    ): List<SmartMealSwap> = options.asSequence()
        .filter { it.recipeId != current.recipeId && it.calories != null && it.calories > 0 }
        .distinctBy { it.recipeId }
        .map { option ->
            val calorieDistance = current.calories?.takeIf { it > 0 }
                ?.let { kotlin.math.abs(it.toLong() - option.calories!!.toLong()).coerceAtMost(1000L).toInt() }
            val proteinDistance = if (current.proteinGrams != null && option.proteinGrams != null)
                kotlin.math.abs(current.proteinGrams - option.proteinGrams).coerceAtMost(100) else null
            val pantry = if (option.totalIngredients > 0)
                (option.availableIngredients.coerceIn(0, option.totalIngredients) * 100 / option.totalIngredients) else 0
            val score = 100 - (calorieDistance ?: 150) / 10 -
                (proteinDistance ?: 15) / 2 + pantry / 5 -
                ((option.preparationMinutes ?: 30).coerceAtLeast(0) / 15)
            SmartMealSwap(option, "Różnica kalorii: ${calorieDistance?.let { "$it kcal" } ?: "brak danych"}; składniki w domu: $pantry%.", score)
        }
        .sortedWith(compareByDescending<SmartMealSwap> { it.score }.thenBy { it.recipe.title })
        .take(maximumResults.coerceIn(0, 20))
        .toList()
}
