package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.viewmodel.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickUseSupportTest {
    private fun recipe() = RecipeEntity(title = "Zupa", description = "Porcje: 4", ingredients = "Marchew — 400 g",
        instructions = "Gotuj", calories = 200, protein = 5, carbs = 30, fat = 7, category = "Obiad")

    @Test fun cookedWeightPreservesNutritionAndScalesEatenGrams() {
        val r = QuickUseSupport.withCookedWeight(recipe(), 1600f, 4f)
        assertEquals(200, r.calories)
        val saved = QuickUseSupport.cookedWeight(r)!!
        val ratio = MealPlannerSupport.servingsFromGrams(200f, QuickUseSupport.portionWeight(saved.first, saved.second))
        assertEquals(0.5f, ratio, 0f)
        assertEquals(100f, ratio * r.calories, 0f)
        assertEquals(2.5f, ratio * r.protein, 0f)
        val replaced = QuickUseSupport.withCookedWeight(r, 2000f, 4f)
        assertEquals(2000f, QuickUseSupport.cookedWeight(replaced)!!.first, 0f)
        assertTrue(runCatching { QuickUseSupport.portionWeight(Float.NaN, 4f) }.isFailure)
        assertTrue(runCatching { QuickUseSupport.portionWeight(1000f, 0f) }.isFailure)
    }

    @Test fun roomCopiesKeepIngredientChangesAndResetCompletion(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.dietaDao()
            val recipeId = dao.insertRecipe(recipe())
            val originalId = dao.insertMealPlan(MealPlanEntity(dayOfWeek = "Poniedziałek", mealType = "Obiad", recipeId = recipeId,
                recipeTitle = "Zupa", servings = 0.5f, calories = 100, isCompleted = true, completedDate = "2026-10-05"))
            val change = RecipeIngredientEntity(recipeId = -originalId, foodName = "Saved substitution", quantity = 30f, caloriesPer100g = 250f)
            dao.insertIngredient(change)
            val copyId = dao.copyMeals(listOf(originalId), "Wtorek", null).single()
            val copied = dao.mealById(copyId)!!
            assertFalse(copied.isCompleted); assertNull(copied.completedDate)
            assertEquals(0.5f, copied.servings, 0f); assertEquals(100, copied.calories)
            assertEquals(-copyId, dao.ingredientsByRecipeId(-copyId).single().recipeId)
            assertEquals(change.foodName, dao.ingredientsByRecipeId(-copyId).single().foodName)
            assertTrue(dao.mealById(originalId)!!.isCompleted)
            assertTrue(runCatching { dao.copyMeals(listOf(originalId, Long.MAX_VALUE), "Środa", null) }.isFailure)
            // The failed multi-meal operation must not leave a partial copy behind.
            assertEquals(2, dao.getAllMealPlans().first().size)
        } finally { db.close() }
    }

    @Test fun ownProductStoresExactNutritionPer100AndUnit(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val p = SubstituteCandidate(name = "Napój", caloriesPer100g = 45.5f, proteinPer100g = 3.2f,
                carbsPer100g = 4.7f, fatPer100g = 1.5f, defaultUnit = "ml", source = SubstituteOptionMode.LOCAL_DATABASE)
            val dao = db.dietaDao()
            val r = QuickUseSupport.productRecipe(p)
            val id = dao.saveImportedRecipe(r, listOf(ScannedProductMealSupport.ingredient(p, 100f, "ml")))
            val stored = QuickUseSupport.product(dao.recipeById(id)!!, dao.ingredientsByRecipeId(id))!!
            assertEquals(45.5f, stored.caloriesPer100g!!, 0f)
            assertEquals("ml", stored.defaultUnit)
            assertEquals(114, stored.nutritionForQuantity(250f).calories)
            val meal = MealPlanEntity(dayOfWeek = "Wtorek", mealType = "Przekąska", recipeId = id, recipeTitle = "Napój", servings = 2.5f, calories = 114)
            val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, dao.recipeById(id), dao.ingredientsByRecipeId(id))
            assertEquals(114, nutrition.calories); assertEquals(8, nutrition.protein)
            assertTrue(runCatching { QuickUseSupport.productRecipe(p.copy(proteinPer100g = null)) }.isFailure)
        } finally { db.close() }
    }

    @Test fun weeklySummaryUsesDatesWithoutDuplicatingOrInventingMissingDays() {
        fun day(date: String) = HabitDay(date, 1800, 100, 200, 50, 2000, 2000, 400, 2500, listOf("Obiad"), emptyList())
        val days = listOf(day("2026-10-09"), day("2026-10-09"), day("2026-10-03"), day("2026-10-02"), day("2026-10-10"))
        val result = QuickUseSupport.weeklyDays(days, LocalDate.parse("2026-10-09"))
        assertEquals(listOf("2026-10-03", "2026-10-09"), result.map { it.date })
        assertEquals(2, result.size)
    }
}
