package com.example

import com.example.data.RecipeEntity
import com.example.viewmodel.FridgeRecipeSupport
import org.junit.Assert.*
import org.junit.Test

class FridgeRecipeSupportTest {
    private fun recipe(id: Long, name: String, ingredients: String) = RecipeEntity(id = id, title = name,
        description = "", ingredients = ingredients, instructions = "Ugotuj", calories = 400,
        protein = 20, carbs = 30, fat = 10, category = "Obiad")

    @Test fun ranksCompleteRecipesFirstAndListsEveryMissingIngredient() {
        val incomplete = recipe(1, "Ryż z kurczakiem", "Ryż — 100 g\nKurczak — 200 g\nOliwa — 10 g")
        val complete = recipe(2, "Jajka z pomidorami", "Jajka — 2 szt.\nPomidory — 100 g")
        val unmatched = recipe(3, "Owsianka", "Płatki owsiane — 50 g")
        val results = FridgeRecipeSupport.find(listOf(incomplete, complete, unmatched), "jajko, pomidor, ryż")
        assertEquals(listOf(2L, 1L), results.map { it.recipe.id })
        assertTrue(results.first().missing.isEmpty())
        assertEquals(listOf("Kurczak", "Oliwa"), results.last().missing)
    }

    @Test fun matchesAccentsAndAliasesButKeepsSpecificProductsDistinct() {
        assertTrue(FridgeRecipeSupport.matches("Ryż", "rice"))
        assertTrue(FridgeRecipeSupport.matches("Jajka", "egg"))
        assertFalse(FridgeRecipeSupport.matches("Napój owsiany", "Płatki owsiane"))
        assertFalse(FridgeRecipeSupport.matches("Mleko kokosowe", "Mleko"))
        assertFalse(FridgeRecipeSupport.matches("", "Mleko"))
    }

    @Test fun emptyFridgeDoesNotSuggestUnrelatedRecipes() {
        assertTrue(FridgeRecipeSupport.find(listOf(recipe(1, "Ryż", "Ryż — 100 g")), "").isEmpty())
    }
    @Test fun suggestionsKeepSpecificIngredientNamesAndAcceptPolishAccents() {
        val recipes = listOf(recipe(1, "Owsianka", "Mleko kokosowe — 100 ml\nJabłko — 180 g"))
        assertEquals(listOf("Mleko kokosowe"), FridgeRecipeSupport.suggestions(recipes, "jajka, mle"))
        assertEquals(listOf("Jabłko"), FridgeRecipeSupport.suggestions(recipes, "jabl"))
        assertTrue(FridgeRecipeSupport.suggestions(recipes, "j").isEmpty())
        val completed = FridgeRecipeSupport.completeSuggestion("jajka, mle", "Mleko kokosowe")
        assertEquals("jajka, Mleko kokosowe, ", completed)
        assertEquals(listOf("Mleko kokosowe"), FridgeRecipeSupport.find(recipes, completed).single().available)
    }

    @Test fun completedProductsAreNotSuggestedAgain() {
        val recipes = listOf(recipe(1, "Jajecznica", "Jajka — 2 szt.\nPomidor — 100 g"))
        assertTrue(FridgeRecipeSupport.suggestions(recipes, "jajko, jaj").isEmpty())
    }
}
