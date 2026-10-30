package com.example.viewmodel

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import com.example.data.RecipeEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DailyAndImportRegressionTest {
    @Test fun realWeekCrossesMonthAndYearCorrectly() {
        val days = MealPlannerSupport.weekDays(LocalDate.of(2027, 1, 1))
        assertEquals(listOf(28, 29, 30, 31, 1, 2, 3), days.map { it.dayNumber })
        assertEquals("Piątek", days[4].canonicalName)
        assertEquals("01.01", days[4].dateLabel)
    }
    @Test fun completionOnlyCountsInItsDatedWeek() {
        val meal = com.example.data.MealPlanEntity(dayOfWeek = "Środa", mealType = "Śniadanie", recipeTitle = "Omlet", calories = 200, isCompleted = true, completedDate = "2026-10-07")
        assertTrue(MealPlannerSupport.completedInCurrentWeek(meal, LocalDate.of(2026, 10, 7)))
        assertFalse(MealPlannerSupport.completedInCurrentWeek(meal, LocalDate.of(2026, 10, 14)))
        assertFalse(MealPlannerSupport.completedInCurrentWeek(meal.copy(completedDate = null), LocalDate.of(2026, 10, 7)))
    }
    @Test fun plannedUneatenAndOtherDaysAreExcludedFromConsumedCalories() {
        val date = LocalDate.of(2026, 10, 7)
        val meal = com.example.data.MealPlanEntity(dayOfWeek = "Środa", mealType = "Śniadanie", recipeTitle = "Omlet", calories = 200)
        assertEquals(0, MealPlannerSupport.consumedCaloriesForDate(listOf(meal), date))
        assertEquals(200, MealPlannerSupport.consumedCaloriesForDate(listOf(meal, meal.copy(isCompleted = true, completedDate = "2026-10-07"), meal.copy(dayOfWeek = "Wtorek", isCompleted = true, completedDate = "2026-10-06")), date))
    }
    @Test fun shoppingDoesNotClassifyMilkSpicesOrUnknownAsProduce() {
        assertEquals("Nabiał", ShoppingCategories.infer("Semi-skimmed milk"))
        assertEquals("Napoje", ShoppingCategories.infer("Mleko kokosowe"))
        assertEquals("Przyprawy", ShoppingCategories.infer("Kurkuma (odrobina do smaku)"))
        assertEquals("Orzechy i nasiona", ShoppingCategories.infer("Pistacje"))
        assertEquals("Dodatki i słodziki", ShoppingCategories.infer("Erytrytol"))
        assertEquals("Warzywa i owoce", ShoppingCategories.infer("Maliny"))
        assertEquals("Inne", ShoppingCategories.infer("Nieznany produkt"))
    }
    @Test fun importedNestedNutritionAndSourceImageAreKept() {
        val result = parseImportedRecipe("""{"title":"Omlet","ingredients":["Jajka — 100 g"],"instructions":["Usmaż"],"nutrition":{"calories":"200 kcal","proteinContent":"15 g","carbohydrateContent":"2 g","fatContent":"14 g"},"image":"https://example.org/omlet.jpg"}""")
        assertEquals(200, result.calories); assertEquals(15, result.protein)
        assertEquals(2, result.carbs); assertEquals(14, result.fat)
        assertEquals("https://example.org/omlet.jpg", result.imageUrl)
    }
    @Test fun removingKnownIngredientSubtractsItsContributionAndKeepsOthers() {
        val recipe = RecipeEntity(title = "Owsianka", description = "", ingredients = "Płatki owsiane — 50 g\nMaliny — 50 g", instructions = "Gotuj", calories = 300, protein = 15, carbs = 45, fat = 8, category = "Śniadanie")
        val result = IngredientSubstitutionSupport.recalculateEditedIngredients(recipe, "Maliny — 50 g", emptyList())
        assertEquals(114, result.calories)
        assertTrue(result.protein > 0)
        assertEquals(recipe.calories, IngredientSubstitutionSupport.recalculateEditedIngredients(recipe.copy(ingredients = "Nieznany składnik — 5 g\nMaliny — 50 g"), "Maliny — 50 g", emptyList()).calories)
    }
}
