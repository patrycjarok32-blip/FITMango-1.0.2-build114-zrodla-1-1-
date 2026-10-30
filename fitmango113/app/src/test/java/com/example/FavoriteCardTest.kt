package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.components.MangoRecipeCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FavoriteCardTest {
    @get:Rule val compose = createComposeRule()
    @Test fun favoriteButtonIsIndependentFromOpeningRecipe() {
        var opened = 0; var favorites = 0
        compose.setContent { MaterialTheme {
            MangoRecipeCard("Owsianka", "Śniadania", 200, "", onClick = { opened++ },
                isFavorite = false, onFavoriteClick = { favorites++ })
        } }
        compose.onNodeWithContentDescription("Dodaj do ulubionych: Owsianka").performClick()
        assertEquals(1, favorites); assertEquals(0, opened)
    }
}
