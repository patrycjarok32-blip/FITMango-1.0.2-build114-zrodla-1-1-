package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.components.*
import com.example.ui.theme.MojaDietaAiTheme
import com.example.viewmodel.*
import com.example.data.RecipeEntity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h900dp")
class PersonalProgressUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun evidenceExpandsActualGenerationSnapshotSeparatelyFromCurrentNumbers() {
        val today = LocalDate.parse("2026-10-22")
        val numbers = SportsGraphicsSupport.report(SportsData(), emptyList(), emptyList(), today, Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("UTC"))
        val explanation = WeeklyAiExplanation(numbers.from.toString(), numbers.until.toString(), "## Kierunek względem celu\nWymaga obserwacji.", "PEŁNY RAPORT", 123, true,
            "Zapis podczas generowania: 20.10, 1700 kcal, zatwierdzony; źródło zepp.")
        compose.setContent { MojaDietaAiTheme { Column(Modifier.verticalScroll(rememberScrollState())) { SportsWeeklyReport(numbers, null, explanation, false) {} } } }
        compose.onNodeWithTag("ai_evidence_snapshot").assertDoesNotExist()
        compose.onNodeWithTag("ai_evidence_toggle").performScrollTo().performClick()
        compose.onNodeWithTag("ai_evidence_snapshot").assertTextEquals(explanation.evidence)
    }
    @Test fun finalCookingStepHasCompletionAndAnExplicitCloseAction() {
        var closed = false
        val recipe = RecipeEntity(id = 1, title = "Test", description = "", ingredients = "", instructions = "Podaj danie.", calories = 0, protein = 0, carbs = 0, fat = 0, category = "Obiad")
        compose.setContent { MojaDietaAiTheme { CookingDialog(recipe) { closed = true } } }
        compose.onNodeWithTag("cooking_next").performClick()
        compose.onNodeWithTag("cooking_finished_close").assertExists()
        assertFalse(closed)
        compose.onNodeWithTag("cooking_finished_close").performClick()
        assertTrue(closed)
    }
}
