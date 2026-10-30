package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NutritionAttentionQueueTest {
    @Test fun oilNutritionRequiresAnOilWordRatherThanAnEmbeddedSubstring() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        NutritionAttentionSupport.load(context)
        assertNull(IngredientSubstitutionSupport.lookupKnownIngredientNutrition("Produkt unikatowy kolejka"))
        for (name in listOf("olej", "Olej rzepakowy", "rafinowany olej słonecznikowy", "oleju rzepakowego", "Oliwa z oliwek", "olive oil")) {
            val profile = IngredientSubstitutionSupport.lookupKnownIngredientNutrition(name)
            assertNotNull(name, profile)
            assertTrue(name, profile!!.caloriesPer100g > 800f)
        }
    }

    @Test fun spicesAreExcludedAndSavedProductDisappearsEvenWithoutQuantity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        NutritionAttentionSupport.load(context)
        val recipe = RecipeEntity(id = 123, title = "Test", description = "", ingredients = "Produkt unikatowy kolejka\nSól — szczypta\nPieprz czarny — szczypta\nPapryka wędzona mielona — szczypta\nZioła prowansalskie — szczypta", instructions = "Dodaj", calories = 0, protein = 0, carbs = 0, fat = 0, category = "Obiad")
        val meal = MealPlanEntity(id = 123, dayOfWeek = "2026-10-23", mealType = "Obiad", recipeId = 123, recipeTitle = recipe.title, calories = 0)
        val before = NutritionAttentionSupport.collect(listOf(meal), listOf(recipe), emptyList())
        assertEquals(listOf("Produkt unikatowy kolejka"), before.map { it.name })
        assertTrue(before.single().quantitiesMissing)
        NutritionAttentionSupport.save(context, before.single().name, SubstituteCandidate(before.single().name, caloriesPer100g = 123f, proteinPer100g = 5f, carbsPer100g = 20f, fatPer100g = 2f))
        assertTrue(NutritionAttentionSupport.collect(listOf(meal), listOf(recipe), emptyList()).isEmpty())
        NutritionAttentionSupport.load(context)
        assertTrue(NutritionAttentionSupport.collect(listOf(meal), listOf(recipe), emptyList()).isEmpty())
        val items = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(recipe, meal, emptyList())
        assertTrue(MealConvenienceSupport.missingIngredients(items, emptyList()).any { it.ingredientName == before.single().name })
        assertFalse(IngredientSubstitutionSupport.isIgnoredSeasoning("Papryka czerwona"))
        assertFalse(IngredientSubstitutionSupport.isIgnoredSeasoning("Czosnek świeży"))
    }
}
