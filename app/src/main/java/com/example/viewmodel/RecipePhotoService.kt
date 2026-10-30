package com.example.viewmodel

import com.example.data.RecipeEntity
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class PhotoServiceException(val provider: String, val retryAfterMillis: Long, val notice: String) : IOException(notice)
data class AutomaticPhoto(val url: String, val credit: String, val ai: Boolean)

/** REST only: no hidden WebView login, paid inference provider, or embedded credential. */
class RecipePhotoService(
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(25, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).callTimeout(240, TimeUnit.SECONDS).build(),
    private val pixabayBase: String = "https://pixabay.com/api/",
    private val fluxBase: String = "https://black-forest-labs-flux-1-schnell.hf.space",
    private val cacheDirectory: File? = null,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private val requestLock = Any()
        private var nextRequestNanos = 0L
        private fun paceSearch() = synchronized(requestLock) {
            val delay = nextRequestNanos - System.nanoTime()
            if (delay > 0) TimeUnit.NANOSECONDS.sleep(delay)
            nextRequestNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(650)
        }
    }
    fun pixabay(recipe: RecipeEntity, token: String, englishQuery: String? = null): AutomaticPhoto? {
        val query = englishQuery ?: RecipePhotoMatching.query(recipe.title) ?: return null
        val photo = searchPixabay(query, token).filter {
            if (englishQuery == null) RecipePhotoMatching.matches(recipe.title, it.title)
            else RecipePhotoMatching.matchesEnglish(query, it.title)
        }.maxByOrNull { RecipePhotoMatching.score(query, it.title, recipe.ingredients) } ?: return null
        return AutomaticPhoto(photo.imageUrl, "Zdjęcie: poglądowe Pixabay; ${photo.author}; ${photo.pageUrl}; ${photo.licenseUrl}", false)
    }

    /** Cache even empty searches for 24 hours; download only the selected photo. */
    fun searchPixabay(query: String, token: String): List<RecipePhotoCandidate> {
        require(token.isNotBlank()) { "Dodaj darmowy klucz Pixabay w Profilu." }
        val text = query.trim().take(100)
        require(text.length >= 2) { "Wpisz nazwę potrawy (2–100 znaków)." }
        val digest = MessageDigest.getInstance("SHA-256").digest((pixabayBase + "|" + token.trim() + "|" + text).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cache = cacheDirectory?.let { File(it, "$digest.json") }
        val cached = cache?.takeIf { it.isFile && now() - it.lastModified() in 0 until 24 * 60 * 60 * 1000L }
            ?.let { runCatching { it.readText().takeIf { value -> JSONObject(value).has("hits") } }.getOrNull() }
        val json = cached ?: run {
            val url = pixabayBase.toHttpUrl().newBuilder().addQueryParameter("key", token.trim())
                .addQueryParameter("q", text).addQueryParameter("lang", "en")
                .addQueryParameter("image_type", "photo").addQueryParameter("category", "food")
                .addQueryParameter("safesearch", "true").addQueryParameter("orientation", "horizontal")
                .addQueryParameter("per_page", "20").build()
            paceSearch()
            val result = executeText(Request.Builder().url(url).build(), "pixabay")
            require(JSONObject(result).has("hits")) { "Nieprawidłowa odpowiedź Pixabay." }
            cache?.let { target ->
                runCatching {
                    target.parentFile?.mkdirs()
                    val temporary = File.createTempFile("search", ".tmp", target.parentFile)
                    try { temporary.writeText(result); if (temporary.renameTo(target)) target.setLastModified(now()) }
                    finally { temporary.delete() }
                }
            }
            result
        }
        val hits = JSONObject(json).optJSONArray("hits") ?: return emptyList()
        return (0 until hits.length()).mapNotNull { index ->
            val hit = hits.optJSONObject(index) ?: return@mapNotNull null
            val image = hit.optString("webformatURL")
            val page = hit.optString("pageURL")
            val safePage = runCatching { page.toHttpUrl().let { it.isHttps && it.host == "pixabay.com" && it.username.isEmpty() && it.password.isEmpty() } }.getOrDefault(false)
            if (!trustedImage(image, false) || !safePage) return@mapNotNull null
            RecipePhotoCandidate(hit.optString("tags"), image, page, hit.optString("user").ifBlank { "Pixabay" },
                "Pixabay Content License", "https://pixabay.com/service/license-summary/")
        }
    }
    fun startAi(recipe: RecipeEntity, token: String): String {
        val prompt = "Appetizing realistic cookbook food photography, close-up of a plated meal. " +
            "Recipe name: ${recipe.title.take(180)}. Ingredients: ${recipe.ingredients.take(1400)}. " +
            "Show only the finished dish and its ingredients, natural daylight, clean background, no text, no signs, no packaging."
        val payload = JSONObject().put("data", JSONArray().put(prompt).put(kotlin.random.Random.nextInt(1, Int.MAX_VALUE))
            .put(false).put(768).put(768).put(4)).toString()
        val request = authorized(Request.Builder().url("$fluxBase/gradio_api/call/infer"), token)
            .post(payload.toRequestBody("application/json".toMediaType())).build()
        return JSONObject(executeText(request, "hf")).getString("event_id").also {
            require(it.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        }
    }
    fun awaitAi(eventId: String, token: String): AutomaticPhoto {
        require(eventId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        val request = authorized(Request.Builder().url("$fluxBase/gradio_api/call/infer/$eventId"), token).build()
        client.newCall(request).execute().use { response ->
            checkStatus(response.code, "hf")
            val reader = response.body?.charStream()?.buffered() ?: throw IOException("Pusta odpowiedź AI")
            var event = ""
            var count = 0
            while (true) {
                val line = reader.readLine() ?: break
                count += line.length
                if (count > 256 * 1024) throw IOException("Odpowiedź AI jest zbyt duża")
                if (line.startsWith("event:")) event = line.substringAfter(':').trim()
                if (!line.startsWith("data:")) continue
                val data = line.substringAfter(':').trim()
                if (event == "error") throw parseAiError(data)
                if (event == "complete") return parseComplete(data)
            }
            throw IOException("AI jeszcze nie zwróciło zdjęcia")
        }
    }
    fun parseComplete(data: String): AutomaticPhoto {
        val image = JSONArray(data).getJSONObject(0).getString("url")
        require(trustedImage(image, true)) { "Nieznany adres obrazu AI" }
        return AutomaticPhoto(image, "Zdjęcie: ilustracja AI; wygenerowana dla przepisu przez FLUX.1-schnell. Obraz poglądowy.", true)
    }
    fun parseAiError(data: String): PhotoServiceException {
        val lower = data.lowercase()
        val quota = listOf("quota", "exceeded", "rate limit", "gpu duration", "too many").any { it in lower }
        return PhotoServiceException("hf", if (quota) 24 * 60 * 60 * 1000L else 15 * 60 * 1000L,
            if (quota) "Darmowy limit AI wyczerpany. Kolejka spróbuje ponownie za 24 godziny." else "Serwis AI jest chwilowo niedostępny. Kolejka spróbuje ponownie.")
    }
    fun trustedImage(url: String, ai: Boolean): Boolean = runCatching {
        val parsed = url.toHttpUrl()
        parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty() &&
            (if (ai) parsed.host == fluxBase.toHttpUrl().host else parsed.host in setOf("cdn.pixabay.com", "pixabay.com"))
    }.getOrDefault(false)
    fun download(photo: AutomaticPhoto): ByteArray {
        var url = photo.url.toHttpUrl()
        repeat(4) {
            require(trustedImage(url.toString(), photo.ai)) { "Nieznany adres zdjęcia." }
            // Never send API credentials with a download or redirect.
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    url = url.resolve(response.header("Location").orEmpty()) ?: throw IOException("Nieprawidłowe przekierowanie zdjęcia")
                } else {
                    if (!response.isSuccessful) throw IOException("Nie udało się pobrać zdjęcia")
                    val body = response.body ?: throw IOException("Puste zdjęcie")
                    if (body.contentLength() > 8 * 1024 * 1024) throw IOException("Zdjęcie jest zbyt duże")
                    return body.byteStream().use { readLimited(it, 8 * 1024 * 1024) }
                }
            }
        }
        throw IOException("Zbyt wiele przekierowań zdjęcia")
    }
    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
            if (read < 0) break
            output.write(buffer, 0, read)
            if (output.size() > limit) throw IOException("Plik jest zbyt duży")
        }
        return output.toByteArray()
    }
    private fun authorized(builder: Request.Builder, token: String): Request.Builder =
        if (token.isBlank()) builder else builder.header("Authorization", "Bearer $token")
    private fun executeText(request: Request, provider: String): String = client.newCall(request).execute().use {
        checkStatus(it.code, provider)
        val body = it.body ?: throw IOException("Pusta odpowiedź")
        val bytes = body.byteStream().use { stream -> readLimited(stream, 256 * 1024) }
        if (bytes.size > 256 * 1024) throw IOException("Zbyt duża odpowiedź")
        String(bytes, Charsets.UTF_8)
    }
    private fun checkStatus(code: Int, provider: String) {
        if (code in 200..299) return
        val auth = code == 401 || code == 403 || (provider == "pixabay" && code == 400)
        val quota = code == 429
        throw PhotoServiceException(provider, when { auth -> 365 * 24 * 60 * 60 * 1000L; quota -> 24 * 60 * 60 * 1000L; else -> 15 * 60 * 1000L },
            when { auth -> "Sprawdź klucz ${if (provider == "pixabay") "Pixabay" else "Hugging Face"} w Profilu."; quota -> "Darmowy limit zdjęć wyczerpany. Kolejka poczeka 24 godziny."; else -> "Serwis zdjęć jest chwilowo niedostępny. Kolejka spróbuje ponownie." })
    }
}

/** Conservative matching: unfamiliar or poorly described results fall through to AI. */
object RecipePhotoMatching {
    private val dishes = listOf(
        listOf("gofr", "waffle") to "waffles",
        listOf("pulpety", "klops", "meatball") to "meatballs",
        listOf("gnocchi") to "gnocchi",
        listOf("pierog", "dumpling") to "dumplings",
        listOf("risotto") to "risotto",
        listOf("gulasz", "stew") to "stew",
        listOf("placusz", "placki", "racuch") to "pancakes",
        listOf("szarlot", "apple pie") to "pie",
        listOf("ciasto", "sernik", "cake") to "cake",
        listOf("zapiekank", "casserole") to "casserole",
        listOf("kotlet", "cutlet") to "cutlet",
        listOf("koktajl", "smoothie") to "smoothie",
        listOf("chleb", "bread") to "bread",
        listOf("pizza") to "pizza",
        listOf("omlet", "omelet", "omelette") to "omelette",
        listOf("jajeczn", "scrambled eggs") to "scrambled eggs",
        listOf("owsiank", "porridge", "oatmeal") to "oatmeal",
        listOf("platki", "cereal") to "cereal",
        listOf("nalesnik", "pancake") to "pancakes",
        listOf("salat", "salad") to "salad",
        listOf("kanapk", "sandwich") to "sandwich",
        listOf("tost", "toast") to "toast",
        listOf("zupa", "soup") to "soup",
        listOf("makaron", "pasta", "spaghetti") to "pasta",
        listOf("frytk", "fries") to "fries",
        listOf("ryz", "rice") to "rice",
        listOf("losos", "salmon") to "salmon",
        listOf("kurcz", "chicken") to "chicken",
        listOf("frytk", "fries") to "fries")
    private val ingredients = listOf(
        listOf("kurcz", "chicken") to "chicken", listOf("losos", "salmon") to "salmon",
        listOf("awokado", "avocado") to "avocado", listOf("szpin", "spinach") to "spinach",
        listOf("jabl", "apple", "szarlot") to "apple", listOf("brokul", "broccoli") to "broccoli",
        listOf("pomidor", "tomato") to "tomato",
        listOf("ziemniac", "ziemniak", "potato") to "potato",
        listOf("szczawi", "szczaw", "sorrel") to "sorrel",
        listOf("gruszk", "pear") to "pear",
        listOf("banan", "banana") to "banana",
        listOf("dyni", "pumpkin") to "pumpkin",
        listOf("pieczark", "grzyb", "mushroom") to "mushroom",
        listOf("marchew", "carrot") to "carrot",
        listOf("truskaw", "strawberry") to "strawberry",
        listOf("wolow", "beef") to "beef",
        listOf("orzech", "peanut") to "peanut",
        listOf("malin", "raspberry") to "raspberry",
        listOf("borow", "blueberry") to "blueberry",
        listOf("czekolad", "chocolate") to "chocolate")
    private fun normalize(text: String) = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace("ł", "l")
    private fun terms(title: String): List<String> {
        val text = normalize(title)
        val dish = dishes.firstOrNull { (names, _) -> names.any { it in text } }?.second ?: return emptyList()
        return (listOf(dish) + ingredients.filter { (names, _) -> names.any { it in text } }.map { it.second }).distinct()
    }
    fun query(title: String): String? = terms(title).takeIf { it.isNotEmpty() }?.joinToString(" ")
    fun cleanEnglishQuery(text: String): String? = text.trim().lowercase().takeIf {
        it.length in 3..80 && Regex("[a-z]+(?: [a-z]+){0,7}").matches(it)
    }
    fun englishSearchQuery(text: String): String? {
        val normalized = normalize(text)
        val polish = Regex("[ąćęłńóśźż]", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
            Regex("\\b(zupa|gofr\\w*|owsiank\\w*|nalesnik\\w*|kurcz\\w*|losos\\w*|salatk\\w*|makaron\\w*|ryz|szpinak\\w*|pomidor\\w*|ciasto|sernik|placki|placusz\\w*)\\b").containsMatchIn(normalized)
        return if (polish) query(text) else cleanEnglishQuery(text) ?: query(text)
    }
    fun matchesEnglish(query: String, alt: String): Boolean {
        val text = normalize(alt)
        val words = cleanEnglishQuery(query)?.split(' ')?.filter { it !in listOf("with", "and", "of", "a", "the", "plate", "bowl", "food", "delicious", "healthy", "homemade") } ?: return false
        if (forbidden.any { it in text }) return false
        val preparedDish = setOf("waffles", "waffle", "pancakes", "pancake", "soup", "stew", "salad", "omelette", "omelet",
            "sandwich", "toast", "pizza", "cake", "pie", "casserole", "cutlet", "smoothie", "bread", "fries", "risotto", "gnocchi",
            "dumplings", "meatballs", "scrambled", "oatmeal", "porridge", "cereal")
        val hasDish = words.any { it in preparedDish }
        val plated = listOf("cooked", "grilled", "baked", "roasted", "fried", "dish", "meal", "plate", "bowl")
            .any { containsFoodWord(text, it) }
        return words.isNotEmpty() && (hasDish || plated) && words.all { containsFoodWord(text, it) }
    }
    private val forbidden = listOf("sign", "street", "menu", "restaurant exterior", "packaging", "advertisement", "logo", "uncooked", "raw", "illustration")
    private fun containsFoodWord(text: String, word: String): Boolean {
        val variants = when (word) {
            "omelette", "omelet" -> listOf("omelette", "omelet")
            "oatmeal", "porridge" -> listOf("oatmeal", "porridge")
            "pancakes", "pancake" -> listOf("pancake", "pancakes")
            "waffles", "waffle" -> listOf("waffle", "waffles")
            "fries" -> listOf("fries", "fry")
            "dumplings" -> listOf("dumpling", "dumplings", "pierogi")
            "berries" -> listOf("berries", "berry")
            else -> listOf(word, word.removeSuffix("s"), word + "s", word.removeSuffix("y") + "ies")
        }
        return variants.any { Regex("(?<![a-z])" + Regex.escape(it) + "(?![a-z])").containsMatchIn(text) }
    }
    fun score(query: String, alt: String, recipeIngredients: String = ""): Int {
        val text = normalize(alt)
        val requested = query.split(' ').count { containsFoodWord(text, it) }
        val ingredientsText = normalize(recipeIngredients)
        val extra = ingredients.count { (names, term) -> names.any { it in ingredientsText } && containsFoodWord(text, term) }
        return requested * 10 + extra * 2 + if (listOf("plate", "bowl", "meal", "cooked").any { containsFoodWord(text, it) }) 1 else 0
    }
    fun matches(title: String, alt: String): Boolean {
        val text = normalize(alt)
        val terms = terms(title)
        return terms.isNotEmpty() && matchesEnglish(terms.joinToString(" "), alt)
    }
}
