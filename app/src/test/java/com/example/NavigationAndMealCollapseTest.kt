package com.example

import androidx.compose.runtime.*
import kotlinx.coroutines.runBlocking
import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.flow.first
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.MealPlanEntity
import com.example.data.RecipeEntity
import com.example.health.*
import com.example.ui.navigation.MainNavigation
import com.example.ui.screens.PlannedMealDetailCard
import com.example.viewmodel.DietaViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationAndMealCollapseTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recipesCanReturnToStartThroughBottomTabAndHomeButton() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = DietaViewModel(app)
        viewModel.healthConnect.readerOverride = object : HealthConnectReader {
            override suspend fun checkAvailability() = HealthConnectAvailability.Available
            override suspend fun getGrantedPermissions() = emptySet<String>()
            override suspend fun syncAllHealthData() = HealthSyncResult()
        }
        compose.setContent { MainNavigation(viewModel) }
        compose.onNodeWithTag("nav_tab_recipes").performClick()
        compose.onNodeWithText("Twoje przepisy").assertIsDisplayed()
        compose.onNodeWithTag("nav_tab_dashboard").performClick()
        compose.onNodeWithText("FITMango").assertIsDisplayed()
        compose.onNodeWithTag("nav_tab_recipes").performClick()
        compose.onNodeWithTag("recipes_go_home").performClick()
        compose.onNodeWithText("FITMango").assertIsDisplayed()
    }

    @Test fun addingIngredientThroughDialogUpdatesSavedRecipeAndPreservesOtherIngredients() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = com.example.data.DietaRepository(com.example.data.AppDatabase.getDatabase(app).dietaDao())
        val original = kotlinx.coroutines.runBlocking {
            val recipe = RecipeEntity(title = "Test dodatkowego składnika", description = "", ingredients = "Ryż — 50 g", instructions = "Ugotuj", calories = 200, protein = 5, carbs = 35, fat = 3, category = "Obiad")
            recipe.copy(id = repo.insertRecipe(recipe))
        }
        val viewModel = DietaViewModel(app)
        var closed = false
        compose.setContent { com.example.ui.theme.MojaDietaAiTheme { androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) { androidx.compose.material3.Text("Przepis"); com.example.ui.components.AddRecipeIngredientDialog(original, viewModel) { closed = true } } } }
        compose.onNodeWithTag("extra_ingredient_name").performTextReplacement("Produkt testowy")
        compose.onNodeWithTag("extra_ingredient_amount").performTextReplacement("25")
        compose.onNodeWithTag("extra_ingredient_kcal").performScrollTo().performTextReplacement("200")
        compose.onNodeWithTag("save_extra_ingredient").performClick()
        try {
            compose.waitUntil(10000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                closed && viewModel.recipes.value.any { it.id == original.id && it.calories == original.calories + 50 }
            }
        } catch (e: Throwable) {
            println("EXTRA_INGREDIENT_CLOSED=$closed")
            println(kotlinx.coroutines.runBlocking { repo.allRecipes.first() })
            println(compose.onRoot(useUnmergedTree = true).printToString())
            throw e
        }
        val saved = viewModel.recipes.value.first { it.id == original.id }
        org.junit.Assert.assertTrue(saved.ingredients.contains(original.ingredients))
        org.junit.Assert.assertTrue(saved.ingredients.contains("Produkt testowy — 25 g"))
    }

    @Test fun mealDetailsCanCollapseAndExpandRepeatedly() {
        val meal = MealPlanEntity(id = 123, dayOfWeek = "Poniedziałek", mealType = "Obiad",
            recipeTitle = "Zupa", calories = 200)
        val recipe = RecipeEntity(title = "Zupa", description = "", ingredients = "100 g marchew",
            instructions = "Ugotuj", calories = 200, protein = 2, carbs = 20, fat = 3, category = "Lunch")
        compose.setContent {
            PlannedMealDetailCard(meal, recipe, emptyList(), "12:00", {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
        compose.onNodeWithTag("decrease_servings_123").assertDoesNotExist()
        compose.onNodeWithTag("toggle_meal_details_123").performClick()
        compose.onNodeWithTag("decrease_servings_123").assertExists()
        compose.onNodeWithTag("toggle_meal_details_123").performClick()
        compose.onNodeWithTag("decrease_servings_123").assertDoesNotExist()
        compose.onNodeWithTag("toggle_meal_details_123").performClick()
        compose.onNodeWithTag("decrease_servings_123").assertExists()
    }
    @Test fun gramsFormSavesConsumedNutritionAndSupportsUndo() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        com.example.viewmodel.RecipePhotoSettings(app).enabled = false
        val dao = com.example.data.AppDatabase.getDatabase(app).dietaDao()
        val recipe = RecipeEntity(title = "Zupa test gramów", description = "", ingredients = "Woda — 500 g",
            instructions = "Ugotuj", calories = 400, protein = 40, carbs = 60, fat = 10, category = "Obiad")
        val recipeId = runBlocking { dao.insertRecipe(recipe) }
        val mealId = runBlocking { dao.insertMealPlan(MealPlanEntity(dayOfWeek = "Poniedziałek", mealType = "Obiad",
            recipeId = recipeId, recipeTitle = recipe.title, calories = 400)) }
        val vm = DietaViewModel(app)
        var saveSucceeded = false
        var saveError: String? = null
        compose.setContent {
            val meals by vm.mealPlans.collectAsState()
            var portionText by remember { mutableStateOf("") }
            var eatenText by remember { mutableStateOf("") }
            var saving by remember { mutableStateOf(false) }
            com.example.ui.theme.MojaDietaAiTheme {
                androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Column {
                        meals.firstOrNull { it.id == mealId }?.let { meal ->
                            com.example.ui.components.MealGramsContent(
                                portionText, eatenText, { portionText = it }, { eatenText = it },
                                400f, saving, saveError,
                                onSave = { eaten, portion ->
                                    saving = true
                                    vm.recordMealGrams(meal, eaten, portion) { success, error ->
                                        saving = false
                                        saveSucceeded = success
                                        saveError = error
                                    }
                                })
                        }
                    }
                }
            }
        }
        compose.waitUntil(10000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.mealPlans.value.any { it.id == mealId }
        }
        compose.onNodeWithTag("meal_portion_grams_input").assertIsDisplayed()
        compose.onNodeWithTag("meal_eaten_grams_input").assertIsDisplayed()
        compose.onNodeWithTag("save_meal_grams").assertIsNotEnabled()
        compose.onNodeWithTag("meal_portion_grams_input").performTextReplacement("500")
        compose.onNodeWithTag("meal_eaten_grams_input").performTextReplacement("50")
        compose.onNodeWithText("Po przeliczeniu: 40 kcal. Makro zmieni się proporcjonalnie.").assertExists()
        compose.onNodeWithTag("save_meal_grams").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            saveSucceeded && vm.mealPlans.value.firstOrNull { it.id == mealId }?.isCompleted == true
        }
        org.junit.Assert.assertNull(saveError)
        val saved = runBlocking { dao.getAllMealPlans().first().first { it.id == mealId } }
        org.junit.Assert.assertEquals(0.1f, saved.servings, 0.001f)
        org.junit.Assert.assertEquals(40, saved.calories)
        org.junit.Assert.assertNotNull(saved.completedDate)
        vm.undoLastMealChange()
        compose.waitUntil(10000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.mealPlans.value.firstOrNull { it.id == mealId }?.servings == 1f
        }
        val restored = runBlocking { dao.getAllMealPlans().first().first { it.id == mealId } }
        org.junit.Assert.assertEquals(400, restored.calories)
        org.junit.Assert.assertFalse(restored.isCompleted)
    }

    @Test fun changingIngredientQuantityDoesNotMarkMealAsEaten() {
        val meal = MealPlanEntity(id = 321, dayOfWeek = "Środa", mealType = "Śniadanie", recipeTitle = "Owsianka", calories = 200)
        val recipe = RecipeEntity(title = "Owsianka", description = "", ingredients = "Płatki owsiane — 50 g", instructions = "Ugotuj", calories = 200, protein = 5, carbs = 30, fat = 3, category = "Śniadanie")
        var eatenClicks = 0
        var quantity = 0f
        compose.setContent {
            com.example.ui.theme.MojaDietaAiTheme {
                androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
                    PlannedMealDetailCard(meal, recipe, emptyList(), "08:00", {}, {}, {}, {}, {}, {}, {}, {}, { eatenClicks++ },
                        onIngredientQuantityChange = { _, newQuantity -> quantity = newQuantity })
                }
            }
        }
        compose.onNodeWithTag("toggle_meal_details_321").performClick()
        compose.onNodeWithTag("edit_ingredient_quantity_321_Płatki owsiane").performClick()
        compose.onNodeWithTag("meal_ingredient_quantity_input").performTextReplacement("75")
        compose.onNodeWithTag("save_meal_ingredient_quantity").performClick()
        org.junit.Assert.assertEquals(75f, quantity, 0f)
        org.junit.Assert.assertEquals(0, eatenClicks)
    }

    @Test fun importedRecipeNameCanBeEditedBeforeRoomSave() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = DietaViewModel(app)
        viewModel.updateRecipePreview(com.example.viewmodel.ImportedRecipe(title = "Nazwa z Instagrama", servings = "1", ingredients = "Płatki owsiane — 50 g", instructions = "Ugotuj", calories = 200, protein = 5, carbs = 30, fat = 3))
        compose.setContent { com.example.ui.theme.MojaDietaAiTheme { com.example.ui.screens.RecipesScreen(viewModel) } }
        compose.onNodeWithTag("preview_edit_title_input").performTextReplacement("Moja owsianka po imporcie")
        compose.onNodeWithTag("preview_save_recipe_button").performClick()
        compose.waitUntil(10000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            viewModel.recipes.value.any { it.title == "Moja owsianka po imporcie" }
        }
        val repo = com.example.data.DietaRepository(com.example.data.AppDatabase.getDatabase(app).dietaDao())
        val saved = kotlinx.coroutines.runBlocking { repo.allRecipes.first().single { it.title == "Moja owsianka po imporcie" } }
        org.junit.Assert.assertEquals("Płatki owsiane — 50 g", saved.ingredients)
        org.junit.Assert.assertEquals(200, saved.calories)
    }

    @Test fun markingOldZeroCalorieMealUsesRecipeNutritionAndExplicitDate() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = com.example.data.AppDatabase.getDatabase(app).dietaDao()
        val today = java.time.LocalDate.now()
        val meal = kotlinx.coroutines.runBlocking {
            val recipe = RecipeEntity(title = "Test spożycia", description = "", ingredients = "Ryż — 50 g", instructions = "Ugotuj", calories = 200, protein = 5, carbs = 40, fat = 2, category = "Obiad")
            val id = dao.insertRecipe(recipe)
            val plan = MealPlanEntity(dayOfWeek = com.example.viewmodel.MealPlannerSupport.weekDays(today)[today.dayOfWeek.value - 1].canonicalName,
                mealType = "Obiad", recipeId = id, recipeTitle = recipe.title, calories = 0)
            plan.copy(id = dao.insertMealPlan(plan))
        }
        val viewModel = DietaViewModel(app)
        compose.setContent { androidx.compose.material3.Text("Dziennik") }
        viewModel.toggleMealPlanCompleted(meal)
        compose.waitUntil(10000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            viewModel.mealPlans.value.any { it.id == meal.id && it.isCompleted && it.completedDate == today.toString() && it.calories == 200 }
        }
        val saved = kotlinx.coroutines.runBlocking { dao.getAllMealPlans().first().first { it.id == meal.id } }
        org.junit.Assert.assertEquals(200, com.example.viewmodel.MealPlannerSupport.consumedCaloriesForDate(listOf(saved), today))
    }

}
