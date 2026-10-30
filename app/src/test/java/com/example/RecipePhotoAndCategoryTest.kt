package com.example

import com.example.viewmodel.RecipeCategories
import com.example.viewmodel.RecipePhotoSearch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipePhotoAndCategoryTest {
    @Test fun existingPolishAndEnglishCategoriesRemainAccessible() {
        assertEquals("Śniadanie", RecipeCategories.canonical("Breakfast"))
        assertEquals("Obiad", RecipeCategories.canonical("Lunch"))
        assertEquals("Kolacja", RecipeCategories.canonical("Dinner"))
        assertEquals("Śniadanie", RecipeCategories.canonical("śniadanie"))
        assertEquals("Inne", RecipeCategories.canonical("unknown"))
    }

    private fun response(title: String = "File:Hummus.jpg", author: String = "Photographer", mime: String = "image/jpeg") = """
        {"query":{"pages":{"1":{"title":"$title","imageinfo":[{
        "mime":"$mime","thumburl":"https://upload.wikimedia.org/photo.jpg",
        "descriptionurl":"https://commons.wikimedia.org/wiki/File:Hummus.jpg",
        "extmetadata":{"Artist":{"value":"<b>$author</b>"},"LicenseShortName":{"value":"CC BY-SA 4.0"},
        "LicenseUrl":{"value":"https://creativecommons.org/licenses/by-sa/4.0/"}}}]}}}}
    """.trimIndent()

    @Test fun photoMetadataKeepsAttributionWithoutHtml() {
        val photo = RecipePhotoSearch.parseCandidates(response()).single()
        assertEquals("Photographer", photo.author)
        assertEquals("CC BY-SA 4.0", photo.license)
        assertTrue(photo.pageUrl.contains("Hummus"))
    }

    @Test fun generatedIllustrationsAndMissingCreditsAreExcluded() {
        assertTrue(RecipePhotoSearch.parseCandidates(response(title = "File:AI-generated hummus.jpg")).isEmpty())
        assertTrue(RecipePhotoSearch.parseCandidates(response(author = "")).isEmpty())
        assertTrue(RecipePhotoSearch.parseCandidates(response(mime = "image/svg+xml")).isEmpty())
    }

    @Test fun automaticPhotosRequireANameMatchRatherThanReusingAnUnrelatedPicture() {
        val hummus = RecipePhotoSearch.parseCandidates(response()).single()
        assertNull(RecipePhotoSearch.matchingPhoto(listOf(hummus), "chicken salad"))
        assertEquals(hummus, RecipePhotoSearch.matchingPhoto(listOf(hummus), "hummus"))
        assertEquals("oatmeal berries bowl", RecipePhotoSearch.suggestedQuery("Złota owsianka z kurkumą i owocami jagodowymi"))
        assertEquals("omelette plate", RecipePhotoSearch.suggestedQuery("Omleto-kanapka"))
        assertNotNull(RecipePhotoSearch.matchingPhoto(RecipePhotoSearch.parseCandidates(response(title = "File:Oatmeal bowl.jpg")), "porridge"))
    }

    @Test fun photoDownloadRejectsUnexpectedHosts() {
        assertFalse(RecipePhotoSearch.isWikimediaUrl("https://upload.wikimedia.org.attacker.example/photo.jpg"))
        assertFalse(RecipePhotoSearch.isWikimediaUrl("http://upload.wikimedia.org/photo.jpg"))
        assertTrue(RecipePhotoSearch.isWikimediaUrl("https://upload.wikimedia.org/photo.jpg"))
    }

    @Test fun dishQueriesKeepChickenAndSpinachAndExcludeStreetSignPhotos() {
        assertEquals("chicken fries vegetables", RecipePhotoSearch.suggestedQuery("FRYTKI Z KURCZAKIEM I WARZYWAMI"))
        assertEquals("spinach omelette toast", RecipePhotoSearch.suggestedQuery("Omlet ze szpinakiem i tostem"))
        assertTrue(RecipePhotoSearch.parseCandidates(response(title = "File:Sweet potato fries shop sign.jpg")).isEmpty())
    }
    @Test fun servingWordsCannotMakeAnUnrelatedDishMatch() {
        val omelette = RecipePhotoSearch.parseCandidates(response(title = "File:Omelette plate.jpg")).single()
        assertEquals(omelette, RecipePhotoSearch.matchingPhoto(listOf(omelette), "omelette plate"))
        assertNull(RecipePhotoSearch.matchingPhoto(listOf(omelette), "hummus plate"))
        val fries = RecipePhotoSearch.parseCandidates(response(title = "File:French fries bowl.jpg")).single()
        assertNull(RecipePhotoSearch.matchingPhoto(listOf(fries), "chicken fries vegetables"))
    }
}
