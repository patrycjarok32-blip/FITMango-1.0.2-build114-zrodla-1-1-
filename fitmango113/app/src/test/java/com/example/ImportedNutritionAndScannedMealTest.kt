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
class ImportedNutritionAndScannedMealTest {
    private fun imported() = ImportedRecipe("Ryż z kurczakiem", "2", "Ryż — 100 g\nKurczak — 200 g", "Ugotuj", 0, 0, 0, 0, "PDF")

    @Test fun missingNutritionUsesWholeRecipeQuantitiesDividedByServings() {
        val result = ImportedNutritionSupport.fromLocalIngredients(imported())!!
        val rice = IngredientSubstitutionSupport.lookupKnownIngredientNutrition("Ryż")!!
        val chicken = IngredientSubstitutionSupport.lookupKnownIngredientNutrition("Kurczak")!!
        assertEquals(kotlin.math.round((rice.caloriesPer100g + chicken.caloriesPer100g * 2) / 2).toInt(), result.calories)
        assertTrue(result.protein > 0); assertTrue(result.carbs > 0); assertTrue(result.fat > 0)
        assertTrue(result.nutritionIsEstimated)
    }

    @Test fun missingQuantityAndUnknownIngredientCannotProducePartialTotals() {
        assertNull(ImportedNutritionSupport.fromLocalIngredients(imported().copy(ingredients = "Ryż — 100 g\nKurczak")))
        assertNull(ImportedNutritionSupport.fromLocalIngredients(imported().copy(ingredients = "Ryż — 100 g\nNieznany składnik — 50 g")))
        assertNull(ImportedNutritionSupport.fromLocalIngredients(imported().copy(servings = "0")))
    }

    @Test fun enrichmentPreservesSourceValuesAndRecipeDetails() {
        val original = imported().copy(calories = 555, protein = 21, imageUrl = "https://example.com/photo.jpg")
        val result = ImportedNutritionSupport.merge(original, ImportedNutritionSupport.fromLocalIngredients(original)!!)
        assertEquals(555, result.calories); assertEquals(21, result.protein)
        assertEquals(original.title, result.title); assertEquals(original.ingredients, result.ingredients)
        assertEquals(original.source, result.source); assertEquals(original.imageUrl, result.imageUrl)
    }

    @Test fun removingIngredientChangesPerServingNutrition() {
        val recipe = RecipeEntity(title = "Ryż z kurczakiem", description = "Porcje: 2", ingredients = "Ryż — 100 g\nKurczak — 200 g",
            instructions = "Ugotuj", calories = 500, protein = 40, carbs = 50, fat = 15, category = "Obiad")
        val result = IngredientSubstitutionSupport.recalculateEditedIngredients(recipe, "Ryż — 100 g", emptyList())
        val chicken = IngredientSubstitutionSupport.lookupKnownIngredientNutrition("Kurczak")!!
        assertEquals(500 - chicken.caloriesPer100g.toInt(), result.calories)
        assertEquals("Ryż — 100 g", result.ingredients)
    }

    @Test fun reimportFillsOldMissingNutritionWithoutDuplicatingRecipe(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.dietaDao()
            val recipe = RecipeEntity(title = "Ryż", description = "", ingredients = "Ryż — 100 g", instructions = "Ugotuj",
                calories = 0, protein = 0, carbs = 0, fat = 0, category = "Obiad")
            val id = dao.saveImportedRecipe(recipe, emptyList())
            val repeatedId = dao.saveImportedRecipe(recipe.copy(calories = 350, protein = 7, carbs = 79, fat = 1), emptyList())
            assertEquals(id, repeatedId); assertEquals(1, dao.getAllRecipes().first().size)
            assertEquals(350, dao.recipeById(id)!!.calories)
        } finally { db.close() }
    }

    @Test fun smallScannedQuantityIsNotClampedToTwentyFiveGrams(): Unit = runBlocking {
        val product = SubstituteCandidate(name = "Jogurt", barcode = "1234567890128", caloriesPer100g = 100f,
            proteinPer100g = 10f, carbsPer100g = 20f, fatPer100g = 5f)
        val recipe = ScannedProductMealSupport.recipe(product, 10f, "g")
        assertEquals(10, recipe.calories)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.dietaDao()
            val existingRecipeId = dao.insertRecipe(recipe.copy(title = "Istniejąca potrawa"))
            dao.insertMealPlan(MealPlanEntity(dayOfWeek = "Środa", mealType = "Śniadanie", recipeId = existingRecipeId,
                recipeTitle = "Istniejąca potrawa", calories = 200, isCompleted = true, completedDate = "2026-10-07"))
            dao.addScannedProductMeal(recipe, ScannedProductMealSupport.ingredient(product, 10f, "g"), "Środa", "Śniadanie")
            val meals = dao.getAllMealPlans().first()
            assertEquals(2, meals.size)
            assertTrue(meals.first { it.recipeTitle == "Istniejąca potrawa" }.isCompleted)
            val added = meals.first { it.recipeTitle == "Jogurt" }
            assertFalse(added.isCompleted); assertEquals("Śniadanie", added.mealType)
            assertEquals(1f, added.servings, 0f); assertEquals(10, added.calories)
            val nutrition = MealPlannerSupport.calculateScaledNutrition(added, recipe)
            assertEquals(10, nutrition.calories)
            val ingredients = dao.getIngredientsForRecipe(added.recipeId).first()
            assertEquals(10f, ingredients.single().grams, 0f)
        } finally { db.close() }
    }
}
