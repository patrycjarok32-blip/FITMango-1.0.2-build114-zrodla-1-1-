package com.example.viewmodel

import org.junit.Assert.*
import org.junit.Test

class RecipePhotoReviewTest {
    @Test fun filenameMismatchFlagsExplicitDifferentDish() {
        assertTrue(RecipePhotoReview.needsReview("Omlet ze szpinakiem", "https://example.com/pizza-margherita.jpg"))
    }
    @Test fun matchingFilenameDoesNotFlag() {
        assertFalse(RecipePhotoReview.needsReview("Omlet ze szpinakiem", "https://example.com/omlet-szpinak.jpg"))
    }
    @Test fun genericImageDoesNotCreateFalseAlarm() {
        assertFalse(RecipePhotoReview.needsReview("Omlet ze szpinakiem", "https://example.com/12345.jpg"))
    }
    @Test fun noPhotoDoesNotCreateFalseAlarm() {
        assertFalse(RecipePhotoReview.needsReview("Omlet ze szpinakiem", ""))
    }
}
