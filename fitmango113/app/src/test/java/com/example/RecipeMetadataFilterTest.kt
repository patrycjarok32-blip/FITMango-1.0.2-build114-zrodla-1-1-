package com.example

import com.example.viewmodel.parseSchemaRecipeDirectly
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecipeMetadataFilterTest {
    private fun json(times: String) = """{"@type":"Recipe","name":"Test zupy",
        "recipeIngredient":["Marchew — 100 g"],"recipeInstructions":"Ugotuj i podaj.",
        "recipeCategory":["Lunch","Dinner"],$times}"""

    @Test fun importKeepsTotalTimeAndBothSourceCategories() {
        val recipe = requireNotNull(parseSchemaRecipeDirectly(json("\"totalTime\":\"PT1H15M\""), "https://example.test/recipe", ""))
        assertEquals(75, recipe.totalPreparationMinutes)
        assertEquals("Lunch / Dinner", recipe.providedCategory)
    }

    @Test fun importAddsPrepAndCookOnlyWhenBothAreKnown() {
        val combined = requireNotNull(parseSchemaRecipeDirectly(json("\"prepTime\":\"PT10M\",\"cookTime\":\"PT20M\""), "https://example.test/recipe", ""))
        assertEquals(30, combined.totalPreparationMinutes)
        val incomplete = requireNotNull(parseSchemaRecipeDirectly(json("\"prepTime\":\"PT10M\""), "https://example.test/recipe", ""))
        assertNull(incomplete.totalPreparationMinutes)
    }
}
