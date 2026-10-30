package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.components.ReductionPlanCard
import com.example.ui.theme.MojaDietaAiTheme
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h900dp")
class ReductionPlanUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun editingSavesUserDurationsInsteadOfSilentlyStartingTheExample() {
        var result: ReductionPlan? = null
        compose.setContent { MojaDietaAiTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ReductionPlanCard(null, LocalDate.parse("2026-10-26"), false, 2000, {}) { result = it }
        } } }
        assertNull(result)
        compose.onNodeWithText("Ustaw etapy").performScrollTo().performClick()
        compose.onNodeWithTag("reduction_weeks_0").performScrollTo().performTextReplacement("2")
        compose.onNodeWithTag("reduction_save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(2, result!!.stages.first().weeks)
            assertEquals(listOf(1800, 1700, 1600), result!!.stages.map { it.calories })
        }
    }
    @Test fun chartIsAvailableWithoutWeightHistoryAndHasAccessibleDescription() {
        val plan = ReductionPlan("2026-10-26", listOf(ReductionStage(1800, 3), ReductionStage(1700, 3)))
        compose.setContent { MojaDietaAiTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ReductionPlanCard(plan, LocalDate.parse("2026-11-16"), false, 1800, {}) {}
        } } }
        compose.onNodeWithTag("reduction_chart").assertExists()
        compose.onNodeWithContentDescription("Plan kalorii: 1800 kcal przez 3 tygodni, 1700 kcal przez 3 tygodni").assertExists()
        compose.onNodeWithText("Do końca ostatniego etapu: 21 dni. Potem oceń dalszy plan.").performScrollTo().assertExists()
    }
}
