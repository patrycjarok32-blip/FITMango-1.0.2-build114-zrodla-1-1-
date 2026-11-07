package com.example.viewmodel

import java.net.URLDecoder
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
    data class VisualReview(val status: String, val labels: List<String>, val confidence: Float)

    /**
     * On-device ML Kit image classification. Only analyses photos explicitly selected by the user.
     * The image and labels are not sent to a FITMango server or saved in the database.
     * Unknown/ambiguous results are not treated as incorrect photos.
     */
    suspend fun analyzeBitmap(title: String, bitmap: Bitmap): VisualReview {
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        return try {
            val labels = suspendCancellableCoroutine<List<com.google.mlkit.vision.label.ImageLabel>> { continuation ->
                labeler.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resume(emptyList()) }
            }
            val top = labels.filter { it.confidence >= .60f }.sortedByDescending { it.confidence }.take(6)
            val foodHints = setOf("food", "dish", "meal", "cuisine", "breakfast", "lunch",
                "dinner", "salad", "soup", "dessert", "bread", "vegetable", "fruit", "meat",
                "pasta", "rice", "egg", "sandwich", "pizza", "cake", "seafood")
            val hasFood = top.any { label -> foodHints.any { it in label.text.lowercase(java.util.Locale.ROOT) } }
            val other = top.firstOrNull { it.confidence >= .80f && !hasFood }
            val status = when {
                top.isEmpty() -> "Nie można wiarygodnie ocenić zdjęcia"
                hasFood -> "Wykryto jedzenie; model nie potwierdza konkretnej potrawy. Sprawdź ręcznie"
                other != null -> "Możliwe niedopasowanie zdjęcia; sprawdź ręcznie"
                else -> "Nie można potwierdzić zgodności potrawy"
            }
            VisualReview(status, top.map { it.text }, top.firstOrNull()?.confidence ?: 0f)
        } finally {
            labeler.close()
        }
    }

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
