package com.example

import com.example.ai.*
import com.example.viewmodel.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProductPhotoSupportTest {
    @Test fun packagingSuggestionsExcludeNutritionAndKeepEditableNames() {
        assertEquals(listOf("Piątnica", "Jogurt naturalny"), ProductPhotoSupport.queriesFromPackaging(
            "Piątnica\nJogurt naturalny 150 g\nWartość odżywcza\nBiałko 5 g\n120 kcal\n150 g\nJOGURT NATURALNY"
        ))
        assertTrue(ProductPhotoSupport.queriesFromPackaging("120 kcal\nBiałko 5 g\n150 g").isEmpty())
    }
    @Test fun recognitionRejectsNonFoodAndEmptyNames() {
        assertEquals("Jogurt naturalny", ProductPhotoSupport.parseQuery("""{"is_food":true,"query":" Jogurt  naturalny ","calories":999}"""))
        assertTrue(runCatching { ProductPhotoSupport.parseQuery("""{"is_food":false,"query":"but"}""") }.isFailure)
        assertTrue(runCatching { ProductPhotoSupport.parseQuery("""{"is_food":true,"query":""}""") }.isFailure)
    }
    @Test fun localSearchPreservesActualValuesAndPolishNames() {
        val product = SubstituteCandidate(name = "Płatki owsiane", caloriesPer100g = 370f)
        val found = ProductPhotoSupport.localMatches("platki owsiane", listOf(product))
        assertEquals(listOf(product), found)
        assertEquals(370f, found.single().caloriesPer100g)
        assertTrue(ProductPhotoSupport.localMatches("", listOf(product)).isEmpty())
    }
    @Test fun visionAddsImageWithoutChangingTextRequests() = runTest {
        val requests = mutableListOf<GenerateContentRequest>()
        val service = object : GeminiApiService {
            override suspend fun listModels(apiKey: String, pageSize: Int, pageToken: String?) = GeminiModelPage()
            override suspend fun generateContent(model: String, apiKey: String, request: GenerateContentRequest): GenerateContentResponse {
                requests.add(request)
                return GenerateContentResponse(listOf(Candidate(Content(listOf(Part("OK"))))))
            }
        }
        assertEquals("OK", callGeminiWithService("food", "identify", true, "fixture", service, listOf("fixture-model"), "aW1hZ2U=").getOrThrow())
        assertEquals("image/jpeg", requests[0].contents.single().parts[1].inlineData!!.mimeType)
        assertEquals("aW1hZ2U=", requests[0].contents.single().parts[1].inlineData!!.data)
        callGeminiWithService("text", "assistant", false, "fixture", service, listOf("fixture-model"))
        assertEquals(1, requests[1].contents.single().parts.size)
        assertNull(requests[1].contents.single().parts.single().inlineData)
        val json = Moshi.Builder().build().adapter(GenerateContentRequest::class.java).toJson(requests[0])
        assertTrue(json.contains("inlineData"))
        assertTrue(json.contains("image/jpeg"))
    }
}
