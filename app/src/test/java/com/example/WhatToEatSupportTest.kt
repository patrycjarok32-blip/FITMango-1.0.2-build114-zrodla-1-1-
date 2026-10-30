package com.example

import com.example.data.*
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test

class WhatToEatSupportTest {
    private val profile = UserProfileEntity(targetCalories = 1800, targetProtein = 110, targetCarbs = 220, targetFat = 60)
    private fun recipe(id: Long, calories: Int, protein: Int) = RecipeEntity(id, "Przepis $id", "", "", "", calories, protein, 10, 5, "Obiad")
    @Test fun portionsFitRemainingBudgetAndFavourMissingProtein() {
        val consumed = ScaledMealNutrition(1500, 80, 200, 55)
        val result = WhatToEatSupport.suggest(listOf(recipe(1, 400, 40), recipe(2, 200, 2), recipe(3, 1400, 50)), consumed, profile)
        assertTrue(result.all { it.calories <= 300 })
        assertEquals(1L, result.first().recipe.id)
        assertEquals(0.75f, result.first().servings, 0f)
        assertEquals(300, result.first().calories)
        assertEquals(30, result.first().protein)
        assertFalse(result.any { it.recipe.id == 3L })
    }
    @Test fun partialAndOverBudgetDaysDoNotOfferMisleadingSuggestions() {
        val recipes = listOf(recipe(1, 400, 30))
        assertTrue(WhatToEatSupport.suggest(recipes, ScaledMealNutrition(1500, 80, 200, 55, isPartial = true), profile).isEmpty())
        assertTrue(WhatToEatSupport.suggest(recipes, ScaledMealNutrition(1900, 80, 200, 55), profile).isEmpty())
        assertTrue(WhatToEatSupport.suggest(recipes, ScaledMealNutrition(1500, null, 200, 55), profile).isEmpty())
    }
    @Test fun largeRecipeLibraryStillReturnsOnlyFiveMatchingMeals() {
        val recipes = (1L..1000L).map { recipe(it, 400, 40) }
        val unrelatedRows = (1L..1000L).map { com.example.data.RecipeIngredientEntity(recipeId = it,
            foodName = "Inny składnik", quantity = 100f) }
        val result = WhatToEatSupport.suggest(recipes, ScaledMealNutrition(1500, 80, 200, 55), profile, unrelatedRows)
        assertEquals(5, result.size)
        assertTrue(result.all { it.calories == 300 && it.servings == 0.75f })
    }
}
