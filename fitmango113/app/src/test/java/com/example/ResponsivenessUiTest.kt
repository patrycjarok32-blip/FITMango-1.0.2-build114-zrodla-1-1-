package com.example

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.*
import com.example.ui.screens.ChatComposer
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
class ResponsivenessUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun sleepUsesTheFullWidthOnAPhoneInsteadOfBeingSqueezedIntoThreeColumns() {
        compose.setContent { MojaDietaAiTheme { SportMetricRow(2272, 0, 396) } }
        val steps = compose.onNodeWithTag("activity_steps_metric").fetchSemanticsNode().boundsInRoot.width
        val sleep = compose.onNodeWithTag("activity_sleep_metric").fetchSemanticsNode().boundsInRoot.width
        assertTrue(sleep > steps * 1.5f)
    }
    @Test fun overviewAndSportDetailsUseTheSameSharedValuesInsteadOfTheirOldCaches() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SportsViewModel(app)
        val today = LocalDate.now()
        val current = CurrentActivity(2272, 396, 877.6, 1)
        val old = SportsData(daily = listOf(ActivityDay(today.toString(), 97, 324)))
        compose.setContent { MojaDietaAiTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            SportsOverviewCard({}, vm, today, current)
            ActivityTodayCard(old, today, current)
        } } }
        compose.onAllNodesWithText(HealthConnectSupport.formatStepsDisplay(2272)).assertCountEquals(2)
        compose.onAllNodesWithText(HealthConnectSupport.formatSleepDurationDisplay(396)).assertCountEquals(2)
        compose.onNodeWithText("97").assertDoesNotExist()
    }
    @Test fun chatKeepsTheExactDraftThroughRecreationAndSendsItOnlyOnExplicitTap() {
        val sent = mutableListOf<String>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MojaDietaAiTheme { ChatComposer(false) { sent += it } } }
        val draft = "Żółć, jabłko i 1800 kcal"
        compose.onNodeWithTag("chat_message_input").performTextInput(draft)
        assertTrue(sent.isEmpty())
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("chat_message_input").assertTextContains(draft)
        compose.onNodeWithContentDescription("Wyślij").performClick()
        compose.runOnIdle { assertEquals(listOf(draft), sent) }
        assertEquals("", compose.onNodeWithTag("chat_message_input").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }
}
