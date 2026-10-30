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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h900dp")
class SportsGraphicsUiTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.parse("2026-10-21")
    private val now = Instant.parse("2026-10-21T12:00:00Z")
    @Test fun selectingBarShowsActualSessionAndSourceWhileMissingDayShowsNoSession() {
        val data = SportsData(history = listOf(SportSession("a", "Spacer", "2026-10-20T10:00:00Z", "2026-10-20T11:00:00Z", "zepp")))
        val days = SportsGraphicsSupport.days(data, today.minusDays(2), now, ZoneId.of("UTC"))
        compose.setContent { MojaDietaAiTheme { SportActivityChart(days) } }
        compose.onNodeWithTag("sport_bars_2026-10-20").performClick()
        compose.onNodeWithText("Źródło: zepp").assertExists()
        compose.onNodeWithTag("sport_bars_2026-10-21").performClick()
        compose.onNodeWithText("2026-10-21 • brak zapisanych sesji").assertExists()
    }
    @Test fun fullOriginalAiIsAvailableOnlyAfterExpansion() {
        val facts = SportsGraphicsSupport.report(SportsData(), emptyList(), emptyList(), today, now, ZoneId.of("UTC"))
        val explanation = WeeklyAiExplanation(facts.from.toString(), facts.until.toString(), "Nieustrukturyzowana odpowiedź.", "ORYGINALNY RAPORT AI", 123, true)
        compose.setContent { MojaDietaAiTheme { Column(Modifier.verticalScroll(rememberScrollState())) { SportsWeeklyReport(facts, null, explanation, false) {} } } }
        compose.onNodeWithTag("sport_report_full").assertDoesNotExist()
        compose.onNodeWithTag("sport_report_expand").performScrollTo().performClick()
        compose.onNodeWithTag("sport_report_full").assertTextEquals("ORYGINALNY RAPORT AI")
    }
}
