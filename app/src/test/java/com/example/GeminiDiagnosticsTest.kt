package com.example

import com.example.ai.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class GeminiDiagnosticsTest {
    private class FakeService : GeminiApiService {
        val tokens = mutableListOf<String?>()
        val generated = mutableListOf<String>()
        var listing: (String?) -> GeminiModelPage = {
            GeminiModelPage(listOf(GeminiModelInfo("models/gemini-3.5-flash", listOf("generateContent"))))
        }
        var generation: () -> GenerateContentResponse = {
            GenerateContentResponse(listOf(Candidate(Content(listOf(Part("OK"))))))
        }
        override suspend fun listModels(apiKey: String, pageSize: Int, pageToken: String?): GeminiModelPage {
            tokens.add(pageToken)
            return listing(pageToken)
        }
        override suspend fun generateContent(model: String, apiKey: String, request: GenerateContentRequest): GenerateContentResponse {
            generated.add(model)
            return generation()
        }
    }

    private fun http(code: Int, body: String) = HttpException(
        Response.error<GenerateContentResponse>(code, body.toResponseBody("application/json".toMediaType()))
    )

    @Test fun paymentErrorRetainsGoogleReasonWithoutLeakingKey() = runTest {
        val service = FakeService()
        val key = "fixture-secret-not-a-real-key"
        service.generation = { throw http(402,
            """{"error":{"status":"PAYMENT_REQUIRED","message":"Balance unavailable for $key","details":[{"reason":"CREDITS_DEPLETED"}]}}""") }
        val error = diagnoseGeminiWithService(key, service).exceptionOrNull()!!.message!!
        assertTrue(error.contains("HTTP 402"))
        assertTrue(error.contains("PAYMENT_REQUIRED"))
        assertTrue(error.contains("CREDITS_DEPLETED"))
        assertTrue(error.contains("[UKRYTY KLUCZ]"))
        assertFalse(error.contains(key))
        assertEquals(1, service.generated.size)
    }

    @Test fun modelListingDoesNotClaimGenerationSuccessOrFreeQuota() = runTest {
        val service = FakeService()
        service.generation = { throw http(429, """{"error":{"message":"Daily quota unavailable"}}""") }
        val error = diagnoseGeminiWithService("fixture", service).exceptionOrNull()!!.message!!
        assertTrue(error.contains("Lista modeli: OK"))
        assertTrue(error.contains("Daily quota unavailable"))
        assertTrue(error.contains("nie potwierdza darmowego limitu"))
        assertFalse(error.contains("Generowanie: OK"))
    }

    @Test fun listingFailureDoesNotGenerateOrAssumePayment() = runTest {
        val service = FakeService()
        service.listing = { throw http(402, "<html>fixture secret</html>") }
        val error = diagnoseGeminiWithService("fixture", service).exceptionOrNull()!!.message!!
        assertTrue(error.contains("lista modeli"))
        assertTrue(error.contains("Nie można ustalić przyczyny"))
        assertFalse(error.contains("<html>"))
        assertTrue(service.generated.isEmpty())
    }

    @Test fun paginatedListUsesOnlyAdvertisedTextGenerationModel() = runTest {
        val service = FakeService()
        service.listing = { token ->
            if (token == null) GeminiModelPage(listOf(
                GeminiModelInfo("models/gemini-fixture-flash-image", listOf("generateContent")),
                GeminiModelInfo("models/embedding", listOf("embedContent"))
            ), "next") else GeminiModelPage(listOf(
                GeminiModelInfo("models/gemini-fixture-flash", listOf("generateContent"))))
        }
        val result = diagnoseGeminiWithService("fixture", service).getOrThrow()
        assertEquals(listOf(null, "next"), service.tokens)
        assertEquals(listOf("gemini-fixture-flash"), service.generated)
        assertTrue(result.contains("Generowanie: OK"))
    }

    @Test fun repeatedPageTokenStopsWithoutGeneration() = runTest {
        val service = FakeService()
        service.listing = { GeminiModelPage(nextPageToken = "repeated") }
        assertTrue(diagnoseGeminiWithService("fixture", service).isFailure)
        assertEquals(2, service.tokens.size)
        assertTrue(service.generated.isEmpty())
    }

    @Test fun missingTextModelsDoesNotGenerate() = runTest {
        val service = FakeService()
        service.listing = { GeminiModelPage(listOf(GeminiModelInfo("models/gemini-pro", listOf("generateContent")))) }
        assertTrue(diagnoseGeminiWithService("fixture", service).isFailure)
        assertTrue(service.generated.isEmpty())
    }

    @Test fun cancellationPropagates() = runTest {
        val service = FakeService()
        service.listing = { throw CancellationException("cancelled") }
        try {
            diagnoseGeminiWithService("fixture", service)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun projectIdIsRejectedBeforeNetworkCall() = runTest {
        val service = FakeService()
        val result = diagnoseGeminiWithService("gen-lang-client-fixture", service)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("identyfikator projektu"))
        assertTrue(service.tokens.isEmpty())
    }

    @Test fun normalAiCallsAlsoPreserveBadRequestDetail() = runTest {
        val service = FakeService()
        service.generation = { throw http(400, """{"error":{"status":"INVALID_ARGUMENT","message":"API key not valid"}}""") }
        val result = callGeminiWithService("test", "test", false, "fixture", service,
            listOf("gemini-3.5-flash", "gemini-3.5-flash-lite"))
        assertTrue(result.exceptionOrNull()!!.message!!.contains("API key not valid"))
        assertEquals(1, service.generated.size)
    }

    @Test fun requestUsesSecretHeaderInsteadOfQueryString() {
        val method = GeminiApiService::class.java.methods.first { it.name == "generateContent" }
        assertTrue(method.parameterAnnotations[1].any { it is retrofit2.http.Header })
        assertFalse(method.parameterAnnotations[1].any { it is retrofit2.http.Query })
    }
}
