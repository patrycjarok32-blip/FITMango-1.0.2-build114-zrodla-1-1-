package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.RecipeEntity
import com.example.viewmodel.*
import com.example.ui.components.CookingDialog
import com.example.ui.theme.MojaDietaAiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp")
class CookingIngredientsTest {
    @get:Rule val compose = createComposeRule()
    private fun recipe() = RecipeEntity(id = 83, title = "Tortilla", description = "",
        ingredients = "Jajka — 100 g\nNasiona chia — 10 g\nOliwa — 5 g\nPomidor — 80 g",
        instructions = "1. Wymieszaj jajka z nasionami chia.\n2. Rozgrzej patelnię z oliwą.\n3. Dodaj jajka.",
        calories = 250, protein = 15, carbs = 5, fat = 20, category = "Śniadanie")

    @Test fun inflectedNamesAreAssociatedWithStepsWithoutInventingQuantitySplits() {
        val recipe = recipe()
        val items = CookingIngredientsSupport.fromRecipe(recipe)
        assertEquals(listOf("Jajka", "Nasiona chia"), CookingIngredientsSupport.forStep("Wymieszaj jajka z nasionami chia.", items).map { it.name })
        assertEquals(listOf("Oliwa"), CookingIngredientsSupport.forStep("Rozgrzej patelnię z oliwą.", items).map { it.name })
        assertTrue(CookingIngredientsSupport.forStep("Odstaw masę na kilka minut.", items).isEmpty())
        assertEquals("Jajka — 100 g", items.first().display)
        assertEquals(items.first(), CookingIngredientsSupport.forStep("Dodaj połowę jajek.", items).single())
        val replaced = CookingIngredientsSupport.fromMeal(listOf(ResolvedMealIngredientItem(
            displayLine = "- Mleko owsiane — 500 ml", ingredientName = "Mleko owsiane", quantityForMeal = 500f,
            unit = "ml", originalIngredientName = "Mleko krowie", originalBaseQuantity = 250f, originalUnit = "ml")))
        assertEquals("Mleko owsiane — 500 ml", replaced.single().display)
        assertEquals(replaced, CookingIngredientsSupport.forStep("Wlej mleko krowie.", replaced))
        assertEquals("Jajka — 100 g\nNasiona chia — 10 g\nOliwa — 5 g\nPomidor — 80 g", recipe.ingredients)
    }

    @Test fun usedChecksStayWithTheStepAndTimerIsRemoved() {
        compose.setContent { MojaDietaAiTheme { CookingDialog(recipe()) {} } }
        compose.onNodeWithTag("cooking_check_0").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithTag("cooking_complete_step").performScrollTo().performClick()
        compose.onNodeWithTag("cooking_check_1").performScrollTo().assertIsOn()
        compose.onNodeWithTag("cooking_next").performClick()
        compose.onNodeWithTag("cooking_check_2").performScrollTo().assertIsOff()
        compose.onNodeWithTag("cooking_next").performClick()
        compose.onNodeWithTag("cooking_check_0").performScrollTo().assertIsOff()
        compose.onNodeWithTag("cooking_previous").performClick()
        compose.onNodeWithTag("cooking_previous").performClick()
        compose.onNodeWithTag("cooking_check_0").performScrollTo().assertIsOn()
        compose.onNodeWithText("Minutnik").assertDoesNotExist()
    }
    @Test fun explicitHalfIsComputedFromTheIngredientQuantity() {
        val oats = CookingIngredient("oats", "Płatki owsiane", "Płatki owsiane — 60 g", quantity = 60f, unit = "g")
        assertEquals(listOf(oats), CookingIngredientsSupport.forStep("1/2 płatków zmiel na mąkę.", listOf(oats)))
        assertEquals("Płatki owsiane — 30 g (½ ilości)", CookingIngredientsSupport.displayForStep("1/2 płatków zmiel na mąkę.", oats))
    }
}
