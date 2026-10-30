package com.example

import androidx.room.Room
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipeDeletionPersistenceTest {
    @Test fun duplicateMergePreservesPlansAndDeletionRemovesRelatedData(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.dietaDao()
            val recipe = RecipeEntity(title="Omlet", description="", ingredients="Jajka — 100 g", instructions="Usmaż", calories=150, protein=12, carbs=1, fat=10, category="Śniadanie")
            val a = dao.insertRecipe(recipe); val b = dao.insertRecipe(recipe)
            val meal = MealPlanEntity(dayOfWeek="Środa",mealType="Śniadanie",recipeId=b,recipeTitle="Omlet",calories=150,isCompleted=true,completedDate="2026-10-07")
            dao.insertMealPlan(meal)
            dao.mergeDuplicateRecipe(recipe.copy(id=b),recipe.copy(id=a))
            val kept = dao.getAllMealPlans().first().single()
            assertEquals(a,kept.recipeId); assertTrue(kept.isCompleted); assertEquals("2026-10-07",kept.completedDate)
            assertEquals(1,dao.getAllRecipes().first().size)
            dao.deleteRecipeAndPlans(recipe.copy(id=a))
            assertTrue(dao.getAllRecipes().first().isEmpty()); assertTrue(dao.getAllMealPlans().first().isEmpty())
        } finally { db.close() }
    }
}
