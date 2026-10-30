package com.example.ai

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

@JsonClass(generateAdapter = true)
data class GeminiModelPage(
    val models: List<GeminiModelInfo> = emptyList(),
    val nextPageToken: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiModelInfo(
    val name: String,
    val supportedGenerationMethods: List<String> = emptyList()
)

internal fun redactGeminiDiagnostic(value: String, apiKey: String): String {
    var safe = if (apiKey.isNotBlank()) value.replace(apiKey, "[UKRYTY KLUCZ]") else value
    safe = safe.replace(Regex("AIza[A-Za-z0-9_-]{20,}"), "[UKRYTY KLUCZ]")
        .replace(Regex("(?i)(?:key|api_key|x-goog-api-key)\\s*[=:]\\s*[^\\s&\"']+"), "key=[UKRYTY KLUCZ]")
    // Server messages are displayed as plain text, never as clickable HTML.
    return safe.filter { !it.isISOControl() || it == '\n' }.take(1800)
}

internal fun geminiHttpError(error: HttpException, apiKey: String, stage: String): Exception {
    val body = try { error.response()?.errorBody()?.string() } catch (_: IOException) { null }
    val googleError = try {
        val root = body?.let { Moshi.Builder().build().adapter(Map::class.java).fromJson(it) }
        root?.get("error") as? Map<*, *>
    } catch (_: Exception) { null }
    val status = googleError?.get("status") as? String
    val message = googleError?.get("message") as? String
    val reasons = (googleError?.get("details") as? List<*>)?.mapNotNull {
        (it as? Map<*, *>)?.get("reason") as? String
    }?.distinct().orEmpty()
    val detail = if (!message.isNullOrBlank()) buildString {
        append("Odpowiedź Google: ")
        if (!status.isNullOrBlank()) append("$status — ")
        append(message)
        if (reasons.isNotEmpty()) append("\nPowód: ${reasons.joinToString()}")
    } else "Google nie przekazał czytelnego opisu błędu. Nie można ustalić przyczyny z samego kodu HTTP."
    return Exception(redactGeminiDiagnostic("Gemini: HTTP ${error.code()} ($stage).\n$detail", apiKey))
}

/** Listing a model does not certify free-tier access or available generation quota. */
suspend fun diagnoseGemini(apiKey: String): Result<String> =
    if (SecureKeyStorage.localOnly()) Result.failure(Exception("Tryb lokalny blokuje zapytania Gemini. Ustawienie znajdziesz w Profilu."))
    else diagnoseGeminiWithService(apiKey.trim(), GeminiClient.service)

internal suspend fun diagnoseGeminiWithService(
    apiKey: String,
    service: GeminiApiService
): Result<String> {
    if (apiKey.isBlank()) return Result.failure(Exception("Wpisz klucz API Gemini."))
    if (apiKey.startsWith("gen-lang-client-")) {
        return Result.failure(Exception("Wpisano identyfikator projektu zamiast klucza API. Skopiuj klucz z Google AI Studio → API keys."))
    }
    val models = linkedSetOf<String>()
    try {
        var token: String? = null
        val seenTokens = mutableSetOf<String>()
        do {
            val page = service.listModels(apiKey, 1000, token)
            page.models.filter { "generateContent" in it.supportedGenerationMethods }
                .map { it.name.removePrefix("models/") }
                .filter { it.matches(Regex("[a-zA-Z0-9._-]+")) }
                .forEach { models.add(it) }
            token = page.nextPageToken?.takeIf { it.isNotBlank() }
            if (token != null && (!seenTokens.add(token) || seenTokens.size > 10)) {
                return Result.failure(Exception("Nie udało się pobrać pełnej listy modeli: nieprawidłowa paginacja Google."))
            }
        } while (token != null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        return Result.failure(geminiHttpError(e, apiKey, "lista modeli"))
    } catch (_: IOException) {
        return Result.failure(Exception("Nie można pobrać listy modeli Gemini. Sprawdź połączenie z internetem."))
    } catch (_: Exception) {
        return Result.failure(Exception("Nie można odczytać listy modeli Gemini. Google zwrócił nieprawidłową odpowiedź."))
    }
    val preferred = listOf("gemini-3.5-flash", "gemini-3.5-flash-lite").filter { it in models }
    // Diagnose only text Flash models; do not run a paid/large model just because it is listed.
    val textFlash = models.filter {
        it.startsWith("gemini-") && "flash" in it &&
            listOf("image", "audio", "live", "tts", "preview", "exp").none { suffix -> suffix in it }
    }.sorted()
    val model = (preferred + textFlash).firstOrNull()
        ?: return Result.failure(Exception("Lista modeli została pobrana, ale brak obsługiwanego tekstowego modelu Flash. Generowanie nie zostało uruchomione."))
    val summary = "Lista modeli: OK. Modele tekstowe Flash: ${textFlash.take(12).joinToString()}" +
        (if (textFlash.size > 12) " (i ${textFlash.size - 12} innych)" else "") +
        "\nObecność na liście nie potwierdza darmowego limitu."
    val generation = callGeminiWithService("Odpowiedz tylko OK.", "Reply briefly.", false,
        apiKey, service, listOf(model))
    return generation.fold(
        onSuccess = { Result.success("$summary\nGenerowanie: OK (model $model).") },
        onFailure = { Result.failure(Exception("$summary\n${it.message}")) }
    )
}
