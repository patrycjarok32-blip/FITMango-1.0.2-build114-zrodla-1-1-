package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Hybrid PDF reader: retains embedded text on normal pages and OCRs image-based pages.
 * Text recognition runs on-device. Each page is processed separately to limit memory.
 */
internal object HybridPdfTextReader {
    suspend fun extract(context: Context, uri: Uri): String {
        val embedded = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use {
                PdfRecipeExtractor.extractFullTextFromPdf(it)
            } ?: error("Nie można otworzyć pliku PDF.")
        }
        val pages = Regex("""(?m)^=== STRONA (\d+) ===\s*$""")
            .findAll(embedded).toList()
        val embeddedByPage = pages.mapIndexed { index, match ->
            val number = match.groupValues[1].toInt()
            val end = pages.getOrNull(index + 1)?.range?.first ?: embedded.length
            number to embedded.substring(match.range.last + 1, end).trim()
        }.toMap()

        val descriptor = withContext(Dispatchers.IO) {
            context.contentResolver.openFileDescriptor(uri, "r")
        } ?: error("Nie można otworzyć PDF do rozpoznawania tekstu.")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return withContext(Dispatchers.IO) {
                PdfRenderer(descriptor).use { renderer ->
                    val result = StringBuilder()
                    for (index in 0 until renderer.pageCount) {
                        val pageNumber = index + 1
                        val original = embeddedByPage[pageNumber].orEmpty()
                        // A page with only a recipe heading must still undergo OCR.
                        val containsRecipeDetails = original.contains("składniki", ignoreCase = true) &&
                            (original.contains("przygotowanie", ignoreCase = true) ||
                                original.contains("sposób przygotowania", ignoreCase = true))
                        val pageText = if (original.count { it.isLetterOrDigit() } >= 180 && containsRecipeDetails) {
                            original
                        } else {
                            renderer.openPage(index).use { page ->
                                val scale = (1800f / page.width.coerceAtLeast(1))
                                    .coerceIn(1.5f, 2.5f)
                                val bitmap = Bitmap.createBitmap(
                                    (page.width * scale).toInt().coerceAtLeast(1),
                                    (page.height * scale).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888
                                )
                                try {
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    val ocr = recognize(recognizer, InputImage.fromBitmap(bitmap, 0))
                                    // Keep the embedded heading AND the OCR-only ingredients and steps.
                                    // Illustrated cookbooks often expose only the title as PDF text.
                                    when {
                                        ocr.isBlank() -> original
                                        original.isBlank() -> ocr
                                        else -> original + "\n" + ocr
                                    }
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                        if (pageText.isNotBlank()) {
                            result.append("=== STRONA ").append(pageNumber).append(" ===\n")
                                .append(pageText).append("\n\n")
                        }
                    }
                    result.toString().trim()
                }
            }
        } finally {
            recognizer.close()
        }
    }

    private suspend fun recognize(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        image: InputImage
    ): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            .addOnCanceledListener { continuation.cancel() }
    }
}
