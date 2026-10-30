package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.viewmodel.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NutritionRegressionTest {
    @Test fun completeCurrentEnergyIsSummedEvenWhenReplacementMacrosAreMissing() {
        val recipe = oatmeal().copy(ingredients = "Płatki owsiane — 50 g")
        val meal = MealPlanEntity(id = 1, recipeId = 1, dayOfWeek = "Środa", mealType = "Śniadanie", recipeTitle = recipe.title, calories = 403)
        val product = SubstituteCandidate(name = "Produkt z etykiety", caloriesPer100g = 200f)
        fun calc(qty: Float, portions: Float = 1f): ScaledMealNutrition {
            val sub = IngredientSubstitutionSupport.encodeMealSubstitutionEntity(mealPlanId = 1,
                originalIngredientName = "Płatki owsiane", originalBaseQuantity = 50f, originalUnit = "g",
                candidate = product, baseQuantityPerServing = qty, unit = "g")
            return IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal.copy(servings = portions), recipe, listOf(sub))
        }
        assertEquals(100, calc(50f).calories)
        assertEquals(200, calc(100f).calories)
        assertEquals(100, calc(50f).calories)
        assertEquals(200, calc(50f, 2f).calories)
        assertNull(calc(50f).protein)
        assertTrue(calc(50f).isPartial)
        assertTrue(calc(50f).note!!.contains("Niepełne"))
    }
    @Test fun unknownIngredientCannotFreezeEditedMealOrDashboardTotals() {
        val recipe = oatmeal().copy(ingredients = "Banan — 100 g\nOdżywka bez danych — 20 g", calories = 489, protein = 39)
        val meal = MealPlanEntity(id = 1, recipeId = 1, dayOfWeek = "Sobota", mealType = "Przekąska",
            recipeTitle = recipe.title, calories = 489, isCompleted = true)
        val banana = RecipeIngredientEntity(recipeId = 1, foodName = "Banan", quantity = 100f, grams = 100f,
            caloriesPer100g = 89f, proteinPer100g = 1f, carbsPer100g = 23f, fatPer100g = 0f)
        fun rows(qty: Float) = listOf(banana, IngredientSubstitutionSupport.encodeMealSubstitutionEntity(mealPlanId = 1,
            originalIngredientName = "Banan", originalBaseQuantity = 100f, originalUnit = "g",
            candidate = SubstituteCandidate(name = "Banan", caloriesPer100g = 89f, proteinPer100g = 1f, carbsPer100g = 23f, fatPer100g = 0f),
            baseQuantityPerServing = qty, unit = "g"))
        val small = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, recipe, rows(100f))
        val large = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, recipe, rows(200f))
        assertEquals(89, small.calories); assertEquals(178, large.calories)
        assertEquals(2, large.protein); assertEquals(46, large.carbs)
        assertTrue(large.isPartial)
        val home = IngredientSubstitutionSupport.totalMealNutrition(listOf(meal), listOf(recipe), rows(200f))
        assertEquals(large.calories, home.calories); assertEquals(large.protein, home.protein)
        assertEquals(large.carbs, home.carbs); assertEquals(large.fat, home.fat); assertTrue(home.isPartial)
        val powder = RecipeIngredientEntity(recipeId = 1, foodName = "Odżywka bez danych", quantity = 20f, grams = 20f,
            caloriesPer100g = 400f, proteinPer100g = 80f, carbsPer100g = 10f, fatPer100g = 5f)
        val complete = IngredientSubstitutionSupport.totalMealNutrition(listOf(meal), listOf(recipe), rows(200f) + powder)
        assertEquals(258, complete.calories); assertEquals(18, complete.protein)
        assertEquals(48, complete.carbs); assertEquals(1, complete.fat); assertFalse(complete.isPartial)
    }
    private fun oatmeal() = RecipeEntity(id = 1, title = "Owsianka", description = "", ingredients = "Płatki owsiane — 50 g\nNapój owsiany — 175 ml", instructions = "Wymieszaj", calories = 403, protein = 15, carbs = 55, fat = 12, category = "Śniadanie")
    private val milk = SubstituteCandidate(name = "Semi-Skimmed Milk", caloriesPer100g = 50f, proteinPer100g = 3.3f, carbsPer100g = 4.8f, fatPer100g = 2f)

    @Test fun oatDrinkCannotBeMistakenForDryOatsAndUnknownDeltaCannotZeroMeal() {
        assertNull(IngredientSubstitutionSupport.lookupKnownIngredientNutrition("Napój owsiany"))
        val recipe = oatmeal()
        val meal = MealPlanEntity(id = 1, recipeId = 1, dayOfWeek = "Środa", mealType = "Śniadanie", recipeTitle = recipe.title, calories = 0)
        val subs = listOf(
            IngredientSubstitutionSupport.encodeMealSubstitutionEntity(mealPlanId = 1, originalIngredientName = "Płatki owsiane", originalBaseQuantity = 50f, originalUnit = "g", candidate = milk, baseQuantityPerServing = 52f, unit = "g"),
            IngredientSubstitutionSupport.encodeMealSubstitutionEntity(mealPlanId = 1, originalIngredientName = "Napój owsiany", originalBaseQuantity = 175f, originalUnit = "ml", candidate = milk, baseQuantityPerServing = 209f, unit = "ml"))
        val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, recipe, subs)
        assertEquals(131, nutrition.calories) // All current products known: (52 + 209) * 50 / 100.
        assertTrue(nutrition.note!!.contains("Suma aktualnych"))
    }

    @Test fun repeatedQuantityChangesUpdateKnownSumsWithUnknownIngredient() {
        val recipe = oatmeal()
        val meal = MealPlanEntity(id = 1, recipeId = 1, dayOfWeek = "Środa", mealType = "Śniadanie", recipeTitle = recipe.title, calories = 243)
        fun calc(qty: Float) = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, recipe,
            listOf(IngredientSubstitutionSupport.encodeMealSubstitutionEntity(mealPlanId = 1, originalIngredientName = "Płatki owsiane", originalBaseQuantity = 50f, originalUnit = "g", candidate = milk, baseQuantityPerServing = qty, unit = "g")))
        assertEquals(50, calc(100f).calories)
        assertEquals(100, calc(200f).calories)
        assertEquals(26, calc(52f).calories)
        assertTrue(calc(52f).isPartial)
    }

    @Test fun gramEditChangesCaloriesWithoutDestroyingImportedRemainder() {
        val recipe = oatmeal()
        val edited = IngredientSubstitutionSupport.recalculateEditedIngredients(recipe, "Płatki owsiane — 100 g\nNapój owsiany — 175 ml", emptyList())
        assertEquals(589, edited.calories)
        assertEquals(22, edited.protein)
        assertEquals(84, edited.carbs)
        assertEquals(16, edited.fat)
    }

    @Test fun recipeGramChangesDoNotAddTheOverriddenOriginalIngredientTwice() {
        val recipe = oatmeal()
        val edited = IngredientSubstitutionSupport.recalculateEditedIngredients(recipe, "Płatki owsiane — 100 g\nNapój owsiany — 175 ml", emptyList())
        val meal = MealPlanEntity(id = 1, recipeId = 1, dayOfWeek = "Środa", mealType = "Śniadanie", recipeTitle = recipe.title, calories = 243)
        val sub = IngredientSubstitutionSupport.encodeMealSubstitutionEntity(mealPlanId = 1, originalIngredientName = "Płatki owsiane", originalBaseQuantity = 50f, originalUnit = "g", candidate = milk, baseQuantityPerServing = 52f, unit = "g")
        assertEquals(26, IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, edited, listOf(sub)).calories)
        assertEquals(52, IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal.copy(servings = 2f), edited, listOf(sub)).calories)
    }

    @Test fun missingDataAndInconsistentBaselineArePreservedInsteadOfBecomingZero() {
        val recipe = oatmeal().copy(calories = 10)
        val replaced = IngredientSubstitutionSupport.replaceIngredientInRecipeEntity(recipe, "Płatki owsiane — 50 g", milk, 1f, "g")
        assertEquals(10, replaced.calories)
        val unknown = IngredientSubstitutionSupport.replaceIngredientInRecipeEntity(oatmeal(), "Napój owsiany — 175 ml", milk, 209f, "ml")
        assertEquals(403, unknown.calories)
        val added = IngredientSubstitutionSupport.addIngredientNutrition(oatmeal(), SubstituteCandidate(name = "Bez danych"), 20f)
        assertEquals(403, added.calories)
    }

    @Test fun knownReplacementDoesNotRequireOriginalPieceMass() {
        val recipe = oatmeal().copy(ingredients = "Jajka — 2 szt.", calories = 380)
        val meal = MealPlanEntity(id = 1, recipeId = 1, dayOfWeek = "Środa", mealType = "Śniadanie", recipeTitle = recipe.title, calories = 380)
        val sub = IngredientSubstitutionSupport.encodeMealSubstitutionEntity(mealPlanId = 1, originalIngredientName = "Jajka", originalBaseQuantity = 2f, originalUnit = "szt.", candidate = milk, baseQuantityPerServing = 100f, unit = "g")
        val mass = RecipeIngredientEntity(recipeId = 1, foodName = "Jajka", quantity = 2f, grams = 100f, unit = "szt.", caloriesPer100g = 155f, proteinPer100g = 13f, carbsPer100g = 1.1f, fatPer100g = 11f)
        assertEquals(50, IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, recipe, listOf(sub, mass)).calories)
        assertEquals(50, IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, recipe, listOf(sub)).calories)
    }

    @Test fun knownReplacementUsesItsOwnMacrosInsteadOfOldMissingMacros() {
        val recipe = oatmeal().copy(ingredients = "Płatki owsiane — 50 g")
        val meal = MealPlanEntity(id = 1, recipeId = 1, dayOfWeek = "Środa", mealType = "Śniadanie", recipeTitle = recipe.title, calories = 403)
        val sub = IngredientSubstitutionSupport.encodeMealSubstitutionEntity(mealPlanId = 1, originalIngredientName = "Płatki owsiane", originalBaseQuantity = 50f, originalUnit = "g", candidate = milk, baseQuantityPerServing = 100f, unit = "g")
        val partial = RecipeIngredientEntity(recipeId = 1, foodName = "Płatki owsiane", quantity = 50f, grams = 50f, unit = "g", caloriesPer100g = 200f, proteinPer100g = -1f, carbsPer100g = -1f, fatPer100g = -1f)
        val result = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, recipe, listOf(sub, partial))
        assertEquals(50, result.calories)
        assertEquals(3, result.protein)
        assertEquals(5, result.carbs)
        assertEquals(2, result.fat)
        assertTrue(result.note!!.contains("Suma aktualnych"))
    }

    @Test fun zeroFatCanBecomePositiveWhenReplacingAnIngredientWithKnownLabelData() {
        val recipe = oatmeal().copy(ingredients = "Ryż — 50 g", fat = 0)
        val rice = RecipeIngredientEntity(recipeId = 1, foodName = "Ryż", quantity = 50f, grams = 50f, unit = "g", caloriesPer100g = 100f, proteinPer100g = 2f, carbsPer100g = 24f, fatPer100g = 0f)
        val updated = IngredientSubstitutionSupport.replaceIngredientInRecipeEntity(recipe, "Ryż — 50 g", milk, 50f, "g", listOf(rice))
        assertEquals(1, updated.fat)
    }

    @Test fun extraIngredientAndItsNutritionAreSavedAtomicallyAndSurviveReopening(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "extra-ingredient.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val repo = DietaRepository(db.dietaDao())
            val id = repo.insertRecipe(oatmeal().copy(id = 0))
            val original = repo.allRecipes.first().first { it.id == id }
            val candidate = SubstituteCandidate(name = "Dodatkowy produkt", caloriesPer100g = 200f, proteinPer100g = 10f, carbsPer100g = 20f, fatPer100g = 9f)
            val updated = IngredientSubstitutionSupport.addIngredientNutrition(original, candidate, 25f).copy(ingredients = original.ingredients + "\nDodatkowy produkt — 25 g")
            repo.updateRecipeWithIngredients(updated, listOf(RecipeIngredientEntity(recipeId = id, foodName = candidate.name, quantity = 25f, grams = 25f, unit = "g", caloriesPer100g = 200f, proteinPer100g = 10f, carbsPer100g = 20f, fatPer100g = 9f)))
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            val reopened = DietaRepository(db.dietaDao())
            val saved = reopened.allRecipes.first().first { it.id == id }
            assertEquals(453, saved.calories)
            assertEquals(18, saved.protein)
            assertTrue(saved.ingredients.contains("Dodatkowy produkt — 25 g"))
            assertEquals(200f, reopened.allRecipeIngredients.first().single().caloriesPer100g, 0f)
            val meal = MealPlanEntity(id = 2, recipeId = id, recipeTitle = saved.title, dayOfWeek = "Środa", mealType = "Śniadanie", servings = 2f, calories = 0)
            assertEquals(906, MealPlannerSupport.calculateScaledNutrition(meal, saved).calories)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
