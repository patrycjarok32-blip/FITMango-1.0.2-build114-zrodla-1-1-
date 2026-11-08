package com.example.viewmodel

import com.example.data.RecipeEntity
import com.example.data.RecipeIngredientEntity
import com.example.data.UserProfileEntity

/** Read-only preview: never changes an existing meal plan without explicit user confirmation. */
data class GeneratedDay(val dayIndex: Int, val suggestions: List<MealSuggestion>, val estimatedCalories: Int)
object SmartWeeklyPlanner {
    fun preview(
        recipes: List<RecipeEntity>,
        ingredients: List<RecipeIngredientEntity>,
        profile: UserProfileEntity,
        days: Int = 7,
        mealsPerDay: Int = 4
    ): List<GeneratedDay> {
        val safeDays = days.coerceIn(1, 7)
        val safeMeals = mealsPerDay.coerceIn(1, 6)
        val candidates = WhatToEatSupport.suggest(
            recipes, ScaledMealNutrition(0, 0, 0, 0), profile, ingredients
        )
        if (candidates.isEmpty()) return emptyList()
        val perMeal = (profile.targetCalories / safeMeals).coerceAtLeast(30)
        return (0 until safeDays).map { day ->
            val chosen = (0 until safeMeals).map { slot ->
                val candidate = candidates[(day * safeMeals + slot) % candidates.size]
                val adjusted = (candidate.servings * perMeal / candidate.calories.coerceAtLeast(1))
                    .coerceIn(0.25f, 2f)
                val ratio = adjusted / candidate.servings
                candidate.copy(servings = adjusted,
                    calories = (candidate.calories * ratio).toInt(),
                    protein = (candidate.protein * ratio).toInt(),
                    carbs = (candidate.carbs * ratio).toInt(),
                    fat = (candidate.fat * ratio).toInt())
            }
            GeneratedDay(day, chosen, chosen.sumOf { it.calories })
        }
    }
}

/** Pantry matching is local; it does not upload household inventory to a server. */
data class PantryMatch(val recipe: RecipeEntity, val available: Int, val total: Int, val missing: List<String>)
object PantryRecipeMatcher {
    fun match(recipes: List<RecipeEntity>, ingredients: List<RecipeIngredientEntity>, pantry: List<String>): List<PantryMatch> {
        val known = pantry.map { it.trim().lowercase() }.filter { it.isNotBlank() }
        return recipes.mapNotNull { recipe ->
            val required = MealPlannerSupport.parseRecipeIngredients(recipe.ingredients, ingredients.filter { it.recipeId == recipe.id })
                .map { it.name.trim() }.filter { it.isNotBlank() }.distinct()
            if (required.isEmpty()) return@mapNotNull null
            val missing = required.filter { item ->
                known.none { stocked -> stocked == item.lowercase() || stocked.contains(item.lowercase()) || item.lowercase().contains(stocked) }
            }
            PantryMatch(recipe, required.size - missing.size, required.size, missing)
        }.sortedWith(compareBy<PantryMatch> { it.missing.size }.thenByDescending { it.available }).take(30)
    }
}
