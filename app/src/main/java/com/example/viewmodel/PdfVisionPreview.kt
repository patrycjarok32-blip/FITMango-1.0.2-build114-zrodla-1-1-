package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Explicit opt-in visual preview. No database writes: user reviews results first.
 * Pages are rendered individually, so large cookbooks do not fill memory.
 */
internal object PdfVisionPreview {
    suspend fun firstThree(context: Context, uri: Uri): Result<List<ParsedRecipe>> = withContext(Dispatchers.IO) {
        runCatching {
            val found = mutableListOf<ParsedRecipe>()
            val errors = mutableListOf<String>()
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Brak dostępu do pliku. Wybierz PDF ponownie.")
            PdfRenderer(descriptor).use { renderer ->
                for (pageIndex in 0 until renderer.pageCount) {
                    if (found.size >= 3) break
                    // Cover pages after a long introduction, with a bounded preview.
                    if (pageIndex >= 35) break
                    try {
                        val encoded = renderer.openPage(pageIndex).use { page ->
                            val scale = (1500f / page.width.coerceAtLeast(1)).coerceIn(1f, 2f)
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                ByteArrayOutputStream().use { stream ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                                    Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                                }
                            } finally { bitmap.recycle() }
                        }
                        val response = com.example.ai.callGemini(
                            prompt = PdfRecipeExtractor.buildPdfChunkExtractionPrompt(
                                "Strona PDF", pageIndex, renderer.pageCount,
                                "Odczytaj przepis bezpośrednio z OBRAZU strony. Zachowaj składniki, ilości, instrukcje i wartości odżywcze. Nie zgaduj brakujących danych. Jeśli nie ma przepisu, zwróć pustą listę."
                            ),
                            jsonMode = true,
                            imageJpegBase64 = encoded
                        ).getOrThrow()
                        val parsed = PdfRecipeExtractor.tryParseStructuredRecipesJson(
                            response, "Strona ${pageIndex + 1}"
                        ) ?: error("AI zwróciło nieprawidłowy JSON na stronie ${pageIndex + 1}.")
                        found.addAll(parsed.map { it.toParsedRecipe("PDF (Gemini Vision)") })
                    } catch (e: Exception) {
                        errors.add("Strona ${pageIndex + 1}: ${e.message ?: "błąd odczytu"}")
                        if (errors.size >= 3 && found.isEmpty()) break
                    }
                }
            }
            if (found.isEmpty()) error(
                "Nie znaleziono przepisów w podglądzie. " + errors.take(3).joinToString("; ")
            )
            PdfRecipeExtractor.deduplicateParsedRecipes(found).take(3)
        }
    }
}
