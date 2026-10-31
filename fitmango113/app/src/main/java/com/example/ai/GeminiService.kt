package com.example.ai

import com.squareup.moshi.JsonClas
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.GET
import retrofit2.http.Header
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit


data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null
)


data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null
)


data class Content(
    val parts: List<Part>
)


data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)


data class InlineData(val mimeType: String, val data: String)


data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)


data class Candidate(
    val content: Content
)

interface GeminiApiService {
    @GET("v1beta/models")
    suspend fun listModels(
        @Header("x-goog-api-key") apiKey: String,
        @Query("pageSize") pageSize: Int = 1000,
        @Query("pageToken") pageToken: String? = null
    ): GeminiModelPage

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

class GeminiRateLimitOrOverloadException(
    message: String,
    val httpStatusCode: Int,
    cause: Throwable? = null
) : Exception(message, cause)

object GeminiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

object GeminiRequestManager {
    private var lastRateLimitTime: Long = 0
    private val RATE_LIMIT_COOLDOWN = 60_000L // 1 minute

    suspend fun callGeminiCached(
        prompt: String,
        repository: com.example.data.DietaRepository
    ): Result<String> {
        // Check rate limit
        if (System.currentTimeMillis() - lastRateLimitTime < RATE_LIMIT_COOLDOWN) {
            return Result.failure(Exception("Limit zapytań do AI został osiągnięty. Spróbuj później."))
        }

        // Check cache
        val promptHash = prompt.hashCode()
        val cachedResponse = repository.getGeminiResponse(promptHash)
        if (cachedResponse != null) {
            return Result.success(cachedResponse)
        }

        // Call Gemini
        val result = callGemini(prompt)

        // Handle result
        result.onSuccess { response ->
            repository.insertGeminiCache(com.example.data.GeminiCacheEntity(promptHash, prompt, response))
        }.onFailure { e ->
            if (e.message?.contains("Limit zapytań", ignoreCase = true) == true) {
                lastRateLimitTime = System.currentTimeMillis()
            }
        }
        
        return result
    }

    suspend fun callGeminiPdfChunkCached(
        prompt: String,
        repository: com.example.data.DietaRepository?,
        isValidResponse: (String) -> Boolean = { it.isNotBlank() }
    ): Result<String> {
        val promptHash = prompt.hashCode()
        if (repository != null) {
            val cachedResponse = repository.getGeminiResponse(promptHash)
            if (cachedResponse != null && isValidResponse(cachedResponse)) {
                return Result.success(cachedResponse)
            }
        }

        val result = callGemini(
            prompt = prompt,
            systemPrompt = "You are Moja Dieta AI, an expert culinary recipe extractor. Always return valid JSON strictly matching the requested schema without markdown formatting or commentary.",
            jsonMode = true
        )

        result.onSuccess { response ->
            if (repository != null && isValidResponse(response)) {
                repository.insertGeminiCache(com.example.data.GeminiCacheEntity(promptHash, prompt, response))
            }
        }

        return result
    }
}

private fun logD(tag: String, msg: String) {
    try {
        android.util.Log.d(tag, msg)
    } catch (_: Throwable) {
        println("D/$tag: $msg")
    }
}

private fun logE(tag: String, msg: String, tr: Throwable? = null) {
    try {
        android.util.Log.e(tag, msg, tr)
    } catch (_: Throwable) {
        println("E/$tag: $msg (${tr?.javaClass?.simpleName}: ${tr?.message})")
    }
}

suspend fun callGemini(
    prompt: String,
    systemPrompt: String = "You are Moja Dieta AI, an expert clinical nutritionist and recipe assistant. Provide helpful, accurate, and inspiring dietary advice, meal plans, and nutritional breakdowns.",
    jsonMode: Boolean = false,
    overrideApiKey: String? = null,
    imageJpegBase64: String? = null
): Result<String> {
    val savedKey = if (overrideApiKey == null) SecureKeyStorage.getKey() else null
    val apiKey = when {
        overrideApiKey != null -> overrideApiKey
        !savedKey.isNullOrBlank() -> savedKey
        else -> ""
    }

    if (apiKey.isBlank()) {
        return Result.failure(Exception("Klucz API Gemini nie jest skonfigurowany. Przejdź do zakładki Profil -> Ustawienia Gemini AI, aby go wprowadzić."))
    }

    if (SecureKeyStorage.localOnly()) return Result.failure(Exception("Tryb lokalny jest włączony. Zapytania Gemini są wyłączone. Dane możesz wpisać ręcznie; ustawienie znajdziesz w Profilu."))
    return callGeminiWithService(prompt, systemPrompt, jsonMode, apiKey.trim(), GeminiClient.service,
        listOf("gemini-3.5-flash", "gemini-3.5-flash-lite"), imageJpegBase64)
}

internal suspend fun callGeminiWithService(
    prompt: String,
    systemPrompt: String,
    jsonMode: Boolean,
    apiKey: String,
    service: GeminiApiService,
    models: List<String>,
    imageJpegBase64: String? = null
): Result<String> {
    
    var lastError: Exception? = null
    var lastRateLimitOr503Code: Int? = null

    for (model in models) {
        var attempts = 0
        while (attempts < 2) { // Max 2 attempts per model
            try {
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)) +
                        (imageJpegBase64?.let { listOf(Part(inlineData = InlineData("image/jpeg", it))) } ?: emptyList()))),
                    systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                    generationConfig = if (jsonMode) {
                        GenerationConfig(
                            responseMimeType = "application/json",
                            temperature = 0.1f,
                            maxOutputTokens = 8192
                        )
                    } else null
                )
                
                logD("GeminiRouter", "Attempting model: $model, attempt: ${attempts + 1}")
                val response = service.generateContent(model, apiKey, request)
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (text != null) {
                    logD("GeminiRouter", "Successfully used model: $model")
                    return Result.success(text)
                } else {
                    lastError = Exception("No response generated by $model.")
                    attempts++
                    continue
                }
            } catch (e: HttpException) {
                val code = e.code()
                logE("GeminiRouter", "HTTP $code with model $model")
                val diagnostic = geminiHttpError(e, apiKey, "model $model")
                if (code in 400..403) {
                    return Result.failure(diagnostic)
                }

                if (code == 429 || code == 503) {
                    lastRateLimitOr503Code = code
                    lastError = diagnostic
                    attempts++
                    if (attempts < 2) {
                        delay(2000L)
                        continue
                    }
                    break // Try next fallback model before giving up
                }
                
                // Retryable errors: other 5xx, 404
                if (code == 404 || code in 500..599) {
                    attempts++
                    if (attempts < 2) {
                        delay(2000L) // Simple backoff
                        continue
                    }
                }
                
                lastError = diagnostic
                break // Fallback to next model
            } catch (e: SocketTimeoutException) {
                logE("GeminiRouter", "Timeout with model $model")
                attempts++
                if (attempts < 2) {
                    delay(2000L)
                    continue
                }
                lastError = e
                break // Fallback
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IOException) {
                return Result.failure(Exception("Nie można połączyć się z Gemini. Sprawdź połączenie z internetem i spróbuj ponownie."))
            } catch (e: Exception) {
                logE("GeminiRouter", "Error with model $model: ${e.javaClass.simpleName}")
                lastError = e
                break // Fallback
            }
        }
    }

    if (lastRateLimitOr503Code != null) {
        return Result.failure(
            GeminiRateLimitOrOverloadException(
                lastError?.message ?: "Limit zapytań do AI został osiągnięty (HTTP $lastRateLimitOr503Code). Spróbuj ponownie później.",
                lastRateLimitOr503Code,
                lastError
            )
        )
    }

    return Result.failure(lastError ?: Exception("Usługa Gemini nie zwróciła odpowiedzi."))
}
