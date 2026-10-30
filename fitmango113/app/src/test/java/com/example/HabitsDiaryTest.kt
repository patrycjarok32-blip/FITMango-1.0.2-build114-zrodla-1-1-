package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HabitsDiaryTest {
    @Test fun classificationDoesNotCountFlavouredDairyOrInventWeightForPieces() {
        assertEquals("fruit", ProduceSupport.category("Jabłko"))
        assertEquals("vegetable", ProduceSupport.category("Brokuły mrożone"))
        assertEquals("other", ProduceSupport.category("Skyr truskawkowy"))
        assertEquals("other", ProduceSupport.category("Pearl barley"))
        assertEquals("fruit", ProduceSupport.category("Pear"))
        assertEquals("fruit", ProduceSupport.category("Pears fresh"))
        assertEquals("other", ProduceSupport.category("Pearled barley"))
        assertEquals("other", ProduceSupport.category("Sok jabłkowy"))
        assertEquals("other", ProduceSupport.category("Ziemniaki"))
        assertNull(ProduceSupport.grams(1f, "szt."))
        assertNull(ProduceSupport.grams(Float.NaN, "g"))
        assertEquals(250f, ProduceSupport.grams(0.25f, "kg")!!, 0f)
    }
    @Test fun tinyMealPortionsScaleProduceAndUnweighedFruitRemainsUnknown() {
        val recipe = RecipeEntity(id = 4, title = "Sałatka", description = "", ingredients = "Marchew — 200 g\nJabłko — 1 szt.",
            instructions = "Wymieszaj", calories = 200, protein = 2, carbs = 40, fat = 1, category = "Obiad")
        val meal = MealPlanEntity(id = 5, dayOfWeek = "Środa", mealType = "Obiad", recipeId = 4, recipeTitle = recipe.title, servings = 0.1f, calories = 20)
        val entries = ProduceSupport.entries(listOf(meal), listOf(recipe), emptyList())
        assertEquals(20f, entries.first { it.category == "vegetable" }.grams!!, 0.01f)
        assertNull(entries.first { it.category == "fruit" }.grams)
    }
    @Test fun snapshotsSurviveDayChangeUndoRestartAndBackup(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        HabitsDiary.restore(context, "")
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repo = DietaRepository(db.dietaDao())
            val recipeId = repo.insertRecipe(RecipeEntity(title = "Marchewka", description = "", ingredients = "Marchew — 200 g",
                instructions = "Pokrój", calories = 100, protein = 2, carbs = 20, fat = 1, category = "Obiad"))
            val id = db.dietaDao().insertMealPlan(MealPlanEntity(dayOfWeek = "Środa", mealType = "Obiad", recipeId = recipeId,
                recipeTitle = "Marchewka", servings = 0.5f, calories = 50, isCompleted = true, completedDate = "2026-10-07"))
            repo.insertBeverageLog(BeverageLogEntity(date = "2026-10-07", type = "Woda", amountMl = 250))
            val date = MutableStateFlow("2026-10-07")
            val diary = HabitsDiary(context, repo, date, scope)
            suspend fun day(predicate: (HabitDay) -> Boolean): HabitDay = withTimeout(10000) {
                diary.days.mapNotNull { it.find { d -> d.date == date.value && predicate(d) } }.first()
            }
            assertEquals(100, day { it.calories == 50 && it.drinksMl == 250 }.vegetableGrams)
            diary.add(date.value, "Jabłko", 75f, "fruit")
            assertEquals(175, day { it.fruitGrams == 75 }.produceGrams)
            diary.refresh(); assertEquals(175, day { it.fruitGrams == 75 }.produceGrams)
            diary.correct(date.value, day { it.fruitGrams == 75 }.produce.first { it.automatic }, 120f, "vegetable")
            assertEquals(195, day { it.vegetableGrams == 120 }.produceGrams)
            assertTrue(day { it.vegetableGrams == 120 }.produce.first { it.automatic }.reviewed)
            val meal = repo.allMealPlans.first().single()
            repo.updateMealPlan(meal.copy(servings = 0.25f, calories = 25))
            assertEquals(60, day { it.calories == 25 && it.vegetableGrams == 60 }.vegetableGrams)
            repo.updateMealPlan(meal.copy(isCompleted = false, completedDate = null))
            assertEquals(75, day { it.calories == 0 && it.vegetableGrams == 0 }.produceGrams)
            repo.updateMealPlan(meal)
            day { it.calories == 50 && it.vegetableGrams == 120 }
            date.value = "2026-10-08"
            day { it.calories == 0 }
            assertEquals(195, diary.days.value.first { it.date == "2026-10-07" }.produceGrams)
            val backup = HabitsDiary.export(context); HabitsDiary.validate(backup)
            scope.cancel()
            HabitsDiary.restore(context, ""); HabitsDiary.restore(context, backup)
            val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val restarted = HabitsDiary(context, repo, date, restartedScope)
                assertEquals(195, restarted.days.value.first { it.date == "2026-10-07" }.produceGrams)
                assertEquals(250, restarted.days.value.first { it.date == "2026-10-07" }.drinksMl)
                assertTrue(restarted.days.value.first { it.date == "2026-10-07" }.produce.first { it.automatic }.reviewed)
            } finally { restartedScope.cancel() }
            assertEquals(id, meal.id)
        } finally { scope.cancel(); db.close(); HabitsDiary.restore(context, "") }
    }
    @Test fun invalidBackupCannotReplaceDiary() {
        assertTrue(runCatching { HabitsDiary.validate("{\"days\":[]}") }.isFailure)
    }
}
