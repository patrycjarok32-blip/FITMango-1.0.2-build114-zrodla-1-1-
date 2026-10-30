package com.example

import com.example.data.MealPlanEntity
import com.example.data.RecipeEntity
import com.example.data.RecipeIngredientEntity
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MealIngredientActionsTest {
    private fun recipe() = RecipeEntity(id = 10, title = "Ryż", description = "", ingredients = "Ryż — 100 g",
        instructions = "Ugotuj", calories = 350, protein = 7, carbs = 79, fat = 1, category = "Obiad")
    private fun meal() = MealPlanEntity(id = 20, dayOfWeek = "Czwartek", mealType = "Obiad", recipeId = 10,
        recipeTitle = "Ryż", servings = 2f, calories = 700, isCompleted = true)
    private fun extra(amount: Float = 25f) = IngredientSubstitutionSupport.encodeMealSubstitutionEntity(
        mealPlanId = 20, originalIngredientName = IngredientSubstitutionSupport.EXTRA_PREFIX + "test",
        originalBaseQuantity = 0f, originalUnit = "g", candidate = SubstituteCandidate(name = "Własny produkt",
            caloriesPer100g = 200f, proteinPer100g = 10f, carbsPer100g = 20f, fatPer100g = 8f),
        baseQuantityPerServing = amount, unit = "g")

    @Test fun addedIngredientBelongsToOneMealAndScalesWithoutChangingBaseRecipe() {
        val recipe = recipe().copy(protein = 1)
        // Explicit label data avoids ambiguously matching generic "Ryż" to brown rice.
        // Deliberately different imported protein confirms edited meals use ingredients.
        val rice = RecipeIngredientEntity(recipeId = 10, foodName = "Ryż", quantity = 100f, grams = 100f,
            caloriesPer100g = 350f, proteinPer100g = 7f, carbsPer100g = 79f, fatPer100g = 1f)
        val rows = listOf(rice, extra())
        val resolved = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(recipe, meal(), rows)
        assertEquals(2, resolved.size)
        assertEquals(50f, resolved.last().quantityForMeal!!, 0.001f)
        val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal(), recipe, rows)
        assertEquals(800, nutrition.calories)
        assertEquals(19, nutrition.protein)
        assertEquals(168, nutrition.carbs)
        assertEquals(6, nutrition.fat)
        assertEquals(1, IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(recipe, meal().copy(id = 21), rows).size)
        assertEquals("Ryż — 100 g", recipe.ingredients)
        assertTrue(meal().isCompleted)
    }

    @Test fun zeroQuantityOverrideRemovesIngredientAndSubtractsItsNutrition() {
        val recipe = recipe().copy(calories = 500)
        val targetMeal = meal().copy(calories = 1000)
        val removal = IngredientSubstitutionSupport.encodeMealSubstitutionEntity(mealPlanId = 20,
            originalIngredientName = "Ryż", originalBaseQuantity = 100f, originalUnit = "g",
            candidate = SubstituteCandidate(name = "Ryż", caloriesPer100g = 0f, proteinPer100g = 0f,
                carbsPer100g = 0f, fatPer100g = 0f), baseQuantityPerServing = 0f, unit = "g")
        assertEquals(0f, IngredientSubstitutionSupport.decodeMealSubstitutionEntity(removal)!!.baseQuantityPerServing, 0.001f)
        assertTrue(IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(recipe, meal(), listOf(removal)).isEmpty())
        val profile = IngredientSubstitutionSupport.lookupKnownIngredientNutrition("Ryż")!!
        val result = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(targetMeal, recipe, listOf(removal))
        assertEquals(0, result.calories)
        assertEquals(1, IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(recipe, meal().copy(id = 21), listOf(removal)).size)
    }

    @Test fun subGramExtrasRetainQuantityAndCannotReplaceMangoByMarkerName() {
        val small = extra(0.25f)
        assertEquals(0.25f, IngredientSubstitutionSupport.decodeMealSubstitutionEntity(small)!!.baseQuantityPerServing, 0.001f)
        val mango = recipe().copy(ingredients = "Mango — 100 g")
        val rows = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(mango, meal(), listOf(small))
        assertEquals("Mango", rows.first().ingredientName)
        assertEquals(2, rows.size)
        val removed = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal(), recipe(), emptyList())
        assertEquals(700, removed.calories)
    }
}
