package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.components.ActivityRing
import com.example.ui.components.RestTimerCard
import com.example.ui.theme.MojaDietaAiTheme
import androidx.compose.foundation.layout.Column
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp")
class SportsControlsUiTest {
    @Test fun historyPermissionsAndRefreshAreDirectlyAvailableWithoutDiaryTabs() {
        var permissions = false
        var refreshed = false
        compose.setContent { MojaDietaAiTheme { com.example.ui.screens.SportsHistoryControls(false, { permissions = true }, { refreshed = true }) } }
        compose.onNodeWithText("Dziennik").assertDoesNotExist()
        compose.onNodeWithText("Plan").assertDoesNotExist()
        compose.onNodeWithTag("sport_permissions").performClick()
        compose.onNodeWithTag("sport_refresh").performClick()
        org.junit.Assert.assertTrue(permissions)
        org.junit.Assert.assertTrue(refreshed)
    }
    @Test fun anotherReadCannotBeStartedWhileHistoryIsLoading() {
        compose.setContent { MojaDietaAiTheme { com.example.ui.screens.SportsHistoryControls(true, {}, {}) } }
        compose.onNodeWithTag("sport_permissions").assertIsNotEnabled()
        compose.onNodeWithTag("sport_refresh").assertIsNotEnabled()
        compose.onNodeWithText("Odczyt…").assertExists()
    }

    @get:Rule val compose = createComposeRule()
    @Test fun activityGoalAndTimerActionsRemainVisibleWithoutAnimationLoops() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("FITMangoRestTimer", 0).edit().clear().commit()
        compose.setContent { MojaDietaAiTheme { Column { ActivityRing(2, 3); RestTimerCard() } } }
        compose.onNodeWithText("2/3").assertExists()
        compose.onNodeWithText("60s").assertExists()
        compose.onNodeWithText("90s").assertExists()
        compose.onNodeWithText("120s").assertExists()
        compose.onNodeWithText("Wyzeruj").performClick()
        compose.onNodeWithTag("rest_timer").assertTextEquals("Przerwa między seriami • 0:00")
    }
}
