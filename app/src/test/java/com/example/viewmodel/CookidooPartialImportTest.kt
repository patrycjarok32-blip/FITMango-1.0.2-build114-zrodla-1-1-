package com.example.viewmodel

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CookidooPartialImportTest {
    private val url = "https://cookidoo.pl/recipes/recipe/pl-PL/r122680"
    // Minimal public-page shape observed on 2026-10-08: no preparation in JSON-LD.
    private val html = """<script type="application/ld+json">{
        "@type":"Recipe","name":"Zupa szczawiowa","recipeYield":"4 porcje",
        "recipeIngredient":["100 g cebuli","800 g wody","4 jajka"],
        "image":"https://assets.tmecosys.com/example.jpg",
        "nutrition":{"calories":"187.3 kcal","proteinContent":"9.6 g",
        "carbohydrateContent":"15.1 g","fatContent":"10.9 g"}
    }</script>"""

    @Test fun `shared Cookidoo caption and multiline caption resolve URL`() {
        for (input in listOf("Sprawdź ten pyszny przepis znaleziony na Cookidoo®: Zupa szczawiowa $url",
            "Zupa szczawiowa\n$url")) {
            val classified = classifyRecipeInput("Website", input)
            assertTrue(classified is RecipeImportInput.Url)
            assertEquals(url, (classified as RecipeImportInput.Url).uri.toString())
            assertTrue(classified.isCookidoo)
        }
    }

    @Test fun `shared Instagram caption supplies fetchable URL`() {
        val input = "Zobacz przepis https://www.instagram.com/reel/abc/?x=1"
        assertEquals(RecipeImportInput.InstagramUrl, classifyRecipeInput("Instagram", input))
        assertEquals("https://www.instagram.com/reel/abc/?x=1", parseValidUriOrNull(input).toString())
    }

    @Test fun `pasted recipe and malformed or ambiguous links remain separate`() {
        val text = "Zupa\nSkładniki:\n2 jajka\n100 g cebuli\nPrzygotowanie:\nUgotuj.\nŹródło: $url"
        assertEquals(RecipeImportInput.Text(text), classifyRecipeInput("Cookidoo", text))
        assertEquals(RecipeImportInput.InvalidUrl, classifyRecipeInput("Website", "https://bad url"))
        assertNull(extractSharedRecipeUrlOrNull("Linki $url https://example.com"))
    }

    @Test fun `partial preview preserves public data without inventing preparation`() {
        val document = Jsoup.parse(html, url)
        assertNull(extractPublicCookidooRecipeOrNull(document, url))
        val preview = extractCookidooPreviewOrNull(document, url)!!
        assertEquals("Zupa szczawiowa", preview.title)
        assertEquals("4 porcje", preview.servings)
        assertEquals(187, preview.calories) // Per portion, never multiplied by four.
        assertEquals(10, preview.protein)
        assertEquals(15, preview.carbs)
        assertEquals(11, preview.fat)
        assertEquals("https://assets.tmecosys.com/example.jpg", preview.imageUrl)
        assertEquals(url, preview.source)
        assertEquals("", preview.instructions)
        assertTrue(preview.requiresInstructionsCompletion)
    }

    @Test fun `login placeholder is not preparation and empty page is rejected`() {
        val gated = html.replace("\"recipeYield\":", "\"recipeInstructions\":\"Zaloguj się, aby zobaczyć przepis\",\"recipeYield\":")
        val preview = extractCookidooPreviewOrNull(Jsoup.parse(gated), url)!!
        assertEquals("", preview.instructions)
        assertTrue(preview.requiresInstructionsCompletion)
        assertNull(extractCookidooPreviewOrNull(Jsoup.parse("<h1>Zaloguj się</h1>"), url))
        val complete = html.replace("\"recipeYield\":", "\"recipeInstructions\":\"Ugotuj składniki.\",\"recipeYield\":")
        assertFalse(extractCookidooPreviewOrNull(Jsoup.parse(complete), url)!!.requiresInstructionsCompletion)
    }
}
