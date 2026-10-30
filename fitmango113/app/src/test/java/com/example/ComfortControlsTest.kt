package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.components.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ComfortControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun compactCardKeepsDetailsUntilExpandedAndCanCollapseAgain() {
        compose.setContent { MaterialTheme { CompactStartCard("Napoje", "500 ml", "water") { Text("Szczegóły napojów") } } }
        compose.onNodeWithText("Szczegóły napojów").assertDoesNotExist()
        compose.onNodeWithTag("compact_start_water").performClick()
        compose.onNodeWithText("Szczegóły napojów").assertIsDisplayed()
        compose.onNodeWithTag("compact_start_water").performClick()
        compose.onNodeWithText("Szczegóły napojów").assertDoesNotExist()
    }
    @Test fun horizontalSwipeChangesOneCalendarDayAcrossWeekBoundary() {
        val day = mutableStateOf("2026-10-11")
        compose.setContent { Box(Modifier.fillMaxSize().swipePlannerDays(day.value) { day.value = it }.testTag("swipe")) }
        compose.onNodeWithTag("swipe").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals("2026-10-12", day.value) }
        compose.onNodeWithTag("swipe").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals("2026-10-11", day.value) }
    }
    @Test fun smallMovementsDoNotChangeDate() {
        assertEquals(0L, swipeDayDelta(20f, 72f))
        assertEquals(1L, swipeDayDelta(-80f, 72f))
        assertEquals(-1L, swipeDayDelta(80f, 72f))
    }
    @Test fun bottomSheetKeepsSaveActionVisibleAndSavesOnce() {
        val open = mutableStateOf(true)
        var saved = 0
        compose.setContent { MaterialTheme {
            if (open.value) MangoEditSheet(onDismissRequest = { open.value = false }, title = { Text("Porcja") },
                text = { Text("Podgląd kalorii") }, confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { saved++; open.value = false }, modifier = Modifier.testTag("save_sheet")) { Text("Zapisz") }
                })
        } }
        compose.onNodeWithTag("save_sheet").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, saved); assertFalse(open.value) }
        compose.onNodeWithTag("save_sheet").assertDoesNotExist()
    }
}
