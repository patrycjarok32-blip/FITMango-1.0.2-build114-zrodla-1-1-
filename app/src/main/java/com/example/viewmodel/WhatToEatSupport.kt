package com.example.viewmodel

import com.example.data.RecipeEntity
import com.example.data.UserProfileEntity
import com.example.data.RecipeIngredientEntity
import com.example.data.MealPlanEntity
import kotlin.math.max
import kotlin.math.roundToInt

data class MealSuggestion(val recipe: RecipeEntity, val servings: Float, val calories: Int, val protein: Int, val carbs: Int, val fat: Int)
object WhatToEatSupport {
    fun suggest(recipes: List<RecipeEntity>, consumed: ScaledMealNutrition, profile: UserProfileEntity, ingredients: List<RecipeIngredientEntity> = emptyList()): List<MealSuggestion> {
        if (consumed.isPartial || consumed.calories == null || consumed.protein == null || consumed.carbs == null || consumed.fat == null) return emptyList()
        val remaining = profile.targetCalories - consumed.calories
        if (remaining < 30) return emptyList()
        val proteinLeft = max(0, profile.targetProtein - consumed.protein)
        val carbsLeft = max(0, profile.targetCarbs - consumed.carbs)
        val fatLeft = max(0, profile.targetFat - consumed.fat)
        fun score(s: MealSuggestion): Double =
            minOf(s.protein, proteinLeft) * 4.0 - max(0, s.carbs - carbsLeft) * 0.7 - max(0, s.fat - fatLeft) * 2.0 + s.calories.toDouble() / remaining
        val ingredientsByRecipe = ingredients.groupBy { it.recipeId }
        return recipes.filter { it.calories > 0 && it.protein >= 0 && it.carbs >= 0 && it.fat >= 0 }.mapNotNull { recipe ->
            val baseMeal = MealPlanEntity(id = Long.MAX_VALUE, dayOfWeek = "Poniedziałek", mealType = "Obiad", recipeId = recipe.id,
                recipeTitle = recipe.title, servings = 1f, calories = recipe.calories)
            val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(baseMeal, recipe, ingredientsByRecipe[recipe.id].orEmpty())
            if (nutrition.isPartial || nutrition.calories == null || nutrition.protein == null || nutrition.carbs == null || nutrition.fat == null) return@mapNotNull null
            listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f).map { portion ->
                MealSuggestion(recipe, portion, (nutrition.calories * portion).roundToInt(), (nutrition.protein * portion).roundToInt(),
                    (nutrition.carbs * portion).roundToInt(), (nutrition.fat * portion).roundToInt())
            }.filter { it.calories in 30..remaining }.maxByOrNull { score(it) }
        }.sortedByDescending { score(it) }.take(5)
    }
}
