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
class MealConvenienceSupportTest {
    private fun meal() = MealPlanEntity(id = 7, dayOfWeek = "Poniedziałek", mealType = "Obiad", recipeId = 1,
        recipeTitle = "Zupa", servings = 0.5f, calories = 100, isCompleted = true, completedDate = "2026-10-05")
    @Test fun savedSetsPersistIndependentOfSourceAndBackupRoundTrips() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val override = RecipeIngredientEntity(recipeId = -7, foodName = "Changed ingredient", quantity = 40f)
        val saved = MealConvenienceSupport.snapshot("Mój obiad", listOf(meal()), listOf(override, override.copy(recipeId = -8)))
        assertFalse(saved.entries.single().meal.isCompleted)
        assertNull(saved.entries.single().meal.completedDate)
        assertEquals(0.5f, saved.entries.single().meal.servings, 0f)
        assertEquals(1, saved.entries.single().overrides.size)
        MealConvenienceSupport.write(context, listOf(saved))
        val backup = MealConvenienceSupport.export(context)
        MealConvenienceSupport.write(context, emptyList())
        MealConvenienceSupport.restore(context, backup)
        assertEquals(listOf(saved), MealConvenienceSupport.read(context))
        assertTrue(runCatching { MealConvenienceSupport.snapshot("", listOf(meal()), emptyList()) }.isFailure)
        MealConvenienceSupport.write(context, emptyList())
    }
    @Test fun batchGramsUseCookedWeightWithoutOverallocating() {
        val servings = MealConvenienceSupport.batchServings(1200f, 4f, listOf(
            BatchMealPortion("Poniedziałek", 300f), BatchMealPortion("Wtorek", 450f)))
        assertEquals(listOf(1f, 1.5f), servings)
        val recipe = RecipeEntity(id = 1, title = "Zupa", description = "", ingredients = "Ryż — 100 g", instructions = "Gotuj",
            calories = 200, protein = 5, carbs = 30, fat = 7, category = "Obiad")
        val baseMeal = meal().copy(servings = servings[1])
        assertEquals(300, IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(baseMeal, recipe, emptyList()).calories)
        val metadata = RecipeIngredientEntity(recipeId = -7, foodName = MealConvenienceSupport.BATCH_WEIGHT, grams = 300f)
        assertEquals(300f, MealConvenienceSupport.batchPortionWeight(7, listOf(metadata))!!, 0f)
        assertTrue(runCatching { MealConvenienceSupport.batchServings(1200f, 4f, listOf(BatchMealPortion("Wtorek", 1300f))) }.isFailure)
        assertTrue(runCatching { MealConvenienceSupport.batchServings(1200f, 4f, listOf(BatchMealPortion("Wtorek", Float.NaN))) }.isFailure)
        assertTrue(runCatching { MealConvenienceSupport.batchServings(1200f, 4f, emptyList()) }.isFailure)
    }
    @Test fun setInsertionKeepsChangesAndRollsBackWhenRecipeMissing(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.dietaDao()
            val recipeId = dao.insertRecipe(RecipeEntity(title = "Zupa", description = "", ingredients = "Marchew — 100 g",
                instructions = "Gotuj", calories = 200, protein = 5, carbs = 30, fat = 7, category = "Obiad"))
            val entry = SavedMealEntry(meal().copy(recipeId = recipeId), listOf(RecipeIngredientEntity(recipeId = -7, foodName = "Changed", quantity = 40f)))
            val id = dao.insertSavedMeals(listOf(entry), "Wtorek").single()
            assertFalse(dao.mealById(id)!!.isCompleted)
            assertNull(dao.mealById(id)!!.completedDate)
            assertEquals(-id, dao.ingredientsByRecipeId(-id).single().recipeId)
            assertEquals("Changed", dao.ingredientsByRecipeId(-id).single().foodName)
            assertTrue(runCatching { dao.insertSavedMeals(listOf(entry, entry.copy(meal = entry.meal.copy(recipeId = Long.MAX_VALUE))), "Środa") }.isFailure)
            assertEquals(1, dao.getAllMealPlans().first().size)
        } finally { db.close() }
    }
    @Test fun completenessDetectsUnknownAndMissingMacrosInsteadOfCallingThemZero() {
        val known = ResolvedMealIngredientItem("Ryż — 100 g", "Ryż", 100f, "g", "Ryż", 100f, "g")
        val unknown = known.copy(ingredientName = "Produkt bez etykiety", originalIngredientName = "Produkt bez etykiety")
        assertTrue(MealConvenienceSupport.missingIngredients(listOf(known), emptyList()).isEmpty())
        assertEquals(listOf(unknown), MealConvenienceSupport.missingIngredients(listOf(known, unknown), emptyList()))
        assertEquals(1, MealConvenienceSupport.missingIngredients(listOf(known.copy(quantityForMeal = null)), emptyList()).size)
    }
}
