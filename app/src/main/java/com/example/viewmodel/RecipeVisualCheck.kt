package com.example.viewmodel

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Opt-in, on-device visual analysis; images are not uploaded to an AI service by this checker. */
object RecipeVisualCheck {
    data class Result(val summary: String, val labels: List<String>, val needsReview: Boolean)

    suspend fun inspect(context: Context, title: String, imageUrl: String): Result {
        if (imageUrl.isBlank()) return Result("Ten przepis nie ma zdjęcia.", emptyList(), true)
        val bitmap = withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build()
            val result = ImageLoader(context).execute(request) as? SuccessResult
                ?: return@withContext null
            (result.drawable as? BitmapDrawable)?.bitmap
        } ?: return Result("Nie udało się odczytać zdjęcia. Sprawdź połączenie lub plik.", emptyList(), true)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        try {
            val labels = suspendCancellableCoroutine<List<com.google.mlkit.vision.label.ImageLabel>> { continuation ->
                labeler.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resume(emptyList()) }
            }.filter { it.confidence >= 0.65f }.sortedByDescending { it.confidence }
                .take(8).map { it.text }
            if (labels.isEmpty()) return Result("Nie udało się wiarygodnie rozpoznać potrawy. Sprawdź zdjęcie ręcznie.", labels, true)
            val normalizedTitle = RecipeCategories.normalized(title)
            val translated = mapOf(
                "omlet" to listOf("omelette", "egg", "food"),
                "jajecznica" to listOf("egg", "breakfast", "food"),
                "zupa" to listOf("soup", "food"),
                "salatka" to listOf("salad", "vegetable", "food"),
                "pizza" to listOf("pizza", "food"),
                "burger" to listOf("hamburger", "burger", "food"),
                "makaron" to listOf("pasta", "noodle", "food"),
                "kanapka" to listOf("sandwich", "bread", "food"),
                "ciasto" to listOf("cake", "dessert", "food"),
                "koktajl" to listOf("smoothie", "drink", "beverage"),
                "ryba" to listOf("fish", "seafood", "food"),
                "kurczak" to listOf("chicken", "poultry", "food")
            )
            val expected = translated.filterKeys { it in normalizedTitle }.values.flatten()
            val found = labels.joinToString(" ").lowercase(java.util.Locale.ROOT)
            val matches = expected.any { it in found }
            val mismatch = expected.isNotEmpty() && !matches
            val summary = when {
                expected.isEmpty() -> "Wykryto elementy zdjęcia, ale nazwa dania jest zbyt szczegółowa do automatycznego porównania."
                mismatch -> "Nie potwierdzono zgodności zdjęcia z nazwą. Sprawdź je ręcznie (to nie jest pewne wykrycie błędu)."
                else -> "Wykryte elementy są zgodne z ogólnym typem potrawy. Sprawdź składniki ręcznie."
            }
            return Result(summary, labels, mismatch)
        } finally { labeler.close() }
    }
}
