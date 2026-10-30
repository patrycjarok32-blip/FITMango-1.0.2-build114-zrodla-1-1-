package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.MealPlanEntity
import com.example.data.RecipeEntity
import com.example.ui.components.*
import com.example.ui.screens.PlannedMealDetailCard
import com.example.ui.theme.MojaDietaAiTheme
import com.example.viewmodel.SubstituteCandidate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp")
class DailyConvenienceUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun suspiciousQuantityCanBeCorrectedOrExplicitlyKeptOnlyOnce() {
        val guard = QuantityReviewGuard()
        var saves = 0
        compose.setContent { MojaDietaAiTheme { QuantityReviewPrompt(guard) } }
        compose.runOnIdle { guard.run(SubstituteCandidate("Masło orzechowe"), 100f, "g") { saves++ } }
        compose.onNodeWithTag("edit_reviewed_quantity").performClick()
        compose.runOnIdle { assertEquals(0, saves); guard.run(SubstituteCandidate("Masło orzechowe"), 100f, "g") { saves++ } }
        compose.onNodeWithTag("keep_reviewed_quantity").performClick()
        compose.runOnIdle { guard.keep(); assertEquals(1, saves) }
    }
    @Test fun compactMealKeepsNutritionAndEatenVisibleAndMovesEditingToOptions() {
        val meal = MealPlanEntity(id = 728, dayOfWeek = "2026-10-14", mealType = "Obiad", recipeTitle = "Zupa", calories = 200)
        val recipe = RecipeEntity(title = "Zupa", description = "", ingredients = "Marchew — 100 g", instructions = "Ugotuj", calories = 200, protein = 2, carbs = 20, fat = 3, category = "Obiad")
        var moved = false
        var eaten = false
        compose.setContent { MojaDietaAiTheme {
            PlannedMealDetailCard(meal, recipe, emptyList(), "12:00", {}, {}, {}, {}, {}, {}, { moved = true }, {}, { eaten = true })
        } }
        compose.onNodeWithTag("toggle_eaten_728").assertIsDisplayed()
        compose.onNodeWithTag("move_meal_728").assertDoesNotExist()
        compose.onNodeWithTag("meal_options_728").performClick()
        compose.onNodeWithTag("move_meal_728").performClick()
        compose.runOnIdle { assertTrue(moved); assertFalse(eaten) }
        compose.onNodeWithTag("toggle_eaten_728").performClick()
        compose.runOnIdle { assertTrue(eaten) }
    }
}
