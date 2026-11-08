package com.example.viewmodel

import com.example.data.RecipeEntity
import com.example.data.UserProfileEntity
import com.example.data.RecipeIngredientEntity
import com.example.data.MealPlanEntity
import kotlin.math.max
import kotlin.math.roundToInt

data class MealSuggestion(val recipe: RecipeEntity, val servings: Float, val calories: Int, val protein: Int, val carbs: Int, val fat: Int, val approximate: Boolean = false)
object WhatToEatSupport {
    fun suggest(recipes: List<RecipeEntity>, consumed: ScaledMealNutrition, profile: UserProfileEntity, ingredients: List<RecipeIngredientEntity> = emptyList(), filter: String = "Wszystkie"): List<MealSuggestion> {
        val calorieOnly = consumed.isPartial || consumed.calories == null || consumed.protein == null || consumed.carbs == null || consumed.fat == null
        val remaining = profile.targetCalories - (consumed.calories ?: 0)
        val unknownConsumedCalories = consumed.calories == null || consumed.isPartial
        if (remaining < 30 && !unknownConsumedCalories) return emptyList()
        val proteinLeft = max(0, profile.targetProtein - (consumed.protein ?: 0))
        val carbsLeft = max(0, profile.targetCarbs - (consumed.carbs ?: 0))
        val fatLeft = max(0, profile.targetFat - (consumed.fat ?: 0))
        fun score(s: MealSuggestion): Double =
            (if (calorieOnly) 0.0 else minOf(s.protein, proteinLeft) * 4.0) - (if (calorieOnly) 0.0 else max(0, s.carbs - carbsLeft) * 0.7) - (if (calorieOnly) 0.0 else max(0, s.fat - fatLeft) * 2.0) + s.calories.toDouble() / remaining
        val ingredientsByRecipe = ingredients.groupBy { it.recipeId }
        return recipes.filter { recipe ->
            val name = recipe.title.lowercase()
            when (filter) {
                "Słodkie" -> listOf("owsiank", "deser", "ciast", "koktajl", "naleśnik", "pudding", "jogurt").any { it in name }
                "Wytrawne" -> listOf("owsiank", "deser", "ciast", "koktajl", "naleśnik", "pudding").none { it in name }
                else -> true
            }
        }.mapNotNull { recipe ->
            val baseMeal = MealPlanEntity(id = Long.MAX_VALUE, dayOfWeek = "Poniedziałek", mealType = "Obiad", recipeId = recipe.id,
                recipeTitle = recipe.title, servings = 1f, calories = recipe.calories)
            val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(baseMeal, recipe, ingredientsByRecipe[recipe.id].orEmpty())
            val approximate = calorieOnly || nutrition.isPartial || nutrition.calories == null || nutrition.protein == null || nutrition.carbs == null || nutrition.fat == null
            if (!calorieOnly && approximate) return@mapNotNull null
            val knownCalories = nutrition.calories?.takeIf { it > 0 } ?: recipe.calories.takeIf { it > 0 } ?: return@mapNotNull null
            listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f).map { portion ->
                MealSuggestion(recipe, portion, (knownCalories * portion).roundToInt(), ((nutrition.protein ?: 0) * portion).roundToInt(),
                    ((nutrition.carbs ?: 0) * portion).roundToInt(), ((nutrition.fat ?: 0) * portion).roundToInt(), approximate)
            }.filter { it.calories >= 30 && (unknownConsumedCalories || it.calories <= remaining) }.maxByOrNull { score(it) }
        }.sortedByDescending { score(it) }.take(5)
    }
}
