package com.example.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfImportReportTest {
    private fun recipe(title: String, ingredients: String, instructions: String) =
        ParsedRecipe(title = title, servings = "1", ingredients = ingredients,
            instructions = instructions, calories = 0, protein = 0, carbs = 0,
            fat = 0, source = "PDF", sourcePageOrChunk = "12")

    @Test fun incompleteRecipesRemainVisibleForReview() {
        val complete = recipe("Lane kluski", "1 jajko", "Wymieszaj")
        val incomplete = recipe("Zupa", "pomidory", "")
        val outcome = PdfChunkExtractionOutcome(
            completedAllChunks = true, rateLimitedOrPaused = false,
            nextChunkIndex = 2, totalChunks = 2, perChunkCounts = mapOf(0 to 2, 1 to 1),
            recipesBeforeDeduplication = listOf(complete, complete, incomplete),
            deduplicatedRecipes = listOf(complete, incomplete)
        )
        val report = outcome.toReviewReport()
        assertEquals(1, report.completeRecipes)
        assertEquals(listOf("Zupa"), report.incompleteTitles)
        assertEquals(1, report.duplicatesRemoved)
        assertTrue(report.summary().contains("do sprawdzenia 1"))
    }

    @Test fun pausedImportIsReportedWithoutClaimingCompletion() {
        val report = PdfChunkExtractionOutcome(
            completedAllChunks = false, rateLimitedOrPaused = true,
            nextChunkIndex = 1, totalChunks = 4, perChunkCounts = emptyMap(),
            recipesBeforeDeduplication = emptyList(), deduplicatedRecipes = emptyList(),
            errorMessage = "429"
        ).toReviewReport()
        assertEquals(1, report.processedChunks)
        assertTrue(report.summary().contains("wstrzymany"))
    }
}
