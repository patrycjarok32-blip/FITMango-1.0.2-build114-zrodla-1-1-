package com.example

import com.example.data.*
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import android.content.Context
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IngredientRepairAndSeasoningTest {
    private fun recipe(text: String) = RecipeEntity(id = 10, title = "Danie", description = "", ingredients = text,
        instructions = "", calories = 500, protein = 10, carbs = 50, fat = 20, category = "Obiad")
    private fun meal() = MealPlanEntity(id = 20, dayOfWeek = "Poniedziałek", mealType = "Obiad", recipeId = 10, recipeTitle = "Danie", calories = 500)
    private fun override(name: String, kcal: Float) = IngredientSubstitutionSupport.encodeMealSubstitutionEntity(mealPlanId = 20,
        originalIngredientName = name, originalBaseQuantity = 100f, originalUnit = "g", candidate = SubstituteCandidate(name = name,
            caloriesPer100g = kcal, proteinPer100g = 2f, carbsPer100g = 3f, fatPer100g = 4f), baseQuantityPerServing = 100f, unit = "g")

    @Test fun exactIngredientRepairWinsOverSimilarEarlierName() {
        val r = recipe("Jogurt — 100 g\nJogurt naturalny — 100 g")
        val rows = listOf(override("Jogurt", 50f), override("Jogurt naturalny", 80f))
        val resolved = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(r, meal(), rows)
        assertEquals("Jogurt naturalny", resolved[1].substitution!!.originalIngredientName)
        assertTrue(MealConvenienceSupport.missingIngredients(resolved, emptyList()).isEmpty())
        assertEquals(130, IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal(), r, rows).calories)
    }

    @Test fun seasoningsWithoutAmountsDoNotBlockNutritionAndHaveNoEnergyContribution() {
        val r = recipe("Produkt — 100 g\nSól do smaku\nPieprz\nPapryka mielona — 20 g")
        val rows = listOf(override("Produkt", 120f))
        val resolved = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(r, meal(), rows)
        assertTrue(MealConvenienceSupport.missingIngredients(resolved, emptyList()).isEmpty())
        val total = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal(), r, rows)
        assertFalse(total.isPartial)
        assertEquals(120, total.calories)
        assertTrue(IngredientSubstitutionSupport.ingredientEnergyLabel("Pieprz", null, "", emptyList()).contains("pominięta"))
        listOf("Papryka czerwona", "Czosnek", "Oliwa", "Cukier", "Masło").forEach {
            assertFalse(it, IngredientSubstitutionSupport.isIgnoredSeasoning(it))
        }
    }

    @Test fun oneSavedRepairSurvivesDatabaseReadAndUndoRestoresRecipeProfiles(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.dietaDao()
            dao.insertRecipe(recipe("Produkt — 100 g")); dao.insertMealPlan(meal())
            val undo = MealUndoStore(db)
            undo.edit(20, "Uzupełniono składnik") { dao.insertIngredient(override("Produkt", 120f)) }
            val persisted = dao.ingredientsForRecipeDirect(-20)
            assertEquals(1, persisted.size)
            assertTrue(MealConvenienceSupport.missingIngredients(
                IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(recipe("Produkt — 100 g"), meal(), persisted), emptyList()).isEmpty())
            assertEquals(120, IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal(), recipe("Produkt — 100 g"), persisted).calories)
            undo.edit(20, "Zmieniono przepis") {
                dao.updateRecipe(recipe("Produkt nowy — 100 g"))
                dao.insertIngredient(RecipeIngredientEntity(recipeId = 10, foodName = "Produkt nowy"))
            }
            assertTrue(undo.undo())
            assertEquals("Produkt — 100 g", dao.recipeById(10)!!.ingredients)
            assertTrue(dao.ingredientsForRecipeDirect(10).isEmpty())
            assertEquals(1, dao.ingredientsForRecipeDirect(-20).size)
        } finally { db.close() }
    }
}
