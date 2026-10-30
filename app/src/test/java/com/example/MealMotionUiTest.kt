package com.example

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.MealPlanEntity
import com.example.ui.components.*
import com.example.ui.navigation.MangoBottomNav
import com.example.ui.navigation.Screen
import com.example.ui.theme.MojaDietaAiTheme
import com.example.viewmodel.ScaledMealNutrition
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp")
class MealMotionUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nutritionFinishesAtSavedValuesAndMissingDataNeverAppearsAsZero() {
        var nutrition by mutableStateOf(ScaledMealNutrition(489, 39, 58, 9))
        compose.setContent { MojaDietaAiTheme { AnimatedMealNutrition(nutrition, "macro_change") } }
        compose.runOnIdle { nutrition = ScaledMealNutrition(412, 32, 46, 11) }
        compose.waitForIdle()
        compose.onNodeWithTag("macro_change").assertTextEquals("412 kcal • B: 32g • W: 46g • T: 11g")
        compose.runOnIdle { nutrition = ScaledMealNutrition(calories = null, protein = null, carbs = null, fat = null) }
        compose.waitForIdle()
        compose.onNodeWithTag("macro_change").assertTextEquals("Brak danych o wartościach odżywczych")
    }
    @Test fun removedMealLeavesQueueAndUndoRestoresItsOriginalPosition() {
        val first = MealPlanEntity(id = 1, dayOfWeek = "2026-10-14", mealType = "Śniadanie", recipeTitle = "Pierwszy", calories = 200)
        val second = first.copy(id = 2, recipeTitle = "Drugi")
        var meals by mutableStateOf(listOf(first, second))
        compose.setContent { MojaDietaAiTheme { Column {
            val retained = rememberMealQueueMotion(meals, "2026-10-14")
            retained.forEach { meal -> key(meal.id) {
                MealQueueMotionItem(meals.any { it.id == meal.id }) { Text(meal.recipeTitle, Modifier.testTag("queue_${meal.id}")) }
            } }
        } } }
        compose.waitForIdle()
        compose.runOnIdle { meals = listOf(second) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithTag("queue_1").assertDoesNotExist()
        compose.onNodeWithTag("queue_2").assertExists()
        compose.runOnIdle { meals = listOf(first, second) }
        compose.waitForIdle()
        compose.onNodeWithTag("queue_1").assertExists()
        compose.onAllNodes(hasTestTag("queue_1") or hasTestTag("queue_2"))[0].assertTextEquals("Pierwszy")
    }
    @Test fun reducedMotionKeepsActionsFinalNumbersAndStateLabelsAvailable() {
        var number by mutableStateOf(ScaledMealNutrition(489, 39, 58, 9))
        var open by mutableStateOf(false)
        compose.setContent { MojaDietaAiTheme { CompositionLocalProvider(LocalReducedMangoMotion provides true) {
            Column {
                AnimatedMealNutrition(number, "reduced_macro")
                NutritionStatusBadge(NutritionStatus.COMPLETE, "Pełne dane")
                NutritionStatusBadge(NutritionStatus.MISSING, "Do uzupełnienia")
                NutritionStatusBadge(NutritionStatus.IN_PROGRESS, "Dzień w toku")
                MangoBottomNav(listOf(Screen.Dashboard, Screen.MealPlan), "meal_plan") {}
                MangoActionsMenu(open, { open = false }) { androidx.compose.material3.DropdownMenuItem(text = { Text("Przenieś") }, onClick = { open = false }) }
            }
        } } }
        compose.runOnIdle { number = ScaledMealNutrition(412, 32, 46, 11); open = true }
        compose.waitForIdle()
        compose.onNodeWithText("Przenieś").performClick()
        compose.onNodeWithTag("reduced_macro").assertTextEquals("412 kcal • B: 32g • W: 46g • T: 11g")
        compose.onNodeWithText("Dzień w toku").assertExists()
        compose.onNodeWithTag("nav_tab_meal_plan").assertIsSelected()
    }
}
