package com.example.viewmodel

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.api.OFFNutriments
import com.example.api.OFFProduct
import com.example.api.OFFProductResponse
import com.example.api.OFFSearchResponse
import com.example.api.OpenFoodFactsApiService
import com.example.api.OpenFoodFactsClient
import com.example.data.AppDatabase
import com.example.data.DietaRepository
import com.example.data.MealPlanEntity
import com.example.data.RecipeEntity
import com.example.ui.screens.BarcodeScannerScreen
import com.example.ui.screens.MealPlanScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import android.os.Looper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w480dp-h2400dp")
class IngredientSubstitutionVerificationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @After
    fun tearDown() {
        OpenFoodFactsClient.resetToDefault()
    }

    @Test
    fun realOpenFoodFactsApi_searchAndBarcodeLookup_andMissingFieldsHandling(): Unit = runBlocking {
        OpenFoodFactsClient.resetToDefault()

        // 1. Live query to real Open Food Facts API (searchProducts & getProductByBarcode)
        var liveSearchSucceeded = false
        var liveProductCount = 0
        var sampleProductInfo = ""
        try {
            val searchResp = OpenFoodFactsClient.searchProductsWithFallback("kasza bulgur")
            val candidates = searchResp.products.orEmpty()
                .mapNotNull { IngredientSubstitutionSupport.fromOpenFoodFactsProduct(it) }
            if (candidates.isNotEmpty()) {
                liveSearchSucceeded = true
                liveProductCount = candidates.size
                val first = candidates.first()
                sampleProductInfo = "${first.name} | brand=${first.brand} | code=${first.barcode} | kcal=${first.caloriesPer100g}"
            }
        } catch (e: Exception) {
            println("SUB_VERIFY_LIVE_OFF_SEARCH_EXCEPTION=${e.javaClass.simpleName}: ${e.message}")
        }

        var liveBarcodeSucceeded = false
        var liveBarcodeProductName = ""
        try {
            // Known real barcode in Open Food Facts (EAN-13)
            val barcodeResp = OpenFoodFactsClient.service.getProductByBarcode("3017620422003")
            if (barcodeResp.status == 1 && barcodeResp.product != null) {
                val cand = IngredientSubstitutionSupport.fromOpenFoodFactsProduct(
                    barcodeResp.product!!,
                    SubstituteOptionMode.BARCODE_SCAN
                )
                if (cand != null) {
                    liveBarcodeSucceeded = true
                    liveBarcodeProductName = "${cand.name} (${cand.brand}) [${cand.caloriesPer100g} kcal/100g]"
                }
            }
        } catch (e: Exception) {
            println("SUB_VERIFY_LIVE_OFF_BARCODE_EXCEPTION=${e.javaClass.simpleName}: ${e.message}")
        }

        println("SUB_VERIFY_LIVE_OFF_SEARCH=$liveSearchSucceeded (count=$liveProductCount, sample=$sampleProductInfo)")
        println("SUB_VERIFY_LIVE_OFF_BARCODE=$liveBarcodeSucceeded ($liveBarcodeProductName)")

        // 2. Verify Moshi parser on real Open Food Facts JSON structure + partial/missing nutrition fields
        val offJsonWithPartialData = """
            {
              "products": [
                {
                  "product_name_pl": "Kasza bulgur ekologiczna",
                  "brands": "BioPlanet",
                  "code": "5907814660121",
                  "image_front_small_url": "https://images.openfoodfacts.org/images/products/590/781/466/0121/front_pl.jpg",
                  "nutriments": {
                    "energy-kcal_100g": 342,
                    "proteins_100g": "12.5",
                    "carbohydrates_100g": 69.0,
                    "fat_100g": 1.5
                  }
                },
                {
                  "product_name": "Produkt bez pełnych makro",
                  "brands": "TestBrand",
                  "code": "5900000000999",
                  "nutriments": {
                    "energy-kcal_100g": 210
                  }
                }
              ]
            }
        """.trimIndent()

        val parsedSearch = OpenFoodFactsClient.parseSearchResponseJson(offJsonWithPartialData)
        val products = parsedSearch.products.orEmpty()
        assertEquals(2, products.size)

        val bulgur = IngredientSubstitutionSupport.fromOpenFoodFactsProduct(products[0])!!
        assertEquals("Kasza bulgur ekologiczna", bulgur.name)
        assertEquals("BioPlanet", bulgur.brand)
        assertEquals("5907814660121", bulgur.barcode)
        assertTrue(bulgur.imageUrl.contains("openfoodfacts.org"))
        assertEquals(342f, bulgur.caloriesPer100g!!, 0.01f)
        assertEquals(12.5f, bulgur.proteinPer100g!!, 0.01f)
        assertEquals(69.0f, bulgur.carbsPer100g!!, 0.01f)
        assertEquals(1.5f, bulgur.fatPer100g!!, 0.01f)

        // Verify missing nutrition values are NOT invented (remain null)
        val partialProduct = IngredientSubstitutionSupport.fromOpenFoodFactsProduct(products[1])!!
        assertEquals(210f, partialProduct.caloriesPer100g!!, 0.01f)
        assertNull("Missing protein must not be invented", partialProduct.proteinPer100g)
        assertNull("Missing carbs must not be invented", partialProduct.carbsPer100g)
        assertNull("Missing fat must not be invented", partialProduct.fatPer100g)

        val partialScaled = partialProduct.nutritionForQuantity(80f)
        assertEquals(168, partialScaled.calories)
        assertNull(partialScaled.protein)
        assertNull(partialScaled.carbs)
        assertNull(partialScaled.fat)

        // 3. Verify EAN-13, EAN-8, UPC-A, UPC-E barcode validation
        assertTrue(IngredientSubstitutionSupport.isValidSupportedBarcode("5907814660121")) // EAN-13
        assertEquals("EAN-13", IngredientSubstitutionSupport.detectBarcodeFormatLabel("5907814660121"))
        assertTrue(IngredientSubstitutionSupport.isValidSupportedBarcode("96385074")) // EAN-8
        assertEquals("EAN-8", IngredientSubstitutionSupport.detectBarcodeFormatLabel("96385074"))
        assertTrue(IngredientSubstitutionSupport.isValidSupportedBarcode("012345678905")) // UPC-A
        assertEquals("UPC-A", IngredientSubstitutionSupport.detectBarcodeFormatLabel("012345678905"))
        assertFalse(IngredientSubstitutionSupport.isValidSupportedBarcode("abc123"))
    }

    @Test
    fun roomPersistence_replaceIngredient_editQuantity_recalculateMacros_preserveOriginalRecipe_andSurviveRestart(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "substitution_persistence_${System.currentTimeMillis()}.db")
        if (dbFile.exists()) dbFile.delete()

        // --- SESSION 1 ---
        val db1 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val repo1 = DietaRepository(db1.dietaDao())
        repo1.seedInitialData()

        // Insert recipe with "Ryż biały — 80 g"
        val curryRecipeId = repo1.insertRecipe(
            RecipeEntity(
                title = "Kurczak curry",
                description = "Porcje: 1",
                ingredients = "Pierś z kurczaka — 150 g\nRyż biały — 80 g\nPapryka — 100 g\nMleko kokosowe — 100 ml",
                instructions = "1. Ugotuj ryż.\n2. Usmaż kurczaka z papryką i mlekiem kokosowym.",
                calories = 520,
                protein = 42,
                carbs = 54,
                fat = 14,
                category = "Obiad",
                source = "Jadlospis.pdf"
            )
        )

        val wedMealId = repo1.insertMealPlan(
            MealPlanEntity(
                dayOfWeek = "Środa",
                mealType = "Obiad",
                recipeId = curryRecipeId,
                recipeTitle = "Kurczak curry",
                servings = 1.0f,
                calories = 520
            )
        )

        // Replace "Ryż biały — 80 g" with "Kasza bulgur — 80 g" in Wednesday Lunch ONLY
        val bulgurCandidate = SubstituteCandidate(
            name = "Kasza bulgur",
            brand = "Plony Natury",
            barcode = "5900000012345",
            imageUrl = "https://images.openfoodfacts.org/bulgur.jpg",
            caloriesPer100g = 342f,
            proteinPer100g = 12.5f,
            carbsPer100g = 69.0f,
            fatPer100g = 1.5f,
            defaultQuantity = 80f,
            defaultUnit = "g",
            source = SubstituteOptionMode.OPEN_FOOD_FACTS
        )

        val subEntity80g = IngredientSubstitutionSupport.encodeMealSubstitutionEntity(
            mealPlanId = wedMealId,
            originalIngredientName = "Ryż biały",
            originalBaseQuantity = 80f,
            originalUnit = "g",
            candidate = bulgurCandidate,
            baseQuantityPerServing = 80f,
            unit = "g"
        )
        val subId = repo1.insertIngredient(subEntity80g)

        val allEntities80g = repo1.allRecipeIngredients.first()
        val recipe1 = repo1.allRecipes.first().first { it.id == curryRecipeId }
        val wedMeal1 = repo1.allMealPlans.first().first { it.id == wedMealId }

        val resolved80g = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(
            recipe = recipe1,
            meal = wedMeal1,
            allIngredientEntities = allEntities80g
        )
        assertTrue(
            "Meal ingredients must show Kasza bulgur — 80 g",
            resolved80g.any { it.displayLine == "Kasza bulgur — 80 g" && it.originalIngredientName == "Ryż biały" }
        )
        assertFalse(
            "Meal ingredients must no longer show Ryż biały as active ingredient",
            resolved80g.any { it.ingredientName.equals("Ryż biały", ignoreCase = true) }
        )

        val nutrition80g = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(
            meal = wedMeal1,
            recipe = recipe1,
            allIngredientEntities = allEntities80g
        )
        // Original Ryż biały (80g): 280 kcal, 5.36g P, 63.12g C, 0.56g F
        // Replacement Kasza bulgur (80g): 273.6 kcal, 10.0g P, 55.2g C, 1.2g F
        // Current ingredients sum at 80g: 737 kcal, 59g P, 64g C, 26g F
        assertEquals(737, nutrition80g.calories)
        assertEquals(59, nutrition80g.protein)
        assertEquals(64, nutrition80g.carbs)
        assertEquals(26, nutrition80g.fat)

        // Now change replacement quantity from 80 g to 150 g
        val subEntity150g = IngredientSubstitutionSupport.encodeMealSubstitutionEntity(
            existingId = subId,
            mealPlanId = wedMealId,
            originalIngredientName = "Ryż biały",
            originalBaseQuantity = 80f,
            originalUnit = "g",
            candidate = bulgurCandidate,
            baseQuantityPerServing = 150f,
            unit = "g"
        )
        repo1.insertIngredient(subEntity150g)

        val allEntities150g = repo1.allRecipeIngredients.first()
        val nutrition150g = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(
            meal = wedMeal1,
            recipe = recipe1,
            allIngredientEntities = allEntities150g
        )
        // Replacement Kasza bulgur (150g): 513 kcal, 18.75g P, 103.5g C, 2.25g F
        // Current ingredients sum at 150g: 977 kcal, 68g P, 112g C, 27g F
        assertEquals(977, nutrition150g.calories)
        assertEquals(68, nutrition150g.protein)
        assertEquals(112, nutrition150g.carbs)
        assertEquals(27, nutrition150g.fat)
        repo1.updateMealPlan(wedMeal1.copy(calories = nutrition150g.calories!!))

        // Verify original recipe is 100% preserved
        val originalRecipeCheck = repo1.allRecipes.first().first { it.id == curryRecipeId }
        assertTrue("Original recipe must still contain Ryż biały — 80 g", originalRecipeCheck.ingredients.contains("Ryż biały — 80 g"))
        assertFalse("Original recipe must not contain Kasza bulgur", originalRecipeCheck.ingredients.contains("Kasza bulgur"))
        assertEquals(520, originalRecipeCheck.calories)
        assertEquals(42, originalRecipeCheck.protein)
        assertEquals(54, originalRecipeCheck.carbs)
        assertEquals(14, originalRecipeCheck.fat)

        println("SUB_VERIFY_80G_MACROS=kcal:${nutrition80g.calories},P:${nutrition80g.protein},C:${nutrition80g.carbs},F:${nutrition80g.fat}")
        println("SUB_VERIFY_150G_MACROS=kcal:${nutrition150g.calories},P:${nutrition150g.protein},C:${nutrition150g.carbs},F:${nutrition150g.fat}")

        db1.close()

        // --- SESSION 2: Simulate full app restart ---
        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val repo2 = DietaRepository(db2.dietaDao())
        repo2.seedInitialData()

        val restartedRecipes = repo2.allRecipes.first()
        val restartedMeals = repo2.allMealPlans.first()
        val restartedIngredients = repo2.allRecipeIngredients.first()

        val restartedCurry = restartedRecipes.first { it.id == curryRecipeId }
        val restartedWedMeal = restartedMeals.first { it.id == wedMealId }
        val restartedResolved = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(
            recipe = restartedCurry,
            meal = restartedWedMeal,
            allIngredientEntities = restartedIngredients
        )

        assertTrue(
            "Substituted ingredient Kasza bulgur — 150 g must persist after restart",
            restartedResolved.any { it.displayLine == "Kasza bulgur — 150 g" }
        )
        assertEquals("Recalculated meal calories must persist after restart", 977, restartedWedMeal.calories)
        assertTrue("Original recipe must still contain Ryż biały after restart", restartedCurry.ingredients.contains("Ryż biały — 80 g"))

        println("SUB_VERIFY_RESTART_PERSISTED=${restartedResolved.map { it.displayLine }}")

        db2.close()
        dbFile.delete()
    }

    @Test
    fun composeUi_fullSubstitutionFlow_offSearch_barcodeScan_aiQuotaFallback_andShoppingListUpdate(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Configure deterministic Open Food Facts API service with real product structure + missing barcode handling
        OpenFoodFactsClient.service = object : OpenFoodFactsApiService {
            override suspend fun searchProducts(query: String): OFFSearchResponse {
                return OFFSearchResponse(
                    products = listOf(
                        OFFProduct(
                            productName = "Kasza bulgur",
                            brands = "Plony Natury",
                            code = "5907814660121",
                            imageUrl = "https://images.openfoodfacts.org/bulgur.jpg",
                            nutriments = OFFNutriments(
                                energyKcal100g = 342f,
                                proteins100g = 12.5f,
                                carbohydrates100g = 69.0f,
                                fat100g = 1.5f
                            )
                        )
                    )
                )
            }

            override suspend fun getProductByBarcode(barcode: String): OFFProductResponse {
                return if (barcode == "5907814660121") {
                    OFFProductResponse(
                        status = 1,
                        product = OFFProduct(
                            productName = "Kasza bulgur Bio",
                            brands = "BioPlanet",
                            code = "5907814660121",
                            imageUrl = "https://images.openfoodfacts.org/bulgur_bio.jpg",
                            nutriments = OFFNutriments(
                                energyKcal100g = 342f,
                                proteins100g = 12.5f,
                                carbohydrates100g = 69.0f,
                                fat100g = 1.5f
                            )
                        )
                    )
                } else {
                    OFFProductResponse(product = null, status = 0)
                }
            }
        }

        val viewModel = DietaViewModel(context.applicationContext as android.app.Application)
        val dao = AppDatabase.getDatabase(context).dietaDao()

        // Clear previous meal plans and insert a clean Wednesday Obiad meal with "Ryż biały — 80 g"
        dao.clearMealPlans()
        val recipeId = dao.insertRecipe(
            RecipeEntity(
                title = "Kurczak curry",
                description = "Porcje: 1",
                ingredients = "Pierś z kurczaka — 150 g\nRyż biały — 80 g\nPapryka — 100 g\nMleko kokosowe — 100 ml",
                instructions = "1. Ugotuj ryż.\n2. Usmaż kurczaka.",
                calories = 520,
                protein = 42,
                carbs = 54,
                fat = 14,
                category = "Obiad",
                source = "Jadlospis.pdf"
            )
        )
        val recipe = dao.getAllRecipes().first().first { it.id == recipeId }
        viewModel.addMealPlan("Środa", "Obiad", recipe, 1.0f)

        composeTestRule.setContent {
            MealPlanScreen(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.mealPlans.value.any { it.recipeTitle == "Kurczak curry" && it.dayOfWeek == MealPlannerSupport.dateForDay("Środa")!!.toString() }
        }

        // Select Wednesday
        composeTestRule.onNodeWithTag("planner_day_Środa").performClick()
        composeTestRule.waitForIdle()

        val wedMeal = viewModel.mealPlans.value.first { it.recipeTitle == "Kurczak curry" && it.dayOfWeek == MealPlannerSupport.dateForDay("Środa")!!.toString() }
        composeTestRule.onNodeWithTag("toggle_meal_details_${wedMeal.id}").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.onAllNodesWithText("- Ryż biały — 80 g").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("- Ryż biały — 80 g").assertIsDisplayed()

        // Click "Zamień" next to "Ryż biały — 80 g"
        composeTestRule.onNodeWithTag("swap_ingredient_${wedMeal.id}_Ryż biały").performClick()
        composeTestRule.waitForIdle()

        // Verify all 4 options are displayed in the replacement dialog
        composeTestRule.onNodeWithTag("swap_mode_OFF").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("swap_mode_SCAN").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("swap_mode_LOCAL").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("swap_mode_AI").performScrollTo().assertIsDisplayed()

        // Test Option 4 first: Simulate Gemini quota exhausted (HTTP 429) and verify error banner + OFF fallback
        viewModel.customAiSubstituteCaller = { _, _ ->
            Result.failure(Exception("HTTP 429: Przekroczono limit zapytań Gemini API (Quota exhausted)."))
        }
        composeTestRule.onNodeWithTag("swap_mode_AI").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.aiSubstitutesError.value != null
        }
        composeTestRule.onNodeWithTag("ai_substitutes_error_banner").performScrollTo().assertIsDisplayed()

        // Now test AI success returning 3 alternatives and requiring explicit selection
        viewModel.customAiSubstituteCaller = { _, _ ->
            Result.success(
                """
                [
                  {"name": "Kasza bulgur", "reason": "Więcej błonnika", "suggestedQuantity": 80, "unit": "g", "caloriesPer100g": 342, "proteinPer100g": 12.5, "carbsPer100g": 69.0, "fatPer100g": 1.5},
                  {"name": "Kasza gryczana", "reason": "Niski IG", "suggestedQuantity": 80, "unit": "g", "caloriesPer100g": 343, "proteinPer100g": 13.3, "carbsPer100g": 71.5, "fatPer100g": 3.4},
                  {"name": "Komosa ryżowa", "reason": "Pełnowartościowe białko", "suggestedQuantity": 80, "unit": "g", "caloriesPer100g": 368, "proteinPer100g": 14.1, "carbsPer100g": 64.2, "fatPer100g": 6.1}
                ]
                """.trimIndent()
            )
        }
        composeTestRule.onNodeWithTag("request_ai_substitutes_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.aiSubstituteSuggestions.value.size == 3
        }
        composeTestRule.onNodeWithTag("ai_product_card_0").performScrollTo().assertIsDisplayed()

        // Test Option 2: Barcode scanner lookup (both missing barcode & valid EAN-13 barcode)
        composeTestRule.onNodeWithTag("swap_mode_SCAN").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("barcode_input_field").performScrollTo().performTextReplacement("5900000099999")
        composeTestRule.onNodeWithTag("barcode_lookup_button").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.barcodeLookupError.value != null
        }
        composeTestRule.onNodeWithTag("barcode_error_text").performScrollTo().assertIsDisplayed()

        // Valid EAN-13 barcode lookup
        composeTestRule.onNodeWithTag("barcode_input_field").performScrollTo().performTextReplacement("5907814660121")
        composeTestRule.onNodeWithTag("barcode_lookup_button").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.scannedProductResult.value != null
        }
        composeTestRule.onNodeWithTag("scanned_product_card").performScrollTo().assertIsDisplayed()

        // Test Option 1: Open Food Facts search & select "Kasza bulgur", edit quantity to 80 g, and confirm replacement
        composeTestRule.onNodeWithTag("swap_mode_OFF").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("off_search_input").performScrollTo().performTextReplacement("Kasza bulgur")
        composeTestRule.onNodeWithTag("off_search_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.offSearchResults.value.isNotEmpty()
        }
        composeTestRule.onNodeWithTag("off_product_card_0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("off_product_card_0").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Verify Original vs Replacement preview is shown for 80 g
        composeTestRule.onNodeWithText("Oryginał: Ryż biały — 80 g").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Zamiennik: Kasza bulgur — 80 g").performScrollTo().assertIsDisplayed()

        // Confirm replacement
        composeTestRule.onNodeWithTag("confirm_swap_button").performClick()
        composeTestRule.waitForIdle()

        val savedMealScope = hasAnyAncestor(hasTestTag("planned_meal_card_${wedMeal.id}"))
        val replacementInMeal = hasText("- Kasza bulgur — 80 g") and savedMealScope
        val originalInMeal = hasText("Oryginał: Ryż biały — 80 g") and savedMealScope
        // Room may emit the new meal before the save callback closes the sheet.
        // Wait for both persistence and dismissal, then assert on this exact meal.
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.onAllNodesWithTag("ingredient_replacement_dialog").fetchSemanticsNodes().isEmpty() &&
                composeTestRule.onAllNodes(replacementInMeal).fetchSemanticsNodes().size == 1
        }

        // Verify the replacement and original caption in the saved meal, not the preview.
        composeTestRule.onNode(replacementInMeal).assertIsDisplayed()
        composeTestRule.onNode(originalInMeal).assertIsDisplayed()

        // Verify shopping list contains "Kasza bulgur" (80 g) and NOT "Ryż biały"
        var shoppingItems = emptyList<com.example.data.ShoppingItemEntity>()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            shoppingItems = runBlocking(Dispatchers.IO) { dao.getAllShoppingItems().first() }
            shoppingItems.any { it.itemName.contains("Kasza bulgur", ignoreCase = true) && it.amount.contains("80") }
        }
        assertTrue(
            "Shopping list must contain replacement Kasza bulgur (80 g)",
            shoppingItems.any { it.itemName.contains("Kasza bulgur", ignoreCase = true) && it.amount.contains("80") }
        )
        assertFalse(
            "Shopping list must NOT contain replaced original ingredient Ryż biały",
            shoppingItems.any { it.itemName.contains("Ryż biały", ignoreCase = true) }
        )

        // Verify original recipe in Room is preserved untouched
        val currentRecipeInDb = dao.getAllRecipes().first().first { it.id == recipeId }
        assertTrue(currentRecipeInDb.ingredients.contains("Ryż biały — 80 g"))
        assertFalse(currentRecipeInDb.ingredients.contains("Kasza bulgur"))

        println("SUB_VERIFY_UI_SHOPPING_LIST=${shoppingItems.joinToString { "${it.itemName}: ${it.amount}" }}")
    }

    @Test
    fun barcodeScannerScreen_cameraPermissionAndManualBarcodeCallback_verification() {
        var scannedCodeResult: String? = null
        composeTestRule.setContent {
            BarcodeScannerScreen(
                onBarcodeScanned = { code -> scannedCodeResult = code },
                onBack = {}
            )
        }
        composeTestRule.waitForIdle()

        // In Robolectric default state without camera permission granted, permission prompt & manual barcode entry are displayed
        composeTestRule.onNodeWithTag("request_camera_permission_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scanner_manual_barcode_input").performTextReplacement("5907814660121")
        composeTestRule.onNodeWithTag("scanner_submit_barcode_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("5907814660121", scannedCodeResult)
    }
}
