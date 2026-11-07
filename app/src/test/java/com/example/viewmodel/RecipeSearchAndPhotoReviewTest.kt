package com.example.viewmodel

import org.junit.Assert.*
import org.junit.Test

class RecipeSearchAndPhotoReviewTest {
    @Test fun parsesNaturalLanguageMealAndLimits() {
        val q = RecipeFilterSupport.parseQuery("Szukam obiadu do 30 minut i do 500 kcal")
        assertEquals("Obiad", q.category)
        assertEquals(30, q.maxMinutes)
        assertEquals(500, q.maxCalories)
    }

    @Test fun parsesProteinAndNoCooking() {
        val q = RecipeFilterSupport.parseQuery("Śniadanie bez gotowania minimum 25 g białka")
        assertEquals("Śniadanie", q.category)
        assertTrue(q.noCook)
        assertEquals(25, q.minProtein)
    }

    @Test fun photoReviewDoesNotFlagUninformativeFilenames() {
        assertFalse(RecipePhotoReview.needsReview("Omlet", "https://example.org/12345.jpg"))
        assertFalse(RecipePhotoReview.needsReview("Omlet", ""))
    }

    @Test fun photoReviewFlagsExplicitDishConflict() {
        assertTrue(RecipePhotoReview.needsReview("Zupa pomidorowa", "https://example.org/pizza.jpg"))
    }
}
