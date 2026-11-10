package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.viewmodel.RecipePhotoSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

internal data class ExercisePhotoCandidate(val preview: String, val page: String, val author: String, val id: Long)

internal object ExercisePhotoSearch {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    fun query(name: String): String = when {
        name.contains("hip thrust", true) -> "barbell hip thrust gym"
        name.contains("przysiad", true) -> "squat exercise gym"
        name.contains("suwnica", true) -> "leg press gym"
        name.contains("martwy ciąg", true) -> "deadlift gym"
        name.contains("wiosłowanie", true) -> "dumbbell row exercise"
        name.contains("wyciskanie", true) -> "bench press gym"
        name.contains("pompki", true) -> "push up exercise"
        name.contains("plank", true) -> "plank exercise"
        name.contains("wykroki", true) -> "lunges gym"
        name.contains("podciąganie", true) -> "pull up gym"
        else -> "gym exercise " + name.take(35)
    }

    suspend fun search(context: Context, name: String, key: String): List<ExercisePhotoCandidate> = withContext(Dispatchers.IO) {
        require(key.isNotBlank()) { "Dodaj klucz Pixabay w ustawieniach zdjęć." }
        val url = "https://pixabay.com/api/".toHttpUrl().newBuilder()
            .addQueryParameter("key", key)
            .addQueryParameter("q", query(name))
            .addQueryParameter("image_type", "photo")
            .addQueryParameter("safesearch", "true")
            .addQueryParameter("per_page", "12")
            .build()
        val cacheKey = java.security.MessageDigest.getInstance("SHA-256")
            .digest((key + "|" + query(name)).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cacheFile = java.io.File(context.cacheDir, "exercise_pixabay_$cacheKey.json")
        val validCache = cacheFile.takeIf {
            it.isFile && System.currentTimeMillis() - it.lastModified() in 0L..86_400_000L
        }?.let { runCatching { it.readText() }.getOrNull() }
        val jsonText = validCache ?: client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Nie udało się wyszukać fotografii." }
            (response.body?.string() ?: error("Brak odpowiedzi")).also { body ->
                runCatching { cacheFile.writeText(body) }
            }
        }
        run {
            val json = JSONObject(jsonText)
            val hits = json.optJSONArray("hits") ?: return@withContext emptyList()
            (0 until hits.length()).mapNotNull { i ->
                val hit = hits.optJSONObject(i) ?: return@mapNotNull null
                val preview = hit.optString("webformatURL")
                val page = hit.optString("pageURL")
                if (!preview.startsWith("https://") || !page.startsWith("https://pixabay.com/")) null
                else ExercisePhotoCandidate(preview, page, hit.optString("user"), hit.optLong("id"))
            }
        }
    }

    suspend fun save(context: Context, exercise: String, candidate: ExercisePhotoCandidate, stage: Int = 0): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = candidate.preview.toHttpUrl()
                require(url.isHttps && (url.host.endsWith(".pixabay.com") || url.host == "pixabay.com"))
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful)
                    val bytes = response.body?.bytes() ?: error("Puste zdjęcie")
                    require(bytes.size <= 8_000_000)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: error("Niepoprawne zdjęcie")
                    val file = java.io.File.createTempFile("exercise", ".jpg", context.cacheDir)
                    try {
                        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                        ExercisePhotoStore.save(context, exercise, android.net.Uri.fromFile(file), stage)
                    } finally { file.delete(); bitmap.recycle() }
                }
            }.getOrDefault(false)
        }
}

@Composable
internal fun ExercisePhotoFinder(exercise: String, stage: Int = 0, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var results by remember(exercise) { mutableStateOf(emptyList<ExercisePhotoCandidate>()) }
    var searching by remember(exercise) { mutableStateOf(false) }
    var message by remember(exercise) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = {
            searching = true
            scope.launch {
                try {
                    val key = RecipePhotoSettings(context).token("pixabay")
                    results = ExercisePhotoSearch.search(context, exercise, key)
                    message = if (results.isEmpty()) "Nie znaleziono zdjęć. Możesz dodać własne." else
                        "Wybierz fotografię pasującą do ćwiczenia i sprawdź jej technikę."
                } catch (e: Exception) { message = e.message ?: "Błąd wyszukiwania." }
                searching = false
            }
        }, enabled = !searching) { Text(if (searching) "Szukam…" else "🔎 Znajdź zdjęcie ćwiczenia") }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { candidate ->
                Column(modifier = Modifier.width(135.dp)) {
                    var saving by remember { mutableStateOf(false) }
                    val image by produceState<android.graphics.Bitmap?>(null, candidate.preview) {
                        value = withContext(Dispatchers.IO) {
                            runCatching {
                                val response = OkHttpClient().newCall(Request.Builder().url(candidate.preview).build()).execute()
                                response.use { BitmapFactory.decodeStream(it.body?.byteStream()) }
                            }.getOrNull()
                        }
                    }
                    if (image != null) Image(image!!.asImageBitmap(), contentDescription = "Fotografia ćwiczenia",
                        modifier = Modifier.fillMaxWidth().height(95.dp), contentScale = ContentScale.Crop)
                    else Surface(modifier = Modifier.fillMaxWidth().height(95.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant) {}
                    Text("Fot. " + candidate.author, style = MaterialTheme.typography.labelSmall)
                    Button(onClick = {
                        saving = true
                        scope.launch {
                            val ok = ExercisePhotoSearch.save(context, exercise, candidate, stage)
                            message = if (ok) "Zdjęcie zapisane. Źródło: " + candidate.page else "Nie udało się zapisać zdjęcia."
                            if (ok) {
                                if (candidate.id > 0) ExercisePhotoLibrary.approve(context, exercise, stage, candidate.id, candidate.page, candidate.author)
                                onSaved()
                            }
                            saving = false
                        }
                    }, enabled = !saving) { Text(if (saving) "Zapisuję…" else "Wybierz") }
                }
            }
        }
    }
}
