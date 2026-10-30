package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.viewmodel.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MealPlanningConvenienceTest {
    @Test fun eatingHidesOnlyThatDaysEntryAndUndoReturnsItToQueue() {
        val date = "2026-10-12"
        val breakfast = MealPlanEntity(id = 1, dayOfWeek = date, mealType = "Śniadanie", recipeTitle = "Owsianka", calories = 400,
            isCompleted = true, completedDate = date)
        val dinner = breakfast.copy(id = 2, mealType = "Obiad", isCompleted = false, completedDate = null)
        assertEquals(listOf(dinner), MealQueueSupport.remaining(listOf(breakfast, dinner), date))
        assertEquals(listOf(breakfast), MealQueueSupport.eaten(listOf(breakfast, dinner), date))
        assertEquals(2, MealQueueSupport.remaining(listOf(breakfast.copy(isCompleted = false, completedDate = null), dinner), date).size)
    }
    @Test fun portionPlanningNeedsNoWeightAndRejectsOverAllocationAndInvalidDates() {
        MealQueueSupport.validatePortions(3f, listOf(PortionMealAssignment("2026-10-13", 1f), PortionMealAssignment("2026-10-20", 0.5f)))
        assertTrue(runCatching { MealQueueSupport.validatePortions(1f, listOf(PortionMealAssignment("2026-10-13", 2f))) }.isFailure)
        assertTrue(runCatching { MealQueueSupport.validatePortions(3f, listOf(PortionMealAssignment("Wtorek", 1f))) }.isFailure)
        assertTrue(runCatching { MealQueueSupport.validatePortions(3f, listOf(PortionMealAssignment("2026-10-13", Float.NaN))) }.isFailure)
    }
    @Test fun copyingToAnotherWeekKeepsSourceAndOverridesButClearsConsumption(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val dao = db.dietaDao()
            val recipeId = dao.insertRecipe(RecipeEntity(title = "Danie", description = "", ingredients = "Ryż — 100 g", instructions = "Ugotuj.", calories = 350, protein = 8, carbs = 70, fat = 2, category = "Obiad"))
            val sourceId = dao.insertMealPlan(MealPlanEntity(dayOfWeek = "2026-10-12", mealType = "Obiad", recipeId = recipeId,
                recipeTitle = "Danie", servings = 1.5f, calories = 525, isCompleted = true, completedDate = "2026-10-12"))
            dao.insertIngredient(RecipeIngredientEntity(recipeId = -sourceId, foodName = "Zamiennik", grams = 40f, caloriesPer100g = 123f))
            val source = dao.mealById(sourceId)!!
            val copyId = dao.copyMeals(listOf(sourceId), "2026-10-20", "Kolacja").single()
            val copy = dao.mealById(copyId)!!
            assertEquals(source, dao.mealById(sourceId))
            assertEquals("2026-10-20", copy.dayOfWeek)
            assertEquals("Kolacja", copy.mealType)
            assertFalse(copy.isCompleted); assertNull(copy.completedDate)
            assertEquals(1.5f, copy.servings, 0f); assertEquals(525, copy.calories)
            val ingredient = dao.ingredientsByRecipeId(-copyId).single()
            assertEquals(-copyId, ingredient.recipeId); assertEquals(40f, ingredient.grams, 0f)
            assertEquals(123f, ingredient.caloriesPer100g, 0f)
        } finally { db.close() }
    }
}
