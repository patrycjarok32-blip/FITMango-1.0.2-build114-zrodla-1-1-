package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.components.StudioBalanceCard
import com.example.ui.theme.MojaDietaAiTheme
import com.example.viewmodel.ScaledMealNutrition
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp")
class StudioLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun balanceUpdatesConsumedRemainingAndMacrosTogetherWithoutChangingPlannedTotal() {
        val nutrition = mutableStateOf(ScaledMealNutrition(489, 39, 58, 9))
        compose.setContent { MojaDietaAiTheme {
            Box(Modifier.width(328.dp)) {
                StudioBalanceCard(nutrition.value, 1800, 110, 250, 60, 1705)
            }
        } }
        compose.onNodeWithTag("dashboard_consumed_calories").assertTextEquals("489")
        compose.onNodeWithTag("dashboard_remaining_calories").assertTextEquals("1311 kcal")
        compose.runOnIdle { nutrition.value = ScaledMealNutrition(412, 34, 47, 8) }
        compose.waitForIdle()
        compose.onNodeWithTag("dashboard_consumed_calories").assertTextEquals("412")
        compose.onNodeWithTag("dashboard_remaining_calories").assertTextEquals("1388 kcal")
        compose.onNodeWithText("34 / 110 g").assertIsDisplayed()
        compose.onNodeWithText("47 / 250 g").assertIsDisplayed()
        compose.onNodeWithText("8 / 60 g").assertIsDisplayed()
        compose.onNodeWithText("Zaplanowano: 1705 kcal").assertIsDisplayed()
    }

    @Test fun exceedingTargetShowsExcessAndPartialDataStaysVisible() {
        compose.setContent { MojaDietaAiTheme {
            StudioBalanceCard(ScaledMealNutrition(1900, 120, 250, 70, isPartial = true),
                1800, 110, 250, 60, 1900)
        } }
        compose.onNodeWithText("Ponad cel").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_remaining_calories").assertTextEquals("100 kcal*")
        compose.onNodeWithTag("dashboard_partial_nutrition").assertIsDisplayed()
        compose.onAllNodesWithText("10 g ponad cel").assertCountEquals(2)
    }
}
