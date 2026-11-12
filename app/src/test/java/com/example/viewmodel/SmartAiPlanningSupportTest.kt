package com.example.viewmodel

import org.junit.Assert.*
import org.junit.Test

class SmartAiPlanningSupportTest {
    @Test fun swapsExcludeCurrentRecipeAndMissingCalories() {
        val current = SmartMealOption(1, "Obiad", 500, 30)
        val results = SmartMealSwapSupport.suggest(current, listOf(
            current, SmartMealOption(2, "Zamiennik", 510, 32),
            SmartMealOption(3, "Brak danych", null, null)
        ))
        assertEquals(listOf(2L), results.map { it.recipe.recipeId })
    }

    @Test fun shoppingCombinesOnlyCompatibleUnits() {
        val result = SmartShoppingSupport.consolidate(listOf(
            SmartShoppingIngredient("Ryż", 100.0, "g"),
            SmartShoppingIngredient("ryż", 50.0, "g"),
            SmartShoppingIngredient("Ryż", 1.0, "szt.")
        ))
        assertEquals(2, result.size)
        assertEquals(150.0, result.first { it.unit == "g" }.quantity!!, 0.001)
    }

    @Test fun unknownQuantityRequiresReview() {
        val result = SmartShoppingSupport.consolidate(listOf(
            SmartShoppingIngredient("Mleko", null, "ml"),
            SmartShoppingIngredient("Mleko", 200.0, "ml")
        ))
        assertTrue(result.single().needsReview)
        assertNull(result.single().quantity)
    }

    @Test fun strengthCoachDoesNotIncreaseWithoutEffort() {
        val result = SmartStrengthCoachSupport.suggest(listOf(
            SmartStrengthSet("squat", 40.0, 10, 10)
        ))
        assertNull(result.single().suggestedWeightKg)
    }

    @Test fun strengthCoachSuggestsProgressionWhenEffortLow() {
        val result = SmartStrengthCoachSupport.suggest(listOf(
            SmartStrengthSet("squat", 40.0, 10, 10, 6)
        ))
        assertEquals(42.5, result.single().suggestedWeightKg!!, 0.001)
    }
}
