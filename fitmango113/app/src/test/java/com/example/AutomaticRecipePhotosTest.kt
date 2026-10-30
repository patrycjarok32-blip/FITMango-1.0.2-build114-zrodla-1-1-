package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.RecipeEntity
import com.example.viewmodel.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutomaticRecipePhotosTest {
    private fun recipe() = RecipeEntity(title = "Omlet ze szpinakiem", description = "", ingredients = "jajka, szpinak",
        instructions = "Usmaż", calories = 300, protein = 20, carbs = 10, fat = 20, category = "Breakfast")

    @Test fun applicationInitializesQueueAndDuplicateImportsKeepOnePendingTask() {
        val context = ApplicationProvider.getApplicationContext<FITMangoTestApplication>()
        assertTrue(context is androidx.work.Configuration.Provider)
        val manager = androidx.work.WorkManager.getInstance(context)
        com.example.viewmodel.RecipePhotoSettings(context).enabled = true
        val imported = recipe().copy(id = 123456)
        RecipePhotoQueue.enqueue(context, listOf(imported))
        RecipePhotoQueue.enqueue(context, listOf(imported))
        val pending = manager.getWorkInfosForUniqueWork("${RecipePhotoQueue.TAG}-${imported.id}").get()
            .filter { !it.state.isFinished }
        assertEquals(1, pending.size)
        assertEquals(androidx.work.WorkInfo.State.ENQUEUED, pending.single().state)
        manager.cancelUniqueWork("${RecipePhotoQueue.TAG}-${imported.id}").result.get()
    }

    @Test fun poorPhotoMatchFallsThroughInsteadOfSelectingStreetSigns() {
        assertEquals("omelette spinach", RecipePhotoMatching.query(recipe().title))
        assertTrue(RecipePhotoMatching.matches(recipe().title, "Spinach omelette on a plate"))
        assertFalse(RecipePhotoMatching.matches(recipe().title, "A plain omelette on a plate"))
        assertFalse(RecipePhotoMatching.matches("Frytki z kurczakiem", "Street restaurant menu sign with fries and chicken"))
        assertNull(RecipePhotoMatching.query("Niezwykła potrawa bez znanej nazwy"))
    }
    @Test fun explicitPhotosAreProtectedAndLegacyStockCanBeReplaced() {
        assertTrue(RecipePhotoQueue.eligible(recipe()))
        assertFalse(RecipePhotoQueue.eligible(recipe().copy(imageUrl = "/phone.jpg", description = "Zdjęcie: dodane z telefonu.")))
        assertFalse(RecipePhotoQueue.eligible(recipe().copy(imageUrl = "https://recipe.example/food.jpg")))
        assertFalse(RecipePhotoQueue.eligible(recipe().copy(imageUrl = "/files/recipe_ai_generated/1.png")))
        assertTrue(RecipePhotoQueue.eligible(recipe().copy(imageUrl = "file:///android_asset/recipe_ai/meal.png")))
    }
    @Test fun unsafeImageHostsAndHttpAreRejected() {
        val service = RecipePhotoService()
        assertTrue(service.trustedImage("https://cdn.pixabay.com/photos/1/a.jpg", false))
        assertFalse(service.trustedImage("https://cdn.pixabay.com.attacker.example/a.jpg", false))
        assertFalse(service.trustedImage("http://cdn.pixabay.com/a.jpg", false))
        assertFalse(service.trustedImage("https://user:password@cdn.pixabay.com/a.jpg", false))
        assertFalse(service.trustedImage("https://attacker.example/a.jpg", true))
    }
    @Test fun pixabaySearchCachesResultsAndDoesNotSendAuthorizationHeader() {
        val server = MockWebServer()
        server.start()
        val cache = java.nio.file.Files.createTempDirectory("pixabay-test").toFile()
        try {
            server.enqueue(MockResponse().setBody("""{"hits":[
                {"tags":"spinach omelette, food", "webformatURL":"https://cdn.pixabay.com/photo/meal.jpg",
                 "pageURL":"https://pixabay.com/photos/meal-123/", "user":"cook"},
                {"tags":"food", "webformatURL":"https://attacker.example/image.jpg",
                 "pageURL":"https://pixabay.com/photos/meal-456/", "user":"other"}]}"""))
            val service = RecipePhotoService(pixabayBase = server.url("/api/").toString(), cacheDirectory = cache)
            val photos = service.searchPixabay("spinach omelette", "test-key")
            assertEquals(1, photos.size)
            assertEquals("cook", photos.single().author)
            assertEquals(photos, service.searchPixabay("spinach omelette", "test-key"))
            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertNull(request.getHeader("Authorization"))
            assertEquals("test-key", request.requestUrl!!.queryParameter("key"))
            assertEquals("photo", request.requestUrl!!.queryParameter("image_type"))
            assertEquals("food", request.requestUrl!!.queryParameter("category"))
            assertEquals("true", request.requestUrl!!.queryParameter("safesearch"))
            assertTrue(cache.listFiles()!!.none { it.name.contains("test-key") || it.readText().contains("test-key") })
        } finally { cache.deleteRecursively(); server.shutdown() }
    }
    @Test fun pixabayEmptyResultsExpireAfter24HoursAndInvalidKeyHasUsefulMessage() {
        val server = MockWebServer()
        server.start()
        val cache = java.nio.file.Files.createTempDirectory("pixabay-test").toFile()
        var time = System.currentTimeMillis()
        try {
            server.enqueue(MockResponse().setBody("{\"hits\":[]}"))
            server.enqueue(MockResponse().setResponseCode(400).setBody("bad key"))
            val service = RecipePhotoService(pixabayBase = server.url("/api/").toString(), cacheDirectory = cache, now = { time })
            assertTrue(service.searchPixabay("salad", "test-key").isEmpty())
            assertTrue(service.searchPixabay("salad", "test-key").isEmpty())
            assertEquals(1, server.requestCount)
            time += 24 * 60 * 60 * 1000L + 1
            try { service.searchPixabay("salad", "test-key"); fail("Expected invalid-key error") }
            catch (error: PhotoServiceException) {
                assertEquals("pixabay", error.provider)
                assertTrue(error.notice.contains("Pixabay"))
                assertFalse(error.notice.contains("test-key"))
            }
            assertEquals(2, server.requestCount)
        } finally { cache.deleteRecursively(); server.shutdown() }
    }
    @Test fun gradioUsesSixInputsAndResumesAnEventThroughHeartbeats() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("{\"event_id\":\"event123\"}"))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "event: heartbeat\ndata: null\n\nevent: complete\ndata: [{\"url\":\"https://localhost/food.png\"},42]\n\n"))
            val service = RecipePhotoService(fluxBase = server.url("/").toString().trimEnd('/'))
            val id = service.startAi(recipe(), "")
            val request = server.takeRequest()
            assertEquals("/gradio_api/call/infer", request.path)
            assertNull(request.getHeader("Authorization"))
            val data = JSONObject(request.body.readUtf8()).getJSONArray("data")
            assertEquals(6, data.length())
            assertTrue(data.getString(0).contains("Omlet ze szpinakiem"))
            assertFalse(data.getBoolean(2))
            assertEquals(768, data.getInt(3)); assertEquals(768, data.getInt(4)); assertEquals(4, data.getInt(5))
            assertTrue(service.awaitAi(id, "").ai)
            assertEquals("/gradio_api/call/infer/event123", server.takeRequest().path)
        } finally { server.shutdown() }
    }
    @Test fun quotaErrorDoesNotTriggerAnotherImmediateGeneration() {
        val error = RecipePhotoService().parseAiError("GPU quota exceeded")
        assertEquals("hf", error.provider)
        assertEquals(24 * 60 * 60 * 1000L, error.retryAfterMillis)
    }
    @Test fun lateDownloadCannotOverwriteAManualPhoto(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val dao = db.dietaDao()
            val old = recipe()
            val id = dao.insertRecipe(old)
            dao.updateRecipePhoto(id, "/phone.jpg", "Zdjęcie: dodane z telefonu.")
            assertEquals(0, dao.saveAutomaticPhoto(id, "", old.title, old.ingredients, "", "/auto.jpg", "auto"))
            assertEquals("/phone.jpg", dao.getAllRecipes().first().single().imageUrl)
        } finally { db.close() }
    }
}
