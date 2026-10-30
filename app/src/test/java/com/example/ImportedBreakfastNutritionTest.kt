package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImportedBreakfastNutritionTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun clearProfiles() {
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        NutritionAttentionSupport.load(context)
    }
    @Test fun screenshotBreakfastHasCaloriesMacrosAndNoMissingIngredients() {
        val recipe = RecipeEntity(id = 1, title = "Placuszki", description = "", ingredients =
            "Jogurtu naturalnego — 65 g\nJajka — 2 szt.\nMąki orkiszowej jasnej — 40 g\nCynamonu — 2 g\nProszku do pieczenia — 1 g\nBanana — 80 g",
            instructions = "Wymieszaj", calories = 396, protein = 11, carbs = 71, fat = 7, category = "Śniadanie")
        val meal = MealPlanEntity(id = 1, dayOfWeek = "2026-10-26", mealType = "Śniadanie", recipeId = 1, recipeTitle = recipe.title, calories = 396)
        val items = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(recipe, meal, emptyList())
        assertEquals(6, items.size)
        assertTrue(MealConvenienceSupport.missingIngredients(items, emptyList()).isEmpty())
        assertTrue(NutritionAttentionSupport.collect(listOf(meal), listOf(recipe), emptyList()).isEmpty())
        val expected = listOf(40, 143, 141, null, 1, 71)
        items.forEachIndexed { i, item ->
            if (i != 3) assertEquals(expected[i], IngredientSubstitutionSupport.ingredientCalories(item.ingredientName, item.quantityForMeal, item.unit, emptyList()))
        }
        assertTrue(IngredientSubstitutionSupport.ingredientEnergyLabel("Cynamonu", 2f, "g", emptyList()).contains("pominięta"))
        val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, recipe, emptyList())
        assertFalse(nutrition.isPartial)
        assertEquals(395, nutrition.calories!!)
        assertEquals(20, nutrition.protein!!); assertEquals(51, nutrition.carbs!!); assertEquals(13, nutrition.fat!!)
    }
    @Test fun absentImportedTotalsAreCalculatedFromRecognizedIngredientsWithoutEdits() {
        val recipe = RecipeEntity(id = 2, title = "Bez sum źródłowych", description = "", ingredients =
            "Jogurtu naturalnego — 65 g\nJajka — 2 szt.\nMąki orkiszowej jasnej — 40 g\nProszku do pieczenia — 1 g",
            instructions = "Wymieszaj", calories = 0, protein = 0, carbs = 0, fat = 0, category = "Śniadanie")
        val meal = MealPlanEntity(id = 2, dayOfWeek = "2026-10-26", mealType = "Śniadanie", recipeId = 2, recipeTitle = recipe.title, calories = 0)
        val filled = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, recipe, emptyList())
        // 61 * .65 + 143 * 1 + 352 * .40 + 53 * .01 = 323.98 kcal.
        // Round the completed sum, not each ingredient (40 + 143 + 141 + 1).
        assertEquals(324, filled.calories!!); assertFalse(filled.isPartial)
        assertNotNull(filled.protein); assertNotNull(filled.carbs); assertNotNull(filled.fat)
        val twoPortions = recipe.copy(description = "Porcje: 2")
        val perPortion = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, twoPortions, emptyList())
        assertEquals(162, perPortion.calories!!)
        val imported = ImportedRecipe(recipe.title, "2", recipe.ingredients + "\nCynamonu — 2 g", recipe.instructions, 0, 0, 0, 0)
        assertEquals(162, ImportedNutritionSupport.fromLocalIngredients(imported)!!.calories)
        assertNull(ImportedNutritionSupport.fromLocalIngredients(imported.copy(ingredients = "Cynamonu — 2 g")))
        // Existing complete source totals remain authoritative for an unedited imported portion.
        val original = recipe.copy(calories = 300, protein = 15, carbs = 35, fat = 10)
        assertEquals(300, IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, original, emptyList()).calories!!)
        val noIngredients = recipe.copy(ingredients = "")
        assertNull(IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, noIngredients, emptyList()).calories)
    }

    @Test fun declinedSpicesAreIgnoredButCaloricBakingIngredientsAreNot() {
        listOf("Cynamonu", "Cynamonu mielonego", "Soli", "Pieprzu czarnego", "Kurkumy", "Kminu rzymskiego", "Ziół prowansalskich", "Gałki muszkatołowej", "Papryki wędzonej mielonej", "Czosnku granulowanego").forEach {
            assertTrue(it, IngredientSubstitutionSupport.isIgnoredSeasoning(it))
        }
        listOf("Jogurtu naturalnego", "Jajka", "Mąki orkiszowej jasnej", "Proszku do pieczenia", "Cukru", "Masła", "Czosnku świeżego", "Papryki czerwonej").forEach {
            assertFalse(it, IngredientSubstitutionSupport.isIgnoredSeasoning(it))
        }
    }
    @Test fun measuredEggMassAndSavedValuesWinOverDefaultReference() {
        val egg = RecipeIngredientEntity(recipeId = 1, foodName = "Jajka", quantity = 2f, unit = "szt.", grams = 120f,
            caloriesPer100g = 150f, proteinPer100g = 10f, carbsPer100g = 1f, fatPer100g = 11f)
        assertEquals(180, IngredientSubstitutionSupport.ingredientCalories("Jajka", 2f, "sztuki", listOf(egg))!!)
        assertFalse(IngredientSubstitutionSupport.ingredientEnergyLabel("Jajka", 2f, "sztuki", listOf(egg)).contains("50 g"))
        val yogurt = SubstituteCandidate("Jogurt naturalny", caloriesPer100g = 80f, proteinPer100g = 5f, carbsPer100g = 7f, fatPer100g = 4f)
        NutritionAttentionSupport.save(context, "Jogurt naturalny", yogurt)
        NutritionAttentionSupport.load(context)
        assertEquals(52, IngredientSubstitutionSupport.ingredientCalories("Jogurtu naturalnego", 65f, "g", emptyList())!!)
        val profile = IngredientSubstitutionSupport.lookupKnownIngredientNutrition("Jogurtu naturalnego")!!
        assertEquals(80f, profile.caloriesPer100g, .001f)
    }
    @Test fun broadCatalogAliasesRemainSpecificAndUnknownFoodsStayUnknown() {
        val aliases = listOf("Płatków owsianych górskich", "Twarogu chudego", "Kaszy gryczanej", "Ryżu białego", "Makaronu pełnoziarnistego", "Mleka migdałowego niesłodzonego", "Chleba żytniego razowego")
        aliases.forEach { assertNotNull(it, IngredientSubstitutionSupport.lookupKnownIngredientNutrition(it)) }
        listOf("Jogurtu truskawkowego", "Mąki orkiszowej pełnoziarnistej", "Jajek czekoladowych", "Masła orzechowego", "Nieznany produkt 991").forEach {
            assertNull(it, ImportedIngredientReferences.find(it))
        }
        assertNull(IngredientSubstitutionSupport.ingredientCalories("Nieznany produkt 991", 100f, "g", emptyList()))
        assertNull(IngredientSubstitutionSupport.ingredientCalories("Jajka", null, "szt.", emptyList()))
    }
}
