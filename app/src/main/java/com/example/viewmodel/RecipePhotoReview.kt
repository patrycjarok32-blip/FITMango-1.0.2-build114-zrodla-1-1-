package com.example.viewmodel

import java.net.URLDecoder

/**
 * Conservative photo metadata review. Does not claim to recognize image pixels.
 * Never deletes, overwrites, or replaces a user's photo.
 */
object RecipePhotoReview {
    private val foodWords = setOf(
        "omlet", "jajecznica", "nalesniki", "owsianka", "zupa", "krupnik",
        "salatka", "kurczak", "makaron", "pizza", "burger", "koktajl",
        "ryba", "losos", "pierogi", "kanapka", "gofry", "placki"
    )
    fun needsReview(title: String, imageUrl: String): Boolean {
        if (imageUrl.isBlank()) return false // missing photos are handled by existing photo tools
        val normalizedTitle = RecipeCategories.normalized(title)
        val titleTokens = foodWords.filter { Regex("\\b" + it + "\\b").containsMatchIn(normalizedTitle) }
        if (titleTokens.isEmpty()) return false
        val filename = runCatching {
            val path = java.net.URI(imageUrl).path.orEmpty().substringAfterLast('/')
            URLDecoder.decode(path, "UTF-8")
        }.getOrDefault("")
        val normalizedFile = RecipeCategories.normalized(filename.replace('-', ' ').replace('_', ' '))
        val fileTokens = foodWords.filter { Regex("\\b" + it + "\\b").containsMatchIn(normalizedFile) }
        // Flag only explicit conflicting dish labels in the image filename.
        return fileTokens.isNotEmpty() && titleTokens.intersect(fileTokens.toSet()).isEmpty()
    }
}
