package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RecipeImageImportException(message: String) : Exception(message)

object RecipeImageExtractor {
    const val MAX_IMAGES = 12

    /** One recipe, all pages in picker order. Never silently skip unreadable pages. */
    internal suspend fun extractTexts(uris: List<Uri>, read: suspend (Uri) -> String): String {
        val images = uris.distinct()
        if (images.isEmpty() || images.size > MAX_IMAGES) {
            throw RecipeImageImportException("Wybierz od 1 do $MAX_IMAGES zdjęć jednego przepisu.")
        }
        val pages = images.mapIndexed { index, uri ->
            val text = try { read(uri).trim() } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                throw RecipeImageImportException("Nie udało się odczytać zdjęcia ${index + 1} z ${images.size}. Wybierz wyraźniejszy plik i spróbuj ponownie.")
            }
            if (text.isBlank()) throw RecipeImageImportException("Brak czytelnego tekstu na zdjęciu ${index + 1} z ${images.size}. Wybierz zdjęcia składników i przygotowania.")
            text
        }
        return pages.joinToString("\n\n")
    }

    // Bundled Latin OCR model; recognizing the image does not require Gemini or a network.
    suspend fun extractText(context: Context, uri: Uri, alignTableRows: Boolean = false): String {
        val image = withContext(Dispatchers.IO) { boundedImage(context, uri) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val text = if (alignTableRows) {
                            // Labels and numbers often arrive in separate OCR blocks. Join by row,
                            // preserving all columns so ambiguous values are never silently chosen.
                            val rows = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
                            result.textBlocks.flatMap { it.lines }.sortedBy { it.boundingBox?.centerY() ?: 0 }.forEach { line ->
                                val rect = line.boundingBox
                                val previous = rows.lastOrNull()?.firstOrNull()?.boundingBox
                                if (rect != null && previous != null &&
                                    kotlin.math.abs(rect.centerY() - previous.centerY()) <= minOf(rect.height(), previous.height()) / 2) {
                                    rows.last().add(line)
                                } else rows.add(mutableListOf(line))
                            }
                            rows.joinToString("\n") { row -> row.sortedBy { it.boundingBox?.left ?: 0 }.joinToString(" ") { it.text } }
                        } else result.text
                        if (continuation.isActive) continuation.resume(text)
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.resumeWithException(RecipeImageImportException("Odczyt został przerwany."))
                    }
            }
        } finally {
            recognizer.close()
        }
    }

    /** Avoid loading a full-resolution phone photo into ML Kit. Respect camera EXIF orientation. */
    internal fun boundedImage(context: Context, uri: Uri): InputImage =
        InputImage.fromBitmap(boundedBitmap(context, uri), 0)

    // Image preparation is independently testable without initializing an OCR engine.
    internal fun boundedBitmap(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Nieczytelne zdjęcie" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw RecipeImageImportException("Nie można otworzyć zdjęcia")
        val orientation = runCatching { resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } }.getOrNull()
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        val oriented = if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return oriented
    }
}
