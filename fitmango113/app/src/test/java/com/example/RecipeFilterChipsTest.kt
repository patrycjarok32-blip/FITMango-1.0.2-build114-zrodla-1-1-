package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.components.RecipeFilterChips
import com.example.ui.theme.MojaDietaAiTheme
import com.example.data.RecipeEntity
import com.example.viewmodel.RecipeFilterSupport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp")
class RecipeFilterChipsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun categoryAndTimeChipsChangeTheVisibleCandidatesAndCanBeCleared() {
        val recipes = listOf(
            RecipeEntity(1, "Owsianka", "Czas: 10 min", "Płatki", "", 200, 10, 20, 5, "Breakfast"),
            RecipeEntity(2, "Jajecznica", "Czas: 25 min", "Jajka", "", 250, 15, 10, 12, "Śniadania"),
            RecipeEntity(3, "Zupa", "Czas: 10 min", "Marchew", "", 100, 3, 15, 3, "Lunch"))
        compose.setContent { MojaDietaAiTheme {
            var category by remember { mutableStateOf("Wszystkie") }
            var preparation by remember { mutableStateOf("Wszystkie czasy") }
            Column {
                RecipeFilterChips(category, { category = it }, preparation, { preparation = it })
                recipes.filter { RecipeFilterSupport.matches(it, "", category, preparation,
                    RecipeFilterSupport.providedMinutes(it), false, false) }.forEach { Text(it.title) }
            }
        } }
        compose.onNodeWithTag("picker_category_Śniadanie").performClick()
        compose.onNodeWithText("Zupa").assertDoesNotExist()
        compose.onNodeWithTag("picker_time_Do 15 minut").performClick()
        compose.onNodeWithText("Owsianka").assertIsDisplayed()
        compose.onNodeWithText("Jajecznica").assertDoesNotExist()
        compose.onNodeWithTag("picker_time_Wszystkie czasy").performClick()
        compose.onNodeWithText("Jajecznica").assertIsDisplayed()
        compose.onNodeWithTag("picker_category_Wszystkie").performClick()
        compose.onNodeWithText("Zupa").assertIsDisplayed()
    }
}
