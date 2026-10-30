package com.example.viewmodel

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipeMultiImageTest {
    @Test fun `pages preserve order and form one complete recipe`() = runBlocking {
        val a = Uri.parse("content://test/ingredients")
        val b = Uri.parse("content://test/preparation")
        val calls = mutableListOf<Uri>()
        val text = RecipeImageExtractor.extractTexts(listOf(a, b, a)) {
            calls.add(it)
            if (it == a) "Zupa\nSkładniki:\n100 g cebuli\n800 g wody"
            else "Przygotowanie:\n1. Ugotuj składniki."
        }
        assertEquals(listOf(a, b), calls)
        val recipe = parseStructuredRecipeText(text, "Zdjęcie OCR")!!
        assertTrue(recipe.ingredients.contains("100 g cebuli"))
        assertTrue(recipe.instructions.contains("Ugotuj składniki"))
    }

    @Test fun `blank page is identified rather than silently omitted`() = runBlocking {
        try {
            RecipeImageExtractor.extractTexts(listOf(Uri.parse("content://test/1"), Uri.parse("content://test/2"))) {
                if (it.lastPathSegment == "1") "Składniki" else ""
            }
            fail("Expected unreadable page error")
        } catch (e: RecipeImageImportException) { assertTrue(e.message!!.contains("2 z 2")) }
    }

    @Test fun `too many images are rejected before OCR`() = runBlocking {
        var calls = 0
        try {
            RecipeImageExtractor.extractTexts((1..13).map { Uri.parse("content://test/$it") }) {
                calls++; "text"
            }
            fail("Expected image limit")
        } catch (_: RecipeImageImportException) { assertEquals(0, calls) }
    }

    @Test fun `OCR cancellation propagates`() = runBlocking {
        try {
            RecipeImageExtractor.extractTexts(listOf(Uri.parse("content://test/1"))) { throw CancellationException("cancel") }
            fail("Expected cancellation")
        } catch (_: CancellationException) { /* Preserved, never converted to unreadable image. */ }
    }
}
