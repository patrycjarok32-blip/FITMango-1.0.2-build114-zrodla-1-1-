package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.data.*
import com.example.viewmodel.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LegacyPlanDatesTest {
    @Test fun undatedThursdayDoesNotAppearInNewWeekAndKnownDatesDoNotRoll() {
        assertFalse(MealPlannerSupport.matchesDay("Czwartek", "2026-10-15"))
        val old = MealPlanEntity(dayOfWeek = "Czwartek", mealType = "Obiad", recipeTitle = "Danie", calories = 400)
        val repaired = LegacyPlanDates.assign(old, LocalDate.parse("2026-10-05"))
        assertEquals("2026-10-08", repaired.dayOfWeek)
        assertFalse(MealPlannerSupport.matchesDay(repaired.dayOfWeek, "2026-10-15"))
        assertEquals(repaired, LegacyPlanDates.assign(repaired, LocalDate.parse("2026-10-12")))
        assertEquals("2026-10-09", LegacyPlanDates.assign(old.copy(isCompleted = true, completedDate = "2026-10-09"), LocalDate.parse("2026-10-12")).dayOfWeek)
    }
    @Test fun repairPreservesIdsOverridesAndConsumptionWhileNewEntriesAreDated(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val dao = db.dietaDao()
            val oldId = dao.insertMealPlanRecord(MealPlanEntity(dayOfWeek = "Czwartek", mealType = "Obiad", recipeTitle = "Danie", calories = 400, servings = 2f))
            val eatenId = dao.insertMealPlanRecord(MealPlanEntity(dayOfWeek = "Piątek", mealType = "Obiad", recipeTitle = "Zjedzone", calories = 500, isCompleted = true, completedDate = "2026-10-09"))
            dao.insertIngredient(RecipeIngredientEntity(recipeId = -oldId, foodName = "Zamiana", grams = 50f))
            dao.repairKnownPlanDates()
            assertEquals("2026-10-09", dao.mealById(eatenId)!!.dayOfWeek)
            assertEquals("Czwartek", dao.mealById(oldId)!!.dayOfWeek)
            dao.assignLegacyPlanWeek(listOf(oldId), "2026-10-05")
            val fixed = dao.mealById(oldId)!!
            assertEquals("2026-10-08", fixed.dayOfWeek)
            assertEquals(2f, fixed.servings, 0f)
            assertEquals(400, fixed.calories)
            assertFalse(fixed.isCompleted)
            assertEquals(50f, dao.ingredientsByRecipeId(-oldId).single().grams, 0f)
            val id = dao.insertMealPlan(MealPlanEntity(dayOfWeek = "Poniedziałek", mealType = "Obiad", recipeTitle = "Nowe", calories = 100))
            assertNotNull(MealPlannerSupport.parsePlanDate(dao.mealById(id)!!.dayOfWeek))
            dao.assignLegacyPlanWeek(listOf(oldId), "2026-10-12")
            assertEquals(fixed, dao.mealById(oldId))
        } finally { db.close() }
    }
}
