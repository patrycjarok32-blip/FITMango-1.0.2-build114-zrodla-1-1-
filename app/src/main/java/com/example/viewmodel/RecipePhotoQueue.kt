package com.example.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import androidx.work.*
import com.example.data.AppDatabase
import com.example.data.RecipeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object RecipePhotoQueue {
    const val TAG = "automatic_recipe_photos"
    internal val mutex = Mutex()
    fun eligible(recipe: RecipeEntity): Boolean =
        !recipe.description.contains("Zdjęcie: dodane z telefonu.") &&
        (recipe.imageUrl.isBlank() || recipe.imageUrl.startsWith("file:///android_asset/recipe_ai/") ||
            recipe.description.contains("Zdjęcie: poglądowe;"))
    fun repairable(context: Context, recipe: RecipeEntity): Boolean =
        !recipe.description.contains("Zdjęcie: dodane z telefonu.") &&
        (eligible(recipe) || RecipePhotoHealth.needsRepair(context, recipe.imageUrl) ||
            recipe.description.contains("Zdjęcie: poglądowe Pixabay;") || recipe.description.contains("Zdjęcie: poglądowe Wikimedia;") || recipe.description.contains("Zdjęcie: ilustracja AI;"))
    fun enqueue(context: Context, recipes: List<RecipeEntity>, retry: Boolean = false, improveExisting: Boolean = false) {
        val settings = RecipePhotoSettings(context)
        if (!settings.enabled) return
        val manager = WorkManager.getInstance(context)
        for (recipe in recipes.filter { eligible(it) || (improveExisting && repairable(context, it)) }) {
            val work = OneTimeWorkRequestBuilder<RecipePhotoWorker>()
                .setInputData(workDataOf("recipe_id" to recipe.id, "repair" to improveExisting))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).addTag(TAG).build()
            // KEEP also applies to explicit retries: never replace an active GPU request.
            manager.enqueueUniqueWork("$TAG-${recipe.id}", ExistingWorkPolicy.KEEP, work)
        }
        if (retry) settings.status("Brakujące zdjęcia dodane do kolejki. Darmowe limity nadal obowiązują.")
    }
}

class RecipePhotoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        RecipePhotoQueue.mutex.withLock {
            val settings = RecipePhotoSettings(applicationContext)
            if (!settings.enabled) return@withLock Result.success()
            val dao = AppDatabase.getDatabase(applicationContext).dietaDao()
            val id = inputData.getLong("recipe_id", -1)
            val recipe = dao.getAllRecipes().first().find { it.id == id } ?: return@withLock Result.success()
            if (!RecipePhotoQueue.eligible(recipe) && !(inputData.getBoolean("repair", false) &&
                    RecipePhotoQueue.repairable(applicationContext, recipe))) return@withLock Result.success()
            val fingerprint = MessageDigest.getInstance("SHA-256").digest((recipe.title + "\n" + recipe.ingredients).toByteArray())
                .joinToString("") { "%02x".format(it) }
            val eventKey = "event_$id"
            val eventFingerprint = "fingerprint_$id"
            val eventTime = "event_time_$id"
            val prefs = settings.prefs
            val service = RecipePhotoService(cacheDirectory = File(applicationContext.cacheDir, "pixabay_search_cache"))
            var photo: AutomaticPhoto? = null
            var downloadedBytes: ByteArray? = null
            try {
                settings.status("Dobieranie zdjęcia: ${recipe.title}")
                val token = settings.token("pixabay")
                var event = prefs.getString(eventKey, null).takeIf {
                    prefs.getString(eventFingerprint, "") == fingerprint &&
                        System.currentTimeMillis() - prefs.getLong(eventTime, 0) < 24 * 60 * 60 * 1000L
                }
                if (event == null && token.isNotBlank() && !settings.blocked("pixabay")) {
                    try {
                        val englishQuery = RecipePhotoMatching.query(recipe.title) ?: run {
                            val queryKey = "english_query_$fingerprint"
                            prefs.getString(queryKey, null) ?: kotlinx.coroutines.withTimeoutOrNull(12_000) {
                                com.example.ai.callGemini(
                                    prompt = "Translate this recipe title into a short English food-photo search query (maximum 8 words). Title: ${recipe.title.take(180)}",
                                    systemPrompt = "Return only English words describing the dish and main ingredient. No explanations, quotes or instructions.")
                                    .getOrNull()?.let(RecipePhotoMatching::cleanEnglishQuery)
                            }?.also { prefs.edit().putString(queryKey, it).apply() }
                        }
                        photo = service.pixabay(recipe, token, englishQuery)
                        photo?.let { selected ->
                            val bytes = service.download(selected)
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096 &&
                                bounds.outMimeType in listOf("image/jpeg", "image/png", "image/webp"))
                            downloadedBytes = bytes
                        }
                    }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: PhotoServiceException) { photo = null; settings.block(e.provider, e.retryAfterMillis); settings.status(e.notice) }
                    catch (_: Exception) { photo = null; settings.status("Zdjęcie z Pixabay jest niedostępne. Szukamy innego źródła.") }
                }
                if (photo == null && event == null) {
                    val query = RecipePhotoMatching.query(recipe.title)
                    val fallback = if (query == null) null else runCatching {
                        RecipePhotoSearch.search(query).filter { RecipePhotoMatching.matchesEnglish(query, it.title) }
                            .maxByOrNull { RecipePhotoMatching.score(query, it.title, recipe.ingredients) }
                    }.getOrElse { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        null
                    }
                    if (fallback != null) {
                        val path = RecipePhotoSearch.savePhoto(applicationContext, fallback)
                        val description = recipe.description.substringBefore("\n\nZdjęcie: ") +
                            "\n\nZdjęcie: poglądowe Wikimedia; ${fallback.author}; ${fallback.pageUrl}; ${fallback.license}; ${fallback.licenseUrl}"
                        val saved = dao.saveAutomaticPhoto(recipe.id, recipe.imageUrl, recipe.title, recipe.ingredients,
                            recipe.description, path, description)
                        // Search saves content-addressed files: another recipe may already use this photograph.
                        settings.status(if (saved == 1) "Zapisano zdjęcie: ${recipe.title}" else "Przepis zmienił się. Zachowano aktualne zdjęcie.")
                        return@withLock Result.success()
                    }
                }
                if (photo == null) {
                    if (settings.blocked("hf")) {
                        settings.status("AI czeka na odnowienie darmowego limitu albo poprawienie klucza w Profilu.")
                        return@withLock Result.retry()
                    }
                    if (event == null) {
                        event = service.startAi(recipe, settings.token("hf"))
                        // Save before polling, so process restart resumes the same request.
                        prefs.edit().putString(eventKey, event).putString(eventFingerprint, fingerprint)
                            .putLong(eventTime, System.currentTimeMillis()).commit()
                    }
                    settings.status("Generowanie ilustracji AI: ${recipe.title}")
                    photo = service.awaitAi(event, settings.token("hf"))
                }
                val result = requireNotNull(photo)
                val bytes = downloadedBytes ?: service.download(result)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096 && bounds.outMimeType in listOf("image/jpeg", "image/png", "image/webp"))
                val directory = File(applicationContext.filesDir, if (result.ai) "recipe_ai_generated" else "recipe_photos_pixabay").apply { mkdirs() }
                val file = File(directory, "$id-${java.util.UUID.randomUUID()}.img")
                val temp = File(directory, "${file.name}.tmp")
                try {
                    temp.writeBytes(bytes)
                    check(temp.renameTo(file))
                    val description = recipe.description.substringBefore("\n\nZdjęcie: ") + "\n\n" + result.credit
                    val saved = dao.saveAutomaticPhoto(recipe.id, recipe.imageUrl, recipe.title, recipe.ingredients,
                        recipe.description, file.absolutePath, description)
                    if (saved == 0) file.delete()
                    settings.status(if (saved == 1) "Zapisano zdjęcie: ${recipe.title}" else "Przepis zmienił się. Zachowano aktualne zdjęcie.")
                } finally { temp.delete() }
                prefs.edit().remove(eventKey).remove(eventFingerprint).remove(eventTime).commit()
                Result.success()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: PhotoServiceException) {
                settings.block(e.provider, e.retryAfterMillis)
                settings.status(e.notice)
                // An error is terminal for this Gradio event; a future retry needs a new event.
                prefs.edit().remove(eventKey).remove(eventFingerprint).remove(eventTime).commit()
                Result.retry()
            } catch (_: Exception) {
                settings.status("Nie udało się pobrać zdjęcia. Kolejka spróbuje ponownie po odzyskaniu połączenia.")
                Result.retry()
            }
        }
    }
}
