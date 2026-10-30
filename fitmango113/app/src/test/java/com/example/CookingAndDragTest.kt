package com.example

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.components.MealDragController
import com.example.ui.components.dropDayAt
import com.example.viewmodel.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CookingAndDragTest {
    @Test fun numberedStepsKeepContinuationLinesAndTimerSupportsPauseAndResume() {
        assertEquals(listOf("Ugotuj ryż.\nOdcedź.", "Dodaj warzywa."), CookingSupport.steps("1. Ugotuj ryż.\nOdcedź.\n2. Dodaj warzywa."))
        assertTrue(CookingSupport.steps(" ").isEmpty())
        val started = CookingTimerState.start(5, 1000)
        assertEquals(300L, started.remainingSeconds(1000))
        val paused = started.pause(121000)
        assertEquals(180L, paused.remainingSeconds(500000))
        val resumed = paused.resume(500000)
        assertEquals(180L, resumed.remainingSeconds(500000))
        assertEquals(0L, resumed.remainingSeconds(700000))
        assertTrue(runCatching { CookingTimerState.start(0, 1000) }.isFailure)
    }
    @Test fun timerSurvivesStoreRecreationAndCompletionIsSignalledOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("FITMangoCooking", 0).edit().clear().commit()
        CookingTimerStore(context, 7).save(CookingTimerState.start(1, 1000))
        val restored = CookingTimerStore(context, 7)
        assertEquals(30L, restored.read().remainingSeconds(31000))
        assertTrue(restored.consumeFinished(61000))
        assertFalse(CookingTimerStore(context, 7).consumeFinished(62000))
    }
    @Test fun dropUsesRootCoordinatesCurrentWeekAndIgnoresCancellationAndSameDay() {
        var dropped: String? = null
        val controller = MealDragController { _, date -> dropped = date }
        val meal = MealPlanEntity(id = 1, dayOfWeek = "2026-10-12", mealType = "Obiad", recipeTitle = "Zupa", calories = 200)
        controller.register("2026-10-12", Rect(0f, 0f, 40f, 50f), "2026-10-12")
        controller.register("2026-10-13", Rect(40f, 0f, 80f, 50f), "2026-10-12")
        controller.begin(meal, Offset(100f, 100f)); controller.move(Offset(-40f, -80f)); controller.finish()
        assertEquals("2026-10-13", dropped)
        dropped = null
        controller.begin(meal, Offset(20f, 20f)); controller.finish(); assertNull(dropped)
        controller.begin(meal, Offset(60f, 20f)); controller.cancel(); controller.finish(); assertNull(dropped)
        controller.register("2026-10-20", Rect(40f, 0f, 80f, 50f), "2026-10-19")
        controller.begin(meal, Offset(60f, 20f)); controller.finish()
        assertEquals("2026-10-20", dropped)
        assertNull(dropDayAt(Offset(-1f, -1f), mapOf("day" to Rect(0f, 0f, 40f, 50f))))
    }
    @Test fun movingAndUndoPreserveMealIdNutritionAndIngredientOverrides(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val dao = db.dietaDao()
            val recipeId = dao.insertRecipe(RecipeEntity(title = "Zupa", description = "", ingredients = "Marchew — 100 g", instructions = "Ugotuj.", calories = 200, protein = 5, carbs = 30, fat = 4, category = "Obiad"))
            val id = dao.insertMealPlan(MealPlanEntity(dayOfWeek = "2026-10-12", mealType = "Obiad", recipeId = recipeId, recipeTitle = "Zupa", calories = 300, servings = 1.5f))
            dao.insertIngredient(RecipeIngredientEntity(recipeId = -id, foodName = "Zamiennik", grams = 40f))
            val before = dao.mealById(id)!!
            val rows = dao.ingredientsForRecipeDirect(-id)
            val undo = MealUndoStore(db)
            undo.edit(id, "Przeniesiono posiłek") { dao.updateMealPlan(before.copy(dayOfWeek = "2026-10-13")) }
            assertEquals("2026-10-13", dao.mealById(id)!!.dayOfWeek)
            assertEquals(rows, dao.ingredientsForRecipeDirect(-id))
            assertTrue(undo.undo())
            assertEquals(before, dao.mealById(id))
            assertEquals(rows, dao.ingredientsForRecipeDirect(-id))
        } finally { db.close() }
    }
}
