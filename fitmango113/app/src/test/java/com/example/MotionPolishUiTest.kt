package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.RecipeEntity
import com.example.data.ShoppingItemEntity
import com.example.ui.components.*
import com.example.ui.theme.MojaDietaAiTheme
import com.example.viewmodel.CookingIngredient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h900dp")
class MotionPolishUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pressDecorationDoesNotConsumeClicksOrEnableDisabledButton() {
        var clicks = 0
        compose.setContent { MojaDietaAiTheme { Column {
            Button(onClick = { clicks++ }, modifier = Modifier.pressScaleEffect().testTag("active")) { Text("Dodaj") }
            Button(onClick = { clicks++ }, enabled = false, modifier = Modifier.pressScaleEffect().testTag("disabled")) { Text("Nieaktywne") }
        } } }
        compose.onNodeWithTag("active").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(170)
        compose.onNodeWithTag("active").performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.onNodeWithTag("disabled").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun checkedShoppingRowIsUpdatedThenMovesToEndAndCanReturn() {
        val a = ShoppingItemEntity(id = 1, itemName = "Jabłko", category = "Warzywa i owoce", weekStart = "2026-10-19")
        val b = a.copy(id = 2, itemName = "Banan")
        var rows by mutableStateOf(listOf(a, b))
        compose.setContent { MojaDietaAiTheme { CompositionLocalProvider(LocalReducedMangoMotion provides false) {
            Column {
                rememberShoppingRows(rows, true, "week").forEach { item ->
                    Row {
                        Checkbox(item.isChecked, { checked ->
                            rows = rows.map { if (it.id == item.id) it.copy(isChecked = checked) else it }
                                .sortedWith(compareBy<ShoppingItemEntity> { it.isChecked }.thenBy { it.id })
                        }, modifier = Modifier.testTag("toggle_${item.id}"))
                        Text("${item.itemName}:${item.isChecked}", Modifier.testTag("row_${item.id}"))
                    }
                }
            }
        } } }
        // Exercise a real control with the normal UI scheduler. Intermediate strike
        // ordering is verified separately against the production stages below.
        compose.onNodeWithTag("toggle_1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("toggle_1").assertIsOn()
        compose.onNodeWithTag("row_1").assertTextEquals("Jabłko:true")
        assertTrue(compose.onNodeWithTag("row_1").fetchSemanticsNode().boundsInRoot.top > compose.onNodeWithTag("row_2").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("toggle_1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("toggle_1").assertIsOff()
        compose.onNodeWithTag("row_1").assertTextEquals("Jabłko:false")
        assertTrue(compose.onNodeWithTag("row_1").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithTag("row_2").fetchSemanticsNode().boundsInRoot.top)
    }

    @Test fun reducedMotionShoppingAndPreviewKeepActionsAvailable() {
        val a = ShoppingItemEntity(id = 1, itemName = "Jabłko", category = "Warzywa i owoce")
        var rows by mutableStateOf(listOf(a))
        compose.setContent { MojaDietaAiTheme { CompositionLocalProvider(LocalReducedMangoMotion provides true) {
            Column { rememberShoppingRows(rows, false, "week").forEach { Text(it.itemName, Modifier.testTag("owned")) }; MangoMotionPreview() }
        } } }
        compose.runOnIdle { rows = listOf(a.copy(isChecked = true)) }
        compose.waitForIdle()
        compose.onNodeWithTag("owned").assertDoesNotExist()
        compose.onNodeWithTag("motion_status").assertTextContains("Ruch jest ograniczony", substring = true)
        compose.onNodeWithTag("motion_preview").performClick()
        compose.onNodeWithTag("motion_preview_result").assertExists()
    }

    @Test fun cookingTransitionsPreserveChecksInTheirOwnStepsAndAllowBackNavigation() {
        val recipe = RecipeEntity(id = 1, title = "Owsianka", description = "", ingredients = "", instructions = "1. Wsyp płatki.\n2. Dodaj banana.", calories = 0, protein = 0, carbs = 0, fat = 0, category = "Śniadanie")
        val ingredients = listOf(CookingIngredient("oats", "Płatki owsiane", "Płatki owsiane — 60 g", quantity = 60f, unit = "g"),
            CookingIngredient("banana", "Banan", "Banan — 100 g", quantity = 100f, unit = "g"))
        compose.setContent { MojaDietaAiTheme { CookingDialog(recipe, ingredients) {} } }
        compose.onNodeWithTag("cooking_step_progress").assertExists()
        compose.onNodeWithTag("cooking_check_oats").performClick().assertIsOn()
        compose.onNodeWithTag("cooking_next").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("cooking_check_banana").assertIsOff()
        compose.onNodeWithTag("cooking_previous").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("cooking_check_oats").assertIsOn()
    }
    @Test fun sourceEditsAndDeletionAppearWithoutWaitingForTheOrderEffect() {
        val a = ShoppingItemEntity(id = 1, itemName = "Jabłko", category = "Warzywa i owoce", amount = "100 g")
        val b = a.copy(id = 2, itemName = "Banan")
        var rows by mutableStateOf(listOf(a, b))
        val staleCommit = java.util.concurrent.atomic.AtomicBoolean(false)
        compose.setContent { MojaDietaAiTheme { Column {
            val current = rows
            val displayed = rememberShoppingRows(current, true, "week")
            SideEffect {
                if (displayed.any { row -> current.none { it == row } }) staleCommit.set(true)
            }
            displayed.forEach { Text("${it.itemName}:${it.amount}", Modifier.testTag("live_${it.id}")) }
        } } }
        compose.runOnIdle { rows = listOf(b, a.copy(itemName = "Jabłko czerwone", amount = "250 g", isChecked = true)) }
        compose.waitForIdle()
        compose.onNodeWithTag("live_1").assertTextEquals("Jabłko czerwone:250 g")
        compose.runOnIdle { rows = listOf(b) }
        compose.waitForIdle()
        compose.onNodeWithTag("live_1").assertDoesNotExist()
        compose.runOnIdle { assertFalse("A committed row used stale source values", staleCommit.get()) }
    }

    @Test fun switchingWeekDoesNotKeepRowsFromThePreviousShoppingList() {
        val a = ShoppingItemEntity(id = 1, itemName = "Jabłko", category = "Warzywa i owoce", weekStart = "2026-10-19")
        val b = a.copy(id = 2, itemName = "Banan", weekStart = "2026-10-26")
        var nextWeek by mutableStateOf(false)
        compose.setContent { MojaDietaAiTheme { Column {
            val source = if (nextWeek) listOf(b) else listOf(a)
            rememberShoppingRows(source, true, if (nextWeek) "2026-10-26" else "2026-10-19")
                .forEach { Text(it.itemName, Modifier.testTag("week_${it.id}")) }
        } } }
        compose.onNodeWithTag("week_1").assertExists()
        compose.runOnIdle { nextWeek = true }
        compose.waitForIdle()
        compose.onNodeWithTag("week_1").assertDoesNotExist()
        compose.onNodeWithTag("week_2").assertTextEquals("Banan")
    }

}
