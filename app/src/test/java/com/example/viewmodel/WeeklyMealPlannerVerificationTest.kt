package com.example.viewmodel

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DietaRepository
import com.example.data.MealPlanEntity
import com.example.data.RecipeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w480dp-h2400dp")
class WeeklyMealPlannerVerificationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun weeklyMealPlanner_endToEndRoomPersistenceAndScalingVerification(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "planner_verification_${System.currentTimeMillis()}.db")
        if (dbFile.exists()) dbFile.delete()

        // --- SESSION 1: Initial startup + recipe imports + planner operations ---
        val db1 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val repo1 = DietaRepository(db1.dietaDao())
        repo1.seedInitialData()

        val initialRecipes = repo1.allRecipes.first()
        assertEquals("Should have 3 seeded recipes initially", 3, initialRecipes.size)

        // Insert a PDF-imported recipe ("Kurczak curry")
        val pdfRecipeId = repo1.insertRecipe(
            RecipeEntity(
                title = "Kurczak curry",
                description = "Porcje: 1 (Źródło: Jadlospis_PDF.pdf)",
                ingredients = "150 g Pierś z kurczaka\n80 g Ryż\n100 g Papryka\n100 ml Mleko kokosowe",
                instructions = "1. Pokrój kurczaka i paprykę.\n2. Ugotuj ryż.\n3. Duś w mleku kokosowym z pastą curry.",
                calories = 520,
                protein = 42,
                carbs = 54,
                fat = 14,
                category = "Obiad",
                source = "Jadlospis_PDF.pdf"
            )
        )
        val pdfRecipe = repo1.allRecipes.first().first { it.id == pdfRecipeId }

        // Insert a Website-imported recipe ("Makaron z łososiem")
        val webRecipeId = repo1.insertRecipe(
            RecipeEntity(
                title = "Makaron z łososiem",
                description = "Porcje: 1 (Zaimportowano z Website)",
                ingredients = "120 g Makaron pełnoziarnisty\n150 g Łosoś atlantycki\n80 g Szpinak baby\n10 ml Oliwa z oliwek",
                instructions = "1. Ugotuj makaron al dente.\n2. Usmaż łososia ze szpinakiem na oliwie.\n3. Wymieszaj i podawaj.",
                calories = 560,
                protein = 38,
                carbs = 48,
                fat = 22,
                category = "Kolacja",
                source = "Website"
            )
        )
        val webRecipe = repo1.allRecipes.first().first { it.id == webRecipeId }

        // 1. Verify all 7 days (Monday–Sunday) and all 5 categories exist in MealPlannerSupport
        assertEquals(7, MealPlannerSupport.WEEK_DAYS.size)
        assertEquals(
            listOf("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela"),
            MealPlannerSupport.WEEK_DAYS.map { it.canonicalName }
        )
        assertEquals(
            listOf("Śniadanie", "II śniadanie", "Obiad", "Przekąska", "Kolacja"),
            MealPlannerSupport.MEAL_CATEGORIES.map { it.canonicalName }
        )

        // 2. Add PDF-imported recipe to Wednesday lunch ("Środa", "Obiad", 1 serving)
        val wedLunchId = repo1.insertMealPlan(
            MealPlanEntity(
                dayOfWeek = MealPlannerSupport.normalizeDayOfWeek("Środa (11.09)"),
                mealType = MealPlannerSupport.normalizeMealType("Obiad"),
                recipeId = pdfRecipe.id,
                recipeTitle = pdfRecipe.title,
                servings = 1.0f,
                calories = pdfRecipe.calories
            )
        )

        // 3. Add website-imported recipe to Monday dinner ("Poniedziałek", "Kolacja", 1 serving)
        val monDinnerId = repo1.insertMealPlan(
            MealPlanEntity(
                dayOfWeek = MealPlannerSupport.normalizeDayOfWeek("Poniedziałek (09.09)"),
                mealType = MealPlannerSupport.normalizeMealType("Kolacja"),
                recipeId = webRecipe.id,
                recipeTitle = webRecipe.title,
                servings = 1.0f,
                calories = webRecipe.calories
            )
        )

        var allPlans = repo1.allMealPlans.first()
        val wedLunch1x = allPlans.first { it.id == wedLunchId }
        val monDinner1x = allPlans.first { it.id == monDinnerId }

        assertEquals(MealPlannerSupport.dateForDay("Środa")!!.toString(), wedLunch1x.dayOfWeek)
        assertEquals("Obiad", wedLunch1x.mealType)
        assertEquals(MealPlannerSupport.dateForDay("Poniedziałek")!!.toString(), monDinner1x.dayOfWeek)
        assertEquals("Kolacja", monDinner1x.mealType)

        // 4. Verify ingredient quantities at 1 serving
        val parsedPdfIngredients = MealPlannerSupport.parseRecipeIngredients(pdfRecipe.ingredients)
        assertEquals("Kurczak curry — 1 porcja", MealPlannerSupport.formatMealHeader(wedLunch1x.recipeTitle, wedLunch1x.servings))
        assertEquals(
            listOf(
                "- Pierś z kurczaka — 150 g",
                "- Ryż — 80 g",
                "- Papryka — 100 g",
                "- Mleko kokosowe — 100 ml"
            ),
            parsedPdfIngredients.map { it.formatBulletForServings(1.0f) }
        )
        println("PLANNER_VERIFY_1X_HEADER=${MealPlannerSupport.formatMealHeader(wedLunch1x.recipeTitle, wedLunch1x.servings)}")
        println("PLANNER_VERIFY_1X_INGREDIENTS=${parsedPdfIngredients.joinToString(" | ") { it.formatBulletForServings(1.0f) }}")

        // 5. Scale ingredients from 1 to 2 servings and recalculate nutrition
        val newServings = 2.0f
        val updatedWedLunch = wedLunch1x.copy(
            servings = newServings,
            calories = (pdfRecipe.calories * newServings).toInt()
        )
        repo1.updateMealPlan(updatedWedLunch)

        val wedLunch2x = repo1.allMealPlans.first().first { it.id == wedLunchId }
        assertEquals(2.0f, wedLunch2x.servings, 0.001f)
        assertEquals("Kurczak curry — 2 porcje", MealPlannerSupport.formatMealHeader(wedLunch2x.recipeTitle, wedLunch2x.servings))
        assertEquals(
            listOf(
                "- Pierś z kurczaka — 300 g",
                "- Ryż — 160 g",
                "- Papryka — 200 g",
                "- Mleko kokosowe — 200 ml"
            ),
            parsedPdfIngredients.map { it.formatBulletForServings(wedLunch2x.servings) }
        )

        val scaledNutrition2x = MealPlannerSupport.calculateScaledNutrition(wedLunch2x, pdfRecipe)
        assertEquals(1040, scaledNutrition2x.calories)
        assertEquals(84, scaledNutrition2x.protein)
        assertEquals(108, scaledNutrition2x.carbs)
        assertEquals(28, scaledNutrition2x.fat)
        println("PLANNER_VERIFY_2X_HEADER=${MealPlannerSupport.formatMealHeader(wedLunch2x.recipeTitle, wedLunch2x.servings)}")
        println("PLANNER_VERIFY_2X_INGREDIENTS=${parsedPdfIngredients.joinToString(" | ") { it.formatBulletForServings(wedLunch2x.servings) }}")
        println("PLANNER_VERIFY_2X_MACROS=kcal:${scaledNutrition2x.calories},P:${scaledNutrition2x.protein},C:${scaledNutrition2x.carbs},F:${scaledNutrition2x.fat}")

        // Verify missing nutritional values are NOT invented
        val recipeWithoutMacros = pdfRecipe.copy(calories = 0, protein = 0, carbs = 0, fat = 0)
        val mealWithoutMacros = wedLunch2x.copy(calories = 0)
        val missingNutrition = MealPlannerSupport.calculateScaledNutrition(mealWithoutMacros, recipeWithoutMacros)
        assertNull(missingNutrition.calories)
        assertNull(missingNutrition.protein)
        assertNull(missingNutrition.carbs)
        assertNull(missingNutrition.fat)
        assertFalse(missingNutrition.hasMacros)

        // 6. Move meal between days (Wednesday -> Friday) and mark as eaten
        repo1.updateMealPlan(
            wedLunch2x.copy(
                dayOfWeek = MealPlannerSupport.dateForDay("Piątek")!!.toString(),
                isCompleted = true
            )
        )
        val movedMeal = repo1.allMealPlans.first().first { it.id == wedLunchId }
        assertEquals(MealPlannerSupport.dateForDay("Piątek")!!.toString(), movedMeal.dayOfWeek)
        assertTrue(movedMeal.isCompleted)

        // 7. Remove a meal (remove initial Monday breakfast)
        val mondayBreakfast = repo1.allMealPlans.first().first {
            it.dayOfWeek == MealPlannerSupport.dateForDay("Poniedziałek")!!.toString() && it.mealType == "Śniadanie"
        }
        val countBeforeDelete = repo1.allMealPlans.first().size
        repo1.deleteMealPlan(mondayBreakfast)
        val countAfterDelete = repo1.allMealPlans.first().size
        assertEquals(countBeforeDelete - 1, countAfterDelete)
        assertFalse(repo1.allMealPlans.first().any { it.id == mondayBreakfast.id })

        db1.close()

        // --- SESSION 2: Simulate App Restart on the same SQLite database file ---
        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val repo2 = DietaRepository(db2.dietaDao())
        // Calling seedInitialData() on restart must NOT overwrite or duplicate existing data
        repo2.seedInitialData()

        val persistedRecipes = repo2.allRecipes.first()
        val persistedPlans = repo2.allMealPlans.first()

        // Verify all 5 recipes (3 seeded + 1 PDF + 1 Website) are preserved
        assertEquals(5, persistedRecipes.size)
        assertTrue(persistedRecipes.any { it.title == "Kurczak curry" && it.source == "Jadlospis_PDF.pdf" })
        assertTrue(persistedRecipes.any { it.title == "Makaron z łososiem" && it.source == "Website" })

        // Verify moved & scaled meal and Monday dinner persisted across app restart
        val persistedMovedMeal = persistedPlans.first { it.id == wedLunchId }
        assertEquals(MealPlannerSupport.dateForDay("Piątek")!!.toString(), persistedMovedMeal.dayOfWeek)
        assertEquals("Obiad", persistedMovedMeal.mealType)
        assertEquals(2.0f, persistedMovedMeal.servings, 0.001f)
        assertEquals(1040, persistedMovedMeal.calories)
        assertTrue(persistedMovedMeal.isCompleted)

        val persistedMonDinner = persistedPlans.first { it.id == monDinnerId }
        assertEquals(MealPlannerSupport.dateForDay("Poniedziałek")!!.toString(), persistedMonDinner.dayOfWeek)
        assertEquals("Kolacja", persistedMonDinner.mealType)
        assertEquals("Makaron z łososiem", persistedMonDinner.recipeTitle)

        assertFalse("Removed meal must stay removed after restart", persistedPlans.any { it.id == mondayBreakfast.id })

        println("PLANNER_VERIFY_RESTART_RECIPES=${persistedRecipes.size}")
        println("PLANNER_VERIFY_RESTART_PLANS=${persistedPlans.size}")

        db2.close()
        dbFile.delete()
        Unit
    }

    @Test
    fun composeUi_addRecipeToPlanner_displayIngredients_scaleServings_andShoppingList(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = DietaViewModel(context.applicationContext as android.app.Application)

        // Wait for initial seed and add PDF + Website recipes via ViewModel/Room
        val dao = AppDatabase.getDatabase(context).dietaDao()
        dao.clearMealPlans()
        val pdfRecipeId = dao.insertRecipe(
            RecipeEntity(
                title = "Kurczak curry",
                description = "Porcje: 1 (Źródło: Jadlospis_PDF.pdf)",
                ingredients = "150 g Pierś z kurczaka\n80 g Ryż\n100 g Papryka\n100 ml Mleko kokosowe",
                instructions = "1. Pokrój kurczaka.\n2. Ugotuj ryż.\n3. Duś z mlekiem kokosowym.",
                calories = 520,
                protein = 42,
                carbs = 54,
                fat = 14,
                category = "Obiad",
                source = "Jadlospis_PDF.pdf"
            )
        )
        val pdfRecipe = dao.getAllRecipes().first().first { it.id == pdfRecipeId }

        // Add PDF recipe to Wednesday lunch via ViewModel
        viewModel.addMealPlan("Środa (11.09)", "Obiad", pdfRecipe, 1.0f)

        composeTestRule.setContent {
            com.example.ui.screens.MealPlanScreen(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        // Wait until Room emits the newly inserted meal plan into ViewModel StateFlow
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            viewModel.mealPlans.value.any { it.recipeTitle == "Kurczak curry" && it.dayOfWeek == MealPlannerSupport.dateForDay("Środa")!!.toString() }
        }

        // Select Wednesday ("Środa") in the weekly planner
        composeTestRule.onNodeWithTag("planner_day_Środa").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Kurczak curry — 1 porcja").fetchSemanticsNodes().isNotEmpty()
        }

        val wedMeal = viewModel.mealPlans.value.first { it.recipeTitle == "Kurczak curry" && it.dayOfWeek == MealPlannerSupport.dateForDay("Środa")!!.toString() }
        composeTestRule.onNodeWithTag("toggle_meal_details_${wedMeal.id}").performClick()
        // Verify Wednesday meal and individual ingredient quantities at 1 serving are displayed
        composeTestRule.onNodeWithText("Kurczak curry — 1 porcja").assertIsDisplayed()
        composeTestRule.onNodeWithText("- Pierś z kurczaka — 150 g").assertIsDisplayed()
        composeTestRule.onNodeWithText("- Ryż — 80 g").assertIsDisplayed()
        composeTestRule.onNodeWithText("- Papryka — 100 g").assertIsDisplayed()
        composeTestRule.onNodeWithText("- Mleko kokosowe — 100 ml").assertIsDisplayed()

        // Verify visible "Zamień" actions exist for the ingredients
        val swapNodes = composeTestRule.onAllNodesWithText("Zamień").fetchSemanticsNodes()
        assertTrue("Each ingredient must have a visible Zamień button", swapNodes.size >= 4)

        // Scale from 1 to 2 servings using the '+' button on the planned meal card
        composeTestRule.onNodeWithTag("increase_servings_${wedMeal.id}").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Kurczak curry — 2 porcje").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify scaled header and 2x ingredient quantities in the running UI
        composeTestRule.onNodeWithText("Kurczak curry — 2 porcje").assertIsDisplayed()
        composeTestRule.onNodeWithText("- Pierś z kurczaka — 300 g").assertIsDisplayed()
        composeTestRule.onNodeWithText("- Ryż — 160 g").assertIsDisplayed()
        composeTestRule.onNodeWithText("- Papryka — 200 g").assertIsDisplayed()
        composeTestRule.onNodeWithText("- Mleko kokosowe — 200 ml").assertIsDisplayed()

        // Verify Shopping List compatibility with the scaled 2-serving meal
        viewModel.generateShoppingList()

        var shoppingItems = emptyList<com.example.data.ShoppingItemEntity>()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            shoppingItems = runBlocking(kotlinx.coroutines.Dispatchers.IO) { dao.getAllShoppingItems().first() }
            shoppingItems.any { it.itemName.contains("Pierś z kurczaka", ignoreCase = true) && it.amount.contains("300") } &&
                shoppingItems.any { it.itemName.contains("Ryż", ignoreCase = true) && it.amount.contains("160") }
        }

        assertTrue(
            "Shopping list must include 300 g Pierś z kurczaka from 2-serving meal",
            shoppingItems.any { it.itemName.contains("Pierś z kurczaka", ignoreCase = true) && it.amount.contains("300") }
        )
        assertTrue(
            "Shopping list must include 160 g Ryż from 2-serving meal",
            shoppingItems.any { it.itemName.contains("Ryż", ignoreCase = true) && it.amount.contains("160") }
        )
        println("PLANNER_VERIFY_SHOPPING_ITEMS=${shoppingItems.joinToString { "${it.itemName}: ${it.amount}" }}")
    }
}
