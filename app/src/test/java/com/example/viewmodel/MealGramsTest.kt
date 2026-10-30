package com.example.viewmodel

import com.example.data.MealPlanEntity
import com.example.data.RecipeEntity
import org.junit.Assert.*
import org.junit.Test

class MealGramsTest {
    private val recipe = RecipeEntity(title = "Zupa", description = "", ingredients = "Woda — 500 g",
        instructions = "Ugotuj", calories = 400, protein = 40, carbs = 60, fat = 10, category = "Obiad")
    private val meal = MealPlanEntity(id = 1, dayOfWeek = "Poniedziałek", mealType = "Obiad",
        recipeTitle = "Zupa", calories = 400)

    @Test fun gramsScaleCaloriesAndAllMacrosIncludingTinyAmounts() {
        for ((grams, expectedCalories) in listOf(250f to 200, 50f to 40, 750f to 600)) {
            val servings = MealPlannerSupport.servingsFromGrams(grams, 500f)
            val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(
                meal.copy(servings = servings), recipe, emptyList())
            assertEquals(expectedCalories, nutrition.calories)
            assertEquals(kotlin.math.round(recipe.protein * servings).toInt(), nutrition.protein)
            assertEquals(kotlin.math.round(recipe.carbs * servings).toInt(), nutrition.carbs)
            assertEquals(kotlin.math.round(recipe.fat * servings).toInt(), nutrition.fat)
            val item = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(
                recipe, meal.copy(servings = servings), emptyList()).single()
            assertEquals(grams, item.quantityForMeal!!, 0.01f)
        }
    }

    @Test fun invalidWeightsCannotBecomeSavedServings() {
        for ((eaten, portion) in listOf(0f to 500f, -1f to 500f, 100f to 0f,
            Float.NaN to 500f, 100f to Float.POSITIVE_INFINITY)) {
            assertTrue(runCatching { MealPlannerSupport.servingsFromGrams(eaten, portion) }.isFailure)
        }
        assertEquals(0.1f, MealPlannerSupport.safeServings(0.1f), 0f)
    }
}
