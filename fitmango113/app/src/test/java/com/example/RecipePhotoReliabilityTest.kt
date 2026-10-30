package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.RecipeEntity
import com.example.viewmodel.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecipePhotoReliabilityTest {
    private fun recipe() = RecipeEntity(title = "Gofry orzechowe", description = "", ingredients = "orzechy, banan",
        instructions = "Piecz", calories = 300, protein = 20, carbs = 40, fat = 10, category = "Śniadanie")

    @Test fun searchPreservesEnglishDetailsAndTranslatesPolishWithoutChangingTitle() {
        assertEquals("waffles peanut", RecipePhotoMatching.query(recipe().title))
        assertEquals("soup chicken", RecipePhotoMatching.query("Zupa z kurczakiem i ryżem"))
        assertEquals("soup sorrel", RecipePhotoMatching.englishSearchQuery("zupa szczawiowa"))
        assertEquals("blueberry pancakes bowl", RecipePhotoMatching.englishSearchQuery("blueberry pancakes bowl"))
        assertEquals("waffles peanut", RecipePhotoSearch.suggestedQuery(recipe().title))
        assertFalse(RecipePhotoMatching.matchesEnglish("apple pie", "pineapple pie"))
        assertFalse(RecipePhotoMatching.matchesEnglish("chicken", "raw chicken packaging"))
        assertFalse(RecipePhotoMatching.matchesEnglish("salmon", "salmon fillet fish seafood"))
        assertTrue(RecipePhotoMatching.matchesEnglish("salmon", "grilled salmon on plate"))
        assertTrue(RecipePhotoMatching.matchesEnglish("pancakes", "pancake on plate"))
        assertEquals("Gofry orzechowe", recipe().title)
    }

    @Test fun automaticSelectionRanksIngredientsInsteadOfTakingFirstMatchingResult() {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"hits":[
                {"tags":"peanut waffles", "webformatURL":"https://cdn.pixabay.com/first.jpg", "pageURL":"https://pixabay.com/photos/one-1/", "user":"cook"},
                {"tags":"peanut waffles banana on plate", "webformatURL":"https://cdn.pixabay.com/better.jpg", "pageURL":"https://pixabay.com/photos/two-2/", "user":"cook"}]}"""))
            val selected = RecipePhotoService(pixabayBase = server.url("/").toString()).pixabay(recipe(), "test")
            assertEquals("https://cdn.pixabay.com/better.jpg", selected!!.url)
        } finally { server.shutdown() }
    }

    @Test fun failedImagesAndOldAutomaticPhotosCanBeRepairedButPhonePhotosStayProtected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val remote = recipe().copy(imageUrl = "https://example.org/meal.png")
        assertFalse(RecipePhotoQueue.repairable(context, remote))
        RecipePhotoHealth.failed(context, remote.imageUrl, true)
        assertTrue(RecipePhotoQueue.repairable(context, remote))
        RecipePhotoHealth.failed(context, remote.imageUrl, false)
        assertFalse(RecipePhotoQueue.repairable(context, remote))
        assertTrue(RecipePhotoQueue.repairable(context, recipe().copy(imageUrl = "/missing/meal.img")))
        assertTrue(RecipePhotoQueue.repairable(context, remote.copy(description = "Zdjęcie: poglądowe Pixabay; cook")))
        assertFalse(RecipePhotoQueue.repairable(context, remote.copy(description = "Zdjęcie: dodane z telefonu.")))
    }

    @Test fun downloadedBytesMustBeAnImageAndSavedPhotoCanBeReadOffline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(runCatching { RecipePhotoSearch.saveValidatedBytes(context, "<html>no picture</html>".toByteArray()) }.isFailure)
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
        val path = RecipePhotoSearch.saveValidatedBytes(context, output.toByteArray())
        try {
            assertTrue(File(path).isFile)
            assertArrayEquals(output.toByteArray(), File(path).readBytes())
            assertFalse(RecipePhotoHealth.needsRepair(context, path))
        } finally { File(path).delete() }
    }
}
