package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.BodyPhotoEntity
import com.example.ui.components.BodyPhotosCard
import com.example.ui.theme.MojaDietaAiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h900dp")
class BodyPhotosUiTest {
    @get:Rule val compose = createComposeRule()
    private fun row(id: Long) = BodyPhotoEntity(id, "${20 + id}.10.2026", "file:///missing-$id.jpg", "", "")
    @Test fun allSavedDatesCanBeReachedAndEachAngleHasARepairAction() {
        compose.setContent { MojaDietaAiTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            BodyPhotosCard((1L..5L).map { row(it) }, { _, _, _, _ -> }, { _, _, _, _ -> }, { _, _ -> }, {})
        } } }
        compose.onNodeWithTag("body_photo_set_1").assertDoesNotExist()
        compose.onNodeWithTag("body_photos_more").performScrollTo().performClick()
        compose.onNodeWithTag("body_photo_set_1").assertExists()
        compose.onNodeWithTag("body_photo_replace_1_FRONT").performScrollTo().assertExists()
        compose.onNodeWithTag("body_photo_replace_1_SIDE").assertExists()
        compose.onNodeWithTag("body_photo_replace_1_BACK").assertExists()
    }
    @Test fun galleryCanCompareTwoDatesWithoutAddingNewPhotos() {
        compose.setContent { MojaDietaAiTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            BodyPhotosCard(listOf(row(1), row(2)), { _, _, _, _ -> fail("Must not save") }, { _, _, _, _ -> }, { _, _ -> }, {})
        } } }
        // Both empty and unavailable views remain visible with explicit repair actions.
        compose.onNodeWithTag("body_photo_replace_2_FRONT").assertExists()
        compose.onAllNodesWithText("Do porównania")[0].performScrollTo().performClick()
        compose.onAllNodesWithText("Do porównania")[0].performScrollTo().performClick()
        compose.onNodeWithTag("body_photos_compare").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Porównanie sylwetki").assertExists()
        compose.onNodeWithText("Zamknij").performClick()
    }
    @Test fun deletingRequiresConfirmationAndCancelLeavesTheSetUntouched() {
        var deleted: Long? = null
        compose.setContent { MojaDietaAiTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            BodyPhotosCard(listOf(row(1)), { _, _, _, _ -> }, { _, _, _, _ -> }, { id, done -> deleted = id; done(null) }, {})
        } } }
        compose.onNodeWithText("Usuń zestaw").performScrollTo().performClick()
        assertNull(deleted)
        compose.onNodeWithText("Anuluj").performClick()
        assertNull(deleted)
        compose.onNodeWithText("Usuń zestaw").performScrollTo().performClick()
        compose.onNodeWithText("Usuń").performClick()
        compose.runOnIdle { assertEquals(1L, deleted) }
    }
}
