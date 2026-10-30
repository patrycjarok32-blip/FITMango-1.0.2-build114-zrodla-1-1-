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
class ImportedIngredientNutritionTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun clearSavedProfiles() {
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        NutritionAttentionSupport.load(context)
    }
    @Test fun screenshotNamesGainNutrientsAndCaloriesAutomatically() {
        val examples = listOf(Triple("Cebuli szalotki", 80f, 58), Triple("Oliwy z oliwek", 5f, 44))
        for ((name, quantity, kcal) in examples) {
            val p = IngredientSubstitutionSupport.lookupKnownIngredientNutrition(name)
            assertNotNull(name, p)
            assertNotNull(name, p!!.proteinPer100g)
            assertEquals(kcal, IngredientSubstitutionSupport.ingredientCalories(name, quantity, "g", emptyList())!!)
            assertTrue(IngredientSubstitutionSupport.ingredientEnergyLabel(name, quantity, "g", emptyList()).contains("szacunek"))
        }
        assertEquals(13, IngredientSubstitutionSupport.ingredientCalories("Czosnku", 3f, "ząbki", emptyList())!!)
        assertTrue(IngredientSubstitutionSupport.ingredientEnergyLabel("Czosnku", 3f, "ząbki", emptyList()).contains("3 g"))
    }
    @Test fun ownNutrientsWinAndOnlyMissingFieldsUseReferences() {
        val row = RecipeIngredientEntity(recipeId = 1, foodName = "Czosnku", caloriesPer100g = 200f,
            proteinPer100g = 0f, carbsPer100g = -1f, fatPer100g = 3f)
        val p = IngredientSubstitutionSupport.lookupKnownIngredientNutrition("Czosnku", listOf(row))!!
        assertEquals(200f, p.caloriesPer100g, .001f)
        assertEquals(0f, p.proteinPer100g!!, .001f)
        assertEquals(33.06f, p.carbsPer100g!!, .001f)
        assertEquals(3f, p.fatPer100g!!, .001f)
        val missingEnergy = row.copy(caloriesPer100g = -1f)
        val filled = IngredientSubstitutionSupport.lookupKnownIngredientNutrition("Czosnku", listOf(missingEnergy))!!
        assertEquals(149f, filled.caloriesPer100g, .001f)
        assertEquals(0f, filled.proteinPer100g!!, .001f)
    }
    @Test fun savedLabelDataApplyToInflectedNamesAndSurviveReload() {
        NutritionAttentionSupport.save(context, "Czosnek", SubstituteCandidate("Czosnek", caloriesPer100g = 180f,
            proteinPer100g = 7f, carbsPer100g = 30f, fatPer100g = 2f))
        NutritionAttentionSupport.load(context)
        val p = IngredientSubstitutionSupport.lookupKnownIngredientNutrition("Czosnku")!!
        assertEquals(180f, p.caloriesPer100g, .001f)
        assertEquals(7f, p.proteinPer100g!!, .001f)
    }
    @Test fun measuredCloveWeightWinsEvenWithoutNutrientData() {
        val row = RecipeIngredientEntity(recipeId = 1, foodName = "Czosnek", quantity = 2f, unit = "ząbek", grams = 10f,
            caloriesPer100g = -1f, proteinPer100g = -1f, carbsPer100g = -1f, fatPer100g = -1f)
        assertEquals(15f, IngredientSubstitutionSupport.quantityInGramsOrMl("Czosnku", 3f, "ząbki", listOf(row))!!, .001f)
        assertFalse(IngredientSubstitutionSupport.ingredientEnergyLabel("Czosnku", 3f, "ząbki", listOf(row)).contains("1 ząbek"))
    }
    @Test fun unknownProcessedNamesAndUnmeasuredUnitsAreNeverGuessed() {
        for (name in listOf("Sos czosnkowy", "Czosnku w proszku", "Szalotki marynowane", "Oliwy smakowej", "Czosnek2", "Produkt unikatowy kolejka"))
            assertNull(name, ImportedIngredientReferences.find(name))
        assertNull(IngredientSubstitutionSupport.ingredientCalories("Czosnku", null, "ząbki", emptyList()))
        assertNull(IngredientSubstitutionSupport.ingredientCalories("Czosnku", 1f, "główka", emptyList()))
        assertNull(IngredientSubstitutionSupport.ingredientCalories("Cebuli szalotki", 2f, "szt.", emptyList()))
        assertNull(IngredientSubstitutionSupport.ingredientCalories("Produkt nieznany", 100f, "g", emptyList()))
    }
    @Test fun existingImportedRecipesResolveWithoutDatabaseRewriteOrChangingServings() {
        val recipe = RecipeEntity(id = 1, title = "Test", description = "", ingredients = "Cebuli szalotki — 80 g\nCzosnku — 3 ząbki\nOliwy z oliwek — 5 g",
            instructions = "Dodaj", calories = 402, protein = 36, carbs = 9, fat = 24, category = "Obiad")
        val meal = MealPlanEntity(id = 1, dayOfWeek = "2026-10-25", mealType = "Obiad", recipeId = 1, recipeTitle = "Test", calories = 402)
        val items = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(recipe, meal, emptyList())
        assertTrue(MealConvenienceSupport.missingIngredients(items, emptyList()).isEmpty())
        assertTrue(NutritionAttentionSupport.collect(listOf(meal), listOf(recipe), emptyList()).isEmpty())
        // Imported portion totals are not replaced by the sum of potentially whole-recipe quantities.
        assertEquals(402, IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, recipe, emptyList()).calories!!)
        val doubled = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(recipe, meal.copy(servings = 2f), emptyList())
        assertEquals(27, IngredientSubstitutionSupport.ingredientCalories(doubled[1].ingredientName, doubled[1].quantityForMeal, doubled[1].unit, emptyList())!!)
    }
}
