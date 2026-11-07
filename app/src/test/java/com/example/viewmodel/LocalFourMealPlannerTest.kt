package com.example.viewmodel

import com.example.data.MealPlanEntity
import com.example.data.RecipeEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LocalFourMealPlannerTest {
    private val start = LocalDate.of(2026, 11, 9)
    private val recipes = listOf(
        RecipeEntity(id = 1, title = "Owsianka", description = "", ingredients = "Płatki", instructions = "",
            calories = 400, protein = 20, carbs = 50, fat = 12, category = "Breakfast"),
        RecipeEntity(id = 2, title = "Kanapki", description = "", ingredients = "Chleb", instructions = "",
            calories = 250, protein = 12, carbs = 32, fat = 8, category = "Snack"),
        RecipeEntity(id = 3, title = "Ryż", description = "", ingredients = "Ryż", instructions = "",
            calories = 600, protein = 25, carbs = 80, fat = 12, category = "Lunch"),
        RecipeEntity(id = 4, title = "Sałatka", description = "", ingredients = "Sałata", instructions = "",
            calories = 400, protein = 20, carbs = 30, fat = 15, category = "Dinner")
    )

    @Test fun generatesFourSavedRecipesForOneThreeAndSevenDays() {
        for (days in listOf(1, 3, 7)) {
            val result = LocalFourMealPlanner.generate(start, days, recipes, emptyList(), 1700)
            assertEquals(days * 4, result.size)
            assertEquals(days, result.map { it.dayOfWeek }.distinct().size)
            result.groupBy { it.dayOfWeek }.values.forEach { meals ->
                assertEquals(LocalFourMealPlanner.SLOTS.toSet(), meals.map { it.mealType }.toSet())
                assertEquals(4, meals.map { it.recipeId }.distinct().size)
                assertTrue(meals.all { meal -> recipes.any { it.id == meal.recipeId && it.title == meal.recipeTitle } })
            }
        }
    }

    @Test fun fillsOnlyMissingSlotAndPreservesExistingMeals() {
        val existing = listOf(MealPlanEntity(dayOfWeek = start.toString(), mealType = "Śniadanie",
            recipeId = 1, recipeTitle = "Owsianka", calories = 400, isCompleted = true))
        val result = LocalFourMealPlanner.generate(start, 1, recipes, existing, 1700)
        assertEquals(3, result.size)
        assertFalse(result.any { it.mealType == "Śniadanie" })
        assertEquals(4, (result + existing).map { it.mealType }.distinct().size)
    }

    @Test fun rejectsEmptyDatabaseWithoutInventingRecipes() {
        assertTrue(runCatching { LocalFourMealPlanner.generate(start, 1, emptyList(), emptyList(), 1700) }.isFailure)
    }

    @Test fun reusesRecipesWhenDatabaseHasFewerThanFour() {
        val result = LocalFourMealPlanner.generate(start, 1, recipes.take(1), emptyList(), 1700)
        assertEquals(4, result.size)
        assertTrue(result.all { it.recipeId == 1L })
    }
}
