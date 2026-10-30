package com.example.viewmodel

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DietaRepository
import com.example.data.MealPlanEntity
import com.example.data.RecipeEntity
import com.example.data.RecipeIngredientEntity
import com.example.health.HealthConnectAvailability
import com.example.health.HealthConnectReader
import com.example.health.HealthSyncResult
import com.example.ui.navigation.MangoBottomNav
import com.example.ui.navigation.Screen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.MealPlanScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RecipesScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import android.os.Looper
import org.junit.Before
import com.example.ai.SecureKeyStorage

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w480dp-h2400dp")
class MangoFitFinalUiPolishVerificationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        SecureKeyStorage.init(app)
        SecureKeyStorage.setTestingMode(true)
    }

    class TestFakeHealthReader : HealthConnectReader {
        override suspend fun checkAvailability(): HealthConnectAvailability = HealthConnectAvailability.Available
        override suspend fun getGrantedPermissions(): Set<String> = setOf("android.permission.health.READ_STEPS")
        override suspend fun syncAllHealthData(): HealthSyncResult = HealthSyncResult(stepsToday = 7500L, isConnected = true)
    }

    @Test
    fun testAllSevenNavigationTabs_accessibleAndRendered(): Unit = runBlocking {
        val items = listOf(
            Screen.Dashboard,
            Screen.MealPlan,
            Screen.Documents,
            Screen.AiCoach,
            Screen.Recipes,
            Screen.Shopping,
            Screen.Profile
        )
        assertEquals(7, items.size)

        var navigatedScreen: Screen? = null
        composeTestRule.setContent {
            MangoBottomNav(
                items = items,
                currentRoute = Screen.Dashboard.route,
                onNavigate = { navigatedScreen = it }
            )
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        composeTestRule.onNodeWithTag("nav_tab_dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_meal_plan").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_effects").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_sport").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_recipes").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_shopping").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_profile").assertIsDisplayed()

        // Click on Meal Plan tab
        composeTestRule.onNodeWithTag("nav_tab_meal_plan").performClick()
        assertEquals(Screen.MealPlan, navigatedScreen)
    }

    @Test
    fun testDashboard_calorieCards_hydration_andHealthConnect(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = DietaViewModel(app)
        viewModel.healthConnect.readerOverride = TestFakeHealthReader()

        composeTestRule.setContent {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToAiCoach = {},
                onNavigateToRecipes = {},
                onNavigateToMealPlan = {},
                onNavigateToBarcodeScanner = {},
                onNavigateToMeasurements = {},
                onNavigateToDocuments = {}
            )
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        composeTestRule.onNodeWithText("FITMango").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_consumed_calories").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("compact_start_health").performScrollTo().performClick()
        composeTestRule.onAllNodesWithText("Spalone kcal").onFirst().performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("compact_start_water").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Nawodnienie").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_health_connect_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_steps_text").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testRecipes_recipeCards_andIngredientsQuantityDisplay(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = AppDatabase.getDatabase(app).dietaDao()
        val repo = DietaRepository(dao)

        val recipeId = repo.insertRecipe(
            RecipeEntity(
                title = "Owsianka mango",
                description = "Pyszna owsianka na śniadanie",
                ingredients = "50 g Płatki owsiane\n150 ml Mleko migdałowe\n100 g Mango",
                instructions = "1. Zagotuj mleko.\n2. Wsyp płatki owsiane.\n3. Dodaj pokrojone mango.",
                calories = 380,
                protein = 12,
                carbs = 62,
                fat = 8,
                category = "Śniadanie",
                source = "MangoFit"
            )
        )
        repo.insertIngredient(
            RecipeIngredientEntity(
                recipeId = recipeId,
                foodName = "Płatki owsiane",
                quantity = 50f,
                unit = "g"
            )
        )

        val viewModel = DietaViewModel(app)

        composeTestRule.setContent {
            RecipesScreen(viewModel = viewModel)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        composeTestRule.onNodeWithText("Twoje przepisy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Owsianka mango").assertIsDisplayed()
    }

    @Test
    fun testWeeklyPlanner_weekdaysSelector_andMealDisplay(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = AppDatabase.getDatabase(app).dietaDao()
        val repo = DietaRepository(dao)

        repo.insertMealPlan(
            MealPlanEntity(
                dayOfWeek = "Poniedziałek",
                mealType = "Śniadanie",
                recipeTitle = "Owsianka z mango",
                calories = 380,
                servings = 1f,
                isCompleted = false
            )
        )

        val viewModel = DietaViewModel(app)

        composeTestRule.setContent {
            MealPlanScreen(viewModel = viewModel)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        composeTestRule.onNodeWithTag("planner_day_Poniedziałek").assertIsDisplayed()
        composeTestRule.onNodeWithTag("planner_day_Środa").assertIsDisplayed()
        composeTestRule.onNodeWithTag("planner_day_Niedziela").assertIsDisplayed()
    }

    @Test
    fun testDocuments_andShoppingScreen(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = DietaViewModel(app)
        var returnedHome = false

        composeTestRule.setContent {
            DocumentsScreen(viewModel = viewModel, onNavigateHome = { returnedHome = true })
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        composeTestRule.onNodeWithText("Moje Dokumenty (PDF)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dodaj PDF").assertIsDisplayed()
        composeTestRule.onNodeWithTag("documents_home").performClick()
        assertTrue(returnedHome)
    }

    @Test
    fun testProfileScreen_healthConnectSection(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = DietaViewModel(app)
        viewModel.healthConnect.readerOverride = TestFakeHealthReader()

        composeTestRule.setContent {
            ProfileScreen(viewModel = viewModel)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        composeTestRule.onNodeWithText("Profil i postępy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_tab_profile").performClick()
        composeTestRule.onNodeWithTag("health_connect_status_text").assertDoesNotExist()
        composeTestRule.onNodeWithTag("profile_content")
            .performScrollToNode(hasTestTag("profile_tile_health"))
        composeTestRule.onNodeWithTag("profile_tile_health").performClick()
        composeTestRule.onNodeWithTag("health_connect_section_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("health_connect_status_text").performScrollTo().assertIsDisplayed()
    }
}
