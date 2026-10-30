package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.viewmodel.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UniversalNutritionRepairTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun recipe(id: Long, text: String) = RecipeEntity(id = id, title = "Test$id", description = "", ingredients = text,
        instructions = "Dodaj", calories = 0, protein = 0, carbs = 0, fat = 0, category = "Obiad")
    private fun candidate(name: String, kcal: Float = 200f) = SubstituteCandidate(name, caloriesPer100g = kcal, proteinPer100g = 5f, carbsPer100g = 20f, fatPer100g = 10f)
    @Before fun clearPreferences() {
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        NutritionAttentionSupport.load(context)
    }
    @Test fun allRecipesIncludingUnplannedAndRepeatedUnknownNamesAreCollected() {
        val recipes = listOf(recipe(1, "Produkt XYZ A — 100 g\nProdukt XYZ A — 20 g\nCynamonu — 2 g"), recipe(2, "Produkt XYZ B"))
        val targets = UniversalNutritionRepair.collect(recipes, emptyList(), emptyList())
        assertEquals(2, targets.size)
        assertEquals(2, targets.first { it.name == "Produkt XYZ A" }.occurrences)
        assertTrue(targets.first { it.name == "Produkt XYZ B" }.quantityMissing)
    }
    @Test fun barcodeSubstitutionsAndMassVolumeBasisAreKeptSeparate() {
        val recipe = recipe(1, "Ryż — 100 g")
        val meal = MealPlanEntity(id = 5, dayOfWeek = "2026-10-26", mealType = "Obiad", recipeId = 1, recipeTitle = "Test", calories = 0)
        val sub = IngredientSubstitutionSupport.encodeMealSubstitutionEntity(mealPlanId = 5, originalIngredientName = "Ryż", originalBaseQuantity = 100f,
            originalUnit = "g", candidate = SubstituteCandidate("Napój XYZ", barcode = "1234567890123"), baseQuantityPerServing = 100f, unit = "ml")
        val target = UniversalNutritionRepair.collect(listOf(recipe), listOf(meal), listOf(sub)).single()
        assertEquals("1234567890123", target.barcode); assertEquals("ml", target.basis)
        assertTrue(UniversalNutritionRepair.matches(target, candidate("Inna nazwa").copy(barcode = target.barcode, defaultUnit = "ml")))
        assertFalse(UniversalNutritionRepair.matches(target, candidate(target.name).copy(barcode = target.barcode, defaultUnit = "g")))
        assertFalse(UniversalNutritionRepair.matches(target, candidate(target.name).copy(barcode = "9999999999999", defaultUnit = "ml")))
    }
    @Test fun exactNamesAreRequiredAndConflictingNutritionNeedsReview() {
        val target = NutritionRepairTarget("Produkt XYZ")
        assertNull(UniversalNutritionRepair.select(target, listOf(candidate("Produkt XYZ z cukrem"))).candidate)
        val conflict = UniversalNutritionRepair.select(target, listOf(candidate(target.name, 100f), candidate(target.name, 300f)))
        assertNull(conflict.candidate); assertEquals(2, conflict.alternatives.size)
        val same = UniversalNutritionRepair.select(target, listOf(candidate(target.name), candidate(target.name).copy(barcode = "1234567890123")))
        assertNotNull(same.candidate)
    }
    @Test fun partialCaloriesAreAcceptedButMissingMacrosAreNeverInventedAsZeros() {
        val partial = SubstituteCandidate("Produkt XYZ", caloriesPer100g = 120f)
        assertTrue(UniversalNutritionRepair.usable(partial))
        val selected = UniversalNutritionRepair.select(NutritionRepairTarget(partial.name), listOf(partial)).candidate!!
        assertEquals(120f, selected.caloriesPer100g!!, .001f); assertNull(selected.proteinPer100g)
        val merged = UniversalNutritionRepair.merge(SubstituteCandidate(partial.name, caloriesPer100g = 0f, proteinPer100g = 7f), candidate(partial.name))
        assertEquals(0f, merged.caloriesPer100g!!, .001f); assertEquals(7f, merged.proteinPer100g!!, .001f)
        assertFalse(UniversalNutritionRepair.usable(partial.copy(caloriesPer100g = Float.NaN)))
        assertFalse(UniversalNutritionRepair.usable(partial.copy(caloriesPer100g = null)))
    }
    @Test fun everyTargetBeyondTheOldDisplayLimitIsProcessedWithoutRealNetwork(): Unit = runBlocking {
        val targets = (1..1030).map { NutritionRepairTarget("Produkt XYZ $it") }
        var calls = 0; var writes = 0
        val result = UniversalNutritionRepairRunner().run(targets, emptyList(), true,
            fetch = { calls++; listOf(candidate(it.name)) }, save = { _, _ -> writes++; NutritionRepairSaveResult(true) }, onProgress = {})
        assertEquals(1030, calls); assertEquals(1030, writes); assertEquals(1030, result.saved)
        assertEquals(1030, result.processed); assertFalse(result.running); assertTrue(result.issues.isEmpty())
    }
    @Test fun repeatedFailuresStopRemoteRetriesButDoNotSkipLocalTargets(): Unit = runBlocking {
        val targets = (1..6).map { NutritionRepairTarget("Produkt XYZ $it") }
        var calls = 0
        val result = UniversalNutritionRepairRunner().run(targets, listOf(candidate(targets.last().name)), true,
            fetch = { calls++; error("offline") }, save = { _, _ -> NutritionRepairSaveResult(true) }, onProgress = {})
        assertEquals(3, calls); assertEquals(6, result.processed); assertEquals(1, result.saved)
        assertTrue(result.issues.any { it.reason.contains("nie wyszukano") })
    }
    @Test fun networkResponsesAreReusedAcrossBasisAndCancellationDoesNotContinue(): Unit = runBlocking {
        var calls = 0
        val same = listOf(NutritionRepairTarget("Produkt XYZ", basis = "g"), NutritionRepairTarget("Produkt XYZ", basis = "ml"))
        UniversalNutritionRepairRunner().run(same, emptyList(), true,
            fetch = { calls++; emptyList() }, save = { _, _ -> error("No candidate") }, onProgress = {})
        assertEquals(1, calls)
        calls = 0
        var writes = 0
        try {
            UniversalNutritionRepairRunner().run((1..3).map { NutritionRepairTarget("Produkt XYZ $it") }, emptyList(), true,
                fetch = { calls++; if (calls == 2) throw CancellationException("stop"); listOf(candidate(it.name)) },
                save = { _, _ -> writes++; NutritionRepairSaveResult(true) }, onProgress = {})
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(2, calls); assertEquals(1, writes)
    }
    @Test fun persistenceFillsOnlyMissingFieldsAndDoesNotDuplicateRepeatedIngredients(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = DietaRepository(db.dietaDao())
            repo.insertRecipe(recipe(1, "Produkt XYZ — 50 g\nProdukt XYZ — 10 g"))
            repo.insertIngredient(RecipeIngredientEntity(recipeId = 1, foodName = "Produkt XYZ", quantity = 50f, grams = 50f,
                caloriesPer100g = -1f, proteinPer100g = 7f, carbsPer100g = 0f, fatPer100g = -1f))
            val store = NutritionRepairStore(context, repo, db)
            val saved = store.save(NutritionRepairTarget("Produkt XYZ"), candidate("Produkt XYZ"))
            assertTrue(saved.changed); assertTrue(saved.complete)
            val row = db.dietaDao().ingredientsForRecipeDirect(1).single()
            assertEquals(200f, row.caloriesPer100g, .001f); assertEquals(7f, row.proteinPer100g, .001f)
            assertEquals(0f, row.carbsPer100g, .001f); assertEquals(10f, row.fatPer100g, .001f)
            assertEquals(50f, row.quantity, .001f); assertEquals(50f, row.grams, .001f)
            assertFalse(store.save(NutritionRepairTarget("Produkt XYZ"), candidate("Produkt XYZ", 500f)).changed)
            val backup = UsabilityPreferences.export(context); UsabilityPreferences.validate(backup)
            assertTrue(backup.contains(NutritionRepairSources.KEY))
        } finally { db.close() }
    }
    @Test fun calorieOnlyPersistenceSurvivesReadAndKeepsUnknownMassAndMacros(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = DietaRepository(db.dietaDao()); repo.insertRecipe(recipe(1, "Produkt XYZ"))
            val result = NutritionRepairStore(context, repo, db).save(NutritionRepairTarget("Produkt XYZ", quantityMissing = true), SubstituteCandidate("Produkt XYZ", caloriesPer100g = 123f))
            assertTrue(result.changed); assertFalse(result.complete)
            val row = db.dietaDao().ingredientsForRecipeDirect(1).single()
            assertEquals(123f, row.caloriesPer100g, .001f); assertEquals(-1f, row.proteinPer100g, .001f)
            assertEquals(0f, row.quantity, .001f); assertEquals(0f, row.grams, .001f)
            assertNull(IngredientSubstitutionSupport.ingredientCalories(row.foodName, null, "g", listOf(row)))
            assertEquals(123f, IngredientSubstitutionSupport.lookupKnownIngredientNutrition(row.foodName, listOf(row))!!.caloriesPer100g, .001f)
            assertTrue(IngredientSubstitutionSupport.ingredientEnergyLabel(row.foodName, null, "g", listOf(row)).contains("123 kcal / 100 g"))
            repo.updateRecipe(recipe(1, "Inny produkt XYZ — 100 g"))
            assertFalse(NutritionRepairStore(context, repo, db).save(NutritionRepairTarget("Produkt XYZ"), candidate("Produkt XYZ")).changed)
        } finally { db.close() }
    }
    @Test fun calorieOnlyDataUpdateMealTotalsWithoutPretendingThatMacroIsComplete(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = DietaRepository(db.dietaDao())
            val r = recipe(1, "Produkt XYZ — 100 g")
            repo.insertRecipe(r)
            repo.insertMealPlans(listOf(MealPlanEntity(id = 1, dayOfWeek = "2026-10-26", mealType = "Obiad", recipeId = 1, recipeTitle = r.title, calories = 0)))
            NutritionRepairStore(context, repo, db).save(NutritionRepairTarget("Produkt XYZ"), SubstituteCandidate("Produkt XYZ", caloriesPer100g = 222f))
            val row = db.dietaDao().ingredientsForRecipeDirect(1).single()
            val meal = db.dietaDao().mealPlansDirect().single()
            assertEquals(222, meal.calories)
            val totals = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, r, listOf(row))
            assertEquals(222, totals.calories!!); assertNull(totals.protein); assertTrue(totals.isPartial)
            val remaining = UniversalNutritionRepair.collect(listOf(r), listOf(meal), listOf(row))
            assertEquals(1, remaining.size) // Remaining macro is visible rather than silently zeroed.
        } finally { db.close() }
    }

    @Test fun offEnergyUsesNormalizedValuesAndNeverConfusesServingWith100g() {
        fun energy(nutrients: String) = com.example.api.OpenFoodFactsClient.parseProductResponseJson(
            """{"status":1,"product":{"product_name":"Produkt XYZ","nutriments":$nutrients}}""").product!!.nutriments!!.energyKcal100g
        assertEquals(200f, energy("""{"energy-kcal_value":50,"energy_100g":836.8}""")!!, .01f)
        assertEquals(300f, energy("""{"energy-kcal_value":50,"energy-kcal_100g":300,"energy_100g":836.8}""")!!, .01f)
        assertNull(energy("""{"energy-kcal_value":50}"""))
        assertEquals(0f, energy("""{"energy-kcal_100g":0}""")!!, .001f)
    }

    @Test fun offlineModeNeverFetchesAndOneFailureDoesNotPreventOtherWrites(): Unit = runBlocking {
        val targets = listOf(NutritionRepairTarget("Produkt XYZ A"), NutritionRepairTarget("Produkt XYZ B"))
        var writes = 0
        val result = UniversalNutritionRepairRunner().run(targets, targets.map { candidate(it.name) }, false,
            fetch = { error("Network forbidden") }, save = { _, _ -> writes++; if (writes == 1) error("write failure"); NutritionRepairSaveResult(true) }, onProgress = {})
        assertEquals(2, result.processed); assertEquals(1, result.saved); assertEquals(1, result.issues.size)
    }
}
