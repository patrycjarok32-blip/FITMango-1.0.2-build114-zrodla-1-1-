package com.example.viewmodel

/**
 * Non-destructive review report. Incomplete recipes are kept for manual correction,
 * never silently counted as successfully imported complete recipes.
 */
data class PdfImportReport(
    val processedChunks: Int,
    val totalChunks: Int,
    val foundBeforeDeduplication: Int,
    val uniqueRecipes: Int,
    val completeRecipes: Int,
    val incompleteTitles: List<String>,
    val duplicatesRemoved: Int,
    val paused: Boolean,
    val errorMessage: String?
) {
    fun summary(): String = buildString {
        append("Analiza PDF: ").append(processedChunks).append('/').append(totalChunks)
            .append(" części; wykryto ").append(foundBeforeDeduplication)
            .append(" przepisów, unikalnych ").append(uniqueRecipes)
            .append(", kompletnych ").append(completeRecipes)
            .append(", do sprawdzenia ").append(incompleteTitles.size)
            .append(", duplikatów ").append(duplicatesRemoved).append('.')
        if (paused) append(" Import wstrzymany — można wznowić.")
        if (!errorMessage.isNullOrBlank()) append(" Problem: ").append(errorMessage)
    }
}

fun PdfChunkExtractionOutcome.toReviewReport(): PdfImportReport {
    val incomplete = deduplicatedRecipes.filter {
        it.ingredients.isBlank() || it.instructions.isBlank()
    }
    return PdfImportReport(
        processedChunks = if (completedAllChunks) totalChunks else nextChunkIndex.coerceIn(0, totalChunks),
        totalChunks = totalChunks,
        foundBeforeDeduplication = recipesBeforeDeduplication.size,
        uniqueRecipes = deduplicatedRecipes.size,
        completeRecipes = deduplicatedRecipes.size - incomplete.size,
        incompleteTitles = incomplete.map { it.title },
        duplicatesRemoved = (recipesBeforeDeduplication.size - deduplicatedRecipes.size).coerceAtLeast(0),
        paused = rateLimitedOrPaused,
        errorMessage = errorMessage
    )
}
