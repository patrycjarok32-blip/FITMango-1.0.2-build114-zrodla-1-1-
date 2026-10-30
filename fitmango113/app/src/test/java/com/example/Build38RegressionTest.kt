package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build38RegressionTest {
    @Test fun polishRecipeUsesEnglishSearchAndKeepsItsTitle() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"hits":[{"tags":"sorrel soup, food", "webformatURL":"https://cdn.pixabay.com/photo/soup.jpg", "pageURL":"https://pixabay.com/photos/soup-123/", "user":"cook"}]}"""))
            val recipe = RecipeEntity(title = "Zupa szczawiowa", description = "", ingredients = "szczaw",
                instructions = "Gotuj", calories = 100, protein = 3, carbs = 10, fat = 5, category = "Obiad")
            val result = RecipePhotoService(pixabayBase = server.url("/").toString()).pixabay(recipe, "test")
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("soup sorrel", request.requestUrl!!.queryParameter("q"))
            assertEquals("en", request.requestUrl!!.queryParameter("lang"))
            assertEquals("Zupa szczawiowa", recipe.title)
            assertNull(RecipePhotoMatching.cleanEnglishQuery("Zupa szczawiowa!"))
            assertTrue(RecipePhotoMatching.matchesEnglish("sorrel soup", "sorrel soup on plate"))
        } finally { server.shutdown() }
    }

    @Test fun ingredientEnergyScalesQuantityButDoesNotInventMissingData() {
        assertEquals(62, IngredientSubstitutionSupport.ingredientCalories("Papryka", 200f, "g", emptyList()))
        assertEquals(62, IngredientSubstitutionSupport.ingredientCalories("Papryka", .2f, "kg", emptyList()))
        assertNull(IngredientSubstitutionSupport.ingredientCalories("Nieznany produkt", 100f, "g", emptyList()))
        assertNull(IngredientSubstitutionSupport.ingredientCalories("Papryka", 1f, "szt.", emptyList()))
        val zero = MealIngredientSubstitution(mealPlanId = 1, originalIngredientName = "Papryka", originalBaseQuantity = 100f,
            originalUnit = "g", replacementName = "Produkt zero", baseQuantityPerServing = 100f, caloriesPer100g = 0f)
        assertEquals(0, IngredientSubstitutionSupport.ingredientCalories("Produkt zero", 100f, "g", emptyList(), zero))
        assertNull(IngredientSubstitutionSupport.ingredientCalories("Papryka", 100f, "g", emptyList(), zero.copy(caloriesPer100g = null)))
    }

    @Test fun largeCameraPhotoIsDecodedAtBoundedResolutionForOcr() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("label", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(4000, 2000, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val image = RecipeImageExtractor.boundedBitmap(context, Uri.fromFile(file))
            try {
                assertTrue(image.width <= 2048)
                assertTrue(image.height <= 2048)
                assertEquals(image.width / 2, image.height)
            } finally { image.recycle() }
        } finally { file.delete(); bitmap.recycle() }
    }
}
