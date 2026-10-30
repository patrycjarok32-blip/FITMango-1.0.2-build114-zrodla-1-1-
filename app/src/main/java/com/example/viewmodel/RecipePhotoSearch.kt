package com.example.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class RecipePhotoCandidate(val title: String, val imageUrl: String, val pageUrl: String,
    val author: String, val license: String, val licenseUrl: String)

object RecipePhotoSearch {
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()

    /** Validate and keep source-site photographs locally instead of storing an expiring link. */
    suspend fun saveSourcePhoto(context: Context, imageUrl: String): String = withContext(Dispatchers.IO) {
        var url = imageUrl.toHttpUrl()
        val downloader = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        repeat(5) {
            require(url.isHttps && url.username.isEmpty() && url.password.isEmpty()) { "Nieprawidłowy adres zdjęcia." }
            val addresses = java.net.InetAddress.getAllByName(url.host)
            require(addresses.isNotEmpty() && addresses.none { address -> address.isAnyLocalAddress ||
                address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress })
            downloader.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    url = url.resolve(response.header("Location").orEmpty()) ?: error("Nieprawidłowe przekierowanie zdjęcia.")
                } else {
                    check(response.isSuccessful) { "Zdjęcie nie jest dostępne." }
                    val body = checkNotNull(response.body)
                    check(body.contentLength() <= 8_000_000)
                    val output = java.io.ByteArrayOutputStream()
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= 8_000_000)
                            output.write(buffer, 0, count)
                        }
                    }
                    return@withContext saveValidatedBytes(context, output.toByteArray())
                }
            }
        }
        error("Zbyt wiele przekierowań zdjęcia.")
    }

    internal fun saveValidatedBytes(context: Context, bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= 8_000_000)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096 &&
            bounds.outMimeType in listOf("image/jpeg", "image/png", "image/webp")) { "Pobrany plik nie jest obsługiwanym zdjęciem." }
        val folder = File(context.filesDir, "recipe_photos").apply { mkdirs() }
        val file = File(folder, java.util.UUID.randomUUID().toString() + ".img")
        val temp = File.createTempFile("photo", ".tmp", folder)
        try { temp.writeBytes(bytes); check(temp.renameTo(file)) } finally { temp.delete() }
        return file.absolutePath
    }

    fun suggestedQuery(title: String): String {
        val name = title.lowercase()
        return when {
            ("avocado" in name || "awokado" in name) && ("egg" in name || "jaj" in name) && ("toast" in name || "tost" in name) -> "avocado egg toast"
            ("salad" in name || "sałat" in name || "salat" in name) && ("chicken" in name || "kurcz" in name) -> "grilled chicken salad"
            ("salmon" in name || "łoso" in name || "loso" in name) && "quinoa" in name -> "salmon quinoa bowl"
            "owsiank" in name -> if ("owoc" in name || "berry" in name || "berries" in name) "oatmeal berries bowl" else "oatmeal porridge bowl"
            "dutch baby" in name -> "Dutch baby pancake"
            "sałat" in name || "salat" in name || "salad" in name -> RecipePhotoMatching.query(title) ?: "salad"
            "łoso" in name || "loso" in name || "salmon" in name -> RecipePhotoMatching.query(title) ?: "salmon"
            "zupa pomidor" in name -> "tomato soup bowl"
            "zupa" in name -> RecipePhotoMatching.query(title) ?: "soup"
            "omlet" in name || "omelet" in name -> if ("szpin" in name) "spinach omelette toast" else "omelette plate"
            "naleśnik" in name || "nalesnik" in name -> "pancakes plate"
            "pierog" in name -> "pierogi plate"
            "szakszuk" in name -> "shakshuka skillet"
            "hummus" in name -> "hummus toast plate"
            "risotto" in name -> "risotto plate"
            "frytki" in name -> if ("kurcz" in name) "chicken fries vegetables" else if ("batat" in name) "sweet potato fries" else "french fries"
            "kurcz" in name || "chicken" in name -> RecipePhotoMatching.query(title) ?: "chicken"
            "tost" in name || "toast" in name -> RecipePhotoMatching.query(title) ?: "toast"
            else -> RecipePhotoMatching.query(title) ?: RecipePhotoMatching.cleanEnglishQuery(title) ?: ""
        }
    }

    fun matchingPhoto(candidates: List<RecipePhotoCandidate>, query: String): RecipePhotoCandidate? {
        fun words(text: String) = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9 ]"), " ").split(Regex("\\s+"))
            .filter { it.length > 2 && it !in listOf("with", "and", "dish", "porcja", "porcje", "food", "plate", "bowl", "skillet", "grilled", "baked", "file", "jpg", "png") }.distinct()
        val wanted = words(query)
        if (wanted.isEmpty()) return null
        fun variants(word: String) = when (word) { "porridge" -> setOf(word, "oatmeal"); "omelette" -> setOf(word, "omelet"); else -> setOf(word) }
        return candidates.map { photo ->
            val photoWords = words(photo.title)
            val score = wanted.count { word -> photoWords.any { it in variants(word) } }
            photo to score
        }
            // For multi-word dishes demand at least two matching food terms. This avoids assigning
            // a generic sandwich or raw ingredient photo to a specific recipe.
            .filter { (_, score) -> score >= if (wanted.size >= 2) 2 else 1 }
            .maxByOrNull { it.second }
            ?.first
    }

    suspend fun search(query: String): List<RecipePhotoCandidate> = withContext(Dispatchers.IO) {
        require(query.trim().length in 2..120) { "Wpisz nazwę potrawy (2–120 znaków)." }
        val url = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query").addQueryParameter("format", "json")
            .addQueryParameter("generator", "search").addQueryParameter("gsrnamespace", "6")
            .addQueryParameter("gsrsearch", query.trim() + " filetype:bitmap -artificial -generated -illustration")
            .addQueryParameter("gsrlimit", "16").addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|mime|extmetadata").addQueryParameter("iiurlwidth", "960")
            .addQueryParameter("iiextmetadatafilter", "Artist|LicenseShortName|LicenseUrl|ImageDescription|Categories")
            .build()
        client.newCall(Request.Builder().url(url).header("User-Agent", "MangoFit/1.0 (personal Android recipe photo search)").build())
            .execute().use { response ->
                check(response.isSuccessful) { "Wyszukiwanie zdjęć jest chwilowo niedostępne (HTTP ${response.code})." }
                parseCandidates(response.body?.string().orEmpty())
            }
    }

    internal fun parseCandidates(json: String): List<RecipePhotoCandidate> {
        val root = JSONObject(json)
        check(!root.has("error")) { "Wyszukiwanie zdjęć jest chwilowo niedostępne." }
        val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        return pages.keys().asSequence().mapNotNull { key ->
            val page = pages.optJSONObject(key) ?: return@mapNotNull null
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@mapNotNull null
            if (info.optString("mime") !in listOf("image/jpeg", "image/png", "image/webp")) return@mapNotNull null
            val meta = info.optJSONObject("extmetadata") ?: return@mapNotNull null
            fun field(name: String) = Jsoup.parse(meta.optJSONObject(name)?.optString("value").orEmpty()).text()
            val title = page.optString("title").removePrefix("File:")
            val content = "$title ${field("ImageDescription")} ${field("Categories")}".lowercase()
            if (listOf("ai-generated", "ai generated", "artificial intelligence", "illustration", "diagram", "logo",
                    "storefront", "shop sign", "signboard", "menu board", "street scene", "restaurant exterior", "food packaging", "advertisement")
                    .any { it in content }) return@mapNotNull null
            val url = info.optString("thumburl").ifBlank { info.optString("url") }
            val pageUrl = info.optString("descriptionurl")
            val license = field("LicenseShortName")
            val author = field("Artist")
            if (!isWikimediaUrl(url) || !isWikimediaUrl(pageUrl) || license.isBlank() || author.isBlank()) return@mapNotNull null
            RecipePhotoCandidate(title, url, pageUrl, author.take(500), license.take(100), field("LicenseUrl"))
        }.toList()
    }

    internal fun isWikimediaUrl(value: String): Boolean = runCatching {
        val url = value.toHttpUrl()
        url.scheme == "https" && url.host in listOf("upload.wikimedia.org", "commons.wikimedia.org", "thumb.wikimedia.org")
    }.getOrDefault(false)

    suspend fun savePhonePhoto(context: Context, uri: android.net.Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 8_000_000) { "Zdjęcie przekracza 8 MB." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Nie można odczytać zdjęcia.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Plik nie jest zdjęciem." }
        val dir = File(context.filesDir, "recipe_photos").apply { mkdirs() }
        val file = File.createTempFile("meal_", ".jpg", dir)
        try { file.writeBytes(bytes) } catch (e: Exception) { file.delete(); throw e }
        file.absolutePath
    }

    /** Save the selected photograph locally so a successful selection remains available offline. */
    suspend fun savePhoto(context: Context, photo: RecipePhotoCandidate): String = withContext(Dispatchers.IO) {
        val pixabay = photo.licenseUrl == "https://pixabay.com/service/license-summary/"
        require(pixabay || isWikimediaUrl(photo.imageUrl))
        val bytes = if (pixabay) RecipePhotoService().download(AutomaticPhoto(photo.imageUrl, "", false)) else client.newCall(Request.Builder().url(photo.imageUrl).header("User-Agent", "MangoFit/1.0 (personal Android recipe photo search)").build()).execute().use { response ->
            check(response.isSuccessful) { "Nie udało się pobrać zdjęcia (HTTP ${response.code})." }
            val body = checkNotNull(response.body)
            check(body.contentLength() <= 8_000_000) { "Zdjęcie jest zbyt duże." }
            body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var size = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    check(size <= 8_000_000) { "Zdjęcie jest zbyt duże." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        check(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096 && bounds.outMimeType in listOf("image/jpeg", "image/png", "image/webp")) { "Pobrany plik nie jest obsługiwanym zdjęciem." }
        val folder = File(context.filesDir, "recipe_photos").apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val file = File(folder, "$name.jpg")
        if (!file.exists()) {
            val temp = File.createTempFile("photo", ".tmp", folder)
            try {
                temp.writeBytes(bytes)
                check(temp.renameTo(file)) { "Nie udało się zapisać zdjęcia." }
            } finally { temp.delete() }
        }
        file.absolutePath
    }
}
