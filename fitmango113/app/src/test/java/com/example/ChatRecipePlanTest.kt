package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.data.*
import com.example.viewmodel.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatRecipePlanTest {
    @Test fun markdownRecipeCanBePreviewedButMultipleRecipesAreNotSilentlyMerged() {
        val text = "# Owsianka\n**Składniki:**\n- Płatki owsiane — 50 g\n- Mleko — 200 ml\n**Przygotowanie:**\n1. Gotuj płatki z mlekiem przez 5 minut."
        val draft = ChatRecipeSupport.draft(text)
        assertNotNull(draft); assertTrue(draft!!.nutritionIsEstimated); assertTrue(draft.ingredients.contains("50"))
        assertNull(ChatRecipeSupport.draft(text + "\nSkładniki:\nRyż — 100 g\nPrzygotowanie:\nUgotuj ryż."))
        assertNull(ChatRecipeSupport.draft("Waga zależy od wielu czynników."))
    }
    @Test fun recipeAndPlanAreCommittedTogetherAndExistingRecipeCanBeReused(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val dao = db.dietaDao()
            val recipe = RecipeEntity(title = "Owsianka AI", description = "Porcje: 1", ingredients = "Płatki — 50 g", instructions = "Gotuj.", calories = 300, protein = 10, carbs = 45, fat = 8, category = "Śniadanie", source = "AI Dietetyk")
            val id = dao.saveChatRecipeAndPlan(recipe, listOf(RecipeIngredientEntity(recipeId = 0, foodName = "Płatki", grams = 50f)), null, "2026-10-20", "Śniadanie", 1.5f)
            assertEquals(450, dao.getAllMealPlans().first().single().calories)
            assertEquals("2026-10-20", dao.getAllMealPlans().first().single().dayOfWeek)
            dao.saveChatRecipeAndPlan(recipe, emptyList(), id, "2026-10-21", "Śniadanie", 1f)
            assertEquals(1, dao.getAllRecipes().first().size); assertEquals(2, dao.getAllMealPlans().first().size)
            assertTrue(runCatching { dao.saveChatRecipeAndPlan(recipe, emptyList(), null, "bad-date", "Śniadanie", 1f) }.isFailure)
            assertEquals(1, dao.getAllRecipes().first().size); assertEquals(2, dao.getAllMealPlans().first().size)
        } finally { db.close() }
    }
}
