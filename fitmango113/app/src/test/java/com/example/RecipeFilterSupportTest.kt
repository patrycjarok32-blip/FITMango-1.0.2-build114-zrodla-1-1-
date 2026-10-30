package com.example

import com.example.data.RecipeEntity
import com.example.viewmodel.RecipeCategories
import com.example.viewmodel.RecipeFilterSupport
import org.junit.Assert.*
import org.junit.Test

class RecipeFilterSupportTest {
    private fun recipe(category: String = "Breakfast", description: String = "", instructions: String = "") =
        RecipeEntity(title = "Owsianka z jabłkiem", description = description,
            ingredients = "Płatki owsiane — 50 g\nJabłko — 100 g", instructions = instructions,
            calories = 300, protein = 12, carbs = 45, fat = 7, category = category)

    @Test fun categoriesAcceptPluralNamesAccentsAndSourceAliases() {
        assertTrue(RecipeCategories.matches("Breakfast", "Śniadania"))
        assertTrue(RecipeCategories.matches("Śniadania", "Śniadanie"))
        assertTrue(RecipeCategories.matches("lunch", "Obiady"))
        assertTrue(RecipeCategories.matches("kolacje", "Dinner"))
        assertFalse(RecipeCategories.matches("Breakfast", "Kolacja"))
        assertTrue(RecipeCategories.matches("Lunch / Dinner", "Obiad"))
        assertTrue(RecipeCategories.matches("Lunch / Dinner", "Kolacja"))
    }

    @Test fun searchFindsIngredientsAndPolishCategoriesWithoutAccents() {
        assertTrue(RecipeFilterSupport.matches(recipe(), "sniadania jablko", "Wszystkie", "Wszystkie czasy", null, false, false))
        assertTrue(RecipeFilterSupport.matches(recipe(), "  platki   owsiane ", "Śniadanie", "Wszystkie czasy", null, false, false))
        assertFalse(RecipeFilterSupport.matches(recipe(), "kurczak", "Wszystkie", "Wszystkie czasy", null, false, false))
    }

    @Test fun durationHandlesMinutesHoursRangesAndIsoSourceValues() {
        assertEquals(15, RecipeFilterSupport.durationMinutes("15 minut"))
        assertEquals(75, RecipeFilterSupport.durationMinutes("1 godz. 15 min"))
        assertEquals(15, RecipeFilterSupport.durationMinutes("10–15 min"))
        assertEquals(75, RecipeFilterSupport.durationMinutes("PT1H15M"))
        assertNull(RecipeFilterSupport.durationMinutes("PT25H"))
    }

    @Test fun labelledMetadataIsReadButOneCookingStepIsNotTotalDuration() {
        assertEquals(25, RecipeFilterSupport.providedMinutes(recipe(description = "Porcje: 2\nCzas całkowity: 25 min")))
        assertEquals(75, RecipeFilterSupport.providedMinutes(recipe(instructions = "Czas całkowity: 1 godz. 15 min\n1. Ugotuj.")))
        assertNull(RecipeFilterSupport.providedMinutes(recipe(instructions = "1. Smaż 5 min.\n2. Piecz 40 min.")))
        assertEquals(45, RecipeFilterSupport.providedMinutes(recipe(description = "Czas przygotowania: 5 min\nCzas całkowity: 45 min")))
    }

    @Test fun categoryAndDurationCombineAndUnknownTimeIsNotCountedAsQuick() {
        val r = recipe()
        assertTrue(RecipeFilterSupport.matches(r, "", "Śniadanie", "Do 15 minut", 15, false, false))
        assertFalse(RecipeFilterSupport.matches(r, "", "Śniadanie", "Do 15 minut", 16, false, false))
        assertFalse(RecipeFilterSupport.matches(r, "", "Obiad", "Do 15 minut", 10, false, false))
        assertFalse(RecipeFilterSupport.matches(r, "", "Śniadanie", "Do 15 minut", null, false, false))
        assertTrue(RecipeFilterSupport.matches(r, "", "Śniadanie", "Brak czasu", null, false, false))
        assertTrue(RecipeFilterSupport.matches(r, "", "Śniadanie", "Do 30 minut", 30, false, false))
        assertTrue(RecipeFilterSupport.matches(r, "", "Śniadanie", "Bez gotowania", null, true, false))
        assertTrue(RecipeFilterSupport.matches(r, "", "Śniadanie", "Do pracy", null, false, true))
    }
}
