package com.example

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.RecipeEntity
import com.example.ui.components.*
import com.example.ui.theme.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp")
class CookingAndAppearanceUiTest {
    @get:Rule val compose = createComposeRule()
    @Before fun reset() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(MangoAppearance.PREFS, 0).edit().clear().commit()
        MangoAppearance.light = false
    }
    @Test fun cookingShowsLargeStepsAndNextPreviousControls() {
        val recipe = RecipeEntity(id = 4, title = "Zupa", description = "", ingredients = "Marchew", instructions = "1. Pokrój marchew.\n2. Ugotuj zupę.", calories = 200, protein = 5, carbs = 30, fat = 4, category = "Obiad")
        compose.setContent { MojaDietaAiTheme { CookingDialog(recipe) {} } }
        compose.onNodeWithTag("cooking_step").assertTextEquals("Pokrój marchew.")
        compose.onNodeWithTag("cooking_next").performClick()
        compose.onNodeWithTag("cooking_step").assertTextEquals("Ugotuj zupę.")
        compose.onNodeWithTag("cooking_previous").performClick()
        compose.onNodeWithTag("cooking_step").assertTextEquals("Pokrój marchew.")
    }
    @Test fun lightThemeCanBeSelectedAndStoredWithoutChangingMealAccentMeaning() {
        compose.setContent { MojaDietaAiTheme { AppearanceSettingsCard() } }
        compose.onNodeWithTag("section_appearance").performClick()
        compose.onNodeWithTag("theme_light").performClick()
        compose.runOnIdle {
            assertTrue(MangoAppearance.light)
            assertTrue(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(MangoAppearance.PREFS, 0).getBoolean(MangoAppearance.KEY, false))
            assertNotEquals(mealCategoryColor("Breakfast"), mealCategoryColor("Lunch"))
            assertNotEquals(mealCategoryColor("Lunch"), mealCategoryColor("Dinner"))
        }
        compose.onNodeWithTag("theme_dark").performClick()
        compose.runOnIdle { assertFalse(MangoAppearance.light) }
    }
}
