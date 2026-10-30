package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.viewmodel.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EverydayConvenienceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun oneSavedNutrientProfileResolvesTheSameUnknownProductInMultipleMealsWithoutInventingQuantities() {
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit(); NutritionAttentionSupport.load(context)
        val recipes = (1L..2L).map { RecipeEntity(id = it, title = "Test$it", description = "", ingredients = "Produkt unikatowy alfa — 100 g", instructions = "Dodaj", calories = 0, protein = 0, carbs = 0, fat = 0, category = "Obiad") }
        val meals = recipes.map { MealPlanEntity(id = it.id, dayOfWeek = "2026-10-21", mealType = "Obiad", recipeId = it.id, recipeTitle = it.title, calories = 0) }
        val queue = NutritionAttentionSupport.collect(meals, recipes, emptyList())
        assertEquals(1, queue.size); assertEquals(2, queue.single().meals)
        NutritionAttentionSupport.save(context, queue.single().name, SubstituteCandidate("Produkt unikatowy alfa", caloriesPer100g = 123f, proteinPer100g = 5f, carbsPer100g = 20f, fatPer100g = 2f))
        assertTrue(NutritionAttentionSupport.collect(meals, recipes, emptyList()).isEmpty())
        val calories = meals.map { IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(it, recipes.first { r -> r.id == it.recipeId }, emptyList()).calories }
        assertEquals(listOf(123, 123), calories)
        val doubled = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meals.first().copy(servings = 2f), recipes.first(), emptyList())
        assertEquals(246, doubled.calories)
        assertEquals(10, doubled.protein); assertEquals(40, doubled.carbs); assertEquals(4, doubled.fat)
        assertFalse(doubled.isPartial)
        assertEquals(246, IngredientSubstitutionSupport.totalMealNutrition(meals, recipes, emptyList()).calories)
        val stored = RecipeIngredientEntity(recipeId = 1, foodName = queue.single().name,
            caloriesPer100g = -1f, proteinPer100g = -1f, carbsPer100g = -1f, fatPer100g = -1f)
        val localProduct = IngredientSubstitutionSupport.buildLocalDatabaseCandidates(listOf(stored), queue.single().name).single()
        assertEquals(123f, localProduct.caloriesPer100g!!, 0f)
        assertEquals(5f, localProduct.proteinPer100g!!, 0f)
        assertEquals(20f, localProduct.carbsPer100g!!, 0f)
        assertEquals(2f, localProduct.fatPer100g!!, 0f)
        val backup = UsabilityPreferences.export(context)
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit(); NutritionAttentionSupport.load(context)
        assertNull(NutritionAttentionSupport.profile(queue.single().name))
        UsabilityPreferences.restore(context, backup)
        assertEquals(123f, NutritionAttentionSupport.profile(queue.single().name)!!.caloriesPer100g, 0f)
        val unknownQuantity = recipes.first().copy(ingredients = "Produkt unikatowy alfa")
        assertTrue(NutritionAttentionSupport.collect(listOf(meals.first()), listOf(unknownQuantity), emptyList()).isEmpty())
        // Missing quantity still needs attention in the meal, not another nutrient entry.
        val resolved = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(unknownQuantity, meals.first(), emptyList())
        assertFalse(MealConvenienceSupport.missingIngredients(resolved, emptyList()).isEmpty())
    }
    @Test fun mealHistorySupportsRepeatedUndoAndRejectsNewerEdits() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.dietaDao(); val store = MealUndoStore(db)
            val recipe = RecipeEntity(id = 891, title = "Ryż", description = "", ingredients = "Ryż — 100 g", instructions = "Ugotuj", calories = 100, protein = 2, carbs = 20, fat = 0, category = "Obiad")
            dao.insertRecipe(recipe)
            val meal = MealPlanEntity(id = 891, dayOfWeek = "2026-10-21", mealType = "Obiad", recipeId = 891, recipeTitle = "Ryż", calories = 100)
            dao.insertMealPlan(meal)
            store.edit(meal.id, "Pierwsza zmiana") { dao.updateMealPlan(meal.copy(calories = 150)) }
            store.edit(meal.id, "Druga zmiana") { dao.updateMealPlan(meal.copy(calories = 200)) }
            assertEquals(2, store.entries.value.size)
            val older = store.entries.value.last().id
            assertTrue(runCatching { store.undo(older) }.isFailure)
            assertEquals(200, dao.mealPlanById(meal.id)!!.calories)
            assertTrue(store.undo()); assertEquals(150, dao.mealPlanById(meal.id)!!.calories)
            store.dismissLabel(); assertEquals(1, store.entries.value.size)
            assertTrue(store.undo()); assertEquals(100, dao.mealPlanById(meal.id)!!.calories)
            assertFalse(store.undo())
        } finally { db.close() }
    }
    @Test fun removedMeasurementCanBeRestoredButCannotOverwriteNewerData() = runBlocking {
        MeasurementChangeHistory.clear()
        val db = AppDatabase.getDatabase(context); val dao = db.dietaDao()
        val row = BodyMeasurementEntity(id = 89001, date = "21.10.2026", weightKg = 61.5f)
        dao.insertBodyMeasurement(row); dao.insertWeightLog(WeightLogEntity(id = 89001, date = row.date, weightKg = row.weightKg))
        val before = MeasurementChangeHistory.snapshot(context)
        dao.deleteBodyMeasurement(row)
        val after = MeasurementChangeHistory.snapshot(context)
        MeasurementChangeHistory.recordBody(context, "Usunięto pomiar", before, after)
        MeasurementChangeHistory.undo(MeasurementChangeHistory.entries.value.first().id)
        assertEquals(before, MeasurementChangeHistory.snapshot(context))
        dao.deleteBodyMeasurement(row)
        MeasurementChangeHistory.recordBody(context, "Usunięto pomiar ponownie", before, MeasurementChangeHistory.snapshot(context))
        dao.insertBodyMeasurement(row.copy(id = 89002, weightKg = 62f))
        assertTrue(runCatching { MeasurementChangeHistory.undo(MeasurementChangeHistory.entries.value.first().id) }.isFailure)
        assertTrue(MeasurementChangeHistory.snapshot(context).measurements.any { it.id == 89002L })
        MeasurementChangeHistory.clear()
    }
    @Test fun cookingScalingKeepsUnknownAmountsAndDoesNotChangeTheOriginalOrDoubleApplyFractions() {
        val oats = CookingIngredient("oats", "Płatki owsiane", "Płatki owsiane — 60 g", quantity = 60f, unit = "g")
        val salt = CookingIngredient("salt", "Sól", "Sól do smaku")
        val scaled = CookingIngredientsSupport.scaled(listOf(oats, salt), 2f)
        assertEquals(120f, scaled.first().quantity!!, 0f)
        assertEquals("Płatki owsiane — 60 g (½ ilości)", CookingIngredientsSupport.displayForStep("Zmiel 1/2 płatków", scaled.first()))
        assertEquals(salt, scaled.last()); assertEquals(60f, oats.quantity!!, 0f)
        assertTrue(runCatching { CookingIngredientsSupport.scaled(listOf(oats), Float.NaN) }.isFailure)
    }
    @Test fun namedMealSetPreservesProductOverridesAndStartsUneatenOnAnotherDate() {
        val meal = MealPlanEntity(id = 89, dayOfWeek = "2026-10-21", mealType = "Śniadanie", recipeId = 89, recipeTitle = "Moje śniadanie", servings = 1.5f, calories = 333, isCompleted = true, completedDate = "2026-10-21")
        val override = RecipeIngredientEntity(id = 89, recipeId = -89, foodName = "Własny produkt", quantity = 170f, grams = 170f)
        val set = MealConvenienceSupport.snapshot("Moje śniadanie", listOf(meal), listOf(override))
        MealConvenienceSupport.write(context, listOf(set))
        val entry = MealConvenienceSupport.read(context).single().entries.single()
        assertEquals(1.5f, entry.meal.servings, 0f); assertEquals(170f, entry.overrides.single().quantity, 0f)
        val copy = QuickUseSupport.freshCopy(entry.meal, "2026-10-23", "Śniadanie")
        assertEquals("2026-10-23", copy.dayOfWeek); assertFalse(copy.isCompleted); assertNull(copy.completedDate)
    }
}
